package edu.stanford.nlp.ie.crf;

import edu.stanford.nlp.math.ArrayMath;
import edu.stanford.nlp.optimization.AbstractStochasticCachingDiffUpdateFunction;
import edu.stanford.nlp.optimization.HasFeatureGrouping;
import edu.stanford.nlp.util.concurrent.*;
import edu.stanford.nlp.util.Index;
import edu.stanford.nlp.util.Pair;

import java.util.*;

/**
 * @author Jenny Finkel
 *         Mengqiu Wang
 */

public class CRFLogConditionalObjectiveFunction extends AbstractStochasticCachingDiffUpdateFunction implements HasCliquePotentialFunction, HasFeatureGrouping {

  public static final int NO_PRIOR = 0;
  public static final int QUADRATIC_PRIOR = 1;
  /* Use a Huber robust regression penalty (L1 except very near 0) not L2 */
  public static final int HUBER_PRIOR = 2;
  public static final int QUARTIC_PRIOR = 3;
  public static final int DROPOUT_PRIOR = 4;

  // public static final boolean DEBUG2 = true;
  public static final boolean DEBUG2 = false;
  public static final boolean DEBUG3 = false;
  public static final boolean TIMED = false;
  // public static final boolean TIMED = true;
  public static final boolean CONDENSE = true;
  // public static final boolean CONDENSE = false;
  public static boolean VERBOSE = false;

  protected final int prior;
  protected final double sigma;
  protected final double epsilon = 0.1; // You can't actually set this at present
  /** label indices - for all possible label sequences - for each feature */
  protected final List<Index<CRFLabel>> labelIndices;
  protected final Index<String> classIndex;  // didn't have <String> before. Added since that's what is assumed everywhere.
  protected final double[][] Ehat; // empirical counts of all the features [feature][class]
  protected final double[][] E;
  protected double[][][] parallelE;
  protected double[][][] parallelEhat;

  protected final int window;
  protected final int numClasses;
  // public static Index<String> featureIndex;  // no idea why this was here [cdm 2013]
  protected final int[] map;
  protected int[][][][] data;  // data[docIndex][tokenIndex][][]
  protected double[][][][] featureVal;  // featureVal[docIndex][tokenIndex][][]
  protected int[][] labels;    // labels[docIndex][tokenIndex]
  protected final int domainDimension;
  // protected double[][] eHat4Update, e4Update;

  protected int[][] weightIndices;
  protected final String backgroundSymbol;

  protected int[][] featureGrouping = null;

  protected static final double smallConst = 1e-6;
  // protected static final double largeConst = 5;

  protected Random rand = new Random(2147483647L);

  protected final int multiThreadGrad;
  // need to ensure the following two objects are only read during multi-threading
  // to ensure thread-safety. It should only be modified in calculate() via setWeights()
  protected double[][] weights;
  protected CliquePotentialFunction cliquePotentialFunc;

  @Override
  public double[] initial() {
    return initial(rand);
  }
  public double[] initial(boolean useRandomSeed) {
    Random randToUse = useRandomSeed ? new Random() : rand;
    return initial(rand);
  }

  public double[] initial(Random randGen) {
    double[] initial = new double[domainDimension()];
    for (int i = 0; i < initial.length; i++) {
      initial[i] = randGen.nextDouble() + smallConst;
      // initial[i] = generator.nextDouble() * largeConst;
      // initial[i] = -1+2*(i);
      // initial[i] = (i == 0 ? 1 : 0);
    }
    return initial;
  }

  public static int getPriorType(String priorTypeStr) {
    if (priorTypeStr == null) return QUADRATIC_PRIOR;  // default
    if ("QUADRATIC".equalsIgnoreCase(priorTypeStr)) {
      return QUADRATIC_PRIOR;
    } else if ("HUBER".equalsIgnoreCase(priorTypeStr)) {
      return HUBER_PRIOR;
    } else if ("QUARTIC".equalsIgnoreCase(priorTypeStr)) {
      return QUARTIC_PRIOR;
    } else if ("DROPOUT".equalsIgnoreCase(priorTypeStr)) {
      return DROPOUT_PRIOR;
    } else if ("NONE".equalsIgnoreCase(priorTypeStr)) {
      return NO_PRIOR;
    } else if (priorTypeStr.equalsIgnoreCase("lasso") ||
               priorTypeStr.equalsIgnoreCase("ridge") ||
               priorTypeStr.equalsIgnoreCase("gaussian") ||
               priorTypeStr.equalsIgnoreCase("ae-lasso") ||
               priorTypeStr.equalsIgnoreCase("sg-lasso") ||
               priorTypeStr.equalsIgnoreCase("g-lasso") ) {
      return NO_PRIOR;
    } else {
      throw new IllegalArgumentException("Unknown prior type: " + priorTypeStr);
    }
  }

  CRFLogConditionalObjectiveFunction(int[][][][] data, int[][] labels, int window, Index<String> classIndex, List<Index<CRFLabel>> labelIndices, int[] map, String priorType, String backgroundSymbol, double sigma, double[][][][] featureVal, int multiThreadGrad) {
    this(data, labels, window, classIndex, labelIndices, map, priorType, backgroundSymbol, sigma, featureVal, multiThreadGrad, true);
  }

  CRFLogConditionalObjectiveFunction(int[][][][] data, int[][] labels, int window, Index<String> classIndex, List<Index<CRFLabel>> labelIndices, int[] map, String priorType, String backgroundSymbol, double sigma, double[][][][] featureVal, int multiThreadGrad, boolean calcEmpirical) {
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
    this.multiThreadGrad = multiThreadGrad;
    // takes docIndex, returns Triple<prob, E, dropoutGrad>
    Ehat = empty2D();
    E = empty2D();
    weights = empty2D();
    if (calcEmpirical)
      empiricalCounts(Ehat);
    int myDomainDimension = 0;
    for (int dim : map) {
      myDomainDimension += labelIndices.get(dim).size();
    }
    domainDimension = myDomainDimension;
  }

  protected void empiricalCounts(double[][] eHat) {
    for (int m = 0; m < data.length; m++) {
      empiricalCountsForADoc(eHat, m);
    }
  }

  protected void empiricalCountsForADoc(double[][] eHat, int docIndex) {
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

  @Override
  public CliquePotentialFunction getCliquePotentialFunction(double[] x) {
    to2D(x, weights);
    return new LinearCliquePotentialFunction(weights);
  }

  protected double expectedAndEmpiricalCountsAndValueForADoc(double[][] E, double[][] Ehat, int docIndex) {
    empiricalCountsForADoc(Ehat, docIndex);
    return expectedCountsAndValueForADoc(E, docIndex);
  }

  public double valueForADoc(int docIndex) {
    return expectedCountsAndValueForADoc(null, docIndex, false, true);
  }

  protected double expectedCountsAndValueForADoc(double[][] E, int docIndex) {
    return expectedCountsAndValueForADoc(E, docIndex, true, true);
  }

  protected double expectedCountsForADoc(double[][] E, int docIndex) {
    return expectedCountsAndValueForADoc(E, docIndex, true, false);
  }

  protected double expectedCountsAndValueForADoc(double[][] E, int docIndex, boolean doExpectedCountCalc, boolean doValueCalc) {
    int[][][] docData = data[docIndex];
    double[][][] featureVal3DArr = null;
    if (featureVal != null) {
      featureVal3DArr = featureVal[docIndex];
    }
    // make a clique tree for this document
    CRFCliqueTree cliqueTree = CRFCliqueTree.getCalibratedCliqueTree(docData, labelIndices, numClasses, classIndex, backgroundSymbol, cliquePotentialFunc, featureVal3DArr);

    double prob = 0.0;
    if (doValueCalc) {
      prob = documentLogProbability(docData, docIndex, cliqueTree);
    }

    if (doExpectedCountCalc) {
      documentExpectedCounts(E, docData, featureVal3DArr, cliqueTree);
    }

    return prob;
  }

  /** Compute the expected counts for this document, which we will need to compute the derivative. */
  protected void documentExpectedCounts(double[][] E, int[][][] docData, double[][][] featureVal3DArr, CRFCliqueTree cliqueTree) {
    // iterate over the positions in this document
    for (int i = 0; i < docData.length; i++) {
      // for each possible clique at this position
      for (int j = 0; j < docData[i].length; j++) {
        Index<CRFLabel> labelIndex = labelIndices.get(j);
        // for each possible labeling for that clique
        for (int k = 0, liSize = labelIndex.size(); k < liSize; k++) {
          int[] label = labelIndex.get(k).getLabel();
          double p = cliqueTree.prob(i, label); // probability of these labels occurring in this clique with these features
          for (int n = 0; n < docData[i][j].length; n++) {
            double fVal = 1.0;
            if (j == 0 && featureVal3DArr != null) { // j == 0 because only node features gets feature values
              fVal = featureVal3DArr[i][j][n];
            }
            E[docData[i][j][n]][k] += p * fVal;
          }
        }
      }
    }
  }

  /** Compute the log probability of the document given the model with the parameters x. */
  private double documentLogProbability(int[][][] docData, int docIndex, CRFCliqueTree cliqueTree) {
    int[] docLabels = labels[docIndex];
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

    double startPosLogProb = cliqueTree.logProbStartPos();
    if (VERBOSE) {
      System.err.printf("P_-1(Background) = % 5.3f%n", startPosLogProb);
    }
    double prob = startPosLogProb;

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
    return prob;
  }

  private ThreadsafeProcessor<Pair<Integer, List<Integer>>, Pair<Integer, Double>> expectedThreadProcessor = new ExpectationThreadsafeProcessor();
  private ThreadsafeProcessor<Pair<Integer, List<Integer>>, Pair<Integer, Double>> expectedAndEmpiricalThreadProcessor = new ExpectationThreadsafeProcessor(true);

  class ExpectationThreadsafeProcessor implements ThreadsafeProcessor<Pair<Integer, List<Integer>>, Pair<Integer, Double>> {
    boolean calculateEmpirical = false;
    public ExpectationThreadsafeProcessor() {
    }

    public ExpectationThreadsafeProcessor(boolean calculateEmpirical) {
      this.calculateEmpirical = calculateEmpirical;
    }

    @Override
    public Pair<Integer, Double> process(Pair<Integer, List<Integer>> threadIDAndDocIndices) {
      int tID = threadIDAndDocIndices.first();
      if (tID < 0 || tID >= multiThreadGrad) throw new IllegalArgumentException("threadID must be with in range 0 <= tID < multiThreadGrad(="+multiThreadGrad+")");
      List<Integer> docIDs = threadIDAndDocIndices.second();
      double[][] partE; // initialized below
      double[][] partEhat = null; // initialized below
      if (multiThreadGrad == 1) {
        partE = E;
        if (calculateEmpirical)
          partEhat = Ehat;
      } else {
        partE = parallelE[tID];
        clear2D(partE);
        if (calculateEmpirical) {
          partEhat = parallelEhat[tID];
          clear2D(partEhat);
        }
      }
      double probSum = 0;
      for (int docIndex: docIDs) {
        if (calculateEmpirical)
          probSum += expectedAndEmpiricalCountsAndValueForADoc(partE, partEhat, docIndex);
        else
          probSum += expectedCountsAndValueForADoc(partE, docIndex);
      }
      return new Pair<Integer, Double>(tID, probSum);
    }

    @Override
    public ThreadsafeProcessor<Pair<Integer, List<Integer>>, Pair<Integer, Double>> newInstance() {
      return this;
    }
  }

  public void setWeights(double[][] weights) {
    this.weights = weights;
    cliquePotentialFunc = new LinearCliquePotentialFunction(weights);
  }


  protected double regularGradientAndValue() {
    int totalLen = data.length;
    List<Integer> docIDs = new ArrayList<Integer>(totalLen);
    for (int m=0; m < totalLen; m++) docIDs.add(m);

    return multiThreadGradient(docIDs, false);
  }

  protected double multiThreadGradient(List<Integer> docIDs, boolean calculateEmpirical) {
    double objective = 0.0;
    if (multiThreadGrad > 1) {
      if (parallelE == null) {
        parallelE = new double[multiThreadGrad][][];
        for (int i=0; i<multiThreadGrad; i++)
          parallelE[i] = empty2D();
      }
      if (calculateEmpirical) {
        if (parallelEhat == null) {
          parallelEhat = new double[multiThreadGrad][][];
          for (int i=0; i<multiThreadGrad; i++)
            parallelEhat[i] = empty2D();
        }
      }
    }

    MulticoreWrapper<Pair<Integer, List<Integer>>, Pair<Integer, Double>> wrapper =
      new MulticoreWrapper<Pair<Integer, List<Integer>>, Pair<Integer, Double>>(multiThreadGrad, (calculateEmpirical ? expectedAndEmpiricalThreadProcessor : expectedThreadProcessor) );

    int totalLen = docIDs.size();
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
        if (calculateEmpirical)
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

    double prob = 0.0; // the log prob of the sequence given the model, which is the negation of value at this point
    // final double[][] weights = to2D(x);
    to2D(x, weights);
    setWeights(weights);

    // the expectations over counts
    // first index is feature index, second index is of possible labeling
    // double[][] E = empty2D();
    clear2D(E);

    prob = regularGradientAndValue();

    if (Double.isNaN(prob)) { // shouldn't be the case
      throw new RuntimeException("Got NaN for prob in CRFLogConditionalObjectiveFunction.calculate()" +
              " - this may well indicate numeric underflow due to overly long documents.");
    }

    // because we minimize -L(\theta)
    value = -prob;
    if (VERBOSE) {
      System.err.println("value is " + Math.exp(-value));
    }

    // compute the partial derivative for each feature by comparing expected counts to empirical counts
    int index = 0;
    for (int i = 0; i < E.length; i++) {
      for (int j = 0; j < E[i].length; j++) {
        // because we minimize -L(\theta)
        derivative[index] = (E[i][j] - Ehat[i][j]);
        if (VERBOSE) {
          System.err.println("deriv(" + i + "," + j + ") = " + E[i][j] + " - " + Ehat[i][j] + " = " + derivative[index]);
        }
        index++;
      }
    }

    applyPrior(x, 1.0);

    // System.err.println("\nfuncVal: " + value);
  }

  @Override
  public int dataDimension() {
    return data.length;
  }

  @Override
  public void calculateStochastic(double[] x, double [] v, int[] batch) {
    double prob = 0.0; // the log prob of the sequence given the model, which is the negation of value at this point
    to2D(x, weights);
    setWeights(weights);

    double batchScale = ((double) batch.length)/((double) this.dataDimension());

    // the expectations over counts
    // first index is feature index, second index is of possible labeling
    // double[][] E = empty2D();

    // iterate over all the documents
    List<Integer> docIDs = new ArrayList<Integer>(batch.length);
    for (int m=0; m < batch.length; m++) docIDs.add(batch[m]);
    prob = multiThreadGradient(docIDs, false); 

    if (Double.isNaN(prob)) { // shouldn't be the case
      throw new RuntimeException("Got NaN for prob in CRFLogConditionalObjectiveFunction.calculate()");
    }

    value = -prob;

    // compute the partial derivative for each feature by comparing expected counts to empirical counts
    int index = 0;
    for (int i = 0; i < E.length; i++) {
      for (int j = 0; j < E[i].length; j++) {
        // real gradient should be empirical-expected;
        // but since we minimize -L(\theta), the gradient is -(empirical-expected)
        derivative[index++] = (E[i][j] - batchScale*Ehat[i][j]);
        if (VERBOSE) {
          System.err.println("deriv(" + i + "," + j + ") = " + E[i][j] + " - " + Ehat[i][j] + " = " + derivative[index - 1]);
        }
      }
    }

    applyPrior(x, batchScale);
  }

  // re-initialization is faster than Arrays.fill(arr, 0)
  // private void clearUpdateEs() {
  //   for (int i = 0; i < eHat4Update.length; i++)
  //     eHat4Update[i] = new double[eHat4Update[i].length];
  //   for (int i = 0; i < e4Update.length; i++)
  //     e4Update[i] = new double[e4Update[i].length];
  // }

  /**
   * Performs stochastic update of weights x (scaled by xScale) based
   * on samples indexed by batch.
   * NOTE: This function does not do regularization (regularization is done by the minimizer).
   *
   * @param x - unscaled weights
   * @param xScale - how much to scale x by when performing calculations
   * @param batch - indices of which samples to compute function over
   * @param gScale - how much to scale adjustments to x
   * @return value of function at specified x (scaled by xScale) for samples
   */
  @Override
  public double calculateStochasticUpdate(double[] x, double xScale, int[] batch, double gScale) {
    double prob = 0.0; // the log prob of the sequence given the model, which is the negation of value at this point
    // int[][] wis = getWeightIndices();
    to2D(x, xScale, weights);
    setWeights(weights);

    // if (eHat4Update == null) {
    //   eHat4Update = empty2D();
    //   e4Update = new double[eHat4Update.length][];
    //   for (int i = 0; i < e4Update.length; i++)
    //     e4Update[i] = new double[eHat4Update[i].length];
    // } else {
    //   clearUpdateEs();
    // }

    // Adjust weight by -gScale*gradient
    // gradient is expected count - empirical count
    // so we adjust by + gScale(empirical count - expected count)

    // iterate over all the documents
    List<Integer> docIDs = new ArrayList<Integer>(batch.length);
    for (int m=0; m < batch.length; m++) docIDs.add(batch[m]);
    prob = multiThreadGradient(docIDs, true); 

    if (Double.isNaN(prob)) { // shouldn't be the case
      throw new RuntimeException("Got NaN for prob in CRFLogConditionalObjectiveFunction.calculate()");
    }

    value = -prob;

    int index = 0;
    for (int i = 0; i < E.length; i++) {
      for (int j = 0; j < E[i].length; j++) {
        x[index++] += (Ehat[i][j] - E[i][j]) * gScale;
      }
    }

    return value;
  }

  /**
   * Performs stochastic gradient update based
   * on samples indexed by batch, but does not apply regularization.
   *
   * @param x - unscaled weights
   * @param batch - indices of which samples to compute function over
   */
  @Override
  public void calculateStochasticGradient(double[] x, int[] batch) {
    if (derivative == null) {
      derivative = new double[domainDimension()];
    }
    // int[][] wis = getWeightIndices();
    // was: double[][] weights = to2D(x, 1.0); // but 1.0 should be the same as omitting 2nd parameter....
    to2D(x, weights);
    setWeights(weights);

    // iterate over all the documents
    List<Integer> docIDs = new ArrayList<Integer>(batch.length);
    for (int m=0; m < batch.length; m++) docIDs.add(batch[m]);
    multiThreadGradient(docIDs, true); 

    int index = 0;
    for (int i = 0; i < E.length; i++) {
      for (int j = 0; j < E[i].length; j++) {
        // real gradient should be empirical-expected;
        // but since we minimize -L(\theta), the gradient is -(empirical-expected)
        derivative[index++] = (E[i][j]-Ehat[i][j]);
      }
    }
  }

  /**
   * Computes value of function for specified value of x (scaled by xScale)
   * only over samples indexed by batch.
   * NOTE: This function does not do regularization (regularization is done by the minimizer).
   *
   * @param x - unscaled weights
   * @param xScale - how much to scale x by when performing calculations
   * @param batch - indices of which samples to compute function over
   * @return value of function at specified x (scaled by xScale) for samples
   */
  @Override
  public double valueAt(double[] x, double xScale, int[] batch) {
    double prob = 0.0; // the log prob of the sequence given the model, which is the negation of value at this point
    // int[][] wis = getWeightIndices();
    to2D(x, xScale, weights);
    setWeights(weights);

    // iterate over all the documents
    for (int ind : batch) {
      prob += valueForADoc(ind);
    }

    if (Double.isNaN(prob)) { // shouldn't be the case
      throw new RuntimeException("Got NaN for prob in CRFLogConditionalObjectiveFunction.calculate()");
    }

    value = -prob;
    return value;
  }

  @Override
  public int[][] getFeatureGrouping() {
    if (featureGrouping != null)
      return featureGrouping;
    else {
      int[][] fg = new int[1][];
      fg[0] = ArrayMath.range(0, domainDimension());
      return fg;
    }
  }

  public void setFeatureGrouping(int[][] fg) {
    this.featureGrouping = fg;
  }

  protected void applyPrior(double[] x, double batchScale) {
    // incorporate priors
    if (prior == QUADRATIC_PRIOR) {
      double lambda = 1 / (sigma * sigma);
      for (int i = 0; i < x.length; i++) {
        double w = x[i];
        value += batchScale * w * w * lambda * 0.5;
        derivative[i] += batchScale * w * lambda;
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


  protected Pair<double[][][], double[][][]> getCondProbs(CRFCliqueTree cTree, int[][][] docData) {
    // first index position is curr index, second index curr-class, third index prev-class
    // e.g. [1][2][3] means curr is at position 1 with class 2, prev is at position 0 with class 3
    double[][][] prevGivenCurr = new double[docData.length][][];
    // first index position is curr index, second index curr-class, third index next-class
    // e.g. [0][2][3] means curr is at position 0 with class 2, next is at position 1 with class 3
    double[][][] nextGivenCurr = new double[docData.length][][];

    for (int i = 0; i < docData.length; i++) {
      prevGivenCurr[i] = new double[numClasses][];
      nextGivenCurr[i] = new double[numClasses][];
      for (int j = 0; j < numClasses; j++) {
        prevGivenCurr[i][j] = new double[numClasses];
        nextGivenCurr[i][j] = new double[numClasses];
      }
    }

    // computing prevGivenCurr and nextGivenCurr
    for (int i=0; i < docData.length; i++) {
      int[] labelPair = new int[2];
      for (int l1 = 0; l1 < numClasses; l1++) {
        labelPair[0] = l1;
        for (int l2 = 0; l2 < numClasses; l2++) {
          labelPair[1] = l2;
          double prob = cTree.logProb(i, labelPair);
          // System.err.println(prob);
          if (i-1 >= 0)
            nextGivenCurr[i-1][l1][l2] = prob;
          prevGivenCurr[i][l2][l1] = prob;
        }
      }

      if (DEBUG2) {
        System.err.println("unnormalized conditionals:");
        if (i>0) {
        System.err.println("nextGivenCurr[" + (i-1) + "]:");
        for (int a = 0; a < nextGivenCurr[i-1].length; a++) {
          for (int b = 0; b < nextGivenCurr[i-1][a].length; b++)
            System.err.print((nextGivenCurr[i-1][a][b])+"\t");
          System.err.println();
        }
        }
        System.err.println("prevGivenCurr[" + (i) + "]:");
        for (int a = 0; a < prevGivenCurr[i].length; a++) {
          for (int b = 0; b < prevGivenCurr[i][a].length; b++)
            System.err.print((prevGivenCurr[i][a][b])+"\t");
          System.err.println();
        }
      }

      for (int j=0; j< numClasses; j++) {
        if (i-1 >= 0) {
          // ArrayMath.normalize(nextGivenCurr[i-1][j]);
          ArrayMath.logNormalize(nextGivenCurr[i-1][j]);
          for (int k = 0; k < nextGivenCurr[i-1][j].length; k++)
            nextGivenCurr[i-1][j][k] = Math.exp(nextGivenCurr[i-1][j][k]);
        }
        // ArrayMath.normalize(prevGivenCurr[i][j]);
        ArrayMath.logNormalize(prevGivenCurr[i][j]);
        for (int k = 0; k < prevGivenCurr[i][j].length; k++)
          prevGivenCurr[i][j][k] = Math.exp(prevGivenCurr[i][j][k]);
      }

      if (DEBUG2) {
        System.err.println("normalized conditionals:");
        if (i>0) {
        System.err.println("nextGivenCurr[" + (i-1) + "]:");
        for (int a = 0; a < nextGivenCurr[i-1].length; a++) {
          for (int b = 0; b < nextGivenCurr[i-1][a].length; b++)
            System.err.print((nextGivenCurr[i-1][a][b])+"\t");
          System.err.println();
        }
        }
        System.err.println("prevGivenCurr[" + (i) + "]:");
        for (int a = 0; a < prevGivenCurr[i].length; a++) {
          for (int b = 0; b < prevGivenCurr[i][a].length; b++)
            System.err.print((prevGivenCurr[i][a][b])+"\t");
          System.err.println();
        }
      }
    }

    return new Pair<double[][][], double[][][]>(prevGivenCurr, nextGivenCurr);
  }

  protected static void combine2DArr(double[][] combineInto, double[][] toBeCombined, double scale) {
    for (int i = 0; i < toBeCombined.length; i++)
      for (int j = 0; j < toBeCombined[i].length; j++)
        combineInto[i][j] += toBeCombined[i][j] * scale;
  }

  protected static void combine2DArr(double[][] combineInto, double[][] toBeCombined) {
    for (int i = 0; i < toBeCombined.length; i++)
      for (int j = 0; j < toBeCombined[i].length; j++)
        combineInto[i][j] += toBeCombined[i][j];
  }

  // TODO(mengqiu) add dimension checks
  protected static void combine2DArr(double[][] combineInto, Map<Integer, double[]> toBeCombined) {
    for (Map.Entry<Integer, double[]> entry: toBeCombined.entrySet()) {
      int key = entry.getKey();
      double[] source = entry.getValue();
      for (int i = 0; i< source.length; i++)
        combineInto[key][i] += source[i];
    }
  }

  protected static void combine2DArr(double[][] combineInto, Map<Integer, double[]> toBeCombined, double scale) {
    for (Map.Entry<Integer, double[]> entry: toBeCombined.entrySet()) {
      int key = entry.getKey();
      double[] source = entry.getValue();
      for (int i = 0; i< source.length; i++)
        combineInto[key][i] += source[i] * scale;
    }
  }

  // this used to be computed lazily, but that was clearly erroneous for multithreading!
  @Override
  public int domainDimension() {
    return domainDimension;
  }

  /**
   * Takes a double array of weights and creates a 2D array where:
   *
   * the first element is the mapped index of the clique size (e.g., node-0, edge-1) matching featuresIndex i
   * the second element is the number of output classes for that clique size
   *
   * @return a 2D weight array
   */
  public static double[][] to2D(double[] weights, List<Index<CRFLabel>> labelIndices, int[] map) {
    double[][] newWeights = new double[map.length][];
    int index = 0;
    for (int i = 0; i < map.length; i++) {
      int labelSize = labelIndices.get(map[i]).size();
      newWeights[i] = new double[labelSize];
      try {
        System.arraycopy(weights, index, newWeights[i], 0, labelSize);
      } catch (Exception ex) {
        System.err.println("weights: " + Arrays.toString(weights));
        System.err.println("newWeights["+i+"]: " + Arrays.toString(newWeights[i]));
        throw new RuntimeException(ex);
      }
      index += labelSize;
    }
    return newWeights;
  }

  public double[][] to2D(double[] weights) {
    return to2D(weights, this.labelIndices, this.map);
  }

  public static void to2D(double[] weights, List<Index<CRFLabel>> labelIndices, int[] map, double[][] newWeights) {
    int index = 0;
    for (int i = 0; i < map.length; i++) {
      int labelSize = labelIndices.get(map[i]).size();
      try {
        System.arraycopy(weights, index, newWeights[i], 0, labelSize);
      } catch (Exception ex) {
        System.err.println("weights: " + Arrays.toString(weights));
        System.err.println("newWeights["+i+"]: " + Arrays.toString(newWeights[i]));
        throw new RuntimeException(ex);
      }
      index += labelSize;
    }
  }

  public void to2D(double[] weights1D, double[][] newWeights) {
    to2D(weights1D, this.labelIndices, this.map, newWeights);
  }

  /** Beware: this changes the input weights array in place. */
  public double[][] to2D(double[] weights1D, double wScale) {
    for (int i = 0; i < weights1D.length; i++)
      weights1D[i] = weights1D[i] * wScale;

    return to2D(weights1D, this.labelIndices, this.map);
  }

  /** Beware: this changes the input weights array in place. */
  public void to2D(double[] weights1D, double wScale, double[][] newWeights) {
    for (int i = 0; i < weights1D.length; i++)
      weights1D[i] = weights1D[i] * wScale;

    to2D(weights1D, this.labelIndices, this.map, newWeights);
  }

  public static void clear2D(double[][] arr2D) {
    for (int i = 0; i < arr2D.length; i++)
      for (int j = 0; j < arr2D[i].length; j++)
        arr2D[i][j] = 0;
  }

  public static void to1D(double[][] weights, double[] newWeights) {
    int index = 0;
    for (double[] weightVector : weights) {
      System.arraycopy(weightVector, 0, newWeights, index, weightVector.length);
      index += weightVector.length;
    }
  }

  public static double[] to1D(double[][] weights, int domainDimension) {
    double[] newWeights = new double[domainDimension];
    int index = 0;
    for (double[] weightVector : weights) {
      System.arraycopy(weightVector, 0, newWeights, index, weightVector.length);
      index += weightVector.length;
    }
    return newWeights;
  }

  public double[] to1D(double[][] weights) {
    return to1D(weights, domainDimension());
  }

  public int[][] getWeightIndices() {
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

  protected double[][] empty2D() {
    double[][] d = new double[map.length][];
    // int index = 0;
    for (int i = 0; i < map.length; i++) {
      d[i] = new double[labelIndices.get(map[i]).size()];
    }
    return d;
  }

  public int[][] getLabels() {
    return labels;
  }
}
