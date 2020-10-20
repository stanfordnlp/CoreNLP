package edu.stanford.nlp.io;

import java.io.IOException;
import java.io.Reader;


/**
 * A Lexer interface to be used with {@link edu.stanford.nlp.process.LexerTokenizer}.  You can put a {@link Reader} inside
 * a Lexer with the {@link #yyreset} method.  An easy way to build classes implementing this
 * interface is with JFlex (http://jflex.de).  Just make sure to include the following in the
 * JFlex source file
 *
 * In the <i>Options and Macros</i> section of the source file, include
 *
 * %class JFlexDummyLexer<br>
 * %standalone<br>
 * %unicode<br>
 * %int<br>
 * <br>
 * %implements edu.stanford.nlp.io.Lexer<br>
 * <br>
 * %{<br>
 * public void pushBack(int n) {<br>
 * yypushback(n);<br>
 * }<br>
 * <br>
 * public int getYYEOF() {<br>
 * return YYEOF;<br>
 * }<br>
 * %}<br>
 *
 * Alternatively, you can customize your own lexer and get lots of
 * flexibility out.
 *
 * @author Roger Levy
 */

public interface Lexer {

  int ACCEPT = 1;
  int IGNORE = 0;

  /**
   * Gets the next token from input and returns an integer value
   * signalling what to do with the token.
   */
  int yylex() throws IOException;

  /**
   * returns the matched input text region
   */
  String yytext();

  /**
   * Pushes back {@code length} character positions in the
   * lexer.  Conventionally used to push back exactly one token.
   */
  void pushBack(int length);

  /**
   * returns value for YYEOF
   */
  int getYYEOF();

  /**
   * put a {@link Reader} inside the Lexer.
   */
  void yyreset(Reader r) throws IOException;

}
