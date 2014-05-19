package edu.stanford.nlp.parser.shiftreduce;


import java.io.PrintWriter;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import edu.stanford.nlp.ling.HasWord;
import edu.stanford.nlp.ling.Label;
import edu.stanford.nlp.ling.Sentence;
import edu.stanford.nlp.parser.KBestViterbiParser;
import edu.stanford.nlp.parser.common.ParserConstraint;
import edu.stanford.nlp.parser.common.ParserQuery;
import edu.stanford.nlp.parser.lexparser.Debinarizer;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.util.Generics;
import edu.stanford.nlp.util.ScoredObject;

public class ShiftReduceParserQuery implements ParserQuery {
  Debinarizer debinarizer = new Debinarizer(false);

  List<? extends HasWord> originalSentence;
  State initialState, finalState;
  Tree debinarized;

  boolean success;
  boolean unparsable;

  final ShiftReduceParser parser;

  public ShiftReduceParserQuery(ShiftReduceParser parser) {
    this.parser = parser;
  }

  // TODO: this isn't a beam, this is just a single width beam.  
  // Make it a beam search
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

  private boolean parseInternal() {
    State state = initialState;
    success = true;
    unparsable = false;
    while (!state.finished) {
      Set<String> features = parser.featureFactory.featurize(state);
      ScoredObject<Integer> predictedTransition = parser.findHighestScoringTransition(state, features, true);
      if (predictedTransition.object() >= 0) {
        // TODO: do something with the score
        Transition transition = parser.transitionIndex.get(predictedTransition.object());
        state = transition.apply(state);
      } else {
        success = false;
        unparsable = true;
        break;
      }
    }
    finalState = state;
    debinarized = debinarizer.transformTree(state.stack.peek());
    return success;
  }

  /**
   * TODO: if we add anything interesting to report, we should report it here
   */
  @Override
  public boolean parseAndReport(List<? extends HasWord> sentence, PrintWriter pwErr) {
    boolean success = parse(sentence);
    //System.err.println(getBestTransitionSequence());
    //System.err.println(getBestBinarizedParse());
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
    ScoredObject<Tree> parse = new ScoredObject<Tree>(debinarized, finalState.score);
    return Collections.singletonList(parse);    
  }

  @Override
  public boolean hasFactoredParse() {
    return false;
  }

  /** TODO: return more if this used a beam */
  @Override
  public List<ScoredObject<Tree>> getKBestPCFGParses(int kbestPCFG) {
    ScoredObject<Tree> parse = new ScoredObject<Tree>(debinarized, finalState.score);
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
    // TODO
    throw new UnsupportedOperationException("Unable to set constraints on the shift reduce parser (yet)");
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
                                      "\n Orig: " + Sentence.listToString(originalSentence) +
                                      "\n Pars: " + Sentence.listToString(leaves));
    }
    // TODO: get rid of this cast
    Iterator<? extends Label> wordsIterator = (Iterator<? extends Label>) originalSentence.iterator();
    for (Tree leaf : leaves) {
      leaf.setLabel(wordsIterator.next());
    }
  }

}
