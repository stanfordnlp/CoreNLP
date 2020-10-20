package edu.stanford.nlp.math;

import java.util.regex.Pattern;

/**
 * This file includes a regular expression to match numbers.  This
 * will save quite a bit of time in places where you want to test if
 * something is a number without wasting the time to parse it or throw
 * an exception if it isn't.  For example, you can call isDouble() to
 * see if a String is a double without having to try/catch the
 * NumberFormatException that gets produced if it is not.

 * The regular expression is conveniently provided in the javadoc for Double.
 * http://java.sun.com/javase/6/docs/api/java/lang/Double.html
 *
 * @author John Bauer
 * (sort of)
 */
public class NumberMatchingRegex {
  private NumberMatchingRegex() {}

  static final Pattern decintPattern = Pattern.compile("[+-]?\\d+");

  /**
   * Tests to see if an integer is a decimal integer, 
   * perhaps starting with +/-.
   */
  public static boolean isDecimalInteger(String string) {
    return (decintPattern.matcher(string).matches());
  }

  static final String Digits     = "(\\p{Digit}+)";
  static final String HexDigits  = "(\\p{XDigit}+)";
  // an exponent is 'e' or 'E' followed by an optionally 
  // signed decimal integer.
  static final String Exp        = "[eE][+-]?"+Digits;
  static final String fpRegex    =
    ("[\\x00-\\x20]*" + // Optional leading "whitespace"
     "[+-]?(" +         // Optional sign character
     "NaN|" +           // "NaN" string
     "Infinity|" +      // "Infinity" string
     
     // A decimal floating-point string representing a finite positive
     // number without a leading sign has at most five basic pieces:
     // Digits . Digits ExponentPart FloatTypeSuffix
     // 
     // Since this method allows integer-only strings as input
     // in addition to strings of floating-point literals, the
     // two sub-patterns below are simplifications of the grammar
     // productions from the Java Language Specification, 2nd 
     // edition, section 3.10.2.
     
     // Digits ._opt Digits_opt ExponentPart_opt FloatTypeSuffix_opt
     "((("+Digits+"(\\.)?("+Digits+"?)("+Exp+")?)|"+
     
     // . Digits ExponentPart_opt FloatTypeSuffix_opt
     "(\\.("+Digits+")("+Exp+")?)|"+
     
     // Hexadecimal strings
     "((" +
     // 0[xX] HexDigits ._opt BinaryExponent FloatTypeSuffix_opt
     "(0[xX]" + HexDigits + "(\\.)?)|" +
     
     // 0[xX] HexDigits_opt . HexDigits BinaryExponent FloatTypeSuffix_opt
     "(0[xX]" + HexDigits + "?(\\.)" + HexDigits + ")" +
     
     ")[pP][+-]?" + Digits + "))" +
     "[fFdD]?))" +
     "[\\x00-\\x20]*");// Optional trailing "whitespace"

  static final Pattern fpPattern = Pattern.compile(fpRegex);

  /**
   * Returns true if the number can be successfully parsed by Double.
   * Locale specific to English and ascii numerals.
   */
  public static boolean isDouble(String string) {
    return (fpPattern.matcher(string).matches());
  }
}