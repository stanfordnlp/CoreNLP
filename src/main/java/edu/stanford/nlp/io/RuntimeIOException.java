package edu.stanford.nlp.io;


/**
 * An unchecked version of {@link java.io.IOException}. Thrown by
 * {@link edu.stanford.nlp.process.Tokenizer} implementing classes,
 * among other things.
 *
 * @author Roger Levy
 * @author Christopher Manning
 */
public class RuntimeIOException extends RuntimeException {

  private static final long serialVersionUID = -8572218999165094626L;

  /**
   * Creates a new exception.
   */
  public RuntimeIOException() {
  }


  /**
   * Creates a new exception with a message.
   *
   * @param message the message for the exception
   */
  public RuntimeIOException(String message) {
    super(message);
  }

  /**
   * Creates a new exception with an embedded cause.
   *
   * @param cause The cause for the exception
   */
  public RuntimeIOException(Throwable cause) {
    super(cause);
  }

  /**
   * Creates a new exception with a message and an embedded cause.
   *
   * @param message the message for the exception
   * @param cause   The cause for the exception
   */
  public RuntimeIOException(String message, Throwable cause) {
    super(message, cause);
  }

}
