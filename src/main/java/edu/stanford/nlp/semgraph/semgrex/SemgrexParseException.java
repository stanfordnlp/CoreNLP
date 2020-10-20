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
  public SemgrexParseException(String message) {
    super(message);
  }

  public SemgrexParseException(String message, Throwable cause) {
    super(message, cause);
  }
}
