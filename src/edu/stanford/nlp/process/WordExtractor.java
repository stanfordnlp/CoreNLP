package edu.stanford.nlp.process;


import edu.stanford.nlp.ling.HasWord;
import edu.stanford.nlp.util.Function;

/**
 * Pulls the word String from a (Has)Word.
 *
 * @author Joseph Smarr (jsmarr@stanford.edu)
 * @author Sarah Spikes (sdspikes@cs.stanford.edu) (Templatization)
 *
 * @param <F> (Must extend HasWord)
 */
public class WordExtractor<F extends HasWord> implements Function<F,String> {
  /**
   * Returns <tt>((HasWord)in).word()</tt>.
   */
  public String apply(F in) {
    return in.word();
  }

}
