package edu.stanford.nlp.util;

public class IntPair extends IntTuple {

  private static final long serialVersionUID = 1L;


  public IntPair() {
    super(2);
  }

  public IntPair(int src, int trgt) {
    super(2);
    elements[0] = src;
    elements[1] = trgt;
  }


  /**
   * Return the first element of the pair
   */
  public int getSource() {
    return get(0);
  }

  /**
   * Return the second element of the pair
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
