package edu.stanford.nlp.ie.crf;

import java.io.Serializable;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.util.Generics;
import edu.stanford.nlp.util.Index;

/**
 * Constrains test-time inference to labels observed in training.
 * 
 * @author Spence Green
 *
 */
public class LabelDictionary implements Serializable {

  private static final long serialVersionUID = 6790400453922524056L;

  private final int DEFAULT_CAPACITY = 30000;

  private final boolean DEBUG = true;
  
  // Bookkeeping
  private Counter<String> observationCounts;
  private Map<String,Set<String>> observedLabels;

  // Final data structure
  private Map<String,Set<Integer>> labelDictionary;
  
  /**
   * Constructor.
   */
  public LabelDictionary() {
    this.observationCounts = new ClassicCounter<String>(DEFAULT_CAPACITY);
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
      observedLabels.put(observation, new HashSet<String>());
    }
    observedLabels.get(observation).add(label.intern());
  }

  /**
   * True if this observation is constrained, and false otherwise.
   * 
   * @param observation
   * @return
   */
  public boolean isConstrained(String observation) {
    return labelDictionary.containsKey(observation);
  }

  /**
   * Get the allowed label set for an observation.
   * 
   * @param observation
   * @return The allowed label set, or null if the observation is unconstrained.
   */
  public Set<Integer> getConstrainedSet(String observation) {
    return labelDictionary.containsKey(observation) ? labelDictionary.get(observation) : null;
  }

  /**
   * Setup the constrained label sets and free bookkeeping resources.
   * 
   * @param threshold
   * @param classIndex 
   */
  public void lock(int threshold, Index<String> classIndex) {
    if (labelDictionary != null) throw new RuntimeException("Dictionary is already locked");
    if (DEBUG) {
      System.err.println("Label Dictionary Status:");
      System.err.printf("# Observations: %d%n", (int) observationCounts.totalCount());
    }
    
    labelDictionary = new HashMap<String,Set<Integer>>();
    for (String observation : observationCounts.keySet()) {
      if (observationCounts.getCount(observation) >= threshold) {
        Set<Integer> allowedLabelIds = Generics.newHashSet();
        Set<String> allowedLabels = observedLabels.get(observation);
        for (String label : allowedLabels) {
          allowedLabelIds.add(classIndex.indexOf(label));
        }
        labelDictionary.put(observation, allowedLabelIds);
        if (DEBUG) {
          System.err.printf("%s : %s%n", observation, allowedLabels.toString());
        }
      } 
    }
    if (DEBUG) {
      System.err.printf("#constraints: %d%n", labelDictionary.keySet().size());
    }
    // Free bookkeeping data structures
    observationCounts = null;
    observedLabels = null;
  }
}
