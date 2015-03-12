package edu.stanford.nlp.optimization;

import java.util.Arrays;

/**
 * @author Dan Klein
 */

public abstract class AbstractCachingDiffFloatFunction implements DiffFloatFunction, HasFloatInitial {

  float[] lastX = null;

  protected float[] derivative = null;
  protected float value = 0.0f;


  abstract public int domainDimension();

  /**
   * Calculate the value at x and the derivative and save them in the respective fields
   *
   */
  abstract protected void calculate(float[] x);

  public float[] initial() {
    float[] initial = new float[domainDimension()];
    Arrays.fill(initial, 0.0f);
    return initial;
  }

  protected void copy(float[] y, float[] x) {
    System.arraycopy(x, 0, y, 0, x.length);
  }

  void ensure(float[] x) {
    if (Arrays.equals(x, lastX)) {
      return;
    }
    if (lastX == null) {
      lastX = new float[domainDimension()];
    }
    if (derivative == null) {
      derivative = new float[domainDimension()];
    }
    copy(lastX, x);
    calculate(x);
  }

  public float valueAt(float[] x) {
    ensure(x);
    return value;
  }

  float norm2(float[] x) {
    float sum = 0.0f;
    for (int i = 0; i < x.length; i++) {
      sum += x[i] * x[i];
    }
    return (float) Math.sqrt(sum);
  }

  public float[] derivativeAt(float[] x) {
    ensure(x);
    return derivative;
  }
}
