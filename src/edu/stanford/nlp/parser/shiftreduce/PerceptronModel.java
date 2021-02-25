package edu.stanford.nlp.parser.shiftreduce;

import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Random;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import edu.stanford.nlp.parser.common.ParserConstraint;
import edu.stanford.nlp.parser.metrics.EvaluateTreebank;
import edu.stanford.nlp.stats.Counters;
import edu.stanford.nlp.stats.IntCounter;
import edu.stanford.nlp.stats.TwoDimensionalIntCounter;
import edu.stanford.nlp.tagger.common.Tagger;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.Treebank;
import edu.stanford.nlp.util.CollectionUtils;
import edu.stanford.nlp.util.Generics;
import edu.stanford.nlp.util.HashIndex;
import edu.stanford.nlp.util.Index;
import edu.stanford.nlp.util.Pair;
import edu.stanford.nlp.util.ReflectionLoading;
import edu.stanford.nlp.util.ScoredComparator;
import edu.stanford.nlp.util.ScoredObject;
import edu.stanford.nlp.util.StringUtils;
import edu.stanford.nlp.util.Timing;
import edu.stanford.nlp.util.concurrent.MulticoreWrapper;
import edu.stanford.nlp.util.concurrent.ThreadsafeProcessor;
import edu.stanford.nlp.util.logging.Redwood;


public class PerceptronModel extends BaseModel  {

  /** A logger for this class */
  private static final Redwood.RedwoodChannels log = Redwood.channels(PerceptronModel.class); // Serializable

  private float learningRate = 1.0f;

  WeightMap featureWeights;
  final FeatureFactory featureFactory;

  public PerceptronModel(ShiftReduceOptions op, Index<Transition> transitionIndex,
                         Set<String> knownStates, Set<String> rootStates, Set<String> rootOnlyStates) {
    super(op, transitionIndex, knownStates, rootStates, rootOnlyStates);
    this.featureWeights = new WeightMap();

    String[] classes = op.featureFactoryClass.split(";");
    if (classes.length == 1) {
      this.featureFactory = ReflectionLoading.loadByReflection(classes[0]);
    } else {
      FeatureFactory[] factories = new FeatureFactory[classes.length];
      for (int i = 0; i < classes.length; ++i) {
        int paren = classes[i].indexOf('(');
        if (paren >= 0) {
          String arg = classes[i].substring(paren + 1, classes[i].length() - 1);
          factories[i] = ReflectionLoading.loadByReflection(classes[i].substring(0, paren), arg);
        } else {
          factories[i] = ReflectionLoading.loadByReflection(classes[i]);
        }
      }
      this.featureFactory = new CombinationFeatureFactory(factories);
    }
  }

  public PerceptronModel(PerceptronModel other) {
    super(other);
    this.featureFactory = other.featureFactory;

    this.featureWeights = new WeightMap();
    for (String feature : other.featureWeights.keySet()) {
      featureWeights.put(feature, new Weight(other.featureWeights.get(feature)));
    }
  }

  private static final NumberFormat NF = new DecimalFormat("0.00");
  private static final NumberFormat FILENAME = new DecimalFormat("0000");

  public void averageScoredModels(Collection<ScoredObject<PerceptronModel>> scoredModels) {
    if (scoredModels.isEmpty()) {
      throw new IllegalArgumentException("Cannot average empty models");
    }

    log.info("Averaging " + scoredModels.size() + " models with scores");
    for (ScoredObject<PerceptronModel> model : scoredModels) {
      log.info(" " + NF.format(model.score()));
    }
    log.info();

    List<PerceptronModel> models = CollectionUtils.transformAsList(scoredModels, ScoredObject::object);
    averageModels(models);
  }

  public void averageModels(Collection<PerceptronModel> models) {
    if (models.isEmpty()) {
      throw new IllegalArgumentException("Cannot average empty models");
    }

    Set<String> features = Generics.newHashSet();
    for (PerceptronModel model : models) {
      for (String feature : model.featureWeights.keySet()) {
        features.add(feature);
      }
    }

    featureWeights = new WeightMap();
    for (String feature : features) {
      featureWeights.put(feature, new Weight());
    }

    int numModels = models.size();
    for (String feature : features) {
      for (PerceptronModel model : models) {
        if (!model.featureWeights.containsKey(feature)) {
          continue;
        }
        featureWeights.get(feature).addScaled(model.featureWeights.get(feature), 1.0f / numModels);
      }
    }
  }

  /**
   * Iterate over the feature weight map.
   * For each feature, remove all transitions with score of 0.
   * Any feature with no transitions left is then removed
   */
  private void condenseFeatures() {
    Iterator<String> featureIt = featureWeights.keySet().iterator();
    while (featureIt.hasNext()) {
      String feature = featureIt.next();
      Weight weights = featureWeights.get(feature);
      weights.condense();
      if (weights.size() == 0) {
        featureIt.remove();
      }
    }
  }

  private void filterFeatures(Set<String> keep) {
    Iterator<String> featureIt = featureWeights.keySet().iterator();
    while (featureIt.hasNext()) {
      if (!keep.contains(featureIt.next())) {
        featureIt.remove();
      }
    }
  }

  public int numWeights() {
    int numWeights = 0;
    for (Map.Entry<String, Weight> stringWeightEntry : featureWeights.entrySet()) {
      numWeights += stringWeightEntry.getValue().size();
    }
    return numWeights;
  }

  public float maxAbs() {
    float maxAbs = 0.0f;
    for (Map.Entry<String, Weight> weight : featureWeights.entrySet()) {
      maxAbs = Math.max(maxAbs, weight.getValue().maxAbs());
    }
    return maxAbs;
  }

  /**
   * Output some random facts about the model and the training iteration
   */
  public void outputStats(TrainingResult result) {
    log.info("While training, got " + result.numCorrect + " transitions correct and " + result.numWrong + " transitions wrong");
    log.info("Number of known features: " + featureWeights.size());

    log.info("Number of non-zero weights: " + numWeights());
    log.info("Weight values maxAbs: " + maxAbs());

    int wordLength = 0;
    for (String feature : featureWeights.keySet()) {
      wordLength += feature.length();
    }
    log.info("Total word length: " + wordLength);

    log.info("Number of transitions: " + transitionIndex.size());

    IntCounter<Pair<Integer, Integer>> firstErrors = new IntCounter<>();
    firstErrors.addAll(result.firstErrors);

    outputFirstErrors(firstErrors);
    outputReordererStats(result.reorderSuccess, result.reorderFail);

    outputTransitionStats(result);
  }

  /** Reconstruct the tag set that was used to train the model by decoding some of the features.
   *  This is slow and brittle but should work!  Only if "-" is not in the tag set....
   */
  @Override
  Set<String> tagSet() {
    Set<String> tags = Generics.newHashSet();
    Pattern p1 = Pattern.compile("Q0TQ1T-([^-]+)-.*");
    Pattern p2 = Pattern.compile("S0T-(.*)");
    for (String feat : featureWeights.keySet()) {
      Matcher m1 = p1.matcher(feat);
      if (m1.matches()) {
        tags.add(m1.group(1));
      }
      Matcher m2 = p2.matcher(feat);
      if (m2.matches()) {
        tags.add(m2.group(1));
      }
    }
    // Add the end of sentence tag!
    // The SR model doesn't use it, but other models do and report it.
    // todo [cdm 2014]: Maybe we should reverse the convention here?!?
    tags.add(Tagger.EOS_TAG);
    return tags;
  }

  /** Convenience method: returns one highest scoring transition, without any ParserConstraints */
  private ScoredObject<Integer> findHighestScoringTransition(State state, List<String> features, boolean requireLegal) {
    Collection<ScoredObject<Integer>> transitions = findHighestScoringTransitions(state, features, requireLegal, 1, null);
    if (transitions.isEmpty()) {
      return null;
    }
    return transitions.iterator().next();
  }

  @Override
  public Collection<ScoredObject<Integer>> findHighestScoringTransitions(State state, boolean requireLegal, int numTransitions, List<ParserConstraint> constraints) {
    List<String> features = featureFactory.featurize(state);
    return findHighestScoringTransitions(state, features, requireLegal, numTransitions, constraints);
  }

  private Collection<ScoredObject<Integer>> findHighestScoringTransitions(State state, List<String> features, boolean requireLegal, int numTransitions, List<ParserConstraint> constraints) {
    float[] scores = new float[transitionIndex.size()];
    for (String feature : features) {
      Weight weight = featureWeights.get(feature);
      if (weight == null) {
        // Features not in our index are ignored
        continue;
      }
      weight.score(scores);
    }

    PriorityQueue<ScoredObject<Integer>> queue = new PriorityQueue<>(numTransitions + 1, ScoredComparator.ASCENDING_COMPARATOR);
    for (int i = 0; i < scores.length; ++i) {
      if (!requireLegal || transitionIndex.get(i).isLegal(state, constraints)) {
        queue.add(new ScoredObject<>(i, scores[i]));
        if (queue.size() > numTransitions) {
          queue.poll();
        }
      }
    }

    return queue;
  }

  /**
   * index: the tree to train
   * binarizedTrees: a list of all the training trees we know about, binarized
   * transitionLists: a list of pre-assembled transitions for the trees
   */
  private TrainingResult trainTree(TrainingExample example) {
    int numCorrect = 0;
    int numWrong = 0;

    Tree tree = example.binarizedTree;

    List<TrainingUpdate> updates = Generics.newArrayList();
    Pair<Integer, Integer> firstError = null;

    IntCounter<Class<? extends Transition>> correctTransitions = new IntCounter<>();
    TwoDimensionalIntCounter<Class<? extends Transition>, Class<? extends Transition>> wrongTransitions = new TwoDimensionalIntCounter<>();

    ReorderingOracle reorderer = null;
    if (op.trainOptions().trainingMethod == ShiftReduceTrainOptions.TrainingMethod.REORDER_ORACLE ||
        op.trainOptions().trainingMethod == ShiftReduceTrainOptions.TrainingMethod.REORDER_BEAM) {
      reorderer = new ReorderingOracle(op, rootOnlyStates, transitionIndex);
    }

    int reorderSuccess = 0;
    int reorderFail = 0;

    if (op.trainOptions().trainingMethod == ShiftReduceTrainOptions.TrainingMethod.BEAM ||
        op.trainOptions().trainingMethod == ShiftReduceTrainOptions.TrainingMethod.REORDER_BEAM) {
      if (op.trainOptions().beamSize <= 0) {
        throw new IllegalArgumentException("Illegal beam size " + op.trainOptions().beamSize);
      }
      PriorityQueue<State> agenda = new PriorityQueue<>(op.trainOptions().beamSize + 1, ScoredComparator.ASCENDING_COMPARATOR);
      State goldState = example.initialStateFromGoldTagTree();
      List<Transition> transitions = example.trainTransitions();
      agenda.add(goldState);

      while (transitions.size() > 0) {
        Transition goldTransition = transitions.get(0);
        Transition highestScoringTransitionFromGoldState = null;
        double highestScoreFromGoldState = 0.0;
        PriorityQueue<State> newAgenda = new PriorityQueue<>(op.trainOptions().beamSize + 1, ScoredComparator.ASCENDING_COMPARATOR);
        State highestScoringState = null;
        // keep track of the state in the current agenda which leads
        // to the highest score on the next agenda.  this will be
        // trained down assuming it is not the correct state
        State highestCurrentState = null;
        for (State currentState : agenda) {
          // TODO: can maybe speed this part up, although it doesn't seem like a critical part of the runtime
          boolean isGoldState = goldState.areTransitionsEqual(currentState);

          List<String> features = featureFactory.featurize(currentState);
          Collection<ScoredObject<Integer>> stateTransitions = findHighestScoringTransitions(currentState, features, true, op.trainOptions().beamSize, null);
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
        if (op.trainOptions().trainingMethod == ShiftReduceTrainOptions.TrainingMethod.REORDER_BEAM &&
            highestScoringTransitionFromGoldState == null) {
          break;
        }

        if (highestScoringState == null) {
          System.err.println("Unable to find a best transition!");
          System.err.println("Previous agenda:");
          for (State state : agenda) {
            System.err.println(state);
          }
          System.err.println("Gold transitions:");
          System.err.println(example.transitions);
          break;
        }

        State newGoldState = goldTransition.apply(goldState, 0.0);

        if (firstError == null && !highestScoringTransitionFromGoldState.equals(goldTransition)) {
          int predictedIndex = transitionIndex.indexOf(highestScoringTransitionFromGoldState);
          int goldIndex = transitionIndex.indexOf(goldTransition);
          if (predictedIndex < 0) {
            throw new AssertionError("Predicted transition not in the index: " + highestScoringTransitionFromGoldState);
          }
          if (goldIndex < 0) {
            throw new AssertionError("Gold transition not in the index: " + goldTransition);
          }
          firstError = new Pair<>(predictedIndex, goldIndex);
        }

        // if highest scoring state used the correct transition, no training
        // otherwise, down the last transition, up the correct
        if (!newGoldState.areTransitionsEqual(highestScoringState)) {
          ++numWrong;
          wrongTransitions.incrementCount(goldTransition.getClass(), highestScoringTransitionFromGoldState.getClass());
          List<String> goldFeatures = featureFactory.featurize(goldState);
          int lastTransition = transitionIndex.indexOf(highestScoringState.transitions.peek());
          updates.add(new TrainingUpdate(featureFactory.featurize(highestCurrentState), -1, lastTransition, learningRate));
          updates.add(new TrainingUpdate(goldFeatures, transitionIndex.indexOf(goldTransition), -1, learningRate));

          if (op.trainOptions().trainingMethod == ShiftReduceTrainOptions.TrainingMethod.BEAM) {
            // If the correct state has fallen off the agenda, break
            if (!ShiftReduceUtils.findStateOnAgenda(newAgenda, newGoldState)) {
              break;
            } else {
              transitions.remove(0);
            }
          } else if (op.trainOptions().trainingMethod == ShiftReduceTrainOptions.TrainingMethod.REORDER_BEAM) {
            if (!ShiftReduceUtils.findStateOnAgenda(newAgenda, newGoldState)) {
              if (!reorderer.reorder(goldState, highestScoringTransitionFromGoldState, transitions)) {
                if (reorderSuccess == 0) reorderFail = 1;
                break;
              }
              newGoldState = highestScoringTransitionFromGoldState.apply(goldState);
              if (!ShiftReduceUtils.findStateOnAgenda(newAgenda, newGoldState)) {
                if (reorderSuccess == 0) reorderFail = 1;
                break;
              }
              reorderSuccess = 1;
            } else {
              transitions.remove(0);
            }
          }
        } else {
          ++numCorrect;
          correctTransitions.incrementCount(goldTransition.getClass());
          transitions.remove(0);
        }

        goldState = newGoldState;
        agenda = newAgenda;
      }
    } else if (op.trainOptions().trainingMethod == ShiftReduceTrainOptions.TrainingMethod.REORDER_ORACLE ||
               op.trainOptions().trainingMethod == ShiftReduceTrainOptions.TrainingMethod.EARLY_TERMINATION ||
               op.trainOptions().trainingMethod == ShiftReduceTrainOptions.TrainingMethod.GOLD) {
      State state = example.initialStateFromGoldTagTree();
      List<Transition> transitions = example.trainTransitions();

      boolean keepGoing = true;
      while (transitions.size() > 0 && keepGoing) {
        Transition gold = transitions.get(0);
        int goldNum = transitionIndex.indexOf(gold);
        List<String> features = featureFactory.featurize(state);
        int predictedNum = findHighestScoringTransition(state, features, false).object();
        Transition predicted = transitionIndex.get(predictedNum);
        if (goldNum == predictedNum) {
          transitions.remove(0);
          state = gold.apply(state);
          numCorrect++;
          correctTransitions.incrementCount(gold.getClass());
        } else {
          numWrong++;
          wrongTransitions.incrementCount(gold.getClass(), predicted.getClass());
          if (firstError == null) {
            firstError = new Pair<>(predictedNum, goldNum);
          }
          // TODO: allow weighted features, weighted training, etc
          updates.add(new TrainingUpdate(features, goldNum, predictedNum, learningRate));
          switch (op.trainOptions().trainingMethod) {
          case EARLY_TERMINATION:
            keepGoing = false;
            break;
          case GOLD:
            transitions.remove(0);
            state = gold.apply(state);
            break;
          case REORDER_ORACLE:
            keepGoing = reorderer.reorder(state, predicted, transitions);
            if (keepGoing) {
              state = predicted.apply(state);
              reorderSuccess = 1;
            } else if (reorderSuccess == 0) {
              reorderFail = 1;
            }
            break;
          default:
            throw new IllegalArgumentException("Unexpected method " + op.trainOptions().trainingMethod);
          }
        }
      }
    }

    return new TrainingResult(updates, numCorrect, numWrong, firstError, correctTransitions, wrongTransitions, reorderSuccess, reorderFail);
  }

  private class TrainTreeProcessor implements ThreadsafeProcessor<TrainingExample, TrainingResult> {
    public TrainTreeProcessor() { }

    @Override
    public TrainingResult process(TrainingExample example) {
      return trainTree(example);
    }

    @Override
    public TrainTreeProcessor newInstance() {
      // already threadsafe
      return this;
    }
  }

  /**
   * Trains a batch of trees and returns the following: a list of
   * Update objects, the number of transitions correct, and the number
   * of transitions wrong.
   *
   * If the model is trained with multiple threads, it is expected
   * that a valid MulticoreWrapper is passed in which does the
   * processing.  In that case, the processing is done on all of the
   * trees without updating any weights, which allows the results for
   * multithreaded training to be reproduced.
   */
  private TrainingResult trainBatch(List<TrainingExample> trainingData, MulticoreWrapper<TrainingExample, TrainingResult> wrapper) {
    List<TrainingResult> results = new ArrayList<>();

    if (op.trainOptions.trainingThreads == 1) {
      for (TrainingExample example : trainingData) {
        TrainingResult result = trainTree(example);
        results.add(result);
      }
    } else {
      for (TrainingExample example : trainingData) {
        wrapper.put(example);
      }
      wrapper.join(false);
      while (wrapper.peek()) {
        TrainingResult result = wrapper.poll();
        results.add(result);
      }
    }
    return new TrainingResult(results);
  }


  private double evaluate(Tagger tagger, Treebank devTreebank, String message) {
    ShiftReduceParser temp = new ShiftReduceParser(op, this);
    EvaluateTreebank evaluator = new EvaluateTreebank(temp.getOp(), null, temp, tagger, temp.getExtraEvals(), temp.getParserQueryEvals());
    evaluator.testOnTreebank(devTreebank);
    double labelF1 = evaluator.getLBScore();
    log.info(message + ": " + labelF1);
    return labelF1;
  }


  /**
   * This increases f1 slightly, probably by letting the parser know
   * what to do in situations it doesn't get to during the training.
   */
  static List<TrainingExample> augmentSubsentences(List<TrainingExample> trainingData, Random random, float augmentFraction) {
    List<TrainingExample> augmentedData = new ArrayList<TrainingExample>(trainingData);
    for (TrainingExample example : trainingData) {
      int window = 7 + example.numSkips;
      if (example.transitions.size() > window + 3 && random.nextDouble() < augmentFraction) {
        int numSkips = random.nextInt(example.transitions.size() - (window + 3)) + window;
        augmentedData.add(new TrainingExample(example.binarizedTree, example.transitions, numSkips));
      }
    }
    return augmentedData;
  }

  /**
   * Output the top 9 transition errors made by the model during training.
   * <br>
   * Creates a copy so that the original counter is unchanged
   */
  private void outputFirstErrors(IntCounter<Pair<Integer, Integer>> firstErrors) {
    if (firstErrors == null || firstErrors.size() == 0)
      return;

    IntCounter<Pair<Integer, Integer>> firstErrorCopy = new IntCounter<>(firstErrors);

    log.info("Most common transition errors: gold -> predicted");
    for (int i = 0; i < 9 && firstErrorCopy.size() > 0; ++i) {
      Pair<Integer, Integer> mostCommon = firstErrorCopy.argmax();
      int count = firstErrorCopy.max();
      firstErrorCopy.decrementCount(mostCommon, count);
      Transition predicted = transitionIndex.get(mostCommon.first());
      Transition gold = transitionIndex.get(mostCommon.second());
      log.info("  # " + (i+1) + ": " + gold + " -> " + predicted + " happened " + firstErrorCopy.max() + " times");
    }
  }

  private void outputReordererStats(int numReorderSuccess, int numReorderFail) {
    if (numReorderSuccess == 0 && numReorderFail == 0)
      return;

    log.info("Reorderer successfully operated at least once on " + numReorderSuccess +
             " training trees and failed to do anything useful on " + numReorderFail + " trees");
  }

  private void outputTransitionStats(TrainingResult result) {
    // Output a list of all the correct guesses
    List<Class<? extends Transition>> sorted = Counters.toSortedList(result.correctTransitions);
    List<String> correct = new ArrayList<>();
    correct.add("Got the following transition types correct:");
    for (Class<? extends Transition> t : sorted) {
      correct.add(ShiftReduceUtils.transitionShortName(t) + ": " + result.correctTransitions.getCount(t));
    }
    log.info(StringUtils.join(correct, "\n  "));

    IntCounter<Class<? extends Transition>> totalGuesses = result.wrongTransitions.totalCounts();
    sorted = Counters.toSortedList(totalGuesses);
    List<String> wrong = new ArrayList<>();
    wrong.add("Got the following transition types incorrect:");
    for (Class<? extends Transition> t : sorted) {
      IntCounter<Class<? extends Transition>> inner = result.wrongTransitions.getCounter(t);
      List<Class<? extends Transition>> sortedInner = Counters.toSortedList(inner);
      for (Class<? extends Transition> u : sortedInner) {
        wrong.add(ShiftReduceUtils.transitionShortName(t) + " -> " + ShiftReduceUtils.transitionShortName(u) + ": " + inner.getCount(u));
      }
    }
    log.info(StringUtils.join(wrong, "\n  "));
  }

  /**
   * Currently, the only thing returned is a counter with the first
   * errors at the best training iteration.  The idea is that this
   * information can be used after partially training a model to
   * figure out some new transitions to add to make the next version
   * of the model better.
   */
  private IntCounter<Pair<Integer, Integer>> trainModel(String serializedPath, Tagger tagger, Random random, List<TrainingExample> trainingData, Treebank devTreebank, int nThreads, Set<String> allowedFeatures, int numIterations) {
    double bestScore = 0.0;
    int bestIteration = 0;

    PriorityQueue<ScoredObject<PerceptronModel>> bestModels = null;
    if (op.trainOptions().averagedModels > 0) {
      bestModels = new PriorityQueue<>(op.trainOptions().averagedModels + 1, ScoredComparator.ASCENDING_COMPARATOR);
    }

    IntCounter<Pair<Integer, Integer>> bestFirstErrors = null;

    MulticoreWrapper<TrainingExample, TrainingResult> wrapper = null;
    if (nThreads != 1) {
      wrapper = new MulticoreWrapper<>(op.trainOptions.trainingThreads, new TrainTreeProcessor());
    }

    IntCounter<String> featureFrequencies = null;
    if (op.trainOptions().featureFrequencyCutoff > 1 && allowedFeatures == null) {
      // allowedFeatures != null means we already filtered rare
      // features once.  Sometimes the exact features found are
      // different depending on how the learning proceeds.  The second
      // time training, we only allow rare features to exist if they
      // met the threshold established the first time around
      featureFrequencies = new IntCounter<>();
    }

    for (int iteration = 1; iteration <= numIterations; ++iteration) {
      Timing trainingTimer = new Timing();
      List<TrainingResult> results = new ArrayList<>();

      List<TrainingExample> augmentedData = augmentSubsentences(trainingData, random, op.trainOptions().augmentSubsentences);
      Collections.shuffle(augmentedData, random);
      log.info("Original list " + trainingData.size() + "; augmented " + augmentedData.size());

      for (int start = 0; start < augmentedData.size(); start += op.trainOptions.batchSize) {
        int end = Math.min(start + op.trainOptions.batchSize, augmentedData.size());
        TrainingResult result = trainBatch(augmentedData.subList(start, end), wrapper);
        results.add(result);

        for (TrainingUpdate update : result.updates) {
          for (String feature : update.features) {
            if (allowedFeatures != null && !allowedFeatures.contains(feature)) {
              continue;
            }
            Weight weight = featureWeights.get(feature);
            if (weight == null) {
              weight = new Weight();
              featureWeights.put(feature, weight);
            }
            weight.updateWeight(update.goldTransition, update.delta);
            weight.updateWeight(update.predictedTransition, -update.delta);

            if (featureFrequencies != null) {
              featureFrequencies.incrementCount(feature, (update.goldTransition >= 0 && update.predictedTransition >= 0) ? 2 : 1);
            }
          }
        }
      }

      float l2Reg = op.trainOptions().l2Reg;
      if (l2Reg > 0.0f) {
        for (Map.Entry<String, Weight> weight : featureWeights.entrySet()) {
          weight.getValue().l2Reg(l2Reg);
        }
      }

      float l1Reg = op.trainOptions().l1Reg;
      if (l1Reg > 0.0f) {
        for (Map.Entry<String, Weight> weight : featureWeights.entrySet()) {
          weight.getValue().l1Reg(l1Reg);
        }
      }

      trainingTimer.done("Iteration " + iteration);
      TrainingResult result = new TrainingResult(results);
      outputStats(result);

      double labelF1 = 0.0;
      if (devTreebank != null) {
        labelF1 = evaluate(tagger, devTreebank, "Label F1 for iteration " + iteration);

        if (labelF1 > bestScore) {
          log.info("New best dev score (previous best " + bestScore + ")");
          bestScore = labelF1;
          bestIteration = iteration;
          bestFirstErrors = new IntCounter<>();
          bestFirstErrors.addAll(result.firstErrors);
        } else {
          log.info("Failed to improve for " + (iteration - bestIteration) + " iteration(s) on previous best score of " + bestScore);
          if (op.trainOptions.stalledIterationLimit > 0 && (iteration - bestIteration >= op.trainOptions.stalledIterationLimit)) {
            log.info("Failed to improve for too long, stopping training");
            break;
          }
        }
        log.info("\n\n");

        if (bestModels != null) {
          PerceptronModel copy = new PerceptronModel(this);
          copy.condenseFeatures();
          bestModels.add(new ScoredObject<>(copy, labelF1));
          if (bestModels.size() > op.trainOptions().averagedModels) {
            bestModels.poll();
          }
        }
      }
      if (op.trainOptions().saveIntermediateModels && serializedPath != null && op.trainOptions.debugOutputFrequency > 0) {
        String tempName = serializedPath.substring(0, serializedPath.length() - 7) + "-" + FILENAME.format(iteration) + "-" + NF.format(labelF1) + ".ser.gz";
        ShiftReduceParser temp = new ShiftReduceParser(op, this);
        temp.saveModel(tempName);
        // TODO: we could save a cutoff version of the model,
        // especially if we also get a dev set number for it, but that
        // might be overkill
      }

      if (iteration % 10 == 0 && op.trainOptions().decayLearningRate > 0.0) {
        learningRate *= op.trainOptions().decayLearningRate;
      }
    } // end for iterations

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
          log.info("Testing with " + i + " models averaged together");
          // TODO: this is kind of ugly, would prefer a separate object
          averageScoredModels(models.subList(0, i));
          double labelF1 = evaluate(tagger, devTreebank, "Label F1 for " + i + " models");
          if (labelF1 > bestF1) {
            bestF1 = labelF1;
            bestSize = i;
          }
        }
        averageScoredModels(models.subList(0, bestSize));
        log.info("Label F1 for " + bestSize + " models: " + bestF1);
      } else {
        averageScoredModels(bestModels);
      }
    }

    // TODO: perhaps we should filter the features and then get dev
    // set scores.  That way we can merge the models which are best
    // after filtering.
    if (featureFrequencies != null) {
      filterFeatures(featureFrequencies.keysAbove(op.trainOptions().featureFrequencyCutoff));
    }

    condenseFeatures();

    return bestFirstErrors;
  }

  /**
   * Randomly drop a fraction of the features.  Return a set of the features being kept.
   * <br>
   * Useful for training shards of the perceptron, for example
   */
  static Set<String> pruneFeatures(Set<String> features, Random random, double drop) {
    Set<String> prunedFeatures = new HashSet<>();
    for (String feature : features) {
      if (random.nextDouble() > drop) {
        prunedFeatures.add(feature);
      }
    }
    if (prunedFeatures.size() == 0) {
      for (String feature : features) {
        prunedFeatures.add(feature);
        break;
      }
    }
    return prunedFeatures;
  }

  /**
   * Starting from the given index, find the next transition that matches the predicate
   */
  static int findTransitionInList(List<Transition> transitions,
                                  int start,
                                  Predicate<Transition> pred) {
    int index = start;
    while (index < transitions.size()) {
      if (pred.test(transitions.get(index))) {
        return index;
      }
      index++;
    }
    return -1;
  }

  static int findClosingBinary(List<Transition> transitions, int start) {
    int index = start;
    int position = 0;
    while (index < transitions.size()) {
      int delta = transitions.get(index).stackSizeChange();
      position = position + delta;
      if (delta < 0 && position <= 0) {
        break;
      }
      ++index;
    }
    if (position < 0) {
      // this happens when the first BinaryTransition occurs before any ShiftTransitions.
      return -1;
    }
    if (position > 0) {
      throw new AssertionError("There should always be BinaryTransitions matching all of the ShiftTransitions found");
    }
    if (!(transitions.get(index) instanceof BinaryTransition)) {
      throw new AssertionError("The above loop should have ended on a BinaryTransition");
    }
    return index;
  }

  /**
   * Choose a random transition from the given list, using the given predicate to determine eligible position
   */
  static int chooseRandomTransition(List<Transition> transitions, Random random, Predicate<Transition> pred) {
    int count = 0;
    for (Transition t : transitions) {
      if (pred.test(t)) {
        ++count;
      }
    }
    if (count == 0) {
      return -1;
    }
    int chosen = random.nextInt(count) + 1;
    int index = 0;
    for (int i = 0; i < chosen; ++i) {
      index = findTransitionInList(transitions, index, pred);
    }
    return index;
  }

  /**
   * Create a new training example with an extra Unary transition and a RemoveUnaryTransition to fix it
   */
  static TrainingExample newExtraShiftUnaryExample(TrainingExample example,
                                                   List<Transition> shiftUnaryErrors,
                                                   Map<Transition, RemoveUnaryTransition> removeUnaries,
                                                   Random random,
                                                   double ratio) {
    // TODO: a worthwhile experiment would be to see if using the
    // partially trained parser to predict the best places to put
    // these new transitions improves the results
    List<Transition> transitions = example.transitions;
    // TODO: perhaps try to use some logic for where the UnaryTransition errors occur most commonly
    int index = chooseRandomTransition(transitions, random, (x) -> (x instanceof BinaryTransition));
    if (index < 0) {
      return null;
    }

    int unaryIndex = index;
    int position = 0;
    while (unaryIndex > 0) {
      --unaryIndex;
      position = position + transitions.get(unaryIndex).stackSizeChange();
      // When we have seen one more Shift than Binary, that means
      // we have built the right side of the BinaryTransition we
      // want to modify
      if (position > 0) {
        break;
      }
    }

    if (unaryIndex <= 0) {
      throw new AssertionError("Shouldn't fail to find the left side of the BinaryTransition.  Starting index: " + index + " Transition sequence: " + transitions.subList(0, index));
    }
    // At this point we have scrolled to before the right side of the Binary Transition
    // If the previous transition is already (Compound?)UnaryTransition,
    // we can't use this particular example
    if ((transitions.get(unaryIndex - 1) instanceof UnaryTransition) ||
        (transitions.get(unaryIndex - 1) instanceof CompoundUnaryTransition)) {
      return null;
    }

    // Now we know that this is a suitable Example for making a new fake example out of
    if (random.nextDouble() > ratio) {
      // nah
      return null;
    }

    List<Transition> fakeTransitions = new ArrayList<>();
    fakeTransitions.addAll(transitions.subList(0, unaryIndex));
    // randomly pick an error to pretend we made
    Transition errorUnary = shiftUnaryErrors.get(random.nextInt(shiftUnaryErrors.size()));
    RemoveUnaryTransition removeUnary = removeUnaries.get(errorUnary);
    if (removeUnary == null) {
      throw new AssertionError("All common shift->unary errors should be covered by a RemoveUnaryTransition");
    }
    fakeTransitions.add(errorUnary);
    // add the remaining transitions until the BinaryTransition we wanted to learn how to fix
    fakeTransitions.addAll(transitions.subList(unaryIndex, index));
    fakeTransitions.add(removeUnary);
    fakeTransitions.addAll(transitions.subList(index, transitions.size()));
    return new TrainingExample(example.binarizedTree,
                               fakeTransitions,
                               unaryIndex+1);
  }

  static List<TrainingExample> extraShiftUnaryExamples(List<Transition> shiftUnaryErrors,
                                                       Index<Transition> newTransitions,
                                                       Map<Transition, RemoveUnaryTransition> removeUnaries,
                                                       List<TrainingExample> trainingData,
                                                       Random random,
                                                       double ratio) {
    List<TrainingExample> newExamples = new ArrayList<>();

    for (TrainingExample example : trainingData) {
      TrainingExample newExample = newExtraShiftUnaryExample(example, shiftUnaryErrors, removeUnaries, random, ratio);
      if (newExample == null) {
        continue;
      }
      //System.out.println("----- creating new transitions -----");
      //System.out.println(example.transitions);
      //System.out.println(newExample.transitions);
      newExamples.add(newExample);
    }
    return newExamples;
  }

  static Map<Transition, RemoveUnaryTransition> buildRemoveUnaryMap(List<Transition> shiftUnaryErrors) {
    Map<Transition, RemoveUnaryTransition> removeUnaries = new HashMap<>();
    for (Transition t : shiftUnaryErrors) {
      if (t instanceof UnaryTransition) {
        removeUnaries.put(t, new RemoveUnaryTransition((UnaryTransition) t));
      } else if (t instanceof CompoundUnaryTransition) {
        removeUnaries.put(t, new RemoveUnaryTransition((CompoundUnaryTransition) t));
      } else {
        throw new AssertionError("Unexpected transition: " + t);
      }
      log.info("Added new transition: " + t + " -> " + removeUnaries.get(t));
    }
    return removeUnaries;
  }

  static Pair<Index<Transition>, List<TrainingExample>> chooseExtraTransitions(Index<Transition> transitionIndex,
                                                                               PerceptronModel initialModel,
                                                                               Tagger tagger,
                                                                               Random random,
                                                                               List<TrainingExample> trainingData,
                                                                               Treebank devTreebank,
                                                                               int nThreads) {
    PerceptronModel tempModel = new PerceptronModel(initialModel);
    // TODO: make this a parameter
    // TODO: model averaging and augmenting are both kinda silly in this context
    IntCounter<Pair<Integer, Integer>> firstErrors = tempModel.trainModel(null, tagger, random, trainingData, devTreebank, nThreads, null, 5);
    log.info("Done training temporary model");

    // Errors where the parser guessed ShiftTransition instead of (Compound?)UnaryTransition
    // This stores the Unary transtion that should have been guessed, since there is only one Shift
    List<Transition> unaryShiftErrors = new ArrayList<>();
    // Errors where the parser guessed (Compound?)UnaryTransition instead of ShiftTransition
    // This stores the Unary transtion that was incorrectly guessed, since there is only one Shift
    List<Transition> shiftUnaryErrors = new ArrayList<>();

    // TODO: make this a parameter
    Counters.retainTop(firstErrors, 20);
    List<Pair<Integer, Integer>> commonFirstErrors = Counters.toSortedList(firstErrors);
    for (Pair<Integer, Integer> mostCommon : commonFirstErrors) {
      Transition predicted = transitionIndex.get(mostCommon.first());
      Transition gold = transitionIndex.get(mostCommon.second());

      if ((predicted instanceof ShiftTransition) &&
          (gold instanceof UnaryTransition || gold instanceof CompoundUnaryTransition)) {
        // Found a situation where the parser was frequently predicting Shift instead of Unary / CompoundUnary
        unaryShiftErrors.add(gold);
      } else if ((gold instanceof ShiftTransition) &&
                 (predicted instanceof UnaryTransition || predicted instanceof CompoundUnaryTransition)) {
        // Found a situation where the parser was frequently predicting Unary / CompoundUnary instead of Shift
        shiftUnaryErrors.add(predicted);
      }
    }

    log.info("Most common gold shift, predicted unary errors: " + shiftUnaryErrors);
    log.info("Most common gold unary, predicted shift errors: " + unaryShiftErrors);

    Index<Transition> newTransitions = new HashIndex<>(transitionIndex);
    // TODO: make 0.5 an option
    Map<Transition, RemoveUnaryTransition> removeUnaries = buildRemoveUnaryMap(shiftUnaryErrors);
    newTransitions.addAll(removeUnaries.values());
    List<TrainingExample> newExamples = extraShiftUnaryExamples(shiftUnaryErrors, newTransitions, removeUnaries, trainingData, random, 0.5);

    List<TrainingExample> newTraining = new ArrayList<>(trainingData);
    newTraining.addAll(newExamples);
    return new Pair<>(newTransitions, newTraining);
  }

  /**
   * Will train the model on the given treebank, using devTreebank as
   * a dev set.  If op.retrainAfterCutoff is set, will rerun training
   * after the first time through on a limited set of features.
   *<br>
   * TODO: why not go from the trainingData to the derived Sets here
   *
   * @param op The options used to initialize the parser
   * @param transitionIndex precalculated transitions from the training data
   * @param knownStates the states in the training data
   * @param rootStates states which occur at the top of the trees
   * @param rootOnlyStates states which ONLY occur at the top of the trees
   * @param initialModel if training a continuation, use this model as the starting point
   *
   * @param serializedPath Where serialized models go.  If the appropriate options are set, the method can use this to save intermediate models.
   * @param tagger The tagger to use when evaluating devTreebank.  TODO: it would make more sense for ShiftReduceParser to retag the trees first
   * @param random A random number generator to use for any random numbers.  Useful to make sure results can be reproduced.
   * @param trainingData The treebank to train from, along with lists of transitions that will reproduce the same trees.
   * @param devTreebank a set of trees which can be used for dev testing (assuming the user provided a dev treebank)
   * @param nThreads how many threads the model can use for training
   */
  public static PerceptronModel trainModel(ShiftReduceOptions op,
                                           Index<Transition> transitionIndex,
                                           Set<String> knownStates,
                                           Set<String> rootStates,
                                           Set<String> rootOnlyStates,
                                           PerceptronModel initialModel,
                                           String serializedPath,
                                           Tagger tagger,
                                           Random random,
                                           List<TrainingExample> trainingData,
                                           Treebank devTreebank,
                                           int nThreads) {
    if (initialModel == null) {
      initialModel = new PerceptronModel(op, transitionIndex, knownStates, rootStates, rootOnlyStates);
    } else if (op.trainOptions().learnExtraTransitions) {
      throw new IllegalArgumentException("Have not yet implemented learning extra transitions starting from an already trained model");
    } else if (!op.trainOptions().learnExtraTransitions && initialModel.op.trainOptions().learnExtraTransitions) {
      throw new IllegalArgumentException("Already trained model had extra transitions");
    }

    if (op.trainOptions().learnExtraTransitions) {
      Pair<Index<Transition>, List<TrainingExample>> extra = chooseExtraTransitions(transitionIndex, initialModel, tagger, random, trainingData, devTreebank, nThreads);
      transitionIndex = extra.first;
      trainingData = extra.second;
      initialModel = new PerceptronModel(op, transitionIndex, knownStates, rootStates, rootOnlyStates);
    }

    if (op.trainOptions().retrainAfterCutoff && op.trainOptions().featureFrequencyCutoff > 0 ||
        op.trainOptions().retrainShards > 1) {
      String tempName = serializedPath.substring(0, serializedPath.length() - 7) + "-" + "temp.ser.gz";
      PerceptronModel currentModel = new PerceptronModel(initialModel);
      currentModel.trainModel(tempName, tagger, random, trainingData, devTreebank, nThreads, null, op.trainOptions().trainingIterations);

      if (op.trainOptions().saveIntermediateModels) {
        ShiftReduceParser temp = new ShiftReduceParser(op, currentModel);
        temp.saveModel(tempName);
      }

      log.info("Beginning retraining");
      Set<String> allowedFeatures = currentModel.featureWeights.keySet();

      currentModel = new PerceptronModel(initialModel);
      currentModel.filterFeatures(allowedFeatures);
      currentModel.trainModel(serializedPath, tagger, random, trainingData, devTreebank, nThreads, allowedFeatures, op.trainOptions().trainingIterations);

      // If we only had one train shard, we are now done.  Otherwise,
      // we retrain N-1 more times, each time dropping a fraction of
      // the features.
      if (op.trainOptions().retrainShards > 1) {
        List<PerceptronModel> shards = Generics.newArrayList();
        shards.add(currentModel);

        for (int i = 1; i < op.trainOptions().retrainShards; ++i) {
          log.info("Beginning retraining of shard " + (i+1));
          Set<String> prunedFeatures = pruneFeatures(allowedFeatures, random, op.trainOptions().retrainShardFeatureDrop);
          currentModel = new PerceptronModel(initialModel);
          currentModel.filterFeatures(prunedFeatures);
          currentModel.trainModel(serializedPath, tagger, random, trainingData, devTreebank, nThreads, prunedFeatures, op.trainOptions().trainingIterations);
          shards.add(currentModel);
        }
        log.info("Averaging " + op.trainOptions().retrainShards + " shards");
        currentModel = new PerceptronModel(initialModel);
        currentModel.averageModels(shards);
        currentModel.condenseFeatures();
        currentModel.evaluate(tagger, devTreebank, "Label F1 for " + op.trainOptions().retrainShards + " averaged shards");
      }

      return currentModel;
    } else {
      PerceptronModel currentModel = new PerceptronModel(initialModel);
      currentModel.trainModel(serializedPath, tagger, random, trainingData, devTreebank, nThreads, null, op.trainOptions().trainingIterations);
      return currentModel;
    }
  }

  private static final long serialVersionUID = 1;

}
