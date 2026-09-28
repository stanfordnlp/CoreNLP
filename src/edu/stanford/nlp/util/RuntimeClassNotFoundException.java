package edu.stanford.nlp.util;


/**
 * An unchecked version of {@link java.lang.ClassNotFoundException}.
 *
 * @author John Bauer
 */
public class RuntimeClassNotFoundException extends RuntimeException {
  /**
   * Wraps the given exception.
   *
   * @param e the ClassNotFoundException to wrap, used as the cause
   */
  public RuntimeClassNotFoundException(ClassNotFoundException e) {
    super(e);
  }
}
