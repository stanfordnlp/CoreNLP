package edu.stanford.nlp.stats;

import static edu.stanford.nlp.math.mtj.MatrixOps.det;
import static edu.stanford.nlp.math.mtj.MatrixOps.invert;

import java.util.Random;

import no.uib.cipr.matrix.DenseCholesky;
import no.uib.cipr.matrix.DenseVector;
import no.uib.cipr.matrix.LowerSPDDenseMatrix;
import no.uib.cipr.matrix.Matrix;
import no.uib.cipr.matrix.SymmTridiagMatrix;
import no.uib.cipr.matrix.Vector;


/**
 * Implements functions to give probabilities for a (multivariate) Gaussian.
 * 
 * @author Teg Grenager
 */
public class Gaussian implements ProbabilityDistribution<Vector> {

  private static final long serialVersionUID = 1960411222642524273L;

  private final Vector mean;
  private final Matrix var;
  private final Matrix prec;
  private final Matrix chol;


  public Vector drawSample(Random random) {
    DenseVector z = new DenseVector(mean.size());
    for(int i = 0; i < z.size(); ++i) {
      z.set(i,random.nextGaussian());
    }
    return chol.mult(z,z).add(mean);
  }

  public static double[] drawSample(Random random, double[] mean, double[] var) {
    double[] sample = new double[mean.length];
    // since sigma is diagonal, we can just sample each component independently
    for (int i=0; i<sample.length; i++) {
      sample[i] = drawSample(random, mean[i], var[i]);
    }
    return sample;
  }

  public static Vector drawSample(Random random, Vector mean, Matrix var) {
    return new Gaussian(mean,var).drawSample(random);
  }

  public static double drawSample(Random random, double mean, double var) {
    return random.nextGaussian()*Math.sqrt(var) + mean;
  }

  public double probabilityOf(Vector x) {
    return Math.exp(logProbabilityOf(x));
  }


  public double logNormConstant() {
    return -mean.size()/2 *  Math.log(2 * Math.PI) - .5 * Math.log(det(var));
  }

  public double logProbabilityOf(double[] x) {
    return logProbabilityOf(new DenseVector(x));
  }


  public double logProbabilityOf(Vector x) {
    Vector myX = x.copy();
    myX.add(-1,mean);
    return -.5 * myX.dot(prec.mult(myX,myX.copy())) + logNormConstant();
  }

  public static double probabilityOf(Vector x, Vector mean, Matrix var) {
    return Math.exp(logProbabilityOf(x, mean, var));
  }

  public static double logProbabilityOf(Vector x, Vector mean, Matrix var) {
    return new Gaussian(mean,var).logProbabilityOf(x);
  }

  public static double probabilityOf(double x, double mean, double var) {
    return Math.exp(logProbabilityOf(x, mean, var));
  }

  public static double logProbabilityOf(double x, double mean, double var) {
    double z = Math.sqrt(2.0 * Math.PI * var);
    double logProb = -((x-mean)*(x-mean)) / (2.0 * var);
    return logProb - Math.log(z);
  }

  public Vector getMean() {
    return mean;
  }

  public Matrix getVar() {
    return var;
  }

  public Gaussian(double[] mean, double[] var) {
    this(new DenseVector(mean), new SymmTridiagMatrix(var,new double[var.length]));
  }

  public Gaussian(Vector mean, Matrix var) {
   super();
   this.mean = mean;
   this.var = var;
   this.prec = invert(var);
   this.chol = new DenseCholesky(var.numRows(),false).factor(new LowerSPDDenseMatrix(var)).getL();
//   System.out.println("det(chol) = "+det(this.chol));
  }

}
