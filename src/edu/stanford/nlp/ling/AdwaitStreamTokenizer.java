package edu.stanford.nlp.ling;

import java.io.Reader;

/**
 * Builds a tokenizer for files where whitespace separates tokens,
 * and eol is significant.  This encoding is used in Adwait-style pos
 * tagged files.
 *
 * @author Christopher Manning
 * @version 2001/01/01
 */
public class AdwaitStreamTokenizer extends PennTagbankStreamTokenizer {

  /**
   * Create a tokenizer for Adwait-style sentences.
   * This sets up simple character meanings for all non-whitespace chars
   *
   * @param r The reader steam
   */
  public AdwaitStreamTokenizer(Reader r) {
    super(r);
    // crucially, eol marks end of sentence.
    eolIsSignificant(true);
  }

}
