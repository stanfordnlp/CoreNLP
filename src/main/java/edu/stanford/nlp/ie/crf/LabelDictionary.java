package edu.stanford.nlp.ie.crf; 
import edu.stanford.nlp.util.logging.Redwood;

import java.io.Serializable;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.stats.Counters;
import edu.stanford.nlp.util.Generics;
import edu.stanford.nlp.util.HashIndex;
import edu.stanford.nlp.util.Index;

/**
 * Constrains test-time inference to labels observed in training.
 *
 * @author Spence Green
 *
 */
public class LabelDictionary implements Serializable  {

  /** A logger for this class */
  private static Redwood.RedwoodChannels log = Redwood.channels(LabelDictionary.class);

  private static final long serialVersionUID = 6790400453922524056L;

  private final boolean DEBUG = false;

  /**
   * Initial capacity of the bookkeeping data structures.
   */
  private final int DEFAULT_CAPACITY = 30000;

  // Bookkeeping
  private Counter<String> observationCounts;
  private Map<String,Set<String>> observedLabels;

  // Final data structure
  private Index<String> observationIndex;
  private int[][] labelDictionary;

  /**
   * Constructor.
   */
  public LabelDictionary() {
    this.observationCounts = new ClassicCounter<>(DEFAULT_CAPACITY);
    this.observedLabels = Generics.newHashMap(DEFAULT_CAPACITY);
  }

  /**
   * Increment counts for an observation/label pair.
   *
   * @param observation
   * @param label
   */
  public void increment(String observation, String label) {
    if (labelDictionary != null) {
      throw new RuntimeException("Label dictionary is already locked.");
    }
    observationCounts.incrementCount(observation);
    if ( ! observedLabels.containsKey(observation)) {
      observedLabels.put(observation, new HashSet<>());
    }
    observedLabels.get(observation).add(label.intern());
  }

  /**
   * True if this observation is constrained, and false otherwise.
   */
  public boolean isConstrained(String observation) {
    return observationIndex.indexOf(observation) >= 0;
  }

  /**
   * Get the allowed label set for an observation.
   *
   * @param observation
   * @return The allowed label set, or null if the observation is unconstrained.
   */
  public int[] getConstrainedSet(String observation) {
    int i = observationIndex.indexOf(observation);
    return i >= 0 ? labelDictionary[i] : null;
  }

  /**
   * Setup the constrained label sets and free bookkeeping resources.
   *
   * @param threshold
   * @param labelIndex
   */
  public void lock(int threshold, Index<String> labelIndex) {
    if (labelDictionary != null) throw new RuntimeException("Label dictionary is already locked");
    log.info("Label dictionary enabled");
    System.err.printf("#observations: %d%n", (int) observationCounts.totalCount());
    Counters.retainAbove(observationCounts, threshold);
    Set<String> constrainedObservations = observationCounts.keySet();
    labelDictionary = new int[constrainedObservations.size()][];
    observationIndex = new HashIndex<>(constrainedObservations.size());
    for (String observation : constrainedObservations) {
      int i = observationIndex.addToIndex(observation);
      assert i < labelDictionary.length;
      Set<String> allowedLabels = observedLabels.get(observation);
      labelDictionary[i] = new int[allowedLabels.size()];
      int j = 0;
      for (String label : allowedLabels) {
        labelDictionary[i][j++] = labelIndex.indexOf(label);
      }
      if (DEBUG) {
        System.err.printf("%s : %s%n", observation, allowedLabels.toString());
      }
    }
    observationIndex.lock();
    System.err.printf("#constraints: %d%n", labelDictionary.length);

    // Free bookkeeping data structures
    observationCounts = null;
    observedLabels = null;
  }
}
