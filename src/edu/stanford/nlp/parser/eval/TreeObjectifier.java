package edu.stanford.nlp.parser.eval;

import edu.stanford.nlp.trees.Tree;

import java.util.Collection;

/**
 * an interface specifying a Tree->Collection function.
 *
 * @author Roger Levy
 * @author Sarah Spikes (sdspikes@cs.stanford.edu) (Templatization)
 *
 * @param <T> The type in the Collection
 */
public interface TreeObjectifier<T> {
  public Collection<T> objectify(Tree t);
}
