package edu.stanford.nlp.international.arabic.pipeline;

import java.io.File;
import java.util.*;
import java.util.regex.*;

import edu.stanford.nlp.trees.treebank.Mapper;
import edu.stanford.nlp.util.Generics;

/**
 * Applies a default set of lexical transformations that have been empirically validated
 * in various Arabic tasks. This class automatically detects the input encoding and applies
 * the appropriate set of transformations.
 *
 * @author Spence Green
 *
 */
public class GaleP4LexMapper implements Mapper {

  private static final Pattern utf8ArabicChart = Pattern.compile("[\u0600-\u06FF]");

  //Buckwalter patterns
  private static final String bwAlefChar = "A"; //U+0627
  private static final Pattern bwDiacritics = Pattern.compile("F|N|K|a|u|i|\\~|o");
  private static final Pattern bwTatweel = Pattern.compile("_");
  private static final Pattern bwAlef = Pattern.compile("\\{");
  private static final Pattern bwQuran = Pattern.compile("`");

  //TODO Extend coverage to entire Arabic code chart
  //Obviously Buckwalter is a lossful conversion, but no assumptions should be made about
  //UTF-8 input from "the wild"
  private static final Pattern utf8Diacritics = Pattern.compile("َ|ً|ُ|ٌ|ِ|ٍ|ّ|ْ");
  private static final Pattern utf8Tatweel = Pattern.compile("ـ");
  private static final Pattern utf8Alef = Pattern.compile("\u0671");
  private static final Pattern utf8Quran = Pattern.compile("[\u0615-\u061A]|[\u06D6-\u06E5]");

  //Patterns to fix segmentation issues observed in the ATB
  private static final Pattern cliticMarker = Pattern.compile("^-|-$");

  private static final Pattern hasNum = Pattern.compile("\\d+");
  private final Set<String> parentTagsToEscape;

  public GaleP4LexMapper() {

    //Tags for the canChangeEncoding() method
    parentTagsToEscape = Generics.newHashSet();
    parentTagsToEscape.add("PUNC");
    parentTagsToEscape.add("LATIN");
    parentTagsToEscape.add("-NONE-");
  }

  private String mapUtf8(String element) {
    //Remove diacritics
    Matcher rmDiacritics = utf8Diacritics.matcher(element);
    element = rmDiacritics.replaceAll("");

    if(element.length() > 1) {
      Matcher rmTatweel = utf8Tatweel.matcher(element);
      element = rmTatweel.replaceAll("");
    }

    //Normalize alef
    Matcher normAlef = utf8Alef.matcher(element);
    element = normAlef.replaceAll("ا");

    //Remove characters that only appear in the Qur'an
    Matcher rmQuran = utf8Quran.matcher(element);
    element = rmQuran.replaceAll("");

    if(element.length() > 1) {
      Matcher rmCliticMarker = cliticMarker.matcher(element);
      element = rmCliticMarker.replaceAll("");
    }

    return element;
  }

  private String mapBuckwalter(String element) {
    //Remove diacritics
    Matcher rmDiacritics = bwDiacritics.matcher(element);
    element = rmDiacritics.replaceAll("");

    //Remove tatweel
    if(element.length() > 1) {
      Matcher rmTatweel = bwTatweel.matcher(element);
      element = rmTatweel.replaceAll("");
    }

    //Normalize alef
    Matcher normAlef = bwAlef.matcher(element);
    element = normAlef.replaceAll(bwAlefChar);

    //Remove characters that only appear in the Qur'an
    Matcher rmQuran = bwQuran.matcher(element);
    element = rmQuran.replaceAll("");

    if(element.length() > 1) {
      Matcher rmCliticMarker = cliticMarker.matcher(element);
      element = rmCliticMarker.replaceAll("");
    }

    return element;
  }

  public String map(String parent, String element) {
    String elem = element.trim();

    if(parentTagsToEscape.contains(parent))
      return elem;

    Matcher utf8Encoding = utf8ArabicChart.matcher(elem);
    return (utf8Encoding.find()) ? mapUtf8(elem) : mapBuckwalter(elem);
  }

  public void setup(File path, String... options) {}

  //Whether or not the encoding of this word can be converted to another encoding
  //from its current encoding (Buckwalter or UTF-8)
  public boolean canChangeEncoding(String parent, String element) {
    parent = parent.trim();
    element = element.trim();

    //Hack for LDC2008E22 idiosyncrasy
    //This is NUMERIC_COMMA in the raw trees. We allow conversion of this
    //token to UTF-8 since it would appear in this encoding in arbitrary
    //UTF-8 text input
    if(parent.contains("NUMERIC_COMMA") || (parent.contains("PUNC") && element.equals("r"))) //Numeric comma
      return true;

    Matcher numMatcher = hasNum.matcher(element);
    if(numMatcher.find() || parentTagsToEscape.contains(parent))
      return false;

    return true;
  }

}
