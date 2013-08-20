package edu.stanford.nlp.parser.lexparser;

import edu.stanford.nlp.io.NumberRangeFileFilter;
import edu.stanford.nlp.ling.*;
import edu.stanford.nlp.objectbank.TokenizerFactory;
import edu.stanford.nlp.process.WhitespaceTokenizer;
import edu.stanford.nlp.process.WordToSentenceProcessor;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.trees.*;
import edu.stanford.nlp.util.HashIndex;
import edu.stanford.nlp.util.Index;
import edu.stanford.nlp.util.Pair;
import edu.stanford.nlp.util.ReflectionLoading;
import edu.stanford.nlp.util.Timing;
import edu.stanford.nlp.wsd.synsense.SubcatMarker;
import edu.stanford.nlp.wsd.synsense.Subcategory;

import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.util.*;

/**
 * Class used at command line to train a subcat parser and serialize it
 * to disk for later use.
 *
 * @author Teg Grenager
 * @author Galen Andrew
 * @author Anna Rafferty (refactoring unknown word model)
 */
public class SubcatParserTrainer {

  private SubcatParserTrainer() {}

  //  static TreebankLangParserParams tlpParams;


  /**
   * Usage: java SubcatParserTrainer -path treebankPath -train low high -saveToTextFile parserFilename
   *
   * @param args Command-line args, as above
   */
  public static void main(String[] args) {
    //    tlpParams = new EnglishTreebankParserParams();
    //    Options.get().tlpParams = tlpParams;
    //    BinarizerFactory.TreeAnnotator.setTreebankLang(tlpParams);

    Options op = new Options();

    op.trainOptions.sisterSplitters = new HashSet<String>(Arrays.asList(op.tlpParams.sisterSplitters()));

    Set<String> targets = null;

    System.out.println("Currently " + new Date());
    System.out.print("Invoked with arguments:");
    for (String arg : args) {
      System.out.print(" " + arg);
    }

    System.out.println();
    int trainLow = 200, trainHigh = 299, testLow = -1, testHigh = -1, heldoutLow = -1, heldoutHigh = -1;
    String path = "/u/nlp/stuff/corpora/Treebank3/parsed/mrg/wsj";
    String binaryOutFile = null;
    String textGrammarOutFile = null;
    String textGrammarInFile = null;
    String testFile = null;

    int i = 0;
    while (i < args.length && args[i].startsWith("-")) {
      if (args[i].equalsIgnoreCase("-path") && (i + 1 < args.length)) {
        path = args[i + 1];
        i += 2;
      } else if (args[i].equalsIgnoreCase("-train") && (i + 2 < args.length)) {
        trainLow = Integer.parseInt(args[i + 1]);
        trainHigh = Integer.parseInt(args[i + 2]);
        i += 3;
      } else if (args[i].equalsIgnoreCase("-test") && (i + 2 < args.length)) {
        testLow = Integer.parseInt(args[i + 1]);
        testHigh = Integer.parseInt(args[i + 2]);
        i += 3;
      } else if (args[i].equalsIgnoreCase("-parse") && (i + 1 < args.length)) {
        testFile = args[i + 1];
        i += 2;
      } else if (args[i].equalsIgnoreCase("-saveToBinary") && (i + 1 < args.length)) {
        binaryOutFile = args[i + 1];
        i += 2;
      } else if (args[i].equalsIgnoreCase("-saveToTextFile") && (i + 1 < args.length)) {
        textGrammarOutFile = args[i + 1];
        i += 2;
      } else if (args[i].equalsIgnoreCase("-loadFromTextFile") && (i + 1 < args.length)) {
        textGrammarInFile = args[i + 1];
        i += 2;
      } else if (args[i].equalsIgnoreCase("-targets") && (i + 1 < args.length)) {
        targets = parseTargets(args[i + 1]);
        i += 2;
      } else {
        i = op.setOptionOrWarn(args, i);
      }
    }

    // this is all the acl03pcfg stuff with some changes
    op.doDep = false;
    op.doPCFG = true;
    // Options.get().smoothInUnknownsThreshold = 30;
//    op.trainOptions.markUnary = true;
    op.trainOptions.markUnary = 1;
    op.trainOptions.PA = true;
    op.trainOptions.gPA = false;
    op.trainOptions.tagPA = true;
    op.trainOptions.tagSelectiveSplit = false;
    op.trainOptions.rightRec = true;
    op.trainOptions.selectiveSplit = true;
    op.trainOptions.markovFactor = true;
    op.trainOptions.markovOrder = 2;
    op.trainOptions.hSelSplit = true;
    op.lexOptions.useUnknownWordSignatures = 2;
    op.lexOptions.flexiTag = true;
    op.dcTags = false;
    // end Train Options setting

    LexicalizedParser pd = null;
    if (textGrammarInFile != null) {
      pd = LexicalizedParser.getParserFromTextFile(textGrammarInFile, op);
    } else {
      op.trainOptions.display();
      op.display();
      NumberRangeFileFilter filt = new NumberRangeFileFilter(trainLow, trainHigh, true);

      Treebank trainTreebank = op.tlpParams.memoryTreebank();
      Timing.startTime();
      System.err.print("Reading trees...");
      trainTreebank.loadPath(path, filt);
      Timing.tick("done.");
      if (targets == null) {
        throw new RuntimeException("no targets given");
      }
      pd = createParserData(trainTreebank, targets, op);
    }

    if (textGrammarOutFile != null) {
      pd.saveParserToTextFile(textGrammarOutFile);
    }
    if (binaryOutFile != null) {
      pd.saveParserToSerialized(binaryOutFile);
    }
    if (testLow >= 0 && testHigh >= 0) {
      Treebank testTreebank = op.tlpParams.testMemoryTreebank();
      testTreebank.loadPath(path, new NumberRangeFileFilter(testLow, testHigh, true));
      pd.parserQuery().testOnTreebank(testTreebank);
    } else if (testFile != null) {
      parseTestFile(testFile, pd, op);
    }
  }

  public static Set<String> parseTargets(String targets) {
    Set<String> result = new HashSet<String>();
    StringTokenizer tok = new StringTokenizer(targets, "|", false);
    while (tok.hasMoreTokens()) {
      result.add(tok.nextToken());
    }
    return result;
  }

  public static void parseTestFile(String testFile, LexicalizedParser lp, Options op) {
    //    lp.setDoRecovery(false);
    TokenizerFactory<Word> tokenizerFactory = WhitespaceTokenizer.factory(true);
    Set<String> set = new HashSet<String>();
    set.add("\n");
    WordToSentenceProcessor wordToSent = new WordToSentenceProcessor("", new HashSet<String>(), set);
    System.err.println("Parsing file: " + testFile);
    Document doc = null;
    TreeTransformer d = new Debinarizer(op.forceCNF);
    try {
      doc = new BasicDocument(tokenizerFactory).init(new InputStreamReader(new FileInputStream(testFile), op.langpack().getEncoding()));
    } catch (Exception e) {
      e.printStackTrace();
    }
    if (doc != null) {
      String[] goalStrings = makeGoalStrings();
      List<ArrayList<HasWord>> sentList = wordToSent.processDocument(doc);
      int t = 0;
      for (ArrayList<HasWord> s : sentList) {
        System.err.println("Parsing [len. " + s.size() + "]: " + Sentence.listToString(s));
        LexicalizedParserQuery pq = lp.parserQuery();
        try {
          pq.parsePCFG(s);
          for (int i = 0; i < Subcategory.SUBCATEGORIES.size(); i++) {
            try {
              double score = pq.getPCFGScore(goalStrings[i]);
              System.out.println("score for " + goalStrings[i] + " is: " + score);
              Tree binaryTree = pq.getBestPCFGParse(false);
              if (binaryTree != null) {
                Tree tree = d.transformTree(binaryTree);
                tree.pennPrint();
              }
            } catch (Throwable e) {
              System.out.println("can't parse to " + goalStrings[i]);
            }
          }
        } catch (UnsupportedOperationException uoe) {
          System.err.println("Whoops, sentence too long.");
        }
      }
    }
  }

  public static String[] makeGoalStrings() {
    String[] goalStrings = new String[Subcategory.SUBCATEGORIES.size()];
    for (int i = 0; i < goalStrings.length; i++) {
      goalStrings[i] = "ROOT_" + i;
    }
    return goalStrings;
  }

  public static LexicalizedParser createParserData(Treebank trainTreebank, Set<String> targetWords, Options op) {
    List<Tree> subcatMarkedTrees = new ArrayList<Tree>(); // to hold full trees marked for subcat but not binarized
    TreebankLangParserParams tlpParams = op.tlpParams;
    TreebankLanguagePack tlp = op.langpack();
    TreeTransformer annotator = new TreeAnnotator(tlpParams.headFinder(), tlpParams, op);
    TreeTransformer subcatmarker = new SubcatMarker();
    Extractor<ClassicCounter<Tree>> localTreeExtractor = new SubcatMarkedLocalTreeExtractor(op.langpack(), op);
    TreeBinarizer binarizer = new TreeBinarizer(tlpParams.headFinder(), tlp, !op.trainOptions.outsideFactor(),
                                                op.trainOptions.markovFactor,
                                                op.trainOptions.markovOrder, op.trainOptions.compactGrammar()>0, op.trainOptions.compactGrammar()>1,
                                                op.trainOptions.HSEL_CUT, op.trainOptions.markFinalStates, 
                                                op.trainOptions.simpleBinarizedLabels, op.trainOptions.noRebinarization);
    TreeAnnotatorAndBinarizer rootAdder = new TreeAnnotatorAndBinarizer(tlpParams, op.forceCNF, false, false, op);

    if (op.trainOptions.selectiveSplit) {
      op.trainOptions.splitters = ParentAnnotationStats.getSplitCategories(trainTreebank, op.trainOptions.selectiveSplitCutOff, tlpParams.treebankLanguagePack());
    }
    if (op.trainOptions.selectivePostSplit) {
      TreeTransformer myTransformer = new TreeAnnotator(tlpParams.headFinder(), tlpParams, op);
      Treebank annotatedTB = trainTreebank.transform(myTransformer);
      op.trainOptions.postSplitters = ParentAnnotationStats.getSplitCategories(annotatedTB, op.trainOptions.selectivePostSplitCutOff, tlpParams.treebankLanguagePack());
    }

    // go through the trees in treebank, and apply the tools to
    // populate data structures with trees
    // trainTreebank has raw PTB trees in it
    Timing.startTime();
    System.out.print("Annotating trees...");
    for (Tree tree : trainTreebank) {

      // annotate with subcategories - this is our first intervention
      tree = subcatmarker.transformTree(tree);
      // annotate for performance
      tree = annotator.transformTree(tree); // try commenting this out first
      rootAdder.addRoot(tree);
      // put in List
      subcatMarkedTrees.add(tree);
    }
    Timing.tick("done.");
    Timing.startTime();
    System.out.print("Extracting local subcat trees...");
    // extract local trees, making a different copy for each subcat and one generic
    // and put them all with correct weights in Counter localTrees
    ClassicCounter<Tree> localTrees = localTreeExtractor.extract(subcatMarkedTrees);
    // go through local trees and binarize them and put in binarizedLocalTrees
    Timing.tick("done.");
    Timing.startTime();
    System.out.print("Binarizing trees...");

    if (op.trainOptions.hSelSplit) {
      binarizer.setDoSelectiveSplit(false);
      for (Tree localTree : localTrees.keySet()) {
        //Tree binarizedTree =
        binarizer.transformTree(localTree); // counted internally
      }
      binarizer.setDoSelectiveSplit(true);
    }
    ClassicCounter<Tree> binarizedLocalTrees = new ClassicCounter<Tree>(); // to hold weighted binarized local trees
    for (Tree localTree : localTrees.keySet()) {
      Tree binarizedTree = binarizer.transformTree(localTree);
      binarizedLocalTrees.incrementCount(binarizedTree, localTrees.getCount(localTree));
    }
    Timing.tick("done.");

    // now I have two data structures: subcatMarkedTrees and binarizedLocalTrees
    // extract grammars
    Index<String> stateIndex = new HashIndex<String>();
    Extractor<Pair<UnaryGrammar,BinaryGrammar>> wbgExtractor = new WeightedBinaryGrammarExtractor(op, binarizedLocalTrees, stateIndex);
    System.out.print("Extracting PCFG...");
    Pair<UnaryGrammar, BinaryGrammar> bgug = wbgExtractor.extract(new ArrayList<Tree>(binarizedLocalTrees.keySet()));
    BinaryGrammar bg = bgug.second;
    bg.splitRules();
    UnaryGrammar ug = bgug.first;
    ug.purgeRules();
    Timing.tick("done.");
    System.out.print("Extracting Lexicon...");
    Index<String> wordIndex = new HashIndex<String>();
    Index<String> tagIndex = new HashIndex<String>();
    SubcatLexicon lex = new SubcatLexicon(targetWords, op.langpack(), op, wordIndex, tagIndex);
    lex.initializeTraining(subcatMarkedTrees.size());
    lex.train(subcatMarkedTrees);
    lex.finishTraining();
    Timing.tick("done.");


    // make sure it has all the goal strings
    String[] goalStrings = makeGoalStrings();
    for (String goalString : goalStrings) {
      stateIndex.indexOf(goalString, true);
    }
    System.out.println("numStates: " + stateIndex.size());
    //System.out.println(stateIndex);

    return new LexicalizedParser(lex, bg, ug, null, stateIndex, wordIndex, tagIndex, op);
  }

  // embedded static classes

  /**
   * Gets a local tree, and creates several local trees, one for each
   * subcat marking, and puts them in the Counter that is returned by the extract function.
   * Can find AbstractTreeExtractor class def in FactoredParser.java
   */
  static class SubcatMarkedLocalTreeExtractor extends AbstractTreeExtractor<ClassicCounter<Tree>> {

    TreebankLanguagePack tlp;

    public SubcatMarkedLocalTreeExtractor(TreebankLanguagePack tlp, Options op) {
      super(op);
      this.tlp = tlp;
    }

    ClassicCounter<Tree> counter = new ClassicCounter<Tree>();
    int numMarkedVerbsTotal;

    @Override
    protected void tallyRoot(Tree root, double weight) {
      numMarkedVerbsTotal = getNumMarkedVerbsInTree(root);
    }

    /**
     * Adds many different local trees to the Counter, including a generic one
     * and one for each subcat, each with different weights.
     */
    @Override
    protected void tallyInternalNode(Tree lt, double ignoredWeight) {
      int numMarkedVerbsLocal = getNumMarkedVerbsInTree(lt);

      // make a generic copy of the local tree, with no subcat info
      Tree genericTree = makeGenericTreeCopy(lt);
      /*
	System.out.println();
	CategoryWordTag.printWordTag = false;
	genericTree.pennPrint();
	CategoryWordTag.printWordTag = true;
      */
      double weight = 1.0;
      if (numMarkedVerbsTotal > 0) {
        weight = numMarkedVerbsTotal - numMarkedVerbsLocal;
      }
      counter.incrementCount(genericTree, weight); // TEG: is this the correct weight?
      //    System.out.println("giving weight: " + counter.getCount(genericTree) + " to tree " + genericTree);

      // separate the tree into several trees, depending on how it is marked up
      Tree[] children = lt.children();
      for (int i = 0; i < children.length; i++) {
        List<Integer> subcats = new ArrayList<Integer>();
        removeSubcatMarkersFromString(children[i].label().value(), subcats); // subcats added to subcats
        for (Integer subcat : subcats) {
          int tempSubcat = subcat.intValue();
          // make a tree corresponding to this subcategory
          Tree customTree = makeCustomTreeCopy(genericTree, tempSubcat, i); // TEG: is this the correct weight?
          /*
            System.out.println();
            CategoryWordTag.printWordTag = false;
            customTree.pennPrint();
            CategoryWordTag.printWordTag = true;
          */
          // put the subcat-specific tree in the Counter with weights
          counter.incrementCount(customTree, 1.0);
        }
      }
    }

    @Override
      public ClassicCounter<Tree> formResult() {
      return counter;
    }

    /**
     * This method returns the number of marked verbs under this subtree.
     * Counts the number of underscores.
     */
    private static int getNumMarkedVerbsInTree(Tree tree) {
      Label tempLabel = tree.label();
      String s = tempLabel.toString();
      if (s == null) {
        return 0;
      }
      int numVerbs = 0;
      for (int i = 0; i < s.length(); i++) {
        if (s.charAt(i) == '_') {
          numVerbs++;
        }
      }
      return numVerbs;
    }

    private Tree makeGenericTreeCopy(Tree lt) {
      TreeFactory tf = lt.treeFactory();
      // make the children List
      List<Tree> newChildren = new ArrayList<Tree>();
      Tree[] children = lt.children();
      for (Tree child : children) {
        String tempLabel = child.label().value();
        tempLabel = removeSubcatMarkersFromString(tempLabel, new ArrayList<Integer>());
        newChildren.add(tf.newLeaf(tempLabel));
      }
      String tempLabel = lt.label().value();
      tempLabel = removeSubcatMarkersFromString(tempLabel, new ArrayList<Integer>());
      Tree result = tf.newTreeNode(tempLabel, newChildren);
      return result;
    }

    // will add the subcats it encounters to subcats
    public String removeSubcatMarkersFromString(String s, List<Integer> subcats) {
      int underscore = s.indexOf('_');
      int lastUnderscore = underscore;
      if (underscore < 0) {
        return s;
      }
      for (int i = underscore + 1; i < s.length(); i++) {
        char current = s.charAt(i);
        if (current == '_') {
          subcats.add(Integer.valueOf(s.substring(lastUnderscore + 1, i)));
          lastUnderscore = i;
        } else {
          // check all the other break characters
          if (tlp.isLabelAnnotationIntroducingCharacter(current)) {
            subcats.add(Integer.valueOf(s.substring(lastUnderscore + 1, i)));
            return s.substring(0, underscore) + s.substring(i, s.length()); // we are done
          }
        }
      }
      subcats.add(Integer.valueOf(s.substring(lastUnderscore + 1, s.length()))); // everything since last underscore
      return s.substring(0, underscore); // nothing good found after the subcat markers
    }

    private Tree makeCustomTreeCopy(Tree tree, int subcat, int childIndex) {
      Tree result = tree.treeSkeletonCopy();

      // change the parent label
      CategoryWordTag parentLabel = (CategoryWordTag) result.label();
      //    if (!parentLabel.value().equals(tlp.startSymbol())) {
      parentLabel = new CategoryWordTag(insertSubcatMarker(parentLabel.category(), subcat), parentLabel.word(), parentLabel.tag());
      result.setLabel(parentLabel);
      //    }

      // change the kid label
      Tree kid = result.children()[childIndex];
      CategoryWordTag kidLabel = (CategoryWordTag) kid.label();
      kidLabel = new CategoryWordTag(insertSubcatMarker(kidLabel.category(), subcat), kidLabel.word(), kidLabel.tag());
      kid.setLabel(kidLabel);
      return result;
    }

    private String insertSubcatMarker(String s, int subcat) {
      int leng = s.length();
      int split = leng;
      for (int i = 1; i < leng; i++) {  // not 0, you never want a null cat
        char current = s.charAt(i);
        if (tlp.isLabelAnnotationIntroducingCharacter(current)) {
          split = i;
          break;
        }
      }
      return s.substring(0, split) + "_" + subcat + s.substring(split, s.length());
    }


  }


  /**
   * Extracts a binary and unary grammar from a weighted Counter of trees.
   */
  static class WeightedBinaryGrammarExtractor extends BinaryGrammarExtractor {

    ClassicCounter<Tree> weights;
    double currentTreeWeight;

    WeightedBinaryGrammarExtractor(Options op, ClassicCounter<Tree> weights, Index<String> stateIndex) {
      super(op, stateIndex);
      this.weights = weights;
    }

    /**
     */
    @Override
    public void tallyTree(Tree t, double weight) {
      // adjust the weight based on our weights rather than using what we are given...
      currentTreeWeight = weights.getCount(t);
      //    System.out.println("Tallying whole tree:" );
      //       CategoryWordTag.printWordTag = false;
      //       t.pennPrint();
      //       CategoryWordTag.printWordTag = true;
      super.tallyTree(t, currentTreeWeight);
    }

    @Override
    protected void tallyPreTerminal(Tree lt, double weight) {
      // because the local trees sometimes look like preterminals, even though they are not
      tallyInternalNode(lt, weight);
    }

  }

  static class SubcatLexicon extends BaseLexicon {

    /**
     *
     */
    private static final long serialVersionUID = 6258269839122647829L;
    Set<Integer> targetWords;
    SubcatMarkedLocalTreeExtractor treeExtractor;

    /*
     * @param targetWords a Set of target words that should be able
     * to parse to the subcat labeled tags.
     */
    public SubcatLexicon(Set<String> targetWordStrings, TreebankLanguagePack tlp, Options op, Index<String> wordIndex, Index<String> tagIndex) {
      super(op, wordIndex, tagIndex);
      targetWords = new HashSet<Integer>();
      for (String targetWord : targetWordStrings) {
        targetWords.add(Integer.valueOf(wordIndex.indexOf(targetWord, true)));
      }
      treeExtractor = new SubcatMarkedLocalTreeExtractor(tlp, op);
    }

    @Override
    public void initializeTraining(double numTrees) {
      SubcatUnknownWordModelTrainer subcatUWMT = 
        new SubcatUnknownWordModelTrainer();
      subcatUWMT.initializeTraining(op, this, wordIndex, tagIndex, 
                                    numTrees, targetWords, treeExtractor);
      this.uwModelTrainer = subcatUWMT;
    }

    /**
     * Trains this lexicon on the Collection of trees.
     * We must ensure that:
     * - the target word when marked gets only subcat marked tags
     * - other words get only non-subcat marked tags 
     *   (we strip off the subcat marking)
     * - no other words can get tagged subcat marked tags when unseen
     * <br>
     * This can be called multiple times with different sets of trees
     * if the underlying UnknownWordModel allows it
     */
    @Override
    public void train(TaggedWord tw, int loc, double weight) {
      //First, train the known words model
      // scan data
      String wordString = tw.word();
      String tagString = tw.tag();
      IntTaggedWord iTW = 
        new IntTaggedWord(wordString, tagString, wordIndex, tagIndex);
      boolean isMarked = tagString.indexOf('_') >= 0;
      
      // check to see whether this word is one of our target words
      if (targetWords.contains(Integer.valueOf(iTW.word)) && isMarked) {
        // if so, we want to put in some marked rules in with the target word marked
        IntTaggedWord miTW = new IntTaggedWord(wordIndex.indexOf(wordString + "^", true), iTW.tag);
        seenCounter.incrementCount(miTW, weight);
        IntTaggedWord miT = new IntTaggedWord(nullWord, miTW.tag);
        seenCounter.incrementCount(miT, weight);
        IntTaggedWord miW = new IntTaggedWord(miTW.word, nullTag);
        seenCounter.incrementCount(miW, weight);
        IntTaggedWord mi = new IntTaggedWord(nullWord, nullTag);
        seenCounter.incrementCount(mi, weight);
        // rules.add(miTW);
        tags.add(miT);
        words.add(miW);
      }
      // if it is the target word, but it is not marked, then we treat it as normal
      
      // everything else should proceed with the unmarked tag
      if (isMarked) {
        tagString = treeExtractor.removeSubcatMarkersFromString(tagString, new ArrayList<Integer>());
        iTW = new IntTaggedWord(iTW.word, tagIndex.indexOf(tagString, true));
      }
      seenCounter.incrementCount(iTW, weight);
      IntTaggedWord iT = new IntTaggedWord(nullWord, iTW.tag);
      seenCounter.incrementCount(iT, weight);
      IntTaggedWord iW = new IntTaggedWord(iTW.word, nullTag);
      seenCounter.incrementCount(iW, weight);
      IntTaggedWord i = new IntTaggedWord(nullWord, nullTag);
      seenCounter.incrementCount(i, weight);
      // rules.add(iTW);
      tags.add(iT);
      words.add(iW);
      
      //Now train the unknown words model
      uwModelTrainer.train(tw, loc, weight);
    }

    @Override
    public void finishTraining() {
      this.setUnknownWordModel(uwModelTrainer.finishTraining());
    }

  }

} // end class SubcatParserTrainer

