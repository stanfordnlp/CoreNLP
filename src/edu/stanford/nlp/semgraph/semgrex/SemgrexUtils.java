package edu.stanford.nlp.semgraph.semgrex;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import edu.stanford.nlp.ling.IndexedWord;
import edu.stanford.nlp.semgraph.SemanticGraphEdge;
import edu.stanford.nlp.util.Pair;

/**
 * Refactored a useful semgrex key comparison, so as to allow multiple places to use it
 *
 * @author John Bauer
 */
public class SemgrexUtils {
  static public int compareKeys(List<String> first, List<String> second) {
    if (first == null && second == null) {
      return 0;
    }
    if (second == null) {
      return -1;
    }
    if (first == null) {
      return 1;
    }
    for (int idx = 0; idx < first.size() && idx < second.size(); ++idx) {
      int cmp = first.get(idx).compareTo(second.get(idx));
      if (cmp != 0) {
        return cmp;
      }
    }
    // what if they are different lengths?
    // shouldn't happen here anyway
    return 0;
  }

  /**
   * For a pattern such as UniqPattern, get this key from the SemgrexMatch.
   *<br>
   * The key is expected to be unambiguous, which can be enforced at SemgrexPattern compilation time
   */
  static public String getKey(SemgrexMatch match, String key) {
    IndexedWord node = match.getNode(key);
    if (node != null) {
      return node.value();
    }
    String varString = match.getVariableString(key);
    if (varString != null) {
      return varString;
    }
    SemanticGraphEdge edge = match.getEdge(key);
    if (edge != null) {
      return edge.getRelation().toString();
    }
    return null;
  }

  static public List<String> buildKey(SemgrexMatch match, List<String> keys) {
    List<String> matchKey = new ArrayList<>();
    for (String key : keys) {
      matchKey.add(getKey(match, key));
    }
    return matchKey;
  }

  static class KeyPairComparator implements Comparator<Pair<Integer, List<String>>> {
    public int compare(Pair<Integer, List<String>> first, Pair<Integer, List<String>> second) {
      return SemgrexUtils.compareKeys(first.second, second.second);
    }
  }
}
