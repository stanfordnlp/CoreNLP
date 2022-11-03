package edu.stanford.nlp.semgraph.semgrex.ssurgeon;

/**
 * A runtime exception that indicates something went wrong executing a
 * Ssurgeon expression.
 *
 * @author John Bauer
 */
public class SsurgeonRuntimeException extends RuntimeException {

  private static final long serialVersionUID = -278683457698L;

  public SsurgeonRuntimeException(String message) {
    super(message);
  }

  public SsurgeonRuntimeException(String message, Throwable cause) {
    super(message, cause);
  }

}
