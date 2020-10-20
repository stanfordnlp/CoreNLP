package edu.stanford.nlp.parser.lexparser; 
import edu.stanford.nlp.util.logging.Redwood;

import java.util.*;

import edu.stanford.nlp.ling.CategoryWordTag;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.HasContext;
import edu.stanford.nlp.ling.HasWord;
import edu.stanford.nlp.ling.Label;
import edu.stanford.nlp.math.SloppyMath;
import edu.stanford.nlp.parser.KBestViterbiParser;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.TreeFactory;
import edu.stanford.nlp.trees.LabeledScoredTreeFactory;
import edu.stanford.nlp.util.*;


/** Implements Eisner and Satta style algorithms for bilexical
 *  PCFG parsing.  The basic class provides O(n<sup>4</sup>)
 *  parsing, with the passed in PCFG and dependency parsers
 *  providing outside scores in an efficient A* search.
 *
 *  @author Dan Klein
 */
public class BiLexPCFGParser implements KBestViterbiParser  {

  /** A logger for this class */
  private static Redwood.RedwoodChannels log = Redwood.channels(BiLexPCFGParser.class);

  protected static final boolean VERBOSE = false;
  protected static final boolean VERY_VERBOSE = false;

  protected HookChart chart;
  protected Heap<Item> agenda;
  protected int length;
  protected int[] words;
  protected Edge goal;
  protected Interner interner;
  protected Scorer scorer;
  protected ExhaustivePCFGParser fscorer;
  protected ExhaustiveDependencyParser dparser;
  protected GrammarProjection projection;
  //pair dep scores

  protected BinaryGrammar bg;
  protected UnaryGrammar ug;
  protected DependencyGrammar dg;
  protected Lexicon lex;
  protected Options op;
  protected List<IntTaggedWord>[] taggedWordList;

  protected final Index<String> wordIndex;
  protected final Index<String> tagIndex;
  protected final Index<String> stateIndex;
  protected CoreLabel[] originalLabels;

  protected TreeFactory tf = new LabeledScoredTreeFactory();

  // temp
  protected long relaxHook1 = 0;
  protected long relaxHook2 = 0;
  protected long relaxHook3 = 0;
  protected long relaxHook4 = 0;

  protected long builtHooks = 0;
  protected long builtEdges = 0;
  protected long extractedHooks = 0;
  protected long extractedEdges = 0;


  private static final double TOL = 1e-10;

  protected static boolean better(double x, double y) {
    return ((x - y) / (Math.abs(x) + Math.abs(y) + 1e-100) > TOL);
  }


  public double getBestScore() {
    if (goal == null) {
      return Double.NEGATIVE_INFINITY;
    } else {
      return goal.score();
    }
  }


  protected Tree extractParse(Edge edge) {
    String head = wordIndex.get(words[edge.head]);
    String tag = tagIndex.get(edge.tag);
    String state = stateIndex.get(edge.state);
    Label label = new CategoryWordTag(state, head, tag);
    if (edge.backEdge == null && edge.backHook == null) {
      // leaf, but needs word terminal
      Tree leaf;
      if (originalLabels[edge.head] != null) {
        leaf = tf.newLeaf(originalLabels[edge.head]);
      } else {
        leaf = tf.newLeaf(head);
      }
      List<Tree> childList = Collections.singletonList(leaf);
      return tf.newTreeNode(label, childList);
    }
    if (edge.backHook == null) {
      // unary
      List<Tree> childList = Collections.singletonList(extractParse(edge.backEdge));
      return tf.newTreeNode(label, childList);
    }
    // binary
    List<Tree> children = new ArrayList<>();
    if (edge.backHook.isPreHook()) {
      children.add(extractParse(edge.backEdge));
      children.add(extractParse(edge.backHook.backEdge));
    } else {
      children.add(extractParse(edge.backHook.backEdge));
      children.add(extractParse(edge.backEdge));
    }
    return tf.newTreeNode(label, children);
  }

  /**
   * Return the best parse of the sentence most recently parsed.
   *
   * @return The best (highest score) tree
   */
  public Tree getBestParse() {
    return extractParse(goal);
  }


  public boolean hasParse() {
    return goal != null && goal.iScore != Double.NEGATIVE_INFINITY;
  }


  // Added by Dan Zeman to store the list of N best trees.
  protected List<Edge> nGoodTrees = new LinkedList<>();



  /**
   * Return the list of k "good" parses of the sentence most recently parsed.
   * (The first is guaranteed to be the best, but later ones are only
   * guaranteed the best subject to the possibilities that disappear because
   * the PCFG/Dep charts only store the best over each span.)
   *
   * @return The list of k best trees
   */
  public List<ScoredObject<Tree>> getKGoodParses(int k) {
    List<ScoredObject<Tree>> nGoodTreesList = new ArrayList<>(op.testOptions.printFactoredKGood);
    for (Edge e : nGoodTrees) {
      nGoodTreesList.add(new ScoredObject<>(extractParse(e), e.iScore));
    }
    return nGoodTreesList;
  }

  /** Get the exact k best parses for the sentence.
   *
   *  @param k The number of best parses to return
   *  @return The exact k best parses for the sentence, with
   *         each accompanied by its score (typically a
   *         negative log probability).
   */
  public List<ScoredObject<Tree>> getKBestParses(int k) {
    throw new UnsupportedOperationException("BiLexPCFGParser doesn't support k best parses");
  }


  /** Get a complete set of the maximally scoring parses for a sentence,
   *  rather than one chosen at random.  This set may be of size 1 or larger.
   *
   *  @return All the equal best parses for a sentence, with each
   *         accompanied by its score
   */
  public List<ScoredObject<Tree>> getBestParses() {
    throw new UnsupportedOperationException("BiLexPCFGParser doesn't support best parses");
  }

  /** Get k parse samples for the sentence.  It is expected that the
   *  parses are sampled based on their relative probability.
   *
   *  @param k The number of sampled parses to return
   *  @return A list of k parse samples for the sentence, with
   *         each accompanied by its score
   */
  public List<ScoredObject<Tree>> getKSampledParses(int k) {
    throw new UnsupportedOperationException("BiLexPCFGParser doesn't support k sampled parses");
  }

  protected Edge tempEdge;

  protected void combine(Edge edge, Hook hook) {
    if (VERBOSE) {
      log.info("Combining: " + edge + " and " + hook);
    }
    // make result edge
    if (hook.isPreHook()) {
      tempEdge.start = edge.start;
      tempEdge.end = hook.end;
    } else {
      tempEdge.start = hook.start;
      tempEdge.end = edge.end;
    }
    tempEdge.state = hook.state;
    tempEdge.head = hook.head;
    tempEdge.tag = hook.tag;
    tempEdge.iScore = hook.iScore + edge.iScore;
    tempEdge.backEdge = edge;
    tempEdge.backHook = hook;
    relaxTempEdge();
  }

  protected void relaxTempEdge() {
    // if (tempEdge.iScore > scorer.iScore(tempEdge)+1e-4) {
    //   log.info(tempEdge+" has i "+tempEdge.iScore+" iE "+scorer.iScore(tempEdge));
    // }
    Edge resultEdge = (Edge) interner.intern(tempEdge);
    if (VERBOSE) {
      System.err.printf("Formed %s %s %.2f was %.2f better? %b\n", (resultEdge == tempEdge ? "new" : "pre-existing"), resultEdge, tempEdge.iScore, resultEdge.iScore, better(tempEdge.iScore, resultEdge.iScore));
    }
    if (resultEdge == tempEdge) {
      tempEdge = new Edge(op.testOptions.exhaustiveTest);
      discoverEdge(resultEdge);
    } else {
      if (better(tempEdge.iScore, resultEdge.iScore) && resultEdge.oScore > Double.NEGATIVE_INFINITY) {
        // we've found a better way of making an edge that may make a parse
        double back = resultEdge.iScore;
        Edge backE = resultEdge.backEdge;
        Hook backH = resultEdge.backHook;
        resultEdge.iScore = tempEdge.iScore;
        resultEdge.backEdge = tempEdge.backEdge;
        resultEdge.backHook = tempEdge.backHook;
        try {
          agenda.decreaseKey(resultEdge);
        } catch (NullPointerException e) {
          if (false) {
            log.info("");
            log.info("Old backEdge: " + backE + " i " + backE.iScore + " o " + backE.oScore + " s " + backE.score());
            log.info("Old backEdge: " + backE + " iE " + scorer.iScore(backE));
            log.info("Old backHook: " + backH + " i " + backH.iScore + " o " + backH.oScore + " s " + backH.score());
            log.info("New backEdge: " + tempEdge.backEdge + " i " + tempEdge.backEdge.iScore + " o " + tempEdge.backEdge.oScore + " s " + tempEdge.backEdge.score());
            log.info("New backEdge: " + tempEdge.backEdge + " iE " + scorer.iScore(tempEdge.backEdge));
            log.info("New backHook: " + tempEdge.backHook + " i " + tempEdge.backHook.iScore + " o " + tempEdge.backHook.oScore + " s " + tempEdge.backHook.score());
            log.error("Formed " + resultEdge + " i " + tempEdge.iScore + " o " + resultEdge.oScore + " s " + resultEdge.score());
            log.error("Formed " + resultEdge + " " + (resultEdge == tempEdge ? "new" : "old") + " " + tempEdge.iScore + " was " + back + " better? " + better(tempEdge.iScore, back));
          }
        }
      }
    }
  }

  protected void discoverEdge(Edge edge) {
    // create new edge
    edge.oScore = scorer.oScore(edge);
    agenda.add(edge);
    builtEdges++;
  }

  protected void discoverHook(Hook hook) {
    hook.oScore = buildOScore(hook);
    if (hook.oScore == Double.NEGATIVE_INFINITY) {
      relaxHook4++;
    }
    builtHooks++;
    agenda.add(hook);
  }

  protected double buildOScore(Hook hook) {
    double bestOScore = Double.NEGATIVE_INFINITY;
    Edge iTemp = new Edge(op.testOptions.exhaustiveTest);
    Edge oTemp = new Edge(op.testOptions.exhaustiveTest);
    iTemp.head = hook.head;
    iTemp.tag = hook.tag;
    iTemp.state = hook.subState;
    oTemp.head = hook.head;
    oTemp.tag = hook.tag;
    oTemp.state = hook.state;
    if (hook.isPreHook()) {
      iTemp.end = hook.start;
      oTemp.end = hook.end;
      for (int start = 0; start <= hook.head; start++) {
        iTemp.start = start;
        oTemp.start = start;
        double oScore = scorer.oScore(oTemp) + scorer.iScore(iTemp);
        //log.info("Score for "+hook+" is i "+iTemp+" ("+scorer.iScore(iTemp)+") o "+oTemp+" ("+scorer.oScore(oTemp)+")");
        bestOScore = SloppyMath.max(bestOScore, oScore);
      }
    } else {
      iTemp.start = hook.end;
      oTemp.start = hook.start;
      for (int end = hook.head + 1; end <= length; end++) {
        iTemp.end = end;
        oTemp.end = end;
        double oScore = scorer.oScore(oTemp) + scorer.iScore(iTemp);
        bestOScore = SloppyMath.max(bestOScore, oScore);
      }
    }
    return bestOScore;
  }

  protected Hook tempHook;

  protected void projectHooks(Edge edge) {
    // form hooks
    // POST HOOKS
    //for (Iterator rI = bg.ruleIteratorByLeftChild(edge.state);
    //      rI.hasNext(); ) {
    List<BinaryRule> ruleList = bg.ruleListByLeftChild(edge.state);
    for (BinaryRule br : ruleList) {
      //BinaryRule br = rI.next();
      if (scorer instanceof LatticeScorer) {
        LatticeScorer lscorer = (LatticeScorer) scorer;
        Edge latEdge = (Edge) lscorer.convertItemSpan(new Edge(edge));
        if (!fscorer.oPossibleL(project(br.parent), latEdge.start) || !fscorer.iPossibleL(project(br.rightChild), latEdge.end)) {
          if (!op.testOptions.exhaustiveTest) {
            continue;
          }
        }
      } else {
        if (!fscorer.oPossibleL(project(br.parent), edge.start) || !fscorer.iPossibleL(project(br.rightChild), edge.end)) {
          if (!op.testOptions.exhaustiveTest) {
            continue;
          }
        }
      }
      for (int head = edge.end; head < length; head++) {
        // cdm Apr 2006: avoid Iterator allocation
        // for (Iterator iTWI = taggedWordList[head].iterator(); iTWI.hasNext();) {
        // IntTaggedWord iTW = (IntTaggedWord) iTWI.next();
        for (int hdi = 0, sz = taggedWordList[head].size(); hdi < sz; hdi++) {
          IntTaggedWord iTW = taggedWordList[head].get(hdi);
          int tag = iTW.tag;
          tempHook.start = edge.start;
          tempHook.end = edge.end;
          tempHook.head = head;
          tempHook.tag = tag;
          tempHook.state = br.parent;
          tempHook.subState = br.rightChild;
          if (!chart.isBuiltL(tempHook.subState, tempHook.end, tempHook.head, tempHook.tag)) {
            continue;
          }
          tempHook.iScore = edge.iScore + br.score + dparser.headScore[dparser.binDistance[head][edge.end]][head][dg.tagBin(tag)][edge.head][dg.tagBin(edge.tag)] + dparser.headStop[edge.head][dg.tagBin(edge.tag)][edge.start] + dparser.headStop[edge.head][dg.tagBin(edge.tag)][edge.end];
          tempHook.backEdge = edge;
          relaxTempHook();
        }
      }
    }
    // PRE HOOKS
    //for (Iterator<BinaryRule> rI = bg.ruleIteratorByRightChild(edge.state);
    //     rI.hasNext(); ) {
    ruleList = bg.ruleListByRightChild(edge.state);
    for (BinaryRule br : ruleList) {
      //BinaryRule br = rI.next();
      if (scorer instanceof LatticeScorer) {
        LatticeScorer lscorer = (LatticeScorer) scorer;
        Edge latEdge = (Edge) lscorer.convertItemSpan(new Edge(edge));
        if (!fscorer.oPossibleR(project(br.parent), latEdge.end) || !fscorer.iPossibleR(project(br.leftChild), latEdge.start)) {
          if (!op.testOptions.exhaustiveTest) {
            continue;
          }
        }
      } else {
        if (!fscorer.oPossibleR(project(br.parent), edge.end) || !fscorer.iPossibleR(project(br.leftChild), edge.start)) {
          if (!op.testOptions.exhaustiveTest) {
            continue;
          }
        }
      }
      for (int head = 0; head < edge.start; head++) {
        // cdm Apr 2006: avoid Iterator allocation
        // for (Iterator iTWI = taggedWordList[head].iterator(); iTWI.hasNext();) {
        //IntTaggedWord iTW = (IntTaggedWord) iTWI.next();
        for (int hdi = 0, sz = taggedWordList[head].size(); hdi < sz; hdi++) {
          IntTaggedWord iTW = taggedWordList[head].get(hdi);
          int tag = iTW.tag;
          tempHook.start = edge.start;
          tempHook.end = edge.end;
          tempHook.head = head;
          tempHook.tag = tag;
          tempHook.state = br.parent;
          tempHook.subState = br.leftChild;
          if (!chart.isBuiltR(tempHook.subState, tempHook.start, tempHook.head, tempHook.tag)) {
            continue;
          }
          tempHook.iScore = edge.iScore + br.score + dparser.headScore[dparser.binDistance[head][edge.start]][head][dg.tagBin(tag)][edge.head][dg.tagBin(edge.tag)] + dparser.headStop[edge.head][dg.tagBin(edge.tag)][edge.start] + dparser.headStop[edge.head][dg.tagBin(edge.tag)][edge.end];
          tempHook.backEdge = edge;
          relaxTempHook();
        }
      }
    }
  }

  protected void registerReal(Edge real) {
    chart.registerRealEdge(real);
  }

  protected void triggerHooks(Edge edge) {
    // we might have built a synth edge, enabling some old real edges to project hooks (the difference between this method and triggerAllHooks is that here we look only at realEdges)
    boolean newL = !chart.isBuiltL(edge.state, edge.start, edge.head, edge.tag);
    boolean newR = !chart.isBuiltR(edge.state, edge.end, edge.head, edge.tag);
    if (VERY_VERBOSE) {
      if (newL) {
        log.info("Triggering on L: " + edge);
      }
      if (newR) {
        log.info("Triggering on R: " + edge);
      }
    }
    chart.registerEdgeIndexes(edge);
    if (newR) {
      // PRE HOOKS
      BinaryRule[] rules = bg.splitRulesWithLC(edge.state);
      for (BinaryRule br : rules) {
        Collection<Edge> realEdges = chart.getRealEdgesWithL(br.rightChild, edge.end);
        for (Edge real : realEdges) {
          tempHook.start = real.start;
          tempHook.end = real.end;
          tempHook.state = br.parent;
          tempHook.subState = br.leftChild;
          tempHook.head = edge.head;
          tempHook.tag = edge.tag;
          tempHook.backEdge = real;
          tempHook.iScore = real.iScore + br.score + dparser.headScore[dparser.binDistance[edge.head][edge.end]][edge.head][dg.tagBin(edge.tag)][real.head][dg.tagBin(real.tag)] + dparser.headStop[real.head][dg.tagBin(real.tag)][real.start] + dparser.headStop[real.head][dg.tagBin(real.tag)][real.end];
          relaxTempHook();
        }
      }
    }
    if (newL) {
      // POST HOOKS
      BinaryRule[] rules = bg.splitRulesWithRC(edge.state);
      for (BinaryRule br : rules) {
        Collection<Edge> realEdges = chart.getRealEdgesWithR(br.leftChild, edge.start);
        for (Edge real : realEdges) {
          tempHook.start = real.start;
          tempHook.end = real.end;
          tempHook.state = br.parent;
          tempHook.subState = br.rightChild;
          tempHook.head = edge.head;
          tempHook.tag = edge.tag;
          tempHook.backEdge = real;
          tempHook.iScore = real.iScore + br.score + dparser.headScore[dparser.binDistance[edge.head][edge.start]][edge.head][dg.tagBin(edge.tag)][real.head][dg.tagBin(real.tag)] + dparser.headStop[real.head][dg.tagBin(real.tag)][real.start] + dparser.headStop[real.head][dg.tagBin(real.tag)][real.end];
          relaxTempHook();
        }
      }
    }
  }

  protected void triggerAllHooks(Edge edge) {
    // we might have built a new edge, enabling some old edges to project hooks
    boolean newL = !chart.isBuiltL(edge.state, edge.start, edge.head, edge.tag);
    boolean newR = !chart.isBuiltR(edge.state, edge.end, edge.head, edge.tag);
    if (VERY_VERBOSE) {
      if (newL) {
        log.info("Triggering on L: " + edge);
      }
      if (newR) {
        log.info("Triggering on R: " + edge);
      }
    }
    chart.registerEdgeIndexes(edge);
    if (newR) {
      // PRE HOOKS
      for (Iterator<BinaryRule> rI = bg.ruleIteratorByLeftChild(edge.state); rI.hasNext();) {
        BinaryRule br = rI.next();
        Collection<Edge> edges = chart.getRealEdgesWithL(br.rightChild, edge.end);
        for (Edge real : edges) {
          tempHook.start = real.start;
          tempHook.end = real.end;
          tempHook.state = br.parent;
          tempHook.subState = br.leftChild;
          tempHook.head = edge.head;
          tempHook.tag = edge.tag;
          tempHook.backEdge = real;
          tempHook.iScore = real.iScore + br.score + dparser.headScore[dparser.binDistance[edge.head][edge.end]][edge.head][dg.tagBin(edge.tag)][real.head][dg.tagBin(real.tag)] + dparser.headStop[real.head][dg.tagBin(real.tag)][real.start] + dparser.headStop[real.head][dg.tagBin(real.tag)][real.end];
          relaxTempHook();
        }
      }
    }
    if (newL) {
      // POST HOOKS
      for (Iterator rI = bg.ruleIteratorByRightChild(edge.state); rI.hasNext();) {
        BinaryRule br = (BinaryRule) rI.next();
        Collection<Edge> edges = chart.getRealEdgesWithR(br.leftChild, edge.start);
        if (VERBOSE) {
          log.info("Looking for: " + stateIndex.get(br.leftChild) + " ending at " + edge.start);
          log.info("Found: " + edges);
        }
        for (Edge real : edges) {
          tempHook.start = real.start;
          tempHook.end = real.end;
          tempHook.state = br.parent;
          tempHook.subState = br.rightChild;
          tempHook.head = edge.head;
          tempHook.tag = edge.tag;
          tempHook.backEdge = real;
          tempHook.iScore = real.iScore + br.score + dparser.headScore[dparser.binDistance[edge.head][edge.start]][edge.head][dg.tagBin(edge.tag)][real.head][dg.tagBin(real.tag)] + dparser.headStop[real.head][dg.tagBin(real.tag)][real.start] + dparser.headStop[real.head][dg.tagBin(real.tag)][real.end];
          relaxTempHook();
        }
      }
    }
  }


  protected void relaxTempHook() {
    relaxHook1++;
    if (VERBOSE) {
      log.info("Considering: " + tempHook + " iP: " + scorer.iPossible(tempHook) + " oP: " + scorer.oPossible(tempHook));
    }
    if (!op.testOptions.exhaustiveTest) {
      if (!scorer.oPossible(tempHook) || !scorer.iPossible(tempHook)) {
        return;
      }
    }
    relaxHook2++;
    Hook resultHook = (Hook) interner.intern(tempHook);
    if (VERBOSE) {
      System.err.printf("Formed %s %s %f was %f\n", resultHook, (resultHook == tempHook ? "new" : "old"), tempHook.iScore, resultHook.iScore);
      if (resultHook.backEdge != null) {
        log.info("  Backtrace: " + resultHook.backEdge);
      }
    }
    if (resultHook == tempHook) {
      relaxHook3++;
      tempHook = new Hook(op.testOptions.exhaustiveTest);
      discoverHook(resultHook);
    }
    if (better(tempHook.iScore, resultHook.iScore)) {
      resultHook.iScore = tempHook.iScore;
      resultHook.backEdge = tempHook.backEdge;
      try {
        agenda.decreaseKey(resultHook);
      } catch (NullPointerException e) {
      }
    }
  }

  protected void projectUnaries(Edge edge) {
    for (Iterator rI = ug.ruleIteratorByChild(edge.state); rI.hasNext();) {
      UnaryRule ur = (UnaryRule) rI.next();
      if (ur.child == ur.parent) {
        continue;
      }
      tempEdge.start = edge.start;
      tempEdge.end = edge.end;
      tempEdge.head = edge.head;
      tempEdge.tag = edge.tag;
      tempEdge.state = ur.parent;
      tempEdge.backEdge = edge;
      tempEdge.backHook = null;
      tempEdge.iScore = edge.iScore + ur.score;
      relaxTempEdge();
    }
  }

  protected void processEdge(Edge edge) {
    // add to chart
    if (VERBOSE) {
      log.info("Adding to chart: " + edge);
    }
    chart.addEdge(edge);
    // fetch existing hooks that can combine with it and combine them
    for (Hook hook : chart.getPreHooks(edge)) {
      combine(edge, hook);
    }
    for (Hook hook : chart.getPostHooks(edge)) {
      combine(edge, hook);
    }
    // do projections
    //if (VERBOSE) log.info("Projecting: "+edge);
    projectUnaries(edge);
    if (!bg.isSynthetic(edge.state) && !op.freeDependencies) {
      projectHooks(edge);
      registerReal(edge);
    }
    if (op.freeDependencies) {
      projectHooks(edge);
      registerReal(edge);
      triggerAllHooks(edge);
    } else {
      triggerHooks(edge);
    }
  }

  protected void processHook(Hook hook) {
    // add to chart
    //if (VERBOSE) log.info("Adding to chart: "+hook);
    chart.addHook(hook);
    Collection<Edge> edges = chart.getEdges(hook);
    for (Edge edge : edges) {
      combine(edge, hook);
    }
  }

  protected void processItem(Item item) {
    if (item.isEdge()) {
      processEdge((Edge) item);
    } else {
      processHook((Hook) item);
    }
  }

  protected void discoverItem(Item item) {
    if (item.isEdge()) {
      discoverEdge((Edge) item);
    } else {
      discoverHook((Hook) item);
    }
  }

  protected Item makeInitialItem(int pos, int tag, int state, double iScore) {
    Edge edge = new Edge(op.testOptions.exhaustiveTest);
    edge.start = pos;
    edge.end = pos + 1;
    edge.state = state;
    edge.head = pos;
    edge.tag = tag;
    edge.iScore = iScore;
    return edge;
  }

  protected List<Item> makeInitialItems(List<? extends HasWord> wordList) {
    List<Item> itemList = new ArrayList<>();
    int length = wordList.size();
    int numTags = tagIndex.size();
    words = new int[length];
    taggedWordList = new List[length];
    int terminalCount = 0;
    originalLabels = new CoreLabel[wordList.size()];
    for (int i = 0; i < length; i++) {
      taggedWordList[i] = new ArrayList<>(numTags);
      HasWord wordObject = wordList.get(i);
      if (wordObject instanceof CoreLabel) {
        originalLabels[i] = (CoreLabel) wordObject;
      }
      String wordStr = wordObject.word();

      //Word context (e.g., morphosyntactic info)
      String wordContextStr = null;
      if(wordObject instanceof HasContext) {
        wordContextStr = ((HasContext) wordObject).originalText();
        if("".equals(wordContextStr))
          wordContextStr = null;
      }

      if (!wordIndex.contains(wordStr)) {
        wordStr = Lexicon.UNKNOWN_WORD;
      }
      int word = wordIndex.indexOf(wordStr);
      words[i] = word;
      for (Iterator<IntTaggedWord> tagI = lex.ruleIteratorByWord(word, i, wordContextStr); tagI.hasNext(); ) {
        IntTaggedWord tagging = tagI.next();
        int tag = tagging.tag;
        //String curTagStr = tagIndex.get(tag);
        //if (!tagStr.equals("") && !tagStr.equals(curTagStr))
        //  continue;
        int state = stateIndex.indexOf(tagIndex.get(tag));
        //itemList.add(makeInitialItem(i,tag,state,1.0*tagging.score));
        // THIS WILL CAUSE BUGS!!!  Don't use with another A* scorer
        tempEdge.state = state;
        tempEdge.head = i;
        tempEdge.start = i;
        tempEdge.end = i + 1;
        tempEdge.tag = tag;
        itemList.add(makeInitialItem(i, tag, state, scorer.iScore(tempEdge)));
        terminalCount++;
        taggedWordList[i].add(new IntTaggedWord(word, tag));
      }
    }
    if (op.testOptions.verbose) {
      log.info("Terminals (# of tag edges in chart): " +
                         terminalCount);
    }
    return itemList;
  }

  protected void scoreDependencies() {
    // just leach it off the dparser for now...
    /*
    IntDependency dependency = new IntDependency();
    for (int head = 0; head < words.length; head++) {
      for (int hTag = 0; hTag < tagIndex.size(); hTag++) {
        for (int arg = 0; arg < words.length; arg++) {
          for (int aTag = 0; aTag < tagIndex.size(); aTag++) {
            Arrays.fill(depScore[head][hTag][arg][aTag],Float.NEGATIVE_INFINITY);
          }
        }
      }
    }
    for (int head = 0; head < words.length; head++) {
      for (int arg = 0; arg < words.length; arg++) {
        if (head == arg)
          continue;
        for (Iterator<IntTaggedWord> headTWI=taggedWordList[head].iterator(); headTWI.hasNext();) {
          IntTaggedWord headTW = headTWI.next();
          for (Iterator<IntTaggedWord> argTWI=taggedWordList[arg].iterator(); argTWI.hasNext();) {
            IntTaggedWord argTW = argTWI.next();
            dependency.head = headTW;
            dependency.arg = argTW;
            dependency.leftHeaded = (head < arg);
            dependency.distance = Math.abs(head-arg);
            depScore[head][headTW.tag][arg][argTW.tag] =
              dg.score(dependency);
            if (false && depScore[head][headTW.tag][arg][argTW.tag] > -100)
              log.info(wordIndex.get(headTW.word)+"/"+tagIndex.get(headTW.tag)+" -> "+wordIndex.get(argTW.word)+"/"+tagIndex.get(argTW.tag)+" score "+depScore[head][headTW.tag][arg][argTW.tag]);
          }
        }
      }
    }
    */
  }

  protected void setGoal(int length) {
    goal = new Edge(op.testOptions.exhaustiveTest);
    goal.start = 0;
    goal.end = length;
    goal.state = stateIndex.indexOf(op.langpack().startSymbol());
    goal.tag = tagIndex.indexOf(Lexicon.BOUNDARY_TAG);
    goal.head = length - 1;
    //goal = (Edge)interner.intern(goal);
  }

  protected void initialize(List<? extends HasWord> words) {
    length = words.size();
    interner = new Interner();
    agenda = new ArrayHeap<>(ScoredComparator.DESCENDING_COMPARATOR);
    chart = new HookChart();
    setGoal(length);
    List<Item> initialItems = makeInitialItems(words);
//    scoreDependencies();
    for (Item item : initialItems) {
      item = (Item) interner.intern(item);
      //if (VERBOSE) log.info("Initial: "+item);
      discoverItem(item);
    }
  }

  /**
   * Parse a Sentence.
   *
   * @return true iff it could be parsed
   */
  public boolean parse(List<? extends HasWord> words) {
    int nGoodRemaining = 0;
    if (op.testOptions.printFactoredKGood > 0) {
      nGoodRemaining = op.testOptions.printFactoredKGood;
      nGoodTrees.clear();
    }

    int spanFound = 0;
    long last = 0;
    int exHook = 0;
    relaxHook1 = 0;
    relaxHook2 = 0;
    relaxHook3 = 0;
    relaxHook4 = 0;
    builtHooks = 0;
    builtEdges = 0;
    extractedHooks = 0;
    extractedEdges = 0;
    if (op.testOptions.verbose) {
      Timing.tick("Starting combined parse.");
    }
    dparser.binDistance = dparser.binDistance; // THIS IS TERRIBLE, BUT SAVES MEMORY
    initialize(words);
    while (!agenda.isEmpty()) {
      Item item = agenda.extractMin();
      if (!item.isEdge()) {
        exHook++;
        extractedHooks++;
      } else {
        extractedEdges++;
      }
      if (relaxHook1 > last + 1000000) {
        last = relaxHook1;
        if (op.testOptions.verbose) {
          log.info("Proposed hooks:   " + relaxHook1);
          log.info("Unfiltered hooks: " + relaxHook2);
          log.info("Built hooks:      " + relaxHook3);
          log.info("Waste hooks:      " + relaxHook4);
          log.info("Extracted hooks:  " + exHook);
        }
      }
      if (item.end - item.start > spanFound) {
        spanFound = item.end - item.start;
        if (op.testOptions.verbose) {
          log.info(spanFound + " ");
        }
      }
      //if (item.end < 5) log.info("Extracted: "+item+" iScore "+item.iScore+" oScore "+item.oScore+" score "+item.score());
      if (item.equals(goal)) {
        if (op.testOptions.verbose) {
          log.info("Found goal!");
          log.info("Comb iScore " + item.iScore); // was goal.iScore
          Timing.tick("Done, parse found.");
          log.info("Built items:      " + (builtEdges + builtHooks));
          log.info("Built hooks:      " + builtHooks);
          log.info("Built edges:      " + builtEdges);
          log.info("Extracted items:  " + (extractedEdges + extractedHooks));
          log.info("Extracted hooks:  " + extractedHooks);
          log.info("Extracted edges:  " + extractedEdges);
          //postMortem();
        }
        if (op.testOptions.printFactoredKGood <= 0) {
          goal = (Edge) item;
          interner = null;
          agenda = null;
          return true;
        } else {
          // Store the parse
          goal = (Edge) item;
          nGoodTrees.add(goal);
          nGoodRemaining--;
          if (nGoodRemaining > 0) {
            if (VERBOSE) {
              log.info("Found parse! Number of remaining trees to find = " + nGoodRemaining);
            }
          } else {
            if (VERBOSE) {
              log.info("Found last parse!");
            }
            interner = null;
            agenda = null;
            return true;
          }
        }
      }
      // Is the currently best item acceptable at all?
      if (item.score() == Double.NEGATIVE_INFINITY) {
        // Do not report failure in nGood mode if we found something earlier.
        if (nGoodTrees.size() > 0) {
          if (VERBOSE) {
            log.info("Aborting kGood search because of an unacceptable (-Inf) item: " + item);
          }
          goal = nGoodTrees.get(0);
          interner = null;
          agenda = null;
          return true;
        }
        log.info("FactoredParser: no consistent parse [hit A*-blocked edges, aborting].");
        if (op.testOptions.verbose) {
          Timing.tick("FactoredParser: no consistent parse [hit A*-blocked edges, aborting].");
        }
        return false;
      }
      // Keep the number of items from getting too large
      if (op.testOptions.MAX_ITEMS > 0 && (builtEdges + builtHooks) >= op.testOptions.MAX_ITEMS) {
        // Do not report failure in kGood mode if we found something earlier.
        if (nGoodTrees.size() > 0) {
          log.info("DEBUG: aborting search because of reaching the MAX_ITEMS work limit [" +
                             op.testOptions.MAX_ITEMS + " items]");
          goal = nGoodTrees.get(0);
          interner = null;
          agenda = null;
          return true;
        }
        log.info("FactoredParser: exceeded MAX_ITEMS work limit [" +
                           op.testOptions.MAX_ITEMS + " items]; aborting.");
        if (op.testOptions.verbose) {
          Timing.tick("FactoredParser: exceeded MAX_ITEMS work limit [" +
                      op.testOptions.MAX_ITEMS + " items]; aborting.");
        }
        return false;
      }
      if (VERBOSE && item.score() != Double.NEGATIVE_INFINITY) {
        System.err.printf("Removing from agenda: %s score i %.2f + o %.2f = %.2f\n", item, item.iScore, item.oScore, item.score());
        if (item.backEdge != null) {
          log.info("  Backtrace: " + item.backEdge.toString() + " " + (item.isEdge() ? (((Edge) item).backHook != null ? ((Edge) item).backHook.toString() : "") : ""));
        }
      }
      processItem(item);
    } // end while agenda is not empty
    // If we are here, the agenda is empty.
    // Do not report failure if we found something earlier.
    if (nGoodTrees.size() > 0) {
      log.info("DEBUG: aborting search because of empty agenda");
      goal = nGoodTrees.get(0);
      interner = null;
      agenda = null;
      return true;
    }
    log.info("FactoredParser: emptied agenda, no parse found!");
    if (op.testOptions.verbose) {
      Timing.tick("FactoredParser: emptied agenda, no parse found!");
    }
    return false;
  }


  protected void postMortem() {
    int numHooks = 0;
    int numEdges = 0;
    int numUnmatchedHooks = 0;
    int total = agenda.size();
    int done = 0;
    while (!agenda.isEmpty()) {
      Item item = agenda.extractMin();
      done++;
      //if(done % (total/10) == 0)
      //        log.info("Scanning: "+100*done/total);
      if (item.isEdge()) {
        numEdges++;
      } else {
        numHooks++;
        Collection edges = chart.getEdges((Hook) item);
        if (edges.size() == 0) {
          numUnmatchedHooks++;
        }
      }
    }
    log.info("--- Agenda Post-Mortem ---");
    log.info("Edges:           " + numEdges);
    log.info("Hooks:           " + numHooks);
    log.info("Unmatched Hooks: " + numUnmatchedHooks);
  }

  protected int project(int state) {
    return projection.project(state);
  }

  public BiLexPCFGParser(Scorer scorer, ExhaustivePCFGParser fscorer, ExhaustiveDependencyParser dparser, BinaryGrammar bg, UnaryGrammar ug, DependencyGrammar dg, Lexicon lex, Options op, Index<String> stateIndex, Index<String> wordIndex, Index<String> tagIndex) {
    this(scorer, fscorer, dparser, bg, ug, dg, lex, op, new NullGrammarProjection(bg, ug), stateIndex, wordIndex, tagIndex);
  }

  BiLexPCFGParser(Scorer scorer, ExhaustivePCFGParser fscorer, ExhaustiveDependencyParser dparser, BinaryGrammar bg, UnaryGrammar ug, DependencyGrammar dg, Lexicon lex, Options op, GrammarProjection projection, Index<String> stateIndex, Index<String> wordIndex, Index<String> tagIndex) {
    this.fscorer = fscorer;
    this.projection = projection;
    this.dparser = dparser;
    this.scorer = scorer;
    this.bg = bg;
    this.ug = ug;
    this.dg = dg;
    this.lex = lex;
    this.op = op;
    this.stateIndex = stateIndex;
    this.wordIndex = wordIndex;
    this.tagIndex = tagIndex;

    tempEdge = new Edge(op.testOptions.exhaustiveTest);
    tempHook = new Hook(op.testOptions.exhaustiveTest);
  }

  public static class N5BiLexPCFGParser extends BiLexPCFGParser {

    @Override
    protected void relaxTempHook() {
      relaxHook1++;
      if (VERBOSE) {
        log.info("Considering: " + tempHook + " iP: " + scorer.iPossible(tempHook) + " oP: " + scorer.oPossible(tempHook));
      }
      if (!op.testOptions.exhaustiveTest) {
        if (!scorer.oPossible(tempHook) || !scorer.iPossible(tempHook)) {
          return;
        }
      }
      relaxHook2++;
      Hook resultHook = tempHook;
      //Hook resultHook = (Hook)interner.intern(tempHook);
      if (VERBOSE) {
        log.info("Formed " + resultHook + " " + (resultHook == tempHook ? "new" : "old") + " " + tempHook.iScore + " was " + resultHook.iScore);
      }
      if (resultHook == tempHook) {
        relaxHook3++;
        tempHook = new Hook(op.testOptions.exhaustiveTest);
        processHook(resultHook);
        builtHooks++;
      }
    }

    N5BiLexPCFGParser(Scorer scorer, ExhaustivePCFGParser fscorer, ExhaustiveDependencyParser leach, BinaryGrammar bg, UnaryGrammar ug, DependencyGrammar dg, Lexicon lex, Options op, Index<String> stateIndex, Index<String> wordIndex, Index<String> tagIndex) {
      super(scorer, fscorer, leach, bg, ug, dg, lex, op, new NullGrammarProjection(bg, ug), stateIndex, wordIndex, tagIndex);
    }

    N5BiLexPCFGParser(Scorer scorer, ExhaustivePCFGParser fscorer, ExhaustiveDependencyParser leach, BinaryGrammar bg, UnaryGrammar ug, DependencyGrammar dg, Lexicon lex, Options op, GrammarProjection proj, Index<String> stateIndex, Index<String> wordIndex, Index<String> tagIndex) {
      super(scorer, fscorer, leach, bg, ug, dg, lex, op, proj, stateIndex, wordIndex, tagIndex);
    }

  } // end class N5BiLexPCFGParser

} // end class BiLexPCFGParser
