package edu.stanford.nlp.util;

/**
 * This allows functions to receive and apply a test that can return
 * a boolean value for a string argument, used for string processing
 * routines.
 * 
 * (i.e. this is Java's verbose equivalent of a typed lambda)
 * @author Eric Yeh
 *
 */
public interface StringBoolTest {
  public boolean test(String input);
}
