package edu.stanford.nlp.neural.rnn;

import org.ejml.simple.SimpleMatrix;

import edu.stanford.nlp.ling.CoreAnnotation;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.Label;
import edu.stanford.nlp.trees.Tree;

import java.util.*;

/** Annotations used by Tree Recursive Neural Networks.
 *
 *  @author John Bauer
 */
public class RNNCoreAnnotations {

  private RNNCoreAnnotations() {} // only static members

  /**
   * Used to denote the vector (distributed representation) at a particular node.
   * This stores a real vector that represents the semantics of a word or phrase.
   */
  public static class NodeVector implements CoreAnnotation<SimpleMatrix> {
    @Override
    public Class<SimpleMatrix> getType() {
      return SimpleMatrix.class;
    }
  }

  /**
   * Get the vector (distributed representation) at a particular node.
   *
   * @param tree The tree node
   * @return The vector (distributed representation) of the given tree
   */
  public static SimpleMatrix getNodeVector(Tree tree) {
    Label label = tree.label();
    if (!(label instanceof CoreLabel)) {
      throw new IllegalArgumentException("CoreLabels required to get the attached node vector");
    }
    return ((CoreLabel) label).get(NodeVector.class);
  }

  /**
   * Used to denote a vector of predictions at a particular node.
   * This is a vector of real values, typically the output of a softmax classification layer,
   * which gives the probabilities of each output value.
   */
  public static class Predictions implements CoreAnnotation<SimpleMatrix> {
    @Override
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

  public static List<Double> getPredictionsAsStringList(Tree tree) {
    SimpleMatrix predictions = getPredictions(tree);
    List<Double> listOfPredictions = new ArrayList<>();
    for (int i = 0 ; i < predictions.numRows() ; i++) {
      listOfPredictions.add(predictions.get(i));
    }
    return listOfPredictions;
  }



  /**
   * Get the argmax of the class predictions.
   * The predicted classes can be an arbitrary set of non-negative integer classes,
   * but in our current sentiment models, the values used are on a 5-point
   * scale of 0 = very negative, 1 = negative, 2 = neutral, 3 = positive,
   * and 4 = very positive.
   */
  public static class PredictedClass implements CoreAnnotation<Integer> {
    @Override
    public Class<Integer> getType() {
      return Integer.class;
    }
  }

  /** Return as an int the predicted class. If it is not defined for a node,
   *  it will return -1
   *
   *  @return Either the sentiment level or -1 if none
   */
  public static int getPredictedClass(Tree tree) {
    return getPredictedClass(tree.label());
  }

  /** Return as an int the predicted class. If it is not defined for a node,
   *  it will return -1
   *
   *  @return Either the sentiment level or -1 if none
   */
  public static int getPredictedClass(Label label) {
    if (!(label instanceof CoreLabel)) {
      throw new IllegalArgumentException("CoreLabels required to get the attached predicted class");
    }
    Integer val = ((CoreLabel) label).get(PredictedClass.class);
    return val == null ? -1: val;
  }

  /** Return as a double the probability of the predicted class. If it is not defined for a node,
   *  it will return -1
   *
   *  @return Either the label probability or -1.0 if none
   */
  public static double getPredictedClassProb(Label label) {
    if (!(label instanceof CoreLabel)) {
      throw new IllegalArgumentException("CoreLabels required to get the attached predicted class probability");
    }
    Integer val = ((CoreLabel) label).get(PredictedClass.class);
    SimpleMatrix predictions = ((CoreLabel) label).get(Predictions.class);
    if (val != null) {
      return predictions.get(val);
    } else {
      return -1.0;
    }
  }


  /**
   * The index of the correct class.
   */
  public static class GoldClass implements CoreAnnotation<Integer> {
    @Override
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
      throw new IllegalArgumentException("CoreLabels required to set the attached gold class");
    }
    ((CoreLabel) label).set(GoldClass.class, goldClass);
  }

  public static class PredictionError implements CoreAnnotation<Double> {
    @Override
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
