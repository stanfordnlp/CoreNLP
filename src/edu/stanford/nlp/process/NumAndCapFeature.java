package edu.stanford.nlp.process;


import edu.stanford.nlp.process.CapitalFeature.CapitalFeatureValue;

/**
 * Provides a partition over the set of possible unseen words that
 * corresponds to the capitalization and inclusion of numbers in the
 * word. Uses the NumAndCapFeatureValue class as possible values.
 *
 * @author Teg Grenager grenager@cs.stanford.edu
 */
public class NumAndCapFeature implements Feature {

  /**
   * 
   */
  private static final long serialVersionUID = 276346155803559010L;
  private NumAndCapFeatureValue[] allValues;
  private CapitalFeature cf = new CapitalFeature();
  private NumberFeature nf = new NumberFeature();

  public int numValues() {
    return allValues.length;
  }

  /**
   * Returns each possible feature in an array. Not for external use.
   */
  public FeatureValue[] allValues() {
    return allValues;
  }

  public NumAndCapFeature() {
    NumberFeature nf = new NumberFeature();
    CapitalFeature cf = new CapitalFeature();
    NumberFeatureValue[] nfv = (NumberFeatureValue[]) nf.allValues();
    CapitalFeatureValue[] cfv = (CapitalFeatureValue[]) cf.allValues();
    allValues = new NumAndCapFeatureValue[nfv.length * cfv.length];
    for (int i = 0; i < nfv.length; i++) {
      for (int j = 0; j < cfv.length; j++) {
        allValues[i * cfv.length + j] = new NumAndCapFeatureValue(nfv[i], cfv[j]);
      }
    }
  }

  /**
   * Use this to get the value of a String s.
   */
  public FeatureValue getValue(String s) {
    CapitalFeatureValue cfv = (CapitalFeatureValue) cf.getValue(s);
    NumberFeatureValue nfv = (NumberFeatureValue) nf.getValue(s);
    return allValues[nfv.getValue() * cf.numValues() + cfv.getValue()];
  }

  public static void main(String[] args) {
    NumAndCapFeatureValue f = new NumAndCapFeatureValue(args[0]);
    NumAndCapFeatureValue g = new NumAndCapFeatureValue(args[1]);
    System.out.println("f is " + f + "; g is " + g);
    System.out.println("f.equals(g) ? " + f.equals(g));
  }

}

/**
 * Provides the set of values used by the NumAndCapFeature class to
 * assign to unseen words.
 *
 * @author Teg Grenager grenager@cs.stanford.edu
 */
class NumAndCapFeatureValue implements FeatureValue {

  private static NumberFeature nf = new NumberFeature();
  private NumberFeatureValue nfv;
  private CapitalFeatureValue cfv;

  NumAndCapFeatureValue(String s) {
    nfv = (NumberFeatureValue) nf.getValue(s);
    cfv = (CapitalFeatureValue) nf.getValue(s);
  }

  NumAndCapFeatureValue(NumberFeatureValue nfv, CapitalFeatureValue cfv) {
    this.nfv = nfv;
    this.cfv = cfv;
  }

  @Override
  public boolean equals(Object that) {
    if (that instanceof NumAndCapFeatureValue) {
      NumAndCapFeatureValue ncfv = (NumAndCapFeatureValue) that;
      return this.nfv == ncfv.nfv && this.cfv == ncfv.cfv;
    } else {
      return false;
    }
  }


  @Override
  public int hashCode() {
    return nfv.getValue() << 3 + cfv.getValue();
  }

  @Override
  public String toString() {
    // return "NumAndCapFeatureValue[" + nfv + "," + cfv + "]";
    return nfv + "-" + cfv;
  }

  private static final long serialVersionUID = -9060210814318734549L;

}
