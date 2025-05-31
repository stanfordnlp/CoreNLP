package edu.stanford.nlp.semgraph.semgrex;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
public class UniqPattern extends SemgrexPattern  {
  private static final long serialVersionUID = -38315768154569L;

  private final SemgrexPattern child;
  private final List<String> keys;

  public UniqPattern(SemgrexPattern child, List<String> keys) {
    this.child = child;
    this.keys = new ArrayList<>(keys);
  }

  private String getKey(SemgrexMatch match, String key) {
    // TODO: could also do edge names or variable groups (once those exist)
    IndexedWord node = match.getNode(key);
    if (node == null) {
      return null;
    }
    return node.value();
  }

  public List<Pair<CoreMap, List<SemgrexMatch>>> postprocessMatches(List<Pair<CoreMap, List<SemgrexMatch>>> matches, boolean keepEmptyMatches) {
    // hashing lists should be okay here since the lists will not change
    // while the postprocessing is happening
    Set<List<String>> seenKeys = new HashSet<>();

    List<Pair<CoreMap, List<SemgrexMatch>>> newMatches = new ArrayList<>();
    for (Pair<CoreMap, List<SemgrexMatch>> sentence : matches) {
      List<SemgrexMatch> newSentenceMatches = new ArrayList<>();
      for (SemgrexMatch match : sentence.second()) {
        List<String> matchKey = new ArrayList<>();
        for (String key : keys) {
          matchKey.add(getKey(match, key));
        }
        if (seenKeys.contains(matchKey)) {
          continue;
        }
        seenKeys.add(matchKey);
        newSentenceMatches.add(match);
      }
      if (newSentenceMatches.size() > 0 || keepEmptyMatches) {
        newMatches.add(new Pair<>(sentence.first(), newSentenceMatches));
      }
    }

    return newMatches;
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
    throw new UnsupportedOperationException("Child should only be set on a UniqPattern at creation time");
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
    sb.append(" :: uniq");
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
