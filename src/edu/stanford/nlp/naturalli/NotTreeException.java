package edu.stanford.nlp.naturalli;

import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.semgraph.SemanticGraphEdge;

public class NotTreeException extends RuntimeException {
  public NotTreeException(SemanticGraph graph, SemanticGraphEdge edge) {
    super("The graph \n" + graph + "\nis not a tree after removing\n" + edge);
  }
}
