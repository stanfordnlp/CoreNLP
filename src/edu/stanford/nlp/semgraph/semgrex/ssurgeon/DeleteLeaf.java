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
public class DeleteLeaf extends SsurgeonEdit {
  public static final String LABEL = "deleteLeaf";
  protected String nodeName; // name of this node

  public DeleteLeaf(String nodeName) {
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
    for (SemanticGraphEdge edge : sg.outgoingEdgeList(tgtNode)) {
      // if there are any outgoing edges, we aren't a leaf
      return false;
    }
    boolean deletedEdge = false;
    // use incomingEdgeList so that deleting an edge
    // doesn't affect the iteration
    for (SemanticGraphEdge edge : sg.incomingEdgeList(tgtNode)) {
      deletedEdge = deletedEdge || sg.removeEdge(edge);
    }
    int deletedIndex = tgtNode.index();
    boolean deletedNode = sg.removeVertex(tgtNode);
    // renumber the indices
    if (deletedNode) {
      SsurgeonUtils.moveNodes(sg, sm, x -> (x >= deletedIndex), x -> x-1, false);
    }
    return deletedEdge || deletedNode;
  }

  @Override
  public String toEditString() {
    StringWriter buf = new StringWriter();
    buf.write(LABEL); buf.write("\t");
    buf.write(Ssurgeon.NODENAME_ARG); buf.write("\t"); buf.write(nodeName);
    return buf.toString();
  }

}
