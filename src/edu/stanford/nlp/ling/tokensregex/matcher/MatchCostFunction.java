package edu.stanford.nlp.ling.tokensregex.matcher;

import java.util.List;

/**
 * Represents the cost of a match
 *
 * @author Angel Chang
 */
public interface MatchCostFunction<K,V> {
  // pairwise cost of replacing k1 with k2
  public double cost(K k1, K k2);

  // cost of adding the sequence as the nth sequence
  public double multiMatchDeltaCost(List<K> k, V v, int n);
}
