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
  public TregexParseException(String message, Throwable cause) {
    super(message, cause);
  }
}
