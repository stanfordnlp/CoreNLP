package edu.stanford.nlp.parser.lexparser; 
import edu.stanford.nlp.util.logging.Redwood;

import edu.stanford.nlp.trees.TreePrint;
import edu.stanford.nlp.trees.TreebankLanguagePack;

import java.io.Serializable;
import java.util.Properties;

/**
 * Options to the parser which affect performance only at testing (parsing)
 * time.
 * <br>
 * The Options class that stores the TestOptions stores the
 * TestOptions as a transient object.  This means that whatever
 * options get set at creation time are forgotten when the parser is
 * serialized.  If you want an option to be remembered when the parser
 * is reloaded, put it in either TrainOptions or in Options itself.
 *
 * @author Dan Klein
 */
public class TestOptions implements Serializable  {

  /** A logger for this class */
  private static Redwood.RedwoodChannels log = Redwood.channels(TestOptions.class);

  static final String DEFAULT_PRE_TAGGER =
    "/u/nlp/data/pos-tagger/distrib/wsj-0-18-bidirectional-nodistsim.tagger";

  public TestOptions() {
    evals = new Properties();
    evals.setProperty("pcfgLB", "true");
    evals.setProperty("depDA", "true");
    evals.setProperty("factLB", "true");
    evals.setProperty("factTA", "true");
    evals.setProperty("summary", "true");
  }

  /**
   * If false, then failure of the PCFG parser to parse a sentence
   * will trigger allowing all tags for words in parse recovery mode,
   * with a log probability of -1000.
   * If true, these extra taggings are not added.
   * It is false by default. Use option -noRecoveryTagging to set
   * to true.
   */
  public boolean noRecoveryTagging = false;

  /** If true, then  failure of the PCFG factor to parse a sentence
   *  will trigger parse recovery mode.
   */
  public boolean doRecovery = true;

  /**
   * If true, the n^4 "speed-up" is not used with the Factored Parser.
   */
  public boolean useN5 = false;

  /** If true, use approximate factored algorithm, which just rescores
   *  PCFG k best, rather than exact factored algorithm.  This algorithm
   *  requires the dependency grammar to exist for rescoring, but not for
   *  the dependency grammar to be run.  Hence the correct usage for
   *  guarding code only required for exact A* factored parsing is now
   *  if (op.doPCFG &amp;&amp; op.doDep &amp;&amp; ! Test.useFastFactored).
   */
  public boolean useFastFactored = false;


  /** If true, use faster iterative deepening CKY algorithm. */
  public boolean iterativeCKY = false;

  /**
   * The maximum sentence length (including punctuation, etc.) to parse.
   */
  public int maxLength = -0xDEADBEEF;
  // initial value is -0xDEADBEEF (actually positive because of 2s complement)

  /**
   * The maximum number of edges and hooks combined that the factored parser
   * will build before giving up.  This number should probably be relative to
   * the sentence length parsed. In general, though, if the parser cannot parse
   * a sentence after this much work then there is no good parse consistent
   * between the PCFG and Dependency parsers.  (Normally, depending on other
   * flags), the parser will then just return the best PCFG parse.)
   */
  public int MAX_ITEMS = 200000;

  /**
   *  The amount of smoothing put in (as an m-estimate) for unknown words.
   *  If negative, set by the code in the lexicon class.
   */
  public double unseenSmooth = -1.0;

  /**
   * Parse trees in test treebank in order of increasing length.
   */
  public boolean increasingLength = false;

  /**
   * Tag the sentences first, then parse given those (coarse) tags.
   */
  public boolean preTag = false;

  /**
   * Parse using only tags given from correct answer or the POS tagger
   */
  public boolean forceTags = preTag;

  public boolean forceTagBeginnings = false;

  /**
   * POS tagger model used when preTag is enabled.
   */
  public String taggerSerializedFile = DEFAULT_PRE_TAGGER;

  /**
   * Only valid with force tags - strips away functionals when forcing
   * the tags, meaning tags have to start
   * appropriately but the parser will assign the functional part.
   */
  public boolean noFunctionalForcing = preTag;

  /**
   * Write EvalB-readable output files.
   */
  public boolean evalb = false;

  /**
   * Print a lot of extra output as you parse.
   */
  public boolean verbose = false; // Don't change this; set with -v

  public final boolean exhaustiveTest = false;

  /** If this variable is true, and the sum of the inside and outside score
   *  for a constituent is worse than the best known score for a sentence by
   *  more than <code>pcfgThresholdValue</code>, then -Inf is returned as the
   *  outside Score by <code>oScore()</code> (while otherwise the true
   *  outside score is returned).
   */
  public final boolean pcfgThreshold = false;
  public final double pcfgThresholdValue = -2.0;

  /**
   * Print out all best PCFG parses.
   */
  public boolean printAllBestParses = false;

  /**
   * Weighting on dependency log probs.  The dependency grammar negative log
   * probability scores are simply multiplied by this number.
   */
  public double depWeight = 1.0;
  public boolean prunePunc = false;

  /** If a token list does not have sentence final punctuation near the
   *  end, then automatically add the default one.
   *  This might help parsing if the treebank is all punctuated.
   *  Not done if reading a treebank.
   */
  public boolean addMissingFinalPunctuation;


  /**
   * Determines format of output trees: choose among penn, oneline
   */
  public String outputFormat = "penn";
  public String outputFormatOptions = "";


  /** If true, write files parsed to a new file with the same name except
   *  for an added ".stp" extension.
   */
  public boolean writeOutputFiles;

  /** If the writeOutputFiles option is true, then output files appear in
   *  this directory.  An unset value (<code>null</code>) means to use
   *  the directory of the source files.  Use <code>""</code> or <code>.</code>
   *  for the current directory.
   */
  public String outputFilesDirectory;

  /** If the writeOutputFiles option is true, then output files appear with
   *  this extension. Use <code>""</code> for no extension.
   */
  public String outputFilesExtension = "stp";

  /**
   * If the writeOutputFiles option is true, then output files appear with
   * this prefix.
   */
  public String outputFilesPrefix = "parses";

  /**
   * If this option is not null, output the k-best equivocation. Must be specified
   * with printPCFGkBest.
   */
  public String outputkBestEquivocation;

  /**
   * The largest span to consider for word-hood.  Used for parsing unsegmented
   * Chinese text and parsing lattices.  Keep it at 1 unless you know what
   * you're doing.
   */
  public int maxSpanForTags = 1;

  /**
   * Turns on normalizing scores for sentence length.  Makes no difference
   * (except decreased efficiency) unless maxSpanForTags is greater than one.
   * Works only for PCFG (so far).
   */
  public boolean lengthNormalization = false;

  /**
   * Used when you want to generate sample parses instead of finding the best
   * parse.  (NOT YET USED.)
   */
  public boolean sample = false;

  /** Printing k-best parses from PCFG, when k &gt; 0. */
  public int printPCFGkBest = 0;

  /** If using a kBest eval, use this many trees. */
  public int evalPCFGkBest = 100;

  /** Printing k-best parses from PCFG, when k &gt; 0. */
  public int printFactoredKGood = 0;

  /** What evaluations to report and how to report them
   *  (using LexicalizedParser). Known evaluations
   *  are: pcfgLB, pcfgCB, pcfgDA, pcfgTA, pcfgLL, pcfgRUO, pcfgCUO, pcfgCatE,
   *  pcfgChildSpecific,
   *  depDA, depTA, depLL,
   *  factLB, factCB, factDA, factTA, factLL, factChildSpecific.
   *  The default is pcfgLB,depDA,factLB,factTA.  You need to negate those
   *  ones out (e.g., <code>-evals "depDA=false"</code>) if you don't want
   *  them.
   *  LB = ParseEval labeled bracketing,   <br>
   *  CB = crossing brackets and zero crossing bracket rate,   <br>
   *  DA = dependency accuracy, TA = tagging accuracy,   <br>
   *  LL = log likelihood score,   <br>
   *  RUO/CUO = rules/categories under and over proposed,  <br>
   *  CatE = evaluation by phrasal category.   <br>
   *  ChildSpecific: supply an argument with =.  F1 will be returned
   *    for only the nodes which have at least one child that matches
   *    this regular expression. <br>
   *  Known styles are: runningAverages, summary, tsv. <br>
   *  The default style is summary.
   *  You need to negate it out if you don't want it.
   *  Invalid names in the argument to this option are not reported!
   */
  public Properties evals;

  /** This variable says to find k good fast factored parses, how many times
   *  k of the best PCFG parses should be examined.
   */
  public int fastFactoredCandidateMultiplier = 3;

  /** This variable says to find k good factored parses, how many added on
   *  best PCFG parses should be examined.
   */
  public int fastFactoredCandidateAddend = 50;


  /** If this is true, the Lexicon is used to score P(w|t) in the backoff inside the
   *  dependency grammar.  (Otherwise, a MLE is used is w is seen, and a constant if
   *  w is unseen.
   */
  public boolean useLexiconToScoreDependencyPwGt = false;

  /** If this is true, perform non-projective dependency parsing.
   */
  public boolean useNonProjectiveDependencyParser = false;

  /**
   * Number of threads to use at test time.  For example,
   * -testTreebank can use this to go X times faster, with the
   * negative consequence that output is not quite as nicely ordered.
   */
  public int testingThreads = 1;

  /**
   * When evaluating, don't print out tons of text.  Only print out the final scores
   */
  public boolean quietEvaluation = false;

  /**
   * Determines method for print trees on output.
   *
   * @param tlpParams The treebank parser params
   * @return A suitable tree printing object
   */
  public TreePrint treePrint(TreebankLangParserParams tlpParams) {
    TreebankLanguagePack tlp = tlpParams.treebankLanguagePack();
    return new TreePrint(outputFormat, outputFormatOptions, tlp, tlpParams.headFinder(), tlpParams.typedDependencyHeadFinder());
  }


  public void display() {
    String str = toString();
    log.info(str);
  }

  @Override
  public String toString() {
    return ("Test parameters" + 
            " maxLength=" + maxLength + 
            " preTag=" + preTag + 
            " outputFormat=" + outputFormat + 
            " outputFormatOptions=" + outputFormatOptions + 
            " printAllBestParses=" + printAllBestParses + 
            " testingThreads=" + testingThreads +
            " quietEvaluation=" + quietEvaluation);
  }

  private static final long serialVersionUID = 7256526346598L;

}
