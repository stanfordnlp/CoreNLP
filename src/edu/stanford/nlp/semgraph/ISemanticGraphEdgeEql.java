package edu.stanford.nlp.semgraph;

/**
 * Interface allowing for different routines to compare for equality over SemanticGraphEdges (typed 
 * lambdas in Java?)
 * @author Eric Yeh
 *
 */
public interface ISemanticGraphEdgeEql {
  /**
   * Compares two edges for equality under this implementation's notion of equality.
   *
   * @param edge1 the first edge
   * @param edge2 the second edge
   * @param sg1 the graph containing {@code edge1}
   * @param sg2 the graph containing {@code edge2}
   * @return whether the two edges are considered equal
   */
  public boolean equals(SemanticGraphEdge edge1, SemanticGraphEdge edge2,
        SemanticGraph sg1, SemanticGraph sg2);
  
}
