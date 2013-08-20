package edu.stanford.nlp.process;


import edu.stanford.nlp.ling.Word;

/**
 * Constructs a Word from a String. This is the default
 * TokenFactory for PTBLexer. It discards the positional information.
 *
 * @author Jenny Finkel
 */
public class WordTokenFactory implements LexedTokenFactory<Word> {

  public Word makeToken(String str, int begin, int length) {
    return new Word(str, begin, begin+length);
  }
}
