package edu.stanford.nlp.ling;

import java.io.Reader;

/**
 * This class implements a <code>SentenceReaderFactory</code> which is
 * suitable for reading in tagged sentences from the Penn Treebank.  It
 * knows how to clean up the rows of equals and square brackets in that
 * source, and decides sentence endings based on the tagging.
 *
 * @author Christopher Manning
 */
public class PennSentenceReaderFactory implements SentenceReaderFactory<TaggedWord> {

  /**
   * The implementation of the <code>SentenceReaderFactory</code> interface.
   */
  public SentenceReader<TaggedWord> newSentenceReader(Reader in) {
    return new SentenceReader<TaggedWord>(in, new TaggedWordFactory(), new PennSentenceNormalizer<TaggedWord>(), new PennTagbankStreamTokenizer(in));
  }

}
