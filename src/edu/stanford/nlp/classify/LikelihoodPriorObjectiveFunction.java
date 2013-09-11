package edu.stanford.nlp.classify;

import edu.stanford.nlp.math.ArrayMath;
import edu.stanford.nlp.optimization.DiffFunction;
import edu.stanford.nlp.util.Pair;

import java.util.HashMap;
import java.util.Iterator;

/**
 * An objective function that is the sum of a log-likelihood and a prior penalty.
 * Requires an object implementing the LogLikelihoodFunction interface
 *
 * @author Kristina Toutanova
 */
public class LikelihoodPriorObjectiveFunction implements DiffFunction {
  private LogLikelihoodFunction logLikelihood;
  public static final int NO_PRIOR = 0;
  public static final int QUADRATIC_PRIOR = 1;
  /* Use a Huber robust regression penalty (L1 except very near 0) not L2 */
  public static final int HUBER_PRIOR = 2;
  public static final int QUARTIC_PRIOR = 3;

  private int prior;
  private double sigma = 1.0;
  private double epsilon;
  private GaussianPriors priors;

  public LikelihoodPriorObjectiveFunction(LogLikelihoodFunction logLikelihood) {
    this(logLikelihood, 1.0);
  }

  public LikelihoodPriorObjectiveFunction(LogLikelihoodFunction logLikelihood, double sigma) {
    this(logLikelihood, QUADRATIC_PRIOR, sigma, 0.0);
  }

  public LikelihoodPriorObjectiveFunction(LogLikelihoodFunction logLikelihood, int prior, double sigma, double epsilon) {
    this.logLikelihood = logLikelihood;
    if (prior >= 0 && prior <= QUARTIC_PRIOR) {
      this.prior = prior;
    } else {
      throw new IllegalArgumentException("Invalid prior: " + prior);
    }
    if (prior == QUADRATIC_PRIOR) {
      priors = new ConstantGaussianPriors(sigma, 0);
    }
    this.epsilon = epsilon;
    this.sigma = sigma;
  }


  /**
   * In case we want to give special sigma and variance to some features.
   *
   */
  public LikelihoodPriorObjectiveFunction(LogLikelihoodFunction logLikelihood, int prior, double sigma, double epsilon, HashMap<Integer, Pair<Double, Double>> specialPriors) {
    this.logLikelihood = logLikelihood;
    if (prior >= 0 && prior <= QUARTIC_PRIOR) {
      this.prior = prior;
    } else {
      throw new IllegalArgumentException("Invalid prior: " + prior);
    }
    if (prior == QUADRATIC_PRIOR) {
      priors = new ArbitraryGaussianPriors(sigma, 0, logLikelihood.domainDimension());
      for (Iterator<Integer> it = specialPriors.keySet().iterator(); it.hasNext();) {
        Integer next = it.next();
        System.err.println("next integer is " + next);
        Pair<Double, Double> val = specialPriors.get(next);
        ((ArbitraryGaussianPriors) priors).setSpecial(next.intValue(), val.first().doubleValue(), val.second().doubleValue());
      }
    }
    this.epsilon = epsilon;
    this.sigma = sigma;
  }


  public double valueAt(double[] x) {
    double value = logLikelihood.logLikelihood(x);
    value = -value; // negative log-likelihood
    if (prior == QUADRATIC_PRIOR) {
      for (int i = 0; i < x.length; i++) {
        double k = 1.0;
        double w = x[i];
        double sigmaSq = priors.sigmaSq(i);
        double mean = priors.mean(i);
        value += k * w * w / 2.0 / sigmaSq;
        if (mean != 0) {
          value += (-2.0 * w * mean + mean * mean) / 2.0 / sigmaSq;
        }
      }
    } else if (prior == HUBER_PRIOR) {
      double sigmaSq = sigma * sigma;
      for (int i = 0; i < x.length; i++) {
        double w = x[i];
        double wabs = Math.abs(w);
        if (wabs < epsilon) {
          value += w * w / 2.0 / epsilon / sigmaSq;
        } else {
          value += (wabs - epsilon / 2) / sigmaSq;
        }
      }
    } else if (prior == QUARTIC_PRIOR) {
      double sigmaQu = sigma * sigma * sigma * sigma;
      for (int i = 0; i < x.length; i++) {
        double k = 1.0;
        double w = x[i];
        value += k * w * w * w * w / 2.0 / sigmaQu;
      }
    }
    return value;
  }

  public int domainDimension() {
    return logLikelihood.domainDimension();
  }

  public double[] derivativeAt(double[] x) {
    double[] derivative = logLikelihood.gradient(x);
    ArrayMath.multiplyInPlace(derivative, -1);
    if (prior == QUADRATIC_PRIOR) {
      for (int i = 0; i < x.length; i++) {
        double k = 1.0;
        double w = x[i];
        double sigmaSq = priors.sigmaSq(i);
        double mean = priors.mean(i);
        derivative[i] += k * (w - mean) / sigmaSq;
      }
    } else if (prior == HUBER_PRIOR) {
      double sigmaSq = sigma * sigma;
      for (int i = 0; i < x.length; i++) {
        double w = x[i];
        double wabs = Math.abs(w);
        if (wabs < epsilon) {
          derivative[i] += w / epsilon / sigmaSq;
        } else {
          derivative[i] += ((w < 0.0) ? -1.0 : 1.0) / sigmaSq;
        }
      }
    } else if (prior == QUARTIC_PRIOR) {
      double sigmaQu = sigma * sigma * sigma * sigma;
      for (int i = 0; i < x.length; i++) {
        double k = 1.0;
        double w = x[i];
        derivative[i] += k * w / sigmaQu;
      }
    }
    return derivative;
  }
}
