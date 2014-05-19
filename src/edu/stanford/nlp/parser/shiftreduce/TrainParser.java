package edu.stanford.nlp.parser.shiftreduce;

import java.io.FileFilter;
import java.io.IOException;
import java.util.List;
import java.util.Set;

import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.io.RuntimeIOException;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.TaggedWord;
import edu.stanford.nlp.parser.lexparser.ArgUtils;
import edu.stanford.nlp.parser.lexparser.BinaryHeadFinder;
import edu.stanford.nlp.parser.lexparser.LexicalizedParser;
import edu.stanford.nlp.parser.lexparser.Options;
import edu.stanford.nlp.trees.BasicCategoryTreeTransformer;
import edu.stanford.nlp.trees.CompositeTreeTransformer;
import edu.stanford.nlp.trees.HeadFinder;
import edu.stanford.nlp.trees.LabeledScoredTreeNode;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.Treebank;
import edu.stanford.nlp.trees.TreeCoreAnnotations;
import edu.stanford.nlp.trees.Trees;
import edu.stanford.nlp.util.Generics;
import edu.stanford.nlp.util.HashIndex;
import edu.stanford.nlp.util.Index;
import edu.stanford.nlp.util.Pair;

public class TrainParser {

  static int findHighestScoringTransition(Index<String> featureIndex, Index<Transition> transitionIndex, double[][] featureWeights, State state, Set<String> features, boolean requireLegal) {
    double[] scores = new double[featureWeights.length];
    for (String feature : features) {
      int featureNum = featureIndex.indexOf(feature);
      if (featureNum >= 0) {
        // Features not in our index are represented by < 0 and are ignored
        for (int i = 0; i < scores.length; ++i) {
          scores[i] += featureWeights[i][featureNum];
        }
      }
    }

    int bestFeature = -1;
    for (int i = 0; i < scores.length; ++i) {
      if ((bestFeature < 0 || scores[i] > scores[bestFeature]) && 
          (!requireLegal || transitionIndex.get(i).isLegal(state))) {
        bestFeature = i;
      }
    }
    
    return bestFeature;
  }

  static State initialStateFromGoldTagTree(Tree tree) {
    List<Tree> preterminals = Generics.newArrayList();
    for (TaggedWord tw : tree.taggedYield()) {
      CoreLabel word = new CoreLabel();
      word.setValue(tw.word());
      CoreLabel tag = new CoreLabel();
      tag.setValue(tw.tag());
      
      LabeledScoredTreeNode wordNode = new LabeledScoredTreeNode(word);
      LabeledScoredTreeNode tagNode = new LabeledScoredTreeNode(tag);
      tagNode.addChild(wordNode);

      word.set(TreeCoreAnnotations.HeadWordAnnotation.class, wordNode);
      word.set(TreeCoreAnnotations.HeadTagAnnotation.class, tagNode);
      tag.set(TreeCoreAnnotations.HeadWordAnnotation.class, wordNode);
      tag.set(TreeCoreAnnotations.HeadTagAnnotation.class, tagNode);

      preterminals.add(tagNode);
    }
    return new State(preterminals);
  }

  public static void main(String[] args) {
    List<String> remainingArgs = Generics.newArrayList();

    String trainTreebankPath = null;
    FileFilter trainTreebankFilter = null;
    String testTreebankPath = null;
    FileFilter testTreebankFilter = null;
    int numTrainingIterations = 10;

    String serializedPath = null;

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
      } else {
        remainingArgs.add(args[argIndex]);
        ++argIndex;
      }
    }

    // TODO: do something with the remaining args, such as set the Options flags...
    // TODO: allow for different languages; by default this does English
    // TODO: since Options and buildTrainTransformer are used in so
    // many different places, it would make sense to factor that out
    Options op = new Options();
    CompositeTreeTransformer transformer = LexicalizedParser.buildTrainTransformer(op);
    BasicCategoryTreeTransformer basicTransformer = new BasicCategoryTreeTransformer(op.langpack());
    transformer.addTransformer(basicTransformer);
    
    if (trainTreebankPath == null) {
      throw new IllegalArgumentException("Must specify a treebank to train from with -treebank");
    }

    System.err.println("Loading training trees from " + trainTreebankPath);
    Treebank trainTreebank = op.tlpParams.memoryTreebank();;
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

      State state = initialStateFromGoldTagTree(tree);
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
    for (int i = 0; i < numTrainingIterations; ++i) {
      int numCorrect = 0;
      int numWrong = 0;
      for (Tree tree : binarizedTrees) {
        List<Transition> transitions = CreateTransitionSequence.createTransitionSequence(tree);
        State state = initialStateFromGoldTagTree(tree);
        for (Transition transition : transitions) {
          int transitionNum = transitionIndex.indexOf(transition);
          Set<String> features = featureFactory.featurize(state);
          int predictedNum = findHighestScoringTransition(featureIndex, transitionIndex, featureWeights, state, features, false);
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

    ShiftReduceParser parser = new ShiftReduceParser(transitionIndex, featureIndex, featureWeights);
    if (serializedPath != null) {
      try {
        IOUtils.writeObjectToFile(parser, serializedPath);
      } catch (IOException e) {
        throw new RuntimeIOException(e);
      }
    }

    if (testTreebankPath != null) {
      System.err.println("Loading test trees from " + testTreebankPath);
      Treebank testTreebank = op.tlpParams.memoryTreebank();
      testTreebank.loadPath(testTreebankPath, testTreebankFilter);
      for (Tree tree : testTreebank) {
        State state = initialStateFromGoldTagTree(tree);
        List<Transition> transitions = Generics.newArrayList();
        while (!state.finished) {
          Set<String> features = featureFactory.featurize(state);
          int predictedNum = findHighestScoringTransition(featureIndex, transitionIndex, featureWeights, state, features, true);
          Transition transition = transitionIndex.get(predictedNum);
          state = transition.apply(state);
          transitions.add(transition);
          /*
          System.err.println("Predicted transition " + transition);
          System.err.println(state);
          if (transitions.size() > 200) {
            System.exit(1);
          }
          */
        }
        System.err.println("Input tree: " + tree);
        System.err.println("Parsed tree: " + state.stack.peek());
        System.err.println("Predicted transition sequence: " + transitions);
      }
    }
  }
}
