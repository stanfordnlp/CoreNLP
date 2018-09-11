package edu.stanford.nlp.process;

import java.util.regex.Pattern;

/** This class contains various static utility methods invoked by JFlex lexers.
 *  Having this utility code placed outside the lexers facilitates normal
 *  IDE code editing.
 *
 *  @author Christopher Manning
 */
public class LexerUtils {

  private LexerUtils() {} // static methods

  private static final Pattern CENTS_PATTERN = Pattern.compile("\u00A2");
  private static final Pattern POUND_PATTERN = Pattern.compile("\u00A3");
  private static final Pattern GENERIC_CURRENCY_PATTERN = Pattern.compile("[\u0080\u00A4\u20A0\u20AC\u20B9]");
  private static final Pattern CP1252_EURO_PATTERN = Pattern.compile("\u0080");

  private static final Pattern ONE_FOURTH_PATTERN = Pattern.compile("\u00BC");
  private static final Pattern ONE_HALF_PATTERN = Pattern.compile("\u00BD");
  private static final Pattern THREE_FOURTHS_PATTERN = Pattern.compile("\u00BE");
  private static final Pattern ONE_THIRD_PATTERN = Pattern.compile("\u2153");
  private static final Pattern TWO_THIRDS_PATTERN = Pattern.compile("\u2154");


  /** Change precomposed fraction characters to spelled out letter forms. */
  public static String normalizeFractions(boolean normalizeFractions, boolean escapeForwardSlashAsterisk, final String in) {
    String out = in;
    if (normalizeFractions) {
      if (escapeForwardSlashAsterisk) {
        out = ONE_FOURTH_PATTERN.matcher(out).replaceAll("1\\\\/4");
        out = ONE_HALF_PATTERN.matcher(out).replaceAll("1\\\\/2");
        out = THREE_FOURTHS_PATTERN.matcher(out).replaceAll("3\\\\/4");
        out = ONE_THIRD_PATTERN.matcher(out).replaceAll("1\\\\/3");
        out = TWO_THIRDS_PATTERN.matcher(out).replaceAll("2\\\\/3");
      } else {
        out = ONE_FOURTH_PATTERN.matcher(out).replaceAll("1/4");
        out = ONE_HALF_PATTERN.matcher(out).replaceAll("1/2");
        out = THREE_FOURTHS_PATTERN.matcher(out).replaceAll("3/4");
        out = ONE_THIRD_PATTERN.matcher(out).replaceAll("1/3");
        out = TWO_THIRDS_PATTERN.matcher(out).replaceAll("2/3");
      }
    }
    return out;
  }


  @SuppressWarnings("unused")
  public static String normalizeCurrency(String in) {
    String s1 = in;
    s1 = CENTS_PATTERN.matcher(s1).replaceAll("cents");
    s1 = POUND_PATTERN.matcher(s1).replaceAll("#");  // historically used for pound in PTB3
    s1 = GENERIC_CURRENCY_PATTERN.matcher(s1).replaceAll("\\$");  // Euro (ECU, generic currency)  -- no good translation!
    s1 = CP1252_EURO_PATTERN.matcher(s1).replaceAll("\u20AC");
    return s1;
  }

  /** Still at least turn cp1252 euro symbol to Unicode one. */
  public static String minimallyNormalizeCurrency(String in) {
    String s1 = in;
    s1 = CP1252_EURO_PATTERN.matcher(s1).replaceAll("\u20AC");
    return s1;
  }

  public static String removeSoftHyphens(String in) {
    // \u00AD is the soft hyphen character, which we remove, regarding it as inserted only for line-breaking
    if (in.indexOf('\u00AD') < 0) {
      // shortcut doing work
      return in;
    }
    int length = in.length();
    StringBuilder out = new StringBuilder(length - 1);
    /*
    // This isn't necessary, as BMP, low, and high surrogate encodings are disjoint!
    for (int offset = 0, cp; offset < length; offset += Character.charCount(cp)) {
      cp = in.codePointAt(offset);
      if (cp != '\u00AD') {
        out.appendCodePoint(cp);
      }
    }
    */
    for (int i = 0; i < length; i++) {
      char ch = in.charAt(i);
      if (ch != '\u00AD') {
       out.append(ch);
      }
    }
    if (out.length() == 0) {
      out.append('-'); // don't create an empty token, put in a regular hyphen
    }
    return out.toString();
  }

  /* CP1252: dagger, double dagger, per mille, bullet, small tilde, trademark */
  public static String processCp1252misc(String arg) {
    switch (arg) {
    case "\u0086":
      return "\u2020";
    case "\u0087":
      return "\u2021";
    case "\u0089":
      return "\u2030";
    case "\u0095":
      return "\u2022";
    case "\u0098":
      return "\u02DC";
    case "\u0099":
      return "\u2122";
    default:
      throw new IllegalArgumentException("Bad process cp1252");
    }
  }

  private static final Pattern AMP_PATTERN = Pattern.compile("(?i:&amp;)");

  /** Convert an XML-escaped ampersand back into an ampersand. */
  public static String normalizeAmp(final String in) {
    return AMP_PATTERN.matcher(in).replaceAll("&");
  }

  /** This quotes a character with a backslash, but doesn't do it
   *  if the character is already preceded by a backslash.
   */
  public static String escapeChar(String s, char c) {
    int i = s.indexOf(c);
    while (i != -1) {
      if (i == 0 || s.charAt(i - 1) != '\\') {
        s = s.substring(0, i) + '\\' + s.substring(i);
        i = s.indexOf(c, i + 2);
      } else {
        i = s.indexOf(c, i + 1);
      }
    }
    return s;
  }

}
