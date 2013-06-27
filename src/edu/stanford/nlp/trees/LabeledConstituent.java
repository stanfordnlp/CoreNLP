package edu.stanford.nlp.trees;

import edu.stanford.nlp.ling.Label;
import edu.stanford.nlp.ling.LabelFactory;
import edu.stanford.nlp.ling.StringLabel;

/**
 * A <code>LabeledConstituent</code> object represents a single bracketing in
 * a derivation, including start and end points and <code>Label</code>
 * information, but excluding probabilistic information.  It is used
 * to represent the basic information that is accumulated in exploring parses.
 *
 * @author Christopher Manning
 * @version 2002/06/01
 */
public class LabeledConstituent extends SimpleConstituent
        /* implements Label */ {

  /**
   * The Label.
   */
  private Label label;


  /**
   * Create an empty <code>LabeledConstituent</code> object.
   */
  public LabeledConstituent() {
    // implicitly super();
  }


  /**
   * Create a <code>LabeledConstituent</code> object with given
   * values.
   *
   * @param start Start node of edge
   * @param end   End node of edge
   */
  public LabeledConstituent(int start, int end) {
    super(start, end);
  }


  /**
   * Create a <code>LabeledConstituent</code> object with given values.
   *
   * @param start Start node of edge
   * @param end   End node of edge
   * @param label The label of the <code>Constituent</code>
   */
  public LabeledConstituent(int start, int end, Label label) {
    super(start, end);
    this.label = label;
  }


  /**
   * Create a <code>LabeledConstituent</code> object with given values.
   *
   * @param start       Start node of edge
   * @param end         End node of edge
   * @param stringValue The name of the <code>Constituent</code>
   */
  public LabeledConstituent(int start, int end, String stringValue) {
    super(start, end);
    this.label = new StringLabel(stringValue);
  }


  @Override
  public Label label() {
    return label;
  }

  @Override
  public void setLabel(Label label) {
    this.label = label;
  }

  @Override
  public void setFromString(String labelStr) {
    this.label = new StringLabel(labelStr);
  }

  /**
   * A <code>LabeledConstituentLabelFactory</code> object makes a
   * <code>StringLabel</code> <code>LabeledScoredConstituent</code>.
   */
  private static class LabeledConstituentLabelFactory implements LabelFactory {

    /**
     * Make a new <code>LabeledConstituent</code>.
     *
     * @param labelStr A string.
     * @return The created label
     */
    public Label newLabel(final String labelStr) {
      return new LabeledConstituent(0, 0, new StringLabel(labelStr));
    }


    /**
     * Make a new <code>LabeledConstituent</code>.
     *
     * @param labelStr A string.
     * @param options  The options are ignored.
     * @return The created label
     */
    public Label newLabel(final String labelStr, final int options) {
      return newLabel(labelStr);
    }


    /**
     * Make a new <code>LabeledConstituent</code>.
     *
     * @param labelStr A string.
     * @return The created label
     */
    public Label newLabelFromString(final String labelStr) {
      return newLabel(labelStr);
    }


    /**
     * Create a new <code>LabeledConstituent</code>.
     *
     * @param oldLabel A <code>Label</code>.
     * @return A new <code>LabeledConstituent</code>
     */
    public Label newLabel(Label oldLabel) {
      return new LabeledConstituent(0, 0, oldLabel);
    }

  }


  // extra class guarantees correct lazy loading (Bloch p.194)
  private static class LabelFactoryHolder {
    static final LabelFactory lf = new LabeledConstituentLabelFactory();
  }

  /**
   * Return a factory for this kind of label.
   * The factory returned is always the same one (a singleton)
   *
   * @return the label factory
   */
  @Override
  public LabelFactory labelFactory() {
    return LabelFactoryHolder.lf;
  }


  // extra class guarantees correct lazy loading (Bloch p.194)
  private static class ConstituentFactoryHolder {

    /**
     * A <code>LabeledConstituentFactory</code> acts as a factory for
     * creating objects of class <code>LabeledConstituent</code>.
     */
    private static class LabeledConstituentFactory implements ConstituentFactory {

      public Constituent newConstituent(int start, int end) {
        return new LabeledConstituent(start, end);
      }

      public Constituent newConstituent(int start, int end, Label label, double score) {
        return new LabeledConstituent(start, end, label);
      }

    }

    static final ConstituentFactory cf = new LabeledConstituentFactory();
  }


  /**
   * Return a factory for this kind of constituent.
   * The factory returned is always the same one (a singleton).
   *
   * @return The constituent factory
   */
  @Override
  public ConstituentFactory constituentFactory() {
    return ConstituentFactoryHolder.cf;
  }


  /**
   * Return a factory for this kind of constituent.
   * The factory returned is always the same one (a singleton).
   *
   * @return The constituent factory
   */
  public static ConstituentFactory factory() {
    return ConstituentFactoryHolder.cf;
  }

}
