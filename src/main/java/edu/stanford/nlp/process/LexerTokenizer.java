package edu.stanford.nlp.process;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;

import edu.stanford.nlp.io.Lexer;
import edu.stanford.nlp.io.RuntimeIOException;


/**
 * An implementation of {@link Tokenizer} designed to work with
 * {@link Lexer} implementing classes.  Throw in a {@link Lexer} on
 * construction and you get a {@link Tokenizer}.
 *
 * @author Roger Levy
 */
public class LexerTokenizer extends AbstractTokenizer<String> {

  private final Lexer lexer;

  /**
   * Internally fetches the next token.
   *
   * @return the next token in the token stream, or null if none exists.
   */
  @Override
  protected String getNext() {
    String token = null;
    try {
      int a = Lexer.IGNORE;
      while (a == Lexer.IGNORE) {
        a = lexer.yylex(); // skip tokens to be ignored
      }
      if (a != lexer.getYYEOF()) {
        token = lexer.yytext();
      }
      // else token remains null
    } catch (IOException e) {
      // do nothing, return null
    }
    return token;
  }

  /** Constructs a tokenizer from a {@link Lexer}.
   */
  public LexerTokenizer(Lexer l) {
    if (l == null) {
      throw new IllegalArgumentException("You can't make a Tokenizer out of a null Lexer!");
    } else {
      this.lexer = l;
    }
  }

  /** Constructs a tokenizer from a {@link Lexer} and makes a {@link Reader}
   *  the active input stream for the tokenizer.
   */
  public LexerTokenizer(Lexer l, Reader r) {
    this(l);

    try {
      l.yyreset(r);
    } catch (IOException e) {
      throw new RuntimeIOException(e.getMessage());
    }

    getNext();
  }


  /**
   * For testing only.
   */
  public static void main(String[] args) throws IOException {
    Tokenizer<String> t = new LexerTokenizer(new JFlexDummyLexer((Reader) null), new BufferedReader(new FileReader(args[0])));
    while (t.hasNext()) {
      System.out.println("token " + t.next());
    }
  }

}
