package edu.stanford.nlp.trees;

import edu.stanford.nlp.util.Filter;

/** 
 * Only accept trees that are short enough (less than or equal to length).
 * It's not always about length, but in this case it is.
 *
 *  @author John Bauer
 */
public class LengthTreeFilter implements Filter<Tree> {
  private int length;

  public LengthTreeFilter(int length) {
    this.length = length;
  }

  public boolean accept(Tree tree) {
    return tree.yield().size() <= length;
  }

  private static final long serialVersionUID = 1;
}
