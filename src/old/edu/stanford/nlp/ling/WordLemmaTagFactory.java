package old.edu.stanford.nlp.ling;

import old.edu.stanford.nlp.process.Morphology;

/**
 * /**
 * A <code>WordLemmaTagFactory</code> acts as a factory for creating
 * objects of class <code>WordLemmaTag</code>.
 *
 * @author Marie-Catherine de Marneffe
 */
public class WordLemmaTagFactory implements LabelFactory {

  public final static int LEMMA_LABEL = 1;
  public final static int TAG_LABEL = 2;

  private final char divider;


  /**
   * Create a new <code>WordLemmaTagFactory</code>.
   * The divider will be taken as '/'.
   */
  public WordLemmaTagFactory() {
    this('/');
  }


  /**
   * Create a new <code>WordLemmaTagFactory</code>.
   *
   * @param divider This character will be used in calls to the one
   *                argument version of <code>newLabel()</code>, to divide
   *                word from lemma and tag.
   */
  public WordLemmaTagFactory(char divider) {
    this.divider = divider;
  }


  /**
   * Make a new label with this <code>String</code> as the value (word).
   * Any other fields of the label would normally be null.
   *
   * @param labelStr The String that will be used for value
   * @return The new WordLemmaTag (lemma and tag will be <code>null</code>)
   */
  public Label newLabel(String labelStr) {
    return new WordLemmaTag(labelStr);
  }


  /**
   * Make a new label with this <code>String</code> as a value component.
   * Any other fields of the label would normally be null.
   *
   * @param labelStr The String that will be used for value
   * @param options  what to make (use labelStr as word, lemma or tag)
   * @return The new WordLemmaTag (word or lemma or tag will be <code>null</code>)
   */
  public Label newLabel(String labelStr, int options) {
    if (options == TAG_LABEL) {
      return new WordLemmaTag(null, null, labelStr);
    } else if (options == LEMMA_LABEL) {
      return new WordLemmaTag(null, labelStr, null);
    } else {
      return new WordLemmaTag(labelStr);
    }

  }


  /**
   * Create a new word, where the label is formed from
   * the <code>String</code> passed in.  The String is divided according
   * to the divider character.  We assume that we can always just
   * divide on the rightmost divider character, rather than trying to
   * parse up escape sequences.  If the divider character isn't found
   * in the word, then the whole string becomes the word, and lemma and tag
   * are <code>null</code>.
   * We assume that if only one divider character is found, word and tag are presents in
   * the String, and lemma will be computed.
   *
   * @param labelStr The word that will go into the <code>Word</code>
   * @return The new WordLemmaTag
   */
  public Label newLabelFromString(String labelStr) {
    int first = labelStr.indexOf(divider);
    int second = labelStr.lastIndexOf(divider);
    if (first == second) {
      return new WordLemmaTag(labelStr.substring(0, first), Morphology.stemStatic(labelStr.substring(0, first), labelStr.substring(first + 1)).word(), labelStr.substring(first + 1));
    } else if (first >= 0) {
      return new WordLemmaTag(labelStr.substring(0, first), labelStr.substring(first + 1, second), labelStr.substring(second + 1));
    } else {
      return new WordLemmaTag(labelStr);
    }
  }


  /**
   * Create a new <code>WordLemmaTag Label</code>, where the label is
   * formed from the <code>Label</code> object passed in.  Depending on what fields
   * each label has, other things will be <code>null</code>.
   *
   * @param oldLabel The Label that the new label is being created from
   * @return a new label of a particular type
   */
  public Label newLabel(Label oldLabel) {
    return new WordLemmaTag(oldLabel);
  }

}
