package edu.stanford.nlp.semgraph.semgrex;

import edu.stanford.nlp.ling.CoreAnnotation;
import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.semgraph.SemanticGraphCoreAnnotations;

/**
 * Track the legal graph names, representing an enum which can be used
 * to verify that a SemgrexPattern was created with a legal name and
 * also used to track the graphs when searching over multiple graphs
 */
public enum SemgrexGraphName {
  BASIC("basic", SemanticGraphCoreAnnotations.BasicDependenciesAnnotation.class),
  ENHANCED("enhanced", SemanticGraphCoreAnnotations.EnhancedDependenciesAnnotation.class);

  public final String lowerName;
  public final Class<? extends CoreAnnotation<SemanticGraph>> annotation;

  public static SemgrexGraphName fromName(String name) {
    for (SemgrexGraphName graph : values()) {
      if (graph.lowerName.equals(name)) {
        return graph;
      }
    }
    return null;
  }

  private SemgrexGraphName(String lowerName, Class<? extends CoreAnnotation<SemanticGraph>> annotation) {
    this.lowerName = lowerName;
    this.annotation = annotation;
  }
}
