/**
 * Title:        StanfordMaxEnt<p>
 * Description:  A Maximum Entropy Toolkit<p>
 * Copyright:    Copyright (c) Kristina Toutanova<p>
 * Company:      Stanford University<p>
 * @author Kristina Toutanova
 * @version 1.0
 */


package edu.stanford.nlp.maxent.iis;

import edu.stanford.nlp.maxent.BinaryFeature;
import edu.stanford.nlp.maxent.BinaryProblem;
import edu.stanford.nlp.maxent.Experiments;
import edu.stanford.nlp.maxent.Feature;

public class LambdaSolveBinary extends LambdaSolve {

  LambdaSolveBinary(BinaryProblem p1, double eps1, double nerr1) {
    p = p1;
    eps = eps1;
    newtonerr = nerr1;
    lambda = new double[p1.fSize];
    lambda_converged = new boolean[p1.fSize];
    probConds = new double[p.data.xSize][p.data.ySize];
    fnumArr = new byte[p.data.xSize][p.data.ySize];
    zlambda = new double[p.data.xSize];
    ftildeArr = new double[p.fSize];
    initCondsZlambdaEtc();
  }


  LambdaSolveBinary(String filename) {
    this.readL(filename);
  }


  @Override
  final void initCondsZlambdaEtc() {
    // init pcond
    for (int x = 0; x < p.data.xSize; x++) {
      for (int y = 0; y < p.data.ySize; y++) {
        probConds[x][y] = 1.0 / p.data.ySize;
      }
    }
    System.out.println(" pcond initialized ");
    // init zlambda
    for (int x = 0; x < p.data.xSize; x++) {
      zlambda[x] = p.data.ySize;
    }
    System.out.println(" zlambda initialized ");
    // init ftildeArr
    for (int i = 0; i < p.fSize; i++) {
      ftildeArr[i] = (p.functions.get(i).ftilde());
    }
    System.out.println(" ftildeArr initialized ");
    // init fnumArr
    for (int f = 0; f < p.fSize; f++) {
      int[] xValues = p.functions.get(f).indexedValues;
      for (int xValue : xValues) {
        int x = xValue / p.data.ySize;
        int y = xValue - x * p.data.ySize;
        fnumArr[x][y]++;
      }
    }//f

  }


  /**
   * This method updates the conditional probabilities in the model, resulting from the
   * update of lambda[index] to lambda[index]+deltaL .
   */

  @Override
  void updateConds(int index, double deltaL) {
    //  for each x that (x,y)=true / exists y
    //  recalculate pcond(y,x) for all
    for (int i = 0; i < p.functions.get(index).len(); i++) {
      // update for this x
      double s = 0;
      int x = p.functions.get(index).getX(i);
      int y = p.functions.get(index).getY(i);
      double zlambdaX = zlambda[x] + pcond(y, x) * zlambda[x] * (Math.exp(deltaL) - 1);
      for (int y1 = 0; y1 < p.data.ySize; y1++) {
        probConds[x][y1] = (probConds[x][y1] * zlambda[x]) / zlambdaX;
        s = s + probConds[x][y1];
      }
      s = s - probConds[x][y];
      probConds[x][y] = probConds[x][y] * Math.exp(deltaL);
      s = s + probConds[x][y];
      zlambda[x] = zlambdaX;
      if (Math.abs(s - 1) > 0.001) {
        System.out.println(x + " index " + i + " deltaL " + deltaL + " tag " + y + " zlambda " + zlambda[x]);
      }
    }

  }

  @Override
  double g(double lambdaP, int index) {
    double s = 0.0;
    // int maxX = Experiments.xSize;
    // int maxY = Experiments.ySize;
    for (int i = 0; i < p.functions.get(index).len(); i++) {
      int y = p.functions.get(index).getY(i);
      int x = p.functions.get(index).getX(i);
      double exponent = Math.exp(lambdaP * fnum(x, y));
      s = s + p.data.ptildeX(x) * pcond(y, x) * 1 * exponent;
    }
    s = s - ftildeArr[index];
    return s;
  }


  @Override
  double gprime(double lambdaP, int index) {
    double s = 0.0;
    // int maxX = Experiments.xSize;
    // int maxY = Experiments.ySize;
    for (int i = 0; i < p.functions.get(index).len(); i++) {
      int y = p.functions.get(index).getY(i);
      int x = p.functions.get(index).getX(i);
      s = s + p.data.ptildeX(x) * pcond(y, x) * 1 * Math.exp(lambdaP * fnum(x, y)) * fnum(x, y);
    }
    return s;
  }

  /**
   * Computes the expected value of a binary feature according to the current model p(y|x).
   */
  @Override
  double fExpected(Feature f) {
    BinaryFeature f1 = (BinaryFeature) f;
    double s = 0.0;
    // int maxX = Experiments.xSize;
    // int maxY = Experiments.ySize;
    for (int i = 0; i < f1.len(); i++) {
      int y = f1.getY(i);
      int x = f1.getX(i);
      s = s + p.data.ptildeX(x) * pcond(y, x);
    }
    return s;
  }


  public static void main(String[] arg) {
    LambdaSolveBinary prob = new LambdaSolveBinary("trainhuge.txt.holder.prob");
    prob.save_lambdas("trainhuge.txt.holder.prob");
    prob.readL("trainhuge.txt.holder.prob");
  }

}
