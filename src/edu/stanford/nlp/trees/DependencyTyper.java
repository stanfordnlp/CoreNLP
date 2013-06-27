package edu.stanford.nlp.trees;

/** A generified interface for making some kind of dependency object
 *  between a head and dependent.
 *
 *  @author Roger Levy
 */
public interface DependencyTyper<T> {

  /** Make a dependency given the Tree that is the head and the dependent,
   *  both of which are contained within root.
   */
  T makeDependency(Tree head, Tree dep, Tree root);

}

