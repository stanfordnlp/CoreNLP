package edu.stanford.nlp.parser.shiftreduce;

import java.io.FileFilter;
import java.io.IOException;
import java.util.List;
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
import edu.stanford.nlp.util.Generics;
import edu.stanford.nlp.util.HashIndex;
import edu.stanford.nlp.util.Index;
import edu.stanford.nlp.util.Pair;
import edu.stanford.nlp.util.ReflectionLoading;

public class TrainParser {

  // java -mx15g edu.stanford.nlp.parser.shiftreduce.TrainParser -testTreebank ../data/parsetrees/wsj.dev.mrg -serializedPath foo.ser.gz
  // java -mx15g edu.stanford.nlp.parser.shiftreduce.TrainParser -trainTreebank ../data/parsetrees/wsj.train.mrg -testTreebank ../data/parsetrees/wsj.dev.mrg -serializedPath foo.ser.gz
  public static void main(String[] args) {
    List<String> remainingArgs = Generics.newArrayList();

    String trainTreebankPath = null;
    FileFilter trainTreebankFilter = null;
    String testTreebankPath = null;
    FileFilter testTreebankFilter = null;
    int numTrainingIterations = 10;

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
      } else if (args[argIndex].equalsIgnoreCase("-numTrainingIterations")) {
        numTrainingIterations = Integer.valueOf(args[argIndex + 1]);
        argIndex += 2;
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
      // TODO: since Options and buildTrainTransformer are used in so
      // many different places, it would make sense to factor that out
      ShiftReduceOptions op = new ShiftReduceOptions();
      op.setOptions("-forceTags");
      if (tlppClass != null) {
        op.tlpParams = ReflectionLoading.loadByReflection(tlppClass);
      }
      op.setOptions(newArgs);

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

      // TODO: allow different feature factories, such as for different languages
      FeatureFactory featureFactory = new BasicFeatureFactory();

      Index<Transition> transitionIndex = new HashIndex<Transition>();
      Index<String> featureIndex = new HashIndex<String>();
      for (Tree tree : binarizedTrees) {
        List<Transition> transitions = CreateTransitionSequence.createTransitionSequence(tree);
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
      
      double[][] featureWeights = new double[transitionIndex.size()][featureIndex.size()];

      parser = new ShiftReduceParser(transitionIndex, featureIndex, featureWeights, op, featureFactory);

      for (int i = 0; i < numTrainingIterations; ++i) {
        int numCorrect = 0;
        int numWrong = 0;
        for (Tree tree : binarizedTrees) {
          List<Transition> transitions = CreateTransitionSequence.createTransitionSequence(tree);
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
                int featureNum = featureIndex.indexOf(feature);
                // TODO: allow weighted features, weighted training, etc
                featureWeights[predictedNum][featureNum] -= 1.0;
                featureWeights[transitionNum][featureNum] += 1.0;
              }
            }
            state = transition.apply(state);
          }
        }
        System.err.println("Iteration " + i + " complete");
        System.err.println("While training, got " + numCorrect + " transitions correct and " + numWrong + " transitions wrong");
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
