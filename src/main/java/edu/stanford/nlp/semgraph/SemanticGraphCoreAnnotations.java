package edu.stanford.nlp.semgraph;

import edu.stanford.nlp.ling.CoreAnnotation;


/** This class collects CoreAnnotations that are used in working with a
 *  SemanticGraph.  (These were originally separated out at a time when
 *  a SemanticGraph was backed by the JGraphT library so as not to
 *  introduce a library dependency for some tools. This is no longer
 *  the case, but they remain gathered here.)
 *
 *  @author Christopher Manning
 */
public class SemanticGraphCoreAnnotations {

  /**
   * The CoreMap key for getting the syntactic dependencies of a sentence.
   * These are collapsed dependencies!
   *
   * This key is typically set on sentence annotations.
   *
   * @deprecated In the future, we will only provide basic, enhanced, and
   * enhanced++ dependencies.
   */
  @Deprecated
  public static class CollapsedDependenciesAnnotation implements CoreAnnotation<SemanticGraph> {
    @Override
    public Class<SemanticGraph> getType() {
      return SemanticGraph.class;
    }
  }


  /**
   * The CoreMap key for getting the syntactic dependencies of a sentence.
   * These are basic dependencies without any post-processing!
   *
   * This key is typically set on sentence annotations.
   *
   */
  public static class BasicDependenciesAnnotation implements CoreAnnotation<SemanticGraph> {
    @Override
    public Class<SemanticGraph> getType() {
      return SemanticGraph.class;
    }
  }


  /**
   * The CoreMap key for getting the syntactic dependencies of a sentence.
   * These are dependencies that are both collapsed and have CC processing!
   *
   * This key is typically set on sentence annotations.
   *
   * @deprecated In the future, we will only provide basic, enhanced, and
   * enhanced++ dependencies.
   */
  @Deprecated
  public static class CollapsedCCProcessedDependenciesAnnotation implements CoreAnnotation<SemanticGraph> {
    @Override
    public Class<SemanticGraph> getType() {
      return SemanticGraph.class;
    }
  }

  /**
   * The CoreMap key for getting the syntactic dependencies of a sentence.
   * These are the enhanced dependencies.
   *
   * This key is typically set on sentence annotations.
   */
  public static class EnhancedDependenciesAnnotation implements CoreAnnotation<SemanticGraph> {
    @Override
    public Class<SemanticGraph> getType() {
      return SemanticGraph.class;
    }
  }

  /**
   * The CoreMap key for getting the syntactic dependencies of a sentence.
   * These are the enhanced++ dependencies.
   *
   * This key is typically set on sentence annotations.
   */
  public static class EnhancedPlusPlusDependenciesAnnotation implements CoreAnnotation<SemanticGraph> {
    @Override
    public Class<SemanticGraph> getType() {
      return SemanticGraph.class;
    }
  }

  /**
   * The CoreMap key for storing a semantic graph that was converted using a non-default converter.
   * Currently only used by the DeterministicCorefAnnotator to store the original Stanford dependencies.
   *
   * This key is typically set on sentence annotations.
   */
  public static class AlternativeDependenciesAnnotation implements CoreAnnotation<SemanticGraph> {
    @Override
    public Class<SemanticGraph> getType() {
      return SemanticGraph.class;
    }
  }

  /**
   * An enum to represent the three types of dependencies generally supported
   */
  public enum DependenciesType {
    BASIC            (BasicDependenciesAnnotation.class),
    ENHANCED         (EnhancedDependenciesAnnotation.class),
    ENHANCEDPLUSPLUS (EnhancedPlusPlusDependenciesAnnotation.class);

    private final Class<? extends CoreAnnotation<SemanticGraph>> annotation;

    DependenciesType(Class<? extends CoreAnnotation<SemanticGraph>> annotation) {
      this.annotation = annotation;
    }

    public Class<? extends CoreAnnotation<SemanticGraph>> annotation() {
      return this.annotation;
    }
  }
}
