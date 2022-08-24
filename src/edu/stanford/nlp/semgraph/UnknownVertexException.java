package edu.stanford.nlp.semgraph;

import edu.stanford.nlp.ling.IndexedWord;

public class UnknownVertexException extends IllegalArgumentException {
  public final IndexedWord vertex;
  public final SemanticGraph graph;

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
