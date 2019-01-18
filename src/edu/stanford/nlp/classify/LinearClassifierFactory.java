// Stanford Classifier - a multiclass maxent classifier
// LinearClassifierFactory
// Copyright (c) 2003-2016 The Board of Trustees of
// The Leland Stanford Junior University. All Rights Reserved.
//
// This program is free software; you can redistribute it and/or
// modify it under the terms of the GNU General Public License
// as published by the Free Software Foundation; either version 2
// of the License, or (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with this program.  If not, see http://www.gnu.org/licenses/ .
//
// For more information, bug reports, fixes, contact:
//    Christopher Manning
//    Dept of Computer Science, Gates 2A
//    Stanford CA 94305-9020
//    USA
//    Support/Questions: java-nlp-user@lists.stanford.edu
//    Licensing: java-nlp-support@lists.stanford.edu
//    https://nlp.stanford.edu/software/classifier.html

package edu.stanford.nlp.classify;

import java.io.BufferedReader;
import java.util.List;
import java.util.function.DoubleUnaryOperator;
import java.util.function.ToDoubleFunction;

import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.io.RuntimeIOException;
import edu.stanford.nlp.ling.Datum;
import edu.stanford.nlp.math.ArrayMath;
import edu.stanford.nlp.optimization.*;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.stats.Counters;
import edu.stanford.nlp.stats.MultiClassAccuracyStats;
import edu.stanford.nlp.stats.Scorer;
import edu.stanford.nlp.util.*;
import edu.stanford.nlp.util.logging.Redwood;


/**
 * Builds various types of linear classifiers, with functionality for
 * setting objective function, optimization method, and other parameters.
 * Classifiers can be defined with passed constructor arguments or using setter methods.
 * Defaults to Quasi-newton optimization of a {@code LogConditionalObjectiveFunction}.
 * (Merges old classes: CGLinearClassifierFactory, QNLinearClassifierFactory, and MaxEntClassifierFactory.)
 * Note that a bias term is not assumed, and so if you want to learn
 * a bias term you should add an "always-on" feature to your examples.
 *
 * @author Jenny Finkel
 * @author Chris Cox (merged factories, 8/11/04)
 * @author Dan Klein (CGLinearClassifierFactory, MaxEntClassifierFactory)
 * @author Galen Andrew (tuneSigma),
 * @author Marie-Catherine de Marneffe (CV in tuneSigma)
 * @author Sarah Spikes (Templatization, though I don't know what to do with the Minimizer)
 * @author Ramesh Nallapati (nmramesh@cs.stanford.edu) {@link #trainSemiSupGE} methods
 */

public class LinearClassifierFactory<L, F> extends AbstractLinearClassifierFactory<L, F>  {

  private static final long serialVersionUID = 7893768984379107397L;
  private double TOL;
  //public double sigma;
  private int mem = 15;
  private boolean verbose = false;
  //private int prior;
  //private double epsilon = 0.0;
  private LogPrior logPrior;
  //private Minimizer<DiffFunction> minimizer;
  //private boolean useSum = false;
  private boolean tuneSigmaHeldOut = false;
  private boolean tuneSigmaCV = false;
  //private boolean resetWeight = true;
  private int folds;
  // range of values to tune sigma across
  private double min = 0.1;
  private double max = 10.0;
  private boolean retrainFromScratchAfterSigmaTuning = false;

  private Factory<Minimizer<DiffFunction>> minimizerCreator = null;
  private int evalIters = -1;
  private Evaluator[] evaluators; // = null;

  /** A logger for this class */
  private static final Redwood.RedwoodChannels logger = Redwood.channels(LinearClassifierFactory.class);

  /** This is the {@code Factory<Minimizer<DiffFunction>>} that we use over and over again. */
  private class QNFactory implements Factory<Minimizer<DiffFunction>> {

    private static final long serialVersionUID = 9028306475652690036L;

    @Override
    public Minimizer<DiffFunction> create() {
      QNMinimizer qnMinimizer = new QNMinimizer(LinearClassifierFactory.this.mem);
      if (! verbose) {
        qnMinimizer.shutUp();
      }
      return qnMinimizer;
    }

  } // end class QNFactory


  public LinearClassifierFactory() {
    this((Factory<Minimizer<DiffFunction>>) null);
  }

  /** NOTE: Constructors that take in a Minimizer create a LinearClassifierFactory that will reuse the minimizer
   *  and will not be threadsafe (unless the Minimizer itself is ThreadSafe, which is probably not the case).
   */
  public LinearClassifierFactory(Minimizer<DiffFunction> min) {
    this(min, 1e-4, false);
  }

  public LinearClassifierFactory(Factory<Minimizer<DiffFunction>> min) {
    this(min, 1e-4, false);
  }

  public LinearClassifierFactory(Minimizer<DiffFunction> min, double tol, boolean useSum) {
    this(min, tol, useSum, 1.0);
  }

  public LinearClassifierFactory(Factory<Minimizer<DiffFunction>> min, double tol, boolean useSum) {
    this(min, tol, useSum, 1.0);
  }

  public LinearClassifierFactory(double tol, boolean useSum, double sigma) {
    this((Factory<Minimizer<DiffFunction>>) null, tol, useSum, sigma);
  }

  public LinearClassifierFactory(Minimizer<DiffFunction> min, double tol, boolean useSum, double sigma) {
    this(min, tol, useSum, LogPrior.LogPriorType.QUADRATIC.ordinal(), sigma);
  }

  public LinearClassifierFactory(Factory<Minimizer<DiffFunction>> min, double tol, boolean useSum, double sigma) {
    this(min, tol, useSum, LogPrior.LogPriorType.QUADRATIC.ordinal(), sigma);
  }

  public LinearClassifierFactory(Minimizer<DiffFunction> min, double tol, boolean useSum, int prior, double sigma) {
    this(min, tol, useSum, prior, sigma, 0.0);
  }

  public LinearClassifierFactory(Factory<Minimizer<DiffFunction>> min, double tol, boolean useSum, int prior, double sigma) {
    this(min, tol, useSum, prior, sigma, 0.0);
  }

  public LinearClassifierFactory(double tol, boolean useSum, int prior, double sigma, double epsilon) {
    this((Factory<Minimizer<DiffFunction>>) null, tol, useSum, new LogPrior(prior, sigma, epsilon));
  }

  public LinearClassifierFactory(double tol, boolean useSum, int prior, double sigma, double epsilon, final int mem) {
    this((Factory<Minimizer<DiffFunction>>) null, tol, useSum, new LogPrior(prior, sigma, epsilon));
    this.mem = mem;
  }

  /**
   * Create a factory that builds linear classifiers from training data.
   *
   * @param min     The method to be used for optimization (minimization) (default: {@link QNMinimizer})
   * @param tol     The convergence threshold for the minimization (default: 1e-4)
   * @param useSum  Asks to the optimizer to minimize the sum of the
   *                likelihoods of individual data items rather than their product (default: false)
   *                NOTE: this is currently ignored!!!
   * @param prior   What kind of prior to use, as an enum constant from class
   *                LogPrior
   * @param sigma   The strength of the prior (smaller is stronger for most
   *                standard priors) (default: 1.0)
   * @param epsilon A second parameter to the prior (currently only used
   *                by the Huber prior)
   */
  public LinearClassifierFactory(Minimizer<DiffFunction> min, double tol, boolean useSum, int prior, double sigma, double epsilon) {
    this(min, tol, useSum, new LogPrior(prior, sigma, epsilon));
  }

  public LinearClassifierFactory(Factory<Minimizer<DiffFunction>> min, double tol, boolean useSum, int prior, double sigma, double epsilon) {
    this(min, tol, useSum, new LogPrior(prior, sigma, epsilon));
  }

  public LinearClassifierFactory(final Minimizer<DiffFunction> min, double tol, boolean useSum, LogPrior logPrior) {
    this.minimizerCreator = new Factory<Minimizer<DiffFunction>>() {
      private static final long serialVersionUID = -6439748445540743949L;

      @Override
      public Minimizer<DiffFunction> create() {
        return min;
      }
    };
    this.TOL = tol;
    //this.useSum = useSum;
    this.logPrior = logPrior;
  }

  /**
   * Create a factory that builds linear classifiers from training data. This is the recommended constructor to
   * bottom out with. Use of a minimizerCreator makes the classifier threadsafe.
   *
   * @param minimizerCreator A Factory for creating minimizers. If this is null, a standard quasi-Newton minimizer
   *                         factory will be used.
   * @param tol     The convergence threshold for the minimization (default: 1e-4)
   * @param useSum  Asks to the optimizer to minimize the sum of the
   *                likelihoods of individual data items rather than their product (Klein and Manning 2001 WSD.)
   *                NOTE: this is currently ignored!!! At some point support for this option was deleted
   * @param logPrior What kind of prior to use, this class specifies its type and hyperparameters.
   */
  public LinearClassifierFactory(Factory<Minimizer<DiffFunction>> minimizerCreator, double tol, boolean useSum, LogPrior logPrior) {
    if (minimizerCreator == null) {
      this.minimizerCreator = new QNFactory();
    } else {
      this.minimizerCreator = minimizerCreator;
    }
    this.TOL = tol;
    //this.useSum = useSum;
    this.logPrior = logPrior;
  }

  /**
   * Set the tolerance.  1e-4 is the default.
   */
  public void setTol(double tol) {
    this.TOL = tol;
  }

  /**
   * Set the prior.
   *
   * @param logPrior One of the priors defined in
   *              {@code LogConditionalObjectiveFunction}.
   *              {@code LogPrior.QUADRATIC} is the default.
   */
  public void setPrior(LogPrior logPrior) {
    this.logPrior = logPrior;
  }

  /**
   * Set the verbose flag for {@link CGMinimizer}.
   * {@code false} is the default.
   */
  public void setVerbose(boolean verbose) {
    this.verbose = verbose;
  }

  /**
   * Sets the minimizer.  {@link QNMinimizer} is the default.
   */
  public void setMinimizerCreator(Factory<Minimizer<DiffFunction>> minimizerCreator) {
    this.minimizerCreator = minimizerCreator;
  }

  /**
   * Sets the epsilon value for {@link LogConditionalObjectiveFunction}.
   */
  public void setEpsilon(double eps) {
    logPrior.setEpsilon(eps);
  }

  public void setSigma(double sigma) {
    logPrior.setSigma(sigma);
  }

  public double getSigma() {
    return logPrior.getSigma();
  }

  /**
   * Sets the minimizer to QuasiNewton. {@link QNMinimizer} is the default.
   */
  public void useQuasiNewton() {
    this.minimizerCreator = new QNFactory();
  }

  public void useQuasiNewton(final boolean useRobust) {
    this.minimizerCreator = new Factory<Minimizer<DiffFunction>>() {
      private static final long serialVersionUID = -9108222058357693242L;
      @Override
      public Minimizer<DiffFunction> create() {
          QNMinimizer qnMinimizer = new QNMinimizer(LinearClassifierFactory.this.mem, useRobust);
          if (!verbose) {
              qnMinimizer.shutUp();
          }
          return qnMinimizer;
      }
    };
  }

  public void useStochasticQN(final double initialSMDGain, final int stochasticBatchSize){
    this.minimizerCreator = new Factory<Minimizer<DiffFunction>>() {
      private static final long serialVersionUID = -7760753348350678588L;
      @Override
      public Minimizer<DiffFunction> create() {
          SQNMinimizer<DiffFunction> sqnMinimizer = new SQNMinimizer<>(LinearClassifierFactory.this.mem, initialSMDGain, stochasticBatchSize, false);
          if (!verbose) {
              sqnMinimizer.shutUp();
          }
          return sqnMinimizer;
      }
    };
  }

  public void useStochasticMetaDescent(){
    useStochasticMetaDescent(0.1, 15, StochasticCalculateMethods.ExternalFiniteDifference, 20);
  }

  public void useStochasticMetaDescent(final double initialSMDGain, final int stochasticBatchSize,
                                       final StochasticCalculateMethods stochasticMethod,final int passes) {
    this.minimizerCreator = new Factory<Minimizer<DiffFunction>>() {
      private static final long serialVersionUID = 6860437108371914482L;
      @Override
      public Minimizer<DiffFunction> create() {
          SMDMinimizer<DiffFunction> smdMinimizer = new SMDMinimizer<>(initialSMDGain, stochasticBatchSize, stochasticMethod, passes);
          if (!verbose) {
              smdMinimizer.shutUp();
          }
          return smdMinimizer;
      }
    };
  }

  public void useStochasticGradientDescent(){
    useStochasticGradientDescent(0.1,15);
  }

  public void useStochasticGradientDescent(final double gainSGD, final int stochasticBatchSize){
    this.minimizerCreator = new Factory<Minimizer<DiffFunction>>() {
      private static final long serialVersionUID = 2564615420955196299L;
      @Override
      public Minimizer<DiffFunction> create() {
          InefficientSGDMinimizer<DiffFunction> sgdMinimizer = new InefficientSGDMinimizer<>(gainSGD, stochasticBatchSize);
          if (!verbose) {
              sgdMinimizer.shutUp();
          }
          return sgdMinimizer;
      }
    };
  }

  public void useInPlaceStochasticGradientDescent() {
    useInPlaceStochasticGradientDescent(-1, -1, 1.0);
  }

  public void useInPlaceStochasticGradientDescent(final int SGDPasses, final int tuneSampleSize, final double sigma) {
    this.minimizerCreator = new Factory<Minimizer<DiffFunction>>() {
      private static final long serialVersionUID = -5319225231759162616L;
      @Override
      public Minimizer<DiffFunction> create() {
          SGDMinimizer<DiffFunction> sgdMinimizer = new SGDMinimizer<>(sigma, SGDPasses, tuneSampleSize);
          if (!verbose) {
              sgdMinimizer.shutUp();
          }
          return sgdMinimizer;
      }
    };
  }

  public void useHybridMinimizerWithInPlaceSGD(final int SGDPasses, final int tuneSampleSize, final double sigma) {
    this.minimizerCreator = new Factory<Minimizer<DiffFunction>>() {
      private static final long serialVersionUID = -3042400543337763144L;
      @Override
      public Minimizer<DiffFunction> create() {
          SGDMinimizer<DiffFunction> firstMinimizer = new SGDMinimizer<>(sigma, SGDPasses, tuneSampleSize);
          QNMinimizer secondMinimizer = new QNMinimizer(mem);
          if (!verbose) {
              firstMinimizer.shutUp();
              secondMinimizer.shutUp();
          }
          return new HybridMinimizer(firstMinimizer, secondMinimizer, SGDPasses);
      }
    };
  }

  public void useStochasticGradientDescentToQuasiNewton(final double SGDGain, final int batchSize, final int sgdPasses,
                                                        final int qnPasses, final int hessSamples, final int QNMem,
                                                        final boolean outputToFile) {
    this.minimizerCreator = new Factory<Minimizer<DiffFunction>>() {
      private static final long serialVersionUID = 5823852936137599566L;
      @Override
      public Minimizer<DiffFunction> create() {
          SGDToQNMinimizer sgdToQNMinimizer = new SGDToQNMinimizer(SGDGain, batchSize, sgdPasses,
                  qnPasses, hessSamples, QNMem, outputToFile);
          if (!verbose) {
              sgdToQNMinimizer.shutUp();
          }
          return sgdToQNMinimizer;
      }
    };
  }

  public void useHybridMinimizer() {
    useHybridMinimizer(0.1, 15, StochasticCalculateMethods.ExternalFiniteDifference, 0);
  }

  public void useHybridMinimizer(final double initialSMDGain, final int stochasticBatchSize,
                                 final StochasticCalculateMethods stochasticMethod, final int cutoffIteration){
    this.minimizerCreator = () -> {
        SMDMinimizer<DiffFunction> firstMinimizer = new SMDMinimizer<>(initialSMDGain, stochasticBatchSize, stochasticMethod, cutoffIteration);
        QNMinimizer secondMinimizer = new QNMinimizer(mem);
        if (!verbose) {
            firstMinimizer.shutUp();
            secondMinimizer.shutUp();
        }
        return new HybridMinimizer(firstMinimizer, secondMinimizer, cutoffIteration);
    };
  }

  /**
   * Set the mem value for {@link QNMinimizer}.
   * Only used with quasi-newton minimization.  15 is the default.
   *
   * @param mem Number of previous function/derivative evaluations to store
   *            to estimate second derivative.  Storing more previous evaluations
   *            improves training convergence speed.  This number can be very
   *            small, if memory conservation is the priority.  For large
   *            optimization systems (of 100,000-1,000,000 dimensions), setting this
   *            to 15 produces quite good results, but setting it to 50 can
   *            decrease the iteration count by about 20% over a value of 15.
   */
  public void setMem(int mem) {
    this.mem = mem;
  }

  /**
   * Sets the minimizer to {@link CGMinimizer}, with the passed {@code verbose} flag.
   */
  public void useConjugateGradientAscent(boolean verbose) {
    this.verbose = verbose;
    useConjugateGradientAscent();
  }

  /**
   * Sets the minimizer to {@link CGMinimizer}.
   */
  public void useConjugateGradientAscent() {
    this.minimizerCreator = new Factory<Minimizer<DiffFunction>>() {
      private static final long serialVersionUID = -561168861131879990L;

      @Override
      public Minimizer<DiffFunction> create() {
        return new CGMinimizer(!LinearClassifierFactory.this.verbose);
      }
    };
  }

  /**
   * NOTE: nothing is actually done with this value!
   *
   * SetUseSum sets the {@code useSum} flag: when turned on,
   * the Summed Conditional Objective Function is used.  Otherwise, the
   * LogConditionalObjectiveFunction is used.  The default is false.
   */
  public void setUseSum(boolean useSum) {
    //this.useSum = useSum;
  }


  private Minimizer<DiffFunction> getMinimizer() {
    // Create a new minimizer
    Minimizer<DiffFunction> minimizer = minimizerCreator.create();
    if (minimizer instanceof HasEvaluators) {
      ((HasEvaluators) minimizer).setEvaluators(evalIters, evaluators);
    }
    return minimizer;
  }


  /**
   * Adapt classifier (adjust the mean of Gaussian prior).
   * Under construction -pichuan
   *
   * @param origWeights the original weights trained from the training data
   * @param adaptDataset the Dataset used to adapt the trained weights
   * @return adapted weights
   */
  public double[][] adaptWeights(double[][] origWeights, GeneralDataset<L, F> adaptDataset) {
    Minimizer<DiffFunction> minimizer = getMinimizer();
    logger.info("adaptWeights in LinearClassifierFactory. increase weight dim only");
    double[][] newWeights = new double[adaptDataset.featureIndex.size()][adaptDataset.labelIndex.size()];

    synchronized (System.class) {
      System.arraycopy(origWeights, 0, newWeights, 0, origWeights.length);
    }

    AdaptedGaussianPriorObjectiveFunction<L, F> objective = new AdaptedGaussianPriorObjectiveFunction<>(adaptDataset, logPrior, newWeights);

    double[] initial = objective.initial();

    double[] weights = minimizer.minimize(objective, TOL, initial);
    return objective.to2D(weights);

    //Question: maybe the adaptWeights can be done just in LinearClassifier ?? (pichuan)
  }

  @Override
  public double[][] trainWeights(GeneralDataset<L, F> dataset) {
    return trainWeights(dataset, null);
  }

  public double[][] trainWeights(GeneralDataset<L, F> dataset, double[] initial) {
    return trainWeights(dataset, initial, false);
  }

  public double[][] trainWeights(GeneralDataset<L, F> dataset, double[] initial, boolean bypassTuneSigma) {
    Minimizer<DiffFunction> minimizer = getMinimizer();
    if(dataset instanceof RVFDataset)
      ((RVFDataset<L,F>)dataset).ensureRealValues();
    double[] interimWeights = null;
    if(! bypassTuneSigma) {
      if (tuneSigmaHeldOut) {
        interimWeights = heldOutSetSigma(dataset); // the optimum interim weights from held-out training data have already been found.
      } else if (tuneSigmaCV) {
        crossValidateSetSigma(dataset,folds); // TODO: assign optimum interim weights as part of this process.
      }
    }
    LogConditionalObjectiveFunction<L, F> objective = new LogConditionalObjectiveFunction<>(dataset, logPrior);
    if(initial == null && interimWeights != null && ! retrainFromScratchAfterSigmaTuning) {
      //logger.info("## taking advantage of interim weights as starting point.");
      initial = interimWeights;
    }
    if (initial == null) {
      initial = objective.initial();
    }

    double[] weights = minimizer.minimize(objective, TOL, initial);
    return objective.to2D(weights);
  }

  /**
   * IMPORTANT: dataset and biasedDataset must have same featureIndex, labelIndex
   */
  public Classifier<L, F> trainClassifierSemiSup(GeneralDataset<L, F> data, GeneralDataset<L, F> biasedData, double[][] confusionMatrix, double[] initial) {
    double[][] weights =  trainWeightsSemiSup(data, biasedData, confusionMatrix, initial);
    LinearClassifier<L, F> classifier = new LinearClassifier<>(weights, data.featureIndex(), data.labelIndex());
    return classifier;
  }

  public double[][] trainWeightsSemiSup(GeneralDataset<L, F> data, GeneralDataset<L, F> biasedData, double[][] confusionMatrix, double[] initial) {
    Minimizer<DiffFunction> minimizer = getMinimizer();
    LogConditionalObjectiveFunction<L, F> objective = new LogConditionalObjectiveFunction<>(data, new LogPrior(LogPrior.LogPriorType.NULL));
    BiasedLogConditionalObjectiveFunction biasedObjective = new BiasedLogConditionalObjectiveFunction(biasedData, confusionMatrix, new LogPrior(LogPrior.LogPriorType.NULL));
    SemiSupervisedLogConditionalObjectiveFunction semiSupObjective = new SemiSupervisedLogConditionalObjectiveFunction(objective, biasedObjective, logPrior);
    if (initial == null) {
      initial = objective.initial();
    }
    double[] weights = minimizer.minimize(semiSupObjective, TOL, initial);
    return objective.to2D(weights);
  }

  /**
   * Trains the linear classifier using Generalized Expectation criteria as described in
   * <tt>Generalized Expectation Criteria for Semi Supervised Learning of Conditional Random Fields</tt>, Mann and McCallum, ACL 2008.
   * The original algorithm is proposed for CRFs but has been adopted to LinearClassifier (which is a simpler special case of a CRF).
   * IMPORTANT: the labeled features that are passed as an argument are assumed to be binary valued, although
   * other features are allowed to be real valued.
   */
  public LinearClassifier<L,F> trainSemiSupGE(GeneralDataset<L, F> labeledDataset, List<? extends Datum<L, F>> unlabeledDataList, List<F> GEFeatures, double convexComboCoeff) {
    Minimizer<DiffFunction> minimizer = getMinimizer();
    LogConditionalObjectiveFunction<L, F> objective = new LogConditionalObjectiveFunction<>(labeledDataset, new LogPrior(LogPrior.LogPriorType.NULL));
    GeneralizedExpectationObjectiveFunction<L,F> geObjective = new GeneralizedExpectationObjectiveFunction<>(labeledDataset, unlabeledDataList, GEFeatures);
    SemiSupervisedLogConditionalObjectiveFunction semiSupObjective = new SemiSupervisedLogConditionalObjectiveFunction(objective, geObjective, null,convexComboCoeff);
    double[] initial = objective.initial();
    double[] weights = minimizer.minimize(semiSupObjective, TOL, initial);
    return new LinearClassifier<>(objective.to2D(weights), labeledDataset.featureIndex(), labeledDataset.labelIndex());
  }


  /**
   * Trains the linear classifier using Generalized Expectation criteria as described in
   * <tt>Generalized Expectation Criteria for Semi Supervised Learning of Conditional Random Fields</tt>, Mann and McCallum, ACL 2008.
   * The original algorithm is proposed for CRFs but has been adopted to LinearClassifier (which is a simpler, special case of a CRF).
   * Automatically discovers high precision, high frequency labeled features to be used as GE constraints.
   * IMPORTANT: the current feature selector assumes the features are binary. The GE constraints assume the constraining features are binary anyway, although
   * it doesn't make such assumptions about other features.
   */
  public LinearClassifier<L,F> trainSemiSupGE(GeneralDataset<L, F> labeledDataset, List<? extends Datum<L, F>> unlabeledDataList) {
    List<F> GEFeatures = getHighPrecisionFeatures(labeledDataset,0.9,10);
    return trainSemiSupGE(labeledDataset, unlabeledDataList, GEFeatures, 0.5);
  }

  public LinearClassifier<L,F> trainSemiSupGE(GeneralDataset<L, F> labeledDataset, List<? extends Datum<L, F>> unlabeledDataList, double convexComboCoeff) {
    List<F> GEFeatures = getHighPrecisionFeatures(labeledDataset,0.9,10);
    return trainSemiSupGE(labeledDataset, unlabeledDataList, GEFeatures, convexComboCoeff);
  }


  /**
   * Returns a list of featured thresholded by minPrecision and sorted by their frequency of occurrence.
   * precision in this case, is defined as the frequency of majority label over total frequency for that feature.
   *
   * @return list of high precision features.
   */
  private List<F> getHighPrecisionFeatures(GeneralDataset<L,F> dataset, double minPrecision, int maxNumFeatures){
    int[][] feature2label = new int[dataset.numFeatures()][dataset.numClasses()];
    // shouldn't be necessary as Java zero fills arrays
    // for(int f = 0; f < dataset.numFeatures(); f++)
    //   Arrays.fill(feature2label[f],0);

    int[][] data = dataset.data;
    int[] labels = dataset.labels;
    for(int d = 0; d < data.length; d++){
      int label = labels[d];
      //System.out.println("datum id:"+d+" label id: "+label);
      if(data[d] != null){
        //System.out.println(" number of features:"+data[d].length);
        for(int n = 0; n < data[d].length; n++){
          feature2label[data[d][n]][label]++;
        }
      }
    }
    Counter<F> feature2freq = new ClassicCounter<>();
    for(int f = 0; f < dataset.numFeatures(); f++){
     int maxF = ArrayMath.max(feature2label[f]);
     int total = ArrayMath.sum(feature2label[f]);
     double precision = ((double)maxF)/total;
     F feature = dataset.featureIndex.get(f);
     if(precision >= minPrecision){
       feature2freq.incrementCount(feature, total);
     }
    }
    if(feature2freq.size() > maxNumFeatures){
      Counters.retainTop(feature2freq, maxNumFeatures);
    }
    //for(F feature : feature2freq.keySet())
      //System.out.println(feature+" "+feature2freq.getCount(feature));
    //System.exit(0);
    return Counters.toSortedList(feature2freq);
  }

  /**
   * Train a classifier with a sigma tuned on a validation set.
   *
   * @return The constructed classifier
   */
  public LinearClassifier<L, F> trainClassifierV(GeneralDataset<L, F> train, GeneralDataset<L, F> validation, double min, double max, boolean accuracy) {
    labelIndex = train.labelIndex();
    featureIndex = train.featureIndex();
    this.min = min;
    this.max = max;
    heldOutSetSigma(train, validation);
    double[][] weights = trainWeights(train);
    return new LinearClassifier<>(weights, train.featureIndex(), train.labelIndex());
  }

  /**
   * Train a classifier with a sigma tuned on a validation set.
   * In this case we are fitting on the last 30% of the training data.
   *
   * @param train The data to train (and validate) on.
   * @return The constructed classifier
   */
  public LinearClassifier<L, F> trainClassifierV(GeneralDataset<L, F> train, double min, double max, boolean accuracy) {
    labelIndex = train.labelIndex();
    featureIndex = train.featureIndex();
    tuneSigmaHeldOut = true;
    this.min = min;
    this.max = max;
    heldOutSetSigma(train);
    double[][] weights = trainWeights(train);
    return new LinearClassifier<>(weights, train.featureIndex(), train.labelIndex());
  }

  /**
   * setTuneSigmaHeldOut sets the {@code tuneSigmaHeldOut} flag: when turned on,
   * the sigma is tuned by means of held-out (70%-30%). Otherwise no tuning on sigma is done.
   * The default is false.
   */
  public void setTuneSigmaHeldOut() {
    tuneSigmaHeldOut = true;
    tuneSigmaCV = false;
  }

  /**
   * setTuneSigmaCV sets the {@code tuneSigmaCV} flag: when turned on,
   * the sigma is tuned by cross-validation. The number of folds is the parameter.
   * If there is less data than the number of folds, leave-one-out is used.
   * The default is false.
   */
  public void setTuneSigmaCV(int folds) {
    tuneSigmaCV = true;
    tuneSigmaHeldOut = false;
    this.folds = folds;
  }

  /**
   * NOTE: Nothing is actually done with this value.
   *
   * resetWeight sets the {@code restWeight} flag. This flag makes sense only if sigma is tuned:
   * when turned on, the weights output by the tuneSigma method will be reset to zero when training the
   * classifier.
   * The default is false.
   */
  public void resetWeight() {
    //resetWeight = true;
  }

  protected static final double[] sigmasToTry = {0.5,1.0,2.0,4.0,10.0, 20.0, 100.0};

  /**
   * Calls the method {@link #crossValidateSetSigma(GeneralDataset, int)} with 5-fold cross-validation.
   * @param dataset the data set to optimize sigma on.
   */
  public void crossValidateSetSigma(GeneralDataset<L, F> dataset) {
    crossValidateSetSigma(dataset, 5);
  }

  /**
   * Calls the method {@link #crossValidateSetSigma(GeneralDataset, int, Scorer, LineSearcher)} with
   * multi-class log-likelihood scoring (see {@link MultiClassAccuracyStats}) and golden-section line search
   * (see {@link GoldenSectionLineSearch}).
   *
   * @param dataset the data set to optimize sigma on.
   */
  public void crossValidateSetSigma(GeneralDataset<L, F> dataset,int kfold) {
    logger.info("##you are here.");
    crossValidateSetSigma(dataset, kfold, new MultiClassAccuracyStats<>(MultiClassAccuracyStats.USE_LOGLIKELIHOOD), new GoldenSectionLineSearch(true, 1e-2, min, max));
  }

  public void crossValidateSetSigma(GeneralDataset<L, F> dataset,int kfold, final Scorer<L> scorer) {
    crossValidateSetSigma(dataset, kfold, scorer, new GoldenSectionLineSearch(true, 1e-2, min, max));
  }
  public void crossValidateSetSigma(GeneralDataset<L, F> dataset,int kfold, LineSearcher minimizer) {
    crossValidateSetSigma(dataset, kfold, new MultiClassAccuracyStats<>(MultiClassAccuracyStats.USE_LOGLIKELIHOOD), minimizer);
  }
  /**
   * Sets the sigma parameter to a value that optimizes the cross-validation score given by {@code scorer}.  Search for an optimal value
   * is carried out by {@code minimizer}.
   *
   * @param dataset the data set to optimize sigma on.
   */
  public void crossValidateSetSigma(GeneralDataset<L, F> dataset,int kfold, final Scorer<L> scorer, LineSearcher minimizer) {
    logger.info("##in Cross Validate, folds = " + kfold);
    logger.info("##Scorer is " + scorer);

    featureIndex = dataset.featureIndex;
    labelIndex = dataset.labelIndex;

    final CrossValidator<L, F> crossValidator = new CrossValidator<>(dataset, kfold);
    final ToDoubleFunction<Triple<GeneralDataset<L, F>,GeneralDataset<L, F>,CrossValidator.SavedState>> scoreFn =
        fold -> {
          GeneralDataset<L, F> trainSet = fold.first();
          GeneralDataset<L, F> devSet   = fold.second();

          double[] weights = (double[])fold.third().state;
          double[][] weights2D;

          weights2D = trainWeights(trainSet, weights,true); // must of course bypass sigma tuning here.

          fold.third().state = ArrayUtils.flatten(weights2D);

          LinearClassifier<L, F> classifier = new LinearClassifier<>(weights2D, trainSet.featureIndex, trainSet.labelIndex);

          double score = scorer.score(classifier, devSet);
          //System.out.println("score: "+score);
          System.out.print(".");
          return score;
        };

    DoubleUnaryOperator negativeScorer =
        sigmaToTry -> {
          //sigma = sigmaToTry;
          setSigma(sigmaToTry);
          Double averageScore = crossValidator.computeAverage(scoreFn);
          logger.info("##sigma = "+getSigma() + " -> average Score: " + averageScore);
          return -averageScore;
        };

    double bestSigma = minimizer.minimize(negativeScorer);
    logger.info("##best sigma: " + bestSigma);
    setSigma(bestSigma);
  }

  /**
   * Set the {@link LineSearcher} to be used in {@link #heldOutSetSigma(GeneralDataset, GeneralDataset)}.
   */
  public void setHeldOutSearcher(LineSearcher heldOutSearcher) {
    this.heldOutSearcher = heldOutSearcher;
  }

  private LineSearcher heldOutSearcher; // = null;

  public double[] heldOutSetSigma(GeneralDataset<L, F> train) {
    Pair<GeneralDataset<L, F>, GeneralDataset<L, F>> data = train.split(0.3);
    return heldOutSetSigma(data.first(), data.second());
  }

  public double[] heldOutSetSigma(GeneralDataset<L, F> train, Scorer<L> scorer) {
    Pair<GeneralDataset<L, F>, GeneralDataset<L, F>> data = train.split(0.3);
    return heldOutSetSigma(data.first(), data.second(), scorer);
  }

  public double[] heldOutSetSigma(GeneralDataset<L, F> train, GeneralDataset<L, F> dev) {
    return heldOutSetSigma(train, dev, new MultiClassAccuracyStats<>(MultiClassAccuracyStats.USE_LOGLIKELIHOOD), heldOutSearcher == null ? new GoldenSectionLineSearch(true, 1e-2, min, max) : heldOutSearcher);
  }

  public double[] heldOutSetSigma(GeneralDataset<L, F> train, GeneralDataset<L, F> dev, final Scorer<L> scorer) {
    return heldOutSetSigma(train, dev, scorer, new GoldenSectionLineSearch(true, 1e-2, min, max));
  }

  public double[] heldOutSetSigma(GeneralDataset<L, F> train, GeneralDataset<L, F> dev, LineSearcher minimizer) {
    return heldOutSetSigma(train, dev, new MultiClassAccuracyStats<>(MultiClassAccuracyStats.USE_LOGLIKELIHOOD), minimizer);
  }

  /**
   * Sets the sigma parameter to a value that optimizes the held-out score given by {@code scorer}.  Search for an
   * optimal value is carried out by {@code minimizer} dataset the data set to optimize sigma on. kfold
   *
   * @return an interim set of optimal weights: the weights
   */
  public double[] heldOutSetSigma(final GeneralDataset<L, F> trainSet, final GeneralDataset<L, F> devSet, final Scorer<L> scorer, LineSearcher minimizer) {

    featureIndex = trainSet.featureIndex;
    labelIndex = trainSet.labelIndex;
    //double[] resultWeights = null;
    Timing timer = new Timing();

    NegativeScorer negativeScorer = new NegativeScorer(trainSet,devSet,scorer,timer);

    timer.start();
    double bestSigma = minimizer.minimize(negativeScorer);
    logger.info("##best sigma: " + bestSigma);
    setSigma(bestSigma);

    return ArrayUtils.flatten(trainWeights(trainSet,negativeScorer.weights,true)); // make sure it's actually the interim weights from best sigma
  }

  class NegativeScorer implements DoubleUnaryOperator {
    public double[] weights; // = null;
    GeneralDataset<L, F> trainSet;
    GeneralDataset<L, F> devSet;
    Scorer<L> scorer;
    Timing timer;

    public NegativeScorer(GeneralDataset<L, F> trainSet, GeneralDataset<L, F> devSet, Scorer<L> scorer,Timing timer) {
      super();
      this.trainSet = trainSet;
      this.devSet = devSet;
      this.scorer = scorer;
      this.timer = timer;
    }

    @Override
    public double applyAsDouble(double sigmaToTry) {
      double[][] weights2D;
      setSigma(sigmaToTry);

      weights2D = trainWeights(trainSet, weights,true); //bypass.

      weights = ArrayUtils.flatten(weights2D);

      LinearClassifier<L, F> classifier = new LinearClassifier<>(weights2D, trainSet.featureIndex, trainSet.labelIndex);

      double score = scorer.score(classifier, devSet);
      //System.out.println("score: "+score);
      //System.out.print(".");
      logger.info("##sigma = " + getSigma() + " -> average Score: " + score);
      logger.info("##time elapsed: " + timer.stop() + " milliseconds.");
      timer.restart();
      return -score;
    }
  }

  /** If set to true, then when training a classifier, after an optimal sigma is chosen a model is relearned from
   * scratch. If set to false (the default), then the model is updated from wherever it wound up in the sigma-tuning process.
   * The latter is likely to be faster, but it's not clear which model will wind up better.  */
  public void setRetrainFromScratchAfterSigmaTuning( boolean retrainFromScratchAfterSigmaTuning) {
    this.retrainFromScratchAfterSigmaTuning = retrainFromScratchAfterSigmaTuning;
  }


  public Classifier<L, F> trainClassifier(Iterable<Datum<L, F>> dataIterable) {
    Minimizer<DiffFunction> minimizer = getMinimizer();
    Index<F> featureIndex = Generics.newIndex();
    Index<L> labelIndex = Generics.newIndex();
    for (Datum<L, F> d : dataIterable) {
      labelIndex.add(d.label());
      featureIndex.addAll(d.asFeatures());//If there are duplicates, it doesn't add them again.
    }
    logger.info(String.format("Training linear classifier with %d features and %d labels", featureIndex.size(), labelIndex.size()));

    LogConditionalObjectiveFunction<L, F> objective = new LogConditionalObjectiveFunction<>(dataIterable, logPrior, featureIndex, labelIndex);
    // [cdm 2014] Commented out next line. Why not use the logPrior set up previously and used at creation???
    // objective.setPrior(new LogPrior(LogPrior.LogPriorType.QUADRATIC));

    double[] initial = objective.initial();
    double[] weights = minimizer.minimize(objective, TOL, initial);

    LinearClassifier<L, F> classifier = new LinearClassifier<>(objective.to2D(weights), featureIndex, labelIndex);
    return classifier;
  }

  public Classifier<L, F> trainClassifier(GeneralDataset<L, F> dataset, float[] dataWeights, LogPrior prior) {
    Minimizer<DiffFunction> minimizer = getMinimizer();
    if (dataset instanceof RVFDataset) {
      ((RVFDataset<L,F>)dataset).ensureRealValues();
    }
    LogConditionalObjectiveFunction<L, F> objective = new LogConditionalObjectiveFunction<>(dataset, dataWeights, prior);

    double[] initial = objective.initial();
    double[] weights = minimizer.minimize(objective, TOL, initial);

    LinearClassifier<L, F> classifier = new LinearClassifier<>(objective.to2D(weights), dataset.featureIndex(), dataset.labelIndex());
    return classifier;
  }


  @Override
  public LinearClassifier<L, F> trainClassifier(GeneralDataset<L, F> dataset) {
    return trainClassifier(dataset, null);
  }

  public LinearClassifier<L, F> trainClassifier(GeneralDataset<L, F> dataset, double[] initial) {
    // Sanity check
    if (dataset instanceof RVFDataset) {
      ((RVFDataset<L, F>) dataset).ensureRealValues();
    }
    if (initial != null) {
      for (double weight : initial) {
        if (Double.isNaN(weight) || Double.isInfinite(weight)) {
          throw new IllegalArgumentException("Initial weights are invalid!");
        }
      }
    }
    // Train classifier
    double[][] weights =  trainWeights(dataset, initial, false);
    LinearClassifier<L, F> classifier = new LinearClassifier<>(weights, dataset.featureIndex(), dataset.labelIndex());
    return classifier;
  }

  public LinearClassifier<L, F> trainClassifierWithInitialWeights(GeneralDataset<L, F> dataset, double[][] initialWeights2D) {
    double[] initialWeights = (initialWeights2D != null)? ArrayUtils.flatten(initialWeights2D):null;
    return trainClassifier(dataset, initialWeights);
  }

  public LinearClassifier<L, F> trainClassifierWithInitialWeights(GeneralDataset<L, F> dataset, LinearClassifier<L,F> initialClassifier) {
    double[][] initialWeights2D = (initialClassifier != null)? initialClassifier.weights():null;
    return trainClassifierWithInitialWeights(dataset, initialWeights2D);
  }


  /**
   * Given the path to a file representing the text based serialization of a
   * Linear Classifier, reconstitutes and returns that LinearClassifier.
   *
   * TODO: Leverage Index
   */
  public static LinearClassifier<String, String> loadFromFilename(String file) {
    try {
      BufferedReader in = IOUtils.readerFromString(file);

      // Format: read indices first, weights, then thresholds
      Index<String> labelIndex = HashIndex.loadFromReader(in);
      Index<String> featureIndex = HashIndex.loadFromReader(in);
      double[][] weights = new double[featureIndex.size()][labelIndex.size()];
      int currLine = 1;
      String line = in.readLine();
      while (line != null && line.length()>0) {
        String[] tuples = line.split(LinearClassifier.TEXT_SERIALIZATION_DELIMITER);
        if (tuples.length != 3) {
            throw new Exception("Error: incorrect number of tokens in weight specifier, line="
                + currLine + " in file " + file);
        }
        currLine++;
        int feature = Integer.parseInt(tuples[0]);
        int label = Integer.parseInt(tuples[1]);
        double value = Double.parseDouble(tuples[2]);
        weights[feature][label] = value;
        line = in.readLine();
      }

      // First line in thresholds is the number of thresholds
      int numThresholds = Integer.parseInt(in.readLine());
      double[] thresholds = new double[numThresholds];
      int curr = 0;
      while ((line = in.readLine()) != null) {
        double tval = Double.parseDouble(line.trim());
        thresholds[curr++] = tval;
      }
      in.close();
      LinearClassifier<String, String> classifier = new LinearClassifier<>(weights, featureIndex, labelIndex);
      return classifier;
    } catch (Exception e) {
      throw new RuntimeIOException("Error in LinearClassifierFactory, loading from file=" + file, e);
    }
  }

  public void setEvaluators(int iters, Evaluator[] evaluators) {
    this.evalIters = iters;
    this.evaluators = evaluators;
  }

  public LinearClassifierCreator<L,F> getClassifierCreator(GeneralDataset<L, F> dataset) {
//    LogConditionalObjectiveFunction<L, F> objective = new LogConditionalObjectiveFunction<L, F>(dataset, logPrior);
    return new LinearClassifierCreator<>(dataset.featureIndex, dataset.labelIndex);
  }

  public static class LinearClassifierCreator<L,F> implements ClassifierCreator, ProbabilisticClassifierCreator
  {
    LogConditionalObjectiveFunction objective;
    Index<F> featureIndex;
    Index<L> labelIndex;

    public LinearClassifierCreator(LogConditionalObjectiveFunction objective, Index<F> featureIndex, Index<L> labelIndex)
    {
      this.objective = objective;
      this.featureIndex = featureIndex;
      this.labelIndex = labelIndex;
    }

    public LinearClassifierCreator(Index<F> featureIndex, Index<L> labelIndex)
    {
      this.featureIndex = featureIndex;
      this.labelIndex = labelIndex;
    }

    public LinearClassifier createLinearClassifier(double[] weights) {
      double[][] weights2D;
      if (objective != null) {
        weights2D = objective.to2D(weights);
      } else {
        weights2D = ArrayUtils.to2D(weights, featureIndex.size(), labelIndex.size());
      }
      return new LinearClassifier<>(weights2D, featureIndex, labelIndex);
    }

    @Override
    public Classifier createClassifier(double[] weights) {
      return createLinearClassifier(weights);
    }

    @Override
    public ProbabilisticClassifier createProbabilisticClassifier(double[] weights) {
      return createLinearClassifier(weights);
    }
  }

}
