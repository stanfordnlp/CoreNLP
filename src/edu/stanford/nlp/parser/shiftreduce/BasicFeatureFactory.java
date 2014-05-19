package edu.stanford.nlp.parser.shiftreduce;

import java.util.Set;

import edu.stanford.nlp.util.Generics;

public class BasicFeatureFactory implements FeatureFactory {
  public Set<String> featurize(State state) {
    Set<String> features = Generics.newHashSet();
    return features;
  }

}

