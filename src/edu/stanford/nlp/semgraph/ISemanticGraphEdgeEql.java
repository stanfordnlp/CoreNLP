package edu.stanford.nlp.semgraph;

/**
 * Interface allowing for different routines to compare for equality over SemanticGraphEdges (typed 
 * lambdas in Java?)
 * @author Eric Yeh
 *
 */
public interface ISemanticGraphEdgeEql {
  public boolean equals(SemanticGraphEdge edge1, SemanticGraphEdge edge2,
        SemanticGraph sg1, SemanticGraph sg2);
  
}
