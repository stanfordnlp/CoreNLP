package edu.stanford.nlp.ie.hmm;

import edu.stanford.nlp.stats.ClassicCounter;
import java.io.PrintWriter;
import java.io.Serializable;
import java.text.NumberFormat;

/**
 * An emit map that implements a three stage shrinkage probability estimate.
 * It uses, the state, the class, and uniform probabilities.
 *
 * @author Jim McFadden
 */
class ShrinkedEmitMap extends AbstractEmitMap implements Serializable {

  /**
   * 
   */
  private static final long serialVersionUID = -1169240648816761264L;
  private EmitMap base;
  private EmitMap parent;
  private EmitMap uniform;

  public double lambda1; // weight for base
  public double lambda2; // weight for parent
  public double lambda3; // weight for uniform

  public ShrinkedEmitMap(EmitMap base, EmitMap parent, EmitMap uniform) {
    this.base = base;
    this.parent = parent;
    this.uniform = uniform;

    lambda1 = 1.0 / 3;
    lambda2 = lambda1;
    lambda3 = lambda1;
  }

  public double get(String in) {
    return lambda1 * base.get(in) + lambda2 * parent.get(in) + lambda3 * uniform.get(in);
  }

  public double get1(String in) {
    return lambda1 * base.get(in);
  }

  public double get2(String in) {
    return lambda2 * parent.get(in);
  }

  public double get3(String in) {
    return lambda3 * uniform.get(in);
  }

  public void set(String s, double d) {
    base.set(s, d);
  }

  public ClassicCounter getCounter() {
    return base.getCounter();
  }

  public EmitMap getBase() {
    return base;
  }

  public void printUnseenEmissions(PrintWriter out, NumberFormat nf) {
    base.printUnseenEmissions(out, nf);
    out.println();
    out.println("Shrink weights");
    out.println("--------------");
    out.println("lambda1 [base/me] --> " + nf.format(lambda1));
    out.println("lambda2 [parent]  --> " + nf.format(lambda2));
    out.println("lambda3 [uniform] --> " + nf.format(lambda3));
  }

}
