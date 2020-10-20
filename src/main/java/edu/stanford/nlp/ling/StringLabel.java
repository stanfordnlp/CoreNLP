package edu.stanford.nlp.ling;

/**
 * A <code>StringLabel</code> object acts as a Label by containing a
 * single String, which it sets or returns in response to requests.
 * The hashCode() and compareTo() methods for this class assume that this
 * string value is non-null.  equals() is correctly implemented
 *
 * @author Christopher Manning
 * @version 2000/12/20
 */
public class StringLabel extends ValueLabel implements HasOffset {

  private String str;

  /**
   * Start position of the word in the original input string
   */
  private int beginPosition = -1;

  /**
   * End position of the word in the original input string
   */
  private int endPosition = -1;


  /**
   * Create a new <code>StringLabel</code> with a null content (i.e., str).
   */
  public StringLabel() {
  }


  /**
   * Create a new <code>StringLabel</code> with the given content.
   *
   * @param str The new label's content
   */
  public StringLabel(String str) {
    this.str = str;
  }

  /**
   * Create a new <code>StringLabel</code> with the given content.
   *
   * @param str The new label's content
   * @param beginPosition Start offset in original text
   * @param endPosition End offset in original text
   */
  public StringLabel(String str, int beginPosition, int endPosition) {
    this.str = str;
    setBeginPosition(beginPosition);
    setEndPosition(endPosition);
  }


  /**
   * Create a new <code>StringLabel</code> with the
   * <code>value()</code> of another label as its label.
   *
   * @param label The other label
   */
  public StringLabel(Label label) {
    this.str = label.value();
    if (label instanceof HasOffset) {
      HasOffset ofs = (HasOffset) label;
      setBeginPosition(ofs.beginPosition());
      setEndPosition(ofs.endPosition());
    }
  }


  /**
   * Return the word value of the label (or null if none).
   *
   * @return String the word value for the label
   */
  @Override
  public String value() {
    return str;
  }


  /**
   * Set the value for the label.
   *
   * @param value The value for the label
   */
  @Override
  public void setValue(final String value) {
    str = value;
  }


  /**
   * Set the label from a String.
   *
   * @param str The str for the label
   */
  @Override
  public void setFromString(final String str) {
    this.str = str;
  }

  @Override
  public String toString() {
    return str;
  }

  // extra class guarantees correct lazy loading (Bloch p.194)
  private static class StringLabelFactoryHolder {

    private StringLabelFactoryHolder() {}

    static final LabelFactory lf = new StringLabelFactory();
  }

  /**
   * Return a factory for this kind of label
   * (i.e., <code>StringLabel</code>).
   * The factory returned is always the same one (a singleton).
   *
   * @return The label factory
   */
  @Override
  public LabelFactory labelFactory() {
    return StringLabelFactoryHolder.lf;
  }


  /**
   * Return a factory for this kind of label.
   *
   * @return The label factory
   */
  public static LabelFactory factory() {
    return StringLabelFactoryHolder.lf;
  }

  public int beginPosition()
  {
    return beginPosition;
  }

  public int endPosition()
  {
    return endPosition;
  }

  public void setBeginPosition(int beginPosition)
  {
    this.beginPosition = beginPosition;
  }

  public void setEndPosition(int endPosition)
  {
    this.endPosition = endPosition;
  }

  private static final long serialVersionUID = -4153619273767524247L;

}
