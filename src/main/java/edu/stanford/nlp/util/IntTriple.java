package edu.stanford.nlp.util;

public class IntTriple extends IntTuple {

  private static final long serialVersionUID = -3744404627253652799L;

  public IntTriple() {
    super(3);
  }

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


  public int getSource() {
    return elements[0];
  }

  public int getTarget() {
    return elements[2];
  }

  public int getMiddle() {
    return elements[1];
  }

}

