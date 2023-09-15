package edu.stanford.nlp.ling;

import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counter;

import java.util.Collection;
import java.util.Collections;

/**
 * A basic implementation of the Datum interface that can be constructed with a
 * Collection of features and one more more labels. The features must be
 * specified at construction, but the labels can be set and/or changed later.
 *
 * @author Jenny Finkel
 *         <a href="mailto:jrfinkel@stanford.edu">jrfinkel@stanford.edu</a>
 * @author Sarah Spikes (sdspikes@stanford.edu) [templatized]
 *
 * @param <L>
 *          The type of the label of the datum
 * @param <F>
 *          The type of individual features stored in the datum
 */
public class RVFDatum<L, F> implements Datum<L, F> {

  private static final long serialVersionUID = -255312811814660438L;

  /**
   * features for this Datum
   */
  private final Counter<F> features;

  /**
   * labels for this Datum. Invariant: always non-null
   */
  private L label; // = null;

  /**
   * Id of this instance
   */
  private String id = null;
  
  /**
   * Constructs a new RVFDatum with the given features and label.
   */
  public RVFDatum(Counter<F> features, L label) {
    this.features = features;
    setLabel(label);
  }

  /**
   * Constructs a new RVFDatum taking the data from a Datum. <i>Implementation
   * note:</i> This constructor allocates its own counter over features, but is
   * only guaranteed correct if the label and feature names are immutable.
   *
   * @param m The Datum to copy.
   */
  public RVFDatum(Datum<L, F> m) {
    this.features = new ClassicCounter<>();
    for (F key : m.asFeatures()) {
      features.incrementCount(key, 1.0);
    }
    setLabel(m.label());
  }

  /**
   * Constructs a new RVFDatum with the given features and no labels.
   */
  public RVFDatum(Counter<F> features) {
    this.features = features;
  }

  /**
   * Constructs a new RVFDatum with no features or labels.
   */
  public RVFDatum() {
    this((ClassicCounter<F>) null);
  }

  /**
   * Returns the Counter of features and values
   */
  public Counter<F> asFeaturesCounter() {
    return features;
  }

  /**
   * Returns the list of features without values
   */
  public Collection<F> asFeatures() {
    return features.keySet();
  }

  /**
   * Removes all currently assigned Labels for this Datum then adds the given
   * Label. Calling <code>setLabel(null)</code> effectively clears all labels.
   */
  public void setLabel(L label) {
    this.label = label;
  }
  
  /**
   * Sets id for this instance
   * @param id
   */
  public void setID(String id){
    this.id = id;
  }

  /**
   * Returns a String representation of this BasicDatum (lists features and
   * labels).
   */
  @Override
  public String toString() {
    return "RVFDatum[id="+id+", features=" + asFeaturesCounter() + ",label=" + label() + "]";
  }

  public L label() {
    return label;
  }

  public Collection<L> labels() {
    return Collections.singletonList(label);
  }

  public double getFeatureCount(F feature) {
    return features.getCount(feature);
  }
  
  public String id(){
    return id;
  }

  /**
   * Returns whether the given RVFDatum contains the same features with the same
   * values as this RVFDatum. An RVFDatum can only be equal to another RVFDatum.
   * <i>Implementation note:</i> Doesn't check the labels, should we change
   * this?
   */
  @Override
  @SuppressWarnings("unchecked")
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof RVFDatum)) {
      return (false);
    }
    RVFDatum<L, F> d = (RVFDatum<L, F>) o;
    return features.equals(d.asFeaturesCounter());
  }

  /** {@inheritDoc} */
  @Override
  public int hashCode() {
    return features.hashCode();
  }

}
