package edu.stanford.nlp.semgraph.semgrex; 

import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.ling.*;
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
                 VariableStrings variableStrings) {
    this.sg = sg;
    this.alignment = alignment;
    this.sg_aligned = sg_aligned;
    this.hyp = hyp;
    this.node = node;
    this.namesToNodes = namesToNodes;
    this.namesToRelations = namesToRelations;
    this.variableStrings = variableStrings;
  }
  
  SemgrexMatcher(SemanticGraph sg,
                 IndexedWord node,
                 Map<String, IndexedWord> namesToNodes,
                 Map<String, String> namesToRelations,
                 VariableStrings variableStrings) {
    this(sg, null, null, true, node, namesToNodes, namesToRelations, variableStrings);
  }

  /**
   * Resets the matcher so that its search starts over.
   */
  public void reset() {
    findIterator = null;
    namesToNodes.clear();
    namesToRelations.clear();
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
   * Topological sorting actually takes a rather large amount of time, if you call multiple
   * patterns on the same tree.
   * This is a weak cache that stores all the trees sorted since the garbage collector last kicked in.
   * The key on this map is the identity hash code (i.e., memory address) of the semantic graph; the
   * value is the sorted list of vertices.
   * <p>
   * Note that this optimization will cause strange things to happen if you mutate a semantic graph between
   * calls to Semgrex.
   */
  private static final WeakHashMap<Integer, List<IndexedWord>> topologicalSortCache = new WeakHashMap<>();

  private void setupFindIterator() {
    try {
      if (hyp) {
        synchronized (topologicalSortCache) {
          List<IndexedWord> topoSort = topologicalSortCache.get(System.identityHashCode(sg));
          if (topoSort == null || topoSort.size() != sg.size()) {  // size check to mitigate a stale cache
            topoSort = sg.topologicalSort();
            topologicalSortCache.put(System.identityHashCode(sg), topoSort);
          }
          findIterator = topoSort.iterator();
        }
      } else if (sg_aligned == null) {
        return;
      } else {
        synchronized (topologicalSortCache) {
          List<IndexedWord> topoSort = topologicalSortCache.get(System.identityHashCode(sg_aligned));
          if (topoSort == null || topoSort.size() != sg_aligned.size()) {  // size check to mitigate a stale cache
            topoSort = sg_aligned.topologicalSort();
            topologicalSortCache.put(System.identityHashCode(sg_aligned), topoSort);
          }
          findIterator = topoSort.iterator();
        }
      }
    } catch (Exception ex) {
      if (hyp) {
        findIterator = sg.vertexSet().iterator();
      } else if (sg_aligned == null) {
        return;
      } else {
        findIterator = sg_aligned.vertexSet().iterator();
      }
    }
  }

  /**
   * Find the next match of the pattern in the graph.
   *
   * @return whether there is a match somewhere in the graph
   */
  public boolean find() {
    // log.info("hyp: " + hyp);
    if (findIterator == null) {
      setupFindIterator();
    }
    if (findIterator == null) {
      return false;
    }
    //  System.out.println("first");
    if (findCurrent != null && matches()) {
    //		log.info("find first: " + findCurrent.word());
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
  
  public String getRelnString(String name) {
    return namesToRelations.get(name);
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
  
  @Override
  public abstract String toString();

  /**
   * Returns the graph associated with this match.
   */
  public SemanticGraph getGraph() {
    return sg;
  }
  
}
