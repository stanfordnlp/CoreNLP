package edu.stanford.nlp.coref;

import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;
import java.util.function.Predicate;

import edu.stanford.nlp.coref.data.CorefChain;
import edu.stanford.nlp.coref.data.CorefCluster;
import edu.stanford.nlp.coref.data.Mention;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.trees.HeadFinder;
import edu.stanford.nlp.trees.SemanticHeadFinder;
import edu.stanford.nlp.trees.international.pennchinese.ChineseSemanticHeadFinder;
import edu.stanford.nlp.util.CollectionUtils;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.Pair;
import edu.stanford.nlp.util.PropertiesUtils;

/**
 * Manages the properties for running coref.
 *
 * @author Kevin Clark
 */
public class CorefProperties {

  private CorefProperties() {} // static methods


  //---------- Coreference Algorithms ----------

  public enum CorefAlgorithmType {CLUSTERING, STATISTICAL, NEURAL, FASTNEURAL, HYBRID, CUSTOM}

  public static CorefAlgorithmType algorithm(Properties props) {
    String type = PropertiesUtils.getString(props, "coref.algorithm",
        getLanguage(props) == Locale.ENGLISH ? "statistical" : "neural");
    return CorefAlgorithmType.valueOf(type.toUpperCase(Locale.ROOT));
  }

  //---------- General Coreference Options ----------

  /**
   * When conll() is true, the neural and statistical (but not fastneural) coref models:
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
        (algorithm(props) != CorefAlgorithmType.STATISTICAL &&
        algorithm(props) != CorefAlgorithmType.FASTNEURAL)|| conll(props));
  }

  public static boolean verbose(Properties props) {
    return PropertiesUtils.getBool(props, "coref.verbose", false);
  }

  public static boolean removeSingletonClusters(Properties props) {
    return PropertiesUtils.getBool(props, "coref.removeSingletonClusters", true);
  }

  public static boolean removeXmlMentions(Properties props) {
    return PropertiesUtils.getBool(props, "coref.removeXmlMentions", false);
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
    return MentionDetectionType.valueOf(type.toUpperCase(Locale.ROOT));
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
    props.setProperty("coref.md.isTraining", String.valueOf(val));
  }

  public static boolean removeNestedMentions(Properties props) {
    return PropertiesUtils.getBool(props, "removeNestedMentions", true);
  }

  public static void setRemoveNestedMentions(Properties props, boolean val) {
    props.setProperty("removeNestedMentions", String.valueOf(val));
  }

  public static boolean liberalMD(Properties props) {
    return PropertiesUtils.getBool(props, "coref.md.liberalMD", false);
  }

  public static boolean useGoldMentions(Properties props) {
    return PropertiesUtils.getBool(props, "coref.md.useGoldMentions", false);
  }

  // ---------- Input and Output Data ----------

  public static final String OUTPUT_PATH_PROP = "coref.conllOutputPath";
  public static String conllOutputPath(Properties props) {
    String returnPath = props.getProperty("coref.conllOutputPath", "/u/nlp/data/coref/logs/");
    if ( ! returnPath.endsWith("/")) {
      returnPath += "/";
    }
    return returnPath;
  }

  public enum Dataset {TRAIN, DEV, TEST}

  public static void setInput(Properties props, Dataset d) {
    props.setProperty("coref.inputPath", d == Dataset.TRAIN ? getTrainDataPath(props) :
      (d == Dataset.DEV ? getDevDataPath(props) : getTestDataPath(props)));
  }

  private static String getDataPath(Properties props) {
    String returnPath = props.getProperty("coref.data", "/u/nlp/data/coref/conll-2012/");
    if ( ! returnPath.endsWith("/")) {
      returnPath += "/";
    }
    return returnPath;
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
    String input = props.getProperty("coref.inputPath", getTestDataPath(props));
    return input;
  }

  public static String getScorerPath(Properties props) {
    return props.getProperty("coref.scorer", "/u/nlp/data/coref/conll-2012/scorer/v8.01/scorer.pl");
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

  public static HeadFinder getHeadFinder(Properties props) {
    Locale lang = getLanguage(props);
    if (lang == Locale.ENGLISH) return new SemanticHeadFinder();
    else if (lang == Locale.CHINESE) return new ChineseSemanticHeadFinder();
    else {
      throw new RuntimeException("Invalid language setting: cannot load HeadFinder");
    }
  }

  public static Predicate<Pair<CorefChain.CorefMention, List<CoreLabel>>> getCorefMentionFilter(Properties props) {
    String filterCorefChain = props.getProperty("coref.evaluate.filter");
    if (filterCorefChain != null) {
        if ("filterCustomerAbstractPronouns".equals(filterCorefChain)) {
            return CorefUtils.filterCustomerAbstractPronouns;
        } else {
            throw new RuntimeException("Cannot create coref.evaluate.filter " + filterCorefChain);
        }
    } else {
        return null;
    }
  }
}
