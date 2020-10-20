package edu.stanford.nlp.parser.lexparser;

import java.util.regex.Pattern;

/**
 * Unknown word signatures for the Arabic Treebank.
 * These handle unvocalized Arabic, in either Buckwalter or Unicode.
 *
 * @author Roger Levy (rog@csli.stanford.edu)
 * @author Christopher Manning (extended to handle UTF-8)
 */
class ArabicUnknownWordSignatures {

  private ArabicUnknownWordSignatures() {
  }


  static boolean allDigitPlus(String word) {
    boolean allDigitPlus = true;
    boolean seenDigit = false;
    for (int i = 0, wlen = word.length(); i < wlen; i++) {
      char ch = word.charAt(i);
      if (Character.isDigit(ch)) {
        seenDigit = true;
      } else if (ch == '-' || ch == '.' || ch == ',' ||
                 ch == '\u066B' || ch == '\u066C' || ch == '\u2212') {
        // U+066B = Arabic decimal separator
        // U+066C = Arabic thousands separator
        // U+2212 = Minus sign
      } else {
        allDigitPlus = false;
      }
    }
    return allDigitPlus && seenDigit;
  }

  /** nisba suffix for deriving adjectives: (i)yy(n) [masc]
   *  or -(i)yya [fem].  Other adjectives are made in the binyanim system
   *  by vowel changes.
   */
  private static final Pattern adjectivalSuffixPattern =
    Pattern.compile("[y\u064A][y\u064A](?:[t\u062A]?[n\u0646])?$");

  static String likelyAdjectivalSuffix(String word) {
    if (adjectivalSuffixPattern.matcher(word).find()) {
      return "-AdjSuffix";
    } else {
      return "";
    }
  }

  private static final Pattern singularPastTenseSuffixPattern = Pattern.compile("[t\u062A]$");

  private static final Pattern pluralFirstPersonPastTenseSuffixPattern = Pattern.compile("[n\u0646][A\u0627]$");
  private static final Pattern pluralThirdPersonMasculinePastTenseSuffixPattern = Pattern.compile("[w\u0648]$");

  // could be used but doesn't seem very discriminating
  // private static final Pattern pluralThirdPersonFemininePastTenseSuffixPattern = Pattern.compile("[n\u0646]$");

  // there doesn't seem to be second-person marking in the corpus, just first
  // and non-first (?)
  static String pastTenseVerbNumberSuffix(String word) {
    if (singularPastTenseSuffixPattern.matcher(word).find())
      return "-PV.sg";
    if (pluralFirstPersonPastTenseSuffixPattern.matcher(word).find())
      return "-PV.pl1";
    if (pluralThirdPersonMasculinePastTenseSuffixPattern.matcher(word).find())
      return "-PV.pl3m";
    return "";
  }

  private static final Pattern pluralThirdPersonMasculinePresentTenseSuffixPattern = Pattern.compile("[w\u0648][\u0646n]$");

  static String presentTenseVerbNumberSuffix(String word) {
    return pluralThirdPersonMasculinePresentTenseSuffixPattern.matcher(word).find() ? "-IV.pl3m" : "";
  }

  private static final Pattern taaMarbuuTaSuffixPattern = Pattern.compile("[\u0629p]$"); // almost always ADJ or NOUN

  static String taaMarbuuTaSuffix(String word) {
    return taaMarbuuTaSuffixPattern.matcher(word).find() ? "-taaMarbuuTa" : "";
  }

  // Roger wrote: "ironically, this seems to be a better indicator of ADJ than
  // of NOUN", but Chris thinks it may just have been a bug in his code
  private static final Pattern abstractionNounSuffixPattern = Pattern.compile("[y\u064a][p\u0629]$");

  static String abstractionNounSuffix(String word) {
    return abstractionNounSuffixPattern.matcher(word).find() ? "-AbstractionSuffix" : "";
  }

  private static final Pattern masdarPrefixPattern = Pattern.compile("^[t\u062A]");

  static String masdarPrefix(String word) {
    return masdarPrefixPattern.matcher(word).find() ? "-maSdr" : "";
  }

}
