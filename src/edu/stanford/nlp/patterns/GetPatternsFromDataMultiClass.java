package edu.stanford.nlp.patterns;

import java.io.*;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.sql.SQLException;
import java.text.DecimalFormat;
import java.time.Duration;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import javax.json.*;

import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.io.RegExFileFilter;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.CoreAnnotations.GoldAnswerAnnotation;
import edu.stanford.nlp.ling.IndexedWord;
import edu.stanford.nlp.ling.tokensregex.Env;
import edu.stanford.nlp.ling.tokensregex.TokenSequencePattern;
import edu.stanford.nlp.patterns.dep.DataInstanceDep;
import edu.stanford.nlp.patterns.surface.*;
import edu.stanford.nlp.patterns.ConstantsAndVariables.ScorePhraseMeasures;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.semgraph.SemanticGraphEdge;
import edu.stanford.nlp.sequences.IOBUtils;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.stats.Counters;
import edu.stanford.nlp.stats.TwoDimensionalCounter;
import edu.stanford.nlp.trees.GrammaticalRelation;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.TreeCoreAnnotations.TreeAnnotation;
import edu.stanford.nlp.util.*;
import edu.stanford.nlp.util.PriorityQueue;
import edu.stanford.nlp.util.TypesafeMap.Key;
import edu.stanford.nlp.util.logging.Redwood;

/**
 * Given text and a seed list, this class gives more words like the seed words
 * by learning surface word or dependency patterns.
 *
 * The multi-threaded class ({@code nthread} parameter for number of
 * threads) takes as input.
 *
 * To use the default options, run
 *
 * {@code java -mx1000m edu.stanford.nlp.patterns.GetPatternsFromDataMultiClass -file text_file -seedWordsFiles label1,seedwordlist1;label2,seedwordlist2;... -outDir output_directory (optional)}
 *
 *
 * {@code fileFormat}: (Optional) Default is text. Valid values are text
 * (or txt) and ser, where the serialized file is of the type {@code Map<String,List<CoreLabel>>}.
 *
 * {@code file}: (Required) Input file(s) (default assumed text). Can be
 * one or more of (concatenated by comma or semi-colon): file, directory, files
 * with regex in the filename (for example: "mydir/health-.*-processed.txt")
 *
 * {@code seedWordsFiles}: (Required)
 * label1,file_seed_words1;label2,file_seed_words2;... where file_seed_words are
 * files with list of seed words, one in each line
 *
 * {@code outDir}: (Optional) output directory where visualization/output
 * files are stored
 *
 * For other flags, see individual comments for each flag.
 *
 * To use a properties file, see
 * projects/core/data/edu/stanford/nlp/patterns/surface/example.properties or patterns/example.properties (depends on which codebase you are using)
 * as an example for the flags and their brief descriptions. Run the code as:
 * {@code java -mx1000m -cp classpath edu.stanford.nlp.patterns.GetPatternsFromDataMultiClass -props dir-as-above/example.properties}
 *
 * IMPORTANT: Many flags are described in the classes
 * {@link ConstantsAndVariables}, {@link edu.stanford.nlp.patterns.surface.CreatePatterns}, and
 * {@link PhraseScorer}.
 *
 * @author Sonal Gupta (sonal@cs.stanford.edu)
 */

public class GetPatternsFromDataMultiClass<E extends Pattern> implements Serializable  {

  /** A logger for this class */
  private static final Redwood.RedwoodChannels log = Redwood.channels(GetPatternsFromDataMultiClass.class);

  private static final long serialVersionUID = 1L;

  //public Map<String, Map<Integer, Set<E>>> patternsForEachToken = null;

  private PatternsForEachToken<E> patsForEachToken = null;

  public Map<String, Set<String>> wordsForOtherClass = null;

  // String channelNameLogger = "patterns";
  /**
   *
   * RlogF is from Riloff 1996, when R's denominator is (pos+neg+unlabeled)
   *
   * RlogFPosNeg is when the R's denominator is just (pos+negative) examples
   *
   * PosNegOdds is just the ratio of number of positive words to number of
   * negative
   *
   * PosNegUnlabOdds is just the ratio of number of positive words to number of
   * negative (unlabeled words + negative)
   *
   * RatioAll is pos/(neg+pos+unlabeled)
   *
   * YanGarber02 is the modified version presented in
   * "Unsupervised Learning of Generalized Names"
   *
   * LOGREG is learning a logistic regression classifier to combine weights to
   * score a phrase (Same as PhEvalInPat, except score of an unlabeled phrase is
   * computed using a logistic regression classifier)
   *
   * LOGREGlogP is learning a logistic regression classifier to combine weights
   * to score a phrase (Same as PhEvalInPatLogP, except score of an unlabeled
   * phrase is computed using a logistic regression classifier)
   *
   * SqrtAllRatio is the pattern scoring used in Gupta et al. JAMIA 2014 paper
   *
   * Below F1SeedPattern and BPB based on paper
   * "Unsupervised Method for Automatics Construction of a disease dictionary..."
   *
   * Precision, Recall, and FMeasure (controlled by fbeta flag) is ranking the patterns using
   * their precision, recall and F_beta measure
   */
  public enum PatternScoring {
    F1SeedPattern, RlogF, RlogFPosNeg, RlogFUnlabNeg, RlogFNeg, PhEvalInPat, PhEvalInPatLogP, PosNegOdds,
    YanGarber02, PosNegUnlabOdds, RatioAll, LOGREG, LOGREGlogP, SqrtAllRatio, LinICML03, kNN
  }

  enum WordScoring {
    BPB, WEIGHTEDNORM
  }

  private Map<String, Boolean> writtenPatInJustification = new HashMap<>();

  private Map<String, Counter<E>> learnedPatterns = new HashMap<>();
  //Same as learnedPatterns but with iteration information
  private Map<String, Map<Integer, Counter<E>>> learnedPatternsEachIter = new HashMap<>();
  Map<String, Counter<CandidatePhrase>> matchedSeedWords = new HashMap<>();
  public Map<String, TwoDimensionalCounter<CandidatePhrase, E>> wordsPatExtracted = new HashMap<>();

  Properties props;
  public ScorePhrases scorePhrases;
  public ConstantsAndVariables constVars;
  public CreatePatterns createPats;

  private final DecimalFormat df = new DecimalFormat("#.##");

  private boolean notComputedAllPatternsYet = true;

  /*
   * when there is only one label
   */
  public GetPatternsFromDataMultiClass(Properties props, Map<String, DataInstance> sents, Set<CandidatePhrase> seedSet, boolean labelUsingSeedSets,
      String answerLabel) throws IOException, InstantiationException, IllegalAccessException, IllegalArgumentException, InvocationTargetException,
      NoSuchMethodException, SecurityException, InterruptedException, ExecutionException, ClassNotFoundException {
    this(props, sents, seedSet, labelUsingSeedSets, PatternsAnnotations.PatternLabel1.class, answerLabel);
  }

  @SuppressWarnings("rawtypes")
  public GetPatternsFromDataMultiClass(Properties props, Map<String, DataInstance> sents, Set<CandidatePhrase> seedSet, boolean labelUsingSeedSets,
      Class answerClass, String answerLabel) throws IOException, InstantiationException, IllegalAccessException, IllegalArgumentException,
      InvocationTargetException, NoSuchMethodException, SecurityException, InterruptedException, ExecutionException, ClassNotFoundException {
    this.props = props;
    Map<String, Class<? extends TypesafeMap.Key<String>>> ansCl = new HashMap<>();
    ansCl.put(answerLabel, answerClass);

    Map<String, Class> generalizeClasses = new HashMap<>();

    Map<String, Map<Class, Object>> ignoreClasses = new HashMap<>();
    ignoreClasses.put(answerLabel, new HashMap<>());

    Map<String, Set<CandidatePhrase>> seedSets = new HashMap<>();
    seedSets.put(answerLabel, seedSet);
    setUpConstructor(sents, seedSets, labelUsingSeedSets, ansCl, generalizeClasses, ignoreClasses);

  }

  @SuppressWarnings("rawtypes")
  public GetPatternsFromDataMultiClass(Properties props, Map<String, DataInstance> sents, Set<CandidatePhrase> seedSet, boolean labelUsingSeedSets,
      String answerLabel, Map<String, Class> generalizeClasses, Map<Class, Object> ignoreClasses) throws IOException, InstantiationException,
      IllegalAccessException, IllegalArgumentException, InvocationTargetException, NoSuchMethodException, SecurityException, InterruptedException,
      ExecutionException, ClassNotFoundException {
    this(props, sents, seedSet, labelUsingSeedSets, PatternsAnnotations.PatternLabel1.class, answerLabel, generalizeClasses, ignoreClasses);
  }

  @SuppressWarnings("rawtypes")
  public GetPatternsFromDataMultiClass(Properties props, Map<String, DataInstance> sents, Set<CandidatePhrase> seedSet, boolean labelUsingSeedSets,
      Class answerClass, String answerLabel, Map<String, Class> generalizeClasses, Map<Class, Object> ignoreClasses) throws IOException,
      InstantiationException, IllegalAccessException, IllegalArgumentException, InvocationTargetException, NoSuchMethodException, SecurityException,
      InterruptedException, ExecutionException, ClassNotFoundException {
    this.props = props;
    Map<String, Class<? extends TypesafeMap.Key<String>>> ansCl = new HashMap<>();
    ansCl.put(answerLabel, answerClass);

    Map<String, Map<Class, Object>> iC = new HashMap<>();
    iC.put(answerLabel, ignoreClasses);

    Map<String, Set<CandidatePhrase>> seedSets = new HashMap<>();
    seedSets.put(answerLabel, seedSet);
    setUpConstructor(sents, seedSets, labelUsingSeedSets, ansCl, generalizeClasses, iC);
  }

  @SuppressWarnings("rawtypes")
  public GetPatternsFromDataMultiClass(Properties props, Map<String, DataInstance> sents, Map<String, Set<CandidatePhrase>> seedSets,
      boolean labelUsingSeedSets) throws IOException, InstantiationException, IllegalAccessException, IllegalArgumentException,
      InvocationTargetException, NoSuchMethodException, SecurityException, ClassNotFoundException, InterruptedException, ExecutionException {
    this.props = props;
    Map<String, Class<? extends TypesafeMap.Key<String>>> ansCl = new HashMap<>();
    Map<String, Class> gC = new HashMap<>();
    Map<String, Map<Class, Object>> iC = new HashMap<>();
    int i = 1;
    for (String label : seedSets.keySet()) {
      String ansclstr = "edu.stanford.nlp.patterns.PatternsAnnotations$PatternLabel" + i;
      ansCl.put(label, (Class<? extends Key<String>>) Class.forName(ansclstr));
      iC.put(label, new HashMap<>());
      i++;
    }

    setUpConstructor(sents, seedSets, labelUsingSeedSets, ansCl, gC, iC);
  }

  @SuppressWarnings("rawtypes")
  public GetPatternsFromDataMultiClass(Properties props, Map<String, DataInstance> sents, Map<String, Set<CandidatePhrase>> seedSets,
      boolean labelUsingSeedSets, Map<String, Class<? extends TypesafeMap.Key<String>>> answerClass) throws IOException, InstantiationException,
      IllegalAccessException, IllegalArgumentException, InvocationTargetException, NoSuchMethodException, SecurityException, InterruptedException,
      ExecutionException, ClassNotFoundException {
    this(props, sents, seedSets, labelUsingSeedSets, answerClass, new HashMap<>(), new HashMap<>());
  }

  /**
   * Generalize classes basically maps label strings to a map of generalized
   * strings and the corresponding class ignoreClasses have to be boolean.
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
  public GetPatternsFromDataMultiClass(Properties props, Map<String, DataInstance> sents, Map<String, Set<CandidatePhrase>> seedSets,
      boolean labelUsingSeedSets, Map<String, Class<? extends TypesafeMap.Key<String>>> answerClass, Map<String, Class> generalizeClasses,
      Map<String, Map<Class, Object>> ignoreClasses) throws IOException, InstantiationException, IllegalAccessException, IllegalArgumentException,
      InvocationTargetException, NoSuchMethodException, SecurityException, InterruptedException, ExecutionException, ClassNotFoundException {
    this.props = props;

    if (ignoreClasses.isEmpty()) {
      for (String label : seedSets.keySet())
        ignoreClasses.put(label, new HashMap<>());
    }
    setUpConstructor(sents, seedSets, labelUsingSeedSets, answerClass, generalizeClasses, ignoreClasses);
  }

  @SuppressWarnings("rawtypes")
  private void setUpConstructor(Map<String, DataInstance> sents, Map<String, Set<CandidatePhrase>> seedSets, boolean labelUsingSeedSets,
      Map<String, Class<? extends TypesafeMap.Key<String>>> answerClass, Map<String, Class> generalizeClasses,
      Map<String, Map<Class, Object>> ignoreClasses) throws IOException, InstantiationException, IllegalAccessException, IllegalArgumentException,
      InvocationTargetException, NoSuchMethodException, SecurityException, InterruptedException, ExecutionException, ClassNotFoundException {

    Data.sents = sents;
    ArgumentParser.fillOptions(Data.class, props);
    ArgumentParser.fillOptions(ConstantsAndVariables.class, props);
    PatternFactory.setUp(props, PatternFactory.PatternType.valueOf(props.getProperty(Flags.patternType)), seedSets.keySet());

    constVars = new ConstantsAndVariables(props, seedSets, answerClass, generalizeClasses, ignoreClasses);

    if (constVars.writeMatchedTokensFiles && constVars.batchProcessSents) {
      throw new RuntimeException(
          "writeMatchedTokensFiles and batchProcessSents cannot be true at the same time (not implemented; also doesn't make sense to save a large sentences json file)");
    }

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

    wordsPatExtracted = new HashMap<>();

    for (String label : answerClass.keySet()) {
      wordsPatExtracted.put(label, new TwoDimensionalCounter<>());
    }

    scorePhrases = new ScorePhrases(props, constVars);
    createPats = new CreatePatterns(props, constVars);
    assert !(constVars.doNotApplyPatterns && (PatternFactory.useStopWordsBeforeTerm || PatternFactory.numWordsCompoundMax > 1)) : " Cannot have both doNotApplyPatterns and (useStopWordsBeforeTerm true or numWordsCompound > 1)!";

    if(constVars.invertedIndexDirectory == null){
      File f  = File.createTempFile("inv","index");
      f.deleteOnExit();
      f.mkdir();
      constVars.invertedIndexDirectory = f.getAbsolutePath();
    }

    Set<String> extremelySmallStopWordsList = CollectionUtils.asSet(".", ",", "in", "on", "of", "a", "the", "an");

    //Function to use to how to add CoreLabels to index
    Function<CoreLabel, Map<String, String>> transformCoreLabelToString = l -> {
      Map<String, String> add = new HashMap<>();
      for (Class gn: constVars.getGeneralizeClasses().values()) {
        Object b  = l.get(gn);
        if (b != null && !b.toString().equals(constVars.backgroundSymbol)) {
          add.put(Token.getKeyForClass(gn),b.toString());
        }
      }
      return add;
    };

    boolean createIndex = false;
    if (constVars.loadInvertedIndex)
      constVars.invertedIndex = SentenceIndex.loadIndex(constVars.invertedIndexClass, props, extremelySmallStopWordsList, constVars.invertedIndexDirectory, transformCoreLabelToString);
    else {
      constVars.invertedIndex = SentenceIndex.createIndex(constVars.invertedIndexClass, null, props, extremelySmallStopWordsList, constVars.invertedIndexDirectory, transformCoreLabelToString);
      createIndex = true;
    }

    int totalNumSents = 0;

    boolean computeDataFreq = false;
    if (Data.rawFreq == null) {
      Data.rawFreq = new ClassicCounter<>();
      computeDataFreq = true;
    }

    ConstantsAndVariables.DataSentsIterator iter = new ConstantsAndVariables.DataSentsIterator(constVars.batchProcessSents);
    while(iter.hasNext()){
      Pair<Map<String, DataInstance>, File> sentsIter = iter.next();
      Map<String, DataInstance> sentsf = sentsIter.first();

      if(constVars.batchProcessSents) {
        for (Entry<String, DataInstance> en : sentsf.entrySet()) {
          Data.sentId2File.put(en.getKey(), sentsIter.second());
        }
      }

      totalNumSents += sentsf.size();

      if(computeDataFreq){
        Data.computeRawFreqIfNull(sentsf, PatternFactory.numWordsCompoundMax);
      }


      Redwood.log(Redwood.DBG, "Initializing sents size " + sentsf.size()
        + " sentences, either by labeling with the seed set or just setting the right classes");
      for (String l : constVars.getAnswerClass().keySet()) {
        Redwood.log(Redwood.DBG, "labelUsingSeedSets is " + labelUsingSeedSets + " and seed set size for " + l + " is " + (seedSets == null?"null":seedSets.get(l).size()));

        Set<CandidatePhrase> seed = seedSets == null || !labelUsingSeedSets ? new HashSet<>() : (seedSets.containsKey(l) ? seedSets.get(l)
          : new HashSet<>());

        if(!matchedSeedWords.containsKey(l)){
          matchedSeedWords.put(l, new ClassicCounter<>());
        }
        Counter<CandidatePhrase> matched = runLabelSeedWords(sentsf, constVars.getAnswerClass().get(l), l, seed, constVars, labelUsingSeedSets);
        System.out.println("matched phrases for " + l + " is " + matched);
        matchedSeedWords.get(l).addAll(matched);


        if (constVars.addIndvWordsFromPhrasesExceptLastAsNeg) {
          Redwood.log(ConstantsAndVariables.minimaldebug, "adding indv words from phrases except last as neg");
          Set<CandidatePhrase> otherseed = new HashSet<>();
          if(labelUsingSeedSets){
            for (CandidatePhrase s : seed) {
              String[] t = s.getPhrase().split("\\s+");
              for (int i = 0; i < t.length - 1; i++) {
                if (!seed.contains(t[i])) {
                  otherseed.add(CandidatePhrase.createOrGet(t[i]));
                }
              }
            }
          }

          runLabelSeedWords(sentsf, PatternsAnnotations.OtherSemanticLabel.class, "OTHERSEM", otherseed, constVars, labelUsingSeedSets);
        }

      }

      if (labelUsingSeedSets && constVars.getOtherSemanticClassesWords() != null) {
        String l = "OTHERSEM";
        if(!matchedSeedWords.containsKey(l)){
          matchedSeedWords.put(l, new ClassicCounter<>());
        }
        matchedSeedWords.get(l).addAll(runLabelSeedWords(sentsf, PatternsAnnotations.OtherSemanticLabel.class, l, constVars.getOtherSemanticClassesWords(), constVars, labelUsingSeedSets));
      }

      if(constVars.removeOverLappingLabelsFromSeed){
        removeOverLappingLabels(sentsf);
      }

      if(createIndex)
        constVars.invertedIndex.add(sentsf, true);

      if(sentsIter.second().exists()){
        Redwood.log(Redwood.DBG, "Saving the labeled seed sents (if given the option) to the same file " + sentsIter.second());
        IOUtils.writeObjectToFile(sentsf, sentsIter.second());
      }

    }


    Redwood.log(Redwood.DBG, "Done loading/creating inverted index of tokens and labeling data with total of "
        + constVars.invertedIndex.size() + " sentences");

    //If the scorer class is LearnFeatWt then individual word class is added as a feature
    if (scorePhrases.phraseScorerClass.equals(ScorePhrasesAverageFeatures.class) && (constVars.usePatternEvalWordClass || constVars.usePhraseEvalWordClass)) {

      if (constVars.externalFeatureWeightsDir == null) {
        File f = File.createTempFile("tempfeat", ".txt");
        f.delete();
        f.deleteOnExit();
        constVars.externalFeatureWeightsDir = f.getAbsolutePath();
      }

      IOUtils.ensureDir(new File(constVars.externalFeatureWeightsDir));

      for (String label : seedSets.keySet()) {
        String externalFeatureWeightsFileLabel = constVars.externalFeatureWeightsDir + "/" + label;
        File f = new File(externalFeatureWeightsFileLabel);
        if (!f.exists()) {
          Redwood.log(Redwood.DBG, "externalweightsfile for the label " + label + " does not exist: learning weights!");
          LearnImportantFeatures lmf = new LearnImportantFeatures();
          ArgumentParser.fillOptions(lmf, props);
          lmf.answerClass = answerClass.get(label);
          lmf.answerLabel = label;
          lmf.setUp();
          lmf.getTopFeatures(new ConstantsAndVariables.DataSentsIterator(constVars.batchProcessSents), constVars.perSelectRand, constVars.perSelectNeg,
              externalFeatureWeightsFileLabel);

        }
        Counter<Integer> distSimWeightsLabel = new ClassicCounter<>();
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
      Counter<CandidatePhrase> dictOddsWeightsLabel = new ClassicCounter<>();
      Counter<CandidatePhrase> otherSemanticClassFreq = new ClassicCounter<>();
      for (CandidatePhrase s : constVars.getOtherSemanticClassesWords()) {
        for (String s1 : StringUtils.getNgrams(Arrays.asList(s.getPhrase().split("\\s+")), 1, PatternFactory.numWordsCompoundMax))
          otherSemanticClassFreq.incrementCount(CandidatePhrase.createOrGet(s1));
      }
      otherSemanticClassFreq = Counters.add(otherSemanticClassFreq, 1.0);
      // otherSemanticClassFreq.setDefaultReturnValue(1.0);

      Map<String, Counter<CandidatePhrase>> labelDictNgram = new HashMap<>();
      for (String label : seedSets.keySet()) {
        Counter<CandidatePhrase> classFreq = new ClassicCounter<>();
        for (CandidatePhrase s : seedSets.get(label)) {
          for (String s1 : StringUtils.getNgrams(Arrays.asList(s.getPhrase().split("\\s+")), 1, PatternFactory.numWordsCompoundMax))
            classFreq.incrementCount(CandidatePhrase.createOrGet(s1));
        }
        classFreq = Counters.add(classFreq, 1.0);
        labelDictNgram.put(label, classFreq);
        // classFreq.setDefaultReturnValue(1.0);
      }

      for (String label : seedSets.keySet()) {
        Counter<CandidatePhrase> otherLabelFreq = new ClassicCounter<>();
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

    //Redwood.log(Redwood.DBG, "All options are:" + "\n" + Maps.toString(getAllOptions(), "","","\t","\n"));
  }

  public PatternsForEachToken getPatsForEachToken() {
    return patsForEachToken;
  }

  /**
   * If a token is labeled for two or more labels, then keep the one that has the longest matching phrase. For example, "lung" as BODYPART label and "lung cancer" as DISEASE label,
   * keep only the DISEASE label for "lung". For this to work, you need to have {@code PatternsAnnotations.Ln} set, which is already done in runLabelSeedWords function.
   */
  private void removeOverLappingLabels(Map<String, DataInstance> sents){
    for(Map.Entry<String, DataInstance> sentEn: sents.entrySet()){

      for(CoreLabel l : sentEn.getValue().getTokens()){
        Map<String, CandidatePhrase> longestMatchingMap = l.get(PatternsAnnotations.LongestMatchedPhraseForEachLabel.class);
        String longestMatchingString = "";
        String longestMatchingLabel = null;
        for(Map.Entry<String, CandidatePhrase> en: longestMatchingMap.entrySet()){
          if(en.getValue().getPhrase().length() > longestMatchingString.length()){
              longestMatchingLabel = en.getKey();
              longestMatchingString = en.getValue().getPhrase();
          }
        }

        if(longestMatchingLabel  != null){

          if(!"OTHERSEM".equals(longestMatchingLabel))
             l.set(PatternsAnnotations.OtherSemanticLabel.class, constVars.backgroundSymbol);

          for(Entry<String, Class<? extends Key<String>>> en: constVars.getAnswerClass().entrySet()) {
            if (!en.getKey().equals(longestMatchingLabel)){
              l.set(en.getValue(), constVars.backgroundSymbol);
            }
            else
              l.set(en.getValue(), en.getKey());
          }
        }
      }
    }
  }

  public static Map<String, DataInstance> runPOSNERParseOnTokens(Map<String, DataInstance> sents, Properties propsoriginal){

    PatternFactory.PatternType type = PatternFactory.PatternType.valueOf(propsoriginal.getProperty(Flags.patternType));
    Properties props = new Properties();
    List<String> anns = new ArrayList<>();
    anns.add("pos");
    anns.add("lemma");

    boolean useTargetParserParentRestriction = Boolean.parseBoolean(propsoriginal.getProperty(Flags.useTargetParserParentRestriction));
    boolean useTargetNERRestriction = Boolean.parseBoolean(propsoriginal.getProperty(Flags.useTargetNERRestriction));
    String posModelPath = props.getProperty(Flags.posModelPath);
    String numThreads = propsoriginal.getProperty(Flags.numThreads);

    if (useTargetParserParentRestriction){
      anns.add("parse");
    } else if(type.equals(PatternFactory.PatternType.DEP))
      anns.add("depparse");

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

    for(Map.Entry<String, DataInstance> en: sents.entrySet()) {
      List<CoreMap> temp = new ArrayList<>();
      CoreMap s= new ArrayCoreMap();
      s.set(CoreAnnotations.TokensAnnotation.class, en.getValue().getTokens());
      temp.add(s);
      Annotation doc = new Annotation(temp);
      try {
        pipeline.annotate(doc);
        if (useTargetParserParentRestriction)
          inferParentParseTag(s.get(TreeAnnotation.class));
      } catch (Exception e) {
        log.warn("Ignoring error: for sentence  " + StringUtils.joinWords(en.getValue().getTokens(), " "));
        log.warn(e);
      }

    }

    Redwood.log(Redwood.DBG, "Done annotating text");
    return sents;
  }

  public static Map<String, DataInstance> runPOSNEROnTokens(List<CoreMap> sentsCM, String posModelPath, boolean useTargetNERRestriction,
      String prefix, boolean useTargetParserParentRestriction, String numThreads, PatternFactory.PatternType type) {
    Annotation doc = new Annotation(sentsCM);

    Properties props = new Properties();
    List<String> anns = new ArrayList<>();
    anns.add("pos");
    anns.add("lemma");

    if (useTargetParserParentRestriction){
      anns.add("parse");
    } else if(type.equals(PatternFactory.PatternType.DEP))
      anns.add("depparse");

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

    Map<String, DataInstance> sents = new HashMap<>();

    for (CoreMap s : doc.get(CoreAnnotations.SentencesAnnotation.class)) {
      if (useTargetParserParentRestriction)
        inferParentParseTag(s.get(TreeAnnotation.class));
      DataInstance d = DataInstance.getNewInstance(type, s);
      sents.put(prefix + s.get(CoreAnnotations.DocIDAnnotation.class), d);
    }

    return sents;
  }

  static StanfordCoreNLP pipeline = null;

  public static int tokenize(Iterator<String> textReader, String posModelPath, boolean lowercase, boolean useTargetNERRestriction, String sentIDPrefix,
                             boolean useTargetParserParentRestriction, String numThreads, boolean batchProcessSents, int numMaxSentencesPerBatchFile,
                             File saveSentencesSerDirFile, Map<String, DataInstance> sents, int numFilesTillNow, PatternFactory.PatternType type) throws InterruptedException, ExecutionException,
    IOException {
    if (pipeline == null) {
      Properties props = new Properties();
      List<String> anns = new ArrayList<>();
      anns.add("tokenize");
      anns.add("ssplit");
      anns.add("pos");
      anns.add("lemma");

      if (useTargetParserParentRestriction){
        anns.add("parse");
      }
      if(type.equals(PatternFactory.PatternType.DEP))
        anns.add("depparse");

      if (useTargetNERRestriction) {
        anns.add("ner");
      }

      props.setProperty("annotators", StringUtils.join(anns, ","));
      props.setProperty("parse.maxlen", "80");
      if(numThreads != null)
        props.setProperty("threads", numThreads);

      props.setProperty("tokenize.options", "ptb3Escaping=false,normalizeParentheses=false,escapeForwardSlashAsterisk=false");

      if (posModelPath != null) {
        props.setProperty("pos.model", posModelPath);
      }
      pipeline = new StanfordCoreNLP(props);
    }

    String text = "";
    int numLines = 0;
    while(textReader.hasNext()) {
      String line = textReader.next();
      numLines ++;
      if (batchProcessSents && numLines > numMaxSentencesPerBatchFile) {
        break;
      }
      if (lowercase)
        line = line.toLowerCase();
      text += line+"\n";
    }

    Annotation doc = new Annotation(text);
    pipeline.annotate(doc);


    int i = -1;
    for (CoreMap s : doc.get(CoreAnnotations.SentencesAnnotation.class)) {
      i++;
      if (useTargetParserParentRestriction)
        inferParentParseTag(s.get(TreeAnnotation.class));
      DataInstance d = DataInstance.getNewInstance(type, s);
      sents.put(sentIDPrefix + i, d);

//      if (batchProcessSents && sents.size() >= numMaxSentencesPerBatchFile) {
//        numFilesTillNow++;
//        File file = new File(saveSentencesSerDirFile + "/sents_" + numFilesTillNow);
//        IOUtils.writeObjectToFile(sents, file);
//        sents = new HashMap<String, DataInstance>();
//        Data.sentsFiles.add(file);
//      }
    }

    Redwood.log(Redwood.DBG, "Done annotating text with " + i + " sentences");

    if (sents.size() > 0 && batchProcessSents) {
      numFilesTillNow++;
      File file = new File(saveSentencesSerDirFile + "/sents_" + numFilesTillNow);
      IOUtils.writeObjectToFile(sents, file);
      Data.sentsFiles.add(file);

      for(String sentid: sents.keySet()) {
        assert !Data.sentId2File.containsKey(sentid) : "Data.sentId2File already contains " + sentid + ". Make sure sentIds are unique!";
        Data.sentId2File.put(sentid, file);
      }
      sents.clear();
    }
    // not lugging around sents if batch processing
    if (batchProcessSents)
      sents = null;
    return numFilesTillNow;
  }

  /*
  public static int tokenize(String text, String posModelPath, boolean lowercase, boolean useTargetNERRestriction, String sentIDPrefix,
      boolean useTargetParserParentRestriction, String numThreads, boolean batchProcessSents, int numMaxSentencesPerBatchFile,
      File saveSentencesSerDirFile, Map<String, DataInstance> sents, int numFilesTillNow) throws InterruptedException, ExecutionException,
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
        sents = new HashMap<String, DataInstance>();
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
*/

  private static void inferParentParseTag(Tree tree) {

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
   * @param l1 array you want to find in l2
   * @param l2
   * @return starting index of the sublist
   */
  public static List<Integer> getSubListIndex(String[] l1, String[] l2, String[] subl2, Set<String> doNotLabelTheseWords, HashSet<String> seenFuzzyMatches,
      int minLen4Fuzzy, boolean fuzzyMatch, boolean ignoreCaseSeedMatch) {
    if (l1.length > l2.length)
      return null;
    EditDistance editDistance = new EditDistance(true);
    List<Integer> allIndices = new ArrayList<>();
    boolean matched = false;
    int index = -1;
    int lastUnmatchedIndex = 0;
    for (int i = 0; i < l2.length;) {

      for (int j = 0; j < l1.length;) {
        boolean d1 = false, d2 = false;
        boolean compareFuzzy = true;
        if (!fuzzyMatch || doNotLabelTheseWords.contains(l2[i]) || doNotLabelTheseWords.contains(subl2[i]) || l2[i].length() <= minLen4Fuzzy || subl2[i].length() <= minLen4Fuzzy)
          compareFuzzy = false;
        if (compareFuzzy == false || l1[j].length() <= minLen4Fuzzy) {
          d1 = (ignoreCaseSeedMatch && l1[j].equalsIgnoreCase(l2[i])) || l1[j].equals(l2[i]);
          if (!d1 && fuzzyMatch)
            d2 = (ignoreCaseSeedMatch && subl2[i].equalsIgnoreCase(l1[j])) || subl2[i].equals(l1[j]);
        } else {
          String combo = l1[j] + "#" + l2[i];
          if ((ignoreCaseSeedMatch && l1[j].equalsIgnoreCase(l2[i])) || l1[j].equals(l2[i])  || seenFuzzyMatches.contains(combo))
            d1 = true;
          else {
            d1 = editDistance.score(l1[j], l2[i]) <= 1;
            if (!d1) {
              String combo2 = l1[j] + "#" + subl2[i];
              if ((ignoreCaseSeedMatch && l1[j].equalsIgnoreCase(subl2[i]) )||l1[j].equals(subl2[i]) || seenFuzzyMatches.contains(combo2))
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

  //if matchcontextlowercase is on, transform that. escape the word etc. Useful for pattern matching later on
  private static Function<CoreLabel, String> stringTransformationFunction = new Function<CoreLabel, String>() {
    @Override
    public String apply(CoreLabel l) {
      String s;
      if(PatternFactory.useLemmaContextTokens){
        s = l.lemma();
        assert s!=null : "Lemma is null and useLemmaContextTokens is true";
      }
      else
        s= l.word();
      if(ConstantsAndVariables.matchLowerCaseContext)
        s = s.toLowerCase();
      assert s!= null;
      return s;
    }
  };

  public static<E> List<List<E>> getThreadBatches(List<E> keyset, int numThreads){
    int num;
    if (numThreads == 1)
      num = keyset.size();
    else
      num = keyset.size() / (numThreads - 1);
    Redwood.log(ConstantsAndVariables.extremedebug, "keyset size is " + keyset.size());
    List<List<E>> threadedSentIds = new ArrayList<>();
    for (int i = 0; i < numThreads; i++) {
      List<E> keys = keyset.subList(i * num, Math.min(keyset.size(), (i + 1) * num));
      threadedSentIds.add(keys);
      Redwood.log(ConstantsAndVariables.extremedebug, "assigning from " + i * num + " till " + Math.min(keyset.size(), (i + 1) * num));
    }
    return threadedSentIds;
  }

  /** Warning: sets labels of words that are not in the given seed set as O!!!
   * */
  public static Counter<CandidatePhrase> runLabelSeedWords(Map<String, DataInstance> sents, Class answerclass, String label, Collection<CandidatePhrase> seedWords, ConstantsAndVariables constVars, boolean overwriteExistingLabels)
      throws InterruptedException, ExecutionException, IOException {

    Redwood.log(Redwood.DBG,"ignoreCaseSeedMatch is " + constVars.ignoreCaseSeedMatch);
    List<List<String>> threadedSentIds = getThreadBatches(new ArrayList<>(sents.keySet()), constVars.numThreads);
    ExecutorService executor = Executors.newFixedThreadPool(constVars.numThreads);
    List<Future<Pair<Map<String, DataInstance>, Counter<CandidatePhrase>>>> list = new ArrayList<>();
    Counter<CandidatePhrase> matchedPhrasesCounter = new ClassicCounter<>();
    for (List<String> keys: threadedSentIds) {
      Callable<Pair<Map<String, DataInstance>, Counter<CandidatePhrase>>> task = new LabelWithSeedWords(seedWords, sents, keys, answerclass, label, constVars.fuzzyMatch, constVars.minLen4FuzzyForPattern, constVars.backgroundSymbol, constVars.getEnglishWords(),
        stringTransformationFunction, constVars.writeMatchedTokensIdsForEachPhrase, overwriteExistingLabels, constVars.patternType, constVars.ignoreCaseSeedMatch);
      Pair<Map<String, DataInstance>, Counter<CandidatePhrase>> sentsi  = executor.submit(task).get();
      sents.putAll(sentsi.first());
      matchedPhrasesCounter.addAll(sentsi.second());
    }
    executor.shutdown();
    Redwood.log("extremedebug","Matched phrases freq is " + matchedPhrasesCounter);
    return matchedPhrasesCounter;
  }

  public static void getFeatures(SemanticGraph graph, IndexedWord vertex, boolean isHead, Collection<String> features, GrammaticalRelation reln){
    if(isHead){
      List<Pair<GrammaticalRelation, IndexedWord>> pt = graph.parentPairs(vertex);
      for(Pair<GrammaticalRelation, IndexedWord> en: pt) {
        features.add("PARENTREL-" + en.first());
      }
    } else{
      //find the relation to the parent
      if(reln == null){
        List<SemanticGraphEdge> parents = graph.getOutEdgesSorted(vertex);
        if(parents.size() > 0)
        reln = parents.get(0).getRelation();
      }
      if(reln != null)
        features.add("REL-" + reln.getShortName());
    }
    //System.out.println("For graph " + graph.toFormattedString() + " and vertex " + vertex + " the features are " + features);
  }


  /**
   * Warning: sets labels of words that are not in the given seed set as O!!!
   */
  @SuppressWarnings("rawtypes")
  public static class LabelWithSeedWords implements Callable<Pair<Map<String, DataInstance>, Counter<CandidatePhrase>>> {
    Map<CandidatePhrase, String[]> seedwordsTokens = new HashMap<>();
    Map<String, DataInstance> sents;
    List<String> keyset;
    Class labelClass;
    HashSet<String> seenFuzzyMatches = new HashSet<>();
    String label;
    int minLen4FuzzyForPattern;
    String backgroundSymbol = "O";
    Set<String> doNotLabelDictWords = null;
    Function<CoreLabel, String> stringTransformation;
    boolean writeMatchedTokensIdsForEachPhrase = false;
    boolean overwriteExistingLabels;
    PatternFactory.PatternType patternType;
    boolean fuzzyMatch = false;
    Map<String, String> ignoreCaseSeedMatch;

    public LabelWithSeedWords(Collection<CandidatePhrase> seedwords, Map<String, DataInstance> sents, List<String> keyset, Class labelclass, String label, boolean fuzzyMatch,
                              int minLen4FuzzyForPattern, String backgroundSymbol, Set<String> doNotLabelDictWords,
                              Function<CoreLabel, String> stringTransformation, boolean writeMatchedTokensIdsForEachPhrase, boolean overwriteExistingLabels, PatternFactory.PatternType type,
                              Map<String, String> ignoreCaseSeedMatch) {
      for (CandidatePhrase s : seedwords)
        this.seedwordsTokens.put(s, s.getPhrase().split("\\s+"));
      this.sents = sents;
      this.keyset = keyset;
      this.labelClass = labelclass;
      this.label = label;
      this.minLen4FuzzyForPattern= minLen4FuzzyForPattern;
      this.backgroundSymbol = backgroundSymbol;
      this.doNotLabelDictWords = doNotLabelDictWords;
      this.stringTransformation = stringTransformation;
      this.writeMatchedTokensIdsForEachPhrase = writeMatchedTokensIdsForEachPhrase;
      this.overwriteExistingLabels = overwriteExistingLabels;
      this.patternType = type;
      this.fuzzyMatch = fuzzyMatch;
      this.ignoreCaseSeedMatch = ignoreCaseSeedMatch;
    }

    @SuppressWarnings("unchecked")
    @Override
    public Pair<Map<String, DataInstance>,Counter<CandidatePhrase>> call()  {
      Map<String, DataInstance> newsent = new HashMap<>();
      Counter<CandidatePhrase> matchedPhrasesCounter = new ClassicCounter<>();
      for (String k : keyset) {
        DataInstance sent = sents.get(k);
        List<CoreLabel> tokensCore = sent.getTokens();

        SemanticGraph graph = null;
        if(patternType.equals(PatternFactory.PatternType.DEP)){
          graph = ((DataInstanceDep)sent).getGraph();
        }

        String[] tokens = new String[tokensCore.size()];
        String[] tokenslemma = new String[tokensCore.size()];
        int num = 0;
        for (CoreLabel l : tokensCore) {

          //Setting the processedTextAnnotation, used in indexing and pattern matching
          l.set(PatternsAnnotations.ProcessedTextAnnotation.class, stringTransformation.apply(l));

          tokens[num] = l.word();
          if(fuzzyMatch && l.lemma() == null)
            throw new RuntimeException("how come lemma is null");
          tokenslemma[num] = l.lemma();

          num++;
        }
        boolean[] labels = new boolean[tokens.length];

        CollectionValuedMap<Integer, CandidatePhrase> matchedPhrases = new CollectionValuedMap<>();
        Map<Integer, CandidatePhrase> longestMatchedPhrases = new HashMap<>();

        for (Entry<CandidatePhrase, String[]> sEn : seedwordsTokens.entrySet()) {
          String[] s = sEn.getValue();
          CandidatePhrase sc = sEn.getKey();
          List<Integer> indices = getSubListIndex(s, tokens, tokenslemma, doNotLabelDictWords, seenFuzzyMatches,
              minLen4FuzzyForPattern, fuzzyMatch, (ignoreCaseSeedMatch.containsKey(label) ? Boolean.valueOf(ignoreCaseSeedMatch.get(label))  : false));

          if (indices != null && !indices.isEmpty()){
            String ph = StringUtils.join(s, " ");
            sc.addFeature("LENGTH-" + s.length, 1.0);

            Collection<String> features = new ArrayList<>();

            for (int index : indices){
              if(graph != null){
                GetPatternsFromDataMultiClass.getFeatures(graph, graph.getNodeByIndex(index + 1), true, features, null);
              }

              if(writeMatchedTokensIdsForEachPhrase) {
                addToMatchedTokensByPhrase(ph, k, index, s.length);
              }

              for (int i = 0; i < s.length; i++) {
                matchedPhrases.add(index + i, sc);

                if(graph != null){
                  try{
                  GetPatternsFromDataMultiClass.getFeatures(graph, graph.getNodeByIndex(index+ i + 1), false, features, null);
                  } catch(Exception e) { log.warn(e); }
                }

                CandidatePhrase longPh = longestMatchedPhrases.get(index+i);
                longPh = longPh != null && longPh.getPhrase().length() > sc.getPhrase().length() ? longPh: sc;
                longestMatchedPhrases.put(index+i, longPh);

                labels[index + i] = true;
              }
            }
          sc.addFeatures(features);
          }
        }
        int i = -1;
        for (CoreLabel l : sent.getTokens()) {
          i++;

          //The second clause is for old sents ser files compatibility reason
          if (!l.containsKey(PatternsAnnotations.MatchedPhrases.class) || !(PatternsAnnotations.MatchedPhrases.class.isInstance(l.get(PatternsAnnotations.MatchedPhrases.class))))
            l.set(PatternsAnnotations.MatchedPhrases.class, new CollectionValuedMap<>());

          if(!l.containsKey(PatternsAnnotations.LongestMatchedPhraseForEachLabel.class))
            l.set(PatternsAnnotations.LongestMatchedPhraseForEachLabel.class, new HashMap<>());

          if (labels[i]) {
            l.set(labelClass, label);

            //set whether labeled by the seeds or not
            if(!l.containsKey(PatternsAnnotations.SeedLabeledOrNot.class))
              l.set(PatternsAnnotations.SeedLabeledOrNot.class, new HashMap<>());
            l.get(PatternsAnnotations.SeedLabeledOrNot.class).put(labelClass, true);


            CandidatePhrase longestMatchingPh = l.get(PatternsAnnotations.LongestMatchedPhraseForEachLabel.class).get(label);
            assert longestMatchedPhrases.containsKey(i);
            longestMatchingPh = (longestMatchingPh != null && (longestMatchingPh.getPhrase().length() > longestMatchedPhrases.get(i).getPhrase().length())) ? longestMatchingPh : longestMatchedPhrases.get(i);
            l.get(PatternsAnnotations.LongestMatchedPhraseForEachLabel.class).put(label, longestMatchingPh);
            matchedPhrasesCounter.incrementCount(longestMatchingPh, 1.0);
            l.get(PatternsAnnotations.MatchedPhrases.class).addAll(label, matchedPhrases.get(i));

            Redwood.log(ConstantsAndVariables.extremedebug, "labeling " + l.word() + " or its lemma " + l.lemma() + " as " + label
              + " because of the dict phrases " + matchedPhrases.get(i));

          } else if(overwriteExistingLabels)
            l.set(labelClass, backgroundSymbol);


        }
        newsent.put(k, sent);
      }
      return new Pair(newsent, matchedPhrasesCounter);
    }
  }

  private static void addToMatchedTokensByPhrase(String ph, String sentid, int index, int length){
    if(!Data.matchedTokensForEachPhrase.containsKey(ph))
      Data.matchedTokensForEachPhrase.put(ph, new HashMap<>());
    Map<String, List<Integer>> matcheds = Data.matchedTokensForEachPhrase.get(ph);
    if(!matcheds.containsKey(sentid))
      matcheds.put(sentid, new ArrayList<>());
    for (int i = 0; i < length; i++)
      matcheds.get(sentid).add(index + i);
  }

  public Map<String, TwoDimensionalCounter<E, CandidatePhrase>> patternsandWords = null;
  //public Map<String, TwoDimensionalCounter<E, String>> allPatternsandWords = null;
  public Map<String, Counter<E>> currentPatternWeights = null;

  //deleteExistingIndex is def false for the second call to this function
  public void processSents(Map<String, DataInstance> sents, Boolean deleteExistingIndex) throws IOException, ClassNotFoundException {

    if (constVars.computeAllPatterns) {
        props.setProperty("createTable", deleteExistingIndex.toString());
        props.setProperty("deleteExisting", deleteExistingIndex.toString());
        props.setProperty("createPatLuceneIndex", deleteExistingIndex.toString());
        Redwood.log(Redwood.DBG, "Computing all patterns");
        createPats.getAllPatterns(sents, props, constVars.storePatsForEachToken);
      }
    else
      Redwood.log(Redwood.DBG, "Reading patterns from existing dir");

    props.setProperty("createTable", "false");
    props.setProperty("deleteExisting","false");
    props.setProperty("createPatLuceneIndex","false");

  }

  private void readSavedPatternsAndIndex() throws IOException, ClassNotFoundException {
    if(!constVars.computeAllPatterns) {
      assert constVars.allPatternsDir != null : "allPatternsDir flag cannot be empty if computeAllPatterns is false!";
      //constVars.setPatternIndex(PatternIndex.load(constVars.allPatternsDir, constVars.storePatsIndex));
      if(constVars.storePatsForEachToken.equals(ConstantsAndVariables.PatternForEachTokenWay.MEMORY))
        patsForEachToken.load(constVars.allPatternsDir);
    }
  }

  @SuppressWarnings({ "unchecked" })
  public Counter<E> getPatterns(String label, Set<E> alreadyIdentifiedPatterns, E p0, Counter<CandidatePhrase> p0Set,
      Set<E> ignorePatterns) throws IOException, ClassNotFoundException {

    TwoDimensionalCounter<E, CandidatePhrase> patternsandWords4Label = new TwoDimensionalCounter<>();
    TwoDimensionalCounter<E, CandidatePhrase> negPatternsandWords4Label = new TwoDimensionalCounter<>();
    //TwoDimensionalCounter<E, String> posnegPatternsandWords4Label = new TwoDimensionalCounter<E, String>();
    TwoDimensionalCounter<E, CandidatePhrase> unLabeledPatternsandWords4Label = new TwoDimensionalCounter<>();
    //TwoDimensionalCounter<E, String> negandUnLabeledPatternsandWords4Label = new TwoDimensionalCounter<E, String>();
    //TwoDimensionalCounter<E, String> allPatternsandWords4Label = new TwoDimensionalCounter<E, String>();
    Set<String> allCandidatePhrases = new HashSet<>();

    ConstantsAndVariables.DataSentsIterator sentsIter = new ConstantsAndVariables.DataSentsIterator(constVars.batchProcessSents);

    boolean firstCallToProcessSents = true;
    while(sentsIter.hasNext()){
      Pair<Map<String, DataInstance>, File> sentsPair = sentsIter.next();
      if(notComputedAllPatternsYet){
        //in the first iteration
        processSents(sentsPair.first(), firstCallToProcessSents);
        firstCallToProcessSents = false;
        if(patsForEachToken == null){
          //in the first iteration, for the first file
          patsForEachToken = PatternsForEachToken.getPatternsInstance(props, constVars.storePatsForEachToken);
          readSavedPatternsAndIndex();
        }
      }
      this.calculateSufficientStats(sentsPair.first(), patsForEachToken, label, patternsandWords4Label, negPatternsandWords4Label, unLabeledPatternsandWords4Label, allCandidatePhrases);
    }

    notComputedAllPatternsYet = false;

    if (constVars.computeAllPatterns){
      if(constVars.storePatsForEachToken.equals(ConstantsAndVariables.PatternForEachTokenWay.DB))
        patsForEachToken.createIndexIfUsingDBAndNotExists();


//        String systemdir = System.getProperty("java.io.tmpdir");
//        File tempFile= File.createTempFile("patterns", ".tmp", new File(systemdir));
//        tempFile.deleteOnExit();
//        tempFile.delete();
//        constVars.allPatternsDir = tempFile.getAbsolutePath();


      if(constVars.allPatternsDir != null){
        IOUtils.ensureDir(new File(constVars.allPatternsDir));
        patsForEachToken.save(constVars.allPatternsDir);
      }
      //savePatternIndex(constVars.allPatternsDir);
    }

    patsForEachToken.close();

    //This is important. It makes sure that we don't recompute patterns in every iteration!
    constVars.computeAllPatterns = false;


    if (patternsandWords == null)
      patternsandWords = new HashMap<>();
    if (currentPatternWeights == null)
      currentPatternWeights = new HashMap<>();

    Counter<E> currentPatternWeights4Label = new ClassicCounter<>();

    Set<E> removePats = enforceMinSupportRequirements(patternsandWords4Label, unLabeledPatternsandWords4Label);
    Counters.removeKeys(patternsandWords4Label, removePats);
    Counters.removeKeys(unLabeledPatternsandWords4Label, removePats);
    Counters.removeKeys(negPatternsandWords4Label, removePats);

    ScorePatterns scorePatterns;

    Class<?> patternscoringclass = getPatternScoringClass(constVars.patternScoring);

    if (patternscoringclass != null && patternscoringclass.equals(ScorePatternsF1.class)) {
      scorePatterns = new ScorePatternsF1(constVars, constVars.patternScoring, label, allCandidatePhrases, patternsandWords4Label, negPatternsandWords4Label,
          unLabeledPatternsandWords4Label, props, p0Set, p0);
      Counter<E> finalPat = scorePatterns.score();
      Counters.removeKeys(finalPat, alreadyIdentifiedPatterns);
      Counters.retainNonZeros(finalPat);
      Counters.retainTop(finalPat, constVars.numPatterns);
      if (Double.isNaN(Counters.max(finalPat)))
        throw new RuntimeException("how is the value NaN");
      Redwood.log(ConstantsAndVariables.minimaldebug, "Selected Patterns: " + finalPat);
      return finalPat;

    } else if (patternscoringclass != null && patternscoringclass.equals(ScorePatternsRatioModifiedFreq.class)) {
      scorePatterns = new ScorePatternsRatioModifiedFreq(constVars, constVars.patternScoring, label, allCandidatePhrases, patternsandWords4Label,
          negPatternsandWords4Label, unLabeledPatternsandWords4Label, phInPatScoresCache, scorePhrases, props);

    } else if (patternscoringclass != null && patternscoringclass.equals(ScorePatternsFreqBased.class)) {
      scorePatterns = new ScorePatternsFreqBased(constVars, constVars.patternScoring, label, allCandidatePhrases, patternsandWords4Label, negPatternsandWords4Label,
          unLabeledPatternsandWords4Label, props);

    } else if (constVars.patternScoring.equals(PatternScoring.kNN)) {
      try {
        Class<? extends ScorePatterns> clazz = (Class<? extends ScorePatterns>) Class.forName("edu.stanford.nlp.patterns.ScorePatternsKNN");
        Constructor<? extends ScorePatterns> ctor = clazz.getConstructor(ConstantsAndVariables.class, PatternScoring.class, String.class, Set.class,
            TwoDimensionalCounter.class, TwoDimensionalCounter.class, TwoDimensionalCounter.class, ScorePhrases.class, Properties.class);
        scorePatterns = ctor.newInstance(constVars, constVars.patternScoring, label, allCandidatePhrases, patternsandWords4Label, negPatternsandWords4Label,
            unLabeledPatternsandWords4Label, scorePhrases, props);

      } catch (ClassNotFoundException e) {
        throw new RuntimeException("kNN pattern scoring is not released yet. Stay tuned.");
      } catch (NoSuchMethodException | InvocationTargetException | InstantiationException | IllegalAccessException e) {
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
      Redwood.log(ConstantsAndVariables.extremedebug, "Patterns size is " + currentPatternWeights4Label.size());
        Counters.removeKeys(currentPatternWeights4Label, alreadyIdentifiedPatterns);
      Redwood.log(ConstantsAndVariables.extremedebug, "Removing already identified patterns of size  " + alreadyIdentifiedPatterns.size()
          + ". New patterns size " + currentPatternWeights4Label.size());
    }

    PriorityQueue<E> q = Counters.toPriorityQueue(currentPatternWeights4Label);
    int num = 0;

    Counter<E> chosenPat = new ClassicCounter<>();

    Set<E> removePatterns = new HashSet<>();

    Set<E> removeIdentifiedPatterns = null;

    while (num < constVars.numPatterns && !q.isEmpty()) {
      E pat = q.removeFirst();
      //E pat = constVars.getPatternIndex().get(patindex);

      if (currentPatternWeights4Label.getCount(pat) < constVars.thresholdSelectPattern) {
        Redwood.log(Redwood.DBG, "The max weight of candidate patterns is " + df.format(currentPatternWeights4Label.getCount(pat))
            + " so not adding anymore patterns");
        break;
      }
      boolean notchoose = false;
      if (!unLabeledPatternsandWords4Label.containsFirstKey(pat) || unLabeledPatternsandWords4Label.getCounter(pat).isEmpty()) {
        Redwood.log(ConstantsAndVariables.extremedebug, "Removing pattern " + pat + " because it has no unlab support; pos words: "
            + patternsandWords4Label.getCounter(pat));
        notchoose = true;
        continue;
      }

      Set<E> removeChosenPats = null;

      if (!notchoose) {
        if (alreadyIdentifiedPatterns != null) {
          for (E p : alreadyIdentifiedPatterns) {
            if (Pattern.subsumes(constVars.patternType, pat, p)) {
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
                removeIdentifiedPatterns = new HashSet<>();

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
        for (Pattern p : chosenPat.keySet()) {
          //E p = constVars.getPatternIndex().get(pindex);

          boolean removeChosenPatFlag = false;
          if (Pattern.sameGenre(constVars.patternType, pat, p)) {

            if(Pattern.subsumes(constVars.patternType, pat, p)){
              Redwood.log(ConstantsAndVariables.extremedebug, "Not choosing pattern " + pat
                  + " because it is contained in or contains the already chosen pattern " + p);
              notchoose = true;
              break;
            }
            else if (E.subsumes(constVars.patternType, p, pat)) {
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
                removeChosenPats = new HashSet<>();
              removeChosenPats.add(pat);
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
    Redwood.log(ConstantsAndVariables.minimaldebug, "\n\n## Selected Patterns for " + label + "##\n");
    List<Pair<E, Double>> chosenPatSorted = Counters.toSortedListWithCounts(chosenPat);
    for (Pair<E, Double> en : chosenPatSorted)
      Redwood.log(ConstantsAndVariables.minimaldebug, en.first() + ":" + df.format(en.second) + "\n");

    if (constVars.outDir != null && !constVars.outDir.isEmpty()) {
      CollectionValuedMap<E, CandidatePhrase> posWords = new CollectionValuedMap<>();
      for (Entry<E, ClassicCounter<CandidatePhrase>> en : patternsandWords4Label.entrySet()) {
        posWords.addAll(en.getKey(), en.getValue().keySet());
      }

      CollectionValuedMap<E, CandidatePhrase> negWords = new CollectionValuedMap<>();
      for (Entry<E, ClassicCounter<CandidatePhrase>> en : negPatternsandWords4Label.entrySet()) {
        negWords.addAll(en.getKey(), en.getValue().keySet());
      }
      CollectionValuedMap<E, CandidatePhrase> unlabWords = new CollectionValuedMap<>();
      for (Entry<E, ClassicCounter<CandidatePhrase>> en : unLabeledPatternsandWords4Label.entrySet()) {
        unlabWords.addAll(en.getKey(), en.getValue().keySet());
      }

      if (constVars.outDir != null) {
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
        for (Pair<E, Double> pat : chosenPatSorted) {
          JsonObjectBuilder o = Json.createObjectBuilder();
          JsonArrayBuilder pos = Json.createArrayBuilder();
          JsonArrayBuilder neg = Json.createArrayBuilder();
          JsonArrayBuilder unlab = Json.createArrayBuilder();

          for (CandidatePhrase w : posWords.get(pat.first()))
            pos.add(w.getPhrase());
          for (CandidatePhrase w : negWords.get(pat.first()))
            neg.add(w.getPhrase());
          for (CandidatePhrase w : unlabWords.get(pat.first()))
            unlab.add(w.getPhrase());

          o.add("Positive", pos);
          o.add("Negative", neg);
          o.add("Unlabeled", unlab);
          o.add("Score", pat.second());

          objThisIter.add(pat.first().toStringSimple(), o);
        }
        obj.add(objThisIter.build());

        IOUtils.ensureDir(new File(filename).getParentFile());
        IOUtils.writeStringToFile(StringUtils.normalize(StringUtils.toAscii(obj.build().toString())), filename, "ASCII");
        writtenPatInJustification.put(label, true);
      }
    }

    if (constVars.justify) {
      Redwood.log(Redwood.DBG, "Justification for Patterns:");
      for (E key : chosenPat.keySet()) {
        Redwood.log(Redwood.DBG, "\nPattern: " + key);
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
    //allPatternsandWords.put(label, allPatternsandWords4Label);
    patternsandWords.put(label, patternsandWords4Label);
    currentPatternWeights.put(label, currentPatternWeights4Label);

    return chosenPat;

  }

//  private void savePatternIndex(String dir ) throws IOException {
//    if(dir != null) {
//      IOUtils.ensureDir(new File(dir));
//      constVars.getPatternIndex().save(dir);
//    }
//    //patsForEachToken.savePatternIndex(constVars.getPatternIndex(), dir);
//
//  }

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

  private static AtomicInteger numCallsToCalStats = new AtomicInteger();


  private static <E> List<List<E>> splitIntoNumThreadsWithSampling(List<E> c, int n, int numThreads) {
    if (n < 0)
      throw new IllegalArgumentException("n < 0: " + n);
    if (n > c.size())
      throw new IllegalArgumentException("n > size of collection: " + n + ", " + c.size());
    List<List<E>> resultAll = new ArrayList<>(numThreads);
    int num;

    if (numThreads == 1)
      num = n;
    else
      num = n / (numThreads - 1);

    System.out.println("shuffled " + c.size() + " sentences and selecting " + num  + " sentences per thread");
    List<E> result = new ArrayList<>(num);
    int totalitems = 0;
    int nitem = 0;
    Random r = new Random(numCallsToCalStats.incrementAndGet());
    boolean[] added = new boolean[c.size()];
    // Arrays.fill(added, false);  // not needed; get false by default
    while(totalitems < n){

      //find the new sample index
      int index;

      do{
        index =  r.nextInt(c.size());
      }while(added[index]);
      added[index] = true;

      E c1 = c.get(index);

      if(nitem == num){
        resultAll.add(result);
        result = new ArrayList<>(num);
        nitem= 0;
      }
      result.add(c1);
      totalitems++;
      nitem ++;
    }

    if(!result.isEmpty())
      resultAll.add(result);
    return resultAll;
  }

  //for each pattern, it calculates positive, negative, and unlabeled words
  private void calculateSufficientStats(Map<String, DataInstance> sents,
                                        PatternsForEachToken patternsForEachToken, String label,
                                        TwoDimensionalCounter<E, CandidatePhrase> patternsandWords4Label,
                                        TwoDimensionalCounter<E, CandidatePhrase> negPatternsandWords4Label,
                                        TwoDimensionalCounter<E, CandidatePhrase> unLabeledPatternsandWords4Label, Set<String> allCandidatePhrases) {

    Redwood.log(Redwood.DBG,"calculating sufficient stats");
    patternsForEachToken.setupSearch();
    // calculating the sufficient statistics
    Class answerClass4Label = constVars.getAnswerClass().get(label);
    int sampleSize = constVars.sampleSentencesForSufficientStats == 1.0 ? sents.size(): (int) Math.round(constVars.sampleSentencesForSufficientStats*sents.size());
    List<List<String>> sampledSentIds = splitIntoNumThreadsWithSampling(CollectionUtils.toList(sents.keySet()), sampleSize, constVars.numThreads);
    Redwood.log(Redwood.DBG,"sampled " + sampleSize + " sentences (" + constVars.sampleSentencesForSufficientStats*100 + "%)");

    ExecutorService executor = Executors.newFixedThreadPool(constVars.numThreads);

    List<Future<Triple<List<Pair<E, CandidatePhrase>>, List<Pair<E, CandidatePhrase>>, List<Pair<E, CandidatePhrase>>>>> list = new ArrayList<>();
    for (List<String> sampledSents : sampledSentIds) {

      Callable<Triple<List<Pair<E, CandidatePhrase>>, List<Pair<E, CandidatePhrase>>, List<Pair<E, CandidatePhrase>>>> task = new CalculateSufficientStatsThreads(patternsForEachToken, sampledSents, sents, label, answerClass4Label);
      Future<Triple<List<Pair<E, CandidatePhrase>>, List<Pair<E, CandidatePhrase>>, List<Pair<E, CandidatePhrase>>>> submit = executor.submit(task);
      list.add(submit);
    }

    // Now retrieve the result
    for (Future<Triple<List<Pair<E, CandidatePhrase>>, List<Pair<E, CandidatePhrase>>, List<Pair<E, CandidatePhrase>>>> future : list) {
      try {
        Triple<List<Pair<E, CandidatePhrase>>, List<Pair<E, CandidatePhrase>>, List<Pair<E, CandidatePhrase>>> stats = future.get();
        addStats(patternsandWords4Label, stats.first());
        addStats(negPatternsandWords4Label, stats.second());
        addStats(unLabeledPatternsandWords4Label, stats.third());
      } catch (Exception e) {
        executor.shutdownNow();
        throw new RuntimeException(e);
      }
    }
    executor.shutdown();


  }

  private void addStats(TwoDimensionalCounter<E, CandidatePhrase> pw, List<Pair<E, CandidatePhrase>> v) {
    for(Pair<E, CandidatePhrase> w: v){
      pw.incrementCount(w.first(), w.second());
    }
  }

  private class CalculateSufficientStatsThreads implements Callable{

    private final Map<String, DataInstance> sents;
    private final PatternsForEachToken patternsForEachToken;
    private final Collection<String> sentIds;
    private final String label;
    private final Class answerClass4Label;

    public CalculateSufficientStatsThreads(PatternsForEachToken patternsForEachToken, Collection<String> sentIds, Map<String, DataInstance> sents,String label, Class answerClass4Label){
      this.patternsForEachToken = patternsForEachToken;
      this.sentIds = sentIds;
      this.sents = sents;
      this.label = label;
      this.answerClass4Label = answerClass4Label;
    }

    @Override
    public Triple<List<Pair<E, CandidatePhrase>>, List<Pair<E, CandidatePhrase>>, List<Pair<E, CandidatePhrase>>> call() throws Exception {

      List<Pair<E, CandidatePhrase>> posWords = new ArrayList<>();
      List<Pair<E, CandidatePhrase>> negWords = new ArrayList<>();
      List<Pair<E, CandidatePhrase>> unlabWords = new ArrayList<>();
      for(String sentId: sentIds){
        Map<Integer, Set<E>> pat4Sent = patternsForEachToken.getPatternsForAllTokens(sentId);
        if (pat4Sent == null) {
          throw new RuntimeException("How come there are no patterns for " + sentId);
        }
        DataInstance sent = sents.get(sentId);
        List<CoreLabel> tokens = sent.getTokens();
        for (int i = 0; i < tokens.size(); i++) {
          CoreLabel token = tokens.get(i);
          //Map<String, Set<String>> matchedPhrases = token.get(PatternsAnnotations.MatchedPhrases.class);

          CandidatePhrase tokenWordOrLemma = CandidatePhrase.createOrGet(token.word());
          CandidatePhrase longestMatchingPhrase;

          if (constVars.useMatchingPhrase) {
            Map<String, CandidatePhrase> longestMatchingPhrases = token.get(PatternsAnnotations.LongestMatchedPhraseForEachLabel.class);
            longestMatchingPhrase = longestMatchingPhrases.get(label);
            longestMatchingPhrase = (longestMatchingPhrase !=null && (longestMatchingPhrase.getPhrase().length() > tokenWordOrLemma.getPhrase().length()))? longestMatchingPhrase : tokenWordOrLemma;
          /*if (matchedPhrases != null && !matchedPhrases.isEmpty()) {
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
          }*/

          } else
            longestMatchingPhrase = tokenWordOrLemma;

          Set<E> pats = pat4Sent.get(i);

          //make a copy of pats because we are changing numwordscompound etc.
          Set newpats = new HashSet<E>();
          boolean changedpats = false;
          // cdm added null test 2018-01-17 to fix NPE, but more needs to be changed to get DEPS option working,
          // apparently including adding more code currently in research package.
          if (pats != null) {
            for (E s : pats) {
              if (s instanceof SurfacePattern) {
                changedpats = true;
                SurfacePattern snew = ((SurfacePattern) s).copyNewToken();
                snew.setNumWordsCompound(PatternFactory.numWordsCompoundMapped.get(label));
                newpats.add(snew);
              }
            }
          }

          if(changedpats)
            pats = newpats;

          //This happens when dealing with the collapseddependencies
          if (pats == null) {
            if(!constVars.patternType.equals(PatternFactory.PatternType.DEP))
              throw new RuntimeException("Why are patterns null for sentence " + sentId + " and token " + i + "(" + tokens.get(i) + "). pat4Sent has token ids " + pat4Sent.keySet() +
                (constVars.batchProcessSents ? "" : ". The sentence is " + Data.sents.get(sentId)) + ". If you have changed parameters, recompute all patterns.");
            continue;
          }

//        Set<E> prevPat = pat.first();
//        Set<E> nextPat = pat.second();
//        Set<E> prevnextPat = pat.third();
          if (PatternFactory.ignoreWordRegex.matcher(token.word()).matches())
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
            for (E s : pats) {
              posWords.add(new Pair<>(s, longestMatchingPhrase));
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
              if (constVars.getOtherSemanticClassesWords().contains(token.word()) || constVars.getOtherSemanticClassesWords().contains(token.lemma()))
                negToken = true;

            if(!negToken){
              for(String labelA : constVars.getLabels()){
                if(!labelA.equals(label)){
                  if(constVars.getSeedLabelDictionary().get(labelA).contains(longestMatchingPhrase) || constVars.getSeedLabelDictionary().get(labelA).contains(tokenWordOrLemma)
                    || constVars.getLearnedWords(labelA).containsKey(longestMatchingPhrase) || constVars.getLearnedWords(labelA).containsKey(tokenWordOrLemma)){
                    negToken = true;
                    break;
                  }
                }
              }
            }

            for (E sindex : pats) {
              if (negToken) {
                negWords.add(new Pair<>(sindex, longestMatchingPhrase));
              } else {
                unlabWords.add(new Pair<>(sindex, longestMatchingPhrase));
              }

            }
          }
        }
      }
      return new Triple<List<Pair<E, CandidatePhrase>>, List<Pair<E, CandidatePhrase>>, List<Pair<E, CandidatePhrase>>>(posWords, negWords, unlabWords);
    }
  }

  private Set<E> enforceMinSupportRequirements(TwoDimensionalCounter<E, CandidatePhrase> patternsandWords4Label,
      TwoDimensionalCounter<E, CandidatePhrase> unLabeledPatternsandWords4Label) {
    Set<E> remove = new HashSet<>();
    for (Entry<E, ClassicCounter<CandidatePhrase>> en : patternsandWords4Label.entrySet()) {
      if (en.getValue().size() < constVars.minPosPhraseSupportForPat) {
        remove.add(en.getKey());
      }

    }
    int numRemoved = remove.size();
    Redwood.log(Redwood.DBG, "Removing " + numRemoved + " patterns that do not meet minPosPhraseSupportForPat requirement of >= "
        + constVars.minPosPhraseSupportForPat);

    for (Entry<E, ClassicCounter<CandidatePhrase>> en : unLabeledPatternsandWords4Label.entrySet()) {
      if (en.getValue().size() < constVars.minUnlabPhraseSupportForPat) {
        remove.add(en.getKey());
      }
    }
    Redwood.log(Redwood.DBG, "Removing " + (remove.size() - numRemoved) + " patterns that do not meet minUnlabPhraseSupportForPat requirement of >= "
        + constVars.minUnlabPhraseSupportForPat);
    return remove;
  }

//  void removeLearnedPattern(String label, E p) {
//    this.learnedPatterns.get(label).remove(p);
//    if (wordsPatExtracted.containsKey(label))
//      for (Entry<String, ClassicCounter<E>> en : this.wordsPatExtracted.get(label).entrySet()) {
//        en.getValue().remove(p);
//      }
//  }

  private void removeLearnedPatterns(String label, Collection<E> pats) {
    Counters.removeKeys(this.learnedPatterns.get(label), pats);

    for(Map.Entry<Integer, Counter<E>> en: this.learnedPatternsEachIter.get(label).entrySet())
      Counters.removeKeys(en.getValue(), pats);

    if (wordsPatExtracted.containsKey(label))
      for (Entry<CandidatePhrase, ClassicCounter<E>> en : this.wordsPatExtracted.get(label).entrySet()) {
        Counters.removeKeys(en.getValue(), pats);
      }
  }

  public static <E> Counter<E> normalizeSoftMaxMinMaxScores(Counter<E> scores, boolean minMaxNorm, boolean softmax, boolean oneMinusSoftMax) {
    double minScore = Double.MAX_VALUE, maxScore = Double.MIN_VALUE;
    Counter<E> newscores = new ClassicCounter<>();
    if (softmax) {
      for (Entry<E, Double> en : scores.entrySet()) {
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
      for (Entry<E, Double> en : newscores.entrySet()) {
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

  public TwoDimensionalCounter<String, ScorePhraseMeasures> phInPatScoresCache = new TwoDimensionalCounter<>();


  public void labelWords(String label, Map<String, DataInstance> sents, Collection<CandidatePhrase> identifiedWords) throws IOException {
    CollectionValuedMap<E, Triple<String, Integer, Integer>> matchedTokensByPat = new CollectionValuedMap<>();
    labelWords(label, sents, identifiedWords, null, matchedTokensByPat);
  }

  public void labelWords(String label, Map<String, DataInstance> sents, Collection<CandidatePhrase> identifiedWords, String outFile,
      CollectionValuedMap<E, Triple<String, Integer, Integer>> matchedTokensByPat) throws IOException {

    Date startTime = new Date();
    Redwood.log(Redwood.DBG, "Labeling " + sents.size() + " sentences with " + identifiedWords.size() + " phrases for label " + label);

    int numTokensLabeled = 0;

    CollectionValuedMap<String, Integer> tokensMatchedPatterns = null;
    if (constVars.restrictToMatched) {
      tokensMatchedPatterns = new CollectionValuedMap<>();
      for (Entry<E, Collection<Triple<String, Integer, Integer>>> en : matchedTokensByPat.entrySet()) {
        for (Triple<String, Integer, Integer> en2 : en.getValue()) {
          for (int i = en2.second(); i <= en2.third(); i++) {
            tokensMatchedPatterns.add(en2.first(), i);
          }
        }
      }
    }

    Map<String, Map<Integer, Set<E>>> tempPatsForSents = new HashMap<>();

    for (Entry<String, DataInstance> sentEn : sents.entrySet()) {
      List<CoreLabel> tokens = sentEn.getValue().getTokens();
      boolean sentenceChanged = false;
      Map<CandidatePhrase, String[]> identifiedWordsTokens = new HashMap<>();
      for (CandidatePhrase s : identifiedWords) {
        String[] toks = s.getPhrase().split("\\s+");
        identifiedWordsTokens.put(s, toks);
      }
      String[] sent = new String[tokens.size()];
      int i = 0;

      Set<Integer> contextWordsRecalculatePats = new HashSet<>();

      for (CoreLabel l :tokens) {
        sent[i] = l.word();
        i++;
      }
      for (Entry<CandidatePhrase, String[]> phEn : identifiedWordsTokens.entrySet()) {
        String[] ph = phEn.getValue();
        List<Integer> ints = ArrayUtils.getSubListIndex(ph, sent, o -> constVars.matchLowerCaseContext ? ((String) o.first()).equalsIgnoreCase((String)o.second()): o.first().equals(o.second()));
        if (ints == null)
          continue;

        for (Integer idx : ints) {
          boolean donotuse = false;
          if (constVars.restrictToMatched) {
            for (int j = 0; j < ph.length; j++) {
              if (!tokensMatchedPatterns.get(sentEn.getKey()).contains(idx + j)) {
                Redwood.log(ConstantsAndVariables.extremedebug, "not labeling " + tokens.get(idx + j).word());
                donotuse = true;
                break;
              }
            }
          }
          if (donotuse == false) {
            String phStr = StringUtils.join(ph, " ");

            if(constVars.writeMatchedTokensIdsForEachPhrase)
              addToMatchedTokensByPhrase(phStr, sentEn.getKey(), idx, ph.length);


            Redwood.log(ConstantsAndVariables.extremedebug,"Labeling because of phrase " + phStr);
            for (int j = 0; j < ph.length; j++) {
              int index = idx + j;
              CoreLabel l = tokens.get(index);
              if (constVars.usePatternResultAsLabel) {
                sentenceChanged = true;
                l.set(constVars.getAnswerClass().get(label), label);
                numTokensLabeled ++;

                //set the matched and the longest phrases
                CollectionValuedMap<String, CandidatePhrase> matched = new CollectionValuedMap<>();
                matched.add(label, phEn.getKey());
                if(!l.containsKey(PatternsAnnotations.MatchedPhrases.class))
                  l.set(PatternsAnnotations.MatchedPhrases.class, matched);
                else
                  l.get(PatternsAnnotations.MatchedPhrases.class).addAll(matched);

                CandidatePhrase longest = l.get(PatternsAnnotations.LongestMatchedPhraseForEachLabel.class).get(label);
                longest = longest != null && longest.getPhrase().length() > phEn.getKey().getPhrase().length() ? longest: phEn.getKey();
                l.get(PatternsAnnotations.LongestMatchedPhraseForEachLabel.class).put(label, longest);

                for (int k = Math.max(0, index - PatternFactory.numWordsCompoundMapped.get(label)); k < tokens.size()
                    && k <= index + PatternFactory.numWordsCompoundMapped.get(label) + 1; k++) {
                  contextWordsRecalculatePats.add(k);
                }

              }
            }
          }
        }
      }

      if (patsForEachToken != null )//&& patsForEachToken.containsSentId(sentEn.getKey()))
      {
        for (int index : contextWordsRecalculatePats){
          if(!tempPatsForSents.containsKey(sentEn.getKey()))
            tempPatsForSents.put(sentEn.getKey(), new HashMap<>());

          tempPatsForSents.get(sentEn.getKey()).put(index, Pattern.getContext(constVars.patternType, sentEn.getValue(), index, ConstantsAndVariables.getStopWords()));
          //patsForEachToken.addPatterns(sentEn.getKey(), index, createPats.getContext(sentEn.getValue(), index));
        }
      }
      if(sentenceChanged){
        constVars.invertedIndex.update(sentEn.getValue().getTokens(), sentEn.getKey());
      }
    }

    if(patsForEachToken != null) {
      patsForEachToken.updatePatterns(tempPatsForSents);//sentEn.getKey(), index, createPats.getContext(sentEn.getValue(), index));
    }

    constVars.invertedIndex.finishUpdating();

    if (outFile != null) {
      Redwood.log(ConstantsAndVariables.minimaldebug, "Writing results to " + outFile);
      IOUtils.writeObjectToFile(sents, outFile);
    }

    Date endTime = new Date();
    Redwood.log(Redwood.DBG, "Done labeling provided sents in " + elapsedTime(startTime, endTime) + ". Total # of tokens labeled: " + numTokensLabeled);
  }


  public void iterateExtractApply() throws IOException, ClassNotFoundException {
    iterateExtractApply(null, null, null);
  }

  /**
   *
   * @param p0 Null in most cases. only used for BPB
   * @param p0Set Null in most cases
   * @param ignorePatterns
   *
   */
  public void iterateExtractApply(Map<String, E> p0, Map<String, Counter<CandidatePhrase>> p0Set, Map<String, Set<E>> ignorePatterns) throws IOException, ClassNotFoundException {

    Map<String, CollectionValuedMap<E, Triple<String, Integer, Integer>>> matchedTokensByPatAllLabels = new HashMap<>();
    //Map<String, Collection<Triple<String, Integer, Integer>>> matchedTokensForPhrases = new HashMap<String, Collection<Triple<String, Integer, Integer>>>();
    Map<String, TwoDimensionalCounter<CandidatePhrase, E>> termsAllLabels = new HashMap<>();

    Map<String, Set<CandidatePhrase>> ignoreWordsAll = new HashMap<>();
    for (String label : constVars.getSeedLabelDictionary().keySet()) {
      matchedTokensByPatAllLabels.put(label, new CollectionValuedMap<>());
      termsAllLabels.put(label, new TwoDimensionalCounter<>());
      if (constVars.useOtherLabelsWordsasNegative) {
        Set<CandidatePhrase> w = new HashSet<>();
        for (Entry<String, Set<CandidatePhrase>> en : constVars.getSeedLabelDictionary().entrySet()) {
          if (en.getKey().equals(label))
            continue;
          w.addAll(en.getValue());
        }
        ignoreWordsAll.put(label, w);
      }
    }

    Redwood.log(ConstantsAndVariables.minimaldebug, "Iterating " + constVars.numIterationsForPatterns + " times.");

    Map<String, BufferedWriter> wordsOutput = new HashMap<>();
    Map<String, BufferedWriter> patternsOutput = new HashMap<>();

    for (String label : constVars.getLabels()) {
      if(constVars.outDir != null){
      IOUtils.ensureDir(new File(constVars.outDir + "/" + constVars.identifier + "/" + label));

      String  wordsOutputFileLabel = constVars.outDir + "/" + constVars.identifier + "/" + label + "/learnedwords.txt";
      wordsOutput.put(label, new BufferedWriter(new FileWriter(wordsOutputFileLabel)));
      Redwood.log(ConstantsAndVariables.minimaldebug, "Saving the learned words for label " + label + " in " + wordsOutputFileLabel);

      }

      if(constVars.outDir != null){
        String  patternsOutputFileLabel = constVars.outDir + "/" + constVars.identifier + "/" + label + "/learnedpatterns.txt";
        patternsOutput.put(label, new BufferedWriter(new FileWriter(patternsOutputFileLabel)));
        Redwood.log(ConstantsAndVariables.minimaldebug, "Saving the learned patterns for label " + label + " in " + patternsOutputFileLabel);
      }
    }

    for (int i = 0; i < constVars.numIterationsForPatterns; i++) {

      Redwood
          .log(ConstantsAndVariables.minimaldebug, "\n\n################################ Iteration " + (i + 1) + " ##############################");
      boolean keepRunning = false;
      Map<String, Counter<CandidatePhrase>> learnedWordsThisIter = new HashMap<>();
      for (String label : constVars.getLabels()) {
        Redwood.log(ConstantsAndVariables.minimaldebug, "\n###Learning for label " + label + " ######");

        String sentout = constVars.sentsOutFile == null ? null : constVars.sentsOutFile + "_" + label;

        Pair<Counter<E>, Counter<CandidatePhrase>> learnedPatWords4label = iterateExtractApply4Label(label, p0 != null ? p0.get(label) : null,
            p0Set != null ? p0Set.get(label) : null, wordsOutput.get(label), sentout, patternsOutput.get(label),
            ignorePatterns != null ? ignorePatterns.get(label) : null, ignoreWordsAll.get(label), matchedTokensByPatAllLabels.get(label),
            termsAllLabels.get(label), i + numIterationsLoadedModel);

        learnedWordsThisIter.put(label, learnedPatWords4label.second());
        if (learnedPatWords4label.first().size() > 0 && constVars.getLearnedWords(label).size() < constVars.maxExtractNumWords) {
          keepRunning = true;
        }
      }

      if (constVars.useOtherLabelsWordsasNegative) {
        for (String label : constVars.getLabels()) {
          for (Entry<String, Counter<CandidatePhrase>> en : learnedWordsThisIter.entrySet()) {
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

      for (String label : constVars.getLabels()) {
        IOUtils.ensureDir(new File(constVars.outDir + "/" + constVars.identifier + "/" + label));

        if (constVars.writeMatchedTokensFiles) {
          ConstantsAndVariables.DataSentsIterator iter = new ConstantsAndVariables.DataSentsIterator(constVars.batchProcessSents);
          int i = 0;
          String suffix ="";
          while(iter.hasNext()){
            i++;
            if(constVars.batchProcessSents)
              suffix = "_"+i;
            writeMatchedTokensAndSents(label, iter.next().first(), suffix, matchedTokensByPatAllLabels.get(label));
          }
        }
      }

      if(constVars.writeMatchedTokensIdsForEachPhrase && constVars.outDir != null){
        String matchedtokensfilename = constVars.outDir + "/" + constVars.identifier  + "/tokenids4matchedphrases" + ".json";
        IOUtils.writeStringToFile(matchedTokensByPhraseJsonString(), matchedtokensfilename, "utf8");

      }
    }

    System.out.println("\n\nAll patterns learned:");

    for(Map.Entry<String, Map<Integer, Counter<E>>> en2: this.learnedPatternsEachIter.entrySet()) {
      System.out.println(en2.getKey()+":");
      for (Map.Entry<Integer, Counter<E>> en : en2.getValue().entrySet()) {
        System.out.println("Iteration " + en.getKey());
        System.out.println(StringUtils.join(en.getValue().keySet(), "\n"));
      }
    }
    System.out.println("\n\nAll words learned:");
    for(String label: constVars.getLabels()) {
      System.out.println("\nLabel " + label +"\n");
      for (Entry<Integer, Counter<CandidatePhrase>> en : this.constVars.getLearnedWordsEachIter(label).entrySet()) {
        System.out.println("Iteration " + en.getKey() + ":\t\t" + en.getValue().keySet());
      }
    }
    // close all the writers
    for (String label : constVars.getLabels()) {
      if(wordsOutput.containsKey(label) && wordsOutput.get(label) != null)
        wordsOutput.get(label).close();
      if(patternsOutput.containsKey(label) && patternsOutput.get(label) != null)
        patternsOutput.get(label).close();
    }
  }

  private void writeMatchedTokensAndSents(String label, Map<String, DataInstance> sents, String suffix, CollectionValuedMap<E, Triple<String, Integer, Integer>> tokensMatchedPat) throws IOException {
    if(constVars.outDir != null){
    Set<String> allMatchedSents = new HashSet<>();
    String matchedtokensfilename = constVars.outDir + "/" + constVars.identifier + "/" + label + "/tokensmatchedpatterns" + suffix + ".json";
    JsonObjectBuilder pats = Json.createObjectBuilder();
    for (Entry<E, Collection<Triple<String, Integer, Integer>>> en : tokensMatchedPat.entrySet()) {
      CollectionValuedMap<String, Pair<Integer, Integer>> matchedStrs = new CollectionValuedMap<>();
      for (Triple<String, Integer, Integer> en2 : en.getValue()) {
        allMatchedSents.add(en2.first());
        matchedStrs.add(en2.first(), new Pair<>(en2.second(), en2.third()));
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
      for (CoreLabel l : sents.get(sentId).getTokens()) {
        sent.add(l.word());
      }
      senttokens.add(sentId, sent);
    }
    String sentfilename = constVars.outDir + "/" + constVars.identifier + "/sentences" + suffix  + ".json";
    IOUtils.writeStringToFile(senttokens.build().toString(), sentfilename, "utf8");
    }
  }

  public static String matchedTokensByPhraseJsonString(String phrase){
    if ( ! Data.matchedTokensForEachPhrase.containsKey(phrase)) {
      return "";
    }
    JsonArrayBuilder arrobj = jsonArrayBuilderFromMapCounter(Data.matchedTokensForEachPhrase.get(phrase));
    return arrobj.build().toString();
  }

  public static String matchedTokensByPhraseJsonString(){
    JsonObjectBuilder pats = Json.createObjectBuilder();

    for (Entry<String, Map<String, List<Integer>>> en : Data.matchedTokensForEachPhrase.entrySet()) {
      JsonArrayBuilder arrobj = jsonArrayBuilderFromMapCounter(en.getValue());
      pats.add(en.getKey(), arrobj);
    }
    return pats.build().toString();
  }

  private static JsonArrayBuilder jsonArrayBuilderFromMapCounter(Map<String, List<Integer>> mapCounter) {
    JsonArrayBuilder arrobj = Json.createArrayBuilder();
    for (Entry<String, List<Integer>> sen : mapCounter.entrySet()) {
      JsonObjectBuilder obj = Json.createObjectBuilder();
      JsonArrayBuilder tokens = Json.createArrayBuilder();
      for(Integer i : sen.getValue()){
        tokens.add(i);
      }
      obj.add(sen.getKey(),tokens);
      arrobj.add(obj);
    }
    return arrobj;
  }

  //numIterTotal = numIter + iterations from previously loaded model!
  private Pair<Counter<E>, Counter<CandidatePhrase>> iterateExtractApply4Label(String label, E p0, Counter<CandidatePhrase> p0Set,
      BufferedWriter wordsOutput, String sentsOutFile, BufferedWriter patternsOut, Set<E> ignorePatterns,
      Set<CandidatePhrase> ignoreWords, CollectionValuedMap<E, Triple<String, Integer, Integer>> matchedTokensByPat,
      TwoDimensionalCounter<CandidatePhrase, E> terms, int numIterTotal) throws IOException, ClassNotFoundException {

    if (!learnedPatterns.containsKey(label)) {
      learnedPatterns.put(label, new ClassicCounter<>());
    }

    if (!learnedPatternsEachIter.containsKey(label)) {
      learnedPatternsEachIter.put(label, new HashMap<>());
    }

    if (!constVars.getLearnedWordsEachIter().containsKey(label)) {
      constVars.getLearnedWordsEachIter().put(label, new TreeMap<>());
    }

//    if (!constVars.getLearnedWords().containsKey(label)) {
//      constVars.getLearnedWords().put(label, new ClassicCounter<CandidatePhrase>());
//    }

    Counter<CandidatePhrase> identifiedWords = new ClassicCounter<>();
    Counter<E> patterns = new ClassicCounter<>();

    Counter<E> patternThisIter = getPatterns(label, learnedPatterns.get(label).keySet(), p0, p0Set, ignorePatterns);

    patterns.addAll(patternThisIter);

    learnedPatterns.get(label).addAll(patterns);

    assert !learnedPatternsEachIter.get(label).containsKey(numIterTotal) : "How come learned patterns already have a key for " + numIterTotal + " keys are " + learnedPatternsEachIter.get(label).keySet();

    learnedPatternsEachIter.get(label).put(numIterTotal, patterns);

      if (sentsOutFile != null)
        sentsOutFile = sentsOutFile + "_" + numIterTotal + "iter.ser";

      Counter<String> scoreForAllWordsThisIteration = new ClassicCounter<>();

      identifiedWords.addAll(scorePhrases.learnNewPhrases(label, this.patsForEachToken, patterns, learnedPatterns.get(label), matchedTokensByPat,
        scoreForAllWordsThisIteration, terms, wordsPatExtracted.get(label), this.patternsandWords.get(label), constVars.identifier, ignoreWords));

      if (identifiedWords.size() > 0) {
        if (constVars.usePatternResultAsLabel) {
          if (constVars.getLabels().contains(label)) {

            ConstantsAndVariables.DataSentsIterator sentsIter = new ConstantsAndVariables.DataSentsIterator(constVars.batchProcessSents);
            while(sentsIter.hasNext()){
              Pair<Map<String, DataInstance>, File> sentsf = sentsIter.next();
              Redwood.log(Redwood.DBG, "labeling sentences from " + sentsf.second());
              labelWords(label, sentsf.first(), identifiedWords.keySet(), sentsOutFile, matchedTokensByPat);
              //write only for batch sentences
              //TODO: make this clean!
              if(sentsf.second().exists() && constVars.batchProcessSents)
                  IOUtils.writeObjectToFile(sentsf.first(), sentsf.second());
            }
          } else
            throw new RuntimeException("why is the answer label null?");
          assert !constVars.getLearnedWordsEachIter().get(label).containsKey(numIterTotal) : "How come learned words already have a key for " + numIterTotal;
          constVars.getLearnedWordsEachIter().get(label).put(numIterTotal, identifiedWords);
        }

        if (wordsOutput != null) {
          wordsOutput.write("\n" + Counters.toSortedString(identifiedWords, identifiedWords.size(), "%1$s", "\n"));
          wordsOutput.flush();
        }
      }

    //}
    if (patternsOut != null)
      this.writePatternsToFile(patterns, patternsOut);

    return new Pair<>(patterns, identifiedWords);
  }

  private void writePatternsToFile(Counter<E> pattern, BufferedWriter outFile) throws IOException {
    for (Entry<E, Double> en : pattern.entrySet())
      outFile.write(en.getKey() + "\t" + en.getValue() + "\n");
  }

  private void writeWordsToFile(Map<Integer, Counter<CandidatePhrase>> words, BufferedWriter outFile) throws IOException {
    for (Entry<Integer, Counter<CandidatePhrase>> en2 : words.entrySet()) {
      outFile.write("###Iteration " + en2.getKey()+"\n");
      for (Entry<CandidatePhrase, Double> en : en2.getValue().entrySet())
        outFile.write(en.getKey() + "\t" + en.getValue() + "\n");
    }
  }

  private static TreeMap<Integer, Counter<CandidatePhrase>> readLearnedWordsFromFile(File file) {
    TreeMap<Integer, Counter<CandidatePhrase>> learned = new TreeMap<>();
    Counter<CandidatePhrase> words = null;
    int numIter = -1;
    for (String line : IOUtils.readLines(file)) {
      if(line.startsWith("###")){
        if(words != null)
          learned.put(numIter, words);
        numIter ++;
        words = new ClassicCounter<>();
        continue;
      }
      String[] t = line.split("\t");
      words.setCount(CandidatePhrase.createOrGet(t[0]), Double.parseDouble(t[1]));
    }
    if(words != null)
      learned.put(numIter, words);
    return learned;
  }

  public Counter<E> getLearnedPatterns(String label) {
    return this.learnedPatterns.get(label);
  }

//  public Counter<E> getLearnedPatternsSurfaceForm(String label) {
//    return this.learnedPatterns.get(label);
//  }


  public Map<String, Counter<E>> getLearnedPatterns() {
    return this.learnedPatterns;
  }

  public Map<String, Map<Integer, Counter<E>>> getLearnedPatternsEachIter() {
    return this.learnedPatternsEachIter;
  }

  public Map<Integer, Counter<E>> getLearnedPatternsEachIter(String label) {
    return this.learnedPatternsEachIter.get(label);
  }


  public void setLearnedPatterns(Counter<E> patterns, String label) {
    this.learnedPatterns.put(label, patterns);
  }

  /**
   * COPIED from CRFClassifier: Count the successes and failures of the model on
   * the given document. Fills numbers in to counters for true positives, false
   * positives, and false negatives, and also keeps track of the entities seen. <br>
   * Returns false if we ever encounter null for gold or guess. NOTE: The
   * current implementation of counting wordFN/FP is incorrect.
   */
  private static boolean countResultsPerEntity(List<CoreLabel> doc, Counter<String> entityTP, Counter<String> entityFP, Counter<String> entityFN,
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

    IOBUtils.countEntityResults(doc, entityTP, entityFP, entityFN, background);

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

      assert (gold != null) : "gold is null";
      assert(guess != null) : "guess is null";


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
        throw new RuntimeException("don't know reached here. not meant for more than one entity label: " + gold + " and " + guess);

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

  private void writeLabelDataSents(Map<String, DataInstance> sents, BufferedWriter writer) throws IOException {
    for (Entry<String, DataInstance> sent : sents.entrySet()) {
      writer.write(sent.getKey() + "\t");

      Map<String, Boolean> lastWordLabeled = new HashMap<>();
      for (String label : constVars.getLabels()) {
        lastWordLabeled.put(label, false);
      }

      for (CoreLabel s : sent.getValue().getTokens()) {
        String str = "";
        //write them in reverse order
        List<String> listEndedLabels = new ArrayList<>();
        //to first finish labels before starting
        List<String> startingLabels = new ArrayList<>();

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

    ConstantsAndVariables.DataSentsIterator sentsIter = new ConstantsAndVariables.DataSentsIterator(constVars.batchProcessSents);
    while(sentsIter.hasNext()){
      Pair<Map<String, DataInstance>, File> sentsf = sentsIter.next();
      this.writeLabelDataSents(sentsf.first(), writer);
    }
    writer.close();
  }

  static public void writeColumnOutput(String outFile, boolean batchProcessSents, Map<String, Class<? extends TypesafeMap.Key<String>>> answerclasses) throws IOException, ClassNotFoundException {
    BufferedWriter writer = new BufferedWriter(new FileWriter(outFile));

    ConstantsAndVariables.DataSentsIterator sentsIter = new ConstantsAndVariables.DataSentsIterator(batchProcessSents);
    while(sentsIter.hasNext()){
      Pair<Map<String, DataInstance>, File> sentsf = sentsIter.next();
      writeColumnOutputSents(sentsf.first(), writer, answerclasses);
    }
    writer.close();
  }

  private static void writeColumnOutputSents(Map<String, DataInstance> sents, BufferedWriter writer, Map<String, Class<? extends TypesafeMap.Key<String>>> answerclasses) throws IOException {
    for (Entry<String, DataInstance> sent : sents.entrySet()) {

      writer.write("\n\n" + sent.getKey() + "\n");

      for (CoreLabel s : sent.getValue().getTokens()) {
        writer.write(s.word()+"\t");
        Set<String> labels = new HashSet<>();
        for (Entry<String, Class<? extends TypesafeMap.Key<String>>> as : answerclasses.entrySet()) {
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

  // public Map<String, DataInstance> loadJavaNLPAnnotatorLabeledFile(String
  // labeledFile, Properties props) throws FileNotFoundException {
  // System.out.println("Loading evaluate file " + labeledFile);
  // Map<String, DataInstance> sents = new HashMap<String,
  // DataInstance>();
  // JavaNLPAnnotatorReaderAndWriter j = new JavaNLPAnnotatorReaderAndWriter();
  // j.init(props);
  // Iterator<DataInstance> iter = j.getIterator(new BufferedReader(new
  // FileReader(labeledFile)));
  // int i = 0;
  // while (iter.hasNext()) {
  // i++;
  // DataInstance s = iter.next();
  // String id = s.get(0).get(CoreAnnotations.DocIDAnnotation.class);
  // if (id == null) {
  // id = Integer.toString(i);
  // }
  // sents.put(id, s);
  // }
  // System.out.println("Read " + sents.size() + " eval sentences");
  // return sents;
  // }

  // private void evaluate(String label, Map<String, DataInstance> sents)
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
  // SurfaceE>, CollectionValuedMap<String, Integer>>>> list = new
  // ArrayList<Future<Pair<TwoDimensionalCounter<Pair<String, String>,
  // SurfaceE>, CollectionValuedMap<String, Integer>>>>();
  // for (int i = 0; i < constVars.numThreads; i++) {
  // // Redwood.log(ConstantsAndVariables.minimaldebug, "assigning from " + i *
  // // num + " till " + Math.min(keyset.size(), (i + 1) * num));
  //
  // Callable<Pair<TwoDimensionalCounter<Pair<String, String>, SurfaceE>,
  // CollectionValuedMap<String, Integer>>> task = null;
  // task = new ApplyPatterns(keyset.subList(i * num,
  // Math.min(keyset.size(), (i + 1) * num)),
  // this.learnedPatterns.get(label), constVars.commonEngWords,
  // usePatternResultAsLabel, this.learnedWords.get(label).keySet(),
  // restrictToMatched, label,
  // constVars.removeStopWordsFromSelectedPhrases,
  // constVars.removePhrasesWithStopWords, constVars);
  // Future<Pair<TwoDimensionalCounter<Pair<String, String>, SurfaceE>,
  // CollectionValuedMap<String, Integer>>> submit = executor
  // .submit(task);
  // list.add(submit);
  // }
  // for (Future<Pair<TwoDimensionalCounter<Pair<String, String>,
  // SurfaceE>, CollectionValuedMap<String, Integer>>> future : list) {
  // Pair<TwoDimensionalCounter<Pair<String, String>, SurfaceE>,
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
  // for (Entry<String, DataInstance> sent : sents.entrySet()) {
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

  public void evaluate(Map<String, DataInstance> testSentences, boolean evalPerEntity) throws IOException {

    for (Entry<String, Class<? extends Key<String>>> anscl : constVars.getAnswerClass().entrySet()) {
      String label = anscl.getKey();
      Counter<String> entityTP = new ClassicCounter<>();
      Counter<String> entityFP = new ClassicCounter<>();
      Counter<String> entityFN = new ClassicCounter<>();

      Counter<String> wordTP = new ClassicCounter<>();
      Counter<String> wordTN = new ClassicCounter<>();
      Counter<String> wordFP = new ClassicCounter<>();
      Counter<String> wordFN = new ClassicCounter<>();

      for (Entry<String, DataInstance> docEn : testSentences.entrySet()) {
        DataInstance doc = docEn.getValue();
        List<CoreLabel> doceval = new ArrayList<>();
        for (CoreLabel l : doc.getTokens()) {
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

  private static List<File> getAllFiles(String file) {

    List<File> allFiles = new ArrayList<>();
    for (String tokfile : file.split("[,;]")) {
      File filef = new File(tokfile);
      if (filef.isDirectory()) {
        Redwood.log(Redwood.DBG, "Will read from directory " + filef);
        String path = ".*";
        File dir = filef;
        for (File f : IOUtils.iterFilesRecursive(dir, java.util.regex.Pattern.compile(path))) {
          Redwood.log(ConstantsAndVariables.extremedebug, "Will read from file " + f);
          allFiles.add(f);
        }
      } else {
        if (filef.exists()) {
          Redwood.log(Redwood.DBG, "Will read from file " + filef);
          allFiles.add(filef);
        } else {
          Redwood.log(Redwood.DBG, "trying to read from file " + filef);
          //Is this a pattern?
          RegExFileFilter fileFilter = new RegExFileFilter(java.util.regex.Pattern.compile(filef.getName()));
          File dir = new File(tokfile.substring(0, tokfile.lastIndexOf("/")));
          File[] files = dir.listFiles(fileFilter);
          allFiles.addAll(Arrays.asList(files));
        }
      }


    }

    return allFiles;
  }

  private Pair<Double, Double> getPrecisionRecall(String label, Map<String, Boolean> goldWords4Label) {
    Set<CandidatePhrase> learnedWords = constVars.getLearnedWords(label).keySet();
    int numcorrect = 0, numincorrect = 0;
    int numgoldcorrect = 0;
    for (Entry<String, Boolean> en : goldWords4Label.entrySet()) {
      if (en.getValue())
        numgoldcorrect++;
    }
    Set<String> assumedNeg = new HashSet<>();
    for (CandidatePhrase e : learnedWords) {
      if (!goldWords4Label.containsKey(e.getPhrase())) {
        assumedNeg.add(e.getPhrase());

        numincorrect++;
        continue;
      }
      if (goldWords4Label.get(e.getPhrase())) {
        numcorrect++;
      } else
        numincorrect++;
    }

    if (!assumedNeg.isEmpty())
      log.info("\nGold entity list does not contain words " + assumedNeg + " for label " + label + ". *****Assuming them as negative.******");

    double precision = numcorrect / (double) (numcorrect + numincorrect);
    double recall = numcorrect / (double) (numgoldcorrect);
    return new Pair<>(precision, recall);
  }

  private static double FScore(double precision, double recall, double beta) {
    double betasq = beta * beta;
    return (1 + betasq) * precision * recall / (betasq * precision + recall);
  }

  public Set<String> getNonBackgroundLabels(CoreLabel l){
    Set<String> labels = new HashSet<>();
    for(Map.Entry<String, Class<? extends Key<String>>> en: constVars.getAnswerClass().entrySet()){
      if(!l.get(en.getValue()).equals(constVars.backgroundSymbol)){
        labels.add(en.getKey());
      }
    }
    return labels;
  }

  public static Map<String, Set<CandidatePhrase>> readSeedWordsFromJSONString(String str){
    Map<String, Set<CandidatePhrase>> seedWords  = new HashMap<>();
    JsonReader jsonReader = Json.createReader(new StringReader(str));
    JsonObject obj = jsonReader.readObject();

    jsonReader.close();
    for (String o : obj.keySet()){
      seedWords.put(o, new HashSet<>());
      JsonArray arr  = obj.getJsonArray(o);
      for(JsonValue v: arr)
        seedWords.get(o).add(CandidatePhrase.createOrGet(v.toString()));
    }
    return seedWords;
  }

  public static Map<String, Set<CandidatePhrase>> readSeedWords(Properties props) {
    String seedWordsFile = props.getProperty("seedWordsFiles");
    if(seedWordsFile != null)
      return readSeedWords(seedWordsFile);
    else{
      Redwood.log(Redwood.FORCE,"NO SEED WORDS FILES PROVIDED!!");
    return Collections.emptyMap();
    }
  }

  public static Map<String, Set<CandidatePhrase>> readSeedWords(String seedWordsFiles){
    Map<String, Set<CandidatePhrase>> seedWords  = new HashMap<>();


    if (seedWordsFiles == null) {
      throw new RuntimeException(
        "Needs both seedWordsFiles and file parameters to run this class!\nseedWordsFiles has format: label1,filewithlistofwords1;label2,filewithlistofwords2;...");
    }
    for (String seedFile : seedWordsFiles.split(";")) {
      String[] t = seedFile.split(",");
      String label = t[0];
      Set<CandidatePhrase> seedWords4Label = new HashSet<>();

      for(int i = 1; i < t.length; i++){
        String seedWordsFile = t[i];
        for(File fin: ConstantsAndVariables.listFileIncludingItself(seedWordsFile)){
            Redwood.log(Redwood.DBG, "Reading seed words from " + fin + " for label " + label);
            for (String line : IOUtils.readLines(fin)) {
              line = line.trim();
              if (line.isEmpty() || line.startsWith("#")) {
                continue;
              }
              line = line.split("\t")[0];
              seedWords4Label.add(CandidatePhrase.createOrGet(line));
            }
          }
      }

      seedWords.put(label, seedWords4Label);
      Redwood.log(ConstantsAndVariables.minimaldebug, "Number of seed words for label " + label + " is " + seedWords4Label.size());
    }
    return seedWords;
  }

  void removeLabelings(String label, Collection<String> removeLabeledPhrases){
    //TODO: write this up when appropriate
  }

  static Class[] printOptionClass = {String.class, Boolean.class, Integer.class, Long.class, Double.class, Float.class};
  public Map<String, String> getAllOptions(){
    Map<String, String> values = new HashMap<>();
    props.forEach((x, y) -> values.put(x.toString(), y.toString()));
    values.putAll(constVars.getAllOptions());
    //StringBuilder sb = new StringBuilder();

    Class<?> thisClass;
    try {
      thisClass = Class.forName(this.getClass().getName());

      Field[] aClassFields = thisClass.getDeclaredFields();
      //sb.append(this.getClass().getSimpleName() + " [ ");
      for(Field f : aClassFields){
        if(f.getGenericType().getClass().isPrimitive() || Arrays.binarySearch(printOptionClass, f.getType().getClass()) >= 0){
          String fName = f.getName();
          Object fvalue = f.get(this);
          values.put(fName, fvalue == null?"null":fvalue.toString());
        //sb.append("(" + f.getType() + ") " + fName + " = " + f.get(this) + ", ");
        }
      }

    } catch (Exception e) {
      log.warn(e);
    }

    return values;
  }

  public static class Flags {
    static public String useTargetParserParentRestriction = "useTargetParserParentRestriction";
    public static String useTargetNERRestriction = "useTargetNERRestriction";
    public static String posModelPath = "posModelPath";
    public static String numThreads = "numThreads";
    public static String patternType = "patternType";
    public static String numIterationsOfSavedPatternsToLoad = "numIterationsOfSavedPatternsToLoad";
    public static String patternsWordsDir = "patternsWordsDir";
    public static String loadModelForLabels = "loadModelForLabels";
  }

  public static Pair<Map<String, DataInstance>,Map<String, DataInstance>> processSents(Properties props, Set<String> labels) throws IOException, ExecutionException, InterruptedException, ClassNotFoundException {
    String fileFormat = props.getProperty("fileFormat");
    Map<String, DataInstance> sents = null;
    boolean batchProcessSents = Boolean.parseBoolean(props.getProperty("batchProcessSents", "false"));
    int numMaxSentencesPerBatchFile = Integer.parseInt(props.getProperty("numMaxSentencesPerBatchFile", String.valueOf(Integer.MAX_VALUE)));

    //works only for non-batch processing!
    boolean preserveSentenceSequence = Boolean.parseBoolean(props.getProperty("preserveSentenceSequence","false"));

    if (!batchProcessSents){
      if(preserveSentenceSequence)
        sents = new LinkedHashMap<>();
      else
        sents = new HashMap<>();

    }
    else {
      Data.sentsFiles = new ArrayList<>();
      Data.sentId2File = new ConcurrentHashMap<>();
    }

    String file = props.getProperty("file");

    String posModelPath = props.getProperty("posModelPath");
    boolean lowercase = Boolean.parseBoolean(props.getProperty("lowercaseText"));
    boolean useTargetNERRestriction = Boolean.parseBoolean(props.getProperty("useTargetNERRestriction"));
    boolean useTargetParserParentRestriction = Boolean.parseBoolean(props.getProperty(Flags.useTargetParserParentRestriction));
    boolean useContextNERRestriction = Boolean.parseBoolean(props.getProperty("useContextNERRestriction"));
    boolean addEvalSentsToTrain = Boolean.parseBoolean(props.getProperty("addEvalSentsToTrain","true"));
    String evalFileWithGoldLabels = props.getProperty("evalFileWithGoldLabels");

    if (file == null && (evalFileWithGoldLabels == null || addEvalSentsToTrain == false)) {
      throw new RuntimeException("No training data! file is " + file + " and evalFileWithGoldLabels is " + evalFileWithGoldLabels
        + " and addEvalSentsToTrain is " + addEvalSentsToTrain);
    }

    if(props.getProperty(Flags.patternType) == null)
      throw new RuntimeException("PatternType not specified. Options are SURFACE and DEP");

    PatternFactory.PatternType patternType = PatternFactory.PatternType.valueOf(props.getProperty(Flags.patternType));

    // Read training file
    if (file != null) {
      String saveSentencesSerDirstr = props.getProperty("saveSentencesSerDir");
      File saveSentencesSerDir = null;
      if (saveSentencesSerDirstr != null) {
        saveSentencesSerDir = new File(saveSentencesSerDirstr);

        if(saveSentencesSerDir.exists() && !fileFormat.equalsIgnoreCase("ser"))
          IOUtils.deleteDirRecursively(saveSentencesSerDir);

        IOUtils.ensureDir(saveSentencesSerDir);
      }

      String systemdir = System.getProperty("java.io.tmpdir");
      File tempSaveSentencesDir = File.createTempFile("sents", ".tmp", new File(systemdir));
      tempSaveSentencesDir.deleteOnExit();
      tempSaveSentencesDir.delete();
      tempSaveSentencesDir.mkdir();


      int numFilesTillNow = 0;
      if (fileFormat == null || fileFormat.equalsIgnoreCase("text") || fileFormat.equalsIgnoreCase("txt")) {

        Map<String, DataInstance> sentsthis ;
        if(preserveSentenceSequence)
          sentsthis = new LinkedHashMap<>();
        else
          sentsthis = new HashMap<>();

        for (File f : GetPatternsFromDataMultiClass.getAllFiles(file)) {
          Redwood.log(Redwood.DBG, "Annotating text in " + f);

          //String text = IOUtils.stringFromFile(f.getAbsolutePath());

          Iterator<String> reader = IOUtils.readLines(f).iterator();
          while(reader.hasNext()){
            numFilesTillNow = tokenize(reader, posModelPath, lowercase, useTargetNERRestriction || useContextNERRestriction, f.getName() + "-" + numFilesTillNow+"-",
              useTargetParserParentRestriction, props.getProperty(Flags.numThreads), batchProcessSents, numMaxSentencesPerBatchFile,
              saveSentencesSerDir == null? tempSaveSentencesDir : saveSentencesSerDir, sentsthis, numFilesTillNow, patternType);
          }

          if (!batchProcessSents) {
            sents.putAll(sentsthis);
          }
        }

        if (!batchProcessSents) {
//          for(Map.Entry<String, DataInstance> d: sents.entrySet()){
//            for(CoreLabel l : d.getValue().getTokens()){
//              for(String label: labels) {
//                if(l.containsKey(PatternsAnnotations.LongestMatchedPhraseForEachLabel.class)){
//                  CandidatePhrase p = l.get(PatternsAnnotations.LongestMatchedPhraseForEachLabel.class).get(label);
//                }
//              }
//            }
//          }

          String outfilename= (saveSentencesSerDir == null ? tempSaveSentencesDir : saveSentencesSerDir) + "/sents_" + numFilesTillNow;
          if(saveSentencesSerDir != null)
            Data.inMemorySaveFileLocation = outfilename;

          Redwood.log(Redwood.FORCE, "Saving sentences in " + outfilename);
          IOUtils.writeObjectToFile(sents, outfilename);
        }
      } else if (fileFormat.equalsIgnoreCase("ser")) {
        for (File f : GetPatternsFromDataMultiClass.getAllFiles(file)) {
          Redwood.log(Redwood.DBG, "reading from ser file " + f);
          if (!batchProcessSents)
            sents.putAll((Map<String, DataInstance>) IOUtils.readObjectFromFile(f));
          else{
            File newf = new File(tempSaveSentencesDir.getAbsolutePath() + "/" + f.getAbsolutePath().replaceAll(java.util.regex.Pattern.quote("/"), "_"));
            IOUtils.cp(f, newf);
            Data.sentsFiles.add(newf);
          }
        }
      } else {
        throw new RuntimeException(
          "Cannot identify the file format. Valid values are text (or txt) and ser, where the serialized file is of the type Map<String, DataInstance>.");
      }
    }

    Map<String, DataInstance> evalsents = new HashMap<>();

    boolean evaluate = Boolean.parseBoolean(props.getProperty("evaluate"));

    // Read Evaluation File
    if (evaluate) {
      if (evalFileWithGoldLabels != null) {

        String saveEvalSentencesSerFile = props.getProperty("saveEvalSentencesSerFile");
        File saveEvalSentencesSerFileFile = null;
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
        if (evalFileFormat == null || evalFileFormat.equalsIgnoreCase("text") || evalFileFormat.equalsIgnoreCase("txt") || evalFileFormat.startsWith("text")) {
          for (File f : allFiles) {
            numFile++;
            Redwood.log(Redwood.DBG, "Annotating text in " + f + ". Num file " + numFile);
            if(evalFileFormat.equalsIgnoreCase("textCoNLLStyle")){
              Map<String, DataInstance> sentsEval = AnnotatedTextReader.parseColumnFile(new BufferedReader(new FileReader(f)), labels, setClassForTheseLabels, true, f.getName());
              evalsents.putAll(runPOSNERParseOnTokens(sentsEval, props));
            } else{
              List<CoreMap> sentsCMs = AnnotatedTextReader.parseFile(new BufferedReader(new FileReader(f)), labels,
                setClassForTheseLabels, true, f.getName());
              evalsents.putAll(runPOSNEROnTokens(sentsCMs, posModelPath, useTargetNERRestriction || useContextNERRestriction, "",
                useTargetParserParentRestriction, props.getProperty(Flags.numThreads), patternType));
            }
          }

        } else if (fileFormat.equalsIgnoreCase("ser")) {
          for (File f : allFiles) {
            evalsents.putAll((Map<? extends String, ? extends DataInstance>) IOUtils.readObjectFromFile(f));
          }
        }
        if (addEvalSentsToTrain) {
          Redwood.log(Redwood.DBG, "Adding " + evalsents.size() + " eval sents to the training set");
        }

        IOUtils.writeObjectToFile(evalsents, saveEvalSentencesSerFileFile);

        if (batchProcessSents) {

          Data.sentsFiles.add(saveEvalSentencesSerFileFile);

          for(String k: evalsents.keySet())
            Data.sentId2File.put(k, saveEvalSentencesSerFileFile);
        } else
          sents.putAll(evalsents);
      }
    }
    return new Pair<Map<String, DataInstance>,Map<String, DataInstance>>(sents, evalsents);
  }

  private void saveModel() throws IOException {
    String patternsWordsDirValue = props.getProperty("patternsWordsDir");
    String patternsWordsDir;
    if (patternsWordsDirValue.endsWith(".zip")) {
      File temp = File.createTempFile("patswords", "dir");
      temp.deleteOnExit();
      temp.delete();
      temp.mkdirs();
      patternsWordsDir = temp.getAbsolutePath();
    } else {
      patternsWordsDir = patternsWordsDirValue;
    }
    Redwood.log(Redwood.FORCE, "Saving output in " + patternsWordsDir);

    IOUtils.ensureDir(new File(patternsWordsDir));
    //writing properties file
    String outPropertiesFile = patternsWordsDir+"model.properties";
    props.store(new BufferedWriter(new FileWriter(outPropertiesFile)), "trained model properties file");

    for (String label : constVars.getLabels()) {

      IOUtils.ensureDir(new File(patternsWordsDir + "/" + label));

      BufferedWriter seedW = new BufferedWriter(new FileWriter(patternsWordsDir+"/"+label+"/seedwords.txt"));

      for(CandidatePhrase p : constVars.getSeedLabelDictionary().get(label)){
        seedW.write(p.getPhrase()+"\n");
      }
      seedW.close();

      Map<Integer, Counter<E>> pats = getLearnedPatternsEachIter(label);
      IOUtils.writeObjectToFile(pats, patternsWordsDir + "/" + label + "/patternsEachIter.ser");


      BufferedWriter w = new BufferedWriter(new FileWriter(patternsWordsDir + "/" + label + "/phrases.txt"));
      writeWordsToFile(constVars.getLearnedWordsEachIter(label), w);

      //Write env
      writeClassesInEnv(constVars.env, ConstantsAndVariables.globalEnv, patternsWordsDir + "/env.txt");

      //Write the token mapping
      if (constVars.patternType.equals(PatternFactory.PatternType.SURFACE))
        IOUtils.writeStringToFile(Token.toStringClass2KeyMapping(), patternsWordsDir + "/tokenenv.txt", "utf8");

      w.close();
    }
//    if (patternsWordsDirValue.endsWith(".zip")) {
//      Redwood.log("Saving the zipped model to " + patternsWordsDirValue);
//      zip(patternsWordsDir, patternsWordsDirValue);
//    }
  }

  private void evaluate(Map<String, DataInstance> evalsents) throws IOException {
    if(constVars.goldEntitiesEvalFiles !=null) {

      for (String label : constVars.getLabels()) {
        if(constVars.goldEntities.containsKey(label)){
          Pair<Double, Double> pr = getPrecisionRecall(label, constVars.goldEntities.get(label));
          Redwood.log(ConstantsAndVariables.minimaldebug,
            "\nFor label " + label + ": Number of gold entities is " + constVars.goldEntities.get(label).size() + ", Precision is " + df.format(pr.first() * 100)
              + ", Recall is " + df.format(pr.second() * 100) + ", F1 is " + df.format(FScore(pr.first(), pr.second(), 1.0) * 100)
              + "\n\n");
        }
      }
    }

    if(evalsents.size() > 0){
      boolean evalPerEntity = Boolean.parseBoolean(props.getProperty("evalPerEntity", "true"));
      evaluate(evalsents, evalPerEntity);
    }

    if (evalsents.size() == 0 && constVars.goldEntitiesEvalFiles == null)
      log.info("No eval sentences or list of gold entities provided to evaluate! Make sure evalFileWithGoldLabels or goldEntitiesEvalFiles is set, or turn off the evaluate flag");

  }



  /**
   * Execute the system give a properties file or object. Returns the model created
   * @param props
   */
  public static<E extends Pattern> GetPatternsFromDataMultiClass<E> run(Properties props) throws IOException, ClassNotFoundException, IllegalAccessException, InterruptedException, ExecutionException, InstantiationException, NoSuchMethodException, InvocationTargetException, SQLException {
    Map<String, Set<CandidatePhrase>> seedWords = readSeedWords(props);

    Map<String, Class> answerClasses = new HashMap<>();
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

    //process all the sentences here!
    Pair<Map<String, DataInstance>, Map<String, DataInstance>> sentsPair = processSents(props, seedWords.keySet());

    boolean labelUsingSeedSets = Boolean.parseBoolean(props.getProperty("labelUsingSeedSets", "true"));

    GetPatternsFromDataMultiClass<E> model = new GetPatternsFromDataMultiClass<>(props, sentsPair.first(), seedWords, labelUsingSeedSets);
    return runNineYards(model, props, sentsPair.second());
  }

  private static<E extends Pattern> GetPatternsFromDataMultiClass<E> runNineYards(GetPatternsFromDataMultiClass<E> model, Properties props, Map<String, DataInstance> evalsents) throws IOException, ClassNotFoundException {

    ArgumentParser.fillOptions(model, props);

    // If you want to reuse patterns and words learned previously (may be on another dataset etc)
    boolean loadSavedPatternsWordsDir = Boolean.parseBoolean(props.getProperty("loadSavedPatternsWordsDir"));


    //#################### Load already save pattersn and phrases
    if (loadSavedPatternsWordsDir)
      loadFromSavedPatternsWordsDir(model , props);


    if (model.constVars.learn) {
      Map<String, E> p0 = new HashMap<>();
      Map<String, Counter<CandidatePhrase>> p0Set = new HashMap<>();
      Map<String, Set<E>> ignorePatterns = new HashMap<>();
      model.iterateExtractApply(p0, p0Set, ignorePatterns);
    }

    //############ Write Output files
    if (model.constVars.markedOutputTextFile != null)
      model.writeLabeledData(model.constVars.markedOutputTextFile);


    if(model.constVars.columnOutputFile != null)
      writeColumnOutput(model.constVars.columnOutputFile, model.constVars.batchProcessSents, model.constVars.getAnswerClass());

    //###################### SAVE MODEL
    if(model.constVars.savePatternsWordsDir)
      model.saveModel();


    //######## EVALUATE ###########################3
    boolean evaluate = Boolean.parseBoolean(props.getProperty("evaluate"));

    if (evaluate && evalsents != null) {
      model.evaluate(evalsents);
    }

    if(model.constVars.saveInvertedIndex){
      model.constVars.invertedIndex.saveIndex(model.constVars.invertedIndexDirectory);
    }

    if(model.constVars.storePatsForEachToken.equals(ConstantsAndVariables.PatternForEachTokenWay.LUCENE)){
      model.patsForEachToken.close();
    }

    return model;
  }

  static int numIterationsLoadedModel = 0;

//  static void unzip(String file, String outputDir) throws IOException {
//    ZipFile zipFile = new ZipFile(file);
//    Enumeration<? extends ZipEntry> entries = zipFile.entries();
//    while (entries.hasMoreElements()) {
//      ZipEntry entry = entries.nextElement();
//      Path entryDestination = new File(outputDir,  entry.getName()).toPath();
//      entryDestination.toFile().getParentFile().mkdirs();
//      if (entry.isDirectory())
//        entryDestination.toFile().mkdirs();
//      else {
//        InputStream in = zipFile.getInputStream(entry);
//        Files.copy(in, entryDestination);
//        in.close();
//      }
//    }
//  }
//
//  static void zip(String directory, String outputFileName) throws IOException {
//    FileOutputStream fos = new FileOutputStream(outputFileName);
//    ZipOutputStream zos = new ZipOutputStream(fos);
//    //level - the compression level (0-9)
//    zos.setLevel(9);
//    addFolder(zos, directory, directory);
//    zos.close();
//  }

  /** copied from http://www.justexample.com/wp/compress-folder-into-zip-file-using-java/ */
    private static void addFolder(ZipOutputStream zos,String folderName,String baseFolderName) throws IOException {
      File f = new File(folderName);
      if(f.exists()){

        if(f.isDirectory()){
          if(!folderName.equalsIgnoreCase(baseFolderName)){
            String entryName = folderName.substring(baseFolderName.length()+1,folderName.length()) + File.separatorChar;
            System.out.println("Adding folder entry " + entryName);
            ZipEntry ze= new ZipEntry(entryName);
            zos.putNextEntry(ze);
          }
          File[] f2 = f.listFiles();
          for (File aF2 : f2) {
            addFolder(zos, aF2.getAbsolutePath(), baseFolderName);
          }
        }else{
          //add file
          //extract the relative name for entry purpose
          String entryName = folderName.substring(baseFolderName.length()+1,folderName.length());
          ZipEntry ze= new ZipEntry(entryName);
          zos.putNextEntry(ze);
          FileInputStream in = new FileInputStream(folderName);
          int len;
          byte[] buffer = new byte[1024];
          while ((len = in.read(buffer)) < 0) {
            zos.write(buffer, 0, len);
          }
          in.close();
          zos.closeEntry();
          System.out.println("OK!");

        }
      }else{
        System.out.println("File or directory not found " + folderName);
      }

    }

  public static<E extends Pattern> Map<E, String> loadFromSavedPatternsWordsDir(GetPatternsFromDataMultiClass<E> model, Properties props) throws IOException, ClassNotFoundException {

    boolean labelSentsUsingModel = Boolean.parseBoolean(props.getProperty("labelSentsUsingModel","true"));
    boolean applyPatsUsingModel = Boolean.parseBoolean(props.getProperty("applyPatsUsingModel","true"));
    int numIterationsOfSavedPatternsToLoad = Integer.parseInt(props.getProperty(Flags.numIterationsOfSavedPatternsToLoad,String.valueOf(Integer.MAX_VALUE)));

    Map<E, String> labelsForPattterns = new HashMap<>();
    String patternsWordsDirValue = props.getProperty(Flags.patternsWordsDir);
    String patternsWordsDir;
//    if(patternsWordsDirValue.endsWith(".zip")){
//      File tempdir = File.createTempFile("patternswordsdir","dir");
//      tempdir.deleteOnExit();
//      tempdir.delete();
//      tempdir.mkdirs();
//      patternsWordsDir = tempdir.getAbsolutePath();
//      unzip(patternsWordsDirValue, patternsWordsDir);
//    }else
      patternsWordsDir = patternsWordsDirValue;


    String sentsOutFile = props.getProperty("sentsOutFile");
    String loadModelForLabels = props.getProperty(Flags.loadModelForLabels);
    List<String> loadModelForLabelsList = null;
    if(loadModelForLabels != null)
      loadModelForLabelsList = Arrays.asList(loadModelForLabels.split("[,;]"));

    for (String label : model.constVars.getLabels()) {

      if(loadModelForLabels != null && !loadModelForLabelsList.contains(label))
        continue;

      assert (new File(patternsWordsDir + "/" + label).exists()) : "Why does the directory " + patternsWordsDir + "/" + label + " not exist?";


      readClassesInEnv(patternsWordsDir + "/env.txt", model.constVars.env, ConstantsAndVariables.globalEnv);

      //Read the token mapping
      if(model.constVars.patternType.equals(PatternFactory.PatternType.SURFACE))
        Token.setClass2KeyMapping(new File(patternsWordsDir+"/tokenenv.txt"));

      //Load Patterns
      File patf = new File(patternsWordsDir + "/" + label + "/patternsEachIter.ser");
      if (patf.exists()) {
        Map<Integer, Counter<E>> patterns = IOUtils.readObjectFromFile(patf);
        if(numIterationsOfSavedPatternsToLoad < Integer.MAX_VALUE){
          Set<Integer> toremove = new HashSet<>();
          for(Integer i : patterns.keySet()){
            if(i >= numIterationsOfSavedPatternsToLoad){
              System.out.println("Removing patterns from iteration " + i);
              toremove.add(i);
            }
          }
          for(Integer i: toremove)
            patterns.remove(i);
        }

        Counter<E> pats = Counters.flatten(patterns);
        for(E p : pats.keySet()){
          labelsForPattterns.put(p, label);
        }

        numIterationsLoadedModel = Math.max(numIterationsLoadedModel, patterns.size());

        model.setLearnedPatterns(pats, label);
        model.setLearnedPatternsEachIter(patterns, label);
        Redwood.log(Redwood.DBG, "Loaded " + model.getLearnedPatterns().get(label).size() + " patterns from " + patf);
      }

      //Load Words
      File wordf = new File(patternsWordsDir + "/" + label + "/phrases.txt");
      if (wordf.exists()) {
        TreeMap<Integer, Counter<CandidatePhrase>> words = GetPatternsFromDataMultiClass.readLearnedWordsFromFile(wordf);
        model.constVars.setLearnedWordsEachIter(words, label);

        if(numIterationsOfSavedPatternsToLoad < Integer.MAX_VALUE){
          Set<Integer> toremove = new HashSet<>();
          for(Integer i : words.keySet()){
            if(i >= numIterationsOfSavedPatternsToLoad){
              System.out.println("Removing patterns from iteration " + i);
              toremove.add(i);
            }
          }
          for(Integer i: toremove)
            words.remove(i);
        }

        numIterationsLoadedModel = Math.max(numIterationsLoadedModel, words.size());

        Redwood.log(Redwood.DBG, "Loaded " + words.size() + " phrases from " + wordf);
      }


      CollectionValuedMap<E, Triple<String, Integer, Integer>> matchedTokensByPat = new CollectionValuedMap<>();

      Iterator<Pair<Map<String, DataInstance>, File>> sentsIter = new ConstantsAndVariables.DataSentsIterator(model.constVars.batchProcessSents);
      TwoDimensionalCounter<CandidatePhrase, E> wordsandLemmaPatExtracted = new TwoDimensionalCounter<>();
      Set<CandidatePhrase> alreadyLabeledWords = new HashSet<>();
      while(sentsIter.hasNext()){
        Pair<Map<String, DataInstance>, File> sents = sentsIter.next();
        if(labelSentsUsingModel){
          Redwood.log(Redwood.DBG, "labeling sentences from " + sents.second() + " with the already learned words");
          assert sents.first() != null : "Why are sents null";
          model.labelWords(label, sents.first(), model.constVars.getLearnedWords(label).keySet(), sentsOutFile, matchedTokensByPat);
          if(sents.second().exists())
            IOUtils.writeObjectToFile(sents, sents.second());
        }
        if (model.constVars.restrictToMatched || applyPatsUsingModel) {
          Redwood.log(Redwood.DBG,"Applying patterns to " + sents.first().size() + " sentences");
          model.constVars.invertedIndex.add(sents.first(), true);
          model.constVars.invertedIndex.add(sents.first(), true);
          model.scorePhrases.applyPats(model.getLearnedPatterns(label), label, wordsandLemmaPatExtracted, matchedTokensByPat, alreadyLabeledWords);
        }
      }
      Counters.addInPlace(model.wordsPatExtracted.get(label), wordsandLemmaPatExtracted);


      System.out.println("All Extracted phrases are " + wordsandLemmaPatExtracted.firstKeySet());

    }
    System.out.flush();
    System.err.flush();
    return labelsForPattterns;
  }

  private void setLearnedPatternsEachIter(Map<Integer, Counter<E>> patterns, String label) {
     this.learnedPatternsEachIter.put(label, patterns);
  }

  private static void readClassesInEnv(String s, Map<String, Env> env, Env globalEnv) throws ClassNotFoundException {

    for(String line: IOUtils.readLines(s)){
      String[] toks = line.split("###");
      if(toks.length == 3){
        String label = toks[0];
        String name = toks[1];
        Class c = Class.forName(toks[2]);
        if(!env.containsKey(label))
          env.put(label, TokenSequencePattern.getNewEnv());
        env.get(label).bind(name, c);
      }else
      if(toks.length ==2){
        String name = toks[0];
        Class c = Class.forName(toks[1]);
        assert c!=null : " Why is name for " + toks[1] + " null";
        globalEnv.bind(name, c);
      }else
        throw new RuntimeException("Ill formed env file!");
    }
  }

  private static void writeClassesInEnv(Map<String, Env> env, Env globalEnv, String file) throws IOException {
    BufferedWriter w = new BufferedWriter(new FileWriter(file));
    for(Entry<String, Env> en: env.entrySet()){
      for(Entry<String, Object> en2: en.getValue().getVariables().entrySet()){
        if(en2.getValue() instanceof Class)
          w.write(en.getKey()+"###"+en2.getKey()+"###"+((Class)en2.getValue()).getName()+"\n");
      }
    }
    for(Entry<String, Object> en2: globalEnv.getVariables().entrySet()){
      if(en2.getValue() instanceof Class)
        w.write(en2.getKey()+"###"+ ((Class)en2.getValue()).getName()+"\n");
    }
    w.close();
  }

  public static String elapsedTime(Date d1, Date d2){
    try{
      Duration period = Duration.between(d1.toInstant(), d2.toInstant());
      // Note: this will become easier with Java 9, using toDaysPart() etc.
      long days = period.toDays();
      period = period.minusDays(days);
      long hours = period.toHours();
      period = period.minusHours(hours);
      long minutes = period.toMinutes();
      period = period.minusMinutes(minutes);
      long seconds = period.getSeconds();
      return days + " days, " + hours + " hours, " + minutes + " minutes, " + seconds + " seconds";
    } catch(java.lang.IllegalArgumentException e) {
      log.warn(e);
    }
    return "";
  }


  public static void main(String[] args) {
    try {
      Properties props = StringUtils.argsToPropertiesWithResolve(args);
      GetPatternsFromDataMultiClass.<SurfacePattern>run(props);
    } catch (OutOfMemoryError e) {
      System.out.println("Out of memory! Either change the memory allotted by running as java -mx20g ... for example if you want to allocate 20G. Or consider using batchProcessSents and numMaxSentencesPerBatchFile flags");
      log.warn(e);
    } catch (Exception e) {
      log.warn(e);
    }
  }

}
