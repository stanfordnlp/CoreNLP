package edu.stanford.nlp.ling;

/**
 * A <code>WordFactory</code> acts as a factory for creating objects of
 * class <code>Word</code>.
 *
 * @author Christopher Manning
 * @version 2000/12/20
 */
public class WordFactory implements LabelFactory {

  /**
   * Creates a new WordFactory.
   */
  public WordFactory() {
  }


  /**
   * Create a new word, where the label is formed from
   * the <code>String</code> passed in.
   *
   * @param word The word that will go into the <code>Word</code>
   * @return The new label
   */
  public Label newLabel(String word) {
    return new Word(word);
  }


  /**
   * Create a new word, where the label is formed from
   * the <code>String</code> passed in.
   *
   * @param word    The word that will go into the <code>Word</code>
   * @param options is ignored by a WordFactory
   * @return The new label
   */
  public Label newLabel(String word, int options) {
    return new Word(word);
  }


  /**
   * Create a new word, where the label is formed from
   * the <code>String</code> passed in.
   *
   * @param word The word that will go into the <code>Word</code>
   * @return The new label
   */
  public Label newLabelFromString(String word) {
    return new Word(word);
  }


  /**
   * Create a new <code>Word Label</code>, where the label is
   * formed from
   * the <code>Label</code> object passed in.  Depending on what fields
   * each label has, other things will be <code>null</code>.
   *
   * @param oldLabel The Label that the new label is being created from
   * @return a new label of a particular type
   */
  public Label newLabel(Label oldLabel) {
    return new Word(oldLabel);
  }

}
