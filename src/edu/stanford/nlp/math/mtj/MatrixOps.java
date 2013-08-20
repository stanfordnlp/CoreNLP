package edu.stanford.nlp.math.mtj;
import no.uib.cipr.matrix.*;

public class MatrixOps {

  public static Matrix invert(Matrix v) {
    Matrix i = Matrices.identity(v.numRows());
    Matrix ai = i.copy();
    v.solve(i, ai);
    return ai;
  } 

  public static double det(Matrix v) {
    DenseLU lu =  DenseLU.factorize(v);
    double det = 1.0;
    Matrix l = lu.getL();
    for(int i = 0; i < l.numRows(); ++i) {
      det *= l.get(i,i);
    }
    Matrix u = lu.getU();
    for(int i = 0; i < u.numRows(); ++i) {
      det *= u.get(i,i);
    }
    return det;
  }

}
