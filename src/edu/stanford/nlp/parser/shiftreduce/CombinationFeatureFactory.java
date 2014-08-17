package edu.stanford.nlp.parser.shiftreduce;

import java.util.List;

/**
 * Combines multiple feature factories into one feature factory
 *
 * @author John Bauer
 */
public class CombinationFeatureFactory extends FeatureFactory {
  FeatureFactory[] factories;

  public CombinationFeatureFactory(FeatureFactory[] factories) {
    this.factories = factories;
  }

  @Override
  public List<String> featurize(State state, List<String> features) {
    for (FeatureFactory factory : factories) {
      factory.featurize(state, features);
    }
    return features;
  }

  private static final long serialVersionUID = 1;
}
