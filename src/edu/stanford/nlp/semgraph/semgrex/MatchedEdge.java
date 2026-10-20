package edu.stanford.nlp.semgraph.semgrex;

import java.io.Serializable;
import java.util.Objects;

import edu.stanford.nlp.semgraph.SemanticGraphEdge;

/**
 * An edge bound to a name by a match, along with the graph it was found in.
 *<br>
 * A sentence can be matched against more than one graph of it, and the
 * same arc can appear in several of them: an edge is equal to another
 * with the same governor, dependent, and relation, whichever graph each
 * came from.  So the edge alone cannot say where it was found, and the
 * name of the graph is kept alongside it.  The name rather than the
 * graph itself is kept so that a user of the match, such as a Ssurgeon
 * edit working on a copy of the graphs, can find the same graph in the
 * graphs it has.
 *
 * @author John Bauer
 */
public final class MatchedEdge implements Serializable {
  private static final long serialVersionUID = 1L;

  /** The edge */
  public final SemanticGraphEdge edge;
  /** The graph the edge was found in */
  public final SemgrexGraphName graph;

  /**
   * An edge and the graph it was found in.
   *
   * @param edge the edge
   * @param graph the name of the graph the edge was found in
   */
  public MatchedEdge(SemanticGraphEdge edge, SemgrexGraphName graph) {
    this.edge = Objects.requireNonNull(edge, "edge");
    this.graph = Objects.requireNonNull(graph, "graph");
  }

  /**
   * Two matched edges are equal if they are equal edges found in the same graph.
   */
  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof MatchedEdge)) {
      return false;
    }
    MatchedEdge other = (MatchedEdge) o;
    return graph == other.graph && edge.equals(other.edge);
  }

  @Override
  public int hashCode() {
    return Objects.hash(edge, graph);
  }

  /** The edge, followed by the graph if it is not the basic graph, as in a pattern */
  @Override
  public String toString() {
    if (graph == SemgrexGraphName.BASIC) {
      return edge.toString();
    }
    return edge + "@" + graph.lowerName;
  }
}
