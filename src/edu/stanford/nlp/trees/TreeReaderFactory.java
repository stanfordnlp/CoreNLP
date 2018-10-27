package edu.stanford.nlp.trees;

import java.io.Reader;

/**
 * A {@code TreeReaderFactory} is a factory for creating objects of
 * class {@code TreeReader}, or some descendant class.
 *
 * @author Christopher Manning
 */
@FunctionalInterface
public interface TreeReaderFactory {

  /**
   * Create a new {@code TreeReader} using the provided
   * {@code Reader}.
   *
   * @param in The {@code Reader} to build on
   * @return The new TreeReader
   */
  TreeReader newTreeReader(Reader in);

}
