package edu.stanford.nlp.parser.shiftreduce;

import java.io.FileFilter;
import java.util.List;
import java.util.Set;

import edu.stanford.nlp.parser.lexparser.ArgUtils;
import edu.stanford.nlp.parser.lexparser.BinaryHeadFinder;
import edu.stanford.nlp.parser.lexparser.LexicalizedParser;
import edu.stanford.nlp.parser.lexparser.Options;
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

public class TrainParser {
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

    Index<Transition> transitionIndex = new HashIndex<Transition>();
    for (Tree tree : binarizedTrees) {
      List<Transition> transitions = CreateTransitionSequence.createTransitionSequence(tree);
      transitionIndex.addAll(transitions);
    }

    System.err.println(transitionIndex);

    Index<String> featureIndex = new HashIndex<String>();
    
  }
}
