package edu.stanford.nlp.semgraph.semgrex;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import edu.stanford.nlp.ling.IndexedWord;
import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.semgraph.SemanticGraphEdge;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.Pair;
import edu.stanford.nlp.util.VariableStrings;

import edu.stanford.nlp.util.logging.Redwood;

/**
 * Wraps a child pattern in a marker that indicates there are flags applied to the pattern.
 * This separate class ensures the toString / parser can round trip between each other.
 * <br>
 * The flags are not actually applied here; the NodePattern itself has case-insensitive
 * marked on it, for example.
 *
 * @author John Bauer
 */
public class ModifierPattern extends SemgrexPattern  {
  /** A logger for this class */
  private static final Redwood.RedwoodChannels log = Redwood.channels(ModifierPattern.class);

  private static final long serialVersionUID = -78784925769132145L;

  private final String flags;
  private final SemgrexPattern child;

  public ModifierPattern(String flags, SemgrexPattern child) {
    this.flags = flags;
    this.child = child;
    if (child == null) {
      throw new SemgrexParseException("ModifierPattern must have exactly one child");
    }
  }

  @Override
  public SemgrexMatcher matcher(SemanticGraph sg, IndexedWord node, Map<String, IndexedWord> namesToNodes,
                                Map<String, String> namesToRelations, Map<String, SemanticGraphEdge> namesToEdges,
                                VariableStrings variableStrings, boolean ignoreCase) {
    return child.matcher(sg, node, namesToNodes, namesToRelations, namesToEdges, variableStrings, ignoreCase);
  }

  @Override
  public SemgrexMatcher matcher(SemanticGraph sg, Alignment alignment, SemanticGraph sg_align, boolean hypToText,
                                IndexedWord node, Map<String, IndexedWord> namesToNodes, Map<String, String> namesToRelations,
                                Map<String, SemanticGraphEdge> namesToEdges,
                                VariableStrings variableStrings, boolean ignoreCase) {
    return child.matcher(sg, alignment, sg_align, hypToText, node, namesToNodes, namesToRelations, namesToEdges, variableStrings, ignoreCase);
  }

  @Override
  public List<Pair<CoreMap, List<SemgrexMatch>>> postprocessMatches(List<Pair<CoreMap, List<SemgrexMatch>>> matches, boolean keepEmptyMatches) {
    return child.postprocessMatches(matches, keepEmptyMatches);
  }

  @Override
  public List<SemgrexPattern> getChildren() {
    return Collections.singletonList(child);
  }

  @Override
  public String toString() {
    StringBuilder str = new StringBuilder();
    str.append("(?");
    str.append(flags);
    str.append(": ");
    str.append(child);
    str.append(" )");
    return str.toString();
  }

  @Override
  public String localString() {
    return "(?" + flags + ":";
  }

  @Override
  public String toString(boolean hasPrecedence) {
    return toString();
  }
}
