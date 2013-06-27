package edu.stanford.nlp.parser.lexparser;

import edu.stanford.nlp.ling.TaggedWord;
import edu.stanford.nlp.util.Index;
import edu.stanford.nlp.util.StringUtils;

import java.io.Serializable;

/** Represents a WordTag (in the sense that equality is defined
 *  on both components), where each half is represented by an
 *  int indexed by a Index.  In this representation, -1 is
 *  used to represent the wildcard ANY value, and -2 is used
 *  to represent a STOP value (i.e., no more dependents).
 *
 * TODO: does that cause any problems regarding unseen words also being -1?
 * TODO: any way to not have links to the Index in each object?
 *
 *  @author Dan Klein
 *  @author Christopher Manning
 */
public class IntTaggedWord implements Serializable, Comparable<IntTaggedWord> {

  public static final int ANY_WORD_INT = -1;
  public static final int ANY_TAG_INT = -1;
  public static final int STOP_WORD_INT = -2;
  public static final int STOP_TAG_INT = -2;

  public static final String ANY = ".*.";
  public static final String STOP = "STOP";

  public final int word;
  public final short tag;

  public int tag() {
    return tag;
  }

  public int word() {
    return word;
  }

  public String wordString(Index<String> wordIndex) {
    String wordStr;
    if (word >= 0) {
      wordStr = wordIndex.get(word);
    } else if (word == ANY_WORD_INT) {
      wordStr = ANY;
    } else {
      wordStr = STOP;
    }
    return wordStr;
  }

  public String tagString(Index<String> tagIndex) {
    String tagStr;
    if (tag >= 0) {
      tagStr = tagIndex.get(tag);
    } else if (tag == ANY_TAG_INT) {
      tagStr = ANY;
    } else {
      tagStr = STOP;
    }
    return tagStr;
  }

  @Override
  public int hashCode() {
    return word ^ (tag << 16);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    } else if (o instanceof IntTaggedWord) {
      IntTaggedWord i = (IntTaggedWord) o;
      return (word == i.word && tag == i.tag);
    } else {
      return false;
    }
  }

  public int compareTo(IntTaggedWord that) {
    if (tag != that.tag) {
      return tag - that.tag;
    } else {
      return word - that.word;
    }
  }

  private static final char[] charsToEscape = { '\"' };


  public String toLexicalEntry(Index<String> wordIndex,
                               Index<String> tagIndex) {
    String wordStr = wordString(wordIndex);
    String tagStr = tagString(tagIndex);
    return '\"' + StringUtils.escapeString(tagStr, charsToEscape, '\\') + "\" -> \"" + StringUtils.escapeString(wordStr, charsToEscape, '\\') + '\"';
  }

  @Override
  public String toString() {
    return word + "/" + tag;
  }

  public String toString(Index<String> wordIndex, Index<String> tagIndex) {
    return wordString(wordIndex)+ '/' +tagString(tagIndex);
  }

  public String toString(String arg,
                         Index<String> wordIndex, Index<String> tagIndex) {
    if (arg.equals("verbose")) {
      return (wordString(wordIndex) + '[' + word + "]/" +
              tagString(tagIndex) + '[' + tag + ']');
    } else {
      return toString(wordIndex, tagIndex);
    }
  }

  public IntTaggedWord(int word, int tag) {
    this.word = word;
    this.tag = (short) tag;
  }

  public TaggedWord toTaggedWord(Index<String> wordIndex,
                                 Index<String> tagIndex) {
    String wordStr = wordString(wordIndex);
    String tagStr = tagString(tagIndex);
    return new TaggedWord(wordStr, tagStr);
  }

  /**
   * Creates an IntTaggedWord given by the String representation
   * of the form &lt;word&gt;|&lt;tag*gt;
   */
  public IntTaggedWord(String s, char splitChar,
                       Index<String> wordIndex, Index<String> tagIndex) {
    // awkward, calls s.indexOf(splitChar) twice
    this(extractWord(s, splitChar), extractTag(s, splitChar),
         wordIndex, tagIndex);
    //    System.out.println("s: " + s);
    //    System.out.println("tagIndex: " + tagIndex);
    //    System.out.println("word: " + word);
    //    System.out.println("tag: " + tag);
  }

  private static String extractWord(String s, char splitChar) {
    int n = s.lastIndexOf(splitChar);
    String result = s.substring(0, n);
    //    System.out.println("extracted word: " + result);
    return result;
  }

  private static String extractTag(String s, char splitChar) {
    int n = s.lastIndexOf(splitChar);
    String result = s.substring(n + 1);
    //    System.out.println("extracted tag: " + result);
    return result;
  }

  /**
   * Creates an IntTaggedWord given by the tagString and wordString
   */
  public IntTaggedWord(String wordString, String tagString,
                       Index<String> wordIndex, Index<String> tagIndex) {
    if (wordString.equals(ANY)) {
      word = ANY_WORD_INT;
    } else if (wordString.equals(STOP)) {
      word = STOP_WORD_INT;
    } else {
      word = wordIndex.indexOf(wordString, true);
    }
    if (tagString.equals(ANY)) {
      tag = (short) ANY_TAG_INT;
    } else if (tagString.equals(STOP)) {
      tag = (short) STOP_TAG_INT;
    } else {
      tag = (short) tagIndex.indexOf(tagString, true);
    }
  }

  private static final long serialVersionUID = 1L;

} // end class IntTaggedWord
