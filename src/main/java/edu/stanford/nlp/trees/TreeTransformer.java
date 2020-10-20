package edu.stanford.nlp.trees;

import java.util.function.Function;

/**
 * This is a simple interface for a function that alters a
 * local {@code Tree}.
 *
 * @author Christopher Manning.
 */
public interface TreeTransformer extends Function<Tree,Tree> {

  /**
   * Does whatever one needs to do to a particular tree.
   * This routine is passed a whole {@code Tree}, and could itself
   * work recursively, but the canonical usage is to invoke this method
   * via the {@code Tree.transform()} method, which will apply the
   * transformer in a bottom-up manner to each local {@code Tree},
   * and hence the implementation of {@code TreeTransformer} should
   * merely examine and change a local (one-level) {@code Tree}.
   *
   * @param t  A tree.  Classes implementing this interface can assume
   *           that the tree passed in is not {@code null}.
   * @return The transformed {@code Tree}
   */
  Tree transformTree(Tree t);

  @Override
  default Tree apply(Tree t) {
    return transformTree(t);
  }

}
