package edu.stanford.nlp.naturalli;

import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.semgraph.SemanticGraphEdge;

public class NotTreeException extends RuntimeException {
  SemanticGraph brokenGraph;
  SemanticGraph originalGraph;

  public NotTreeException(SemanticGraph graph, SemanticGraph originalGraph) {
    super("The graph \n" + graph + "\nis not a tree after its surgery.  Original graph:\n" + originalGraph);
    this.brokenGraph = graph;
    this.originalGraph = originalGraph;
  }
}
