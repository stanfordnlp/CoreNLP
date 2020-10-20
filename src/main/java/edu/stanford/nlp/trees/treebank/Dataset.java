package edu.stanford.nlp.trees.treebank;

import java.util.List;
import java.util.Properties;

/**
 * A generic interface loading, processing, and writing a data set. Classes
 * that implement this interface may be specified in the configuration file
 * using the <code>TYPE</code> parameter. {@link TreebankPreprocessor} will
 * then call {@link #setOptions}, {@link #build} and {@link #getFilenames()}
 * in that order.
 *
 * @author Spence Green
 *
 */
public interface Dataset {

  public enum Encoding {Buckwalter, UTF8}

  /**
   * Sets options for a dataset.
   *
   * @param opts A map from parameter types defined in {@link ConfigParser} to
   * values
   * @return true if opts contains all required options. false, otherwise.
   */
  public boolean setOptions(Properties opts);

  /**
   * Generic method for loading, processing, and writing a dataset.
   */
  public void build();

  /**
   * Returns the filenames written by {@link #build()}.
   *
   * @return A collection of filenames
   */
  public List<String> getFilenames();
}
