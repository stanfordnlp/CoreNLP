package edu.stanford.nlp.coref;

import java.util.Locale;
import java.util.Properties;

import edu.stanford.nlp.util.PropertiesUtils;

/**
 * Manages the properties for running coref
 * @author Kevin Clark
 */
public class CorefProperties {
  //---------- Coreference Algorithms ----------

  public enum CorefAlgorithmType {CLUSTERING, STATISTICAL, NEURAL, HYBRID}
  public static CorefAlgorithmType algorithm(Properties props) {
    String type = PropertiesUtils.getString(props, "coref.algorithm",
        getLanguage(props) == Locale.ENGLISH ? "statistical" : "neural");
    return CorefAlgorithmType.valueOf(type.toUpperCase());
  }

  //---------- General Coreference Options ----------

  /**
   * When conll() is true, coref models
   * <ul>
   *    <li>Use provided POS, NER, Parsing, etc. (instead of using CoreNLP annotators)</li>
   *    <li>Use provided speaker annotations</li>
   *    <li>Use provided document type and genre information</li>
   * </ul>
   */
  public static boolean conll(Properties props) {
    return PropertiesUtils.getBool(props, "coref.conll", false);
  }

  public static boolean useConstituencyParse(Properties props) {
    return PropertiesUtils.getBool(props, "coref.useConstituencyParse",
        algorithm(props) != CorefAlgorithmType.STATISTICAL || conll(props));
  }

  public static boolean verbose(Properties props) {
    return PropertiesUtils.getBool(props, "coref.verbose", false);
  }

  // ---------- Heuristic Mention Filtering ----------

  public static int maxMentionDistance(Properties props) {
    return PropertiesUtils.getInt(props, "coref.maxMentionDistance",
        conll(props) ? Integer.MAX_VALUE : 50);
  }

  public static int maxMentionDistanceWithStringMatch(Properties props) {
    return PropertiesUtils.getInt(props, "coref.maxMentionDistanceWithStringMatch", 500);
  }

  // ---------- Mention Detection ----------

  public enum MentionDetectionType { RULE, HYBRID, DEPENDENCY }
  public static MentionDetectionType mdType(Properties props) {
    String type = PropertiesUtils.getString(props, "coref.md.type",
        useConstituencyParse(props) ? "RULE" : "dep");
    if (type.equalsIgnoreCase("dep")) {
      type = "DEPENDENCY";
    }
    return MentionDetectionType.valueOf(type.toUpperCase());
  }

  public static String getMentionDetectionModel(Properties props) {
    return PropertiesUtils.getString(props, "coref.md.model",
        useConstituencyParse(props) ? "edu/stanford/nlp/models/coref/md-model.ser" :
              "edu/stanford/nlp/models/coref/md-model-dep.ser.gz");
  }

  public static boolean isMentionDetectionTraining(Properties props) {
    return PropertiesUtils.getBool(props, "coref.md.isTraining", false);
  }

  public static void setMentionDetectionTraining(Properties props, boolean val) {
    props.put("coref.md.isTraining", val);
  }

  public static boolean removeNestedMentions(Properties props) {
    return PropertiesUtils.getBool(props, "removeNestedMentions", true);
  }

  public static void setRemoveNestedMentions(Properties props, boolean val) {
    props.put("removeNestedMentions", val);
  }

  public static boolean liberalChineseMD(Properties props) {
    return PropertiesUtils.getBool(props, "coref.md.liberalChineseMD", true);
  }

  // ---------- Input and Output Data ----------

  public static final String OUTPUT_PATH_PROP = "coref.conllOutputPath";
  public static String conllOutputPath(Properties props) {
    return props.getProperty("coref.conllOutputPath");
  }

  public enum Dataset {TRAIN, DEV, TEST};
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
        getDataPath(props) + "v4/data/development/data/" + getLanguageStr(props) + "/annotations/");
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

  public static String getScorerPath(Properties props) {
    return props.getProperty("coref.scorer");
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
}
