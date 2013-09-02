package edu.stanford.nlp.ie.crf;

import edu.stanford.nlp.math.ArrayMath;
import edu.stanford.nlp.optimization.AbstractStochasticCachingDiffUpdateFunction;
import edu.stanford.nlp.util.Index;

import java.util.Arrays;
import java.util.List;

/**
 * @author Jenny Finkel
 *         Mengqiu Wang
 */

public class CRFLogConditionalObjectiveFunction extends AbstractStochasticCachingDiffUpdateFunction implements HasCliquePotentialFunction {

  public static final int NO_PRIOR = 0;
  public static final int QUADRATIC_PRIOR = 1;
  /* Use a Huber robust regression penalty (L1 except very near 0) not L2 */
  public static final int HUBER_PRIOR = 2;
  public static final int QUARTIC_PRIOR = 3;

  private final int prior;
  private final double sigma;
  private final double epsilon = 0.1; // You can't actually set this at present
  /** label indices - for all possible label sequences - for each feature */
  private final List<Index<CRFLabel>> labelIndices;
  private final Index<String> classIndex;  // didn't have <String> before. Added since that's what is assumed everywhere.
  private final double[][] Ehat; // empirical counts of all the features [feature][class]
  private final int window;
  private final int numClasses;
  private final int[] map;
  private final int[][][][] data;  // data[docIndex][tokenIndex][][]
  private final double[][][][] featureVal;  // featureVal[docIndex][tokenIndex][][]
  private final int[][] labels;    // labels[docIndex][tokenIndex]
  private final int domainDimension;
  private double[][] eHat4Update, e4Update;

  private int[][] weightIndices;

  private final String backgroundSymbol;

  public static boolean VERBOSE = false;

  public static int getPriorType(String priorTypeStr) {
    if (priorTypeStr == null) return QUADRATIC_PRIOR;  // default
    if ("QUADRATIC".equalsIgnoreCase(priorTypeStr)) {
      return QUADRATIC_PRIOR;
    } else if ("HUBER".equalsIgnoreCase(priorTypeStr)) {
      return HUBER_PRIOR;
    } else if ("QUARTIC".equalsIgnoreCase(priorTypeStr)) {
      return QUARTIC_PRIOR;
    } else if ("NONE".equalsIgnoreCase(priorTypeStr)) {
      return NO_PRIOR;
    } else {
      throw new IllegalArgumentException("Unknown prior type: " + priorTypeStr);
    }
  }

  CRFLogConditionalObjectiveFunction(int[][][][] data, int[][] labels, int window, Index<String> classIndex, List<Index<CRFLabel>> labelIndices, int[] map, String backgroundSymbol) {
    this(data, labels, window, classIndex, labelIndices, map, "QUADRATIC", backgroundSymbol);
  }

  CRFLogConditionalObjectiveFunction(int[][][][] data, int[][] labels, int window, Index<String> classIndex, List<Index<CRFLabel>> labelIndices, int[] map, String priorType, String backgroundSymbol) {
    this(data, labels, window, classIndex, labelIndices, map, priorType, backgroundSymbol, 1.0, null);
  }

  CRFLogConditionalObjectiveFunction(int[][][][] data, int[][] labels, int window, Index<String> classIndex, List<Index<CRFLabel>> labelIndices, int[] map, String backgroundSymbol, double sigma, double[][][][] featureVal) {
    this(data, labels, window, classIndex, labelIndices, map, "QUADRATIC", backgroundSymbol, sigma, featureVal);
  }

  CRFLogConditionalObjectiveFunction(int[][][][] data, int[][] labels, int window, Index<String> classIndex, List<Index<CRFLabel>> labelIndices, int[] map, String priorType, String backgroundSymbol, double sigma, double[][][][] featureVal) {
    this.window = window;
    this.classIndex = classIndex;
    this.numClasses = classIndex.size();
    this.labelIndices = labelIndices;
    this.map = map;
    this.data = data;
    this.featureVal = featureVal;
    this.labels = labels;
    this.prior = getPriorType(priorType);
    this.backgroundSymbol = backgroundSymbol;
    this.sigma = sigma;
    Ehat = empty2D();
    empiricalCounts(Ehat);
    int myDomainDimension = 0;
    for (int dim : map) {
      myDomainDimension += labelIndices.get(dim).size();
    }
    domainDimension = myDomainDimension;
  }

  // this used to be computed lazily, but that was clearly erroneous for multithreading!
  @Override
  public int domainDimension() {
    return domainDimension;
  }

  /**
   * Takes a double array of weights and creates a 2D array where:
   *
   * the first element is the mapped index of featuresIndex
   * the second element is the index of the of the element
   *
   * @return a 2D weight array
   */
  public static double[][] to2D(double[] weights, List<Index<CRFLabel>> labelIndices, int[] map) {
    double[][] newWeights = new double[map.length][];
    int index = 0;
    for (int i = 0; i < map.length; i++) {
      newWeights[i] = new double[labelIndices.get(map[i]).size()];
      System.arraycopy(weights, index, newWeights[i], 0, labelIndices.get(map[i]).size());
      index += labelIndices.get(map[i]).size();
    }
    return newWeights;
  }

  public double[][] to2D(double[] weights) {
    return to2D(weights, this.labelIndices, this.map);
  }

  public double[][] to2D(double[] weights, double wscale) {
    for (int i = 0; i < weights.length; i++)
      weights[i] = weights[i] * wscale;

    return to2D(weights, this.labelIndices, this.map);
  }

  public static double[] to1D(double[][] weights, int domainDimension) {
    double[] newWeights = new double[domainDimension];
    int index = 0;
    for (int i = 0; i < weights.length; i++) {
      System.arraycopy(weights[i], 0, newWeights, index, weights[i].length);
      index += weights[i].length;
    }
    return newWeights;
  }

  public double[] to1D(double[][] weights) {
    return to1D(weights, domainDimension());
  }

  public int[][] getWeightIndices()
  {
    if (weightIndices == null) {
      weightIndices = new int[map.length][];
      int index = 0;
      for (int i = 0; i < map.length; i++) {
        weightIndices[i] = new int[labelIndices.get(map[i]).size()];
        for (int j = 0; j < labelIndices.get(map[i]).size(); j++) {
          weightIndices[i][j] = index;
          index++;
        }
      }
    }
    return weightIndices;
  }

  private double[][] empty2D() {
    double[][] d = new double[map.length][];
    // int index = 0;
    for (int i = 0; i < map.length; i++) {
      d[i] = new double[labelIndices.get(map[i]).size()];
    }
    return d;
  }

  private void empiricalCounts(double[][] eHat) {
    for (int m = 0; m < data.length; m++) {
      empiricalCountsForADoc(eHat, m);
    }
  }
      
  private void empiricalCountsForADoc(double[][] eHat, int docIndex) {
    int[][][] docData = data[docIndex];
    int[] docLabels = labels[docIndex];
    int[] windowLabels = new int[window];
    Arrays.fill(windowLabels, classIndex.indexOf(backgroundSymbol));
    double[][][] featureValArr = null;
    if (featureVal != null)
      featureValArr = featureVal[docIndex];

    if (docLabels.length>docData.length) { // only true for self-training
      // fill the windowLabel array with the extra docLabels
      System.arraycopy(docLabels, 0, windowLabels, 0, windowLabels.length);
      // shift the docLabels array left
      int[] newDocLabels = new int[docData.length];
      System.arraycopy(docLabels, docLabels.length-newDocLabels.length, newDocLabels, 0, newDocLabels.length);
      docLabels = newDocLabels;
    }
    for (int i = 0; i < docData.length; i++) {
      System.arraycopy(windowLabels, 1, windowLabels, 0, window - 1);
      windowLabels[window - 1] = docLabels[i];
      for (int j = 0; j < docData[i].length; j++) {
        int[] cliqueLabel = new int[j + 1];
        System.arraycopy(windowLabels, window - 1 - j, cliqueLabel, 0, j + 1);
        CRFLabel crfLabel = new CRFLabel(cliqueLabel);
        int labelIndex = labelIndices.get(j).indexOf(crfLabel);
        //System.err.println(crfLabel + " " + labelIndex);
        for (int n = 0; n < docData[i][j].length; n++) {
          double fVal = 1.0;
          if (featureValArr != null && j == 0) // j == 0 because only node features gets feature values
            fVal = featureValArr[i][j][n];
          eHat[docData[i][j][n]][labelIndex] += fVal;
        }
      }
    }
  }

  public double valueForADoc(double[][] weights, int docIndex) {
    return expectedCountsAndValueForADoc(weights, null, docIndex, true);
  }

  private double expectedCountsAndValueForADoc(double[][] weights, double[][] E, int docIndex) {
    return expectedCountsAndValueForADoc(weights, E, docIndex, false);
  }

  public CliquePotentialFunction getCliquePotentialFunction(double[] x) {
    double[][] weights = to2D(x);
    return new LinearCliquePotentialFunction(weights);
  }

  private double expectedCountsAndValueForADoc(double[][] weights, double[][] E, int docIndex, boolean skipExpectedCountCal) {
    double prob = 0;
    int[][][] docData = data[docIndex];
    int[] docLabels = labels[docIndex];

    double[][][] featureVal3DArr = null;
    if (featureVal != null)
      featureVal3DArr = featureVal[docIndex];

    CliquePotentialFunction cliquePotentialFunc = new LinearCliquePotentialFunction(weights);
    // make a clique tree for this document
    CRFCliqueTree cliqueTree = CRFCliqueTree.getCalibratedCliqueTree(docData, labelIndices, numClasses, classIndex, backgroundSymbol, cliquePotentialFunc, featureVal3DArr);

    // compute the log probability of the document given the model with the parameters x
    int[] given = new int[window - 1];
    Arrays.fill(given, classIndex.indexOf(backgroundSymbol));
    if (docLabels.length>docData.length) { // only true for self-training
      // fill the given array with the extra docLabels
      System.arraycopy(docLabels, 0, given, 0, given.length);
      // shift the docLabels array left
      int[] newDocLabels = new int[docData.length];
      System.arraycopy(docLabels, docLabels.length-newDocLabels.length, newDocLabels, 0, newDocLabels.length);
      docLabels = newDocLabels;
    }

    // iterate over the positions in this document
    for (int i = 0; i < docData.length; i++) {
      int label = docLabels[i];
      double p = cliqueTree.condLogProbGivenPrevious(i, label, given);
      if (VERBOSE) {
        System.err.println("P(" + label + "|" + ArrayMath.toString(given) + ")=" + p);
      }
      prob += p;
      System.arraycopy(given, 1, given, 0, given.length - 1);
      given[given.length - 1] = label;
    }
    
    if (!skipExpectedCountCal) {
      // compute the expected counts for this document, which we will need to compute the derivative
      // iterate over the positions in this document
      for (int i = 0; i < docData.length; i++) {
        // for each possible clique at this position
        for (int j = 0; j < docData[i].length; j++) {
          Index<CRFLabel> labelIndex = labelIndices.get(j);
          // for each possible labeling for that clique
          for (int k = 0; k < labelIndex.size(); k++) {
            int[] label = labelIndex.get(k).getLabel();
            double p = cliqueTree.prob(i, label); // probability of these labels occurring in this clique with these features
            for (int n = 0; n < docData[i][j].length; n++) {
              double fVal = 1.0;
              if (j == 0 && featureVal3DArr != null) // j == 0 because only node features gets feature values
                fVal = featureVal3DArr[i][j][n];
              E[docData[i][j][n]][k] += p * fVal;
            }
          }
        }
      }
    }

    return prob;
  }

  /**
   * Calculates both value and partial derivatives at the point x, and save them internally.
   */
  @Override
  public void calculate(double[] x) {

    double prob = 0.0; // the log prob of the sequence given the model, which is the negation of value at this point
    double[][] weights = to2D(x);

    // the expectations over counts
    // first index is feature index, second index is of possible labeling
    double[][] E = empty2D();

    // iterate over all the documents
    for (int m = 0; m < data.length; m++) {
      prob += expectedCountsAndValueForADoc(weights, E, m);
    }

    if (Double.isNaN(prob)) { // shouldn't be the case
      throw new RuntimeException("Got NaN for prob in CRFLogConditionalObjectiveFunction.calculate()" +
              " - this may well indicate numeric underflow due to overly long documents.");
    }

    value = -prob;
    if (VERBOSE) {
      System.err.println("value is " + value);
    }

    // compute the partial derivative for each feature by comparing expected counts to empirical counts
    int index = 0;
    for (int i = 0; i < E.length; i++) {
      for (int j = 0; j < E[i].length; j++) {
        derivative[index++] = (E[i][j] - Ehat[i][j]);
        if (VERBOSE) {
          System.err.println("deriv(" + i + "," + j + ") = " + E[i][j] + " - " + Ehat[i][j] + " = " + derivative[index - 1]);
        }
      }
    }

    applyPrior(x, 1.0);
  }

  private void applyPrior(double[] x, double batchScale) {
    // incorporate priors
    if (prior == QUADRATIC_PRIOR) {
      double sigmaSq = sigma * sigma;
      double lambda = 1 / 2.0 / sigmaSq;
      for (int i = 0; i < x.length; i++) {
        double w = x[i];
        value += batchScale * w * w * lambda;
        derivative[i] += batchScale * w / sigmaSq;
      }
    } else if (prior == HUBER_PRIOR) {
      double sigmaSq = sigma * sigma;
      for (int i = 0; i < x.length; i++) {
        double w = x[i];
        double wabs = Math.abs(w);
        if (wabs < epsilon) {
          value += batchScale*w * w / 2.0 / epsilon / sigmaSq;
          derivative[i] += batchScale*w / epsilon / sigmaSq;
        } else {
          value += batchScale*(wabs - epsilon / 2) / sigmaSq;
          derivative[i] += batchScale*((w < 0.0) ? -1.0 : 1.0) / sigmaSq;
        }
      }
    } else if (prior == QUARTIC_PRIOR) {
      double sigmaQu = sigma * sigma * sigma * sigma;
      double lambda = 1 / 2.0 / sigmaQu;
      for (int i = 0; i < x.length; i++) {
        double w = x[i];
        value += batchScale * w * w * w * w * lambda;
        derivative[i] += batchScale * w / sigmaQu;
      }
    }
  }

  @Override
  public void calculateStochastic(double[] x, double [] v, int[] batch){
    calculateStochasticGradientOnly(x,batch);
  }

  @Override
  public int dataDimension(){
    return data.length;
  }


  //TODO(mengqiu) SGD based methods are not yet compatible with featureVals
  public void calculateStochasticGradientOnly(double[] x, int[] batch) {

    double prob = 0.0; // the log prob of the sequence given the model, which is the negation of value at this point
    double[][] weights = to2D(x);

    double batchScale = ((double) batch.length)/((double) this.dataDimension());

    // the expectations over counts
    // first index is feature index, second index is of possible labeling
    double[][] E = empty2D();
    // iterate over all the documents
    for (int ind : batch) {
      prob += expectedCountsAndValueForADoc(weights, E, ind);
    }

    if (Double.isNaN(prob)) { // shouldn't be the case
      throw new RuntimeException("Got NaN for prob in CRFLogConditionalObjectiveFunction.calculate()");
    }

    value = -prob;

    // compute the partial derivative for each feature by comparing expected counts to empirical counts
    int index = 0;
    for (int i = 0; i < E.length; i++) {
      for (int j = 0; j < E[i].length; j++) {
        derivative[index++] = (E[i][j] - batchScale*Ehat[i][j]);
        if (VERBOSE) {
          System.err.println("deriv(" + i + "," + j + ") = " + E[i][j] + " - " + Ehat[i][j] + " = " + derivative[index - 1]);
        }
      }
    }

    applyPrior(x, batchScale);
  }

  // re-inititalization is faster than Arrays.fill(arr, 0)
  private void clearUpdateEs() {
    for (int i = 0; i < eHat4Update.length; i++)
      eHat4Update[i] = new double[eHat4Update[i].length];
    for (int i = 0; i < e4Update.length; i++)
      e4Update[i] = new double[e4Update[i].length];
  }

  /**
   * Performs stochastic update of weights x (scaled by xscale) based
   * on samples indexed by batch.
   * NOTE: This function does not do regularization (regularization is done by the minimizer).
   *
   * @param x - unscaled weights
   * @param xscale - how much to scale x by when performing calculations
   * @param batch - indices of which samples to compute function over
   * @param gscale - how much to scale adjustments to x
   * @return value of function at specified x (scaled by xscale) for samples
   */
  @Override
  public double calculateStochasticUpdate(double[] x, double xscale, int[] batch, double gscale) {
    double prob = 0.0; // the log prob of the sequence given the model, which is the negation of value at this point
    // int[][] wis = getWeightIndices();
    double[][] weights = to2D(x, xscale);

    if (eHat4Update == null) {
      eHat4Update = empty2D();
      e4Update = new double[eHat4Update.length][];
      for (int i = 0; i < e4Update.length; i++)
        e4Update[i] = new double[eHat4Update[i].length];
    } else {
      clearUpdateEs();
    }

    // Adjust weight by -gscale*gradient
    // gradient is expected count - empirical count
    // so we adjust by + gscale(empirical count - expected count)

    // iterate over all the documents
    for (int ind : batch) {
      // clearUpdateEs();

      empiricalCountsForADoc(eHat4Update, ind);
      prob += expectedCountsAndValueForADoc(weights, e4Update, ind);

      /* the commented out code below is to iterate over the batch docs instead of iterating over all
         parameters at the end, which is more efficient; but it would also require us to clearUpdateEs()
         for each document, which is likely to out-weight the cost of iterating over params once at the end
      
      for (int i = 0; i < data[ind].length; i++) {
        // for each possible clique at this position
        for (int j = 0; j < data[ind][i].length; j++) {
          Index labelIndex = labelIndices.get(j);
          // for each possible labeling for that clique
          for (int k = 0; k < labelIndex.size(); k++) {
            for (int n = 0; n < data[ind][i][j].length; n++) {
              // Adjust weight by (eHat-e)*gscale (empirical count minus expected count scaled)
              int fIndex = docData[i][j][n];
              x[wis[fIndex][k]] += (eHat4Update[fIndex][k] - e4Update[fIndex][k]) * gscale;
            }
          }
        }
      }
      */
    }

    if (Double.isNaN(prob)) { // shouldn't be the case
      throw new RuntimeException("Got NaN for prob in CRFLogConditionalObjectiveFunction.calculate()");
    }

    value = -prob;

    int index = 0;
    for (int i = 0; i < e4Update.length; i++) {
      for (int j = 0; j < e4Update[i].length; j++) {
        x[index++] += (eHat4Update[i][j] - e4Update[i][j]) * gscale;
      }
    }
     
    return value;
  }

  /**
   * Computes value of function for specified value of x (scaled by xscale)
   * only over samples indexed by batch.
   * NOTE: This function does not do regularization (regularization is done by the minimizer).
   *
   * @param x - unscaled weights
   * @param xscale - how much to scale x by when performing calculations
   * @param batch - indices of which samples to compute function over
   * @return value of function at specified x (scaled by xscale) for samples
   */
  @Override
  public double valueAt(double[] x, double xscale, int[] batch) {
    double prob = 0; // the log prob of the sequence given the model, which is the negation of value at this point
    // int[][] wis = getWeightIndices();
    double[][] weights = to2D(x, xscale);

    // iterate over all the documents
    for (int ind : batch) {
      prob += valueForADoc(weights, ind);
    }

    if (Double.isNaN(prob)) { // shouldn't be the case
      throw new RuntimeException("Got NaN for prob in CRFLogConditionalObjectiveFunction.calculate()");
    }

    value = -prob;
    return value;
  }
}
