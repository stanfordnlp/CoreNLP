package edu.stanford.nlp.semgraph.semgrex.ssurgeon;

import edu.stanford.nlp.semgraph.semgrex.SemgrexMatcher;
import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.ling.IndexedWord;

/**
 * An edit which an Ssurgeon pattern applies to a graph at each Semgrex match.
 * The edits available, and the syntax of their edit strings, are described
 * in {@link Ssurgeon}.
 */
public abstract class SsurgeonEdit {
   
  private SsurgeonPattern owningPattern = null;

  /** Creates an edit with no owning pattern. */
  public SsurgeonEdit() { }
  
  
  /**
   * Given a matching instance (via the SemgrexMatcher), performs an in-place
   * modification on the given SemanticGraph.
   *
   * @param sg The graph to edit
   * @param sm The matcher, positioned at the current match
   * @return whether or not there was an edit
   */
  public abstract boolean evaluate(SemanticGraph sg, SemgrexMatcher sm);

  /**
   * Returns this edit in the Ssurgeon edit syntax.
   *
   * @return A parseable string representing the edit
   */
  public abstract String toEditString(); // This should be a parseable String representing the edit
  @Override
  public String toString() { return toEditString(); }
  
  /**
   * Compares two edits by their string representations.  This overloads,
   * rather than overrides, {@link Object#equals(Object)}.
   *
   * @param tgt The edit to compare to
   * @return Whether the two edits have the same string representation
   */
  public boolean equals(SsurgeonEdit tgt) {
    return this.toString().equals(tgt.toString());
  }

  /**
   * Returns the pattern this edit belongs to.
   *
   * @return The owning pattern, or null if none has been set
   */
  public SsurgeonPattern getOwningPattern() {
    return owningPattern;
  }

  /**
   * Sets the pattern this edit belongs to.
   *
   * @param owningPattern The owning pattern
   */
  public void setOwningPattern(SsurgeonPattern owningPattern) {
    this.owningPattern = owningPattern;
  }
  
  /**
   * Used to retrieve the named node.  If not found in the SemgrexMatcher, check the
   * owning pattern object, as this could've been a created node.
   *
   * @param nodeName The name of the node
   * @param sm The matcher, positioned at the current match
   * @return The named node, or null if neither the matcher nor the owning pattern has it
   */
  public IndexedWord getNamedNode(String nodeName, SemgrexMatcher sm) {
    IndexedWord ret = sm.getNode(nodeName);
    if ((ret == null) && getOwningPattern() != null)
      return getOwningPattern().getNamedNode(nodeName);
    return ret; 
  }
  
  /**
   * Records a node under a name in the owning pattern, which must be set.
   *
   * @param newNode The node
   * @param name The name for the node
   */
  public void addNamedNode(IndexedWord newNode, String name) {
    getOwningPattern().addNamedNode(newNode, name);
  }
}
