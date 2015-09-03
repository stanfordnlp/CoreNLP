package edu.stanford.nlp.ling;

import edu.stanford.nlp.util.TypesafeMap;

public interface AbstractCoreLabel extends Label, HasWord, HasIndex, HasTag, HasLemma, HasOffset, TypesafeMap {

  /**
   * Return the named entity class of the label (or null if none).
   *
   * @return The NER class for the label
   */
  public String ner();

  /**
   * Set the named entity class of the label.
   *
   * @param ner The NER class for the label
   */
  public void setNER(String ner);

  // These next two are a partial implementation of HasContext. Maybe clean this up someday?

  public String originalText();

  public void setOriginalText(String originalText);

  /**
   * Return a non-null String value for a key. This method is included
   * for backwards compatibility with the removed class AbstractMapLabel.
   * It is guaranteed to not return null; if the key is not present or
   * has a null value, it returns the empty string ("").  It is only valid to
   * call this method when key is paired with a value of type String.
   *
   * @param <KEY> A key type with a String value
   * @param key The key to return the value of.
   * @return "" if the key is not in the map or has the value {@code null}
   *     and the String value of the key otherwise
   */
  public <KEY extends Key<String>> String getString(Class<KEY> key);

}
