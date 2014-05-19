package edu.stanford.nlp.optimization;

/**
 * Function for stochastic calculations that does update in place
 * (instead of maintaining and returning the derivative).
 *
 * Weights are represented by an array of doubles and a scalar
 * that indicates how much to scale all weights by.
 * This allows all weights to be scaled by just modifying the scalar.
 *
 * @author Angel Chang
 */
public abstract class AbstractStochasticCachingDiffUpdateFunction
        extends AbstractStochasticCachingDiffFunction {

  protected boolean skipValCalc = false;

  /**
   * Gets a random sample (this is sampling with replacement).
   *
   * @param sampleSize number of samples to generate
   * @return array of indices for random sample of sampleSize
   */
  public int[] getSample(int sampleSize) {
    int[] sample = new int[sampleSize];
    for (int i = 0; i < sampleSize; i++) {
      sample[i] = randGenerator.nextInt(this.dataDimension()); // Just generate a random index
    }
    return sample;
  }

  /**
   * Computes value of function for specified value of x (scaled by xScale)
   * only over samples indexed by batch.
   *
   * @param x unscaled weights
   * @param xScale how much to scale x by when performing calculations
   * @param batch indices of which samples to compute function over
   * @return value of function at specified x (scaled by xScale) for samples
   */
  public abstract double valueAt(double[] x, double xScale, int[] batch);

  public double valueAt(double[] x, double xScale, int batchSize) {
    getBatch(batchSize);
    return valueAt(x, xScale, thisBatch);
  }

  /**
   * Performs stochastic update of weights x (scaled by xScale) based
   * on samples indexed by batch.
   *
   * @param x unscaled weights
   * @param xScale how much to scale x by when performing calculations
   * @param batch indices of which samples to compute function over
   * @param gain how much to scale adjustments to x
   * @return value of function at specified x (scaled by xScale) for samples
   */
  public abstract double calculateStochasticUpdate(double[] x, double xScale, int[] batch, double gain);

  /**
   * Performs stochastic update of weights x (scaled by xScale) based
   * on next batch of batchSize.
   *
   * @param x unscaled weights
   * @param xScale how much to scale x by when performing calculations
   * @param batchSize number of samples to pick next
   * @param gain how much to scale adjustments to x
   * @return value of function at specified x (scaled by xScale) for samples
   */
  public double calculateStochasticUpdate(double[] x, double xScale, int batchSize, double gain) {
    getBatch(batchSize);
    return calculateStochasticUpdate(x, xScale, thisBatch, gain);
  }

  /**
   * Performs stochastic gradient calculation based
   * on samples indexed by batch and do not apply regularization.
   * does not update the parameter values
   *
   * @param x unscaled weights
   * @param batch indices of which samples to compute function over
   * @return value of function at specified x (not scaled) for samples
   */
  public abstract void calculateStochasticGradient(double[] x, int[] batch);

  /**
   * Performs stochastic gradient updates based
   * on samples indexed by batch and do not apply regularization.
   *
   * @param x unscaled weights
   * @param batchSize number of samples to pick next
   */
  public void calculateStochasticGradient(double[] x, int batchSize) {
    getBatch(batchSize);
    calculateStochasticGradient(x, thisBatch);
  }

}
