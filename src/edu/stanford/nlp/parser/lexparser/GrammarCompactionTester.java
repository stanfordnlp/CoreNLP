package edu.stanford.nlp.parser.lexparser; 
import edu.stanford.nlp.util.logging.Redwood;

import edu.stanford.nlp.fsm.*;
import edu.stanford.nlp.io.NumberRangeFileFilter;
import edu.stanford.nlp.parser.metrics.EvaluateTreebank;
import edu.stanford.nlp.trees.*;
import edu.stanford.nlp.util.*;
import edu.stanford.nlp.stats.ClassicCounter;
import java.util.*;


/**
 * @author Teg Grenager (grenager@cs.stanford.edu)
 */
public class GrammarCompactionTester  {

  /** A logger for this class */
  private static Redwood.RedwoodChannels log = Redwood.channels(GrammarCompactionTester.class);

  // for debugging
  //  public static MergeableGraph debugGraph = null;

  ExhaustivePCFGParser parser = null;
  ExhaustiveDependencyParser dparser = null;
  BiLexPCFGParser bparser = null;
  Scorer scorer = null;
  Options op;

  //  TreebankLangParserParams tlpParams = new EnglishTreebankParserParams();
  // tlpParams may be changed to something else later, so don't use it till
  // after options are parsed.


  GrammarCompactor compactor = null;

  Map<String, List<List<String>>> allTestPaths = Generics.newHashMap();
  Map<String, List<List<String>>> allTrainPaths = Generics.newHashMap();

  String asciiOutputPath = null;
  String path = "/u/nlp/stuff/corpora/Treebank3/parsed/mrg/wsj";
  int trainLow = 200, trainHigh = 2199, testLow = 2200, testHigh = 2219;

  String suffixOrderString = null;
  String minArcNumString = null;
  String maxMergeCostString = null;
  String sizeCutoffString = null;
  String minPortionArcsString = null;
  String ignoreUnsupportedSuffixesString = "false";
  String splitParamString = null;
  String costModelString = null;
  String verboseString = null;
  String minArcCostString = null;
  String trainThresholdString = null;
  String heldoutThresholdString = null;
  int markovOrder = -1;
  String smoothParamString = null;
  String scoringData = null;
  String allowEpsilonsString = null;
  boolean saveGraphs = false;
  private int indexRangeLow;
  private int indexRangeHigh;
  private String outputFile = null;
  private String inputFile = null;
  private boolean toy = false;

  /**
   */
  public Map<String,List<List<String>>> extractPaths(String path, int low, int high, boolean annotate) {

    // setup tree transforms
    Treebank trainTreebank = op.tlpParams.memoryTreebank(); // this is a new one
    TreebankLanguagePack tlp = op.langpack();

    trainTreebank.loadPath(path, new NumberRangeFileFilter(low, high, true));

    if (op.trainOptions.selectiveSplit) {
      op.trainOptions.splitters = ParentAnnotationStats.getSplitCategories(trainTreebank, op.trainOptions.selectiveSplitCutOff, op.tlpParams.treebankLanguagePack());
    }
    if (op.trainOptions.selectivePostSplit) {
      TreeTransformer myTransformer = new TreeAnnotator(op.tlpParams.headFinder(), op.tlpParams, op);
      Treebank annotatedTB = trainTreebank.transform(myTransformer);
      op.trainOptions.postSplitters = ParentAnnotationStats.getSplitCategories(annotatedTB, op.trainOptions.selectivePostSplitCutOff, op.tlpParams.treebankLanguagePack());
    }

    List<Tree> trainTrees = new ArrayList<>();
    HeadFinder hf = null;
    if (op.trainOptions.leftToRight) {
      hf = new LeftHeadFinder();
    } else {
      hf = op.tlpParams.headFinder();
    }
    TreeTransformer annotator = new TreeAnnotator(hf, op.tlpParams, op);
    for (Tree tree : trainTreebank) {
      if (annotate) {
        tree = annotator.transformTree(tree);
      }
      trainTrees.add(tree);
    }
    Extractor<Map<String,List<List<String>>>> pExtractor = new PathExtractor(hf, op);
    Map<String,List<List<String>>> allPaths = pExtractor.extract(trainTrees);
    return allPaths;
  }


  public static void main(String[] args) {
    new GrammarCompactionTester().runTest(args);
  }

  public void runTest(String[] args) {
    System.out.println("Currently " + new Date());
    System.out.print("Invoked with arguments:");
    for (String arg : args) {
      System.out.print(" " + arg);
    }

    System.out.println();

    int i = 0;
    while (i < args.length && args[i].startsWith("-")) {
      if (args[i].equalsIgnoreCase("-path") && (i + 1 < args.length)) {
        path = args[i + 1];
        i += 2;
      } else if (args[i].equalsIgnoreCase("-saveToAscii") && (i + 1 < args.length)) {
        asciiOutputPath = args[i + 1];
        i += 2;
      } else if (args[i].equalsIgnoreCase("-train") && (i + 2 < args.length)) {
        trainLow = Integer.parseInt(args[i + 1]);
        trainHigh = Integer.parseInt(args[i + 2]);
        i += 3;
      } else if (args[i].equalsIgnoreCase("-test") && (i + 2 < args.length)) {
        testLow = Integer.parseInt(args[i + 1]);
        testHigh = Integer.parseInt(args[i + 2]);
        i += 3;
      } else if (args[i].equalsIgnoreCase("-index") && (i + 2 < args.length)) {
        indexRangeLow = Integer.parseInt(args[i + 1]);
        indexRangeHigh = Integer.parseInt(args[i + 2]);
        i += 3;
      } else if (args[i].equalsIgnoreCase("-outputFile")) {
        outputFile = args[i + 1];
        i += 2;
      } else if (args[i].equalsIgnoreCase("-inputFile")) {
        inputFile = args[i + 1];
        i += 2;
      } else if (args[i].equalsIgnoreCase("-suffixOrder")) {
        suffixOrderString = args[i + 1];
        i += 2;
      } else if (args[i].equalsIgnoreCase("-minArcNum")) {
        minArcNumString = args[i + 1];
        i += 2;
      } else if (args[i].equalsIgnoreCase("-maxMergeCost")) {
        maxMergeCostString = args[i + 1];
        i += 2;
      } else if (args[i].equalsIgnoreCase("-sizeCutoff")) {
        sizeCutoffString = args[i + 1];
        i += 2;
      } else if (args[i].equalsIgnoreCase("-minPortionArcs")) {
        minPortionArcsString = args[i + 1];
        i += 2;
      } else if (args[i].equalsIgnoreCase("-ignoreUnsupportedSuffixes")) {
        ignoreUnsupportedSuffixesString = args[i + 1];
        i += 2;
      } else if (args[i].equalsIgnoreCase("-trainThreshold")) {
        trainThresholdString = args[i + 1];
        i += 2;
      } else if (args[i].equalsIgnoreCase("-heldoutThreshold")) {
        heldoutThresholdString = args[i + 1];
        i += 2;
      } else if (args[i].equalsIgnoreCase("-minArcCost")) {
        minArcCostString = args[i + 1];
        i += 2;
      } else if (args[i].equalsIgnoreCase("-splitParam")) {
        splitParamString = args[i + 1];
        i += 2;
      } else if (args[i].equalsIgnoreCase("-costModel")) {
        costModelString = args[i + 1];
        i += 2;
      } else if (args[i].equalsIgnoreCase("-scoringData")) {
        scoringData = args[i + 1];
        i += 2;
      } else if (args[i].equalsIgnoreCase("-verbose")) {
        verboseString = args[i + 1];
        i += 2;
      } else if (args[i].equalsIgnoreCase("-allowEpsilons")) {
        allowEpsilonsString = args[i + 1];
        i += 2;
      } else if (args[i].equalsIgnoreCase("-saveGraphs")) {
        saveGraphs = true;
        i++;
      } else if (args[i].equalsIgnoreCase("-toy")) {
        toy = true;
        i++;
      } else if (args[i].equalsIgnoreCase("-markovOrder")) {
        markovOrder = Integer.parseInt(args[i + 1]);
        i += 2;
      } else if (args[i].equalsIgnoreCase("-smoothParam")) {
        smoothParamString = args[i + 1];
        i += 2;
      } else {
        i = op.setOptionOrWarn(args, i);
      }
    }

    op.trainOptions.sisterSplitters = Generics.newHashSet(Arrays.asList(op.tlpParams.sisterSplitters()));
    if (op.trainOptions.compactGrammar() == 4) {
      System.out.println("Instantiating fsm.LossyGrammarCompactor");
      try {
        Class[] argTypes = new Class[13];
        Class strClass = String.class;
        for (int j = 0; j < argTypes.length; j++) {
          argTypes[j] = strClass;
        }
        Object[] cArgs = new Object[13];
        cArgs[0] = suffixOrderString;
        cArgs[1] = minArcNumString;
        cArgs[2] = trainThresholdString;
        cArgs[3] = heldoutThresholdString;
        cArgs[4] = sizeCutoffString;
        cArgs[5] = minPortionArcsString;
        cArgs[6] = splitParamString;
        cArgs[7] = ignoreUnsupportedSuffixesString;
        cArgs[8] = minArcCostString;
        cArgs[9] = smoothParamString;
        cArgs[10] = costModelString;
        cArgs[11] = scoringData;
        cArgs[12] = verboseString;
        compactor = (GrammarCompactor) Class.forName("fsm.LossyGrammarCompactor").getConstructor(argTypes).newInstance(cArgs);
      } catch (Exception e) {
        log.info("Couldn't instantiate GrammarCompactor: " + e);
        e.printStackTrace();
      }
    } else if (op.trainOptions.compactGrammar() == 5) {
      System.out.println("Instantiating fsm.CategoryMergingGrammarCompactor");
      try {
        Class[] argTypes = new Class[6];
        Class strClass = String.class;
        for (int j = 0; j < argTypes.length; j++) {
          argTypes[j] = strClass;
        }
        Object[] cArgs = new Object[6];
        cArgs[0] = splitParamString;
        cArgs[1] = trainThresholdString;
        cArgs[2] = heldoutThresholdString;
        cArgs[3] = minArcCostString;
        cArgs[4] = ignoreUnsupportedSuffixesString;
        cArgs[5] = smoothParamString;
        compactor = (GrammarCompactor) Class.forName("fsm.CategoryMergingGrammarCompactor").getConstructor(argTypes).newInstance(cArgs);
      } catch (Exception e) {
        throw new RuntimeException("Couldn't instantiate CategoryMergingGrammarCompactor." + e);
      }
    } else if (op.trainOptions.compactGrammar() == 3) {
      System.out.println("Instantiating fsm.ExactGrammarCompactor");
      compactor = new ExactGrammarCompactor(op, saveGraphs, true);
    } else if (op.trainOptions.compactGrammar() > 0) {
    }

    if (markovOrder >= 0) {
      op.trainOptions.markovOrder = markovOrder;
      op.trainOptions.hSelSplit = false;
    }
    if (toy) {
      buildAndCompactToyGrammars();
    } else {
      testGrammarCompaction();
    }
  }

  /*
  private static void testOneAtATimeMerging() {

    // use the parser constructor to extract the grammars from the treebank once
    LexicalizedParser lp = new LexicalizedParser(path, new NumberRangeFileFilter(trainLow, trainHigh, true), tlpParams);

    ParserData pd = lp.parserData();
    Pair originalGrammar = new Pair(pd.ug, pd.bg);

    // extract a bunch of paths
    Timing.startTime();
    System.out.print("Extracting other paths...");
    allTrainPaths = extractPaths(path, trainLow, trainHigh, true);
    allTestPaths = extractPaths(path, testLow, testHigh, true);
    Timing.tick("done");

    List mergePairs = null;
    if (inputFile != null) {
      // read merge pairs from file and do them and parse
      System.out.println("getting pairs from file: " + inputFile);
      mergePairs = getMergePairsFromFile(inputFile);
    }
    // try one merge at a time and parse afterwards
    Numberer originalNumberer = Numberer.getGlobalNumberer("states");
    String header = "index\tmergePair\tmergeCost\tparseF1\n";
    StringUtils.printToFile(outputFile, header, true);

    for (int i = indexRangeLow; i < indexRangeHigh; i++) {

      Timing.startTime();
      Numberer.getNumberers().put("states", originalNumberer);
      if (mergePairs != null)
        System.out.println("passing merge pairs to compactor: " + mergePairs);
      CategoryMergingGrammarCompactor compactor = new CategoryMergingGrammarCompactor(mergePairs, i);
      System.out.println("Compacting grammars with index " + i);
      Pair compactedGrammar = compactor.compactGrammar(originalGrammar, allTrainPaths, allTestPaths);
      Pair mergePair = null;
      double mergeCosts = Double.NEGATIVE_INFINITY;
      List mergeList = compactor.getCompletedMergeList();
      if (mergeList != null && mergeList.size() > 0) {
        mergePair = (Pair) mergeList.get(0);
        mergeCosts = compactor.getActualScores().getCount(mergePair);
      }


      ParserData newPd = new ParserData(pd.lex,
                                        (BinaryGrammar) compactedGrammar.second, (UnaryGrammar) compactedGrammar.first,
                                        pd.dg, pd.numbs, pd.pt);

      lp = new LexicalizedParser(newPd);
      Timing.tick("done.");

      Treebank testTreebank = tlpParams.testMemoryTreebank();
      testTreebank.loadPath(path, new NumberRangeFileFilter(testLow, testHigh, true));
      System.out.println("Currently " + new Date());
      double f1 = lp.testOnTreebank(testTreebank);
      System.out.println("Currently " + new Date());

      String resultString = i + "\t" + mergePair + "\t" + mergeCosts + "\t" + f1 + "\n";
      StringUtils.printToFile(outputFile, resultString, true);
    }
  }

  private static List getMergePairsFromFile(String filename) {
    List result = new ArrayList();
    try {
      String fileString = StringUtils.slurpFile(new File(filename));
      StringTokenizer st = new StringTokenizer(fileString);
      while (st.hasMoreTokens()) {
        String token1 = st.nextToken();
        if (st.hasMoreTokens()) {
          String token2 = st.nextToken();
          UnorderedPair pair = new UnorderedPair(token1, token2);
          result.add(pair);
        }
      }
    } catch (Exception e) {
      throw new RuntimeException("couldn't access file: " + filename);
    }
    return result;
  }
*/
  /*
//    System.out.println(MergeableGraph.areIsomorphic(graphs[0], graphs[1], graphs[0].getStartNode(), graphs[1].getStartNode()));
//    System.out.println(MergeableGraph.areIsomorphic(graphs[1], graphs[2], graphs[1].getStartNode(), graphs[2].getStartNode()));
//    System.out.println(MergeableGraph.areIsomorphic(graphs[2], graphs[0], graphs[2].getStartNode(), graphs[0].getStartNode()));

  // now go through the grammars themselves and see if they are equal
  System.out.println("UR 0 and 1: " + equalsUnary(((UnaryGrammar)grammars[0].first).rules(),((UnaryGrammar)grammars[1].first).rules()));
  System.out.println("UR 1 and 2: "  + equalsUnary(((UnaryGrammar)grammars[1].first).rules(),((UnaryGrammar)grammars[2].first).rules()));
  System.out.println("UR 2 and 0: "  + equalsUnary(((UnaryGrammar)grammars[2].first).rules(),((UnaryGrammar)grammars[0].first).rules()));

  System.out.println("BR 0 and 1: "  + equalsBinary(((BinaryGrammar)grammars[0].second).rules(),((BinaryGrammar)grammars[1].second).rules()));
  System.out.println("BR 1 and 2: " + equalsBinary(((BinaryGrammar)grammars[1].second).rules(),((BinaryGrammar)grammars[2].second).rules()));
  System.out.println("BR 2 and 0: " + equalsBinary(((BinaryGrammar)grammars[2].second).rules(),((BinaryGrammar)grammars[0].second).rules()));

    System.exit(0);

  // now go through the grammars we made and see if they are equal!
  Set[] unaryRules = new Set[3];
  Set[] binaryRules = new Set[3];
  for (int i=0; i<grammars.length; i++) {
    unaryRules[i] = new HashSet();
    System.out.println(i + " size: " + ((UnaryGrammar)grammars[i].first()).numRules());
    for (Iterator unRuleI = ((UnaryGrammar)grammars[i].first()).iterator(); unRuleI.hasNext();) {
      UnaryRule ur = (UnaryRule) unRuleI.next();
      String parent = (String) stateNumberers[i].object(ur.parent);
      String child = (String) stateNumberers[i].object(ur.child);
      unaryRules[i].add(new StringUnaryRule(parent, child, ur.score));
    }
    binaryRules[i] = new HashSet();
    System.out.println(i + " size: " + ((BinaryGrammar)grammars[i].second()).numRules());
    for (Iterator binRuleI = ((BinaryGrammar)grammars[i].second()).iterator(); binRuleI.hasNext();) {
      BinaryRule br = (BinaryRule) binRuleI.next();
      String parent = (String) stateNumberers[i].object(br.parent);
      String leftChild = (String) stateNumberers[i].object(br.leftChild);
      String rightChild = (String) stateNumberers[i].object(br.rightChild);
      binaryRules[i].add(new StringBinaryRule(parent, leftChild, rightChild, br.score));
    }
  }

  System.out.println("uR 0 and 1: " + equals(unaryRules[0],unaryRules[1]));
  System.out.println("uR 1 and 2: " + equals(unaryRules[1],unaryRules[2]));
  System.out.println("uR 2 and 0: " + equals(unaryRules[2],unaryRules[0]));

  System.out.println("bR 0 and 1: " + equals(binaryRules[0],binaryRules[1]));
  System.out.println("bR 1 and 2: " + equals(binaryRules[1],binaryRules[2]));
  System.out.println("bR 2 and 0: " + equals(binaryRules[2],binaryRules[0]));

}
*/

  /*
    public static void testCategoryMergingProblem() {
      LexicalizedParser lp = new LexicalizedParser(path, new NumberRangeFileFilter(trainLow, trainHigh, true), tlpParams);

      // test it without the change
      Treebank testTreebank = tlpParams.testMemoryTreebank();
      testTreebank.loadPath(path, new NumberRangeFileFilter(testLow, testHigh, true));
      System.out.println("Currently " + new Date());
      lp.testOnTreebank(testTreebank);
      System.out.println("Currently " + new Date());

      // pull out the rules and consistently change the name of one of the states
      ParserData pd = lp.parserData();
      BinaryGrammar bg = pd.bg;
      UnaryGrammar ug = pd.ug;
      Numberer stateNumberer = Numberer.getGlobalNumberer("states");
      UnaryGrammar newUG = new UnaryGrammar(stateNumberer.total()+1);
      for (Iterator urIter = ug.iterator(); urIter.hasNext();) {
        UnaryRule rule = (UnaryRule) urIter.next();
        rule.parent = changeIfNecessary(rule.parent, stateNumberer);
        rule.child = changeIfNecessary(rule.child, stateNumberer);
        newUG.addRule(rule);
      }
      BinaryGrammar newBG = new BinaryGrammar(stateNumberer.total()+1);
      for (Iterator urIter = bg.iterator(); urIter.hasNext();) {
        BinaryRule rule = (BinaryRule) urIter.next();
        rule.parent = changeIfNecessary(rule.parent, stateNumberer);
        rule.leftChild = changeIfNecessary(rule.leftChild, stateNumberer);
        rule.rightChild = changeIfNecessary(rule.rightChild, stateNumberer);
        newBG.addRule(rule);
      }
      newUG.purgeRules();
      newBG.splitRules();
      pd.ug = newUG;
      pd.bg = newBG;
      lp = new LexicalizedParser(pd);

      // test it with the change
      testTreebank = tlpParams.testMemoryTreebank();
      testTreebank.loadPath(path, new NumberRangeFileFilter(testLow, testHigh, true));
      System.out.println("Currently " + new Date());
      lp.testOnTreebank(testTreebank);
      System.out.println("Currently " + new Date());
    }
  */

  public Pair<UnaryGrammar, BinaryGrammar> translateAndSort(Pair<UnaryGrammar, BinaryGrammar> grammar, Index<String> oldIndex, Index<String> newIndex) {
    System.out.println("oldIndex.size()" + oldIndex.size() + " newIndex.size()" + newIndex.size());
    UnaryGrammar ug = grammar.first;
    List<UnaryRule> unaryRules = new ArrayList<>();
    for (UnaryRule rule : ug.rules()) {
      rule.parent = translate(rule.parent, oldIndex, newIndex);
      rule.child = translate(rule.child, oldIndex, newIndex);
      unaryRules.add(rule);
    }
    Collections.sort(unaryRules);

    UnaryGrammar newUG = new UnaryGrammar(newIndex);
    for (UnaryRule unaryRule : unaryRules) {
      newUG.addRule(unaryRule);
    }
    newUG.purgeRules();

    BinaryGrammar bg = grammar.second;
    List<BinaryRule> binaryRules = new ArrayList<>();
    for (BinaryRule rule : bg.rules()) {
      rule.parent = translate(rule.parent, oldIndex, newIndex);
      rule.leftChild = translate(rule.leftChild, oldIndex, newIndex);
      rule.rightChild = translate(rule.rightChild, oldIndex, newIndex);
      binaryRules.add(rule);
    }
    Collections.sort(unaryRules);

    BinaryGrammar newBG = new BinaryGrammar(newIndex);
    for (BinaryRule binaryRule : binaryRules) {
      newBG.addRule(binaryRule);
    }
    newBG.splitRules();

    return Generics.newPair(newUG, newBG);
  }

  private static int translate(int i, Index<String> oldIndex, Index<String> newIndex) {
    return newIndex.addToIndex(oldIndex.get(i));
  }

  // WTF is this?
  public int changeIfNecessary(int i, Index<String> n) {
    String s = n.get(i);
    if (s.equals("NP^PP")) {
      System.out.println("changed");
      return n.addToIndex("NP-987928374");
    }
    return i;
  }

  public boolean equalsBinary(List<BinaryRule> l1, List<BinaryRule> l2) {
    // put each into a map to itself
    Map<BinaryRule, BinaryRule> map1 = Generics.newHashMap();
    for (BinaryRule o : l1) {
      map1.put(o, o);
    }
    Map<BinaryRule, BinaryRule> map2 = Generics.newHashMap();
    for (BinaryRule o : l2) {
      map2.put(o, o);
    }
    boolean isEqual = true;
    for (BinaryRule rule1 : map1.keySet()) {
      BinaryRule rule2 = map2.get(rule1);
      if (rule2 == null) {
        System.out.println("no rule for " + rule1);
        isEqual = false;
      } else {
        map2.remove(rule2);
        if (rule1.score != rule2.score) {
          System.out.println(rule1 + " and " + rule2 + " have diff scores");
          isEqual = false;
        }
      }
    }
    System.out.println("left over: " + map2.keySet());
    return isEqual;
  }

  public boolean equalsUnary(List<UnaryRule> l1, List<UnaryRule> l2) {
    // put each into a map to itself
    Map<UnaryRule, UnaryRule> map1 = Generics.newHashMap();
    for (UnaryRule o : l1) {
      map1.put(o, o);
    }
    Map<UnaryRule, UnaryRule> map2 = Generics.newHashMap();
    for (UnaryRule o : l2) {
      map2.put(o, o);
    }
    boolean isEqual = true;
    for (UnaryRule rule1 : map1.keySet()) {
      UnaryRule rule2 = map2.get(rule1);
      if (rule2 == null) {
        System.out.println("no rule for " + rule1);
        isEqual = false;
      } else {
        map2.remove(rule2);
        if (rule1.score != rule2.score) {
          System.out.println(rule1 + " and " + rule2 + " have diff scores");
          isEqual = false;
        }
      }
    }
    System.out.println("left over: " + map2.keySet());
    return isEqual;
  }

  private static <T> boolean equalSets(Set<T> set1, Set<T> set2) {
    boolean isEqual = true;
    if (set1.size() != set2.size()) {
      System.out.println("sizes different: " + set1.size() + " vs. " + set2.size());
      isEqual = false;
    }
    Set<T> newSet1 = (Set<T>) ((HashSet<T>) set1).clone();
    newSet1.removeAll(set2);
    if (newSet1.size() > 0) {
      isEqual = false;
      System.out.println("set1 left with: " + newSet1);
    }
    Set<T> newSet2 = (Set<T>) ((HashSet<T>) set2).clone();
    newSet2.removeAll(set1);
    if (newSet2.size() > 0) {
      isEqual = false;
      System.out.println("set2 left with: " + newSet2);
    }
    return isEqual;
  }

  /*
  public static void testAutomatonCompaction() {
    // make our LossyAutomatonCompactor from the parameters passed at command line
    // now set up the compactor2 constructor args
    // extract a bunch of paths
    Timing.startTime();
    System.out.print("Extracting paths from treebank...");
    allTrainPaths = extractPaths(path, trainLow, trainHigh, false);
    allTestPaths = extractPaths(path, testLow, testHigh, false);
    Timing.tick("done");

    // for each category, construct an automaton and then compact it
    for (Iterator catIter = allTrainPaths.keySet().iterator(); catIter.hasNext();) {
      // construct an automaton from the paths
      String category = (String) catIter.next();
      List trainPaths = (List) allTrainPaths.get(category);
      List testPaths = (List) allTestPaths.get(category);
      if (testPaths == null) testPaths = new ArrayList();
      // now make the graph with the training paths (the LossyAutomatonCompactor will reestimate the weights anyway)
      TransducerGraph graph = TransducerGraph.createGraphFromPaths(trainPaths, 3);
      System.out.println("Created graph for: " + category);

      System.out.println();
      int numArcs1 = graph.getArcs().size();

      LossyAutomatonCompactor compactor = new LossyAutomatonCompactor(3, // horizonOrder, 1 means that only exactly compatible merges are considered
								      0, // min nmber of arcs
								      10000000.0, // maxMergeCost
								      0.5, // splitParam
								      false, //  ignoreUnsupportedSuffixes
                      -1000, // minArcCost
								      trainPaths,
								      testPaths,
								      LossyAutomatonCompactor.DATA_LIKELIHOOD_COST, // costModel
								      false); // verbose

      TransducerGraph result = compactor.compactFA(graph);
      //do we need this?      result = new TransducerGraph(result, ntsp);  // pull out strings from sets returned by minimizer
      int numArcs2 = result.getArcs().size();
      System.out.println("LossyGrammarCompactor compacted "+category+" from " + numArcs1 + " to " + numArcs2 + " arcs");

    }


  }
*/
  private static <T> int numTokens(List<List<T>> paths) {
    int result = 0;
    for (List<T> path : paths) {
      result += path.size();
    }
    return result;
  }

  public void buildAndCompactToyGrammars() {
    // extract a bunch of paths
    System.out.print("Extracting other paths...");
    allTrainPaths = extractPaths(path, trainLow, trainHigh, true);
    TransducerGraph.NodeProcessor ntsp = new TransducerGraph.SetToStringNodeProcessor(new PennTreebankLanguagePack());
    TransducerGraph.NodeProcessor otsp = new TransducerGraph.ObjectToSetNodeProcessor();
    TransducerGraph.ArcProcessor isp = new TransducerGraph.InputSplittingProcessor();
    TransducerGraph.ArcProcessor ocp = new TransducerGraph.OutputCombiningProcessor();
    TransducerGraph.GraphProcessor normalizer = new TransducerGraph.NormalizingGraphProcessor(false);
    TransducerGraph.GraphProcessor quasiDeterminizer = new QuasiDeterminizer();
    AutomatonMinimizer exactMinimizer = new FastExactAutomatonMinimizer();
    for (String key : allTrainPaths.keySet()) {
      System.out.println("creating graph for " + key);
      List<List<String>> paths = allTrainPaths.get(key);
      ClassicCounter<List<String>> pathCounter = new ClassicCounter<>();
      for (List<String> o : paths) {
        pathCounter.incrementCount(o);
      }
      ClassicCounter<List<String>> newPathCounter = removeLowCountPaths(pathCounter, 2);
      paths.retainAll(newPathCounter.keySet()); // get rid of the low count ones
      TransducerGraph result = TransducerGraph.createGraphFromPaths(newPathCounter, 1000);
      // exact compaction
      int numArcs = result.getArcs().size();
      int numNodes = result.getNodes().size();
      if (numArcs == 0) {
        continue;
      }
      System.out.println("initial graph has " + numArcs + " arcs and " + numNodes + " nodes.");
      GrammarCompactor.writeFile(result, "unminimized", key);
      // do exact minimization
      result = normalizer.processGraph(result); // normalize it so that exact minimization works properly
      result = quasiDeterminizer.processGraph(result); // push probabilities left or down
      result = new TransducerGraph(result, ocp); // combine outputs into inputs
      result = exactMinimizer.minimizeFA(result); // minimize the thing
      result = new TransducerGraph(result, ntsp);  // pull out strings from sets returned by minimizer
      result = new TransducerGraph(result, isp); // split outputs from inputs
      numArcs = result.getArcs().size();
      numNodes = result.getNodes().size();

      System.out.println("after exact minimization graph has " + numArcs + " arcs and " + numNodes + " nodes.");
      GrammarCompactor.writeFile(result, "exactminimized", key);

      // do additional lossy minimization
      /*
      NewLossyAutomatonCompactor compactor2 = new NewLossyAutomatonCompactor(paths, true);
      result = compactor2.compactFA(result);
      result = new TransducerGraph(result, ntsp);  // pull out strings from sets returned by minimizer
      numArcs = result.getArcs().size();
      numNodes = result.getNodes().size();

      System.out.println("after lossy minimization graph has " + numArcs + " arcs and " + numNodes + " nodes.");
      GrammarCompactor.writeFile(result, "lossyminimized", key);
      */
    }
  }

  private static ClassicCounter<List<String>> removeLowCountPaths(ClassicCounter<List<String>> paths, double thresh) {
    ClassicCounter<List<String>> result = new ClassicCounter<>();
    int numRetained = 0;
    for (List<String> path : paths.keySet()) {
      double count = paths.getCount(path);
      if (count >= thresh) {
        result.setCount(path, count);
        numRetained++;
      }
    }
    System.out.println("retained " + numRetained);
    return result;
  }

  public void testGrammarCompaction() {

    // these for testing against the markov 3rd order baseline

    // use the parser constructor to extract the grammars from the treebank
    op = new Options();
    LexicalizedParser lp = LexicalizedParser.trainFromTreebank(path, new NumberRangeFileFilter(trainLow, trainHigh, true), op);

    // compact grammars
    if (compactor != null) {

      // extract a bunch of paths
      Timing.startTime();
      System.out.print("Extracting other paths...");
      allTrainPaths = extractPaths(path, trainLow, trainHigh, true);
      allTestPaths = extractPaths(path, testLow, testHigh, true);
      Timing.tick("done");

      // compact grammars
      Timing.startTime();
      System.out.print("Compacting grammars...");
      Pair<UnaryGrammar, BinaryGrammar> grammar = Generics.newPair(lp.ug, lp.bg);
      Triple<Index<String>, UnaryGrammar, BinaryGrammar> compactedGrammar = compactor.compactGrammar(grammar, allTrainPaths, allTestPaths, lp.stateIndex);
      lp.stateIndex = compactedGrammar.first();
      lp.ug = compactedGrammar.second();
      lp.bg = compactedGrammar.third();

      Timing.tick("done.");
    }

    if (asciiOutputPath != null) {
      lp.saveParserToTextFile(asciiOutputPath);
    }

    // test it
    Treebank testTreebank = op.tlpParams.testMemoryTreebank();
    testTreebank.loadPath(path, new NumberRangeFileFilter(testLow, testHigh, true));
    System.out.println("Currently " + new Date());
    EvaluateTreebank evaluator = new EvaluateTreebank(lp);
    evaluator.testOnTreebank(testTreebank);
    System.out.println("Currently " + new Date());
  }

}

class StringUnaryRule {
  public String parent;
  public String child;
  public double score;

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof StringUnaryRule)) {
      return false;
    }

    final StringUnaryRule stringUnaryRule = (StringUnaryRule) o;

    if (score != stringUnaryRule.score) {
      return false;
    }
    if (child != null ? !child.equals(stringUnaryRule.child) : stringUnaryRule.child != null) {
      return false;
    }
    if (parent != null ? !parent.equals(stringUnaryRule.parent) : stringUnaryRule.parent != null) {
      return false;
    }

    return true;
  }

  @Override
  public int hashCode() {
    int result;
    long temp;
    result = (parent != null ? parent.hashCode() : 0);
    result = 29 * result + (child != null ? child.hashCode() : 0);
    temp = Double.doubleToLongBits(score);
    result = 29 * result + (int) (temp ^ (temp >>> 32));
    return result;
  }

  @Override
  public String toString() {
    return "UR:::::" + parent + ":::::" + child + ":::::" + score;
  }

  public StringUnaryRule(String parent, String child, double score) {
    this.parent = parent;
    this.child = child;
    this.score = score;
  }
}

class StringBinaryRule {
  public String parent;
  public String leftChild;
  public String rightChild;
  public double score;

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof StringBinaryRule)) {
      return false;
    }

    final StringBinaryRule stringBinaryRule = (StringBinaryRule) o;

    if (score != stringBinaryRule.score) {
      return false;
    }
    if (leftChild != null ? !leftChild.equals(stringBinaryRule.leftChild) : stringBinaryRule.leftChild != null) {
      return false;
    }
    if (parent != null ? !parent.equals(stringBinaryRule.parent) : stringBinaryRule.parent != null) {
      return false;
    }
    if (rightChild != null ? !rightChild.equals(stringBinaryRule.rightChild) : stringBinaryRule.rightChild != null) {
      return false;
    }

    return true;
  }

  @Override
  public int hashCode() {
    int result;
    long temp;
    result = (parent != null ? parent.hashCode() : 0);
    result = 29 * result + (leftChild != null ? leftChild.hashCode() : 0);
    result = 29 * result + (rightChild != null ? rightChild.hashCode() : 0);
    temp = Double.doubleToLongBits(score);
    result = 29 * result + (int) (temp ^ (temp >>> 32));
    return result;
  }

  @Override
  public String toString() {
    return "BR:::::" + parent + ":::::" + leftChild + ":::::" + rightChild + ":::::" + score;
  }

  public StringBinaryRule(String parent, String leftChild, String rightChild, double score) {
    this.parent = parent;
    this.leftChild = leftChild;
    this.rightChild = rightChild;
    this.score = score;
  }
}
