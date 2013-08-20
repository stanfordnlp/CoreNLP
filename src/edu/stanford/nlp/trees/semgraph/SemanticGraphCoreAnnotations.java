package edu.stanford.nlp.trees.semgraph;

import edu.stanford.nlp.ling.CoreAnnotation;


/** Keeps SemanticGraphCoreAnnotations so they do not introduce
 *  dependencies for code not using the jgrapht library.
 *
 *  @author Christopher Manning
 */
public class SemanticGraphCoreAnnotations {

  /**
   * The CoreMap key for getting the syntactic dependencies of a sentence.
   * These are collapsed dependencies!
   *
   * This key is typically set on sentence annotations.
   */
  public static class CollapsedDependenciesAnnotation implements CoreAnnotation<SemanticGraph> {
    public Class<SemanticGraph> getType() {
      return SemanticGraph.class;
    }
  }


  /**
   * The CoreMap key for getting the syntactic dependencies of a sentence.
   * These are basic dependencies without any post-processing!
   *
   * This key is typically set on sentence annotations.
   */
  public static class BasicDependenciesAnnotation implements CoreAnnotation<SemanticGraph> {
    public Class<SemanticGraph> getType() {
      return SemanticGraph.class;
    }
  }


  /**
   * The CoreMap key for getting the syntactic dependencies of a sentence.
   * These are dependencies that are both collapsed and have CC processing!
   *
   * This key is typically set on sentence annotations.
   */
  public static class CollapsedCCProcessedDependenciesAnnotation implements CoreAnnotation<SemanticGraph> {
    public Class<SemanticGraph> getType() {
      return SemanticGraph.class;
    }
  }

}
