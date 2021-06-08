package edu.stanford.nlp.util;


/**
 * An unchecked version of {@link java.lang.ClassNotFoundException}.
 *
 * @author John Bauer
 */
public class RuntimeClassNotFoundException extends RuntimeException {
  public RuntimeClassNotFoundException(ClassNotFoundException e) {
    super(e);
  }
}
