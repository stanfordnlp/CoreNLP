package edu.stanford.nlp.util;

import java.io.File;

/**
 * Interface for a Visitor pattern for Files.
 * This interface is used by some existing code, but new code should
 * probably use FileArrayList or FileSequentialCollection, which fit
 * better with the Collections orientation of recent Java releases.
 *
 * @author Christopher Manning
 */
public interface FileProcessor {

  /**
   * Apply this predicate to a <code>File</code>.  This method can
   * assume the <code>file</code> is a file and not a directory.
   *
   * @see FilePathProcessor for traversing directories
   */
  public void processFile(File file);

}
