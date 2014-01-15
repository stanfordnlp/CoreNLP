package edu.stanford.nlp.trees.tregex.tsurgeon;

/**
 * Something has gone wrong internally in Tsurgeon
 *
 * @author John Bauer
 */
public class TsurgeonRuntimeException extends RuntimeException {

  private static final long serialVersionUID = 1;

  /**
   * Creates a new exception with a message.
   *
   * @param message the message for the exception
   */
  public TsurgeonRuntimeException(String message) {
    super(message);
  }

}
