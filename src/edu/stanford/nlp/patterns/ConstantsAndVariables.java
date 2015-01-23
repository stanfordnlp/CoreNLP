package edu.stanford.nlp.patterns;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.tokensregex.Env;
import edu.stanford.nlp.ling.tokensregex.NodePattern;
import edu.stanford.nlp.ling.tokensregex.TokenSequencePattern;
import edu.stanford.nlp.patterns.GetPatternsFromDataMultiClass.PatternScoring;
import edu.stanford.nlp.patterns.GetPatternsFromDataMultiClass.WordScoring;
import edu.stanford.nlp.patterns.dep.DepPatternFactory;
import edu.stanford.nlp.patterns.surface.SurfacePatternFactory;
import edu.stanford.nlp.process.WordShapeClassifier;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.util.*;
import edu.stanford.nlp.util.Execution.Option;
import edu.stanford.nlp.util.TypesafeMap.Key;
import edu.stanford.nlp.util.logging.Redwood;

import javax.json.Json;
import javax.json.JsonArrayBuilder;
import javax.json.JsonObjectBuilder;

public class ConstantsAndVariables<E> implements Serializable{

  private static final long serialVersionUID = 1L;

  /**
   * Maximum number of iterations to run
   */
  @Option(name = "numIterationsForPatterns")
  public Integer numIterationsForPatterns = 10;

  /**
   * Maximum number of patterns learned in each iteration
   */
  @Option(name = "numPatterns")
  public int numPatterns = 10;

  /**
   * The output directory where the justifications of learning patterns and
   * phrases would be saved. These are needed for visualization
   */
  @Option(name = "outDir")
  public String outDir = null;

  /**
   * Cached file of all patterns for all tokens
   */
  @Option(name = "allPatternsDir")
  public String allPatternsDir = null;

  /**
   * If all patterns should be computed. Otherwise patterns are read from
   * allPatternsFile
   */
  @Option(name = "computeAllPatterns")
  public boolean computeAllPatterns = true;

  // @Option(name = "removeRedundantPatterns")
  // public boolean removeRedundantPatterns = true;

  /**
   * Pattern Scoring mechanism. See {@link PatternScoring} for options.
   */
  @Option(name = "patternScoring")
  public PatternScoring patternScoring = PatternScoring.PosNegUnlabOdds;

  /**
   * Threshold for learning a pattern
   */
  @Option(name = "thresholdSelectPattern")
  public double thresholdSelectPattern = 1.0;

//  /**
//   * Do not learn patterns that do not extract any unlabeled tokens (kind of
//   * useless)
//   */
//  @Option(name = "discardPatternsWithNoUnlabSupport")
//  public boolean discardPatternsWithNoUnlabSupport = true;

  /**
   * Currently, does not work correctly. TODO: make this work. Ideally this
   * would label words only when they occur in the context of any learned
   * pattern. This comment seems old. Test it!
   */
  @Option(name = "restrictToMatched")
  public boolean restrictToMatched = false;

  /**
   * Label words that are learned so that in further iterations we have more
   * information
   */
  @Option(name = "usePatternResultAsLabel")
  public boolean usePatternResultAsLabel = true;

  /**
   * Debug flag for learning patterns. 0 means no output, 1 means necessary output, 2 means necessary output+some justification, 3 means extreme debug output
   */
  @Option(name = "debug")
  public int debug = 1;

  /**
   * Do not learn patterns in which the neighboring words have the same label.
   * Deprecated!
   */
  //@Option(name = "ignorePatWithLabeledNeigh")
  //public boolean ignorePatWithLabeledNeigh = false;

  /**
   * Save this run as ...
   */
  @Option(name = "identifier")
  public String identifier = "getpatterns";

  /**
   * Use the actual dictionary matching phrase(s) instead of the token word or
   * lemma in calculating the stats
   */
  @Option(name = "useMatchingPhrase")
  public boolean useMatchingPhrase = true;

  /**
   * Reduce pattern threshold (=0.8*current_value) to extract as many patterns
   * as possible (still restricted by <code>numPatterns</code>)
   */
  @Option(name = "tuneThresholdKeepRunning")
  public boolean tuneThresholdKeepRunning = false;

  /**
   * Maximum number of words to learn
   */
  @Option(name = "maxExtractNumWords")
  public int maxExtractNumWords = Integer.MAX_VALUE;

  /**
   * use the seed dictionaries and the new words learned for the other labels in
   * the previous iterations as negative
   */
  @Option(name = "useOtherLabelsWordsasNegative")
  public boolean useOtherLabelsWordsasNegative = true;

  /**
   * If not null, write the output like
   * "w1 w2 <label1> w3 <label2>w4</label2> </label1> w5 ... " if w3 w4 have
   * label1 and w4 has label 2
   */
  @Option(name = "markedOutputTextFile")
  String markedOutputTextFile = null;

  /**
   * If you want output of form "word\tlabels-separated-by-comma" in newlines
   */
  @Option(name="columnOutputFile")
  String columnOutputFile = null;


  /**
   * Lowercase the context words/lemmas
   */
  @Option(name = "matchLowerCaseContext")
  public boolean matchLowerCaseContext = true;


  /**
   * Initials of all POS tags to use if
   * <code>usePOS4Pattern</code> is true, separated by comma.
   */
  @Option(name = "targetAllowedTagsInitialsStr")
  public String targetAllowedTagsInitialsStr = null;

  public Map<String, Set<String>> allowedTagsInitials = null;

  /**
   * Allowed NERs for labels. Format is label1,NER1,NER11;label2,NER2,NER21,NER22;label3,...
   * <code>useTargetNERRestriction</code> flag should be true
   */
  @Option(name = "targetAllowedNERs")
  public String targetAllowedNERs = null;


  public Map<String, Set<String>> allowedNERsforLabels = null;

  /**
   * Number of words to learn in each iteration
   */
  @Option(name = "numWordsToAdd")
  public int numWordsToAdd = 10;


  @Option(name = "thresholdNumPatternsApplied")
  public double thresholdNumPatternsApplied = 2;

  @Option(name = "wordScoring")
  public WordScoring wordScoring = WordScoring.WEIGHTEDNORM;

  @Option(name = "thresholdWordExtract")
  public double thresholdWordExtract = 0.2;

  public boolean justify = false;

  /**
   * Sigma for L2 regularization in Logisitic regression, if a classifier is
   * used to score phrases
   */
  @Option(name = "LRSigma")
  public double LRSigma = 1.0;

  /**
   * English words that are not labeled when labeling using seed dictionaries
   */
  @Option(name = "englishWordsFiles")
  public String englishWordsFiles = null;

  private Set<String> englishWords = null;

  /**
   * Words to be ignored when learning phrases if
   * <code>removePhrasesWithStopWords</code> or
   * <code>removeStopWordsFromSelectedPhrases</code> is true. Also, these words
   * are considered negative when scoring a pattern (similar to
   * othersemanticclasses).
   */
  @Option(name = "commonWordsPatternFiles")
  public String commonWordsPatternFiles = null;

  private Set<String> commonEngWords = null;

  /**
   * List of dictionary phrases that are negative for all labels to be learned.
   * Format is file_1,file_2,... where file_i has each phrase in a different
   * line
   *
   */
  @Option(name = "otherSemanticClassesFiles")
  public String otherSemanticClassesFiles = null;

  // set of words that are considered negative for all classes
  private Set<String> otherSemanticClassesWords = null;

  /**
   * Seed dictionary, set in the class that uses this class
   */
  private Map<String, Set<String>> seedLabelDictionary = new HashMap<String, Set<String>>();

  /**
   * Just the set of labels
   */
  private Set<String> labels = new HashSet<String>();


  private Map<String, Class<? extends TypesafeMap.Key<String>>> answerClass = null;


  /**
   * Can be used only when using the API - using the appropriate constructor.
   * Tokens with specified classes set (has to be boolean return value, even
   * though this variable says object) will be ignored.
   */
  @SuppressWarnings("rawtypes")
  private Map<String, Map<Class, Object>> ignoreWordswithClassesDuringSelection = null;

  /**
   * These classes will be generalized. It can only be used via the API using
   * the appropriate constructor. All label classes are by default generalized.
   */
  @SuppressWarnings("rawtypes")
  private static Map<String, Class> generalizeClasses = new HashMap<String, Class>();

  /**
   * Minimum length of words that can be matched fuzzily
   */
  @Option(name = "minLen4FuzzyForPattern")
  public int minLen4FuzzyForPattern = 6;

  /**
   * Do not learn phrases that match this regex.
   */
  @Option(name = "wordIgnoreRegex")
  public String wordIgnoreRegex = "[^a-zA-Z]*";

  /**
   * Number of threads
   */
  @Option(name = "numThreads")
  public int numThreads = 1;

  /**
   * Words that are not learned. Patterns are not created around these words.
   * And, if useStopWordsBeforeTerm in {@link edu.stanford.nlp.patterns.surface.CreatePatterns} is true.
   */
  @Option(name = "stopWordsPatternFiles", gloss = "stop words")
  public String stopWordsPatternFiles = null;

  private static Set<String> stopWords = null;



  /**
   * Environment for {@link TokenSequencePattern}
   */
  public Map<String, Env> env = new HashMap<String, Env>();



  /**
   *
   */
  @Option(name = "removeStopWordsFromSelectedPhrases")
  public boolean removeStopWordsFromSelectedPhrases = false;

  /**
   *
   */
  @Option(name = "removePhrasesWithStopWords")
  public boolean removePhrasesWithStopWords = false;

  private boolean alreadySetUp = false;

  /**
   * Cluster file, in which each line is word/phrase<tab>clusterid
   */
  @Option(name = "wordClassClusterFile")
  String wordClassClusterFile = null;

  private Map<String, Integer> wordClassClusters = null;

  /**
   * General cluster file, if you wanna use it somehow, in which each line is
   * word/phrase<tab>clusterid
   */
  @Option(name = "generalWordClassClusterFile")
  String generalWordClassClusterFile = null;

  private Map<String, Integer> generalWordClassClusters = null;

  @Option(name = "includeExternalFeatures")
  public boolean includeExternalFeatures = false;

  @Option(name = "externalFeatureWeightsFile")
  public String externalFeatureWeightsFile = null;

  @Option(name = "doNotApplyPatterns")
  public boolean doNotApplyPatterns = false;


  /**
   * If score for a pattern is square rooted
   */
  @Option(name = "sqrtPatScore")
  public boolean sqrtPatScore = false;

  /**
   * Remove patterns that have number of unlabeled words is less than this.
   */
  @Option(name = "minUnlabPhraseSupportForPat")
  public int minUnlabPhraseSupportForPat = 0;

  /**
   * Remove patterns that have number of positive words less than this.
   */
  @Option(name = "minPosPhraseSupportForPat")
  public int minPosPhraseSupportForPat = 1;

  /**
   * For example, if positive seed dict contains "cancer" and "breast cancer" then "breast" is included as negative
   */
  @Option(name="addIndvWordsFromPhrasesExceptLastAsNeg")
  public boolean addIndvWordsFromPhrasesExceptLastAsNeg = false;

  /**
   * Cached files
   */
  private ConcurrentHashMap<String, Double> editDistanceFromEnglishWords = new ConcurrentHashMap<String, Double>();
  /**
   * Cached files
   */
  private ConcurrentHashMap<String, String> editDistanceFromEnglishWordsMatches = new ConcurrentHashMap<String, String>();
  /**
   * Cached files
   */
  private ConcurrentHashMap<String, Double> editDistanceFromOtherSemanticClasses = new ConcurrentHashMap<String, Double>();
  /**
   * Cached files
   */
  private ConcurrentHashMap<String, String> editDistanceFromOtherSemanticClassesMatches = new ConcurrentHashMap<String, String>();
  /**
   * Cached files
   */
  private ConcurrentHashMap<String, Double> editDistanceFromThisClass = new ConcurrentHashMap<String, Double>();
  /**
   * Cached files
   */
  private ConcurrentHashMap<String, String> editDistanceFromThisClassMatches = new ConcurrentHashMap<String, String>();

  private Map<String, Counter<String>> wordShapesForLabels = new HashMap<String, Counter<String>>();



  String channelNameLogger = "settingUp";

  public Map<String, Counter<Integer>> distSimWeights = new HashMap<String, Counter<Integer>>();
  public Map<String, Counter<String>> dictOddsWeights = new HashMap<String, Counter<String>>();

  @Option(name="invertedIndexClass", gloss="another option is Lucene backed, which is not included in the CoreNLP release. Contact us to get a copy (distributed under Apache License).")
  public Class<? extends SentenceIndex> invertedIndexClass = InvertedIndexByTokens.class;

  /**
   * Where the inverted index (either in memory or lucene) is stored
   */
  @Option(name="invertedIndexDirectory")
  public String invertedIndexDirectory;

  @Option(name="clubNeighboringLabeledWords")
  public boolean clubNeighboringLabeledWords = false;

  @Option(name="patternType", required=true)
  public PatternFactory.PatternType patternType = null;

  public Set<String> getLabels() {
    return labels;
  }
  //PatternFactory.PatternType.SURFACE;


//  public PatternIndex getPatternIndex() {
//    return patternIndex;
//  }
//
//  public void setPatternIndex(PatternIndex patternIndex) {
//    this.patternIndex = patternIndex;
//  }


  public enum ScorePhraseMeasures {
    DISTSIM, GOOGLENGRAM, PATWTBYFREQ, EDITDISTSAME, EDITDISTOTHER, DOMAINNGRAM, SEMANTICODDS, WORDSHAPE
  }


  /**
   * Keeps only one label for each token, whichever has the longest
   */
  @Option(name="removeOverLappingLabelsFromSeed")
  public boolean removeOverLappingLabelsFromSeed = false;

  /**
   * Only works if you have single label. And the word classes are given.
   */
  @Option(name = "usePhraseEvalWordClass")
  public boolean usePhraseEvalWordClass = false;

  /**
   * use google tf-idf for learning phrases
   */
  @Option(name = "usePhraseEvalGoogleNgram")
  public boolean usePhraseEvalGoogleNgram = false;

  /**
   * use domain tf-idf for learning phrases
   */
  @Option(name = "usePhraseEvalDomainNgram")
  public boolean usePhraseEvalDomainNgram = false;

  /**
   * use \sum_allpat pattern_wt_that_extracted_phrase/phrase_freq for learning
   * phrases
   */
  @Option(name = "usePhraseEvalPatWtByFreq")
  public boolean usePhraseEvalPatWtByFreq = true;

  /**
   * odds of the phrase freq in the label dictionary vs other dictionaries
   */
  @Option(name = "usePhraseEvalSemanticOdds")
  public boolean usePhraseEvalSemanticOdds = false;

  /**
   * Edit distance between this phrase and the other phrases in the label
   * dictionary
   */
  @Option(name = "usePhraseEvalEditDistSame")
  public boolean usePhraseEvalEditDistSame = false;

  /**
   * Edit distance between this phrase and other phrases in other dictionaries
   */
  @Option(name = "usePhraseEvalEditDistOther")
  public boolean usePhraseEvalEditDistOther = false;

  @Option(name = "usePhraseEvalWordShape")
  public boolean usePhraseEvalWordShape = false;

  /**
   * Used only if {@link #patternScoring} is <code>PhEvalInPat</code> or
   * <code>PhEvalInPat</code>. See usePhrase* for meanings.
   */
  @Option(name = "usePatternEvalWordClass")
  public boolean usePatternEvalWordClass = false;

  /**
   * Used only if {@link #patternScoring} is <code>PhEvalInPat</code> or
   * <code>PhEvalInPat</code>. See usePhrase* for meanings.
   */
  @Option(name = "usePatternEvalWordShape")
  public boolean usePatternEvalWordShape = false;

  /**
   * Used only if {@link #patternScoring} is <code>PhEvalInPat</code> or
   * <code>PhEvalInPat</code>. See usePhrase* for meanings.
   */
  @Option(name = "usePatternEvalGoogleNgram")
  public boolean usePatternEvalGoogleNgram = false;

  /**
   * Used only if {@link #patternScoring} is <code>PhEvalInPat</code> or
   * <code>PhEvalInPat</code>. See usePhrase* for meanings.
   */
  @Option(name = "usePatternEvalDomainNgram")
  public boolean usePatternEvalDomainNgram = false;

  /**
   * Used only if {@link #patternScoring} is <code>PhEvalInPat</code> or
   * <code>PhEvalInPat</code>. See usePhrase* for meanings.
   */
  @Option(name = "usePatternEvalSemanticOdds")
  public boolean usePatternEvalSemanticOdds = false;

  /**
   * Used only if {@link #patternScoring} is <code>PhEvalInPat</code> or
   * <code>PhEvalInPat</code>. See usePhrase* for meanings.
   */
  @Option(name = "usePatternEvalEditDistSame")
  public boolean usePatternEvalEditDistSame = false;

  /**
   * Used only if {@link #patternScoring} is <code>PhEvalInPat</code> or
   * <code>PhEvalInPat</code>. See usePhrase* for meanings.
   */
  @Option(name = "usePatternEvalEditDistOther")
  public boolean usePatternEvalEditDistOther = false;

  /**
   * These are used to learn weights for features if using logistic regression.
   * Percentage of non-labeled tokens selected as negative.
   */
  @Option(name = "perSelectRand")
  public double perSelectRand = 0.01;

  /**
   * These are used to learn weights for features if using logistic regression.
   * Percentage of negative tokens selected as negative.
   */
  @Option(name = "perSelectNeg")
  public double perSelectNeg = 1;

  /**
   * Especially useful for multi word phrase extraction. Do not extract a phrase
   * if any word is labeled with any other class.
   */
  @Option(name = "doNotExtractPhraseAnyWordLabeledOtherClass")
  public boolean doNotExtractPhraseAnyWordLabeledOtherClass = true;

  /**
   * You can save the inverted index. Lucene index is saved by default to <code>invertedIndexDirectory</code> if given.
   */
  @Option(name="saveInvertedIndex")
  public boolean saveInvertedIndex  = false;

  /**
   * You can load the inverted index using this file.
   * If false and using lucene index, the existing directory is deleted and new index is made.
   */
  @Option(name="loadInvertedIndex")
  public boolean loadInvertedIndex  = false;


  @Option(name = "storePatsForEachToken", gloss="used for storing patterns in PSQL/MEMORY/LUCENE")
  public PatternForEachTokenWay storePatsForEachToken = PatternForEachTokenWay.MEMORY;

  @Option(name = "storePatsIndex", gloss="used for storing patterns index")
  public PatternIndexWay storePatsIndex = PatternIndexWay.MEMORY;

  @Option(name="sampleSentencesForSufficientStats",gloss="% sentences to use for learning pattterns" )
  double sampleSentencesForSufficientStats = 1.0;

//  /**
//   * Directory where to save the sentences ser files.
//   */
//  @Option(name="saveSentencesSerDir")
//  public File saveSentencesSerDir = null;
//
//  public boolean usingDirForSentsInIndex = false;

  // @Option(name = "wekaOptions")
  // public String wekaOptions = "";

  public static String backgroundSymbol = "O";

  int wordShaper = WordShapeClassifier.WORDSHAPECHRIS2;
  private Map<String, String> wordShapeCache = new HashMap<String, String>();

  public SentenceIndex invertedIndex;

  public static String extremedebug = "extremePatDebug";
  public static String minimaldebug = "minimaldebug";

  Properties props;

  public enum PatternForEachTokenWay {MEMORY, LUCENE, DB};
  public enum PatternIndexWay {MEMORY, OPENHFT, LUCENE};

  public ConstantsAndVariables(Properties props, Set<String> labels, Map<String, Class<? extends Key<String>>> answerClass, Map<String, Class> generalizeClasses,
                               Map<String, Map<Class, Object>> ignoreClasses) throws IOException {
    this.labels = labels;
    for(String label: labels){
      this.seedLabelDictionary.put(label, new HashSet<String>());
    }
    this.answerClass = answerClass;
    this.generalizeClasses = generalizeClasses;
    if(this.generalizeClasses == null)
      this.generalizeClasses = new HashMap<String, Class>();
    this.generalizeClasses.putAll(answerClass);
    this.ignoreWordswithClassesDuringSelection = ignoreClasses;
    setUp(props);
  }

  public ConstantsAndVariables(Properties props, Map<String, Set<String>> labelDictionary, Map<String, Class<? extends Key<String>>> answerClass, Map<String, Class> generalizeClasses,
                               Map<String, Map<Class, Object>> ignoreClasses) throws IOException {
    this.seedLabelDictionary = labelDictionary;
    this.labels = labelDictionary.keySet();
    this.answerClass = answerClass;
    this.generalizeClasses = generalizeClasses;
    if(this.generalizeClasses == null)
      this.generalizeClasses = new HashMap<String, Class>();
    this.generalizeClasses.putAll(answerClass);
    this.ignoreWordswithClassesDuringSelection = ignoreClasses;
    setUp(props);
  }

  public ConstantsAndVariables(Properties props, Set<String> labels,  Map<String, Class<? extends TypesafeMap.Key<String>>> answerClass) throws IOException {
    this.labels = labels;
    for(String label: labels){
      this.seedLabelDictionary.put(label, new HashSet<String>());
    }
    this.answerClass = answerClass;
    this.generalizeClasses = new HashMap<String, Class>();
    this.generalizeClasses.putAll(answerClass);
    setUp(props);
  }

  public ConstantsAndVariables(Properties props, String label,  Class<? extends TypesafeMap.Key<String>> answerClass) throws IOException {
    this.labels = new HashSet<String>();
    this.labels.add(label);
    this.seedLabelDictionary.put(label, new HashSet<String>());
    this.answerClass = new HashMap<>();
    this.answerClass.put(label, answerClass);
    this.generalizeClasses = new HashMap<String, Class>();
    this.generalizeClasses.putAll(this.answerClass);
    setUp(props);
  }


  public ConstantsAndVariables(Properties props, Set<String> labels,  Map<String, Class<? extends TypesafeMap.Key<String>>> answerClass, Map<String, Class> generalizeClasses) throws IOException {
    this.labels = labels;
    for(String label: labels){
      this.seedLabelDictionary.put(label, new HashSet<String>());
    }
    this.answerClass = answerClass;
    this.generalizeClasses = generalizeClasses;
    if(this.generalizeClasses == null)
      this.generalizeClasses = new HashMap<String, Class>();
    this.generalizeClasses.putAll(answerClass);
    setUp(props);
  }

  @SuppressWarnings("rawtypes")
  public void setUp(Properties props) throws IOException {
    if (alreadySetUp) {
      return;
    }

    Execution.fillOptions(this, props);
    Execution.fillOptions(PatternFactory.class, props);
    Execution.fillOptions(SurfacePatternFactory.class, props);
    Execution.fillOptions(DepPatternFactory.class, props);

    if (wordIgnoreRegex != null && !wordIgnoreRegex.isEmpty())
      PatternFactory.ignoreWordRegex = Pattern.compile(wordIgnoreRegex);

    for (String label : labels) {
      env.put(label, TokenSequencePattern.getNewEnv());
      // env.get(label).bind("answer", answerClass.get(label));
      for (Entry<String, Class<? extends Key<String>>> en : this.answerClass
          .entrySet()) {
        env.get(label).bind(en.getKey(), en.getValue());
      }
      for (Entry<String, Class> en : generalizeClasses.entrySet())
        env.get(label).bind(en.getKey(), en.getValue());
    }
    Redwood.log(Redwood.DBG, channelNameLogger, "Running with debug output");
    stopWords = new HashSet<String>();

    if(stopWordsPatternFiles != null) {
      Redwood.log(ConstantsAndVariables.minimaldebug, channelNameLogger, "Reading stop words from "
        + stopWordsPatternFiles);
      for (String stopwfile : stopWordsPatternFiles.split("[;,]"))
        stopWords.addAll(IOUtils.linesFromFile(stopwfile));
    }

    englishWords = new HashSet<String>();
    if(englishWordsFiles != null) {
      System.out.println("Reading english words from " + englishWordsFiles);
      for (String englishWordsFile : englishWordsFiles.split("[;,]"))
        englishWords.addAll(IOUtils.linesFromFile(englishWordsFile));
    }

    if (commonWordsPatternFiles != null) {
      commonEngWords = Collections.synchronizedSet(new HashSet<String>());
      for (String file : commonWordsPatternFiles.split("[;,]"))
        commonEngWords.addAll(IOUtils.linesFromFile(file));
    }

    if (otherSemanticClassesFiles != null) {
      if (otherSemanticClassesWords == null)
        otherSemanticClassesWords = Collections
            .synchronizedSet(new HashSet<String>());
      for (String file : otherSemanticClassesFiles.split("[;,]")) {
        for (String w : IOUtils.linesFromFile(file)) {

          String[] t = w.split("\\s+");
          if (t.length <= PatternFactory.numWordsCompound)
            otherSemanticClassesWords.add(w);

        }
      }

      System.out.println("Size of othersemantic class variables is "
        + otherSemanticClassesWords.size());
    } else {
      otherSemanticClassesWords = Collections.synchronizedSet(new HashSet<String>());
      System.out.println("Size of othersemantic class variables is " + 0);
    }

    String stopStr = "/";
    int i = 0;
    for (String s : stopWords) {
      if (i > 0)
        stopStr += "|";
      stopStr += Pattern.quote(s.replaceAll("\\\\", "\\\\\\\\"));
      i++;
    }
    stopStr += "/";
    for (String label : labels) {
      env.get(label).bind("$FILLER",
          "/" + StringUtils.join(PatternFactory.fillerWords, "|") + "/");
      env.get(label).bind("$STOPWORD", stopStr);
      env.get(label).bind("$MOD", "[{tag:/JJ.*/}]");
      if (matchLowerCaseContext){
        env.get(label).setDefaultStringMatchFlags(NodePattern.CASE_INSENSITIVE);
        env.get(label).setDefaultStringPatternFlags(Pattern.CASE_INSENSITIVE);
      }
      env.get(label).bind("OTHERSEM",
          PatternsAnnotations.OtherSemanticLabel.class);
      env.get(label).bind("grandparentparsetag", CoreAnnotations.GrandparentAnnotation.class);
    }

    if (wordClassClusterFile != null) {
      wordClassClusters = new HashMap<String, Integer>();
      for (String line : IOUtils.readLines(wordClassClusterFile)) {
        String[] t = line.split("\t");
        wordClassClusters.put(t[0], Integer.parseInt(t[1]));
      }
    }

    if (generalWordClassClusterFile != null) {
      setGeneralWordClassClusters(new HashMap<String, Integer>());
      for (String line : IOUtils.readLines(generalWordClassClusterFile)) {
        String[] t = line.split("\t");
        getGeneralWordClassClusters().put(t[0], Integer.parseInt(t[1]));
      }
    }

    if(targetAllowedTagsInitialsStr!= null){
      allowedTagsInitials = new HashMap<String, Set<String>>();
      for(String labelstr : targetAllowedTagsInitialsStr.split(";")){
        String[] t = labelstr.split(",");
        Set<String> st = new HashSet<String>();
        for(int j = 1; j < t.length; j++)
          st.add(t[j]);
        allowedTagsInitials.put(t[0], st);
      }
    }

    if(PatternFactory.useTargetNERRestriction && targetAllowedNERs !=null){
      allowedNERsforLabels = new HashMap<String, Set<String>>();
      for(String labelstr : targetAllowedNERs.split(";")){
        String[] t = labelstr.split(",");
        Set<String> st = new HashSet<String>();
        for(int j = 1; j < t.length; j++)
          st.add(t[j]);
        allowedNERsforLabels.put(t[0], st);

      }
    }

    //patternIndex = PatternIndex.newInstance(storePatsIndex, allPatternsDir);
    alreadySetUp = true;
  }



  //streams sents, files-from-which-sents-were read
  static public class DataSentsIterator implements Iterator<Pair<Map<String, DataInstance>, File>> {

    boolean readInMemory = false;
    Iterator<File> sentfilesIter = null;
    boolean batchProcessSents;
    public DataSentsIterator(boolean batchProcessSents){
      this.batchProcessSents = batchProcessSents;
      if(batchProcessSents){
        sentfilesIter = Data.sentsFiles.iterator();
        }

    }
    @Override
    public boolean hasNext() {
      if(batchProcessSents){
       return sentfilesIter.hasNext();
      }else{
        return !readInMemory;
      }
    }

    @Override
    public Pair<Map<String, DataInstance>, File> next() {
      if(batchProcessSents){
        try {
          File f= sentfilesIter.next();
          return new Pair<Map<String, DataInstance>, File>(IOUtils.readObjectFromFile(f), f);
        } catch (IOException e) {
          throw new RuntimeException(e);
        } catch (ClassNotFoundException e) {
          throw new RuntimeException(e);
        }
      }else{
        readInMemory= true;
        return new Pair(Data.sents, new File(""));
      }
    }
  }

  public Map<String, Counter<String>> getWordShapesForLabels() {
    return wordShapesForLabels;
  }

  public void setWordShapesForLabels(Map<String, Counter<String>> wordShapesForLabels) {
    this.wordShapesForLabels = wordShapesForLabels;
  }
  public void addGeneralizeClasses(Map<String, Class> gen) {
    this.generalizeClasses.putAll(gen);
  }

  public static Map<String, Class> getGeneralizeClasses() {
    return generalizeClasses;
  }

  public static Set<String> getStopWords() {
    return stopWords;
  }

  public void addWordShapes(String label, Set<String> words){
    if(!this.wordShapesForLabels.containsKey(label)){
      this.wordShapesForLabels.put(label, new ClassicCounter<String>());
    }
    for(String w: words){
      String ws = null;
      if(wordShapeCache.containsKey(w))
        ws = wordShapeCache.get(w);
      else{
       ws = WordShapeClassifier.wordShape(w, wordShaper);
       wordShapeCache.put(w, ws);
      }

      wordShapesForLabels.get(label).incrementCount(ws);

    }
  }

  public void setSeedLabelDictionary(Map<String, Set<String>> seedSets) {
    this.seedLabelDictionary = seedSets;

    if(usePhraseEvalWordShape || usePatternEvalWordShape){
      this.wordShapesForLabels.clear();
     for(Entry<String, Set<String>> en: seedSets.entrySet())
       addWordShapes(en.getKey(), en.getValue());
    }
  }

  public Map<String, Set<String>> getSeedLabelDictionary() {
    return this.seedLabelDictionary;
  }

  public void addLabelDictionary(String label, Set<String> words) {
    this.seedLabelDictionary.get(label).addAll(words);

    if(usePhraseEvalWordShape || usePatternEvalWordShape)
      addWordShapes(label, words);
  }

  Map<String, Counter<String>> learnedWords = new HashMap<String, Counter<String>>();

  public Counter<String> getLearnedWords(String label) {
    return this.learnedWords.get(label);
  }

  public Map<String, Counter<String>> getLearnedWords() {
    return learnedWords;
  }

  public String getLearnedWordsAsJson(){
    JsonObjectBuilder obj = Json.createObjectBuilder();
    for(Map.Entry<String, Counter<String>> en: learnedWords.entrySet()){
      JsonArrayBuilder arr = Json.createArrayBuilder();
      for(String k: en.getValue().keySet())
        arr.add(k);
      obj.add(en.getKey(), arr);
    }
    return obj.build().toString();
  }

  public void setLearnedWords(Counter<String> words, String label) {
    this.learnedWords.put(label, words);
  }


  public Set<String> getEnglishWords() {
    return this.englishWords;
  }

  public Set<String> getCommonEngWords() {
    return this.commonEngWords;
  }

  public Set<String> getOtherSemanticClassesWords() {
    return this.otherSemanticClassesWords;
  }

  public void setOtherSemanticClassesWords(Set<String> other) {
    this.otherSemanticClassesWords = other;
  }

  public Map<String, Integer> getWordClassClusters() {
    return this.wordClassClusters;
  }

  private Pair<String, Double> getEditDist(Set<String> words, String ph) {
    double minD = editDistMax;
    String minPh = ph;
    for (String e : words) {
      if (e.equals(ph))
        return new Pair<String, Double>(ph, 0.0);

      double d = EditDistanceDamerauLevenshteinLike.editDistance(e, ph, 3);

      if (d == 1)
        return new Pair<String, Double>(e, d);
      if (d == -1)
        d = editDistMax;
      if (d < minD) {
        minD = d;
        minPh = e;
      }
    }
    return new Pair<String, Double>(minPh, minD);

  }

  double editDistMax = 100;

  /**
   * Use this option if you are limited by memory ; ignored if fileFormat is ser.
   */
  @Option(name="batchProcessSents")
  public boolean batchProcessSents = false;

  @Option(name="writeMatchedTokensFiles")
  public boolean writeMatchedTokensFiles = false;

  @Option(name="writeMatchedTokensIdsForEachPhrase")
  public boolean writeMatchedTokensIdsForEachPhrase = false;

  public Pair<String, Double> getEditDistanceFromThisClass(String label,
      String ph, int minLen) {
    if (ph.length() < minLen)
      return new Pair<String, Double>(ph, editDistMax);
    if (editDistanceFromThisClass.containsKey(ph))
      return new Pair<String, Double>(editDistanceFromThisClassMatches.get(ph),
          editDistanceFromThisClass.get(ph));

    Set<String> words = seedLabelDictionary.get(label);
    words.addAll(learnedWords.get(label).keySet());
    Pair<String, Double> minD = getEditDist(words, ph);

    // double minDtotal = editDistMax;
    // String minPh = "";
    // if (minD.second() == editDistMax && ph.contains(" ")) {
    // for (String s : ph.split("\\s+")) {
    // Pair<String, Double> minDSingle = getEditDist(seedLabelDictionary.get(label),
    // s);
    // if (minDSingle.second() < minDtotal) {
    // minDtotal = minDSingle.second;
    // }
    // minPh += " " + minDSingle.first();
    // }
    // minPh = minPh.trim();
    // } else {
    double minDtotal = minD.second();
    String minPh = minD.first();
    // }
    assert (!minPh.isEmpty());
    editDistanceFromThisClass.putIfAbsent(ph, minDtotal);
    editDistanceFromThisClassMatches.putIfAbsent(ph, minPh);
    return new Pair<String, Double>(minPh, minDtotal);
  }

  public Pair<String, Double> getEditDistanceFromOtherSemanticClasses(
      String ph, int minLen) {
    if (ph.length() < minLen)
      return new Pair<String, Double>(ph, editDistMax);
    if (editDistanceFromOtherSemanticClasses.containsKey(ph))
      return new Pair<String, Double>(
          editDistanceFromOtherSemanticClassesMatches.get(ph),
          editDistanceFromOtherSemanticClasses.get(ph));

    Pair<String, Double> minD = getEditDist(otherSemanticClassesWords, ph);

    // double minDtotal = editDistMax;
    // String minPh = "";
    // if (minD.second() == editDistMax && ph.contains(" ")) {
    // for (String s : ph.split("\\s+")) {
    // Pair<String, Double> minDSingle = getEditDist(otherSemanticClassesWords, s);
    // if (minDSingle.second() < minDtotal) {
    // minDtotal = minDSingle.second;
    // }
    // minPh += " " + minDSingle.first();
    // }
    // minPh = minPh.trim();
    // } else {
    double minDtotal = minD.second();
    String minPh = minD.first();
    // }
    assert (!minPh.isEmpty());
    editDistanceFromOtherSemanticClasses.putIfAbsent(ph, minDtotal);
    editDistanceFromOtherSemanticClassesMatches.putIfAbsent(ph, minPh);
    return new Pair<String, Double>(minPh, minDtotal);
  }

  public double getEditDistanceFromEng(String ph, int minLen) {
    if (ph.length() < minLen)
      return editDistMax;
    if (editDistanceFromEnglishWords.containsKey(ph))
      return editDistanceFromEnglishWords.get(ph);
    Pair<String, Double> d = getEditDist(commonEngWords, ph);
    double minD = d.second();
    String minPh = d.first();
    if (d.second() > 2) {
      Pair<String, Double> minD2 = getEditDist(otherSemanticClassesWords, ph);
      if (minD2.second < minD) {
        minD = minD2.second();
        minPh = minD2.first();
      }
    }

    editDistanceFromEnglishWords.putIfAbsent(ph, minD);
    editDistanceFromEnglishWordsMatches.putIfAbsent(ph, minPh);
    return minD;
  }

  public ConcurrentHashMap<String, Double> getEditDistanceFromEnglishWords() {
    return this.editDistanceFromEnglishWords;
  }

  public ConcurrentHashMap<String, String> getEditDistanceFromEnglishWordsMatches() {
    return this.editDistanceFromEnglishWordsMatches;
  }

  public double getEditDistanceScoresOtherClass(String g) {
    double editDist;
    String editDistPh;
    if (editDistanceFromOtherSemanticClasses.containsKey(g)) {
      editDist = editDistanceFromOtherSemanticClasses.get(g);
      editDistPh = editDistanceFromOtherSemanticClassesMatches.get(g);
    } else {
      Pair<String, Double> editMatch = getEditDistanceFromOtherSemanticClasses(
          g, 4);
      editDist = editMatch.second();
      editDistPh = editMatch.first();
    }
    assert (!editDistPh.isEmpty());
    return editDist / (double) editDistPh.length();
  }

  /**
   * 1 if lies in edit distance, 0 if not close to any words
   *
   * @param g
   * @return
   */
  public double getEditDistanceScoresOtherClassThreshold(String g) {
    double editDistRatio = getEditDistanceScoresOtherClass(g);

    if (editDistRatio < 0.2)
      return 1;
    else
      return 0;
  }

  public double getEditDistanceScoresThisClassThreshold(String label, String g) {
    double editDistRatio = getEditDistanceScoresThisClass(label, g);
    if (editDistRatio < 0.2)
      return 1;
    else
      return 0;
  }

  public double getEditDistanceScoresThisClass(String label, String g) {
    double editDist;
    String editDistPh;
    if (editDistanceFromThisClass.containsKey(g)) {
      editDist = editDistanceFromThisClass.get(g);
      editDistPh = editDistanceFromThisClassMatches.get(g);
    } else {
      Pair<String, Double> editMatch = getEditDistanceFromThisClass(label, g, 4);
      editDist = editMatch.second();
      editDistPh = editMatch.first();
    }
    assert (!editDistPh.isEmpty());
    return editDist / (double) editDistPh.length();
  }

  public static boolean isFuzzyMatch(String w1, String w2, int minLen4Fuzzy) {
    EditDistance editDistance = new EditDistance(true);
    if (w1.equals(w2))
      return true;
    if (w2.length() > minLen4Fuzzy) {
      double d = editDistance.score(w1, w2);
      if (d == 1) {
        return true;
      }
    }
    return false;
  }

  public static String containsFuzzy(Set<String> words, String w,
      int minLen4Fuzzy) {
    for (String w1 : words) {
      if (isFuzzyMatch(w1, w, minLen4Fuzzy))
        return w1;
    }
    return null;
  }

  public Map<String, Integer> getGeneralWordClassClusters() {
    return generalWordClassClusters;
  }

  public void setGeneralWordClassClusters(
      Map<String, Integer> generalWordClassClusters) {
    this.generalWordClassClusters = generalWordClassClusters;
  }

  public Map<String, String> getWordShapeCache() {
    return wordShapeCache;
  }


  public Map<String, Class<? extends Key<String>>> getAnswerClass() {
    return answerClass;
  }


  public Map<String, Map<Class, Object>> getIgnoreWordswithClassesDuringSelection() {
    return ignoreWordswithClassesDuringSelection;
  }

  public void addSeedWords(String label, Collection<String> seeds) throws Exception {
    if(!seedLabelDictionary.containsKey(label)){
      throw new Exception("label not present in the model");
    }
    this.seedLabelDictionary.get(label).addAll(seeds);
  }

}
