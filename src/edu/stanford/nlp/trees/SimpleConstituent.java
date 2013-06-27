package edu.stanford.nlp.trees;

import edu.stanford.nlp.ling.Label;
import edu.stanford.nlp.ling.LabelFactory;

/**
 * A <code>SimpleConstituent</code> object defines a generic edge in a graph.
 * The <code>SimpleConstituent</code> records only the endpoints of the
 * <code>Constituent</code>, as two integers.
 * It doesn't label the edges.
 * (It doesn't implement equals() since this actually decreases
 * performance on a non-final class (requires dynamic resolution of which
 * to call).)
 *
 * @author Christopher Manning
 */
public class SimpleConstituent extends Constituent {

  /**
   * Left node of edge.
   */
  private int start;

  /**
   * End node of edge.
   */
  private int end;


  /**
   * Create an empty <code>SimpleConstituent</code> object.
   */
  public SimpleConstituent() {
    // implicitly super();
  }


  /**
   * Create a <code>SimpleConstituent</code> object with given values.
   *
   * @param start start node of edge
   * @param end   end node of edge
   */
  public SimpleConstituent(int start, int end) {
    this.start = start;
    this.end = end;
  }


  /**
   * access start node.
   */
  @Override
  public int start() {
    return start;
  }


  /**
   * set start node.
   */
  @Override
  public void setStart(int start) {
    this.start = start;
  }


  /**
   * access end node.
   */
  @Override
  public int end() {
    return end;
  }


  /**
   * set end node.
   */
  @Override
  public void setEnd(int end) {
    this.end = end;
  }


  /**
   * A <code>SimpleConstituentLabelFactory</code> object makes a
   * <code>StringLabel</code> <code>LabeledScoredConstituent</code>.
   */
  private static class SimpleConstituentLabelFactory implements LabelFactory {

    /**
     * Make a new <code>SimpleConstituent</code>.
     *
     * @param labelStr A string.
     * @return The created label
     */
    public Label newLabel(final String labelStr) {
      return new SimpleConstituent(0, 0);
    }


    /**
     * Make a new <code>SimpleConstituent</code>.
     *
     * @param labelStr A string.
     * @param options  The options are ignored.
     * @return The created label
     */
    public Label newLabel(final String labelStr, final int options) {
      return newLabel(labelStr);
    }


    /**
     * Make a new <code>SimpleConstituent</code>.
     *
     * @param labelStr A string.
     * @return The created label
     */
    public Label newLabelFromString(final String labelStr) {
      return newLabel(labelStr);
    }


    /**
     * Create a new <code>SimpleConstituent</code>.
     *
     * @param oldLabel A <code>Label</code>.
     * @return A new <code>SimpleConstituent</code>
     */
    public Label newLabel(Label oldLabel) {
      return new SimpleConstituent(0, 0);
    }

  }


  // extra class guarantees correct lazy loading (Bloch p.194)
  private static class LabelFactoryHolder {
    static final LabelFactory lf = new SimpleConstituentLabelFactory();
  }


  /**
   * Return a factory for this kind of label.
   * The factory returned is always the same one (a singleton)
   *
   * @return the label factory
   */
  public LabelFactory labelFactory() {
    return LabelFactoryHolder.lf;
  }


  // extra class guarantees correct lazy loading (Bloch p.194)
  private static class ConstituentFactoryHolder {

    /**
     * A <code>SimpleConstituentFactory</code> acts as a factory for
     * creating objects of class <code>SimpleConstituent</code>.
     */
    private static class SimpleConstituentFactory implements ConstituentFactory {

      public Constituent newConstituent(int start, int end) {
        return new SimpleConstituent(start, end);
      }

      public Constituent newConstituent(int start, int end, Label label, double score) {
        return new SimpleConstituent(start, end);
      }

    }

    static final ConstituentFactory cf = new SimpleConstituentFactory();
  }


  /**
   * Return a factory for this kind of constituent.
   * The factory returned is always the same one (a singleton).
   *
   * @return The constituent factory
   */
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
