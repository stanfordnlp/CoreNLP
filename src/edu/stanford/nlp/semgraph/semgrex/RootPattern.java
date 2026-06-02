package edu.stanford.nlp.semgraph.semgrex; 

import java.util.Collections;
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
 * A root level pattern for storing final information about the SemgrexPattern,
 * such as the known edge names used in the pattern
 *
 * TODO: reassess if it would actually be more useful to just keep
 * this information in the children
 *
 * @author John Bauer
 */
public class RootPattern extends SemgrexPattern  {
  final SemgrexPattern child;
  final Set<String> knownEdges;

  public List<SemgrexPattern> getChildren() {
    return Collections.singletonList(child);
  }

  public Set<String> getKnownEdges() {
    return knownEdges;
  }

  public String localString() {
    return child.localString();
  }

  public void setChild(SemgrexPattern child) {
    throw new UnsupportedOperationException("Cannot update a SemgrexPattern's children once set!");
  }

  public String toString() {
    return child.toString();
  }

  public String toString(boolean hasPrecedence) {
    return child.toString(hasPrecedence);
  }

  public SemgrexMatcher matcher(SemanticGraph sg, IndexedWord node, Map<String, IndexedWord> namesToNodes,
                                Map<String, String> namesToRelations, Map<String, SemanticGraphEdge> namesToEdges,
                                VariableStrings variableStrings, boolean ignoreCase) {
    return child.matcher(sg, node, namesToNodes, namesToRelations, namesToEdges, variableStrings, ignoreCase);
  }

  public SemgrexMatcher matcher(SemanticGraph sg, Alignment alignment, SemanticGraph sg_align, boolean hypToText,
                                IndexedWord node, Map<String, IndexedWord> namesToNodes, Map<String, String> namesToRelations,
                                Map<String, SemanticGraphEdge> namesToEdges,
                                VariableStrings variableStrings, boolean ignoreCase) {
    return child.matcher(sg, alignment, sg_align, hypToText, node, namesToNodes, namesToRelations, namesToEdges, variableStrings, ignoreCase);
  }

  public List<Pair<CoreMap, List<SemgrexMatch>>> postprocessMatches(List<Pair<CoreMap, List<SemgrexMatch>>> matches, boolean keepEmptyMatches) {
    return child.postprocessMatches(matches, keepEmptyMatches);
  }

  RootPattern(SemgrexPattern child, Set<String> knownEdges) {
    if (child == null) {
      throw new IllegalArgumentException("Cannot wrap a null SemgrexPattern");
    }
    this.child = child;
    this.knownEdges = Collections.unmodifiableSet(knownEdges);
  }
}
