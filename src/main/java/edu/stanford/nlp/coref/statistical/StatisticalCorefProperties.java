package edu.stanford.nlp.coref.statistical;

import java.util.Arrays;
import java.util.Properties;

import edu.stanford.nlp.coref.CorefProperties;
import edu.stanford.nlp.util.PropertiesUtils;

/**
 * Manages the properties for training and running statistical coreference systems.
 * @author Kevin Clark
 */
public class StatisticalCorefProperties {
  public static String trainingPath(Properties props) {
    return props.getProperty("coref.statistical.trainingPath");
  }

  private static String getDefaultModelPath(Properties props, String modelName) {
    return "edu/stanford/nlp/models/coref/statistical/" + modelName +
        (CorefProperties.conll(props) ? "_conll" : "") + ".ser.gz";
  }

  public static String classificationModelPath(Properties props) {
    return PropertiesUtils.getString(props, "coref.statistical.classificationModel",
        getDefaultModelPath(props, "classification_model"));
  }

  public static String rankingModelPath(Properties props) {
    return PropertiesUtils.getString(props, "coref.statistical.rankingModel",
        getDefaultModelPath(props, "ranking_model"));
  }

  public static String anaphoricityModelPath(Properties props) {
    return PropertiesUtils.getString(props, "coref.statistical.anaphoricityModel",
        getDefaultModelPath(props, "anaphoricity_model"));
  }

  public static String clusteringModelPath(Properties props) {
    return PropertiesUtils.getString(props, "coref.statistical.clusteringModel",
        getDefaultModelPath(props, "clustering_model"));
  }

  public static String wordCountsPath(Properties props) {
    return PropertiesUtils.getString(props, "coref.statistical.wordCounts",
        "edu/stanford/nlp/models/coref/statistical/word_counts.ser.gz");
  }

  public static double[] pairwiseScoreThresholds(Properties props) {
    String thresholdsProp = props.getProperty("coref.statistical.pairwiseScoreThresholds");
    if (thresholdsProp != null) {
      String[] split = thresholdsProp.split(",");
      if (split.length == 4) {
        return Arrays.stream(split).mapToDouble(Double::parseDouble).toArray();
      }
    }
    double threshold = PropertiesUtils.getDouble(
        props, "coref.statistical.pairwiseScoreThresholds", 0.35);
    return new double[] {threshold, threshold, threshold, threshold};
  }

  public static double minClassImbalance(Properties props) {
    return PropertiesUtils.getDouble(props, "coref.statistical.minClassImbalance", 0);
  }

  public static int maxTrainExamplesPerDocument(Properties props) {
    return PropertiesUtils.getInt(props, "coref.statistical.maxTrainExamplesPerDocument",
        Integer.MAX_VALUE);
  }
}
