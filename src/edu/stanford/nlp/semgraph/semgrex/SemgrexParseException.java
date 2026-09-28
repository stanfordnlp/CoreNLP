package edu.stanford.nlp.semgraph.semgrex;

/**
 * A runtime exception that indicates something went wrong parsing a
 * semgrex expression.  The purpose is to make those exceptions
 * unchecked exceptions, as there are only a few circumstances in
 * which one could recover.
 * 
 * @author John Bauer
 */
public class SemgrexParseException extends RuntimeException {
  /**
   * Creates an exception with the given message.
   *
   * @param message a description of the problem
   */
  public SemgrexParseException(String message) {
    super(message);
  }

  /**
   * Creates an exception with the given message and cause.
   *
   * @param message a description of the problem
   * @param cause the underlying exception
   */
  public SemgrexParseException(String message, Throwable cause) {
    super(message, cause);
  }
}
