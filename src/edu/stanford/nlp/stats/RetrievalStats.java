package edu.stanford.nlp.stats;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import edu.stanford.nlp.ling.Datum;
import edu.stanford.nlp.util.DefaultValuedMap;
import edu.stanford.nlp.util.Function;

public class RetrievalStats {

  /**
   * Calculate the average precision of the results retrieved for a query.
   * 
   * @param relevant The set of items that are truly relevant to the query.
   * @param results  The list of items that were retrieved for the query.
   * @return         The average precision.
   */
  public static <T> double averagePrecision(final Set<T> relevant, List<T> results) {
    if (relevant.size() == 0) {
      throw new IllegalArgumentException("relevant set must be non-empty");
    }
    Function<T, Boolean> isRelevant = new Function<T, Boolean>() {
      public Boolean apply(T result) {
        return relevant.contains(result);
      }
    };
    return averagePrecision(results, isRelevant, relevant.size());
  }
  
  /**
   * Calculate the average precision of the results retrieved for a query.
   * 
   * @param data          The Datums representing the items retrieved for the
   *                      query. A Datum should have a true label if it is
   *                      relevant, and a false label otherwise.
   * @param totalRelevant The total number of items that are truly relevant to
   *                      the query. If null, it will be assumed that all the
   *                      total number of relevant items is the same as the
   *                      number of Datums with true labels.
   * @return              The average precision.
   */
  public static <D extends Datum<Boolean, ?>> double averagePrecision(
      Iterable<D> data, Integer totalRelevant) {
    Function<D, Boolean> isRelevant = new Function<D, Boolean>() {
      public Boolean apply(D item) {
        return item.label();
      }
    };
    return averagePrecision(data, isRelevant, totalRelevant);
  }
  
  /**
   * Calculate the average precision of the results retrieved for a query.
   * 
   * @param items         The retrieved items.
   * @param isRelevant    A function indicating which items are relevant.
   * @param totalRelevant The total number of relevant items, or null if
   *                      the total number of relevant items is the same as
   *                      the number of items where isRelevant returns true.
   * @return              The average precision.
   */
  public static <T> double averagePrecision(
      Iterable<T> items, Function<T, Boolean> isRelevant, Integer totalRelevant) {
    double value = 0.0;
    int rank = 0;
    double found = 0.0;
    for (T item: items) {
      ++ rank;
      if (isRelevant.apply(item)) {
        ++ found;
        value += found / rank;
      }
    }
    double size = totalRelevant == null ? found : totalRelevant;
    return value / size;
  }
  
  /**
   * Calculate PageRank scores for each node in the graph. Default values will be
   * supplied for the number of iterations and the damping factor.
   * 
   * @param <T>           The node type.
   * @param outLinks      A Map from nodes to the nodes they link to. Note that there
   *                      should be a key for each node in the graph, even if that
   *                      node links to no other nodes.
   * @return              A Counter containing PageRank scores for each node. 
   */
  public static <T> Counter<T> pageRanks(Map<T, ? extends Collection<T>> outLinks) {
    return pageRanks(outLinks, 100, 0.85);
  }

  /**
   * Calculate PageRank scores for each node in the graph.
   * 
   * @param <T>           The node type.
   * @param outLinks      A Map from nodes to the nodes they link to. Note that there
   *                      should be a key for each node in the graph, even if that
   *                      node links to no other nodes.
   * @param iterations    The number of iterations of the PageRank algorithm to run.
   * @param dampingFactor The PageRank damping factor, roughly, the fraction of the score
   *                      which should be determined by link structure.
   * @return              A Counter containing PageRank scores for each node. 
   */
  public static <T> Counter<T> pageRanks(Map<T, ? extends Collection<T>> outLinks,
      int iterations, double dampingFactor) {
    
    // collect in-links, counts of out-links per node and nodes with no out-links
    Map<T, Integer> outLinkSizes = new HashMap<T, Integer>();
    Set<T> noOutLinkNodes = new HashSet<T>();
    Map<T, Set<T>> inLinks = DefaultValuedMap.hashSetValuedMap();
    for (Map.Entry<T, ? extends Collection<T>> entry: outLinks.entrySet()) {
      T node = entry.getKey();
      Collection<T> targetNodes = entry.getValue();
      
      // identify nodes with no out-links
      if (targetNodes.size() == 0) {
        noOutLinkNodes.add(node);
      }
      
      // store size of out-link set for each node
      outLinkSizes.put(node, targetNodes.size());
      
      // build in-links map
      for (T targetNode: targetNodes) {
        if (!outLinks.containsKey(targetNode)) {
          String format = "found an edge to a node which was not a key in the map: %s";
          throw new RuntimeException(String.format(format, targetNode));
        }
        inLinks.get(targetNode).add(node);
      }
    }
    
    // start all nodes with PageRank 1/N
    int nodeCount = outLinks.size();
    Counter<T> pageRanks = new ClassicCounter<T>();
    for (T node: outLinks.keySet()) {
      pageRanks.setCount(node, 1.0 / nodeCount);
    }
    
    // iteratively update PageRank values
    for (int i = 0; i < iterations; ++ i) {
      Counter<T> newRanks = new ClassicCounter<T>();
      
      // calculate the contribution of the nodes with no outgoing lists
      double noReferenceSum = 0;
      for (T node: noOutLinkNodes) {
        noReferenceSum += pageRanks.getCount(node) / nodeCount;
      }
      
      // determine new PageRank scores for each node based on the nodes
      // linking to them (including the "sink" node adjustment above)
      for (T node: outLinks.keySet()) {
        double sum = noReferenceSum;
        for (T sourceNode: inLinks.get(node)) {
          int sourceOutLinkCount = outLinkSizes.get(sourceNode);
          sum += pageRanks.getCount(sourceNode) / sourceOutLinkCount; 
        }
        double pageRank = (1 - dampingFactor) / nodeCount + dampingFactor * sum;
        newRanks.setCount(node, pageRank);
      }
      
      // replace the last iteration's PageRank scores with the new scores
      pageRanks = newRanks;
    }
    
    // return the PageRank scores
    return pageRanks;
  }
  
}
