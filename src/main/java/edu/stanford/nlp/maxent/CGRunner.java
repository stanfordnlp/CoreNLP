/*
 * Title:       Stanford JavaNLP.
 * Description: A Maximum Entropy Toolkit.
 * Copyright:   Copyright (c) 2002. Board of Trustees of Leland Stanford Junior University.
 * Company:     Stanford University, All Rights Reserved.
 */
package edu.stanford.nlp.maxent; 

import java.util.Arrays;

import edu.stanford.nlp.maxent.iis.LambdaSolve;
import edu.stanford.nlp.optimization.*;
import edu.stanford.nlp.util.ReflectionLoading;
import edu.stanford.nlp.util.logging.Redwood;

/**
 * This class will call an optimization method such as Conjugate Gradient or
 * Quasi-Newton  on a LambdaSolve object to find
 * optimal parameters, including imposing a Gaussian prior on those
 * parameters.
 *
 * @author Kristina Toutanova
 * @author Christopher Manning
 */
public class CGRunner  {

  /** A logger for this class */
  private static final Redwood.RedwoodChannels log = Redwood.channels(CGRunner.class);

  private static final boolean SAVE_LAMBDAS_REGULARLY = false;

  private final LambdaSolve prob;
  private final String filename;
  /**
   * Error tolerance passed to CGMinimizer
   */
  private final double tol;
  private final boolean useGaussianPrior;
  private final double priorSigmaS;
  private final double[] sigmaSquareds; // = null;

  private static final double DEFAULT_TOLERANCE = 1e-4;
  private static final double DEFAULT_SIGMASQUARED = 0.5;


  /**
   * Set up a LambdaSolve problem for solution by a Minimizer.
   * Uses a Gaussian prior with a sigma<sup>2</sup> of 0.5.
   *
   * @param prob     The problem to solve
   * @param filename Used (with extension) to save intermediate results.
   */
  public CGRunner(LambdaSolve prob, String filename) {
    this(prob, filename, DEFAULT_SIGMASQUARED);
  }

  /**
   * Set up a LambdaSolve problem for solution by a Minimizer,
   * specifying a value for sigma<sup>2</sup>.
   *
   * @param prob             The problem to solve
   * @param filename         Used (with extension) to save intermediate results.
   * @param priorSigmaS      The prior sigma<sup>2</sup>: this doubled will be
   *                         used to divide the lambda<sup>2</sup> values as the
   *                         prior penalty in the likelihood.  A value of 0.0
   *                         or Double.POSITIVE_INFINITY
   *                         indicates to not use regularization.
   */
  public CGRunner(LambdaSolve prob, String filename, double priorSigmaS) {
    this(prob, filename, DEFAULT_TOLERANCE, priorSigmaS);
  }

  /**
   * Set up a LambdaSolve problem for solution by a Minimizer.
   *
   * @param prob             The problem to solve
   * @param filename         Used (with extension) to save intermediate results.
   * @param tol              Tolerance of errors (passed to CG)
   * @param priorSigmaS      The prior sigma<sup>2</sup>: this doubled will be
   *                         used to divide the lambda<sup>2</sup> values as the
   *                         prior penalty.  A value of 0.0
   *                         or Double.POSITIVE_INFINITY
   *                         indicates to not use regularization.
   */
  public CGRunner(LambdaSolve prob, String filename, double tol, double priorSigmaS) {
    this.prob = prob;
    this.filename = filename;
    this.tol = tol;
    this.useGaussianPrior = priorSigmaS != 0.0 && priorSigmaS != Double.POSITIVE_INFINITY;
    this.priorSigmaS = priorSigmaS;
    this.sigmaSquareds = null;
  }


  /**
   * Set up a LambdaSolve problem for solution by a Minimizer.
   *
   * @param prob             The problem to solve
   * @param filename         Used (with extension) to save intermediate results.
   * @param tol              Tolerance of errors (passed to CG)
   * @param sigmaSquareds    The prior sigma<sup>2</sup> for each feature: this doubled will be
   *                         used to divide the lambda<sup>2</sup> values as the
   *                         prior penalty. This array must have size the number of features.
   *                         If it is null, no regularization will be performed.
   */
  public CGRunner(LambdaSolve prob, String filename, double tol, double[] sigmaSquareds) {
    this.prob = prob;
    this.filename = filename;
    this.tol = tol;
    this.useGaussianPrior = sigmaSquareds !=null;
    this.sigmaSquareds = sigmaSquareds;
    this.priorSigmaS = -1.0; // not used
  }

  private void printOptimizationResults(LikelihoodFunction df, MonitorFunction monitor) {
    double negLogLike = df.valueAt(prob.lambda);
    System.err.printf("After optimization neg (penalized) log cond likelihood: %1.2f%n", negLogLike);
    if (monitor != null) {
      monitor.reportMonitoring(negLogLike);
    }
    int numNonZero = 0;
    for (int i = 0; i < prob.lambda.length; i++) {
      if (prob.lambda[i] != 0.0) {
        // 0.0 == -0.0 in IEEE math!
        numNonZero++;
      }
    }
    System.err.printf("Non-zero parameters: %d/%d (%1.2f%%)%n", numNonZero, prob.lambda.length,
        (100.0 * numNonZero) / prob.lambda.length);
  }


  /**
   * Solves the problem using a quasi-newton method (L-BFGS).  The solution
   * is stored in the {@code lambda} array of {@code prob}.
   */
  public void solveQN() {
    LikelihoodFunction df = new LikelihoodFunction(prob, tol, useGaussianPrior, priorSigmaS, sigmaSquareds);
    MonitorFunction monitor = new MonitorFunction(prob, df, filename);
    Minimizer<DiffFunction> cgm = new QNMinimizer(monitor, 10);

    // all parameters are started at 0.0
    prob.lambda = cgm.minimize(df, tol, new double[df.domainDimension()]);
    printOptimizationResults(df, monitor);
  }

  public void solveOWLQN2(double weight) {
    LikelihoodFunction df = new LikelihoodFunction(prob, tol, useGaussianPrior, priorSigmaS, sigmaSquareds);
    MonitorFunction monitor = new MonitorFunction(prob, df, filename);
    QNMinimizer cgm = new QNMinimizer(monitor, 10);
    cgm.useOWLQN(true, weight);

    // all parameters are started at 0.0
    prob.lambda = cgm.minimize(df, tol, new double[df.domainDimension()]);
    printOptimizationResults(df, monitor);
  }

  /**
   * Solves the problem using conjugate gradient (CG).  The solution
   * is stored in the {@code lambda} array of {@code prob}.
   */
  public void solveCG() {
    LikelihoodFunction df = new LikelihoodFunction(prob, tol, useGaussianPrior, priorSigmaS, sigmaSquareds);
    MonitorFunction monitor = new MonitorFunction(prob, df, filename);
    Minimizer<DiffFunction> cgm = new CGMinimizer(monitor);

    // all parameters are started at 0.0
    prob.lambda = cgm.minimize(df, tol, new double[df.domainDimension()]);
    printOptimizationResults(df, monitor);
  }

  /**
   * Solves the problem using OWLQN.  The solution
   * is stored in the {@code lambda} array of {@code prob}.  Note that the
   * likelihood function will be a penalized L2 likelihood function unless you
   * have turned this off via setting the priorSigmaS to 0.0.
   *
   * @param weight Controls the sparseness/regularization of the L1 solution.
   *     The bigger the number the sparser the solution.  Weights between
   *     0.01 and 1.0 typically give good performance.
   */
  public void solveL1(double weight) {
    LikelihoodFunction df = new LikelihoodFunction(prob, tol, useGaussianPrior, priorSigmaS, sigmaSquareds);
    Minimizer<DiffFunction> owl = ReflectionLoading.loadByReflection("edu.stanford.nlp.optimization.OWLQNMinimizer", weight);
    prob.lambda = owl.minimize(df, tol, new double[df.domainDimension()]);
    printOptimizationResults(df, null);
  }


  /**
   * This class implements the DiffFunction interface for Minimizer
   */
  private static final class LikelihoodFunction implements DiffFunction {

    private final LambdaSolve model;
    private final double tol;
    private final boolean useGaussianPrior;
    private final double[] sigmaSquareds;
    private int valueAtCalls;
    private double likelihood;


    public LikelihoodFunction(LambdaSolve m, double tol, boolean useGaussianPrior, double sigmaSquared, double[] sigmaSquareds) {
      model = m;
      this.tol = tol;
      this.useGaussianPrior = useGaussianPrior;
      if (useGaussianPrior) {
        // keep separate prior on each parameter for flexibility
        this.sigmaSquareds = new double[model.lambda.length];
        if (sigmaSquareds != null) {
          System.arraycopy(sigmaSquareds, 0, this.sigmaSquareds, 0, sigmaSquareds.length);
        } else {
          Arrays.fill(this.sigmaSquareds, sigmaSquared);
        }
      } else {
        this.sigmaSquareds = null;
      }
    }

    @Override
    public int domainDimension() {
      return model.lambda.length;
    }

    public double likelihood() {
      return likelihood;
    }

    public int numCalls() {
      return valueAtCalls;
    }


    @Override
    public double valueAt(double[] lambda) {
      valueAtCalls++;
      model.lambda = lambda;
      double lik = model.logLikelihoodScratch();

      if (useGaussianPrior) {
        //double twoSigmaSquared = 2 * sigmaSquared;
        for (int i = 0; i < lambda.length; i++) {
          lik += (lambda[i] * lambda[i]) / (sigmaSquareds[i] + sigmaSquareds[i]);
        }
      }
      // log.info(valueAtCalls + " calls to valueAt;" +
      //		       " penalized log likelihood is " + lik);

      likelihood = lik;
      return lik;
    }


    @Override
    public double[] derivativeAt(double[] lambda) {
      boolean eq = true;
      for (int j = 0; j < lambda.length; j++) {
        if (Math.abs(lambda[j] - model.lambda[j]) > tol) {
          eq = false;
          break;
        }
      }
      if (!eq) {
        log.info("derivativeAt: call with different value");
        valueAt(lambda);
      }

      double[] drvs = model.getDerivatives();

      // System.out.println("for lambdas "+lambda[0]+" "+lambda[1] +
      //                   " derivatives "+drvs[0]+" "+drvs[1]);

      if (useGaussianPrior) {
        // prior penalty
        for (int j = 0; j < lambda.length; j++) {
          // double sign=1;
          // if(lambda[j]<=0){sign=-1;}
          drvs[j] += lambda[j] / sigmaSquareds[j];
        }
      }

      //System.out.println("final derivatives "+drvs[0]+" "+drvs[1]);
      return drvs;
    }

  } // end static class LikelihoodFunction


  /**
   * This class is used in the monitor.
   */
  private static final class MonitorFunction implements Function {

    private final LambdaSolve model;
    private final LikelihoodFunction lf;
    private final String filename;
    private int iterations; // = 0

    public MonitorFunction(LambdaSolve m, LikelihoodFunction lf, String filename) {
      this.model = m;
      this.lf = lf;
      this.filename = filename;
    }

    @Override
    public double valueAt(double[] lambda) {
      double likelihood = lf.likelihood();
      // this line is printed in the middle of the normal line of QN minimization, so put println at beginning
      log.info();
      log.info(reportMonitoring(likelihood));

      if (SAVE_LAMBDAS_REGULARLY  && iterations > 0 && iterations % 5 == 0) {
        model.save_lambdas(filename + '.' + iterations + ".lam");
      }

      if (iterations > 0 && iterations % 30 == 0) {
        model.checkCorrectness();
      }
      iterations++;

      return 42; // never cause premature termination.
    }

    public String reportMonitoring(double likelihood) {
      return "Iter. " + iterations + ": " + "neg. log cond. likelihood = " + likelihood + " [" + lf.numCalls() + " calls to valueAt]";
    }

    @Override
    public int domainDimension() {
      return lf.domainDimension();
    }

  } // end static class MonitorFunction

}
