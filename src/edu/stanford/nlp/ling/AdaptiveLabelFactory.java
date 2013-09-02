package edu.stanford.nlp.ling;

/**
 * An <code>AdaptiveLabelFactory</code> object makes simple
 * <code>Label</code>s for objects, by creating a label of an
 * appropriate type depending on the arguments passed in. <p>
 * People haven't used this much.  It may or may not be a good class to
 * have.
 *
 * @author Christopher Manning
 */
public class AdaptiveLabelFactory implements LabelFactory {

  public final static int WORD_LABEL = 1;

  public final static int CATEGORY_LABEL = 4;

  /**
   * The AdaptiveLabelFactory does not support this operation, because
   * it does not know what sort of label to make.
   *
   * @param labelStr A string that determines the content of the label.
   * @return The created label
   * @throws UnsupportedOperationException
   */
  public Label newLabel(String labelStr) {
    return new StringLabel(labelStr);
  }


  /**
   * Create a new <code>Label</code>, where the <code>Label</code>
   * is formed from the passed in String, using it to name the component
   * given by <code>options</code>.
   *
   * @param labelStr A String to use as the label
   * @param options  What kind of label it is
   * @return The created label
   */
  public Label newLabel(String labelStr, int options) {
    if (options == WORD_LABEL) {
      return new Word(labelStr);
    } else if (options == TaggedWordFactory.TAG_LABEL) {
      return new Tag(labelStr);
    } else if (options == CATEGORY_LABEL) {
      return new Category(labelStr);
    } else {
      throw new UnsupportedOperationException();
    }
  }


  /**
   * The AdaptiveLabelFactory does not support this operation, because
   * it does not know what sort of label to make.
   *
   * @param labelStr A string that determines the content of the label.
   * @return The created label
   * @throws UnsupportedOperationException
   */
  public Label newLabelFromString(String labelStr) {
    throw new UnsupportedOperationException();
  }


  /**
   * Create a new <code>Label</code>, where the label is
   * formed from
   * the <code>Label</code> object passed in.  Depending on the first
   * non-<code>null</code> component, looking in
   * the order category, first, then word, and then tag, an object of
   * the appropriate sort is created.
   *
   * @param oldLabel The Label that the new label is being created from
   * @return a new label of a particular type
   */
  public Label newLabel(Label oldLabel) {
    if (oldLabel instanceof HasWord) {
      return newLabel(((HasWord) oldLabel).word(), WORD_LABEL);
    } else if (oldLabel instanceof HasCategory) {
      return newLabel(((HasCategory) oldLabel).category(), CATEGORY_LABEL);
    } else if (oldLabel instanceof HasTag) {
      return newLabel(((HasTag) oldLabel).tag(), TaggedWordFactory.TAG_LABEL);
    } else {
      throw new UnsupportedOperationException();
    }
  }

}
