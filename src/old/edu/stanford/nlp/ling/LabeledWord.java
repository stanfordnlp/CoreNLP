package old.edu.stanford.nlp.ling;

/**
 * A <code>LabeledWord</code> object contains a word and its tag.
 * The <code>value()</code> of a TaggedWord is the Word.  The tag
 * is, and is a Label instead of a String
 */
public class LabeledWord extends Word {

  private Label tag;
  
  private static String divider = "/";

  /**
   * Create a new <code>TaggedWord</code>.
   * It will have <code>null</code> for its content fields.
   */
  public LabeledWord() {
    super();
  }

  /**
   * Create a new <code>TaggedWord</code>.
   *
   * @param word The word, which will have a <code>null</code> tag
   */
  public LabeledWord(String word) {
    super(word);
  }

  /**
   * Create a new <code>TaggedWord</code>.
   *
   * @param word The word
   * @param tag  The tag
   */
  public LabeledWord(String word, Label tag) {
    super(word);
    this.tag = tag;
  }

  public LabeledWord(Label word, Label tag) {
    super(word);
    this.tag = tag;
  }

  public Label tag() {
    return tag;
  }

  public void setTag(Label tag) {
    this.tag = tag;
  }

  @Override
  public String toString() {
    return word() + divider + tag;
  }

  /**
   * Set the String (usually just a single character) used to mark
   * the separation between the word and the tag.  This is used when
   * printing out a <code>TaggedWord</code> via the <code>toString()</code>
   * method, and when reading in a <code>TaggedWord</code> via the
   * <code>setFromString()</code> method.
   * Unless altered using this method, the default is "/".
   *
   * @param divider The String between words and tags
   */
  public static void setDivider(String divider) {
    LabeledWord.divider = divider;
  }

  // extra class guarantees correct lazy loading (Bloch p.194)
  private static class LabelFactoryHolder {

    private LabelFactoryHolder() {}

    private static final LabelFactory lf = new TaggedWordFactory();

  }

  /**
   * Return a factory for this kind of label
   * (i.e., <code>TaggedWord</code>).
   * The factory returned is always the same one (a singleton).
   *
   * @return The label factory
   */
  @Override
  public LabelFactory labelFactory() {
    return LabelFactoryHolder.lf;
  }


  /**
   * Return a factory for this kind of label.
   *
   * @return The label factory
   */
  public static LabelFactory factory() {
    return LabelFactoryHolder.lf;
  }

  private static final long serialVersionUID = -7252006452127051085L;

}
