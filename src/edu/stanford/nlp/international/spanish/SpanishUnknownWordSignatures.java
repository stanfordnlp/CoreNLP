package edu.stanford.nlp.international.spanish;

import java.util.regex.Pattern;

/**
 * Contains patterns for matching certain word types in Spanish, such
 * as common suffices for nouns, verbs, adjectives and adverbs.
 */
public class SpanishUnknownWordSignatures {

  private static final Pattern pMasculine = Pattern.compile("os?$");
  private static final Pattern pFeminine = Pattern.compile("as?$");

  // The following patterns help to distinguish between verbs in the
  // conditional tense and -er, -ir verbs in the indicative imperfect.
  // Words in these two forms have matching suffixes and are otherwise
  // difficult to distinguish.
  private static final Pattern pConditionalSuffix = Pattern.compile("[aei]ría(?:s|mos|is|n)?$");
  private static final Pattern pImperfectErIrSuffix = Pattern.compile("[^r]ía(?:s|mos|is|n)?$");

  private SpanishUnknownWordSignatures() {} // static methods

  public static boolean hasMasculineSuffix(String s) {
    return pMasculine.matcher(s).find();
  }

  public static boolean hasFeminineSuffix(String s) {
    return pFeminine.matcher(s).find();
  }

  public static boolean hasConditionalSuffix(String s) {
    return pConditionalSuffix.matcher(s).find();
  }

  public static boolean hasImperfectErIrSuffix(String s) {
    return pImperfectErIrSuffix.matcher(s).find();
  }

}
