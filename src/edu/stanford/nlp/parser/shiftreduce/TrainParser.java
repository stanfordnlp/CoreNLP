package edu.stanford.nlp.parser.shiftreduce;

import java.io.FileFilter;
import java.util.List;
import java.util.Set;

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

  static State initialStateFromTrainingTree(Tree tree) {
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

    for (int argIndex = 0; argIndex < args.length; ) {
      if (args[argIndex].equalsIgnoreCase("-treebank")) {
        Pair<String, FileFilter> treebankDescription = ArgUtils.getTreebankDescription(args, argIndex, "-treebank");
        argIndex = argIndex + ArgUtils.numSubArgs(args, argIndex) + 1;
        trainTreebankPath = treebankDescription.first();
        trainTreebankFilter = treebankDescription.second();
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
    Treebank treebank = op.tlpParams.memoryTreebank();;
    treebank.loadPath(trainTreebankPath, trainTreebankFilter);
    treebank = treebank.transform(transformer);
    System.err.println("Read in " + treebank.size() + " trees from " + trainTreebankPath);

    HeadFinder binaryHeadFinder = new BinaryHeadFinder(op.tlpParams.headFinder());
    List<Tree> binarizedTrees = Generics.newArrayList();
    for (Tree tree : treebank) {
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

      State state = initialStateFromTrainingTree(tree);
      for (Transition transition : transitions) {
        Set<String> features = featureFactory.featurize(state);
        featureIndex.addAll(features);
        state = transition.apply(state);
      }
    }
  }
}
