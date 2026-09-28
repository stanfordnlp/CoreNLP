package edu.stanford.nlp.semgraph.semgrex;

import java.io.Serializable;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import edu.stanford.nlp.ling.IndexedWord;
import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.semgraph.SemanticGraphEdge;
import edu.stanford.nlp.util.VariableStrings;

/**
 * Stores the results of a single match.
 *<br>
 * This is useful for keeping track of a SemgrexMatcher after already processing its result.
 * In particular, it will be possible to post-process results after coordinating all of the results.
 * For example, results can be sorted or uniqed by the matching nodes, as long as all of the results
 * are already compiled
 */

public class SemgrexMatch implements Serializable  {
  private static final long serialVersionUID = 978254376856L;

  /** The pattern which produced this match */
  final SemgrexPattern matchedPattern;

  /** The graph the matcher was searching when the match was found */
  final SemanticGraph sg;
  /** Copy of the matcher's named nodes */
  final Map<String, IndexedWord> namesToNodes;
  /** Copy of the matcher's named relations */
  final Map<String, String> namesToRelations;
  /** Copy of the matcher's named edges */
  final Map<String, SemanticGraphEdge> namesToEdges;
  /** Copy of the matcher's variable group strings */
  final VariableStrings variableStrings;

  /** Copy of the alignment being matched across, or null if there is none */
  final Alignment alignment;
  /** The default graph on the other side of the alignment, or null if there is none */
  final SemanticGraph sg_aligned;
  /** Whether the match was made on the side the alignment maps from */
  final boolean hyp;

  /** The node which matched the root of the pattern */
  final IndexedWord match;

  /**
   * Records the current match of {@code matcher}, copying its named
   * nodes, relations, edges, and variables.
   *
   * @param pattern The pattern which produced the match
   * @param matcher The matcher, which should currently be at a match
   */
  public SemgrexMatch(SemgrexPattern pattern, SemgrexMatcher matcher) {
    matchedPattern = pattern;
    sg = matcher.graphs.get(matcher.getGraph());
    namesToNodes = new HashMap<>(matcher.namesToNodes);
    namesToRelations = new HashMap<>(matcher.namesToRelations);
    namesToEdges = new HashMap<>(matcher.namesToEdges);
    variableStrings = new VariableStrings(matcher.variableStrings);
    if (matcher.graphs.alignment != null) {
      alignment = new Alignment(matcher.graphs.alignment);
    } else {
      alignment = null;
    }
    sg_aligned = matcher.graphs.crossAlignment() == null ? null : matcher.graphs.crossAlignment().getDefault();
    hyp = matcher.graphs.mapsFrom();
    match = matcher.getMatch();
  }

  /**
   * Returns the node which matched the root of the pattern.
   *
   * @return The matched node
   */
  public IndexedWord getMatch() {
    return match;
  }

  /**
   * Returns the node matched with the given name.
   *
   * @param name The node name
   * @return The named node, or null if there is none
   */
  public IndexedWord getNode(String name) {
    return namesToNodes.get(name);
  }

  /**
   * Returns the names of the named nodes in this match.
   *
   * @return A view of the node names
   */
  public Set<String> getNodeNames() {
    return namesToNodes.keySet();
  }

  /**
   * Returns the names of the named relations in this match.
   *
   * @return A view of the relation names
   */
  public Set<String> getRelationNames() {
    return namesToRelations.keySet();
  }

  /**
   * Returns the relation matched with the given name.
   *
   * @param name The relation name
   * @return The matched relation, or null if there is none
   */
  public String getRelnString(String name) {
    return namesToRelations.get(name);
  }

  /**
   * Returns the names of the named edges in this match.
   *
   * @return A view of the edge names
   */
  public Set<String> getEdgeNames() {
    return namesToEdges.keySet();
  }

  /**
   * Returns the edge matched with the given name.
   *
   * @param name The edge name
   * @return The named edge, or null if there is none
   */
  public SemanticGraphEdge getEdge(String name) {
    return namesToEdges.get(name);
  }

  /**
   * Returns the names of the variable groups set in this match.
   *
   * @return A new sorted list of variable names
   */
  public Collection<String> getVariableNames() {
    return variableStrings.getVariableNames();
  }

  /**
   * Returns the string matched by a variable group.
   *
   * @param var The variable name
   * @return The variable's string, or null if it is not set
   */
  public String getVariableString(String var) {
    return variableStrings.getString(var);
  }

  public String toString() {
    StringBuilder builder = new StringBuilder();
    builder.append(matchedPattern);
    builder.append("\n");
    builder.append(sg);
    builder.append("\n");
    for (Map.Entry<String, IndexedWord> entry : namesToNodes.entrySet()) {
      builder.append(entry.getKey() + " matched at " + entry.getValue() + "\n");
    }
    for (Map.Entry<String, SemanticGraphEdge> entry : namesToEdges.entrySet()) {
      builder.append(entry.getKey() + " matched at " + entry.getValue() + "\n");
    }
    return builder.toString();
  }
}
