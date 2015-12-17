package edu.stanford.nlp.classify;

import edu.stanford.nlp.ling.Datum;
import edu.stanford.nlp.ling.RVFDatum;
import edu.stanford.nlp.util.Index;

/**
 * A weighted version of the RVF dataset.
 *
 * @author Gabor Angeli
 */
public class WeightedRVFDataset<L, F> extends RVFDataset<L, F> {

  private static final long serialVersionUID = 1L;

  float[] weights = new float[16];

  public WeightedRVFDataset() {
    super();
  }

  protected WeightedRVFDataset(Index<L> labelIndex, int[] trainLabels, Index<F> featureIndex, int[][] trainData, double[][] trainValues, float[] trainWeights) {
    super(labelIndex, trainLabels, featureIndex, trainData, trainValues);
    this.weights = trainWeights;
  }

  private float[] trimToSize(float[] i) {
    float[] newI = new float[size];
    synchronized (System.class) {
      System.arraycopy(i, 0, newI, 0, size);
    }
    return newI;
  }


  /**
   * Get the weight array for this dataset.
   * Used in, e.g., {@link edu.stanford.nlp.classify.LogConditionalObjectiveFunction}.
   *
   * @return A float array of the weights of this dataset's datums.
   */
  public float[] getWeights() {
    if (weights.length != size) {
      weights = trimToSize(weights);
    }
    return weights;
  }

  /**
   * Register a weight in the weights array.
   * This must be called before the superclass' methods.
   *
   * @param weight The weight to register.
   */
  private void addWeight(float weight) {
    if (weights.length == size) {
      float[] newWeights = new float[size * 2];
      synchronized (System.class) {
        System.arraycopy(weights, 0, newWeights, 0, size);
      }
      weights = newWeights;
    }
    weights[size] = weight;
    // note: don't increment size!
  }

  /**
   * Add a datum, with a given weight.
   * @param d The datum to add.
   * @param weight The weight of this datum.
   */
  public void add(RVFDatum<L, F> d, float weight) {
    addWeight(weight);
    super.add(d);
  }


  /** {@inheritDoc} */
  @Override
  public void add(Datum<L, F> d) {
    addWeight(1.0f);
    super.add(d);
  }

  /** {@inheritDoc} */
  @Override
  public void add(Datum<L, F> d, String src, String id) {
    addWeight(1.0f);
    super.add(d, src, id);
  }

}
