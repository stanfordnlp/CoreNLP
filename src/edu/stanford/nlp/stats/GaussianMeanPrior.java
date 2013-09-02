package edu.stanford.nlp.stats;

import static edu.stanford.nlp.math.mtj.MatrixOps.invert;

import java.util.Random;

import no.uib.cipr.matrix.DenseVector;
import no.uib.cipr.matrix.Matrix;
import no.uib.cipr.matrix.SymmTridiagMatrix;
import no.uib.cipr.matrix.Vector;

/**
 * A conjugate prior for a multivariate Gaussian likelihood, where the variance is known
 * and fixed, and we have uncertainty only about the mean. The mean is distributed
 * according to a multivariate Gaussian.
 *
 * @author dlwh (allow arbitrary covariance matrices)
 * @author grenager
 *
 */
public class GaussianMeanPrior implements ConjugatePrior<Gaussian, Vector> {

  /**
   * 
   */
  private static final long serialVersionUID = -8056200031494870713L;
  // the parameters of this distribution over possible Gaussians
  Vector mean;
  Matrix var;
  Matrix precision;

  // the sigma that partly defines the Gaussians produced by this distribution
  Matrix likelihoodVar;
  Matrix likePrecision;

  public double getPredictiveProbability(Vector observation) {
    if (observation.size() != mean.size()) throw new RuntimeException(observation.size() +"!="+ mean.size());
    return Gaussian.probabilityOf(observation, mean, var.copy().add(likelihoodVar));
  }

  public double getPredictiveLogProbability(Vector observation) {
    if (observation.size() != mean.size()) throw new RuntimeException(observation.size() +"!="+ mean.size());
    return Gaussian.logProbabilityOf(observation, mean, var.copy().add(likelihoodVar));
  }

  /**
   * Works whether evidence counts are positive (normal case, adding observations)
   * or negative (removing observations).
   * No guarantees on what happens if you unobserve things that were never observed
   * in the first place!
   */
  public GaussianMeanPrior getPosteriorDistribution(Counter<Vector> evidence) {
    double n = evidence.totalCount(); // might be negative, right?
    if (n==0.0) { // no evidence
//      System.err.println("No evidence, returning the prior!");
//      System.err.println("newMean: " + Arrays.toString(mean));
      return this;
    }
//    System.err.println("evidence size: " + evidence.totalCount());
    // now we have to figure out what the new mus and sigmas are for each dimension
    // can treat the dimensions as independent because we have diagonal variance

    // compute a sample mean
    double[] sampleMean = new double[mean.size()];
    for (Vector x : evidence.keySet()) {
      if (x.size() != mean.size()) throw new RuntimeException("I am dist of dim " + mean.size()+" but you're giving me an x of size() " + x.size());
      double count = evidence.getCount(x);
      for (int i=0; i<x.size(); i++) {
        sampleMean[i] += (x.get(i)*count);
      }
    }
    for (int i=0; i<sampleMean.length; i++) {
      sampleMean[i] /= n; // if counts were negative, then n is negative, and this will make a positive mean again!
    }

    // (Sigma_0^-1 + n * Sigma^-1)^-1
    Matrix postVar = precision.copy();
    postVar.add(n,likePrecision);
    postVar = invert(postVar);

    // (\Sigma^-1 mean)
    Vector m = mean.copy();
    precision.mult(mean,m);


    Vector barX = new DenseVector(sampleMean);
    likePrecision.mult(n,barX.copy(),barX);

    m.add(barX);
    postVar.mult(m.copy(),m);
    
    return new GaussianMeanPrior(m, postVar, likelihoodVar);
  }

  public double getPosteriorPredictiveProbability(Counter<Vector> evidence, Vector observation) {
    GaussianMeanPrior posterior = getPosteriorDistribution(evidence);
    return posterior.getPredictiveProbability(observation);
  }

  public double getPosteriorPredictiveLogProbability(Counter<Vector> evidence, Vector observation) {
    GaussianMeanPrior posterior = getPosteriorDistribution(evidence);
    return posterior.getPredictiveLogProbability(observation);
  }

  public Gaussian drawSample(Random random) {
    Vector newMean = Gaussian.drawSample(random, mean, var);
    return new Gaussian(newMean, likelihoodVar);
  }

  /**
   * Based solely on the probability of the mean of the Gaussian according to this prior.
   */
  public double probabilityOf(Gaussian object) {
    if (!object.getVar().equals(likelihoodVar)) throw  new RuntimeException("Variance doesn't match.");
    Vector likelihoodMean = object.getMean();
    return Gaussian.probabilityOf(likelihoodMean, mean, var);
  }

  /**
   * Based solely on the probability of the mean of the Gaussian according to this prior.
   */
  public double logProbabilityOf(Gaussian object) {
    if (! object.getVar().equals(likelihoodVar)) throw  new RuntimeException("Variance doesn't match.");
    Vector likelihoodMean = object.getMean();
    return Gaussian.logProbabilityOf(likelihoodMean, mean, var);
  }

  public Matrix getLikelihoodVar() {
    return likelihoodVar;
  }

  public Vector getMean() {
    return mean;
  }

  public Matrix getVar() {
    return var;
  }

  @Override
  public String toString() {
    return "mean=" + mean + ", var=" + var + ", likelihoodVar=" + likelihoodVar;
  }

  public GaussianMeanPrior(double[] mean, double[] var, double[] likelihoodVar) {
    this(new DenseVector(mean), new SymmTridiagMatrix(var,new double[var.length]), new SymmTridiagMatrix(likelihoodVar,new double[likelihoodVar.length]));
  }

  public GaussianMeanPrior(Vector mean, Matrix var, Matrix likelihoodVar) {
    this.mean = mean;
    this.var = var;
    this.precision = invert(var);
    this.likelihoodVar = likelihoodVar;
    this.likePrecision = invert(likelihoodVar);
  }



}
