package old.edu.stanford.nlp.ling;

/**
 * @author grenager
 */
public interface HasContext {

  /**
   * @return the String before the word
   */
  public String before();

  /**
   * Set the String before the word.
   * @param before the String before the word
   */
  public void setBefore(String before);

  /**
   * Prepend this String to the before String.
   *
   * @param before the String to be prepended
   */
  public void prependBefore(String before);

  /**
   * Return the String which is the unmangled word.
   *
   * @return the unmangled word
   */
  public String current();

  /**
   * Set the String which is the unmangled word.
   *
   * @param current the unmangled word
   */
  public void setCurrent(String current);

  /**
   * Return the String after the word.
   *
   * @return the String after the word
   */
  public String after();

  /**
   * Set the String after the word.
   *
   * @param after The String after the word
   */
  public void setAfter(String after);
  /**
   * Append this String to the current after String
   *
   * @param after The String to be prepended
   */
  public void appendAfter(String after);

}
