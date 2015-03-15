package edu.stanford.nlp.ie;

import edu.stanford.nlp.ie.regexp.NumberSequenceClassifier;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.tokensregex.Env;
import edu.stanford.nlp.ling.tokensregex.TokenSequenceMatcher;
import edu.stanford.nlp.ling.tokensregex.TokenSequencePattern;
import edu.stanford.nlp.pipeline.ChunkAnnotationUtils;
import edu.stanford.nlp.pipeline.CoreMapAggregator;
import edu.stanford.nlp.pipeline.CoreMapAttributeAggregator;
import edu.stanford.nlp.util.*;

import java.math.BigDecimal;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Provides functions for converting words to numbers
 * Unlike QuantifiableEntityNormalizer that normalizes various
 *   types of quantifiable entities like money and dates,
 * NumberNormalizer only normalizes numeric expressions
 *   (e.g. one => 1, two hundred => 200.0 )
 *
 * <br>
 * This code is somewhat hacked together, so should be reworked.
 *
 * <br>
 * There is a library in perl for parsing english numbers:
 * http://blog.cordiner.net/2010/01/02/parsing-english-numbers-with-perl/
 *
 * <p>
 * TODO: To be merged into QuantifiableEntityNormalizer.
 *        It can be used by QuantifiableEntityNormalizer
 *        to first convert numbers expressed as words
 *        into numeric quantities before figuring
 *        out how to do higher level combos
 *        (like one hundred dollars and five cents)
 * <br>
 * TODO: Known to not handle the following:
 *       oh: two oh one
 *       non-integers: one and a half, one point five, three fifth
 *       funky numbers: pi
 * <br>
 * TODO: This class is very language dependent
 *        Should really be AmericanEnglishNumberNormalizer
 * <br>
 * TODO: Make things not static
 *
 * @author Angel Chang
 */
public class NumberNormalizer {

  private NumberNormalizer() {} // static class

  private static final Logger logger = Logger.getLogger(NumberNormalizer.class.getName());
  // TODO: make this not static, let different NumberNormalizers use
  // different loggers
  public static void setVerbose(boolean verbose) {
    if (verbose) {
      logger.setLevel(Level.FINE);
    } else {
      logger.setLevel(Level.SEVERE);
    }
  }

  // Need these in order - first must come after 21st
  //public static final Pattern teOrdinalWords = Pattern.compile("(?i)(tenth|eleventh|twelfth|thirteenth|fourteenth|fifteenth|sixteenth|seventeenth|eighteenth|nineteenth|twentieth|twenty-first|twenty-second|twenty-third|twenty-fourth|twenty-fifth|twenty-sixth|twenty-seventh|twenty-eighth|twenty-ninth|thirtieth|thirty-first|first|second|third|fourth|fifth|sixth|seventh|eighth|ninth)");
  //static final Pattern teNumOrds = Pattern.compile("(?i)([23]?1-?st|11-?th|[23]?2-?nd|12-?th|[12]?3-?rd|13-?th|[12]?[4-90]-?th|30-?th)");
  //static final Pattern unitNumsPattern = Pattern.compile("(?i)(one|two|three|four|five|six|seven|eight|nine)");
  //static final Pattern uniqueNumsPattern  = Pattern.compile("(?i)(ten|eleven|twelve|thirteen|fourteen|fifteen|sixteen|seventeen|eighteen|nineteen)");
  //static final Pattern tensNumsPattern = Pattern.compile("(?i)(twenty|thirty|forty|fifty|sixty|seventy|eighty|ninety)");
  private static final Pattern numUnitPattern = Pattern.compile("(?i)(hundred|thousand|million|billion|trillion)");
  private static final Pattern numEndUnitPattern = Pattern.compile("(?i)(gross|dozen|score)");

  /***********************/

  private static final Pattern numberTermPattern = Pattern.compile("(?i)(zero|one|two|three|four|five|six|seven|eight|nine|ten|eleven|twelve|thirteen|fourteen|fifteen|sixteen|seventeen|eighteen|nineteen|twenty|thirty|forty|fifty|sixty|seventy|eighty|ninety|hundred|thousand|million|billion|trillion|first|second|third|fourth|fifth|sixth|seventh|eighth|ninth|tenth|eleventh|twelfth|thirteenth|fourteenth|fifteenth|sixteenth|seventeenth|eighteenth|nineteenth|twentieth|thirtieth|fortieth|fiftieth|sixtieth|seventieth|eightieth|ninetieth|hundred?th|thousandth|millionth|billionth|trillionth)");
  private static final Pattern numberTermPattern2 = Pattern.compile("(?i)(" + numberTermPattern.pattern() + "(-" + numberTermPattern.pattern() + ")?)");
  private static final Pattern ordinalUnitPattern = Pattern.compile("(?i)(hundredth|thousandth|millionth)");

  // private static final String[] unitWords = {"trillion", "billion", "million", "thousand", "hundred"};
  // private static final String[] endUnitWords = {"gross", "dozen", "score"};

  // Converts numbers in words to numeric form
  // works through trillions
  protected static final Pattern digitsPattern = Pattern.compile("\\d+");
  private static final Pattern numPattern = Pattern.compile("[-+]?(?:\\d+(?:,\\d\\d\\d)*(?:\\.\\d*)?|\\.\\d+)");
  private static final Pattern numRangePattern = Pattern.compile("(" + numPattern.pattern() + ")-(" + numPattern.pattern() + ")");
  // private static final Pattern[] endUnitWordsPattern = new Pattern[endUnitWords.length];
  // private static final Pattern[] unitWordsPattern = new Pattern[unitWords.length];
  // static {
  //   int i = 0;
  //   for (String uw:endUnitWords) {
  //     endUnitWordsPattern[i] = Pattern.compile("(.*)\\s*" + Pattern.quote(uw) + "\\s*(.*)");
  //     i++;
  //   }
  //   int ii = 0;
  //   for (String uw:unitWords) {
  //     unitWordsPattern[ii] = Pattern.compile("(.*)\\s*" + Pattern.quote(uw) + "\\s*(.*)");
  //     ii++;
  //   }
  // }

  // TODO: similar to QuantifiableEntityNormalizer.wordsToValues
  //       QuantifiableEntityNormalizer also has bn (for billion)
  //       should consolidate
  //       here we use Number representation instead of double...
  private static final  Map<String,Number> word2NumMap = Generics.newHashMap();
  static
  {
    // Special words for numbers
    word2NumMap.put("dozen", 12);
    word2NumMap.put("score", 20);
    word2NumMap.put("gross", 144);
    word2NumMap.put("quarter", 0.25);
    word2NumMap.put("half", 0.5);
    word2NumMap.put("oh", 0);
    word2NumMap.put("a"  ,  1);
    word2NumMap.put("an"  ,  1);

    // Standard words for numbers
    word2NumMap.put("zero", 0);
    word2NumMap.put("one", 1);
    word2NumMap.put("two",  2);
    word2NumMap.put("three",  3);
    word2NumMap.put("four", 4);
    word2NumMap.put("five",  5);
    word2NumMap.put("six",  6);
    word2NumMap.put("seven", 7);
    word2NumMap.put("eight",  8);
    word2NumMap.put("nine",  9);
    word2NumMap.put("ten", 10);
    word2NumMap.put("eleven", 11);
    word2NumMap.put("twelve",  12);
    word2NumMap.put("thirteen", 13);
    word2NumMap.put("fourteen", 14);
    word2NumMap.put("fifteen",  15);
    word2NumMap.put("sixteen", 16);
    word2NumMap.put("seventeen", 17);
    word2NumMap.put("eighteen",  18);
    word2NumMap.put("nineteen", 19);
    word2NumMap.put("twenty", 20);
    word2NumMap.put("thirty",  30);
    word2NumMap.put("forty", 40);
    word2NumMap.put("fifty", 50);
    word2NumMap.put("sixty",  60);
    word2NumMap.put("seventy", 70);
    word2NumMap.put("eighty", 80);
    word2NumMap.put("ninety",  90);
    word2NumMap.put("hundred", 100);
    word2NumMap.put("thousand", 1000);
    word2NumMap.put("million",  1000000);
    word2NumMap.put("billion", 1000000000);
    word2NumMap.put("trillion", 1000000000000L);
  }

  // similar to QuantifiableEntityNormalizer.ordinalsToValues
  private static final Map<String,Number> ordWord2NumMap = Generics.newHashMap();
  static {
    ordWord2NumMap.put("zeroth", 0);
    ordWord2NumMap.put("first", 1);
    ordWord2NumMap.put("second", 2);
    ordWord2NumMap.put("third", 3);
    ordWord2NumMap.put("fourth", 4);
    ordWord2NumMap.put("fifth", 5);
    ordWord2NumMap.put("sixth", 6);
    ordWord2NumMap.put("seventh", 7);
    ordWord2NumMap.put("eighth", 8);
    ordWord2NumMap.put("ninth", 9);
    ordWord2NumMap.put("tenth", 10);
    ordWord2NumMap.put("eleventh", 11);
    ordWord2NumMap.put("twelfth", 12);
    ordWord2NumMap.put("thirteenth", 13);
    ordWord2NumMap.put("fourteenth", 14);
    ordWord2NumMap.put("fifteenth", 15);
    ordWord2NumMap.put("sixteenth", 16);
    ordWord2NumMap.put("seventeenth", 17);
    ordWord2NumMap.put("eighteenth", 18);
    ordWord2NumMap.put("nineteenth", 19);
    ordWord2NumMap.put("twentieth", 20);
    ordWord2NumMap.put("thirtieth", 30);
    ordWord2NumMap.put("fortieth", 40);
    ordWord2NumMap.put("fiftieth", 50);
    ordWord2NumMap.put("sixtieth", 60);
    ordWord2NumMap.put("seventieth", 70);
    ordWord2NumMap.put("eightieth", 80);
    ordWord2NumMap.put("ninetieth", 90);
    ordWord2NumMap.put("hundredth", 100);
    ordWord2NumMap.put("hundreth", 100); // really a spelling error
    ordWord2NumMap.put("thousandth", 1000);
    ordWord2NumMap.put("millionth", 1000000);
    ordWord2NumMap.put("billionth", 1000000000);
    ordWord2NumMap.put("trillionth", 1000000000000L);
  }

  // Seems to work better than quantifiable entity normalizer's numeric conversion
  private static final Pattern alphaPattern = Pattern.compile("([a-zA-Z]+)");
  private static final Pattern wsPattern = Pattern.compile("\\s+");

  /**
   * Fairly generous utility function to convert a string representing
   * a number (hopefully) to a Number.
   * Assumes that something else has somehow determined that the string
   * makes ONE suitable number.
   * The value of the number is determined by:
   * 0. Breaking up the string into pieces using whitespace
   *    (stuff like "and", "-", "," is turned into whitespace);
   * 1. Determining the numeric value of the pieces;
   * 2. Finding the numeric value of each piece;
   * 3. Combining the pieces together to form the overall value:
   *    a. Find the largest component and its value (X),
   *    b. Let B = overall value of pieces to the left (recursive),
   *    c. Let C = overall value of pieces to the right recursive),
   *    d. The overall value = B*X + C.
   *
   * @param str The String to convert
   * @return numeric value of string
   */
  public static Number wordToNumber(String str){
    if (str.trim().equals("")) {
      return null;
    }

    boolean neg = false;

    String originalString = str;

    // Trims and lowercases stuff
    str = str.trim();
    str = str.toLowerCase();

    if (str.startsWith("-")) {
      neg = true;
    }

    // eliminate hyphens, commas, and the word "and"
    str = str.replaceAll("\\band\\b", " ");
    str = str.replaceAll("-", " ");
    str = str.replaceAll("(\\d),(\\d)", "$1$2");  // Maybe something like 4,233,000 ??
    str = str.replaceAll(",", " ");
//    str = str.replaceAll("(\\d)(\\w)","$1 $2");

    // Trims again (do we need this?)
    str = str.trim();

    // TODO: error checking....
    //if string starts with "a ", as in "a hundred", replace it with "one"
    if (str.startsWith("a ")) {
      str = str.replace("a", "one");
    }

    // cut off some trailing s
    if (str.endsWith("sands")) {
      // thousands
      str = str.substring(0, str.length() - 1);
    } else if (str.endsWith("ions")) {
      // millions, billions, etc
      str = str.substring(0, str.length() - 1);
    }

    // now count words
    String[] fields = wsPattern.split(str);
    Number[] numFields = new Number[fields.length];
    int numWords = fields.length;

    // get numeric value of each word piece
    for (int curIndex = 0; curIndex < numWords; curIndex++) {
      String curPart = fields[curIndex];
      Matcher m = alphaPattern.matcher(curPart);
      if (m.find()) {
        // Some part of the word has alpha characters
        Number curNum;
        if (word2NumMap.containsKey(curPart)) {
          curNum = word2NumMap.get(curPart);
        } else if (ordWord2NumMap.containsKey(curPart)) {
          if (curIndex == numWords-1){
            curNum = ordWord2NumMap.get(curPart);
          } else {
            throw new NumberFormatException("Error in wordToNumber function.");
          }
        } else if (curIndex > 0 && (curPart.endsWith("ths") || curPart.endsWith("rds"))) {
          // Fractions?
          curNum = ordWord2NumMap.get(curPart.substring(0, curPart.length()-1));
          if (curNum != null) {
            curNum = 1/curNum.doubleValue();
          } else {
            throw new NumberFormatException("Bad number put into wordToNumber.  Word is: \"" + curPart + "\", originally part of \"" + originalString + "\", piece # " + curIndex);
          }
        } else if (Character.isDigit(curPart.charAt(0))) {
          if (curPart.endsWith("th") || curPart.endsWith("rd") || curPart.endsWith("nd") || curPart.endsWith("st")) {
            curPart = curPart.substring(0, curPart.length()-2);
          }
          if (digitsPattern.matcher(curPart).matches()) {
            curNum = Long.parseLong(curPart);
          } else{
            throw new NumberFormatException("Bad number put into wordToNumber.  Word is: \"" + curPart + "\", originally part of \"" + originalString + "\", piece # " + curIndex);
          }
        } else {
          throw new NumberFormatException("Bad number put into wordToNumber.  Word is: \"" + curPart + "\", originally part of \"" + originalString + "\", piece # " + curIndex);
        }
        numFields[curIndex] = curNum;
      } else {
        // Word is all numeric
        if (digitsPattern.matcher(curPart).matches()) {
          numFields[curIndex] = Long.parseLong(curPart);
        } else if (numPattern.matcher(curPart).matches()) {
          numFields[curIndex] = new BigDecimal(curPart);
        } else {
          // Hmm, strange number
          throw new NumberFormatException("Bad number put into wordToNumber.  Word is: \"" + curPart + "\", originally part of \"" + originalString + "\", piece # " + curIndex);
        }
      }
    }
    Number n = wordToNumberRecurse(numFields);
    return (neg)? -n.doubleValue():n;
  }

  private static Number wordToNumberRecurse(Number[] numFields)
  {
    return wordToNumberRecurse(numFields, 0, numFields.length);
  }

  private static Number wordToNumberRecurse(Number[] numFields, int start, int end)
  {
    // return solitary number
    if (end <= start) return 0;
    if (end - start == 1) {
      return numFields[start];
    }

    // first, find highest number in string
    Number highestNum = Double.NEGATIVE_INFINITY;
    int highestNumIndex = start;
    for (int i = start; i < end; i++) {
      Number curNum = numFields[i];
      if (curNum != null && curNum.doubleValue() >= highestNum.doubleValue()){
        highestNum = curNum;
        highestNumIndex = i;
      }
    }

    Number beforeNum = 1;
    if (highestNumIndex > start) {
      beforeNum = wordToNumberRecurse(numFields, start, highestNumIndex);
      if (beforeNum == null) beforeNum = 1;
    }
    Number afterNum = wordToNumberRecurse(numFields, highestNumIndex+1, end);
    if (afterNum == null) afterNum = 0;

    // TODO: Everything is treated as double... losing precision information here
    //       Sufficient for now
    //       Should we usually use BigDecimal to do our calculations?
    //       There are also fractions to consider.
    Number evaluatedNumber = ((beforeNum.doubleValue() * highestNum.doubleValue()) + afterNum.doubleValue());
    return evaluatedNumber;
  }

  public static Env getNewEnv()
  {
    Env env = TokenSequencePattern.getNewEnv();

    // Do case insensitive matching
    env.setDefaultStringPatternFlags(Pattern.CASE_INSENSITIVE);

    initEnv(env);
    return env;
  }

  public static void initEnv(Env env)
  {
    // Custom binding for numeric values expressions
    env.bind("numtype", CoreAnnotations.NumericTypeAnnotation.class);
    env.bind("numvalue", CoreAnnotations.NumericValueAnnotation.class);
    env.bind("numcomptype", CoreAnnotations.NumericCompositeTypeAnnotation.class);
    env.bind("numcompvalue", CoreAnnotations.NumericCompositeValueAnnotation.class);
    env.bind("$NUMCOMPTERM", " [ { numcomptype::EXISTS } & !{ numcomptype:NUMBER_RANGE } ] ");
    env.bind("$NUMTERM", " [ { numtype::EXISTS } & !{ numtype:NUMBER_RANGE } ] ");
    env.bind("$NUMRANGE", " [ { numtype:NUMBER_RANGE } ] ");
    // TODO: Improve code to only recognize integers
    env.bind("$INTTERM", " [ { numtype::EXISTS } & !{ numtype:NUMBER_RANGE } & !{ word:/.*\\.\\d+.*/} ] ");
    env.bind("$POSINTTERM", " [ { numvalue>0 } & !{ word:/.*\\.\\d+.*/} ] ");
    env.bind("$ORDTERM", " [ { numtype:ORDINAL } ] ");
    env.bind("$BEFORE_WS", " [ { before:/\\s*/ } | !{ before::EXISTS} ]");
    env.bind("$AFTER_WS", " [ { after:/\\s*/ } | !{ after::EXISTS} ]");
    env.bind("$BEFORE_AFTER_WS", " [ $BEFORE_WS & $AFTER_WS ]");
  }

  private static final Env env = getNewEnv();

  private static final TokenSequencePattern numberPattern = TokenSequencePattern.compile(
          env, "$NUMTERM ( [/,/ & $BEFORE_WS]? [$POSINTTERM & $BEFORE_WS]  )* ( [/,/ & $BEFORE_WS]? [/and/ & $BEFORE_WS] [$POSINTTERM & $BEFORE_WS]+ )? ");
  /**
   * Find and mark numbers (does not need NumberSequenceClassifier)
   * Each token is annotated with the numeric value and type
   * - CoreAnnotations.NumericTypeAnnotation.class: ORDINAL, UNIT (hundred, thousand,..., dozen, gross,...), NUMBER
   * - CoreAnnotations.NumericValueAnnotation.class: Number representing the numeric value of the token
   *   ( two thousand => 2 1000 )
   *
   * Tries also to separate individual numbers like four five six,
   *   while keeping numbers like four hundred and seven together
   * Annotate tokens belonging to each composite number with
   * - CoreAnnotations.NumericCompositeTypeAnnotation.class: ORDINAL (1st, 2nd), NUMBER (one hundred)
   * - CoreAnnotations.NumericCompositeValueAnnotation.class: Number representing the composite numeric value
   *   ( two thousand => 2000 2000 )
   *
   * Also returns list of CoreMap representing the identified numbers
   *
   * The function is overly aggressive in marking possible numbers
   *  - should either do more checks or use in conjunction with NumberSequenceClassifier
   *    to avoid marking certain tokens (like second/NN) as numbers...
   *
   * @param annotation The annotation structure
   * @return list of CoreMap representing the identified numbers
   */
  public static List<CoreMap> findNumbers(CoreMap annotation)
  {
    List<CoreLabel> tokens = annotation.get(CoreAnnotations.TokensAnnotation.class);
    for (CoreLabel token:tokens) {
      String w = token.word();
      w = w.trim().toLowerCase();

      if (/*("CD".equals(token.get(CoreAnnotations.PartOfSpeechAnnotation.class))  || */
           NumberNormalizer.numPattern.matcher(w).matches() || NumberNormalizer.numberTermPattern2.matcher(w).matches() ||
              NumberSequenceClassifier.ORDINAL_PATTERN.matcher(w).matches() || NumberNormalizer.numEndUnitPattern.matcher(w).matches()) {
        // TODO: first ADVERB and second NN shouldn't be marked as ordinals
        // But maybe we don't care, this can just mark the potential numbers, something else can disregard those
        try {
          token.set(CoreAnnotations.NumericValueAnnotation.class, NumberNormalizer.wordToNumber(w));
          if (NumberSequenceClassifier.ORDINAL_PATTERN.matcher(w).find()) {
            token.set(CoreAnnotations.NumericTypeAnnotation.class, "ORDINAL");
          } else if (NumberNormalizer.numUnitPattern.matcher(w).matches()) {
            token.set(CoreAnnotations.NumericTypeAnnotation.class, "UNIT");
          } else if (NumberNormalizer.numEndUnitPattern.matcher(w).matches()) {
            token.set(CoreAnnotations.NumericTypeAnnotation.class, "UNIT");
          } else {
            token.set(CoreAnnotations.NumericTypeAnnotation.class, "NUMBER");
          }
        } catch (Exception ex) {
          logger.warning("Error interpreting number " + w + ": " + ex.getMessage());
        }
      }
    }
    // TODO: Should we allow "," in written out numbers?
    // TODO: Handle "-" that is not with token?
    TokenSequenceMatcher matcher = numberPattern.getMatcher(tokens);
    List<CoreMap> numbers = new ArrayList<CoreMap>();
    while (matcher.find()) {
      @SuppressWarnings("unused")
      List<CoreMap> matchedTokens = matcher.groupNodes();
      int numStart = matcher.start();
      int possibleNumEnd = -1;
      int lastUnitPos = -1;
      int possibleNumStart = -1;
      Number possibleNumEndUnit = null;
      Number lastUnit = null;
      // Check if we need to split matched chunk up more
      for (int i = matcher.start(); i < matcher.end(); i++) {
        CoreLabel token = tokens.get(i);
        CoreLabel prev = (i > matcher.start())? tokens.get(i - 1): null;
        Number num = token.get(CoreAnnotations.NumericValueAnnotation.class);
        Number prevNum = (prev != null)? prev.get(CoreAnnotations.NumericValueAnnotation.class):null;
        String w = token.word();
        w = w.trim().toLowerCase();
        if (",".equals(w)) {
          if (lastUnit != null && lastUnitPos == i-1) {
            // OKAY, this may be one big number
            possibleNumEnd = i;
            possibleNumEndUnit = lastUnit;
          } else {
            // Not one big number
            if (numStart < i) {
              numbers.add(ChunkAnnotationUtils.getAnnotatedChunk(annotation, numStart, i));
              numStart = i+1;
              possibleNumEnd = -1;
              possibleNumEndUnit = null;
              lastUnit = null;
              lastUnitPos = -1;
            }
          }
          if (numStart == i) {
            numStart = i+1;
          }
        } else if ("and".equals(w)) {
          // Check if number before and was unit
          String prevWord = prev.word();
          if (lastUnitPos == i-1 || (lastUnitPos == i-2 && ",".equals(prevWord))) {
            // Okay
          } else {
            // Two separate numbers
            if (numStart < possibleNumEnd) {
              numbers.add(ChunkAnnotationUtils.getAnnotatedChunk(annotation, numStart, possibleNumEnd));
              if (possibleNumStart >= possibleNumEnd) {
                numStart = possibleNumStart;
              } else {
                numStart = i+1;
              }
            } else if (numStart < i) {
              numbers.add(ChunkAnnotationUtils.getAnnotatedChunk(annotation, numStart, i));
              numStart = i+1;
            }
            if (lastUnitPos < numStart) {
              lastUnit = null;
              lastUnitPos = -1;
            }
            possibleNumEnd = -1;
            possibleNumEndUnit = null;
          }
        } else {
          // NUMBER or ORDINAL
          String numType = token.get(CoreAnnotations.NumericTypeAnnotation.class);
          if ("UNIT".equals(numType)) {
            // Compare this unit with previous
            if (lastUnit == null || lastUnit.longValue() > num.longValue()) {
              // lastUnit larger than this unit
              // maybe four thousand two hundred?
              // OKAY, probably one big number
            } else {
              if (numStart < possibleNumEnd) {
                // Units are increasing - check if this unit is >= unit before "," (if so, need to split into chunks)
                // Not one big number  ( had a comma )
                if (num.longValue() >= possibleNumEndUnit.longValue()) {
                  numbers.add(ChunkAnnotationUtils.getAnnotatedChunk(annotation, numStart, possibleNumEnd));
                  if (possibleNumStart >= possibleNumEnd) {
                    numStart = possibleNumStart;
                  } else {
                    numStart = i;
                  }
                  possibleNumEnd = -1;
                  possibleNumEndUnit = null;
                }
              } else {
                // unit is increasing - can be okay, maybe five hundred thousand?
                // what about four hundred five thousand
                // unit might also be the same, as in thousand thousand,
                // which we convert to million
              }
            }
            lastUnit = num;
            lastUnitPos = i;
          } else {
            // Normal number
            if (num == null) {
              logger.warning("NO NUMBER: " + token.word());
              continue;
            }
            if (prevNum != null) {
              if (num.doubleValue() > 0) {
                if (num.doubleValue() < 10) {
                  // This number is a digit
                  // Treat following as two separate numbers
                  //    \d+ [0-9]
                  //    [one to nine]  [0-9]
                  if (NumberNormalizer.numPattern.matcher(prev.word()).matches() ||
                          prevNum.longValue() < 10 || prevNum.longValue() % 10 != 0 ) {
                    // two separate numbers
                    if (numStart < i) {
                      numbers.add(ChunkAnnotationUtils.getAnnotatedChunk(annotation, numStart, i));
                    }
                    numStart = i;
                    possibleNumEnd = -1;
                    possibleNumEndUnit = null;
                    lastUnit = null;
                    lastUnitPos = -1;
                  }
                } else {
                  String prevNumType = prev.get(CoreAnnotations.NumericTypeAnnotation.class);
                  if ("UNIT".equals(prevNumType)) {
                    // OKAY
                  } else if (!ordinalUnitPattern.matcher(w).matches()) {
                    // Start of new number
                    if (numStart < i) {
                      numbers.add(ChunkAnnotationUtils.getAnnotatedChunk(annotation, numStart, i));
                    }
                    numStart = i;
                    possibleNumEnd = -1;
                    possibleNumEndUnit = null;
                    lastUnit = null;
                    lastUnitPos = -1;
                  }
                }
              }
            }
            if ("ORDINAL".equals(numType)) {
              if (possibleNumEnd >= 0) {
                if (numStart < possibleNumEnd) {
                  numbers.add(ChunkAnnotationUtils.getAnnotatedChunk(annotation, numStart, possibleNumEnd));
                }
                if (possibleNumStart > possibleNumEnd) {
                  numbers.add(ChunkAnnotationUtils.getAnnotatedChunk(annotation, possibleNumStart, i+1));
                } else {
                  numbers.add(ChunkAnnotationUtils.getAnnotatedChunk(annotation, possibleNumEnd+1, i+1));
                }
              } else {
                if (numStart < i+1) {
                  numbers.add(ChunkAnnotationUtils.getAnnotatedChunk(annotation, numStart, i+1));
                }
              }
              numStart = i+1;
              possibleNumEnd = -1;
              possibleNumEndUnit = null;
              lastUnit = null;
              lastUnitPos = -1;
            }
            if (possibleNumStart < possibleNumEnd) {
              possibleNumStart = i;
            }
          }
        }
      }
      if (numStart < matcher.end()) {
        numbers.add(ChunkAnnotationUtils.getAnnotatedChunk(annotation, numStart, matcher.end()));
      }
    }
    for (CoreMap n:numbers) {
      String exp = n.get(CoreAnnotations.TextAnnotation.class);
      List<CoreLabel> ts = n.get(CoreAnnotations.TokensAnnotation.class);
      String label = ts.get(ts.size() - 1).get(CoreAnnotations.NumericTypeAnnotation.class);
      if ("UNIT".equals(label)) {
        label = "NUMBER";
      }
      try {
        Number num = NumberNormalizer.wordToNumber(exp);
        if (num == null) {
          logger.warning("NO NUMBER FOR: \"" + exp + "\"");
        }
        n.set(CoreAnnotations.NumericCompositeValueAnnotation.class, num);
        n.set(CoreAnnotations.NumericCompositeTypeAnnotation.class, label);
        for (CoreLabel t:ts) {
          t.set(CoreAnnotations.NumericCompositeValueAnnotation.class, num);
          t.set(CoreAnnotations.NumericCompositeTypeAnnotation.class, label);
        }
      } catch (NumberFormatException ex) {
        logger.log(Level.WARNING, "Invalid number for: \"" + exp + "\"", ex);
      }
    }
    return numbers;
  }

  /**
   * Find and mark number ranges
   * Ranges are NUM1 [-|to] NUM2 where NUM2 > NUM1
   *
   * Each number range is marked with
   * - CoreAnnotations.NumericTypeAnnotation.class: NUMBER_RANGE
   * - CoreAnnotations.NumericObjectAnnotation.class: {@code Pair<Number>} representing the start/end of the range
   *
   * @param annotation - annotation where numbers have already been identified
   * @return list of CoreMap representing the identified number ranges
   */
  private static final TokenSequencePattern rangePattern = TokenSequencePattern.compile(env, "(?:$NUMCOMPTERM /-|to/ $NUMCOMPTERM) | $NUMRANGE");
  public static List<CoreMap> findNumberRanges(CoreMap annotation)
  {
    List<CoreMap> numerizedTokens = annotation.get(CoreAnnotations.NumerizedTokensAnnotation.class);
    for (CoreMap token:numerizedTokens) {
      String w = token.get(CoreAnnotations.TextAnnotation.class);
      w = w.trim().toLowerCase();
      Matcher rangeMatcher = NumberNormalizer.numRangePattern.matcher(w);
      if (rangeMatcher.matches()) {
        try {
          String w1 = rangeMatcher.group(1);
          String w2 = rangeMatcher.group(2);
          Number v1 = NumberNormalizer.wordToNumber(w1);
          Number v2 = NumberNormalizer.wordToNumber(w2);
          if (v2.doubleValue() > v1.doubleValue()) {
            token.set(CoreAnnotations.NumericTypeAnnotation.class, "NUMBER_RANGE");
            token.set(CoreAnnotations.NumericCompositeTypeAnnotation.class, "NUMBER_RANGE");
            Pair<Number,Number> range = new Pair<Number,Number>(v1,v2);
            token.set(CoreAnnotations.NumericCompositeObjectAnnotation.class, range);
          }
        } catch (Exception ex) {
          logger.warning("Error interpreting number range " + w + ": " + ex.getMessage());
        }
      }
    }
    List<CoreMap> numberRanges = new ArrayList<CoreMap>();
    TokenSequenceMatcher matcher = rangePattern.getMatcher(numerizedTokens);
    while (matcher.find()) {
      List<CoreMap> matched = matcher.groupNodes();
      if (matched.size() == 1) {
        numberRanges.add(matched.get(0));
      } else {
        Number v1 = matched.get(0).get(CoreAnnotations.NumericCompositeValueAnnotation.class);
        Number v2 = matched.get(matched.size()-1).get(CoreAnnotations.NumericCompositeValueAnnotation.class);
        if (v2.doubleValue() > v1.doubleValue()) {
          CoreMap newChunk = ChunkAnnotationUtils.getMergedChunk(numerizedTokens,  matcher.start(), matcher.end(),
                  CoreMapAttributeAggregator.getDefaultAggregators());
          newChunk.set(CoreAnnotations.NumericCompositeTypeAnnotation.class, "NUMBER_RANGE");
          Pair<Number,Number> range = new Pair<Number,Number>(v1,v2);
          newChunk.set(CoreAnnotations.NumericCompositeObjectAnnotation.class, range);
          numberRanges.add(newChunk);
        }
      }
    }
    return numberRanges;
  }

  /**
   * Takes annotation and identifies numbers in the annotation
   * Returns a list of tokens (as CoreMaps) with numbers merged
   * As by product, also marks each individual token with the TokenBeginAnnotation and TokenEndAnnotation
   * - this is mainly to make it easier to the rest of the code to figure out what the token offsets are.
   *
   * Note that this copies the annotation, since it modifies token offsets in the original
   * @param annotationRaw The annotation to find numbers in
   * @return list of CoreMap representing the identified numbers
   */
  public static List<CoreMap> findAndMergeNumbers(CoreMap annotationRaw){
    //copy annotation to preserve its integrity
    CoreMap annotation = new ArrayCoreMap(annotationRaw);
    // Find and label numbers
    List<CoreMap> numbers = NumberNormalizer.findNumbers(annotation);
    CoreMapAggregator numberAggregator = CoreMapAggregator.getAggregator(CoreMapAttributeAggregator.DEFAULT_NUMERIC_AGGREGATORS, CoreAnnotations.TokensAnnotation.class);

    // We are going to mark the token begin and token end for each token so we can more easily deal with
    // ensuring correct token offsets for merging
    //get sentence offset
    Integer startTokenOffset = annotation.get(CoreAnnotations.TokenBeginAnnotation.class);
    if (startTokenOffset == null) {
      startTokenOffset = 0;
    }
    //set token offsets
    int i = 0;
    List<Integer> savedTokenBegins = new LinkedList<Integer>();
    List<Integer> savedTokenEnds = new LinkedList<Integer>();
    for (CoreMap c:annotation.get(CoreAnnotations.TokensAnnotation.class)) {
      //set token begin
      if( (i==0 && c.get(CoreAnnotations.TokenBeginAnnotation.class) != null) || (i > 0 && !savedTokenBegins.isEmpty()) ){
        savedTokenBegins.add(c.get(CoreAnnotations.TokenBeginAnnotation.class));
      }
      c.set(CoreAnnotations.TokenBeginAnnotation.class, i+startTokenOffset);
      i++;
      //set token end
      if( (i==1 && c.get(CoreAnnotations.TokenEndAnnotation.class) != null) || (i > 1 && !savedTokenEnds.isEmpty()) ){
        savedTokenEnds.add(c.get(CoreAnnotations.TokenEndAnnotation.class));
      }
      c.set(CoreAnnotations.TokenEndAnnotation.class, i+startTokenOffset);
    }
    //merge numbers
    final Integer startTokenOffsetFinal = startTokenOffset;
    List<CoreMap> mergedNumbers = numberAggregator.merge(annotation.get(CoreAnnotations.TokensAnnotation.class), numbers,
          new Function<CoreMap, Interval<Integer>>() {
            @Override
            public Interval<Integer> apply(CoreMap in) {
              return Interval.toInterval(
                    in.get(CoreAnnotations.TokenBeginAnnotation.class) - startTokenOffsetFinal,
                    in.get(CoreAnnotations.TokenEndAnnotation.class) - startTokenOffsetFinal);
            }
          });
    //restore token offsets
    if (!savedTokenBegins.isEmpty() && !savedTokenEnds.isEmpty()) {
      for (CoreMap c : mergedNumbers) {
        // get new indices
        int newBegin = c.get(CoreAnnotations.TokenBeginAnnotation.class) - startTokenOffset;
        int newEnd = c.get(CoreAnnotations.TokenEndAnnotation.class) - startTokenOffset;
        // get token offsets for those indices
        c.set(CoreAnnotations.TokenBeginAnnotation.class, savedTokenBegins.get(newBegin));
        c.set(CoreAnnotations.TokenEndAnnotation.class, savedTokenEnds.get(newEnd-1));
      }
    }
    //return
    return mergedNumbers;
  }

  public static List<CoreMap> findAndAnnotateNumericExpressions(CoreMap annotation)
  {
    List<CoreMap> mergedNumbers = NumberNormalizer.findAndMergeNumbers(annotation);
    annotation.set(CoreAnnotations.NumerizedTokensAnnotation.class, mergedNumbers);
    return mergedNumbers;
  }

  public static List<CoreMap> findAndAnnotateNumericExpressionsWithRanges(CoreMap annotation)
  {
    Integer startTokenOffset = annotation.get(CoreAnnotations.TokenBeginAnnotation.class);
    if (startTokenOffset == null) {
      startTokenOffset = 0;
    }
    List<CoreMap> mergedNumbers = NumberNormalizer.findAndMergeNumbers(annotation);
    annotation.set(CoreAnnotations.NumerizedTokensAnnotation.class, mergedNumbers);
    // Find and label number ranges
    List<CoreMap> numberRanges = NumberNormalizer.findNumberRanges(annotation);
    final Integer startTokenOffsetFinal = startTokenOffset;
    List<CoreMap> mergedNumbersWithRanges = CollectionUtils.mergeListWithSortedMatchedPreAggregated(
            annotation.get(CoreAnnotations.NumerizedTokensAnnotation.class), numberRanges,
          new Function<CoreMap, Interval<Integer>>() {
            @Override
            public Interval<Integer> apply(CoreMap in) {
              return Interval.toInterval(
                    in.get(CoreAnnotations.TokenBeginAnnotation.class) - startTokenOffsetFinal,
                    in.get(CoreAnnotations.TokenEndAnnotation.class) - startTokenOffsetFinal);
            }
          });
    annotation.set(CoreAnnotations.NumerizedTokensAnnotation.class, mergedNumbersWithRanges);
    return mergedNumbersWithRanges;
  }

}
