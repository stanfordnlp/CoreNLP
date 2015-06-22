package edu.stanford.nlp.trees;

import java.io.Serializable;
import java.util.function.Predicate;

/** 
 * Only accept trees that are short enough (less than or equal to length).
 * It's not always about length, but in this case it is.
 *
 *  @author John Bauer
 */
public class LengthTreeFilter implements Predicate<Tree>, Serializable {
  private int length;

  public LengthTreeFilter(int length) {
    this.length = length;
  }

  public boolean test(Tree tree) {
    return tree.yield().size() <= length;
  }

  private static final long serialVersionUID = 1;
}
