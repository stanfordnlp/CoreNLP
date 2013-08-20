package edu.stanford.nlp.ling;

import java.io.Reader;

/**
 * This class implements a <code>SentenceReaderFactory</code> which is
 * suitable for reading in tagged or untagged sentences formatted
 * one per line with underscores used only to separate words from POS tags.
 * These are the conventions used in Adwait Ratnaparkhi's Java MXPOST and
 * MXTERMINATOR software.  The conventions are these: End-of-line
 * indicates a sentence end.  Tags are separated from words by an
 * underscore character (_).  The underscore character does not appear in
 * words.
 *
 * @author Christopher Manning
 * @version 2001/01/01
 */
public class AdwaitSentenceReaderFactory<T extends HasWord> implements SentenceReaderFactory<T> {

  private LabelFactory lf;


  /**
   * Constructs an AdwaitSentenceReaderFactory.
   */
  public AdwaitSentenceReaderFactory() {
    this(true);
  }


  /**
   * Create a new <code>AdwaitSentenceReaderFactory</code>.
   *
   * @param taggedWords Set to <code>true</code> if it should created
   *                    TaggedWord objects, <code>false</code> if it should
   *                    create Word objects
   */
  public AdwaitSentenceReaderFactory(boolean taggedWords) {
    this(taggedWords, '_');
  }


  /**
   * Create a new <code>AdwaitSentenceReaderFactory</code>.
   *
   * @param taggedWords Set to <code>true</code> if it should created
   *                    TaggedWord objects, <code>false</code> if it should
   *                    create Word objects
   */
  public AdwaitSentenceReaderFactory(boolean taggedWords, char tagSeparator) {
    if (taggedWords) {
      lf = new TaggedWordFactory(tagSeparator);
    } else {
      lf = new WordFactory();
    }
  }


  /**
   * Create a new <code>AdwaitSentenceReaderFactory</code>.
   *
   * @param lf LabelFactory to be used to create type for words read in
   */
  public AdwaitSentenceReaderFactory(LabelFactory lf) {
    this.lf = lf;
  }


  /**
   * Implementation of the <code>SentenceReaderFactory</code> interface.
   *
   * @param in The Reader for input
   */
  public SentenceReader<T> newSentenceReader(Reader in) {
    return new SentenceReader<T>(in, lf, new OnePerLineSentenceNormalizer<T>(), new AdwaitStreamTokenizer(in));
  }

}
