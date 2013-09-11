package edu.stanford.nlp.ling;

/**
 * A class for sentence normalization.  The
 * <code>OnePerLineSentenceNormalizer</code> deals with line-based sentences
 * and does minimal other normalization.
 *
 * @author Christopher Manning
 */
public class OnePerLineSentenceNormalizer<T extends HasWord> extends SentenceNormalizer<T> {

  public OnePerLineSentenceNormalizer() {
  }

  /**
   * This function can be checked by a <code>SentenceReader</code> so as
   * to know whether an end-of-line is always to be treated as an
   * end-of-sentence. If this is true, then the
   * <code>endSentenceToken()</code> function is not used.
   *
   * @return true if an eol is always a sentence end
   */
  @Override
  public boolean eolIsSentenceEnd() {
    return true;
  }

}
