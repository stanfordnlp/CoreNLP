package edu.stanford.nlp.trees.tregex.tsurgeon;

/**
 * A runtime exception that indicates something went wrong parsing a
 * Tsurgeon expression.  The purpose is to make those exceptions
 * unchecked exceptions, as there are only a few circumstances in
 * which one could recover.
 *
 * @author John Bauer
 */
public class TsurgeonParseException extends RuntimeException {

  private static final long serialVersionUID = -4417368416943652737L;

  public TsurgeonParseException(String message) {
    super(message);
  }

  public TsurgeonParseException(String message, Throwable cause) {
    super(message, cause);
  }

}
