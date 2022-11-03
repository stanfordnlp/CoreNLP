package edu.stanford.nlp.semgraph.semgrex.ssurgeon;

/**
 * A runtime exception that indicates something went wrong parsing a
 * Ssurgeon expression.
 *
 * @author John Bauer
 */
public class SsurgeonParseException extends RuntimeException {

  private static final long serialVersionUID = -278683457698L;

  public SsurgeonParseException(String message) {
    super(message);
  }

  public SsurgeonParseException(String message, Throwable cause) {
    super(message, cause);
  }

}
