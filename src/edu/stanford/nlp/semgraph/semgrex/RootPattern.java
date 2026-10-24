package edu.stanford.nlp.semgraph.semgrex; 

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import edu.stanford.nlp.ling.IndexedWord;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.Pair;
import edu.stanford.nlp.util.VariableStrings;

/** 
 * A root level pattern for storing final information about the SemgrexPattern,
 * such as the known edge names used in the pattern
 *
 * TODO: reassess if it would actually be more useful to just keep
 * this information in the children
 *
 * @author John Bauer
 */
public class RootPattern extends SemgrexPattern  {
  /** The pattern this root wraps */
  final SemgrexPattern child;
  /** The node names used in the pattern */
  final Set<String> knownVariables;
  /** The variable group names used in the pattern */
  final Set<String> knownVarGroups;
  /** The edge names used in the pattern */
  final Set<String> knownEdges;

  public List<SemgrexPattern> getChildren() {
    return Collections.singletonList(child);
  }

  public Set<String> getKnownVariables() {
    return knownVariables;
  }

  public Set<String> getKnownVarGroups() {
    return knownVarGroups;
  }

  public Set<String> getKnownEdges() {
    return knownEdges;
  }

  public String localString() {
    return child.localString();
  }

  public String toString() {
    return child.toString();
  }

  public String toString(boolean hasPrecedence) {
    return child.toString(hasPrecedence);
  }

  public SemgrexMatcher matcher(SemgrexGraphs graphs, SemgrexGraphName currentGraph,
                                IndexedWord node, Map<String, IndexedWord> namesToNodes, Map<String, String> namesToRelations,
                                Map<String, MatchedEdge> namesToEdges,
                                VariableStrings variableStrings, boolean ignoreCase) {
    return child.matcher(graphs, currentGraph, node, namesToNodes, namesToRelations, namesToEdges, variableStrings, ignoreCase);
  }

  public List<Pair<CoreMap, List<SemgrexMatch>>> postprocessMatches(List<Pair<CoreMap, List<SemgrexMatch>>> matches, boolean keepEmptyMatches) {
    return child.postprocessMatches(matches, keepEmptyMatches);
  }

  /**
   * Wraps the pattern the parser built, with the names it found.
   *<br>
   * A node, a regex group, and an edge may not share a name, as in
   * {@code {lemma:/(.*)/#1%foo} >nsubj=foo {}}.  Such names have nothing
   * to do with each other, but sharing one suggests otherwise, and a tool
   * which looks the name up, such as {@code ::uniq foo}, would find only
   * one of them.
   *
   * @throws SemgrexParseException if two of a node, a regex group, and an edge have the same name
   */
  RootPattern(SemgrexPattern child, Set<String> knownVariables, Set<String> knownVarGroups, Set<String> knownEdges) {
    if (child == null) {
      throw new IllegalArgumentException("Cannot wrap a null SemgrexPattern");
    }
    checkNames(knownVariables, "a node", knownVarGroups, "a regex group");
    checkNames(knownVariables, "a node", knownEdges, "an edge");
    checkNames(knownVarGroups, "a regex group", knownEdges, "an edge");
    this.child = child;
    this.knownVariables = Collections.unmodifiableSet(knownVariables);
    this.knownVarGroups = Collections.unmodifiableSet(knownVarGroups);
    this.knownEdges = Collections.unmodifiableSet(knownEdges);
  }

  /** Rejects a name which is in both sets */
  private static void checkNames(Set<String> first, String firstKind, Set<String> second, String secondKind) {
    for (String name : first) {
      if (second.contains(name)) {
        throw new SemgrexParseException("Semgrex pattern uses " + name + " as the name of both " + firstKind + " and " + secondKind +
                                        ", which is very confusing, as the two have nothing to do with each other.  Please rename one of them");
      }
    }
  }
}
