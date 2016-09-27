package edu.stanford.nlp.coref.statistical;

import java.util.Arrays;
import java.util.Properties;

import edu.stanford.nlp.coref.CorefProperties;
import edu.stanford.nlp.util.PropertiesUtils;

public class StatisticalCorefProperties {
  private static final String DEFAULT_MODELS_PATH = "edu/stanford/nlp/models/dcoref/";

  public static Properties addHcorefProps(Properties props) {
    Properties newProps = (Properties) props.clone();
    newProps.setProperty(CorefProperties.USE_SEMANTICS_PROP, "false");
    newProps.setProperty(CorefProperties.GENDER_NUMBER_PROP,
        "edu/stanford/nlp/models/dcoref/gender.data.gz");
    newProps.setProperty(CorefProperties.INPUT_TYPE_PROP, "conll");
    if (props.containsKey("coref.scorer")) {
      newProps.setProperty(CorefProperties.PATH_SCORER_PROP, props.getProperty("coref.scorer"));
    }

    if (conll(props)) {
      newProps.setProperty(CorefProperties.PARSER_PROP,props.getProperty(CorefProperties.PARSER_PROP, "true"));
      newProps.setProperty(CorefProperties.MD_TYPE_PROP, props.getProperty(CorefProperties.MD_TYPE_PROP, "rule"));
      newProps.setProperty("coref.useMarkedDiscourse", "true");
    } else {
      String mdPath = PropertiesUtils.getString(newProps, "coref.mentionDetectionModel",
          "edu/stanford/nlp/models/coref/md-model.ser");
      //String mdDir = mdPath.substring(0, mdPath.lastIndexOf('/') + 1);
      //String mdModelName = mdPath.substring(mdPath.lastIndexOf('/') + 1);
      //newProps.setProperty("coref.md.model", mdModelName);
      //newProps.setProperty(CorefProperties.PATH_SERIALIZED_PROP, mdDir);
      newProps.setProperty(CorefProperties.MD_TYPE_PROP, "dependency");
      newProps.setProperty(CorefProperties.USE_GOLD_POS_PROP, "false");
      newProps.setProperty(CorefProperties.USE_GOLD_NE_PROP, "false");
      newProps.setProperty(CorefProperties.USE_GOLD_PARSES_PROP, "false");
    }
    if (props.containsKey("coref.test")) {
      newProps.setProperty(CorefProperties.PATH_INPUT_PROP, props.getProperty("coref.test"));
    }

    return newProps;
  }

  public enum Dataset {TRAIN, DEV, TEST};
  public static void setInput(Properties props, Dataset d) {
    props.setProperty(CorefProperties.PATH_INPUT_PROP, d == Dataset.TRAIN
        ? props.getProperty("coref.train") : (d == Dataset.DEV ? props.getProperty("coref.dev")
            : props.getProperty("coref.test")));
  }

  public static boolean conll(Properties props) {
    return PropertiesUtils.getBool(props, "coref.conll", false);
  }

  public static String trainingPath(Properties props) {
    return props.getProperty("coref.trainingPath");
  }

  public static String conllOutputPath(Properties props) {
    return props.getProperty("coref.conllOutputPath");
  }

  public static String classificationModelPath(Properties props) {
    return PropertiesUtils.getString(props, "coref.classificationModel",
        "edu/stanford/nlp/models/scoref/classification_model.ser.gz");
  }

  public static String rankingModelPath(Properties props) {
    return PropertiesUtils.getString(props, "coref.rankingModel",
        "edu/stanford/nlp/models/scoref/ranking_model.ser.gz");
  }

  public static String anaphoricityModelPath(Properties props) {
    return PropertiesUtils.getString(props, "coref.anaphoricityModel",
        "edu/stanford/nlp/models/scoref/anaphoricity_model.ser.gz");
  }

  public static String clusteringModelPath(Properties props) {
    return PropertiesUtils.getString(props, "coref.clusteringModel",
        "edu/stanford/nlp/models/scoref/clustering_model.ser");
  }

  public static String wordCountsPath(Properties props) {
    return PropertiesUtils.getString(props, "coref.wordCounts",
        "edu/stanford/nlp/models/scoref/word_counts.ser.gz");
  }

  private static String defaultModelPath(Properties props, String modelName) {
    return DEFAULT_MODELS_PATH + modelName + (conll(props) ? "_conll" : "" + ".ser");
  }

  public static boolean cluster(Properties props) {
    return PropertiesUtils.getBool(props, "coref.doClustering", true);
  }

  public static int maxMentionDistance(Properties props) {
    return PropertiesUtils.getInt(props, "coref.maxMentionDistance", 50);
  }

  public static int maxMentionDistanceWithStringMatch(Properties props) {
    return PropertiesUtils.getInt(props, "coref.maxMentionDistanceWithStringMatch", 5000);
  }

  public static double[] pairwiseScoreThresholds(Properties props) {
    String thresholdsProp = (String) props.get("coref.pairwiseScoreThresholds");
    if (thresholdsProp != null) {
      String[] split = thresholdsProp.split(",");
      if (split.length == 4) {
        return Arrays.stream(split).mapToDouble(Double::parseDouble).toArray();
      }
    }
    double threshold = PropertiesUtils.getDouble(props, "coref.pairwiseScoreThresholds", 0.35);
    return new double[] {threshold, threshold, threshold, threshold};
  }

  public static boolean useConstituencyParse(Properties props) {
    boolean defaultValue = conll(props);
    return PropertiesUtils.getBool(props, CorefProperties.PARSER_PROP, defaultValue);
  }

  public static double minClassImbalance(Properties props) {
    return PropertiesUtils.getDouble(props, "coref.minClassImbalance", 0);
  }

  public static int minTrainExamplesPerDocument(Properties props) {
    return PropertiesUtils.getInt(props, "coref.minTrainExamplesPerDocument", Integer.MAX_VALUE);
  }
}
