package edu.stanford.nlp.util;

/**
 * An {@link IntTuple} of length 3, whose elements are called the
 * source, middle, and target.
 */
public class IntTriple extends IntTuple {

  private static final long serialVersionUID = -3744404627253652799L;

  /** Creates a triple of three zeros. */
  public IntTriple() {
    super(3);
  }

  /**
   * Creates a triple with the given elements.
   *
   * @param src The first element (the source)
   * @param mid The second element (the middle)
   * @param trgt The third element (the target)
   */
  public IntTriple(int src, int mid, int trgt) {
    super(3);
    elements[0] = src;
    elements[1] = mid;
    elements[2] = trgt;
  }


  @Override
  public IntTuple getCopy() {
    IntTriple nT = new IntTriple(elements[0], elements[1], elements[2]);
    return nT;
  }


  /**
   * Returns the first element.
   *
   * @return The source
   */
  public int getSource() {
    return elements[0];
  }

  /**
   * Returns the third element.
   *
   * @return The target
   */
  public int getTarget() {
    return elements[2];
  }

  /**
   * Returns the second element.
   *
   * @return The middle
   */
  public int getMiddle() {
    return elements[1];
  }

}

