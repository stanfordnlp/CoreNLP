package edu.stanford.nlp.semgraph.semgrex;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import edu.stanford.nlp.ling.IndexedWord;
import edu.stanford.nlp.semgraph.SemanticGraphEdge;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.Pair;
import edu.stanford.nlp.util.VariableStrings;

/**
 * At semgrex creation time, this takes a list of nodes or attributes.
 *<br>
 * At batch processing time, this sorts the sentences by the values of
 * those keys in their matches, as in {@code :: sort foo} or
 * {@code :: rsort foo}.
 */
public class SortPattern extends SemgrexPattern  {
  private static final long serialVersionUID = -3843276786L;

  /** The pattern whose matches get sorted */
  private final SemgrexPattern child;
  /** The node names or attributes used as sort keys */
  private final List<String> keys;
  /** Whether to sort in reverse order ({@code rsort}) */
  private final boolean reverse;

  /**
   * Creates a sort operation over the matches of child.
   *
   * @param child the pattern whose matches get sorted
   * @param keys the node names or attributes used as sort keys; the list is copied
   * @param reverse whether to sort in reverse order
   */
  public SortPattern(SemgrexPattern child, List<String> keys, boolean reverse) {
    this.child = child;
    this.keys = new ArrayList<>(keys);
    this.reverse = reverse;
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
        List<String> newKey = SemgrexUtils.buildKey(match, keys);
        if (SemgrexUtils.compareKeys(newKey, key) < 0) {
          key = newKey;
        }
      }
      sentenceKeys.add(new Pair<>(idx, key));
    }

    if (this.reverse) {
      Collections.sort(sentenceKeys, Collections.reverseOrder(new SemgrexUtils.KeyPairComparator()));
    } else {
      Collections.sort(sentenceKeys, new SemgrexUtils.KeyPairComparator());
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
  public List<SemgrexPattern> getChildren() {
    if (child == null) {
      return Collections.emptyList();
    } else {
      return Collections.singletonList(child);
    }
  }

  /**
   * Returns the pattern as a String, optionally without the child pattern.
   *
   * @param hasPrecedence ignored
   * @param addChild whether to include the child pattern before the {@code :: sort} operation
   * @return the {@code :: sort} or {@code :: rsort} operation and its keys,
   *   preceded by the child pattern if addChild is set
   */
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
  public SemgrexMatcher matcher(SemgrexGraphs graphs, SemgrexGraphName currentGraph,
                                IndexedWord node,
                                Map<String, IndexedWord> namesToNodes,
                                Map<String, String> namesToRelations,
                                Map<String, SemanticGraphEdge> namesToEdges,
                                VariableStrings variableStrings,
                                boolean ignoreCase) {
    return child.matcher(graphs, currentGraph, node, namesToNodes, namesToRelations, namesToEdges, variableStrings, ignoreCase);
  }
}
