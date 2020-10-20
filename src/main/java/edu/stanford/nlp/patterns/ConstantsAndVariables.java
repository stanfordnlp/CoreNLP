package edu.stanford.nlp.patterns;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.lang.reflect.Field;
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
import edu.stanford.nlp.stats.Counters;
import edu.stanford.nlp.util.*;
import edu.stanford.nlp.util.ArgumentParser.Option;
import edu.stanford.nlp.util.TypesafeMap.Key;
import edu.stanford.nlp.util.logging.Redwood;

import javax.json.Json;
import javax.json.JsonArrayBuilder;
import javax.json.JsonObjectBuilder;

public class ConstantsAndVariables implements Serializable {

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
  public static boolean matchLowerCaseContext = true;


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

  private Set<String> englishWords = new HashSet<>();

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
  private Set<CandidatePhrase> otherSemanticClassesWords = null;

  /**
   * Seed dictionary, set in the class that uses this class
   */
  private Map<String, Set<CandidatePhrase>> seedLabelDictionary = new HashMap<>();

  /**
   * Just the set of labels
   */
  private Set<String> labels = new HashSet<>();


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
  private static Map<String, Class> generalizeClasses = new HashMap<>();

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

  private static Set<CandidatePhrase> stopWords = null;



  /**
   * Environment for {@link TokenSequencePattern}
   */
  public Map<String, Env> env = new HashMap<>();

  public static Env globalEnv = TokenSequencePattern.getNewEnv();


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

  private Map<String, Integer> wordClassClusters = new HashMap<>();

  /**
   * General cluster file, if you wanna use it somehow, in which each line is
   * word/phrase<tab>clusterid
   */
  @Option(name = "generalWordClassClusterFile")
  String generalWordClassClusterFile = null;

  private Map<String, Integer> generalWordClassClusters = null;

//  @Option(name = "includeExternalFeatures")
//  public boolean includeExternalFeatures = false;

  @Option(name = "externalFeatureWeightsFile")
  public String externalFeatureWeightsDir = null;

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
  private ConcurrentHashMap<String, Double> editDistanceFromEnglishWords = new ConcurrentHashMap<>();
  /**
   * Cached files
   */
  private ConcurrentHashMap<String, String> editDistanceFromEnglishWordsMatches = new ConcurrentHashMap<>();
  /**
   * Cached files
   */
  private ConcurrentHashMap<String, Double> editDistanceFromOtherSemanticClasses = new ConcurrentHashMap<>();
  /**
   * Cached files
   */
  private ConcurrentHashMap<String, String> editDistanceFromOtherSemanticClassesMatches = new ConcurrentHashMap<>();
  /**
   * Cached files
   */
  private ConcurrentHashMap<String, Double> editDistanceFromThisClass = new ConcurrentHashMap<>();
  /**
   * Cached files
   */
  private ConcurrentHashMap<String, String> editDistanceFromThisClassMatches = new ConcurrentHashMap<>();

  private ConcurrentHashMap<String, Counter<String>> wordShapesForLabels = new ConcurrentHashMap<>();



  String channelNameLogger = "settingUp";

  public Map<String, Counter<Integer>> distSimWeights = new HashMap<>();
  public Map<String, Counter<CandidatePhrase>> dictOddsWeights = new HashMap<>();

  @Option(name="invertedIndexClass", gloss="another option is Lucene backed, which is not included in the CoreNLP release. Contact us to get a copy (distributed under Apache License).")
  public Class<? extends SentenceIndex> invertedIndexClass = InvertedIndexByTokens.class;

  /**
   * Where the inverted index (either in memory or lucene) is stored
   */
  @Option(name="invertedIndexDirectory")
  public String invertedIndexDirectory;

  @Option(name="clubNeighboringLabeledWords")
  public boolean clubNeighboringLabeledWords = false;

  @Option(name="patternType")
  public PatternFactory.PatternType patternType = PatternFactory.PatternType.SURFACE;

  @Option(name="subsampleUnkAsNegUsingSim", gloss="When learning a classifier, remove phrases from unknown phrases that are too close to the positive phrases")
  public boolean subsampleUnkAsNegUsingSim = false;

//  @Option(name="subSampleUnkAsNegUsingSimPercentage", gloss="When using subsampleUnkAsNegUsingSim, select bottom %")
//  public double subSampleUnkAsNegUsingSimPercentage = 0.95;

  @Option(name="expandPositivesWhenSampling", gloss="when sampling for learning feature wts for learning phrases, expand the positives")
  public boolean expandPositivesWhenSampling = false;

  @Option(name="expandNegativesWhenSampling", gloss="when sampling for learning feature wts for learning phrases, expand the negatives")
  public boolean expandNegativesWhenSampling = false;

  @Option(name="similarityThresholdHighPrecision", gloss="used for expanding positives")
  public double similarityThresholdHighPrecision  = 0.7;

  @Option(name="positiveSimilarityThresholdLowPrecision", gloss="used for not choosing close unknowns as positives")
  public double positiveSimilarityThresholdLowPrecision  = 0.5;

//  @Option(name="subSampleUnkAsPosUsingSimPercentage", gloss="When using expandPositivesWhenSampling, select top % after applying the threshold")
//  public double subSampleUnkAsPosUsingSimPercentage = 0.05;

  @Option(name="wordVectorFile", gloss = "if using word vectors for computing similarities")
  public String wordVectorFile = null;

  @Option(name="useWordVectorsToComputeSim", gloss="use vectors directly instead of word classes for computing similarity")
  public boolean useWordVectorsToComputeSim;

  @Option(name="logFileVectorSimilarity", gloss="To store vectors for selected/almost-selected positive and negative words")
  String logFileVectorSimilarity = null;

  @Option(name="goldEntitiesEvalFiles", gloss="label1,gold_list_of_entities_file;label2,...")
  public String goldEntitiesEvalFiles = null;

  @Option(name="evaluate")
  public boolean evaluate = false;

  Map<String, Map<String, Boolean>> goldEntities = new HashMap<>();

  @Option(name="featureCountThreshold")
  public int featureCountThreshold = 1;

  @Option(name="expandPhrasesNumTopSimilar", gloss="k in kNN")
  public int expandPhrasesNumTopSimilar = 1;

  /**
   * Whether to do a fuzzy matching when matching seeds to text. You can tune minLen4FuzzyForPattern parameter.
   */
  @Option(name="fuzzyMatch")
  public boolean fuzzyMatch = false;

  /**
   * Ignore case when matching seed words. It's a map so something like {name->true,place->false}
   */
  @Option(name="ignoreCaseSeedMatch")
  public Map<String, String> ignoreCaseSeedMatch = new HashMap<>();

  @Option(name="sentsOutFile")
  public String sentsOutFile = null;

  @Option(name="savePatternsWordsDir")
  public boolean savePatternsWordsDir = true;

  @Option(name="learn")
  public boolean learn = true;


  public Set<String> getLabels() {
    return labels;
  }

//  public void addLearnedWords(String trainLabel, Counter<CandidatePhrase> identifiedWords) {
//    if(!learnedWords.containsKey(trainLabel))
//      learnedWords.put(trainLabel, new ClassicCounter<CandidatePhrase>());
//    this.learnedWords.get(trainLabel).addAll(identifiedWords);
//  }

  public Map<String, String> getAllOptions() {
    Map<String, String> values = new HashMap<>();
    if(props != null)
      props.forEach( (x,y) -> values.put(x.toString(),y == null?"null":y.toString()));

    Class<?> thisClass;
    try {
      thisClass = Class.forName(this.getClass().getName());

      Field[] aClassFields = thisClass.getDeclaredFields();
      for(Field f : aClassFields){
        if(f.getType().getClass().isPrimitive() || Arrays.binarySearch(GetPatternsFromDataMultiClass.printOptionClass, f.getType()) >= 0){
          String fName = f.getName();
          Object fvalue = f.get(this);
          values.put(fName, fvalue == null ? "null" : fvalue.toString());
        }
      }

    } catch (Exception e) {
      e.printStackTrace();
    }
    return values;
  }

  public boolean hasSeedWordOrOtherSem(CandidatePhrase p) {
    for(Map.Entry<String, Set<CandidatePhrase>> seeds: this.seedLabelDictionary.entrySet()){
      if(seeds.getValue().contains(p))
        return true;
    }
    if(otherSemanticClassesWords.contains(p))
      return true;
    return false;
  }

  public TreeMap<Integer, Counter<CandidatePhrase>> getLearnedWordsEachIter(String label) {
    return learnedWordsEachIter.get(label);
  }

  public Map<String, TreeMap<Integer, Counter<CandidatePhrase>>>  getLearnedWordsEachIter() {
    return learnedWordsEachIter;
  }

  public void setLearnedWordsEachIter(TreeMap<Integer, Counter<CandidatePhrase>> words, String label) {
    this.learnedWordsEachIter.put(label, words);
  }


  //PatternFactory.PatternType.SURFACE;


//  public PatternIndex getPatternIndex() {
//    return patternIndex;
//  }
//
//  public void setPatternIndex(PatternIndex patternIndex) {
//    this.patternIndex = patternIndex;
//  }


  public static class ScorePhraseMeasures implements Comparable {

    String name;
    static int num = 0;
    int numObj;
    static Map<String, ScorePhraseMeasures> createdObjects = new ConcurrentHashMap<>();

    public static ScorePhraseMeasures create(String n){
      if(createdObjects.containsKey(n))
        return createdObjects.get(n);
      else
        return new ScorePhraseMeasures(n);
    }

    private ScorePhraseMeasures(String n){
      this.name= n;
      numObj = num++;
      createdObjects.put(n, this);
    }

    @Override
    public String toString(){return name;}

    @Override
    public boolean equals(Object o){
      if(! (o instanceof ScorePhraseMeasures)) return false;
      return ((ScorePhraseMeasures)o).numObj == (this.numObj);
    }

    static final ScorePhraseMeasures DISTSIM = new ScorePhraseMeasures("DistSim");
    static final ScorePhraseMeasures GOOGLENGRAM = new ScorePhraseMeasures("GoogleNGram");
    static final ScorePhraseMeasures PATWTBYFREQ=new ScorePhraseMeasures("PatWtByFreq");
    static final ScorePhraseMeasures  EDITDISTSAME=new ScorePhraseMeasures("EditDistSame");
    static final ScorePhraseMeasures  EDITDISTOTHER =new ScorePhraseMeasures("EditDistOther");
    static final ScorePhraseMeasures  DOMAINNGRAM =new ScorePhraseMeasures("DomainNgram");
    static final ScorePhraseMeasures  SEMANTICODDS =new ScorePhraseMeasures("SemanticOdds");
    static final ScorePhraseMeasures  WORDSHAPE = new ScorePhraseMeasures("WordShape");
    static final ScorePhraseMeasures WORDVECPOSSIMAVG = new ScorePhraseMeasures("WordVecPosSimAvg");
    static final ScorePhraseMeasures WORDVECPOSSIMMAX = new ScorePhraseMeasures("WordVecPosSimMax");
    static final ScorePhraseMeasures WORDVECNEGSIMAVG = new ScorePhraseMeasures("WordVecNegSimAvg");
    static final ScorePhraseMeasures WORDVECNEGSIMMAX = new ScorePhraseMeasures("WordVecNegSimMax");
    static final ScorePhraseMeasures ISFIRSTCAPITAL = new ScorePhraseMeasures("IsFirstLetterCapital");
    static final ScorePhraseMeasures WORDSHAPESTR = new  ScorePhraseMeasures("WordShapeStr");
    static final ScorePhraseMeasures BOW = new ScorePhraseMeasures("Word");
    @Override
    public int compareTo(Object o) {
      if(!(o instanceof  ScorePhraseMeasures))
        return -1;
      else return o.toString().compareTo(this.toString());
    }
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
   * Only works if you have single label. And the word vectors are given.
   */
  @Option(name = "usePhraseEvalWordVector")
  public boolean usePhraseEvalWordVector = false;

  /**
   * use google tf-idf for learning phrases. Need to also provide googleNgram_dbname,
   * googleNgram_username and googleNgram_host
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

  @Option(name = "usePhraseEvalWordShape", gloss="% of phrases of that label that have the same word shape")
  public boolean usePhraseEvalWordShape = false;

  @Option(name="usePhraseEvalWordShapeStr", gloss="uses the word shape str as a feature")
  public boolean usePhraseEvalWordShapeStr = false;

  @Option(name="usePhraseEvalFirstCapital", gloss="words starts with a capital letter")
  public boolean usePhraseEvalFirstCapital;

  /**
   * use bag of words
   */
  @Option(name="usePhraseEvalBOW")
  public boolean usePhraseEvalBOW = false;

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

  @Option(name="usePatternEvalWordShapeStr", gloss="uses the word shape str as a feature")
  public boolean usePatternEvalWordShapeStr = false;

  @Option(name="usePatternEvalFirstCapital", gloss="words starts with a capital letter")
  public boolean usePatternEvalFirstCapital;

  /**
   * Used only if {@link #patternScoring} is <code>PhEvalInPat</code> or
   * <code>PhEvalInPat</code>. See usePhrase* for meanings.
   */
  @Option(name = "usePatternEvalGoogleNgram")
  public boolean usePatternEvalGoogleNgram = false;

  /**
   * Used only if {@link #patternScoring} is <code>PhEvalInPat</code> or
   * <code>PhEvalInPat</code>. See usePhrase* for meanings. Need to also provide googleNgram_dbname,
   * googleNgram_username and googleNgram_host
   */
  @Option(name = "usePatternEvalDomainNgram")
  public boolean usePatternEvalDomainNgram = false;

  /**
   * Used only if {@link #patternScoring} is <code>PhEvalInPat</code> or
   * <code>PhEvalInPatLogP</code>. See usePhrase* for meanings.
   */
  @Option(name = "usePatternEvalSemanticOdds")
  public boolean usePatternEvalSemanticOdds = false;

  /**
   * Used only if {@link #patternScoring} is <code>PhEvalInPat</code> or
   * <code>PhEvalInPatLogP</code>. See usePhrase* for meanings.
   */
  @Option(name = "usePatternEvalEditDistSame")
  public boolean usePatternEvalEditDistSame = false;

  /**
   * Used only if {@link #patternScoring} is <code>PhEvalInPat</code> or
   * <code>PhEvalInPatLogP</code>. See usePhrase* for meanings.
   */
  @Option(name = "usePatternEvalEditDistOther")
  public boolean usePatternEvalEditDistOther = false;

  /**
   * use bag of words
   */
  @Option(name="usePatternEvalBOW")
  public boolean usePatternEvalBOW = false;


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
//
//  @Option(name = "storePatsIndex", gloss="used for storing patterns index")
//  public PatternIndexWay storePatsIndex = PatternIndexWay.MEMORY;

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
  private ConcurrentHashMap<String, String> wordShapeCache = new ConcurrentHashMap<>();

  public SentenceIndex invertedIndex;

  public static String extremedebug = "extremePatDebug";
  public static String minimaldebug = "minimaldebug";

  Properties props;

  public enum PatternForEachTokenWay {MEMORY, LUCENE, DB};
  public enum PatternIndexWay {MEMORY, OPENHFT, LUCENE};

  public List<String> functionWords = Arrays.asList("a","an","the","of","at","on","in","he","she","him","her","they","them","and","no","not","nor","as","do");

  public ConstantsAndVariables(Properties props, Set<String> labels, Map<String, Class<? extends Key<String>>> answerClass, Map<String, Class> generalizeClasses,
                               Map<String, Map<Class, Object>> ignoreClasses) throws IOException {
    this.labels = labels;
    for(String label: labels){
      this.seedLabelDictionary.put(label, new HashSet<>());
    }
    this.answerClass = answerClass;
    this.generalizeClasses = generalizeClasses;
    if(this.generalizeClasses == null)
      this.generalizeClasses = new HashMap<>();
    this.generalizeClasses.putAll(answerClass);
    this.ignoreWordswithClassesDuringSelection = ignoreClasses;
    setUp(props);
  }

  public ConstantsAndVariables(Properties props, Map<String, Set<CandidatePhrase>> labelDictionary, Map<String, Class<? extends Key<String>>> answerClass, Map<String, Class> generalizeClasses,
                               Map<String, Map<Class, Object>> ignoreClasses) throws IOException {

    //make the list unmodifiable!
    for(Entry<String, Set<CandidatePhrase>> en2: labelDictionary.entrySet()){
      seedLabelDictionary.put(en2.getKey(), Collections.unmodifiableSet(en2.getValue()));
    }

    this.labels = labelDictionary.keySet();
    this.answerClass = answerClass;
    this.generalizeClasses = generalizeClasses;
    if(this.generalizeClasses == null)
      this.generalizeClasses = new HashMap<>();
    this.generalizeClasses.putAll(answerClass);
    this.ignoreWordswithClassesDuringSelection = ignoreClasses;
    setUp(props);
  }

  public ConstantsAndVariables(Properties props, Set<String> labels,  Map<String, Class<? extends TypesafeMap.Key<String>>> answerClass) throws IOException {
    this.labels = labels;
    for(String label: labels){
      this.seedLabelDictionary.put(label, new HashSet<>());
    }
    this.answerClass = answerClass;
    this.generalizeClasses = new HashMap<>();
    this.generalizeClasses.putAll(answerClass);
    setUp(props);
  }

  public ConstantsAndVariables(Properties props, String label,  Class<? extends TypesafeMap.Key<String>> answerClass) throws IOException {
    this.labels = new HashSet<>();
    this.labels.add(label);
    this.seedLabelDictionary.put(label, new HashSet<>());
    this.answerClass = new HashMap<>();
    this.answerClass.put(label, answerClass);
    this.generalizeClasses = new HashMap<>();
    this.generalizeClasses.putAll(this.answerClass);
    setUp(props);
  }


  public ConstantsAndVariables(Properties props, Set<String> labels,  Map<String, Class<? extends TypesafeMap.Key<String>>> answerClass, Map<String, Class> generalizeClasses) throws IOException {
    this.labels = labels;
    for(String label: labels){
      this.seedLabelDictionary.put(label, new HashSet<>());
    }
    this.answerClass = answerClass;
    this.generalizeClasses = generalizeClasses;
    if(this.generalizeClasses == null)
      this.generalizeClasses = new HashMap<>();
    this.generalizeClasses.putAll(answerClass);
    setUp(props);
  }

  @SuppressWarnings("rawtypes")
  public void setUp(Properties props) throws IOException {
    if (alreadySetUp) {
      return;
    }

    Redwood.log(Redwood.DBG, "Setting up ConstantsAndVariables");

    ArgumentParser.fillOptions(this, props);
    ArgumentParser.fillOptions(PatternFactory.class, props);
    ArgumentParser.fillOptions(SurfacePatternFactory.class, props);
    ArgumentParser.fillOptions(DepPatternFactory.class, props);

    if (wordIgnoreRegex != null && !wordIgnoreRegex.isEmpty()) {
      Redwood.log(Redwood.DBG, "Ignore word regex is " + wordIgnoreRegex);
      PatternFactory.ignoreWordRegex = Pattern.compile(wordIgnoreRegex);
    }

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
    stopWords = new HashSet<>();

    if(stopWordsPatternFiles != null) {
      Redwood.log(ConstantsAndVariables.minimaldebug, channelNameLogger, "Reading stop words from "
        + stopWordsPatternFiles);
      for (String stopwfile : stopWordsPatternFiles.split("[;,]"))
      {
        for(String word: IOUtils.readLines(stopwfile)){
          if(!word.trim().isEmpty())
            stopWords.add(CandidatePhrase.createOrGet(word.trim()));
        }
      }
    }

    englishWords = new HashSet<>();
    if(englishWordsFiles != null) {
      System.out.println("Reading english words from " + englishWordsFiles);
      for (String englishWordsFile : englishWordsFiles.split("[;,]"))
        englishWords.addAll(IOUtils.linesFromFile(englishWordsFile));
    }

    if (commonWordsPatternFiles != null) {
      commonEngWords = Collections.synchronizedSet(new HashSet<>());
      for (String file : commonWordsPatternFiles.split("[;,]"))
        commonEngWords.addAll(IOUtils.linesFromFile(file));
    }

    if (otherSemanticClassesFiles != null) {
      if (otherSemanticClassesWords == null)
        otherSemanticClassesWords = Collections
            .synchronizedSet(new HashSet<>());
      for (String file : otherSemanticClassesFiles.split("[;,]")) {
        for (File f : listFileIncludingItself(file)) {
          for (String w : IOUtils.readLines(f)) {
            String[] t = w.split("\\s+");
            if (t.length <= PatternFactory.numWordsCompoundMax)
              otherSemanticClassesWords.add(CandidatePhrase.createOrGet(w));
          }
        }
      }
      System.out.println("Size of othersemantic class variables is "
        + otherSemanticClassesWords.size());
    } else {
      otherSemanticClassesWords = Collections.synchronizedSet(new HashSet<>());
      System.out.println("Size of othersemantic class variables is " + 0);
    }

    String stopStr = "/";
    int i = 0;
    for (CandidatePhrase s : stopWords) {
      if (i > 0)
        stopStr += "|";
      stopStr += Pattern.quote(s.getPhrase().replaceAll("\\\\", "\\\\\\\\"));
      i++;
    }
    stopStr += "/";
    for (String label : labels) {
      env.get(label).bind("$FILLER",
          "/" + StringUtils.join(PatternFactory.fillerWords, "|") + "/");
      env.get(label).bind("$STOPWORD", stopStr);
      env.get(label).bind("$MOD", "[{tag:/JJ.*/}]");
      if (matchLowerCaseContext){
        env.get(label).setDefaultStringMatchFlags(NodePattern.CASE_INSENSITIVE | NodePattern.UNICODE_CASE);
        env.get(label).setDefaultStringPatternFlags(Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
      }
      env.get(label).bind("OTHERSEM",
          PatternsAnnotations.OtherSemanticLabel.class);
      env.get(label).bind("grandparentparsetag", CoreAnnotations.GrandparentAnnotation.class);
    }

    if (wordClassClusterFile != null) {
      wordClassClusters = new HashMap<>();
      for (String line : IOUtils.readLines(wordClassClusterFile)) {
        String[] t = line.split("\t");
        wordClassClusters.put(t[0], Integer.parseInt(t[1]));
      }
    }

    if (generalWordClassClusterFile != null) {
      setGeneralWordClassClusters(new HashMap<>());
      for (String line : IOUtils.readLines(generalWordClassClusterFile)) {
        String[] t = line.split("\t");
        getGeneralWordClassClusters().put(t[0], Integer.parseInt(t[1]));
      }
    }

    if(targetAllowedTagsInitialsStr!= null){
      allowedTagsInitials = new HashMap<>();
      for(String labelstr : targetAllowedTagsInitialsStr.split(";")){
        String[] t = labelstr.split(",");
        Set<String> st = new HashSet<>();
        for(int j = 1; j < t.length; j++)
          st.add(t[j]);
        allowedTagsInitials.put(t[0], st);
      }
    }

    if(PatternFactory.useTargetNERRestriction && targetAllowedNERs !=null){
      allowedNERsforLabels = new HashMap<>();
      for(String labelstr : targetAllowedNERs.split(";")){
        String[] t = labelstr.split(",");
        Set<String> st = new HashSet<>();
        for(int j = 1; j < t.length; j++)
          st.add(t[j]);
        allowedNERsforLabels.put(t[0], st);

      }
    }

    for(String label: labels){
      learnedWordsEachIter.put(label, new TreeMap<>());
    }

   if(usePhraseEvalGoogleNgram || usePatternEvalDomainNgram) {
     Data.usingGoogleNgram = true;
     ArgumentParser.fillOptions(GoogleNGramsSQLBacked.class, props);
   }
  if(goldEntitiesEvalFiles !=null && evaluate)
    goldEntities = readGoldEntities(goldEntitiesEvalFiles);
    alreadySetUp = true;
  }


  public static Iterable<File> listFileIncludingItself(String file) {
    File f = new File(file);
    if(!f.isDirectory())
      return Arrays.asList(f);
    else return IOUtils.iterFilesRecursive(f);
  }

  // The format of goldEntitiesEvalFiles is assumed same as
  // seedwordsfiles: label,file;label2,file2;...
  // Each file of gold entities consists of each entity in newline with
  // incorrect entities marked with "#" at the end of the entity.
  // Learned entities not present in the gold file are considered
  // negative.
  static Map<String, Map<String, Boolean>> readGoldEntities(String goldEntitiesEvalFiles){
    Map<String, Map<String, Boolean>> goldWords = new HashMap<>();
    if (goldEntitiesEvalFiles != null) {
      for (String gfile : goldEntitiesEvalFiles.split(";")) {
        String[] t = gfile.split(",");
        String label = t[0];
        String goldfile = t[1];
        Map<String, Boolean> goldWords4Label = new HashMap<>();
        for (String line : IOUtils.readLines(goldfile)) {
          line = line.trim();
          if (line.isEmpty())
            continue;

          if (line.endsWith("#"))
            goldWords4Label.put(line.substring(0, line.length() - 1), false);
          else
            goldWords4Label.put(line, true);
        }
        goldWords.put(label, goldWords4Label);
      }
    }
    return goldWords;
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
          return new Pair<>(IOUtils.readObjectFromFile(f), f);
        } catch (IOException | ClassNotFoundException e) {
          throw new RuntimeException(e);
        }
      }else{
        readInMemory= true;
        return new Pair<>(Data.sents, new File(Data.inMemorySaveFileLocation));
      }
    }
  }

  public Map<String, Counter<String>> getWordShapesForLabels() {
    return wordShapesForLabels;
  }

//  public void setWordShapesForLabels(ConcurrentHashMap<String, Counter<String>> wordShapesForLabels) {
//    this.wordShapesForLabels = wordShapesForLabels;
//  }
//  public void addGeneralizeClasses(Map<String, Class> gen) {
//    this.generalizeClasses.putAll(gen);
//  }

  public static Map<String, Class> getGeneralizeClasses() {
    return generalizeClasses;
  }

  public static Set<CandidatePhrase> getStopWords() {
    return stopWords;
  }

  public void addWordShapes(String label, Set<CandidatePhrase> words){
    if(!this.wordShapesForLabels.containsKey(label)){
      this.wordShapesForLabels.put(label, new ClassicCounter<>());
    }
    for(CandidatePhrase wc: words){
      String w = wc.getPhrase();
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

//  public void setSeedLabelDictionary(Map<String, Set<CandidatePhrase>> seedSets) {
//    this.seedLabelDictionary = seedSets;
//
//    if(usePhraseEvalWordShape || usePatternEvalWordShape){
//      this.wordShapesForLabels.clear();
//     for(Entry<String, Set<CandidatePhrase>> en: seedSets.entrySet())
//       addWordShapes(en.getKey(), en.getValue());
//    }
//  }

  public Map<String, Set<CandidatePhrase>> getSeedLabelDictionary() {

    return this.seedLabelDictionary;
  }


  //Map<String, Counter<CandidatePhrase>> learnedWords = new HashMap<String, Counter<CandidatePhrase>>();
  Map<String, TreeMap<Integer, Counter<CandidatePhrase>>> learnedWordsEachIter = new HashMap<>();

  public Counter<CandidatePhrase> getLearnedWords(String label) {
    Counter<CandidatePhrase> learned = Counters.flatten(learnedWordsEachIter.get(label));
    if(learned == null){
      learned = new ClassicCounter<>();
      learnedWordsEachIter.put(label, new TreeMap<>());
    }
    return learned;
  }

//  public Map<String, Counter<CandidatePhrase>> getLearnedWords() {
//    return Counters.flatten(learnedWordsEachIter);
//  }
  //public void setLearnedWords(Counter<CandidatePhrase> words, String label) {
  //  this.learnedWords.put(label, words);
  //}

  public String getLearnedWordsAsJson(){
    JsonObjectBuilder obj = Json.createObjectBuilder();
    for(String label: getLabels()){
    Counter<CandidatePhrase> learnedWords =  getLearnedWords(label);
      JsonArrayBuilder arr = Json.createArrayBuilder();
      for(CandidatePhrase k: learnedWords.keySet())
        arr.add(k.getPhrase());
      obj.add(label, arr);
    }
    return obj.build().toString();
  }

  public String getLearnedWordsAsJsonLastIteration(){
    JsonObjectBuilder obj = Json.createObjectBuilder();
    for(String label: getLabels()){
      Counter<CandidatePhrase> learnedWords =  getLearnedWordsEachIter(label).lastEntry().getValue();
      JsonArrayBuilder arr = Json.createArrayBuilder();
      for(CandidatePhrase k: learnedWords.keySet())
        arr.add(k.getPhrase());
      obj.add(label, arr);
    }
    return obj.build().toString();
  }

  public String getSetWordsAsJson(Map<String, Counter<CandidatePhrase>> words){
    JsonObjectBuilder obj = Json.createObjectBuilder();
    for(String label: getLabels()){
      JsonArrayBuilder arr = Json.createArrayBuilder();
      for(CandidatePhrase k: words.get(label).keySet())
        arr.add(k.getPhrase());
      obj.add(label, arr);
    }
    return obj.build().toString();
  }



  public Set<String> getEnglishWords() {
    return this.englishWords;
  }

  public Set<String> getCommonEngWords() {
    return this.commonEngWords;
  }

  public Set<CandidatePhrase> getOtherSemanticClassesWords() {
    return this.otherSemanticClassesWords;
  }

  public void setOtherSemanticClassesWords(Set<CandidatePhrase> other) {
    this.otherSemanticClassesWords = other;
  }

  public Map<String, Integer> getWordClassClusters() {
    return this.wordClassClusters;
  }

  private Pair<String, Double> getEditDist(Collection<CandidatePhrase> words, String ph) {
    double minD = editDistMax;
    String minPh = ph;
    for (CandidatePhrase ec : words) {
      String e = ec.getPhrase();
      if (e.equals(ph))
        return new Pair<>(ph, 0.0);

      double d = EditDistanceDamerauLevenshteinLike.editDistance(e, ph, 3);

      if (d == 1)
        return new Pair<>(e, d);
      if (d == -1)
        d = editDistMax;
      if (d < minD) {
        minD = d;
        minPh = e;
      }
    }
    return new Pair<>(minPh, minD);

  }

  final double editDistMax = 1000;

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
      return new Pair<>(ph, editDistMax);
//    if (editDistanceFromThisClass.containsKey(ph))
//      return new Pair<String, Double>(editDistanceFromThisClassMatches.get(ph),
//          editDistanceFromThisClass.get(ph));

    Set<CandidatePhrase> words = new HashSet<>(seedLabelDictionary.get(label));
    words.addAll(getLearnedWords(label).keySet());
    Pair<String, Double> minD = getEditDist(words, ph);

    double minDtotal = minD.second();
    String minPh = minD.first();
    assert (!minPh.isEmpty());
//    editDistanceFromThisClass.putIfAbsent(ph, minDtotal);
//    editDistanceFromThisClassMatches.putIfAbsent(ph, minPh);
    return new Pair<>(minPh, minDtotal);
  }

  public Pair<String, Double> getEditDistanceFromOtherClasses(String label, String ph, int minLen) {
    if (ph.length() < minLen)
      return new Pair<>(ph, editDistMax);
//    if (editDistanceFromOtherSemanticClasses.containsKey(ph))
//      return new Pair<String, Double>(
//          editDistanceFromOtherSemanticClassesMatches.get(ph),
//          editDistanceFromOtherSemanticClasses.get(ph));

    Pair<String, Double> minD = getEditDist(otherSemanticClassesWords, ph);
    String minPh = minD.first();
    double minDfinal = minD.second();
    for(String l: labels){
      if(l.equals(label))
        continue;
      Pair<String, Double> editMatch = getEditDistanceFromThisClass(l, ph, minLen);
      if(editMatch.second() < minDfinal){
        minDfinal = editMatch.second();
        minPh = editMatch.first();
      }
    }
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

    // }
    assert (!minPh.isEmpty());
//    editDistanceFromOtherSemanticClasses.putIfAbsent(ph, minDtotal);
//    editDistanceFromOtherSemanticClassesMatches.putIfAbsent(ph, minPh);
    return new Pair<>(minPh, minDfinal);
  }

//  public double getEditDistanceFromEng(String ph, int minLen) {
//    if (ph.length() < minLen)
//      return editDistMax;
//    if (editDistanceFromEnglishWords.containsKey(ph))
//      return editDistanceFromEnglishWords.get(ph);
//    Pair<String, Double> d = getEditDist(commonEngWords, ph);
//    double minD = d.second();
//    String minPh = d.first();
//    if (d.second() > 2) {
//      Pair<String, Double> minD2 = getEditDist(CandidatePhrase.convertToString(otherSemanticClassesWords), ph);
//      if (minD2.second < minD) {
//        minD = minD2.second();
//        minPh = minD2.first();
//      }
//    }
//
//    editDistanceFromEnglishWords.putIfAbsent(ph, minD);
//    editDistanceFromEnglishWordsMatches.putIfAbsent(ph, minPh);
//    return minD;
//  }

  public ConcurrentHashMap<String, Double> getEditDistanceFromEnglishWords() {
    return this.editDistanceFromEnglishWords;
  }

  public ConcurrentHashMap<String, String> getEditDistanceFromEnglishWordsMatches() {
    return this.editDistanceFromEnglishWordsMatches;
  }

  public double getEditDistanceScoresOtherClass(String label, String g) {
    double editDist;
    String editDistPh;
//    if (editDistanceFromOtherSemanticClasses.containsKey(g)) {
//      editDist = editDistanceFromOtherSemanticClasses.get(g);
//      editDistPh = editDistanceFromOtherSemanticClassesMatches.get(g);
//    } else {
      Pair<String, Double> editMatch = getEditDistanceFromOtherClasses(label, g, 4);
      editDist = editMatch.second();
      editDistPh = editMatch.first();
//    }
    assert (!editDistPh.isEmpty());
    return (editDist == editDistMax ? 1.0 : (editDist / (double) Math.max(g.length(), editDistPh.length())));
  }

  /**
   * 1 if lies in edit distance, 0 if not close to any words
   *
   * @param g
   * @return
   */
  public double getEditDistanceScoresOtherClassThreshold(String label, String g) {
    double editDistRatio = getEditDistanceScoresOtherClass(label, g);

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
//    if (editDistanceFromThisClass.containsKey(g)) {
//      editDist = editDistanceFromThisClass.get(g);
//      editDistPh = editDistanceFromThisClassMatches.get(g);
//      assert (!editDistPh.isEmpty());
//    } else {
//
      Pair<String, Double> editMatch = getEditDistanceFromThisClass(label, g, 4);
      editDist = editMatch.second();
      editDistPh = editMatch.first();
      assert (!editDistPh.isEmpty());
    //}

    return ((editDist == editDistMax) ? 1.0 : (editDist / (double) Math.max(g.length(), editDistPh.length())));
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

  public static CandidatePhrase containsFuzzy(Set<CandidatePhrase> words, CandidatePhrase w,
      int minLen4Fuzzy) {
    for (CandidatePhrase w1 : words) {
      if (isFuzzyMatch(w1.getPhrase(), w.getPhrase(), minLen4Fuzzy))
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

  public void addSeedWords(String label, Collection<CandidatePhrase> seeds) throws Exception {
    if(!seedLabelDictionary.containsKey(label)){
      throw new Exception("label not present in the model");
    }

    Set<CandidatePhrase> seedWords = new HashSet<>(seedLabelDictionary.get(label));
    seedWords.addAll(seeds);
    seedLabelDictionary.put(label, Collections.unmodifiableSet(seedWords));
  }

}
