package edu.stanford.nlp.semgraph.semgrex.ssurgeon;

import java.io.StringWriter;
import java.util.Collections;
import java.util.Set;

import edu.stanford.nlp.ling.IndexedWord;
import edu.stanford.nlp.semgraph.semgrex.SemgrexGraphName;
import edu.stanford.nlp.semgraph.semgrex.SemgrexGraphs;
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
  /** The graph this edit works on */
  protected final SemgrexGraphName graph;

  /**
   * Creates an edit which removes all incoming edges of the named node.
   *
   * @param nodeName the name of the node in the Semgrex pattern
   */
  public KillAllIncomingEdges(String nodeName) {
    this(nodeName, SemgrexGraphName.BASIC);
  }

  /**
   * Creates an edit which removes all incoming edges of the named node in the given graph.
   *
   * @param nodeName the name of the node in the Semgrex pattern
   * @param graph the graph to remove the edges from
   */
  public KillAllIncomingEdges(String nodeName, SemgrexGraphName graph) {
    this.nodeName = nodeName;
    this.graph = graph;
  }

  /**
   * Removes the incoming edges of the node in this edit's graph.
   * If executed twice on the same node, the second time there
   * will be no further updates
   */
  @Override
  public boolean evaluate(SemgrexGraphs graphs, SemgrexMatcher sm) {
    SemanticGraph sg = graphs.get(graph);
    IndexedWord tgtNode = sm.getNode(nodeName);
    if (sg == null || tgtNode == null || !sg.containsVertex(tgtNode)) {
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
    if (graph != SemgrexGraphName.BASIC) {
      buf.write("\t");
      buf.write(Ssurgeon.GRAPH_ARG); buf.write(" ");
      buf.write(graph.lowerName);
    }
    return buf.toString();
  }

  /**
   * The graph this edit works on, if it is not the basic graph.
   */
  @Override
  public Set<SemgrexGraphName> requiredGraphs() {
    return graph == SemgrexGraphName.BASIC ? Collections.emptySet() : Collections.singleton(graph);
  }
}
