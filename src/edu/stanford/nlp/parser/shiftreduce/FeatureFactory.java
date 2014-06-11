package edu.stanford.nlp.parser.shiftreduce;

import java.util.Set;

public interface FeatureFactory {
  Set<String> featurize(State state);
}
