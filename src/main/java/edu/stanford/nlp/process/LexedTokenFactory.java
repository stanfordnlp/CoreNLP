package edu.stanford.nlp.process;

/**
 * Constructs a token (of arbitrary type) from a String and its position
 * in the underlying text.  This is used to create tokens in JFlex lexers
 * such as PTBTokenizer.
 */
public interface LexedTokenFactory<T> {

  /**
   * Constructs a token (of arbitrary type) from a String and its position
   * in the underlying text. The int arguments are used just to record token
   * character offsets in an underlying text. This method does not take
   * a substring of {@code str}.
   *
   * @param str The String extracted by the lexer.
   * @param begin The offset in the document of the first character in this string.
   * @param length The number of characters the string takes up in the document.
   * @return The token of type T.
   */
  T makeToken(String str, int begin, int length);

}
