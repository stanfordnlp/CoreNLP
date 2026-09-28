package edu.stanford.nlp.trees.tregex;

/**
 * A runtime exception that indicates something went wrong parsing a
 * tregex expression.  The purpose is to make those exceptions
 * unchecked exceptions, as there are only a few circumstances in
 * which one could recover.
 * 
 * @author John Bauer
 */
public class TregexParseException extends RuntimeException {
  /**
   * Creates an exception with the given message and cause.
   *
   * @param message a description of the problem
   * @param cause the underlying exception
   */
  public TregexParseException(String message, Throwable cause) {
    super(message, cause);
  }
}
