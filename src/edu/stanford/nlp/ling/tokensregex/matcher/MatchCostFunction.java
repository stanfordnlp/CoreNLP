package edu.stanford.nlp.ling.tokensregex.matcher;

import java.util.List;

/**
 * Represents the cost of a match
 *
 * @author Angel Chang
 */
public interface MatchCostFunction<K,V> {
  // pairwise cost of replacing k1 with k2 at position n
  public double cost(K k1, K k2, int n);

  // cost of adding the sequence k with value v to the match
  public double multiMatchDeltaCost(List<K> k, V v, List<Match<K,V>> prevMultiMatch, List<Match<K,V>> curMultiMatch);

  // pairwise cost of replacing k1 with k2,k3 at position n
  //public double cost(K k1, K k2, K k3, int n);

  public static abstract class AbstractMatchCostFunction<K,V> implements MatchCostFunction<K,V> {
    // pairwise cost of replacing k1 with k2,k3 at position n
    public double cost(K k1, K k2, int n) { return 0; }
    public double cost(K k1, K k2, K k3, int n) { return 0; }
    public double multiMatchDeltaCost(List<K> k, V v, List<Match<K,V>> prevMultiMatch, List<Match<K,V>> curMultiMatch) { return 0; }
  }
}
