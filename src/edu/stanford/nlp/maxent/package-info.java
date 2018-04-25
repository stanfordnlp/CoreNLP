/**
 * This package deals with defining and solving maximum entropy problems.
 * In the future it will have facilities for easier definition of maxent problems.
 * <p>
 * If you are new to this package, take a look at the following classes:
 * <ul>
 * <li>LinearType2Classifier
 * <li>Type2Datum
 * <li>Type2Dataset
 * </ul>
 * Possibly the simplest way to use it is to fill up a Type2Dataset with
 * Type2Datum objects (a Type2Datum is essentially a map from classes
 * into sets of feature values), and then to use
 * {@code LinearType2Classifier.trainClassifier()} on your
 * Type2Dataset to train a classifier.  You can then use the
 * {@code classOf()}, {@code scoresOf()}, and
 * {@code justificationOf()} methods of the resulting
 * LinearType2Classifier object.
 */
package edu.stanford.nlp.maxent;
