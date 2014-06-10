package edu.stanford.nlp.ling.tokensregex.matcher;

import edu.stanford.nlp.util.StringUtils;

import java.util.List;

/**
* Represents an approximate match with a cost
*
* @author Angel Chang
*/
public class ApproxMatch<K,V> extends Match<K,V> {
  double cost;

  // TODO: These should be moved away...
  List<List<K>> multimatched;
  List<V> multivalues;

  public double getCost() {
    return cost;
  }

  public List<List<K>> getMultimatched() {
    return multimatched;
  }

  public List<V> getMultivalues() {
    return multivalues;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    if (!super.equals(o)) return false;

    ApproxMatch that = (ApproxMatch) o;

    if (Double.compare(that.cost, cost) != 0) return false;
    if (multimatched != null ? !multimatched.equals(that.multimatched) : that.multimatched != null) return false;
    if (multivalues != null ? !multivalues.equals(that.multivalues) : that.multivalues != null) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = super.hashCode();
    long temp;
    temp = Double.doubleToLongBits(cost);
    result = 31 * result + (int) (temp ^ (temp >>> 32));
    result = 31 * result + (multimatched != null ? multimatched.hashCode() : 0);
    result = 31 * result + (multivalues != null ? multivalues.hashCode() : 0);
    return result;
  }

  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("(");
    if (multimatched != null && multivalues != null) {
      sb.append("[" + StringUtils.join(multimatched, "-") + "]");
      sb.append(" -> ").append(StringUtils.join(multivalues, "-"));
      sb.append(" at (").append(begin);
      sb.append(",").append(end).append(")");
    } else {
      sb.append(super.toString());
    }
    sb.append(",").append(cost).append(")");
    return sb.toString();
  }
}
