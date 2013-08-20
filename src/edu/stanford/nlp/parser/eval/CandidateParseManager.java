package edu.stanford.nlp.parser.eval;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Reader;
import java.io.StringReader;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import edu.stanford.nlp.ling.HasTag;
import edu.stanford.nlp.ling.StringLabel;
import edu.stanford.nlp.ling.TaggedWord;
import edu.stanford.nlp.parser.lexparser.AbstractTreebankParserParams;
import edu.stanford.nlp.parser.lexparser.EnglishTreebankParserParams;
import edu.stanford.nlp.parser.lexparser.TreebankLangParserParams;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.stats.Counters;
import edu.stanford.nlp.stats.EquivalenceClassEval;
import edu.stanford.nlp.stats.EquivalenceClasser;
import edu.stanford.nlp.trees.Constituent;
import edu.stanford.nlp.trees.DependencyTyper;
import edu.stanford.nlp.trees.HeadFinder;
import edu.stanford.nlp.trees.LabeledConstituent;
import edu.stanford.nlp.trees.PennTreeReader;
import edu.stanford.nlp.trees.PennTreeReaderFactory;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.TreeReader;
import edu.stanford.nlp.trees.TreeReaderFactory;
import edu.stanford.nlp.trees.TreeTransformer;
import edu.stanford.nlp.util.Filter;
import edu.stanford.nlp.util.Sets;
import edu.stanford.nlp.util.StringUtils;

/**
 * A utility class to manage multiple candidate parses for a single tree.   Some uses:
 * <ul>
 * <li>{@link #readCandidateSets}: reads in some candidate parse sets
 * <li>{@link #readSingletonCandidates}: reads in some singleton candidate parse sets (use this typically for evaluating single-output parsing)
 * <li>{@link #bestPerformance}({@link #parsevalObjectifier}) : determines the parseval performance choosing the best candidate for each gold tree
 * <li>bestPerformance({@link #typedDependencyObjectifier}) : determines best-case typed dependency performance
 * <li>bestPerformance({@link #untypedDependencyObjectifier}) : determines best-case untyped dependency performance
 * </ul>
 *
 * @author Roger Levy
 * @author Sarah Spikes (sdspikes@cs.stanford.edu) - cleanup and filling in types
 */
public class CandidateParseManager {

  private boolean verbose = false;

  private String rootCategory;

  private Pattern spliceOut = null;

  private Pattern lengthPrune = null;

  private static class RegexFilter implements Filter<Tree> {
    /**
     * 
     */
    private static final long serialVersionUID = -3422583330068617937L;
    private Pattern p;

    public RegexFilter(Pattern p) {
      this.p = p;
    }

    public boolean accept(Tree tree) {
      return p == null ? true : ! p.matcher(tree.label().value()).matches();
    }
  }


  private int maxLength = Integer.MAX_VALUE;

  Collection<CandidateParses> parseSets;

  private static final boolean DEBUG = false;

  public CandidateParseManager() {
    parseSets = new ArrayList<CandidateParses>();
  }

  public CandidateParseManager(int maxLength, Pattern l, Pattern s, String rootCat) {
    this();
    this.maxLength = maxLength;
    this.lengthPrune = l;
    this.spliceOut = s;
    this.rootCategory = rootCat;
  }

  /* this objectifier does *NOT* treat you well if the collinizer strips different terminals from the gold and test trees. */
  private static TreeObjectifier<Constituent> tagObjectifier(final TreeTransformer collinizer, final Pattern ignoreBrackets) {
    return new TreeObjectifier<Constituent>() {
      public Collection<Constituent> objectify(Tree t) {
        Collection<Constituent> result = new ArrayList<Constituent>();
        Tree t1 = collinizer.transformTree(t);
        if(t1==null)
          return result;
        ArrayList<TaggedWord> tws = t1.taggedYield();
        int i = 0;
        for(TaggedWord tw : tws) {
          result.add(new LabeledConstituent(i,i+1,((HasTag) tw).tag()));
          i++;
        }
        Collection<Constituent> toRemove = new ArrayList<Constituent>();
        if(ignoreBrackets != null)
          for(Constituent c : result) {
            if(ignoreBrackets.matcher(c.label().value()).matches())
              toRemove.add(c);
          }
        return result;
      }
    };
  }

  public static TreeObjectifier<Constituent> parsevalObjectifier(final TreeTransformer collinizer) {
    return parsevalObjectifier(collinizer,null);
  }

  /**
   * returns a TreeObjectifier for labeled parseval type evaluation
   */
  public static TreeObjectifier<Constituent> parsevalObjectifier(final TreeTransformer collinizer, Pattern ignoreBrackets) {
    return parsevalObjectifier(collinizer,true, ignoreBrackets);
  }

  /**
   * returns a TreeObjectifier for labeled parseval type evaluation
   */
  public static TreeObjectifier<Constituent> unlabeledParsevalObjectifier(final TreeTransformer collinizer) {
    return parsevalObjectifier(collinizer,false, null);
  }

  /**
   * returns a TreeObjectifier for parseval type evaluation
   */
  public static TreeObjectifier<Constituent> parsevalObjectifier(final TreeTransformer collinizer, final boolean labelBracketings, final Pattern ignoreBrackets) {
    return new TreeObjectifier<Constituent>() {
      public Collection<Constituent> objectify(Tree t) {
        Collection<Constituent> result = new ArrayList<Constituent>();
        result.addAll(AbstractTreebankParserParams.parsevalObjectify(t, collinizer,labelBracketings));
        Tree t1 =collinizer.transformTree(t);
        if(t1 == null)
          return result;
        Collection<Constituent> toRemove = new ArrayList<Constituent>();
        if(ignoreBrackets != null)
          for(Constituent c : result) {
            if(ignoreBrackets.matcher(c.label().value()).matches())
              toRemove.add(c);
          }
        result.removeAll(toRemove);
        return result;
      }
    };
  }

  /**
   * returns an EquivalenceClasser that classes parseval constituents by syntactic category
   */
  public static EquivalenceClasser<Constituent, String> parsevalCategoryClasser() {
    return new EquivalenceClasser<Constituent, String>() {
      public String equivalenceClass(Constituent c) {
        return c.value();
      }
    };
  }

  public static <T> TreeObjectifier<T> dependencyObjectifier(final DependencyTyper<T> typer, final TreeTransformer collinizer, final HeadFinder hf) {
    return new TreeObjectifier<T>() {
      public Collection<T> objectify(Tree t) {
        return AbstractTreebankParserParams.dependencyObjectify(t, hf, collinizer,typer);
      }
    };
  }

  /**
   * returns a TreeObjectifier for directed word-word dependencies
   */
  public static TreeObjectifier<List<String>> untypedDependencyObjectifier(final TreeTransformer collinizer, final HeadFinder hf) {
    return new TreeObjectifier<List<String>>() {
      public Collection<List<String>> objectify(Tree t) {
        return AbstractTreebankParserParams.untypedDependencyObjectify(t, hf, collinizer);
      }
    };
  }

    /**
   * returns a TreeObjectifier for unordered word-word dependencies
   */
  public static TreeObjectifier<List<String>> unorderedUntypedDependencyObjectifier(final TreeTransformer collinizer, final HeadFinder hf) {
    return new TreeObjectifier<List<String>>() {
      public Collection<List<String>> objectify(Tree t) {
        return AbstractTreebankParserParams.unorderedUntypedDependencyObjectify(t, hf, collinizer);
      }
    };
  }

  /**
   * returns a TreeObjectifier for directed word-word dependencies typed by the triple of <mother,head,daughter> syntactic
   * category where the dependency occurs
   */
  public static TreeObjectifier<List<String>> typedDependencyObjectifier(final TreeTransformer collinizer, final HeadFinder hf) {
    return new TreeObjectifier<List<String>>() {
      public Collection<List<String>> objectify(Tree t) {
        return AbstractTreebankParserParams.typedDependencyObjectify(t, hf, collinizer);
      }
    };
  }

      /**
   * returns a TreeObjectifier for unordered word-word dependencies
   */
  public static TreeObjectifier<List<String>> unorderedTypedDependencyObjectifier(final TreeTransformer collinizer, final HeadFinder hf) {
    return new TreeObjectifier<List<String>>() {
      public Collection<List<String>> objectify(Tree t) {
        return AbstractTreebankParserParams.unorderedTypedDependencyObjectify(t, hf, collinizer);
      }
    };
  }

  public boolean add(CandidateParses o) {
    return parseSets.add(o);
  }

  /**
   * returns the best performance on the objectification specified by <code>objectifier</code>.
   * @param objectifier specifies the breakdown of the Tree into objects for scoring.
   */
  public <T> Counter[] bestPerformance(TreeObjectifier<T> objectifier) {
    return performance(objectifier, new BestTallierFactory());
  }


   /**
   * returns the performance statistic specified by <code>tf</code> calculated on the objectification specified by <code>objectifier</code>.
   * @param objectifier specifies the breakdown of the Tree into objects for scoring.
   */
  public <T> Counter[] performance(TreeObjectifier<T> objectifier, TallierFactory tf) {
    return performance(objectifier,EquivalenceClassEval.DEFAULT_CHECKER, tf);
  }

  /**
   * returns the performance statistic specified by <code>tf</code> can calculated on the objectification specified by <code>objectifier</code>,
   * according to the standard of equality specified by <code>eq</code>.
   */
  public <T> Counter[] performance(TreeObjectifier<T>  objectifier, EquivalenceClassEval.EqualityChecker eq, TallierFactory tf) {
    return performance(objectifier, eq, tf,false);
  }

  public <T> Counter[] performance(TreeObjectifier<T>  objectifier, EquivalenceClassEval.EqualityChecker eq, TallierFactory tf,boolean bagEval) {
    return performance(objectifier,EquivalenceClassEval.<T,String>nullEquivalenceClasser(),eq,tf,bagEval);
  }

  /** returns set-based performance statistics specified by <code>tf</code> can calculated on the objectification specified by <code>objectifier</code>,
   * according to the standard of equality specified by <code>eq</code>, with statistics reported in equivalence classes
   * specified by <code>equiv</code>.
   */
  public <T> Counter[] performance(TreeObjectifier<T>  objectifier, EquivalenceClasser equiv, EquivalenceClassEval.EqualityChecker eq, TallierFactory tf) {
    return performance(objectifier,equiv,eq,tf,false);
  }


  /** returns the performance statistic specified by <code>tf</code> can calculated on the objectification specified by <code>objectifier</code>,
   * according to the standard of equality specified by <code>eq</code>, with statistics reported in equivalence classes
   * specified by <code>equiv</code>.  *RAW* precision, recall, and f1 score are determined by counting each unit of evaluation (e.g., a labeled bracket, or a word-word dependency)
   * equally in the global evaluation.  *WEIGHTED* precision, recall, and f1 score are macro-averaged over sentences: each sentence gets a score, and these scores are averaged across sentences
   * with each sentence contributing equally.  Note that weighted F1 is not in general the harmonic mean of weighted precision and weighted recall.
   * @param bagEval if true, calculate bag-based eval rather than set-based eval
   * @return {@link ClassicCounter}[] specifying: rawPrecision, bestNumGuesses, rawRecall, numGolds, rawF1, weightedPrecision, weightedRecall, weightedF1
   */
  public <T> Counter[] performance(TreeObjectifier<T> objectifier, EquivalenceClasser<T, String> equiv, EquivalenceClassEval.EqualityChecker<T> eq, TallierFactory tf, boolean bagEval) {
    Filter<Tree> spliceOutFilter = new RegexFilter(spliceOut);
    Filter<Tree> lengthPruneFilter = new RegexFilter(lengthPrune);
    ClassicCounter bestWeightedPrecision = new ClassicCounter();
    ClassicCounter bestWeightedRecall = new ClassicCounter();
    ClassicCounter bestGuessedCorrect = new ClassicCounter();
    ClassicCounter bestNumGuesses = new ClassicCounter();
    ClassicCounter bestGoldsCorrect = new ClassicCounter();
    ClassicCounter numGolds = new ClassicCounter();
    int numEvaluations = 0;
    ClassicCounter numNonzeroGuesses = new ClassicCounter();
    ClassicCounter numNonzeroGolds = new ClassicCounter();
    int setNum = 0;
    int numGoldItems = 0;
    int numGuessItems = 0;
    for (CandidateParses c : parseSets) {
      setNum++;
      if(verbose){
        System.err.println("true tree number " + setNum + ':');
        c.trueTree().pennPrint(System.err); // testing
      }
      if(c.trueTree().prune(lengthPruneFilter).yield().size() > maxLength)
        continue;
      EquivalenceClassEval eval = new EquivalenceClassEval(equiv, eq);
      eval.setBagEval(bagEval);
      Tallier tallier = tf.tallier(eval);
      Collection golds = objectifier.objectify(spliceOut(c.trueTree(),spliceOutFilter));
      numGoldItems+= golds.size();
      //System.out.println(golds); // testing
      if (golds.size() == 0) {
        continue;
      }
      //c.trueParse().pennPrint(); // testing
      if (c.candidates().size() < 1) {
        throw new UnsupportedOperationException("Sorry, zero candidates for gold " + c.trueTree());
      }
      for (Tree t : c.candidates()) {
        Collection guesses = objectifier.objectify(spliceOut(t,spliceOutFilter));
        numGuessItems += guesses.size();
        //System.err.println("Gold/guess items for set " + setNum + ": " + golds.size() + "\t" + guesses.size());
        //System.out.println(guesses); // testing
        tallier.tally(golds, guesses);
      }
      //if(tallier.resultRecall()  < 0.3 && tallier.resultPrecision() < 0.3) { // testing
      //  System.out.println("terrible parse!");
      //  for(Tree t : c.candidates())
      //    t.pennPrint();
      //}
      Counters.addInPlace(bestWeightedPrecision, tallier.resultPrecision());
      Counters.addInPlace(bestWeightedRecall, tallier.resultRecall());
      Counters.addInPlace(bestNumGuesses, tallier.resultNumGuesses());
      Counters.addInPlace(bestGuessedCorrect, Counters.product(tallier.resultNumGuesses(), tallier.resultPrecision()));
      Counters.addInPlace(numGolds, tallier.resultNumGolds());
      Counters.addInPlace(bestGoldsCorrect, Counters.product(tallier.resultNumGolds(), tallier.resultRecall()));
      numEvaluations++;
      //System.out.println("### " + numEvaluations + " " + bestWeightedPrecision + " " + bestWeightedRecall + " " + bestWeightedF1);
      for (Object o : tallier.resultNumGuesses().keySet()) {
        numNonzeroGuesses.incrementCount(o);
      }
      for (Object o : tallier.resultNumGolds().keySet()) {
        numNonzeroGolds.incrementCount(o);
      }
    }
    Counter rawPrecision = Counters.division(bestGuessedCorrect,bestNumGuesses);
    Counter rawRecall = Counters.division(bestGoldsCorrect,numGolds);
    Counter weightedPrecision = Counters.division(bestWeightedPrecision,numNonzeroGuesses);
    Counter weightedRecall = Counters.division(bestWeightedRecall,numNonzeroGuesses);
    System.err.println("total golds/guesses: " + numGoldItems + '\t' + numGuessItems);
    return new Counter[]{rawPrecision, bestNumGuesses, rawRecall, numGolds, EquivalenceClassEval.f1(rawPrecision, rawRecall), weightedPrecision, weightedRecall, EquivalenceClassEval.f1(weightedPrecision, weightedRecall)};
  }

  public static final String[] statistics = new String[]{"raw prec.", "  raw guesses", "   raw recall", "    raw golds", "       raw f1", "weighted prec.", "weighted recall", "weighted f1"};

  /* calls spliceout and renames the root category to rootCategory if it turns out empty.*/
  private Tree spliceOut(Tree t, Filter f) {
    Tree result = t.spliceOut(f);
    if(result.label() == null)
      result.setLabel(new StringLabel(rootCategory));
    return result;
  }


  /** Reads a set of candidate Trees.  Trees are read by {@link TreeReader}s specified by <code>trf</code>;
   * <code>trueReader</code> reads single trees in at a time; <code>candidateReader</code> reads in lists of
   * candidates in the form (candidate1 candidate2 ... candidate3).  This is really only designed to work
   * right now with {@link PennTreeReader} (bad design).
   *
   * @throws IOException
   */
  public void readCandidateSets(TreeReaderFactory trf, Reader trueReader, Reader candidateReader) throws IOException {
    TopParenSplitter tps = new TopParenSplitter(candidateReader);
    String cand = "";
    String s = "";
    while ((s = tps.yylex()) != null) {
      cand += s;
    }
    String[] cands = cand.split(TopParenSplitter.SPLIT);

    TreeReader trueTreeReader = trf.newTreeReader(trueReader);
    Tree trueTree = null;
    int i = 0;
    while ((trueTree = trueTreeReader.readTree()) != null) {
      TreeReader candidateTreeReader = trf.newTreeReader(new StringReader(cands[i]));
      Set<Tree> candidates = new HashSet<Tree>();
      Tree candidateTree = null;
      while ((candidateTree = candidateTreeReader.readTree()) != null) {
        candidates.add(candidateTree);
      }
      CandidateParses cp = new CandidateParses(candidates, trueTree);
      parseSets.add(cp);
      i++;
    }
  }

  /**
   * Reads in a set of singleton-candidates.
   * @throws IOException
   */
  public void readSingletonCandidates(TreeReaderFactory trf, Reader trueReader, Reader candidateReader) throws IOException {
    TreeReader trueTreeReader = trf.newTreeReader(trueReader);
    TreeReader candTreeReader = trf.newTreeReader(candidateReader);
    Tree trueTree = null;
    while ((trueTree = trueTreeReader.readTree()) != null) {
      Tree candidateTree = candTreeReader.readTree();
      if (DEBUG) System.out.println("Gold tree: " + trueTree);
      if (DEBUG) System.out.println("Candidate tree: " + candidateTree);
      Set<Tree> candidates = new HashSet<Tree>();
      candidates.add(candidateTree);
      CandidateParses cp = new CandidateParses(candidates, trueTree);
      parseSets.add(cp);
    }
  }

  /** Equivalent to display(new PrintWriter(System.out,true)). */
  public void display() {
    display(new PrintWriter(System.out, true));
  }

  /** writes all (gold + candidate set) items to pw.*/
  public void display(PrintWriter pw) {
    for (CandidateParses cp : parseSets) {
      cp.display(pw);
    }
  }

  /** shows the objectification of each (gold + set). */
  public void displayObjectification(PrintWriter pw, TreeObjectifier tobj) {
    for (CandidateParses cp : parseSets) {
      cp.displayObjectification(pw,tobj);
    }
  }

  static final String LANG_OPTION = "-lang";
  private static final String TLPP_OPTION = "-tlppArg";
  private static final String HEAD_FINDER_OPTION = "-hf";

  private static final String MAXLENGTH_OPTION = "-maxLength";

  private static final String ROOT_CATEGORY_OPTION = "-root";
  private static final String BAG_EVAL_OPTION = "-bagEval";
  private static final String LENGTHPRUNE_OPTION= "-lengthPrune";
  private static final String SPLICEOUT_OPTION= "-spliceOut";
  private static final String IGNORE_BRACKETINGS_OPTION = "-ignoreBrackets";


  /** Calculates PARSEVAL, untyped dependency, and typed dependency figures on Penn Treebank-style trees.
   *  Usage:
   * java CandidateParseManager goldFile candidateFile
   * Options:
   * <ul>
   * <li> <code>-single</code> reads in singleton candidates from the candidate file. By default,
   *      the candidate file is assumed to have parses in lists of the form (C1 C2 ... Cn)
   * <li> <code>-lang [classname]</code> uses the {@link TreebankLangParserParams} class specified.
   * <li> <code>-bagEval</code> uses bag-based eval (e.g., like EVALB does for bracketings.)
   * <li> <code>-maxLength N</code> only eval sentences whose gold tree is <= N terms
   * <li><code>-pruneForLength [regex]</code> prune out labels matching [regex] to determine whether a sentence is under "maxlength"
   * <li> <code>-spliceOut [regex]<code> splice out nodes matching [regex]
   * </ul>
   *
   *
   * @throws Exception
   */
  public static void main(String[] args) throws Exception {
    Pattern spliceOut = null;
    Pattern lengthPrune = null;
    Pattern ignoreBracketings = null;
    String rootCategory = "TOP";
    boolean bagEval = false;
    int maxLength = Integer.MAX_VALUE;
    int bufferNum = 11;  // Width of buffer
    NumberFormat nf = new DecimalFormat();
    nf.setMaximumFractionDigits(3);
    nf.setMinimumFractionDigits(3);
    HeadFinder hf = null;

    if (args.length < 2) {
      System.err.println("Usage: java CandidateParseManager [-single|-lang tLPP|-root <root-category>] goldFile candidateFile");
      System.exit(0);
    }
    Map<String,Integer> flagMap = new HashMap<String,Integer>();
    flagMap.put(LANG_OPTION, Integer.valueOf(1));
    flagMap.put(TLPP_OPTION, Integer.valueOf(1));
    flagMap.put(HEAD_FINDER_OPTION, Integer.valueOf(1));
    flagMap.put(ROOT_CATEGORY_OPTION, Integer.valueOf(1));
    flagMap.put(MAXLENGTH_OPTION, Integer.valueOf(1));
    flagMap.put(LENGTHPRUNE_OPTION, Integer.valueOf(1));
    flagMap.put(SPLICEOUT_OPTION, Integer.valueOf(1));
    flagMap.put(IGNORE_BRACKETINGS_OPTION, Integer.valueOf(1));
    Map<String,String[]> argsMap = StringUtils.argsToMap(args, flagMap);
    args = argsMap.get(null);

    TreebankLangParserParams tlpp = new EnglishTreebankParserParams();
    if(argsMap.containsKey(BAG_EVAL_OPTION))
      bagEval = true;
    if(argsMap.containsKey(MAXLENGTH_OPTION))
      maxLength = Integer.parseInt(argsMap.get(MAXLENGTH_OPTION)[0]);
    if(argsMap.containsKey(SPLICEOUT_OPTION))
      spliceOut = Pattern.compile(argsMap.get(SPLICEOUT_OPTION)[0]);
    if(argsMap.containsKey(LENGTHPRUNE_OPTION))
      lengthPrune = Pattern.compile(argsMap.get(LENGTHPRUNE_OPTION)[0]);
    if(argsMap.containsKey(IGNORE_BRACKETINGS_OPTION))
      ignoreBracketings = Pattern.compile(argsMap.get(IGNORE_BRACKETINGS_OPTION)[0]);
    if (argsMap.keySet().contains(LANG_OPTION)) {
      tlpp = (TreebankLangParserParams) Class.forName(argsMap.get(LANG_OPTION)[0]).newInstance();
      System.err.println("Using treebank language parameters "+ tlpp.getClass().getName());
    }
    if (argsMap.containsKey(TLPP_OPTION)) {
      String[] tlppArgs = argsMap.get(TLPP_OPTION);
      for (int i = 0; i < tlppArgs.length; i++)
        tlppArgs[i] = '-' + tlppArgs[i];
      tlpp.setOptionFlag(tlppArgs,0);
    }

    if(argsMap.containsKey(HEAD_FINDER_OPTION)) {
      hf = (HeadFinder) Class.forName(argsMap.get(HEAD_FINDER_OPTION)[0]).newInstance();
      System.err.println("Using head-finder "+ hf.getClass().getName());
    }
    else {
      hf = tlpp.headFinder();
    }

    if(argsMap.containsKey(ROOT_CATEGORY_OPTION))
      rootCategory = argsMap.get(ROOT_CATEGORY_OPTION)[0];


    Reader rTrue = new BufferedReader(new FileReader(args[0]));
    Reader rCand = new BufferedReader(new FileReader(args[1]));
    TreeReaderFactory trf = new PennTreeReaderFactory();
    CandidateParseManager manager = new CandidateParseManager(maxLength,lengthPrune,spliceOut,rootCategory);
    if (argsMap.keySet().contains("-single")) {
      manager.readSingletonCandidates(trf, rTrue, rCand);
    } else {
      manager.readCandidateSets(trf, rTrue, rCand);
    }
    if (DEBUG) manager.display();
    Counter[] stats;
    System.out.println("Summary statistics:");
    System.out.println("Tagging evaluation:");
    stats = manager.performance(tagObjectifier(tlpp.collinizerEvalb(),ignoreBracketings), EquivalenceClassEval.DEFAULT_CHECKER, new BestTallierFactory(),bagEval);
    for (int i = 0; i < statistics.length; i++) {
      System.out.println(statistics[i] + ": " + stats[i]);
    }
    System.out.println("Unlabeled PARSEVAL evaluation:");
    System.out.println("Best by F1:");
    stats = manager.performance(parsevalObjectifier(tlpp.collinizerEvalb(),ignoreBracketings), unlabeledBracketingEqualityChecker, new BestTallierFactory(),bagEval);
    for (int i = 0; i < statistics.length; i++) {
      System.out.println(statistics[i] + ": " + stats[i]);
    }
    System.out.println("PARSEVAL evaluation:");
    System.out.println("Best by F1:");
    stats = manager.performance(parsevalObjectifier(tlpp.collinizerEvalb(),ignoreBracketings), EquivalenceClassEval.DEFAULT_CHECKER, new BestTallierFactory(),bagEval);
    for (int i = 0; i < statistics.length; i++) {
      System.out.println(statistics[i] + ": " + stats[i]);
    }
    System.out.println("Untyped dependency evaluation:");
    System.out.println("Best by F1:");
    stats = manager.performance(untypedDependencyObjectifier(tlpp.collinizerEvalb(), hf), EquivalenceClassEval.DEFAULT_CHECKER, new BestTallierFactory(),bagEval);
    for (int i = 0; i < statistics.length; i++) {
      System.out.println(statistics[i] + ": " + stats[i]);
    }
    System.out.println("Unordered untyped dependency evaluation:");
    System.out.println("Best by F1:");
    stats = manager.performance(unorderedUntypedDependencyObjectifier(tlpp.collinizerEvalb(), hf), EquivalenceClassEval.DEFAULT_CHECKER, new BestTallierFactory(),bagEval);
    for (int i = 0; i < statistics.length; i++) {
      System.out.println(statistics[i] + ": " + stats[i]);
    }
    System.out.println("Typed dependency evaluation:");
    System.out.println("Best by F1:");
    stats = manager.performance(typedDependencyObjectifier(tlpp.collinizerEvalb(), hf), EquivalenceClassEval.DEFAULT_CHECKER, new BestTallierFactory(),bagEval);
    for (int i = 0; i < statistics.length; i++) {
      System.out.println(statistics[i] + ": " + stats[i]);
    }
    System.out.println("Unordered Typed dependency evaluation:");
    System.out.println("Best by F1:");
    stats = manager.performance(unorderedTypedDependencyObjectifier(tlpp.collinizerEvalb(), hf), EquivalenceClassEval.DEFAULT_CHECKER, new BestTallierFactory(),bagEval);
    for (int i = 0; i < statistics.length; i++) {
      System.out.println(statistics[i] + ": " + stats[i]);
    }
    System.out.println("\nDetailed statistics:");
    System.out.println("Tagging evaluation by POS tag:");
    stats = manager.performance(tagObjectifier(tlpp.collinizerEvalb(),ignoreBracketings), parsevalCategoryClasser(),
        (EquivalenceClassEval.EqualityChecker<Constituent>) EquivalenceClassEval.DEFAULT_CHECKER,
        new BestTallierFactory(),bagEval);
    prettyPrintStats(stats,bufferNum,nf);
    System.out.println("PARSEVAL evaluation by category:");
    System.out.println("Best by F1:");
    stats = manager.performance(parsevalObjectifier(tlpp.collinizerEvalb(),ignoreBracketings), parsevalCategoryClasser(),
        (EquivalenceClassEval.EqualityChecker<Constituent>) EquivalenceClassEval.DEFAULT_CHECKER,
        new BestTallierFactory(),bagEval);
    prettyPrintStats(stats, bufferNum, nf);
    System.out.println("Typed dependency evaluation by dependency type:");
    System.out.println("Best by F1:");
    stats = manager.performance(typedDependencyObjectifier(tlpp.collinizerEvalb(), hf),
        AbstractTreebankParserParams.typedDependencyClasser(),
        (EquivalenceClassEval.EqualityChecker<List<String>>) EquivalenceClassEval.DEFAULT_CHECKER,
        new BestTallierFactory(),bagEval);
    prettyPrintStats(stats,bufferNum,nf);
  /*
    System.out.println("\nWorst by F1:");
    stats = manager.performance(typedDependencyObjectifier(tlpp.collinizerEvalb(), tlpp.headFinder()), EquivalenceClassEval.DEFAULT_CHECKER, new WorstTallierFactory());
    for (int i = 0; i < statistics.length; i++) {
      System.out.println(statistics[i] + ": " + stats[i]);
    }
    System.out.println("\nAverage:");
    stats = manager.performance(typedDependencyObjectifier(tlpp.collinizerEvalb(), tlpp.headFinder()), EquivalenceClassEval.DEFAULT_CHECKER, new AverageTallierFactory());
    for (int i = 0; i < statistics.length; i++) {
      System.out.println(statistics[i] + ": " + stats[i]);
    }
    */
  }

  private static void prettyPrintStats(Counter[] stats, int bufferNum, NumberFormat nf) {
    for (int i = 0; i < statistics.length; i++) {
      System.out.print('\t' + statistics[i]);
    }
    System.out.println();
    List keys = new ArrayList(stats[4].keySet());
    Collections.sort(keys,Collections.reverseOrder(Counters.toComparatorDescending(stats[3])));
    for(Object key : keys) {
      System.out.println(key);
      for (Counter c : stats) {
        System.out.print("\t");
        for (int z = 0; z < bufferNum - nf.format(c.getCount(key)).length(); z++)
          System.out.print(" ");
        System.out.print(nf.format(c.getCount(key)));

      }
      System.out.println();
    }
  }


  static interface Tallier {
    public void tally(Collection golds, Collection guesses);

    public ClassicCounter resultNumGuesses();

    public ClassicCounter resultNumGolds();

    public ClassicCounter resultPrecision();

    public ClassicCounter resultRecall();

  }

  static abstract class AbstractTallier implements Tallier {
    EquivalenceClassEval eval;

    ClassicCounter resultPrecision;
    ClassicCounter resultRecall;
    ClassicCounter resultNumGuesses;
    ClassicCounter resultNumGolds;

    protected AbstractTallier(EquivalenceClassEval eval) {
      this.eval = eval;
      resultNumGolds = new ClassicCounter();
    }

    /** This should also be overriden and the super called. */
    public void tally(Collection golds, Collection guesses) {
      eval.eval(guesses,golds);
      resultNumGolds = eval.lastNumGolds();
    }

    public ClassicCounter resultNumGuesses() {
      return resultNumGuesses;
    }

    public ClassicCounter resultNumGolds() {
      return resultNumGolds;
    }

    public ClassicCounter resultPrecision() {
      return resultPrecision;
    }

    public ClassicCounter resultRecall() {
      return resultRecall;
    }


  }

  /** can be set to consider "best" as:
   *
   * 1) best by class
   *
   * 2) best overall
   */
  static class BestTallier extends AbstractTallier {
    private boolean bestByClass = false;
    ClassicCounter resultF1;

    public BestTallier(EquivalenceClassEval eval) {
      super(eval);
      resultPrecision = new ClassicCounter();
      resultRecall = new ClassicCounter();
      resultF1 = new ClassicCounter();
      resultNumGuesses = new ClassicCounter();
    }

    @Override
    public void tally(Collection golds, Collection guesses) {
      super.tally(golds,guesses);
      if(bestByClass) {
        Set keys = Sets.union(eval.lastPrecision().keySet(),eval.lastRecall().keySet());
        for(Object key : keys) {
          double thisF1 = EquivalenceClassEval.f1(eval.lastPrecision(key),eval.lastRecall(key));
          if(thisF1 >= resultF1.getCount(key) || resultF1.getCount(null) == 0.0) {
            resultPrecision.setCount(key,eval.lastPrecision(key));
            resultRecall.setCount(key,eval.lastRecall(key));
            resultF1.setCount(key,thisF1);
            resultNumGuesses.setCount(key,eval.lastNumGuessed(key));
          }
        }
      }
      else {
        double thisF1 = EquivalenceClassEval.f1(
                eval.lastNumGuessedCorrect().totalCount()/eval.lastNumGuessed().totalCount(),
                eval.lastNumGoldsCorrect().totalCount()/eval.lastNumGolds().totalCount());
        //System.err.println("Recall/Precision: " + eval.lastRecall().getCount(null) + "\t" + eval.lastPrecision().getCount(null));
        if (thisF1 >= resultF1.getCount(null) || resultF1.getCount(null)==0.0) {
          resultF1.setCount(null,thisF1); // use resultF1 only to store an aggregate score
          resultPrecision = eval.lastPrecision();
          resultRecall = eval.lastRecall();
          resultNumGuesses = eval.lastNumGuessed();
        }
      }
    }

  }

  /*
  static class WorstTallier extends AbstractTallier {
    double resultF1;

    public WorstTallier(EquivalenceClassEval eval) {
      super(eval);
      resultPrecision = Double.MAX_VALUE;
      resultRecall = Double.MAX_VALUE;
      resultF1 = Double.MAX_VALUE;
      resultNumGuesses = Double.MAX_VALUE;
    }

    public void tally(Collection golds, Collection guesses) {
      //System.out.println("golds:" + golds);
      //System.out.println("guesses:" + guesses);
      eval.eval(guesses, golds);
      if (eval.lastF1(null) < resultF1) {
        resultF1 = eval.lastF1(null);
        resultPrecision = eval.lastPrecision(null);
        resultRecall = eval.lastRecall(null);
        resultNumGuesses = eval.lastNumGuessed(null);
      }
    }

  }

  static class AverageTallier extends AbstractTallier {
    public AverageTallier(EquivalenceClassEval eval) {
      super(eval);
      resultPrecision = 0.0;
      resultRecall = 0.0;
      resultNumGuesses = 0.0;
    }

    double n = 0.0;
    double sumPrecision = 0.0;
    double sumRecall = 0.0;
    double sumNumGuesses = 0.0;


    public void tally(Collection golds, Collection guesses) {
      eval.eval(guesses, golds);
      n += 1.0;
      sumPrecision += eval.lastPrecision(null);
      sumRecall += eval.lastRecall(null);
      sumNumGuesses += eval.lastNumGuessed(null);
      resultPrecision = sumPrecision / n;
      resultRecall = sumRecall / n;
      resultNumGuesses = sumNumGuesses / n;
    }

  }
  */

  public interface TallierFactory {
    public Tallier tallier(EquivalenceClassEval eval);
  }

  public static class BestTallierFactory implements TallierFactory {
    public Tallier tallier(EquivalenceClassEval eval) {
      return new BestTallier(eval);
    }
  }

  /*
  static class WorstTallierFactory implements TallierFactory {
    public Tallier tallier(EquivalenceClassEval eval) {
      return new WorstTallier(eval);
    }
  }

  static class AverageTallierFactory implements TallierFactory {
    public Tallier tallier(EquivalenceClassEval eval) {
      return new AverageTallier(eval);
    }
  }
  */

  private static EquivalenceClassEval.EqualityChecker unlabeledBracketingEqualityChecker = new EquivalenceClassEval.EqualityChecker() {
    public boolean areEqual(Object o1, Object o2) {
      Constituent c1 = (Constituent) o1;
      Constituent c2 = (Constituent) o2;
      return c1.start()== c2.start() && c1.end()==c2.end();
    }
  };

}


