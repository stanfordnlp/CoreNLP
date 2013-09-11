package edu.stanford.nlp.stats;

import no.uib.cipr.matrix.*;
import java.util.Random;

/**
 *
 * Draws samples from an Inverse Wishart Distribution, the conjugate prior 
 * for covariance matrices.
 *
 * @author dlwh
 *
 */
public class InverseWishart implements ProbabilityDistribution<Matrix> {

  /**
   * 
   */
  private static final long serialVersionUID = -1000464962771645475L;
  private final Wishart w;
  private final Matrix v;

  public InverseWishart(double df, Matrix v) {
    Matrix i = Matrices.identity(v.numRows());
    Matrix ai = i.copy();
    v.solve(i, ai);
    this.v = v;
    this.w = new Wishart(df, ai);
  }

  public Matrix getInverseScale() {
    return v;
  }

  public double getDf() {
    return w.getDf();
  }

  public Matrix mean() {
    return v.copy().scale(1.0/(getDf() - v.numRows() - 1));
  }

  public double probabilityOf(Matrix m) {
    throw new RuntimeException("not implemented");
  }

  public double logProbabilityOf(Matrix m) {
    throw new RuntimeException("not implemented");
  }

  public Matrix drawSample(Random r) {
    Matrix ret = w.drawSample(r);
    Matrix i = Matrices.identity(ret.numRows());
    Matrix ri = i.copy();
    return ret.solve(i,ri);
  }

}
