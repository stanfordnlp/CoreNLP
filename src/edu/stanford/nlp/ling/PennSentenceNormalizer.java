package edu.stanford.nlp.ling;

import java.util.ArrayList;
import java.util.Iterator;

/**
 * A class for Penn tag directory sentence normalization.
 * This one knows about the funny
 * things in Penn Treebank pos files -- like lots of equals signs and
 * square brackets.  It also interns strings.
 * A Singleton.
 *
 * @author Christopher Manning
 */
public class PennSentenceNormalizer<T extends HasWord> extends SentenceNormalizer<T> {

  private boolean divideOffTags; // = false
  private char tagDivider = '/';


  /**
   * Constructs a PennSentenceNormalizer object.
   */
  public PennSentenceNormalizer() {
    super();
  }


  /**
   * Constructs a PennSentenceNormalizer object.
   *
   * @param divideOffTags <code>true</code> iff an unescaped
   *                      <code>tagDivider</code>
   *                      and all characters to the right of it should be cut off
   *                      from words
   * @param tagDivider    The character that separates words from their tags
   */
  public PennSentenceNormalizer(boolean divideOffTags, char tagDivider) {
    super();
    this.divideOffTags = divideOffTags;
    this.tagDivider = tagDivider;
  }


  @Override
  public String normalizeString(String word) {
    if (divideOffTags) {
      int index;
      index = word.lastIndexOf(tagDivider);
      if (index >= 0) {
        word = word.substring(0, index);
      }
    }
    return word.intern();
  }

  /**
   * Normalize a sentence -- this method assumes that the argument
   * that it is passed is the whole (linguistic) <code>Sentence</code>.
   * It is normally implemented as a List-walking routine.
   *
   * @param sent The sentence to be normalized
   * @param lf   the LabelFactory to create new Labels (if needed)
   * @return Sentence the normalized sentence
   */
  @Override
  public ArrayList<T> normalizeSentence(ArrayList<T> sent, LabelFactory lf) {
    String w;
    // using a for loop over an array wouldn't be correct here, as
    // when you remove() something, the others move down in index
    Iterator<T> iter = sent.iterator();
    while (iter.hasNext()) {
      w = iter.next().word();
      if (w.equals("======================================") || w.equals("[") || w.equals("]")) {
        iter.remove();
      }
    }
    return sent;
  }


  /**
   * Returns true if this token represents the end of a sentence.
   * Perhaps shouldn't be in this class, but it seemed a good place
   * since other source-specific handling is here....
   * This is called on the token as read _prior_ to normalization.
   * This seems more useful, as can detect things that are deleted during
   * the normalization process.
   *
   * @param token The <code>String</code> to be checked
   * @param prev  The previous token
   * @param next  The next token (lookahead)
   * @return boolean True if this token is a sentence end
   */
  @Override
  public boolean endSentenceToken(String token, String prev, String next) {
    if (token.endsWith("/.")) {
      if (next != null && (next.equals("''/''") || next.endsWith("/)"))) {
        return false;
      } else {
        return true;
      }
    } else if (token.equals("''/''") || token.endsWith("/)")) {
      if (prev != null && prev.endsWith("/.") && (next == null || (!Character.isLowerCase(next.charAt(0))))) {
        return true;
      } else {
        return false;
      }
    } else {
      return false;
    }
  }

}
