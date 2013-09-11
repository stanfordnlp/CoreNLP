package edu.stanford.nlp.ling;

import java.io.Reader;

/**
 * This class implements a simple default <code>SentenceReaderFactory</code>.
 *
 * @author Christopher Manning
 */
public class SimpleSentenceReaderFactory<T extends HasWord> implements SentenceReaderFactory<T> {

  /**
   * The implementation of the <code>SentenceReaderFactory</code> interface.
   * It creates a simple <code>SentenceReader</code> which literally
   * reproduces sentences in the SentenceBank.
   */
  public SentenceReader<T> newSentenceReader(Reader in) {
    return new SentenceReader<T>(in);
  }

}
