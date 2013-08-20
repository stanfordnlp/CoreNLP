package edu.stanford.nlp.ling;

import java.io.Reader;
import java.io.StreamTokenizer;

/**
 * Builds a tokenizer for Penn pos tagged directories.
 *
 * @author Christopher Manning
 * @version 2000/12/21
 */
public class PennTagbankStreamTokenizer extends StreamTokenizer {

  /**
   * Create a tokenizer for PennTreebank trees.
   * This sets up all the character meanings for treebank trees.
   *
   * @param r The reader steam
   */
  public PennTagbankStreamTokenizer(Reader r) {
    super(r);
    // start with new tokenizer syntax -- everything ordinary
    resetSyntax();

    // treat chars in words as words, like a-zA-Z
    // treat all the typewriter keyboard symbols as parts of words
    // You need to look at an ASCII table to understand this!
    wordChars('!', '/');
    wordChars('0', '9');
    wordChars(':', '@');
    wordChars('A', 'Z');
    wordChars('[', '`');
    wordChars('a', 'z');
    wordChars('{', '~');
    wordChars(128, 255); //C.Thompson, added 11/02 // 8 bit stuff only

    // take the normal white space charaters, including tab, return,
    // space
    whitespaceChars(0, ' ');
  }

}
