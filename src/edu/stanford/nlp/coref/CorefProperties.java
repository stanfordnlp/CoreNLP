package edu.stanford.nlp.coref;

import java.io.File;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;

import edu.stanford.nlp.coref.data.Dictionaries.MentionType;
import edu.stanford.nlp.coref.sieve.Sieve.ClassifierType;
import edu.stanford.nlp.util.Generics;
import edu.stanford.nlp.util.PropertiesUtils;


public class CorefProperties {

  public enum CorefAlgorithmType {CLUSTERING, STATISTICAL, NEURAL, HYBRID}
  public enum CorefInputType { RAW, CONLL, ACE, MUC }
  public enum Dataset {TRAIN, DEV, TEST}
  public enum MentionDetectionType { RULE, HYBRID, DEPENDENCY }

  // general
  public static final String LANG_PROP = "coref.language";
  public static final String SIEVES_PROP = "coref.sieves";
  public static final String ALLOW_REPARSING_PROP = "coref.allowReparsing";
  public static final String SCORE_PROP = "coref.doScore";
  public static final String PARSER_PROP = "coref.useConstituencyTree";
  public static final String THREADS_PROP = "coref.threadCount";
  public static final String INPUT_TYPE_PROP = "coref.input.type";
  public static final String POSTPROCESSING_PROP = "coref.postprocessing";
  public static final String MD_TYPE_PROP = "mention.type";
  public static final String USE_SINGLETON_PREDICTOR_PROP = "coref.useSingletonPredictor";
  public static final String SEED_PROP = "coref.seed";
  public static final String CONLL_AUTO_PROP = "coref.conll.auto";
  public static final String MD_TRAIN_PROP = "mention.isTraining";    // train MD classifier
  public static final String USE_SEMANTICS_PROP = "coref.useSemantics";    // load semantics if true
  public static final String CURRENT_SIEVE_FOR_TRAIN_PROP = "coref.currentSieveForTrain";
  public static final String STORE_TRAINDATA_PROP = "coref.storeTrainData";
  public static final String USE_GOLD_NE_PROP = "coref.useGoldNE";
  public static final String USE_GOLD_PARSES_PROP = "coref.useGoldParse";
  public static final String USE_GOLD_POS_PROP = "coref.useGoldPOS";
  private static final String REMOVE_NESTED = "removeNested";
  private static final String ADD_MISSING_ANNOTATIONS = "coref.addMissingAnnotations";

  // logging & system check & analysis
  public static final String DEBUG_PROP = "coref.debug";
  public static final String LOG_PROP = "coref.logFile";
  public static final String TIMER_PROP = "coref.checkTime";
  public static final String MEMORY_PROP = "coref.checkMemory";
  public static final String PRINT_MDLOG_PROP = "mention.print.log";
  public static final String CALCULATE_IMPORTANCE_PROP = "coref.calculateFeatureImportance";
  public static final String DO_ANALYSIS_PROP = "coref.analysis.doAnalysis";
  public static final String ANALYSIS_SKIP_MTYPE_PROP = "coref.analysis.skip.mType";
  public static final String ANALYSIS_SKIP_ATYPE_PROP = "coref.analysis.skip.aType";

  // data & io
  public static final String STATES_PROP = "coref.states";
  public static final String DEMONYM_PROP = "coref.demonym";
  public static final String ANIMATE_PROP = "coref.animate";
  public static final String INANIMATE_PROP = "coref.inanimate";
  public static final String MALE_PROP = "coref.male";
  public static final String NEUTRAL_PROP = "coref.neutral";
  public static final String FEMALE_PROP = "coref.female";
  public static final String PLURAL_PROP = "coref.plural";
  public static final String SINGULAR_PROP = "coref.singular";
  public static final String GENDER_NUMBER_PROP = "coref.big.gender.number";
  public static final String COUNTRIES_PROP = "coref.countries";
  public static final String STATES_PROVINCES_PROP = "coref.states.provinces";
  public static final String DICT_LIST_PROP = "coref.dictlist";
  public static final String DICT_PMI_PROP = "coref.dictpmi";
  public static final String SIGNATURES_PROP = "coref.signatures";
  public static final String LOAD_WORD_EMBEDDING_PROP = "coref.loadWordEmbedding";
  public static final String WORD2VEC_PROP = "coref.path.word2vec";
  public static final String WORD2VEC_SERIALIZED_PROP = "coref.path.word2vecSerialized";

  public static final String PATH_SCORER_PROP = "coref.scorer";

  public static final String PATH_INPUT_PROP = "coref.path.input";
  public static final String PATH_OUTPUT_PROP = "coref.path.output";

  public static final String PATH_TRAIN_PROP = "coref.path.traindata";
  public static final String PATH_EVAL_PROP = "coref.path.evaldata";

  public static final String PATH_SERIALIZED_PROP = "coref.path.serialized";

  // models
  public static final String PATH_SINGLETON_PREDICTOR_PROP = "coref.path.singletonPredictor";
  public static final String PATH_MODEL_PROP = "coref.SIEVENAME.model";
  public static final String MENTION_DETECTION_MODEL_PROP = "mention.model";

  // sieve option
  public static final String CLASSIFIER_TYPE_PROP = "coref.SIEVENAME.classifierType";
  public static final String NUM_TREE_PROP = "coref.SIEVENAME.numTrees";
  public static final String NUM_FEATURES_PROP = "coref.SIEVENAME.numFeatures";
  public static final String TREE_DEPTH_PROP = "coref.SIEVENAME.treeDepth";
  public static final String MAX_SENT_DIST_PROP = "coref.SIEVENAME.maxSentDist";
  public static final String MTYPE_PROP = "coref.SIEVENAME.mType";
  public static final String ATYPE_PROP = "coref.SIEVENAME.aType";
  public static final String DOWNSAMPLE_RATE_PROP = "coref.SIEVENAME.downsamplingRate";
  public static final String THRES_FEATURECOUNT_PROP = "coref.SIEVENAME.thresFeatureCount";
  public static final String FEATURE_SELECTION_PROP = "coref.SIEVENAME.featureSelection";
  public static final String THRES_MERGE_PROP = "coref.SIEVENAME.merge.thres";
  public static final String THRES_FEATURE_SELECTION_PROP = "coref.SIEVENAME.pmi.thres";
  public static final String DEFAULT_PRONOUN_AGREEMENT_PROP = "coref.defaultPronounAgreement";

  // features
  public static final String USE_BASIC_FEATURES_PROP = "coref.SIEVENAME.useBasicFeatures";
  public static final String COMBINE_OBJECTROLE_PROP = "coref.SIEVENAME.combineObjectRole";
  public static final String USE_MD_FEATURES_PROP = "coref.SIEVENAME.useMentionDetectionFeatures";
  public static final String USE_DCOREFRULE_FEATURES_PROP = "coref.SIEVENAME.useDcorefRuleFeatures";
  public static final String USE_POS_FEATURES_PROP = "coref.SIEVENAME.usePOSFeatures";
  public static final String USE_LEXICAL_FEATURES_PROP = "coref.SIEVENAME.useLexicalFeatures";
  public static final String USE_WORD_EMBEDDING_FEATURES_PROP = "coref.SIEVENAME.useWordEmbeddingFeatures";

  public static final Locale LANGUAGE_DEFAULT = Locale.ENGLISH;
  public static final int MONITOR_DIST_CMD_FINISHED_WAIT_MILLIS = 60000;

  /** if true, use truecase annotator */
  public static final boolean USE_TRUECASE = false;

  /** if true, remove appositives, predicate nominatives in post processing */
  public static final boolean REMOVE_APPOSITION_PREDICATENOMINATIVES = true;

  /** if true, remove singletons in post processing */
  public static final boolean REMOVE_SINGLETONS = true;

  /** property for conll output path **/
  public static final String OUTPUT_PATH_PROP = "coref.conllOutputPath";

  // current list of dcoref sieves
  private static final Set<String> dcorefSieveNames = new HashSet<>(Arrays.asList("MarkRole", "DiscourseMatch",
          "ExactStringMatch", "RelaxedExactStringMatch", "PreciseConstructs", "StrictHeadMatch1",
          "StrictHeadMatch2", "StrictHeadMatch3", "StrictHeadMatch4", "RelaxedHeadMatch", "PronounMatch", "SpeakerMatch",
          "ChineseHeadMatch"));

  // return what coref algorithm the user wants to use
  public static CorefAlgorithmType algorithm(Properties props) {
    String type = PropertiesUtils.getString(props, "coref.algorithm",
            getLanguage(props) == Locale.ENGLISH ? "statistical" : "neural");
    return CorefAlgorithmType.valueOf(type.toUpperCase());
  }

  public static boolean conll(Properties props) {
    return PropertiesUtils.getBool(props, "coref.conll", false);
  }


  public static boolean doScore(Properties props) {
    return PropertiesUtils.getBool(props, SCORE_PROP, false);
  }
  public static boolean checkTime(Properties props) {
    return PropertiesUtils.getBool(props, TIMER_PROP, false);
  }
  public static boolean checkMemory(Properties props) {
    return PropertiesUtils.getBool(props, MEMORY_PROP, false);
  }

  public static String conllOutputPath(Properties props) {
    return props.getProperty(OUTPUT_PATH_PROP);
  }

  // renaming of this property, will delete the other one soon
  public static boolean useConstituencyParse(Properties props) {
    return PropertiesUtils.getBool(props, "coref.useConstituencyParse",
            algorithm(props) != CorefAlgorithmType.STATISTICAL || conll(props));
  }

  public static boolean useConstituencyTree(Properties props) {
    return PropertiesUtils.getBool(props, PARSER_PROP, false);
  }

  /** Input data for CorefDocMaker. It is traindata for training, or testdata for evaluation */
  public static String getPathInput(Properties props) {
    return PropertiesUtils.getString(props, PATH_INPUT_PROP, null);
  }
  public static String getPathOutput(Properties props) {
    return PropertiesUtils.getString(props, PATH_OUTPUT_PROP, "/home/heeyoung/log-coref/conlloutput/");
  }
  public static String getPathTrainData(Properties props) {
    return PropertiesUtils.getString(props, PATH_TRAIN_PROP, "/scr/nlp/data/conll-2012/v4/data/train/data/english/annotations/");
  }
  public static String getPathEvalData(Properties props) {
    return PropertiesUtils.getString(props, PATH_EVAL_PROP, "/scr/nlp/data/conll-2012/v9/data/test/data/english/annotations");
  }
  public static int getThreadCounts(Properties props) {
    return PropertiesUtils.getInt(props, THREADS_PROP, Runtime.getRuntime().availableProcessors());
  }
  public static String getPathScorer(Properties props) {
    return PropertiesUtils.getString(props, PATH_SCORER_PROP, "/scr/nlp/data/conll-2012/scorer/v8.01/scorer.pl");
  }
  public static CorefInputType getInputType(Properties props) {
    String inputType = PropertiesUtils.getString(props, INPUT_TYPE_PROP, "raw");
    return CorefInputType.valueOf(inputType.toUpperCase());
  }
  public static boolean printMDLog(Properties props) {
    return PropertiesUtils.getBool(props, PRINT_MDLOG_PROP, false);
  }
  public static boolean doPostProcessing(Properties props) {
    return PropertiesUtils.getBool(props, POSTPROCESSING_PROP, false);
  }

  /** if true, use conll auto files, else use conll gold files */
  public static boolean useCoNLLAuto(Properties props) {
    return PropertiesUtils.getBool(props, CONLL_AUTO_PROP, true);
  }
  public static MentionDetectionType getMDType(Properties props) {
    String defaultMD;
    if (getLanguage(props).equals(Locale.ENGLISH)) {
      // defaultMD for English should be RULE since this is highest performing score for scoref
      defaultMD = "RULE";
    } else if (getLanguage(props).equals(Locale.CHINESE)) {
      // defaultMD for Chinese should be RULE for now
      defaultMD = "RULE";
    } else {
      // general default is "RULE" for now
      defaultMD = "RULE";
    }
    String type = PropertiesUtils.getString(props, MD_TYPE_PROP, defaultMD);
    if(type.equalsIgnoreCase("dep")) type = "DEPENDENCY";
    return MentionDetectionType.valueOf(type.toUpperCase());
  }
  public static boolean useSingletonPredictor(Properties props) {
    return PropertiesUtils.getBool(props, USE_SINGLETON_PREDICTOR_PROP, false);
  }
  public static String getPathSingletonPredictor(Properties props) {
    return PropertiesUtils.getString(props, PATH_SINGLETON_PREDICTOR_PROP, "edu/stanford/nlp/models/dcoref/singleton.predictor.ser");
  }
  public static String getPathModel(Properties props, String sievename) {
    return props.getProperty(PATH_SERIALIZED_PROP) + File.separator +
        props.getProperty(PATH_MODEL_PROP.replace("SIEVENAME", sievename), "MISSING_MODEL_FOR_"+sievename);
  }
  public static boolean debug(Properties props) {
    return PropertiesUtils.getBool(props, DEBUG_PROP, false);
  }

  public static ClassifierType getClassifierType(Properties props, String sievename) {
    if(dcorefSieveNames.contains(sievename)) return ClassifierType.RULE;
    if(sievename.toLowerCase().endsWith("-rf")) return ClassifierType.RF;
    if(sievename.toLowerCase().endsWith("-oracle")) return ClassifierType.ORACLE;
    String classifierType = PropertiesUtils.getString(props, CLASSIFIER_TYPE_PROP.replace("SIEVENAME", sievename), null);
    return ClassifierType.valueOf(classifierType);
  }
  public static double getMergeThreshold(Properties props, String sievename) {
    String key = THRES_MERGE_PROP.replace("SIEVENAME", sievename);
    return PropertiesUtils.getDouble(props, key, 0.3);
  }
  public static void setMergeThreshold(Properties props, String sievename, double value) {
    String key = THRES_MERGE_PROP.replace("SIEVENAME", sievename);
    props.setProperty(key, String.valueOf(value));
  }

  public static int getNumTrees(Properties props, String sievename) {
    return PropertiesUtils.getInt(props, NUM_TREE_PROP.replace("SIEVENAME", sievename), 100);
  }
  public static int getSeed(Properties props) {
    return PropertiesUtils.getInt(props, SEED_PROP, 1);
  }
  public static int getNumFeatures(Properties props, String sievename) {
    return PropertiesUtils.getInt(props, NUM_FEATURES_PROP.replace("SIEVENAME", sievename), 30);
  }
  public static int getTreeDepth(Properties props, String sievename) {
    return PropertiesUtils.getInt(props, TREE_DEPTH_PROP.replace("SIEVENAME", sievename), 0);
  }
  public static boolean calculateFeatureImportance(Properties props) {
    return PropertiesUtils.getBool(props, CALCULATE_IMPORTANCE_PROP, false);
  }

  public static int getMaxSentDistForSieve(Properties props, String sievename) {
    return PropertiesUtils.getInt(props, MAX_SENT_DIST_PROP.replace("SIEVENAME", sievename), 1000);
  }

  public static String getMentionDetectionModel(Properties props) {
    return PropertiesUtils.getString(props, MENTION_DETECTION_MODEL_PROP,
            useConstituencyParse(props) ? "edu/stanford/nlp/models/coref/md-model.ser" :
                    "edu/stanford/nlp/models/coref/md-model-dep.ser.gz");
  }

  public static Set<MentionType> getMentionType(Properties props, String sievename) {
    return getMentionTypes(props, MTYPE_PROP.replace("SIEVENAME", sievename));
  }
  public static Set<MentionType> getAntecedentType(Properties props, String sievename) {
    return getMentionTypes(props, ATYPE_PROP.replace("SIEVENAME", sievename));
  }

  private static Set<MentionType> getMentionTypes(Properties props, String propKey) {
    if(!props.containsKey(propKey) || props.getProperty(propKey).equalsIgnoreCase("all")){
      return new HashSet<>(Arrays.asList(MentionType.values()));
    }

    Set<MentionType> types = new HashSet<>();
    for(String type : props.getProperty(propKey).trim().split(",\\s*")) {
      if(type.toLowerCase().matches("i|you|we|they|it|she|he")) type = "PRONOMINAL";
      types.add(MentionType.valueOf(type));
    }
    return types;
  }
  public static double getDownsamplingRate(Properties props, String sievename) {
    return PropertiesUtils.getDouble(props, DOWNSAMPLE_RATE_PROP.replace("SIEVENAME", sievename), 1);
  }
  public static int getFeatureCountThreshold(Properties props, String sievename) {
    return PropertiesUtils.getInt(props, THRES_FEATURECOUNT_PROP.replace("SIEVENAME", sievename), 20);
  }
  public static boolean useBasicFeatures(Properties props, String sievename) {
    return PropertiesUtils.getBool(props, USE_BASIC_FEATURES_PROP.replace("SIEVENAME", sievename), true);
  }
  public static boolean combineObjectRoles(Properties props, String sievename) {
    return PropertiesUtils.getBool(props, COMBINE_OBJECTROLE_PROP.replace("SIEVENAME", sievename), true);
  }
  public static boolean useMentionDetectionFeatures(Properties props, String sievename) {
    return PropertiesUtils.getBool(props, USE_MD_FEATURES_PROP.replace("SIEVENAME", sievename), true);
  }
  public static boolean useDcorefRules(Properties props, String sievename) {
    return PropertiesUtils.getBool(props, USE_DCOREFRULE_FEATURES_PROP.replace("SIEVENAME", sievename), true);
  }
  public static boolean usePOSFeatures(Properties props, String sievename) {
    return PropertiesUtils.getBool(props, USE_POS_FEATURES_PROP.replace("SIEVENAME", sievename), true);
  }
  public static boolean useLexicalFeatures(Properties props, String sievename) {
    return PropertiesUtils.getBool(props, USE_LEXICAL_FEATURES_PROP.replace("SIEVENAME", sievename), true);
  }
  public static boolean useWordEmbedding(Properties props, String sievename) {
    return PropertiesUtils.getBool(props, USE_WORD_EMBEDDING_FEATURES_PROP.replace("SIEVENAME", sievename), true);
  }

  private static Set<String> getMentionTypeStr(Properties props, String sievename, String whichMention) {
    Set<String> strs = Generics.newHashSet();
    String propKey = whichMention;
    if (!props.containsKey(propKey)) {
      String prefix = "coref." + sievename + ".";
      propKey = prefix + propKey;
    }
    if(props.containsKey(propKey)) strs.addAll(Arrays.asList(props.getProperty(propKey).split(",")));
    return strs;
  }
  public static Set<String> getMentionTypeStr(Properties props, String sievename) {
    return getMentionTypeStr(props, sievename, "mType");
  }
  public static Set<String> getAntecedentTypeStr(Properties props, String sievename) {
    return getMentionTypeStr(props, sievename, "aType");
  }
  public static String getSieves(Properties props) {
    return PropertiesUtils.getString(props, SIEVES_PROP, "SpeakerMatch,PreciseConstructs,pp-rf,cc-rf,pc-rf,ll-rf,pr-rf");
  }
  public static String getPathSerialized(Properties props) {
    return props.getProperty(PATH_SERIALIZED_PROP);
  }
  public static boolean doPMIFeatureSelection(Properties props, String sievename) {
    return PropertiesUtils.getString(props, FEATURE_SELECTION_PROP.replace("SIEVENAME", sievename), "pmi").equalsIgnoreCase("pmi");
  }
  public static double getPMIThres(Properties props, String sievename) {
    return PropertiesUtils.getDouble(props, THRES_FEATURE_SELECTION_PROP.replace("SIEVENAME", sievename), 0.0001);
  }
  public static boolean doAnalysis(Properties props) {
    return PropertiesUtils.getBool(props, DO_ANALYSIS_PROP, false);
  }
  public static String getSkipMentionType(Properties props) {
    return PropertiesUtils.getString(props, ANALYSIS_SKIP_MTYPE_PROP, null);
  }
  public static String getSkipAntecedentType(Properties props) {
    return PropertiesUtils.getString(props, ANALYSIS_SKIP_ATYPE_PROP, null);
  }
  public static boolean useSemantics(Properties props) {
    return PropertiesUtils.getBool(props, USE_SEMANTICS_PROP, true);
  }
  public static String getPathSerializedWordVectors(Properties props) {
    return PropertiesUtils.getString(props, WORD2VEC_SERIALIZED_PROP, "/scr/nlp/data/coref/wordvectors/en/vector.ser.gz");
  }
  public static String getCurrentSieveForTrain(Properties props) {
    return PropertiesUtils.getString(props, CURRENT_SIEVE_FOR_TRAIN_PROP, null);
  }
//  public static String getCurrentSieve(Properties props) {
//    return PropertiesUtils.getString(props, CURRENT_SIEVE_PROP, null);
//  }
  public static boolean loadWordEmbedding(Properties props) {
    return PropertiesUtils.getBool(props, LOAD_WORD_EMBEDDING_PROP, true);
  }
  public static String getPathWord2Vec(Properties props) {
    return PropertiesUtils.getString(props, WORD2VEC_PROP, null);
  }

  public static String getGenderNumber(Properties props) {
    return PropertiesUtils.getString(props, GENDER_NUMBER_PROP, "edu/stanford/nlp/models/dcoref/gender.data.gz");
  }

  public static boolean storeTrainData(Properties props) {
    return PropertiesUtils.getBool(props, STORE_TRAINDATA_PROP, false);
  }

  public static boolean allowReparsing(Properties props) {
    return PropertiesUtils.getBool(props, ALLOW_REPARSING_PROP, true);
  }

  public static boolean useGoldNE(Properties props) {
    return PropertiesUtils.getBool(props, USE_GOLD_NE_PROP, true);
  }
  public static boolean useGoldParse(Properties props) {
    return PropertiesUtils.getBool(props, USE_GOLD_PARSES_PROP, true);
  }
  public static boolean useGoldPOS(Properties props) {
    return PropertiesUtils.getBool(props, USE_GOLD_POS_PROP, true);
  }
  public static boolean isMentionDetectionTraining(Properties props) {
    return PropertiesUtils.getBool(props, CorefProperties.MD_TRAIN_PROP, false);
  }
  public static void setMentionDetectionTraining(Properties props, boolean val) {
    props.put(CorefProperties.MD_TRAIN_PROP, val);
  }
  public static void setRemoveNestedMentions(Properties props,boolean bool){
    props.setProperty(CorefProperties.REMOVE_NESTED, String.valueOf(bool));
  }
  public static boolean removeNestedMentions(Properties props){
    return PropertiesUtils.getBool(props, CorefProperties.REMOVE_NESTED, true);
  }

  public static boolean useDefaultPronounAgreement(Properties props){
    return PropertiesUtils.getBool(props, CorefProperties.DEFAULT_PRONOUN_AGREEMENT_PROP,false);
  }

  public static boolean addMissingAnnotations(Properties props) {
    return PropertiesUtils.getBool(props, ADD_MISSING_ANNOTATIONS, false);
  }

  // heuristic mention filtering
  public static int maxMentionDistance(Properties props) {
    return PropertiesUtils.getInt(props, "coref.maxMentionDistance",
            conll(props) ? Integer.MAX_VALUE : 50);
  }

  public static int maxMentionDistanceWithStringMatch(Properties props) {
    return PropertiesUtils.getInt(props, "coref.maxMentionDistanceWithStringMatch", 500);
  }

  // type of algorithm for mention detection
  public static MentionDetectionType mdType(Properties props) {
    String type = PropertiesUtils.getString(props, "coref.md.type",
            useConstituencyParse(props) ? "RULE" : "dep");
    if (type.equalsIgnoreCase("dep")) {
      type = "DEPENDENCY";
    }
    return MentionDetectionType.valueOf(type.toUpperCase());
  }

  // use a more liberal policy for Chinese mention detection
  public static boolean liberalChineseMD(Properties props) {
    return PropertiesUtils.getBool(props, "coref.md.liberalChineseMD", true);
  }

  public static void setInput(Properties props, Dataset d) {
    props.setProperty("coref.inputPath", d == Dataset.TRAIN ? getTrainDataPath(props) :
            (d == Dataset.DEV ? getDevDataPath(props) : getTestDataPath(props)));
  }

  public static String getDataPath(Properties props) {
    return props.getProperty("coref.data", "/scr/nlp/data/conll-2012/");
  }

  public static String getTrainDataPath(Properties props) {
    return props.getProperty("coref.trainData",
            getDataPath(props) + "v4/data/train/data/" + getLanguageStr(props) + "/annotations/");
  }

  public static String getDevDataPath(Properties props) {
    return props.getProperty("coref.devData",
            getDataPath(props) + "v4/data/dev/data/" + getLanguageStr(props) + "/annotations/");
  }

  public static String getTestDataPath(Properties props) {
    return props.getProperty("coref.testData",
            getDataPath(props) + "v9/data/test/data/" + getLanguageStr(props) + "/annotations");
  }

  public static String getInputPath(Properties props) {
    String input = props.getProperty("coref.inputPath",
            props.containsKey("coref.data") ? getTestDataPath(props) : null);
    return input;
  }

  public static Locale getLanguage(Properties props) {
    String lang = PropertiesUtils.getString(props, "coref.language", "en");
    if (lang.equalsIgnoreCase("en") || lang.equalsIgnoreCase("english")) {
      return Locale.ENGLISH;
    } else if(lang.equalsIgnoreCase("zh") || lang.equalsIgnoreCase("chinese")) {
      return Locale.CHINESE;
    } else {
      throw new IllegalArgumentException("unsupported language");
    }
  }

  private static String getLanguageStr(Properties props) {
    return getLanguage(props).getDisplayName().toLowerCase();
  }

  public static String getScorerPath(Properties props) {
    return props.getProperty("coref.scorer");
  }
}
