// Stanford Classifier - a multiclass maxent classifier
// GradientDiscountedLogLinearClassifier
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
// along with this program; if not, write to the Free Software
// Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
//
// For more information, bug reports, fixes, contact:
//    Christopher Manning
//    Dept of Computer Science, Gates 1A
//    Stanford CA 94305-9010
//    USA
//    Support/Questions: java-nlp-user@lists.stanford.edu
//    Licensing: java-nlp-support@lists.stanford.edu
//    http://www-nlp.stanford.edu/software/classifier.shtml

package edu.stanford.nlp.classify;

import edu.stanford.nlp.ling.Datum;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.stats.Counters;

import java.util.Collection;

/**
 * A Log-linear classifier that returns scores after
 * adjusting weights according to the gradient
 * of the log-linear objective.
 *
 * @author Galen Andrew
 */
public class GradientDiscountedLogLinearClassifier<L, F> implements Classifier<L, F> {

  /**
   * 
   */
  private static final long serialVersionUID = 1546827958928525578L;
  private final LinearClassifier<L, F> inner;
  private final double alpha;
  private boolean classification; // is it a classifier for classification rather than id
  public static int totalCalls;
  public static int argMaxChanged;

  public GradientDiscountedLogLinearClassifier(LinearClassifier<L, F> inner, double alpha) {
    this.inner = inner;
    this.alpha = alpha;
    Collection<L> possible = inner.labels();
    classification = false;
    if (possible.size() > 2) {
      classification = true;
    }
    System.err.println("created model for classification " + classification);
  }

  public Collection<L> labels() {
    return inner.labels();
  }

  public L classOf(Datum<L, F> example) {
    return Counters.argmax(scoresOf(example));
  }

  public Counter<L> scoresOf(Datum<L, F> example) {
    if (example.label().equals("NONE") && classification) {
      return inner.scoresOf(example);
    }
    Counter<L> probs = inner.probabilityOf(example);

    totalCalls++;
    Counter<L> scores = inner.scoresOf(example);
    Object oldMaxLabel = Counters.argmax(scores);


    Collection<F> feats = example.asFeatures();
    int nf = feats.size();
    for (L label : probs.keySet()) {
      double indicator = label.equals(example.label()) ? 1.0 : 0.0;
      double adjustment = alpha * nf * (indicator - probs.getCount(label));
      double oldScore = scores.getCount(label);
      double newScore = oldScore - adjustment;
      if (Math.random() < .001) {
        System.err.println("old score " + oldScore + " adjustment " + adjustment + " label " + label + " example label " + example.label());
      }
      scores.setCount(label, newScore);
      //score += inner.weight(feature, label) - adjustment;
    }
    Object newMaxLabel = Counters.argmax(scores);
    if (oldMaxLabel.equals(newMaxLabel)) {
      argMaxChanged++;
    }
    return scores;
  }
}
