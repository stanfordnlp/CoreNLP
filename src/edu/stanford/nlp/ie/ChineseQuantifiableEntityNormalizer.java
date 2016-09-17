package edu.stanford.nlp.ie;

import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.sequences.SeqClassifierFlags;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.Generics;
import edu.stanford.nlp.util.logging.Redwood;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
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
 */
public class ChineseQuantifiableEntityNormalizer {

  private static Redwood.RedwoodChannels log = Redwood.channels(ChineseQuantifiableEntityNormalizer.class);

  private static final boolean DEBUG = true;

  public static String BACKGROUND_SYMBOL = SeqClassifierFlags.DEFAULT_BACKGROUND_SYMBOL;

  private static final Set<String> quantifiable;  //Entity types that are quantifiable
  private static final ClassicCounter<String> wordsToValues;
  private static final ClassicCounter<String> quantityUnitToValues;

  private static final String LITERAL_DECIMAL_POINT = "点";

  // Patterns we need
  // TODO: here we are not considering 1) negative numbers, 2) Chinese traditional characters
  private static final Pattern ARABIC_NUMBERS_PATTERN = Pattern.compile("[\\d\\.]+");
  private static final Pattern CHINESE_LITERAL_DECIMAL_PATTERN = Pattern.compile("[一二三四五六七八九零〇]+");

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

  }

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
   * @param <E>
   */
  public static <E extends CoreMap> void addNormalizedQuantitiesToEntities(List<E> list) {

    // Fix the NER sequence if necessay
    fixupNerBeforeNormalization(list);

    // Now that NER tags has been fixed up, we do another pass to add the normalization
    String prevNerTag = BACKGROUND_SYMBOL;
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
            // TODO: add DATE
            break;
          default:
            if(prevNerTag.equals(NUMBER_TAG) || prevNerTag.equals(PERCENT_TAG) ||
                prevNerTag.equals(MONEY_TAG)) {
              // TODO: look for modifiers like "大约", "多于"
            }
            processEntity(collector, prevNerTag, modifier, nextWord);
            break;
        }
        collector = new ArrayList<>();
      }

      // If currNerTag is quantifiable, we add it into collector
      if(quantifiable.contains(currNerTag)) {
        collector.add(wi);
      }
      // move on and update prev pointer
      prevNerTag = currNerTag;
    }
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
   * @param <E>
   * @return
   */
  private static <E extends CoreMap> List<E> processEntity(List<E> l,
            String entityType, String compModifier, String nextWord) {
    if(DEBUG) {
      log.info("ChineseQuantifiableEntityNormalizer.processEntity: " + l);
    }
    // convert the entity annotations into a string
    String s = singleEntityToString(l);
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
        String q = normalizedNumberString(s, nextWord);
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
        break;
      case DATE_TAG:
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
   * Normalize a percent string. We handle both % and ‰.
   *
   * @param s
   * @param nextWord
   * @return
   */
  private static String normalizedPercentString(String s, String nextWord) {
    String ns = "";
    if(s.startsWith("百分之")) {
      ns = normalizedNumberString(s.substring(3), nextWord);
      if(ns != null) {
        ns += "%";
      }
    } else if (s.startsWith("千分之")) {
      ns = normalizedNumberString(s.substring(3), nextWord);
      if(ns != null) {
        ns += "‰";
      }
    } else if (s.endsWith("%")) {
      // we also handle the case where the percent ends with a % character
      ns = normalizedNumberString(s.substring(0, s.length()-1), nextWord);
      if(ns != null) {
        ns += "%";
      }
    } else if (s.endsWith("‰")) {
      ns = normalizedNumberString(s.substring(0, s.length()-1), nextWord);
      ns += "‰";
    } else {
      // otherwise we assume the entire percent is a number
      ns = normalizedNumberString(s, nextWord);
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
      return normalizedNumberString(s.substring(1), nextWord);
    } else {
      return normalizedNumberString(s, nextWord);
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
   * @return
   */
  private static String normalizedNumberString(String s, String nextWord) {
    // First remove unnecessary characters in the string
    s = s.trim().replaceAll(" ", "");
    s = s.replaceAll(",", ""); // remove
    // In case of pure arabic numbers, return the straight value of it
    if(ARABIC_NUMBERS_PATTERN.matcher(s).matches()) {
      return prettyNumber(String.format("%f", Double.valueOf(s)));
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
    return prettyNumber(String.format("%f", Double.valueOf(integerValue.doubleValue() + decimalValue.doubleValue())));
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
      Double first = recurNormalizeLiteralIntegerString(s.substring(0,idx));
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

  /**
   * Concatenate entity annotations to a String. Note that Chinese does not use space to separate
   * tokens so we will follow this convention here.
   *
   * @param l
   * @param <E>
   * @return
   */
  public static <E extends CoreMap> String singleEntityToString(List<E> l) {
    String entityType = l.get(0).get(CoreAnnotations.NamedEntityTagAnnotation.class);
    StringBuilder sb = new StringBuilder();
    for (E w : l) {
      if(!w.get(CoreAnnotations.NamedEntityTagAnnotation.class).equals(entityType)) {
        log.fatal("Incontinuous NER tags detected in entity: " + l);
      }
      sb.append(w.get(CoreAnnotations.TextAnnotation.class));
    }
    return sb.toString();
  }

  public static String prettyNumber(String s) {
    if(s == null) {
      return null;
    }
    s = s.indexOf(".") < 0 ? s : s.replaceAll("0*$", "").replaceAll("\\.$", "");
    return s;
  }


  /**
   * Fix up the NER sequence in case this is necessary.
   *
   * @param list
   * @param <E>
   */
  public static <E extends CoreMap> void fixupNerBeforeNormalization(List<E> list) {
  }

}
