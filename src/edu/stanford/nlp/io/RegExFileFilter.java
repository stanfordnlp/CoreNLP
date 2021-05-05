package edu.stanford.nlp.io;

import java.io.*;
import java.util.regex.*;
/**
 * Implements a file filter that filters based on a passed in {@link java.util.regex.Pattern}.
 * Preciesly, it will accept exactly those {@link java.io.File}s for which
 * the matches() method of the Pattern returns true on the output of the getName()
 * method of the File.
 *
 * @author Jenny Finkel
 */
public class RegExFileFilter implements FileFilter {

  private final Pattern pattern;

  public RegExFileFilter(String string) {
    this(Pattern.compile(string));
  }

  /**
   * Sets up a RegExFileFilter which checks if the file name (not the
   * entire path) matches the passed in {@link java.util.regex.Pattern}.
   */
  public RegExFileFilter(Pattern pattern) {
    this.pattern = pattern;
  }

  /**
   * Checks whether a file satisfies the selection filter.
   *
   * @param file The file
   * @return true if the file is acceptable
   */
  public boolean accept(File file) {
    Matcher m = pattern.matcher(file.getName());
    return m.matches();
  }

}
