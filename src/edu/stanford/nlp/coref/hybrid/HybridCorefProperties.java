package edu.stanford.nlp.coref.hybrid;

import java.io.File;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;

import edu.stanford.nlp.coref.hybrid.sieve.Sieve.ClassifierType;
import edu.stanford.nlp.coref.data.Dictionaries.MentionType;
import edu.stanford.nlp.util.Generics;
import edu.stanford.nlp.util.PropertiesUtils;

/**
 * Properties for the hybrid coref system.
 *
 * @author Heeyoung Lee
 * @author Kevin Clark
 */
public class HybridCorefProperties {

  // public enum CorefInputType { RAW, CONLL, ACE, MUC }

  // general
  public static final String LANG_PROP = "coref.language";
  private static final String SIEVES_PROP = "coref.sieves";
  private static final String SCORE_PROP = "coref.doScore";
  private static final String THREADS_PROP = "coref.threadCount";
  private static final String POSTPROCESSING_PROP = "coref.postprocessing";
  private static final String SEED_PROP = "coref.seed";
  private static final String CONLL_AUTO_PROP = "coref.conll.auto";
  private static final String USE_SEMANTICS_PROP = "coref.useSemantics";    // load semantics if true
  public static final String CURRENT_SIEVE_FOR_TRAIN_PROP = "coref.currentSieveForTrain";
  private static final String STORE_TRAINDATA_PROP = "coref.storeTrainData";
  private static final String ADD_MISSING_ANNOTATIONS = "coref.addMissingAnnotations";

  // logging & system check & analysis
  private static final String DEBUG_PROP = "coref.debug";
  public static final String LOG_PROP = "coref.logFile";
  private static final String TIMER_PROP = "coref.checkTime";
  private static final String MEMORY_PROP = "coref.checkMemory";
  private static final String PRINT_MDLOG_PROP = "coref.print.md.log";
  private static final String CALCULATE_IMPORTANCE_PROP = "coref.calculateFeatureImportance";
  private static final String DO_ANALYSIS_PROP = "coref.analysis.doAnalysis";
  private static final String ANALYSIS_SKIP_MTYPE_PROP = "coref.analysis.skip.mType";
  private static final String ANALYSIS_SKIP_ATYPE_PROP = "coref.analysis.skip.aType";

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
  private static final String WORD2VEC_PROP = "coref.path.word2vec";
  private static final String WORD2VEC_SERIALIZED_PROP = "coref.path.word2vecSerialized";

  private static final String PATH_SERIALIZED_PROP = "coref.path.serialized";

  // models
  private static final String PATH_MODEL_PROP = "coref.SIEVENAME.model";

  // sieve option
  private static final String CLASSIFIER_TYPE_PROP = "coref.SIEVENAME.classifierType";
  private static final String NUM_TREE_PROP = "coref.SIEVENAME.numTrees";
  private static final String NUM_FEATURES_PROP = "coref.SIEVENAME.numFeatures";
  private static final String TREE_DEPTH_PROP = "coref.SIEVENAME.treeDepth";
  private static final String MAX_SENT_DIST_PROP = "coref.SIEVENAME.maxSentDist";
  private static final String MTYPE_PROP = "coref.SIEVENAME.mType";
  private static final String ATYPE_PROP = "coref.SIEVENAME.aType";
  private static final String DOWNSAMPLE_RATE_PROP = "coref.SIEVENAME.downsamplingRate";
  private static final String THRES_FEATURECOUNT_PROP = "coref.SIEVENAME.thresFeatureCount";
  private static final String FEATURE_SELECTION_PROP = "coref.SIEVENAME.featureSelection";
  private static final String THRES_MERGE_PROP = "coref.SIEVENAME.merge.thres";
  private static final String THRES_FEATURE_SELECTION_PROP = "coref.SIEVENAME.pmi.thres";
  private static final String DEFAULT_PRONOUN_AGREEMENT_PROP = "coref.defaultPronounAgreement";

  // features
  private static final String USE_BASIC_FEATURES_PROP = "coref.SIEVENAME.useBasicFeatures";
  private static final String COMBINE_OBJECTROLE_PROP = "coref.SIEVENAME.combineObjectRole";
  private static final String USE_MD_FEATURES_PROP = "coref.SIEVENAME.useMentionDetectionFeatures";
  private static final String USE_DCOREFRULE_FEATURES_PROP = "coref.SIEVENAME.useDcorefRuleFeatures";
  private static final String USE_POS_FEATURES_PROP = "coref.SIEVENAME.usePOSFeatures";
  private static final String USE_LEXICAL_FEATURES_PROP = "coref.SIEVENAME.useLexicalFeatures";
  private static final String USE_WORD_EMBEDDING_FEATURES_PROP = "coref.SIEVENAME.useWordEmbeddingFeatures";

  public static final Locale LANGUAGE_DEFAULT = Locale.ENGLISH;

  /** if true, remove appositives, predicate nominatives in post processing */
  public static final boolean REMOVE_APPOSITION_PREDICATENOMINATIVES = true;

  /** if true, remove singletons in post processing */
  public static final boolean REMOVE_SINGLETONS = true;

  // current list of dcoref sieves
  private static final Set<String> dcorefSieveNames = new HashSet<>(Arrays.asList("MarkRole", "DiscourseMatch",
          "ExactStringMatch", "RelaxedExactStringMatch", "PreciseConstructs", "StrictHeadMatch1",
          "StrictHeadMatch2", "StrictHeadMatch3", "StrictHeadMatch4", "RelaxedHeadMatch", "PronounMatch", "SpeakerMatch",
          "ChineseHeadMatch"));


  private HybridCorefProperties() {} // static methods/ constants


  public static boolean doScore(Properties props) {
    return PropertiesUtils.getBool(props, SCORE_PROP, false);
  }
  public static boolean checkTime(Properties props) {
    return PropertiesUtils.getBool(props, TIMER_PROP, false);
  }
  public static boolean checkMemory(Properties props) {
    return PropertiesUtils.getBool(props, MEMORY_PROP, false);
  }

  public static int getThreadCounts(Properties props) {
    return PropertiesUtils.getInt(props, THREADS_PROP, Runtime.getRuntime().availableProcessors());
  }
  public static Locale getLanguage(Properties props) {
    String lang = PropertiesUtils.getString(props, LANG_PROP, "en");
    if(lang.equalsIgnoreCase("en") || lang.equalsIgnoreCase("english")) return Locale.ENGLISH;
    else if(lang.equalsIgnoreCase("zh") || lang.equalsIgnoreCase("chinese")) return Locale.CHINESE;
    else throw new RuntimeException("unsupported language");
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
    return PropertiesUtils.getBool(props, USE_SEMANTICS_PROP, false);
  }
  public static String getPathSerializedWordVectors(Properties props) {
    return PropertiesUtils.getString(props, WORD2VEC_SERIALIZED_PROP, "/u/scr/nlp/data/coref/wordvectors/en/vector.ser.gz");
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

  public static boolean useDefaultPronounAgreement(Properties props){
    return PropertiesUtils.getBool(props, HybridCorefProperties.DEFAULT_PRONOUN_AGREEMENT_PROP,false);
  }

  public static boolean addMissingAnnotations(Properties props) {
    return PropertiesUtils.getBool(props, ADD_MISSING_ANNOTATIONS, false);
  }

}
