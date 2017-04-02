package edu.stanford.nlp.international.arabic.pipeline;

import java.io.File;
import java.io.Serializable;
import java.util.Arrays;
import java.util.Collections;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import edu.stanford.nlp.international.arabic.Buckwalter;
import edu.stanford.nlp.trees.treebank.Mapper;
import edu.stanford.nlp.trees.international.arabic.ATBTreeUtils;
import edu.stanford.nlp.util.Generics;

/**
 * Applies a default set of lexical transformations that have been empirically validated
 * in various Arabic tasks. This class automatically detects the input encoding and applies
 * the appropriate set of transformations.
 *
 * @author Spence Green
 *
 */
public class DefaultLexicalMapper implements Mapper, Serializable {

  private static final long serialVersionUID = -3798804368296999785L;

  private final Pattern utf8ArabicChart = Pattern.compile("[\u0600-\u06FF]");

  //Buckwalter patterns
  private final String bwAlefChar = "A"; //U+0627
  private final Pattern bwDiacritics = Pattern.compile("F|N|K|a|u|i|\\~|o");
  private final Pattern bwTatweel = Pattern.compile("_");
  private final Pattern bwAlef = Pattern.compile("\\{|\\||>|<");
  private final Pattern bwQuran = Pattern.compile("`");
  private final Pattern bwNullAnaphoraMarker = Pattern.compile("\\[nll\\]");

  public final Pattern latinPunc = Pattern.compile("([\u0021-\u002F\u003A-\u0040\\u005B-\u0060\u007B-\u007E\u00A1-\u00BF\u00F7\u2010-\u2027\u2030-\u205E\u20A0-\u20BA])+");
  public final Pattern arabicPunc = Pattern.compile("([\u00AB\u00BB\u0609-\u060D\u061B-\u061F\u066A\u066C-\u066D\u06D4])+");

  public final Pattern arabicDigit = Pattern.compile("([\u06F0-\u06F9\u0660-\u0669])+");

  //TODO Extend coverage to entire Arabic code chart
  //Obviously Buckwalter is a lossful conversion, but no assumptions should be made about
  //UTF-8 input from "the wild"
  private final Pattern utf8Diacritics = Pattern.compile("َ|ً|ُ|ٌ|ِ|ٍ|ّ|ْ|\u0670");
  private final Pattern utf8Tatweel = Pattern.compile("ـ");
  private final Pattern utf8Alef = Pattern.compile("ا|إ|أ|آ|\u0671");
  private final Pattern utf8Quran = Pattern.compile("[\u0615-\u061A\u06D6-\u06E5]");
  private final Pattern utf8ProDrop = Pattern.compile("\\[نلل\\]");

  //Patterns to fix segmentation issues observed in the ATB
  public final Pattern segmentationMarker = Pattern.compile("^-+|-+$");
  private final Pattern morphemeBoundary = Pattern.compile("\\+");

  private final Pattern hasDigit = Pattern.compile("\\d+");

  // Process the vocalized section for parsing
  private boolean useATBVocalizedSectionMapping = false;

  // Strip morpheme boundary markers in the vocalized section
  private boolean stripMorphemeMarkersInUTF8 = false;

  // Strip all morpheme and segmentation markers in UTF-8 Arabic
  private boolean stripSegmentationMarkersInUTF8 = false;

  //wsg: "LATIN" does not appear in the Bies tagset, so be sure to pass
  //in the extended POS tags during normalization
  private final String parentTagString = "PUNC LATIN -NONE-";
  private final Set<String> parentTagsToEscape;

  private final String utf8CliticString = "ل ف و ما ه ها هم هن نا كم تن تم ى ي هما ك ب م";
//  private final Set<String> utf8Clitics;
  private final Set<String> bwClitics;

  public DefaultLexicalMapper() {
    parentTagsToEscape =
      Collections.unmodifiableSet(Generics.newHashSet(Arrays.asList(parentTagString.split("\\s+"))));

//    utf8Clitics =
//      Collections.unmodifiableSet(Generics.newHashSet(Arrays.asList(utf8CliticString.split("\\s+"))));

    Buckwalter bw = new Buckwalter(true);
    String bwString = bw.apply(utf8CliticString);
    bwClitics =
      Collections.unmodifiableSet(Generics.newHashSet(Arrays.asList(bwString.split("\\s+"))));
  }

  private String mapUtf8(String element) {
    Matcher latinPuncOnly = latinPunc.matcher(element);
    Matcher arbPuncOnly = arabicPunc.matcher(element);
    if(latinPuncOnly.matches() || arbPuncOnly.matches()) return element;

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

    Matcher rmProDrop = utf8ProDrop.matcher(element);
    element = rmProDrop.replaceAll("");

    if (stripMorphemeMarkersInUTF8) {
      Matcher rmMorphemeBoundary = morphemeBoundary.matcher(element);
      String strippedElem = rmMorphemeBoundary.replaceAll("");
      if(strippedElem.length() > 0)
        element = strippedElem;
    }
    if (stripSegmentationMarkersInUTF8) {
      String strippedElem = segmentationMarker.matcher(element).replaceAll("");
      if(strippedElem.length() > 0)
        element = strippedElem;
    }

    return element;
  }

  private String mapBuckwalter(String element) {
    Matcher puncOnly = latinPunc.matcher(element);
    if(puncOnly.matches()) return element;

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

    Matcher rmProDrop = bwNullAnaphoraMarker.matcher(element);
    element = rmProDrop.replaceAll("");

    // This conditional is used for normalizing raw ATB trees
    // Morpheme boundaries are removed, and segmentation markers are retained on
    // segmented morphemes (not the tokens to which the morphemes were attached)
    if (useATBVocalizedSectionMapping && element.length() > 1) {
      Matcher rmMorphemeBoundary = morphemeBoundary.matcher(element);
      element = rmMorphemeBoundary.replaceAll("");

      //wsg: This is hairy due to tokens like this in the vocalized section:
      //        layos-+-a
      Matcher cliticMarker = segmentationMarker.matcher(element);
      if(cliticMarker.find() && !hasDigit.matcher(element).find()) {
        String strippedElem = cliticMarker.replaceAll("");
        if(strippedElem.length() > 0)
          element = bwClitics.contains(strippedElem) ? element : strippedElem;
      }

    } else if (element.length() > 1 && !ATBTreeUtils.reservedWords.contains(element)) {
      Matcher rmCliticMarker = segmentationMarker.matcher(element);
      element = rmCliticMarker.replaceAll("");
    }

    return element;
  }

  public String map(String parent, String element) {
    String elem = element.trim();

    if(parent != null && parentTagsToEscape.contains(parent))
      return elem;

    Matcher utf8Encoding = utf8ArabicChart.matcher(elem);
    return (utf8Encoding.find()) ? mapUtf8(elem) : mapBuckwalter(elem);
  }

  public void setup(File path, String... options) {
    if(options == null) return;

    for (final String opt : options) {
      switch (opt) {
        case "ATBVocalizedSection":
          useATBVocalizedSectionMapping = true;
          break;
        case "StripSegMarkersInUTF8":
          stripSegmentationMarkersInUTF8 = true;
          break;
        case "StripMorphMarkersInUTF8":
          stripMorphemeMarkersInUTF8 = true;
          break;
      }
    }
  }

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

    Matcher numMatcher = hasDigit.matcher(element);
    return !(numMatcher.find() || parentTagsToEscape.contains(parent));
  }

  public static void main(String[] args) {
    Mapper m = new DefaultLexicalMapper();

    System.out.printf("< :-> %s\n",m.map(null, "FNKqq"));
  }
}
