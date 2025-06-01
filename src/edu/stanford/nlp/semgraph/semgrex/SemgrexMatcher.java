package edu.stanford.nlp.semgraph.semgrex; 

import edu.stanford.nlp.graph.CyclicGraphException;
import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.semgraph.SemanticGraphEdge;
import edu.stanford.nlp.ling.*;
import edu.stanford.nlp.util.VariableStrings;
import edu.stanford.nlp.util.logging.Redwood;

import java.util.*;

/**
 * A {@code SemgrexMatcher} can be used to match a {@link SemgrexPattern}
 * against a {@link edu.stanford.nlp.semgraph.SemanticGraph}.
 * <p>
 * Usage should be the same as {@link java.util.regex.Matcher}.
 *
 * @author Chloe Kiddon
 */
public abstract class SemgrexMatcher  {

  /** A logger for this class */
  private static final Redwood.RedwoodChannels log = Redwood.channels(SemgrexMatcher.class);
	
  final SemanticGraph sg;
  final Map<String, IndexedWord> namesToNodes;
  final Map<String, String> namesToRelations;
  final Map<String, SemanticGraphEdge> namesToEdges;
  final VariableStrings variableStrings;

  IndexedWord node;

  // to be used for patterns involving "@"
  final Alignment alignment;
  final SemanticGraph sg_aligned;
  final boolean hyp;

  // these things are used by "find"
  private Iterator<IndexedWord> findIterator;
  private IndexedWord findCurrent;


  SemgrexMatcher(SemanticGraph sg,
                 Alignment alignment,
                 SemanticGraph sg_aligned,
                 boolean hyp, 
                 IndexedWord node,
                 Map<String, IndexedWord> namesToNodes,
                 Map<String, String> namesToRelations,
                 Map<String, SemanticGraphEdge> namesToEdges,
                 VariableStrings variableStrings) {
    this.sg = sg;
    this.alignment = alignment;
    this.sg_aligned = sg_aligned;
    this.hyp = hyp;
    this.node = node;
    this.namesToNodes = namesToNodes;
    this.namesToRelations = namesToRelations;
    this.namesToEdges = namesToEdges;
    this.variableStrings = variableStrings;
  }

  SemgrexMatcher(SemanticGraph sg,
                 IndexedWord node,
                 Map<String, IndexedWord> namesToNodes,
                 Map<String, String> namesToRelations,
                 Map<String, SemanticGraphEdge> namesToEdges,
                 VariableStrings variableStrings) {
    this(sg, null, null, true, node, namesToNodes, namesToRelations, namesToEdges, variableStrings);
  }

  /**
   * Resets the matcher so that its search starts over.
   */
  public void reset() {
    findIterator = null;
    namesToNodes.clear();
    namesToRelations.clear();
    namesToEdges.clear();
  }

  /**
   * Resets the matcher to start searching on the given node for matching
   * subexpressions.
   */
  void resetChildIter(IndexedWord node) {
    this.node = node;
    resetChildIter();
  }

  /**
   * Resets the matcher to restart search for matching subexpressions
   */
  void resetChildIter() {
  }

  /**
   * Does the pattern match the graph?  It's actually closer to
   * java.util.regex's "lookingAt" in that the root of the graph has to match
   * the root of the pattern but the whole tree does not have to be "accounted
   * for".  Like with lookingAt the beginning of the string has to match the
   * pattern, but the whole string doesn't have to be "accounted for".
   *
   * @return whether the node matches the pattern
   */
  public abstract boolean matches();

  /** Rests the matcher and tests if it matches in the graph when rooted at
   * {@code node}.
   *
   * @return whether the matcher matches at node
   */
  public boolean matchesAt(IndexedWord node) {
    resetChildIter(node);
    return matches();
  }

  /**
   * Get the last matching node -- that is, the node that matches the root node
   * of the pattern.  Returns null if there has not been a match.
   *
   * @return last match
   */
  public abstract IndexedWord getMatch();


  /**
   * Find the next match of the pattern in the graph.
   *
   * @return whether there is a match somewhere in the graph
   */
  public boolean find() {
    // log.info("hyp: " + hyp);
    // there was a cache of the topological sorts to reuse across
    // SemgrexPatterns which used IdentityHashMap to remember
    // SemanticGraphs, but it was apparently the cause of various
    // thread safety bugs when the results were used for an old
    // SemanticGraph
    if (findIterator == null) {
      if (hyp) {
        try {
          findIterator = sg.topologicalSort().iterator();
        } catch (CyclicGraphException e) {
          findIterator = sg.vertexSet().iterator();
        }
      } else if (sg_aligned == null) {
        return false;
      } else {
        try {
          findIterator = sg_aligned.topologicalSort().iterator();
        } catch (CyclicGraphException e) {
          findIterator = sg_aligned.vertexSet().iterator();
        }
      }
    }

    if (findCurrent != null && matches()) {
      return true;
    }
    //log.info("here");
    while (findIterator.hasNext()) {
      findCurrent = findIterator.next();
     // System.out.println("final: " + namesToNodes);
      resetChildIter(findCurrent);
      // System.out.println("after reset: " + namesToNodes);
      // Should not be necessary to reset namesToNodes here, since it
      // gets cleaned up by resetChildIter
      //namesToNodes.clear();
      //namesToRelations.clear();
      if (matches()) {
    	//  log.info("find second: " + findCurrent.word());
        return true;
      }
    }
    return false;
  }

  /** 
   * Find the next match of the pattern in the graph such that the matching node
   * (that is, the node matching the root node of the pattern) differs from the
   * previous matching node.
   *
   * @return true iff another matching node is found.
   */
  public boolean findNextMatchingNode() {
    IndexedWord lastMatchingNode = getMatch();
    while(find()) {
      if(getMatch() != lastMatchingNode)
        return true;
    }
    return false;
  }
  
  /**
   * Returns the node labeled with {@code name} in the pattern.
   *
   * @param name the name of the node, specified in the pattern.
   * @return node labeled by the name
   */
  public IndexedWord getNode(String name) {
    return namesToNodes.get(name);
  }

  public IndexedWord putNode(String name, IndexedWord node) {
    return namesToNodes.put(name, node);
  }

  public String getRelnString(String name) {
    return namesToRelations.get(name);
  }
  
  public SemanticGraphEdge getEdge(String name) {
    return namesToEdges.get(name);
  }

  /**
   * Returns the set of names for named nodes in this pattern.
   * This is used as a convenience routine, when there are numerous patterns
   * with named nodes to track.
   */
  public Set<String> getNodeNames() {
    return namesToNodes.keySet();
  }

  /**
   * Returns the set of names for named relations in this pattern.
   */
  public Set<String> getRelationNames() {
    return namesToRelations.keySet();
  }
  
  /**
   * Returns the set of names for named edges in this pattern
   */
  public Set<String> getEdgeNames() {
    return namesToEdges.keySet();
  }

  public SemanticGraphEdge putNamedEdge(String name, SemanticGraphEdge edge) {
    return namesToEdges.put(name, edge);
  }

  @Override
  public abstract String toString();

  /**
   * Returns the graph associated with this match.
   */
  public SemanticGraph getGraph() {
    return sg;
  }
  
}
