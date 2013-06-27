package edu.stanford.nlp.trees;

/**
 * Allows us to verify that a wordnet connection is available without compile
 * time errors if the package is not found.
 *
 * @author Chris Cox
 * @author Eric Yeh
 */
public interface WordNetConnection {

  // Used String arg version, instead of StringBuffer - EY 02/02/07
  public boolean wordNetContains(String s);
}
