package edu.stanford.nlp.coref.fastneural;

import java.util.Properties;

import edu.stanford.nlp.util.PropertiesUtils;

/**
 * Manages the properties for training and running fast neural coreference systems.
 * @author Kevin Clark
 */
public class FastNeuralCorefProperties {
  public static double greedyness(Properties props) {
    return PropertiesUtils.getDouble(props, "coref.fastneural.greedyness", 0.5);
  }

  public static String modelPath(Properties props) {
    return PropertiesUtils.getString(props, "coref.fastneural.modelPath",
        "edu/stanford/nlp/models/coref/fastneural/fast-english-model.ser.gz");
  }
}
