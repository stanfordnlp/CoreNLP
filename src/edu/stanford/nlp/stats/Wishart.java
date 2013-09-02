package edu.stanford.nlp.stats;

import no.uib.cipr.matrix.*;
import java.util.Random;

/**
 * Generates symmetric positive definite square matrix
 */
public class Wishart implements ProbabilityDistribution<Matrix> {

  /**
   * 
   */
  private static final long serialVersionUID = -1524540511821287970L;
  private final double df; // degrees of freedom
  private final Matrix v; // prior matrix
  private final Matrix l; // L from Cholesky decomposition of V
  private final Matrix lt;// transpose of L

  public Wishart(double df, Matrix v) {
      this.df = df;
      if(df < v.numRows()) throw new IllegalArgumentException("df < v.numRows!");
      this.v = v;
      this.l = new DenseCholesky(v.numRows(),false).factor(new LowerSPDDenseMatrix(v)).getL(); 
      this.lt = new DenseMatrix(l).transpose();
  }

  public double probabilityOf(Matrix m) {
    throw new RuntimeException("not implemented");
  }

  public double logProbabilityOf(Matrix m) {
    throw new RuntimeException("not implemented");
  }

  public Matrix drawSample(Random r) {
    Matrix diag = new DenseMatrix(v.numRows(),v.numColumns());
    for(int i = 0; i < v.numRows(); ++i) {
      double chi2 = Gamma.drawSample(r,df - i,2);
      diag.set(i,i,Math.sqrt(chi2));
      for(int j = 0; j < i; ++j) {
        diag.set(i,j,r.nextGaussian());
      }
    }
    Matrix c = new DenseMatrix(diag.numRows(),diag.numColumns());
    Matrix d = new DenseMatrix(diag.numRows(),diag.numColumns());
    return l.mult(diag,d).mult(diag.transAmult(1,lt,c),diag);
  }

  public Matrix getScaleMatrix() {
    return v;
  }

  public double getDf() {
    return df;
  }

}
