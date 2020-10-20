package edu.stanford.nlp.ling;


/**
 * A <code>CategoryWordTag</code> object acts as a complex Label
 * which contains a category, a head word, and a tag.
 * The category label is the primary <code>value</code>
 *
 * @author Christopher Manning
 */
public class CategoryWordTag extends StringLabel implements HasCategory, HasWord, HasTag {

  private static final long serialVersionUID = -745085381666943254L;

  protected String word;
  protected String tag;

  /**
   * If this is false, the tag and word are never printed in toString()
   * calls.
   */
  public static boolean printWordTag = true;

  /**
   * If set to true, when a terminal or preterminal has as its category
   * something that is also the word or tag value, the latter are
   * suppressed.
   */
  public static boolean suppressTerminalDetails; // = false;


  public CategoryWordTag() {
    super();
  }

  /**
   * This one argument constructor sets just the value.
   *
   * @param label the string that will become the category/value
   */
  public CategoryWordTag(String label) {
    super(label);
  }

  public CategoryWordTag(String category, String word, String tag) {
    super(category);
    this.word = word;
    this.tag = tag;
  }

  /**
   * Creates a new CategoryWordTag label from an existing label.
   * The oldLabel value() -- i.e., category -- is used for the new label.
   * The tag and word
   * are initialized iff the current label implements HasTag and HasWord
   * respectively.
   *
   * @param oldLabel The label to use as a basis of this Label
   */
  public CategoryWordTag(Label oldLabel) {
    super(oldLabel);
    if (oldLabel instanceof HasTag) {
      this.tag = ((HasTag) oldLabel).tag();
    }
    if (oldLabel instanceof HasWord) {
      this.word = ((HasWord) oldLabel).word();
    }
  }

  public String category() {
    return value();
  }

  public void setCategory(String category) {
    setValue(category);
  }

  public String word() {
    return word;
  }

  public void setWord(String word) {
    this.word = word;
  }

  public String tag() {
    return tag;
  }

  public void setTag(String tag) {
    this.tag = tag;
  }

  public void setCategoryWordTag(String category, String word, String tag) {
    setCategory(category);
    setWord(word);
    setTag(tag);
  }


  /**
   * Returns a <code>String</code> representation of the label.
   * This attempts to be somewhat clever in choosing to print or
   * suppress null components and the details of words or categories
   * depending on the setting of <code>printWordTag</code> and
   * <code>suppressTerminalDetails</code>.
   *
   * @return The label as a string
   */
  @Override
  public String toString() {
    if (category() != null) {
      if ((word() == null || tag() == null) || !printWordTag || (suppressTerminalDetails && (word().equals(category()) || tag().equals(category())))) {
        return category();
      } else {
        return category() + "[" + word() + "/" + tag() + "]";
      }
    } else {
      if (tag() == null) {
        return word();
      } else {
        return word() + "/" + tag();
      }
    }
  }


  /**
   * Returns a <code>String</code> representation of the label.
   * If the argument String is "full" then all components of the label
   * are returned, and otherwise the normal toString() is returned.
   *
   * @return The label as a string
   */
  public String toString(String mode) {
    if ("full".equals(mode)) {
        return category() + "[" + word() + "/" + tag() + "]";
    }
    return toString();
  }


  /**
   * Set everything by reversing a toString operation.
   * This should be added at some point.
   */
  @Override
  public void setFromString(String labelStr) {
    throw new UnsupportedOperationException();
  }


  // extra class guarantees correct lazy loading (Bloch p.194)
  private static class LabelFactoryHolder {
    private LabelFactoryHolder() {}
    private static final LabelFactory lf = new CategoryWordTagFactory();
  }

  /**
   * Return a factory for this kind of label
   * (i.e., <code>CategoryWordTag</code>).
   * The factory returned is always the same one (a singleton).
   *
   * @return The label factory
   */
  @Override
  public LabelFactory labelFactory() {
    return LabelFactoryHolder.lf;
  }


  /**
   * Return a factory for this kind of label
   *
   * @return The label factory
   */
  public static LabelFactory factory() {
    return LabelFactoryHolder.lf;
  }

}

