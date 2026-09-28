package edu.stanford.nlp.semgraph.semgrex.ssurgeon;

/**
 * A runtime exception that indicates something went wrong parsing a
 * Ssurgeon expression.
 *
 * @author John Bauer
 */
public class SsurgeonParseException extends RuntimeException {

  private static final long serialVersionUID = -278683457698L;

  /**
   * Creates an exception with the given message.
   *
   * @param message Description of the problem
   */
  public SsurgeonParseException(String message) {
    super(message);
  }

  /**
   * Creates an exception with the given message and cause.
   *
   * @param message Description of the problem
   * @param cause The underlying exception
   */
  public SsurgeonParseException(String message, Throwable cause) {
    super(message, cause);
  }

}
