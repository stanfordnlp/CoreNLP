package edu.stanford.nlp.ie;

import edu.stanford.nlp.ie.regexp.ChineseNumberSequenceClassifier;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.sequences.SeqClassifierFlags;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.Generics;
import edu.stanford.nlp.util.StringUtils;
import edu.stanford.nlp.util.logging.Redwood;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.lang.System.err;

/**
 * A Chinese correspondence of the {@link QuantifiableEntityNormalizer} that normalizes NUMBER, DATE, TIME,
 * MONEY, PERCENT and ORDINAL amounts expressed in Chinese.
 *
 * Note that this class is originally designed for the Chinese KBP Challenge, so it only
 * supports minimal functionalities. This needs to be completed in the future.
 *
 * @author Yuhao Zhang
 * @author Peng Qi
 */
public class ChineseQuantifiableEntityNormalizer {

  private static Redwood.RedwoodChannels log = Redwood.channels(ChineseQuantifiableEntityNormalizer.class);

  private static final boolean DEBUG = false;

  public static String BACKGROUND_SYMBOL = SeqClassifierFlags.DEFAULT_BACKGROUND_SYMBOL;

  private static final Set<String> quantifiable;  //Entity types that are quantifiable
  private static final ClassicCounter<String> wordsToValues;
  private static final ClassicCounter<String> quantityUnitToValues;
  private static final Map<String, Character> multiCharCurrencyWords; // used by money
  private static final Map<String, Character> oneCharCurrencyWords; // used by money

  private static final Map<String, String> fullDigitToHalfDigit;

  private static final Map<String, Integer> yearModifiers;
  private static final Map<String, Integer> monthDayModifiers;

  private static final String LITERAL_DECIMAL_POINT = "点";

  // Patterns we need
  // TODO (yuhao): here we are not considering 1) negative numbers, 2) Chinese traditional characters
  private static final Pattern ARABIC_NUMBERS_PATTERN = Pattern.compile("[-+]?\\d*\\.?\\d+");
  // This is the all-literal-number-characters sequence, excluding unit characters like 十 or 万
  private static final Pattern CHINESE_LITERAL_NUMBER_SEQUENCE_PATTERN = Pattern.compile("[一二三四五六七八九零〇]+");
  // The decimal part of a float number should be exactly literal number sequence without units
  private static final Pattern CHINESE_LITERAL_DECIMAL_PATTERN = CHINESE_LITERAL_NUMBER_SEQUENCE_PATTERN;

  // Used by quantity modifiers
  private static final String greaterEqualThreeWords = "(?:大|多|高)于或者?等于";
  private static final String lessEqualThreeWords = "(?:小|少|低)于或者?等于";

  private static final String greaterEqualTwoWords = "(?:大|多)于等于|不(?:少|小|低)于";
  private static final String lessEqualTwoWords = "(?:小|少)于等于|不(?:大|少|高)于|不超过";
  private static final String approxTwoWords = "大(?:概|约|致)(?:是|为)|大概其";

  private static final String greaterThanOneWord = "(?:大|多|高)于|(?:超|高|多)过";;
  private static final String lessThanOneWord = "(?:小|少|低)于|不(?:到|够|足)";
  private static final String approxOneWord = "大(?:约|概|致)|接?近|差不多|几乎|左右|上下|约(?:为|略)";

  // All the tags we need
  private static final String NUMBER_TAG = "NUMBER";
  private static final String DATE_TAG = "DATE";
  private static final String TIME_TAG = "TIME";
  private static final String MONEY_TAG = "MONEY";
  private static final String ORDINAL_TAG = "ORDINAL";
  private static final String PERCENT_TAG = "PERCENT";

  // static initialization of useful properties
  static {
    quantifiable = Generics.newHashSet();
    quantifiable.add(NUMBER_TAG);
    quantifiable.add(DATE_TAG);
    quantifiable.add(TIME_TAG);
    quantifiable.add(MONEY_TAG);
    quantifiable.add(PERCENT_TAG);
    quantifiable.add(ORDINAL_TAG);

    quantityUnitToValues = new ClassicCounter<>();
    quantityUnitToValues.setCount("十", 10.0);
    quantityUnitToValues.setCount("百", 100.0);
    quantityUnitToValues.setCount("千", 1000.0);
    quantityUnitToValues.setCount("万", 10000.0);
    quantityUnitToValues.setCount("亿", 100000000.0);

    wordsToValues = new ClassicCounter<>();
    wordsToValues.setCount("零", 0.0);
    wordsToValues.setCount("〇", 0.0);
    wordsToValues.setCount("一", 1.0);
    wordsToValues.setCount("二", 2.0);
    wordsToValues.setCount("两", 2.0);
    wordsToValues.setCount("三", 3.0);
    wordsToValues.setCount("四", 4.0);
    wordsToValues.setCount("五", 5.0);
    wordsToValues.setCount("六", 6.0);
    wordsToValues.setCount("七", 7.0);
    wordsToValues.setCount("八", 8.0);
    wordsToValues.setCount("九", 9.0);
    wordsToValues.addAll(quantityUnitToValues); // all units are also quantifiable individually

    multiCharCurrencyWords = Generics.newHashMap();
    multiCharCurrencyWords.put("美元", '$');
    multiCharCurrencyWords.put("美分", '$');
    multiCharCurrencyWords.put("英镑", '£');
    multiCharCurrencyWords.put("先令", '£');
    multiCharCurrencyWords.put("便士", '£');
    multiCharCurrencyWords.put("欧元", '€');
    multiCharCurrencyWords.put("日元", '¥');
    multiCharCurrencyWords.put("韩元", '₩');

    oneCharCurrencyWords = Generics.newHashMap();
    oneCharCurrencyWords.put("刀", '$');
    oneCharCurrencyWords.put("镑", '£');
    oneCharCurrencyWords.put("元", '元');   // We follow the tradition in English to use 元 instead of ¥ for RMB
    // For all other currency, we use default currency symbol $

    yearModifiers = Generics.newHashMap();
    yearModifiers.put("前", -2);
    yearModifiers.put("去", -1);
    yearModifiers.put("上", -1);
    yearModifiers.put("今", 0);
    yearModifiers.put("同", 0);
    yearModifiers.put("此", 0);
    yearModifiers.put("该", 0);
    yearModifiers.put("本", 0);
    yearModifiers.put("明", 1);
    yearModifiers.put("来", 1);
    yearModifiers.put("下", 1);
    yearModifiers.put("后", 2);

    monthDayModifiers = Generics.newHashMap();
    monthDayModifiers.put("昨", -1);
    monthDayModifiers.put("上", -1);
    monthDayModifiers.put("今", 0);
    monthDayModifiers.put("同", 0);
    monthDayModifiers.put("此", 0);
    monthDayModifiers.put("该", 0);
    monthDayModifiers.put("本", 0);
    monthDayModifiers.put("来", 1);
    monthDayModifiers.put("明", 1);
    monthDayModifiers.put("下", 1);

    fullDigitToHalfDigit = Generics.newHashMap();
    fullDigitToHalfDigit.put("１", "1");
    fullDigitToHalfDigit.put("２", "2");
    fullDigitToHalfDigit.put("３", "3");
    fullDigitToHalfDigit.put("４", "4");
    fullDigitToHalfDigit.put("５", "5");
    fullDigitToHalfDigit.put("６", "6");
    fullDigitToHalfDigit.put("７", "7");
    fullDigitToHalfDigit.put("８", "8");
    fullDigitToHalfDigit.put("９", "9");
    fullDigitToHalfDigit.put("０", "0");
  }

  // Patterns used by DATE and TIME (must be after the static initializers to make use of the modifiers)
  private static final String CHINESE_DATE_NUMERALS_PATTERN = "[一二三四五六七八九零十〇]";
  private static final String CHINESE_AND_ARABIC_NUMERALS_PATTERN = "[一二三四五六七八九零十〇\\d]";
  private static final String CHINESE_AND_ARABIC_NUMERALS_PATTERN_WO_TEN = "[一二三四五六七八九零〇\\d]";
  private static final String YEAR_MODIFIER_PATTERN = "[" + String.join("", yearModifiers.keySet()) + "]";
  private static final String MONTH_DAY_MODIFIER_PATTERN = "[" + String.join("", monthDayModifiers.keySet()) + "]";

  private static final String BASIC_DD_PATTERN = "("
          + CHINESE_AND_ARABIC_NUMERALS_PATTERN + "{1,3}|" + MONTH_DAY_MODIFIER_PATTERN + ")[日号&&[^年月]]?";
  private static final String BASIC_MMDD_PATTERN = "(" + CHINESE_AND_ARABIC_NUMERALS_PATTERN + "{1,2}|"
          + MONTH_DAY_MODIFIER_PATTERN + ")(?:月份?|\\-|/|\\.)(?:" + BASIC_DD_PATTERN + ")?";
  private static final String BASIC_YYYYMMDD_PATTERN = "(" + CHINESE_AND_ARABIC_NUMERALS_PATTERN_WO_TEN + "{2,4}|"
          + YEAR_MODIFIER_PATTERN + ")(?:年[份度]?|\\-|/|\\.)?" + "(?:" + BASIC_MMDD_PATTERN + ")?";
  private static final String ENGLISH_MMDDYYYY_PATTERN = "(\\d{1,2})[/\\-\\.](\\d{1,2})(?:[/\\-\\.](\\d{4}))?";

  private static final String RELATIVE_TIME_PATTERN = "([昨今明])[天晨晚夜早]";
  private static final String BIRTH_DECADE_PATTERN = "(" + CHINESE_AND_ARABIC_NUMERALS_PATTERN + "[0零〇5五])后";


  private ChineseQuantifiableEntityNormalizer() { } // static methods

  /**
   * Identifies contiguous MONEY, TIME, DATE, or PERCENT entities
   * and tags each of their constituents with a "normalizedQuantity"
   * label which contains the appropriate normalized string corresponding to
   * the full quantity.
   * Unlike the English normalizer, this method currently does not support
   * concatenation or SUTime.
   *
   * @param list A list of {@link CoreMap}s representing a single document.
   *             Note: We assume the NERs has been labelled and the labels
   *             will be updated in place.
   * @param document
   * @param sentence
   * @param <E>
   */
  public static <E extends CoreMap> void addNormalizedQuantitiesToEntities(List<E> list, CoreMap document, CoreMap sentence) {

    // Fix the NER sequence if necessay
    fixupNerBeforeNormalization(list);

    // Now that NER tags has been fixed up, we do another pass to add the normalization
    String prevNerTag = BACKGROUND_SYMBOL;
    int beforeIndex = -1;
    ArrayList<E> collector = new ArrayList<>();
    for (int i = 0, sz = list.size(); i <= sz; i++) {
      // we should always keep list.size() unchanged inside the loop
      E wi = null;
      String currNerTag = null;
      String nextWord = "";
      if(i < sz) {
        wi = list.get(i);
        if(DEBUG) {
          log.info("addNormalizedQuantitiesToEntities: wi=" + wi + ", collector=" + collector);
        }
        if(i+1 < sz) {
          nextWord = list.get(i+1).get(CoreAnnotations.TextAnnotation.class);
          if(nextWord == null) {
            nextWord = "";
          }
        }
        // We assume NERs have been set by previous NER taggers
        currNerTag = wi.get(CoreAnnotations.NamedEntityTagAnnotation.class);
        // TODO: may need to detect TIME modifier here?
      }
      E wprev = (i > 0) ? list.get(i-1) : null;
      // if the current wi is a non-continuation and the last one was a
      // quantity, we close and process the last segment.
      // TODO: also need to check compatibility as the English normalizer does
      if((currNerTag == null || !currNerTag.equals(prevNerTag)) && quantifiable.contains(prevNerTag)) {
        String modifier = null;
        // Need different handling for different tags
        switch (prevNerTag) {
          case TIME_TAG:
            // TODO: add TIME
            break;
          case DATE_TAG:
            processEntity(collector, prevNerTag, modifier, nextWord, document);
            break;
          default:
            if(prevNerTag.equals(NUMBER_TAG) || prevNerTag.equals(PERCENT_TAG) ||
                prevNerTag.equals(MONEY_TAG)) {
              // we are doing for prev tag so afterIndex should really be i
              modifier = detectQuantityModifier(list, beforeIndex, i);
            }
            processEntity(collector, prevNerTag, modifier, nextWord);
            break;
        }
        collector = new ArrayList<>();
      }

      // If currNerTag is quantifiable, we add it into collector
      if(quantifiable.contains(currNerTag)) {
        if(collector.isEmpty()) {
          beforeIndex = i - 1;
        }
        collector.add(wi);
      }
      // move on and update prev pointer
      prevNerTag = currNerTag;
    }
  }

  /**
   * Detect the quantity modifiers ahead of a numeric string. This method will look at three words ahead
   * and one word afterwards at most. Examples of modifiers are "大约", "多于".
   *
   * @param list
   * @param beforeIndex
   * @param afterIndex
   * @param <E>
   * @return
   */
  private static <E extends CoreMap> String detectQuantityModifier(List<E> list, int beforeIndex, int afterIndex) {
    String prev = (beforeIndex >= 0) ? list.get(beforeIndex).get(CoreAnnotations.TextAnnotation.class).toLowerCase(): "";
    String prev2 = (beforeIndex - 1 >= 0) ? list.get(beforeIndex - 1).get(CoreAnnotations.TextAnnotation.class).toLowerCase(): "";
    String prev3 = (beforeIndex - 2 >= 0) ? list.get(beforeIndex - 2).get(CoreAnnotations.TextAnnotation.class).toLowerCase(): "";
    int sz = list.size();
    String next = (afterIndex < sz) ? list.get(afterIndex).get(CoreAnnotations.TextAnnotation.class).toLowerCase(): "";

    if (DEBUG) {
      // output space for clarity
      log.info("Quantifiable modifiers: previous: " + prev3 + ' ' + prev2+ ' ' + prev);
      log.info("Quantifiable modifiers: next: " + next);
    }

    // Actually spaces won't be used for Chinese
    String longPrev = prev3 + prev2 + prev;
    if (longPrev.matches(lessEqualThreeWords)) { return "<="; }
    if (longPrev.matches(greaterEqualThreeWords)) { return ">="; }

    longPrev = prev2 + prev;
    if (longPrev.matches(greaterEqualTwoWords)) { return ">="; }
    if (longPrev.matches(lessEqualTwoWords)) { return "<="; }
    if (longPrev.matches(approxTwoWords)) { return "~"; }

    if (prev.matches(greaterThanOneWord)) { return ">"; }
    if (prev.matches(lessThanOneWord)) { return "<"; }
    if (prev.matches(approxOneWord)) { return "~"; }

    if (next.matches(approxOneWord)) { return "~"; }

    // As backup, we also check whether prev matches a two-word pattern, just in case the segmenter fails
    // This happens to <= or >= patterns sometime as observed.
    if (prev.matches(greaterEqualTwoWords)) { return ">="; }
    if (prev.matches(lessEqualTwoWords)) { return "<="; }

    // otherwise, not modifier detected and return null
    if (DEBUG) { err.println("Quantifiable: not a quantity modifier"); }
    return null;
  }

  private static <E extends CoreMap> List<E> processEntity(List<E> l,
           String entityType, String compModifier, String nextWord) {
    return processEntity(l, entityType, compModifier, nextWord, null);
  }

  /**
   * Process an entity given the NER tag, extracted modifier and the next word in the document.
   * The normalized quantity will be written in place.
   *
   * @param l A collector that collects annotations for the entity.
   * @param entityType Quantifiable NER tag.
   * @param compModifier The extracted modifier around the entity of interest. Different NER tags should
   *                    have different extraction rules.
   * @param nextWord Next word in the document.
   * @param document Reference to the document.
   * @param <E>
   * @return
   */
  private static <E extends CoreMap> List<E> processEntity(List<E> l,
            String entityType, String compModifier, String nextWord, CoreMap document) {
    if(DEBUG) {
      log.info("ChineseQuantifiableEntityNormalizer.processEntity: " + l);
    }
    // convert the entity annotations into a string
    String s = singleEntityToString(l);
    StringBuilder sb = new StringBuilder();
    // convert all full digits to half digits
    for (int i = 0, sz = s.length(); i < sz; i++) {
      String ch = s.substring(i, i+1);
      if (fullDigitToHalfDigit.containsKey(ch)) {
        ch = fullDigitToHalfDigit.get(ch);
      }
      sb.append(ch);
    }
    s = sb.toString();
    if(DEBUG) {
      log.info("Quantifiable: Processing entity string " + s);
    }
    String p = null;
    switch (entityType) {
      case NUMBER_TAG:
        p = "";
        if (compModifier != null) {
          p = compModifier;
        }
        String q = normalizedNumberString(s, nextWord, 1.0);
        if (q != null) {
          p = p.concat(q);
        } else {
          p = null;
        }
        break;
      case ORDINAL_TAG:
        // ordinal won't have modifier
        p = normalizedOrdinalString(s, nextWord);
        break;
      case PERCENT_TAG:
        p = normalizedPercentString(s, nextWord);
        break;
      case MONEY_TAG:
        p = "";
        if (compModifier != null) {
          p = compModifier;
        }
        q = normalizedMoneyString(s, nextWord);
        if (q != null) {
          p = p.concat(q);
        } else {
          p = null;
        }
        break;
      case DATE_TAG:
        if (s.matches(BASIC_YYYYMMDD_PATTERN) || s.matches(BASIC_MMDD_PATTERN)
                || s.matches(ENGLISH_MMDDYYYY_PATTERN) || s.matches(BASIC_DD_PATTERN)
                || s.matches(RELATIVE_TIME_PATTERN) || s.matches(BIRTH_DECADE_PATTERN)) {
          String docdate = document.get(CoreAnnotations.DocDateAnnotation.class);
          p = normalizeDateString(s, docdate);
        }
        break;
      case TIME_TAG:
        break;
    }
    if (DEBUG) {
      err.println("Quantifiable: Processed '" + s + "' as '" + p + '\'');
    }
    // Write the normalized NER values in place
    for (E wi : l) {
      if (p != null) {
        if (DEBUG) {
          log.info("Changing normalized NER from " + wi.get(CoreAnnotations.NormalizedNamedEntityTagAnnotation.class) + " to " + p);
        }
        wi.set(CoreAnnotations.NormalizedNamedEntityTagAnnotation.class, p);
      }
    }
    // This return value is not necessarily useful as the labelling is done in place.
    return l;
  }

  /**
   * Normalize a money string. A currency symbol will be added accordingly.
   * The assumption is that the money string will be clean enough: either lead by a currency sign (like $),
   * or trailed by a currency word. Otherwise we give up normalization.
   *
   * @param s
   * @param nextWord
   * @return
   */
  private static String normalizedMoneyString(String s, String nextWord) {
    if (DEBUG) {
      log.info("normalizedMoneyString: Normalizing " + s);
    }
    // default multiplier is 1
    double multiplier = 1.0;

    char currencySign = '$'; // by default we use $, following English
    boolean notMatched = true;
    // We check multiCharCurrencyWords first
    for (String currencyWord : multiCharCurrencyWords.keySet()) {
      if (notMatched && StringUtils.find(s, currencyWord)) {
        switch(currencyWord) {
          case "美分" :
            multiplier = 0.01;
            break;
          case "先令" :
            multiplier = 0.05;
            break;
          case "便士" :
            multiplier = 1.0/240;
            break;
        }
        s = s.replaceAll(currencyWord, "");
        currencySign = multiCharCurrencyWords.get(currencyWord);
        notMatched = false;
      }
    }
    // Then we check oneCharCurrencyWords
    if(notMatched) {
      for(String currencyWord : oneCharCurrencyWords.keySet()) {
        if(notMatched && StringUtils.find(s, currencyWord)) {
          // TODO: change multiplier
          s = s.replaceAll(currencyWord, "");
          currencySign = oneCharCurrencyWords.get(currencyWord);
          notMatched = false;
        }
      }
    }
    // We check all other currency cases if we miss both dictionaries above
    if(notMatched) {
      for(String currencyWord : ChineseNumberSequenceClassifier.CURRENCY_WORDS_VALUES) {
        if(notMatched && StringUtils.find(s, currencyWord)) {
          s = s.replaceAll(currencyWord, "");
          break;
        }
      }
    }

    // Now we assert the string should be all numbers
    String value = normalizedNumberString(s, nextWord, multiplier);
    if(value == null) {
      if(DEBUG) {
        log.info("normalizedMoneyString: Failed to parse number " + s);
      }
      return null;
    } else {
      return currencySign + value;
    }
  }

  /**
   * Normalize a percent string. We handle both % and ‰.
   *
   * @param s
   * @param nextWord
   * @return
   */
  private static String normalizedPercentString(String s, String nextWord) {
    String ns = "";
    if(s.startsWith("百分之")) {
      ns = normalizedNumberString(s.substring(3), nextWord, 1.0);
      if(ns != null) {
        ns += "%";
      }
    } else if (s.startsWith("千分之")) {
      ns = normalizedNumberString(s.substring(3), nextWord, 1.0);
      if(ns != null) {
        ns += "‰";
      }
    } else if (s.endsWith("%")) {
      // we also handle the case where the percent ends with a % character
      ns = normalizedNumberString(s.substring(0, s.length()-1), nextWord, 1.0);
      if(ns != null) {
        ns += "%";
      }
    } else if (s.endsWith("‰")) {
      ns = normalizedNumberString(s.substring(0, s.length()-1), nextWord, 1.0);
      ns += "‰";
    } else {
      // otherwise we assume the entire percent is a number
      ns = normalizedNumberString(s, nextWord, 1.0);
      if(ns != null) {
        ns += "%";
      }
    }
    return ns;
  }

  /**
   * Normalize an ordinal string.
   * If the string starts with "第", we assume the number is followed; otherwise
   * we assume the entire body is a number.
   *
   * @param s
   * @param nextWord
   * @return
   */
  private static String normalizedOrdinalString(String s, String nextWord) {
    if(s.startsWith("第")) {
      return normalizedNumberString(s.substring(1), nextWord, 1.0);
    } else {
      return normalizedNumberString(s, nextWord, 1.0);
    }
  }

  /**
   * Normalize a string into the corresponding standard numerical values (in String form).
   * Note that this can only handle a string of pure numerical expressions, like
   * "两万三千零七十二点五六" or "23072.56". Other NERs like MONEY or DATE needs to be handled
   * in their own methods.
   * In any case we fail, this method will just return a null.
   *
   * @param s The string input.
   * @param nextWord The next word in sequence. This is likely to be useless for Chinese.
   * @param multiplier A multiplier to make things simple for callers
   * @return
   */
  private static String normalizedNumberString(String s, String nextWord, double multiplier) {
    // First remove unnecessary characters in the string
    s = s.trim();
    s = s.replaceAll("[ \t\n\0\f\r,]", ""); // remove all unnecessary characters
    // In case of pure arabic numbers, return the straight value of it
    if(ARABIC_NUMBERS_PATTERN.matcher(s).matches()) {
      return prettyNumber(String.format("%f", multiplier * Double.valueOf(s)));
    }
    // If this is not all arabic, we assume it to be either Chinese literal or mix of Chinese literal and arabic
    // We handle decimal point first
    int decimalIndex = s.indexOf(LITERAL_DECIMAL_POINT);
    Double decimalValue = Double.valueOf(0);
    if(decimalIndex != -1) {
      // handle decimal part
      if(DEBUG) {
        log.info("Normalizing decimal part: " + s.substring(decimalIndex+1));
      }
      decimalValue = normalizeLiteralDecimalString(s.substring(decimalIndex+1));
      // if fails at parsing decimal value, return null
      if(decimalValue == null) {
        return null;
      }
      // update s to be the integer part
      s = s.substring(0, decimalIndex);
    }
    if(DEBUG) {
      log.info("Normalizing integer part: " + s);
    }
    Double integerValue = recurNormalizeLiteralIntegerString(s);
    if(integerValue == null) {
      return null;
    }
    // both decimal and integer part are parsable, we combine them to form the final result
    // the formatting of numbers in Java is really annoying
    return prettyNumber(String.format("%f", multiplier * Double.valueOf(integerValue.doubleValue() + decimalValue.doubleValue())));
  }

  /**
   * Recursively parse a integer String expressed in either Chinese or a mix of Chinese and arabic numbers.
   *
   * @param s
   * @return
   */
  private static Double recurNormalizeLiteralIntegerString(String s) {
    // If empty, return 0
    if(s.isEmpty()) {
      return Double.valueOf(0);
    }

    // TODO: check if it is valid. It is possible that this is a vague number like "五六十" which cannot be parsed by current implementation.

    // In case of pure arabic numbers, return the straight value of it
    if(ARABIC_NUMBERS_PATTERN.matcher(s).matches()) {
      return Double.valueOf(s);
    }
    //If s has more than 1 char and first char is 零 or 〇, it is likely
    // to be useless
    if(s.length() > 1 && (s.startsWith("零") || s.startsWith("〇"))) {
      s = s.substring(1);
    }
    //If there is only one char left and we can quantify it, we return the value of it
    if(s.length() == 1 && wordsToValues.containsKey(s)) {
      return Double.valueOf(wordsToValues.getCount(s));
    }

    // Now parse the integer, making use of the compositionality of Chinese literal numbers
    Double value;
    value = compositeAtUnitIfExists(s, "亿");
    if(value != null) {
      return value;
    } else {
      value = compositeAtUnitIfExists(s, "万");
    }
    if(value != null) {
      return value;
    } else {
      value = compositeAtUnitIfExists(s, "千");
    }
    if(value != null) {
      return value;
    } else {
      value = compositeAtUnitIfExists(s, "百");
    }
    if(value != null) {
      return value;
    } else {
      value = compositeAtUnitIfExists(s, "十");
    }
    if(value != null) {
      return value;
    }
    // otherwise we fail to parse and just return null
    return null;
  }

  /**
   * Check if a unit exists in the literal string. If so, parse it by making use of
   * the compositionality; otherwise return null.
   *
   * @param s
   * @param unit
   * @return
   */
  private static Double compositeAtUnitIfExists(String s, String unit) {
    // invalid unit
    if(!quantityUnitToValues.containsKey(unit)) {
      return null;
    }
    int idx = s.indexOf(unit);
    if(idx != -1) {
      Double first = Double.valueOf(1.0);
      // Here we need special handling for 十 and 百 when they occur as the first char
      // As in Chinese 十二 is very common, 百二十 is sometimes valid as well.
      if(("十".equals(unit) || "百".equals(unit)) && idx == 0) {
        // do nothing
      } else {
        // otherwise we try to parse the value before the unit
        first = recurNormalizeLiteralIntegerString(s.substring(0,idx));
      }
      Double second = recurNormalizeLiteralIntegerString(s.substring(idx+1));

      if(first != null && second != null) {
        return Double.valueOf(first.doubleValue() * quantityUnitToValues.getCount(unit) + second.doubleValue());
      }
    }
    // return null if unit is not present or fails to parse
    return null;
  }

  /**
   * Normalize decimal part of the string. Note that this only handles Chinese literal expressions.
   * @param s
   * @return
   */
  private static Double normalizeLiteralDecimalString(String s) {
    // if s is empty return 0
    if(s.isEmpty()) {
      return Double.valueOf(0);
    }
    // if s is not valid Chinese literal decimal expressions, return null
    if(!CHINESE_LITERAL_DECIMAL_PATTERN.matcher(s).matches()) {
      return null;
    }
    // after checking we assume the decimal part should be correct
    double decimalValue = 0;
    double base = 1;
    for(int i=0, sz=s.length(); i<sz; i++) {
      // update base
      base *= 0.1;
      String c = Character.toString(s.charAt(i));
      if(!wordsToValues.containsKey(c)) {
        // some uncatchable character is present, return null
        return null;
      }
      double v = wordsToValues.getCount(c);
      decimalValue += v * base;
    }
    return Double.valueOf(decimalValue);
  }

  private static String normalizeMonthOrDay(String s, String context) {
    int ctx = -1;
    if ( ! context.equals("XX"))
      ctx = Integer.parseInt(context);

    if (monthDayModifiers.containsKey(s)) {
      if (ctx >= 0)
        // todo: this is unsafe as it's not bound-checked for validity
        return String.format("%02d", ctx + monthDayModifiers.get(s));
      else
        return "XX";
    } else {
      String candidate;

      if (s == null) {
        return "XX";
      } else {

        if (s.matches(CHINESE_DATE_NUMERALS_PATTERN + "+")) {
          candidate = prettyNumber(String.format("%f", recurNormalizeLiteralIntegerString(s)));
        } else {
          candidate = s;
        }
      }

      if (candidate.length() < 2)
        candidate = "0" + candidate;

      return candidate;
    }
  }

  private static String normalizeYear(String s, String contextYear) {
    return normalizeYear(s, contextYear, false);
  }

  private static String normalizeYear(String s, String contextYear, boolean strict) {
    int ctx = -1;
    if (!contextYear.equals("XXXX"))
      ctx = Integer.parseInt(contextYear);

    if (yearModifiers.containsKey(s)) {
      if (ctx >= 0)
        return String.format("%d", ctx + yearModifiers.get(s));
      else
        return "XXXX";
    } else {
      String candidate;
      StringBuilder yearcandidate = new StringBuilder();
      for (int i = 0; i < s.length(); i++) {
        String t = String.valueOf(s.charAt(i));
        if (CHINESE_LITERAL_DECIMAL_PATTERN.matcher(t).matches()) {
          if (wordsToValues.containsKey(t)) {
            yearcandidate.append((int) wordsToValues.getCount(t));
          } else {
            // something unexpected happened
            return null;
          }
        } else {
          yearcandidate.append(t);
        }
      }

      candidate = yearcandidate.toString();
      if (candidate.length() != 2) {
        return candidate;
      }

      if (ctx < 0) {
        // use the current year as reference point for two digit year normalization by default
        ctx = Integer.parseInt(new SimpleDateFormat("yyyy").format(new Date()));
      }

      // note: this is a very crude heuristic for determining actual year from two digit expressions
      int cand = Integer.valueOf(candidate);

      if ((strict && cand >= (ctx % 100)) || cand > (ctx % 100 + 10)) {
        // referring to the previous century
        cand += (ctx / 100 - 1) * 100;
      } else {
        // referring to the same century
        cand += (ctx / 100) * 100;
      }

      return String.format("%d", cand);
    }
  }

  /**
   * Normalizes date strings.
   * @param s Input date string
   * @param ctxdate Context date (usually doc_date)
   * @return Normalized Timex expression of the input date string
     */
  public static String normalizeDateString(String s, String ctxdate) {
    // TODO [pengqi]: need to handle basic localization ("在七月二日到[八日]间")
    // TODO [pengqi]: need to handle literal numeral dates (usually used in events, e.g. "三一五" for 03-15)
    // TODO [pengqi]: might need to add a pattern for centuries ("上世纪90年代")?

    Pattern p;
    Matcher m;
    String ctxyear = "XXXX", ctxmonth = "XX", ctxday = "XX";

    // set up context date
    if (ctxdate != null) {
      p = Pattern.compile("^" + BASIC_YYYYMMDD_PATTERN + "$");
      m = p.matcher(ctxdate);

      if (m.find() && m.groupCount() == 3) {
        ctxyear = m.group(1);
        ctxmonth = m.group(2);
        ctxday = m.group(3);
      }
    }

    p = Pattern.compile("^" + BIRTH_DECADE_PATTERN + "$");
    m = p.matcher(s);

    if (m.find() && m.groupCount() == 1) {
      StringBuilder res = new StringBuilder();

      res.append(normalizeYear(m.group(1), ctxyear, true).substring(0, 3) + "X");
      res.append("-XX-XX");

      return res.toString();
    }

    p = Pattern.compile("^" + RELATIVE_TIME_PATTERN + "$");
    m = p.matcher(s);

    if (m.find() && m.groupCount() == 1) {
      StringBuilder res = new StringBuilder();

      res.append(ctxyear);
      res.append("-");
      res.append(ctxmonth);
      res.append("-");
      res.append(normalizeMonthOrDay(m.group(1), ctxday));

      return res.toString();
    }

    p = Pattern.compile("^" + BASIC_YYYYMMDD_PATTERN + "$");
    m = p.matcher(s);

    if (m.find() && m.groupCount() == 3) {
      StringBuilder res = new StringBuilder();

      res.append(normalizeYear(m.group(1), ctxyear));
      res.append("-");
      res.append(normalizeMonthOrDay(m.group(2), ctxmonth));
      res.append("-");
      res.append(normalizeMonthOrDay(m.group(3), ctxday));

      return res.toString();
    }

    p = Pattern.compile("^" + BASIC_MMDD_PATTERN + "$");
    m = p.matcher(s);

    if (m.find() && m.groupCount() == 2) {
      StringBuilder res = new StringBuilder();

      res.append(ctxyear);
      res.append("-");
      res.append(normalizeMonthOrDay(m.group(1), ctxmonth));
      res.append("-");
      res.append(normalizeMonthOrDay(m.group(2), ctxday));

      return res.toString();
    }

    p = Pattern.compile("^" + BASIC_DD_PATTERN + "$");
    m = p.matcher(s);

    if (m.find() && m.groupCount() == 1) {
      StringBuilder res = new StringBuilder();

      res.append(ctxyear);
      res.append("-");
      res.append(ctxmonth);
      res.append("-");
      res.append(normalizeMonthOrDay(m.group(1), ctxday));

      return res.toString();
    }

    p = Pattern.compile("^" + ENGLISH_MMDDYYYY_PATTERN + "$");
    m = p.matcher(s);

    if (m.find() && m.groupCount() == 3) {
      StringBuilder res = new StringBuilder();

      if (m.group(3) == null)
        res.append(ctxyear);
      else
        res.append(normalizeYear(m.group(3), ctxyear));
      res.append("-");
      res.append(normalizeMonthOrDay(m.group(1), ctxmonth));
      res.append("-");
      res.append(normalizeMonthOrDay(m.group(2), ctxday));

      return res.toString();
    }

    return s;
  }

  /**
   * Concatenate entity annotations to a String. Note that Chinese does not use space to separate
   * tokens so we will follow this convention here.
   *
   * @param l
   * @param <E>
   * @return
   */
  private static <E extends CoreMap> String singleEntityToString(List<E> l) {
    String entityType = l.get(0).get(CoreAnnotations.NamedEntityTagAnnotation.class);
    StringBuilder sb = new StringBuilder();
    for (E w : l) {
      if(!w.get(CoreAnnotations.NamedEntityTagAnnotation.class).equals(entityType)) {
        log.error("differing NER tags detected in entity: " + l);
        throw new Error("Error with entity construction, two tokens had inconsistent NER tags");
      }
      sb.append(w.get(CoreAnnotations.TextAnnotation.class));
    }
    return sb.toString();
  }

  private static String prettyNumber(String s) {
    if (s == null) {
      return null;
    }
    s = ! s.contains(".") ? s : s.replaceAll("0*$", "").replaceAll("\\.$", "");
    return s;
  }


  /**
   * Fix up the NER sequence in case this is necessary.
   *
   * @param list
   * @param <E>
   */
  private static <E extends CoreMap> void fixupNerBeforeNormalization(List<E> list) {
  }

}
