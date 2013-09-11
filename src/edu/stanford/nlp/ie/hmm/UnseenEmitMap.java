package edu.stanford.nlp.ie.hmm;

import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.util.Filters;
import java.io.PrintWriter;
import java.io.Serializable;
import java.text.NumberFormat;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

/**
 * An emission map that can generate seen and unseen words, via a
 * feature decomposition of the latter.
 * <p/>
 * Implementation note: This effectively uses linear discounting (the seenP
 * factor) for seen words -- it'd be better to move to something like
 * absolute discounting or Good-Turing.
 *
 * @author Jim McFadden
 */
class UnseenEmitMap extends AbstractEmitMap implements Serializable {

  private FeatureMap fmap;
  private EmitMap base;     // this will contain a vocab
  private boolean baseLocked; // whether to only set seenP while tuning params or also update base

  public double seenP;
  /**
   * Smoothed counts to add to both seen and unseen mass for estimating seenP: {@value}.
   */
  public static final double seenSmoothing = 1.0;


  /**
   * Initialize a new <code>UnseenEmitMap</code>.  It starts knowing
   * about the words and probabilities in starter.
   * The seenP is set to 0.99, to make it slightly less dangerous to
   * use this without having changed it.
   */
  public UnseenEmitMap(ClassicCounter starter) {
    this(new PlainEmitMap(starter));
  }

  public UnseenEmitMap(EmitMap b) {
    base = b;
    seenP = 0.99;
    setBaseLocked(false);
  }

  public double get(String s) {
    double baseProb = base.get(s);
    if (baseProb != 0.0) {
      return seenP * baseProb;
    } else {
      return getUnseen(s);
    }
  }

  /**
   * Get probability of word as unseen
   */
  public double getUnseen(String s) {
    return (1.0 - seenP) * featureVal(s);
  }

  public void set(String s, double d) {
    base.set(s, d);
  }

  /**
   * Returns whether base emissions are locked from further updates.
   */
  public boolean isBaseLocked() {
    return (baseLocked);
  }

  /**
   * Sets ehwther base emissions are locked from further updates.
   */
  public void setBaseLocked(boolean baseLocked) {
    this.baseLocked = baseLocked;
  }


  /**
   * Note that you have to have called setFeatureMap on an UnseenEmitMap
   * before this will work.  (At present this is done in HMM.java.)
   *
   * @return The probability for this word according to the feature-based
   *         model (not scaled by seenP)
   */
  private double featureVal(String s) {
    return fmap.getProb(s);
  }

  public void setFeatureMap(FeatureMap newfmap, double stateNumTokens) {
    newfmap.setProbs(stateNumTokens);
    fmap = newfmap;
  }

  /**
   * Has a feature map been assigned to this EmitMap (or is it null)?
   */
  public boolean hasFeatureMap() {
    return fmap != null;
  }

  public EmitMap getBase() {
    return base;
  }

  public ClassicCounter getCounter() {
    return base.getCounter();
  }

  /**
   * return whether this word has been seen previously in this state
   */
  public boolean isSeen(String word) {
    return getCounter().containsKey(word);
  }

  public void printUnseenEmissions(PrintWriter out, NumberFormat nf) {
    out.println();
    if (fmap == null) {
      out.println("UnseenEmitMap not yet calculated");
      return;
    }
    out.println("P(seen) = " + nf.format(seenP));
    out.print("P(xyz) = " + nf.format(featureVal("xyz")));
    out.print("  P(Xyz) = " + nf.format(featureVal("Xyz")));
    out.print("  P(XY) = " + nf.format(featureVal("XY")));
    out.println("  P(gh@bu.edu) = " + nf.format(featureVal("gh@bu.edu")));
    out.print("P(987) = " + nf.format(featureVal("987")));
    out.print("  P(9.8) = " + nf.format(featureVal("9.8")));
    out.print("  P(A8) = " + nf.format(featureVal("A8")));
    out.println("  P(1975) = " + nf.format(featureVal("1975")));
  }

  /**
   * Tunes the seenP to the fraction of expected emissions in the HMM's vocab.
   * If the base map has been locked, seenP is all that gets updated, otherwise
   * the base emit map is set to the (normalized) expected emissions.
   *
   * @see #setBaseLocked
   */
  @Override
  public double tuneParameters(ClassicCounter<String> expectedEmissions, HMM hmm) {
    if (!isBaseLocked()) {
      base.tuneParameters(expectedEmissions, hmm); // set base probs

      Set vocab = new HashSet(base.getCounter().keySet()); // changing base, so can't iterate on it
      for (Iterator iter = vocab.iterator(); iter.hasNext();) {
        // If one keeps on re-estimating words that have probably not been
        // seen in
        // this state, but have been seen in other states, then their
        // estimates will gradually head down to very small numbers.  If
        // their probability is less than half for an unseen of their type,
        // then we should trim them.
        // XXXX Check this more carefully, but we don't want to set
        // words to way low probabilities ... one's far lower than they
        // would get as unseen words
        String s = (String) iter.next();
        if (base.get(s) < getUnseen(s) / 2.0) {
          base.set(s, 0.0);
        }
      }
    }

    // E-step
    double total = 0.0;
    for (String key : expectedEmissions.keySet()) {
      if ((Filters.collectionAcceptFilter(hmm.getVocab().keySet())).accept(key)) {
        total += expectedEmissions.getCount(key);
      }
    }
    double totalSeen = (total) + seenSmoothing;
    double total1 = 0.0;
    for (String key : expectedEmissions.keySet()) {
      if ((Filters.collectionRejectFilter(hmm.getVocab().keySet())).accept(key)) {
        total1 += expectedEmissions.getCount(key);
      }
    }
    double totalUnseen = (total1) + seenSmoothing;
    double newVal = totalSeen / (totalSeen + totalUnseen);

    // M-step
    double change = Math.abs(newVal - seenP);
    seenP = newVal;
    /*
    if (verbose)
      System.err.println("P(seen|state = "+i +") = " +
           nf.format(newVal) +
                       " totalSeen = " + nf.format(totalSeen[i]) +
                       " totalUnseen = " + nf.format(totalUnseen[i]));
     */

    return (change);
  }

  private static final long serialVersionUID = 8649691914904065558L;

}
