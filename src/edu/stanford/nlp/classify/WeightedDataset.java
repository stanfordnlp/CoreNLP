package edu.stanford.nlp.classify;

import edu.stanford.nlp.ling.Datum;
import edu.stanford.nlp.util.Index;

import java.util.Collection;
import java.util.Random;

/**
 * @author Galen Andrew
 * @author Sarah Spikes (sdspikes@cs.stanford.edu) (Templatization)
 */
public class WeightedDataset<L, F> extends Dataset<L, F> {
  /**
   * 
   */
  private static final long serialVersionUID = -5435125789127705430L;
  protected float[] weights;

  public WeightedDataset(Index<L> labelIndex, int[] labels, Index<F> featureIndex, int[][] data, int size, float[] weights) {
    super(labelIndex, labels, featureIndex, data, data.length);
    this.weights = weights;
  }

  public WeightedDataset() {
    this(10);
  }

  public WeightedDataset(int initSize) {
    super(initSize);
    weights = new float[initSize];
  }

  private float[] trimToSize(float[] i) {
    float[] newI = new float[size];
    System.arraycopy(i, 0, newI, 0, size);
    return newI;
  }

  public float[] getWeights() {
    weights = trimToSize(weights);
    return weights;
  }

  @Override
  public float[] getFeatureCounts() {
    float[] counts = new float[featureIndex.size()];
    for (int i = 0, m = size; i < m; i++) {
      for (int j = 0, n = data[i].length; j < n; j++) {
        counts[data[i][j]] += weights[i];
      }
    }
    return counts;
  }

  @Override
  public void add(Datum<L, F> d) {
    add(d, 1.0f);
  }

  @Override
  public void add(Collection<F> features, L label) {
    add(features, label, 1.0f);
  }

  public void add(Datum<L, F> d, float weight) {
    add(d.asFeatures(), d.label(), weight);
  }

  @Override
  protected void ensureSize() {
    super.ensureSize();
    if (weights.length == size) {
      float[] newWeights = new float[size * 2];
      System.arraycopy(weights, 0, newWeights, 0, size);
      weights = newWeights;
    }
  }

  public void add(Collection<F> features, L label, float weight) {
    ensureSize();
    addLabel(label);
    addFeatures(features);
    weights[size++] = weight;
  }
  
  /**
   * Randomizes the data array in place
   * Needs to be redefined here because we need to randomize the weights as well
   */
  @Override
  public void randomize(int randomSeed) {
    Random rand = new Random(randomSeed);
    for(int j = size - 1; j > 0; j --){
      int randIndex = rand.nextInt(j);
      
      int [] tmp = data[randIndex];
      data[randIndex] = data[j];
      data[j] = tmp;
      
      int tmpl = labels[randIndex];
      labels[randIndex] = labels[j];
      labels[j] = tmpl;
      
      float tmpw = weights[randIndex];
      weights[randIndex] = weights[j];
      weights[j] = tmpw;
    }
  }
}
