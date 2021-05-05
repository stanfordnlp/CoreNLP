package edu.stanford.nlp.io;

import java.io.File;
import java.io.FileFilter;

/**
 * Implements a file filter that examines a number in a filename to
 * determine acceptance.  This is useful for wanting to process ranges
 * of numbered files in collections where each file has some name, part
 * of which is alphabetic and constant, and part of which is numeric.
 * The test is evaluated based on the rightmost natural number found in
 * the filename string.  (It only looks in the final filename, not in other
 * components of the path.)  Number ranges are inclusive.
 *
 * @author Christopher Manning
 * @version 2000/12/29
 */
public class NumberRangeFileFilter implements FileFilter {

  private final int minimum;
  private final int maximum;
  private final boolean recursively;

  /**
   * Sets up a NumberRangeFileFilter by specifying the range of numbers
   * to accept, and whether to also traverse
   * folders for recursive search.
   *
   * @param min     The minimum number file to accept (checks &ge; this one)
   * @param max     The maximum number file to accept (checks &le; this one)
   * @param recurse go into folders
   */
  public NumberRangeFileFilter(int min, int max, boolean recurse) {
    minimum = min;
    maximum = max;
    recursively = recurse;
  }

  /**
   * Checks whether a file satisfies the number range selection filter.
   *
   * @param file The file
   * @return true if the file is within the range filtered for
   */
  public boolean accept(File file) {
    if (file.isDirectory()) {
      return recursively;
    } else {
      String filename = file.getName();
      int k = filename.length() - 1;
      char c = filename.charAt(k);
      while (k >= 0 && (c < '0' || c > '9')) {
        k--;
        if (k >= 0) {
          c = filename.charAt(k);
        }
      }
      if (k < 0) {
        return false;
      }
      int j = k;
      c = filename.charAt(j);
      while (j >= 0 && (c >= '0' && c <= '9')) {
        j--;
        if (j >= 0) {
          c = filename.charAt(j);
        }
      }
      j++;
      k++;
      String theNumber = filename.substring(j, k);
      int number = Integer.parseInt(theNumber);
      return (number >= minimum) && (number <= maximum);
    }
  }

}
