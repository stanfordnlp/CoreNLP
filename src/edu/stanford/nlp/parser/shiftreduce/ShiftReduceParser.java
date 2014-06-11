package edu.stanford.nlp.parser.shiftreduce;

import java.io.FileFilter;
import java.io.IOException;
import java.io.Serializable;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
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
import edu.stanford.nlp.parser.common.ParserGrammar;
import edu.stanford.nlp.parser.common.ParserQuery;
import edu.stanford.nlp.parser.lexparser.BinaryHeadFinder;
import edu.stanford.nlp.parser.lexparser.EvaluateTreebank;
import edu.stanford.nlp.parser.lexparser.Options;
import edu.stanford.nlp.parser.lexparser.TreebankLangParserParams;
import edu.stanford.nlp.parser.lexparser.TreeBinarizer;
import edu.stanford.nlp.parser.metrics.ParserQueryEval;
import edu.stanford.nlp.parser.metrics.Eval;
import edu.stanford.nlp.tagger.maxent.MaxentTagger;
import edu.stanford.nlp.trees.BasicCategoryTreeTransformer;
import edu.stanford.nlp.trees.CompositeTreeTransformer;
import edu.stanford.nlp.trees.HeadFinder;
import edu.stanford.nlp.trees.LabeledScoredTreeNode;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.Treebank;
import edu.stanford.nlp.trees.TreebankLanguagePack;
import edu.stanford.nlp.trees.TreeCoreAnnotations;
import edu.stanford.nlp.trees.Trees;
import edu.stanford.nlp.util.CollectionUtils;
import edu.stanford.nlp.util.Function;
import edu.stanford.nlp.util.Generics;
import edu.stanford.nlp.util.HashIndex;
import edu.stanford.nlp.util.Index;
import edu.stanford.nlp.util.Pair;
import edu.stanford.nlp.util.ReflectionLoading;
import edu.stanford.nlp.util.ScoredComparator;
import edu.stanford.nlp.util.ScoredObject;
import edu.stanford.nlp.util.Timing;
import edu.stanford.nlp.util.Triple;
import edu.stanford.nlp.util.concurrent.MulticoreWrapper;
import edu.stanford.nlp.util.concurrent.ThreadsafeProcessor;


public class ShiftReduceParser implements Serializable, ParserGrammar {
  final Index<Transition> transitionIndex;
  final Map<String, List<ScoredObject<Integer>>> featureWeights;

  final ShiftReduceOptions op;

  final FeatureFactory featureFactory;

  public ShiftReduceParser(ShiftReduceOptions op) {
    this.transitionIndex = new HashIndex<Transition>();
    this.featureWeights = Generics.newHashMap();
    this.op = op;
    this.featureFactory = ReflectionLoading.loadByReflection(op.featureFactoryClass);
  }

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

  public ShiftReduceParser deepCopy() {
    // TODO: should we deep copy the options?
    ShiftReduceParser copy = new ShiftReduceParser(op);
    copy.copyWeights(this);
    return copy;
  }

  /**
   * Fill in the current object's weights with the other parser's weights.
   */
  public void copyWeights(ShiftReduceParser other) {
    transitionIndex.clear();
    for (Transition transition : other.transitionIndex) {
      transitionIndex.add(transition);
    }
    featureWeights.clear();
    for (String feature : other.featureWeights.keySet()) {
      List<ScoredObject<Integer>> newWeights = Generics.newArrayList();
      for (ScoredObject<Integer> weight : other.featureWeights.get(feature)) {
        if (weight.score() != 0.0) {
          newWeights.add(new ScoredObject<Integer>(weight.object(), weight.score()));
        }
      }
      if (newWeights.size() > 0) {
        featureWeights.put(feature, newWeights);
      }
    }
  }

  public static ShiftReduceParser averageModels(Collection<ScoredObject<ShiftReduceParser>> scoredModels) {
    if (scoredModels.size() == 0) {
      throw new IllegalArgumentException("Cannot average empty models");
    }

    System.err.print("Averaging models with scores");
    for (ScoredObject<ShiftReduceParser> model : scoredModels) {
      System.err.print(" " + NF.format(model.score()));
    }
    System.err.println();

    List<ShiftReduceParser> models = CollectionUtils.transformAsList(scoredModels, new Function<ScoredObject<ShiftReduceParser>, ShiftReduceParser>() { public ShiftReduceParser apply(ScoredObject<ShiftReduceParser> object) { return object.object(); }});

    ShiftReduceParser firstModel = models.iterator().next();
    ShiftReduceOptions op = firstModel.op;
    // TODO: should we deep copy the options?
    ShiftReduceParser copy = new ShiftReduceParser(op);

    for (Transition transition : firstModel.transitionIndex) {
      copy.transitionIndex.add(transition);
    }
    
    for (ShiftReduceParser model : models) {
      if (!model.transitionIndex.equals(copy.transitionIndex)) {
        throw new IllegalArgumentException("Can only average models with the same transition index");
      }
    }

    Set<String> features = Generics.newHashSet();
    for (ShiftReduceParser model : models) {
      for (String feature : model.featureWeights.keySet()) {
        features.add(feature);
      }
    }

    for (String feature : features) {
      List<ScoredObject<Integer>> weights = Generics.newArrayList();
      copy.featureWeights.put(feature, weights);
    }
    
    int numModels = models.size();
    for (String feature : features) {
      for (ShiftReduceParser model : models) {
        if (!model.featureWeights.containsKey(feature)) {
          continue;
        }
        for (ScoredObject<Integer> weight : model.featureWeights.get(feature)) {
          updateWeight(copy.featureWeights.get(feature), weight.object(), weight.score() / numModels);
        }
      }
    }

    return copy;
  }

  public static void updateWeight(List<ScoredObject<Integer>> weights, int transition, double delta) {
    for (int i = 0; i < weights.size(); ++i) {
      ScoredObject<Integer> weight = weights.get(i);
      if (weight.object() == transition) {
        weight.setScore(weight.score() + delta);
        return;
      } else if (weight.object() > transition) {
        weights.add(i, new ScoredObject<Integer>(transition, delta));
        return;
      }
    }
    weights.add(new ScoredObject<Integer>(transition, delta));
  }

  public ParserQuery parserQuery() {
    return new ShiftReduceParserQuery(this);
  }


  /**
   * Iterate over the feature weight map.
   * For each feature, remove all transitions with score of 0.
   * Any feature with no transitions left is then removed
   */
  public void condenseFeatures() {
    Iterator<String> featureIt = featureWeights.keySet().iterator();
    while (featureIt.hasNext()) {
      String feature = featureIt.next();
      List<ScoredObject<Integer>> weights = featureWeights.get(feature);
      Iterator<ScoredObject<Integer>> weightIt = weights.iterator();
      while (weightIt.hasNext()) {
        ScoredObject<Integer> score = weightIt.next();
        if (score.score() == 0.0) {
          weightIt.remove();
        }
      }
      if (weights.size() == 0) {
        featureIt.remove();
      }
    }
  }


  /**
   * Output some random facts about the parser
   */
  public void outputStats() {
    System.err.println("Number of known features: " + featureWeights.size());

    int numWeights = 0;
    for (String feature : featureWeights.keySet()) {
      numWeights += featureWeights.get(feature).size();
    }
    System.err.println("Number of non-zero weights: " + numWeights);

    int wordLength = 0;
    for (String feature : featureWeights.keySet()) {
      wordLength += feature.length();
    }
    System.err.println("Total word length: " + wordLength);

    System.err.println("Number of transitions: " + transitionIndex.size());
  }

  /** TODO: add an eval which measures transition accuracy? */
  @Override
  public List<Eval> getExtraEvals() {
    return Collections.emptyList();
  }

  @Override
  public List<ParserQueryEval> getParserQueryEvals() {
    if (op.recordBinarized == null && op.recordDebinarized == null) {
      return Collections.emptyList();
    }
    List<ParserQueryEval> evals = Generics.newArrayList();
    if (op.recordBinarized != null) {
      evals.add(new TreeRecorder(TreeRecorder.Mode.BINARIZED, op.recordBinarized));
    }
    if (op.recordDebinarized != null) {
      evals.add(new TreeRecorder(TreeRecorder.Mode.DEBINARIZED, op.recordDebinarized));
    }
    return evals;
  }

  public ScoredObject<Integer> findHighestScoringTransition(State state, List<String> features, boolean requireLegal) {
    Collection<ScoredObject<Integer>> transitions = findHighestScoringTransitions(state, features, requireLegal, 1);
    if (transitions.size() == 0) {
      return null;
    }
    return transitions.iterator().next();
  }

  public Collection<ScoredObject<Integer>> findHighestScoringTransitions(State state, List<String> features, boolean requireLegal, int numTransitions) {
    double[] scores = new double[transitionIndex.size()];
    for (String feature : features) {
      List<ScoredObject<Integer>> weights = featureWeights.get(feature);
      if (weights == null) {
        // Features not in our index are ignored
        continue;
      }
      for (ScoredObject<Integer> weight : weights) {
        scores[weight.object()] += weight.score();
      }
    }

    PriorityQueue<ScoredObject<Integer>> queue = new PriorityQueue<ScoredObject<Integer>>(numTransitions + 1, ScoredComparator.ASCENDING_COMPARATOR);
    for (int i = 0; i < scores.length; ++i) {
      if (!requireLegal || transitionIndex.get(i).isLegal(state)) {
        queue.add(new ScoredObject<Integer>(i, scores[i]));
        if (queue.size() > numTransitions) {
          queue.poll();
        }
      }
    }

    return queue;
  }

  public static State initialStateFromGoldTagTree(Tree tree) {
    return initialStateFromTaggedSentence(tree.taggedYield());
  }

  public static State initialStateFromTaggedSentence(List<? extends HasWord> words) {
    List<Tree> preterminals = Generics.newArrayList();
    for (HasWord hw : words) {
      CoreLabel wordLabel = new CoreLabel();
      wordLabel.setValue(hw.word());
      if (!(hw instanceof HasTag)) {
        throw new RuntimeException("Expected tagged words");
      }
      String tag = ((HasTag) hw).tag();
      if (tag == null) {
        throw new RuntimeException("Word is not tagged");
      }
      CoreLabel tagLabel = new CoreLabel();
      tagLabel.setValue(((HasTag) hw).tag());
      
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
    op.setOptions("-forceTags", "-debugOutputFrequency", "1");
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
    TreeBinarizer binarizer = new TreeBinarizer(op.tlpParams.headFinder(), op.tlpParams.treebankLanguagePack(), false, false, 0, false, false, 0.0, false, true, true);
    BasicCategoryTreeTransformer basicTransformer = new BasicCategoryTreeTransformer(op.langpack());
    CompositeTreeTransformer transformer = new CompositeTreeTransformer();
    transformer.addTransformer(binarizer);
    transformer.addTransformer(basicTransformer);
      
    Treebank treebank = readTreebank(treebankPath, treebankFilter);
    treebank = treebank.transform(transformer);

    HeadFinder binaryHeadFinder = new BinaryHeadFinder(op.tlpParams.headFinder());
    List<Tree> binarizedTrees = Generics.newArrayList();
    for (Tree tree : treebank) {
      Trees.convertToCoreLabels(tree);
      tree.percolateHeadAnnotations(binaryHeadFinder);
      binarizedTrees.add(tree);
    }
    System.err.println("Converted trees to binarized format");
    return binarizedTrees;
  }

  public List<List<Transition>> createTransitionSequences(List<Tree> binarizedTrees) {
    List<List<Transition>> transitionLists = Generics.newArrayList();
    for (Tree tree : binarizedTrees) {
      List<Transition> transitions = CreateTransitionSequence.createTransitionSequence(tree, op.compoundUnaries);
      transitionLists.add(transitions);
    }
    return transitionLists;
  }

  // TODO: factor out the retagging?
  public static void redoTags(Tree tree, MaxentTagger tagger) {
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
    MaxentTagger tagger;

    public RetagProcessor(MaxentTagger tagger) {
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

  public static void redoTags(List<Tree> trees, MaxentTagger tagger, int nThreads) {
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
    final double delta;

    Update(List<String> features, int goldTransition, int predictedTransition, double delta) {
      this.features = features;
      this.goldTransition = goldTransition;
      this.predictedTransition = predictedTransition;
      this.delta = delta;
    }
  }

  private Pair<Integer, Integer> trainTree(int index, List<Tree> binarizedTrees, List<List<Transition>> transitionLists, List<Update> updates) {
    int numCorrect = 0;
    int numWrong = 0;

    Tree tree = binarizedTrees.get(index);
    List<Transition> transitions = transitionLists.get(index);

    State state = ShiftReduceParser.initialStateFromGoldTagTree(tree);
    for (Transition transition : transitions) {
      int transitionNum = transitionIndex.indexOf(transition);
      List<String> features = featureFactory.featurize(state);
      int predictedNum = findHighestScoringTransition(state, features, false).object();
      Transition predicted = transitionIndex.get(predictedNum);
      if (transitionNum == predictedNum) {
        numCorrect++;
      } else {
        numWrong++;
        // TODO: allow weighted features, weighted training, etc
        updates.add(new Update(features, transitionNum, predictedNum, 1.0));
      }
      if (op.trainingErrorHandling == ShiftReduceOptions.TrainingErrorHandling.EARLY_TERMINATION && transitionNum != predictedNum) {
        break;
      }
      state = transition.apply(state);
    }

    return Pair.makePair(numCorrect, numWrong);
  }

  private class TrainTreeProcessor implements ThreadsafeProcessor<Integer, Pair<Integer, Integer>> {
    List<Tree> binarizedTrees;
    List<List<Transition>> transitionLists;
    List<Update> updates; // this needs to be a synchronized list
    
    public TrainTreeProcessor(List<Tree> binarizedTrees, List<List<Transition>> transitionLists, List<Update> updates) {
      this.binarizedTrees = binarizedTrees;
      this.transitionLists = transitionLists;
      this.updates = updates;
    }

    public Pair<Integer, Integer> process(Integer index) {
      return trainTree(index, binarizedTrees, transitionLists, updates);
    }

    public TrainTreeProcessor newInstance() {
      // already threadsafe
      return this;
    }
  }

  private Triple<List<Update>, Integer, Integer> trainBatch(List<Integer> indices, List<Tree> binarizedTrees, List<List<Transition>> transitionLists, List<Update> updates, MulticoreWrapper<Integer, Pair<Integer, Integer>> wrapper) {
    int numCorrect = 0;
    int numWrong = 0;
    if (op.trainOptions.trainingThreads == 1) {
      for (Integer index : indices) {
        Pair<Integer, Integer> count = trainTree(index, binarizedTrees, transitionLists, updates);
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

  private void trainAndSave(String trainTreebankPath, FileFilter trainTreebankFilter,
                            String devTreebankPath, FileFilter devTreebankFilter,
                            String serializedPath) {
    List<Tree> binarizedTrees = readBinarizedTreebank(trainTreebankPath, trainTreebankFilter);

    int nThreads = op.trainOptions.trainingThreads;
    nThreads = nThreads <= 0 ? Runtime.getRuntime().availableProcessors() : nThreads;      

    MaxentTagger tagger = null;
    if (op.testOptions.preTag) {
      Timing retagTimer = new Timing();
      tagger = new MaxentTagger(op.testOptions.taggerSerializedFile);
      redoTags(binarizedTrees, tagger, nThreads);
      retagTimer.done("Retagging");
    }

    Timing transitionTimer = new Timing();
    List<List<Transition>> transitionLists = createTransitionSequences(binarizedTrees);
    for (List<Transition> transitions : transitionLists) {
      transitionIndex.addAll(transitions);
    }
    transitionTimer.done("Converting trees into transition lists");
    System.err.println("Number of transitions: " + transitionIndex.size());
    
    Random random = new Random(op.trainOptions.randomSeed);

    Treebank devTreebank = null;
    if (devTreebankPath != null) {
      devTreebank = readTreebank(devTreebankPath, devTreebankFilter);
    }

    double bestScore = 0.0;
    int bestIteration = 0;
    PriorityQueue<ScoredObject<ShiftReduceParser>> bestModels = null;
    if (op.averagedModels > 0) {
      bestModels = new PriorityQueue<ScoredObject<ShiftReduceParser>>(op.averagedModels + 1, ScoredComparator.ASCENDING_COMPARATOR);
    }

    List<Integer> indices = Generics.newArrayList();
    for (int i = 0; i < binarizedTrees.size(); ++i) {
      indices.add(i);
    }

    List<Update> updates = Generics.newArrayList();
    MulticoreWrapper<Integer, Pair<Integer, Integer>> wrapper = null;
    if (nThreads != 1) {
      updates = Collections.synchronizedList(updates);
      wrapper = new MulticoreWrapper<Integer, Pair<Integer, Integer>>(op.trainOptions.trainingThreads, new TrainTreeProcessor(binarizedTrees, transitionLists, updates));
    }

    for (int iteration = 1; iteration <= op.trainOptions.trainingIterations; ++iteration) {
      Timing trainingTimer = new Timing();
      int numCorrect = 0;
      int numWrong = 0;
      Collections.shuffle(indices, random);
      for (int start = 0; start < indices.size(); start += op.trainOptions.batchSize) {
        int end = Math.min(start + op.trainOptions.batchSize, indices.size());
        Triple<List<Update>, Integer, Integer> result = trainBatch(indices.subList(start, end), binarizedTrees, transitionLists, updates, wrapper);

        numCorrect += result.second;
        numWrong += result.third;

        for (Update update : result.first) {
          for (String feature : update.features) {
            List<ScoredObject<Integer>> weights = featureWeights.get(feature);
            if (weights == null) {
              weights = Generics.newArrayList();
              featureWeights.put(feature, weights);
            }
            updateWeight(weights, update.goldTransition, update.delta);
            updateWeight(weights, update.predictedTransition, -update.delta);
          }
        }
        updates.clear();
      }
      trainingTimer.done("Iteration " + iteration);
      System.err.println("While training, got " + numCorrect + " transitions correct and " + numWrong + " transitions wrong");
      outputStats();


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
        
        if (bestModels != null) {
          bestModels.add(new ScoredObject<ShiftReduceParser>(this.deepCopy(), labelF1));
          if (bestModels.size() > op.averagedModels) {
            bestModels.poll();
          }
        }
      }
      if (serializedPath != null && op.trainOptions.debugOutputFrequency > 0) {
        String tempName = serializedPath.substring(0, serializedPath.length() - 7) + "-" + FILENAME.format(iteration) + "-" + NF.format(labelF1) + ".ser.gz";
        try {
          IOUtils.writeObjectToFile(this, tempName);
        } catch (IOException e) {
          throw new RuntimeIOException(e);
        }
      }
    }

    if (wrapper != null) {
      wrapper.join();
    }

    if (bestModels != null) {
      if (op.cvAveragedModels && devTreebank != null) {
        List<ScoredObject<ShiftReduceParser>> models = Generics.newArrayList();
        while (bestModels.size() > 0) {
          models.add(bestModels.poll());
        }
        Collections.reverse(models);
        double bestF1 = 0.0;
        int bestSize = 0;
        for (int i = 1; i < models.size(); ++i) {
          System.err.println("Testing with " + i + " models averaged together");
          ShiftReduceParser parser = averageModels(models.subList(0, i));
          EvaluateTreebank evaluator = new EvaluateTreebank(parser.op, null, parser);
          evaluator.testOnTreebank(devTreebank);
          double labelF1 = evaluator.getLBScore();
          System.err.println("Label F1 for " + i + " models: " + labelF1);
          if (labelF1 > bestF1) {
            bestF1 = labelF1;
            bestSize = i;
          }
        }
        copyWeights(averageModels(models.subList(0, bestSize)));
      } else {
        copyWeights(ShiftReduceParser.averageModels(bestModels));
      }
    }

    condenseFeatures();

    if (serializedPath != null) {
      try {
        IOUtils.writeObjectToFile(this, serializedPath);
      } catch (IOException e) {
        throw new RuntimeIOException(e);
      }
    }
  }

  // java -mx5g edu.stanford.nlp.parser.shiftreduce.ShiftReduceParser -testTreebank ../data/parsetrees/wsj.dev.mrg -serializedPath foo.ser.gz
  // java -mx5g edu.stanford.nlp.parser.shiftreduce.ShiftReduceParser -testTreebank ../data/parsetrees/wsj.dev.mrg -serializedPath ../codebase/retagged7.ser.gz -preTag -taggerSerializedFile ../data/pos-tagger/distrib/wsj-0-18-bidirectional-nodistsim.tagger
  // java -mx10g edu.stanford.nlp.parser.shiftreduce.ShiftReduceParser -trainTreebank ../data/parsetrees/wsj.train.mrg -devTreebank ../data/parsetrees/wsj.dev.mrg -serializedPath foo.ser.gz
  // java -mx10g edu.stanford.nlp.parser.shiftreduce.ShiftReduceParser -trainTreebank ../data/parsetrees/wsj.train.mrg -devTreebank ../data/parsetrees/wsj.dev.mrg -preTag -taggerSerializedFile ../data/pos-tagger/distrib/wsj-0-18-bidirectional-nodistsim.tagger -trainingThreads 4 -batchSize 10 -serializedPath foo.ser.gz
  // Sources:
  //   A Classifier-Based Parser with Linear Run-Time Complexity (Kenji Sagae and Alon Lavie)
  //   Transition-Based Parsing of the Chinese Treebank using a Global Discriminative Model (Zhang and Clark)
  //   Fast and Accurate Shift-Reduce Constituent Parsing (Zhu et al)
  // Sources with stuff to implement:
  //   http://honnibal.wordpress.com/2013/12/18/a-simple-fast-algorithm-for-natural-language-dependency-parsing/
  //   Learning Sparser Perceptron Models (Goldberg and Elhadad) (unpublished)
  //   A Dynamic Oracle for Arc-Eager Dependency Parsing (Goldberg and Nivre)
  public static void main(String[] args) {
    List<String> remainingArgs = Generics.newArrayList();

    String trainTreebankPath = null;
    FileFilter trainTreebankFilter = null;
    String testTreebankPath = null;
    FileFilter testTreebankFilter = null;
    String devTreebankPath = null;
    FileFilter devTreebankFilter = null;

    String serializedPath = null;

    String tlppClass = null;

    for (int argIndex = 0; argIndex < args.length; ) {
      if (args[argIndex].equalsIgnoreCase("-trainTreebank")) {
        Pair<String, FileFilter> treebankDescription = ArgUtils.getTreebankDescription(args, argIndex, "-trainTreebank");
        argIndex = argIndex + ArgUtils.numSubArgs(args, argIndex) + 1;
        trainTreebankPath = treebankDescription.first();
        trainTreebankFilter = treebankDescription.second();
      } else if (args[argIndex].equalsIgnoreCase("-testTreebank")) {
        Pair<String, FileFilter> treebankDescription = ArgUtils.getTreebankDescription(args, argIndex, "-testTreebank");
        argIndex = argIndex + ArgUtils.numSubArgs(args, argIndex) + 1;
        testTreebankPath = treebankDescription.first();
        testTreebankFilter = treebankDescription.second();
      } else if (args[argIndex].equalsIgnoreCase("-devTreebank")) {
        Pair<String, FileFilter> treebankDescription = ArgUtils.getTreebankDescription(args, argIndex, "-devTreebank");
        argIndex = argIndex + ArgUtils.numSubArgs(args, argIndex) + 1;
        devTreebankPath = treebankDescription.first();
        devTreebankFilter = treebankDescription.second();
      } else if (args[argIndex].equalsIgnoreCase("-serializedPath")) {
        serializedPath = args[argIndex + 1];
        argIndex += 2;
      } else if (args[argIndex].equalsIgnoreCase("-tlpp")) {
        tlppClass = args[argIndex] + 1;
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
      ShiftReduceOptions op = buildTrainingOptions(tlppClass, newArgs);
      parser = new ShiftReduceParser(op);
      parser.trainAndSave(trainTreebankPath, trainTreebankFilter, devTreebankPath, devTreebankFilter, serializedPath);
    }

    if (serializedPath != null && parser == null) {
      try {
        parser = IOUtils.readObjectFromFile(serializedPath);
        parser.op.setOptions("-forceTags");
      } catch (IOException e) {
        throw new RuntimeIOException(e);
      } catch (ClassNotFoundException e) {
        throw new RuntimeIOException(e);
      }
      parser.op.setOptions(newArgs);
    }

    //parser.outputStats();

    if (testTreebankPath != null) {
      System.err.println("Loading test trees from " + testTreebankPath);
      Treebank testTreebank = parser.op.tlpParams.memoryTreebank();
      testTreebank.loadPath(testTreebankPath, testTreebankFilter);
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

