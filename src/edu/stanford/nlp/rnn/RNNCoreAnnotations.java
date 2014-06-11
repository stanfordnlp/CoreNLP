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
  
  public static SimpleMatrix getPredictions(Tree tree) {
    Label label = tree.label();
    if (!(label instanceof CoreLabel)) {
      throw new IllegalArgumentException("CoreLabels required to get the attached predictions");
    }
    return ((CoreLabel) label).get(Predictions.class);
  }

  /**
   * argmax of the Predictions
   */
  public static class PredictedClass implements CoreAnnotation<Integer> {
    public Class<Integer> getType() {
      return Integer.class;
    }
  }

  public static int getPredictedClass(Tree tree) {
    Label label = tree.label();
    if (!(label instanceof CoreLabel)) {
      throw new IllegalArgumentException("CoreLabels required to get the attached gold class");
    }
    return ((CoreLabel) label).get(PredictedClass.class);
  }

  /**
   * The index of the correct class
   */
  public static class GoldClass implements CoreAnnotation<Integer> {
    public Class<Integer> getType() {
      return Integer.class;
    }
  }

  public static int getGoldClass(Tree tree) {
    Label label = tree.label();
    if (!(label instanceof CoreLabel)) {
      throw new IllegalArgumentException("CoreLabels required to get the attached gold class");
    }
    return ((CoreLabel) label).get(GoldClass.class);
  }

  public static void setGoldClass(Tree tree, int goldClass) {
    Label label = tree.label();
    if (!(label instanceof CoreLabel)) {
      throw new IllegalArgumentException("CoreLabels required to get the attached gold class");
    }
    ((CoreLabel) label).set(GoldClass.class, goldClass);
  }

  public static class PredictionError implements CoreAnnotation<Double> {
    public Class<Double> getType() {
      return Double.class;
    }
  }

  public static double getPredictionError(Tree tree) {
    Label label = tree.label();
    if (!(label instanceof CoreLabel)) {
      throw new IllegalArgumentException("CoreLabels required to get the attached prediction error");
    }
    return ((CoreLabel) label).get(PredictionError.class);
  }

  public static void setPredictionError(Tree tree, double error) {
    Label label = tree.label();
    if (!(label instanceof CoreLabel)) {
      throw new IllegalArgumentException("CoreLabels required to set the attached prediction error");
    }
    ((CoreLabel) label).set(PredictionError.class, error);
  }
}
