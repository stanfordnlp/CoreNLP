package edu.stanford.nlp.classify;

import edu.stanford.nlp.ling.BasicDatum;
import edu.stanford.nlp.ling.Datum;

/**
 * Stores a classified Datum with predicted and correct labels.
 * 
 * @author Sarah Spikes (sdspikes@cs.stanford.edu) (Templatization)
 *
 * @param <L> The type of the labels in the Dataset
 * @param <F> The type of the features in the Dataset
 */
public class ClassifiedDatum<L, F> extends BasicDatum<L, F> {
  /**
   * 
   */
  private static final long serialVersionUID = -9065823265750491154L;
  private final L predictedLabel;

  /**
   * Constructs a new classification result for the given Datum with the given
   * predicted and correct labels.   NOTE: this constructor overrides the correct label in the Datum argument.
   * Don't use this constructor anymore, it's going to vanish.
   *
   * @param datum          Datum that was classified
   * @param predictedLabel label (class) predicted for this Datum by the Classifier
   * @param correctLabel   correct label (class) to compare prediction to
   */
  public ClassifiedDatum(Datum<L, F> datum, L predictedLabel, L correctLabel) {
    super(datum.asFeatures(), correctLabel);
    this.predictedLabel = predictedLabel;
  }

  /**
   * Constructs a new classificationr esult for the given Datum with the given
   * predicted label, and using the datum's label as the correct label.
   *
   * @param datum          Data that was classified (containing correct label)
   * @param predictedLabel label (class) predicted for this Datum by the Classifier
   */
  public ClassifiedDatum(Datum<L, F> datum, L predictedLabel) {
    this(datum, predictedLabel, datum.label());
  }

  /**
   * Returns the label (class) predicted for the Datum by the Classifier.
   */
  public L getPredictedLabel() {
    return (predictedLabel);
  }

  /**
   * Returns whether the predicted label matches the correct label. Throws a NullPointerException if the
   * predicted label is null.
   */
  public boolean isCorrect() {
    return (getPredictedLabel().equals(label()));
  }


  /**
   * Returns a string with the datum, correct label, and predicted label.
   */
  @Override
  public String toString() {
    return ("ClassifiedDatum[features=" + asFeatures() + ",correctLabel=" + label() + ",predictedLabel=" + predictedLabel + "]");
  }

}
