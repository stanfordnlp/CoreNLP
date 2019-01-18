// Stanford Classifier - a multiclass maxent classifier
// NaiveBayesClassifier
// Copyright (c) 2003-2007 The Board of Trustees of
// The Leland Stanford Junior University. All Rights Reserved.
//
// This program is free software; you can redistribute it and/or
// modify it under the terms of the GNU General Public License
// as published by the Free Software Foundation; either version 2
// of the License, or (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with this program.  If not, see http://www.gnu.org/licenses/ .
//
// For more information, bug reports, fixes, contact:
//    Christopher Manning
//    Dept of Computer Science, Gates 2A
//    Stanford CA 94305-9020
//    USA
//    Support/Questions: java-nlp-user@lists.stanford.edu
//    Licensing: java-nlp-support@lists.stanford.edu
//    https://nlp.stanford.edu/software/classifier.html

package edu.stanford.nlp.classify;

import edu.stanford.nlp.ling.Datum;
import edu.stanford.nlp.ling.RVFDatum;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.stats.Counters;
import edu.stanford.nlp.util.Pair;

import java.io.PrintStream;
import java.util.Iterator;
import java.util.Set;
import java.util.Collection;


import edu.stanford.nlp.util.logging.Redwood;

/**
 * A Naive Bayes classifier with a fixed number of features.
 * The features are assumed to have integer values even though RVFDatum will return doubles.
 *
 * @author Kristina Toutanova (kristina@cs.stanford.edu)
 * @author Sarah Spikes (sdspikes@cs.stanford.edu) - Templatization.  Not sure what the weights counter
 *         is supposed to hold; given the weights function it seems to hold {@code Pair<Pair<L, F>, Object>}
 *         but this seems like a strange thing to expect to be passed in.
 */
public class NaiveBayesClassifier<L, F> implements Classifier<L, F>, RVFClassifier<L, F> {

  private static final long serialVersionUID = 1544820342684024068L;

  private Counter<Pair<Pair<L, F>, Number>> weights; //the keys will be class and feature and value
  private Counter<L> priors;
  private Set<F> features; // we need all features to add the weights for zero-valued ones
  private boolean addZeroValued; // whether to add features as having value 0 if they are not in Datum/RFVDatum
  private Counter<L> priorZero; //if we need to add the zeros, pre-compute the weight for all zeros for each class
  private Set<L> labels;
  private final Integer zero = Integer.valueOf(0);

  private static final Redwood.RedwoodChannels logger = Redwood.channels(NaiveBayesClassifier.class);

  public Collection<L> labels() {
    return labels;
  }

  public L classOf(RVFDatum<L, F> example) {
    Counter<L> scores = scoresOf(example);
    return Counters.argmax(scores);
  }

  public ClassicCounter<L> scoresOf(RVFDatum<L, F> example) {
    ClassicCounter<L> scores = new ClassicCounter<>();
    Counters.addInPlace(scores, priors);
    if (addZeroValued) {
      Counters.addInPlace(scores, priorZero);
    }
    for (L l : labels) {
      double score = 0.0;
      Counter<F> features = example.asFeaturesCounter();
      for (F f : features.keySet()) {
        int value = (int) features.getCount(f);
        score += weight(l, f, Integer.valueOf(value));
        if (addZeroValued) {
          score -= weight(l, f, zero);
        }
      }
      scores.incrementCount(l, score);
    }
    return scores;
  }


  public L classOf(Datum<L, F> example) {
    RVFDatum<L, F> rvf = new RVFDatum<>(example);
    return classOf(rvf);
  }

  public ClassicCounter<L> scoresOf(Datum<L, F> example) {
    RVFDatum<L, F> rvf = new RVFDatum<>(example);
    return scoresOf(rvf);
  }

  public NaiveBayesClassifier(Counter<Pair<Pair<L, F>, Number>> weights, Counter<L> priors, Set<L> labels, Set<F> features, boolean addZero) {
    this.weights = weights;
    this.features = features;
    this.priors = priors;
    this.labels = labels;
    addZeroValued = addZero;
    if (addZeroValued) {
      initZeros();
    }
  }


  public float accuracy(Iterator<RVFDatum<L, F>> exampleIterator) {
    int correct = 0;
    int total = 0;
    for (; exampleIterator.hasNext();) {
      RVFDatum<L, F> next = exampleIterator.next();
      L guess = classOf(next);
      if (guess.equals(next.label())) {
        correct++;
      }
      total++;
    }
    logger.info("correct " + correct + " out of " + total);
    return correct / (float) total;
  }

  public void print(PrintStream pw) {
    pw.println("priors ");
    pw.println(priors.toString());
    pw.println("weights ");
    pw.println(weights.toString());
  }

  public void print() {
    print(System.out);
  }

  private double weight(L label, F feature, Number val) {
    Pair<Pair<L, F>, Number> p = new Pair<>(new Pair<>(label, feature), val);
    double v = weights.getCount(p);
    return v;
  }

  public NaiveBayesClassifier(Counter<Pair<Pair<L, F>, Number>> weights, Counter<L> priors, Set<L> labels) {
    this(weights, priors, labels, null, false);
  }

  /**
   * In case the features for which there is a value 0 in an example need to have their coefficients multiplied in,
   * we need to pre-compute the addition
   * priorZero(l)=sum_{features} wt(l,feat=0)
   */
  private void initZeros() {
    priorZero = new ClassicCounter<>();
    for (L label : labels) {
      double score = 0;
      for (F feature : features) {
        score += weight(label, feature, zero);
      }
      priorZero.setCount(label, score);
    }
  }

}
