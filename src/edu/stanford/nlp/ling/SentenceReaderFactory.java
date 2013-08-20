package edu.stanford.nlp.ling;

import java.io.Reader;

/**
 * A <code>SentenceReaderFactory</code> is a factory for creating objects of
 * class <code>SentenceReader</code>, or some descendent class.
 *
 * @author Christopher Manning
 */
public interface SentenceReaderFactory<T extends HasWord> {

  /**
   * The factory for making a new <code>SentenceReader</code>.
   *
   * @param in The Reader to read sentences from.
   * @return A SentenceReader that reads sentences.
   */
  public SentenceReader<T> newSentenceReader(Reader in);

}
