package edu.stanford.nlp.parser.shiftreduce;

import java.io.Serializable;
import java.util.Set;

public interface FeatureFactory extends Serializable {
  Set<String> featurize(State state);
}
