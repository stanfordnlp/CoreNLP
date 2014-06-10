package edu.stanford.nlp.ling.tokensregex.matcher;

/**
* Exact match cost function
*
* @author Angel Chang
*/
public final class ExactMatchCost<K> implements MatchCostFunction<K> {
  @Override
  public double cost(K k1, K k2) {
    if (k1 != null) {
      return (k1.equals(k2))? 0:1;
    } else if (k2 != null) {
      return (k2 == null)? 0:1;
    } else return 0;
  }

  @Override
  public double cost(K k) {
    return 0;
  }

  @Override
  public double cost(int n) {
    return 0;
  }
}
