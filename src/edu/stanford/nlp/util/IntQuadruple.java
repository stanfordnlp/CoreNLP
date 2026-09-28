package edu.stanford.nlp.util;

/**
 * An IntTuple of four integers: source, middle, target and second target.
 */
public class IntQuadruple extends IntTuple {

  private static final long serialVersionUID = 7154973101012473479L;


  /** Creates a quadruple with all four elements 0. */
  public IntQuadruple() {
    super(4);
  }

  /**
   * Creates a quadruple with the given elements.
   *
   * @param src the source (element 0)
   * @param mid the middle (element 1)
   * @param trgt the target (element 2)
   * @param trgt2 the second target (element 3)
   */
  public IntQuadruple(int src, int mid, int trgt, int trgt2) {
    super(4);
    elements[0] = src;
    elements[1] = mid;
    elements[2] = trgt;
    elements[3] = trgt2;
  }


  @Override
  public IntTuple getCopy() {
    IntQuadruple nT = new IntQuadruple(elements[0], elements[1], elements[2], elements[3]);
    return nT;
  }


  /**
   * Returns the source (element 0).
   *
   * @return the source
   */
  public int getSource() {
    return get(0);
  }


  /**
   * Returns the middle (element 1).
   *
   * @return the middle
   */
  public int getMiddle() {
    return get(1);
  }

  /**
   * Returns the target (element 2).
   *
   * @return the target
   */
  public int getTarget() {
    return get(2);
  }

  /**
   * Returns the second target (element 3).
   *
   * @return the second target
   */
  public int getTarget2() {
    return get(3);
  }
}
