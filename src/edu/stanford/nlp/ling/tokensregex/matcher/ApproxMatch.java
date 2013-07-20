package edu.stanford.nlp.ling.tokensregex.matcher;

import edu.stanford.nlp.util.StringUtils;

import java.util.List;

/**
* Represents an approximate match with a cost
*
* @author Angel Chang
*/
public class ApproxMatch<K,V> extends MultiMatch<K,V> {
  double cost;

  public double getCost() {
    return cost;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    if (!super.equals(o)) return false;

    ApproxMatch that = (ApproxMatch) o;

    if (Double.compare(that.cost, cost) != 0) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = super.hashCode();
    long temp;
    temp = Double.doubleToLongBits(cost);
    result = 31 * result + (int) (temp ^ (temp >>> 32));
    return result;
  }

  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("(");
    sb.append(super.toString());
    sb.append(",").append(cost).append(")");
    return sb.toString();
  }
}
