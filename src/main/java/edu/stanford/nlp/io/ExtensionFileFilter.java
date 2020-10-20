package edu.stanford.nlp.io;

import java.io.File;

/**
 * Implements a file filter that uses file extensions to filter files.
 *
 * @author cmanning 2000/01/24
 */
public class ExtensionFileFilter extends javax.swing.filechooser.FileFilter implements java.io.FileFilter {

  private String extension;    // = null
  private boolean recursively;

  /**
   * Sets up Extension file filter by specifying an extension
   * to accept (currently only 1) and whether to also display
   * folders for recursive search.
   * The passed extension may be null, in which case the filter
   * will pass all files (passing an empty String does not have the same
   * effect -- this would look for file names ending in a period).
   *
   * @param ext     File extension (need not include period) or passing null means accepting all files
   * @param recurse go into folders
   */
  public ExtensionFileFilter(String ext, boolean recurse) {
    if (ext != null) {
      if (ext.startsWith(".")) {
        extension = ext;
      } else {
        extension = '.' + ext;
      }
    }
    recursively = recurse;
  }

  /**
   * Sets up an extension file filter that will recurse into sub directories.
   * @param ext The extension to accept (with or without a leading period).
   */
  public ExtensionFileFilter(String ext) {
    this(ext, true);
  }

  /**
   * Checks whether a file satisfies the selection filter.
   *
   * @param file The file
   * @return true if the file is acceptable
   */
  @SuppressWarnings("SimplifiableIfStatement")
  @Override
  public boolean accept(File file) {
    if (file.isDirectory()) {
      return recursively;
    } else if (extension == null) {
      return true;
    } else {
      return file.getName().endsWith(extension);
    }
  }

  /**
   * Returns a description of what extension is being used (for file choosers).
   * For example, if the suffix is "xml", the description will be
   * "XML Files (*.xml)".
   *
   * @return description of this file filter
   */
  @Override
  public String getDescription() {
    String ucExt = extension.substring(1).toUpperCase();
    return ucExt + " Files (*" + extension + ')';
  }

}
