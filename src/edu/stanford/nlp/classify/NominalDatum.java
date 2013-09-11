package edu.stanford.nlp.classify;

import java.util.Map;

public class NominalDatum<FEATURE_NAME, FEATURE_VALUE, LABEL> {

  Map<FEATURE_NAME, FEATURE_VALUE> features;
  LABEL label;

  public NominalDatum(Map<FEATURE_NAME, FEATURE_VALUE> f, LABEL l) {
    features = f;
    label = l;
  }

  public Map<FEATURE_NAME, FEATURE_VALUE> asFeatures() {
    return features;
  }

  public Object label() {
    return label;
  }

}
