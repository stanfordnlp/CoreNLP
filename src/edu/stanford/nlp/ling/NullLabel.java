package edu.stanford.nlp.ling;

/**
 * A <code>NullLabel</code> object acts as a Label with linguistic
 * attributes, but doesn't actually store or return anything.  It returns
 * <code>null</code> to any requests.  Designed to be extended.
 *
 * @author Christopher Manning
 */
public class NullLabel extends ValueLabel implements HasWord, HasTag, HasCategory {

  /**
   * 
   */
  private static final long serialVersionUID = -4546142182973131732L;


  public NullLabel() {
  }

  public String word() {
    return null;
  }

  public void setWord(String word) {
  }

  public String tag() {
    return null;
  }

  public void setTag(String tag) {
  }

  public String category() {
    return null;
  }

  public void setCategory(String category) {
  }


  /**
   * A <code>NullLabelFactory</code> object makes a simple
   * <code>NullLabel</code> with no content from the argument.
   */
  private static class NullLabelFactory implements LabelFactory {

    /** Since a NullLabel stores nothing, it is really a Singleton.
     *  We don't enforce this in the NullLabel class, but we may as well
     *  have the factory always return the same one.
     */
    private static final NullLabel nullLabel = new NullLabel();

    /**
     * Make a new <code>NullLabel</code>, ignoring the argument.
     * <i>Actually always returns the same object.</i>
     *
     * @param labelStr A string that will be ignored.
     * @return The created label
     */
    public Label newLabel(final String labelStr) {
      return nullLabel;
    }


    /**
     * Make a new <code>NullLabel</code>, ignoring the argument.
     * <i>Actually always returns the same object.</i>
     *
     * @param labelStr A string that will be ignored.
     * @param options  The options are ignored.
     * @return The created label
     */
    public Label newLabel(final String labelStr, final int options) {
      return nullLabel;
    }


    /**
     * Make a new <code>NullLabel</code>, ignoring the argument.
     * <i>Actually always returns the same object.</i>
     *
     * @param labelStr A string that will be ignored.
     * @return The created label
     */
    public Label newLabelFromString(final String labelStr) {
      return nullLabel;
    }


    /**
     * Create a new <code>NullLabel</code>, ignoring the argument.
     * <i>Actually always returns the same object.</i>
     *
     * @param oldLabel A <code>Label</code>, which will be ignored.
     * @return A new <code>NullLabel</code>
     */
    public Label newLabel(Label oldLabel) {
      return nullLabel;
    }

  }


  // extra class guarantees correct lazy loading (Bloch p.194)
  private static class LabelFactoryHolder {
    private static final LabelFactory lf = new NullLabelFactory();
  }

  /**
   * Return a factory for this kind of label.
   * (I.e., <code>NullLabel</code>.)
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

}
