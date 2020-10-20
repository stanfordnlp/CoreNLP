package edu.stanford.nlp.util;

/**
 * Just a single integer
 *
 * @author Kristina Toutanova (kristina@cs.stanford.edu)
 */

public class IntUni extends IntTuple {

  public IntUni() {
    super(1);
  }


  public IntUni(int src) {
    super(1);
    elements[0] = src;
  }


  public int getSource() {
    return elements[0];
  }

  public void setSource(int src) {
    elements[0] = src;
  }


  @Override
  public IntTuple getCopy() {
    IntUni nT = new IntUni(elements[0]);
    return nT;
  }

  public void add(int val) {
    elements[0] += val;
  }

  private static final long serialVersionUID = -7182556672628741200L;

}
