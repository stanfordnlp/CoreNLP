package edu.stanford.nlp.ling;

/**
 * A <code>TaggedWord</code> object contains a word and its tag.
 * The <code>value()</code> of a TaggedWord is the Word.  The tag
 * is secondary.
 *
 * @author Christopher Manning
 */
public class TaggedWord extends Word implements HasTag {

  private String tag;

  private static final String DIVIDER = "/";

  /**
   * Create a new <code>TaggedWord</code>.
   * It will have <code>null</code> for its content fields.
   */
  public TaggedWord() {
    super();
  }

  /**
   * Create a new <code>TaggedWord</code>.
   *
   * @param word The word, which will have a <code>null</code> tag
   */
  public TaggedWord(String word) {
    super(word);
  }

  /**
   * Create a new <code>TaggedWord</code>.
   *
   * @param word The word
   * @param tag  The tag
   */
  public TaggedWord(String word, String tag) {
    super(word);
    this.tag = tag;
  }

  /**
   * Create a new <code>TaggedWord</code>.
   *
   * @param oldLabel A Label.  If it implements the HasWord and/or
   *                 HasTag interface, then the corresponding value will be set
   */
  public TaggedWord(Label oldLabel) {
    super(oldLabel.value());
    if (oldLabel instanceof HasTag) {
      this.tag = ((HasTag) oldLabel).tag();
    }
  }

  /**
   * Create a new <code>TaggedWord</code>.
   *
   * @param word This word is passed to the supertype constructor
   * @param tag  The <code>value()</code> of this label is set as the
   *             tag of this Label
   */
  public TaggedWord(Label word, Label tag) {
    super(word);
    this.tag = tag.value();
  }

  public String tag() {
    return tag;
  }

  public void setTag(String tag) {
    this.tag = tag;
  }

  @Override
  public String toString() {
    return toString(DIVIDER);
  }

  public String toString(String divider) {
    return word() + divider + tag;
  }


  /**
   * Sets a TaggedWord from decoding
   * the <code>String</code> passed in.  The String is divided according
   * to the divider character (usually, "/").  We assume that we can
   * always just
   * divide on the rightmost divider character, rather than trying to
   * parse up escape sequences.  If the divider character isn't found
   * in the word, then the whole string becomes the word, and the tag
   * is <code>null</code>.
   *
   * @param taggedWord The word that will go into the <code>Word</code>
   */
  public void setFromString(String taggedWord) {
    setFromString(taggedWord, DIVIDER);
  }

  public void setFromString(String taggedWord, String divider) {  
    int where = taggedWord.lastIndexOf(divider);
    if (where >= 0) {
      setWord(taggedWord.substring(0, where));
      setTag(taggedWord.substring(where + 1));
    } else {
      setWord(taggedWord);
      setTag(null);
    }
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
