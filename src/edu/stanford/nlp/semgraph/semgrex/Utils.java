package edu.stanford.nlp.semgraph.semgrex;

import java.util.List;

/**
 * Refactored a useful semgrex key comparison, so as to allow multiple places to use it
 *
 * @author John Bauer
 */
public class Utils {
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
}
