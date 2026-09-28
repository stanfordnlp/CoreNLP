package edu.stanford.nlp.semgraph.semgrex.ssurgeon;

import java.io.StringWriter;

import edu.stanford.nlp.ling.IndexedWord;
import edu.stanford.nlp.semgraph.semgrex.SemgrexMatcher;
import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.semgraph.SemanticGraphEdge;

/**
 * This action deletes the given node, along with its incoming edges,
 * if it is a leaf (has no outgoing edges).  The indices of the later
 * words are then shifted down by one.
 * @author lumberjack
 *
 */
public class DeleteLeaf extends SsurgeonEdit {
  /** The name of this edit in an Ssurgeon script */
  public static final String LABEL = "deleteLeaf";
  /** The name of the matched node to delete */
  protected String nodeName; // name of this node

  /**
   * Creates an edit which deletes the named node if it is a leaf.
   *
   * @param nodeName The name of the node in the Semgrex pattern
   */
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
