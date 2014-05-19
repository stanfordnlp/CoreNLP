package edu.stanford.nlp.parser.shiftreduce;

import java.io.FileFilter;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Random;
import java.util.Set;

import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.io.RuntimeIOException;
import edu.stanford.nlp.parser.common.ArgUtils;
import edu.stanford.nlp.parser.lexparser.BinaryHeadFinder;
import edu.stanford.nlp.parser.lexparser.EvaluateTreebank;
import edu.stanford.nlp.parser.lexparser.Options;
import edu.stanford.nlp.parser.lexparser.TreeBinarizer;
import edu.stanford.nlp.trees.BasicCategoryTreeTransformer;
import edu.stanford.nlp.trees.CompositeTreeTransformer;
import edu.stanford.nlp.trees.HeadFinder;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.Treebank;
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

public class TrainParser {

  // java -mx5g edu.stanford.nlp.parser.shiftreduce.TrainParser -testTreebank ../data/parsetrees/wsj.dev.mrg -serializedPath foo.ser.gz
  // java -mx10g edu.stanford.nlp.parser.shiftreduce.TrainParser -trainTreebank ../data/parsetrees/wsj.train.mrg -devTreebank ../data/parsetrees/wsj.dev.mrg -serializedPath foo.ser.gz
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
      throw new IllegalArgumentException("Must specify a treebank to train from with -trainTreebank");
    }

    ShiftReduceParser parser = null;

    if (trainTreebankPath != null) {
      ShiftReduceOptions op = new ShiftReduceOptions();
      op.setOptions("-forceTags");
      if (tlppClass != null) {
        op.tlpParams = ReflectionLoading.loadByReflection(tlppClass);
      }
      op.setOptions(newArgs);

      if (op.trainOptions.randomSeed == 0) {
        op.trainOptions.randomSeed = (new Random()).nextLong();
        System.err.println("Random seed not set by options, using " + op.trainOptions.randomSeed);
      }

      TreeBinarizer binarizer = new TreeBinarizer(op.tlpParams.headFinder(), op.tlpParams.treebankLanguagePack(), false, false, 0, false, false, 0.0, false, true, true);
      BasicCategoryTreeTransformer basicTransformer = new BasicCategoryTreeTransformer(op.langpack());
      CompositeTreeTransformer transformer = new CompositeTreeTransformer();
      transformer.addTransformer(binarizer);
      transformer.addTransformer(basicTransformer);
      
      System.err.println("Loading training trees from " + trainTreebankPath);
      Treebank trainTreebank = op.tlpParams.memoryTreebank();
      trainTreebank.loadPath(trainTreebankPath, trainTreebankFilter);
      trainTreebank = trainTreebank.transform(transformer);
      System.err.println("Read in " + trainTreebank.size() + " trees from " + trainTreebankPath);

      HeadFinder binaryHeadFinder = new BinaryHeadFinder(op.tlpParams.headFinder());
      List<Tree> binarizedTrees = Generics.newArrayList();
      for (Tree tree : trainTreebank) {
        Trees.convertToCoreLabels(tree);
        tree.percolateHeadAnnotations(binaryHeadFinder);
        binarizedTrees.add(tree);
      }

      parser = new ShiftReduceParser(op);

      // TODO: the following training code should be a method of ShiftReduceParser

      Index<Transition> transitionIndex = parser.transitionIndex;
      FeatureFactory featureFactory = parser.featureFactory;
      Index<String> featureIndex = new HashIndex<String>();
      for (Tree tree : binarizedTrees) {
        List<Transition> transitions = CreateTransitionSequence.createTransitionSequence(tree, op.compoundUnaries);
        transitionIndex.addAll(transitions);

        State state = ShiftReduceParser.initialStateFromGoldTagTree(tree);
        for (Transition transition : transitions) {
          Set<String> features = featureFactory.featurize(state);
          featureIndex.addAll(features);
          state = transition.apply(state);
        }
      }

      System.err.println("Number of unique features: " + featureIndex.size());
      System.err.println("Number of transitions: " + transitionIndex.size());
      System.err.println("Feature space will be " + (featureIndex.size() * transitionIndex.size()));
      
      Map<String, List<ScoredObject<Integer>>> featureWeights = parser.featureWeights;
      for (String feature : featureIndex) {
        List<ScoredObject<Integer>> weights = Generics.newArrayList();
        featureWeights.put(feature, weights);
      }

      Random random = new Random(parser.op.trainOptions.randomSeed);

      Treebank devTreebank = null;
      if (devTreebankPath != null) {
        System.err.println("Loading dev trees from " + devTreebankPath);
        devTreebank = parser.op.tlpParams.memoryTreebank();
        devTreebank.loadPath(devTreebankPath, devTreebankFilter);
        System.err.println("Loaded " + devTreebank.size() + " trees");
      }

      double bestScore = 0.0;
      int bestIteration = 0;
      PriorityQueue<ScoredObject<ShiftReduceParser>> bestModels = null;
      if (parser.op.averagedModels > 0) {
        bestModels = new PriorityQueue<ScoredObject<ShiftReduceParser>>(parser.op.averagedModels + 1, ScoredComparator.ASCENDING_COMPARATOR);
      }

      for (int iteration = 1; iteration <= parser.op.trainOptions.trainingIterations; ++iteration) {
        int numCorrect = 0;
        int numWrong = 0;
        Collections.shuffle(binarizedTrees, random);
        for (Tree tree : binarizedTrees) {
          List<Transition> transitions = CreateTransitionSequence.createTransitionSequence(tree, op.compoundUnaries);
          State state = ShiftReduceParser.initialStateFromGoldTagTree(tree);
          for (Transition transition : transitions) {
            int transitionNum = transitionIndex.indexOf(transition);
            Set<String> features = featureFactory.featurize(state);
            int predictedNum = parser.findHighestScoringTransition(state, features, false).object();
            Transition predicted = transitionIndex.get(predictedNum);
            if (transitionNum == predictedNum) {
              numCorrect++;
            } else {
              numWrong++;
              for (String feature : features) {
                List<ScoredObject<Integer>> weights = featureWeights.get(feature);
                // TODO: allow weighted features, weighted training, etc
                ShiftReduceParser.updateWeight(weights, transitionNum, 1.0);
                ShiftReduceParser.updateWeight(weights, predictedNum, -1.0);
              }
            }
            state = transition.apply(state);
          }
        }
        System.err.println("Iteration " + iteration + " complete");
        System.err.println("While training, got " + numCorrect + " transitions correct and " + numWrong + " transitions wrong");

        if (devTreebank != null) {
          EvaluateTreebank evaluator = new EvaluateTreebank(parser.op, null, parser);
          evaluator.testOnTreebank(devTreebank);
          double labelF1 = evaluator.getLBScore();
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
            bestModels.add(new ScoredObject<ShiftReduceParser>(parser.deepCopy(), labelF1));
            if (bestModels.size() > parser.op.averagedModels) {
              bestModels.poll();
            }
          }
        }
      }

      if (bestModels != null) {
        List<ShiftReduceParser> models = CollectionUtils.transformAsList(bestModels, new Function<ScoredObject<ShiftReduceParser>, ShiftReduceParser>() { public ShiftReduceParser apply(ScoredObject<ShiftReduceParser> object) { return object.object(); }});
        parser = ShiftReduceParser.averageModels(models);
      }

      parser.condenseFeatures();

      if (serializedPath != null) {
        try {
          IOUtils.writeObjectToFile(parser, serializedPath);
        } catch (IOException e) {
          throw new RuntimeIOException(e);
        }
      }
    }

    if (serializedPath != null && parser == null) {
      try {
        parser = IOUtils.readObjectFromFile(serializedPath);
      } catch (IOException e) {
        throw new RuntimeIOException(e);
      } catch (ClassNotFoundException e) {
        throw new RuntimeIOException(e);
      }
      parser.op.setOptions("-forceTags");
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
}
