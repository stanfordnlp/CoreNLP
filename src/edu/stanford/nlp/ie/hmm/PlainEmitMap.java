package edu.stanford.nlp.ie.hmm;

import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counters;

import java.io.PrintWriter;
import java.io.Serializable;
import java.text.NumberFormat;

/**
 * Simply uses stored Counter as its map.  That is, for words in the
 * Counter, they will have "probability" whatever value was stored
 * in the Counter, and all other words have probability zero.
 * (So, this isn't very safe to use by itself.)
 *
 * @author Jim McFadden
 */
public final class PlainEmitMap extends AbstractEmitMap implements Serializable {

  private ClassicCounter<String> map;

  public PlainEmitMap() {
    this(new ClassicCounter());
  }

  /**
   * Uses a copy of these starting counts. Normalizes them and removes 0s.
   */
  public PlainEmitMap(ClassicCounter start) {
    map = new ClassicCounter<String>();
    Counters.addInPlace(map, start);
    Counters.retainNonZeros(map);
    Counters.normalize(map);
  }


  /**
   * An easy-to-use constructor for hand specifying a small multinomial
   * distribution.
   *
   * @param observations The emission events
   * @param probs        The paired probabilities of the emission events
   */
  public PlainEmitMap(String[] observations, double[] probs) {
    if (observations.length != probs.length) {
      throw new IllegalArgumentException("Array lengths differ");
    }
    map = new ClassicCounter<String>();
    //map = new HashMap(observations.length);
    for (int i = 0; i < observations.length; i++) {
      set(observations[i], probs[i]);
    }
  }


  /**
   * Get the stored value (count/probability) for this token.
   */
  public double get(String in) {
    return (map.getCount(in));
    /*
  Object o = map.get(in);
  if (o == null) {
    return 0.0;
  } else {
    return ((Double) o).doubleValue();
  }
     */
  }


  public void set(String in, double d) {
    if (d != 0.0) {
      map.setCount(in, d);
      //map.put(in, new Double(d) );
    } else {
      map.remove(in);
    }
  }


  public void addTo(String str, double d) {
    if (map.getCount(str) + d == 0) {
      map.remove(str); // don't store 0-counts
    } else {
      map.incrementCount(str, d);
    }
  }

  /**
   * Sets emission probs to the given expected emissions (after normalizing).
   * Returns the max change of any single emission prob.
   */
  @Override
  public double tuneParameters(ClassicCounter expectedEmissions, HMM hmm) {
    ClassicCounter<String> newMap = new ClassicCounter<String>();
    Counters.addInPlace(newMap, expectedEmissions);
    Counters.normalize(newMap);

    // computes max param change
    ClassicCounter<String> changes = new ClassicCounter<String>();
    Counters.addInPlace(changes, map);
    Counters.subtractInPlace(changes, newMap);
    double maxChange = 0;
    for (String str : changes.keySet()) {
      maxChange = Math.max(maxChange, Math.abs(changes.getCount(str)));
    }

    map = newMap; // copies over new emissions

    return maxChange;
  }

  public ClassicCounter getCounter() {
    return map;
  }


  public void printUnseenEmissions(PrintWriter out, NumberFormat nf) {
  }


  private static final long serialVersionUID = -4107771331720888029L;

}
