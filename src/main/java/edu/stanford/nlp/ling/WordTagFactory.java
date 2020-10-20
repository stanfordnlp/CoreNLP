package edu.stanford.nlp.ling;

/**
 * A <code>WordTagFactory</code> acts as a factory for creating
 * objects of class <code>WordTag</code>.  Note that
 * <code>WordTag</code> is a replacement for (now deprecated)
 * <code>TaggedWord</code>, which has a less stringent equality
 * condition.
 *
 * @author Christopher Manning, Roger Levy
 * @version 2003/04
 */
public class WordTagFactory implements LabelFactory {

  private final char divider;


  /**
   * Create a new <code>WordTagFactory</code>.
   * The divider will be taken as '/'.
   */
  public WordTagFactory() {
    this('/');
  }


  /**
   * Create a new <code>WordTagFactory</code>.
   *
   * @param divider This character will be used in calls to the one
   *                argument version of <code>newLabel()</code>, to divide
   *                the word from the tag.  Stuff after the last instance of this
   *                character will become the tag, and stuff before it will
   *                become the label.
   */
  public WordTagFactory(char divider) {
    this.divider = divider;
  }


  /**
   * Make a new label with this <code>String</code> as the value (word).
   * Any other fields of the label would normally be null.
   *
   * @param labelStr The String that will be used for value
   * @return The new WordTag (tag will be <code>null</code>)
   */
  public Label newLabel(String labelStr) {
    return new WordTag(labelStr);
  }


  /**
   * Make a new label with this <code>String</code> as a value component.
   * Any other fields of the label would normally be null.
   *
   * @param labelStr The String that will be used for value
   * @param options  what to make (use labelStr as word or tag)
   * @return The new WordTag (tag or word will be <code>null</code>)
   */
  public Label newLabel(String labelStr, int options) {
    if (options == TaggedWordFactory.TAG_LABEL) {
      return new WordTag(null, labelStr);
    } else {
      return new WordTag(labelStr);
    }
  }


  /**
   * Create a new word, where the label is formed from
   * the <code>String</code> passed in.  The String is divided according
   * to the divider character.  We assume that we can always just
   * divide on the rightmost divider character, rather than trying to
   * parse up escape sequences.  If the divider character isn't found
   * in the word, then the whole string becomes the word, and the tag
   * is <code>null</code>.
   *
   * @param word The word that will go into the <code>Word</code>
   * @return The new WordTag
   */
  public Label newLabelFromString(String word) {
    int where = word.lastIndexOf(divider);
    if (where >= 0) {
      return new WordTag(word.substring(0, where), word.substring(where + 1));
    } else {
      return new WordTag(word);
    }
  }


  /**
   * Create a new <code>WordTag Label</code>, where the label is
   * formed from
   * the <code>Label</code> object passed in.  Depending on what fields
   * each label has, other things will be <code>null</code>.
   *
   * @param oldLabel The Label that the new label is being created from
   * @return a new label of a particular type
   */
  public Label newLabel(Label oldLabel) {
    if (oldLabel instanceof HasTag) {
      return new WordTag(oldLabel.value(), ((HasTag) oldLabel).tag());
    } else {
      return new WordTag(oldLabel.value());
    }
  }
}
