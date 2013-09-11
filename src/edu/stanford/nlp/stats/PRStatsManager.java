package edu.stanford.nlp.stats;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Lets you store many PrecisionRecallStats by name and perform averages.
 * Maintains a Map from unique String names to Lists of PrecisionRecallStats
 * objects. This might represent multiple folds of an experiment for various
 * classes (or target fields) but it can be used generally. Then you can do
 * macro-averaging with {@link #getAggregateStats} or micro-averaging with
 * getAveragePrecision/Recall/FMeasure.
 *
 * @author Joseph Smarr (jsmarr@stanford.edu)
 * @author Sarah Spikes (sdspikes@cs.stanford.edu) (Templatization)
 *
 * @param <L> The type of the names
 */
public class PRStatsManager<L> {
  /**
   * Name (L) -> List of PrecisionRecallStats
   */
  protected Map<L, List<PrecisionRecallStats>> statListsByName = null;

  /**
   * Creates a new (empty) PRStatsManager.
   */
  public PRStatsManager() {
    clearAllStats();
  }

  /**
   * Adds the given stats to the list of stats being kept under the given key.
   * Creates a new list if this name has never been used before.
   */
  public void addStats(L name, PrecisionRecallStats stats) {
    List<PrecisionRecallStats> statList = statListsByName.get(name);
    if (statList == null) {
      statList = new ArrayList<PrecisionRecallStats>();
    }
    statList.add(stats);
    statListsByName.put(name, statList);
  }

  /**
   * Removes all stats associated with the given name.
   */
  public void clearStats(L name) {
    statListsByName.remove(name);
  }

  /**
   * Removes all stats associated with all names.
   */
  public void clearAllStats() {
    statListsByName = new HashMap<L, List<PrecisionRecallStats>>();
  }

  /**
   * Returns a new PrecisionRecallStats that is the sum of all the current
   * individual PrecisionRecallStats kept for the given name. This is useful
   * for macro-averaging across several folds.
   * If no stats have been maintained for this name, this will return an empty
   * PrecisionRecallStats (but not null).
   */
  public PrecisionRecallStats getAggregateStats(String name) {
    PrecisionRecallStats aggregateStats = new PrecisionRecallStats();
    List<PrecisionRecallStats> statList = statListsByName.get(name);
    if (statList != null) {
      for (int i = 0; i < statList.size(); i++) {
        aggregateStats.addCounts(statList.get(i));
      }
    }
    return (aggregateStats);
  }

  /**
   * Returns the List of PrecisionRecallStats kept for the given name, or
   * <tt>null</tt> if no stats have been kept for this name. Use this method
   * if you want direct access to the individual stats (as opposed to overall
   * sums or averages, which the other methods provide.
   */
  public List<PrecisionRecallStats> getStatsList(String name) {
    return statListsByName.get(name);
  }

  /**
   * Returns the average precision of all stats kept for the given name.
   * This is a micro-average since precision is separately assesses for each
   * of the stored stats. Returns 0.0 if no stats are kept for this name.
   */
  public double getAveragePrecision(String name) {
    return (getAverageScore(name, "getPrecision"));
  }

  /**
   * Returns the average recall of all stats kept for the given name.
   * This is a micro-average since precision is separately assesses for each
   * of the stored stats. Returns 0.0 if no stats are kept for this name.
   */
  public double getAverageRecall(String name) {
    return (getAverageScore(name, "getRecall"));
  }

  /**
   * Returns the average F1 of all stats kept for the given name.
   * This is a micro-average since precision is separately assesses for each
   * of the stored stats. Returns 0.0 if no stats are kept for this name.
   */
  public double getAverageFMeasure(String name) {
    return (getAverageScore(name, "getFMeasure"));
  }

  /**
   * Returns the average score for all stats stored under the given name using
   * the given scoring method. The scoringMethodName should be one of
   * "getPrecision", "getRecall", or "getFMeasure" (F1 is assumed).
   * Return 0.0 if there are no stats stored for this name or if an invalid
   * scoring method name is provided.
   */
  private double getAverageScore(String name, String scoringMethodName) {
    double average = 0.0;

    try {
      Method scoringMethod = PrecisionRecallStats.class.getMethod(scoringMethodName);
      List<PrecisionRecallStats> statList = statListsByName.get(name);
      if (statList != null) {
        for (int i = 0; i < statList.size(); i++) {
          // invoke method on each stats and sum total score
          PrecisionRecallStats stats = statList.get(i);
          Double score = (Double) scoringMethod.invoke(stats);
          average += score.doubleValue();
        }
        average /= statList.size();
      }
    } catch (Exception e) {
      e.printStackTrace();
    }

    return (average);
  }
  
  /**
   * Returns set of all names for handled stats.
   */
  public Collection<L> getNames() {
    return statListsByName.keySet();
  }

  /**
   * For internal debugging purposes only.
   */
  public static void main(String[] args) {
    PRStatsManager<String> manager = new PRStatsManager<String>();
    manager.addStats("a", new PrecisionRecallStats(5, 8, 1));
    manager.addStats("a", new PrecisionRecallStats(2, 0, 5));
    manager.addStats("b", new PrecisionRecallStats(7, 8, 2));

    System.err.println(manager.getStatsList("a"));
    System.err.println(manager.getStatsList("b"));
    System.err.println(manager.getStatsList("c"));

    System.out.println(manager.getAggregateStats("a"));
    System.out.println(manager.getAggregateStats("b"));
    System.out.println(manager.getAggregateStats("c"));

    System.out.println(manager.getAveragePrecision("a"));
    System.out.println(manager.getAveragePrecision("b"));
    System.out.println(manager.getAveragePrecision("c"));

    System.out.println(manager.getAverageRecall("a"));
    System.out.println(manager.getAverageRecall("b"));
    System.out.println(manager.getAverageRecall("c"));

    System.out.println(manager.getAverageFMeasure("a"));
    System.out.println(manager.getAverageFMeasure("b"));
    System.out.println(manager.getAverageFMeasure("c"));

  }
}
