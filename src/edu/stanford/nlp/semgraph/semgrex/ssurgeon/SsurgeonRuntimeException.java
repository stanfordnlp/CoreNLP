package edu.stanford.nlp.semgraph.semgrex.ssurgeon;

/**
 * A runtime exception that indicates something went wrong executing a
 * Ssurgeon expression.
 *
 * @author John Bauer
 */
public class SsurgeonRuntimeException extends RuntimeException {

  private static final long serialVersionUID = -278683457698L;

  /**
   * Creates an exception with the given message.
   *
   * @param message The detail message
   */
  public SsurgeonRuntimeException(String message) {
    super(message);
  }

  /**
   * Creates an exception with the given message and cause.
   *
   * @param message The detail message
   * @param cause The underlying cause
   */
  public SsurgeonRuntimeException(String message, Throwable cause) {
    super(message, cause);
  }

}
