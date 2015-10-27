package edu.stanford.nlp.scoref;

import java.util.Properties;

import edu.stanford.nlp.hcoref.CorefProperties;
import edu.stanford.nlp.util.PropertiesUtils;
import edu.stanford.nlp.util.StringUtils;

public class StatisticalCorefProperties {
  private static final String DEFAULT_MODELS_PATH = "edu/stanford/nlp/models/dcoref/";

  public static Properties loadProps(String file) {
    return addHcorefProps(StringUtils.argsToProperties(new String[] {"-props", file}));
  }

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
      newProps.setProperty(CorefProperties.INPUT_TYPE_PROP, "conll");
      newProps.setProperty(CorefProperties.PARSER_PROP, "true");
      newProps.setProperty(CorefProperties.MD_TYPE_PROP, "rule");
      newProps.setProperty("coref.useMarkedDiscourse", "true");
    } else {
      newProps.setProperty(CorefProperties.MD_TYPE_PROP, "dependency");
      newProps.setProperty("coref.md.model", "md-model.ser");
      newProps.setProperty(CorefProperties.PATH_SERIALIZED_PROP,
          "/Users/kevinclark/Programming/research/kbp/hybrid-conll/");
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
        defaultModelPath(props, "classification"));
  }

  public static String rankingModelPath(Properties props) {
    return PropertiesUtils.getString(props, "coref.rankingModel",
        defaultModelPath(props, "ranking"));
  }

  public static String anaphoricityModelPath(Properties props) {
    return PropertiesUtils.getString(props, "coref.anaphoricityModel",
        defaultModelPath(props, "anaphoricity"));
  }

  public static String clusteringModelPath(Properties props) {
    return PropertiesUtils.getString(props, "coref.clusteringModel",
        defaultModelPath(props, "clustering"));
  }

  public static String wordCountsPath(Properties props) {
    return PropertiesUtils.getString(props, "coref.wordCounts",
        defaultModelPath(props, "wordCounts"));
  }

  private static String defaultModelPath(Properties props, String modelName) {
    return DEFAULT_MODELS_PATH + modelName + (conll(props) ? "-conll" : "" + ".ser");
  }

  public static boolean cluster(Properties props) {
    return PropertiesUtils.getBool(props, "coref.doClustering", true);
  }

  public static int maxMentionDistance(Properties props) {
    return PropertiesUtils.getInt(props, "coref.maxMentionDistance", Integer.MAX_VALUE);
  }

  public static double pairwiseScoreThreshold(Properties props) {
    return PropertiesUtils.getDouble(props, "coref.pairwiseScoreThreshold", 0.3);
  }

  public static boolean useConstituencyParse(Properties props) {
    return conll(props);
  }

  public static double minClassImbalance(Properties props) {
    return PropertiesUtils.getDouble(props, "coref.minClassImbalance", 0);
  }

  public static int minTrainExamplesPerDocument(Properties props) {
    return PropertiesUtils.getInt(props, "coref.minTrainExamplesPerDocument", Integer.MAX_VALUE);
  }
}
