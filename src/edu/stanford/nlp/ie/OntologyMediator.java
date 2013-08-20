package edu.stanford.nlp.ie;

import java.util.Set;

/**
 * Generic interface for exposing basic information about an Ontology.
 * The IE code should be able to work with mutliple ontology implementations,
 * but some of the code needs to know class names and slot names for classes.
 * The OntologyMediator interface allows this abstraction: implementations can
 * be written for each specific ontology implementation and provide a common
 * description through this class.
 *
 * @author Joseph Smarr (jsmarr@stanford.edu)
 */
public interface OntologyMediator {
  /**
   * Returns the set of unique class (concept) names in the ontology (as Strings).
   */
  public Set<String> getClassNames();

  /**
   * Returns the set of unique slot names (properties) for the given class name (as Strings).
   */
  public Set<String> getSlotNames(String className);

  /**
   * Returns the set of class names that are parents of the given class (as Strings).
   * In a single-inheritence hierarchy, this is at most one parent, but in a
   * multiple-inheritence hierarchy a class may have several parents.
   */
  public Set<String> getSuperclasses(String className);
}
