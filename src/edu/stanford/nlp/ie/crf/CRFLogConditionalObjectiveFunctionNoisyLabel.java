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
  protected final double[][][] parallelEhat;
  protected final double[][] errorMatrix;

  CRFLogConditionalObjectiveFunctionNoisyLabel(int[][][][] data, int[][] labels, int window, Index<String> classIndex, List<Index<CRFLabel>> labelIndices, int[] map, String priorType, String backgroundSymbol, double sigma, double[][][][] featureVal, int multiThreadGrad, double[][] errorMatrix) {
    
    super(data, labels, window, classIndex, labelIndices, map, priorType, backgroundSymbol, sigma, featureVal, multiThreadGrad, false);
    this.errorMatrix = errorMatrix;

    parallelEhat = new double[multiThreadGrad][][];
    for (int i=0; i<multiThreadGrad; i++)
      parallelEhat[i] = empty2D();
  }

  public CliquePotentialFunction getFunc(int docIndex) {
    int[] docLabels = labels[docIndex];
    return new NoisyLabelLinearCliquePotentialFunction(weights, docLabels, errorMatrix);
  }

  public void setWeights(double[][] weights) {
    super.setWeights(weights); 
  }

  protected double expectedCountsAndValueForADoc(double[][] E, double[][] Ehat, int docIndex, boolean doExpectedCountCalc, boolean doValueCalc) {
    int[][][] docData = data[docIndex];
    double[][][] featureVal3DArr = null;
    if (featureVal != null) {
      featureVal3DArr = featureVal[docIndex];
    }
    // make a clique tree for this document
    CRFCliqueTree cliqueTreeNoisyLabel = CRFCliqueTree.getCalibratedCliqueTree(docData, labelIndices, numClasses, classIndex, backgroundSymbol, getFunc(docIndex), featureVal3DArr);
    CRFCliqueTree cliqueTree = CRFCliqueTree.getCalibratedCliqueTree(docData, labelIndices, numClasses, classIndex, backgroundSymbol, cliquePotentialFunc, featureVal3DArr);

    double prob = 0.0;
    if (doValueCalc) {
      prob = cliqueTreeNoisyLabel.totalMass() - cliqueTree.totalMass();
    }

    if (doExpectedCountCalc) {
      documentExpectedCounts(E, docData, featureVal3DArr, cliqueTree);
      documentExpectedCounts(Ehat, docData, featureVal3DArr, cliqueTreeNoisyLabel);
    }

    return prob;
  }

  private ThreadsafeProcessor<Pair<Integer, List<Integer>>, Pair<Integer, Double>> gradientThreadProcessor =
          new ThreadsafeProcessor<Pair<Integer, List<Integer>>, Pair<Integer, Double>>() {
            @Override
            public Pair<Integer, Double> process(Pair<Integer, List<Integer>> threadIDAndDocIndices) {
              int tID = threadIDAndDocIndices.first();
              if (tID < 0 || tID >= multiThreadGrad) throw new IllegalArgumentException("threadID must be with in range 0 <= tID < multiThreadGrad(="+multiThreadGrad+")");
              List<Integer> docIDs = threadIDAndDocIndices.second();
              double[][] partE; // initialized below
              double[][] partEhat; // initialized below
              if (multiThreadGrad == 1) {
                partE = E;
                partEhat = Ehat;
              } else {
                partE = parallelE[tID];
                partEhat = parallelEhat[tID];
                clear2D(partE);
                clear2D(partEhat);
              }
              double probSum = 0;
              for (int docIndex: docIDs) {
                probSum += expectedCountsAndValueForADoc(partE, partEhat, docIndex, true, true);
              }
              return new Pair<Integer, Double>(tID, probSum);
            }
            @Override
            public ThreadsafeProcessor<Pair<Integer, List<Integer>>, Pair<Integer, Double>> newInstance() {
              return this;
            }
          };

  @Override
  protected double regularGradientAndValue() {
    double objective = 0.0;
    MulticoreWrapper<Pair<Integer, List<Integer>>, Pair<Integer, Double>> wrapper =
      new MulticoreWrapper<Pair<Integer, List<Integer>>, Pair<Integer, Double>>(multiThreadGrad, gradientThreadProcessor);

    int totalLen = data.length;
    List<Integer> docIDs = new ArrayList<Integer>(totalLen);
    for (int m=0; m < totalLen; m++) docIDs.add(m);
    int partLen = totalLen / multiThreadGrad;
    int currIndex = 0;
    for (int part=0; part < multiThreadGrad; part++) {
      int endIndex = currIndex + partLen;
      if (part == multiThreadGrad-1)
        endIndex = totalLen;
      List<Integer> subList = docIDs.subList(currIndex, endIndex);
      wrapper.put(new Pair<Integer, List<Integer>>(part, subList));
      currIndex = endIndex;
    }
    wrapper.join();
    while (wrapper.peek()) {
      Pair<Integer, Double> result = wrapper.poll();
      int tID = result.first();
      objective += result.second();
      if (multiThreadGrad > 1) {
        combine2DArr(E, parallelE[tID]);
        combine2DArr(Ehat, parallelEhat[tID]);
      }
    }

    return objective;
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
