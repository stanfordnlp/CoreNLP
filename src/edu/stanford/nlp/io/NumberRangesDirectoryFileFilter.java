package edu.stanford.nlp.io;

import edu.stanford.nlp.util.Pair;

import java.io.FileFilter;
import java.io.File;
import java.util.List;
import java.util.ArrayList;
import java.util.Iterator;

/**
 * See NumberRangesFileFilter, this does the same except it filters directories not files.
 *
 * @author Christopher Manning (author of NumberRangesFileFilter from which all this code was taken)
 * @author Alex Kleeman
 * @version 2007/05/12
 */
public class NumberRangesDirectoryFileFilter implements FileFilter {

  private List<Pair<Integer,Integer>> ranges = new ArrayList<Pair<Integer,Integer>>();
  private boolean recursively;
  private FileFilter fileFilter;


  /**
   * Sets up a NumberRangesDirectoryFileFilter by specifying the ranges of numbers
   * to accept, and whether to also traverse
   * folders for recursive search.
   *
   * @param ranges  The ranges of numbers to accept (see class documentation)
   * @param recurse Whether to go into subfolders
   * @throws IllegalArgumentException If the String ranges does not
   *                                  contain a suitable ranges format
   */
  public NumberRangesDirectoryFileFilter(String ranges, boolean recurse) {
    this(ranges, null, recurse);
  }
  
  public NumberRangesDirectoryFileFilter(String ranges, FileFilter fileFilter, boolean recurse) {
    this.fileFilter = fileFilter;
    recursively = recurse;
    try {
      String[] ra = ranges.split(",");
      for (int i = 0; i < ra.length; i++) {
        String[] one = ra[i].split("-");
        if (one.length > 2) {
          throw new IllegalArgumentException("Too many hyphens");
        } else {
          int low = Integer.parseInt(one[0].trim());
          int high;
          if (one.length == 2) {
            high = Integer.parseInt(one[1].trim());
          } else {
            high = low;
          }
          Pair<Integer,Integer> p = new Pair<Integer,Integer>(Integer.valueOf(low), Integer.valueOf(high));
          this.ranges.add(p);
        }
      }
    } catch (Exception e) {
      IllegalArgumentException iae = new IllegalArgumentException("Constructor argument not valid: " + ranges);
      iae.initCause(e);
      throw iae;
    }
  }


  /**
   * Checks whether a directory satisfies the number range selection filter.
   *
   * @param file The Directory
   * @return true If the directory is within the ranges filtered for, false if file is not a directory
   */
  public boolean accept(File file) {
    if (file.isDirectory()) {
            String filename = file.getName();
      int k = filename.length() - 1;
      char c = filename.charAt(k);
      while (k >= 0 && !Character.isDigit(c)) {
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
      while (j >= 0 && Character.isDigit(c)) {
        j--;
        if (j >= 0) {
          c = filename.charAt(j);
        }
      }
      j++;
      k++;
      String theNumber = filename.substring(j, k);
      int number = Integer.parseInt(theNumber);
      for (Pair<Integer,Integer> p : ranges) {
        int low = p.first().intValue();
        int high = p.second().intValue();
        if (number >= low && number <= high) {
          return true;
        }
      }
      return false;
    } else {
      if (fileFilter != null) {
        return fileFilter.accept(file);
      }
      return false;
    }
  }

  @Override
  public String toString() {
    StringBuilder sb;
    if (recursively) {
      sb = new StringBuilder("recursively ");
    } else {
      sb = new StringBuilder();
    }
    for (Iterator<Pair<Integer,Integer>> it = ranges.iterator(); it.hasNext(); ) {
      Pair<Integer,Integer> p = it.next();
      int low = p.first().intValue();
      int high = p.second().intValue();
      if (low == high) {
        sb.append(low);
      } else {
        sb.append(low);
        sb.append("-");
        sb.append(high);
      }
      if (it.hasNext()) {
        sb.append(",");
      }
    }
    return sb.toString();
  }

}
