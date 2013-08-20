package edu.stanford.nlp.ie.hmm;

import edu.stanford.nlp.stats.ClassicCounter;

import java.io.PrintWriter;
import java.io.Serializable;
import java.text.NumberFormat;

/**
 * EmitMap that always emits one string with probability 1 and nothing else.
 *
 * @author Jim McFadden
 */
public class ConstantEmitMap implements EmitMap, Serializable {

  /**
   * 
   */
  private static final long serialVersionUID = -299716773121693601L;
  private String s;

  /**
   * Constructs a new EmiMap that only emits the given string with prob 1.
   */
  public ConstantEmitMap(String s) {
    this.s = s;
  }

  public String getString() {
    return s;
  }

  public double get(String s) {
    if (this.s.equals(s)) {
      return 1.0;
    } else {
      return 0.0;
    }
  }

  /**
   * Don't call this.
   */
  public void set(String s, double d) {
    throw
            new UnsupportedOperationException("can't call ConstantEmitMap.set()");
  }

  /**
   * Don't call this.
   */
  public ClassicCounter getCounter() {
    System.err.println("can't call ConstantEmitMap.getMap()");
    return null;
  }

  /**
   * Does nothing and returns 0.
   */
  public double tuneParameters(ClassicCounter expectedEmissions, HMM hmm) {
    return (0);
  }

  public void printEmissions(PrintWriter pw, boolean onlyCommon) {
    pw.println("Always emits: " + getString());
  }

  /**
   * Does nothing.
   */
  public void printUnseenEmissions(PrintWriter pw, NumberFormat nf) {
  }

}
