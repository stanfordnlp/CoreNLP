package edu.stanford.nlp.semgraph.semgrex;

import java.io.Serializable;

import edu.stanford.nlp.semgraph.SemanticGraph;

/**
 * The graphs a pattern is being matched against, and how they are joined.
 *<br>
 * A pattern is normally matched against one graph.  The alignment
 * relation, {@code @}, matches across two of them: a hypothesis graph and
 * a text graph, with an {@link Alignment} saying which node of one goes
 * with which node of the other.
 *<br>
 * All of this is fixed for the whole of a match, so one of these is built
 * once and passed down by reference.  Which of the graphs is currently
 * being searched is not kept here, because that changes as the match
 * descends: see HeadedPattern, which swaps to the other graph when the
 * relation it is following is an alignment.
 *
 * @author John Bauer
 */
public class SemgrexGraphs implements Serializable {

  private static final long serialVersionUID = 1L;

  /** The graph a pattern starts from, the hypothesis graph when there is an alignment */
  public final SemanticGraph graph;

  /** The graph an alignment crosses to, or null when there is no alignment */
  public final SemanticGraph alignedGraph;

  /** How the two graphs are joined, or null when there is no alignment */
  public final Alignment alignment;

  public SemgrexGraphs(SemanticGraph graph) {
    this(graph, null, null);
  }

  public SemgrexGraphs(SemanticGraph graph, Alignment alignment, SemanticGraph alignedGraph) {
    this.graph = graph;
    this.alignment = alignment;
    this.alignedGraph = alignedGraph;
  }

  /**
   * The graph to search, given which side of the alignment the match has reached.
   */
  public SemanticGraph get(boolean hyp) {
    return hyp ? graph : alignedGraph;
  }

  @Override
  public String toString() {
    if (alignment == null) {
      return "SemgrexGraphs(" + graph + ")";
    }
    return "SemgrexGraphs(" + graph + " aligned to " + alignedGraph + ")";
  }
}
