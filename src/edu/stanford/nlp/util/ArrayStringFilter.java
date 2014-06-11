package edu.stanford.nlp.util;


/**
 * Filters Strings based on whether they exactly match any string in
 * the array it is initially onstructed with.  Saves some time over
 * using regexes if the array of strings is small enough.  No specific
 * experiments exist for how long the array can be before performance
 * is worse than a regex, but the English dependencies code was helped
 * by replacing disjunction regexes of 6 words or fewer with this.
 *
 * @author John Bauer
 */
public class ArrayStringFilter implements Filter<String> {
  private final String[] words;
  private final int length;

  public ArrayStringFilter(String ... words) {
    this.words = new String[words.length];
    for (int i = 0; i < words.length; ++i) {
      this.words[i] = words[i];
    }
    this.length = words.length;
  }

  public boolean accept(String input) {
    for (int i = 0; i < length; ++i) {
      if (words[i].equals(input)) {
        return true;
      }
    }
    return false;
  }

  private static final long serialVersionUID = 1;
}