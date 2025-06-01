package edu.stanford.nlp.semgraph.semgrex;

import java.io.Serializable;
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

  final SemgrexPattern matchedPattern;

  final SemanticGraph sg;
  final Map<String, IndexedWord> namesToNodes;
  final Map<String, String> namesToRelations;
  final Map<String, SemanticGraphEdge> namesToEdges;
  final VariableStrings variableStrings;

  final Alignment alignment;
  final SemanticGraph sg_aligned;
  final boolean hyp;

  final IndexedWord match;

  public SemgrexMatch(SemgrexPattern pattern, SemgrexMatcher matcher) {
    matchedPattern = pattern;
    sg = matcher.sg;
    namesToNodes = new HashMap<>(matcher.namesToNodes);
    namesToRelations = new HashMap<>(matcher.namesToRelations);
    namesToEdges = new HashMap<>(matcher.namesToEdges);
    variableStrings = new VariableStrings(matcher.variableStrings);
    if (matcher.alignment != null) {
      alignment = new Alignment(matcher.alignment);
    } else {
      alignment = null;
    }
    sg_aligned = matcher.sg_aligned;
    hyp = matcher.hyp;
    match = matcher.getMatch();
  }

  public IndexedWord getMatch() {
    return match;
  }

  public IndexedWord getNode(String name) {
    return namesToNodes.get(name);
  }

  public Set<String> getNodeNames() {
    return namesToNodes.keySet();
  }
}
