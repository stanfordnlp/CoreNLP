package edu.stanford.nlp.process;

/**
 * Provides a partition over the set of possible unseen words that
 * corresponds to the formatting of numbers in the
 * word. Uses the NumberFeatureValue class as possible values.
 *
 * @author Teg Grenager grenager@cs.stanford.edu
 */
public class NumberFeature implements Feature {

  /**
   * 
   */
  private static final long serialVersionUID = -7214323781757946666L;
  /**
   * Is a positive integer: [0-9]+
   */
  private NumberFeatureValue CARDINAL = new NumberFeatureValue(0);
  /**
   * Other number: has a digit, and other stuff is . , and/or leading -
   */
  private NumberFeatureValue NUMBER = new NumberFeatureValue(1);
  /**
   * There is a digit in the word, but not one of the above two
   */
  private NumberFeatureValue HASNUMBER = new NumberFeatureValue(2);
  /**
   * There is no digit in the word
   */
  private NumberFeatureValue NONUMBER = new NumberFeatureValue(3);

  /**
   * Returns each possible feature in an array. Not for external use.
   */
  private NumberFeatureValue[] allValues = new NumberFeatureValue[]{CARDINAL, NUMBER, HASNUMBER, NONUMBER};

  public FeatureValue[] allValues() {
    return allValues;
  }

  public int numValues() {
    return allValues.length;
  }

  public FeatureValue getValue(String s) {
    int length = s.length();
    boolean number = true, seenDigit = false;
    boolean seenNonDigit = false;
    if (length == 0) {
      return NONUMBER;
    }
    for (int i = 0; i < length; i++) {
      char ch = s.charAt(i);
      boolean digit = Character.isDigit(ch);
      if (digit) {
        seenDigit = true;
      } else {
        seenNonDigit = true;
      }
      // allow commas, decimals, and negative numbers
      digit = digit || ch == '.' || ch == ',' || (i == 0 && ch == '-');
      if (!digit) {
        number = false;
      }
    }

    if (seenDigit && !seenNonDigit) {
      return CARDINAL;
    } else if (number && seenDigit) {
      return NUMBER;
    } else if (seenDigit) {
      return HASNUMBER;
    } else {
      return NONUMBER;
    }
  }


}

/**
 * Provides the set of values used by the NumberFeature class to
 * assign to unseen words.
 *
 * @author Teg Grenager grenager@cs.stanford.edu
 */
class NumberFeatureValue implements FeatureValue {

  private final int val;

  NumberFeatureValue(int val) {
    this.val = val;
  }

  public int getValue() {
    return val;
  }


  @Override
  public boolean equals(Object that) {
    if (that instanceof NumberFeatureValue) {
      NumberFeatureValue nfv = (NumberFeatureValue) that;
      return nfv.val == this.val;
    } else {
      return false;
    }
  }

  @Override
  public int hashCode() {
    return val;
  }


  @Override
  public String toString() {
    switch (val) {
      case 0:
        return "CARD";
      case 1:
        return "NUM";
      case 2:
        return "HASNUM";
      case 3:
        return "NONUM";
      default:
        return "Not a known number";
    }
  }


  private static final long serialVersionUID = 242051791620699698L;

}
