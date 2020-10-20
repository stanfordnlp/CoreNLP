package edu.stanford.nlp.parser.lexparser; 
import edu.stanford.nlp.util.logging.Redwood;

import java.util.*;
import java.io.FileFilter;
import java.io.PrintWriter;

import edu.stanford.nlp.io.NumberRangesFileFilter;
import edu.stanford.nlp.io.NumberRangeFileFilter;
import edu.stanford.nlp.ling.Label;
import edu.stanford.nlp.ling.CategoryWordTag;
import edu.stanford.nlp.ling.CategoryWordTagFactory;
import edu.stanford.nlp.ling.StringLabelFactory;
import edu.stanford.nlp.ling.Word;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.trees.*;
import edu.stanford.nlp.util.Triple;


public class TreeAnnotatorAndBinarizer implements TreeTransformer  {

  /** A logger for this class */
  private static Redwood.RedwoodChannels log = Redwood.channels(TreeAnnotatorAndBinarizer.class);

  private final TreeFactory tf;
  private final TreebankLanguagePack tlp;
  private final TreeTransformer annotator;
  private final TreeBinarizer binarizer;
  private final PostSplitter postSplitter;
  private final boolean forceCNF;
  private final TrainOptions trainOptions;
  private final ClassicCounter<Tree> annotatedRuleCounts;
  private final ClassicCounter<String> annotatedStateCounts;

  public TreeAnnotatorAndBinarizer(TreebankLangParserParams tlpParams, boolean forceCNF, boolean insideFactor, boolean doSubcategorization, Options op) {
    this(tlpParams.headFinder(), tlpParams.headFinder(), tlpParams, forceCNF, insideFactor, doSubcategorization, op);
  }

  public TreeAnnotatorAndBinarizer(HeadFinder annotationHF, HeadFinder binarizationHF, TreebankLangParserParams tlpParams, boolean forceCNF, boolean insideFactor, boolean doSubcategorization, Options op) {
    this.trainOptions = op.trainOptions;
    if (doSubcategorization) {
      annotator = new TreeAnnotator(annotationHF, tlpParams, op);
    } else {
      annotator = new TreeNullAnnotator(annotationHF);
    }
    binarizer = new TreeBinarizer(binarizationHF, tlpParams.treebankLanguagePack(), insideFactor, trainOptions.markovFactor, trainOptions.markovOrder, trainOptions.compactGrammar() > 0, trainOptions.compactGrammar() > 1, trainOptions.HSEL_CUT, trainOptions.markFinalStates, trainOptions.simpleBinarizedLabels, trainOptions.noRebinarization);
    if (trainOptions.selectivePostSplit) {
      postSplitter = new PostSplitter(tlpParams, op);
    } else {
      postSplitter = null;
    }
    this.tf = new LabeledScoredTreeFactory(new CategoryWordTagFactory());
    this.tlp = tlpParams.treebankLanguagePack();
    this.forceCNF = forceCNF;
    if (trainOptions.printAnnotatedRuleCounts) {
      annotatedRuleCounts = new ClassicCounter<>();
    } else {
      annotatedRuleCounts = null;
    }
    if (trainOptions.printAnnotatedStateCounts) {
      annotatedStateCounts = new ClassicCounter<>();
    } else {
      annotatedStateCounts = null;
    }
  }

  public void dumpStats() {
    if (trainOptions.selectivePostSplit) {
      postSplitter.dumpStats();
    }
  }

  public void setDoSelectiveSplit(boolean doSelectiveSplit) {
    binarizer.setDoSelectiveSplit(doSelectiveSplit);
  }

  /**
   * Changes the ROOT label, and adds a Lexicon.BOUNDARY daughter to it.
   * This is needed for the dependency parser.
   * <i>Note:</i> This is a destructive operation on the tree passed in!!
   *
   * @param t The current tree into which a boundary is inserted
   */
  public void addRoot(Tree t) {
    if (t.isLeaf()) {
      log.info("Warning: tree is leaf: " + t);
      t = tf.newTreeNode(tlp.startSymbol(), Collections.singletonList(t));
    }
    t.setLabel(new CategoryWordTag(tlp.startSymbol(), Lexicon.BOUNDARY, Lexicon.BOUNDARY_TAG));
    List<Tree> preTermChildList = new ArrayList<>();
    Tree boundaryTerm = tf.newLeaf(new Word(Lexicon.BOUNDARY));//CategoryWordTag(Lexicon.BOUNDARY,Lexicon.BOUNDARY,""));
    preTermChildList.add(boundaryTerm);
    Tree boundaryPreTerm = tf.newTreeNode(new CategoryWordTag(Lexicon.BOUNDARY_TAG, Lexicon.BOUNDARY, Lexicon.BOUNDARY_TAG), preTermChildList);
    List<Tree> childList = t.getChildrenAsList();
    childList.add(boundaryPreTerm);
    t.setChildren(childList);
  }

  /** The tree t is normally expected to be a Penn-Treebank-style tree
   *  in which the top node is an extra node that has a unary expansion.
   *  If this isn't the case, an extra node is added and the user is warned.
   */
  @Override
  public Tree transformTree(Tree t) {
    if (trainOptions.printTreeTransformations > 0) {
      trainOptions.printTrainTree(null, "ORIGINAL TREE:", t);
    }
    Tree trTree = annotator.transformTree(t);
    if (trainOptions.selectivePostSplit) {
      trTree = postSplitter.transformTree(trTree);
    }
    if (trainOptions.printTreeTransformations > 0) {
      trainOptions.printTrainTree(trainOptions.printAnnotatedPW, "ANNOTATED TREE:", trTree);
    }
    if (trainOptions.printAnnotatedRuleCounts) {
      Tree tr2 = trTree.deepCopy(new LabeledScoredTreeFactory(), new StringLabelFactory());
      Set<Tree> localTrees = tr2.localTrees();
      for (Tree tr : localTrees) {
        annotatedRuleCounts.incrementCount(tr);
      }
    }
    if (trainOptions.printAnnotatedStateCounts) {
      for (Tree subt : trTree) {
        if ( ! subt.isLeaf()) {
          annotatedStateCounts.incrementCount(subt.label().value());
        }
      }
    }

    // if we add the ROOT first, then we don't know how to percolate the heads at the top
    addRoot(trTree); // this creates a few non-binarized rules at the top

    Tree binarizedTree = binarizer.transformTree(trTree);
    if (trainOptions.printTreeTransformations > 0) {
      trainOptions.printTrainTree(trainOptions.printBinarizedPW, "BINARIZED TREE:", binarizedTree);
      trainOptions.printTreeTransformations--;
    }
    if (forceCNF) {
      binarizedTree = new CNFTransformers.ToCNFTransformer().transformTree(binarizedTree);
      //        System.out.println("BinarizedCNF:\n");
      //        binarizedTree.pennPrint();
    }
    return binarizedTree;
  }

  public void printRuleCounts() {
    log.info();
    for (Tree t : annotatedRuleCounts.keySet()) {
      log.info(annotatedRuleCounts.getCount(t) + "\t" +
                       t.label().value() + " -->");
      for (Tree dtr : t.getChildrenAsList()) {
        log.info(" ");
        log.info(dtr.label().value());
      }
      log.info();
    }
  }

  public void printStateCounts() {
    log.info();
    log.info("Annotated state counts");
    Set<String> keys = annotatedStateCounts.keySet();
    List<String> keyList = new ArrayList<>(keys);
    Collections.sort(keyList);
    for (String s : keyList) {
      log.info(s + "\t" + annotatedStateCounts.getCount(s));
    }
  }


  // main helper function
  private static int numSubArgs(String[] args, int index) {
    int i = index;
    while (i + 1 < args.length && args[i + 1].charAt(0) != '-') {
      i++;
    }
    return i - index;
  }


  private static void removeDeleteSplittersFromSplitters(TreebankLanguagePack tlp, Options op) {
    if (op.trainOptions.deleteSplitters != null) {
      List<String> deleted = new ArrayList<>();
      for (String del : op.trainOptions.deleteSplitters) {
        String baseDel = tlp.basicCategory(del);
        boolean checkBasic = del.equals(baseDel);
        for (Iterator<String> it = op.trainOptions.splitters.iterator(); it.hasNext(); ) {
          String elem = it.next();
          String baseElem = tlp.basicCategory(elem);
          boolean delStr = checkBasic && baseElem.equals(baseDel) || elem.equals(del);
          if (delStr) {
            it.remove();
            deleted.add(elem);
          }
        }
      }
      if (op.testOptions.verbose) {
        log.info("Removed from vertical splitters: " + deleted);
      }
    }
  }


  /** @return A Triple of binaryTrainTreebank, binarySecondaryTreebank, binaryTuneTreebank.
   */
  public static Triple<Treebank, Treebank, Treebank> getAnnotatedBinaryTreebankFromTreebank(Treebank trainTreebank,
      Treebank secondaryTreebank,
      Treebank tuneTreebank,
      Options op) {
    // setup tree transforms
    TreebankLangParserParams tlpParams = op.tlpParams;
    TreebankLanguagePack tlp = tlpParams.treebankLanguagePack();

    if (op.testOptions.verbose) {
      PrintWriter pwErr = tlpParams.pw(System.err);
      pwErr.print("Training ");
      pwErr.println(trainTreebank.textualSummary(tlp));
      if (secondaryTreebank != null) {
        pwErr.print("Secondary training ");
        pwErr.println(secondaryTreebank.textualSummary(tlp));
      }
    }

    CompositeTreeTransformer trainTransformer =
      new CompositeTreeTransformer();
    if (op.trainOptions.preTransformer != null) {
      trainTransformer.addTransformer(op.trainOptions.preTransformer);
    }
    if (op.trainOptions.collinsPunc) {
      CollinsPuncTransformer collinsPuncTransformer =
        new CollinsPuncTransformer(tlp);
      trainTransformer.addTransformer(collinsPuncTransformer);
    }

    log.info("Binarizing trees...");
    TreeAnnotatorAndBinarizer binarizer;
    if (!op.trainOptions.leftToRight) {
      binarizer = new TreeAnnotatorAndBinarizer(tlpParams, op.forceCNF, !op.trainOptions.outsideFactor(), !op.trainOptions.predictSplits, op);
    } else {
      binarizer = new TreeAnnotatorAndBinarizer(tlpParams.headFinder(), new LeftHeadFinder(), tlpParams, op.forceCNF, !op.trainOptions.outsideFactor(), !op.trainOptions.predictSplits, op);
    }
    trainTransformer.addTransformer(binarizer);

    if (op.wordFunction != null) {
      TreeTransformer wordFunctionTransformer =
        new TreeLeafLabelTransformer(op.wordFunction);
      trainTransformer.addTransformer(wordFunctionTransformer);
    }

    Treebank wholeTreebank;
    if (secondaryTreebank == null) {
      wholeTreebank = trainTreebank;
    } else {
      wholeTreebank = new CompositeTreebank(trainTreebank, secondaryTreebank);
    }

    if (op.trainOptions.selectiveSplit) {
      op.trainOptions.splitters = ParentAnnotationStats.getSplitCategories(wholeTreebank, op.trainOptions.tagSelectiveSplit, 0, op.trainOptions.selectiveSplitCutOff, op.trainOptions.tagSelectiveSplitCutOff, tlp);
      removeDeleteSplittersFromSplitters(tlp, op);
      if (op.testOptions.verbose) {
        List<String> list = new ArrayList<>(op.trainOptions.splitters);
        Collections.sort(list);
        log.info("Parent split categories: " + list);
      }
    }

    if (op.trainOptions.selectivePostSplit) {
      // Do all the transformations once just to learn selective splits on annotated categories
      TreeTransformer myTransformer = new TreeAnnotator(tlpParams.headFinder(), tlpParams, op);
      wholeTreebank = wholeTreebank.transform(myTransformer);
      op.trainOptions.postSplitters = ParentAnnotationStats.getSplitCategories(wholeTreebank, true, 0, op.trainOptions.selectivePostSplitCutOff, op.trainOptions.tagSelectivePostSplitCutOff, tlp);
      if (op.testOptions.verbose) {
        log.info("Parent post annotation split categories: " + op.trainOptions.postSplitters);
      }
    }
    if (op.trainOptions.hSelSplit) {
      // We run through all the trees once just to gather counts for hSelSplit!
      int ptt = op.trainOptions.printTreeTransformations;
      op.trainOptions.printTreeTransformations = 0;
      binarizer.setDoSelectiveSplit(false);
      for (Tree tree : wholeTreebank) {
        trainTransformer.transformTree(tree);
      }
      binarizer.setDoSelectiveSplit(true);
      op.trainOptions.printTreeTransformations = ptt;
    }
    // we've done all the setup now. here's where the train treebank is transformed.
    trainTreebank = trainTreebank.transform(trainTransformer);
    if (secondaryTreebank != null) {
      secondaryTreebank = secondaryTreebank.transform(trainTransformer);
    }
    if (op.trainOptions.printAnnotatedStateCounts) {
      binarizer.printStateCounts();
    }
    if (op.trainOptions.printAnnotatedRuleCounts) {
      binarizer.printRuleCounts();
    }

    if (tuneTreebank != null) {
      tuneTreebank = tuneTreebank.transform(trainTransformer);
    }

    if (op.testOptions.verbose) {
      binarizer.dumpStats();
    }

    return new Triple<>(trainTreebank, secondaryTreebank, tuneTreebank);
  }


  /** Lets you test out the TreeAnnotatorAndBinarizer on the command line.
   *
   *  @param args Command line arguments: All flags accepted by FactoredParser.setOptionFlag
   *     and -train treebankPath [fileRanges]
   */
  public static void main(String[] args) {
    Options op = new Options();
    String treebankPath = null;
    FileFilter trainFilter = null;

    int i = 0;
    while (i < args.length && args[i].startsWith("-")) {
      if (args[i].equalsIgnoreCase("-train")) {
        int numSubArgs = numSubArgs(args, i);
        i++;
        if (numSubArgs >= 1) {
          treebankPath = args[i];
          i++;
        } else {
          throw new RuntimeException("Error: -train option must have treebankPath as first argument.");
        }
        if (numSubArgs == 2) {
          trainFilter = new NumberRangesFileFilter(args[i++], true);
        } else if (numSubArgs >= 3) {
          int low = Integer.parseInt(args[i]);
          int high = Integer.parseInt(args[i + 1]);
          trainFilter = new NumberRangeFileFilter(low, high, true);
          i += 2;
        }
      } else {
        i = op.setOption(args, i);
      }
    }
    if (i < args.length) {
      log.info("usage: java TreeAnnotatorAndBinarizer options*");
      log.info("  Options are like for lexicalized parser including -train treebankPath fileRange]");
      return;
    }

    log.info("Annotating from treebank dir: " + treebankPath);
    Treebank trainTreebank = op.tlpParams.diskTreebank();
    if (trainFilter == null) {
      trainTreebank.loadPath(treebankPath);
    } else {
      trainTreebank.loadPath(treebankPath, trainFilter);
    }

    Treebank binaryTrainTreebank = getAnnotatedBinaryTreebankFromTreebank(trainTreebank, null, null, op).first();

    Iterator<Tree> it = trainTreebank.iterator();
    for (Tree t : binaryTrainTreebank) {
      System.out.println("Original tree:");
      it.next().pennPrint();
      System.out.println("Binarized tree:");
      t.pennPrint();
      System.out.println();
    }
  } // end main



  /** This does nothing but a function to change the tree nodes into
   *  CategoryWordTag, while the leaves are StringLabels. That's what the
   *  rest of the code assumes.
   */
  static class TreeNullAnnotator implements TreeTransformer {

    private final TreeFactory tf =
      new LabeledScoredTreeFactory(new CategoryWordTagFactory());
    private final HeadFinder hf;

    public Tree transformTree(Tree t) {
      // make a defensive copy which the helper method can then mangle
      Tree copy = t.treeSkeletonCopy(tf);
      return transformTreeHelper(copy);
    }

    private Tree transformTreeHelper(Tree t) {
      if (t != null) {
        String cat = t.label().value();
        if (t.isLeaf()) {
          Label label = new Word(cat); //new CategoryWordTag(cat,cat,"");
          t.setLabel(label);
        } else {
          Tree[] kids = t.children();
          for (Tree child : kids) {
            transformTreeHelper(child); // recursive call
          }
          Tree headChild = hf.determineHead(t);
          String tag;
          String word;
          if (headChild == null) {
            log.error("null head for tree\n" + t.toString());
            word = null;
            tag = null;
          } else if (headChild.isLeaf()) {
            tag = cat;
            word = headChild.label().value();
          } else {
            CategoryWordTag headLabel = (CategoryWordTag) headChild.label();
            word = headLabel.word();
            tag = headLabel.tag();
          }
          Label label = new CategoryWordTag(cat, word, tag);
          t.setLabel(label);
        }
      }
      return t;
    }

    public TreeNullAnnotator(HeadFinder hf) {
      this.hf = hf;
    }

  } // end static class TreeNullAnnotator

} // end class TreeAnnotatorAndBinarizer
