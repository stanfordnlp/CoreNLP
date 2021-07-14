package edu.stanford.nlp.parser.shiftreduce; 
import edu.stanford.nlp.util.logging.Redwood;


import java.io.PrintWriter;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.PriorityQueue;
import java.util.stream.Collectors;

import edu.stanford.nlp.ling.HasWord;
import edu.stanford.nlp.ling.Label;
import edu.stanford.nlp.ling.SentenceUtils;
import edu.stanford.nlp.parser.KBestViterbiParser;
import edu.stanford.nlp.parser.common.ParserConstraint;
import edu.stanford.nlp.parser.common.ParserQuery;
import edu.stanford.nlp.parser.lexparser.Debinarizer;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.tregex.TregexPattern;
import edu.stanford.nlp.trees.tregex.tsurgeon.Tsurgeon;
import edu.stanford.nlp.trees.tregex.tsurgeon.TsurgeonPattern;
import edu.stanford.nlp.util.Generics;
import edu.stanford.nlp.util.RuntimeInterruptedException;
import edu.stanford.nlp.util.ScoredComparator;
import edu.stanford.nlp.util.ScoredObject;

public class ShiftReduceParserQuery implements ParserQuery  {

  /** A logger for this class */
  private static Redwood.RedwoodChannels log = Redwood.channels(ShiftReduceParserQuery.class);
  Debinarizer debinarizer = new Debinarizer(false);

  List<? extends HasWord> originalSentence;
  private State initialState, finalState;
  Tree debinarized;

  boolean success;
  boolean unparsable;

  private List<State> bestParses;

  final ShiftReduceParser parser;

  List<ParserConstraint> constraints = null;

  public ShiftReduceParserQuery(ShiftReduceParser parser) {
    this.parser = parser;
  }

  @Override
  public boolean parse(List<? extends HasWord> sentence) {
    this.originalSentence = sentence;
    initialState = ShiftReduceParser.initialStateFromTaggedSentence(sentence);
    return parseInternal();
  }

  public boolean parse(Tree tree) {
    this.originalSentence = tree.yieldHasWord();
    initialState = ShiftReduceParser.initialStateFromGoldTagTree(tree);
    return parseInternal();
  }

  // TODO: we are assuming that sentence final punctuation always has
  // either . or PU as the tag.
  private static TregexPattern rearrangeFinalPunctuationTregex =
    TregexPattern.compile("__ !> __ <- (__=top <- (__ <<- (/[.]|PU/=punc < /[.!?。！？]/ ?> (__=single <: =punc))))");

  private static TsurgeonPattern rearrangeFinalPunctuationTsurgeon =
    Tsurgeon.parseOperation("[move punc >-1 top] [if exists single prune single]");

  private boolean parseInternal() {
    final int maxBeamSize;
    if (parser.op.testOptions().beamSize == 0) {
      maxBeamSize = Math.max(parser.op.trainOptions().beamSize, 1);
    } else {
      maxBeamSize = parser.op.testOptions().beamSize;
    }

    success = true;
    unparsable = false;
    PriorityQueue<State> oldBeam = new PriorityQueue<>(maxBeamSize + 1, ScoredComparator.ASCENDING_COMPARATOR);
    PriorityQueue<State> beam = new PriorityQueue<>(maxBeamSize + 1, ScoredComparator.ASCENDING_COMPARATOR);
    // nextBeam will keep track of an unused PriorityQueue to cut down on the number of PriorityQueue objects created
    PriorityQueue<State> nextBeam = new PriorityQueue<>(maxBeamSize + 1, ScoredComparator.ASCENDING_COMPARATOR);
    beam.add(initialState);
    while (beam.size() > 0) {
      if (Thread.interrupted()) { // Allow interrupting the parser
        throw new RuntimeInterruptedException();
      }
      // log.info("================================================");
      // log.info("Current beam:");
      // log.info(beam);
      PriorityQueue<State> temp = oldBeam;
      oldBeam = beam;
      beam = nextBeam;
      beam.clear();
      nextBeam = temp;

      State bestState = null;
      for (State state : oldBeam) {
        if (Thread.interrupted()) {  // Allow interrupting the parser
          throw new RuntimeInterruptedException();
        }
        Collection<ScoredObject<Integer>> predictedTransitions = parser.model.findHighestScoringTransitions(state, true, maxBeamSize, constraints);
        // log.info("Examining state: " + state);
        for (ScoredObject<Integer> predictedTransition : predictedTransitions) {
          Transition transition = parser.model.transitionIndex.get(predictedTransition.object());
          State newState = transition.apply(state, predictedTransition.score());
          // log.info("  Transition: " + transition + " (" + predictedTransition.score() + ")");
          if (bestState == null || bestState.score() < newState.score()) {
            bestState = newState;
          }
          beam.add(newState);
          if (beam.size() > maxBeamSize) {
            beam.poll();
          }
        }
      }
      if (beam.size() == 0) {
        // Oops, time for some fallback plan
        // This can happen with the set of constraints given by the original paper
        // For example, one particular French model had a situation where it would reach
        //   @Ssub @Ssub .
        // without a left(Ssub) transition, so finishing the parse was impossible.
        // This will probably result in a bad parse, but at least it
        // will result in some sort of parse.
        for (State state : oldBeam) {
          Transition transition = parser.model.findEmergencyTransition(state, constraints);
          if (transition != null) {
            State newState = transition.apply(state);
            if (bestState == null || bestState.score() < newState.score()) {
              bestState = newState;
            }
            beam.add(newState);
          }
        }
      }

      // bestState == null only happens when we have failed to make progress, so quit
      // If the bestState is finished, we are done
      if (bestState == null || bestState.isFinished()) {
        break;
      }
    }

    bestParses = beam.stream().filter((state) -> state.isFinished())
      .collect(Collectors.toList());

    if (bestParses.size() == 0) {
      success = false;
      unparsable = true;
      debinarized = null;
      finalState = null;
      bestParses = Collections.emptyList();
    } else {
      Collections.sort(bestParses, beam.comparator());
      Collections.reverse(bestParses);
      finalState = bestParses.get(0);
      debinarized = debinarizer.transformTree(finalState.stack.peek());
      debinarized = Tsurgeon.processPattern(rearrangeFinalPunctuationTregex, rearrangeFinalPunctuationTsurgeon, debinarized);
    }
    return success;
  }

  /**
   * TODO: if we add anything interesting to report, we should report it here
   */
  @Override
  public boolean parseAndReport(List<? extends HasWord> sentence, PrintWriter pwErr) {
    boolean success = parse(sentence);
    //log.info(getBestTransitionSequence());
    //log.info(getBestBinarizedParse());
    return success;
  }

  public Tree getBestBinarizedParse() {
    return finalState.stack.peek();
  }

  public List<Transition> getBestTransitionSequence() {
    return finalState.transitions.asList();
  }

  @Override
  public double getPCFGScore() {
    return finalState.score;
  }

  @Override
  public Tree getBestParse() {
    return debinarized;
  }

  @Override
  public List<ScoredObject<Tree>> getKBestParses(int k) { return this.getKBestPCFGParses(k); }

  @Override
  public double getBestScore() { return this.getPCFGScore(); }

  /** TODO: can we get away with not calling this PCFG? */
  @Override
  public Tree getBestPCFGParse() {
    return debinarized;
  }

  @Override
  public Tree getBestDependencyParse(boolean debinarize) {
    return null;
  }

  @Override
  public Tree getBestFactoredParse() {
    return null;
  }

  /** TODO: if this is a beam, return all equal parses */
  @Override
  public List<ScoredObject<Tree>> getBestPCFGParses() {
    ScoredObject<Tree> parse = new ScoredObject<>(debinarized, finalState.score);
    return Collections.singletonList(parse);
  }

  @Override
  public boolean hasFactoredParse() {
    return false;
  }

  /** TODO: return more if this used a beam */
  @Override
  public List<ScoredObject<Tree>> getKBestPCFGParses(int kbestPCFG) {
    ScoredObject<Tree> parse = new ScoredObject<>(debinarized, finalState.score);
    return Collections.singletonList(parse);
  }

  @Override
  public List<ScoredObject<Tree>> getKGoodFactoredParses(int kbest) {
    throw new UnsupportedOperationException();
  }

  @Override
  public KBestViterbiParser getPCFGParser() {
    // TODO: find some way to treat this as a KBestViterbiParser?
    return null;
  }

  @Override
  public KBestViterbiParser getDependencyParser() {
    return null;
  }

  @Override
  public KBestViterbiParser getFactoredParser() {
    return null;
  }

  @Override
  public void setConstraints(List<ParserConstraint> constraints) {
    this.constraints = constraints;
  }

  @Override
  public boolean saidMemMessage() {
    return false;
  }

  @Override
  public boolean parseSucceeded() {
    return success;
  }

  /** TODO: skip sentences which are too long */
  @Override
  public boolean parseSkipped() {
    return false;
  }

  @Override
  public boolean parseFallback() {
    return false;
  }

  /** TODO: add memory handling? */
  @Override
  public boolean parseNoMemory() {
    return false;
  }

  @Override
  public boolean parseUnparsable() {
    return unparsable;
  }

  @Override
  public List<? extends HasWord> originalSentence() {
    return originalSentence;
  }

  /**
   * TODO: clearly this should be a default method in ParserQuery once Java 8 comes out
   */
  @Override
  public void restoreOriginalWords(Tree tree) {
    if (originalSentence == null || tree == null) {
      return;
    }
    List<Tree> leaves = tree.getLeaves();
    if (leaves.size() != originalSentence.size()) {
      throw new IllegalStateException("originalWords and sentence of different sizes: " + originalSentence.size() + " vs. " + leaves.size() +
                                      "\n Orig: " + SentenceUtils.listToString(originalSentence) +
                                      "\n Pars: " + SentenceUtils.listToString(leaves));
    }
    Iterator<Tree> leafIterator = leaves.iterator();
    for (HasWord word : originalSentence) {
      Tree leaf = leafIterator.next();
      if (!(word instanceof Label)) {
        continue;
      }
      leaf.setLabel((Label) word);
    }
  }

}
