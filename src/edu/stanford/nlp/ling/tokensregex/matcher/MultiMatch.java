package edu.stanford.nlp.ling.tokensregex.matcher;

import edu.stanford.nlp.util.StringUtils;

import java.util.List;

/**
 * Represent multimatches
 *
 * @author Angel Chang
 */
public class MultiMatch<K,V> extends Match<K,V> {
  List<List<K>> multimatched;
  List<V> multivalues;

  public MultiMatch() {}

  public MultiMatch(List<K> matched, V value, int begin, int end, List<List<K>> multimatched, List<V> multivalues) {
    this.matched = matched;
    this.value = value;
    this.begin = begin;
    this.end = end;
    this.multimatched = multimatched;
    this.multivalues = multivalues;
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

    MultiMatch that = (MultiMatch) o;

    if (multimatched != null ? !multimatched.equals(that.multimatched) : that.multimatched != null) return false;
    if (multivalues != null ? !multivalues.equals(that.multivalues) : that.multivalues != null) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = super.hashCode();
    result = 31 * result + (multimatched != null ? multimatched.hashCode() : 0);
    result = 31 * result + (multivalues != null ? multivalues.hashCode() : 0);
    return result;
  }

  public String toString() {
    StringBuilder sb = new StringBuilder();
    if (multimatched != null && multivalues != null) {
      sb.append("[" + StringUtils.join(multimatched, "-") + "]");
      sb.append(" -> ").append(StringUtils.join(multivalues, "-"));
      sb.append(" at (").append(begin);
      sb.append(",").append(end).append(")");
    } else {
      sb.append(super.toString());
    }
    return sb.toString();
  }
}
