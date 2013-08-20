package edu.stanford.nlp.trees.tregex.tsurgeon;

/**
 * A runtime exception that indicates something went wrong parsing a
 * tsurgeon expression.  The purpose is to make those exceptions
 * unchecked exceptions, as there are only a few circumstances in
 * which one could recover.
 * 
 * @author John Bauer
 */
public class TsurgeonParseException extends RuntimeException {
  public TsurgeonParseException(String message, Throwable cause) {
    super(message, cause);
  }
}
