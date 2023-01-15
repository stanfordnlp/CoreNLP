package edu.stanford.nlp.semgraph.semgrex.ssurgeon;

import java.io.StringWriter;

import edu.stanford.nlp.ling.IndexedWord;
import edu.stanford.nlp.semgraph.semgrex.SemgrexMatcher;
import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.semgraph.SemanticGraphEdge;

/**
 * Given a named edge, governor, and dependent, removes that edge
 * from the SemanticGraph.
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
  public static final String LABEL = "removeNamedEdge";

  protected final String edgeName; // Name of the matched edge in the SemgrexPattern
  
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
   *<br>
   * TODO: it should not be necessary to have node names for this to work.
   * Any edge that gets matched by the edge matcher should just work
   * This operation is not used anywhere, even in RTE, so we should be
   * able to change its semantics to not include the node names
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

  public String getEdgeName() {
    return edgeName;
  }
}
