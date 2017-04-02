package edu.stanford.nlp.semgraph.semgrex;

import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.ling.*;


import java.util.*;

/**
 * A <code>SemgrexMatcher</code> can be used to match a {@link SemgrexPattern}
 * against a {@link edu.stanford.nlp.semgraph.SemanticGraph}. <p/>
 *
 * Usage should be the same as {@link java.util.regex.Matcher}. <p/>
 *
 * @author Chloe Kiddon
 */
public abstract class SemgrexMatcher {
	
  SemanticGraph sg;
  Map<String, IndexedWord> namesToNodes;
  Map<String, String> namesToRelations;
  VariableStrings variableStrings;

  IndexedWord node;

  // to be used for patterns involving "@"
  Alignment alignment;
  SemanticGraph sg_aligned;
  boolean hyp;

  // these things are used by "find"
  Iterator<IndexedWord> findIterator;
  IndexedWord findCurrent;

  SemgrexMatcher(SemanticGraph sg, 
                 Alignment alignment,
                 SemanticGraph sg_aligned,
                 boolean hyp, 
                 IndexedWord node,
                 Map<String, IndexedWord> namesToNodes,
                 Map<String, String> namesToRelations,
                 VariableStrings variableStrings) {
    this.sg = sg;
    this.alignment = (alignment == null) ? null : alignment;
    this.sg_aligned = (sg_aligned == null) ? null : sg_aligned;
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
   * subexpressions
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
   * <code>node</code>.
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
   * Find the next match of the pattern in the graph
   *
   * @return whether there is a match somewhere in the graph
   */
  public boolean find() {
    // System.err.println("hyp: " + hyp);
    if (findIterator == null) {
      try {
        if (hyp)
          findIterator = sg.topologicalSort().iterator();
        else if (sg_aligned == null)
          return false;
        else
          findIterator = sg_aligned.topologicalSort().iterator();
    			
      } catch (Exception ex) {
        if (hyp)
          findIterator = sg.vertexSet().iterator();
        else if (sg_aligned == null)
          return false;
        else
          findIterator = sg_aligned.vertexSet().iterator();
      }
    }
  //  System.out.println("first");
    if (findCurrent != null && matches()) {
    //		System.err.println("find first: " + findCurrent.word());
      return true;
    }
    //System.err.println("here");
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
    	//  System.err.println("find second: " + findCurrent.word());
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
   * Returns the node labeled with <code>name</code> in the pattern.
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
  abstract public String toString();

  /**
   * Returns the graph associated with this match.
   */
  public SemanticGraph getGraph() {
    return sg;
  }
  
}
