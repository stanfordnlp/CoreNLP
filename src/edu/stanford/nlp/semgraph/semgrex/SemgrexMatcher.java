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
	
  final Map<String, IndexedWord> namesToNodes;
  final Map<String, String> namesToRelations;
  final Map<String, SemanticGraphEdge> namesToEdges;
  final VariableStrings variableStrings;

  IndexedWord node;

  /** The graphs being matched against, and the alignment joining them */
  final SemgrexGraphs graphs;

  // these things are used by "find"
  private Iterator<IndexedWord> findIterator;
  private IndexedWord findCurrent;


  SemgrexMatcher(SemgrexGraphs graphs,
                 IndexedWord node,
                 Map<String, IndexedWord> namesToNodes,
                 Map<String, String> namesToRelations,
                 Map<String, SemanticGraphEdge> namesToEdges,
                 VariableStrings variableStrings) {
    this.graphs = graphs;
    this.node = node;
    this.namesToNodes = namesToNodes;
    this.namesToRelations = namesToRelations;
    this.namesToEdges = namesToEdges;
    this.variableStrings = variableStrings;
  }

  /**
   * Resets the matcher so that its search starts over.
   */
  public void reset() {
    findIterator = null;
    namesToNodes.clear();
    namesToRelations.clear();
    namesToEdges.clear();
    variableStrings.reset();
  }

  /**
   * Resets the matcher to start searching on the given node for matching
   * subexpressions.
   */
  /**
   * The nodes which could start a match, in the order they are tried.
   *<br>
   * Only the graphs a match could start in are searched.  For the usual
   * pattern that is one graph, and the nodes come out in dependency order
   * as they always have.  Where more than one graph is involved there is
   * no such order between them, so the nodes are taken together in
   * sentence order, which is where an enhanced graph's extra nodes belong.
   */
  private Iterator<IndexedWord> candidates() {
    Set<SemgrexGraphName> names = getPattern().startingGraphs();
    if (names.isEmpty()) {
      // nothing in the pattern says where it may start, so anywhere it can
      names = EnumSet.allOf(SemgrexGraphName.class);
    }

    List<SemanticGraph> searched = new ArrayList<>();
    for (SemgrexGraphName name : names) {
      SemanticGraph graph = graphs.get(name);
      if (graph != null) {
        searched.add(graph);
      }
    }
    if (searched.isEmpty()) {
      return Collections.emptyIterator();
    }
    if (searched.size() == 1) {
      SemanticGraph only = searched.get(0);
      try {
        return only.topologicalSort().iterator();
      } catch (CyclicGraphException e) {
        return only.vertexSet().iterator();
      }
    }

    // IndexedWord orders by index, then empty index, then copy, so the
    // extra nodes of an enhanced graph land where they belong
    Set<IndexedWord> union = new TreeSet<>();
    for (SemanticGraph graph : searched) {
      union.addAll(graph.vertexSet());
    }
    return union.iterator();
  }

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

  public abstract SemgrexGraphName getGraph();

  /**
   * The pattern this matcher was built from.
   * Not a member variable because each Matcher stores
   * its local class instead of an abstract SemgrexPattern
   */
  abstract SemgrexPattern getPattern();

  /**
   * The graphs a relation written after this one should be matched against.
   *<br>
   * Normally the ones this matcher is searching.  A matcher which crossed
   * an alignment reports the sentence it crossed to, so that the relations
   * below it carry on there.  Read after a match, since which graphs apply
   * can depend on which part of the pattern matched.
   */
  public SemgrexGraphs getGraphs() {
    return graphs;
  }

  /**
   * Find the next match of the pattern in the graph.
   *
   * @return whether there is a match somewhere in the graph
   */
  public boolean find() {
    // there was a cache of the topological sorts to reuse across
    // SemgrexPatterns which used IdentityHashMap to remember
    // SemanticGraphs, but it was apparently the cause of various
    // thread safety bugs when the results were used for an old
    // SemanticGraph
    if (findIterator == null) {
      findIterator = candidates();
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
}
