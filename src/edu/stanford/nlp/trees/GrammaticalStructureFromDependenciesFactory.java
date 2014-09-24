package edu.stanford.nlp.trees;

import java.util.List;

/**
 * An interface for a factory that builds a GrammaticalStructure from
 * a list of TypedDependencies and a TreeGraphNode.  This is useful
 * when building a GrammaticalStructure from a conll data file, for example.
 *
 * @author John Bauer
 */
public interface GrammaticalStructureFromDependenciesFactory {
  GrammaticalStructure build(List<TypedDependency> projectiveDependencies, TreeGraphNode root);
}
