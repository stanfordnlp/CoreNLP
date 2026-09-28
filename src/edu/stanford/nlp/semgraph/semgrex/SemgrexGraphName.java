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
  /** The basic dependencies graph */
  BASIC("basic", SemanticGraphCoreAnnotations.BasicDependenciesAnnotation.class),
  /** The enhanced dependencies graph */
  ENHANCED("enhanced", SemanticGraphCoreAnnotations.EnhancedDependenciesAnnotation.class);

  /** The lowercase name used for this graph in a pattern */
  public final String lowerName;
  /** The sentence annotation key under which this graph is stored */
  public final Class<? extends CoreAnnotation<SemanticGraph>> annotation;

  /**
   * Looks up a graph by name, ignoring case.
   *
   * @param name the graph name, such as {@code basic} or {@code enhanced}
   * @return the matching graph, or null if the name is not a legal graph name
   */
  public static SemgrexGraphName fromName(String name) {
    for (SemgrexGraphName graph : values()) {
      if (graph.lowerName.equalsIgnoreCase(name)) {
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
