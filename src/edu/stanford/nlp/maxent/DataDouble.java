/**
 * Title:        StanfordMaxEnt<p>
 * Description:  A Maximum Entropy Toolkit<p>
 * Copyright:    Copyright (c) Kristina Toutanova<p>
 * Company:      Stanford University<p>
 */

package edu.stanford.nlp.maxent;

/**
 * @author Kristina Toutanova
 * @version 1.0
 */
public class DataDouble extends DataGeneric {
  double[] x;
  int y;
  double cost;

  public DataDouble() {
  }

  public DataDouble(int size) {
    x = new double[size];
  }


  public DataDouble(double[] x, int y) {
    this.x = x;
    this.y = y;

  }


  public double cost() {
    return cost;
  }

  public void setCost(double c) {
    cost = c;
  }


  public double getX(int index) {
    return x[index];
  }


  public void setX(int fNo, double val) {
    x[fNo] = val;
  }

  @Override
  public String getY() {
    return String.valueOf(y);
  }

  public int getYNo() {
    return y;

  }


  public void setYNo(int y) {
    this.y = y;

  }

}
