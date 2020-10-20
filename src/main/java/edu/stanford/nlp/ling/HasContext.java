package edu.stanford.nlp.ling;

/**
 * @author grenager
 */
public interface HasContext {

  /**
   * @return the String before the word
   */
  public String before();

  /**
   * Set the whitespace String before the word.
   * @param before the whitespace String before the word
   */
  public void setBefore(String before);

  /**
   * Return the String which is the original character sequence of the token.
   *
   * @return The original character sequence of the token
   */
  public String originalText();

  /**
   * Set the String which is the original character sequence of the token.
   *
   * @param originalText The original character sequence of the token
   */
  public void setOriginalText(String originalText);

  /**
   * Return the whitespace String after the word.
   *
   * @return The whitespace String after the word
   */
  public String after();

  /**
   * Set the whitespace String after the word.
   *
   * @param after The whitespace String after the word
   */
  public void setAfter(String after);

}
