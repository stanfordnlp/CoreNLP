package edu.stanford.nlp.coref.neural;

import java.util.Locale;
import java.util.Properties;

import edu.stanford.nlp.coref.CorefProperties;
import edu.stanford.nlp.util.PropertiesUtils;

/**
 * Manages the properties for training and running neural coreference systems.
 * @author Kevin Clark
 */
public class NeuralCorefProperties {
  public static double greedyness(Properties props) {
    return PropertiesUtils.getDouble(props, "coref.neural.greedyness", 0.5);
  }

  public static String modelPath(Properties props) {
    String defaultPath = "edu/stanford/nlp/models/coref/neural/" +
        (CorefProperties.getLanguage(props) == Locale.CHINESE ? "chinese" : "english") +
        (CorefProperties.conll(props) ? "-model-conll" : "-model-default") + ".ser.gz";
    return PropertiesUtils.getString(props, "coref.neural.modelPath", defaultPath);
  }

  public static String pretrainedEmbeddingsPath(Properties props) {
    String defaultPath = "edu/stanford/nlp/models/coref/neural/" +
        (CorefProperties.getLanguage(props) == Locale.CHINESE ? "chinese" : "english") +
        "-embeddings.ser.gz";
    return PropertiesUtils.getString(props, "coref.neural.embeddingsPath", defaultPath);
  }
}
