package edu.stanford.nlp.semgraph.semgrex.ssurgeon;

import java.io.StringWriter;

import edu.stanford.nlp.ling.IndexedWord;
import edu.stanford.nlp.semgraph.semgrex.SemgrexMatcher;
import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.semgraph.SemanticGraphEdge;

/**
 * This action removes all incoming edges for the given node.
 * @author lumberjack
 *
 */
public class KillAllIncomingEdges extends SsurgeonEdit {
  /** The command name of this edit in an Ssurgeon script */
  public static final String LABEL = "killAllIncomingEdges";
  /** The name of the matched node whose incoming edges are removed */
  protected String nodeName; // name of this node

  /**
   * Creates an edit which removes all incoming edges of the named node.
   *
   * @param nodeName the name of the node in the Semgrex pattern
   */
  public KillAllIncomingEdges(String nodeName) {
    this.nodeName = nodeName;
  }

  /**
   * If executed twice on the same node, the second time there
   * will be no further updates
   */
  @Override
  public boolean evaluate(SemanticGraph sg, SemgrexMatcher sm) {
    IndexedWord tgtNode = getNamedNode(nodeName, sm);
    if (tgtNode == null) {
      return false;
    }
    boolean success = false;
    // use incomingEdgeList so that deleting an edge
    // doesn't affect the iteration
    for (SemanticGraphEdge edge : sg.incomingEdgeList(tgtNode)) {
      success = success || sg.removeEdge(edge);
    }
    return success;
  }

  @Override
  public String toEditString() {
    StringWriter buf = new StringWriter();
    buf.write(LABEL); buf.write("\t");
    buf.write(Ssurgeon.NODENAME_ARG); buf.write("\t"); buf.write(nodeName);
    return buf.toString();
  }

}
