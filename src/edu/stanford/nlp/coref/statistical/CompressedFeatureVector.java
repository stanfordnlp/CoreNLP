package edu.stanford.nlp.coref.statistical;

import java.io.Serializable;
import java.util.List;

public class CompressedFeatureVector implements Serializable {
  private static final long serialVersionUID = -8889507443653366753L;
  public final List<Integer> keys;
  public final List<Double> values;

  public CompressedFeatureVector(List<Integer> keys, List<Double> values) {
    this.keys = keys;
    this.values = values;
  }
}
