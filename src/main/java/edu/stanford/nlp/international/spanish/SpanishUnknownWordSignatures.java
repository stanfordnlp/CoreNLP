package edu.stanford.nlp.international.spanish;

import java.util.regex.Pattern;

/**
 * Contains patterns for matching certain word types in Spanish, such
 * as common suffices for nouns, verbs, adjectives and adverbs.
 *
 * These utilities are used to characterize unknown words within the
 * POS tagger and the parser.
 *
 * @see edu.stanford.nlp.tagger.maxent.ExtractorFramesRare
 * @see edu.stanford.nlp.parser.lexparser.SpanishUnknownWordModel
 *
 * @author Jon Gauthier
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

  private static final Pattern pImperfect = Pattern.compile(
    "(?:aba(?:[sn]|is)?|ábamos|[^r]ía(?:s|mos|is|n)?)$");
  private static final Pattern pInfinitive = Pattern.compile("[aei]r$");

  private static final Pattern pAdverb = Pattern.compile("mente$");

  // Most of the words disguised as first-person plural verb forms have
  // contrastive stress.. yay, easy to match!
  private static final Pattern pVerbFirstPersonPlural = Pattern.compile(
    "(?<!últ|máx|mín|án|próx|ís|cént|[np]ón|prést|gít|ínt|pár" +
      "|^extr|^supr|^tr?|^[Rr]?|gr)[eia]mos$");

  private static final Pattern pGerund = Pattern.compile(
    "(?i)((?<!^([bmn]|bl|com|contrab|cu|[fh]ern))a" +
      "|(?<!^(asci|ati|atu|compr|condesci|conti|desati|desci|desenti|disti|divid|enci|enti|estup" +
      "|exti|fi|hi|malenti|pret|refer|rever|sobreenti|subti|ti|transci|trasci|trem))e)ndo$");

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

  public static boolean hasImperfectSuffix(String s) {
    return pImperfect.matcher(s).find();
  }

  public static boolean hasInfinitiveSuffix(String s) {
    return pInfinitive.matcher(s).find();
  }

  public static boolean hasAdverbSuffix(String s) {
    return pAdverb.matcher(s).find();
  }

  public static boolean hasVerbFirstPersonPluralSuffix(String s) {
    return pVerbFirstPersonPlural.matcher(s).find();
  }

  public static boolean hasGerundSuffix(String s) {
    return pGerund.matcher(s).find();
  }

  // The *Suffix methods are used by the SpanishUnknownWordModel to
  // build a representation of an unknown word.

  public static String conditionalSuffix(String s) {
    return hasConditionalSuffix(s) ? "-cond" : "";
  }

  public static String imperfectSuffix(String s) {
    return hasImperfectSuffix(s) ? "-imp" : "";
  }

  public static String infinitiveSuffix(String s) {
    return hasInfinitiveSuffix(s) ? "-inf" : "";
  }

  public static String adverbSuffix(String s) {
    return hasAdverbSuffix(s) ? "-adv" : "";
  }

}
