package edu.stanford.nlp.ling.tokensregex.matcher;

import edu.stanford.nlp.util.HasInterval;
import edu.stanford.nlp.util.Interval;
import edu.stanford.nlp.util.StringUtils;

import java.util.List;

/**
* Represent a matched span over sequence of elements
*
* @author Angel Chang
*/
public class Match<K,V> implements HasInterval<Integer>
{
  /* List of elements that were actually matched */
  List<K> matched;
  /* Value corresponding to the matched span */
  V value;
  /* Start offset of the span */
  int begin;
  /* End offset of the span */
  int end;
  Object customMatchObject;  // Custom match object
  transient Interval<Integer> span;

  public Match() {}

  public Match(List<K> matched, V value, int begin, int end) {
    this.matched = matched;
    this.value = value;
    this.begin = begin;
    this.end = end;
  }

  public List<K> getMatched() {
    return matched;
  }

  public int getMatchedLength() {
    return (matched != null)? matched.size():0;
  }

  public V getValue() {
    return value;
  }

  public int getBegin() {
    return begin;
  }

  public int getEnd() {
    return end;
  }

  public Object getCustom() {
    return customMatchObject;
  }

  public void setCustom(Object customMatchObject) {
    this.customMatchObject = customMatchObject;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    Match match = (Match) o;

    if (begin != match.begin) return false;
    if (end != match.end) return false;
    if (matched != null ? !matched.equals(match.matched) : match.matched != null) return false;
    if (value != null ? !value.equals(match.value) : match.value != null) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = matched != null ? matched.hashCode() : 0;
    result = 31 * result + begin;
    result = 31 * result + end;
    return result;
  }

  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("[" + ((matched != null)? StringUtils.join(matched, " - "):"") + "]");
    sb.append(" -> ").append(value);
    sb.append(" at (").append(begin);
    sb.append(",").append(end).append(")");
    return sb.toString();
  }

  public Interval<Integer> getInterval() {
    if (span == null) span = Interval.toInterval(begin, end, Interval.INTERVAL_OPEN_END);
    return span;
  }
}
