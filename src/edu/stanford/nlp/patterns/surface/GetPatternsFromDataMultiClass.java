package edu.stanford.nlp.patterns.surface;

import java.io.BufferedInputStream;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Serializable;
import java.lang.reflect.InvocationTargetException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonArrayBuilder;
import javax.json.JsonObjectBuilder;
import javax.json.JsonReader;
import javax.json.JsonValue;

import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.patterns.surface.ConstantsAndVariables;
import edu.stanford.nlp.patterns.surface.ConstantsAndVariables.ScorePhraseMeasures;
import edu.stanford.nlp.patterns.surface.Data;
import edu.stanford.nlp.patterns.surface.PatternsAnnotations;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.stats.Counters;
import edu.stanford.nlp.stats.TwoDimensionalCounter;
import edu.stanford.nlp.util.ArrayUtils;
import edu.stanford.nlp.util.CollectionUtils;
import edu.stanford.nlp.util.Execution.*;
import edu.stanford.nlp.util.CollectionValuedMap;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.EditDistance;
import edu.stanford.nlp.util.Execution;
import edu.stanford.nlp.util.Pair;
import edu.stanford.nlp.util.PriorityQueue;
import edu.stanford.nlp.util.Sets;
import edu.stanford.nlp.util.StringUtils;
import edu.stanford.nlp.util.Triple;
import edu.stanford.nlp.util.logging.Redwood;

/**
 * Given text and a seed list, this class gives more words like the seed words
 * by learning surface word patterns.
 * <p>
 * 
 * The multi-threaded class (<code>nthread</code> parameter for number of
 * threads) takes as input.
 * 
 * To use the default options, run
 * <p>
 * <code>java -mx1000m edu.stanford.nlp.patterns.surface.GetPatternsFromDataMultiClass -file text_file -seedWordsFiles label1,seedwordlist1;label2,seedwordlist2;... -justificationDirJson output_directory (optional)</code>
 * <p>
 * IMPORTANT: Many flags are described in the classes
 * {@link ConstantsAndVariables}, {@link CreatePatterns}, and
 * {@link ScorePhrases}.
 * 
 * <code>fileFormat</code>: (Optional) Default is text. Valid values are text
 * (or txt) and ser, where the serialized file is of the type Map<String,
 * List<CoreLabel>>.
 * <p>
 * <code>file</code>: (Required) Input file (default assumed text)
 * <p>
 * <code>seedWordsFiles</code>: (Required)
 * label1,file_seed_words1;label2,file_seed_words2;... where file_seed_words are
 * files with list of seed words, one in each line
 * <p>
 * <code>justificationDirJson</code>: (Optional) output directory where
 * visualization/output files are stored
 * <p>
 * For other flags, see individual comments for each flag.
 * 
 * @author Sonal Gupta (sonal@cs.stanford.edu)
 */

public class GetPatternsFromDataMultiClass implements Serializable {

  private static final long serialVersionUID = 1L;

  public Map<String, Map<Integer, Triple<Set<SurfacePattern>, Set<SurfacePattern>, Set<SurfacePattern>>>> patternsForEachToken = null;

  public Map<String, Set<String>> wordsForOtherClass = null;
  Counter<String> patternsOtherClass = null;

  String channelNameLogger = "patterns";

  /**
   * 
   * RlogF is from Riloff 1996, when R's denominator is (pos+neg+unlabeled)
   * <p>
   * RlogFPosNeg is when the R's denominator is just (pos+negative) examples
   * <p>
   * PosNegOdds is just the ratio of number of positive words to number of
   * negative
   * <p>
   * PosNegUnlabOdds is just the ratio of number of positive words to number of
   * negative (unlabeled words + negative)
   * <p>
   * RatioAll is pos/(neg+pos+unlabeled)
   * <p>
   * YanGarber02 is the modified version presented in
   * "Unsupervised Learning of Generalized Names"
   * <p>
   * LOGREG is learning logisitic regression
   * <p>
   * SqrtAllRatio is the pattern scoring used in Gupta et al. JAMIA
   * <p>
   * Below F1 and BPB based on paper
   * "Unsupervised Method for Automatics Construction of a disease dictionary..."
   * 2014 paper
   */
  public enum PatternScoring {
    F1, RlogF, RlogFPosNeg, RlogFUnlabNeg, RlogFNeg, PhEvalInPat, PhEvalInPatLogP, PosNegOdds, YanGarber02, PosNegUnlabOdds, RatioAll, LOGREG, SqrtAllRatio, LinICML03

  };

  enum WordScoring {
    BPB, WEIGHTEDNORM
  };

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
   * Detailed review of why each pattern and phrase was extracted in the command
   * line
   */
  @Option(name = "justify")
  public boolean justify = false;

  /**
   * The output directory where the justifications of learning patterns and
   * phrases would be saved. These are needed for visualization
   */
  @Option(name = "justificationDirJson")
  public String justificationDirJson = null;

  /**
   * Maximum number of words in the target phrase
   */
  @Option(name = "numWordsCompound")
  public int numWordsCompound = 2;

  /**
   * If score for a pattern is square rooted
   */
  @Option(name = "sqrtPatScore")
  public boolean sqrtPatScore = false;

  /**
   * Cached file of all patterns for all tokens
   */
  @Option(name = "allPatternsFile")
  public String allPatternsFile = null;

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

  /**
   * Do not learn patterns that do not extract any unlabeled tokens (kind of
   * useless)
   */
  @Option(name = "discardPatternsWithNoUnlabSupport")
  public boolean discardPatternsWithNoUnlabSupport = true;

  /**
   * Currently, does not work correctly. TODO: make this work. Ideally this
   * would label words only when they occur in the context of any learned
   * pattern
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
   * Debug flag for learning patterns
   */
  @Option(name = "learnPatternsDebug")
  public boolean learnPatternsDebug = false;

  /**
   * Do not learn patterns in which the neighboring words have the same label.
   */
  @Option(name = "ignorePatWithLabeledNeigh")
  public boolean ignorePatWithLabeledNeigh = false;

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
  public boolean useMatchingPhrase = false;

  /**
   * Remove patterns that have number of positive words less than this.
   */
  @Option(name = "minPosPhraseSupportForPat")
  public int minPosPhraseSupportForPat = 1;

  /**
   * Remove patterns that have number of words in the denominator of the
   * patternscoring measure less than this.
   */
  @Option(name = "minUnlabNegPhraseSupportForPat")
  public int minUnlabNegPhraseSupportForPat = 0;

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
   * Debug log output
   */
  @Option(name = "extremedebug")
  public boolean extremedebug = false;

  /**
   * use the seed dictionaries and the new words learned for the other labels in
   * the previous iterations as negative
   */
  @Option(name = "useOtherLabelsWordsasNegative")
  public boolean useOtherLabelsWordsasNegative = true;

  Map<String, Boolean> writtenPatInJustification = new HashMap<String, Boolean>();


  Map<String, Counter<SurfacePattern>> learnedPatterns = new HashMap<String, Counter<SurfacePattern>>();
  Map<String, Counter<String>> learnedWords = new HashMap<String, Counter<String>>();

  public Map<String, TwoDimensionalCounter<String, SurfacePattern>> wordsPatExtracted = new HashMap<String, TwoDimensionalCounter<String, SurfacePattern>>();

  boolean alreadySetUp = false;

  Properties props;
  public ScorePhrases scorePhrases;
  public ConstantsAndVariables constVars = new ConstantsAndVariables();
  public CreatePatterns createPats;

  DecimalFormat df = new DecimalFormat("#.##");

  /*
   * when there is only one label
   */
  public GetPatternsFromDataMultiClass(Properties props,
      Map<String, List<CoreLabel>> sents, Set<String> seedSet,
      String answerLabel) throws IOException, InstantiationException,
      IllegalAccessException, IllegalArgumentException,
      InvocationTargetException, NoSuchMethodException, SecurityException {
    this(props, sents, seedSet, PatternsAnnotations.PatternLabel1.class,
        answerLabel);
  }

  @SuppressWarnings("rawtypes")
  public GetPatternsFromDataMultiClass(Properties props,
      Map<String, List<CoreLabel>> sents, Set<String> seedSet,
      Class answerClass, String answerLabel) throws IOException,
      InstantiationException, IllegalAccessException, IllegalArgumentException,
      InvocationTargetException, NoSuchMethodException, SecurityException {
    this.props = props;
    Map<String, Class> ansCl = new HashMap<String, Class>();
    ansCl.put(answerLabel, answerClass);

    Map<String, Map<String, Class>> generalizeClasses = new HashMap<String, Map<String, Class>>();
    generalizeClasses.put(answerLabel, new HashMap<String, Class>());

    Map<String, Map<Class, Object>> ignoreClasses = new HashMap<String, Map<Class, Object>>();
    ignoreClasses.put(answerLabel, new HashMap<Class, Object>());

    Map<String, Set<String>> seedSets = new HashMap<String, Set<String>>();
    seedSets.put(answerLabel, seedSet);
    setUpConstructor(sents, seedSets, ansCl, generalizeClasses, ignoreClasses);

  }

  @SuppressWarnings("rawtypes")
  public GetPatternsFromDataMultiClass(Properties props,
      Map<String, List<CoreLabel>> sents, Set<String> seedSet,
      String answerLabel, Map<String, Class> generalizeClasses,
      Map<Class, Object> ignoreClasses) throws IOException,
      InstantiationException, IllegalAccessException, IllegalArgumentException,
      InvocationTargetException, NoSuchMethodException, SecurityException {
    this(props, sents, seedSet, PatternsAnnotations.PatternLabel1.class,
        answerLabel, generalizeClasses, ignoreClasses);
  }

  @SuppressWarnings("rawtypes")
  public GetPatternsFromDataMultiClass(Properties props,
      Map<String, List<CoreLabel>> sents, Set<String> seedSet,
      Class answerClass, String answerLabel,
      Map<String, Class> generalizeClasses, Map<Class, Object> ignoreClasses)
      throws IOException, InstantiationException, IllegalAccessException,
      IllegalArgumentException, InvocationTargetException,
      NoSuchMethodException, SecurityException {
    this.props = props;
    Map<String, Class> ansCl = new HashMap<String, Class>();
    ansCl.put(answerLabel, answerClass);

    Map<String, Map<String, Class>> gC = new HashMap<String, Map<String, Class>>();
    gC.put(answerLabel, generalizeClasses);
    Map<String, Map<Class, Object>> iC = new HashMap<String, Map<Class, Object>>();
    iC.put(answerLabel, ignoreClasses);

    Map<String, Set<String>> seedSets = new HashMap<String, Set<String>>();
    seedSets.put(answerLabel, seedSet);
    setUpConstructor(sents, seedSets, ansCl, gC, iC);
  }

  @SuppressWarnings("rawtypes")
  public GetPatternsFromDataMultiClass(Properties props,
      Map<String, List<CoreLabel>> sents, Map<String, Set<String>> seedSets)
      throws IOException, InstantiationException, IllegalAccessException,
      IllegalArgumentException, InvocationTargetException,
      NoSuchMethodException, SecurityException, ClassNotFoundException {
    this.props = props;
    Map<String, Class> ansCl = new HashMap<String, Class>();
    Map<String, Map<String, Class>> gC = new HashMap<String, Map<String, Class>>();
    Map<String, Map<Class, Object>> iC = new HashMap<String, Map<Class, Object>>();
    int i = 1;
    for (String label : seedSets.keySet()) {
      String ansclstr = "edu.stanford.nlp.patterns.surface.PatternsAnnotations$PatternLabel"
          + i;
      ansCl.put(label, Class.forName(ansclstr));

      gC.put(label, new HashMap<String, Class>());
      iC.put(label, new HashMap<Class, Object>());
      i++;
    }

    setUpConstructor(sents, seedSets, ansCl, gC, iC);
  }

  @SuppressWarnings("rawtypes")
  public GetPatternsFromDataMultiClass(Properties props,
      Map<String, List<CoreLabel>> sents, Map<String, Set<String>> seedSets,
      Map<String, Class> answerClass) throws IOException,
      InstantiationException, IllegalAccessException, IllegalArgumentException,
      InvocationTargetException, NoSuchMethodException, SecurityException {
    this(props, sents, seedSets, answerClass,
        new HashMap<String, Map<String, Class>>(),
        new HashMap<String, Map<Class, Object>>());
  }

  /**
   * generalize classes basically maps label strings to a map of generalized
   * strings and the corresponding class ignoreClasses have to be boolean
   * 
   * @throws IOException
   * @throws SecurityException
   * @throws NoSuchMethodException
   * @throws InvocationTargetException
   * @throws IllegalArgumentException
   * @throws IllegalAccessException
   * @throws InstantiationException
   */
  @SuppressWarnings("rawtypes")
  public GetPatternsFromDataMultiClass(Properties props,
      Map<String, List<CoreLabel>> sents, Map<String, Set<String>> seedSets,
      Map<String, Class> answerClass,
      Map<String, Map<String, Class>> generalizeClasses,
      Map<String, Map<Class, Object>> ignoreClasses) throws IOException,
      InstantiationException, IllegalAccessException, IllegalArgumentException,
      InvocationTargetException, NoSuchMethodException, SecurityException {
    this.props = props;
    if (generalizeClasses.isEmpty()) {
      for (String label : seedSets.keySet())
        generalizeClasses.put(label, new HashMap<String, Class>());
    }
    if (ignoreClasses.isEmpty()) {
      for (String label : seedSets.keySet())
        ignoreClasses.put(label, new HashMap<Class, Object>());
    }
    setUpConstructor(sents, seedSets, answerClass, generalizeClasses,
        ignoreClasses);
  }

  @SuppressWarnings("rawtypes")
  private void setUpConstructor(Map<String, List<CoreLabel>> sents,
      Map<String, Set<String>> seedSets, Map<String, Class> answerClass,
      Map<String, Map<String, Class>> generalizeClasses,
      Map<String, Map<Class, Object>> ignoreClasses) throws IOException,
      InstantiationException, IllegalAccessException, IllegalArgumentException,
      InvocationTargetException, NoSuchMethodException, SecurityException {

    Data.sents = sents;
    Execution.fillOptions(Data.class, props);
    Execution.fillOptions(constVars, props);
    constVars.answerClass = answerClass;
    // constVars.answerLabels = answerLabel;
    constVars.ignoreWordswithClassesDuringSelection = ignoreClasses;
    constVars.generalizeClasses = generalizeClasses;
    constVars.setLabelDictionary(seedSets);

    constVars.setUp(props);

    // wordsForOtherClass = new HashMap<String, Set<String>>();
    wordsPatExtracted = new HashMap<String, TwoDimensionalCounter<String, SurfacePattern>>();

    for (String label : seedSets.keySet()) {
      // wordsForOtherClass.put(label, new HashSet<String>());
      wordsPatExtracted.put(label,
          new TwoDimensionalCounter<String, SurfacePattern>());
    }
    scorePhrases = new ScorePhrases(props, constVars);
    createPats = new CreatePatterns(props, constVars);
    assert !(constVars.doNotApplyPatterns && (createPats.useStopWordsBeforeTerm || constVars.numWordsCompound > 1)) : " Cannot have both doNotApplyPatterns and (useStopWordsBeforeTerm true or numWordsCompound > 1)!";
    // logFile = new PrintWriter(new FileWriter("patterns_log.txt"));
  }

  public void setChannelName(String str) {
    channelNameLogger = str;
  }

  public void setUp() {

    if (!learnPatternsDebug) {
      Redwood.hideChannelsEverywhere(Redwood.DBG);
    }
    if (!extremedebug) {
      Redwood.hideChannelsEverywhere("extremePatDebug");
    }
    Redwood.log(Redwood.DBG, channelNameLogger, "Running with debug output");
    alreadySetUp = true;

  }

  public static Map<String, List<CoreLabel>> tokenize(String text,
      String posModelPath) throws InterruptedException, ExecutionException,
      IOException {
    Properties props = new Properties();
    props.setProperty("annotators", "tokenize,ssplit,pos,lemma");
    if (posModelPath != null) {
      props.setProperty("pos.model", posModelPath);
    }
    StanfordCoreNLP pipeline = new StanfordCoreNLP(props);
    Annotation doc = new Annotation(text);
    pipeline.annotate(doc);
    Map<String, List<CoreLabel>> sents = new HashMap<String, List<CoreLabel>>();
    int i = -1;
    for (CoreMap s : doc.get(CoreAnnotations.SentencesAnnotation.class)) {
      i++;
      sents.put(Integer.toString(i),
          s.get(CoreAnnotations.TokensAnnotation.class));
    }

    return sents;
  }

  /**
   * If l1 is a part of l2, it finds the starting index of l1 in l2 If l1 is not
   * a sub-array of l2, then it returns -1 note that l2 should have the exact
   * elements and order as in l1
   * 
   * @param l1
   *          array you want to find in l2
   * @param l2
   * @return starting index of the sublist
   */
  public static List<Integer> getSubListIndex(String[] l1, String[] l2,
      String[] subl2, Set<String> englishWords,
      HashSet<String> seenFuzzyMatches, int minLen4Fuzzy) {
    if (l1.length > l2.length)
      return null;
    EditDistance editDistance = new EditDistance(true);
    List<Integer> allIndices = new ArrayList<Integer>();
    boolean matched = false;
    int index = -1;
    int lastUnmatchedIndex = 0;
    for (int i = 0; i < l2.length;) {

      for (int j = 0; j < l1.length;) {
        boolean d1 = false, d2 = false;
        boolean compareFuzzy = true;
        if (englishWords.contains(l2[i]) || englishWords.contains(subl2[i])
            || l2[i].length() <= minLen4Fuzzy
            || subl2[i].length() <= minLen4Fuzzy)
          compareFuzzy = false;
        if (compareFuzzy == false || l1[j].length() <= minLen4Fuzzy) {
          d1 = l1[j].equals(l2[i]) ? true : false;
          if (!d1)
            d2 = subl2[i].equals(l1[j]) ? true : false;
        } else {
          String combo = l1[j] + "#" + l2[i];
          if (l1[j].equals(l2[i]) || seenFuzzyMatches.contains(combo))
            d1 = true;
          else {
            d1 = editDistance.score(l1[j], l2[i]) <= 1;
            if (!d1) {
              String combo2 = l1[j] + "#" + subl2[i];
              if (l1[j].equals(subl2[i]) || seenFuzzyMatches.contains(combo2))
                d2 = true;
              else {
                d2 = editDistance.score(l1[j], subl2[i]) <= 1;
                if (d2) {
                  // System.out.println(l1[j] + " matched with " + subl2[i]);
                  seenFuzzyMatches.add(combo2);
                }
              }
            } else if (d1) {
              // System.out.println(l1[j] + " matched with " + l2[i]);
              seenFuzzyMatches.add(combo);
            }
          }
        }
        // if (l1[j].equals(l2[i]) || subl2[i].equals(l1[j])) {
        if (d1 || d2) {

          index = i;
          i++;
          j++;
          if (j == l1.length) {
            matched = true;
            break;
          }
        } else {
          j = 0;
          i = lastUnmatchedIndex + 1;
          lastUnmatchedIndex = i;
          index = -1;
          if (lastUnmatchedIndex == l2.length)
            break;
        }
        if (i >= l2.length) {
          index = -1;
          break;
        }
      }
      if (i == l2.length || matched) {
        if (index >= 0)
          // index = index - l1.length + 1;
          allIndices.add(index - l1.length + 1);
        matched = false;
        lastUnmatchedIndex = index;

        // break;
      }
    }
    // get starting point

    return allIndices;
  }

  public void runLabelSeedWords(String label, Set<String> seedWords)
      throws InterruptedException, ExecutionException, IOException {

    List<String> keyset = new ArrayList<String>(Data.sents.keySet());

    int num = 0;
    if (constVars.numThreads == 1)
      num = keyset.size();
    else
      num = keyset.size() / (constVars.numThreads - 1);
    ExecutorService executor = Executors
        .newFixedThreadPool(constVars.numThreads);
    Redwood.log(Redwood.FORCE, channelNameLogger,
        "keyset size is " + keyset.size());
    List<Future<Map<String, List<CoreLabel>>>> list = new ArrayList<Future<Map<String, List<CoreLabel>>>>();
    for (int i = 0; i < constVars.numThreads; i++) {
      List<String> keys = keyset.subList(i * num,
          Math.min(keyset.size(), (i + 1) * num));
      Redwood.log(Redwood.FORCE, channelNameLogger, "assigning from " + i * num
          + " till " + Math.min(keyset.size(), (i + 1) * num));

      Callable<Map<String, List<CoreLabel>>> task = new LabelWithSeedWords(
          seedWords, Data.sents, keys, constVars.answerClass.get(label), label);
      Future<Map<String, List<CoreLabel>>> submit = executor.submit(task);
      list.add(submit);
    }

    // // Now retrieve the result

    for (Future<Map<String, List<CoreLabel>>> future : list) {
      Data.sents.putAll(future.get());
    }
    executor.shutdown();
  }

  @SuppressWarnings("rawtypes")
  public class LabelWithSeedWords implements
      Callable<Map<String, List<CoreLabel>>> {
    Set<String[]> seedwordsTokens = new HashSet<String[]>();
    Map<String, List<CoreLabel>> sents;
    List<String> keyset;
    Class labelClass;
    HashSet<String> seenFuzzyMatches = new HashSet<String>();
    String label;

    public LabelWithSeedWords(Set<String> seedwords,
        Map<String, List<CoreLabel>> sents, List<String> keyset,
        Class labelclass, String label) {
      for (String s : seedwords)
        this.seedwordsTokens.add(s.split("\\s+"));
      this.sents = sents;
      this.keyset = keyset;
      this.labelClass = labelclass;
      this.label = label;
    }

    @SuppressWarnings("unchecked")
    @Override
    public Map<String, List<CoreLabel>> call() throws Exception {
      Map<String, List<CoreLabel>> newsent = new HashMap<String, List<CoreLabel>>();
      for (String k : keyset) {
        List<CoreLabel> sent = sents.get(k);
        String[] tokens = new String[sent.size()];
        String[] tokenslemma = new String[sent.size()];
        int num = 0;
        for (CoreLabel l : sent) {
          tokens[num] = l.word();
          if (l.lemma() == null)
            throw new RuntimeException("how come lemma is null");
          tokenslemma[num] = l.lemma();
          num++;
        }
        boolean[] labels = new boolean[tokens.length];
        CollectionValuedMap<Integer, String> matchedPhrases = new CollectionValuedMap<Integer, String>();
        for (String[] s : seedwordsTokens) {
          List<Integer> indices = getSubListIndex(s, tokens, tokenslemma,
              constVars.getEnglishWords(), seenFuzzyMatches,
              constVars.minLen4FuzzyForPattern);
          if (indices != null && !indices.isEmpty())
            for (int index : indices)
              for (int i = 0; i < s.length; i++) {
                matchedPhrases.add(index + i, StringUtils.join(s, " "));
                labels[index + i] = true;
                Redwood
                    .log(
                        Redwood.DBG,
                        channelNameLogger,
                        "labeling " + tokens[index + i] + " or its lemma "
                            + tokenslemma[index + i] + " as " + label
                            + " because of the dict phrase "
                            + StringUtils.join(s, " "));
              }
        }
        int i = -1;
        for (CoreLabel l : sent) {
          i++;
          if (labels[i])
            l.set(labelClass, label);
          else
            l.set(labelClass, constVars.backgroundSymbol);
          l.set(PatternsAnnotations.MatchedPhrases.class,
              (Set<String>) matchedPhrases.get(i));
        }
        newsent.put(k, sent);
      }
      return newsent;
    }
  }

  public static <E> Counter<E> divisionNonNaN(Counter<E> c1, Counter<E> c2) {
    Counter<E> result = c1.getFactory().create();
    for (E key : Sets.union(c1.keySet(), c2.keySet())) {
      if (c2.getCount(key) != 0)
        result.setCount(key, c1.getCount(key) / c2.getCount(key));
    }
    return result;
  }

  public Map<String, TwoDimensionalCounter<SurfacePattern, String>> patternsandWords = null;
  public Map<String, TwoDimensionalCounter<SurfacePattern, String>> allPatternsandWords = null;
  public Map<String, Counter<SurfacePattern>> currentPatternWeights = null;

  @SuppressWarnings({ "unchecked", "rawtypes" })
  public Counter<SurfacePattern> getPatterns(String label,
      Set<SurfacePattern> alreadyIdentifiedPatterns, SurfacePattern p0,
      Counter<String> p0Set, Set<SurfacePattern> ignorePatterns,
      Counter<String> externalWordWeights) throws InterruptedException,
      ExecutionException, IOException, ClassNotFoundException {

    if (!alreadySetUp)
      setUp();
    // TODO: changed
    Counter<String> externalWordWeightsNormalized = null;
    if (externalWordWeights != null)
      externalWordWeightsNormalized = GetPatternsFromDataMultiClass
          .normalizeSoftMaxMinMaxScores(externalWordWeights, true, true, false);
    if (this.patternsForEachToken == null) {
      if (computeAllPatterns) {
        Redwood.log(Redwood.FORCE, channelNameLogger, "Computing all patterns");
        this.patternsForEachToken = createPats
            .getAllPatterns(label, Data.sents);
        // if (removeRedundantPatterns)
        // removeRedundantPatterns(numThreads);
        if (allPatternsFile != null)
          IOUtils.writeObjectToFile(this.patternsForEachToken, allPatternsFile);
      } else {
        this.patternsForEachToken = IOUtils.readObjectFromFile(allPatternsFile);
        Redwood.log(Redwood.FORCE, channelNameLogger, "Read all patterns from "
            + allPatternsFile);
      }
    }
    
    Class answerClass4Label = constVars.answerClass.get(label);
    String answerLabel4Label = label;// constVars.answerLabels.get(label);

    if (patternsandWords == null)
      patternsandWords = new HashMap<String, TwoDimensionalCounter<SurfacePattern, String>>();
    if (allPatternsandWords == null)
      allPatternsandWords = new HashMap<String, TwoDimensionalCounter<SurfacePattern, String>>();
    if (currentPatternWeights == null)
      currentPatternWeights = new HashMap<String, Counter<SurfacePattern>>();
    TwoDimensionalCounter<SurfacePattern, String> patternsandWords4Label = new TwoDimensionalCounter<SurfacePattern, String>();
    TwoDimensionalCounter<SurfacePattern, String> negPatternsandWords4Label = new TwoDimensionalCounter<SurfacePattern, String>();
    TwoDimensionalCounter<SurfacePattern, String> posnegPatternsandWords4Label = new TwoDimensionalCounter<SurfacePattern, String>();
    TwoDimensionalCounter<SurfacePattern, String> unLabeledPatternsandWords4Label = new TwoDimensionalCounter<SurfacePattern, String>();
    TwoDimensionalCounter<SurfacePattern, String> negandUnLabeledPatternsandWords4Label = new TwoDimensionalCounter<SurfacePattern, String>();
    TwoDimensionalCounter<SurfacePattern, String> allPatternsandWords4Label = new TwoDimensionalCounter<SurfacePattern, String>();
    Counter<SurfacePattern> currentPatternWeights4Label = new ClassicCounter<SurfacePattern>();

    for (Entry<String, List<CoreLabel>> sentEn : Data.sents.entrySet()) {
      Map<Integer, Triple<Set<SurfacePattern>, Set<SurfacePattern>, Set<SurfacePattern>>> pat4Sent = this.patternsForEachToken
          .get(sentEn.getKey());
      if (pat4Sent == null) {
        throw new RuntimeException("How come there are no patterns for "
            + sentEn.getKey() + ". The total patternsForEachToken size is "
            + patternsForEachToken.size() + " and keys "
            + patternsForEachToken.keySet());
      }
      List<CoreLabel> sent = sentEn.getValue();
      for (int i = 0; i < sent.size(); i++) {
        CoreLabel token = sent.get(i);
        Set<String> matchedPhrases = token
            .get(PatternsAnnotations.MatchedPhrases.class);

        String tokenWordOrLemma = token.word();
        String longestMatchingPhrase = null;

        if (useMatchingPhrase) {
          if (matchedPhrases != null && !matchedPhrases.isEmpty()) {
            for (String s : matchedPhrases) {
              if (s.equals(tokenWordOrLemma)) {
                longestMatchingPhrase = tokenWordOrLemma;
                break;
              }
              if (longestMatchingPhrase == null
                  || longestMatchingPhrase.length() > s.length()) {
                longestMatchingPhrase = s;
              }
            }
          } else {
            longestMatchingPhrase = tokenWordOrLemma;
          }
        } else
          longestMatchingPhrase = tokenWordOrLemma;

        Triple<Set<SurfacePattern>, Set<SurfacePattern>, Set<SurfacePattern>> pat = pat4Sent
            .get(i);
        if (pat == null)
          throw new RuntimeException("Why are patterns null for sentence "
              + sentEn.getKey() + " and token " + i);
        Set<SurfacePattern> prevPat = pat.first();
        Set<SurfacePattern> nextPat = pat.second();
        Set<SurfacePattern> prevnextPat = pat.third();
        if (constVars.ignoreWordRegex.matcher(token.word()).matches())
          continue;

        if (token.get(answerClass4Label).equals(answerLabel4Label.toString())) {

          boolean prevTokenLabel = i == 0 ? false : sent.get(i - 1)
              .get(answerClass4Label).equals(answerLabel4Label.toString());
          boolean nextTokenLabel = i == sent.size() - 1 ? false : sent
              .get(i + 1).get(answerClass4Label)
              .equals(answerLabel4Label.toString());
          if (!ignorePatWithLabeledNeigh || !prevTokenLabel) {
            for (SurfacePattern s : prevPat) {
              patternsandWords4Label.getCounter(s).incrementCount(
                  longestMatchingPhrase);
              posnegPatternsandWords4Label.getCounter(s).incrementCount(
                  longestMatchingPhrase);
              allPatternsandWords4Label.getCounter(s).incrementCount(
                  longestMatchingPhrase);
            }
          }
          if (!ignorePatWithLabeledNeigh || !nextTokenLabel) {
            for (SurfacePattern s : nextPat) {
              patternsandWords4Label.getCounter(s).incrementCount(
                  longestMatchingPhrase);
              posnegPatternsandWords4Label.getCounter(s).incrementCount(
                  longestMatchingPhrase);
              allPatternsandWords4Label.getCounter(s).incrementCount(
                  longestMatchingPhrase);
            }
          }
          if (!ignorePatWithLabeledNeigh
              || (!prevTokenLabel && !nextTokenLabel)) {
            for (SurfacePattern s : prevnextPat) {

              patternsandWords4Label.getCounter(s).incrementCount(
                  longestMatchingPhrase);
              posnegPatternsandWords4Label.getCounter(s).incrementCount(
                  longestMatchingPhrase);
              allPatternsandWords4Label.getCounter(s).incrementCount(
                  longestMatchingPhrase);
            }
          }
        } else {
          boolean negToken = false;
          Map<Class, Object> ignore = constVars.ignoreWordswithClassesDuringSelection
              .get(label);
          for (Class igCl : ignore.keySet())
            if ((boolean) token.get(igCl)) {
              negToken = true;
              break;
            }
          if (!negToken)
            if (constVars.getOtherSemanticClasses().contains(token.word())
                || constVars.getOtherSemanticClasses().contains(token.lemma()))
              negToken = true;

          for (SurfacePattern s : CollectionUtils.union(
              CollectionUtils.union(prevPat, nextPat), prevnextPat)) {

            if (negToken) {
              negPatternsandWords4Label.getCounter(s).incrementCount(
                  tokenWordOrLemma);
              posnegPatternsandWords4Label.getCounter(s).incrementCount(
                  tokenWordOrLemma);
            } else {
              unLabeledPatternsandWords4Label.getCounter(s).incrementCount(
                  tokenWordOrLemma);
            }
            negandUnLabeledPatternsandWords4Label.getCounter(s).incrementCount(
                tokenWordOrLemma);
            allPatternsandWords4Label.incrementCount(s, tokenWordOrLemma);
          }
        }
      }
    }

    CollectionValuedMap<SurfacePattern, String> posWords = new CollectionValuedMap<SurfacePattern, String>();
    for (Entry<SurfacePattern, ClassicCounter<String>> en : patternsandWords4Label
        .entrySet()) {
      posWords.addAll(en.getKey(), en.getValue().keySet());
    }
    CollectionValuedMap<SurfacePattern, String> negWords = new CollectionValuedMap<SurfacePattern, String>();
    for (Entry<SurfacePattern, ClassicCounter<String>> en : negPatternsandWords4Label
        .entrySet()) {
      negWords.addAll(en.getKey(), en.getValue().keySet());
    }
    CollectionValuedMap<SurfacePattern, String> unlabWords = new CollectionValuedMap<SurfacePattern, String>();
    for (Entry<SurfacePattern, ClassicCounter<String>> en : unLabeledPatternsandWords4Label
        .entrySet()) {
      unlabWords.addAll(en.getKey(), en.getValue().keySet());
    }

    // Baseline; ignore if not interested
    if (patternScoring.equals(PatternScoring.F1)) {

      Counter<SurfacePattern> specificity = new ClassicCounter<SurfacePattern>();
      Counter<SurfacePattern> sensitivity = new ClassicCounter<SurfacePattern>();

      if (p0Set.keySet().size() == 0)
        throw new RuntimeException("how come p0set size is empty for " + p0
            + "?");

      for (Entry<SurfacePattern, ClassicCounter<String>> en : patternsandWords4Label
          .entrySet()) {

        int common = CollectionUtils.intersection(en.getValue().keySet(),
            p0Set.keySet()).size();
        if (common == 0)
          continue;
        if (en.getValue().keySet().size() == 0)
          throw new RuntimeException("how come counter for " + en.getKey()
              + " is empty?");

        specificity.setCount(en.getKey(), common
            / (double) en.getValue().keySet().size());
        sensitivity.setCount(en.getKey(), common / (double) p0Set.size());
      }
      Counters.retainNonZeros(specificity);
      Counters.retainNonZeros(sensitivity);
      Counter<SurfacePattern> add = Counters.add(sensitivity, specificity);
      Counter<SurfacePattern> product = Counters.product(sensitivity,
          specificity);
      Counters.retainNonZeros(product);
      Counters.retainKeys(product, add.keySet());
      Counter<SurfacePattern> finalPat = Counters.scale(
          Counters.division(product, add), 2);
      Counters.removeKeys(finalPat, alreadyIdentifiedPatterns);
      Counters.retainNonZeros(finalPat);
      Counters.retainTop(finalPat, 1);
      if (Double.isNaN(Counters.max(finalPat)))
        throw new RuntimeException("how is the value NaN");
      Redwood.log(Redwood.FORCE, channelNameLogger, "Selected Pattern: "
          + finalPat);
      return finalPat;
    } else if (patternScoring.equals(PatternScoring.PosNegUnlabOdds)
        || patternScoring.equals(PatternScoring.PosNegOdds)
        || patternScoring.equals(PatternScoring.RatioAll)
        || patternScoring.equals(PatternScoring.PhEvalInPat)
        || patternScoring.equals(PatternScoring.PhEvalInPatLogP)
        || patternScoring.equals(PatternScoring.LOGREG)
        || patternScoring.equals(PatternScoring.SqrtAllRatio)) {

      boolean useFreqPhraseExtractedByPat = false;
      if (patternScoring.equals(PatternScoring.SqrtAllRatio))
        useFreqPhraseExtractedByPat = true;

      Counter<SurfacePattern> numeratorPatWt = this.convert2OneDim(label,
          patternsandWords4Label, sqrtPatScore, false, null,
          minPosPhraseSupportForPat, useFreqPhraseExtractedByPat);
      Counter<SurfacePattern> denominatorPatWt = null;

      if (patternScoring.equals(PatternScoring.PosNegUnlabOdds)) {
        // deno = negandUnLabeledPatternsandWords4Label;
        denominatorPatWt = this.convert2OneDim(label,
            negandUnLabeledPatternsandWords4Label, sqrtPatScore, false,
            externalWordWeightsNormalized, minUnlabNegPhraseSupportForPat,
            useFreqPhraseExtractedByPat);
      } else if (patternScoring.equals(PatternScoring.RatioAll)) {
        // deno = allPatternsandWords4Label;
        denominatorPatWt = this.convert2OneDim(label,
            allPatternsandWords4Label, sqrtPatScore, false,
            externalWordWeightsNormalized, minUnlabNegPhraseSupportForPat,
            useFreqPhraseExtractedByPat);
      } else if (patternScoring.equals(PatternScoring.PosNegOdds)) {
        // deno = negPatternsandWords4Label;
        denominatorPatWt = this.convert2OneDim(label,
            negPatternsandWords4Label, sqrtPatScore, false,
            externalWordWeightsNormalized, minUnlabNegPhraseSupportForPat,
            useFreqPhraseExtractedByPat);
      } else if (patternScoring.equals(PatternScoring.PhEvalInPat)
          || patternScoring.equals(PatternScoring.PhEvalInPatLogP)
          || patternScoring.equals(PatternScoring.LOGREG)) {
        // deno = negandUnLabeledPatternsandWords4Label;
        denominatorPatWt = this.convert2OneDim(label,
            negandUnLabeledPatternsandWords4Label, sqrtPatScore, true,
            externalWordWeightsNormalized, minUnlabNegPhraseSupportForPat,
            useFreqPhraseExtractedByPat);
      } else if (patternScoring.equals(PatternScoring.SqrtAllRatio)) {
        // deno = negandUnLabeledPatternsandWords4Label;
        denominatorPatWt = this.convert2OneDim(label,
            negandUnLabeledPatternsandWords4Label, true, false,
            externalWordWeightsNormalized, minUnlabNegPhraseSupportForPat,
            useFreqPhraseExtractedByPat);
      } else
        throw new RuntimeException("Cannot understand patterns scoring");

      currentPatternWeights4Label = divisionNonNaN(numeratorPatWt,
          denominatorPatWt);

      if (patternScoring.equals(PatternScoring.PhEvalInPatLogP)) {
        Counter<SurfacePattern> logpos_i = new ClassicCounter<SurfacePattern>();
        for (Entry<SurfacePattern, ClassicCounter<String>> en : patternsandWords4Label
            .entrySet()) {
          logpos_i.setCount(en.getKey(), Math.log(en.getValue().size()));
        }
        Counters.multiplyInPlace(currentPatternWeights4Label, logpos_i);
      }
      Counters.retainNonZeros(currentPatternWeights4Label);

    } else if (patternScoring.equals(PatternScoring.RlogF)
        || patternScoring.equals(PatternScoring.RlogFPosNeg)
        || patternScoring.equals(PatternScoring.RlogFUnlabNeg)
        || patternScoring.equals(PatternScoring.RlogFNeg)
        || patternScoring.equals(PatternScoring.YanGarber02)
        || patternScoring.equals(PatternScoring.LinICML03)) {

      Counter<SurfacePattern> pos_i = new ClassicCounter<SurfacePattern>();
      Counter<SurfacePattern> all_i = new ClassicCounter<SurfacePattern>();
      Counter<SurfacePattern> neg_i = new ClassicCounter<SurfacePattern>();
      Counter<SurfacePattern> unlab_i = new ClassicCounter<SurfacePattern>();

      for (Entry<SurfacePattern, ClassicCounter<String>> en : negPatternsandWords4Label
          .entrySet()) {
        neg_i.setCount(en.getKey(), en.getValue().size());
      }

      for (Entry<SurfacePattern, ClassicCounter<String>> en : unLabeledPatternsandWords4Label
          .entrySet()) {
        unlab_i.setCount(en.getKey(), en.getValue().size());
      }

      for (Entry<SurfacePattern, ClassicCounter<String>> en : patternsandWords4Label
          .entrySet()) {
        pos_i.setCount(en.getKey(), en.getValue().size());
      }

      for (Entry<SurfacePattern, ClassicCounter<String>> en : allPatternsandWords4Label
          .entrySet()) {
        all_i.setCount(en.getKey(), en.getValue().size());
      }

      Counter<SurfacePattern> posneg_i = Counters.add(pos_i, neg_i);
      Counter<SurfacePattern> logFi = new ClassicCounter<SurfacePattern>(pos_i);
      Counters.logInPlace(logFi);

      if (patternScoring.equals(PatternScoring.RlogF)) {
        // deno = allPatternsandWords4Label;
        currentPatternWeights4Label = Counters.product(
            Counters.division(pos_i, all_i), logFi);
      } else if (patternScoring.equals(PatternScoring.RlogFPosNeg)) {
        System.out.println("computing rlogfposneg");
        // deno = Counters.add(patternsandWords4Label,
        // negPatternsandWords4Label);// posnegPatternsandWords4Label;
        System.out.println("computed deno");
        currentPatternWeights4Label = Counters.product(
            Counters.division(pos_i, posneg_i), logFi);
        System.out.println("computed rlogfposneg");
      } else if (patternScoring.equals(PatternScoring.RlogFUnlabNeg)) {
        System.out.println("computing rlogfunlabeg");
        // deno = Counters.add(negPatternsandWords4Label,
        // unLabeledPatternsandWords4Label);
        System.out.println("computed deno");
        currentPatternWeights4Label = Counters.product(
            Counters.division(pos_i, Counters.add(neg_i, unlab_i)), logFi);
      } else if (patternScoring.equals(PatternScoring.RlogFNeg)) {
        System.out.println("computing rlogfneg");
        // deno = negPatternsandWords4Label;
        currentPatternWeights4Label = Counters.product(
            Counters.division(pos_i, neg_i), logFi);
      } else if (patternScoring.equals(PatternScoring.YanGarber02)) {
        // deno = allPatternsandWords4Label;
        Counter<SurfacePattern> acc = Counters.division(pos_i,
            Counters.add(pos_i, neg_i));
        double thetaPrecision = 0.8;
        Counters.retainAbove(acc, thetaPrecision);
        Counter<SurfacePattern> conf = Counters.product(
            Counters.division(pos_i, all_i), logFi);
        for (SurfacePattern p : acc.keySet()) {
          currentPatternWeights4Label.setCount(p, conf.getCount(p));
        }
      } else if (patternScoring.equals(PatternScoring.LinICML03)) {
        // deno = allPatternsandWords4Label;
        Counter<SurfacePattern> acc = Counters.division(pos_i,
            Counters.add(pos_i, neg_i));
        double thetaPrecision = 0.8;
        Counters.retainAbove(acc, thetaPrecision);
        Counter<SurfacePattern> conf = Counters.product(Counters.division(
            Counters.add(pos_i, Counters.scale(neg_i, -1)), all_i), logFi);
        for (SurfacePattern p : acc.keySet()) {
          currentPatternWeights4Label.setCount(p, conf.getCount(p));
        }
      } else {
        throw new RuntimeException("not implemented");
      }

    } else {
      throw new RuntimeException("not implemented");
    }

    Redwood.log("extremePatDebug", "patterns counter size is "
        + currentPatternWeights4Label.size());

    if (ignorePatterns != null && !ignorePatterns.isEmpty()) {
      Counters.removeKeys(currentPatternWeights4Label, ignorePatterns);
      Redwood.log(
          "extremePatDebug",
          "Removing patterns from ignorePatterns of size  "
              + ignorePatterns.size() + ". New patterns size "
              + currentPatternWeights4Label.size());
    }

    if (alreadyIdentifiedPatterns != null
        && !alreadyIdentifiedPatterns.isEmpty()) {
      Counters.removeKeys(currentPatternWeights4Label,
          alreadyIdentifiedPatterns);
      Redwood.log("extremePatDebug",
          "Removing already identified patterns of size  "
              + alreadyIdentifiedPatterns.size() + ". New patterns size "
              + currentPatternWeights4Label.size());
    }

    PriorityQueue<SurfacePattern> q = Counters
        .toPriorityQueue(currentPatternWeights4Label);
    int num = 0;

    Counter<SurfacePattern> chosenPat = new ClassicCounter<SurfacePattern>();

    Set<SurfacePattern> removePatterns = new HashSet<SurfacePattern>();

    while (num < numPatterns && !q.isEmpty()) {
      SurfacePattern pat = q.removeFirst();
      if (currentPatternWeights4Label.getCount(pat) < thresholdSelectPattern) {
        Redwood.log(
            Redwood.DBG,
            channelNameLogger,
            "The max count in pattern is "
                + currentPatternWeights4Label.getCount(pat)
                + " so not adding anymore patterns");
        break;
      }
      boolean notchoose = false;
      if (discardPatternsWithNoUnlabSupport
          && (unLabeledPatternsandWords4Label.containsFirstKey(pat) || unLabeledPatternsandWords4Label
              .getCounter(pat).isEmpty())) {
        Redwood.log("extremePatDebug", "Removing pattern " + pat
            + " because it has no unlab support; pos words: "
            + patternsandWords4Label.getCounter(pat) + " and all words "
            + allPatternsandWords4Label.getCounter(pat));
        notchoose = true;
        continue;
      }

      SurfacePattern removeIdentifiedPattern = null, removeChosenPat = null;

      if (!notchoose) {
        if (alreadyIdentifiedPatterns != null) {
          for (SurfacePattern p : alreadyIdentifiedPatterns) {

            if (pat.nextContext.contains(p.nextContext)
                && pat.prevContext.contains(p.prevContext)) {
              Redwood
                  .log(
                      "extremePatDebug",
                      "Removing pattern "
                          + pat
                          + " because it is contained in or contains the already chosen pattern "
                          + p);
              notchoose = true;
              break;
            }
            int rest = pat.equalContext(p);
            // the contexts dont match
            if (rest == Integer.MAX_VALUE)
              continue;
            // if pat is less restrictive, remove p and add pat!
            if (rest < 0) {
              removeIdentifiedPattern = p;
            } else {
              notchoose = true;
              break;
            }
          }
        }
      }

      if (!notchoose) {
        for (SurfacePattern p : chosenPat.keySet()) {

          if (pat.nextContext.contains(p.nextContext)
              && pat.prevContext.contains(p.prevContext)) {
            Redwood
                .log(
                    "extremePatDebug",
                    "Removing pattern "
                        + pat
                        + " because it is contained in or contains the already chosen pattern "
                        + p);
            notchoose = true;
            break;
          }
          int rest = pat.equalContext(p);
          // the contexts dont match
          if (rest == Integer.MAX_VALUE)
            continue;
          // if pat is less restrictive, remove p from chosen patterns and add
          // pat!
          if (rest < 0) {
            removeChosenPat = p;
            num--;
          } else {
            removeIdentifiedPattern = null;
            notchoose = true;
            break;
          }

        }
      }
      if (notchoose)
        continue;
      if (removeChosenPat != null) {
        Redwood.log("extremePatDebug",
            "Removing already chosen pattern in this iteration "
                + removeChosenPat + " in favor of " + pat);
        chosenPat.remove(removeChosenPat);
      }
      if (removeIdentifiedPattern != null) {
        Redwood.log("extremePatDebug", "Removing already identified pattern "
            + removeChosenPat + " in favor of " + pat);
        removePatterns.add(removeIdentifiedPattern);
      }
      chosenPat.setCount(pat, currentPatternWeights4Label.getCount(pat));
      num++;
    }

    this.removeLearnedPatterns(label, removePatterns);

    Redwood.log(Redwood.DBG, channelNameLogger,
        "final size of the patterns is " + chosenPat.size());
    Redwood.log(Redwood.FORCE, channelNameLogger, "## Selected Patterns ## \n");
    List<Pair<SurfacePattern, Double>> chosenPatSorted = Counters
        .toSortedListWithCounts(chosenPat);
    for (Pair<SurfacePattern, Double> en : chosenPatSorted)
      Redwood.log(Redwood.FORCE, channelNameLogger, en.first()
          .toStringToWrite() + ":" + df.format(en.second) + "\n");

    // if (deno != null) {
    if (justificationDirJson != null && !justificationDirJson.isEmpty()) {
      IOUtils.ensureDir(new File(justificationDirJson + "/" + label));

      String filename = this.justificationDirJson + "/" + label + "/"
          + identifier + "_patterns" + ".json";

      JsonArrayBuilder obj = Json.createArrayBuilder();
      if (writtenPatInJustification.containsKey(label)
          && writtenPatInJustification.get(label)) {
        JsonReader jsonReader = Json.createReader(new BufferedInputStream(
            new FileInputStream(filename)));
        JsonArray objarr = jsonReader.readArray();
        jsonReader.close();
        for (JsonValue o : objarr)
          obj.add(o);
      } else
        obj = Json.createArrayBuilder();

      JsonObjectBuilder objThisIter = Json.createObjectBuilder();
      for (Pair<SurfacePattern, Double> pat : chosenPatSorted) {
        JsonObjectBuilder o = Json.createObjectBuilder();
        JsonArrayBuilder pos = Json.createArrayBuilder();
        JsonArrayBuilder neg = Json.createArrayBuilder();
        JsonArrayBuilder unlab = Json.createArrayBuilder();

        for (String w : posWords.get(pat.first()))
          pos.add(w);
        for (String w : negWords.get(pat.first()))
          neg.add(w);
        for (String w : unlabWords.get(pat.first()))
          unlab.add(w);
        o.add("Positive", pos);
        o.add("Negative", neg);
        o.add("Unlabeled", unlab);
        o.add("Score", pat.second());
        objThisIter.add(pat.first().toStringSimple(), o);
      }
      obj.add(objThisIter.build());
      // Redwood.log(Redwood.FORCE, channelNameLogger,
      // "Writing justification at " + filename);
      IOUtils.ensureDir(new File(filename).getParentFile());
      IOUtils.writeStringToFile(obj.build().toString(), filename, "utf8");
      writtenPatInJustification.put(label, true);
    }
    // }
    if (justify) {
      Redwood
          .log(Redwood.DBG, channelNameLogger, "Justification for Patterns:");
      for (SurfacePattern key : chosenPat.keySet()) {
        Redwood.log(Redwood.DBG, channelNameLogger,
            "Pattern: " + key.toStringToWrite());
        Redwood.log(
            Redwood.DBG,
            channelNameLogger,
            "Positive Words:"
                + Counters.toSortedString(
                    patternsandWords4Label.getCounter(key),
                    patternsandWords4Label.getCounter(key).size(), "%1$s:%2$f",
                    ";"));
        Redwood.log(
            Redwood.DBG,
            channelNameLogger,
            "Negative words: "
                + Counters.toSortedString(
                    allPatternsandWords4Label.getCounter(key),
                    allPatternsandWords4Label.getCounter(key).size(),
                    "%1$s:%2$f", ";"));
        // Redwood.log(Redwood.DBG, channelNameLogger, "Freq in patterns " +
        // numeratorPatWt.getCount(key));
        // Redwood.log(Redwood.DBG, channelNameLogger, "Freq in negPatterns " +
        // denominatorPatWt.getCount(key));
      }
    }
    allPatternsandWords.put(label, allPatternsandWords4Label);
    patternsandWords.put(label, patternsandWords4Label);
    currentPatternWeights.put(label, currentPatternWeights4Label);

    return chosenPat;

  }

  void removeLearnedPattern(String label, SurfacePattern p) {
    this.learnedPatterns.get(label).remove(p);
    if (wordsPatExtracted.containsKey(label))
      for (Entry<String, ClassicCounter<SurfacePattern>> en : this.wordsPatExtracted
          .get(label).entrySet()) {
        en.getValue().remove(p);
      }
  }

  void removeLearnedPatterns(String label, Collection<SurfacePattern> pats) {
    Counters.removeKeys(this.learnedPatterns.get(label), pats);
    if (wordsPatExtracted.containsKey(label))
      for (Entry<String, ClassicCounter<SurfacePattern>> en : this.wordsPatExtracted
          .get(label).entrySet()) {
        Counters.removeKeys(en.getValue(), pats);
      }
  }

  public static Counter<String> normalizeSoftMaxMinMaxScores(
      Counter<String> scores, boolean minMaxNorm, boolean softmax,
      boolean oneMinusSoftMax) {
    double minScore = Double.MAX_VALUE, maxScore = Double.MIN_VALUE;
    Counter<String> newscores = new ClassicCounter<String>();
    if (softmax) {
      for (Entry<String, Double> en : scores.entrySet()) {
        Double score = null;
        if (oneMinusSoftMax)
          score = (1 / (1 + Math.exp(Math.min(7, en.getValue()))));
        else
          score = (1 / (1 + Math.exp(-1 * Math.min(7, en.getValue()))));
        if (score < minScore)
          minScore = score;
        if (score > maxScore)
          maxScore = score;
        newscores.setCount(en.getKey(), score);
      }
    } else {
      newscores.addAll(scores);
      minScore = Counters.min(newscores);
      maxScore = Counters.max(newscores);
    }

    // System.out.println("max and min scores are " + maxScore + " and " +
    // minScore);
    if (minMaxNorm) {
      for (Entry<String, Double> en : newscores.entrySet()) {
        double score;
        if (minScore == maxScore)
          score = minScore;
        else
          score = (en.getValue() - minScore + 1e-10) / (maxScore - minScore);
        newscores.setCount(en.getKey(), score);
      }
    }
    return newscores;
  }

  public TwoDimensionalCounter<String, ScorePhraseMeasures> phInPatScores = new TwoDimensionalCounter<String, ScorePhraseMeasures>();

  Counter<SurfacePattern> convert2OneDim(String label,
      TwoDimensionalCounter<SurfacePattern, String> patternsandWords,
      boolean sqrtPatScore, boolean scorePhrasesInPatSelection,
      Counter<String> dictOddsWordWeights, Integer minPhraseSupport,
      boolean useFreqPhraseExtractedByPat) throws IOException {

    if (Data.googleNGram.size() == 0 && Data.googleNGramsFile != null) {
      Data.loadGoogleNGrams();
    }
    Data.computeRawFreqIfNull(numWordsCompound);

    Counter<SurfacePattern> patterns = new ClassicCounter<SurfacePattern>();

    Counter<String> googleNgramNormScores = new ClassicCounter<String>();
    Counter<String> domainNgramNormScores = new ClassicCounter<String>();

    Counter<String> externalFeatWtsNormalized = new ClassicCounter<String>();
    Counter<String> editDistanceFromOtherSemanticBinaryScores = new ClassicCounter<String>();
    Counter<String> editDistanceFromAlreadyExtractedBinaryScores = new ClassicCounter<String>();
    double externalWtsDefault = 0.5;

    if ((patternScoring.equals(PatternScoring.PhEvalInPat) || patternScoring
        .equals(PatternScoring.PhEvalInPatLogP)) && scorePhrasesInPatSelection) {
      Set<String> allPhrasesInQuestion = new HashSet<String>();
      for (Entry<SurfacePattern, ClassicCounter<String>> d : patternsandWords
          .entrySet()) {
        allPhrasesInQuestion.addAll(d.getValue().keySet());
      }
      for (String g : allPhrasesInQuestion) {
        if (constVars.usePatternEvalEditDistOther) {

          editDistanceFromOtherSemanticBinaryScores.setCount(g,
              constVars.getEditDistanceScoresOtherClassThreshold(g));
        }
        if (constVars.usePatternEvalEditDistSame) {
          editDistanceFromAlreadyExtractedBinaryScores.setCount(g,
              1 - constVars.getEditDistanceScoresThisClassThreshold(label, g));
        }

        if (constVars.usePatternEvalGoogleNgram) {
          if (Data.googleNGram.containsKey(g)) {
            assert (Data.rawFreq.containsKey(g));
            googleNgramNormScores
                .setCount(
                    g,
                    ((1 + Data.rawFreq.getCount(g)
                        * Math.sqrt(Data.ratioGoogleNgramFreqWithDataFreq)) / Data.googleNGram
                        .getCount(g)));
          }
        }
        if (constVars.usePatternEvalDomainNgram) {
          // calculate domain-ngram wts
          if (Data.domainNGramRawFreq.containsKey(g)) {
            assert (Data.rawFreq.containsKey(g));
            domainNgramNormScores.setCount(g,
                scorePhrases.phraseScorer.getDomainNgramScore(g));
          }
        }

        if (constVars.usePatternEvalWordClass) {
          Integer num = constVars.getWordClassClusters().get(g);
          if (num != null && constVars.distSimWeights.containsKey(num)) {
            externalFeatWtsNormalized.setCount(g,
                constVars.distSimWeights.getCount(num));
          } else
            externalFeatWtsNormalized.setCount(g, externalWtsDefault);
        }
      }
      // TODO : changed
      if (constVars.usePatternEvalGoogleNgram)
        googleNgramNormScores = GetPatternsFromDataMultiClass
            .normalizeSoftMaxMinMaxScores(googleNgramNormScores, true, true,
                false);
      if (constVars.usePatternEvalDomainNgram)
        domainNgramNormScores = GetPatternsFromDataMultiClass
            .normalizeSoftMaxMinMaxScores(domainNgramNormScores, true, true,
                false);
      if (constVars.usePatternEvalWordClass)
        externalFeatWtsNormalized = GetPatternsFromDataMultiClass
            .normalizeSoftMaxMinMaxScores(externalFeatWtsNormalized, true,
                true, false);
    }

    else if (patternScoring.equals(PatternScoring.LOGREG)
        && scorePhrasesInPatSelection) {
      // classifier = scorePhrases.learnClassifier(Data.sents, label, true,
      // constVars.perSelectRand, constVars.perSelectNeg, null, null,
      // dictOddsWordWeights, constVars.wekaOptions);
      throw new RuntimeException("Not implemented currently");
    }

    Counter<String> cachedScoresForThisIter = new ClassicCounter<String>();

    for (Entry<SurfacePattern, ClassicCounter<String>> d : patternsandWords
        .entrySet()) {

      if (minPhraseSupport != null && minPhraseSupport > 1) {
        if (d.getValue().size() < minPhraseSupport)
          continue;
      }

      for (Entry<String, Double> e : d.getValue().entrySet()) {
        String word = e.getKey();
        Counter<ScorePhraseMeasures> scoreslist = new ClassicCounter<ScorePhraseMeasures>();
        double score = 1;
        if ((patternScoring.equals(PatternScoring.PhEvalInPat) || patternScoring
            .equals(PatternScoring.PhEvalInPatLogP))
            && scorePhrasesInPatSelection) {
          if (cachedScoresForThisIter.containsKey(word)) {
            score = cachedScoresForThisIter.getCount(word);
          } else {
            if (constVars.getOtherSemanticClasses().contains(word)
                || constVars.getCommonEngWords().contains(word))
              score = 1;
            else {

              if (constVars.usePatternEvalSemanticOdds) {
                double semanticClassOdds = 1;
                if (dictOddsWordWeights.containsKey(word))
                  semanticClassOdds = 1 - dictOddsWordWeights.getCount(word);
                scoreslist.setCount(ScorePhraseMeasures.SEMANTICODDS,
                    semanticClassOdds);
              }

              if (constVars.usePatternEvalGoogleNgram) {
                double gscore = 0;
                if (googleNgramNormScores.containsKey(word)) {
                  gscore = 1 - googleNgramNormScores.getCount(word);
                }
                scoreslist.setCount(ScorePhraseMeasures.GOOGLENGRAM, gscore);
              }

              if (constVars.usePatternEvalDomainNgram) {
                double domainscore;
                if (domainNgramNormScores.containsKey(word)) {
                  domainscore = 1 - domainNgramNormScores.getCount(word);
                } else
                  domainscore = 1 - scorePhrases.phraseScorer
                      .getPhraseWeightFromWords(domainNgramNormScores, word,
                          scorePhrases.phraseScorer.OOVDomainNgramScore);
                scoreslist.setCount(ScorePhraseMeasures.DOMAINNGRAM,
                    domainscore);
              }
              if (constVars.usePatternEvalWordClass) {
                double externalFeatureWt = externalWtsDefault;
                if (externalFeatWtsNormalized.containsKey(e.getKey()))
                  externalFeatureWt = 1 - externalFeatWtsNormalized.getCount(e
                      .getKey());
                scoreslist.setCount(ScorePhraseMeasures.DISTSIM,
                    externalFeatureWt);
              }

              if (constVars.usePatternEvalEditDistOther) {
                assert editDistanceFromOtherSemanticBinaryScores.containsKey(e
                    .getKey()) : "How come no edit distance info?";
                scoreslist.setCount(ScorePhraseMeasures.EDITDISTOTHER,
                    editDistanceFromOtherSemanticBinaryScores.getCount(e
                        .getKey()));
              }
              if (constVars.usePatternEvalEditDistSame) {
                scoreslist.setCount(ScorePhraseMeasures.EDITDISTSAME,
                    editDistanceFromAlreadyExtractedBinaryScores.getCount(e
                        .getKey()));
              }

              // taking average
              score = Counters.mean(scoreslist);

              phInPatScores.setCounter(e.getKey(), scoreslist);
            }

            cachedScoresForThisIter.setCount(e.getKey(), score);
          }
        } else if (patternScoring.equals(PatternScoring.LOGREG)
            && scorePhrasesInPatSelection) {
          // score = 1 - scorePhrases.scoreUsingClassifer(classifier,
          // e.getKey(), label, true, null, null, dictOddsWordWeights);
          throw new RuntimeException("not implemented yet");
        }
        if (useFreqPhraseExtractedByPat)
          score = score * e.getValue();
        if (sqrtPatScore)
          patterns.incrementCount(d.getKey(), Math.sqrt(score));
        else
          patterns.incrementCount(d.getKey(), score);

      }
    }

    return patterns;
  }

  // TODO: this right now doesn't work for matchPatterns because of
  // DictAnnotationDTorSC. we are not setting DT, SC thing in the test sentences
  @SuppressWarnings({ "unchecked" })
  public void labelWords(String label, Map<String, List<CoreLabel>> sents,
      Set<String> identifiedWords, Set<SurfacePattern> patterns,
      String outFile, CollectionValuedMap<String, Integer> tokensMatchedPatterns)
      throws IOException {

    for (Entry<String, List<CoreLabel>> sentEn : sents.entrySet()) {

      Set<String[]> identifiedWordsTokens = new HashSet<String[]>();
      for (String s : identifiedWords) {
        String[] toks = s.split("\\s+");
        identifiedWordsTokens.add(toks);
      }
      String[] sent = new String[sentEn.getValue().size()];
      int i = 0;

      Set<Integer> contextWordsRecalculatePats = new HashSet<Integer>();

      for (CoreLabel l : sentEn.getValue()) {
        sent[i] = l.word();
        i++;
      }
      for (String[] ph : identifiedWordsTokens) {
        List<Integer> ints = ArrayUtils.getSubListIndex(ph, sent);
        if (ints == null)
          continue;

        for (Integer idx : ints) {
          boolean donotuse = false;
          if (restrictToMatched) {
            for (int j = 0; j < ph.length; j++) {
              if (!tokensMatchedPatterns.get(sentEn.getKey()).contains(idx + j)) {
                Redwood.log(Redwood.DBG, "not labeling "
                    + sentEn.getValue().get(idx + j).word());
                donotuse = true;
                break;
              }
            }
          }
          if (donotuse == false) {
            for (int j = 0; j < ph.length; j++) {
              int index = idx + j;
              CoreLabel l = sentEn.getValue().get(index);
              if (usePatternResultAsLabel) {
                l.set(constVars.answerClass.get(label), label);
                Set<String> matched = new HashSet<String>();
                matched.add(StringUtils.join(ph, " "));
                l.set(PatternsAnnotations.MatchedPhrases.class, matched);
                for (int k = Math.max(0, index - this.numWordsCompound); k < sentEn
                    .getValue().size()
                    && k <= index + this.numWordsCompound + 1; k++) {
                  contextWordsRecalculatePats.add(k);
                }

              }
            }
          }
        }
      }

      if (patternsForEachToken.containsKey(sentEn.getKey())) {
        for (int index : contextWordsRecalculatePats)
          this.patternsForEachToken.get(sentEn.getKey()).put(index,
              createPats.getContext(label, sentEn.getValue(), index));
      }
    }

    if (outFile != null) {
      Redwood.log(Redwood.FORCE, channelNameLogger, "Writing results to "
          + outFile);
      IOUtils.writeObjectToFile(sents, outFile);
    }
  }

  public void iterateExtractApply(Map<String, SurfacePattern> p0,
      Map<String, Counter<String>> p0Set,
      Map<String, Counter<String>> externalWordWeights, String wordsOutputFile,
      String sentsOutFile, String patternsOutFile,
      Map<String, Set<SurfacePattern>> ignorePatterns, String feedbackfile)
      throws ClassNotFoundException, IOException, InterruptedException,
      ExecutionException {

    Map<String, Set<String>> ignoreWordsAll = new HashMap<String, Set<String>>();
    if (this.useOtherLabelsWordsasNegative) {
      for (String label : constVars.getLabelDictionary().keySet()) {
        Set<String> w = new HashSet<String>();
        for (Entry<String, Set<String>> en : constVars.getLabelDictionary()
            .entrySet()) {
          if (en.getKey().equals(label))
            continue;
          w.addAll(en.getValue());
        }
        ignoreWordsAll.put(label, w);
      }
    }

    Redwood.log(Redwood.FORCE, "Iterating " + numIterationsForPatterns
        + " times.");
    for (int i = 0; i < numIterationsForPatterns; i++) {
      Redwood.log(Redwood.FORCE, channelNameLogger,
          "\n\n################################ Iteration " + (i + 1)
              + " ##############################");
      Map<String, Counter<String>> learnedWordsThisIter = new HashMap<String, Counter<String>>();
      for (String label : constVars.getLabelDictionary().keySet()) {
        Redwood.log(Redwood.FORCE, channelNameLogger,
            "\n###Learning for label " + label + " ######");
        String wordsout = wordsOutputFile == null ? null : wordsOutputFile
            + "_" + label;
        String sentout = sentsOutFile == null ? null : sentsOutFile + "_"
            + label;
        String patout = patternsOutFile == null ? null : patternsOutFile + "_"
            + label;
        Counter<String> learnedWords4label = iterateExtractApply4Label(
            label,
            p0 != null ? p0.get(label) : null,
            p0Set != null ? p0Set.get(label) : null,
            externalWordWeights != null ? externalWordWeights.get(label) : null,
            wordsout, sentout, patout,
            ignorePatterns != null ? ignorePatterns.get(label) : null,
            feedbackfile, 1, ignoreWordsAll.get(label));
        learnedWordsThisIter.put(label, learnedWords4label);
      }

      if (this.useOtherLabelsWordsasNegative) {
        for (String label : constVars.getLabelDictionary().keySet()) {
          for (Entry<String, Counter<String>> en : learnedWordsThisIter
              .entrySet()) {
            if (en.getKey().equals(label))
              continue;
            ignoreWordsAll.get(label).addAll(en.getValue().keySet());
          }
        }
      }
    }

    System.out.println("\n\nAll words learned:");
    for (Entry<String, Counter<String>> en : this.learnedWords.entrySet()) {
      System.out.println(en.getKey() + ":\t\t" + en.getValue().keySet()
          + "\n\n");
    }

  }

  public Counter<String> iterateExtractApply4Label(String label,
      SurfacePattern p0, Counter<String> p0Set,
      Counter<String> dictOddsWordWeights, String wordsOutputFile,
      String sentsOutFile, String patternsOutFile,
      Set<SurfacePattern> ignorePatterns, String feedbackfile, int numIter,
      Set<String> ignoreWords) throws IOException, InterruptedException,
      ExecutionException, ClassNotFoundException {

    if (!learnedPatterns.containsKey(label)) {
      learnedPatterns.put(label, new ClassicCounter<SurfacePattern>());
    }
    if (!learnedWords.containsKey(label)) {
      learnedWords.put(label, new ClassicCounter<String>());
    }

    CollectionValuedMap<String, Integer> tokensMatchedPatterns = new CollectionValuedMap<String, Integer>();

    BufferedWriter wordsOutput = null;
    if (wordsOutputFile != null)
      wordsOutput = new BufferedWriter(new FileWriter(wordsOutputFile));
    TwoDimensionalCounter<String, SurfacePattern> terms = new TwoDimensionalCounter<String, SurfacePattern>();

    Counter<String> identifiedWords = new ClassicCounter<String>();
    for (int i = 0; i < numIter; i++) {

      Counter<SurfacePattern> patterns = getPatterns(label, learnedPatterns
          .get(label).keySet(), p0, p0Set, ignorePatterns, dictOddsWordWeights);
      learnedPatterns.get(label).addAll(patterns);

      if (sentsOutFile != null)
        sentsOutFile = sentsOutFile + "_" + i + "iter.ser";

      Counter<String> scoreForAllWordsThisIteration = new ClassicCounter<String>();
      identifiedWords.addAll(scorePhrases.learnNewPhrases(label, Data.sents,
          this.patternsForEachToken, patterns, learnedPatterns.get(label),
          dictOddsWordWeights, tokensMatchedPatterns,
          scoreForAllWordsThisIteration, terms, wordsPatExtracted.get(label),
          currentPatternWeights.get(label), this.patternsandWords.get(label),
          this.allPatternsandWords.get(label), identifier, ignoreWords));

      if (usePatternResultAsLabel)
        if (constVars.getLabelDictionary().containsKey(label))
          labelWords(label, Data.sents, identifiedWords.keySet(),
              patterns.keySet(), sentsOutFile, tokensMatchedPatterns);
        else
          throw new RuntimeException("why is the answer label null?");
      learnedWords.get(label).addAll(identifiedWords);

      if (wordsOutput != null) {
        if (i > 0)
          wordsOutput.write(",");
        wordsOutput.write("'#Iteration " + (i + 1) + ",");
        wordsOutput.write(Counters.toSortedString(identifiedWords,
            identifiedWords.size(), "'%1$s'", ","));
        wordsOutput.flush();
      }

      if (patterns.size() == 0 && identifiedWords.size() == 0) {
        if (learnedWords.get(label).size() >= maxExtractNumWords) {
          System.out
              .println("Ending because no new words identified and total words learned till now >= max words "
                  + maxExtractNumWords);
          break;
        }
        if (tuneThresholdKeepRunning) {
          thresholdSelectPattern = 0.8 * thresholdSelectPattern;
          System.out
              .println("\n\nTuning thresholds to keep running. New Pattern threshold is  "
                  + thresholdSelectPattern);
        } else
          break;
      }
    }
    if (wordsOutput != null)
      wordsOutput.close();
    if (patternsOutFile != null)
      this.writePatternsToFile(learnedPatterns.get(label), patternsOutFile);

    return identifiedWords;
  }

  void writePatternsToFile(Counter<SurfacePattern> pattern, String outFile)
      throws IOException {
    JsonObjectBuilder obj = Json.createObjectBuilder();
    IOUtils.ensureDir(new File(outFile).getParentFile());
    for (Entry<SurfacePattern, Double> en : pattern.entrySet())
      obj.add(en.getKey().toString(), en.getValue());
    IOUtils.writeStringToFile(obj.build().toString(), outFile, "UTF-8");

  }

  // Counter<String> readPatternsFromFile(String file) throws
  // JsonSyntaxException, JsonIOException, FileNotFoundException {
  // Gson gson = new GsonBuilder().create();
  // Type typeOfT = new TypeToken<Map<String, Double>>() {
  // }.getType();
  //
  // Counter<String> patterns = new ClassicCounter<String>();
  // patterns.addAll(Counters.fromMap((Map<String, Double>) gson.fromJson(new
  // BufferedReader(new FileReader(file)), typeOfT)));
  // return patterns;
  // }

  public Counter<String> getLearnedWords(String label) {
    return this.learnedWords.get(label);
  }

  public Counter<SurfacePattern> getLearnedPatterns(String label) {
    return this.learnedPatterns.get(label);
  }

  // public Map<String, List<CoreLabel>> loadJavaNLPAnnotatorLabeledFile(String
  // labeledFile, Properties props) throws FileNotFoundException {
  // System.out.println("Loading evaluate file " + labeledFile);
  // Map<String, List<CoreLabel>> sents = new HashMap<String,
  // List<CoreLabel>>();
  // JavaNLPAnnotatorReaderAndWriter j = new JavaNLPAnnotatorReaderAndWriter();
  // j.init(props);
  // Iterator<List<CoreLabel>> iter = j.getIterator(new BufferedReader(new
  // FileReader(labeledFile)));
  // int i = 0;
  // while (iter.hasNext()) {
  // i++;
  // List<CoreLabel> s = iter.next();
  // String id = s.get(0).get(CoreAnnotations.DocIDAnnotation.class);
  // if (id == null) {
  // id = Integer.toString(i);
  // }
  // sents.put(id, s);
  // }
  // System.out.println("Read " + sents.size() + " eval sentences");
  // return sents;
  // }

  // private void evaluate(String label, Map<String, List<CoreLabel>> sents)
  // throws IOException, InterruptedException, ExecutionException {
  // Redwood.log(Redwood.DBG, "labeling " + learnedWords.get(label));
  // CollectionValuedMap<String, Integer> tokensMatchedPatterns = new
  // CollectionValuedMap<String, Integer>();
  //
  // if (restrictToMatched) {
  // if (!alreadySetUp)
  // setUp();
  // List<String> keyset = new ArrayList<String>(sents.keySet());
  // int num = 0;
  // if (constVars.numThreads == 1)
  // num = keyset.size();
  // else
  // num = keyset.size() / (constVars.numThreads - 1);
  // ExecutorService executor = Executors
  // .newFixedThreadPool(constVars.numThreads);
  // // Redwood.log(Redwood.FORCE, channelNameLogger, "keyset size is " +
  // // keyset.size());
  // List<Future<Pair<TwoDimensionalCounter<Pair<String, String>,
  // SurfacePattern>, CollectionValuedMap<String, Integer>>>> list = new
  // ArrayList<Future<Pair<TwoDimensionalCounter<Pair<String, String>,
  // SurfacePattern>, CollectionValuedMap<String, Integer>>>>();
  // for (int i = 0; i < constVars.numThreads; i++) {
  // // Redwood.log(Redwood.FORCE, channelNameLogger, "assigning from " + i *
  // // num + " till " + Math.min(keyset.size(), (i + 1) * num));
  //
  // Callable<Pair<TwoDimensionalCounter<Pair<String, String>, SurfacePattern>,
  // CollectionValuedMap<String, Integer>>> task = null;
  // task = new ApplyPatterns(keyset.subList(i * num,
  // Math.min(keyset.size(), (i + 1) * num)),
  // this.learnedPatterns.get(label), constVars.commonEngWords,
  // usePatternResultAsLabel, this.learnedWords.get(label).keySet(),
  // restrictToMatched, label,
  // constVars.removeStopWordsFromSelectedPhrases,
  // constVars.removePhrasesWithStopWords, constVars);
  // Future<Pair<TwoDimensionalCounter<Pair<String, String>, SurfacePattern>,
  // CollectionValuedMap<String, Integer>>> submit = executor
  // .submit(task);
  // list.add(submit);
  // }
  // for (Future<Pair<TwoDimensionalCounter<Pair<String, String>,
  // SurfacePattern>, CollectionValuedMap<String, Integer>>> future : list) {
  // Pair<TwoDimensionalCounter<Pair<String, String>, SurfacePattern>,
  // CollectionValuedMap<String, Integer>> res = future
  // .get();
  // tokensMatchedPatterns.addAll(res.second());
  // }
  // executor.shutdown();
  // }
  //
  // this.labelWords(label, sents, this.learnedWords.get(label).keySet(),
  // this.learnedPatterns.get(label).keySet(), null, tokensMatchedPatterns);
  // Counter<String> entityTP = new ClassicCounter<String>();
  // Counter<String> entityFP = new ClassicCounter<String>();
  // Counter<String> entityFN = new ClassicCounter<String>();
  // for (Entry<String, List<CoreLabel>> sent : sents.entrySet()) {
  // for (CoreLabel l : sent.getValue()) {
  // if (l.containsKey(constVars.answerClass.get(label))
  // && l.get(constVars.answerClass.get(label)) != null)
  // l.set(CoreAnnotations.AnswerAnnotation.class,
  // l.get(constVars.answerClass.get(label)).toString());
  // if (!l.containsKey(CoreAnnotations.AnswerAnnotation.class)
  // || l.get(CoreAnnotations.AnswerAnnotation.class) == null) {
  // l.set(CoreAnnotations.AnswerAnnotation.class,
  // SeqClassifierFlags.DEFAULT_BACKGROUND_SYMBOL);
  //
  // }
  //
  // }
  // CRFClassifier.countResults(sent.getValue(), entityTP, entityFP, entityFN,
  // SeqClassifierFlags.DEFAULT_BACKGROUND_SYMBOL);
  // }
  //
  // Counter<String> precision = Counters.division(entityTP,
  // Counters.add(entityTP, entityFP));
  // Counter<String> recall = Counters.division(entityTP,
  // Counters.add(entityTP, entityFN));
  // Counter<String> fscore = Counters.getFCounter(precision, recall, 1.0);
  // System.out.println("Precision: " + precision);
  // System.out.println("Recall: " + recall);
  // System.out.println("FScore: " + fscore);
  // }

  @SuppressWarnings({ "rawtypes" })
  public static void main(String[] args) {
    try {

      Properties props = StringUtils.argsToPropertiesWithResolve(args);
      boolean readFromSavedInstance = Boolean.parseBoolean(props
          .getProperty("readFromSavedInstance"));
      String inputSavedInstanceFile = props
          .getProperty("inputSavedInstanceFile");
      boolean saveInstance = Boolean.parseBoolean(props
          .getProperty("saveInstance"));
      String outputSavedInstanceFile = props
          .getProperty("outputSavedInstanceFile");

      GetPatternsFromDataMultiClass g = null;
      String patternOutFile = props.getProperty("patternOutFile");
      // TODO: are we not using this?
      // Map<String, Set<String>> ignoreWordsList = new HashMap<String,
      // Set<String>>();
      // Set<String> ignoreWordsList4Label = new HashSet<String>();
      String sentsOutFile = props.getProperty("sentsOutFile");
      String wordsOutputFile = props.getProperty("wordsOutputFile");
      Map<String, Set<SurfacePattern>> ignorePatterns = new HashMap<String, Set<SurfacePattern>>();
      Map<String, SurfacePattern> p0 = new HashMap<String, SurfacePattern>();
      Map<String, Counter<String>> p0Set = new HashMap<String, Counter<String>>();
      Map<String, Counter<String>> externalWordWeights = new HashMap<String, Counter<String>>();

      if (inputSavedInstanceFile == null
          || !new File(inputSavedInstanceFile).exists()) {
        readFromSavedInstance = false;
      }
      Map<String, Set<String>> seedWords = new HashMap<String, Set<String>>();

      if (readFromSavedInstance) {
        System.out.println("Reading the GetPatternsFromData instance from "
            + inputSavedInstanceFile);
        g = IOUtils.readObjectFromFile(inputSavedInstanceFile);
      } else {
        String seedWordsFiles = props.getProperty("seedWordsFiles");
        if (seedWordsFiles == null) {
          throw new RuntimeException(
              "Needs both seedWordsFiles and file parameters to run this class!\nseedWordsFiles has format: label1,filewithlistofwords1;label2,filewithlistofwords2;...");
        }
        for (String seedFile : seedWordsFiles.split(";")) {
          String[] t = seedFile.split(",");
          String label = t[0];
          String seedWordsFile = t[1];
          Set<String> seedWords4Label = new HashSet<String>();
          for (String line : IOUtils.readLines(seedWordsFile)) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("#"))
              continue;
            seedWords4Label.add(line);
          }
          seedWords.put(label, seedWords4Label);
          System.out.println("Number of seed words for label " + label + " is "
              + seedWords4Label.size());
        }

        Map<String, Class> answerClasses = new HashMap<String, Class>();
        String ansClasses = props.getProperty("answerClasses");
        if (ansClasses != null) {
          for (String l : ansClasses.split(";")) {
            String[] t = l.split(",");
            String label = t[0];
            String cl = t[1];
            Class answerClass = ClassLoader.getSystemClassLoader()
                .loadClass(cl);
            answerClasses.put(label, answerClass);
          }
        }

        Map<String, List<CoreLabel>> sents = null;

        String fileFormat = props.getProperty("fileFormat");
        String file = props.getProperty("file");
        String posModelPath = props.getProperty("posModelPath");
        if (fileFormat == null || fileFormat.equalsIgnoreCase("text")
            || fileFormat.equalsIgnoreCase("txt")) {
          String text = IOUtils.stringFromFile(file);
          sents = tokenize(text, posModelPath);
        } else if (fileFormat.equalsIgnoreCase("ser")) {
          sents = IOUtils.readObjectFromFile(file);
        } else
          throw new RuntimeException(
              "Cannot identify the file format. Valid values are text (or txt) and ser, where the serialized file is of the type Map<String, List<CoreLabel>>.");
        System.out.println("Processing # sents " + sents.size());
        g = new GetPatternsFromDataMultiClass(props, sents, seedWords);
        Execution.fillOptions(g, props);
        g.extremedebug = Boolean
            .parseBoolean(props.getProperty("extremedebug"));
        g.setUp();
        for (String l : seedWords.keySet()) {
          g.runLabelSeedWords(l, seedWords.get(l));
        }
      }

      g.extremedebug = Boolean.parseBoolean(props.getProperty("extremedebug"));

      String feedbackfile = props.getProperty("feedbackFile");
      System.out.println("Already learned words are "
          + g.getLearnedWords("onelabel"));
      g.iterateExtractApply(p0, p0Set, externalWordWeights, wordsOutputFile,
          sentsOutFile, patternOutFile, ignorePatterns, feedbackfile);

      if (saveInstance) {
        System.out.println("Saving the instance to " + outputSavedInstanceFile);
        IOUtils.writeObjectToFile(g, outputSavedInstanceFile);
      }
      boolean evaluate = Boolean.parseBoolean(props.getProperty("evaluate"));
      if (evaluate) {
        throw new RuntimeException("not implemented");
        // String evalFile = props.getProperty("evalFile");
        // Map<String, List<CoreLabel>> evalSents =
        // g.loadJavaNLPAnnotatorLabeledFile(evalFile, props);
        // g.evaluate("onelabel", evalSents);
      }

    } catch (Exception e) {
      e.printStackTrace();
    }
  }
}
