package edu.stanford.nlp.trees;

import java.io.Reader;

/**
 * A <code>TreeReaderFactory</code> is a factory for creating objects of
 * class <code>TreeReader</code>, or some descendant class.
 *
 * @author Christopher Manning
 */
public interface TreeReaderFactory {

  /**
   * Create a new <code>TreeReader</code> using the provided
   * <code>Reader</code>.
   *
   * @param in The <code>Reader</code> to build on
   * @return The new TreeReader
   */
  public TreeReader newTreeReader(Reader in);

}
