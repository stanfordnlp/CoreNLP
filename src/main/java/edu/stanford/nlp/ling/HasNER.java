package edu.stanford.nlp.ling;

/**
 * This token is able to produce NER tags
 *
 * @author Gabor Angeli
 */
public interface HasNER {

  /**
   * Return the named entity class of the label (or null if none).
   *
   * @return The NER class for the label
   */
  String ner();

  /**
   * Set the named entity class of the label.
   *
   * @param ner The NER class for the label
   */
  void setNER(String ner);

}
