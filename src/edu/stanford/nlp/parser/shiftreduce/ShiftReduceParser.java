package edu.stanford.nlp.parser.shiftreduce;

import java.io.Serializable;

import edu.stanford.nlp.parser.lexparser.Options;
import edu.stanford.nlp.util.Index;

public class ShiftReduceParser implements Serializable {
  final Index<Transition> transitionIndex;
  final Index<String> featureIndex;
  final double[][] featureWeights;

  // TODO: replace this with our own options object?
  final Options op;

  // TODO: then we could fold the featureFactory into our options
  final FeatureFactory featureFactory;

  public ShiftReduceParser(Index<Transition> transitionIndex, Index<String> featureIndex,
                           double[][] featureWeights, Options op, FeatureFactory featureFactory) {
    this.transitionIndex = transitionIndex;
    this.featureIndex = featureIndex;
    this.featureWeights = featureWeights;
    this.op = op;
    this.featureFactory = featureFactory;
  }

  private static final long serialVersionUID = 1;  
}

