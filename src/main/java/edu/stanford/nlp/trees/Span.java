package edu.stanford.nlp.trees;

import edu.stanford.nlp.ling.Label;

/**
 * A <code>Span</code> is an optimized <code>SimpleConstituent</code> object.
 * It provides exactly the same functionality as a SimpleConstituent, but
 * by being final, and with its own implementation of Span equality,
 * it runs faster, so as to placate Dan Klein.  (With JDK1.3 client, it still
 * doesn't run as fast as an implementation outside of the SimpleConstituent
 * hierarchy, but with JDK1.3 server, it does!  And both versions are
 * several times faster with -server than -client, so that should be used.)
 *
 * @author Christopher Manning
 * @version 2001/01/08
 */
public final class Span extends SimpleConstituent {

  /**
   * Create an empty <code>Span</code> object.
   */
  public Span() {
    // implicitly super();
  }


  /**
   * Create a <code>Span</code> object with given values.
   *
   * @param start start node of edge
   * @param end   end node of edge
   */
  public Span(int start, int end) {
    super(start, end);
  }


  /**
   * An overloading for efficiency for when you know that you're comparing
   * with a Span.
   *
   * @param sp the span to compare against
   * @return whether they have the same start and end
   * @see Constituent#equals(Object)
   */
  public boolean equals(final Span sp) {
    return start() == sp.start() && end() == sp.end();
  }


  // extra class guarantees correct lazy loading (Bloch p.194)
  private static class ConstituentFactoryHolder {

    private ConstituentFactoryHolder() {}  // static holder class


    /**
     * A <code>SpanFactory</code> acts as a factory for creating objects
     * of class <code>Span</code>.
     * An interface.
     */
    private static class SpanFactory implements ConstituentFactory {

      public Constituent newConstituent(int start, int end) {
        return new Span(start, end);
      }


      public Constituent newConstituent(int start, int end, Label label, double score) {
        return new Span(start, end);
      }

    }


    private static final ConstituentFactory cf = new SpanFactory();

  } // end static class ConstituentFactoryHolder


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
