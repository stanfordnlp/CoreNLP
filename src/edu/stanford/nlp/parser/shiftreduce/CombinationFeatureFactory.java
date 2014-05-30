package edu.stanford.nlp.parser.shiftreduce;

import java.util.List;

import edu.stanford.nlp.util.Generics;

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
  public List<String> featurize(State state) {
    List<String> features = Generics.newArrayList();
    for (FeatureFactory factory : factories) {
      features.addAll(factory.featurize(state));
    }
    return features;
  }

  private static final long serialVersionUID = 1;  
}
