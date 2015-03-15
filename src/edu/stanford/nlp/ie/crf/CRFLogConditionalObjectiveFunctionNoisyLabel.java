package edu.stanford.nlp.ie.crf;

import edu.stanford.nlp.math.ArrayMath;
import edu.stanford.nlp.optimization.AbstractStochasticCachingDiffUpdateFunction;
import edu.stanford.nlp.optimization.HasFeatureGrouping;
import edu.stanford.nlp.util.concurrent.*;
import edu.stanford.nlp.util.Index;
import edu.stanford.nlp.util.Pair;

import java.util.*;

/**
 * @author Mengqiu Wang
 */

public class CRFLogConditionalObjectiveFunctionNoisyLabel extends CRFLogConditionalObjectiveFunction {
  // protected final double[][][] parallelEhat;
  protected final double[][] errorMatrix;

  CRFLogConditionalObjectiveFunctionNoisyLabel(int[][][][] data, int[][] labels, int window, Index<String> classIndex, List<Index<CRFLabel>> labelIndices, int[] map, String priorType, String backgroundSymbol, double sigma, double[][][][] featureVal, int multiThreadGrad, double[][] errorMatrix) {
    
    super(data, labels, window, classIndex, labelIndices, map, priorType, backgroundSymbol, sigma, featureVal, multiThreadGrad, false);
    this.errorMatrix = errorMatrix;
  }

  public CliquePotentialFunction getFunc(int docIndex) {
    int[] docLabels = labels[docIndex];
    return new NoisyLabelLinearCliquePotentialFunction(weights, docLabels, errorMatrix);
  }

  public void setWeights(double[][] weights) {
    super.setWeights(weights); 
  }

  @Override
  protected double expectedAndEmpiricalCountsAndValueForADoc(double[][] E, double[][] Ehat, int docIndex) {
    int[][][] docData = data[docIndex];
    double[][][] featureVal3DArr = null;
    if (featureVal != null) {
      featureVal3DArr = featureVal[docIndex];
    }
    // make a clique tree for this document
    CRFCliqueTree cliqueTreeNoisyLabel = CRFCliqueTree.getCalibratedCliqueTree(docData, labelIndices, numClasses, classIndex, backgroundSymbol, getFunc(docIndex), featureVal3DArr);
    CRFCliqueTree cliqueTree = CRFCliqueTree.getCalibratedCliqueTree(docData, labelIndices, numClasses, classIndex, backgroundSymbol, cliquePotentialFunc, featureVal3DArr);

    double prob = 0.0;
    prob = cliqueTreeNoisyLabel.totalMass() - cliqueTree.totalMass();

    documentExpectedCounts(E, docData, featureVal3DArr, cliqueTree);
    documentExpectedCounts(Ehat, docData, featureVal3DArr, cliqueTreeNoisyLabel);
    return prob;
  }

  @Override
  protected double regularGradientAndValue() {
    int totalLen = data.length;
    List<Integer> docIDs = new ArrayList<Integer>(totalLen);
    for (int m=0; m < totalLen; m++) docIDs.add(m);

    return multiThreadGradient(docIDs, true);
  }

  /**
   * Calculates both value and partial derivatives at the point x, and save them internally.
   */
  @Override
  public void calculate(double[] x) {
    clear2D(Ehat);
    super.calculate(x);
  }
}
