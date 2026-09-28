package edu.stanford.nlp.semgraph.semgrex.ssurgeon;

import java.io.StringWriter;

import edu.stanford.nlp.ling.IndexedWord;
import edu.stanford.nlp.semgraph.semgrex.SemgrexMatcher;
import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.semgraph.SemanticGraphEdge;

/**
 * Given a named edge, removes that edge from the SemanticGraph.
 *
 * NOTE: you should manually reassign roots for dangling subtrees,
 * or delete them outright.  This does not perform any new root 
 * assignments.
 * 
 * TODO: implement logging functionality
 * @author yeh1
 *
 */
public class RemoveNamedEdge extends SsurgeonEdit {  
  /** The name of this operation in an Ssurgeon edit string */
  public static final String LABEL = "removeNamedEdge";

  /** The name of the edge to remove */
  protected final String edgeName; // Name of the matched edge in the SemgrexPattern
  
  /**
   * Creates an edit which removes a named edge.
   *
   * @param edgeName The name of the edge in the Semgrex pattern
   */
  public RemoveNamedEdge(String edgeName) {
    this.edgeName = edgeName;
  }
  
  @Override
  public String toEditString() {
    StringWriter buf = new StringWriter();
    buf.write(LABEL); buf.write("\t");
    buf.write(Ssurgeon.EDGE_NAME_ARG);buf.write(" ");    
    buf.write(edgeName); buf.write("\t");
    return buf.toString();
  }

  /**
   * Removes the named edge from the graph, if it exists.
   */
  @Override
  public boolean evaluate(SemanticGraph sg, SemgrexMatcher sm) {
    SemanticGraphEdge edge = sm.getEdge(edgeName);

    if (edge != null) {
      sg.removeEdge(edge);
      return true;
    }
    return false;
  }

  /**
   * Returns the name of the edge this edit removes.
   *
   * @return The edge name
   */
  public String getEdgeName() {
    return edgeName;
  }
}
