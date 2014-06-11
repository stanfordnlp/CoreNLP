package edu.stanford.nlp.ling;

import java.io.DataInputStream;
import java.io.DataOutputStream;

/**
 * A WordTag corresponds to a tagged (e.g., for part of speech) word
 * and is implemented with String-valued word and tag.  It implements
 * the Label interface; the <code>value()</code> method for that
 * interface corresponds to the word of the WordTag.
 * <p/>
 * The equality relation for WordTag is defined as identity of both
 * word and tag.  Note that this is different from
 * <code>TaggedWord</code>, for which equality derives from
 * <code>ValueLabel</code> and requires only identity of value.
 *
 * @author Roger Levy
 */

public class WordTag implements Label, HasWord, HasTag, Comparable<WordTag> {

  private String word;
  private String tag;
  private static final String DIVIDER = "/";

  /**
   * Create a new <code>WordTag</code>.
   *
   * @param word This word is passed to the supertype constructor
   * @param tag  The <code>value()</code> of this label is set as the
   *             tag of this Label
   */
  public WordTag(String word, String tag) {
    setWord(word);
    setTag(tag);
  }

  public WordTag(String word) {
    this(word, null);
  }

  public <E extends Label & HasTag> WordTag(E word) {
    this(word.value(), word.tag());
  }

  private WordTag() { }  // only used internally for doing setFromString()

  /**
   * Create a new <code>WordTag</code> from a Label.  The value of
   * the Label corresponds to the word of the WordTag.
   *
   * @param word The <code>value()</code> of this label is set as the
   *             word of the <code>WordTag</code>
   * @param tag  The <code>value()</code> of this label is set as the
   *             tag of the <code>WordTag</code>
   */
  public WordTag(Label word, Label tag) {
    this(word.value(), tag.value());
  }


  public static WordTag valueOf(String s) {
    WordTag result = new WordTag();
    result.setFromString(s);
    return result;
  }

  public static WordTag valueOf(String s, String tagDivider) {
    WordTag result = new WordTag();
    result.setFromString(s, tagDivider);
    return result;
  }

  /**
   * Return a String representation of just the "main" value of this label.
   *
   * @return the "value" of the label
   */
  public String value() {
    return word;
  }

  public String word() {
    return value();
  }

  /**
   * Set the value for the label (if one is stored).
   *
   * @param value - the value for the label
   */
  public void setValue(String value) {
    word = value;
  }

  public String tag() {
    return tag;
  }

  public void setWord(String word) {
    setValue(word);
  }

  public void setTag(String tag) {
    this.tag = tag;
  }


  /**
   * Return a String representation of the label.  For a multipart label,
   * this will return all parts.  The <code>toString()</code> method
   * causes a label to spill its guts.  It should always return an
   * empty string rather than <code>null</code> if there is no value.
   *
   * @return a text representation of the full label contents
   */
  @Override
  public String toString() {
    return toString(DIVIDER);
  }

  public String toString(String divider) {
    String tag = tag();
    if (tag == null) {
      return word();
    } else {
      return word() + divider + tag;
    }
  }


  /**
   * Sets a WordTag from decoding
   * the <code>String</code> passed in.  The String is divided according
   * to the divider character (usually, "/").  We assume that we can
   * always just
   * divide on the rightmost divider character, rather than trying to
   * parse up escape sequences.  If the divider character isn't found
   * in the word, then the whole string becomes the word, and the tag
   * is <code>null</code>.
   *
   * @param wordTagString The word that will go into the <code>Word</code>
   */
  @Override
  public void setFromString(String wordTagString) {
    setFromString(wordTagString, DIVIDER);
  }

  public void setFromString(String wordTagString, String divider) {
    int where = wordTagString.lastIndexOf(divider);
    if (where >= 0) {
      setWord(wordTagString.substring(0, where).intern());
      setTag(wordTagString.substring(where + 1).intern());
    } else {
      setWord(wordTagString.intern());
      setTag(null);
    }
  }

  /** A WordTag is equal only to another WordTag with the same word and tag values.
   */
  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof WordTag)) return false;
    final WordTag wordTag = (WordTag) o;
    if (tag != null ? !tag.equals(wordTag.tag) : wordTag.tag != null) return false;
    if (word != null ? !word.equals(wordTag.word) : wordTag.word != null) return false;
    return true;
  }

  @Override
  public int hashCode() {
    int result;
    result = (word != null ? word.hashCode() : 0);
    result = 29 * result + (tag != null ? tag.hashCode() : 0);
    return result;
  }

  /**
   * Orders first by word, then by tag.
   *
   * @param wordTag object to compare to
   * @return result (positive if <code>this</code> is greater than
   *         <code>obj</code>, 0 if equal, negative otherwise)
   */
  public int compareTo(WordTag wordTag) {
    int first = (word != null ? word().compareTo(wordTag.word()) : 0);
    if(first != 0)
      return first;
    else {
      if (tag() == null) {
        if (wordTag.tag() == null)
          return 0;
        else
          return -1;
      }
      return tag().compareTo(wordTag.tag());
    }
  }


  // extra class guarantees correct lazy loading (Bloch p.194)
  private static class LabelFactoryHolder {

    private static final LabelFactory lf = new WordTagFactory();

  }

  /**
   * Return a factory for this kind of label
   * (i.e., <code>TaggedWord</code>).
   * The factory returned is always the same one (a singleton).
   *
   * @return The label factory
   */
  public LabelFactory labelFactory() {
    return LabelFactoryHolder.lf;
  }


  /**
   * Return a factory for this kind of label.
   *
   * @return The label factory
   */
  public static LabelFactory factory() {
    return LabelFactoryHolder.lf;
  }


  public void read(DataInputStream in) {
    try {
      word = in.readUTF();
      tag = in.readUTF();

    } catch (Exception e) {
      e.printStackTrace();
    }
  }


  public void save(DataOutputStream out) {
    try {
      out.writeUTF(word);
      out.writeUTF(tag);
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  private static final long serialVersionUID = -1859527239216813742L;

}
