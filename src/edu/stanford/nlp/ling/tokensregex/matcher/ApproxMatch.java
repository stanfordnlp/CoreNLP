package edu.stanford.nlp.ling.tokensregex.matcher;

import edu.stanford.nlp.util.Interval;
import edu.stanford.nlp.util.StringUtils;

import java.util.List;

/**
* Represents an approximate match with a cost
*
* @author Angel Chang
*/
public class ApproxMatch<K,V> extends MultiMatch<K,V> {
  double cost;
  Interval<Integer>[] alignments;  // Tracks alignments from original sequence to matched sequence (null indicates not aligned)

  public ApproxMatch() {
  }

  public ApproxMatch(List<K> matched, V value, int begin, int end, double cost) {
    this.matched = matched;
    this.value = value;
    this.begin = begin;
    this.end = end;
    this.cost = cost;
  }

  public ApproxMatch(List<K> matched, V value, int begin, int end, List<Match<K,V>> multimatches, double cost) {
    this.matched = matched;
    this.value = value;
    this.begin = begin;
    this.end = end;
    this.multimatches = multimatches;
    this.cost = cost;
  }

  public ApproxMatch(List<K> matched, V value, int begin, int end, List<Match<K,V>> multimatches, double cost, Interval[] alignments) {
    this.matched = matched;
    this.value = value;
    this.begin = begin;
    this.end = end;
    this.multimatches = multimatches;
    this.cost = cost;
    this.alignments = alignments;
  }

  public double getCost() {
    return cost;
  }

  public Interval<Integer>[] getAlignments() {
    return alignments;
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
    sb.append(",").append(cost);
    if (alignments != null) {
      sb.append(", [").append(StringUtils.join(alignments, ", ")).append("]");
    }
    sb.append(")");
    return sb.toString();
  }
}
