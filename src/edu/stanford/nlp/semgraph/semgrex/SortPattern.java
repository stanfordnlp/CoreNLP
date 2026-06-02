package edu.stanford.nlp.semgraph.semgrex;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import edu.stanford.nlp.ling.IndexedWord;
import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.semgraph.SemanticGraphEdge;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.Pair;
import edu.stanford.nlp.util.VariableStrings;

/**
 * At semgrex creation time, this takes a list of nodes or attributes.
 *<br>
 * At batch processing time, this pares a list of matches down to
 * one match for each matching attributes.
 */
public class SortPattern extends SemgrexPattern  {
  private static final long serialVersionUID = -3843276786L;

  private final SemgrexPattern child;
  private final List<String> keys;
  private final boolean reverse;

  public SortPattern(SemgrexPattern child, List<String> keys, boolean reverse) {
    this.child = child;
    this.keys = new ArrayList<>(keys);
    this.reverse = reverse;
  }

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

  static class KeyPairComparator implements Comparator<Pair<Integer, List<String>>> {
    public int compare(Pair<Integer, List<String>> first, Pair<Integer, List<String>> second) {
      return compareKeys(first.second, second.second);
    }
  }

  /**
   * Sort sentences by how they matched the keys in the ::sort operation
   *<br>
   * In the case of multiple matches for a single sentence, this chooses the lowest key
   */
  public List<Pair<CoreMap, List<SemgrexMatch>>> postprocessMatches(List<Pair<CoreMap, List<SemgrexMatch>>> matches, boolean keepEmptyMatches) {
    List<Pair<CoreMap, List<SemgrexMatch>>> filteredMatches = new ArrayList<>();
    for (Pair<CoreMap, List<SemgrexMatch>> sentence : matches) {
      if (sentence.second().size() > 0 || keepEmptyMatches) {
        filteredMatches.add(sentence);
      }
    }

    List<Pair<Integer, List<String>>> sentenceKeys = new ArrayList<>();
    for (int idx = 0; idx < filteredMatches.size(); ++idx) {
      Pair<CoreMap, List<SemgrexMatch>> sentence = filteredMatches.get(idx);
      List<String> key = null;
      for (SemgrexMatch match : sentence.second()) {
        List<String> newKey = buildKey(match, keys);
        if (compareKeys(newKey, key) < 0) {
          key = newKey;
        }
      }
      sentenceKeys.add(new Pair<>(idx, key));
    }

    if (this.reverse) {
      Collections.sort(sentenceKeys, Collections.reverseOrder(new KeyPairComparator()));
    } else {
      Collections.sort(sentenceKeys, new KeyPairComparator());
    }

    List<Pair<CoreMap, List<SemgrexMatch>>> finalMatches = new ArrayList<>();
    for (int idx = 0; idx < sentenceKeys.size(); ++idx) {
      Pair<Integer, List<String>> key = sentenceKeys.get(idx);
      finalMatches.add(filteredMatches.get(key.first));
    }

    return finalMatches;
  }

  boolean isSorted() {
    return true;
  }

  @Override
  public String localString() {
    return toString(true, false);
  }

  @Override
  public String toString() {
    return toString(true, true);
  }

  @Override
  public String toString(boolean hasPrecedence) {
    return toString(hasPrecedence, true);
  }

  @Override
  public void setChild(SemgrexPattern n) {
    throw new UnsupportedOperationException("Child should only be set on a SortPattern at creation time");
  }

  @Override
  public List<SemgrexPattern> getChildren() {
    if (child == null) {
      return Collections.emptyList();
    } else {
      return Collections.singletonList(child);
    }
  }

  public String toString(boolean hasPrecedence, boolean addChild) {
    StringBuilder sb = new StringBuilder();
    if (addChild) {
      sb.append(child.toString(true));
    }
    if (this.reverse) {
      sb.append(" :: rsort");
    } else {
      sb.append(" :: sort");
    }
    for (String key : keys) {
      sb.append(" ");
      sb.append(key);
    }
    return sb.toString();
  }

  @Override
  public SemgrexMatcher matcher(SemanticGraph sg, IndexedWord node,
                                Map<String, IndexedWord> namesToNodes,
                                Map<String, String> namesToRelations,
                                Map<String, SemanticGraphEdge> namesToEdges,
                                VariableStrings variableStrings,
                                boolean ignoreCase) {
    return child.matcher(sg, node, namesToNodes, namesToRelations, namesToEdges, variableStrings, ignoreCase);
  }

  @Override
  public SemgrexMatcher matcher(SemanticGraph sg,
                                Alignment alignment, SemanticGraph sg_align,
                                boolean hyp, IndexedWord node,
                                Map<String, IndexedWord> namesToNodes,
                                Map<String, String> namesToRelations,
                                Map<String, SemanticGraphEdge> namesToEdges,
                                VariableStrings variableStrings,
                                boolean ignoreCase) {
    return child.matcher(sg, alignment, sg_align, hyp, node, namesToNodes, namesToRelations, namesToEdges, variableStrings, ignoreCase);
  }
}
