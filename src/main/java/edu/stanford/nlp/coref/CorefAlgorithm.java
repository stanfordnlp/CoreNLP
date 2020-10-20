package edu.stanford.nlp.coref;

import java.util.Properties;

import edu.stanford.nlp.coref.CorefProperties.CorefAlgorithmType;
import edu.stanford.nlp.coref.data.Dictionaries;
import edu.stanford.nlp.coref.data.Document;
import edu.stanford.nlp.coref.fastneural.FastNeuralCorefAlgorithm;
import edu.stanford.nlp.coref.hybrid.HybridCorefSystem;
import edu.stanford.nlp.coref.neural.NeuralCorefAlgorithm;
import edu.stanford.nlp.coref.statistical.ClusteringCorefAlgorithm;
import edu.stanford.nlp.coref.statistical.StatisticalCorefAlgorithm;
import edu.stanford.nlp.util.PropertiesUtils;

/**
 * A CorefAlgorithms make coreference decisions on the provided {@link Document} after
 * mention detection has been performed.
 *
 * @author Kevin Clark
 */
public interface CorefAlgorithm {

  void runCoref(Document document);

  static CorefAlgorithm fromProps(Properties props, Dictionaries dictionaries) {
    CorefAlgorithmType algorithm = CorefProperties.algorithm(props);
    if (algorithm == CorefAlgorithmType.CLUSTERING) {
      return new ClusteringCorefAlgorithm(props, dictionaries);
    } else if (algorithm == CorefAlgorithmType.STATISTICAL) {
      return new StatisticalCorefAlgorithm(props, dictionaries);
    } else if (algorithm == CorefAlgorithmType.NEURAL) {
      return new NeuralCorefAlgorithm(props, dictionaries);
    } else if (algorithm == CorefAlgorithmType.FASTNEURAL) {
      return new FastNeuralCorefAlgorithm(props, dictionaries);
    } else if (algorithm == CorefAlgorithmType.CUSTOM) {
      String classname = PropertiesUtils.getString(props, "coref.algorithm.class", null);
      try {
        if (classname != null) {
          Class clazz = Class.forName(classname);
          return (CorefAlgorithm) clazz.getConstructor(Properties.class, Dictionaries.class).newInstance(props, dictionaries);
        } else {
          throw new RuntimeException("Please specify coref.algorithm.class");
        }
      } catch (Exception e) {
        throw new RuntimeException("Error creating custom coref system", e);
      }
    } else {
      try {
        return new HybridCorefSystem(props, dictionaries);
      } catch (Exception e) {
        throw new RuntimeException("Error creating hybrid coref system", e);
      }
    }
  }

}
