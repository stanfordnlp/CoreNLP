package edu.stanford.nlp.parser.lexparser;

import java.util.NoSuchElementException;

public class NoSuchParseException extends NoSuchElementException {
  private static final long serialVersionUID = 2;  

  public NoSuchParseException() {
    super();
  }

  public NoSuchParseException(String error) {
    super(error);
  }
}
