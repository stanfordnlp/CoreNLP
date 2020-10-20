package edu.stanford.nlp.loglinear.model;

import java.io.BufferedWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Created on 10/20/15.
 * @author keenon
 * <p>
 * This is a wrapper function to keep a namespace of namespace of recognized features, so that building a set of
 * ConcatVectors for featurizing a model is easier and more intuitive. It's actually quite simple, and threadsafe.
 */
public class ConcatVectorNamespace {
  final Map<String, Integer> featureToIndex = new HashMap<>();
  final Map<String, Map<String, Integer>> sparseFeatureIndex = new HashMap<>();
  final Map<String, Map<Integer, String>> reverseSparseFeatureIndex = new HashMap<>();

  /**
   * Creates a new vector that is appropriately sized to accommodate all the features that have been named so far.
   *
   * @return a new, empty ConcatVector
   */
  public ConcatVector newVector() {
    return new ConcatVector(featureToIndex.size());
  }

  /**
   * This constructs a fresh vector that is sized correctly to accommodate all the known sparse values for vectors
   * that are possibly sparse.
   *
   * @return a new, internally correctly sized ConcatVector that will work correctly as weights for features from
   * this namespace;
   */
  public ConcatVector newWeightsVector() {
    ConcatVector vector = new ConcatVector(featureToIndex.size());
    for (Map.Entry<String, Map<String, Integer>> entry : sparseFeatureIndex.entrySet()) {
      int size = entry.getValue().size();
      vector.setDenseComponent(ensureFeature(entry.getKey()), new double[size]);
    }
    return vector;
  }

  /**
   * An optimization, this lets clients inform the ConcatVectorNamespace of how many features to expect, so
   * that we can avoid resizing ConcatVectors.
   *
   * @param featureName the feature to add to our index
   */
  public int ensureFeature(String featureName) {
    synchronized (featureToIndex) {
      if (!featureToIndex.containsKey(featureName)) {
        featureToIndex.put(featureName, featureToIndex.size());
      }
      return featureToIndex.get(featureName);
    }
  }

  /**
   * An optimization, this lets clients inform the ConcatVectorNamespace of how many sparse feature components to
   * expect, again so that we can avoid resizing ConcatVectors.
   *
   * @param featureName the feature to use in our index
   * @param index       the sparse value to ensure is available
   */
  public int ensureSparseFeature(String featureName, String index) {
    ensureFeature(featureName);
    synchronized (sparseFeatureIndex) {
      if (!sparseFeatureIndex.containsKey(featureName)) {
        sparseFeatureIndex.put(featureName, new HashMap<>());
        reverseSparseFeatureIndex.put(featureName, new HashMap<>());
      }
    }
    final Map<String, Integer> sparseIndex = sparseFeatureIndex.get(featureName);
    final Map<Integer, String> reverseSparseIndex = reverseSparseFeatureIndex.get(featureName);
    synchronized (sparseIndex) {
      if (!sparseIndex.containsKey(index)) {
        reverseSparseIndex.put(sparseIndex.size(), index);
        sparseIndex.put(index, sparseIndex.size());
      }
      return sparseIndex.get(index);
    }
  }

  /**
   * This adds a dense feature to a vector, setting the appropriate component of the given vector to the passed in
   * value.
   *
   * @param vector      the vector
   * @param featureName the feature whose value to set
   * @param value       the value we want to set this vector to
   */
  public void setDenseFeature(ConcatVector vector, String featureName, double[] value) {
    vector.setDenseComponent(ensureFeature(featureName), value);
  }

  /**
   * This adds a sparse feature to a vector, setting the appropriate component of the given vector to the passed in
   * value.
   *
   * @param vector      the vector
   * @param featureName the feature whose value to set
   * @param index       the index of the one-hot vector to set, as a string, which we will translate into a mapping
   * @param value       the value we want to set this one-hot index to
   */
  public void setSparseFeature(ConcatVector vector, String featureName, String index, double value) {
    vector.setSparseComponent(ensureFeature(featureName), ensureSparseFeature(featureName, index), value);
  }

  /**
   * This prints out a ConcatVector by mapping to the namespace, to make debugging learning algorithms easier.
   *
   * @param vector the vector to print
   * @param bw     the output stream to write to
   */
  public void debugVector(ConcatVector vector, BufferedWriter bw) throws IOException {
    for (Map.Entry<String, Integer> entry : featureToIndex.entrySet()) {
      String key = entry.getKey();
      bw.write(key);
      bw.write(":\n");
      int i = entry.getValue();
      if (vector.isComponentSparse(i)) {
        debugFeatureValue(key, vector.getSparseIndex(i), vector, bw);
      } else {
        double[] arr = vector.getDenseComponent(i);
        for (int j = 0; j < arr.length; j++) {
          debugFeatureValue(key, j, vector, bw);
        }
      }
    }
  }

  /**
   * This writes a feature's individual value, using the human readable name if possible, to a StringBuilder
   */
  private void debugFeatureValue(String feature, int index, ConcatVector vector, BufferedWriter bw) throws IOException {
    bw.write("\t");
    if (sparseFeatureIndex.containsKey(feature) && sparseFeatureIndex.get(feature).values().contains(index)) {
      // we can map this index to an interpretable string, so we do
      bw.write(reverseSparseFeatureIndex.get(feature).get(index));
    } else {
      // we can't map this to a useful string, so we default to the number
      bw.write(Integer.toString(index));
    }
    bw.write(": ");
    bw.write(Double.toString(vector.getValueAt(featureToIndex.get(feature), index)));
    bw.write("\n");
  }
}
