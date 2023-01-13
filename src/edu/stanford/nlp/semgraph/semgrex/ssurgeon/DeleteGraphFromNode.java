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
  public static final String LABEL = "delete";

  String destroyNodeName;

  public DeleteGraphFromNode(String destroyNodeName) {
    this.destroyNodeName = destroyNodeName;
  }

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
