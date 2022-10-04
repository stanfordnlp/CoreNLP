package edu.stanford.nlp.graph;

public class CyclicGraphException extends IllegalStateException {
  public final String error;
  public final DirectedMultiGraph<?, ?> graph;

  public CyclicGraphException(String error, DirectedMultiGraph<?, ?> graph) {
    this.error = error;
    this.graph = graph;
  }

  public String toString() {
    return super.toString() + ": " + error + "\nGraph:\n" + graph;
  }
}
