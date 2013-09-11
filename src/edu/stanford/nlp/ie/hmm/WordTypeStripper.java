package edu.stanford.nlp.ie.hmm;

import edu.stanford.nlp.ling.TypedTaggedWord;
import edu.stanford.nlp.util.Function;

/**
 * Function that sets the type of a {@link edu.stanford.nlp.ling.TypedTaggedWord} to 0.
 *
 * @author Joseph Smarr (jsmarr@stanford.edu)
 * @see #apply
 */
public class WordTypeStripper implements Function {
  /**
   * If <tt>in</tt> is a  <tt>TypedTaggedWord</tt>, returns a new copy of the
   * word with the type set to 0. Otherwise returns <tt>in</tt> directly.
   * <p>NOTE: Ideally one could just test for <tt>HasType</tt> and then make
   * a copy of <tt>in</tt> and call <tt>setType</tt> but it's not clear how
   * to make the copy.
   */
  public Object apply(Object in) {
    if (in instanceof TypedTaggedWord) {
      TypedTaggedWord oldTTW = (TypedTaggedWord) in;
      TypedTaggedWord newTTW = new TypedTaggedWord(oldTTW.word(), oldTTW.tag(), 0);
      return (newTTW);
    } else {
      return (in);
    }
  }
}
