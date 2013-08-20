package edu.stanford.nlp.stats;

import java.util.Map;
import java.util.Random;

import no.uib.cipr.matrix.DenseMatrix;
import no.uib.cipr.matrix.Matrix;
import no.uib.cipr.matrix.Vector;

/**
 * A conjugate prior for a multivariate Gaussian likelihood, where the mean is known
 * and fixed, and we have uncertainty only about the covariance matrix. 
 *
 * @author dlwh
 *
 */
public class GaussianCovariancePrior implements ConjugatePrior<Gaussian, Vector> {

  /**
   * 
   */
  private static final long serialVersionUID = -3832920718403518986L;
  // the parameters of this distribution over possible Gaussians
  private final Matrix var;
  private final double df;
  private final InverseWishart inner;

  // the mean that partly defines the Gaussians produced by this distribution
  private Vector likelihoodMean;

  public double getPredictiveProbability(Vector observation) {
    return Gaussian.probabilityOf(observation, likelihoodMean, inner.mean());
  }

  public double getPredictiveLogProbability(Vector observation) {
    return Gaussian.logProbabilityOf(observation, likelihoodMean, inner.mean());
  }

  /**
   * Works whether evidence counts are positive (normal case, adding observations)
   * or negative (removing observations).
   * No guarantees on what happens if you unobserve things that were never observed
   * in the first place!
   */
  public GaussianCovariancePrior getPosteriorDistribution(Counter<Vector> evidence) {
    double n = evidence.totalCount(); // might be negative, right?
    if (n==0.0) { // no evidence
//      System.err.println("No evidence, returning the prior!");
//      System.err.println("newMean: " + Arrays.toString(mean));
      return this;
    }
//    System.err.println("evidence size: " + evidence.totalCount());
    // postVar = var + \sum_i (x_i - mu) (x_i - mu)^T
    Matrix res = var;
    for(Map.Entry<Vector,Double> x: evidence.entrySet()) {
      Matrix xfix = new DenseMatrix(x.getKey().copy().add(-1,likelihoodMean));
      res = xfix.transBmultAdd(x.getValue(),xfix,res);
    }

    return new GaussianCovariancePrior(df + n, res,likelihoodMean);
  }

  public double getPosteriorPredictiveProbability(Counter<Vector> evidence, Vector observation) {
    return getPosteriorDistribution(evidence).getPredictiveProbability(observation);
  }

  public double getPosteriorPredictiveLogProbability(Counter<Vector> evidence, Vector observation) {
    return getPosteriorDistribution(evidence).getPredictiveLogProbability(observation);
  }

  public Gaussian drawSample(Random random) {
    return new Gaussian(likelihoodMean,inner.drawSample(random));
  }

  /**
   * Based solely on the probability of the mean of the Gaussian according to this prior.
   */
  public double probabilityOf(Gaussian object) {
    Matrix likelihoodVariance = object.getVar();
    return inner.probabilityOf(likelihoodVariance);
  }

  /**
   * Based solely on the probability of the mean of the Gaussian according to this prior.
   */
  public double logProbabilityOf(Gaussian object) {
    Matrix likelihoodVariance = object.getVar();
    return inner.logProbabilityOf(likelihoodVariance);
  }

  public Vector getLikelihoodMean() {
    return likelihoodMean;
  }

  public Matrix getScaleMatrix() {
    return var;
  }

  public double getDf() {
    return df;
  }

  public GaussianCovariancePrior(double df, Matrix var, Vector likelihoodMean) {
    this.likelihoodMean = likelihoodMean;
    this.df = df;
    this.var = var;
    this.inner = new InverseWishart(df,var);
  }

}
