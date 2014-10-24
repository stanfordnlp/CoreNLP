// Stanford Parser -- a probabilistic lexicalized NL CFG parser
// Copyright (c) 2002 - 2014 The Board of Trustees of
// The Leland Stanford Junior University. All Rights Reserved.
//
// This program is free software; you can redistribute it and/or
// modify it under the terms of the GNU General Public License
// as published by the Free Software Foundation; either version 2
// of the License, or (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with this program; if not, write to the Free Software Foundation,
// Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
//
// For more information, bug reports, fixes, contact:
//    Christopher Manning
//    Dept of Computer Science, Gates 1A
//    Stanford CA 94305-9010
//    USA
//    parser-support@lists.stanford.edu
//    http://nlp.stanford.edu/software/srparser.shtml

package edu.stanford.nlp.parser.shiftreduce;

import java.io.FileFilter;
import java.io.IOException;
import java.io.Serializable;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Random;
import java.util.Set;

import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.io.RuntimeIOException;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.HasTag;
import edu.stanford.nlp.ling.HasWord;
import edu.stanford.nlp.ling.Label;
import edu.stanford.nlp.ling.TaggedWord;
import edu.stanford.nlp.ling.Word;
import edu.stanford.nlp.parser.common.ArgUtils;
import edu.stanford.nlp.parser.common.ParserConstraint;
import edu.stanford.nlp.parser.common.ParserGrammar;
import edu.stanford.nlp.parser.common.ParserQuery;
import edu.stanford.nlp.parser.common.ParserUtils;
import edu.stanford.nlp.parser.lexparser.BinaryHeadFinder;
import edu.stanford.nlp.parser.lexparser.EvaluateTreebank;
import edu.stanford.nlp.parser.lexparser.Options;
import edu.stanford.nlp.parser.lexparser.TreebankLangParserParams;
import edu.stanford.nlp.parser.lexparser.TreeBinarizer;
import edu.stanford.nlp.parser.metrics.ParserQueryEval;
import edu.stanford.nlp.parser.metrics.Eval;
import edu.stanford.nlp.stats.IntCounter;
import edu.stanford.nlp.tagger.common.Tagger;
import edu.stanford.nlp.trees.BasicCategoryTreeTransformer;
import edu.stanford.nlp.trees.CompositeTreeTransformer;
import edu.stanford.nlp.trees.HeadFinder;
import edu.stanford.nlp.trees.LabeledScoredTreeNode;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.Treebank;
import edu.stanford.nlp.trees.TreebankLanguagePack;
import edu.stanford.nlp.trees.TreeCoreAnnotations;
import edu.stanford.nlp.trees.Trees;
import edu.stanford.nlp.util.ArrayUtils;
import edu.stanford.nlp.util.ErasureUtils;
import edu.stanford.nlp.util.Generics;
import edu.stanford.nlp.util.HashIndex;
import edu.stanford.nlp.util.Index;
import edu.stanford.nlp.util.Pair;
import edu.stanford.nlp.util.ReflectionLoading;
import edu.stanford.nlp.util.ScoredComparator;
import edu.stanford.nlp.util.ScoredObject;
import edu.stanford.nlp.util.StringUtils;
import edu.stanford.nlp.util.Timing;
import edu.stanford.nlp.util.Triple;
import edu.stanford.nlp.util.concurrent.MulticoreWrapper;
import edu.stanford.nlp.util.concurrent.ThreadsafeProcessor;


/**
 * Overview and description available at
 * http://nlp.stanford.edu/software/srparser.shtml
 *
 * @author John Bauer
 */
public class ShiftReduceParser extends ParserGrammar implements Serializable {

  final Index<Transition> transitionIndex;

  final ShiftReduceOptions op;

  PerceptronModel model;

  Set<String> knownStates;
  Set<String> rootStates;
  Set<String> rootOnlyStates;

  public ShiftReduceParser(ShiftReduceOptions op) {
    this.transitionIndex = new HashIndex<Transition>();
    this.op = op;
    this.model = new PerceptronModel(this.op, this.transitionIndex);
  }

  public ShiftReduceParser(ShiftReduceParser other, PerceptronModel model) {
    this.transitionIndex = other.transitionIndex;
    this.op = other.op;
    this.knownStates = other.knownStates;
    this.rootStates = other.rootStates;
    this.rootOnlyStates = other.rootOnlyStates;

    this.model = model;
  }

  /*
  private void readObject(ObjectInputStream in)
    throws IOException, ClassNotFoundException
  {
    ObjectInputStream.GetField fields = in.readFields();
    transitionIndex = ErasureUtils.uncheckedCast(fields.get("transitionIndex", null));
    op = ErasureUtils.uncheckedCast(fields.get("op", null));
    featureFactory = ErasureUtils.uncheckedCast(fields.get("featureFactory", null));
    featureWeights = ErasureUtils.uncheckedCast(fields.get("featureWeights", null));
    knownStates = ErasureUtils.uncheckedCast(fields.get("knownStates", null));
    rootStates = ErasureUtils.uncheckedCast(fields.get("rootStates", null));
    if (rootStates == null) {
      rootStates = Collections.singleton("ROOT");
      System.err.println("Adding rootStates: " + rootStates);
    }
    rootOnlyStates = ErasureUtils.uncheckedCast(fields.get("rootOnlyStates", null));
    if (rootOnlyStates == null) {
      rootOnlyStates = Collections.singleton("ROOT");
      System.err.println("Adding rootOnlyStates: " + rootOnlyStates);
    }
  }
  */

  @Override
  public Options getOp() {
    return op;
  }

  @Override
  public TreebankLangParserParams getTLPParams() {
    return op.tlpParams;
  }

  @Override
  public TreebankLanguagePack treebankLanguagePack() {
    return getTLPParams().treebankLanguagePack();
  }

  private final static String[] BEAM_FLAGS = { "-beamSize", "4" };

  @Override
  public String[] defaultCoreNLPFlags() {
    if (op.trainOptions().beamSize > 1) {
      return ArrayUtils.concatenate(getTLPParams().defaultCoreNLPFlags(), BEAM_FLAGS);
    } else {
      // TODO: this may result in some options which are useless for
      // this model, such as -retainTmpSubcategories
      return getTLPParams().defaultCoreNLPFlags();
    }
  }

  @Override
  public boolean requiresTags() {
    return true;
  }

  @Override
  public ParserQuery parserQuery() {
    return new ShiftReduceParserQuery(this);
  }

  @Override
  public Tree parse(String sentence) {
    if (!getOp().testOptions.preTag) {
      throw new UnsupportedOperationException("Can only parse raw text if a tagger is specified, as the ShiftReduceParser cannot produce its own tags");
    }
    return super.parse(sentence);    
  }

  @Override
  public Tree parse(List<? extends HasWord> sentence) {
    ShiftReduceParserQuery pq = new ShiftReduceParserQuery(this);
    if (pq.parse(sentence)) {
      return pq.getBestParse();
    }
    return ParserUtils.xTree(sentence);
  }


  /** TODO: add an eval which measures transition accuracy? */
  @Override
  public List<Eval> getExtraEvals() {
    return Collections.emptyList();
  }

  @Override
  public List<ParserQueryEval> getParserQueryEvals() {
    if (op.testOptions().recordBinarized == null && op.testOptions().recordDebinarized == null) {
      return Collections.emptyList();
    }
    List<ParserQueryEval> evals = Generics.newArrayList();
    if (op.testOptions().recordBinarized != null) {
      evals.add(new TreeRecorder(TreeRecorder.Mode.BINARIZED, op.testOptions().recordBinarized));
    }
    if (op.testOptions().recordDebinarized != null) {
      evals.add(new TreeRecorder(TreeRecorder.Mode.DEBINARIZED, op.testOptions().recordDebinarized));
    }
    return evals;
  }

  /**
   * Returns a transition which might not even be part of the model,
   * but will hopefully allow progress in an otherwise stuck parse
   *
   * TODO: perhaps we want to create an EmergencyTransition class
   * which indicates that something has gone wrong
   */
  public Transition findEmergencyTransition(State state, List<ParserConstraint> constraints) {
    if (state.stack.size() == 0) {
      return null;
    }

    // See if there is a constraint whose boundaries match the end
    // points of the top node on the stack.  If so, we can apply a
    // UnaryTransition / CompoundUnaryTransition if that would solve
    // the constraint
    if (constraints != null) {
      final Tree top = state.stack.peek();
      for (ParserConstraint constraint : constraints) {
        if (ShiftReduceUtils.leftIndex(top) != constraint.start || ShiftReduceUtils.rightIndex(top) != constraint.end - 1) {
          continue;
        }
        if (ShiftReduceUtils.constraintMatchesTreeTop(top, constraint)) {
          continue;
        }
        // found an unmatched constraint that can be fixed with a unary transition
        // now we need to find a matching state for the transition
        for (String label : knownStates) {
          if (constraint.state.matcher(label).matches()) {
            return ((op.compoundUnaries) ?
                    new CompoundUnaryTransition(Collections.singletonList(label), false) :
                    new UnaryTransition(label, false));
          }
        }
      }
    }

    if (ShiftReduceUtils.isTemporary(state.stack.peek()) &&
        (state.stack.size() == 1 || ShiftReduceUtils.isTemporary(state.stack.pop().peek()))) {
      return ((op.compoundUnaries) ?
              new CompoundUnaryTransition(Collections.singletonList(state.stack.peek().value().substring(1)), false) :
              new UnaryTransition(state.stack.peek().value().substring(1), false));
    }

    if (state.stack.size() == 1 && state.tokenPosition >= state.sentence.size()) {
      // either need to finalize or transition to a root state
      if (!rootStates.contains(state.stack.peek().value())) {
        String root = rootStates.iterator().next();
        return ((op.compoundUnaries) ?
                new CompoundUnaryTransition(Collections.singletonList(root), false) :
                new UnaryTransition(root, false));
      }
    }

    if (state.stack.size() == 1) {
      return null;
    }

    if (ShiftReduceUtils.isTemporary(state.stack.peek())) {
      return new BinaryTransition(state.stack.peek().value().substring(1), BinaryTransition.Side.RIGHT);
    }

    if (ShiftReduceUtils.isTemporary(state.stack.pop().peek())) {
      return new BinaryTransition(state.stack.pop().peek().value().substring(1), BinaryTransition.Side.LEFT);
    }

    return null;
  }

  public static State initialStateFromGoldTagTree(Tree tree) {
    return initialStateFromTaggedSentence(tree.taggedYield());
  }

  public static State initialStateFromTaggedSentence(List<? extends HasWord> words) {
    List<Tree> preterminals = Generics.newArrayList();
    for (int index = 0; index < words.size(); ++index) {
      HasWord hw = words.get(index);

      CoreLabel wordLabel;
      String tag;
      if (hw instanceof CoreLabel) {
        wordLabel = (CoreLabel) hw;
        tag = wordLabel.tag();
        CoreLabel cl = (CoreLabel) hw;
      } else {
        wordLabel = new CoreLabel();
        wordLabel.setValue(hw.word());
        wordLabel.setWord(hw.word());
        if (!(hw instanceof HasTag)) {
          throw new IllegalArgumentException("Expected tagged words");
        }
        tag = ((HasTag) hw).tag();
        wordLabel.setTag(tag);
      }
      if (tag == null) {
        throw new IllegalArgumentException("Input word not tagged");
      }
      CoreLabel tagLabel = new CoreLabel();
      tagLabel.setValue(tag);

      // Index from 1.  Tools downstream from the parser expect that
      // Internally this parser uses the index, so we have to
      // overwrite incorrect indices if the label is already indexed
      wordLabel.setIndex(index + 1);
      tagLabel.setIndex(index + 1);

      LabeledScoredTreeNode wordNode = new LabeledScoredTreeNode(wordLabel);
      LabeledScoredTreeNode tagNode = new LabeledScoredTreeNode(tagLabel);
      tagNode.addChild(wordNode);

      wordLabel.set(TreeCoreAnnotations.HeadWordAnnotation.class, wordNode);
      wordLabel.set(TreeCoreAnnotations.HeadTagAnnotation.class, tagNode);
      tagLabel.set(TreeCoreAnnotations.HeadWordAnnotation.class, wordNode);
      tagLabel.set(TreeCoreAnnotations.HeadTagAnnotation.class, tagNode);

      preterminals.add(tagNode);
    }
    return new State(preterminals);
  }

  public static ShiftReduceOptions buildTrainingOptions(String tlppClass, String[] args) {
    ShiftReduceOptions op = new ShiftReduceOptions();
    op.setOptions("-forceTags", "-debugOutputFrequency", "1", "-quietEvaluation");
    if (tlppClass != null) {
      op.tlpParams = ReflectionLoading.loadByReflection(tlppClass);
    }
    op.setOptions(args);

    if (op.trainOptions.randomSeed == 0) {
      op.trainOptions.randomSeed = (new Random()).nextLong();
      System.err.println("Random seed not set by options, using " + op.trainOptions.randomSeed);
    }
    return op;
  }

  public Treebank readTreebank(String treebankPath, FileFilter treebankFilter) {
    System.err.println("Loading trees from " + treebankPath);
    Treebank treebank = op.tlpParams.memoryTreebank();
    treebank.loadPath(treebankPath, treebankFilter);
    System.err.println("Read in " + treebank.size() + " trees from " + treebankPath);
    return treebank;
  }

  public List<Tree> readBinarizedTreebank(String treebankPath, FileFilter treebankFilter) {
    Treebank treebank = readTreebank(treebankPath, treebankFilter);
    List<Tree> binarized = binarizeTreebank(treebank, op);
    System.err.println("Converted trees to binarized format");
    return binarized;
  }

  public static List<Tree> binarizeTreebank(Treebank treebank, Options op) {
    TreeBinarizer binarizer = TreeBinarizer.simpleTreeBinarizer(op.tlpParams.headFinder(), op.tlpParams.treebankLanguagePack());
    BasicCategoryTreeTransformer basicTransformer = new BasicCategoryTreeTransformer(op.langpack());
    CompositeTreeTransformer transformer = new CompositeTreeTransformer();
    transformer.addTransformer(binarizer);
    transformer.addTransformer(basicTransformer);

    treebank = treebank.transform(transformer);

    HeadFinder binaryHeadFinder = new BinaryHeadFinder(op.tlpParams.headFinder());
    List<Tree> binarizedTrees = Generics.newArrayList();
    for (Tree tree : treebank) {
      Trees.convertToCoreLabels(tree);
      tree.percolateHeadAnnotations(binaryHeadFinder);
      // Index from 1.  Tools downstream expect index from 1, so for
      // uses internal to the srparser we have to renormalize the
      // indices, with the result that here we have to index from 1
      tree.indexLeaves(1, true);
      binarizedTrees.add(tree);
    }
    return binarizedTrees;
  }

  public static Set<String> findKnownStates(List<Tree> binarizedTrees) {
    Set<String> knownStates = Generics.newHashSet();
    for (Tree tree : binarizedTrees) {
      findKnownStates(tree, knownStates);
    }
    return Collections.unmodifiableSet(knownStates);
  }

  public static void findKnownStates(Tree tree, Set<String> knownStates) {
    if (tree.isLeaf() || tree.isPreTerminal()) {
      return;
    }
    if (!ShiftReduceUtils.isTemporary(tree)) {
      knownStates.add(tree.value());
    }
    for (Tree child : tree.children()) {
      findKnownStates(child, knownStates);
    }
  }


  // TODO: factor out the retagging?
  public static void redoTags(Tree tree, Tagger tagger) {
    List<Word> words = tree.yieldWords();
    List<TaggedWord> tagged = tagger.apply(words);
    List<Label> tags = tree.preTerminalYield();
    if (tags.size() != tagged.size()) {
      throw new AssertionError("Tags are not the same size");
    }
    for (int i = 0; i < tags.size(); ++i) {
      tags.get(i).setValue(tagged.get(i).tag());
    }
  }

  private static class RetagProcessor implements ThreadsafeProcessor<Tree, Tree> {
    Tagger tagger;

    public RetagProcessor(Tagger tagger) {
      this.tagger = tagger;
    }

    public Tree process(Tree tree) {
      redoTags(tree, tagger);
      return tree;
    }

    public RetagProcessor newInstance() {
      // already threadsafe
      return this;
    }
  }

  public static void redoTags(List<Tree> trees, Tagger tagger, int nThreads) {
    if (nThreads == 1) {
      for (Tree tree : trees) {
        redoTags(tree, tagger);
      }
    } else {
      MulticoreWrapper<Tree, Tree> wrapper = new MulticoreWrapper<Tree, Tree>(nThreads, new RetagProcessor(tagger));
      for (Tree tree : trees) {
        wrapper.put(tree);
      }
      wrapper.join();
      // trees are changed in place
    }
  }

  private static final NumberFormat NF = new DecimalFormat("0.00");
  private static final NumberFormat FILENAME = new DecimalFormat("0000");

  private static class Update {
    final List<String> features;
    final int goldTransition;
    final int predictedTransition;
    final float delta;

    Update(List<String> features, int goldTransition, int predictedTransition, float delta) {
      this.features = features;
      this.goldTransition = goldTransition;
      this.predictedTransition = predictedTransition;
      this.delta = delta;
    }
  }

  private static boolean findStateOnAgenda(Collection<State> agenda, State state) {
    for (State other : agenda) {
      if (other.areTransitionsEqual(state)) {
        return true;
      }
    }
    return false;
  }

  private Pair<Integer, Integer> trainTree(int index, List<Tree> binarizedTrees, List<List<Transition>> transitionLists, List<Update> updates, Oracle oracle) {
    int numCorrect = 0;
    int numWrong = 0;

    Tree tree = binarizedTrees.get(index);

    ReorderingOracle reorderer = null;
    if (op.trainOptions().trainingMethod == ShiftReduceTrainOptions.TrainingMethod.REORDER_ORACLE ||
        op.trainOptions().trainingMethod == ShiftReduceTrainOptions.TrainingMethod.REORDER_BEAM) {
      reorderer = new ReorderingOracle(op);
    }

    // TODO.  This training method seems to be working in that it
    // trains models just like the gold and early termination methods do.
    // However, it causes the feature space to go crazy.  Presumably
    // leaving out features with low weights or low frequencies would
    // significantly help with that.  Otherwise, not sure how to keep
    // it under control.
    if (op.trainOptions().trainingMethod == ShiftReduceTrainOptions.TrainingMethod.ORACLE) {
      State state = ShiftReduceParser.initialStateFromGoldTagTree(tree);
      while (!state.isFinished()) {
        List<String> features = model.featureFactory.featurize(state);
        ScoredObject<Integer> prediction = model.findHighestScoringTransition(state, features, true);
        if (prediction == null) {
          throw new AssertionError("Did not find a legal transition");
        }
        int predictedNum = prediction.object();
        Transition predicted = transitionIndex.get(predictedNum);
        OracleTransition gold = oracle.goldTransition(index, state);
        if (gold.isCorrect(predicted)) {
          numCorrect++;
          if (gold.transition != null && !gold.transition.equals(predicted)) {
            int transitionNum = transitionIndex.indexOf(gold.transition);
            if (transitionNum < 0) {
              // TODO: do we want to add unary transitions which are
              // only possible when the parser has gone off the rails?
              continue;
            }
            updates.add(new Update(features, transitionNum, -1, 1.0f));
          }
        } else {
          numWrong++;
          int transitionNum = -1;
          if (gold.transition != null) {
            transitionNum = transitionIndex.indexOf(gold.transition);
            // TODO: this can theoretically result in a -1 gold
            // transition if the transition exists, but is a
            // CompoundUnaryTransition which only exists because the
            // parser is wrong.  Do we want to add those transitions?
          }
          updates.add(new Update(features, transitionNum, predictedNum, 1.0f));
        }
        state = predicted.apply(state);
      }
    } else if (op.trainOptions().trainingMethod == ShiftReduceTrainOptions.TrainingMethod.BEAM ||
               op.trainOptions().trainingMethod == ShiftReduceTrainOptions.TrainingMethod.REORDER_BEAM) {
      if (op.trainOptions().beamSize <= 0) {
        throw new IllegalArgumentException("Illegal beam size " + op.trainOptions().beamSize);
      }
      List<Transition> transitions = Generics.newLinkedList(transitionLists.get(index));
      PriorityQueue<State> agenda = new PriorityQueue<State>(op.trainOptions().beamSize + 1, ScoredComparator.ASCENDING_COMPARATOR);
      State goldState = ShiftReduceParser.initialStateFromGoldTagTree(tree);
      agenda.add(goldState);
      int transitionCount = 0;
      while (transitions.size() > 0) {
        Transition goldTransition = transitions.get(0);
        Transition highestScoringTransitionFromGoldState = null;
        double highestScoreFromGoldState = 0.0;
        PriorityQueue<State> newAgenda = new PriorityQueue<State>(op.trainOptions().beamSize + 1, ScoredComparator.ASCENDING_COMPARATOR);
        State highestScoringState = null;
        State highestCurrentState = null;
        for (State currentState : agenda) {
          boolean isGoldState = (op.trainOptions().trainingMethod == ShiftReduceTrainOptions.TrainingMethod.REORDER_BEAM &&
                                 goldState.areTransitionsEqual(currentState));

          List<String> features = model.featureFactory.featurize(currentState);
          Collection<ScoredObject<Integer>> stateTransitions = model.findHighestScoringTransitions(currentState, features, true, op.trainOptions().beamSize, null);
          for (ScoredObject<Integer> transition : stateTransitions) {
            State newState = transitionIndex.get(transition.object()).apply(currentState, transition.score());
            newAgenda.add(newState);
            if (newAgenda.size() > op.trainOptions().beamSize) {
              newAgenda.poll();
            }
            if (highestScoringState == null || highestScoringState.score() < newState.score()) {
              highestScoringState = newState;
              highestCurrentState = currentState;
            }
            if (isGoldState &&
                (highestScoringTransitionFromGoldState == null || transition.score() > highestScoreFromGoldState)) {
              highestScoringTransitionFromGoldState = transitionIndex.get(transition.object());
              highestScoreFromGoldState = transition.score();
            }
          }
        }

        // This can happen if the REORDER_BEAM method backs itself
        // into a corner, such as transitioning to something that
        // can't have a FinalizeTransition applied.  This doesn't
        // happen for the BEAM method because in that case the correct
        // state (eg one with ROOT) isn't on the agenda so it stops.
        if (op.trainOptions().trainingMethod == ShiftReduceTrainOptions.TrainingMethod.REORDER_BEAM && highestScoringTransitionFromGoldState == null) {
          break;
        }

        State newGoldState = goldTransition.apply(goldState, 0.0);

        // if highest scoring state used the correct transition, no training
        // otherwise, down the last transition, up the correct
        if (!newGoldState.areTransitionsEqual(highestScoringState)) {
          ++numWrong;
          List<String> goldFeatures = model.featureFactory.featurize(goldState);
          int lastTransition = transitionIndex.indexOf(highestScoringState.transitions.peek());
          updates.add(new Update(model.featureFactory.featurize(highestCurrentState), -1, lastTransition, 1.0f));
          updates.add(new Update(goldFeatures, transitionIndex.indexOf(goldTransition), -1, 1.0f));

          if (op.trainOptions().trainingMethod == ShiftReduceTrainOptions.TrainingMethod.BEAM) {
            // If the correct state has fallen off the agenda, break
            if (!findStateOnAgenda(newAgenda, newGoldState)) {
              break;
            } else {
              transitions.remove(0);
            }
          } else if (op.trainOptions().trainingMethod == ShiftReduceTrainOptions.TrainingMethod.REORDER_BEAM) {
            if (!findStateOnAgenda(newAgenda, newGoldState)) {
              if (!reorderer.reorder(goldState, highestScoringTransitionFromGoldState, transitions)) {
                break;
              }
              newGoldState = highestScoringTransitionFromGoldState.apply(goldState);
              if (!findStateOnAgenda(newAgenda, newGoldState)) {
                break;
              }
            } else {
              transitions.remove(0);
            }
          }
        } else {
          ++numCorrect;
          transitions.remove(0);
        }

        goldState = newGoldState;
        agenda = newAgenda;
      }
    } else if (op.trainOptions().trainingMethod == ShiftReduceTrainOptions.TrainingMethod.REORDER_ORACLE ||
               op.trainOptions().trainingMethod == ShiftReduceTrainOptions.TrainingMethod.EARLY_TERMINATION ||
               op.trainOptions().trainingMethod == ShiftReduceTrainOptions.TrainingMethod.GOLD) {
      State state = ShiftReduceParser.initialStateFromGoldTagTree(tree);
      List<Transition> transitions = transitionLists.get(index);
      transitions = Generics.newLinkedList(transitions);
      boolean keepGoing = true;
      while (transitions.size() > 0 && keepGoing) {
        Transition transition = transitions.get(0);
        int transitionNum = transitionIndex.indexOf(transition);
        List<String> features = model.featureFactory.featurize(state);
        int predictedNum = model.findHighestScoringTransition(state, features, false).object();
        Transition predicted = transitionIndex.get(predictedNum);
        if (transitionNum == predictedNum) {
          transitions.remove(0);
          state = transition.apply(state);
          numCorrect++;
        } else {
          numWrong++;
          // TODO: allow weighted features, weighted training, etc
          updates.add(new Update(features, transitionNum, predictedNum, 1.0f));
          switch (op.trainOptions().trainingMethod) {
          case EARLY_TERMINATION:
            keepGoing = false;
            break;
          case GOLD:
            transitions.remove(0);
            state = transition.apply(state);
            break;
          case REORDER_ORACLE:
            keepGoing = reorderer.reorder(state, predicted, transitions);
            if (keepGoing) {
              state = predicted.apply(state);
            }
            break;
          default:
            throw new IllegalArgumentException("Unexpected method " + op.trainOptions().trainingMethod);
          }
        }
      }
    }

    return Pair.makePair(numCorrect, numWrong);
  }

  private class TrainTreeProcessor implements ThreadsafeProcessor<Integer, Pair<Integer, Integer>> {
    List<Tree> binarizedTrees;
    List<List<Transition>> transitionLists;
    List<Update> updates; // this needs to be a synchronized list
    Oracle oracle;

    public TrainTreeProcessor(List<Tree> binarizedTrees, List<List<Transition>> transitionLists, List<Update> updates, Oracle oracle) {
      this.binarizedTrees = binarizedTrees;
      this.transitionLists = transitionLists;
      this.updates = updates;
      this.oracle = oracle;
    }

    public Pair<Integer, Integer> process(Integer index) {
      return trainTree(index, binarizedTrees, transitionLists, updates, oracle);
    }

    public TrainTreeProcessor newInstance() {
      // already threadsafe
      return this;
    }
  }

  /**
   * Trains a batch of trees and returns the following: a list of
   * Update objects, the number of transitions correct, and the number
   * of transitions wrong.
   * <br>
   * If the model is trained with multiple threads, it is expected
   * that a valid MulticoreWrapper is passed in which does the
   * processing.  In that case, the processing is done on all of the
   * trees without updating any weights, which allows the results for
   * multithreaded training to be reproduced.
   */
  private Triple<List<Update>, Integer, Integer> trainBatch(List<Integer> indices, List<Tree> binarizedTrees, List<List<Transition>> transitionLists, List<Update> updates, Oracle oracle, MulticoreWrapper<Integer, Pair<Integer, Integer>> wrapper) {
    int numCorrect = 0;
    int numWrong = 0;
    if (op.trainOptions.trainingThreads == 1) {
      for (Integer index : indices) {
        Pair<Integer, Integer> count = trainTree(index, binarizedTrees, transitionLists, updates, oracle);
        numCorrect += count.first;
        numWrong += count.second;
      }
    } else {
      for (Integer index : indices) {
        wrapper.put(index);
      }
      wrapper.join(false);
      while (wrapper.peek()) {
        Pair<Integer, Integer> result = wrapper.poll();
        numCorrect += result.first;
        numWrong += result.second;
      }
    }
    return new Triple<List<Update>, Integer, Integer>(updates, numCorrect, numWrong);
  }

  /**
   * Get all of the states which occur at the root, even if they occur
   * elsewhere in the tree.  Useful for knowing when you can Finalize
   * a tree
   */
  private static Set<String> findRootStates(List<Tree> trees) {
    Set<String> roots = Generics.newHashSet();
    for (Tree tree : trees) {
      roots.add(tree.value());
    }
    return Collections.unmodifiableSet(roots);
  }

  /**
   * Get all of the states which *only* occur at the root.  Useful for
   * knowing which transitions can't be done internal to the tree
   */
  private static Set<String> findRootOnlyStates(List<Tree> trees, Set<String> rootStates) {
    Set<String> rootOnlyStates = Generics.newHashSet(rootStates);
    for (Tree tree : trees) {
      for (Tree child : tree.children()) {
        findRootOnlyStatesHelper(child, rootStates, rootOnlyStates);
      }
    }
    return Collections.unmodifiableSet(rootOnlyStates);
  }

  private static void findRootOnlyStatesHelper(Tree tree, Set<String> rootStates, Set<String> rootOnlyStates) {
    rootOnlyStates.remove(tree.value());
    for (Tree child : tree.children()) {
      findRootOnlyStatesHelper(child, rootStates, rootOnlyStates);
    }
  }

  private void trainModel(String serializedPath, Tagger tagger, Random random, List<Tree> binarizedTrees, List<List<Transition>> transitionLists, Treebank devTreebank, int nThreads, Set<String> allowedFeatures) {
    double bestScore = 0.0;
    int bestIteration = 0;
    PriorityQueue<ScoredObject<PerceptronModel>> bestModels = null;
    if (op.trainOptions().averagedModels > 0) {
      bestModels = new PriorityQueue<ScoredObject<PerceptronModel>>(op.trainOptions().averagedModels + 1, ScoredComparator.ASCENDING_COMPARATOR);
    }

    List<Integer> indices = Generics.newArrayList();
    for (int i = 0; i < binarizedTrees.size(); ++i) {
      indices.add(i);
    }

    Oracle oracle = null;
    if (op.trainOptions().trainingMethod == ShiftReduceTrainOptions.TrainingMethod.ORACLE) {
      oracle = new Oracle(binarizedTrees, op.compoundUnaries, rootStates);
    }

    List<Update> updates = Generics.newArrayList();
    MulticoreWrapper<Integer, Pair<Integer, Integer>> wrapper = null;
    if (nThreads != 1) {
      updates = Collections.synchronizedList(updates);
      wrapper = new MulticoreWrapper<Integer, Pair<Integer, Integer>>(op.trainOptions.trainingThreads, new TrainTreeProcessor(binarizedTrees, transitionLists, updates, oracle));
    }

    IntCounter<String> featureFrequencies = null;
    if (op.trainOptions().featureFrequencyCutoff > 1) {
      featureFrequencies = new IntCounter<String>();
    }

    for (int iteration = 1; iteration <= op.trainOptions.trainingIterations; ++iteration) {
      Timing trainingTimer = new Timing();
      int numCorrect = 0;
      int numWrong = 0;
      Collections.shuffle(indices, random);
      for (int start = 0; start < indices.size(); start += op.trainOptions.batchSize) {
        int end = Math.min(start + op.trainOptions.batchSize, indices.size());
        Triple<List<Update>, Integer, Integer> result = trainBatch(indices.subList(start, end), binarizedTrees, transitionLists, updates, oracle, wrapper);

        numCorrect += result.second;
        numWrong += result.third;

        for (Update update : result.first) {
          for (String feature : update.features) {
            if (allowedFeatures != null && !allowedFeatures.contains(feature)) {
              continue;
            }
            Weight weights = model.featureWeights.get(feature);
            if (weights == null) {
              weights = new Weight();
              model.featureWeights.put(feature, weights);
            }
            weights.updateWeight(update.goldTransition, update.delta);
            weights.updateWeight(update.predictedTransition, -update.delta);

            if (featureFrequencies != null) {
              featureFrequencies.incrementCount(feature, (update.goldTransition >= 0 && update.predictedTransition >= 0) ? 2 : 1);
            }
          }
        }
        updates.clear();
      }
      trainingTimer.done("Iteration " + iteration);
      System.err.println("While training, got " + numCorrect + " transitions correct and " + numWrong + " transitions wrong");
      model.outputStats();


      double labelF1 = 0.0;
      if (devTreebank != null) {
        EvaluateTreebank evaluator = new EvaluateTreebank(op, null, this, tagger);
        evaluator.testOnTreebank(devTreebank);
        labelF1 = evaluator.getLBScore();
        System.err.println("Label F1 after " + iteration + " iterations: " + labelF1);

        if (labelF1 > bestScore) {
          System.err.println("New best dev score (previous best " + bestScore + ")");
          bestScore = labelF1;
          bestIteration = iteration;
        } else {
          System.err.println("Failed to improve for " + (iteration - bestIteration) + " iteration(s) on previous best score of " + bestScore);
          if (op.trainOptions.stalledIterationLimit > 0 && (iteration - bestIteration >= op.trainOptions.stalledIterationLimit)) {
            System.err.println("Failed to improve for too long, stopping training");
            break;
          }
        }
        System.err.println();

        if (bestModels != null) {
          bestModels.add(new ScoredObject<PerceptronModel>(new PerceptronModel(model), labelF1));
          if (bestModels.size() > op.trainOptions().averagedModels) {
            bestModels.poll();
          }
        }
      }
      if (op.trainOptions().saveIntermediateModels && serializedPath != null && op.trainOptions.debugOutputFrequency > 0) {
        String tempName = serializedPath.substring(0, serializedPath.length() - 7) + "-" + FILENAME.format(iteration) + "-" + NF.format(labelF1) + ".ser.gz";
        saveModel(tempName);
        // TODO: we could save a cutoff version of the model,
        // especially if we also get a dev set number for it, but that
        // might be overkill
      }
    }

    if (wrapper != null) {
      wrapper.join();
    }

    if (bestModels != null) {
      if (op.trainOptions().cvAveragedModels && devTreebank != null) {
        List<ScoredObject<PerceptronModel>> models = Generics.newArrayList();
        while (bestModels.size() > 0) {
          models.add(bestModels.poll());
        }
        Collections.reverse(models);
        double bestF1 = 0.0;
        int bestSize = 0;
        for (int i = 1; i <= models.size(); ++i) {
          System.err.println("Testing with " + i + " models averaged together");
          PerceptronModel model = PerceptronModel.averageScoredModels(models.subList(0, i));
          ShiftReduceParser temp = new ShiftReduceParser(this, model);
          EvaluateTreebank evaluator = new EvaluateTreebank(temp.op, null, temp, tagger);
          evaluator.testOnTreebank(devTreebank);
          double labelF1 = evaluator.getLBScore();
          System.err.println("Label F1 for " + i + " models: " + labelF1);
          if (labelF1 > bestF1) {
            bestF1 = labelF1;
            bestSize = i;
          }
        }
        model = PerceptronModel.averageScoredModels(models.subList(0, bestSize));
      } else {
        model = PerceptronModel.averageScoredModels(bestModels);
      }
    }

    // TODO: perhaps we should filter the features and then get dev
    // set scores.  That way we can merge the models which are best
    // after filtering.
    if (featureFrequencies != null) {
      model.filterFeatures(featureFrequencies.keysAbove(op.trainOptions().featureFrequencyCutoff));
    }

    model.condenseFeatures();
  }

  private void train(List<Pair<String, FileFilter>> trainTreebankPath,
                     Pair<String, FileFilter> devTreebankPath,
                     String serializedPath, Set<String> allowedFeatures) {
    System.err.println("Training method: " + op.trainOptions().trainingMethod);

    List<Tree> binarizedTrees = Generics.newArrayList();
    for (Pair<String, FileFilter> treebank : trainTreebankPath) {
      binarizedTrees.addAll(readBinarizedTreebank(treebank.first(), treebank.second()));
    }

    int nThreads = op.trainOptions.trainingThreads;
    nThreads = nThreads <= 0 ? Runtime.getRuntime().availableProcessors() : nThreads;

    Tagger tagger = null;
    if (op.testOptions.preTag) {
      Timing retagTimer = new Timing();
      tagger = Tagger.loadModel(op.testOptions.taggerSerializedFile);
      redoTags(binarizedTrees, tagger, nThreads);
      retagTimer.done("Retagging");
    }

    knownStates = findKnownStates(binarizedTrees);
    rootStates = findRootStates(binarizedTrees);
    rootOnlyStates = findRootOnlyStates(binarizedTrees, rootStates);

    System.err.println("Known states: " + knownStates);
    System.err.println("States which occur at the root: " + rootStates);
    System.err.println("States which only occur at the root: " + rootStates);

    Timing transitionTimer = new Timing();
    List<List<Transition>> transitionLists = CreateTransitionSequence.createTransitionSequences(binarizedTrees, op.compoundUnaries, rootStates, rootOnlyStates);
    for (List<Transition> transitions : transitionLists) {
      transitionIndex.addAll(transitions);
    }
    transitionTimer.done("Converting trees into transition lists");
    System.err.println("Number of transitions: " + transitionIndex.size());

    Random random = new Random(op.trainOptions.randomSeed);

    Treebank devTreebank = null;
    if (devTreebankPath != null) {
      devTreebank = readTreebank(devTreebankPath.first(), devTreebankPath.second());
    }

    trainModel(serializedPath, tagger, random, binarizedTrees, transitionLists, devTreebank, nThreads, allowedFeatures);
  }

  public void setOptionFlags(String ... flags) {
    op.setOptions(flags);
  }

  public static ShiftReduceParser loadModel(String path, String ... extraFlags) {
    ShiftReduceParser parser = null;
    try {
      Timing timing = new Timing();
      System.err.print("Loading parser from serialized file " + path + " ...");
      parser = IOUtils.readObjectFromURLOrClasspathOrFileSystem(path);
      timing.done();
    } catch (IOException e) {
      throw new RuntimeIOException(e);
    } catch (ClassNotFoundException e) {
      throw new RuntimeIOException(e);
    }
    if (extraFlags.length > 0) {
      parser.setOptionFlags(extraFlags);
    }
    return parser;
  }

  public void saveModel(String path) {
    try {
      IOUtils.writeObjectToFile(this, path);
    } catch (IOException e) {
      throw new RuntimeIOException(e);
    }
  }

  static final String[] FORCE_TAGS = { "-forceTags" };

  public static void main(String[] args) {
    List<String> remainingArgs = Generics.newArrayList();

    List<Pair<String, FileFilter>> trainTreebankPath = null;
    Pair<String, FileFilter> testTreebankPath = null;
    Pair<String, FileFilter> devTreebankPath = null;

    String serializedPath = null;

    String tlppClass = null;

    String continueTraining = null;

    for (int argIndex = 0; argIndex < args.length; ) {
      if (args[argIndex].equalsIgnoreCase("-trainTreebank")) {
        if (trainTreebankPath == null) {
          trainTreebankPath = Generics.newArrayList();
        }
        trainTreebankPath.add(ArgUtils.getTreebankDescription(args, argIndex, "-trainTreebank"));
        argIndex = argIndex + ArgUtils.numSubArgs(args, argIndex) + 1;
      } else if (args[argIndex].equalsIgnoreCase("-testTreebank")) {
        testTreebankPath = ArgUtils.getTreebankDescription(args, argIndex, "-testTreebank");
        argIndex = argIndex + ArgUtils.numSubArgs(args, argIndex) + 1;
      } else if (args[argIndex].equalsIgnoreCase("-devTreebank")) {
        devTreebankPath = ArgUtils.getTreebankDescription(args, argIndex, "-devTreebank");
        argIndex = argIndex + ArgUtils.numSubArgs(args, argIndex) + 1;
      } else if (args[argIndex].equalsIgnoreCase("-serializedPath") || args[argIndex].equalsIgnoreCase("-model")) {
        serializedPath = args[argIndex + 1];
        argIndex += 2;
      } else if (args[argIndex].equalsIgnoreCase("-tlpp")) {
        tlppClass = args[argIndex + 1];
        argIndex += 2;
      } else if (args[argIndex].equalsIgnoreCase("-continueTraining")) {
        continueTraining = args[argIndex + 1];
        argIndex += 2;
      } else {
        remainingArgs.add(args[argIndex]);
        ++argIndex;
      }
    }

    String[] newArgs = new String[remainingArgs.size()];
    newArgs = remainingArgs.toArray(newArgs);

    if (trainTreebankPath == null && serializedPath == null) {
      throw new IllegalArgumentException("Must specify a treebank to train from with -trainTreebank or a parser to load with -serializedPath");
    }

    ShiftReduceParser parser = null;

    if (trainTreebankPath != null) {
      System.err.println("Training ShiftReduceParser");
      System.err.println("Initial arguments:");
      System.err.println("   " + StringUtils.join(args));
      if (continueTraining != null) {
        parser = ShiftReduceParser.loadModel(continueTraining, ArrayUtils.concatenate(FORCE_TAGS, newArgs));
      } else {
        ShiftReduceOptions op = buildTrainingOptions(tlppClass, newArgs);
        parser = new ShiftReduceParser(op);
      }
      ShiftReduceOptions op = parser.op;
      if (op.trainOptions().retrainAfterCutoff && op.trainOptions().featureFrequencyCutoff > 0) {
        // TODO: factor out some of the treebank loading
        String tempName = serializedPath.substring(0, serializedPath.length() - 7) + "-" + "temp.ser.gz";
        parser.train(trainTreebankPath, devTreebankPath, tempName, null);
        parser.saveModel(tempName);
        Set<String> features = parser.model.featureWeights.keySet();
        parser = new ShiftReduceParser(op);
        parser.train(trainTreebankPath, devTreebankPath, serializedPath, features);
      } else {
        parser.train(trainTreebankPath, devTreebankPath, serializedPath, null);
      }
      parser.saveModel(serializedPath);
    }

    if (serializedPath != null && parser == null) {
      parser = ShiftReduceParser.loadModel(serializedPath, ArrayUtils.concatenate(FORCE_TAGS, newArgs));
    }

    //parser.outputStats();

    if (testTreebankPath != null) {
      System.err.println("Loading test trees from " + testTreebankPath.first());
      Treebank testTreebank = parser.op.tlpParams.memoryTreebank();
      testTreebank.loadPath(testTreebankPath.first(), testTreebankPath.second());
      System.err.println("Loaded " + testTreebank.size() + " trees");

      EvaluateTreebank evaluator = new EvaluateTreebank(parser.op, null, parser);
      evaluator.testOnTreebank(testTreebank);

      // System.err.println("Input tree: " + tree);
      // System.err.println("Debinarized tree: " + query.getBestParse());
      // System.err.println("Parsed binarized tree: " + query.getBestBinarizedParse());
      // System.err.println("Predicted transition sequence: " + query.getBestTransitionSequence());
    }
  }


  private static final long serialVersionUID = 1;
}

