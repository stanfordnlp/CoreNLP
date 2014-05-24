package edu.stanford.nlp.parser.shiftreduce;

import java.io.Serializable;
import java.util.List;

public interface FeatureFactory extends Serializable {
  List<String> featurize(State state);
}
