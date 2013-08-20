package edu.stanford.nlp.ie.hmm;

import edu.stanford.nlp.process.Feature;
import edu.stanford.nlp.process.FeatureValue;
import edu.stanford.nlp.process.NumAndCapFeature;

import java.io.Serializable;
import java.text.NumberFormat;
import java.util.HashMap;
import java.util.Iterator;

/**
 * This maps from the featural representation of unseen tokens to a count
 * or probability for them.  Initially one puts in counts for (pseudo) unseen
 * word types, with a weight, one then calls <code>setProb()</code>,
 * and then one can get (pseudo) probabilities
 * back for any type according to the unseen map.
 * <p/>
 * <i>Below are some implementation notes</i>
 * <p/>
 * The main substantive question is what probability to assign to an
 * unseen <i>token</i> given that the vocabulary is unbounded.  This code
 * was trying to answer
 * answer this question by assuming that over feature equivalence classes,
 * the average basic probability of an unseen type will be the same as
 * that of a token seen once.  These numbers will be scaled by the
 * seen/unseen probability of the state.  Providing the seen probability is
 * greater than 0.5, this will further reduce the unseen token probability.
 * However, that didn't seem to work very well.  It's now modeled via the
 * number of types seen in the entire data.  It's a probability per class
 * multiplied by numBuckets / numSeenWordTypes.
 *
 * @author Jim McFadden
 * @author Christopher Manning
 */
final class FeatureMap implements Serializable {

  /**
   * This hashmap stays small -- just space of feature values.
   * Could make this an array
   */
  private HashMap counts;
  private double total;
  private boolean calculated = false;
  private double unseenProb;  // the prob given an unseen Feature combination
  private Feature f;

  private static final boolean DEBUG_FEATUREMAP = false;

  public FeatureMap(Feature f) {
    counts = new HashMap();
    total = 0.0;
    this.f = f;
  }


  public void addToCount(String s, double d) {
    if (calculated) {
      System.err.println("Adding after calculating, not good.");
      return;
    }

    FeatureValue fv = f.getValue(s);
    Double val = (Double) counts.get(fv);

    if (val == null) {
      counts.put(fv, new Double(d));
    } else {
      counts.put(fv, new Double(val.doubleValue() + d));
    }

    total += d;
  }


  public double getTotal() {
    return total;
  }


  public void setProbs(double stateGeneratedTokens) {
    if (!calculated) {
      if (DEBUG_FEATUREMAP) {
        System.out.println("State generated tokens = " + stateGeneratedTokens + "; numValues = " + f.numValues());
      }
      Iterator it = counts.keySet().iterator();
      // System.out.println("Calculating feature map");
      while (it.hasNext()) {
        FeatureValue fv = (FeatureValue) it.next();
        if (DEBUG_FEATUREMAP) {
          Double val = (Double) counts.get(fv);  /// just debugging
          System.out.println(fv + " counts " + val + " total " + total + " prob " + calculateProb(fv, stateGeneratedTokens));
        }
        counts.put(fv, new Double(calculateProb(fv, stateGeneratedTokens)));
      }
      // calculate the prob for an unseen feature, using null key
      unseenProb = calculateProb(null, stateGeneratedTokens);
      calculated = true;
    }
  }


  /**
   * Get a pseudo probability for an unseen word at runtime.
   * When this is called, the counts Map stores pseudo probabilities for
   * feature combinations that were "seen" in held-out data.
   */
  public double getProb(String s) {
    if (!calculated) {
      System.err.println("FeatureMap: probs not calculated, bad");
      return 0.0;
    }
    Double d = (Double) counts.get(f.getValue(s));
    if (d != null) {
      return d.doubleValue();
    } else {
      return unseenProb;
    }
  }

  /**
   * Get the probability for a particular feature type
   */
  public double getProb(FeatureValue fv) {
    Double d = (Double) counts.get(fv);
    if (d != null) {
      return d.doubleValue();
    } else {
      return 0.0;
    }
  }


  /**
   * Add-k constant for unseen feature estimation in smoothing
   */
  private static final double lidstone = 0.5;

  /**
   * The idea here is that probability estimates from these unseen features
   * are much too high relative to rare word estimates, so we reduce them.
   * That is, we want to have a factor for P(wordtype|feature), but we don't
   * and without it, just P(feature) is far too high [small multinomial] vs.
   * P(rareWord) [big multinomial].
   * This is a parameter that one should be able to optimize automatically,
   * but we simply set it.  Note that a good value is effected by how we
   * do smoothing, etc.  Currently, a value around 100 seems about right....
   * Now disused, and calculated per state.
   */
  private static final double UNSEEN_REDUCTION_FACTOR = 100.0;


  private double calculateProb(FeatureValue fv, double stateGeneratedTokens) {
    double m = 0.0;
    Double val = (Double) counts.get(fv);
    if (val != null) {
      m = val.doubleValue();
    }

    // we use add-k type smoothing
    // (mass + k) / ( total + k *numFeatureBuckets)

    // It used to be like this:
    // where k is chosen so that 20% of mass is given away uniformly
    // k = .25 (total / buckets)
    // but this is awful: insensitive to how many data counts we have seen
    // now it's being done as plain add-k smoothing, trying k = 0.5
    // this should be okay, as the number of buckets is modest
    // double k = 0.25 * total / buckets;

    double numValues = f.numValues();

    // was: (so: new one makes it bigger if numSeenTokens is small, and
    // smaller if numSeenTokens is more than about 40)
    // return ((m + lidstone)/(total + lidstone * buckets)) /
    //       UNSEEN_REDUCTION_FACTOR;
    // or
    // return lidstoneProb * buckets / numSeenTokens;
    double lidstoneProb = (m + lidstone) / (total + lidstone * numValues);
    // return lidstoneProb * buckets / corpusSeenTypes;
    // Teg: HERE!
    return lidstoneProb / (stateGeneratedTokens * UNSEEN_REDUCTION_FACTOR);
  }


  /**
   * Show the behavior of the unseen word module.  You pass it arguments
   * which are interpreted as training until it sees "-test", and then it
   * interprets the rest as test arguments whose probability should be
   * given.  For example, <br><code>
   * java edu.stanford.nlp.ie.hmm.FeatureMap [-f featureClass] 3 7 21 ABC
   * -test house 33 IBM
   * </code>
   *
   * @param args Command line arguments, as above
   */
  public static void main(String[] args) {
    if (args.length == 0) {
      System.err.println("usage: java edu.stanford.nlp.ie.hmm.FeatureMap [-f featureClass] 3 7 21 ABC -test house 33 IBM");
      System.exit(0);
    }
    Feature feature = new NumAndCapFeature();
    int i = 0;
    if (args.length > 1 && args[0].equals("-f")) {
      try {
        feature = (Feature) Class.forName(args[1]).newInstance();
      } catch (Exception e) {
        System.err.println(e);
      }
      i += 2;
    }
    FeatureMap fm = new FeatureMap(feature);
    for (; i < args.length; i++) {
      if (args[i].equals("-test")) {
        break;
      } else {
        fm.addToCount(args[i], 1.0);
      }
    }
    fm.setProbs(i);
    i++;
    NumberFormat nf = NumberFormat.getNumberInstance();
    nf.setMaximumFractionDigits(8);
    nf.setMinimumFractionDigits(8);
    for (; i < args.length; i++) {
      double gp = fm.getProb(args[i]);
      System.out.println(args[i] + ":\t" + fm.f.getValue(args[i]) + "\tprob = " + nf.format(gp));
    }
  }

  private static final long serialVersionUID = -6886447607970484004L;

}
