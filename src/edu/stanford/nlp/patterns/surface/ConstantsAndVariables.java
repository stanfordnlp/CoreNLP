package edu.stanford.nlp.patterns.surface;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.ling.tokensregex.Env;
import edu.stanford.nlp.ling.tokensregex.TokenSequencePattern;
import edu.stanford.nlp.patterns.surface.LearnImportantFeatures;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.util.CollectionUtils;
import edu.stanford.nlp.util.EditDistance;
import edu.stanford.nlp.util.Execution;
import edu.stanford.nlp.util.Pair;
import edu.stanford.nlp.util.StringUtils;
import edu.stanford.nlp.util.Execution.Option;
import edu.stanford.nlp.util.logging.Redwood;

public class ConstantsAndVariables {

  @Option(name = "LRSigma")
  public double LRSigma = 1.0;

  @Option(name = "englishWordsFiles")
  public String englishWordsFiles = null;
  public Set<String> englishWords = null;

  @Option(name = "commonWordsPatternFiles")
  public String commonWordsPatternFiles = null;
  public Set<String> commonEngWords = null;

  @Option(name = "otherSemanticClassesFiles")
  public String otherSemanticClassesFiles = null;

  public Set<String> otherSemanticClasses = null;

  public Map<String, Set<String>> labelDictionary = new HashMap<String, Set<String>>();

  public Map<String, Class> answerClass = null;
  //public Map<String, String> answerLabels = null;
  public Map<String, Map<Class, Object>> ignoreWordswithClassesDuringSelection = null;
  // TODO: not getting used!!
  public Map<String, Map<String, Class>> generalizeClasses = null;

  @Option(name = "minLen4FuzzyForPattern")
  public int minLen4FuzzyForPattern = 6;

  @Option(name = "wordIgnoreRegex")
  public String wordIgnoreRegex = "[^a-zA-Z]*";

  @Option(name = "numThreads")
  public int numThreads = 1;

  @Option(name = "stopWordsPatternFiles", gloss = "stop words")
  public String stopWordsPatternFiles = null;

  public Set<String> stopWords = null;

  public List<String> fillerWords = Arrays.asList("a", "an", "the", "`", "``", "'", "''");
  public Map<String, Env> env = new HashMap<String, Env>();
  // by default doesn't ignore anything
  public Pattern ignoreWordRegex = Pattern.compile("a^");

  @Option(name = "removeStopWordsFromSelectedPhrases")
  public boolean removeStopWordsFromSelectedPhrases = false;

  @Option(name = "removePhrasesWithStopWords")
  public boolean removePhrasesWithStopWords = false;

  boolean alreadySetUp = false;

  @Option(name = "distSimClusterFile")
  String distSimClusterFile = null;
  public Map<String, Integer> distSimClusters = null;

  @Option(name = "includeExternalFeatures")
  public boolean includeExternalFeatures = false;

  @Option(name = "externalFeatureWeightsFile")
  public String externalFeatureWeightsFile = null;

  @Option(name = "doNotApplyPatterns")
  public boolean doNotApplyPatterns = false;

  @Option(name = "numWordsCompound")
  public int numWordsCompound = 2;
  

  public ConcurrentHashMap<String, Double> editDistanceFromEnglishWords = new ConcurrentHashMap<String, Double>();
  public ConcurrentHashMap<String, String> editDistanceFromEnglishWordsMatches = new ConcurrentHashMap<String, String>();

  public ConcurrentHashMap<String, Double> editDistanceFromOtherSemanticClasses = new ConcurrentHashMap<String, Double>();
  public ConcurrentHashMap<String, String> editDistanceFromOtherSemanticClassesMatches = new ConcurrentHashMap<String, String>();

  public ConcurrentHashMap<String, Double> editDistanceFromThisClass = new ConcurrentHashMap<String, Double>();
  public ConcurrentHashMap<String, String> editDistanceFromThisClassMatches = new ConcurrentHashMap<String, String>();

  String channelNameLogger = "settingUp";

  public Counter<Integer> distSimWeights = new ClassicCounter<Integer>();

  @Option(name = "scorePhrasesSumNormalized")
  public boolean scorePhrasesSumNormalized = false;

  public enum ScorePhraseMeasures {
    DISTSIM, GOOGLENGRAM, PATWTBYFREQ, EDITDISTSAME, EDITDISTOTHER, DOMAINNGRAM, SEMANTICODDS
  };

  @Option(name = "usePhraseEvalDistSim")
  public boolean usePhraseEvalDistSim = false;
  @Option(name = "usePhraseEvalGoogleNgram")
  public boolean usePhraseEvalGoogleNgram = false;
  @Option(name = "usePhraseEvalDomainNgram")
  public boolean usePhraseEvalDomainNgram = false;
  @Option(name = "usePhraseEvalPatWtByFreq")
  public boolean usePhraseEvalPatWtByFreq = false;
  @Option(name = "usePhraseEvalSemanticOdds")
  public boolean usePhraseEvalSemanticOdds = false;
  @Option(name = "usePhraseEvalEditDistSame")
  public boolean usePhraseEvalEditDistSame = false;
  @Option(name = "usePhraseEvalEditDistOther")
  public boolean usePhraseEvalEditDistOther = false;

  @Option(name = "usePatternEvalDistSim")
  public boolean usePatternEvalDistSim = false;
  @Option(name = "usePatternEvalGoogleNgram")
  public boolean usePatternEvalGoogleNgram = false;
  @Option(name = "usePatternEvalDomainNgram")
  public boolean usePatternEvalDomainNgram = false;
  @Option(name = "usePatternEvalSemanticOdds")
  public boolean usePatternEvalSemanticOdds = false;
  @Option(name = "usePatternEvalEditDistSame")
  public boolean usePatternEvalEditDistSame = false;
  @Option(name = "usePatternEvalEditDistOther")
  public boolean usePatternEvalEditDistOther = false;

  // weka options
  @Option(name = "perSelectRand")
  public double perSelectRand = 0.01;
  @Option(name = "perSelectNeg")
  public double perSelectNeg = 0.1;
  @Option(name = "wekaOptions")
  public String wekaOptions = "";

  Properties props;

  public void setUp(Properties props) throws IOException {
    if (alreadySetUp) {
      return;
    }
    if (externalFeatureWeightsFile != null) {
      File f = new File(externalFeatureWeightsFile);
      if (!f.exists()) {
        System.err.println("externalweightsfile does not exist: learning weights!");
        LearnImportantFeatures lmf = new LearnImportantFeatures();
        if (answerClass.size() > 1 || this.labelDictionary.size() > 1)
          throw new RuntimeException("not implemented");
        lmf.answerClass = CollectionUtils.toList(answerClass.values()).get(0);
        lmf.answerLabel = CollectionUtils.toList(labelDictionary.keySet()).get(0);

        Execution.fillOptions(lmf, props);
        lmf.setUp();
        lmf.getTopFeatures(Data.sents, perSelectRand, perSelectNeg, wekaOptions);

      }
      for (String line : IOUtils.readLines(externalFeatureWeightsFile)) {
        String[] t = line.split(":");
        if (!t[0].startsWith("Cluster"))
          continue;
        String s = t[0].replace("Cluster-", "");
        Integer clusterNum = Integer.parseInt(s);
        distSimWeights.setCount(clusterNum, Double.parseDouble(t[1]));
      }
    }

    if (wordIgnoreRegex != null && !wordIgnoreRegex.isEmpty())
      ignoreWordRegex = Pattern.compile(wordIgnoreRegex);
    for (String label : labelDictionary.keySet()) {
      env.put(label, TokenSequencePattern.getNewEnv());
      env.get(label).bind("answer", answerClass.get(label));
      for (Entry<String, Class> en : generalizeClasses.get(label).entrySet())
        env.get(label).bind(en.getKey(), en.getValue());
    }
    Redwood.log(Redwood.DBG, channelNameLogger, "Running with debug output");
    stopWords = new HashSet<String>();
    Redwood.log(Redwood.FORCE, channelNameLogger, "Reading stop words from " + stopWordsPatternFiles);
    for (String stopwfile : stopWordsPatternFiles.split("[;,]"))
      stopWords.addAll(IOUtils.linesFromFile(stopwfile));

    englishWords = new HashSet<String>();
    System.out.println("Reading english words from " + englishWordsFiles);
    for (String englishWordsFile : englishWordsFiles.split("[;,]"))
      englishWords.addAll(IOUtils.linesFromFile(englishWordsFile));

    if (commonWordsPatternFiles != null) {
      commonEngWords = Collections.synchronizedSet(new HashSet<String>());
      for (String file : commonWordsPatternFiles.split("[;,]"))
        commonEngWords.addAll(IOUtils.linesFromFile(file));
    }

    if (otherSemanticClassesFiles != null) {
      otherSemanticClasses = Collections.synchronizedSet(new HashSet<String>());
      for (String file : otherSemanticClassesFiles.split("[;,]")) {
        for (String w : IOUtils.linesFromFile(file)) {

          String[] t = w.split("\\s+");
          if (t.length <= this.numWordsCompound)
            otherSemanticClasses.add(w);

        }
      }
      System.out.println("Size of othersemantic class variables is " + otherSemanticClasses.size());
    } else {
      otherSemanticClasses = new HashSet<String>();
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
    for (String label : labelDictionary.keySet()) {
      env.get(label).bind("$FILLER", "/" + StringUtils.join(fillerWords, "|") + "/");
      env.get(label).bind("$STOPWORD", stopStr);
      env.get(label).bind("$MOD", "[{tag:/JJ.*/}]");
    }

    if (distSimClusterFile != null) {
      distSimClusters = new HashMap<String, Integer>();
      for (String line : IOUtils.readLines(distSimClusterFile)) {
        String[] t = line.split("\t");
        distSimClusters.put(t[0], Integer.parseInt(t[1]));
      }
    }
    alreadySetUp = true;
  }

  private Pair<String, Double> getEditDist(Set<String> words, String ph) {
    double minD = editDistMax;
    String minPh = ph;
    for (String e : words) {
      if (e.equals(ph))
        return new Pair<String, Double>(ph, 0.0);

      double d = EditDistanceDL.editDistance(e, ph, 3);

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

  public Pair<String, Double> getEditDistanceFromThisClass(String label, String ph, int minLen) {
    if (ph.length() < minLen)
      return new Pair<String, Double>(ph, editDistMax);
    if (editDistanceFromThisClass.containsKey(ph))
      return new Pair<String, Double>(editDistanceFromThisClassMatches.get(ph), editDistanceFromThisClass.get(ph));

    Pair<String, Double> minD = getEditDist(labelDictionary.get(label), ph);

    // double minDtotal = editDistMax;
    // String minPh = "";
    // if (minD.second() == editDistMax && ph.contains(" ")) {
    // for (String s : ph.split("\\s+")) {
    // Pair<String, Double> minDSingle = getEditDist(labelDictionary.get(label),
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

  public Pair<String, Double> getEditDistanceFromOtherSemanticClasses(String ph, int minLen) {
    if (ph.length() < minLen)
      return new Pair<String, Double>(ph, editDistMax);
    if (editDistanceFromOtherSemanticClasses.containsKey(ph))
      return new Pair<String, Double>(editDistanceFromOtherSemanticClassesMatches.get(ph), editDistanceFromOtherSemanticClasses.get(ph));

    Pair<String, Double> minD = getEditDist(otherSemanticClasses, ph);

    // double minDtotal = editDistMax;
    // String minPh = "";
    // if (minD.second() == editDistMax && ph.contains(" ")) {
    // for (String s : ph.split("\\s+")) {
    // Pair<String, Double> minDSingle = getEditDist(otherSemanticClasses, s);
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
      Pair<String, Double> minD2 = getEditDist(otherSemanticClasses, ph);
      if (minD2.second < minD) {
        minD = minD2.second();
        minPh = minD2.first();
      }
    }

    editDistanceFromEnglishWords.putIfAbsent(ph, minD);
    editDistanceFromEnglishWordsMatches.putIfAbsent(ph, minPh);
    return minD;
  }

  public double getEditDistanceScoresOtherClass(String g) {
    double editDist;
    String editDistPh;
    if (editDistanceFromOtherSemanticClasses.containsKey(g)) {
      editDist = editDistanceFromOtherSemanticClasses.get(g);
      editDistPh = editDistanceFromOtherSemanticClassesMatches.get(g);
    } else {
      Pair<String, Double> editMatch = getEditDistanceFromOtherSemanticClasses(g, 4);
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

  public static String containsFuzzy(Set<String> words, String w, int minLen4Fuzzy) {
    for (String w1 : words) {
      if (isFuzzyMatch(w1, w, minLen4Fuzzy))
        return w1;
    }
    return null;
  }

}
