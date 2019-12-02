package edu.stanford.nlp.coref;

import java.util.Properties;

import edu.stanford.nlp.coref.CorefProperties.CorefAlgorithmType;
import edu.stanford.nlp.coref.data.Dictionaries;
import edu.stanford.nlp.coref.data.Document;
import edu.stanford.nlp.coref.hybrid.HybridCorefSystem;
import edu.stanford.nlp.coref.neural.NeuralCorefAlgorithm;
import edu.stanford.nlp.coref.statistical.ClusteringCorefAlgorithm;
import edu.stanford.nlp.coref.statistical.StatisticalCorefAlgorithm;

/**
 * A CorefAlgorithms makes coreference decisions on the provided {@link Document} after
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
    } else {
      try {
        return new HybridCorefSystem(props, dictionaries);
      } catch (Exception e) {
        throw new RuntimeException("Error creating hybrid coref system", e);
      }
    }
  }

}
