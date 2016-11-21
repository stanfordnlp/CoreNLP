package edu.stanford.nlp.optimization;

import java.util.Arrays;

/**
 * @author Dan Klein
 */

public abstract class AbstractCachingDiffFloatFunction implements DiffFloatFunction, HasFloatInitial {

  private float[] lastX = null;

  protected float[] derivative = null;
  protected float value = 0.0f;


  @Override
  abstract public int domainDimension();

  /**
   * Calculate the value at x and the derivative and save them in the respective fields
   *
   */
  abstract protected void calculate(float[] x);

  @Override
  public float[] initial() {
    float[] initial = new float[domainDimension()];
    // Arrays.fill(initial, 0.0f);  // not needed; Java arrays zero initialized
    return initial;
  }

  protected static void copy(float[] y, float[] x) {
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

  @Override
  public float valueAt(float[] x) {
    ensure(x);
    return value;
  }

  static float norm2(float[] x) {
    float sum = 0.0f;
    for (float aX : x) {
      sum += aX * aX;
    }
    return (float) Math.sqrt(sum);
  }

  @Override
  public float[] derivativeAt(float[] x) {
    ensure(x);
    return derivative;
  }

}
