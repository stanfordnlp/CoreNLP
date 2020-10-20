package edu.stanford.nlp.ling;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Basic implementation of Datum interface that can be constructed with a
 * Collection of features and one more more labels. The features must be
 * specified
 * at construction, but the labels can be set and/or changed later.
 *
 * @author Joseph Smarr (jsmarr@stanford.edu)
 * @author Sarah Spikes (sdspikes@cs.stanford.edu) (Templatization)
 *
 * @param <LabelType> The type of the labels in the Dataset
 * @param <FeatureType> The type of the features in the Dataset
 */
public class BasicDatum<LabelType, FeatureType> implements Datum<LabelType, FeatureType> {

  /**
   * features for this Datum
   */
  @SuppressWarnings({"NonSerializableFieldInSerializableClass"})
  private final Collection<FeatureType> features;

  /**
   * labels for this Datum. Invariant: always non-null
   */
  @SuppressWarnings({"NonSerializableFieldInSerializableClass"})
  private final List<LabelType> labels = new ArrayList<>();

  /**
   * Constructs a new BasicDatum with the given features and labels.
   */
  public BasicDatum(Collection<FeatureType> features, Collection<LabelType> labels) {
    this(features);
    setLabels(labels);
  }

  /**
   * Constructs a new BasicDatum with the given features and label.
   */
  public BasicDatum(Collection<FeatureType> features, LabelType label) {
    this(features);
    setLabel(label);
  }

  /**
   * Constructs a new BasicDatum with the given features and no labels.
   */
  public BasicDatum(Collection<FeatureType> features) {
    this.features = features;
  }

  /**
   * Constructs a new BasicDatum with no features or labels.
   */
  public BasicDatum() {
    this(null);
  }

  /**
   * Returns the collection that this BasicDatum was constructed with.
   */
  public Collection<FeatureType> asFeatures() {
    return (features);
  }

  /**
   * Returns the first label for this Datum, or null if none have been set.
   */
  public LabelType label() {
    return ((labels.size() > 0) ?  labels.get(0) : null);
  }

  /**
   * Returns the complete List of labels for this Datum, which may be empty.
   */
  public Collection<LabelType> labels() {
    return labels;
  }

  /**
   * Removes all currently assigned Labels for this Datum then adds the
   * given Label.
   * Calling <tt>setLabel(null)</tt> effectively clears all labels.
   */
  public void setLabel(LabelType label) {
    labels.clear();
    addLabel(label);
  }

  /**
   * Removes all currently assigned labels for this Datum then adds all
   * of the given Labels.
   */
  public void setLabels(Collection<LabelType> labels) {
    this.labels.clear();
    if (labels != null) {
      this.labels.addAll(labels);
    }
  }

  /**
   * Adds the given Label to the List of labels for this Datum if it is not
   * null.
   */
  public void addLabel(LabelType label) {
    if (label != null) {
      labels.add(label);
    }
  }

  /**
   * Returns a String representation of this BasicDatum (lists features and labels).
   */
  @Override
  public String toString() {
    return ("BasicDatum[features=" + asFeatures() + ",labels=" + labels() + "]");
  }


  /**
   * Returns whether the given Datum contains the same features as this Datum.
   * Doesn't check the labels, should we change this?
   */
  @SuppressWarnings("unchecked")
  @Override
  public boolean equals(Object o) {
    if (!(o instanceof Datum)) {
      return (false);
    }

    Datum<LabelType, FeatureType> d = (Datum<LabelType, FeatureType>) o;
    return features.equals(d.asFeatures());
  }

  public int hashCode() {
    return features.hashCode();
  }

  private static final long serialVersionUID = -4857004070061779966L;

}

