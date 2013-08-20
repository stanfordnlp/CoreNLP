package edu.stanford.nlp.ie;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import edu.stanford.nlp.io.ExtensionFileFilter;

/**
 * Utility class for deserializing FieldExtractors from files.
 * Updated on 02/12/03 for post-KAON cleanup.
 *
 * @author Joseph Smarr (jsmarr@stanford.edu)
 */
public class ExtractorUtilities {

  /**
   * Private constructor to prevent direct instantiation.
   */
  private ExtractorUtilities() {
  }


  /**
   * Reads in a previously serialized FieldExtractor from the given input file
   * and returns it. Returns null if reading the file fails. This method
   * assumes that the FieldExtractor was simply serialized as an object in
   * the standard way.
   *
   * @see AbstractFieldExtractor#loadExtractor(InputStream)
   */
  public static FieldExtractor loadExtractor(InputStream in) {
    try {
      return (AbstractFieldExtractor.loadExtractor(in));
    } catch (Exception e) {
      return (null);
    }
  }

  /**
   * Convinience method that loads a serialized extractor from the given file.
   * Returns null if anything goes wrong.
   *
   * @see #loadExtractor(InputStream)
   */
  public static FieldExtractor loadExtractor(File objFile) {
    try {
      return (loadExtractor(new FileInputStream(objFile)));
    } catch (FileNotFoundException e) {
      return (null);
    }
  }


  /**
   * Looks for all .obj files in the given directory and tries to load
   * them as FieldExtractors.
   * Returns all the successfully loaded FieldExtractors in the given
   * array.  The array may have 0 elements if no FieldExtractors are
   * successfully loaded, but will not be null.  This method
   * does not currently load .obj files recursively from sub directories,
   * so everything has to be just sitting in the given dir (this could be
   * changed if we want to start maintaining a large collection of
   * serialized FieldExtractors.
   */
  public static FieldExtractor[] loadAllExtractors(File dir) {
    File[] files = dir.listFiles(new ExtensionFileFilter("obj", false));
    if (files == null) {
      return (new FieldExtractor[0]);
    }

    List<FieldExtractor> extractors = new ArrayList<FieldExtractor>();
    for (int i = 0; i < files.length; i++) {
      FieldExtractor extractor = loadExtractor(files[i]);
      if (extractor != null) {
        extractors.add(extractor);
      }
    }
    // System.err.println("Extractors: " + extractors);

    return extractors.toArray(new FieldExtractor[0]);
  }

}
