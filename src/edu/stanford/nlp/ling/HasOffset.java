package edu.stanford.nlp.ling;

import java.io.Serializable;

/**
 * Something that implements the {@code HasOffset} interface
 * carries char offset references to an original text String.
 *
 * @author Richard Eckart (Technische Universitat Darmstadt)
 */
public interface HasOffset extends Serializable {

  /**
   * Return the beginning char offset of the label (or -1 if none).
   * Note that these are currently measured in terms of UTF-16 char offsets, not codepoints,
   * so that when non-BMP Unicode characters are present, such a character will add 2 to
   * the position. On the other hand, these values will work with String#substring() and
   * you can then calculate the number of codepoints in a substring.
   *
   * @return the beginning position for the label
   */
  int beginPosition();

  /**
   * Set the beginning character offset for the label.
   * Setting this key to "-1" can be used to indicate no valid value.
   *
   * @param beginPos The beginning position
   */
  void setBeginPosition(int beginPos);

  /**
   * Return the ending char offset of the label (or -1 if none).
   * As usual in Java, this is the offset of the char <i>after</i> this token.
   * Note that these are currently measured in terms of UTF-16 char offsets, not codepoints,
   * so that when non-BMP Unicode characters are present, such a character will add 2 to
   * the position. On the other hand, these values will work with String#substring() and
   * you can then calculate the number of codepoints in a substring.
   *
   * @return the end position for the label
   */
  int endPosition();

  /**
   * Set the ending character offset of the label (or -1 if none).
   *
   * @param endPos The end character offset for the label
   */
  void setEndPosition(int endPos);

}
