package edu.stanford.nlp.util;

/**
 * Just a single integer
 *
 * @author Kristina Toutanova (kristina@cs.stanford.edu)
 */

public class IntUni extends IntTuple {

  /** Creates an IntUni holding 0. */
  public IntUni() {
    super(1);
  }


  /**
   * Creates an IntUni holding the given value.
   *
   * @param src the value
   */
  public IntUni(int src) {
    super(1);
    elements[0] = src;
  }


  /**
   * Returns the value.
   *
   * @return the value
   */
  public int getSource() {
    return elements[0];
  }

  /**
   * Sets the value.
   *
   * @param src the new value
   */
  public void setSource(int src) {
    elements[0] = src;
  }


  @Override
  public IntTuple getCopy() {
    IntUni nT = new IntUni(elements[0]);
    return nT;
  }

  /**
   * Adds the given amount to the value.
   *
   * @param val the amount to add
   */
  public void add(int val) {
    elements[0] += val;
  }

  private static final long serialVersionUID = -7182556672628741200L;

}
