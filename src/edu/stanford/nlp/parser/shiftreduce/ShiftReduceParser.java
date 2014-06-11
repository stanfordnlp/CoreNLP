package edu.stanford.nlp.parser.shiftreduce;

import java.io.Serializable;

import edu.stanford.nlp.util.Index;

public class ShiftReduceParser implements Serializable {
  final Index<Transition> transitionIndex;
  final Index<String> featureIndex;
  final double[][] featureWeights;

  public ShiftReduceParser(Index<Transition> transitionIndex, Index<String> featureIndex,
                           double[][] featureWeights) {
    this.transitionIndex = transitionIndex;
    this.featureIndex = featureIndex;
    this.featureWeights = featureWeights;
  }

  private static final long serialVersionUID = 1;  
}

