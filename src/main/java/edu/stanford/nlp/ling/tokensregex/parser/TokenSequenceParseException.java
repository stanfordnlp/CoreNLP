package edu.stanford.nlp.ling.tokensregex.parser;

/**
 * Created by sonalg on 2/5/15.
 */
public class TokenSequenceParseException extends Exception {

  public TokenSequenceParseException(){super();}

  public TokenSequenceParseException(String msg){super(msg);}

  public TokenSequenceParseException(String message, Throwable throwable) {
    super(message, throwable);
  }
}
