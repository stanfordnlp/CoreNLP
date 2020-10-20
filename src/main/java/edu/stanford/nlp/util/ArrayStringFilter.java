package edu.stanford.nlp.util;

import java.io.Serializable;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Filters Strings based on whether they exactly match any string in
 * the array it is initially constructed with.  Saves some time over
 * using regexes if the array of strings is small enough.  No specific
 * experiments exist for how long the array can be before performance
 * is worse than a regex, but the English dependencies code was helped
 * by replacing disjunction regexes of 6 words or fewer with this.
 *
 * @author John Bauer
 */
public class ArrayStringFilter implements Predicate<String>, Serializable {
  private final String[] words;
  private final int length;
  private final Mode mode;

  public enum Mode {
    EXACT, PREFIX, CASE_INSENSITIVE
  }

  public ArrayStringFilter(Mode mode, String ... words) {
    if (mode == null) {
      throw new NullPointerException("Cannot handle null mode");
    }
    this.mode = mode;
    this.words = new String[words.length];
    System.arraycopy(words, 0, this.words, 0, words.length);
    this.length = words.length;
  }

  @Override
  public boolean test(String input) {
    switch (mode) {
    case EXACT:
      for (int i = 0; i < length; ++i) {
        if (words[i].equals(input)) {
          return true;
        }
      }
      return false;
    case PREFIX:
      if (input == null) {
        return false;
      }
      for (int i = 0; i < length; ++i) {
        if (input.startsWith(words[i])) {
          return true;
        }
      }
      return false;
    case CASE_INSENSITIVE:
      for (int i = 0; i < length; ++i) {
        if (words[i].equalsIgnoreCase(input)) {
          return true;
        }
      }
      return false;
    default:
      throw new IllegalArgumentException("Unknown mode " + mode);
    }
  }

  @Override
  public String toString() {
    return mode.toString() + ':' + StringUtils.join(words, ",");
  }

  @Override
  public int hashCode() {
    int result = 1;
    for (String word : words) {
      result += word.hashCode();
    }
    return result;
  }

  @Override
  public boolean equals(Object other) {
    if (other == this) {
      return true;
    }
    if (!(other instanceof ArrayStringFilter)) {
      return false;
    }
    ArrayStringFilter filter = (ArrayStringFilter) other;
    if (filter.mode != this.mode || filter.length != this.length) {
      return false;
    }
    Set<String> myWords = new HashSet<>(Arrays.asList(this.words));
    Set<String> otherWords = new HashSet<>(Arrays.asList(filter.words));
    return myWords.equals(otherWords);
  }

  private static final long serialVersionUID = 1;

}
