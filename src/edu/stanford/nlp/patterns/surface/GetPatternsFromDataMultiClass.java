package edu.stanford.nlp.patterns.surface;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Serializable;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
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
import java.util.regex.Pattern;

import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonArrayBuilder;
import javax.json.JsonObjectBuilder;
import javax.json.JsonReader;
import javax.json.JsonValue;

import edu.stanford.nlp.ie.crf.CRFClassifier;
import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.CoreAnnotations.GoldAnswerAnnotation;
import edu.stanford.nlp.patterns.surface.ConstantsAndVariables.ScorePhraseMeasures;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.stats.Counters;
import edu.stanford.nlp.stats.TwoDimensionalCounter;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.TreeCoreAnnotations.TreeAnnotation;
import edu.stanford.nlp.util.ArrayUtils;
import edu.stanford.nlp.util.CollectionUtils;
import edu.stanford.nlp.util.CollectionValuedMap;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.EditDistance;
import edu.stanford.nlp.util.Execution;
import edu.stanford.nlp.util.Pair;
import edu.stanford.nlp.util.PriorityQueue;
import edu.stanford.nlp.util.StringUtils;
import edu.stanford.nlp.util.Triple;
import edu.stanford.nlp.util.TypesafeMap;
import edu.stanford.nlp.util.TypesafeMap.Key;
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
 * <code>java -mx1000m edu.stanford.nlp.patterns.surface.GetPatternsFromDataMultiClass -file text_file -seedWordsFiles label1,seedwordlist1;label2,seedwordlist2;... -outDir output_directory (optional)</code>
 * <p>
 * 
 * <code>fileFormat</code>: (Optional) Default is text. Valid values are text
 * (or txt) and ser, where the serialized file is of the type <code>Map&lt;String,
 * List&lt;CoreLabel&gt;&gt;</code>.
 * <p>
 * <code>file</code>: (Required) Input file(s) (default assumed text). Can be
 * one or more of (concatenated by comma or semi-colon): file, directory, files
 * with regex in the filename (for example: "mydir/health-.*-processed.txt")
 * <p>
 * <code>seedWordsFiles</code>: (Required)
 * label1,file_seed_words1;label2,file_seed_words2;... where file_seed_words are
 * files with list of seed words, one in each line
 * <p>
 * <code>outDir</code>: (Optional) output directory where visualization/output
 * files are stored
 * <p>
 * For other flags, see individual comments for each flag.
 * 
 * <p>
 * To use a properties file, see
 * projects/core/data/edu/stanford/nlp/patterns/surface/example.properties or patterns/example.properties (depends on which codebase you are using)
 * as an example for the flags and their brief descriptions. Run the code as:
 * <code>java -mx1000m -cp classpath edu.stanford.nlp.patterns.surface.GetPatternsFromDataMultiClass -props dir-as-above/example.properties</code>
 * 
 * <p>
 * IMPORTANT: Many flags are described in the classes
 * {@link ConstantsAndVariables}, {@link CreatePatterns}, and
 * {@link PhraseScorer}.
 * 
 * 
 * 
 * @author Sonal Gupta (sonal@cs.stanford.edu)
 */

public class GetPatternsFromDataMultiClass implements Serializable {

  private static final long serialVersionUID = 1L;

  public Map<String, Map<Integer, Triple<Set<SurfacePattern>, Set<SurfacePattern>, Set<SurfacePattern>>>> patternsForEachToken = null;

  public Map<String, Set<String>> wordsForOtherClass = null;

  // String channelNameLogger = "patterns";
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
   * LOGREG is learning a logisitic regression classifier to combine weights to
   * score a phrase (Same as PhEvalInPat, except score of an unlabeled phrase is
   * computed using a logistic regression classifier)
   * <p>
   * LOGREGlogP is learning a logisitic regression classifier to combine weights
   * to score a phrase (Same as PhEvalInPatLogP, except score of an unlabeled
   * phrase is computed using a logistic regression classifier)
   * <p>
   * SqrtAllRatio is the pattern scoring used in Gupta et al. JAMIA 2014 paper
   * <p>
   * Below F1SeedPattern and BPB based on paper
   * "Unsupervised Method for Automatics Construction of a disease dictionary..."
   * <p>
   * Precision, Recall, and FMeasure (controlled by fbeta flag) is ranking the patterns using 
   * their precision, recall and F_beta measure 
   */
  public enum PatternScoring {
    F1SeedPattern, RlogF, RlogFPosNeg, RlogFUnlabNeg, RlogFNeg, PhEvalInPat, PhEvalInPatLogP, PosNegOdds, 
    YanGarber02, PosNegUnlabOdds, RatioAll, LOGREG, LOGREGlogP, SqrtAllRatio, LinICML03, kNN, Precision, Recall, FMeasure
  }

  enum WordScoring {
    BPB, WEIGHTEDNORM
  }

  Map<String, Boolean> writtenPatInJustification = new HashMap<String, Boolean>();

  Map<String, Counter<SurfacePattern>> learnedPatterns = new HashMap<String, Counter<SurfacePattern>>();
  Map<String, Counter<String>> learnedWords = new HashMap<String, Counter<String>>();

  public Map<String, TwoDimensionalCounter<String, SurfacePattern>> wordsPatExtracted = new HashMap<String, TwoDimensionalCounter<String, SurfacePattern>>();

  Properties props;
  public ScorePhrases scorePhrases;
  public ConstantsAndVariables constVars;
  public CreatePatterns createPats;

  DecimalFormat df = new DecimalFormat("#.##");

  /*
   * when there is only one label
   */
  public GetPatternsFromDataMultiClass(Properties props, Map<String, List<CoreLabel>> sents, Set<String> seedSet, boolean labelUsingSeedSets,
      String answerLabel) throws IOException, InstantiationException, IllegalAccessException, IllegalArgumentException, InvocationTargetException,
      NoSuchMethodException, SecurityException, InterruptedException, ExecutionException, ClassNotFoundException {
    this(props, sents, seedSet, labelUsingSeedSets, PatternsAnnotations.PatternLabel1.class, answerLabel);
  }

  @SuppressWarnings("rawtypes")
  public GetPatternsFromDataMultiClass(Properties props, Map<String, List<CoreLabel>> sents, Set<String> seedSet, boolean labelUsingSeedSets,
      Class answerClass, String answerLabel) throws IOException, InstantiationException, IllegalAccessException, IllegalArgumentException,
      InvocationTargetException, NoSuchMethodException, SecurityException, InterruptedException, ExecutionException, ClassNotFoundException {
    this.props = props;
    Map<String, Class<? extends TypesafeMap.Key<String>>> ansCl = new HashMap<String, Class<? extends TypesafeMap.Key<String>>>();
    ansCl.put(answerLabel, answerClass);

    Map<String, Class> generalizeClasses = new HashMap<String, Class>();

    Map<String, Map<Class, Object>> ignoreClasses = new HashMap<String, Map<Class, Object>>();
    ignoreClasses.put(answerLabel, new HashMap<Class, Object>());

    Map<String, Set<String>> seedSets = new HashMap<String, Set<String>>();
    seedSets.put(answerLabel, seedSet);
    setUpConstructor(sents, seedSets, labelUsingSeedSets, ansCl, generalizeClasses, ignoreClasses);

  }

  @SuppressWarnings("rawtypes")
  public GetPatternsFromDataMultiClass(Properties props, Map<String, List<CoreLabel>> sents, Set<String> seedSet, boolean labelUsingSeedSets,
      String answerLabel, Map<String, Class> generalizeClasses, Map<Class, Object> ignoreClasses) throws IOException, InstantiationException,
      IllegalAccessException, IllegalArgumentException, InvocationTargetException, NoSuchMethodException, SecurityException, InterruptedException,
      ExecutionException, ClassNotFoundException {
    this(props, sents, seedSet, labelUsingSeedSets, PatternsAnnotations.PatternLabel1.class, answerLabel, generalizeClasses, ignoreClasses);
  }

  @SuppressWarnings("rawtypes")
  public GetPatternsFromDataMultiClass(Properties props, Map<String, List<CoreLabel>> sents, Set<String> seedSet, boolean labelUsingSeedSets,
      Class answerClass, String answerLabel, Map<String, Class> generalizeClasses, Map<Class, Object> ignoreClasses) throws IOException,
      InstantiationException, IllegalAccessException, IllegalArgumentException, InvocationTargetException, NoSuchMethodException, SecurityException,
      InterruptedException, ExecutionException, ClassNotFoundException {
    this.props = props;
    Map<String, Class<? extends TypesafeMap.Key<String>>> ansCl = new HashMap<String, Class<? extends TypesafeMap.Key<String>>>();
    ansCl.put(answerLabel, answerClass);

    Map<String, Map<Class, Object>> iC = new HashMap<String, Map<Class, Object>>();
    iC.put(answerLabel, ignoreClasses);

    Map<String, Set<String>> seedSets = new HashMap<String, Set<String>>();
    seedSets.put(answerLabel, seedSet);
    setUpConstructor(sents, seedSets, labelUsingSeedSets, ansCl, generalizeClasses, iC);
  }

  @SuppressWarnings("rawtypes")
  public GetPatternsFromDataMultiClass(Properties props, Map<String, List<CoreLabel>> sents, Map<String, Set<String>> seedSets,
      boolean labelUsingSeedSets) throws IOException, InstantiationException, IllegalAccessException, IllegalArgumentException,
      InvocationTargetException, NoSuchMethodException, SecurityException, ClassNotFoundException, InterruptedException, ExecutionException {
    this.props = props;
    Map<String, Class<? extends TypesafeMap.Key<String>>> ansCl = new HashMap<String, Class<? extends TypesafeMap.Key<String>>>();
    Map<String, Class> gC = new HashMap<String, Class>();
    Map<String, Map<Class, Object>> iC = new HashMap<String, Map<Class, Object>>();
    int i = 1;
    for (String label : seedSets.keySet()) {
      String ansclstr = "edu.stanford.nlp.patterns.surface.PatternsAnnotations$PatternLabel" + i;
      ansCl.put(label, (Class<? extends Key<String>>) Class.forName(ansclstr));
      iC.put(label, new HashMap<Class, Object>());
      i++;
    }

    setUpConstructor(sents, seedSets, labelUsingSeedSets, ansCl, gC, iC);
  }

  @SuppressWarnings("rawtypes")
  public GetPatternsFromDataMultiClass(Properties props, Map<String, List<CoreLabel>> sents, Map<String, Set<String>> seedSets,
      boolean labelUsingSeedSets, Map<String, Class<? extends TypesafeMap.Key<String>>> answerClass) throws IOException, InstantiationException,
      IllegalAccessException, IllegalArgumentException, InvocationTargetException, NoSuchMethodException, SecurityException, InterruptedException,
      ExecutionException, ClassNotFoundException {
    this(props, sents, seedSets, labelUsingSeedSets, answerClass, new HashMap<String, Class>(), new HashMap<String, Map<Class, Object>>());
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
   * @throws ExecutionException
   * @throws InterruptedException
   * @throws ClassNotFoundException
   */
  @SuppressWarnings("rawtypes")
  public GetPatternsFromDataMultiClass(Properties props, Map<String, List<CoreLabel>> sents, Map<String, Set<String>> seedSets,
      boolean labelUsingSeedSets, Map<String, Class<? extends TypesafeMap.Key<String>>> answerClass, Map<String, Class> generalizeClasses,
      Map<String, Map<Class, Object>> ignoreClasses) throws IOException, InstantiationException, IllegalAccessException, IllegalArgumentException,
      InvocationTargetException, NoSuchMethodException, SecurityException, InterruptedException, ExecutionException, ClassNotFoundException {
    this.props = props;

    if (ignoreClasses.isEmpty()) {
      for (String label : seedSets.keySet())
        ignoreClasses.put(label, new HashMap<Class, Object>());
    }
    setUpConstructor(sents, seedSets, labelUsingSeedSets, answerClass, generalizeClasses, ignoreClasses);
  }

  @SuppressWarnings("rawtypes")
  private void setUpConstructor(Map<String, List<CoreLabel>> sents, Map<String, Set<String>> seedSets, boolean labelUsingSeedSets,
      Map<String, Class<? extends TypesafeMap.Key<String>>> answerClass, Map<String, Class> generalizeClasses,
      Map<String, Map<Class, Object>> ignoreClasses) throws IOException, InstantiationException, IllegalAccessException, IllegalArgumentException,
      InvocationTargetException, NoSuchMethodException, SecurityException, InterruptedException, ExecutionException, ClassNotFoundException {

    Data.sents = sents;
    Execution.fillOptions(Data.class, props);
    constVars = new ConstantsAndVariables(props, seedSets, answerClass, generalizeClasses, ignoreClasses);
    //Execution.fillOptions(constVars, props);
    //constVars.ignoreWordswithClassesDuringSelection = ignoreClasses;
    //constVars.addGeneralizeClasses(generalizeClasses);
    //constVars.setLabelDictionary(seedSets);

    if (constVars.writeMatchedTokensFiles && constVars.batchProcessSents) {
      throw new RuntimeException(
          "writeMatchedTokensFiles and batchProcessSents cannot be true at the same time (not implemented; also doesn't make sense to save a large sentences json file)");
    }

    //constVars.setUp(props);
    if (constVars.debug < 1) {
      Redwood.hideChannelsEverywhere(ConstantsAndVariables.minimaldebug);
    }
    if (constVars.debug < 2) {
      Redwood.hideChannelsEverywhere(Redwood.DBG);
    }
    constVars.justify = true;
    if (constVars.debug < 3) {
      constVars.justify = false;
    }
    if (constVars.debug < 4) {
      Redwood.hideChannelsEverywhere(ConstantsAndVariables.extremedebug);
    }

    Redwood.log(Redwood.DBG, "Running with debug output");
    Redwood.log(ConstantsAndVariables.extremedebug, "Running with extreme debug output");

    wordsPatExtracted = new HashMap<String, TwoDimensionalCounter<String, SurfacePattern>>();

    File invIndexDir = null;
    boolean createInvIndex = true;
    if (constVars.loadInvertedIndexDir != null) {
      createInvIndex = false;

      constVars.invertedIndex = InvertedIndexByTokens.loadIndex(constVars.loadInvertedIndexDir);
      if (constVars.invertedIndex.isBatchProcessed() != constVars.batchProcessSents) {
        throw new RuntimeException("The index was created with batchProcessSents as " + constVars.invertedIndex.isBatchProcessed()
            + ". Use the same flag or create a new index");
      }
      Redwood.log(Redwood.DBG, "Loaded index from " + constVars.loadInvertedIndexDir);
    }
    // else if(constVars.saveInvertedIndexDir != null){

    // if(constVars.diskBackedInvertedIndex){
    // invIndexDir = new File(constVars.saveInvertedIndexDir+"/cache");
    // IOUtils.deleteDirRecursively(invIndexDir);
    // IOUtils.ensureDir(invIndexDir);
    // }}

    else if (constVars.saveInvertedIndexDir == null) {

      String dir = System.getProperty("java.io.tmpdir");
      invIndexDir = File.createTempFile(dir, ".dir");
      invIndexDir.delete();
      invIndexDir.deleteOnExit();
    }

    Set<String> specialwords4Index = new HashSet<String>();
    specialwords4Index.addAll(Arrays.asList("fw", "FW", "sw", "SW", "OTHERSEM", "othersem"));

    for (String label : answerClass.keySet()) {
      wordsPatExtracted.put(label, new TwoDimensionalCounter<String, SurfacePattern>());

      specialwords4Index.add(label);
      specialwords4Index.add(label.toLowerCase());
    }

    scorePhrases = new ScorePhrases(props, constVars);
    createPats = new CreatePatterns(props, constVars);
    assert !(constVars.doNotApplyPatterns && (createPats.useStopWordsBeforeTerm || constVars.numWordsCompound > 1)) : " Cannot have both doNotApplyPatterns and (useStopWordsBeforeTerm true or numWordsCompound > 1)!";

    String prefixFileForIndex = null;
    if (constVars.usingDirForSentsInIndex) {
      prefixFileForIndex = constVars.saveSentencesSerDir;
    }

    if (createInvIndex)
      constVars.invertedIndex = new InvertedIndexByTokens(invIndexDir, constVars.matchLowerCaseContext, constVars.getStopWords(), specialwords4Index,
          constVars.batchProcessSents, prefixFileForIndex);

    int totalNumSents = 0;

    if (constVars.batchProcessSents) {
      if (createInvIndex || labelUsingSeedSets) {

        for (File f : Data.sentsFiles) {

          Map<String, List<CoreLabel>> sentsf = IOUtils.readObjectFromFile(f);

          totalNumSents += sentsf.size();

          if (createInvIndex) {
            String filename = "";
            if (constVars.usingDirForSentsInIndex) {
              filename = f.getName();
            } else
              filename = f.getAbsolutePath();

            constVars.invertedIndex.add(sentsf, filename, constVars.useLemmaContextTokens);
          }
          Redwood.log(Redwood.DBG, "Initializing sents from " + f + " with " + sentsf.size()
              + " sentences, either by labeling with the seed set or just setting the right classes");
          for (String l : constVars.getAnswerClass().keySet()) {

            Set<String> seed = seedSets == null || !labelUsingSeedSets ? new HashSet<String>() : (seedSets.containsKey(l) ? seedSets.get(l)
                : new HashSet<String>());

            runLabelSeedWords(sentsf, constVars.getAnswerClass().get(l), l, seed, constVars);

            Set<String> otherseed = constVars.getOtherSemanticClasses() == null || !labelUsingSeedSets ? new HashSet<String>() : constVars
                .getOtherSemanticClasses();
            if (constVars.addIndvWordsFromPhrasesExceptLastAsNeg) {
              for (String s : seed) {
                String[] t = s.split("\\s+");
                for (int i = 0; i < t.length - 1; i++) {
                  if (!seed.contains(t[i])) {
                    otherseed.add(t[i]);
                  }
                }
              }
            }
            if (constVars.getOtherSemanticClasses() != null)
              runLabelSeedWords(sentsf, PatternsAnnotations.OtherSemanticLabel.class, "OTHERSEM", otherseed, constVars);

          }
          Redwood.log(Redwood.DBG, "Saving the labeled seed sents (if given the option) to the same file " + f);
          IOUtils.writeObjectToFile(sentsf, f);
        }
      }
    } else {

      totalNumSents = Data.sents.size();

      if (createInvIndex)
        constVars.invertedIndex.add(Data.sents, "1", constVars.useLemmaContextTokens);

      Redwood.log(Redwood.DBG, "Initializing sents " + Data.sents.size()
          + " sentences, either by labeling with the seed set or just setting the right classes");
      for (String l : constVars.getAnswerClass().keySet()) {

        Set<String> seed = seedSets == null || !labelUsingSeedSets ? new HashSet<String>() : (seedSets.containsKey(l) ? seedSets.get(l)
            : new HashSet<String>());

        runLabelSeedWords(Data.sents, constVars.getAnswerClass().get(l), l, seed, constVars);

        Set<String> otherseed = constVars.getOtherSemanticClasses() == null || !labelUsingSeedSets ? new HashSet<String>() : constVars
            .getOtherSemanticClasses();
        if (constVars.getOtherSemanticClasses() != null)
          runLabelSeedWords(Data.sents, PatternsAnnotations.OtherSemanticLabel.class, "OTHERSEM", otherseed, constVars);
      }

    }

    if (constVars.saveInvertedIndexDir != null) {
      IOUtils.ensureDir(new File(constVars.saveInvertedIndexDir));
      constVars.invertedIndex.saveIndex(constVars.saveInvertedIndexDir);
    }

    Redwood.log(Redwood.DBG, "Done creating inverted index of " + constVars.invertedIndex.size() + " tokens and labeling data with total of "
        + totalNumSents + " sentences");

    if (constVars.usePatternEvalWordClass || constVars.usePhraseEvalWordClass) {

      if (constVars.externalFeatureWeightsFile == null) {
        File f = File.createTempFile("tempfeat", ".txt");
        f.delete();
        f.deleteOnExit();
        constVars.externalFeatureWeightsFile = f.getAbsolutePath();
      }

      for (String label : seedSets.keySet()) {
        String externalFeatureWeightsFileLabel = constVars.externalFeatureWeightsFile + "_" + label;
        File f = new File(externalFeatureWeightsFileLabel);
        if (!f.exists()) {
          Redwood.log(Redwood.DBG, "externalweightsfile for the label " + label + " does not exist: learning weights!");
          LearnImportantFeatures lmf = new LearnImportantFeatures();
          // if (answerClass.size() > 1 || this.labelDictionary.size() > 1)
          // throw new RuntimeException("not implemented");
          Execution.fillOptions(lmf, props);
          lmf.answerClass = answerClass.get(label);
          lmf.answerLabel = label;
          lmf.setUp();
          lmf.getTopFeatures(constVars.batchProcessSents, Data.sentsFiles, Data.sents, constVars.perSelectRand, constVars.perSelectNeg,
              externalFeatureWeightsFileLabel);

        }
        Counter<Integer> distSimWeightsLabel = new ClassicCounter<Integer>();
        for (String line : IOUtils.readLines(externalFeatureWeightsFileLabel)) {
          String[] t = line.split(":");
          if (!t[0].startsWith("Cluster"))
            continue;
          String s = t[0].replace("Cluster-", "");
          Integer clusterNum = Integer.parseInt(s);
          distSimWeightsLabel.setCount(clusterNum, Double.parseDouble(t[1]));
        }
        constVars.distSimWeights.put(label, distSimWeightsLabel);
      }
    }

    // computing semantic odds values
    if (constVars.usePatternEvalSemanticOdds || constVars.usePhraseEvalSemanticOdds) {
      Counter<String> dictOddsWeightsLabel = new ClassicCounter<String>();
      Counter<String> otherSemanticClassFreq = new ClassicCounter<String>();
      for (String s : constVars.getOtherSemanticClasses()) {
        for (String s1 : StringUtils.getNgrams(Arrays.asList(s.split("\\s+")), 1, constVars.numWordsCompound))
          otherSemanticClassFreq.incrementCount(s1);
      }
      otherSemanticClassFreq = Counters.add(otherSemanticClassFreq, 1.0);
      // otherSemanticClassFreq.setDefaultReturnValue(1.0);

      Map<String, Counter<String>> labelDictNgram = new HashMap<String, Counter<String>>();
      for (String label : seedSets.keySet()) {
        Counter<String> classFreq = new ClassicCounter<String>();
        for (String s : seedSets.get(label)) {
          for (String s1 : StringUtils.getNgrams(Arrays.asList(s.split("\\s+")), 1, constVars.numWordsCompound))
            classFreq.incrementCount(s1);
        }
        classFreq = Counters.add(classFreq, 1.0);
        labelDictNgram.put(label, classFreq);
        // classFreq.setDefaultReturnValue(1.0);
      }

      for (String label : seedSets.keySet()) {
        Counter<String> otherLabelFreq = new ClassicCounter<String>();
        for (String label2 : seedSets.keySet()) {
          if (label.equals(label2))
            continue;
          otherLabelFreq.addAll(labelDictNgram.get(label2));
        }
        otherLabelFreq.addAll(otherSemanticClassFreq);
        dictOddsWeightsLabel = Counters.divisionNonNaN(labelDictNgram.get(label), otherLabelFreq);
        constVars.dictOddsWeights.put(label, dictOddsWeightsLabel);
      }
    }
  }

  public static Map<String, List<CoreLabel>> runPOSNEROnTokens(List<CoreMap> sentsCM, String posModelPath, boolean useTargetNERRestriction,
      String prefix, boolean useTargetParserParentRestriction, String numThreads) {
    Annotation doc = new Annotation(sentsCM);

    Properties props = new Properties();
    List<String> anns = new ArrayList<String>();
    anns.add("pos");
    anns.add("lemma");

    if (useTargetParserParentRestriction) {
      anns.add("parse");

    }
    if (useTargetNERRestriction) {
      anns.add("ner");
    }

    props.setProperty("annotators", StringUtils.join(anns, ","));
    props.setProperty("parse.maxlen", "80");
    props.setProperty("nthreads", numThreads);
    props.setProperty("threads", numThreads);

    // props.put( "tokenize.options",
    // "ptb3Escaping=false,normalizeParentheses=false,escapeForwardSlashAsterisk=false");

    if (posModelPath != null) {
      props.setProperty("pos.model", posModelPath);
    }
    StanfordCoreNLP pipeline = new StanfordCoreNLP(props, false);

    Redwood.log(Redwood.DBG, "Annotating text");
    pipeline.annotate(doc);
    Redwood.log(Redwood.DBG, "Done annotating text");

    Map<String, List<CoreLabel>> sents = new HashMap<String, List<CoreLabel>>();

    for (CoreMap s : doc.get(CoreAnnotations.SentencesAnnotation.class)) {
      if (useTargetParserParentRestriction)
        inferParentParseTag(s.get(TreeAnnotation.class));
      sents.put(prefix + s.get(CoreAnnotations.DocIDAnnotation.class), s.get(CoreAnnotations.TokensAnnotation.class));
    }

    return sents;
  }

  static StanfordCoreNLP pipeline = null;

  public static int tokenize(String text, String posModelPath, boolean lowercase, boolean useTargetNERRestriction, String sentIDPrefix,
      boolean useTargetParserParentRestriction, String numThreads, boolean batchProcessSents, int numMaxSentencesPerBatchFile,
      File saveSentencesSerDirFile, Map<String, List<CoreLabel>> sents, int numFilesTillNow) throws InterruptedException, ExecutionException,
      IOException {
    if (pipeline == null) {
      Properties props = new Properties();
      List<String> anns = new ArrayList<String>();
      anns.add("tokenize");
      anns.add("ssplit");
      anns.add("pos");
      anns.add("lemma");

      if (useTargetParserParentRestriction) {
        anns.add("parse");
      }
      if (useTargetNERRestriction) {
        anns.add("ner");
      }

      props.setProperty("annotators", StringUtils.join(anns, ","));
      props.setProperty("parse.maxlen", "80");
      props.setProperty("threads", numThreads);

      props.put("tokenize.options", "ptb3Escaping=false,normalizeParentheses=false,escapeForwardSlashAsterisk=false");

      if (posModelPath != null) {
        props.setProperty("pos.model", posModelPath);
      }
      pipeline = new StanfordCoreNLP(props);
    }
    if (lowercase)
      text = text.toLowerCase();

    Annotation doc = new Annotation(text);
    pipeline.annotate(doc);
    Redwood.log(Redwood.DBG, "Done annotating text");

    int i = -1;
    for (CoreMap s : doc.get(CoreAnnotations.SentencesAnnotation.class)) {
      i++;
      if (useTargetParserParentRestriction)
        inferParentParseTag(s.get(TreeAnnotation.class));
      sents.put(sentIDPrefix + i, s.get(CoreAnnotations.TokensAnnotation.class));
      if (batchProcessSents && sents.size() >= numMaxSentencesPerBatchFile) {
        numFilesTillNow++;
        File file = new File(saveSentencesSerDirFile + "/sents_" + numFilesTillNow);
        IOUtils.writeObjectToFile(sents, file);
        sents = new HashMap<String, List<CoreLabel>>();
        Data.sentsFiles.add(file);
      }

    }
    if (sents.size() > 0 && batchProcessSents) {
      numFilesTillNow++;
      File file = new File(saveSentencesSerDirFile + "/sents_" + numFilesTillNow);
      IOUtils.writeObjectToFile(sents, file);
      Data.sentsFiles.add(file);
      sents.clear();
    }
    // not lugging around sents if batch processing
    if (batchProcessSents)
      sents = null;
    return numFilesTillNow;
  }

  static void inferParentParseTag(Tree tree) {

    String grandstr = tree.value();
    for (Tree child : tree.children()) {
      for (Tree grand : child.children()) {
        if (grand.isLeaf()) {
          ((CoreLabel) grand.label()).set(CoreAnnotations.GrandparentAnnotation.class, grandstr);
        }
      }
      inferParentParseTag(child);
    }

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
  public static List<Integer> getSubListIndex(String[] l1, String[] l2, String[] subl2, Set<String> englishWords, HashSet<String> seenFuzzyMatches,
      int minLen4Fuzzy) {
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
        if (englishWords.contains(l2[i]) || englishWords.contains(subl2[i]) || l2[i].length() <= minLen4Fuzzy || subl2[i].length() <= minLen4Fuzzy)
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

  public static void runLabelSeedWords(Map<String, List<CoreLabel>> sents, Class answerclass, String label, Set<String> seedWords, ConstantsAndVariables constVars)
      throws InterruptedException, ExecutionException, IOException {

    List<String> keyset = new ArrayList<String>(sents.keySet());

    int num = 0;
    if (constVars.numThreads == 1)
      num = keyset.size();
    else
      num = keyset.size() / (constVars.numThreads - 1);
    ExecutorService executor = Executors.newFixedThreadPool(constVars.numThreads);
    Redwood.log(ConstantsAndVariables.extremedebug, "keyset size is " + keyset.size());
    List<Future<Map<String, List<CoreLabel>>>> list = new ArrayList<Future<Map<String, List<CoreLabel>>>>();
    for (int i = 0; i < constVars.numThreads; i++) {
      List<String> keys = keyset.subList(i * num, Math.min(keyset.size(), (i + 1) * num));
      Redwood.log(ConstantsAndVariables.extremedebug, "assigning from " + i * num + " till " + Math.min(keyset.size(), (i + 1) * num));

      Callable<Map<String, List<CoreLabel>>> task = new LabelWithSeedWords(seedWords, sents, keys, answerclass, label, constVars.minLen4FuzzyForPattern, constVars.backgroundSymbol, constVars.getEnglishWords());
      Future<Map<String, List<CoreLabel>>> submit = executor.submit(task);
      list.add(submit);
    }

    // Now retrieve the result

    for (Future<Map<String, List<CoreLabel>>> future : list) {
      try {
        sents.putAll(future.get());
      } catch (Exception e) {
        executor.shutdownNow();
        throw new RuntimeException(e);
      }
    }
    executor.shutdown();
  }

  @SuppressWarnings("rawtypes")
  public static class LabelWithSeedWords implements Callable<Map<String, List<CoreLabel>>> {
    Set<String[]> seedwordsTokens = new HashSet<String[]>();
    Map<String, List<CoreLabel>> sents;
    List<String> keyset;
    Class labelClass;
    HashSet<String> seenFuzzyMatches = new HashSet<String>();
    String label;
    int minLen4FuzzyForPattern;
    String backgroundSymbol = "O";
    Set<String> dictWords = null;

    public LabelWithSeedWords(Set<String> seedwords, Map<String, List<CoreLabel>> sents, List<String> keyset, Class labelclass, String label, int minLen4FuzzyForPattern, String backgroundSymbol, Set<String> dictWords) {
      for (String s : seedwords)
        this.seedwordsTokens.add(s.split("\\s+"));
      this.sents = sents;
      this.keyset = keyset;
      this.labelClass = labelclass;
      this.label = label;
      this.minLen4FuzzyForPattern= minLen4FuzzyForPattern;
      this.backgroundSymbol = backgroundSymbol;
      this.dictWords = dictWords;
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
          List<Integer> indices = getSubListIndex(s, tokens, tokenslemma, dictWords, seenFuzzyMatches,
              minLen4FuzzyForPattern);
          if (indices != null && !indices.isEmpty())
            for (int index : indices)
              for (int i = 0; i < s.length; i++) {
                matchedPhrases.add(index + i, StringUtils.join(s, " "));
                labels[index + i] = true;
              }
        }
        int i = -1;
        for (CoreLabel l : sent) {
          i++;
          if (labels[i]) {
            l.set(labelClass, label);
            Redwood.log(ConstantsAndVariables.extremedebug, "labeling " + l.word() + " or its lemma " + l.lemma() + " as " + label
                + " because of the dict phrases " + (Set<String>) matchedPhrases.get(i));
          } else
            l.set(labelClass, backgroundSymbol);
          if (!l.containsKey(PatternsAnnotations.MatchedPhrases.class))
            l.set(PatternsAnnotations.MatchedPhrases.class, new HashSet<String>());
          l.get(PatternsAnnotations.MatchedPhrases.class).addAll(matchedPhrases.get(i));

        }
        newsent.put(k, sent);
      }
      return newsent;
    }
  }

  public Map<String, TwoDimensionalCounter<SurfacePattern, String>> patternsandWords = null;
  public Map<String, TwoDimensionalCounter<SurfacePattern, String>> allPatternsandWords = null;
  public Map<String, Counter<SurfacePattern>> currentPatternWeights = null;

  @SuppressWarnings({ "unchecked" })
  public Counter<SurfacePattern> getPatterns(String label, Set<SurfacePattern> alreadyIdentifiedPatterns, SurfacePattern p0, Counter<String> p0Set,
      Set<SurfacePattern> ignorePatterns) throws InterruptedException, ExecutionException, IOException, ClassNotFoundException,
      InstantiationException, IllegalAccessException, IllegalArgumentException, InvocationTargetException, NoSuchMethodException, SecurityException {

    TwoDimensionalCounter<SurfacePattern, String> patternsandWords4Label = new TwoDimensionalCounter<SurfacePattern, String>();
    TwoDimensionalCounter<SurfacePattern, String> negPatternsandWords4Label = new TwoDimensionalCounter<SurfacePattern, String>();
    TwoDimensionalCounter<SurfacePattern, String> posnegPatternsandWords4Label = new TwoDimensionalCounter<SurfacePattern, String>();
    TwoDimensionalCounter<SurfacePattern, String> unLabeledPatternsandWords4Label = new TwoDimensionalCounter<SurfacePattern, String>();
    TwoDimensionalCounter<SurfacePattern, String> negandUnLabeledPatternsandWords4Label = new TwoDimensionalCounter<SurfacePattern, String>();
    TwoDimensionalCounter<SurfacePattern, String> allPatternsandWords4Label = new TwoDimensionalCounter<SurfacePattern, String>();

    if (!constVars.batchProcessSents) {
      // if not batch processing
      if (this.patternsForEachToken == null) {
        // if patterns for each token null
        if (constVars.computeAllPatterns) {
          Redwood.log(Redwood.DBG, "Computing all patterns");
          this.patternsForEachToken = createPats.getAllPatterns(Data.sents);
          constVars.computeAllPatterns =false;
        } else {
          // read from the saved file
          this.patternsForEachToken = IOUtils.readObjectFromFile(constVars.allPatternsFile);
          Redwood.log(ConstantsAndVariables.minimaldebug, "Read all patterns from " + constVars.allPatternsFile);
        }
      }
      this.calculateSufficientStats(Data.sents, patternsForEachToken, label, patternsandWords4Label, posnegPatternsandWords4Label,
          allPatternsandWords4Label, negPatternsandWords4Label, unLabeledPatternsandWords4Label, negandUnLabeledPatternsandWords4Label);
    }
    // batch processing sentences
    else {

      for (File f : Data.sentsFiles) {

        Redwood.log(Redwood.DBG, (constVars.computeAllPatterns ? "Creating patterns and " : "") + "calculating sufficient statistics from " + f);

        Map<String, List<CoreLabel>> sents = IOUtils.readObjectFromFile(f);

        Map<String, Map<Integer, Triple<Set<SurfacePattern>, Set<SurfacePattern>, Set<SurfacePattern>>>> pats4File = null;

        if (constVars.computeAllPatterns) {
          if (this.patternsForEachToken == null)
            this.patternsForEachToken = new HashMap<String, Map<Integer, Triple<Set<SurfacePattern>, Set<SurfacePattern>, Set<SurfacePattern>>>>();
          pats4File = createPats.getAllPatterns(sents);
          this.patternsForEachToken.putAll(pats4File);
        } else {
          if (this.patternsForEachToken == null) {
            // read only for the first time
            this.patternsForEachToken = IOUtils.readObjectFromFile(constVars.allPatternsFile);
            Redwood.log(ConstantsAndVariables.minimaldebug, "Read all patterns from " + constVars.allPatternsFile);
          }
          pats4File = this.patternsForEachToken;
        }

        this.calculateSufficientStats(sents, pats4File, label, patternsandWords4Label, posnegPatternsandWords4Label, allPatternsandWords4Label,
            negPatternsandWords4Label, unLabeledPatternsandWords4Label, negandUnLabeledPatternsandWords4Label);
      }
    }
    if (constVars.computeAllPatterns && constVars.allPatternsFile != null) {
      IOUtils.writeObjectToFile(this.patternsForEachToken, constVars.allPatternsFile);
    }

    if (patternsandWords == null)
      patternsandWords = new HashMap<String, TwoDimensionalCounter<SurfacePattern, String>>();
    if (allPatternsandWords == null)
      allPatternsandWords = new HashMap<String, TwoDimensionalCounter<SurfacePattern, String>>();
    if (currentPatternWeights == null)
      currentPatternWeights = new HashMap<String, Counter<SurfacePattern>>();

    Counter<SurfacePattern> currentPatternWeights4Label = new ClassicCounter<SurfacePattern>();

    Set<SurfacePattern> removePats = enforceMinSupportRequirements(patternsandWords4Label, unLabeledPatternsandWords4Label);
    Counters.removeKeys(patternsandWords4Label, removePats);
    Counters.removeKeys(unLabeledPatternsandWords4Label, removePats);
    Counters.removeKeys(negandUnLabeledPatternsandWords4Label, removePats);
    Counters.removeKeys(allPatternsandWords4Label, removePats);
    Counters.removeKeys(posnegPatternsandWords4Label, removePats);
    Counters.removeKeys(negPatternsandWords4Label, removePats);

    // Redwood.log(ConstantsAndVariables.extremedebug,
    // "Patterns around positive words in the label " + label + " are " +
    // patternsandWords4Label);
    ScorePatterns scorePatterns;

    Class<?> patternscoringclass = getPatternScoringClass(constVars.patternScoring);

    if (patternscoringclass != null && patternscoringclass.equals(ScorePatternsF1.class)) {
      scorePatterns = new ScorePatternsF1(constVars, constVars.patternScoring, label, patternsandWords4Label, negPatternsandWords4Label,
          unLabeledPatternsandWords4Label, negandUnLabeledPatternsandWords4Label, allPatternsandWords4Label, props, p0Set, p0);
      Counter<SurfacePattern> finalPat = scorePatterns.score();
      Counters.removeKeys(finalPat, alreadyIdentifiedPatterns);
      Counters.retainNonZeros(finalPat);
      Counters.retainTop(finalPat, 1);
      if (Double.isNaN(Counters.max(finalPat)))
        throw new RuntimeException("how is the value NaN");
      Redwood.log(ConstantsAndVariables.minimaldebug, "Selected Pattern: " + finalPat);
      return finalPat;

    } else if (patternscoringclass != null && patternscoringclass.equals(ScorePatternsRatioModifiedFreq.class)) {
      scorePatterns = new ScorePatternsRatioModifiedFreq(constVars, constVars.patternScoring, label, patternsandWords4Label,
          negPatternsandWords4Label, unLabeledPatternsandWords4Label, negandUnLabeledPatternsandWords4Label, allPatternsandWords4Label,
          phInPatScoresCache, scorePhrases, props);

    } else if (patternscoringclass != null && patternscoringclass.equals(ScorePatternsFreqBased.class)) {
      scorePatterns = new ScorePatternsFreqBased(constVars, constVars.patternScoring, label, patternsandWords4Label, negPatternsandWords4Label,
          unLabeledPatternsandWords4Label, negandUnLabeledPatternsandWords4Label, allPatternsandWords4Label, props);

    } else if (constVars.patternScoring.equals(PatternScoring.kNN)) {
      try {
        Class<? extends ScorePatterns> clazz = (Class<? extends ScorePatterns>) Class.forName("edu.stanford.nlp.patterns.surface.ScorePatternsKNN");
        Constructor<? extends ScorePatterns> ctor = clazz.getConstructor(ConstantsAndVariables.class, PatternScoring.class, String.class,
            TwoDimensionalCounter.class, TwoDimensionalCounter.class, TwoDimensionalCounter.class, TwoDimensionalCounter.class,
            TwoDimensionalCounter.class, ScorePhrases.class, Properties.class);
        scorePatterns = ctor.newInstance(constVars, constVars.patternScoring, label, patternsandWords4Label, negPatternsandWords4Label,
            unLabeledPatternsandWords4Label, negandUnLabeledPatternsandWords4Label, allPatternsandWords4Label, scorePhrases, props);

      } catch (ClassNotFoundException e) {
        throw new RuntimeException("kNN pattern scoring is not released yet. Stay tuned.");
      } catch (NoSuchMethodException e) {
        throw new RuntimeException("newinstance of kNN not created", e);
      } catch (InvocationTargetException e) {
        throw new RuntimeException("newinstance of kNN not created", e);
      } catch (IllegalAccessException e) {
        throw new RuntimeException("newinstance of kNN not created", e);
      } catch (InstantiationException e) {
        throw new RuntimeException("newinstance of kNN not created", e);
      }
    } else {
      throw new RuntimeException(constVars.patternScoring + " is not implemented (check spelling?). ");
    }

    scorePatterns.setUp(props);
    currentPatternWeights4Label = scorePatterns.score();

    Redwood.log(ConstantsAndVariables.extremedebug, "patterns counter size is " + currentPatternWeights4Label.size());

    if (ignorePatterns != null && !ignorePatterns.isEmpty()) {
      Counters.removeKeys(currentPatternWeights4Label, ignorePatterns);
      Redwood.log(ConstantsAndVariables.extremedebug, "Removing patterns from ignorePatterns of size  " + ignorePatterns.size()
          + ". New patterns size " + currentPatternWeights4Label.size());
    }

    if (alreadyIdentifiedPatterns != null && !alreadyIdentifiedPatterns.isEmpty()) {
      Counters.removeKeys(currentPatternWeights4Label, alreadyIdentifiedPatterns);
      Redwood.log(ConstantsAndVariables.extremedebug, "Removing already identified patterns of size  " + alreadyIdentifiedPatterns.size()
          + ". New patterns size " + currentPatternWeights4Label.size());
    }

    PriorityQueue<SurfacePattern> q = Counters.toPriorityQueue(currentPatternWeights4Label);
    int num = 0;

    Counter<SurfacePattern> chosenPat = new ClassicCounter<SurfacePattern>();

    Set<SurfacePattern> removePatterns = new HashSet<SurfacePattern>();
    
    Set<SurfacePattern> removeIdentifiedPatterns = null;
    
    while (num < constVars.numPatterns && !q.isEmpty()) {
      SurfacePattern pat = q.removeFirst();
      if (currentPatternWeights4Label.getCount(pat) < constVars.thresholdSelectPattern) {
        Redwood.log(Redwood.DBG, "The max weight of candidate patterns is " + df.format(currentPatternWeights4Label.getCount(pat))
            + " so not adding anymore patterns");
        break;
      }
      boolean notchoose = false;
      if (!unLabeledPatternsandWords4Label.containsFirstKey(pat) || unLabeledPatternsandWords4Label.getCounter(pat).isEmpty()) {
        Redwood.log(ConstantsAndVariables.extremedebug, "Removing pattern " + pat + " because it has no unlab support; pos words: "
            + patternsandWords4Label.getCounter(pat) + " and all words " + allPatternsandWords4Label.getCounter(pat));
        notchoose = true;
        continue;
      }

      Set<SurfacePattern> removeChosenPats = null;

      if (!notchoose) {
        if (alreadyIdentifiedPatterns != null) {
          for (SurfacePattern p : alreadyIdentifiedPatterns) {
            if (SurfacePattern.subsumes(pat, p)) {
              // if (pat.getNextContextStr().contains(p.getNextContextStr()) &&
              // pat.getPrevContextStr().contains(p.getPrevContextStr())) {
              Redwood.log(ConstantsAndVariables.extremedebug, "Not choosing pattern " + pat
                  + " because it is contained in or contains the already chosen pattern " + p);
              notchoose = true;
              break;
            }

            int rest = pat.equalContext(p);
            // the contexts dont match
            if (rest == Integer.MAX_VALUE)
              continue;
            // if pat is less restrictive, remove p and add pat!
            if (rest < 0) {
              if(removeIdentifiedPatterns == null)
                removeIdentifiedPatterns = new HashSet<SurfacePattern>();
              
              removeIdentifiedPatterns.add(p);
            } else {
              notchoose = true;
              break;
            }
          }
        }
      }

      // In this iteration:
      if (!notchoose) {
        for (SurfacePattern p : chosenPat.keySet()) {
          boolean removeChosenPatFlag = false;
          if (SurfacePattern.sameGenre(pat, p)) {
            
            if(SurfacePattern.subsumes(pat, p)){
              Redwood.log(ConstantsAndVariables.extremedebug, "Not choosing pattern " + pat
                  + " because it is contained in or contains the already chosen pattern " + p);
              notchoose = true;
              break;
            } 
            else if (SurfacePattern.subsumes(p, pat)) {
              //subsume is true even if equal context
              
              //check if equal context
              int rest = pat.equalContext(p);

              // the contexts do not match
              if (rest == Integer.MAX_VALUE)
              {
                Redwood.log(ConstantsAndVariables.extremedebug, "Not choosing pattern " + p
                    + " because it is contained in or contains another chosen pattern in this iteration " + pat);  
                removeChosenPatFlag = true;
              }
              // if pat is less restrictive, remove p from chosen patterns and
              // add pat!
              else if (rest < 0) {
                removeChosenPatFlag = true;
              } else {
                notchoose = true;
                break;
              }
            } 

            
            if (removeChosenPatFlag) {
              if(removeChosenPats == null)
                removeChosenPats = new HashSet<SurfacePattern>();
              removeChosenPats.add(p);
              num--;
            }

          }
        }
      }
      
      if (notchoose) {
        Redwood.log(Redwood.DBG, "Not choosing " + pat + " for whatever reason!");
        continue;
      }

      if (removeChosenPats != null) {
        Redwood.log(ConstantsAndVariables.extremedebug, "Removing already chosen patterns in this iteration " + removeChosenPats + " in favor of "
            + pat);
        Counters.removeKeys(chosenPat, removeChosenPats);
      }
      
      if (removeIdentifiedPatterns != null) {
        Redwood.log(ConstantsAndVariables.extremedebug, "Removing already identified patterns " + removeIdentifiedPatterns + " in favor of " + pat);
        removePatterns.addAll(removeIdentifiedPatterns);

      }
      
      chosenPat.setCount(pat, currentPatternWeights4Label.getCount(pat));
      num++;
      
    }

    this.removeLearnedPatterns(label, removePatterns);

    Redwood.log(Redwood.DBG, "final size of the patterns is " + chosenPat.size());
    Redwood.log(ConstantsAndVariables.minimaldebug, "## Selected Patterns ## \n");
    List<Pair<SurfacePattern, Double>> chosenPatSorted = Counters.toSortedListWithCounts(chosenPat);
    for (Pair<SurfacePattern, Double> en : chosenPatSorted)
      Redwood.log(ConstantsAndVariables.minimaldebug, en.first().toStringToWrite() + ":" + df.format(en.second) + "\n");

    if (constVars.outDir != null && !constVars.outDir.isEmpty()) {
      CollectionValuedMap<SurfacePattern, String> posWords = new CollectionValuedMap<SurfacePattern, String>();
      for (Entry<SurfacePattern, ClassicCounter<String>> en : patternsandWords4Label.entrySet()) {
        posWords.addAll(en.getKey(), en.getValue().keySet());
      }

      CollectionValuedMap<SurfacePattern, String> negWords = new CollectionValuedMap<SurfacePattern, String>();
      for (Entry<SurfacePattern, ClassicCounter<String>> en : negPatternsandWords4Label.entrySet()) {
        negWords.addAll(en.getKey(), en.getValue().keySet());
      }
      CollectionValuedMap<SurfacePattern, String> unlabWords = new CollectionValuedMap<SurfacePattern, String>();
      for (Entry<SurfacePattern, ClassicCounter<String>> en : unLabeledPatternsandWords4Label.entrySet()) {
        unlabWords.addAll(en.getKey(), en.getValue().keySet());
      }
      String outputdir = constVars.outDir + "/" + constVars.identifier + "/" + label;
      Redwood.log(ConstantsAndVariables.minimaldebug, "Saving output in " + outputdir);

      IOUtils.ensureDir(new File(outputdir));

      String filename = outputdir + "/patterns" + ".json";

      JsonArrayBuilder obj = Json.createArrayBuilder();
      if (writtenPatInJustification.containsKey(label) && writtenPatInJustification.get(label)) {
        JsonReader jsonReader = Json.createReader(new BufferedInputStream(new FileInputStream(filename)));
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

      IOUtils.ensureDir(new File(filename).getParentFile());
      IOUtils.writeStringToFile(obj.build().toString(), filename, "utf8");
      writtenPatInJustification.put(label, true);
    }

    if (constVars.justify) {
      Redwood.log(Redwood.DBG, "Justification for Patterns:");
      for (SurfacePattern key : chosenPat.keySet()) {
        Redwood.log(Redwood.DBG, "\nPattern: " + key.toStringToWrite());
        Redwood.log(
            Redwood.DBG,
            "Positive Words:"
                + Counters.toSortedString(patternsandWords4Label.getCounter(key), patternsandWords4Label.getCounter(key).size(), "%1$s:%2$f", ";"));

        Redwood.log(
            Redwood.DBG,
            "Negative Words:"
                + Counters.toSortedString(negPatternsandWords4Label.getCounter(key), negPatternsandWords4Label.getCounter(key).size(), "%1$s:%2$f",
                    ";"));

        Redwood.log(
            Redwood.DBG,
            "Unlabeled Words:"
                + Counters.toSortedString(unLabeledPatternsandWords4Label.getCounter(key), unLabeledPatternsandWords4Label.getCounter(key).size(),
                    "%1$s:%2$f", ";"));
      }
    }
    allPatternsandWords.put(label, allPatternsandWords4Label);
    patternsandWords.put(label, patternsandWords4Label);
    currentPatternWeights.put(label, currentPatternWeights4Label);

    return chosenPat;

  }

  public static Class getPatternScoringClass(PatternScoring patternScoring) {
    if (patternScoring.equals(PatternScoring.F1SeedPattern)) {
      return ScorePatternsF1.class;
    } else if (patternScoring.equals(PatternScoring.PosNegUnlabOdds) || patternScoring.equals(PatternScoring.PosNegOdds)
        || patternScoring.equals(PatternScoring.RatioAll) || patternScoring.equals(PatternScoring.PhEvalInPat)
        || patternScoring.equals(PatternScoring.PhEvalInPatLogP) || patternScoring.equals(PatternScoring.LOGREG)
        || patternScoring.equals(PatternScoring.LOGREGlogP) || patternScoring.equals(PatternScoring.SqrtAllRatio)) {

      return ScorePatternsRatioModifiedFreq.class;

    } else if (patternScoring.equals(PatternScoring.RlogF) || patternScoring.equals(PatternScoring.RlogFPosNeg)
        || patternScoring.equals(PatternScoring.RlogFUnlabNeg) || patternScoring.equals(PatternScoring.RlogFNeg)
        || patternScoring.equals(PatternScoring.YanGarber02) || patternScoring.equals(PatternScoring.LinICML03)) {
      return ScorePatternsFreqBased.class;

    } else {
      return null;
    }
  }

  private void calculateSufficientStats(Map<String, List<CoreLabel>> sents,
      Map<String, Map<Integer, Triple<Set<SurfacePattern>, Set<SurfacePattern>, Set<SurfacePattern>>>> patternsForEachToken, String label,
      TwoDimensionalCounter<SurfacePattern, String> patternsandWords4Label,
      TwoDimensionalCounter<SurfacePattern, String> posnegPatternsandWords4Label,
      TwoDimensionalCounter<SurfacePattern, String> allPatternsandWords4Label,
      TwoDimensionalCounter<SurfacePattern, String> negPatternsandWords4Label,
      TwoDimensionalCounter<SurfacePattern, String> unLabeledPatternsandWords4Label,
      TwoDimensionalCounter<SurfacePattern, String> negandUnLabeledPatternsandWords4Label) {
    // calculating the sufficient statistics
    Class answerClass4Label = constVars.getAnswerClass().get(label);

    for (Entry<String, List<CoreLabel>> sentEn : sents.entrySet()) {
      Map<Integer, Triple<Set<SurfacePattern>, Set<SurfacePattern>, Set<SurfacePattern>>> pat4Sent = patternsForEachToken.get(sentEn.getKey());
      if (pat4Sent == null) {
        throw new RuntimeException("How come there are no patterns for " + sentEn.getKey() + ". The total patternsForEachToken size is "
            + patternsForEachToken.size() + " and keys " + patternsForEachToken.keySet());
      }
      List<CoreLabel> sent = sentEn.getValue();
      for (int i = 0; i < sent.size(); i++) {
        CoreLabel token = sent.get(i);
        Set<String> matchedPhrases = token.get(PatternsAnnotations.MatchedPhrases.class);

        String tokenWordOrLemma = token.word();
        String longestMatchingPhrase = null;

        if (constVars.useMatchingPhrase) {
          if (matchedPhrases != null && !matchedPhrases.isEmpty()) {
            for (String s : matchedPhrases) {
              if (s.equals(tokenWordOrLemma)) {
                longestMatchingPhrase = tokenWordOrLemma;
                break;
              }
              if (longestMatchingPhrase == null || longestMatchingPhrase.length() > s.length()) {
                longestMatchingPhrase = s;
              }
            }
          } else {
            longestMatchingPhrase = tokenWordOrLemma;
          }
        } else
          longestMatchingPhrase = tokenWordOrLemma;

        Triple<Set<SurfacePattern>, Set<SurfacePattern>, Set<SurfacePattern>> pat = pat4Sent.get(i);
        if (pat == null)
          throw new RuntimeException("Why are patterns null for sentence " + sentEn.getKey() + " and token " + i);
        Set<SurfacePattern> prevPat = pat.first();
        Set<SurfacePattern> nextPat = pat.second();
        Set<SurfacePattern> prevnextPat = pat.third();
        if (constVars.ignoreWordRegex.matcher(token.word()).matches())
          continue;

        // if the target word/phrase does not satisfy the POS requirement
        String tag = token.tag();
        if (constVars.allowedTagsInitials != null && constVars.allowedTagsInitials.containsKey(label)) {
          boolean use = false;
          for (String allowed : constVars.allowedTagsInitials.get(label)) {
            if (tag.startsWith(allowed)) {
              use = true;
              break;
            }
          }
          if (!use)
            continue;
        }

        // if the target word/phrase does not satisfy the NER requirements
        String nertag = token.ner();
        if (constVars.allowedNERsforLabels != null && constVars.allowedNERsforLabels.containsKey(label)) {
          if (!constVars.allowedNERsforLabels.get(label).contains(nertag)) {
            continue;
          }
        }
        if (token.get(answerClass4Label).equals(label)) {
          // Positive
          boolean prevTokenLabel = i == 0 ? false : sent.get(i - 1).get(answerClass4Label).equals(label);
          boolean nextTokenLabel = i == sent.size() - 1 ? false : sent.get(i + 1).get(answerClass4Label).equals(label);
          if (!constVars.ignorePatWithLabeledNeigh || !prevTokenLabel) {
            for (SurfacePattern s : prevPat) {
              patternsandWords4Label.getCounter(s).incrementCount(longestMatchingPhrase);
              posnegPatternsandWords4Label.getCounter(s).incrementCount(longestMatchingPhrase);
              allPatternsandWords4Label.getCounter(s).incrementCount(longestMatchingPhrase);
            }
          }
          if (!constVars.ignorePatWithLabeledNeigh || !nextTokenLabel) {
            for (SurfacePattern s : nextPat) {
              patternsandWords4Label.getCounter(s).incrementCount(longestMatchingPhrase);
              posnegPatternsandWords4Label.getCounter(s).incrementCount(longestMatchingPhrase);
              allPatternsandWords4Label.getCounter(s).incrementCount(longestMatchingPhrase);
            }
          }
          if (!constVars.ignorePatWithLabeledNeigh || (!prevTokenLabel && !nextTokenLabel)) {
            for (SurfacePattern s : prevnextPat) {

              patternsandWords4Label.getCounter(s).incrementCount(longestMatchingPhrase);
              posnegPatternsandWords4Label.getCounter(s).incrementCount(longestMatchingPhrase);
              allPatternsandWords4Label.getCounter(s).incrementCount(longestMatchingPhrase);
            }
          }
        } else {
          // Negative or unlabeled
          boolean negToken = false;
          Map<Class, Object> ignore = constVars.getIgnoreWordswithClassesDuringSelection().get(label);
          for (Class igCl : ignore.keySet())
            if ((Boolean) token.get(igCl)) {
              negToken = true;
              break;
            }
          if (!negToken)
            if (constVars.getOtherSemanticClasses().contains(token.word()) || constVars.getOtherSemanticClasses().contains(token.lemma()))
              negToken = true;

          for (SurfacePattern s : CollectionUtils.union(CollectionUtils.union(prevPat, nextPat), prevnextPat)) {

            if (negToken) {
              negPatternsandWords4Label.getCounter(s).incrementCount(tokenWordOrLemma);
              posnegPatternsandWords4Label.getCounter(s).incrementCount(tokenWordOrLemma);
            } else {
              unLabeledPatternsandWords4Label.getCounter(s).incrementCount(tokenWordOrLemma);
            }
            negandUnLabeledPatternsandWords4Label.getCounter(s).incrementCount(tokenWordOrLemma);
            allPatternsandWords4Label.incrementCount(s, tokenWordOrLemma);
          }
        }
      }
    }
  }

  private Set<SurfacePattern> enforceMinSupportRequirements(TwoDimensionalCounter<SurfacePattern, String> patternsandWords4Label,
      TwoDimensionalCounter<SurfacePattern, String> unLabeledPatternsandWords4Label) {
    Set<SurfacePattern> remove = new HashSet<SurfacePattern>();
    for (Entry<SurfacePattern, ClassicCounter<String>> en : patternsandWords4Label.entrySet()) {
      if (en.getValue().size() < constVars.minPosPhraseSupportForPat) {
        remove.add(en.getKey());
      }

    }
    int numRemoved = remove.size();
    Redwood.log(Redwood.DBG, "Removing " + numRemoved + " patterns that do not meet minPosPhraseSupportForPat requirement of >= "
        + constVars.minPosPhraseSupportForPat);

    for (Entry<SurfacePattern, ClassicCounter<String>> en : unLabeledPatternsandWords4Label.entrySet()) {
      if (en.getValue().size() < constVars.minUnlabPhraseSupportForPat) {
        remove.add(en.getKey());
      }
    }
    Redwood.log(Redwood.DBG, "Removing " + (remove.size() - numRemoved) + " patterns that do not meet minUnlabPhraseSupportForPat requirement of >= "
        + constVars.minUnlabPhraseSupportForPat);
    return remove;
  }

  void removeLearnedPattern(String label, SurfacePattern p) {
    this.learnedPatterns.get(label).remove(p);
    if (wordsPatExtracted.containsKey(label))
      for (Entry<String, ClassicCounter<SurfacePattern>> en : this.wordsPatExtracted.get(label).entrySet()) {
        en.getValue().remove(p);
      }
  }

  void removeLearnedPatterns(String label, Collection<SurfacePattern> pats) {
    Counters.removeKeys(this.learnedPatterns.get(label), pats);
    if (wordsPatExtracted.containsKey(label))
      for (Entry<String, ClassicCounter<SurfacePattern>> en : this.wordsPatExtracted.get(label).entrySet()) {
        Counters.removeKeys(en.getValue(), pats);
      }
  }

  public static Counter<String> normalizeSoftMaxMinMaxScores(Counter<String> scores, boolean minMaxNorm, boolean softmax, boolean oneMinusSoftMax) {
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

  public TwoDimensionalCounter<String, ScorePhraseMeasures> phInPatScoresCache = new TwoDimensionalCounter<String, ScorePhraseMeasures>();

  // TODO: this right now doesn't work for matchPatterns because of
  // DictAnnotationDTorSC. we are not setting DT, SC thing in the test sentences
  // Update: (may be this comment is not relevant anymore.)

  public void labelWords(String label, Map<String, List<CoreLabel>> sents, Set<String> identifiedWords, Set<SurfacePattern> patterns, String outFile,
      CollectionValuedMap<SurfacePattern, Triple<String, Integer, Integer>> matchedTokensByPat) throws IOException {

    CollectionValuedMap<String, Integer> tokensMatchedPatterns = null;
    if (constVars.restrictToMatched) {
      tokensMatchedPatterns = new CollectionValuedMap<String, Integer>();
      for (Entry<SurfacePattern, Collection<Triple<String, Integer, Integer>>> en : matchedTokensByPat.entrySet()) {
        for (Triple<String, Integer, Integer> en2 : en.getValue()) {
          for (int i = en2.second(); i <= en2.third(); i++) {
            tokensMatchedPatterns.add(en2.first(), i);
          }
        }
      }
    }

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
          if (constVars.restrictToMatched) {
            for (int j = 0; j < ph.length; j++) {
              if (!tokensMatchedPatterns.get(sentEn.getKey()).contains(idx + j)) {
                Redwood.log(ConstantsAndVariables.extremedebug, "not labeling " + sentEn.getValue().get(idx + j).word());
                donotuse = true;
                break;
              }
            }
          }
          if (donotuse == false) {
            for (int j = 0; j < ph.length; j++) {
              int index = idx + j;
              CoreLabel l = sentEn.getValue().get(index);
              if (constVars.usePatternResultAsLabel) {
                l.set(constVars.getAnswerClass().get(label), label);
                Set<String> matched = new HashSet<String>();
                matched.add(StringUtils.join(ph, " "));
                l.set(PatternsAnnotations.MatchedPhrases.class, matched);
                for (int k = Math.max(0, index - constVars.numWordsCompound); k < sentEn.getValue().size()
                    && k <= index + constVars.numWordsCompound + 1; k++) {
                  contextWordsRecalculatePats.add(k);
                }

              }
            }
          }
        }
      }

      if (patternsForEachToken != null && patternsForEachToken.containsKey(sentEn.getKey())) {
        for (int index : contextWordsRecalculatePats)
          this.patternsForEachToken.get(sentEn.getKey()).put(index, createPats.getContext(sentEn.getValue(), index));
      }
    }

    if (outFile != null) {
      Redwood.log(ConstantsAndVariables.minimaldebug, "Writing results to " + outFile);
      IOUtils.writeObjectToFile(sents, outFile);
    }
  }


  public void iterateExtractApply() throws IllegalAccessException, InterruptedException, ExecutionException, IOException, InstantiationException, NoSuchMethodException, InvocationTargetException, ClassNotFoundException {
    iterateExtractApply(null, null, null, null, null, null);
  }

  /**
   *
   * @param p0 Null in most cases. only used for BPB
   * @param p0Set Null in most cases
   * @param wordsOutputFile If null, output is in the output directory
   * @param sentsOutFile
   * @param patternsOutFile
   * @param ignorePatterns
   *
   */
  public void iterateExtractApply(Map<String, SurfacePattern> p0, Map<String, Counter<String>> p0Set, String wordsOutputFile, String sentsOutFile,
      String patternsOutFile, Map<String, Set<SurfacePattern>> ignorePatterns) throws ClassNotFoundException, IOException, InterruptedException,
      ExecutionException, InstantiationException, IllegalAccessException, IllegalArgumentException, InvocationTargetException, NoSuchMethodException,
      SecurityException {

    Map<String, CollectionValuedMap<SurfacePattern, Triple<String, Integer, Integer>>> matchedTokensByPatAllLabels = new HashMap<String, CollectionValuedMap<SurfacePattern, Triple<String, Integer, Integer>>>();
    Map<String, TwoDimensionalCounter<String, SurfacePattern>> termsAllLabels = new HashMap<String, TwoDimensionalCounter<String, SurfacePattern>>();

    Map<String, Set<String>> ignoreWordsAll = new HashMap<String, Set<String>>();
    for (String label : constVars.getLabelDictionary().keySet()) {
      matchedTokensByPatAllLabels.put(label, new CollectionValuedMap<SurfacePattern, Triple<String, Integer, Integer>>());
      termsAllLabels.put(label, new TwoDimensionalCounter<String, SurfacePattern>());
      if (constVars.useOtherLabelsWordsasNegative) {
        Set<String> w = new HashSet<String>();
        for (Entry<String, Set<String>> en : constVars.getLabelDictionary().entrySet()) {
          if (en.getKey().equals(label))
            continue;
          w.addAll(en.getValue());
        }
        ignoreWordsAll.put(label, w);
      }
    }

    Redwood.log(ConstantsAndVariables.minimaldebug, "Iterating " + constVars.numIterationsForPatterns + " times.");

    Map<String, BufferedWriter> wordsOutput = new HashMap<String, BufferedWriter>();
    Map<String, BufferedWriter> patternsOutput = new HashMap<String, BufferedWriter>();

    for (String label : constVars.getLabelDictionary().keySet()) {
      IOUtils.ensureDir(new File(constVars.outDir + "/" + constVars.identifier + "/" + label));

      String wordsOutputFileLabel;
      if (wordsOutputFile == null)
        wordsOutputFileLabel = constVars.outDir + "/" + constVars.identifier + "/" + label + "/learnedwords.txt";
      else
        wordsOutputFileLabel = wordsOutputFile + "_" + label;

      wordsOutput.put(label, new BufferedWriter(new FileWriter(wordsOutputFileLabel)));
      Redwood.log(ConstantsAndVariables.minimaldebug, "Saving the learned words for label " + label + " in " + wordsOutputFileLabel);

      String patternsOutputFileLabel = patternsOutFile + "_" + label;
      if (patternsOutFile == null)
        patternsOutputFileLabel = constVars.outDir + "/" + constVars.identifier + "/" + label + "/learnedpatterns.txt";
      patternsOutput.put(label, new BufferedWriter(new FileWriter(patternsOutputFileLabel)));
      Redwood.log(ConstantsAndVariables.minimaldebug, "Saving the learned patterns for label " + label + " in " + patternsOutputFileLabel);
    }

    for (int i = 0; i < constVars.numIterationsForPatterns; i++) {

      Redwood
          .log(ConstantsAndVariables.minimaldebug, "\n\n################################ Iteration " + (i + 1) + " ##############################");
      boolean keepRunning = false;
      Map<String, Counter<String>> learnedWordsThisIter = new HashMap<String, Counter<String>>();
      for (String label : constVars.getLabelDictionary().keySet()) {
        Redwood.log(ConstantsAndVariables.minimaldebug, "\n###Learning for label " + label + " ######");

        String sentout = sentsOutFile == null ? null : sentsOutFile + "_" + label;

        Pair<Counter<SurfacePattern>, Counter<String>> learnedPatWords4label = iterateExtractApply4Label(label, p0 != null ? p0.get(label) : null,
            p0Set != null ? p0Set.get(label) : null, wordsOutput.get(label), sentout, patternsOutput.get(label),
            ignorePatterns != null ? ignorePatterns.get(label) : null, 1, ignoreWordsAll.get(label), matchedTokensByPatAllLabels.get(label),
            termsAllLabels.get(label));

        learnedWordsThisIter.put(label, learnedPatWords4label.second());
        if (learnedPatWords4label.first().size() > 0) {
          keepRunning = true;
        }
      }

      if (constVars.useOtherLabelsWordsasNegative) {
        for (String label : constVars.getLabelDictionary().keySet()) {
          for (Entry<String, Counter<String>> en : learnedWordsThisIter.entrySet()) {
            if (en.getKey().equals(label))
              continue;
            ignoreWordsAll.get(label).addAll(en.getValue().keySet());
          }
        }
      }

      if (!keepRunning) {
        if (!constVars.tuneThresholdKeepRunning) {
          Redwood.log(ConstantsAndVariables.minimaldebug, "No patterns learned for all labels. Ending iterations.");
          break;
        } else {
          constVars.thresholdSelectPattern = 0.8 * constVars.thresholdSelectPattern;
          Redwood.log(ConstantsAndVariables.minimaldebug, "\n\nTuning thresholds to keep running. New Pattern threshold is  "
              + constVars.thresholdSelectPattern);
        }
      }
    }

    if (constVars.outDir != null && !constVars.outDir.isEmpty()) {
      Redwood.log(ConstantsAndVariables.minimaldebug, "Writing justification files");
      Set<String> allMatchedSents = new HashSet<String>();

      for (String label : constVars.getLabelDictionary().keySet()) {
        CollectionValuedMap<SurfacePattern, Triple<String, Integer, Integer>> tokensMatchedPat = matchedTokensByPatAllLabels.get(label);
        IOUtils.ensureDir(new File(constVars.outDir + "/" + constVars.identifier + "/" + label));

        if (constVars.writeMatchedTokensFiles) {

          String matchedtokensfilename = constVars.outDir + "/" + constVars.identifier + "/" + label + "/tokensmatchedpatterns" + ".json";
          JsonObjectBuilder pats = Json.createObjectBuilder();
          for (Entry<SurfacePattern, Collection<Triple<String, Integer, Integer>>> en : tokensMatchedPat.entrySet()) {
            CollectionValuedMap<String, Pair<Integer, Integer>> matchedStrs = new CollectionValuedMap<String, Pair<Integer, Integer>>();
            for (Triple<String, Integer, Integer> en2 : en.getValue()) {
              allMatchedSents.add(en2.first());
              matchedStrs.add(en2.first(), new Pair<Integer, Integer>(en2.second(), en2.third()));
            }

            JsonObjectBuilder senttokens = Json.createObjectBuilder();
            for (Entry<String, Collection<Pair<Integer, Integer>>> sen : matchedStrs.entrySet()) {
              JsonArrayBuilder obj = Json.createArrayBuilder();
              for (Pair<Integer, Integer> sen2 : sen.getValue()) {
                JsonArrayBuilder startend = Json.createArrayBuilder();
                startend.add(sen2.first());
                startend.add(sen2.second());
                obj.add(startend);
              }
              senttokens.add(sen.getKey(), obj);
            }
            pats.add(en.getKey().toStringSimple(), senttokens);
          }
          IOUtils.writeStringToFile(pats.build().toString(), matchedtokensfilename, "utf8");

          // Writing the sentence json file -- tokens for each sentence
          JsonObjectBuilder senttokens = Json.createObjectBuilder();
          for (String sentId : allMatchedSents) {
            JsonArrayBuilder sent = Json.createArrayBuilder();
            for (CoreLabel l : Data.sents.get(sentId)) {
              sent.add(l.word());
            }
            senttokens.add(sentId, sent);
          }
          String sentfilename = constVars.outDir + "/" + constVars.identifier + "/sentences" + ".json";
          IOUtils.writeStringToFile(senttokens.build().toString(), sentfilename, "utf8");
        }
      }

    }

    System.out.println("\n\nAll patterns learned:");
    for (Entry<String, Counter<SurfacePattern>> en : this.learnedPatterns.entrySet()) {
      System.out.println(en.getKey() + ":\t\t" + StringUtils.join(en.getValue().keySet(), "\n") + "\n\n");
    }

    System.out.println("\n\nAll words learned:");
    for (Entry<String, Counter<String>> en : this.learnedWords.entrySet()) {
      System.out.println(en.getKey() + ":\t\t" + en.getValue().keySet() + "\n\n");
    }

    // close all the writers
    for (String label : constVars.getLabelDictionary().keySet()) {
      wordsOutput.get(label).close();
      patternsOutput.get(label).close();
    }
  }

  public Pair<Counter<SurfacePattern>, Counter<String>> iterateExtractApply4Label(String label, SurfacePattern p0, Counter<String> p0Set,
      BufferedWriter wordsOutput, String sentsOutFile, BufferedWriter patternsOut, Set<SurfacePattern> ignorePatterns, int numIter,
      Set<String> ignoreWords, CollectionValuedMap<SurfacePattern, Triple<String, Integer, Integer>> matchedTokensByPat,
      TwoDimensionalCounter<String, SurfacePattern> terms) throws IOException, InterruptedException, ExecutionException, ClassNotFoundException,
      InstantiationException, IllegalAccessException, IllegalArgumentException, InvocationTargetException, NoSuchMethodException, SecurityException {

    if (!learnedPatterns.containsKey(label)) {
      learnedPatterns.put(label, new ClassicCounter<SurfacePattern>());
    }
    if (!learnedWords.containsKey(label)) {
      learnedWords.put(label, new ClassicCounter<String>());
    }

    Counter<String> identifiedWords = new ClassicCounter<String>();
    Counter<SurfacePattern> patterns = new ClassicCounter<SurfacePattern>();
    for (int i = 0; i < numIter; i++) {

      patterns.addAll(getPatterns(label, learnedPatterns.get(label).keySet(), p0, p0Set, ignorePatterns));
      learnedPatterns.get(label).addAll(patterns);

      if (sentsOutFile != null)
        sentsOutFile = sentsOutFile + "_" + i + "iter.ser";

      Counter<String> scoreForAllWordsThisIteration = new ClassicCounter<String>();

      identifiedWords.addAll(scorePhrases.learnNewPhrases(label, this.patternsForEachToken, patterns, learnedPatterns.get(label), matchedTokensByPat,
          scoreForAllWordsThisIteration, terms, wordsPatExtracted.get(label), currentPatternWeights.get(label), this.patternsandWords.get(label),
          this.allPatternsandWords.get(label), constVars.identifier, ignoreWords));

      if (identifiedWords.size() > 0) {
        if (constVars.usePatternResultAsLabel) {
          if (constVars.getLabelDictionary().containsKey(label)) {
            if (constVars.batchProcessSents) {
              for (File f : Data.sentsFiles) {
                Redwood.log(Redwood.DBG, "labeling sentences from " + f);
                Map<String, List<CoreLabel>> sents = IOUtils.readObjectFromFile(f);
                labelWords(label, sents, identifiedWords.keySet(), patterns.keySet(), sentsOutFile, matchedTokensByPat);
                IOUtils.writeObjectToFile(sents, f);
              }
            } else
              labelWords(label, Data.sents, identifiedWords.keySet(), patterns.keySet(), sentsOutFile, matchedTokensByPat);
          } else
            throw new RuntimeException("why is the answer label null?");
          learnedWords.get(label).addAll(identifiedWords);
        }

        if (wordsOutput != null) {
          // if (i > 0)
          // wordsOutput.write("\n");
          // wordsOutput.write("\n#Iteration " + (i + 1) + "\n");
          wordsOutput.write("\n" + Counters.toSortedString(identifiedWords, identifiedWords.size(), "%1$s", "\n"));
          wordsOutput.flush();
        }
      }
      if (patterns.size() == 0 && identifiedWords.size() == 0) {
        if (learnedWords.get(label).size() >= constVars.maxExtractNumWords) {
          System.out.println("Ending because no new words identified and total words learned till now >= max words " + constVars.maxExtractNumWords);
          break;
        }
        if (constVars.tuneThresholdKeepRunning) {
          constVars.thresholdSelectPattern = 0.8 * constVars.thresholdSelectPattern;
          System.out.println("\n\nTuning thresholds to keep running. New Pattern threshold is  " + constVars.thresholdSelectPattern);
        } else
          break;
      }
    }
    if (patternsOut != null)
      this.writePatternsToFile(learnedPatterns.get(label), patternsOut);

    return new Pair<Counter<SurfacePattern>, Counter<String>>(patterns, identifiedWords);
  }

  void writePatternsToFile(Counter<SurfacePattern> pattern, BufferedWriter outFile) throws IOException {
    for (Entry<SurfacePattern, Double> en : pattern.entrySet())
      outFile.write(en.getKey().toString() + "\t" + en.getValue() + "\n");
  }

  void writeWordsToFile(Counter<String> words, BufferedWriter outFile) throws IOException {
    for (Entry<String, Double> en : words.entrySet())
      outFile.write(en.getKey() + "\t" + en.getValue() + "\n");
  }

  Counter<String> readLearnedWordsFromFile(File file) {
    Counter<String> words = new ClassicCounter<String>();
    for (String line : IOUtils.readLines(file)) {
      String[] t = line.split("\t");
      words.setCount(t[0], Double.parseDouble(t[1]));
    }
    return words;
  }

  public Counter<String> getLearnedWords(String label) {
    return this.learnedWords.get(label);
  }

  public Counter<SurfacePattern> getLearnedPatterns(String label) {
    return this.learnedPatterns.get(label);
  }

  public Map<String, Counter<SurfacePattern>> getLearnedPatterns() {
    return this.learnedPatterns;
  }

  public void setLearnedWords(Counter<String> words, String label) {
    this.learnedWords.put(label, words);
  }

  public void setLearnedPatterns(Counter<SurfacePattern> patterns, String label) {
    this.learnedPatterns.put(label, patterns);
  }

  /**
   * COPIED from CRFClassifier: Count the successes and failures of the model on
   * the given document. Fills numbers in to counters for true positives, false
   * positives, and false negatives, and also keeps track of the entities seen. <br>
   * Returns false if we ever encounter null for gold or guess. NOTE: The
   * current implementation of counting wordFN/FP is incorrect.
   */
  public static boolean countResultsPerEntity(List<CoreLabel> doc, Counter<String> entityTP, Counter<String> entityFP, Counter<String> entityFN,
      String background, Counter<String> wordTP, Counter<String> wordTN, Counter<String> wordFP, Counter<String> wordFN,
      Class<? extends TypesafeMap.Key<String>> whichClassToCompare) {
    int index = 0;
    int goldIndex = 0, guessIndex = 0;
    String lastGold = background, lastGuess = background;

    // As we go through the document, there are two events we might be
    // interested in. One is when a gold entity ends, and the other
    // is when a guessed entity ends. If the gold and guessed
    // entities end at the same time, started at the same time, and
    // match entity type, we have a true positive. Otherwise we
    // either have a false positive or a false negative.
    String str = "";

    String s = "";
    for (CoreLabel l : doc) {
      s += " " + l.word() + ":" + l.get(CoreAnnotations.GoldAnswerAnnotation.class) + ":" + l.get(whichClassToCompare);
    }
    for (CoreLabel line : doc) {

      String gold = line.get(CoreAnnotations.GoldAnswerAnnotation.class);
      String guess = line.get(whichClassToCompare);

      if (gold == null || guess == null)
        return false;

      if (lastGold != null && !lastGold.equals(gold) && !lastGold.equals(background)) {
        if (lastGuess.equals(lastGold) && !lastGuess.equals(guess) && goldIndex == guessIndex) {
          wordTP.incrementCount(str);
          entityTP.incrementCount(lastGold, 1.0);
        } else {
          // System.out.println("false negative: " + str);
          wordFN.incrementCount(str);
          entityFN.incrementCount(lastGold, 1.0);
          str = "";

        }
      }

      if (lastGuess != null && !lastGuess.equals(guess) && !lastGuess.equals(background)) {
        if (lastGuess.equals(lastGold) && !lastGuess.equals(guess) && goldIndex == guessIndex && !lastGold.equals(gold)) {
          // correct guesses already tallied
          // str = "";
          // only need to tally false positives
        } else {
          // System.out.println("false positive: " + str);
          entityFP.incrementCount(lastGuess, 1.0);
          wordFP.incrementCount(str);
        }
        str = "";
      }

      if (lastGuess != null && lastGold != null && lastGold.equals(background) && lastGuess.equals(background)) {
        str = "";
      }

      if (lastGold == null || !lastGold.equals(gold)) {
        lastGold = gold;
        goldIndex = index;
      }

      if (lastGuess == null || !lastGuess.equals(guess)) {
        lastGuess = guess;
        guessIndex = index;
      }

      ++index;
      if (str.isEmpty())
        str = line.word();
      else
        str += " " + line.word();
    }

    // We also have to account for entities at the very end of the
    // document, since the above logic only occurs when we see
    // something that tells us an entity has ended
    if (lastGold != null && !lastGold.equals(background)) {
      if (lastGold.equals(lastGuess) && goldIndex == guessIndex) {
        entityTP.incrementCount(lastGold, 1.0);
        wordTP.incrementCount(str);
      } else {
        entityFN.incrementCount(lastGold, 1.0);
        wordFN.incrementCount(str);
      }
      str = "";
    }
    if (lastGuess != null && !lastGuess.equals(background)) {
      if (lastGold.equals(lastGuess) && goldIndex == guessIndex) {
        // correct guesses already tallied
      } else {
        entityFP.incrementCount(lastGuess, 1.0);
        wordFP.incrementCount(str);
      }
      str = "";
    }
    return true;
  }

  /**
   * Count the successes and failures of the model on the given document
   * ***token-based***. Fills numbers in to counters for true positives, false
   * positives, and false negatives, and also keeps track of the entities seen. <br>
   * Returns false if we ever encounter null for gold or guess.
   * 
   * this currently is only for testing one label at a time
   */
  public static void countResultsPerToken(List<CoreLabel> doc, Counter<String> entityTP, Counter<String> entityFP, Counter<String> entityFN,
      String background, Counter<String> wordTP, Counter<String> wordTN, Counter<String> wordFP, Counter<String> wordFN,
      Class<? extends TypesafeMap.Key<String>> whichClassToCompare) {

    CRFClassifier.countResults(doc, entityTP, entityFP, entityFN, background);

    // int index = 0;
    // int goldIndex = 0, guessIndex = 0;
    // String lastGold = background, lastGuess = background;
    // As we go through the document, there are two events we might be
    // interested in. One is when a gold entity ends, and the other
    // is when a guessed entity ends. If the gold and guessed
    // entities end at the same time, started at the same time, and
    // match entity type, we have a true positive. Otherwise we
    // either have a false positive or a false negative.
    for (CoreLabel line : doc) {

      String gold = line.get(GoldAnswerAnnotation.class);
      String guess = line.get(whichClassToCompare);

      if (gold == null || guess == null)
        throw new RuntimeException("why is gold or guess null?");

      if (gold.equals(guess) && !gold.equalsIgnoreCase(background)) {
        entityTP.incrementCount(gold);
        wordTP.incrementCount(line.word());
      } else if (!gold.equals(guess) && !gold.equalsIgnoreCase(background) && guess.equalsIgnoreCase(background)) {
        entityFN.incrementCount(gold);
        wordFN.incrementCount(line.word());

      } else if (!gold.equals(guess) && !guess.equalsIgnoreCase(background) && gold.equalsIgnoreCase(background)) {
        wordFP.incrementCount(line.word());
        entityFP.incrementCount(guess);
      } else if (gold.equals(guess) && !gold.equalsIgnoreCase(background)) {
        wordTN.incrementCount(line.word());
      } else if (!(gold.equalsIgnoreCase(background) && guess.equalsIgnoreCase(background)))
        throw new RuntimeException("don't know reached here. not meant for more than one entity label");

    }

  }

  public static void countResults(List<CoreLabel> doc, Counter<String> entityTP, Counter<String> entityFP, Counter<String> entityFN,
      String background, Counter<String> wordTP, Counter<String> wordTN, Counter<String> wordFP, Counter<String> wordFN,
      Class<? extends TypesafeMap.Key<String>> whichClassToCompare, boolean evalPerEntity) {
    if (evalPerEntity) {
      countResultsPerEntity(doc, entityTP, entityFP, entityFN, background, wordTP, wordTN, wordFP, wordFN, whichClassToCompare);
    } else {
      countResultsPerToken(doc, entityTP, entityFP, entityFN, background, wordTP, wordTN, wordFP, wordFN, whichClassToCompare);
    }
  }

  private void writeLabelDataSents(Map<String, List<CoreLabel>> sents, BufferedWriter writer) throws IOException {
    for (Entry<String, List<CoreLabel>> sent : sents.entrySet()) {
      writer.write(sent.getKey() + "\t");

      Map<String, Boolean> lastWordLabeled = new HashMap<String, Boolean>();
      for (String label : constVars.getLabelDictionary().keySet()) {
        lastWordLabeled.put(label, false);
      }

      for (CoreLabel s : sent.getValue()) {
        String str = "";
        //write them in reverse order
        List<String> listEndedLabels = new ArrayList<String>();
        //to first finish labels before starting
        List<String> startingLabels = new ArrayList<String>();
        
        for (Entry<String, Class<? extends TypesafeMap.Key<String>>> as : constVars.getAnswerClass().entrySet()) {
          String label = as.getKey();
          boolean lastwordlabeled = lastWordLabeled.get(label);
          if (s.get(as.getValue()).equals(label)) {
            if (!lastwordlabeled) {
              startingLabels.add(label);
            }
            lastWordLabeled.put(label, true);
          } else {
            if (lastwordlabeled) {
              listEndedLabels.add(label);
            }
            lastWordLabeled.put(label, false);
          }
        }
        for(int i = listEndedLabels.size() -1 ; i >=0; i--)
          str += " </" + listEndedLabels.get(i) + ">";
        for(String label : startingLabels){
          str += " <" + label + "> ";
        }
        str += " " + s.word();
        writer.write(str.trim() + " ");
      }
      writer.write("\n");
    }

  }

  public void writeLabeledData(String outFile) throws IOException, ClassNotFoundException {
    BufferedWriter writer = new BufferedWriter(new FileWriter(outFile));
    if (!constVars.batchProcessSents) {
      this.writeLabelDataSents(Data.sents, writer);
    } else {
      for (File f : Data.sentsFiles) {
        Map<String, List<CoreLabel>> sents = IOUtils.readObjectFromFile(f);
        this.writeLabelDataSents(sents, writer);
      }
    }
    writer.close();
  }

  public void writeColumnOutput(String outFile) throws IOException, ClassNotFoundException {
    BufferedWriter writer = new BufferedWriter(new FileWriter(outFile));
    if (!constVars.batchProcessSents) {
      this.writeColumnOutputSents(Data.sents, writer);
    } else {
      for (File f : Data.sentsFiles) {
        Map<String, List<CoreLabel>> sents = IOUtils.readObjectFromFile(f);
        this.writeColumnOutputSents(sents, writer);
      }
    }
    writer.close();
  }

  private void writeColumnOutputSents(Map<String, List<CoreLabel>> sents, BufferedWriter writer) throws IOException {
    for (Entry<String, List<CoreLabel>> sent : sents.entrySet()) {

      //writer.write("###"+sent.getKey() + "\n");

      for (CoreLabel s : sent.getValue()) {
        writer.write(s.word()+"\t");
        Set<String> labels = new HashSet<String>();
        for (Entry<String, Class<? extends TypesafeMap.Key<String>>> as : constVars.getAnswerClass().entrySet()) {
          String label = as.getKey();
          if (s.get(as.getValue()).equals(label)) {
            labels.add(label);
          }
        }
        if(labels.isEmpty())
          writer.write("O\n");
        else
          writer.write(StringUtils.join(labels,",")+"\n");
      }
      writer.write("\n");
    }
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
  // // Redwood.log(ConstantsAndVariables.minimaldebug, "keyset size is " +
  // // keyset.size());
  // List<Future<Pair<TwoDimensionalCounter<Pair<String, String>,
  // SurfacePattern>, CollectionValuedMap<String, Integer>>>> list = new
  // ArrayList<Future<Pair<TwoDimensionalCounter<Pair<String, String>,
  // SurfacePattern>, CollectionValuedMap<String, Integer>>>>();
  // for (int i = 0; i < constVars.numThreads; i++) {
  // // Redwood.log(ConstantsAndVariables.minimaldebug, "assigning from " + i *
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

  public void evaluate(Map<String, List<CoreLabel>> testSentences, boolean evalPerEntity) throws IOException {

    for (Entry<String, Class<? extends Key<String>>> anscl : constVars.getAnswerClass().entrySet()) {
      String label = anscl.getKey();
      Counter<String> entityTP = new ClassicCounter<String>();
      Counter<String> entityFP = new ClassicCounter<String>();
      Counter<String> entityFN = new ClassicCounter<String>();

      Counter<String> wordTP = new ClassicCounter<String>();
      Counter<String> wordTN = new ClassicCounter<String>();
      Counter<String> wordFP = new ClassicCounter<String>();
      Counter<String> wordFN = new ClassicCounter<String>();

      for (Entry<String, List<CoreLabel>> docEn : testSentences.entrySet()) {
        List<CoreLabel> doc = docEn.getValue();
        List<CoreLabel> doceval = new ArrayList<CoreLabel>();
        for (CoreLabel l : doc) {
          CoreLabel l2 = new CoreLabel();
          l2.setWord(l.word());

          if (l.get(anscl.getValue()).equals(label)) {
            l2.set(CoreAnnotations.AnswerAnnotation.class, label);
          } else
            l2.set(CoreAnnotations.AnswerAnnotation.class, constVars.backgroundSymbol);

          // If the gold label is not the label we are calculating the scores
          // for, set it to the background symbol
          if (!l.get(CoreAnnotations.GoldAnswerAnnotation.class).equals(label)) {
            l2.set(CoreAnnotations.GoldAnswerAnnotation.class, constVars.backgroundSymbol);
          } else
            l2.set(CoreAnnotations.GoldAnswerAnnotation.class, label);
          doceval.add(l2);
        }

        countResults(doceval, entityTP, entityFP, entityFN, constVars.backgroundSymbol, wordTP, wordTN, wordFP, wordFN,
            CoreAnnotations.AnswerAnnotation.class, evalPerEntity); //
      }
      System.out.println("False Positives: " + Counters.toSortedString(wordFP, wordFP.size(), "%s:%.2f", ";"));
      System.out.println("False Negatives: " + Counters.toSortedString(wordFN, wordFN.size(), "%s:%.2f", ";"));

      Redwood.log(Redwood.DBG, "\nFor label " + label + " True Positives: " + entityTP + "\tFalse Positives: " + entityFP + "\tFalse Negatives: "
          + entityFN);
      Counter<String> precision = Counters.division(entityTP, Counters.add(entityTP, entityFP));
      Counter<String> recall = Counters.division(entityTP, Counters.add(entityTP, entityFN));
      Redwood.log(ConstantsAndVariables.minimaldebug, "\nFor label " + label + " Precision: " + precision + ", Recall: " + recall + ", F1 score:  "
          + FScore(precision, recall, 1));
      // Redwood.log(ConstantsAndVariables.minimaldebug, "Total: " +
      // Counters.add(entityFP, entityTP));
    }

  }

  public static <D> Counter<D> FScore(Counter<D> precision, Counter<D> recall, double beta) {
    double betasq = beta * beta;
    return Counters.divisionNonNaN(Counters.scale(Counters.product(precision, recall), (1 + betasq)),
        (Counters.add(Counters.scale(precision, betasq), recall)));
  }

  public static List<File> getAllFiles(String file) {
    
    List<File> allFiles = new ArrayList<File>();
    for (String tokfile : file.split("[,;]")) {
      File filef = new File(tokfile);
      if (filef.isDirectory()) {
        String path = ".*";
        File dir = filef;
        for (File f : IOUtils.iterFilesRecursive(dir, Pattern.compile(path))) {
          Redwood.log(Redwood.DBG, "Reading file " + f);
          allFiles.add(f);
        }
      } else {
        Redwood.log(Redwood.DBG, "Reading file " + filef);
        allFiles.add(filef);
      }

      // RegExFileFilter fileFilter = new RegExFileFilter(Pattern.compile(ext));
      // File[] files = dir.listFiles(fileFilter);
    }

    return allFiles;
  }

  private Pair<Double, Double> getPrecisionRecall(String label, Map<String, Boolean> goldWords4Label) {
    Set<String> learnedWords = getLearnedWords(label).keySet();
    int numcorrect = 0, numincorrect = 0;
    int numgoldcorrect = 0;
    for (Entry<String, Boolean> en : goldWords4Label.entrySet()) {
      if (en.getValue())
        numgoldcorrect++;
    }
    Set<String> assumedNeg = new HashSet<String>();
    for (String e : learnedWords) {
      if (!goldWords4Label.containsKey(e)) {
        assumedNeg.add(e);

        numincorrect++;
        continue;
      }
      if (goldWords4Label.get(e)) {
        numcorrect++;
      } else
        numincorrect++;
    }

    if (!assumedNeg.isEmpty())
      System.err.println("Gold entity list does not contain words " + assumedNeg + " for label " + label + ". Assuming them as negative.");

    double precision = numcorrect / (double) (numcorrect + numincorrect);
    double recall = numcorrect / (double) (numgoldcorrect);
    return new Pair<Double, Double>(precision, recall);
  }

  public double FScore(double precision, double recall, double beta) {
    double betasq = beta * beta;
    return (1 + betasq) * precision * recall / (betasq * precision + recall);
  }

  public static Map<String, Set<String>> readSeedWords(Properties props) {
    return readSeedWords(props.getProperty("seedWordsFiles"));
  }

  public static Map<String, Set<String>> readSeedWords(String seedWordsFiles){
    Map<String, Set<String>> seedWords  = new HashMap<String, Set<String>>();


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
        if (line.isEmpty() || line.startsWith("#")) {
          continue;
        }
        seedWords4Label.add(line);
      }
      seedWords.put(label, seedWords4Label);
      Redwood.log(ConstantsAndVariables.minimaldebug, "Number of seed words for label " + label + " is " + seedWords4Label.size());
    }
    return seedWords;
  }
  /**
   * Execute the system give a properties file or object. Returns the model created
   * @param props
   */
  public static GetPatternsFromDataMultiClass run(Properties props) throws IOException, ClassNotFoundException, IllegalAccessException, InterruptedException, ExecutionException, InstantiationException, NoSuchMethodException, InvocationTargetException {
    Map<String, Set<SurfacePattern>> ignorePatterns = new HashMap<String, Set<SurfacePattern>>();
    Map<String, SurfacePattern> p0 = new HashMap<String, SurfacePattern>();
    Map<String, Counter<String>> p0Set = new HashMap<String, Counter<String>>();

    String fileFormat = props.getProperty("fileFormat");

    Map<String, Set<String>> seedWords = readSeedWords(props);

    Map<String, Class> answerClasses = new HashMap<String, Class>();
    String ansClasses = props.getProperty("answerClasses");
    if (ansClasses != null) {
      for (String l : ansClasses.split(";")) {
        String[] t = l.split(",");
        String label = t[0];
        String cl = t[1];
        Class answerClass = ClassLoader.getSystemClassLoader().loadClass(cl);
        answerClasses.put(label, answerClass);
      }
    }

    Map<String, List<CoreLabel>> sents = null;
    boolean batchProcessSents = Boolean.parseBoolean(props.getProperty("batchProcessSents", "false"));
    int numMaxSentencesPerBatchFile = Integer.parseInt(props.getProperty("numMaxSentencesPerBatchFile", String.valueOf(Integer.MAX_VALUE)));

    if (!batchProcessSents)
      sents = new HashMap<String, List<CoreLabel>>();
    else
      Data.sentsFiles = new ArrayList<File>();

    String file = props.getProperty("file");

    String posModelPath = props.getProperty("posModelPath");
    boolean lowercase = Boolean.parseBoolean(props.getProperty("lowercaseText"));
    boolean useTargetNERRestriction = Boolean.parseBoolean(props.getProperty("useTargetNERRestriction"));
    boolean useTargetParserParentRestriction = Boolean.parseBoolean(props.getProperty("useTargetParserParentRestriction"));
    boolean useContextNERRestriction = Boolean.parseBoolean(props.getProperty("useContextNERRestriction"));

    boolean evaluate = Boolean.parseBoolean(props.getProperty("evaluate"));
    boolean addEvalSentsToTrain = Boolean.parseBoolean(props.getProperty("addEvalSentsToTrain"));
    String evalFileWithGoldLabels = props.getProperty("evalFileWithGoldLabels");

    if (file == null && (evalFileWithGoldLabels == null || addEvalSentsToTrain == false)) {
      throw new RuntimeException("No training data! file is " + file + " and evalFileWithGoldLabels is " + evalFileWithGoldLabels
        + " and addEvalSentsToTrain is " + addEvalSentsToTrain);
    }

    String saveSentencesSerDir = null;
    boolean usingDirForSentsInIndex = true;
    // Read training file
    if (file != null) {
      saveSentencesSerDir = props.getProperty("saveSentencesSerDir");
      File saveSentencesSerDirFile = null;
      if (saveSentencesSerDir != null) {
        saveSentencesSerDirFile = new File(saveSentencesSerDir);
        IOUtils.ensureDir(saveSentencesSerDirFile);
        IOUtils.writeObjectToFile(sents, saveSentencesSerDir + "/sents_all.ser");
      } else {
        String systemdir = System.getProperty("java.io.tmpdir");
        saveSentencesSerDirFile = File.createTempFile("sents", ".tmp", new File(systemdir));
        saveSentencesSerDirFile.deleteOnExit();
        saveSentencesSerDir = saveSentencesSerDirFile.getAbsolutePath();
        saveSentencesSerDirFile.delete();
        saveSentencesSerDirFile.mkdir();
      }

      List<File> allFiles = GetPatternsFromDataMultiClass.getAllFiles(file);
      int numFilesTillNow = 0;
      if (fileFormat == null || fileFormat.equalsIgnoreCase("text") || fileFormat.equalsIgnoreCase("txt")) {
        Map<String, List<CoreLabel>> sentsthis = new HashMap<String, List<CoreLabel>>();
        for (File f : allFiles) {
          Redwood.log(Redwood.DBG, "Annotating text in " + f);

          String text = IOUtils.stringFromFile(f.getAbsolutePath());

          numFilesTillNow = tokenize(text, posModelPath, lowercase, useTargetNERRestriction || useContextNERRestriction, f.getName() + "-",
            useTargetParserParentRestriction, props.getProperty("numThreads"), batchProcessSents, numMaxSentencesPerBatchFile,
            saveSentencesSerDirFile, sentsthis, numFilesTillNow);
          if (!batchProcessSents) {
            sents.putAll(sentsthis);
          }
        }

        if (!batchProcessSents) {
          IOUtils.writeObjectToFile(sents, saveSentencesSerDirFile + "/sents_" + numFilesTillNow);
        }

      } else if (fileFormat.equalsIgnoreCase("ser")) {
        usingDirForSentsInIndex = false;
        for (File f : allFiles) {
          if (!batchProcessSents)
            sents.putAll((Map<String, List<CoreLabel>>) IOUtils.readObjectFromFile(f));
          else{
            File newf = new File(saveSentencesSerDir + "/" + f.getAbsolutePath().replaceAll(Pattern.quote("/"), "_"));
            IOUtils.cp(f, newf);
            Data.sentsFiles.add(newf);
          }
        }
      } else {
        throw new RuntimeException(
          "Cannot identify the file format. Valid values are text (or txt) and ser, where the serialized file is of the type Map<String, List<CoreLabel>>.");
      }
    }

    Map<String, List<CoreLabel>> evalsents = new HashMap<String, List<CoreLabel>>();
    File saveEvalSentencesSerFileFile = null;

    // Read Evaluation File
    if (evaluate) {
      if (evalFileWithGoldLabels != null) {

        String saveEvalSentencesSerFile = props.getProperty("saveEvalSentencesSerFile");
        if (saveEvalSentencesSerFile == null) {
          String systemdir = System.getProperty("java.io.tmpdir");
          saveEvalSentencesSerFileFile = File.createTempFile("evalsents", ".tmp", new File(systemdir));
        } else
          saveEvalSentencesSerFileFile = new File(saveEvalSentencesSerFile);

        Map setClassForTheseLabels = new HashMap<String, Class>();
        //boolean splitOnPunct = Boolean.parseBoolean(props.getProperty("splitOnPunct", "true"));
        List<File> allFiles = GetPatternsFromDataMultiClass.getAllFiles(evalFileWithGoldLabels);
        int numFile = 0;
        String evalFileFormat = props.getProperty("evalFileFormat");
        if (evalFileFormat == null || evalFileFormat.equalsIgnoreCase("text") || evalFileFormat.equalsIgnoreCase("txt")) {
          for (File f : allFiles) {
            numFile++;
            Redwood.log(Redwood.DBG, "Annotating text in " + f + ". Num file " + numFile);
            List<CoreMap> sentsCMs = AnnotatedTextReader.parseFile(new BufferedReader(new FileReader(f)), seedWords.keySet(),
              setClassForTheseLabels, true, f.getName());
            evalsents.putAll(runPOSNEROnTokens(sentsCMs, posModelPath, useTargetNERRestriction || useContextNERRestriction, "",
              useTargetParserParentRestriction, props.getProperty("numThreads")));
          }

        } else if (fileFormat.equalsIgnoreCase("ser")) {
          for (File f : allFiles) {
            evalsents.putAll((Map<? extends String, ? extends List<CoreLabel>>) IOUtils.readObjectFromFile(f));
          }
        }
        // if (addEvalSentsToTrain) {
        Redwood.log(Redwood.DBG, "Adding " + evalsents.size() + " eval sents to the training set");

        // }

        IOUtils.writeObjectToFile(evalsents, saveEvalSentencesSerFileFile);

        if (batchProcessSents) {
          if (Data.sentsFiles == null)
            Data.sentsFiles = new ArrayList<File>();
          Data.sentsFiles.add(saveEvalSentencesSerFileFile);
        } else
          sents.putAll(evalsents);
      }
    }

    boolean learn = Boolean.parseBoolean(props.getProperty("learn", "true"));

    boolean labelUsingSeedSets = Boolean.parseBoolean(props.getProperty("labelUsingSeedSets", "true"));

    GetPatternsFromDataMultiClass model = new GetPatternsFromDataMultiClass(props, sents, seedWords, labelUsingSeedSets);

    model.constVars.usingDirForSentsInIndex = usingDirForSentsInIndex;
    model.constVars.saveSentencesSerDir = saveSentencesSerDir;

    Execution.fillOptions(model, props);

    // Redwood.log(ConstantsAndVariables.minimaldebug,
    // "Total number of training sentences " + Data.sents.size());

    String sentsOutFile = props.getProperty("sentsOutFile");

    String wordsOutputFile = props.getProperty("wordsOutputFile");

    String patternOutFile = props.getProperty("patternOutFile");

    // If you want to reuse patterns and words learned previously (may be on
    // another dataset etc)
    boolean loadSavedPatternsWordsDir = Boolean.parseBoolean(props.getProperty("loadSavedPatternsWordsDir"));
    String patternsWordsDir = props.getProperty("patternsWordsDir");

    if (loadSavedPatternsWordsDir) {
      for (String label : model.constVars.getLabelDictionary().keySet()) {
        assert (new File(patternsWordsDir + "/" + label).exists());
        File patf = new File(patternsWordsDir + "/" + label + "/patterns.ser");
        if (patf.exists()) {
          Counter<SurfacePattern> patterns = IOUtils.readObjectFromFile(patf);
          model.setLearnedPatterns(patterns, label);
          Redwood.log(Redwood.DBG, "Loaded " + patterns.size() + " patterns from " + patf);
        }
        File wordf = new File(patternsWordsDir + "/" + label + "/phrases.txt");
        if (wordf.exists()) {
          Counter<String> words = model.readLearnedWordsFromFile(wordf);
          model.setLearnedWords(words, label);
          Redwood.log(Redwood.DBG, "Loaded " + words.size() + " from " + patf);
        }
        CollectionValuedMap<SurfacePattern, Triple<String, Integer, Integer>> matchedTokensByPat = null;

        if (model.constVars.restrictToMatched) {
          TwoDimensionalCounter<Pair<String, String>, SurfacePattern> wordsandLemmaPatExtracted = new TwoDimensionalCounter<Pair<String, String>, SurfacePattern>();
          model.scorePhrases.applyPats(model.getLearnedPatterns(label), label, false, wordsandLemmaPatExtracted, matchedTokensByPat);
        }

        if (model.constVars.batchProcessSents) {
          for (File f : Data.sentsFiles) {
            Redwood.log(Redwood.DBG, "labeling sentences from " + f + " with the already learned words");
            Map<String, List<CoreLabel>> sentsf = IOUtils.readObjectFromFile(f);
            assert sentsf != null : "Why are sents null";
            model.labelWords(label, sentsf, model.getLearnedWords(label).keySet(), model.getLearnedPatterns(label).keySet(), sentsOutFile, matchedTokensByPat);
            IOUtils.writeObjectToFile(sentsf, f);
          }
        } else
          model.labelWords(label, Data.sents, model.getLearnedWords(label).keySet(), model.getLearnedPatterns(label).keySet(), sentsOutFile, matchedTokensByPat);
      }
    }

    if (learn)
      model.iterateExtractApply(p0, p0Set, wordsOutputFile, sentsOutFile, patternOutFile, ignorePatterns);

    if (model.constVars.markedOutputTextFile != null) {
      model.writeLabeledData(model.constVars.markedOutputTextFile);
    }

    if(model.constVars.columnOutputFile != null)
      model.writeColumnOutput(model.constVars.columnOutputFile);

    boolean savePatternsWordsDir = Boolean.parseBoolean(props.getProperty("savePatternsWordsDir"));

    if (savePatternsWordsDir) {
      for (String label : model.constVars.getLabelDictionary().keySet()) {
        IOUtils.ensureDir(new File(patternsWordsDir + "/" + label));
        IOUtils.writeObjectToFile(model.getLearnedPatterns(label), patternsWordsDir + "/" + label + "/patterns.ser");
        BufferedWriter w = new BufferedWriter(new FileWriter(patternsWordsDir + "/" + label + "/phrases.txt"));
        model.writeWordsToFile(model.getLearnedWords(label), w);
        w.close();
      }
    }

    if (evaluate) {
      // The format of goldEntitiesEvalFiles is assumed same as
      // seedwordsfiles: label,file;label2,file2;...
      // Each file of gold entities consists of each entity in newline with
      // incorrect entities marked with "#" at the end of the entity.
      // Learned entities not present in the gold file are considered
      // negative.
      String goldEntitiesEvalFiles = props.getProperty("goldEntitiesEvalFiles");
      if (goldEntitiesEvalFiles != null) {
        for (String gfile : goldEntitiesEvalFiles.split(";")) {
          String[] t = gfile.split(",");
          String label = t[0];
          String goldfile = t[1];
          Map<String, Boolean> goldWords4Label = new HashMap<String, Boolean>();
          for (String line : IOUtils.readLines(goldfile)) {
            line = line.trim();
            if (line.isEmpty())
              continue;

            if (line.endsWith("#"))
              goldWords4Label.put(line.substring(0, line.length() - 1), false);
            else
              goldWords4Label.put(line, true);
          }
          Pair<Double, Double> pr = model.getPrecisionRecall(label, goldWords4Label);
          Redwood.log(ConstantsAndVariables.minimaldebug,
            "\nFor label " + label + ": Number of gold entities is " + goldWords4Label.size() + ", Precision is " + model.df.format(pr.first() * 100)
              + ", Recall is " + model.df.format(pr.second() * 100) + ", F1 is " + model.df.format(model.FScore(pr.first(), pr.second(), 1.0) * 100)
              + "\n\n");
        }

      }
      if (saveEvalSentencesSerFileFile != null && saveEvalSentencesSerFileFile.exists()) {
        if (batchProcessSents)
          evalsents = IOUtils.readObjectFromFile(saveEvalSentencesSerFileFile);
        boolean evalPerEntity = Boolean.parseBoolean(props.getProperty("evalPerEntity", "true"));
        model.evaluate(evalsents, evalPerEntity);
      }

      if (evalsents.size() == 0 && goldEntitiesEvalFiles == null)
        System.err.println("No eval sentences or list of gold entities provided to evaluate! Make sure evalFileWithGoldLabels or goldEntitiesEvalFiles is set, or turn off the evaluate flag");

    }
    return model;
  }


  public static void main(String[] args) {
    try {
      Properties props = StringUtils.argsToPropertiesWithResolve(args);
      GetPatternsFromDataMultiClass.run(props);
    } catch (OutOfMemoryError e) {
      System.out.println("Out of memory! Either change the memory alloted by running as java -mx20g ... for example if you want to allot 20G. Or consider using batchProcessSents and numMaxSentencesPerBatchFile flags");
      e.printStackTrace();
    } catch (Exception e) {
      e.printStackTrace();
    }
  } // end main()

}
