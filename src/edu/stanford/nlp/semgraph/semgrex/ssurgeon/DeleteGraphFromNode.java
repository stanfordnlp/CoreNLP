package edu.stanford.nlp.semgraph.semgrex.ssurgeon;

import java.io.StringWriter;
import java.util.*;

import edu.stanford.nlp.ling.IndexedWord;
import edu.stanford.nlp.semgraph.semgrex.SemgrexMatcher;
import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.semgraph.SemanticGraphEdge;
import edu.stanford.nlp.util.Generics;

/**
 * This destroys the subgraph starting from the given node.  Use this when
 * the SemanticGraph has been cut and separated into two separate graphs,
 * and you wish to destroy one of them.
 *
 * @author yeh1
 *
 */
public class DeleteGraphFromNode extends SsurgeonEdit {
  /** The name of this edit in an Ssurgeon script */
  public static final String LABEL = "delete";

  /** The name of the matched node from which to start deleting */
  String destroyNodeName;

  /**
   * Creates an edit which deletes everything connected to the named node.
   *
   * @param destroyNodeName The name of the node in the Semgrex pattern
   */
  public DeleteGraphFromNode(String destroyNodeName) {
    this.destroyNodeName = destroyNodeName;
  }

  /**
   * Builds the edit from its argument string, which is the node name.
   *
   * @param args The node name; surrounding whitespace is trimmed
   * @return The new edit
   */
  public static DeleteGraphFromNode fromArgs(String args) {
    return new DeleteGraphFromNode(args.trim());
  }

  @Override
  public String toEditString() {
    StringWriter buf = new StringWriter();
    buf.write(LABEL); buf.write("\t");
    buf.write(Ssurgeon.NODENAME_ARG);buf.write(" ");
    buf.write(destroyNodeName);
    return buf.toString();
  }

  /**
   * Adds to {@code seenVerts} every vertex connected to {@code vertex},
   * following both incoming and outgoing edges.
   *
   * @param vertex The vertex to start from
   * @param sg The graph to search
   * @param seenVerts The vertices found so far; updated in place
   */
  protected static void crawl(IndexedWord vertex, SemanticGraph sg, Set<IndexedWord> seenVerts) {
    seenVerts.add(vertex);
    for (SemanticGraphEdge edge : sg.incomingEdgeIterable(vertex)) {
      IndexedWord gov = edge.getGovernor();
      if (!seenVerts.contains(gov)) {
        crawl(gov, sg, seenVerts);
      }
    }

    for (SemanticGraphEdge edge : sg.outgoingEdgeIterable(vertex)) {
      IndexedWord dep = edge.getDependent();
      if (!seenVerts.contains(dep)) {
        crawl(dep, sg, seenVerts);
      }
    }
  }

  /**
   * Finds every vertex connected to {@code vertex}, following both incoming and
   * outgoing edges, including {@code vertex} itself.
   *
   * @param vertex The vertex to start from
   * @param sg The graph to search
   * @return The set of connected vertices
   */
  protected static Set<IndexedWord> crawl(IndexedWord vertex, SemanticGraph sg) {
    Set<IndexedWord> seen = Generics.newHashSet();
    crawl(vertex, sg, seen);
    return seen;
  }


  @Override
  public boolean evaluate(SemanticGraph sg, SemgrexMatcher sm) {
    IndexedWord seedNode = getNamedNode(destroyNodeName, sm);
    if (seedNode == null || !sg.containsVertex(seedNode)) {
      return false;
    }

    boolean deletedRoot = false;
    Set<IndexedWord> nodesToDestroy = crawl(seedNode, sg);
    for (IndexedWord node : nodesToDestroy) {
      if (sg.isRoot(node)) {
        deletedRoot = true;
      }
      sg.removeVertex(node);
    }
    // After destroy nodes, need to reset the roots if any roots were destroyed
    if (deletedRoot) {
      sg.resetRoots();
    }
    return true;
  }

}
