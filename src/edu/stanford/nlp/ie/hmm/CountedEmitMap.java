package edu.stanford.nlp.ie.hmm;

import edu.stanford.nlp.stats.ClassicCounter;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.PrintWriter;
import java.io.Serializable;
import java.text.NumberFormat;
import java.util.Iterator;

/**
 * Keep the emission distribution (map) and local vocabulary (vocab) for the
 * state. Use count to record
 * how many times this state is been visited so far. The count will be used to
 * calculate the weights during the state merge
 *
 * @author Haoyi Wang, Zhen Yin
 * @see HMMLearner
 */
public class CountedEmitMap extends AbstractEmitMap implements Serializable {

  /**
   *
   */
  private static final long serialVersionUID = -4204798202147785958L;
  private ClassicCounter<String> counter;
  private ClassicCounter<String> vocab;
  private double count;


  /**
   * Default constructor
   */
  public CountedEmitMap() {
  }


  /**
   * Construct a new CountedEmitMap from an emission distribution
   *
   * @param start A small multinomial distribution
   */
  public CountedEmitMap(ClassicCounter<String> start) {
    counter = new ClassicCounter<String>(start);
  }


  /**
   * Construct a new CountedEmitMap from a CountedEmitMap
   *
   * @param source Source EmitMap
   */
  public CountedEmitMap(CountedEmitMap source) {
    counter = new ClassicCounter<String>(source.getCounter());
    vocab = new ClassicCounter<String>(source.getVocab());
    count = source.getCount();
  }


  /**
   * An easy-to-use constructor for hand specifying a small multinomial
   * distribution.
   *
   * @param observations The emission events
   * @param probs        The paired probabilities of the emission events
   */
  public CountedEmitMap(String[] observations, double[] probs) {
    if (observations.length != probs.length) {
      throw new IllegalArgumentException("Array lengths differ");
    }
    counter = new ClassicCounter<String>();
    for (int i = 0; i < observations.length; i++) {
      set(observations[i], probs[i]);
    }
  }


  /**
   * Coustruct a new CountedEmitMap from word counts.
   *
   * @param observations The emission events
   * @param counts       Word counts
   */

  public CountedEmitMap(String[] observations, int[] counts) {
    if (observations.length != counts.length) {
      throw new IllegalArgumentException("Array lengths differ");
    }

    counter = new ClassicCounter<String>();
    vocab = new ClassicCounter<String>();
    count = 0;

    for (int i = 0; i < observations.length; i++) {
      set(observations[i], counts[i]);
      count += counts[i];
    }

    for (int i = 0; i < observations.length; i++) {
      set(observations[i], (counts[i] / count));
    }
  }


  /**
   * Merge two emission distributions.
   *
   * @param other Another merge candidate
   * @return the ratio between the two states (used for weights)
   */

  public double merge(CountedEmitMap other) {
    if (other.getCount() == 0.0) {
      throw new IllegalArgumentException("Empty CountedEmitMap");
    }

    double ratio = other.getCount() / count;
    //new count
    count += other.getCount();
    Iterator<String> iter = other.getVocab().keySet().iterator();

    while (iter.hasNext()) {
      // adds each count from new vocab, merging as needed
      String key = iter.next();
      double currentCount = vocab.getCount(key);
      double addedCount = other.getVocab().getCount(key);
      double newCount = currentCount + addedCount;
      set(key, newCount);
    }

    //normalize the probability
    iter = vocab.keySet().iterator();

    while (iter.hasNext()) {
      String key = iter.next();
      double c = vocab.getCount(key);
      set(key, c / count);
    }

    return ratio;
  }


  /**
   * Get the stored value (count/probability) for this token.
   *
   * @param in A word
   * @return The probability for this word
   */
  public double get(String in) {
    double o = counter.getCount(in);
    if (o == 0.0) {
      return 0.001;
    } else {
      return o;
    }
  }


  /**
   * to save space, we delete records that map to zero
   */
  public void consolidate() {
    ClassicCounter<String> newMap = new ClassicCounter<String>();
    // int saveCount = 0;

    for (String s : counter.keySet()) {
      double d = counter.getCount(s);
      if (d != 0.0) {
        newMap.setCount(s, d);
      } else {
        // saveCount++;
      }
    }
    counter = newMap;
  }


  private void writeObject(ObjectOutputStream out) throws IOException {
    consolidate();
    out.defaultWriteObject();
  }


  /**
   * Set the probability for a word
   *
   * @param in The word
   * @param d  The probability of the word
   */
  public void set(String in, double d) {
    if (d != 0.0) {
      counter.setCount(in, d);
    } else {
      counter.remove(in);
    }
  }


  /**
   * Set the count for a word
   *
   * @param in The word
   * @param c  The count of the word
   */
  public void set(String in, int c) {
    if (c != 0) {
      vocab.setCount(in, c);
    } else {
      vocab.remove(in);
    }
  }


  /**
   * Return the emission distribution map
   */
  public ClassicCounter<String> getCounter() {
    return counter;
  }

  public void printUnseenEmissions(PrintWriter out, NumberFormat nf) {
  }


  /**
   * Return the word count of this state.
   *
   * @return The word count of this state
   */
  public double getCount() {
    return count;
  }


  /**
   * Return the local vocabulary.
   *
   * @return The local vocabulary
   */

  public ClassicCounter<String> getVocab() {
    return vocab;
  }

}
