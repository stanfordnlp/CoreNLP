package edu.stanford.nlp.process;

/**
 * Provides a partition over the set of possible unseen words that
 * corresponds to the capitalization of characters in the
 * word. Uses the CapitalFeatureValue class as possible values.
 *
 * @author Teg Grenager grenager@cs.stanford.edu
 */
public class CapitalFeature implements Feature {

  /**
   * 
   */
  private static final long serialVersionUID = 8673609728403992572L;
  /**
   * A word that has at least two letters, such that one is word initial and
   * capitalized, and the other is lowercase
   */
  private CapitalFeatureValue CAPITALIZED = new CapitalFeatureValue(0);
  /**
   * A word that has at least one letter and all letters are uppercase
   */
  private CapitalFeatureValue ALLCAPS = new CapitalFeatureValue(1);
  /**
   * A word that has at least one letter and all letters are lowercase
   */
  private CapitalFeatureValue LOWERCASE = new CapitalFeatureValue(2);
  /**
   * A word that has at least one letter but is not one of the above
   */
  private CapitalFeatureValue MIXEDCASE = new CapitalFeatureValue(3);
  /**
   * A word with no letters
   */
  private CapitalFeatureValue NONLETTERS = new CapitalFeatureValue(4);

  /**
   * Returns each possible feature in an array. Not for external use.
   */
  private final CapitalFeatureValue[] allValues = new CapitalFeatureValue[]{CAPITALIZED, ALLCAPS, LOWERCASE, MIXEDCASE, NONLETTERS};

  public int numValues() {
    return allValues.length;
  }

  public FeatureValue[] allValues() {
    return allValues;
  }

  /**
   * CDM: This could probably be redone using regular expressions.
   * Using s.toCharArray() didn't seem to speed things up (reverse?).
   */
  public FeatureValue getValue(String s) {
    int leng = s.length();
    if (leng == 0) {
      return NONLETTERS;
    }

    boolean seenLower = false;
    boolean seenUpper = false;
    boolean allCaps = true, allLower = true, initCap = false;

    for (int i = 0; i < leng; i++) {
      char ch = s.charAt(i);
      boolean up = Character.isUpperCase(ch);
      boolean let = Character.isLetter(ch);

      if (up) {
        // System.out.println("Seen upper: " + ch);
        seenUpper = true;
        allLower = false;
        if (i == 0) {
          initCap = true;
        }
      } else if (let) {
        seenLower = true;
        allCaps = false;
      }
    }

    if (initCap && seenLower) {
      return CAPITALIZED;
    } else if (seenUpper && allCaps) {
      return ALLCAPS;
    } else if (seenLower && allLower) {
      return LOWERCASE;
    } else if (seenLower) {
      return MIXEDCASE;
    } else {
      return NONLETTERS;
    }
  }

  

  /**
   * Provides the set of values used by the CapitalFeature class to
   * assign to unseen words.
   *
   * @author Teg Grenager grenager@cs.stanford.edu
   */
  public static class CapitalFeatureValue implements FeatureValue {

    private final int val;

    private CapitalFeatureValue(int val) {
      this.val = val;
    }

    public int getValue() {
      return val;
    }

    @Override
    public boolean equals(Object that) {
      if (that instanceof CapitalFeatureValue) {
        CapitalFeatureValue cfv = (CapitalFeatureValue) that;
        return cfv.val == this.val;
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
          return "CAPITALIZED";
        case 1:
          return "ALLCAPS";
        case 2:
          return "LOWERCASE";
        case 3:
          return "MIXEDCASE";
        case 4:
          return "NONLETTERS";
        default:
          return "Not a known case";
      }
    }

    private static final long serialVersionUID = -5772396359064803302L;

  }
}
