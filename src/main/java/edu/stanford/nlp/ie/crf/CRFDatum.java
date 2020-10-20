package edu.stanford.nlp.ie.crf;

import edu.stanford.nlp.ling.Datum;

import java.io.Serializable;
import java.util.Arrays;
import java.util.List;


/**
 * The representation of Datums used internally in CRFClassifier.
 *
 * @author Jenny Finkel
 */

public class CRFDatum<FEAT,LAB> implements Serializable {

  /**
   * Features for this Datum.
   */
  @SuppressWarnings({"NonSerializableFieldInSerializableClass"})
  private final List<FEAT> features;
  @SuppressWarnings({"NonSerializableFieldInSerializableClass"})
  private final LAB label;
  @SuppressWarnings({"NonSerializableFieldInSerializableClass"})
  // featureVals holds the (optional) feature value for non-boolean features
  // such as the ones used in continuous vector space embeddings
  private final List<double[]> featureVals;

  /**
   * Constructs a new BasicDatum with the given features and label.
   *
   * @param features The features of the CRFDatum
   * @param label The label of the CRFDatum
   */
  public CRFDatum(List<FEAT> features, LAB label, List<double[]> featureVals) {
    this.features = features;
    this.label = label;
    this.featureVals = featureVals;
  }

  /**
   * Returns the collection that this BasicDatum was constructed with.
   *
   * @return the collection that this BasicDatum was constructed with.
   */
  public List<FEAT> asFeatures() {
    return features;
  }

  /**
   * Returns the double array containing the feature values.
   *
   * @return The double array that contains the feature values matching each feature as
   *         returned by {@code asFeatures()}
   */
  public List<double[]> asFeatureVals() {
    return featureVals;
  }


  /**
   * Returns the label for this Datum, or null if none have been set.
   * @return The label for this Datum, or null if none have been set.
   */

  public LAB label() {
    return label;
  }

  /**
   * Returns a String representation of this BasicDatum (lists features and labels).
   */
  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder(10000).append("CRFDatum[\n") //
    .append("    label=").append(label).append('\n');
    for (int i = 0, sz = features.size(); i < sz; i++) {
      sb.append("    features(").append(i).append("):").append(features.get(i)) //
      .append(", val=").append(Arrays.toString(featureVals.get(i))) //
      .append('\n');
    }
    return sb.append(']').toString();
  }


  /**
   * Returns whether the given Datum contains the same features as this Datum.
   * Doesn't check the labels, should we change this?
   * (CDM Feb 2012: Also doesn't correctly respect the contract for equals,
   * since it gives one way equality with other Datum's.)
   *
   * @param o The object to test equality with
   * @return Whether it is equal to this CRFDatum in terms of features
   */
  @Override
  public boolean equals(Object o) {
	// FIXME: what about labels, featureVals?
    return o instanceof Datum && features.equals(((Datum<?, ?>) o).asFeatures());
  }

  @Override
  public int hashCode() {
    return features.hashCode();
  }

  private static final long serialVersionUID = -8345554365027671190L;

}

