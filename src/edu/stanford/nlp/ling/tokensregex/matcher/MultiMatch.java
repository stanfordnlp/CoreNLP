package edu.stanford.nlp.ling.tokensregex.matcher;

import edu.stanford.nlp.util.HasInterval;
import edu.stanford.nlp.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Represent multimatches
 *
 * @author Angel Chang
 */
public class MultiMatch<K,V> extends Match<K,V> {
  List<Match<K,V>> multimatches;

  public MultiMatch() {}

  public MultiMatch(List<K> matched, V value, int begin, int end, List<Match<K,V>> multimatches) {
    this.matched = matched;
    this.value = value;
    this.begin = begin;
    this.end = end;
    this.multimatches = multimatches;
  }

  public List<Match<K,V>> getMultimatches() {
    return multimatches;
  }

  public List<List<K>> getMultimatched() {
    if (multimatches == null) return null;
    List<List<K>> multimatched = new ArrayList<>(multimatches.size());
    for (Match<K,V> m:multimatches) {
      multimatched.add(m.getMatched());
    }
    return multimatched;
  }

  public List<V> getMultivalues() {
    if (multimatches == null) return null;
    List<V> multivalues = new ArrayList<>(multimatches.size());
    for (Match<K,V> m:multimatches) {
      multivalues.add(m.getValue());
    }
    return multivalues;
  }

  // Offsets in the original string to which each multimatch is aligned to
  public List<HasInterval<Integer>> getMultioffsets() {
    if (multimatches == null) return null;
    List<HasInterval<Integer>> multioffsets = new ArrayList<>(multimatches.size());
    for (Match<K,V> m:multimatches) {
      multioffsets.add(m.getInterval());
    }
    return multioffsets;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    if (!super.equals(o)) return false;

    MultiMatch that = (MultiMatch) o;

    if (multimatches != null ? !multimatches.equals(that.multimatches) : that.multimatches != null) return false;

    return true;
  }

  public String toString() {
    StringBuilder sb = new StringBuilder();
    if (multimatches != null) {
      sb.append("[" + StringUtils.join(getMultimatches(), ", ") + "]");
    } else {
      sb.append(super.toString());
    }
    return sb.toString();
  }
}
