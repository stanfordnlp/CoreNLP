package edu.stanford.nlp.ling;

import java.util.ArrayList;

/**
 * A class for sentence normalization.  Part of the job of a
 * <code>SentenceNormalizer</code> is to encode what is a sentence end.
 * The default one does no
 * normalization, but implements Penn Treebank rules for a sentence end.
 * Other sentence normalizers will change various node labels.
 * Another operation that a <code>SentenceNormalizer</code>
 * may wish to perform is interning the <code>String</code>'s passed to
 * it.  A Singleton.  Designed to be overriden.
 *
 * @author Christopher Manning
 */
public class SentenceNormalizer<T extends HasWord> {

  public SentenceNormalizer() {
  }


  /**
   * Normalizes a read string word (and maybe intern it).
   *
   * @param word The word to normalize
   * @return The normalized form
   */
  public String normalizeString(String word) {
    return word;
  }


  /**
   * Normalize a sentence -- this method assumes that the argument
   * that it is passed is the whole (linguistic) <code>Sentence</code>.
   * It is normally implemented as a List-walking routine.  It is
   * assumed that the unnormalized sentence can be destructively
   * modified, as it is otherwise unneeded.
   *
   * @param sent The sentence to be normalized
   * @param lf   the LabelFactory to create new words (if needed)
   * @return Sentence the normalized sentence
   */
  public ArrayList<T> normalizeSentence(ArrayList<T> sent, LabelFactory lf) {
    return sent;
  }


  /**
   * This function can be checked by a <code>SentenceReader</code> so as
   * to know whether an end-of-line is always to be treated as an
   * end-of-sentence. If this is true, then the
   * <code>endSentenceToken()</code> function is not used.
   *
   * @return true if an eol is always a sentence end
   */
  public boolean eolIsSentenceEnd() {
    return false;
  }


  /**
   * Returns true if this token represents the end of a sentence.
   * Perhaps shouldn't be in this class, but it seemed a good place
   * since other source-specific handling is here....
   * <p>CDM 2007: This is actually PTB specific handling and should really
   * go elsewhere. Default could be one-per-line?
   *
   * @param token The <code>String</code> to be checked
   * @param prev  The previous token
   * @param next  The next token (lookahead)
   * @return boolean True if this token is a sentence end
   */
  public boolean endSentenceToken(String token, String prev, String next) {
    return token == null || token.endsWith("/.");
  }

}
