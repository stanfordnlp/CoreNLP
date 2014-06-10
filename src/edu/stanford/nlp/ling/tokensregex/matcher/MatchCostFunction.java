package edu.stanford.nlp.ling.tokensregex.matcher;

import edu.stanford.nlp.util.Function;
import edu.stanford.nlp.util.Pair;

/**
 * Represents the cost of a match
 *
 * @author Angel Chang
 */
public interface MatchCostFunction<K> {
  // pairwise cost of replacing k1 with k2
  public double cost(K k1, K k2);

  // cost of having using k
  public double cost(K k);

  // cost of adding the nth element
  public double cost(int n);

  ;


}
