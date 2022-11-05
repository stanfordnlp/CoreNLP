package edu.stanford.nlp.semgraph.semgrex.ssurgeon;

import edu.stanford.nlp.semgraph.semgrex.SemgrexMatcher;
import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.ling.IndexedWord;

public abstract class SsurgeonEdit {
   
  private SsurgeonPattern owningPattern = null;
  
  
  /**
   * Given a matching instance (via the SemgrexMatcher), performs an in-place
   * modification on the given SemanticGraph.
   * <br>
   * @return whether or not there was an edit
   */
  public abstract boolean evaluate(SemanticGraph sg, SemgrexMatcher sm);

  public abstract String toEditString(); // This should be a parseable String representing the edit
  @Override
  public String toString() { return toEditString(); }
  
  public boolean equals(SsurgeonEdit tgt) {
    return this.toString().equals(tgt.toString());
  }

  public SsurgeonPattern getOwningPattern() {
    return owningPattern;
  }

  public void setOwningPattern(SsurgeonPattern owningPattern) {
    this.owningPattern = owningPattern;
  }
  
  /**
   * Used to retrieve the named node.  If not found in the SemgrexMatcher, check the
   * owning pattern object, as this could've been a created node.
   */
  public IndexedWord getNamedNode(String nodeName, SemgrexMatcher sm) {
    IndexedWord ret = sm.getNode(nodeName);
    if ((ret == null) && getOwningPattern() != null)
      return getOwningPattern().getNamedNode(nodeName);
    return ret; 
  }
  
  public void addNamedNode(IndexedWord newNode, String name) {
    getOwningPattern().addNamedNode(newNode, name);
  }
}
