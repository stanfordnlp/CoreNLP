package edu.stanford.nlp.util;

/**
 * An {@link IntTuple} of length two, whose elements are called the source
 * and the target.
 */
public class IntPair extends IntTuple {

  private static final long serialVersionUID = 1L;


  /** Creates a pair of two zeros. */
  public IntPair() {
    super(2);
  }

  /**
   * Creates a pair with the given elements.
   *
   * @param src the first element (the source)
   * @param trgt the second element (the target)
   */
  public IntPair(int src, int trgt) {
    super(2);
    elements[0] = src;
    elements[1] = trgt;
  }


  /**
   * Return the first element of the pair
   *
   * @return the first element
   */
  public int getSource() {
    return get(0);
  }

  /**
   * Return the second element of the pair
   *
   * @return the second element
   */
  public int getTarget() {
    return get(1);
  }


  @Override
  public IntTuple getCopy() {
    return new IntPair(elements[0], elements[1]);
  }

  @Override
  public boolean equals(Object iO) {
    if(!(iO instanceof IntPair)) {
      return false;
    }
    IntPair i = (IntPair) iO;
    return elements[0] == i.get(0) && elements[1] == i.get(1);
  }

  @Override
  public int hashCode() {
    return elements[0] * 17 + elements[1];
  }

}
