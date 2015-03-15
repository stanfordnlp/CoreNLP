package edu.stanford.nlp.international.french;

import java.util.regex.Pattern;

/**
 * Contains patterns for matching certain word types in French, such
 * as common suffices for nouns, verbs, adjectives and adverbs.
 */
public class FrenchUnknownWordSignatures {
  private static final Pattern pNounSuffix = Pattern.compile("(ier|ière|ité|ion|ison|isme|ysme|iste|esse|eur|euse|ence|eau|erie|ng|ette|age|ade|ance|ude|ogue|aphe|ate|duc|anthe|archie|coque|érèse|ergie|ogie|lithe|mètre|métrie|odie|pathie|phie|phone|phore|onyme|thèque|scope|some|pole|ôme|chromie|pie)s?$");
  private static final Pattern pAdjSuffix = Pattern.compile("(iste|ième|uple|issime|aire|esque|atoire|ale|al|able|ible|atif|ique|if|ive|eux|aise|ent|ois|oise|ante|el|elle|ente|oire|ain|aine)s?$");
  private static final Pattern pHasDigit = Pattern.compile("\\d+");
  private static final Pattern pIsDigit = Pattern.compile("^\\d+$");
  private static final Pattern pPosPlural = Pattern.compile("(s|ux)$");
  private static final Pattern pVerbSuffix = Pattern.compile("(ir|er|re|ez|ont|ent|ant|ais|ait|ra|era|eras|é|és|ées|isse|it)$");
  private static final Pattern pAdvSuffix = Pattern.compile("(iment|ement|emment|amment)$");
  private static final Pattern pHasPunc = Pattern.compile("([\u0021-\u002F\u003A-\u0040\\u005B\u005C\\u005D\u005E-\u0060\u007B-\u007E\u00A1-\u00BF\u2010-\u2027\u2030-\u205E\u20A0-\u20B5])+");
  private static final Pattern pIsPunc = Pattern.compile("([\u0021-\u002F\u003A-\u0040\\u005B\u005C\\u005D\u005E-\u0060\u007B-\u007E\u00A1-\u00BF\u2010-\u2027\u2030-\u205E\u20A0-\u20B5])+$");
  private static final Pattern pAllCaps = Pattern.compile("^[A-Z\\u00C0-\\u00DD]+$");

  public static boolean hasNounSuffix(String s) {
    return pNounSuffix.matcher(s).find();
  }
  
  public static String nounSuffix(String s) {
    return hasNounSuffix(s) ? "-noun" : "";
  }

  public static boolean hasAdjSuffix(String s) {
    return pAdjSuffix.matcher(s).find();
  }
  
  public static String adjSuffix(String s) {
    return hasAdjSuffix(s) ? "-adj" : "";
  }
  
  public static String hasDigit(String s) {
    return pHasDigit.matcher(s).find() ? "-num" : "";
  }
  
  public static String isDigit(String s) {
    return pIsDigit.matcher(s).find() ? "-isNum" : "";
  }
  
  public static boolean hasVerbSuffix(String s) {
    return pVerbSuffix.matcher(s).find();
  }

  public static String verbSuffix(String s) {
    return hasVerbSuffix(s) ? "-verb" : "";
  }
  
  public static boolean hasPossiblePlural(String s) {
    return pPosPlural.matcher(s).find();
  }

  public static String possiblePlural(String s) {
    return hasPossiblePlural(s) ? "-plural" : "";
  }
  
  public static boolean hasAdvSuffix(String s) {
    return pAdvSuffix.matcher(s).find();
  }

  public static String advSuffix(String s) {
    return hasAdvSuffix(s) ? "-adv" : "";
  }
  
  public static String hasPunc(String s) {
    return pHasPunc.matcher(s).find() ? "-hpunc" : "";
  }
  
  public static String isPunc(String s) {
    return pIsPunc.matcher(s).matches() ? "-ipunc" : "";
  }
  
  public static String isAllCaps(String s) {
    return pAllCaps.matcher(s).matches() ? "-allcap" : "";
  }
  
  public static String isCapitalized(String s) {
    if(s.length() > 0) {
      Character ch = s.charAt(0);
      return Character.isUpperCase(ch) ? "-upper" : "";
    }
    return "";
  }
}
