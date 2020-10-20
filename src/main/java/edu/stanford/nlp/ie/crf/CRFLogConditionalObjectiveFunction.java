package edu.stanford.nlp.ie.crf;

import edu.stanford.nlp.math.ArrayMath;
import edu.stanford.nlp.optimization.AbstractStochasticCachingDiffUpdateFunction;
import edu.stanford.nlp.optimization.HasFeatureGrouping;
import edu.stanford.nlp.util.concurrent.*;
import edu.stanford.nlp.util.Index;
import edu.stanford.nlp.util.Pair;
import edu.stanford.nlp.util.logging.Redwood;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * @author Jenny Finkel
 *         Mengqiu Wang
 */

public class CRFLogConditionalObjectiveFunction extends AbstractStochasticCachingDiffUpdateFunction implements HasCliquePotentialFunction, HasFeatureGrouping  {

  /** A logger for this class */
  private static final Redwood.RedwoodChannels log = Redwood.channels(CRFLogConditionalObjectiveFunction.class);

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
    return initial(randToUse);
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

    log.info("Running gradient on " + multiThreadGrad + " threads");
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
    double[][][] featureValArr = (featureVal != null) ? featureVal[docIndex] : null;

    if (docLabels.length>docData.length) { // only true for self-training
      // fill the windowLabel array with the extra docLabels
      System.arraycopy(docLabels, 0, windowLabels, 0, windowLabels.length);
      // shift the docLabels array left
      docLabels = Arrays.copyOfRange(docLabels, docLabels.length-docData.length, docLabels.length);
    }
    for (int i = 0; i < docData.length; i++) {
      System.arraycopy(windowLabels, 1, windowLabels, 0, window - 1);
      windowLabels[window - 1] = docLabels[i];
      int[][] docData_i = docData[i];
      for (int j = 0; j < docData_i.length; j++) {
        int[] cliqueLabel = new int[j + 1];
        System.arraycopy(windowLabels, window - 1 - j, cliqueLabel, 0, j + 1);
        CRFLabel crfLabel = new CRFLabel(cliqueLabel);
        int labelIndex = labelIndices.get(j).indexOf(crfLabel);
        //log.info(crfLabel + " " + labelIndex);
        int[] docData_ij = docData_i[j];
        double[] featureValArr_ij = (j == 0 && featureValArr != null) ? featureValArr[i][j] : null; // j == 0 because only node features gets feature values
        for (int n = 0; n < docData_ij.length; n++) {
          eHat[docData_ij[n]][labelIndex] += featureValArr_ij != null ? featureValArr_ij[n] : 1.0;
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
    double[][][] featureVal3DArr = featureVal != null ? featureVal[docIndex] : null;
    // make a clique tree for this document
    CRFCliqueTree<String> cliqueTree = CRFCliqueTree.getCalibratedCliqueTree(docData, labelIndices, numClasses, classIndex, backgroundSymbol, cliquePotentialFunc, featureVal3DArr);

    double prob = doValueCalc ? documentLogProbability(docData, docIndex, cliqueTree) : 0.;

    if (doExpectedCountCalc) {
      documentExpectedCounts(E, docData, featureVal3DArr, cliqueTree);
    }

    return prob;
  }

  /** Compute the expected counts for this document, which we will need to compute the derivative. */
  protected void documentExpectedCounts(double[][] E, int[][][] docData, double[][][] featureVal3DArr, CRFCliqueTree<String> cliqueTree) {
    // iterate over the positions in this document
    for (int i = 0; i < docData.length; i++) {
      // for each possible clique at this position
      int[][] docData_i = docData[i];
      for (int j = 0; j < docData_i.length; j++) {
        Index<CRFLabel> labelIndex = labelIndices.get(j);
        int[] docData_ij = docData_i[j];
        double[] featureValArr_ij = (j == 0 && featureVal3DArr != null) ? featureVal3DArr[i][j] : null; // j == 0 because only node features gets feature values
        // for each possible labeling for that clique
        for (int k = 0, liSize = labelIndex.size(); k < liSize; k++) {
          int[] label = labelIndex.get(k).getLabel();
          double p = cliqueTree.prob(i, label); // probability of these labels occurring in this clique with these features
          for (int n = 0; n < docData_ij.length; n++) {
            E[docData_ij[n]][k] += featureValArr_ij != null ? p * featureValArr_ij[n] : p;
          }
        }
      }
    }
  }

  /** Compute the log probability of the document given the model with the parameters x. */
  private double documentLogProbability(int[][][] docData, int docIndex, CRFCliqueTree<String> cliqueTree) {
    int[] docLabels = labels[docIndex];
    int[] given = new int[window - 1];
    Arrays.fill(given, classIndex.indexOf(backgroundSymbol));
    if (docLabels.length>docData.length) { // only true for self-training
      // fill the given array with the extra docLabels
      System.arraycopy(docLabels, 0, given, 0, given.length);
      // shift the docLabels array left
      docLabels = Arrays.copyOfRange(docLabels, docLabels.length-docData.length, docLabels.length);
    }

    double startPosLogProb = cliqueTree.logProbStartPos();
    if (VERBOSE || Double.isNaN(startPosLogProb)) {
      System.err.printf("P_-1(Background) = % 5.3f%n", startPosLogProb);
    }
    double prob = startPosLogProb;

    // iterate over the positions in this document
    for (int i = 0; i < docData.length; i++) {
      int label = docLabels[i];
      double p = cliqueTree.condLogProbGivenPrevious(i, label, given);
      if (VERBOSE || Double.isNaN(p)) {
        log.info("P(" + label + "|" + ArrayMath.toString(given) + ")=" + p);
      }
      prob += p;
      System.arraycopy(given, 1, given, 0, given.length - 1);
      given[given.length - 1] = label;
    }
    return prob;
  }

  /** Task part for a single thread */
  private static class TaskPart {
    public int id, begin, end;
    public int[] docIds;

    public TaskPart(int id, int begin, int end, int[] docIds) {
      this.id = id;
      this.begin = begin;
      this.end = end;
      this.docIds = docIds;
    }
  }

  /** Objective function result of a single thread */
  private static class TaskResult {
    public int id;
    public double objective;

    public TaskResult(int id, double objective) {
      this.id = id;
      this.objective = objective;
    }
  }

  private ThreadsafeProcessor<TaskPart, TaskResult> expectedThreadProcessor = new ExpectationThreadsafeProcessor();
  private ThreadsafeProcessor<TaskPart, TaskResult> expectedAndEmpiricalThreadProcessor = new ExpectationThreadsafeProcessorWithEmpirical();

  class ExpectationThreadsafeProcessor implements ThreadsafeProcessor<TaskPart, TaskResult> {
    @Override
    public TaskResult process(TaskPart part) {
      double[][] partE = multiThreadGrad == 1 ? E : clear2D(parallelE[part.id]);
      int begin = part.begin, end = part.end;
      int[] docIds = part.docIds;
      double probSum = 0;
      for (int i = begin; i < end; i++) {
        probSum += expectedCountsAndValueForADoc(partE, docIds[i]);
      }
      return new TaskResult(part.id, probSum);
    }

    @Override
    public ThreadsafeProcessor<TaskPart, TaskResult> newInstance() {
      return this;
    }
  }

  class ExpectationThreadsafeProcessorWithEmpirical implements ThreadsafeProcessor<TaskPart, TaskResult> {
    @Override
    public TaskResult process(TaskPart part) {
      double[][] partE = multiThreadGrad == 1 ? E : clear2D(parallelE[part.id]);
      double[][] partEhat = multiThreadGrad == 1 ? Ehat : clear2D(parallelEhat[part.id]);
      int begin = part.begin, end = part.end;
      int[] docIds = part.docIds;
      double probSum = 0;
      for (int i = begin; i < end; i++) {
        probSum += expectedAndEmpiricalCountsAndValueForADoc(partE, partEhat, docIds[i]);
      }
      return new TaskResult(part.id, probSum);
    }

    @Override
    public ThreadsafeProcessor<TaskPart, TaskResult> newInstance() {
      return this;
    }
  }

  public void setWeights(double[][] weights) {
    this.weights = weights;
    cliquePotentialFunc = new LinearCliquePotentialFunction(weights);
  }


  protected double regularGradientAndValue() {
    return multiThreadGradient(ArrayMath.range(0, data.length), false);
  }

  protected double multiThreadGradient(int[] docIDs, boolean calculateEmpirical) {
    double objective = 0.0;
    if (multiThreadGrad <= 1) {
      return (calculateEmpirical ? expectedAndEmpiricalThreadProcessor : expectedThreadProcessor).process(new TaskPart(0, 0, docIDs.length, docIDs)).objective;
    }
    // TODO: This is a bunch of unnecessary heap traffic, should all be on the stack
    if (parallelE == null) {
      parallelE = new double[multiThreadGrad][][];
      for (int i=0; i<multiThreadGrad; i++) {
        parallelE[i] = empty2D();
      }
    }
    if (calculateEmpirical && parallelEhat == null) {
      parallelEhat = new double[multiThreadGrad][][];
      for (int i=0; i<multiThreadGrad; i++) {
        parallelEhat[i] = empty2D();
      }
    }

    // TODO: this is a huge amount of machinery for no discernible reason
    MulticoreWrapper<TaskPart, TaskResult> wrapper =
            new MulticoreWrapper<>(multiThreadGrad,
                (calculateEmpirical ? expectedAndEmpiricalThreadProcessor : expectedThreadProcessor));

    int totalLen = docIDs.length;
    int partLen = (totalLen + multiThreadGrad - 1) / multiThreadGrad;
    for (int part = 0; part < multiThreadGrad; part++) {
      int currIndex = part * partLen;
      int endIndex = Math.min(currIndex + partLen, totalLen);
      wrapper.put(new TaskPart(part, currIndex, endIndex, docIDs));
    }
    wrapper.join();
    // This all seems fine. May want to start running this after the joins, in case we have different end-times
    while (wrapper.peek()) {
      TaskResult result = wrapper.poll();
      int tID = result.id;
      objective += result.objective;
      combine2DArr(E, parallelE[tID]);
      if (calculateEmpirical) {
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

    // final double[][] weights = to2D(x);
    to2D(x, weights);
    setWeights(weights);

    // the expectations over counts
    // first index is feature index, second index is of possible labeling
    // double[][] E = empty2D();
    clear2D(E);

    double prob = regularGradientAndValue(); // the log prob of the sequence given the model, which is the negation of value at this point

    if (Double.isNaN(prob)) { // shouldn't be the case
      throw new RuntimeException("Got NaN for prob in CRFLogConditionalObjectiveFunction.calculate()" +
              " - this may well indicate numeric underflow due to overly long documents.");
    }

    // because we minimize -L(\theta)
    value = -prob;
    if (VERBOSE) {
      log.info("value is " + Math.exp(-value));
    }

    // compute the partial derivative for each feature by comparing expected counts to empirical counts
    int index = 0;
    for (int i = 0; i < E.length; i++) {
      double[] E_i = E[i], Ehat_i = Ehat[i];
      for (int j = 0; j < E_i.length; j++) {
        // because we minimize -L(\theta)
        derivative[index] = (E_i[j] - Ehat_i[j]);
        if (VERBOSE) {
          log.info("deriv(" + i + "," + j + ") = " + E_i[j] + " - " + Ehat_i[j] + " = " + derivative[index]);
        }
        index++;
      }
    }

    applyPrior(x, 1.0);

    // log.info("\nfuncVal: " + value);
  }

  @Override
  public int dataDimension() {
    return data.length;
  }

  @Override
  public void calculateStochastic(double[] x, double [] v, int[] batch) {
    to2D(x, weights);
    setWeights(weights);

    double batchScale = ((double) batch.length)/((double) this.dataDimension());

    // the expectations over counts
    // first index is feature index, second index is of possible labeling
    // double[][] E = empty2D();

    // iterate over all the documents
    double prob = multiThreadGradient(batch, false);  // the log prob of the sequence given the model, which is the negation of value at this point

    if (Double.isNaN(prob)) { // shouldn't be the case
      throw new RuntimeException("Got NaN for prob in CRFLogConditionalObjectiveFunction.calculate()");
    }

    value = -prob;

    // compute the partial derivative for each feature by comparing expected counts to empirical counts
    int index = 0;
    for (int i = 0; i < E.length; i++) {
      double[] E_i = E[i], Ehat_i = Ehat[i];
      for (int j = 0; j < E_i.length; j++) {
        // real gradient should be empirical-expected;
        // but since we minimize -L(\theta), the gradient is -(empirical-expected)
        derivative[index++] = (E_i[j] - batchScale*Ehat_i[j]);
        if (VERBOSE) {
          log.info("deriv(" + i + "," + j + ") = " + E_i[j] + " - " + Ehat_i[j] + " = " + derivative[index - 1]);
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
    // int[][] wis = getWeightIndices();
    ArrayMath.multiplyInPlace(x, xScale);
    to2D(x, weights);
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
    double prob = multiThreadGradient(batch, true); // the log prob of the sequence given the model, which is the negation of value at this point

    if (Double.isNaN(prob)) { // shouldn't be the case
      throw new RuntimeException("Got NaN for prob in CRFLogConditionalObjectiveFunction.calculate()");
    }

    value = -prob;

    int index = 0;
    for (int i = 0; i < E.length; i++) {
      double[] E_i = E[i], Ehat_i = Ehat[i];
      for (int j = 0; j < E_i.length; j++) {
        x[index++] += (Ehat_i[j] - E_i[j]) * gScale;
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
    multiThreadGradient(batch, true);

    int index = 0;
    for (int i = 0; i < E.length; i++) {
      final double[] Ei = E[i], Ehati = Ehat[i];
      for (int j = 0; j < Ei.length; j++) {
        // real gradient should be empirical-expected;
        // but since we minimize -L(\theta), the gradient is -(empirical-expected)
        derivative[index++] = (Ei[j] - Ehati[j]);
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
    ArrayMath.multiplyInPlace(x, xScale);
    to2D(x, weights);
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
    return featureGrouping != null ? featureGrouping : new int[][] { ArrayMath.range(domainDimension()) };
  }

  public void setFeatureGrouping(int[][] fg) {
    this.featureGrouping = fg;
  }

  protected void applyPrior(double[] x, double batchScale) {
    // incorporate priors
    if (prior == QUADRATIC_PRIOR) {
      double lambda = batchScale / (sigma * sigma);
      for (int i = 0; i < x.length; i++) {
        double w = x[i], wlambda = w * lambda;
        value += w * wlambda * 0.5;
        derivative[i] += wlambda;
      }
    } else if (prior == HUBER_PRIOR) {
      double batchScaleSigmaSq = batchScale / (sigma * sigma);
      for (int i = 0; i < x.length; i++) {
        double w = x[i], wabs = w < 0 ? -w : w;
        if (wabs < epsilon) {
          double weps = batchScaleSigmaSq * w / epsilon;
          value += w * .5 * weps;
          derivative[i] += weps;
        } else {
          value += batchScaleSigmaSq*(wabs - epsilon * .5);
          derivative[i] += w < 0 ? -batchScaleSigmaSq : batchScaleSigmaSq;
        }
      }
    } else if (prior == QUARTIC_PRIOR) {
      double sigmasq = sigma * sigma, batchScaleSigmaQu = batchScale / (sigmasq * sigmasq);
      double lambda = .5 * batchScaleSigmaQu;
      for (int i = 0; i < x.length; i++) {
        double w = x[i], ww = w * w;
        value += ww * ww * lambda;
        derivative[i] += batchScaleSigmaQu * w;
      }
    }
  }


  protected Pair<double[][][], double[][][]> getCondProbs(CRFCliqueTree<String> cTree, int[][][] docData) {
    // first index position is curr index, second index curr-class, third index prev-class
    // e.g. [1][2][3] means curr is at position 1 with class 2, prev is at position 0 with class 3
    double[][][] prevGivenCurr = new double[docData.length][numClasses][numClasses];
    // first index position is curr index, second index curr-class, third index next-class
    // e.g. [0][2][3] means curr is at position 0 with class 2, next is at position 1 with class 3
    double[][][] nextGivenCurr = new double[docData.length][numClasses][numClasses];

    // computing prevGivenCurr and nextGivenCurr
    for (int i=0; i < docData.length; i++) {
      final double[][] prevGivenCurrI = prevGivenCurr[i];
      final double[][] nextGivenCurrIm1 = i > 0 ? nextGivenCurr[i-1] : null;
      int[] labelPair = new int[2];
      for (int l1 = 0; l1 < numClasses; l1++) {
        labelPair[0] = l1;
        for (int l2 = 0; l2 < numClasses; l2++) {
          labelPair[1] = l2;
          double prob = cTree.logProb(i, labelPair);
          // log.info(prob);
          if (i > 0)
            nextGivenCurrIm1[l1][l2] = prob;
          prevGivenCurrI[l2][l1] = prob;
        }
      }

      if (DEBUG2) {
        log.info("unnormalized conditionals:");
        if (i>0) {
          log.info("nextGivenCurr[" + (i-1) + "]:");
          for (int a = 0; a < nextGivenCurrIm1.length; a++) {
            for (int b = 0; b < nextGivenCurrIm1[a].length; b++)
              log.info((nextGivenCurrIm1[a][b])+"\t");
            log.info();
          }
        }
        log.info("prevGivenCurr[" + (i) + "]:");
        for (int a = 0; a < prevGivenCurrI.length; a++) {
          for (int b = 0; b < prevGivenCurrI[a].length; b++)
            log.info((prevGivenCurrI[a][b])+"\t");
          log.info();
        }
      }

      for (int j=0; j< numClasses; j++) {
        if (i > 0) {
          // ArrayMath.normalize(nextGivenCurr[i-1][j]);
          double[] row = nextGivenCurrIm1[j];
          ArrayMath.logNormalize(row);
          ArrayMath.expInPlace(row);
        }
        // ArrayMath.normalize(prevGivenCurr[i][j]);
        double[] row = prevGivenCurrI[j];
        ArrayMath.logNormalize(row);
        ArrayMath.expInPlace(row);
      }

      if (DEBUG2) {
        log.info("normalized conditionals:");
        if (i>0) {
          log.info("nextGivenCurr[" + (i-1) + "]:");
          for (int a = 0; a < nextGivenCurrIm1.length; a++) {
            for (int b = 0; b < nextGivenCurrIm1[a].length; b++)
              log.info((nextGivenCurrIm1[a][b])+"\t");
            log.info();
          }
        }
        log.info("prevGivenCurr[" + (i) + "]:");
        for (int a = 0; a < prevGivenCurrI.length; a++) {
          for (int b = 0; b < prevGivenCurrI[a].length; b++)
            log.info((prevGivenCurrI[a][b])+"\t");
          log.info();
        }
      }
    }

    return new Pair<>(prevGivenCurr, nextGivenCurr);
  }

  protected static void combine2DArr(double[][] combineInto, double[][] toBeCombined, double scale) {
    for (int i = 0; i < toBeCombined.length; i++) {
      double[] row = combineInto[i], srcRow = toBeCombined[i];
      for (int j = 0; j < srcRow.length; j++)
        row[j] += srcRow[j] * scale;
    }
  }

  protected static void combine2DArr(double[][] combineInto, double[][] toBeCombined) {
    for (int i = 0; i < toBeCombined.length; i++) {
      double[] row = combineInto[i], srcRow = toBeCombined[i];
      for (int j = 0; j < srcRow.length; j++)
        row[j] += srcRow[j];
    }
  }

  // TODO(mengqiu) add dimension checks
  protected static void combine2DArr(double[][] combineInto, Map<Integer, double[]> toBeCombined) {
    for (Map.Entry<Integer, double[]> entry: toBeCombined.entrySet()) {
      double[] row = combineInto[entry.getKey()], source = entry.getValue();
      for (int i = 0; i< source.length; i++)
        row[i] += source[i];
    }
  }

  protected static void combine2DArr(double[][] combineInto, Map<Integer, double[]> toBeCombined, double scale) {
    for (Map.Entry<Integer, double[]> entry: toBeCombined.entrySet()) {
      double[] row = combineInto[entry.getKey()], source = entry.getValue();
      for (int i = 0; i< source.length; i++)
        row[i] += source[i] * scale;
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
      System.arraycopy(weights, index, newWeights[i], 0, labelSize);
      index += labelSize;
    }
    return newWeights;
  }

  public double[][] to2D(double[] weights) {
    return to2D(weights, this.labelIndices, this.map);
  }

  public float[][] to2Dfloat(double[] weights) {
    return CRFClassifier.to2D(weights, this.labelIndices, this.map);
  }

  /**
   * Takes a double array of weights and populates a 2D array where:
   *
   * the first element is the mapped index of the clique size (e.g., node-0, edge-1) matching featuresIndex i
   * the second element is the number of output classes for that clique size
   *
   * @return a 2D weight array
   */
  public static void to2D(double[] weights, List<Index<CRFLabel>> labelIndices, int[] map, double[][] newWeights) {
    int index = 0;
    for (int i = 0; i < map.length; i++) {
      int labelSize = labelIndices.get(map[i]).size();
      System.arraycopy(weights, index, newWeights[i], 0, labelSize);
      index += labelSize;
    }
  }

  public void to2D(double[] weights1D, double[][] newWeights) {
    to2D(weights1D, this.labelIndices, this.map, newWeights);
  }

  public static double[][] clear2D(double[][] arr2D) {
    for (int i = 0; i < arr2D.length; i++)
      Arrays.fill(arr2D[i], 0);
    return arr2D;
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
        final int labelSize = labelIndices.get(map[i]).size();
        int[] row = weightIndices[i] = new int[labelSize];
        for (int j = 0; j < labelSize; j++) {
          row[j] = index++;
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
