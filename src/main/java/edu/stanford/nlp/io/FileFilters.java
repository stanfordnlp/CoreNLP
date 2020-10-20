package edu.stanford.nlp.io;

import java.io.FileFilter;
import java.io.File;
import java.util.regex.Pattern;


/** Provides some static methods for combination file filters.
 *  @author Christopher Manning
 */
public class FileFilters {

  private FileFilters() {}

  public static FileFilter conjunctionFileFilter(FileFilter a, FileFilter b) {
    return new ConjunctionFileFilter(a, b);
  }

  public static FileFilter negationFileFilter(FileFilter a) {
    return new NegationFileFilter(a);
  }

  public static FileFilter findRegexFileFilter(String regex) {
    return new FindRegexFileFilter(regex);
  }


  /**
   * Implements a conjunction file filter.
   */
  private static class ConjunctionFileFilter implements java.io.FileFilter {

    private final FileFilter f1;
    private final FileFilter f2;

    /**
     * Sets up file filter.
     *
     * @param a One file filter
     * @param b The other file filter
     */
    public ConjunctionFileFilter(FileFilter a, FileFilter b) {
      f1 = a;
      f2 = b;
    }

    /**
     * Checks whether a file satisfies the selection filter.
     *
     * @param file The file
     * @return true if the file is acceptable
     */
    public boolean accept(File file) {
      return f1.accept(file) && f2.accept(file);
    }

  }


  /**
   * Implements a negation file filter.
   */
  private static class NegationFileFilter implements java.io.FileFilter {

    private final FileFilter f1;

    /**
     * Sets up file filter.
     *
     * @param a A file filter
     */
    public NegationFileFilter(FileFilter a) {
      f1 = a;
    }

    /**
     * Checks whether a file satisfies the selection filter.
     *
     * @param file The file
     * @return true if the file is acceptable
     */
    public boolean accept(File file) {
      return ! f1.accept(file);
    }

  }

  /**
   * Implements a conjunction file filter.
   */
  private static class FindRegexFileFilter implements java.io.FileFilter {

    private final Pattern p;
    /**
     * Sets up file filter.
     *
     * @param regex The pattern to match (as find()
     */
    public FindRegexFileFilter(String regex) {
      p = Pattern.compile(regex);
    }

    /**
     * Checks whether a file satisfies the selection filter.
     *
     * @param file The file
     * @return true if the file is acceptable
     */
    public boolean accept(File file) {
      return p.matcher(file.getName()).find();
    }

  }


}
