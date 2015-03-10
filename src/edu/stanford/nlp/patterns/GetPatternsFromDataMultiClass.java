package edu.stanford.nlp.patterns;

import java.io.*;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.sql.SQLException;
import java.text.DecimalFormat;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;


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
import org.joda.time.Interval;
import org.joda.time.Period;

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
 * <code>java -mx1000m edu.stanford.nlp.patterns.GetPatternsFromDataMultiClass -file text_file -seedWordsFiles label1,seedwordlist1;label2,seedwordlist2;... -outDir output_directory (optional)</code>
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
 * <code>java -mx1000m -cp classpath edu.stanford.nlp.patterns.GetPatternsFromDataMultiClass -props dir-as-above/example.properties</code>
 *
 * <p>
 * IMPORTANT: Many flags are described in the classes
 * {@link ConstantsAndVariables}, {@link edu.stanford.nlp.patterns.surface.CreatePatterns}, and
 * {@link PhraseScorer}.
 *
 *
 *
 * @author Sonal Gupta (sonal@cs.stanford.edu)
 */

public class  GetPatternsFromDataMultiClass<E extends Pattern> implements Serializable {

  private static final long serialVersionUID = 1L;

  //public Map<String, Map<Integer, Set<E>>> patternsForEachToken = null;

  private PatternsForEachToken<E> patsForEachToken = null;

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

  Map<String, Counter<E>> learnedPatterns = new HashMap<String, Counter<E>>();
  //Same as learnedPatterns but with iteration information
  Map<String, Map<Integer, Counter<E>>> learnedPatternsEachIter = new HashMap<String, Map<Integer, Counter<E>>>();

  public Map<String, TwoDimensionalCounter<String, E>> wordsPatExtracted = new HashMap<String, TwoDimensionalCounter<String, E>>();

  Properties props;
  public ScorePhrases scorePhrases;
  public ConstantsAndVariables constVars;
  public CreatePatterns createPats;

  DecimalFormat df = new DecimalFormat("#.##");

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
    Map<String, Class<? extends TypesafeMap.Key<String>>> ansCl = new HashMap<String, Class<? extends TypesafeMap.Key<String>>>();
    ansCl.put(answerLabel, answerClass);

    Map<String, Class> generalizeClasses = new HashMap<String, Class>();

    Map<String, Map<Class, Object>> ignoreClasses = new HashMap<String, Map<Class, Object>>();
    ignoreClasses.put(answerLabel, new HashMap<Class, Object>());

    Map<String, Set<CandidatePhrase>> seedSets = new HashMap<String, Set<CandidatePhrase>>();
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
    Map<String, Class<? extends TypesafeMap.Key<String>>> ansCl = new HashMap<String, Class<? extends TypesafeMap.Key<String>>>();
    ansCl.put(answerLabel, answerClass);

    Map<String, Map<Class, Object>> iC = new HashMap<String, Map<Class, Object>>();
    iC.put(answerLabel, ignoreClasses);

    Map<String, Set<CandidatePhrase>> seedSets = new HashMap<String, Set<CandidatePhrase>>();
    seedSets.put(answerLabel, seedSet);
    setUpConstructor(sents, seedSets, labelUsingSeedSets, ansCl, generalizeClasses, iC);
  }

  @SuppressWarnings("rawtypes")
  public GetPatternsFromDataMultiClass(Properties props, Map<String, DataInstance> sents, Map<String, Set<CandidatePhrase>> seedSets,
      boolean labelUsingSeedSets) throws IOException, InstantiationException, IllegalAccessException, IllegalArgumentException,
      InvocationTargetException, NoSuchMethodException, SecurityException, ClassNotFoundException, InterruptedException, ExecutionException {
    this.props = props;
    Map<String, Class<? extends TypesafeMap.Key<String>>> ansCl = new HashMap<String, Class<? extends TypesafeMap.Key<String>>>();
    Map<String, Class> gC = new HashMap<String, Class>();
    Map<String, Map<Class, Object>> iC = new HashMap<String, Map<Class, Object>>();
    int i = 1;
    for (String label : seedSets.keySet()) {
      String ansclstr = "edu.stanford.nlp.patterns.PatternsAnnotations$PatternLabel" + i;
      ansCl.put(label, (Class<? extends Key<String>>) Class.forName(ansclstr));
      iC.put(label, new HashMap<Class, Object>());
      i++;
    }

    setUpConstructor(sents, seedSets, labelUsingSeedSets, ansCl, gC, iC);
  }

  @SuppressWarnings("rawtypes")
  public GetPatternsFromDataMultiClass(Properties props, Map<String, DataInstance> sents, Map<String, Set<CandidatePhrase>> seedSets,
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
  public GetPatternsFromDataMultiClass(Properties props, Map<String, DataInstance> sents, Map<String, Set<CandidatePhrase>> seedSets,
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
  private void setUpConstructor(Map<String, DataInstance> sents, Map<String, Set<CandidatePhrase>> seedSets, boolean labelUsingSeedSets,
      Map<String, Class<? extends TypesafeMap.Key<String>>> answerClass, Map<String, Class> generalizeClasses,
      Map<String, Map<Class, Object>> ignoreClasses) throws IOException, InstantiationException, IllegalAccessException, IllegalArgumentException,
      InvocationTargetException, NoSuchMethodException, SecurityException, InterruptedException, ExecutionException, ClassNotFoundException {

    Data.sents = sents;
    Execution.fillOptions(Data.class, props);
    Execution.fillOptions(ConstantsAndVariables.class, props);
    PatternFactory.setUp(props, PatternFactory.PatternType.valueOf(props.getProperty(Flags.patternType)));

    constVars = new ConstantsAndVariables(props, seedSets, answerClass, generalizeClasses, ignoreClasses);

    //Execution.fillOptions(constVars, props);
    //constVars.ignoreWordswithClassesDuringSelection = ignoreClasses;
    //constVars.addGeneralizeClasses(generalizeClasses);
    //constVars.setSeedLabelDictionary(seedSets);

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

    wordsPatExtracted = new HashMap<String, TwoDimensionalCounter<String, E>>();

    //File invIndexDir = null;
    //boolean createInvIndex = true;
//    if (constVars.loadInvertedIndexDir != null) {
//      createInvIndex = false;
//
//      constVars.invertedIndex = InvertedIndexByTokens.loadIndex(constVars.loadInvertedIndexDir);
//      if (constVars.invertedIndex.isBatchProcessed() != constVars.batchProcessSents) {
//        throw new RuntimeException("The index was created with batchProcessSents as " + constVars.invertedIndex.isBatchProcessed()
//            + ". Use the same flag or create a new index");
//      }
//      Redwood.log(Redwood.DBG, "Loaded index from " + constVars.loadInvertedIndexDir);
//    }
    // else if(constVars.saveInvertedIndexDir != null){

    // if(constVars.diskBackedInvertedIndex){
    // invIndexDir = new File(constVars.saveInvertedIndexDir+"/cache");
    // IOUtils.deleteDirRecursively(invIndexDir);
    // IOUtils.ensureDir(invIndexDir);
    // }}

//    else if (constVars.saveInvertedIndexDir == null) {
//
//      String dir = System.getProperty("java.io.tmpdir");
//      invIndexDir = File.createTempFile(dir, ".dir");
//      invIndexDir.delete();
//      invIndexDir.deleteOnExit();
//    }

//    Set<String> specialwords4Index = new HashSet<String>();
//    specialwords4Index.addAll(Arrays.asList("fw", "FW", "sw", "SW", "OTHERSEM", "othersem"));

    for (String label : answerClass.keySet()) {
      wordsPatExtracted.put(label, new TwoDimensionalCounter<String, E>());
//      specialwords4Index.add(label);
//      specialwords4Index.add(label.toLowerCase());
    }

    scorePhrases = new ScorePhrases(props, constVars);
    createPats = new CreatePatterns(props, constVars);
    assert !(constVars.doNotApplyPatterns && (PatternFactory.useStopWordsBeforeTerm || PatternFactory.numWordsCompound > 1)) : " Cannot have both doNotApplyPatterns and (useStopWordsBeforeTerm true or numWordsCompound > 1)!";

//    String prefixFileForIndex = null;
//    if (constVars.usingDirForSentsInIndex) {
//      prefixFileForIndex = constVars.saveSentencesSerDir;
//    }


    //  constVars.invertedIndex = new SentenceIndex(constVars.matchLowerCaseContext, constVars.getStopWords(), specialwords4Index,
     //   constVars.batchProcessSents);
      // new InvertedIndexByTokens(constVars.matchLowerCaseContext, constVars.getStopWords(), specialwords4Index,
      //    constVars.batchProcessSents, prefixFileForIndex);

    if(constVars.invertedIndexDirectory == null){
      File f  = File.createTempFile("inv","index");
      f.deleteOnExit();
      f.mkdir();
      constVars.invertedIndexDirectory = f.getAbsolutePath();
    }

    Set<String> extremelySmallStopWordsList = CollectionUtils.asSet(new String[]{".", ",", "in", "on", "of", "a", "the", "an"});

    //Function to use to how to add corelabels to index
    Function transformCoreLabelToString = new Function<CoreLabel, Map<String, String>>() {
      @Override
      public Map<String, String> apply(CoreLabel l) {
        Map<String, String> add = new HashMap<String, String>();
        for(Class gn: constVars.getGeneralizeClasses().values()){
          Object b  = l.get(gn);
          if(b != null && !b.toString().equals(constVars.backgroundSymbol)){
            add.put(Token.getKeyForClass(gn),b.toString());
          }
        }
        return add;
      }
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
      Data.rawFreq = new ClassicCounter<CandidatePhrase>();
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
        Data.computeRawFreqIfNull(sentsf, PatternFactory.numWordsCompound);
      }


      Redwood.log(Redwood.DBG, "Initializing sents size " + sentsf.size()
        + " sentences, either by labeling with the seed set or just setting the right classes");
      for (String l : constVars.getAnswerClass().keySet()) {
        Redwood.log(Redwood.DBG, "labelUsingSeedSets is " + labelUsingSeedSets + " and seed set size for " + l + " is " + (seedSets == null?"null":seedSets.get(l).size()));

        Set<CandidatePhrase> seed = seedSets == null || !labelUsingSeedSets ? new HashSet<CandidatePhrase>() : (seedSets.containsKey(l) ? seedSets.get(l)
          : new HashSet<CandidatePhrase>());

        runLabelSeedWords(sentsf, constVars.getAnswerClass().get(l), l, seed, constVars, labelUsingSeedSets);


        if (constVars.addIndvWordsFromPhrasesExceptLastAsNeg) {
          Redwood.log(ConstantsAndVariables.minimaldebug, "adding indv words from phrases except last as neg");
          Set<CandidatePhrase> otherseed = new HashSet<CandidatePhrase>();
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

      if (labelUsingSeedSets && constVars.getOtherSemanticClassesWords() != null)
        runLabelSeedWords(sentsf, PatternsAnnotations.OtherSemanticLabel.class, "OTHERSEM", constVars.getOtherSemanticClassesWords(), constVars, labelUsingSeedSets);

      if(constVars.removeOverLappingLabelsFromSeed){
        removeOverLappingLabels(sentsf);
      }

      if(createIndex)
        constVars.invertedIndex.add(sentsf, true);

      if(constVars.batchProcessSents){
        Redwood.log(Redwood.DBG, "Saving the labeled seed sents (if given the option) to the same file " + sentsIter.second());
        IOUtils.writeObjectToFile(sentsf, sentsIter.second());
      }
    }


//    if (constVars.batchProcessSents) {
//        for (File f : Data.sentsFiles) {
//
//          if(!f.exists())
//            throw new RuntimeException("File " + f + " does not exist. Something is wrong. Contact the author with full details.");
//
//          Redwood.log(Redwood.DBG, "Reading file from " + f.getAbsolutePath());
//
//          Map<String, DataInstance> sentsf = IOUtils.readObjectFromFile(f);
//
//          for(Entry<String, DataInstance> en: sentsf.entrySet()){
//            Data.sentId2File.put(en.getKey(), f);
//          }
//
//          totalNumSents += sentsf.size();
//
//          if(computeDataFreq){
//            Data.computeRawFreqIfNull(sentsf, PatternFactory.numWordsCompound);
//          }
//
//
//          Redwood.log(Redwood.DBG, "Initializing sents from " + f + " with " + sentsf.size()
//              + " sentences, either by labeling with the seed set or just setting the right classes");
//          for (String l : constVars.getAnswerClass().keySet()) {
//            Redwood.log(Redwood.DBG, "labelUsingSeedSets is " + labelUsingSeedSets + " and seed set size for " + l + " is " + (seedSets == null?"null":seedSets.size()));
//
//            Set<CandidatePhrase> seed = seedSets == null || !labelUsingSeedSets ? new HashSet<CandidatePhrase>() : (seedSets.containsKey(l) ? seedSets.get(l)
//                : new HashSet<CandidatePhrase>());
//
//            runLabelSeedWords(sentsf, constVars.getAnswerClass().get(l), l, seed, constVars, labelUsingSeedSets);
//
//
//            if (constVars.addIndvWordsFromPhrasesExceptLastAsNeg) {
//              Set<CandidatePhrase> otherseed = new HashSet<CandidatePhrase>();
//              for (CandidatePhrase s : seed) {
//                String[] t = s.getPhrase().split("\\s+");
//                for (int i = 0; i < t.length - 1; i++) {
//                  if (!seed.contains(t[i])) {
//                    otherseed.add(new CandidatePhrase(t[i]));
//                  }
//                }
//              }
//              runLabelSeedWords(sentsf, PatternsAnnotations.OtherSemanticLabel.class, "OTHERSEM", otherseed, constVars, labelUsingSeedSets);
//            }
//
//          }
//
//          if (constVars.getOtherSemanticClassesWords() != null)
//            runLabelSeedWords(sentsf, PatternsAnnotations.OtherSemanticLabel.class, "OTHERSEM", constVars.getOtherSemanticClassesWords(), constVars, labelUsingSeedSets);
//
//          if(constVars.removeOverLappingLabelsFromSeed){
//            removeOverLappingLabels(sentsf);
//          }
//
//          constVars.invertedIndex.add(sentsf, true);
//
//
//          Redwood.log(Redwood.DBG, "Saving the labeled seed sents (if given the option) to the same file " + f);
//          IOUtils.writeObjectToFile(sentsf, f);
//        }
//
//    } else {
//
//      //not batch processing sentences
//
//      totalNumSents = Data.sents.size();
//
//      if(computeDataFreq){
//        Data.computeRawFreqIfNull(Data.sents, PatternFactory.numWordsCompound);
//      }
//
//
//      Redwood.log(Redwood.DBG, "Initializing sents " + Data.sents.size()
//          + " sentences, either by labeling with the seed set or just setting the right classes");
//      for (String l : constVars.getAnswerClass().keySet()) {
//
//        Set<CandidatePhrase> seed = seedSets == null || !labelUsingSeedSets ? new HashSet<CandidatePhrase>() : (seedSets.containsKey(l) ? seedSets.get(l)
//            : new HashSet<CandidatePhrase>());
//
//        runLabelSeedWords(Data.sents, constVars.getAnswerClass().get(l), l, seed, constVars, labelUsingSeedSets);
//
//        if (constVars.addIndvWordsFromPhrasesExceptLastAsNeg) {
//          Set<CandidatePhrase> otherseed = new HashSet<CandidatePhrase>();
//          for (CandidatePhrase s : seed) {
//            String[] t = s.getPhrase().split("\\s+");
//            for (int i = 0; i < t.length - 1; i++) {
//              if (!seed.contains(t[i])) {
//                otherseed.add(new CandidatePhrase(t[i]));
//              }
//            }
//          }
//          runLabelSeedWords(Data.sents, PatternsAnnotations.OtherSemanticLabel.class, "OTHERSEM", otherseed, constVars, labelUsingSeedSets);
//        }
//      }
//
//
//      if (constVars.getOtherSemanticClassesWords() != null)
//        runLabelSeedWords(Data.sents, PatternsAnnotations.OtherSemanticLabel.class, "OTHERSEM", constVars.getOtherSemanticClassesWords() , constVars, labelUsingSeedSets);
//
//      if(constVars.removeOverLappingLabelsFromSeed){
//        removeOverLappingLabels(Data.sents);
//      }
//
//      if(createIndex)
//        constVars.invertedIndex.add(Data.sents, true);
//
//    }


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
          // if (answerClass.size() > 1 || this.labelDictionary.size() > 1)
          // throw new RuntimeException("not implemented");
          Execution.fillOptions(lmf, props);
          lmf.answerClass = answerClass.get(label);
          lmf.answerLabel = label;
          lmf.setUp();
          lmf.getTopFeatures(new ConstantsAndVariables.DataSentsIterator(constVars.batchProcessSents), constVars.perSelectRand, constVars.perSelectNeg,
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
      Counter<CandidatePhrase> dictOddsWeightsLabel = new ClassicCounter<CandidatePhrase>();
      Counter<CandidatePhrase> otherSemanticClassFreq = new ClassicCounter<CandidatePhrase>();
      for (CandidatePhrase s : constVars.getOtherSemanticClassesWords()) {
        for (String s1 : StringUtils.getNgrams(Arrays.asList(s.getPhrase().split("\\s+")), 1, PatternFactory.numWordsCompound))
          otherSemanticClassFreq.incrementCount(CandidatePhrase.createOrGet(s1));
      }
      otherSemanticClassFreq = Counters.add(otherSemanticClassFreq, 1.0);
      // otherSemanticClassFreq.setDefaultReturnValue(1.0);

      Map<String, Counter<CandidatePhrase>> labelDictNgram = new HashMap<String, Counter<CandidatePhrase>>();
      for (String label : seedSets.keySet()) {
        Counter<CandidatePhrase> classFreq = new ClassicCounter<CandidatePhrase>();
        for (CandidatePhrase s : seedSets.get(label)) {
          for (String s1 : StringUtils.getNgrams(Arrays.asList(s.getPhrase().split("\\s+")), 1, PatternFactory.numWordsCompound))
            classFreq.incrementCount(CandidatePhrase.createOrGet(s1));
        }
        classFreq = Counters.add(classFreq, 1.0);
        labelDictNgram.put(label, classFreq);
        // classFreq.setDefaultReturnValue(1.0);
      }

      for (String label : seedSets.keySet()) {
        Counter<CandidatePhrase> otherLabelFreq = new ClassicCounter<CandidatePhrase>();
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
   * keep only the DISEASE label for "lung". For this to work, you need to have <code>PatternsAnnotations.Ln</code> set, which is already done in runLabelSeedWords function.
   */
  public void removeOverLappingLabels(Map<String, DataInstance> sents){
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
    List<String> anns = new ArrayList<String>();
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
      List<CoreMap> temp = new ArrayList<CoreMap>();
      CoreMap s= new ArrayCoreMap();
      s.set(CoreAnnotations.TokensAnnotation.class, en.getValue().getTokens());
      temp.add(s);
      Annotation doc = new Annotation(temp);
      try {
        pipeline.annotate(doc);
        if (useTargetParserParentRestriction)
          inferParentParseTag(s.get(TreeAnnotation.class));
      }catch(Exception e){
        System.out.println("Ignoring error: for sentence  " + StringUtils.joinWords(en.getValue().getTokens(), " "));
        e.printStackTrace();
      }

    }

    Redwood.log(Redwood.DBG, "Done annotating text");
    return sents;
  }

  public static Map<String, DataInstance> runPOSNEROnTokens(List<CoreMap> sentsCM, String posModelPath, boolean useTargetNERRestriction,
      String prefix, boolean useTargetParserParentRestriction, String numThreads, PatternFactory.PatternType type) {
    Annotation doc = new Annotation(sentsCM);

    Properties props = new Properties();
    List<String> anns = new ArrayList<String>();
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

    Map<String, DataInstance> sents = new HashMap<String, DataInstance>();

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
      List<String> anns = new ArrayList<String>();
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
  public static List<Integer> getSubListIndex(String[] l1, String[] l2, String[] subl2, Set<String> doNotLabelTheseWords, HashSet<String> seenFuzzyMatches,
      int minLen4Fuzzy, boolean fuzzyMatch, boolean ignoreCaseSeedMatch) {
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
        if (!fuzzyMatch || doNotLabelTheseWords.contains(l2[i]) || doNotLabelTheseWords.contains(subl2[i]) || l2[i].length() <= minLen4Fuzzy || subl2[i].length() <= minLen4Fuzzy)
          compareFuzzy = false;
        if (compareFuzzy == false || l1[j].length() <= minLen4Fuzzy) {
          d1 = l1[j].equals(l2[i]) ? true : false;
          if (!d1 && fuzzyMatch)
            d2 = subl2[i].equals(l1[j]) ? true : false;
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
  static  Function<CoreLabel, String> stringTransformationFunction = new Function<CoreLabel, String>() {
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
    List<List<E>> threadedSentIds = new ArrayList<List<E>>();
    for (int i = 0; i < numThreads; i++) {
      List<E> keys = keyset.subList(i * num, Math.min(keyset.size(), (i + 1) * num));
      threadedSentIds.add(keys);
      Redwood.log(ConstantsAndVariables.extremedebug, "assigning from " + i * num + " till " + Math.min(keyset.size(), (i + 1) * num));
    }
    return threadedSentIds;
  }

  /** Warning: sets labels of words that are not in the given seed set as O!!!
   * */
  public static void runLabelSeedWords(Map<String, DataInstance> sents, Class answerclass, String label, Collection<CandidatePhrase> seedWords, ConstantsAndVariables constVars, boolean overwriteExistingLabels)
      throws InterruptedException, ExecutionException, IOException {

    List<List<String>> threadedSentIds = getThreadBatches(new ArrayList<String>(sents.keySet()), constVars.numThreads);
    ExecutorService executor = Executors.newFixedThreadPool(constVars.numThreads);
    List<Future<Map<String, DataInstance>>> list = new ArrayList<Future<Map<String, DataInstance>>>();

    for (List<String> keys: threadedSentIds) {
      Callable<Map<String, DataInstance>> task = new LabelWithSeedWords(seedWords, sents, keys, answerclass, label, constVars.fuzzyMatch, constVars.minLen4FuzzyForPattern, constVars.backgroundSymbol, constVars.getEnglishWords(),
        stringTransformationFunction, constVars.writeMatchedTokensIdsForEachPhrase, overwriteExistingLabels, constVars.patternType, constVars.ignoreCaseSeedMatch);
      Map<String, DataInstance> sentsi  = executor.submit(task).get();
      sents.putAll(sentsi);
    }

    // Now retrieve the result


    executor.shutdown();
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

  static void addLengthFeature(){}

  /**
   * Warning: sets labels of words that are not in the given seed set as O!!!
   */
  @SuppressWarnings("rawtypes")
  public static class LabelWithSeedWords implements Callable<Map<String, DataInstance>> {
    Map<CandidatePhrase, String[]> seedwordsTokens = new HashMap<CandidatePhrase, String[]>();
    Map<String, DataInstance> sents;
    List<String> keyset;
    Class labelClass;
    HashSet<String> seenFuzzyMatches = new HashSet<String>();
    String label;
    int minLen4FuzzyForPattern;
    String backgroundSymbol = "O";
    Set<String> doNotLabelDictWords = null;
    Function<CoreLabel, String> stringTransformation;
    boolean writeMatchedTokensIdsForEachPhrase = false;
    boolean overwriteExistingLabels;
    PatternFactory.PatternType patternType;
    boolean fuzzyMatch = false;
    boolean ignoreCaseSeedMatch = false;

    public LabelWithSeedWords(Collection<CandidatePhrase> seedwords, Map<String, DataInstance> sents, List<String> keyset, Class labelclass, String label, boolean fuzzyMatch,
                              int minLen4FuzzyForPattern, String backgroundSymbol, Set<String> doNotLabelDictWords,
                              Function<CoreLabel, String> stringTransformation, boolean writeMatchedTokensIdsForEachPhrase, boolean overwriteExistingLabels, PatternFactory.PatternType type, boolean ignoreCaseSeedMatch) {
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
    public Map<String, DataInstance> call()  {
      Map<String, DataInstance> newsent = new HashMap<String, DataInstance>();
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

        CollectionValuedMap<Integer, CandidatePhrase> matchedPhrases = new CollectionValuedMap<Integer, CandidatePhrase>();
        Map<Integer, CandidatePhrase> longestMatchedPhrases = new HashMap<Integer, CandidatePhrase>();

        for (Entry<CandidatePhrase, String[]> sEn : seedwordsTokens.entrySet()) {
          String[] s = sEn.getValue();
          CandidatePhrase sc = sEn.getKey();
          List<Integer> indices = getSubListIndex(s, tokens, tokenslemma, doNotLabelDictWords, seenFuzzyMatches,
              minLen4FuzzyForPattern, fuzzyMatch, ignoreCaseSeedMatch);

          if (indices != null && !indices.isEmpty()){
            String ph = StringUtils.join(s, " ");
            sc.addFeature("LENGTH-" + s.length, 1.0);

            Collection<String> features = new ArrayList<String>();

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
                  }catch(Exception e){e.printStackTrace();}
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
            l.set(PatternsAnnotations.MatchedPhrases.class, new CollectionValuedMap<String, CandidatePhrase>());

          if(!l.containsKey(PatternsAnnotations.LongestMatchedPhraseForEachLabel.class))
            l.set(PatternsAnnotations.LongestMatchedPhraseForEachLabel.class, new HashMap<String, CandidatePhrase>());

          if (labels[i]) {
            l.set(labelClass, label);

            //set whether labeled by the seeds or not
            if(!l.containsKey(PatternsAnnotations.SeedLabeledOrNot.class))
              l.set(PatternsAnnotations.SeedLabeledOrNot.class, new HashMap<Class, Boolean>());
            l.get(PatternsAnnotations.SeedLabeledOrNot.class).put(labelClass, true);

            if(l.get(PatternsAnnotations.LongestMatchedPhraseForEachLabel.class).containsKey(label))
              System.out.println("\n"+l.get(PatternsAnnotations.LongestMatchedPhraseForEachLabel.class).get(label) + " for label " + label+"\n\n");

            CandidatePhrase longestMatchingPh = l.get(PatternsAnnotations.LongestMatchedPhraseForEachLabel.class).get(label);
            assert longestMatchedPhrases.containsKey(i);
            longestMatchingPh = (longestMatchingPh != null && (longestMatchingPh.getPhrase().length() > longestMatchedPhrases.get(i).getPhrase().length())) ? longestMatchingPh : longestMatchedPhrases.get(i);
            l.get(PatternsAnnotations.LongestMatchedPhraseForEachLabel.class).put(label, longestMatchingPh);
            l.get(PatternsAnnotations.MatchedPhrases.class).addAll(label, matchedPhrases.get(i));

            Redwood.log(ConstantsAndVariables.extremedebug, "labeling " + l.word() + " or its lemma " + l.lemma() + " as " + label
              + " because of the dict phrases " + matchedPhrases.get(i));

          } else if(overwriteExistingLabels)
            l.set(labelClass, backgroundSymbol);


        }
        newsent.put(k, sent);
      }
      return newsent;
    }
  }

  static private void addToMatchedTokensByPhrase(String ph, String sentid, int index, int length){
    if(!Data.matchedTokensForEachPhrase.containsKey(ph))
      Data.matchedTokensForEachPhrase.put(ph, new HashMap<String, List<Integer>>());
    Map<String, List<Integer>> matcheds = Data.matchedTokensForEachPhrase.get(ph);
    if(!matcheds.containsKey(sentid))
      matcheds.put(sentid, new ArrayList<Integer>());
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

  void readSavedPatternsAndIndex() throws IOException, ClassNotFoundException {
    if(!constVars.computeAllPatterns) {
      assert constVars.allPatternsDir != null : "allPatternsDir flag cannot be emoty if computeAllPatterns is false!";
      //constVars.setPatternIndex(PatternIndex.load(constVars.allPatternsDir, constVars.storePatsIndex));
      if(constVars.storePatsForEachToken.equals(ConstantsAndVariables.PatternForEachTokenWay.MEMORY))
        patsForEachToken.load(constVars.allPatternsDir);
    }
  }

  @SuppressWarnings({ "unchecked" })
  public Counter<E> getPatterns(String label, Set<E> alreadyIdentifiedPatterns, E p0, Counter<CandidatePhrase> p0Set,
      Set<E> ignorePatterns) throws IOException, ClassNotFoundException {

    TwoDimensionalCounter<E, CandidatePhrase> patternsandWords4Label = new TwoDimensionalCounter<E, CandidatePhrase>();
    TwoDimensionalCounter<E, CandidatePhrase> negPatternsandWords4Label = new TwoDimensionalCounter<E, CandidatePhrase>();
    //TwoDimensionalCounter<E, String> posnegPatternsandWords4Label = new TwoDimensionalCounter<E, String>();
    TwoDimensionalCounter<E, CandidatePhrase> unLabeledPatternsandWords4Label = new TwoDimensionalCounter<E, CandidatePhrase>();
    //TwoDimensionalCounter<E, String> negandUnLabeledPatternsandWords4Label = new TwoDimensionalCounter<E, String>();
    //TwoDimensionalCounter<E, String> allPatternsandWords4Label = new TwoDimensionalCounter<E, String>();
    Set<String> allCandidatePhrases = new HashSet<String>();

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
    /*
      if (!constVars.batchProcessSents) {

        if(notComputedAllPatternsYet){
          // if not batch processing
          processSents(Data.sents);
          patsForEachToken = PatternsForEachToken.getPatternsInstance(props, constVars.storePatsForEachToken);
          readSavedPatternsAndIndex();
          System.out.println("size of pats for each token is " + patsForEachToken.size());
        }

       this.calculateSufficientStats(Data.sents, patsForEachToken, label, patternsandWords4Label, negPatternsandWords4Label, unLabeledPatternsandWords4Label, allCandidatePhrases);


      }// batch processing sentences
      else {
        for (File f : Data.sentsFiles) {
          Redwood.log(Redwood.DBG, (constVars.computeAllPatterns ? "Creating patterns and " : "") + "calculating sufficient statistics from " + f);
          Map<String, DataInstance> sents = IOUtils.readObjectFromFile(f);

          if(notComputedAllPatternsYet){
            //in the first iteration
            processSents(sents);
            if(patsForEachToken == null){
              //in the first iteration, for the first file
              patsForEachToken = PatternsForEachToken.getPatternsInstance(props, constVars.storePatsForEachToken);
              readSavedPatternsAndIndex();
            }
          }
          this.calculateSufficientStats(sents, patsForEachToken, label, patternsandWords4Label, negPatternsandWords4Label, unLabeledPatternsandWords4Label, allCandidatePhrases);
        }
      }
*/
    notComputedAllPatternsYet = false;

    if (constVars.computeAllPatterns){
      if(constVars.storePatsForEachToken.equals(ConstantsAndVariables.PatternForEachTokenWay.DB))
        patsForEachToken.createIndexIfUsingDBAndNotExists();


      IOUtils.ensureDir(new File(constVars.allPatternsDir));
      patsForEachToken.save(constVars.allPatternsDir);
      //savePatternIndex(constVars.allPatternsDir);
    }

    patsForEachToken.close();

    //This is important. It makes sure that we don't recompute patterns in every iteration!
    constVars.computeAllPatterns = false;


    if (patternsandWords == null)
      patternsandWords = new HashMap<String, TwoDimensionalCounter<E, CandidatePhrase>>();
//    if (allPatternsandWords == null)
//      allPatternsandWords = new HashMap<String, TwoDimensionalCounter<E, String>>();
    if (currentPatternWeights == null)
      currentPatternWeights = new HashMap<String, Counter<E>>();

    Counter<E> currentPatternWeights4Label = new ClassicCounter<E>();

    Set<E> removePats = enforceMinSupportRequirements(patternsandWords4Label, unLabeledPatternsandWords4Label);
    Counters.removeKeys(patternsandWords4Label, removePats);
    Counters.removeKeys(unLabeledPatternsandWords4Label, removePats);
//    Counters.removeKeys(negandUnLabeledPatternsandWords4Label, removePats);
//    Counters.removeKeys(allPatternsandWords4Label, removePats);
//    Counters.removeKeys(posnegPatternsandWords4Label, removePats);
    Counters.removeKeys(negPatternsandWords4Label, removePats);

    // Redwood.log(ConstantsAndVariables.extremedebug,
    // "Patterns around positive words in the label " + label + " are " +
    // patternsandWords4Label);
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
      Redwood.log(ConstantsAndVariables.extremedebug, "Patterns size is " + currentPatternWeights4Label.size());
        Counters.removeKeys(currentPatternWeights4Label, alreadyIdentifiedPatterns);
      Redwood.log(ConstantsAndVariables.extremedebug, "Removing already identified patterns of size  " + alreadyIdentifiedPatterns.size()
          + ". New patterns size " + currentPatternWeights4Label.size());
    }

    PriorityQueue<E> q = Counters.toPriorityQueue(currentPatternWeights4Label);
    int num = 0;

    Counter<E> chosenPat = new ClassicCounter<E>();

    Set<E> removePatterns = new HashSet<E>();

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
                removeIdentifiedPatterns = new HashSet<E>();

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
                removeChosenPats = new HashSet<E>();
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
      Redwood.log(ConstantsAndVariables.minimaldebug, en.first().toString() + ":" + df.format(en.second) + "\n");

    if (constVars.outDir != null && !constVars.outDir.isEmpty()) {
      CollectionValuedMap<E, CandidatePhrase> posWords = new CollectionValuedMap<E, CandidatePhrase>();
      for (Entry<E, ClassicCounter<CandidatePhrase>> en : patternsandWords4Label.entrySet()) {
        posWords.addAll(en.getKey(), en.getValue().keySet());
      }

      CollectionValuedMap<E, CandidatePhrase> negWords = new CollectionValuedMap<E, CandidatePhrase>();
      for (Entry<E, ClassicCounter<CandidatePhrase>> en : negPatternsandWords4Label.entrySet()) {
        negWords.addAll(en.getKey(), en.getValue().keySet());
      }
      CollectionValuedMap<E, CandidatePhrase> unlabWords = new CollectionValuedMap<E, CandidatePhrase>();
      for (Entry<E, ClassicCounter<CandidatePhrase>> en : unLabeledPatternsandWords4Label.entrySet()) {
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

    if (constVars.justify) {
      Redwood.log(Redwood.DBG, "Justification for Patterns:");
      for (E key : chosenPat.keySet()) {
        Redwood.log(Redwood.DBG, "\nPattern: " + key.toString());
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

  static AtomicInteger numCallsToCalStats = new AtomicInteger();


  public static <E> List<List<E>> splitIntoNumThreadsWithSampling(List<E> c, int n, int numThreads) {
    if (n < 0)
      throw new IllegalArgumentException("n < 0: " + n);
    if (n > c.size())
      throw new IllegalArgumentException("n > size of collection: " + n + ", " + c.size());
    List<List<E>> resultAll = new ArrayList<List<E>>(numThreads);
    int num;

    if (numThreads == 1)
      num = n;
    else
      num = n / (numThreads - 1);

    System.out.println("shuffled " + c.size() + " sentences and selecting " + num  + " sentences per thread");
    List<E> result = new ArrayList<E>(num);
    int totalitems = 0;
    int nitem = 0;
    Random r = new Random(numCallsToCalStats.incrementAndGet());
    boolean[] added = new boolean[c.size()];
    Arrays.fill(added, false);
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
        result = new ArrayList<E>(num);
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

    List<Future<Triple<List<Pair<E, CandidatePhrase>>, List<Pair<E, CandidatePhrase>>, List<Pair<E, CandidatePhrase>>>>> list = new ArrayList<Future<Triple<List<Pair<E, CandidatePhrase>>, List<Pair<E, CandidatePhrase>>, List<Pair<E, CandidatePhrase>>>>>();
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

      List<Pair<E, CandidatePhrase>> posWords = new ArrayList<Pair<E, CandidatePhrase>>();
      List<Pair<E, CandidatePhrase>> negWords = new ArrayList<Pair<E, CandidatePhrase>>();
      List<Pair<E, CandidatePhrase>> unlabWords = new ArrayList<Pair<E, CandidatePhrase>>();
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
              //E s = constVars.patternIndex.get(sindex);

              //patternsandWords4Label.getCounter(sindex).incrementCount(longestMatchingPhrase);
              posWords.add(new Pair<E, CandidatePhrase>(s, longestMatchingPhrase));
              //posnegPatternsandWords4Label.getCounter(sindex).incrementCount(longestMatchingPhrase);
              //allPatternsandWords4Label.getCounter(sindex).incrementCount(longestMatchingPhrase);
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
                negWords.add(new Pair<E, CandidatePhrase>(sindex, longestMatchingPhrase));
              } else {
                unlabWords.add(new Pair<E, CandidatePhrase>(sindex, longestMatchingPhrase));
              }

            }
          }
        }
      }
      return new Triple(posWords, negWords, unlabWords);
    }
  }

  private Set<E> enforceMinSupportRequirements(TwoDimensionalCounter<E, CandidatePhrase> patternsandWords4Label,
      TwoDimensionalCounter<E, CandidatePhrase> unLabeledPatternsandWords4Label) {
    Set<E> remove = new HashSet<E>();
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

  void removeLearnedPatterns(String label, Collection<E> pats) {
    Counters.removeKeys(this.learnedPatterns.get(label), pats);

    for(Map.Entry<Integer, Counter<E>> en: this.learnedPatternsEachIter.get(label).entrySet())
      Counters.removeKeys(en.getValue(), pats);

    if (wordsPatExtracted.containsKey(label))
      for (Entry<String, ClassicCounter<E>> en : this.wordsPatExtracted.get(label).entrySet()) {
        Counters.removeKeys(en.getValue(), pats);
      }
  }

  public static <E> Counter<E> normalizeSoftMaxMinMaxScores(Counter<E> scores, boolean minMaxNorm, boolean softmax, boolean oneMinusSoftMax) {
    double minScore = Double.MAX_VALUE, maxScore = Double.MIN_VALUE;
    Counter<E> newscores = new ClassicCounter<E>();
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

  public TwoDimensionalCounter<String, ScorePhraseMeasures> phInPatScoresCache = new TwoDimensionalCounter<String, ScorePhraseMeasures>();


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
      tokensMatchedPatterns = new CollectionValuedMap<String, Integer>();
      for (Entry<E, Collection<Triple<String, Integer, Integer>>> en : matchedTokensByPat.entrySet()) {
        for (Triple<String, Integer, Integer> en2 : en.getValue()) {
          for (int i = en2.second(); i <= en2.third(); i++) {
            tokensMatchedPatterns.add(en2.first(), i);
          }
        }
      }
    }

    Map<String, Map<Integer, Set<E>>> tempPatsForSents = new HashMap<String, Map<Integer, Set<E>>>();

    for (Entry<String, DataInstance> sentEn : sents.entrySet()) {
      List<CoreLabel> tokens = sentEn.getValue().getTokens();
      boolean sentenceChanged = false;
      Map<CandidatePhrase, String[]> identifiedWordsTokens = new HashMap<CandidatePhrase, String[]>();
      for (CandidatePhrase s : identifiedWords) {
        String[] toks = s.getPhrase().split("\\s+");
        identifiedWordsTokens.put(s, toks);
      }
      String[] sent = new String[tokens.size()];
      int i = 0;

      Set<Integer> contextWordsRecalculatePats = new HashSet<Integer>();

      for (CoreLabel l :tokens) {
        sent[i] = l.word();
        i++;
      }
      for (Entry<CandidatePhrase, String[]> phEn : identifiedWordsTokens.entrySet()) {
        String[] ph = phEn.getValue();
        //TODO: match lowercase text given option?!
        List<Integer> ints = ArrayUtils.getSubListIndex(ph, sent);
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
                CollectionValuedMap<String, CandidatePhrase> matched = new CollectionValuedMap<String, CandidatePhrase>();
                matched.add(label, phEn.getKey());
                if(!l.containsKey(PatternsAnnotations.MatchedPhrases.class))
                  l.set(PatternsAnnotations.MatchedPhrases.class, matched);
                else
                  l.get(PatternsAnnotations.MatchedPhrases.class).addAll(matched);

                CandidatePhrase longest = l.get(PatternsAnnotations.LongestMatchedPhraseForEachLabel.class).get(label);
                longest = longest != null && longest.getPhrase().length() > phEn.getKey().getPhrase().length() ? longest: phEn.getKey();
                l.get(PatternsAnnotations.LongestMatchedPhraseForEachLabel.class).put(label, longest);

                for (int k = Math.max(0, index - PatternFactory.numWordsCompound); k < tokens.size()
                    && k <= index + PatternFactory.numWordsCompound + 1; k++) {
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
            tempPatsForSents.put(sentEn.getKey(), new HashMap<Integer, Set<E>>());

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
  public void iterateExtractApply(Map<String, E> p0, Map<String, Counter<CandidatePhrase>> p0Set, String wordsOutputFile, String sentsOutFile,
      String patternsOutFile, Map<String, Set<E>> ignorePatterns) throws IOException, ClassNotFoundException {

    Map<String, CollectionValuedMap<E, Triple<String, Integer, Integer>>> matchedTokensByPatAllLabels = new HashMap<String, CollectionValuedMap<E, Triple<String, Integer, Integer>>>();
    Map<String, Collection<Triple<String, Integer, Integer>>> matchedTokensForPhrases = new HashMap<>();
    Map<String, TwoDimensionalCounter<String, E>> termsAllLabels = new HashMap<String, TwoDimensionalCounter<String, E>>();

    Map<String, Set<CandidatePhrase>> ignoreWordsAll = new HashMap<String, Set<CandidatePhrase>>();
    for (String label : constVars.getSeedLabelDictionary().keySet()) {
      matchedTokensByPatAllLabels.put(label, new CollectionValuedMap<E, Triple<String, Integer, Integer>>());
      termsAllLabels.put(label, new TwoDimensionalCounter<String, E>());
      if (constVars.useOtherLabelsWordsasNegative) {
        Set<CandidatePhrase> w = new HashSet<CandidatePhrase>();
        for (Entry<String, Set<CandidatePhrase>> en : constVars.getSeedLabelDictionary().entrySet()) {
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

    for (String label : constVars.getLabels()) {
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
      Map<String, Counter<CandidatePhrase>> learnedWordsThisIter = new HashMap<String, Counter<CandidatePhrase>>();
      for (String label : constVars.getLabels()) {
        Redwood.log(ConstantsAndVariables.minimaldebug, "\n###Learning for label " + label + " ######");

        String sentout = sentsOutFile == null ? null : sentsOutFile + "_" + label;

        Pair<Counter<E>, Counter<CandidatePhrase>> learnedPatWords4label = iterateExtractApply4Label(label, p0 != null ? p0.get(label) : null,
            p0Set != null ? p0Set.get(label) : null, wordsOutput.get(label), sentout, patternsOutput.get(label),
            ignorePatterns != null ? ignorePatterns.get(label) : null, ignoreWordsAll.get(label), matchedTokensByPatAllLabels.get(label),
            termsAllLabels.get(label), i);

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

      if(constVars.writeMatchedTokensIdsForEachPhrase){
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
    for (Entry<String, Counter<CandidatePhrase>> en : this.constVars.getLearnedWords().entrySet()) {
      System.out.println(en.getKey() + ":\t\t" + en.getValue().keySet() + "\n\n");
    }

    // close all the writers
    for (String label : constVars.getLabels()) {
      wordsOutput.get(label).close();
      patternsOutput.get(label).close();
    }
  }

  void writeMatchedTokensAndSents(String label, Map<String, DataInstance> sents, String suffix, CollectionValuedMap<E, Triple<String, Integer, Integer>> tokensMatchedPat) throws IOException {
    Set<String> allMatchedSents = new HashSet<String>();
    String matchedtokensfilename = constVars.outDir + "/" + constVars.identifier + "/" + label + "/tokensmatchedpatterns" + suffix + ".json";
    JsonObjectBuilder pats = Json.createObjectBuilder();
    for (Entry<E, Collection<Triple<String, Integer, Integer>>> en : tokensMatchedPat.entrySet()) {
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
      for (CoreLabel l : sents.get(sentId).getTokens()) {
        sent.add(l.word());
      }
      senttokens.add(sentId, sent);
    }
    String sentfilename = constVars.outDir + "/" + constVars.identifier + "/sentences" + suffix  + ".json";
    IOUtils.writeStringToFile(senttokens.build().toString(), sentfilename, "utf8");
  }

  public static String matchedTokensByPhraseJsonString(String phrase){
    if(!Data.matchedTokensForEachPhrase.containsKey(phrase))
      return "";
    JsonArrayBuilder arrobj =Json.createArrayBuilder();
    for (Entry<String, List<Integer>> sen : Data.matchedTokensForEachPhrase.get(phrase).entrySet()) {
      JsonObjectBuilder obj = Json.createObjectBuilder();
      JsonArrayBuilder tokens = Json.createArrayBuilder();
      for(Integer i : sen.getValue()){
        tokens.add(i);
      }
      obj.add(sen.getKey(),tokens);
      arrobj.add(obj);
    }
    return arrobj.build().toString();
  }

  public static String matchedTokensByPhraseJsonString(){
    JsonObjectBuilder pats = Json.createObjectBuilder();

    for (Entry<String, Map<String, List<Integer>>> en : Data.matchedTokensForEachPhrase.entrySet()) {

      JsonArrayBuilder arrobj =Json.createArrayBuilder();
      for (Entry<String, List<Integer>> sen : en.getValue().entrySet()) {
        JsonObjectBuilder obj = Json.createObjectBuilder();
        JsonArrayBuilder tokens = Json.createArrayBuilder();
        for(Integer i : sen.getValue()){
          tokens.add(i);
        }
        obj.add(sen.getKey(),tokens);
        arrobj.add(obj);
      }
      pats.add(en.getKey(), arrobj);
    }
    return pats.build().toString();
  }

  private Pair<Counter<E>, Counter<CandidatePhrase>> iterateExtractApply4Label(String label, E p0, Counter<CandidatePhrase> p0Set,
      BufferedWriter wordsOutput, String sentsOutFile, BufferedWriter patternsOut, Set<E> ignorePatterns,
      Set<CandidatePhrase> ignoreWords, CollectionValuedMap<E, Triple<String, Integer, Integer>> matchedTokensByPat,
      TwoDimensionalCounter<String, E> terms, int numIter) throws IOException, ClassNotFoundException {

    if (!learnedPatterns.containsKey(label)) {
      learnedPatterns.put(label, new ClassicCounter<E>());
    }

    if (!learnedPatternsEachIter.containsKey(label)) {
      learnedPatternsEachIter.put(label, new HashMap<Integer, Counter<E>>());
    }


    if (!constVars.getLearnedWords().containsKey(label)) {
      constVars.getLearnedWords().put(label, new ClassicCounter<CandidatePhrase>());
    }

    Counter<CandidatePhrase> identifiedWords = new ClassicCounter<CandidatePhrase>();
    Counter<E> patterns = new ClassicCounter<E>();
    //for (int i = 0; i < numIter; i++) {
      Counter<E> patternThisIter = getPatterns(label, learnedPatterns.get(label).keySet(), p0, p0Set, ignorePatterns);
      patterns.addAll(patternThisIter);
      learnedPatterns.get(label).addAll(patternThisIter);
      learnedPatternsEachIter.get(label).put(numIter, patternThisIter);

      if (sentsOutFile != null)
        sentsOutFile = sentsOutFile + "_" + numIter + "iter.ser";

      Counter<String> scoreForAllWordsThisIteration = new ClassicCounter<String>();

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
              if(sentsf.second().exists())
              IOUtils.writeObjectToFile(sentsf.first(), sentsf.second());
            }

            /*
            if (constVars.batchProcessSents) {
              for (File f : Data.sentsFiles) {
                Redwood.log(Redwood.DBG, "labeling sentences from " + f);
                Map<String, DataInstance> sents = IOUtils.readObjectFromFile(f);
                labelWords(label, sents, identifiedWords.keySet(), sentsOutFile, matchedTokensByPat);
                IOUtils.writeObjectToFile(sents, f);
              }
            } else
              labelWords(label, Data.sents, identifiedWords.keySet(), sentsOutFile, matchedTokensByPat);*/
          } else
            throw new RuntimeException("why is the answer label null?");
          constVars.getLearnedWords().get(label).addAll(identifiedWords);
        }

        if (wordsOutput != null) {
          // if (i > 0)
          // wordsOutput.write("\n");
          // wordsOutput.write("\n#Iteration " + (i + 1) + "\n");
          wordsOutput.write("\n" + Counters.toSortedString(identifiedWords, identifiedWords.size(), "%1$s", "\n"));
          wordsOutput.flush();
        }
      }

    //}
    if (patternsOut != null)
      this.writePatternsToFile(patterns, patternsOut);

    return new Pair<Counter<E>, Counter<CandidatePhrase>>(patterns, identifiedWords);
  }

  void writePatternsToFile(Counter<E> pattern, BufferedWriter outFile) throws IOException {
    for (Entry<E, Double> en : pattern.entrySet())
      outFile.write(en.getKey().toString() + "\t" + en.getValue() + "\n");
  }

  void writeWordsToFile(Counter<CandidatePhrase> words, BufferedWriter outFile) throws IOException {
    for (Entry<CandidatePhrase, Double> en : words.entrySet())
      outFile.write(en.getKey() + "\t" + en.getValue() + "\n");
  }

  Counter<CandidatePhrase> readLearnedWordsFromFile(File file) {
    Counter<CandidatePhrase> words = new ClassicCounter<CandidatePhrase>();
    for (String line : IOUtils.readLines(file)) {
      String[] t = line.split("\t");
      words.setCount(CandidatePhrase.createOrGet(t[0]), Double.parseDouble(t[1]));
    }
    return words;
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

      Map<String, Boolean> lastWordLabeled = new HashMap<String, Boolean>();
      for (String label : constVars.getLabels()) {
        lastWordLabeled.put(label, false);
      }

      for (CoreLabel s : sent.getValue().getTokens()) {
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
        Set<String> labels = new HashSet<String>();
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
      Counter<String> entityTP = new ClassicCounter<String>();
      Counter<String> entityFP = new ClassicCounter<String>();
      Counter<String> entityFN = new ClassicCounter<String>();

      Counter<String> wordTP = new ClassicCounter<String>();
      Counter<String> wordTN = new ClassicCounter<String>();
      Counter<String> wordFP = new ClassicCounter<String>();
      Counter<String> wordFN = new ClassicCounter<String>();

      for (Entry<String, DataInstance> docEn : testSentences.entrySet()) {
        DataInstance doc = docEn.getValue();
        List<CoreLabel> doceval = new ArrayList<CoreLabel>();
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

  public static List<File> getAllFiles(String file) {

    List<File> allFiles = new ArrayList<File>();
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
    Set<String> assumedNeg = new HashSet<String>();
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
      System.err.println("\nGold entity list does not contain words " + assumedNeg + " for label " + label + ". *****Assuming them as negative.******");

    double precision = numcorrect / (double) (numcorrect + numincorrect);
    double recall = numcorrect / (double) (numgoldcorrect);
    return new Pair<Double, Double>(precision, recall);
  }

  public double FScore(double precision, double recall, double beta) {
    double betasq = beta * beta;
    return (1 + betasq) * precision * recall / (betasq * precision + recall);
  }

  public Set<String> getNonBackgroundLabels(CoreLabel l){
    Set<String> labels = new HashSet<String>();
    for(Map.Entry<String, Class<? extends Key<String>>> en: constVars.getAnswerClass().entrySet()){
      if(!l.get(en.getValue()).equals(constVars.backgroundSymbol)){
        labels.add(en.getKey());
      }
    }
    return labels;
  }

  public static Map<String, Set<CandidatePhrase>> readSeedWordsFromJSONString(String str){
    Map<String, Set<CandidatePhrase>> seedWords  = new HashMap<String, Set<CandidatePhrase>>();
    JsonReader jsonReader = Json.createReader(new StringReader(str));
    JsonObject obj = jsonReader.readObject();

    jsonReader.close();
    for (String o : obj.keySet()){
      seedWords.put(o, new HashSet<CandidatePhrase>());
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
    Map<String, Set<CandidatePhrase>> seedWords  = new HashMap<String, Set<CandidatePhrase>>();


    if (seedWordsFiles == null) {
      throw new RuntimeException(
        "Needs both seedWordsFiles and file parameters to run this class!\nseedWordsFiles has format: label1,filewithlistofwords1;label2,filewithlistofwords2;...");
    }
    for (String seedFile : seedWordsFiles.split(";")) {
      String[] t = seedFile.split(",");
      String label = t[0];
      Set<CandidatePhrase> seedWords4Label = new HashSet<CandidatePhrase>();
      if(t.length == 2){
        String seedWordsFile = t[1];
        File f = new File(seedWordsFile);

        for(File fin: ConstantsAndVariables.listFileIncludingItself(seedWordsFile)){
            Redwood.log(Redwood.DBG, "Reading seed words from " + fin + " for label " + label);
            for (String line : IOUtils.readLines(fin)) {
              line = line.trim();
              if (line.isEmpty() || line.startsWith("#")) {
                continue;
              }
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
    Map<String, String> values = new HashMap<String, String>();
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
      e.printStackTrace();
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

  public static Pair processSents(Properties props, Set<String> labels) throws IOException, ExecutionException, InterruptedException, ClassNotFoundException {
    String fileFormat = props.getProperty("fileFormat");
    Map<String, DataInstance> sents = null;
    boolean batchProcessSents = Boolean.parseBoolean(props.getProperty("batchProcessSents", "false"));
    int numMaxSentencesPerBatchFile = Integer.parseInt(props.getProperty("numMaxSentencesPerBatchFile", String.valueOf(Integer.MAX_VALUE)));

    //works only for non-batch processing!
    boolean preserveSentenceSequence = Boolean.parseBoolean(props.getProperty("preserveSentenceSequence","false"));

    if (!batchProcessSents){
      if(preserveSentenceSequence)
        sents = new LinkedHashMap<String, DataInstance>();
      else
        sents = new HashMap<String, DataInstance>();

    }
    else {
      Data.sentsFiles = new ArrayList<File>();
      Data.sentId2File = new ConcurrentHashMap<String, File>();
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
      throw new RuntimeException("PattenrType not specified. Options are SURFACE and DEP");

    PatternFactory.PatternType patternType = PatternFactory.PatternType.valueOf(props.getProperty(Flags.patternType));
    File saveSentencesSerDir = null;
    File tempSaveSentencesDir = null;
    //boolean usingDirForSentsInIndex = true;
    // Read training file
    if (file != null) {
      String saveSentencesSerDirstr = props.getProperty("saveSentencesSerDir");
      if (saveSentencesSerDirstr != null) {
        saveSentencesSerDir = new File(saveSentencesSerDirstr);

        if(saveSentencesSerDir.exists() && !fileFormat.equalsIgnoreCase("ser"))
          IOUtils.deleteDirRecursively(saveSentencesSerDir);

        IOUtils.ensureDir(saveSentencesSerDir);
//        if(!batchProcessSents)
//          IOUtils.writeObjectToFile(sents, saveSentencesSerDirstr + "/sents_all.ser");
      }

      String systemdir = System.getProperty("java.io.tmpdir");
      tempSaveSentencesDir = File.createTempFile("sents", ".tmp", new File(systemdir));
      tempSaveSentencesDir.deleteOnExit();
      tempSaveSentencesDir.delete();
      tempSaveSentencesDir.mkdir();


      int numFilesTillNow = 0;
      if (fileFormat == null || fileFormat.equalsIgnoreCase("text") || fileFormat.equalsIgnoreCase("txt")) {

        Map<String, DataInstance> sentsthis ;
        if(preserveSentenceSequence)
          sentsthis = new LinkedHashMap<String, DataInstance>();
        else
          sentsthis = new HashMap<String, DataInstance>();

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
          for(Map.Entry<String, DataInstance> d: sents.entrySet()){
            for(CoreLabel l : d.getValue().getTokens()){
              for(String label: labels) {
                if(l.containsKey(PatternsAnnotations.LongestMatchedPhraseForEachLabel.class)){
                  CandidatePhrase p = l.get(PatternsAnnotations.LongestMatchedPhraseForEachLabel.class).get(label);
              }}
            }
          }
          String outfilename= (saveSentencesSerDir == null ? tempSaveSentencesDir : saveSentencesSerDir) + "/sents_" + numFilesTillNow;
          Redwood.log("Saving sentences in " + outfilename);
          IOUtils.writeObjectToFile(sents, outfilename);
        }

        //IOUtils.writeObjectToFile(CandidatePhrase.candidatePhraseMap, (saveSentencesSerDir == null? tempSaveSentencesDir: saveSentencesSerDir) + "/candidatePhraseMap.ser");

      } else if (fileFormat.equalsIgnoreCase("ser")) {
        //usingDirForSentsInIndex = false;
        for (File f : GetPatternsFromDataMultiClass.getAllFiles(file)) {
//          if(f.getName().equalsIgnoreCase("candidatePhraseMap.ser")){
//            ConcurrentHashMap<String, CandidatePhrase> candidatPhraseMap = IOUtils.readObjectFromFile(f);
//            CandidatePhrase.setCandidatePhraseMap(candidatPhraseMap);
//
//          }else{
          Redwood.log(Redwood.DBG, "reading from ser file " + f);
          if (!batchProcessSents)
            sents.putAll((Map<String, DataInstance>) IOUtils.readObjectFromFile(f));
          else{
            File newf = new File(tempSaveSentencesDir.getAbsolutePath() + "/" + f.getAbsolutePath().replaceAll(java.util.regex.Pattern.quote("/"), "_"));
            IOUtils.cp(f, newf);
            Data.sentsFiles.add(newf);
          }
         // }
        }
      } else {
        throw new RuntimeException(
          "Cannot identify the file format. Valid values are text (or txt) and ser, where the serialized file is of the type Map<String, DataInstance>.");
      }
    }


    Map<String, DataInstance> evalsents = new HashMap<String, DataInstance>();
    File saveEvalSentencesSerFileFile = null;

    boolean evaluate = Boolean.parseBoolean(props.getProperty("evaluate"));

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
    return new Pair(sents, evalsents);
  }

  /**
   * Execute the system give a properties file or object. Returns the model created
   * @param props
   */
  public static<E extends Pattern> GetPatternsFromDataMultiClass<E> run(Properties props) throws IOException, ClassNotFoundException, IllegalAccessException, InterruptedException, ExecutionException, InstantiationException, NoSuchMethodException, InvocationTargetException, SQLException {
    Map<String, Set<E>> ignorePatterns = new HashMap<String, Set<E>>();
    Map<String, E> p0 = new HashMap<String, E>();
    Map<String, Counter<CandidatePhrase>> p0Set = new HashMap<String, Counter<CandidatePhrase>>();


    Map<String, Set<CandidatePhrase>> seedWords = readSeedWords(props);

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

    //process all the sentences here!
    Pair<Map<String, DataInstance>, Map<String, DataInstance>> sentsPair = processSents(props, seedWords.keySet());

    boolean learn = Boolean.parseBoolean(props.getProperty("learn", "true"));

    boolean labelUsingSeedSets = Boolean.parseBoolean(props.getProperty("labelUsingSeedSets", "true"));

    GetPatternsFromDataMultiClass<E> model = new GetPatternsFromDataMultiClass<E>(props, sentsPair.first(), seedWords, labelUsingSeedSets);

//    model.constVars.usingDirForSentsInIndex = usingDirForSentsInIndex;
//    model.constVars.saveSentencesSerDir = saveSentencesSerDir;

    Execution.fillOptions(model, props);

    // Redwood.log(ConstantsAndVariables.minimaldebug,
    // "Total number of training sentences " + Data.sents.size());

    String sentsOutFile = props.getProperty("sentsOutFile");

    String wordsOutputFile = props.getProperty("wordsOutputFile");

    String patternOutFile = props.getProperty("patternOutFile");

    // If you want to reuse patterns and words learned previously (may be on
    // another dataset etc)
    boolean loadSavedPatternsWordsDir = Boolean.parseBoolean(props.getProperty("loadSavedPatternsWordsDir"));
    boolean labelSentsUsingModel = Boolean.parseBoolean(props.getProperty("labelSentsUsingModel","true"));
    boolean applyPatsUsingModel = Boolean.parseBoolean(props.getProperty("applyPatsUsingModel","true"));
    int numIterationsOfSavedPatternsToLoad = Integer.parseInt(props.getProperty(Flags.numIterationsOfSavedPatternsToLoad,String.valueOf(Integer.MAX_VALUE)));
    //Load already save pattersn and phrases
    if (loadSavedPatternsWordsDir) {
      loadFromSavedPatternsWordsDir(model , props, labelSentsUsingModel, applyPatsUsingModel, numIterationsOfSavedPatternsToLoad);
    }

    if (learn)
      model.iterateExtractApply(p0, p0Set, wordsOutputFile, sentsOutFile, patternOutFile, ignorePatterns);

    if (model.constVars.markedOutputTextFile != null) {
      model.writeLabeledData(model.constVars.markedOutputTextFile);
    }

    if(model.constVars.columnOutputFile != null)
      writeColumnOutput(model.constVars.columnOutputFile, model.constVars.batchProcessSents, model.constVars.getAnswerClass());

    boolean savePatternsWordsDir = Boolean.parseBoolean(props.getProperty("savePatternsWordsDir"));

    if (savePatternsWordsDir) {
      String patternsWordsDir = props.getProperty("patternsWordsDir");
      Redwood.log(Redwood.FORCE,"Saving output in " + patternsWordsDir);
      //save pattern index!
//      if(!model.patsForEachToken.getUseDBForTokenPatterns() && model.constVars.allPatternsDir == null){
//        String allPatsDir = patternsWordsDir+"/allpatterns/";
//        IOUtils.ensureDir(new File(allPatsDir));
//        model.savePatternIndex(allPatsDir);
//        Redwood.log(Redwood.FORCE, "WARNING: SAVING OF THE MODEL IS SET BUT allPatternsDir IS NOT SET. SAVING ALL PATTERNS DIR TO " + allPatsDir+ ". USE THIS AS allPatternsDir WHEN LOADING THE MODEL!");
//      } //else if using DB, already saved when creating patterns;

      for (String label : model.constVars.getLabels()) {
        IOUtils.ensureDir(new File(patternsWordsDir + "/" + label));
        Map<Integer, Counter<E>> pats = model.getLearnedPatternsEachIter(label);
        //Counter<E> patsSur = model.constVars.transformPatternsToSurface(pats);
        IOUtils.writeObjectToFile(pats, patternsWordsDir + "/" + label + "/patternsEachIter.ser");
        BufferedWriter w = new BufferedWriter(new FileWriter(patternsWordsDir + "/" + label + "/phrases.txt"));
        model.writeWordsToFile(model.constVars.getLearnedWords(label), w);
        writeClassesInEnv(model.constVars.env, ConstantsAndVariables.globalEnv, patternsWordsDir + "/env.txt");
        w.close();
      }
    }

    boolean evaluate = Boolean.parseBoolean(props.getProperty("evaluate"));

    if (evaluate) {
      if(model.constVars.goldEntitiesEvalFiles !=null) {

        for (String label : model.constVars.getLabels()) {
          if(model.constVars.goldEntities.containsKey(label)){
            Pair<Double, Double> pr = model.getPrecisionRecall(label, model.constVars.goldEntities.get(label));
            Redwood.log(ConstantsAndVariables.minimaldebug,
              "\nFor label " + label + ": Number of gold entities is " + model.constVars.goldEntities.get(label).size() + ", Precision is " + model.df.format(pr.first() * 100)
                + ", Recall is " + model.df.format(pr.second() * 100) + ", F1 is " + model.df.format(model.FScore(pr.first(), pr.second(), 1.0) * 100)
                + "\n\n");
          }
        }
      }

      //File saveEvalSentencesSerFileFile = sentsPair.second();
      Map<String, DataInstance> evalsents = sentsPair.second();
      //if (saveEvalSentencesSerFileFile != null && saveEvalSentencesSerFileFile.exists()) {
        //if (batchProcessSents)
        //  evalsents = IOUtils.readObjectFromFile(saveEvalSentencesSerFileFile);
        if(evalsents.size() > 0){
          boolean evalPerEntity = Boolean.parseBoolean(props.getProperty("evalPerEntity", "true"));
          model.evaluate(evalsents, evalPerEntity);
        }
     // }

      if (evalsents.size() == 0 && model.constVars.goldEntitiesEvalFiles == null)
        System.err.println("No eval sentences or list of gold entities provided to evaluate! Make sure evalFileWithGoldLabels or goldEntitiesEvalFiles is set, or turn off the evaluate flag");

    }

    if(model.constVars.saveInvertedIndex){
      model.constVars.invertedIndex.saveIndex(model.constVars.invertedIndexDirectory);
    }

    if(model.constVars.storePatsForEachToken.equals(ConstantsAndVariables.PatternForEachTokenWay.LUCENE)){
      model.patsForEachToken.close();
    }
    return model;
  }


  public static<E extends Pattern> Map<E, String> loadFromSavedPatternsWordsDir(GetPatternsFromDataMultiClass<E> model, Properties props, boolean labelSentsUsingModel, boolean applyPatsUsingModel, int numIterationsOfSavedPatternsToLoad) throws IOException, ClassNotFoundException {
    Map<E, String> labelsForPattterns = new HashMap<E, String>();
    String patternsWordsDir = props.getProperty(Flags.patternsWordsDir);
    String sentsOutFile = props.getProperty("sentsOutFile");
    String loadModelForLabels = props.getProperty(Flags.loadModelForLabels);
    List<String> loadModelForLabelsList = null;
    if(loadModelForLabels != null)
      loadModelForLabelsList = Arrays.asList(loadModelForLabels.split("[,;]"));

    for (String label : model.constVars.getLabels()) {

      if(loadModelForLabels != null && !loadModelForLabelsList.contains(label))
        continue;

      assert (new File(patternsWordsDir + "/" + label).exists());


     /* if(!model.constVars.useDBForTokenPatterns){
        assert model.constVars.allPatternsDir != null && new File(model.constVars.allPatternsDir).exists() : "Should save allPatternsFile when saving the model and use that";
        model.patsForEachToken = new PatternsForEachToken(props, IOUtils.readObjectFromFile(model.constVars.allPatternsDir+"/allpatterns.ser"));
        model.constVars.setPatternIndex( IOUtils.readObjectFromFile(model.constVars.allPatternsDir+"/patternshashindex.ser"));
      }else {
        props.setProperty("createTable", "false");
        props.setProperty("deleteExisting", "false");
        model.patsForEachToken = new PatternsForEachToken(props);
        model.constVars.setPatternIndex(model.patsForEachToken.readPatternIndexFromDB());
      }
*/
      readClassesInEnv(patternsWordsDir + "/env.txt", model.constVars.env, ConstantsAndVariables.globalEnv);

      File patf = new File(patternsWordsDir + "/" + label + "/patternsEachIter.ser");
      if (patf.exists()) {
        Map<Integer, Counter<E>> patterns = IOUtils.readObjectFromFile(patf);
        if(numIterationsOfSavedPatternsToLoad < Integer.MAX_VALUE){
          Set<Integer> toremove = new HashSet<Integer>();
          for(Integer i : patterns.keySet()){
            if(i >= numIterationsOfSavedPatternsToLoad){
              System.out.println("Removing patterns from iteration " + i);
              toremove.add(i);
            }
          }
          for(Integer i: toremove)
            patterns.remove(i);
        }
        //model.constVars.getPatternIndex().finishCommit();
        Counter<E> pats = Counters.flatten(patterns);
        for(E p : pats.keySet()){
          labelsForPattterns.put(p, label);
        }
        model.setLearnedPatterns(pats, label);
        model.setLearnedPatternsEachIter(patterns, label);
        Redwood.log(Redwood.DBG, "Loaded " + model.getLearnedPatterns().get(label).size() + " patterns from " + patf);
      }


      File wordf = new File(patternsWordsDir + "/" + label + "/phrases.txt");
      if (wordf.exists()) {
        Counter<CandidatePhrase> words = model.readLearnedWordsFromFile(wordf);
        model.constVars.setLearnedWords(words, label);
        Redwood.log(Redwood.DBG, "Loaded " + words.size() + " phrases from " + wordf);
      }
      CollectionValuedMap<E, Triple<String, Integer, Integer>> matchedTokensByPat = new CollectionValuedMap<>();

      Iterator<Pair<Map<String, DataInstance>, File>> sentsIter = new ConstantsAndVariables.DataSentsIterator(model.constVars.batchProcessSents);
      TwoDimensionalCounter<Pair<String, String>, E> wordsandLemmaPatExtracted = new TwoDimensionalCounter<Pair<String, String>, E>();

      while(sentsIter.hasNext()){
        Pair<Map<String, DataInstance>, File> sents = sentsIter.next();
        if (model.constVars.restrictToMatched || applyPatsUsingModel) {
          Redwood.log(Redwood.DBG,"Applying patterns to " + sents.first().size() + " sentences");
          model.constVars.invertedIndex.add(sents.first(), true);
          model.constVars.invertedIndex.add(sents.first(), true);
          model.scorePhrases.applyPats(model.getLearnedPatterns(label), label, wordsandLemmaPatExtracted, matchedTokensByPat);
        }
        if(labelSentsUsingModel){
            Redwood.log(Redwood.DBG, "labeling sentences from " + sents.second() + " with the already learned words");
            assert sents.first() != null : "Why are sents null";
            model.labelWords(label, sents.first(), model.constVars.getLearnedWords(label).keySet(), sentsOutFile, matchedTokensByPat);
          if(sents.second().exists())
            IOUtils.writeObjectToFile(sents, sents.second());
      }
      }


//      if(labelSentsUsingModel){
//        if (model.constVars.batchProcessSents) {
//          for (File f : Data.sentsFiles) {
//            Redwood.log(Redwood.DBG, "labeling sentences from " + f + " with the already learned words");
//            Map<String, DataInstance> sentsf = IOUtils.readObjectFromFile(f);
//            assert sentsf != null : "Why are sents null";
//            model.labelWords(label, sentsf, model.getLearnedWords(label).keySet(), sentsOutFile, matchedTokensByPat);
//            IOUtils.writeObjectToFile(sentsf, f);
//          }
//        } else
//          model.labelWords(label, Data.sents, model.getLearnedWords(label).keySet(), sentsOutFile, matchedTokensByPat);
//      }
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
    Interval interval = new Interval(d1.getTime(), d2.getTime());
    Period period = interval.toPeriod();
    return period.getDays() + " days, " + period.getHours()+" hours, " + period.getMinutes()  +" minutes, " +period.getSeconds()+" seconds";
    }catch(java.lang.IllegalArgumentException e){
      e.printStackTrace();
    }
    return "";
  }


  public static void main(String[] args) {
    try {
      Properties props = StringUtils.argsToPropertiesWithResolve(args);
      GetPatternsFromDataMultiClass.<SurfacePattern>run(props);
    } catch (OutOfMemoryError e) {
      System.out.println("Out of memory! Either change the memory alloted by running as java -mx20g ... for example if you want to allot 20G. Or consider using batchProcessSents and numMaxSentencesPerBatchFile flags");
      e.printStackTrace();
    } catch (Exception e) {
      e.printStackTrace();
    }
  } // end main()

}
//  public void testProtobufSerialization() throws Exception {
//    // Check the regexner is integrated with the StanfordCoreNLP
//    Properties props = new Properties();
//    props.setProperty("annotators", "tokenize,ssplit,pos,lemma,parse");
//    String text = "Barack Obama, a Yale professor, is president.";
//    Annotation document = new Annotation(text);
//    StanfordCoreNLP pipeline = new StanfordCoreNLP(props);
//    pipeline.annotate(document);
//    File tempfile = File.createTempFile("temp","gz");
//    tempfile.deleteOnExit();
//    ProtobufAnnotationSerializer serializer = new ProtobufAnnotationSerializer(true);
//    GZIPOutputStream gz = new GZIPOutputStream(new FileOutputStream(tempfile));
//    gz.write(serializer.toProto(document).toByteArray());
//    gz.finish();
//    gz.close();
//
//    //IOUtils.writeObjectToFile(document.get(CoreAnnotations.SentencesAnnotation.class), tempfile);
//    Annotation doc2 = serializer.read(new BufferedInputStream(new GZIPInputStream(new FileInputStream((tempfile))))).first();
//
//  }
