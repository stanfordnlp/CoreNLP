package edu.stanford.nlp.rnn;

import org.ejml.simple.SimpleMatrix;

import edu.stanford.nlp.ling.CoreAnnotation;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.Label;
import edu.stanford.nlp.trees.Tree;

public class RNNCoreAnnotations {

  private RNNCoreAnnotations() {} // only static members

  /**
   * Used to denote the vector at a particular node
   */
  public static class NodeVector implements CoreAnnotation<SimpleMatrix> {
    public Class<SimpleMatrix> getType() {
      return SimpleMatrix.class;
    }
  }
  
  public static SimpleMatrix getNodeVector(Tree tree) {
    Label label = tree.label();
    if (!(label instanceof CoreLabel)) {
      throw new IllegalArgumentException("CoreLabels required to get the attached node vector");
    }

    return ((CoreLabel) label).get(NodeVector.class);
  }
  
  /**
   * Used to denote a vector of predictions at a particular node
   */
  public static class Predictions implements CoreAnnotation<SimpleMatrix> {
    public Class<SimpleMatrix> getType() {
      return SimpleMatrix.class;
    }
  }
  
  /**
   * argmax of the Predictions
   */
  public static class PredictedClass implements CoreAnnotation<Integer> {
    public Class<Integer> getType() {
      return Integer.class;
    }
  }
}
