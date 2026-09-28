package edu.stanford.nlp.util;


/**
 * An unchecked version of {@link java.lang.InterruptedException}. Thrown by
 * classes that pay attention to if they were interrupted, such as the LexicalizedParser.
 *
 * @author John Bauer
 */
public class RuntimeInterruptedException extends RuntimeException {
  /** Creates an exception with no cause. */
  public RuntimeInterruptedException() {
    super();
  }

  /**
   * Wraps an {@link InterruptedException}.
   *
   * @param e The interruption being wrapped
   */
  public RuntimeInterruptedException(InterruptedException e) {
    super(e);
  }
}