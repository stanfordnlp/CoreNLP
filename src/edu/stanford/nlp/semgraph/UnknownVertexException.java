package edu.stanford.nlp.semgraph;

import edu.stanford.nlp.ling.IndexedWord;

/**
 * Thrown when an operation on a {@link SemanticGraph} refers to a vertex
 * which is not in that graph.
 */
public class UnknownVertexException extends IllegalArgumentException {
  /** The vertex which was not found; may be null */
  public final IndexedWord vertex;
  /** The graph which was searched for the vertex */
  public final SemanticGraph graph;

  /**
   * Creates an exception for a vertex not found in a graph.
   *
   * @param vertex The vertex which was not found
   * @param graph The graph which does not contain the vertex
   */
  public UnknownVertexException(IndexedWord vertex, SemanticGraph graph) {
    this.vertex = vertex;
    this.graph = graph;
  }

  public String toString() {
    if (vertex == null) {
      return super.toString() + ": Operation attempted on unknown vertex " + vertex + " in graph " + graph;
    } else {
      return super.toString() + ": Operation attempted on unknown vertex " + vertex + " (index " + vertex.index() + " sentIndex " + vertex.sentIndex() + ") in graph " + graph;
    }
  }
}
