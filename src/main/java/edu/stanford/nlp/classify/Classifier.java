package edu.stanford.nlp.classify;

import edu.stanford.nlp.ling.Datum;
import edu.stanford.nlp.ling.RVFDatum;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.util.Pair;

import java.io.Serializable;
import java.util.Collection;

/**
 * A simple interface for classifying and scoring data points, implemented
 * by most of the classifiers in this package.  A basic Classifier
 * works over a List of categorical features.  For classifiers over
 * real-valued features, see {@link RVFClassifier}.
 *
 * @author Dan Klein
 * @author Sarah Spikes (sdspikes@cs.stanford.edu) (Templatization)
 *
 * @param <L> The type of the label(s) in each Datum
 * @param <F> The type of the features in each Datum
 */

public interface Classifier<L, F> extends Serializable {
  public L classOf(Datum<L, F> example);

  public Counter<L> scoresOf(Datum<L, F> example);

  public Collection<L> labels();

  /**
   * Evaluates the precision and recall of this classifier against a dataset, and the target label.
   *
   * @param testData The dataset to evaluate the classifier on.
   * @param targetLabel The target label (e.g., for relation extraction, this is the relation we're interested in).
   * @return A pair of the precision (first) and recall (second) of the classifier on the target label.
   */
  public default Pair<Double, Double> evaluatePrecisionAndRecall(GeneralDataset<L, F> testData, L targetLabel) {
    if (targetLabel == null) {
      throw new IllegalArgumentException("Must supply a target label to compute precision and recall against");
    }
    // Variables to count
    int numCorrectAndTarget = 0;
    int numTargetGuess = 0;
    int numTargetGold = 0;
    // Iterate over dataset
    for (RVFDatum<L, F> datum : testData) {
      // Get the gold label
      L label = datum.label();
      if (label == null) {
        throw new IllegalArgumentException("Cannot compute precision and recall on unlabelled dataset. Offending datum: " + datum);
      }
      // Get the guess label
      L guess = classOf(datum);
      // Compute statistics on datum
      if (label.equals(targetLabel)) {
        numTargetGold += 1;
      }
      if (guess.equals(targetLabel)) {
        numTargetGuess += 1;
        if (guess.equals(label)) {
          numCorrectAndTarget += 1;
        }
      }
    }
    // Aggregate statistics
    double precision = numTargetGuess == 0 ? 0.0 : ((double) numCorrectAndTarget) / ((double) numTargetGuess);
    double recall = numTargetGold == 0 ? 1.0 : ((double) numCorrectAndTarget) / ((double) numTargetGold);
    return Pair.makePair(precision, recall);
  }

  /**
   * Evaluate the accuracy of this classifier on the given dataset.
   *
   * @param testData The dataset to evaluate the classifier on.
   * @return The accuracy of the classifier on the given dataset.
   */
  public default double evaluateAccuracy(GeneralDataset<L, F> testData) {
    int numCorrect = 0;
    for (RVFDatum<L, F> datum : testData) {
      // Get the gold label
      L label = datum.label();
      if (label == null) {
        throw new IllegalArgumentException("Cannot compute precision and recall on unlabelled dataset. Offending datum: " + datum);
      }
      // Get the guess
      L guess = classOf(datum);
      // Compute statistics
      if (label.equals(guess)) {
        numCorrect += 1;
      }
    }
    return ((double) numCorrect) / ((double) testData.size);
  }

}
