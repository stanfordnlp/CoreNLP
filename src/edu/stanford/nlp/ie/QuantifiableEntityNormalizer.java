package edu.stanford.nlp.ie;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import edu.stanford.nlp.ie.pascal.ISODateInstance;
import edu.stanford.nlp.ie.regexp.NumberSequenceClassifier;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.sequences.SeqClassifierFlags;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.time.TimeAnnotations;
import edu.stanford.nlp.time.Timex;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.EditDistance;
import edu.stanford.nlp.util.Generics;
import edu.stanford.nlp.util.Pair;
import edu.stanford.nlp.util.StringUtils;
import edu.stanford.nlp.util.logging.Redwood;


/**
 * Various methods for normalizing Money, Date, Percent, Time, and
 * Number, Ordinal amounts.
 * These matchers are generous in that they try to quantify something
 * that's already been labelled by an NER system; don't use them to make
 * classification decisions.  This class has a twin in the pipeline world:
 * {@link edu.stanford.nlp.pipeline.QuantifiableEntityNormalizingAnnotator}.
 * Please keep the substantive content here, however, so as to lessen code
 * duplication.
 *
 * <i>Implementation note:</i> The extensive test code for this class is
 * now in a separate JUnit Test class.  This class depends on the background
 * symbol for NER being the default background symbol.  This should be fixed
 * at some point.
 *
 * @author Chris Cox
 * @author Christopher Manning (extended for RTE)
 * @author Anna Rafferty
 */
public class QuantifiableEntityNormalizer  {

  /** A logger for this class */
  private static final Redwood.RedwoodChannels log = Redwood.channels(QuantifiableEntityNormalizer.class);

  private static final boolean DEBUG = false;
  private static final boolean DEBUG2 = false;  // String normalizing functions

  public static String BACKGROUND_SYMBOL = SeqClassifierFlags.DEFAULT_BACKGROUND_SYMBOL; // this isn't a constant; it's set by the QuantifiableEntityNormalizingAnnotator

  private static final Pattern timePattern = Pattern.compile("([0-2]?[0-9])((?::[0-5][0-9]){0,2})([PpAa]\\.?[Mm]\\.?)?");

  private static final Pattern moneyPattern = Pattern.compile("([$\u00A3\u00A5\u20AC#]?)(-?[0-9,]*)(\\.[0-9]*)?+");
  private static final Pattern scorePattern = Pattern.compile(" *([0-9]+) *- *([0-9]+) *");

  //Collections of entity types
  private static final Set<String> quantifiable;  //Entity types that are quantifiable
  private static final Set<String> collapseBeforeParsing;
  private static final Set<String> timeUnitWords;
  private static final Map<String, Double> moneyMultipliers;
  private static final Map<String, Integer> moneyMultipliers2;
  private static final Map<String, Character> currencyWords;
  public static final ClassicCounter<String> wordsToValues;
  public static final ClassicCounter<String> ordinalsToValues;

  static {

    quantifiable = Generics.newHashSet();
    quantifiable.add("MONEY");
    quantifiable.add("TIME");
    quantifiable.add("DATE");
    quantifiable.add("PERCENT");
    quantifiable.add("NUMBER");
    quantifiable.add("ORDINAL");
    quantifiable.add("DURATION");

    collapseBeforeParsing = Generics.newHashSet();
    collapseBeforeParsing.add("PERSON");
    collapseBeforeParsing.add("ORGANIZATION");
    collapseBeforeParsing.add("LOCATION");

    timeUnitWords = Generics.newHashSet();
    timeUnitWords.add("second");
    timeUnitWords.add("seconds");
    timeUnitWords.add("minute");
    timeUnitWords.add("minutes");
    timeUnitWords.add("hour");
    timeUnitWords.add("hours");
    timeUnitWords.add("day");
    timeUnitWords.add("days");
    timeUnitWords.add("week");
    timeUnitWords.add("weeks");
    timeUnitWords.add("month");
    timeUnitWords.add("months");
    timeUnitWords.add("year");
    timeUnitWords.add("years");

    currencyWords = Generics.newHashMap();
    currencyWords.put("dollars?", '$');
    currencyWords.put("cents?", '$');
    currencyWords.put("pounds?", '\u00A3');
    currencyWords.put("pence|penny", '\u00A3');
    currencyWords.put("yen", '\u00A5');
    currencyWords.put("euros?", '\u20AC');
    currencyWords.put("won", '\u20A9');
    currencyWords.put("\\$", '$');
    currencyWords.put("\u00A2", '$');  // cents
    currencyWords.put("\u00A3", '\u00A3');  // pounds
    currencyWords.put("#", '\u00A3');      // for Penn treebank
    currencyWords.put("\u00A5", '\u00A5');  // Yen
    currencyWords.put("\u20AC", '\u20AC');  // Euro
    currencyWords.put("\u20A9", '\u20A9');  // Won
    currencyWords.put("yuan", '\u5143');   // Yuan

    moneyMultipliers = Generics.newHashMap();
    moneyMultipliers.put("trillion", 1000000000000.0);  // can't be an integer
    moneyMultipliers.put("billion",1000000000.0);
    moneyMultipliers.put("bn",1000000000.0);
    moneyMultipliers.put("million", 1000000.0);
    moneyMultipliers.put("thousand", 1000.0);
    moneyMultipliers.put("hundred", 100.0);
    moneyMultipliers.put("b.", 1000000000.0);
    moneyMultipliers.put("m.", 1000000.0);
    moneyMultipliers.put(" m ",1000000.0);
    moneyMultipliers.put(" k ",1000.0);

    moneyMultipliers2 = Generics.newHashMap();
    moneyMultipliers2.put("[0-9](m)(?:[^a-zA-Z]|$)", 1000000);
    moneyMultipliers2.put("[0-9](b)(?:[^a-zA-Z]|$)", 1000000000);

    wordsToValues = new ClassicCounter<>();
    wordsToValues.setCount("zero", 0.0);
    wordsToValues.setCount("one", 1.0);
    wordsToValues.setCount("two", 2.0);
    wordsToValues.setCount("three", 3.0);
    wordsToValues.setCount("four", 4.0);
    wordsToValues.setCount("five", 5.0);
    wordsToValues.setCount("six", 6.0);
    wordsToValues.setCount("seven", 7.0);
    wordsToValues.setCount("eight", 8.0);
    wordsToValues.setCount("nine", 9.0);
    wordsToValues.setCount("ten", 10.0);
    wordsToValues.setCount("eleven", 11.0);
    wordsToValues.setCount("twelve", 12.0);
    wordsToValues.setCount("thirteen", 13.0);
    wordsToValues.setCount("fourteen", 14.0);
    wordsToValues.setCount("fifteen", 15.0);
    wordsToValues.setCount("sixteen", 16.0);
    wordsToValues.setCount("seventeen", 17.0);
    wordsToValues.setCount("eighteen", 18.0);
    wordsToValues.setCount("nineteen", 19.0);
    wordsToValues.setCount("twenty", 20.0);
    wordsToValues.setCount("thirty", 30.0);
    wordsToValues.setCount("forty", 40.0);
    wordsToValues.setCount("fifty", 50.0);
    wordsToValues.setCount("sixty", 60.0);
    wordsToValues.setCount("seventy", 70.0);
    wordsToValues.setCount("eighty", 80.0);
    wordsToValues.setCount("ninety", 90.0);
    wordsToValues.setCount("hundred", 100.0);
    wordsToValues.setCount("thousand", 1000.0);
    wordsToValues.setCount("million", 1000000.0);
    wordsToValues.setCount("billion", 1000000000.0);
    wordsToValues.setCount("bn", 1000000000.0);
    wordsToValues.setCount("trillion", 1000000000000.0);
    wordsToValues.setCount("dozen", 12.0);

    ordinalsToValues = new ClassicCounter<>();
    ordinalsToValues.setCount("zeroth", 0.0);
    ordinalsToValues.setCount("first", 1.0);
    ordinalsToValues.setCount("second", 2.0);
    ordinalsToValues.setCount("third", 3.0);
    ordinalsToValues.setCount("fourth", 4.0);
    ordinalsToValues.setCount("fifth", 5.0);
    ordinalsToValues.setCount("sixth", 6.0);
    ordinalsToValues.setCount("seventh", 7.0);
    ordinalsToValues.setCount("eighth", 8.0);
    ordinalsToValues.setCount("ninth", 9.0);
    ordinalsToValues.setCount("tenth", 10.0);
    ordinalsToValues.setCount("eleventh", 11.0);
    ordinalsToValues.setCount("twelfth", 12.0);
    ordinalsToValues.setCount("thirteenth", 13.0);
    ordinalsToValues.setCount("fourteenth", 14.0);
    ordinalsToValues.setCount("fifteenth", 15.0);
    ordinalsToValues.setCount("sixteenth", 16.0);
    ordinalsToValues.setCount("seventeenth", 17.0);
    ordinalsToValues.setCount("eighteenth", 18.0);
    ordinalsToValues.setCount("nineteenth", 19.0);
    ordinalsToValues.setCount("twentieth", 20.0);
    ordinalsToValues.setCount("twenty-first", 21.0);
    ordinalsToValues.setCount("twenty-second", 22.0);
    ordinalsToValues.setCount("twenty-third", 23.0);
    ordinalsToValues.setCount("twenty-fourth", 24.0);
    ordinalsToValues.setCount("twenty-fifth", 25.0);
    ordinalsToValues.setCount("twenty-sixth", 26.0);
    ordinalsToValues.setCount("twenty-seventh", 27.0);
    ordinalsToValues.setCount("twenty-eighth", 28.0);
    ordinalsToValues.setCount("twenty-ninth", 29.0);
    ordinalsToValues.setCount("thirtieth", 30.0);
    ordinalsToValues.setCount("thirty-first", 31.0);
    ordinalsToValues.setCount("fortieth", 40.0);
    ordinalsToValues.setCount("fiftieth", 50.0);
    ordinalsToValues.setCount("sixtieth", 60.0);
    ordinalsToValues.setCount("seventieth", 70.0);
    ordinalsToValues.setCount("eightieth", 80.0);
    ordinalsToValues.setCount("ninetieth", 90.0);
    ordinalsToValues.setCount("hundredth", 100.0);
    ordinalsToValues.setCount("thousandth", 1000.0);
    ordinalsToValues.setCount("millionth", 1000000.0);
    ordinalsToValues.setCount("billionth", 1000000000.0);
    ordinalsToValues.setCount("trillionth", 1000000000000.0);
  }

  private QuantifiableEntityNormalizer() {} // this is all static

  /**
   * This method returns the closest match in set such that the match
   * has more than three letters and differs from word only by one substitution,
   * deletion, or insertion.  If not match exists, returns null.
   */
  private static String getOneSubstitutionMatch(String word, Set<String> set) {
    // TODO (?) pass the EditDistance around more places to make this
    // more efficient.  May not really matter.
    EditDistance ed = new EditDistance();
    for (String cur : set) {
      if (isOneSubstitutionMatch(word, cur, ed)) {
        return cur;
      }
    }
    return null;
  }

  private static boolean isOneSubstitutionMatch(String word, String match,
                                                EditDistance ed) {
    if(word.equalsIgnoreCase(match))
      return true;
    if(match.length() > 3) {
      if(ed.score(word, match) <= 1)
        return true;
    }
    return false;
  }

  /** Convert the content of a List of CoreMaps to a single
   *  space-separated String.  This grabs stuff based on the get(CoreAnnotations.NamedEntityTagAnnotation.class) field.
   *  [CDM: Changed to look at NamedEntityTagAnnotation not AnswerClass Jun 2010, hoping that will fix a bug.]
   *
   *  @param l The List
   *  @return one string containing all words in the list, whitespace separated
   */
  private static <E extends CoreMap> String singleEntityToString(List<E> l) {
    String entityType = l.get(0).get(CoreAnnotations.NamedEntityTagAnnotation.class);
    StringBuilder sb = new StringBuilder();
    for (E w : l) {
      assert(w.get(CoreAnnotations.NamedEntityTagAnnotation.class).equals(entityType));
      sb.append(w.get(CoreAnnotations.TextAnnotation.class));
      sb.append(' ');
    }
    return sb.toString();
  }


  /**
   * Currently this populates a {@code List<CoreLabel>} with words from the passed List,
   * but NER entities are collapsed and {@link CoreLabel} constituents of entities have
   * NER information in their "quantity" fields.
   *
   * NOTE: This now seems to be used nowhere.  The collapsing is done elsewhere.
   * That's probably appropriate; it doesn't seem like this should be part of
   * QuantifiableEntityNormalizer, since it's set to collapse non-quantifiable
   * entities....
   *
   * @param l a list of CoreLabels with NER labels,
   * @return a Sentence where PERSON, ORG, LOC, entities are collapsed.
   */
  public static List<CoreLabel> collapseNERLabels(List<CoreLabel> l) {
    if(DEBUG) {
      for (CoreLabel w: l) {
        log.info("<<"+w.get(CoreAnnotations.TextAnnotation.class)+"::"+w.get(CoreAnnotations.PartOfSpeechAnnotation.class)+"::"+w.get(CoreAnnotations.NamedEntityTagAnnotation.class)+">>");
      }
    }

    List<CoreLabel> s = new ArrayList<>();
    String lastEntity = BACKGROUND_SYMBOL;
    StringBuilder entityStringCollector = null;

    //Iterate through each word....
    for (CoreLabel w: l) {
      String entityType = w.get(CoreAnnotations.NamedEntityTagAnnotation.class);
      //if we've just completed an entity and we're looking at a non-continuation,
      //we want to add that now.
      if (entityStringCollector != null && ! entityType.equals(lastEntity)) {
        CoreLabel nextWord = new CoreLabel();
        nextWord.setWord(entityStringCollector.toString());
        nextWord.set(CoreAnnotations.PartOfSpeechAnnotation.class, "NNP");
        nextWord.set(CoreAnnotations.NamedEntityTagAnnotation.class, lastEntity);
        s.add(nextWord);
        if (DEBUG) {
          log.info("Quantifiable: Collapsing " + entityStringCollector);
        }
        entityStringCollector = null;
      }
      //If its not to be collapsed, toss it onto the sentence.
      if ( ! collapseBeforeParsing.contains(entityType)) {
        s.add(w);
      } else { //If it is to be collapsed....
        //if its a continuation of the last entity, add it to the
        //current buffer.
        if (entityType.equals(lastEntity)){
          assert entityStringCollector != null;
          entityStringCollector.append('_');
          entityStringCollector.append(w.get(CoreAnnotations.TextAnnotation.class));
        } else {
          //and its NOT a continuation, make a new buffer.
          entityStringCollector = new StringBuilder();
          entityStringCollector.append(w.get(CoreAnnotations.TextAnnotation.class));
        }
      }
      lastEntity=entityType;
    }
    // if the last token was a named-entity, we add it here.
    if (entityStringCollector!=null) {
      CoreLabel nextWord = new CoreLabel();
      nextWord.setWord(entityStringCollector.toString());
      nextWord.set(CoreAnnotations.PartOfSpeechAnnotation.class, "NNP");
      nextWord.set(CoreAnnotations.NamedEntityTagAnnotation.class, lastEntity);
      s.add(nextWord);
    }
    for (CoreLabel w : s) {
      log.info("<<"+w.get(CoreAnnotations.TextAnnotation.class)+"::"+w.get(CoreAnnotations.PartOfSpeechAnnotation.class)+"::"+w.get(CoreAnnotations.NamedEntityTagAnnotation.class)+">>");
    }
    return s;
  }


  /**
   * Provided for backwards compatibility; see normalizedDateString(s, openRangeMarker)
   */
  static String normalizedDateString(String s, Timex timexFromSUTime) {
    return normalizedDateString(s, ISODateInstance.NO_RANGE, timexFromSUTime);
  }

  /**
   * Returns a string that represents either a single date or a range of
   * dates.  Representation pattern is roughly ISO8601, with some extensions
   * for greater expressivity; see {@link ISODateInstance} for details.
   *
   * @param s Date string to normalize
   * @param openRangeMarker a marker for whether this date is not involved in
   * an open range, is involved in an open range that goes forever backward and
   * stops at s, or is involved in an open range that goes forever forward and
   * starts at s.  See {@link ISODateInstance}.
   * @return A yyyymmdd format normalized date
   */
  private static String normalizedDateString(String s, String openRangeMarker, Timex timexFromSUTime) {
    if(timexFromSUTime != null) {
      if(timexFromSUTime.value() != null){
        // fully disambiguated temporal
        return timexFromSUTime.value();
      } else {
        // this is a relative date, e.g., "yesterday"
        return timexFromSUTime.altVal();
      }
    }

    ISODateInstance d = new ISODateInstance(s, openRangeMarker);
    if (DEBUG2) { log.info("normalizeDate: " + s + " to " + d.getDateString()); }
    return d.getDateString();
  }

  private static String normalizedDurationString(String s, Timex timexFromSUTime) {
    if(timexFromSUTime != null) {
      if(timexFromSUTime.value() != null){
        // fully disambiguated temporal
        return timexFromSUTime.value();
      } else {
        // something else
        return timexFromSUTime.altVal();
      }
    }
    // TODO: normalize duration ourselves
    return null;
  }

  /**
   * Tries to heuristically determine if the given word is a year
   */
  private static boolean isYear(CoreMap word) {
    String wordString = word.get(CoreAnnotations.TextAnnotation.class);
    if(word.get(CoreAnnotations.PartOfSpeechAnnotation.class) == null || word.get(CoreAnnotations.PartOfSpeechAnnotation.class).equals("CD")) {
      //one possibility: it's a two digit year with an apostrophe: '90
      if(wordString.length() == 3  && wordString.startsWith("'")) {
        if (DEBUG) {
          log.info("Found potential two digit year: " + wordString);
        }
        wordString = wordString.substring(1);
        try {
          Integer.parseInt(wordString);
          return true;
        } catch(Exception e) {
          return false;
        }
      }
      //if it is 4 digits, with first one <3 (usually we're not talking about
      //the far future, say it's a year
      if(wordString.length() == 4) {
        try {
          int num = Integer.parseInt(wordString);
          if(num < 3000)
            return true;
        } catch(Exception e) {
          return false;
        }
      }
    }
    return false;
  }


  private static final String dateRangeAfterOneWord = "after|since";
  private static final String dateRangeBeforeOneWord = "before|until";
  private static final List<Pair<String, String>> dateRangeBeforePairedOneWord;
  static {
    dateRangeBeforePairedOneWord = new ArrayList<>();
    dateRangeBeforePairedOneWord.add(new Pair<>("between", "and"));
    dateRangeBeforePairedOneWord.add(new Pair<>("from", "to"));
    dateRangeBeforePairedOneWord.add(new Pair<>("from", "-"));
  }

  private static final String datePrepositionAfterWord = "in|of";


  /**
   * Takes the strings of the one previous and 3 next words to a date to
   * detect date range modifiers like "before" or "between {@code <date>} and {@code <date>}.
   *
   * @param <E>
   */
  private static <E extends CoreMap> String detectDateRangeModifier(List<E> date, List<E> list, int beforeIndex, int afterIndex) {
    E prev = (beforeIndex >= 0) ? list.get(beforeIndex) : null;
    int sz = list.size();
    E next = (afterIndex < sz) ? list.get(afterIndex) : null;
    E next2 = (afterIndex + 1 < sz) ? list.get(afterIndex + 1) : null;
    E next3 = (afterIndex + 2 < sz) ? list.get(afterIndex + 2) : null;


    if (DEBUG) {
      log.info("DateRange: previous: " + prev);
      log.info("Quantifiable: next: " + next + ' ' + next2 + ' ' + next3);
    }

    //sometimes the year gets tagged as CD but not as a date - if this happens, we want to add it in
    if (next != null && isYear(next)) {
      date.add(next);
      next.set(CoreAnnotations.NamedEntityTagAnnotation.class, "DATE");
      afterIndex++;
    }
    if (next2 != null && isYear(next2)) {
      // This code here just seems wrong.... why are we marking next as a date without checking anything?
      date.add(next);
      assert(next != null); // keep the static analysis happy.
      next.set(CoreAnnotations.NamedEntityTagAnnotation.class, "DATE");
      date.add(next2);
      next2.set(CoreAnnotations.NamedEntityTagAnnotation.class, "DATE");
      afterIndex += 2;
    }

    //sometimes the date will be stated in a form like "June of 1984" -> we'd like this to be 198406
    if(next != null && next.get(CoreAnnotations.TextAnnotation.class).matches(datePrepositionAfterWord)) {
      //check if the next next word is a year or month
      if(next2 != null && (isYear(next2))) {//TODO: implement month!
        date.add(next);
        date.add(next2);
        afterIndex += 2;
      }
    }

    //String range = detectTwoSidedRangeModifier(date.get(0), list, beforeIndex, afterIndex);
    //if(range !=ISODateInstance.NO_RANGE) return range;
    //check if it's an open range - two sided ranges get checked elsewhere
    //based on the prev word
    if(prev != null) {
      String prevWord = prev.get(CoreAnnotations.TextAnnotation.class).toLowerCase();
      if(prevWord.matches(dateRangeBeforeOneWord)) {
        //we have an open range of the before type - e.g., Before June 6, John was 5
        prev.set(CoreAnnotations.PartOfSpeechAnnotation.class, "DATE_MOD");
        return ISODateInstance.OPEN_RANGE_BEFORE;
      } else if(prevWord.matches(dateRangeAfterOneWord)) {
        //we have an open range of the after type - e.g., After June 6, John was 6
        prev.set(CoreAnnotations.PartOfSpeechAnnotation.class, "DATE_MOD");
        return ISODateInstance.OPEN_RANGE_AFTER;
      }
    }


    return ISODateInstance.NO_RANGE;
  }

  // Version of above without any weird stuff
  private static <E extends CoreMap> String detectDateRangeModifier(E prev)
  {
    if(prev != null) {
      String prevWord = prev.get(CoreAnnotations.TextAnnotation.class).toLowerCase();
      if(prevWord.matches(dateRangeBeforeOneWord)) {
        //we have an open range of the before type - e.g., Before June 6, John was 5
        return ISODateInstance.OPEN_RANGE_BEFORE;
      } else if(prevWord.matches(dateRangeAfterOneWord)) {
        //we have an open range of the after type - e.g., After June 6, John was 6
        return ISODateInstance.OPEN_RANGE_AFTER;
      }
    }
    return ISODateInstance.NO_RANGE;
  }


  /**
   * This should detect things like "between 5 and 5 million" and "from April 3 to June 6"
   * Each side of the range is tagged with the correct numeric quantity (e.g., 5/5x10E6 or
   * ****0403/****0606) and the other words (e.g., "between", "and", "from", "to") are
   * tagged as quantmod to avoid penalizing them for lack of alignment/matches.
   *
   * This method should be called after other collapsing is complete (e.g. 5 million should already be
   * concatenated)
   * @param <E>
   */
  private static <E extends CoreMap> List<E> detectTwoSidedRangeModifier(E firstDate, List<E> list, int beforeIndex, int afterIndex, boolean concatenate) {
    E prev = (beforeIndex >= 0) ? list.get(beforeIndex) : null;
    //E cur = list.get(0);
    int sz = list.size();
    E next = (afterIndex < sz) ? list.get(afterIndex) : null;
    E next2 = (afterIndex + 1 < sz) ? list.get(afterIndex + 1) : null;
    List<E> toRemove = new ArrayList<>();

    String curNER = (firstDate == null ? "" : firstDate.get(CoreAnnotations.NamedEntityTagAnnotation.class));
    if(curNER == null) curNER = "";
    if(firstDate == null || firstDate.get(CoreAnnotations.NormalizedNamedEntityTagAnnotation.class) == null) return toRemove;
    //TODO: make ranges actually work
    //first check if it's of the form "between <date> and <date>"/etc

    if (prev != null) {
      for (Pair<String, String> ranges : dateRangeBeforePairedOneWord) {
        if (prev.get(CoreAnnotations.TextAnnotation.class).matches(ranges.first())) {
          if (next != null && next2 != null) {
            String nerNext2 = next2.get(CoreAnnotations.NamedEntityTagAnnotation.class);
            if (next.get(CoreAnnotations.TextAnnotation.class).matches(ranges.second()) && nerNext2 != null && nerNext2.equals(curNER)) {
              //Add rest in
              prev.set(CoreAnnotations.PartOfSpeechAnnotation.class, "QUANT_MOD");
              String rangeString;
              if(curNER.equals("DATE")) {
                ISODateInstance c = new ISODateInstance(new ISODateInstance(firstDate.get(CoreAnnotations.NormalizedNamedEntityTagAnnotation.class)),
                    new ISODateInstance(next2.get(CoreAnnotations.NormalizedNamedEntityTagAnnotation.class)));
                rangeString = c.getDateString();
              } else {
                rangeString = firstDate.get(CoreAnnotations.NormalizedNamedEntityTagAnnotation.class) + '-' + next2.get(CoreAnnotations.NormalizedNamedEntityTagAnnotation.class);
              }
              if (DEBUG) {
                log.info("#1: Changing normalized NER from " + firstDate.get(CoreAnnotations.NormalizedNamedEntityTagAnnotation.class) + " to " + rangeString + " at index " + beforeIndex);
              }
              firstDate.set(CoreAnnotations.NormalizedNamedEntityTagAnnotation.class, rangeString);
              if (DEBUG) {
                log.info("#2: Changing normalized NER from " + next2.get(CoreAnnotations.NormalizedNamedEntityTagAnnotation.class) + " to " + rangeString + " at index " + afterIndex);
              }
              next2.set(CoreAnnotations.NormalizedNamedEntityTagAnnotation.class, rangeString);
              next.set(CoreAnnotations.NamedEntityTagAnnotation.class, nerNext2);
              if (DEBUG) {
                log.info("#3: Changing normalized NER from " + next.get(CoreAnnotations.NormalizedNamedEntityTagAnnotation.class) + " to " + rangeString + " at index " + (afterIndex + 1));
              }
              next.set(CoreAnnotations.NormalizedNamedEntityTagAnnotation.class, rangeString);
              if (concatenate) {
                List<E> numberWords = new ArrayList<>();
                numberWords.add(firstDate);
                numberWords.add(next);
                numberWords.add(next2);
                concatenateNumericString(numberWords, toRemove);

              }
            }
          }
        }
      }
    }
    return toRemove;
  }

  /**
   * Concatenates separate words of a date or other numeric quantity into one node (e.g., 3 November -> 3_November)
   * Tag is CD or NNP, and other words are added to the remove list
   */
  private static <E extends CoreMap> void concatenateNumericString(List<E> words, List<E> toRemove) {
    if (words.size() <= 1) return;
    boolean first = true;
    StringBuilder newText = new StringBuilder();
    E foundEntity = null;
    for (E word : words) {
      if (foundEntity == null && (word.get(CoreAnnotations.PartOfSpeechAnnotation.class).equals("CD") || word.get(CoreAnnotations.PartOfSpeechAnnotation.class).equals("NNP"))) {
        foundEntity = word;
      }
      if (first) {
        first = false;
      } else {
        newText.append('_');
      }
      newText.append(word.get(CoreAnnotations.TextAnnotation.class));
    }
    if (foundEntity == null) {
      foundEntity = words.get(0);//if we didn't find one with the appropriate tag, just take the first one
    }
    toRemove.addAll(words);
    toRemove.remove(foundEntity);
    foundEntity.set(CoreAnnotations.PartOfSpeechAnnotation.class, "CD");  // cdm 2008: is this actually good for dates??
    String collapsed = newText.toString();
    foundEntity.set(CoreAnnotations.TextAnnotation.class, collapsed);
    foundEntity.set(CoreAnnotations.OriginalTextAnnotation.class, collapsed);
  }


  public static String normalizedTimeString(String s, Timex timexFromSUTime) {
    return normalizedTimeString(s, null, timexFromSUTime);
  }

  private static String normalizedTimeString(String s, String ampm, Timex timexFromSUTime) {
    if(timexFromSUTime != null){
      if(timexFromSUTime.value() != null){
        // this timex is fully disambiguated
        return timexFromSUTime.value();
      } else {
        // not disambiguated; contains some relative date
        return timexFromSUTime.altVal();
      }
    }

    if (DEBUG2) { log.info("normalizingTime: " + s); }
    s = s.replaceAll("[ \t\n\0\f\r]", "");
    Matcher m = timePattern.matcher(s);
    if (s.equalsIgnoreCase("noon")) {
      return "12:00pm";
    } else if (s.equalsIgnoreCase("midnight")) {
      return "00:00am";  // or "12:00am" ?
    } else if (s.equalsIgnoreCase("morning")) {
      return "M";
    } else if (s.equalsIgnoreCase("afternoon")) {
      return "A";
    } else if (s.equalsIgnoreCase("evening")) {
      return "EN";
    } else if (s.equalsIgnoreCase("night")) {
      return "N";
    } else if (s.equalsIgnoreCase("day")) {
      return "D";
    } else if (s.equalsIgnoreCase("suppertime")) {
      return "EN";
    } else if (s.equalsIgnoreCase("lunchtime")) {
      return "MD";
    } else if (s.equalsIgnoreCase("midday")) {
      return "MD";
    } else if (s.equalsIgnoreCase("teatime")) {
      return "A";
    } else if (s.equalsIgnoreCase("dinnertime")) {
      return "EN";
    } else if (s.equalsIgnoreCase("dawn")) {
      return "EM";
    } else if (s.equalsIgnoreCase("dusk")) {
      return "EN";
    } else if (s.equalsIgnoreCase("sundown")) {
      return "EN";
    } else if (s.equalsIgnoreCase("sunup")) {
      return "EM";
    } else if (s.equalsIgnoreCase("daybreak")) {
      return "EM";
    } else if (m.matches()) {
      if (DEBUG2) {
        log.info("timePattern matched groups: |%s| |%s| |%s| |%s|\n", m.group(0), m.group(1), m.group(2), m.group(3));
      }
      // group 1 is hours, group 2 is minutes and maybe seconds; group 3 is am/pm
      StringBuilder sb = new StringBuilder();
      sb.append(m.group(1));
      if (m.group(2) == null || "".equals(m.group(2))) {
        sb.append(":00");
      } else {
        sb.append(m.group(2));
      }
      if (m.group(3) != null) {
        String suffix = m.group(3);
        suffix = suffix.replaceAll("\\.", "");
        suffix = suffix.toLowerCase();
        sb.append(suffix);
      } else if (ampm != null) {
        sb.append(ampm);
      // } else {
        // Do nothing; leave ambiguous
        // sb.append("pm");
      }
      if (DEBUG2) {
        log.info("normalizedTimeString new str: " + sb);
      }
      return sb.toString();
    } else if (DEBUG) {
      log.info("Quantifiable: couldn't normalize " + s);
    }
    return null;
  }

  /**
   * Heuristically decides if s is in American (42.33) or European (42,33) number format
   * and tries to turn European version into American.
   *
   */
  private static String convertToAmerican(String s) {
    if(s.contains(",")) {
      //turn all but the last into blanks - this isn't really correct, but it's close enough for now
      while(s.indexOf(',') != s.lastIndexOf(','))
        s = s.replaceFirst(",", "");
      int place = s.lastIndexOf(',');
      //if it's american, should have at least three characters after it
      if (place  >= s.length() - 3 && place != s.length() - 1) {
        s = s.substring(0, place) + '.' + s.substring(place + 1);
      } else {
        s = s.replace(",", "");
      }
    }
    return s;
  }

  static String normalizedMoneyString(String s, Number numberFromSUTime) {
    //first, see if it looks like european style
    s = convertToAmerican(s);
    // clean up string
    s = s.replaceAll("[ \t\n\0\f\r,]", "");
    s = s.toLowerCase();
    if (DEBUG2) {
      log.info("normalizedMoneyString: Normalizing "+s);
    }

    double multiplier = 1.0;

    // do currency words
    char currencySign = '$';
    for (Map.Entry<String, Character> stringCharacterEntry : currencyWords.entrySet()) {
      String key = stringCharacterEntry.getKey();
      if (StringUtils.find(s, key)) {
        if (DEBUG2) { log.info("Found units: " + key); }
        if (key.equals("pence|penny") || key.equals("cents?") || key.equals("\u00A2")) {
          multiplier *= 0.01;
        }
        // if(DEBUG) { log.info("Quantifiable: Found "+ currencyWord); }
        s = s.replaceAll(key, "");
        currencySign = stringCharacterEntry.getValue();
      }
    }

    // process rest as number
    String value = normalizedNumberStringQuiet(s, multiplier, "", numberFromSUTime);
    if (value == null) {
      return null;
    } else {
      return currencySign + value;
    }
  }

  public static String normalizedNumberString(String s, String nextWord, Number numberFromSUTime) {
    if (DEBUG2) { log.info("normalizedNumberString: normalizing " + s); }
    return normalizedNumberStringQuiet(s, 1.0, nextWord, numberFromSUTime);
  }


  private static final Pattern allSpaces = Pattern.compile(" *");


  public static String normalizedNumberStringQuiet(String s,
      double multiplier,
      String nextWord,
      Number numberFromSUTime) {
    // normalizations from SUTime take precedence, if available
    if(numberFromSUTime != null){
      double v = Double.valueOf(numberFromSUTime.toString());
      return Double.toString(v * multiplier);
    }

    // clean up string
    String origSClean = s.replaceAll("[\t\n\0\f\r]", "");
    if (allSpaces.matcher(origSClean).matches()) {
      return s;
    }
    String[] origSSplit = origSClean.split(" ");
    s = s.replaceAll("[ \t\n\0\f\r]", "");
    //see if it looks like european style
    s = convertToAmerican(s);
    // remove parenthesis around numbers
    // if PTBTokenized, this next bit should be a no-op
    // in some contexts parentheses might indicate a negative number, but ignore that.
    if (s.startsWith("(") && s.endsWith(")")) {
      s = s.substring(1, s.length() - 1);
      if (DEBUG2) log.info("Deleted (): " + s);
    }
    s = s.toLowerCase();

    // get multipliers like "billion"
    boolean foundMultiplier = false;
    for (Map.Entry<String, Double> stringDoubleEntry : moneyMultipliers.entrySet()) {
      String moneyTag = stringDoubleEntry.getKey();
      if (s.contains(moneyTag)) {
        // if (DEBUG) {err.println("Quantifiable: Found "+ moneyTag);}
        //special case check: m can mean either meters or million - if nextWord is high or long, we assume meters - this is a huge and bad hack!!!
        if (moneyTag.equals("m") && (nextWord.equals("high") || nextWord.equals("long") )) continue;
        s = s.replaceAll(moneyTag, "");
        multiplier *= stringDoubleEntry.getValue();
        foundMultiplier = true;
      }
    }

    for (Map.Entry<String, Integer> stringIntegerEntry : moneyMultipliers2.entrySet()) {
      String moneyTag = stringIntegerEntry.getKey();
      Matcher m = Pattern.compile(moneyTag).matcher(s);
      if (m.find()) {
        // if(DEBUG){err.println("Quantifiable: Found "+ moneyTag);}
        multiplier *= stringIntegerEntry.getValue();
        foundMultiplier = true;
        int start = m.start(1);
        int end = m.end(1);
        // err.print("Deleting from " + s);
        s = s.substring(0, start) + s.substring(end);
        // err.println("; Result is " + s);
      }
    }

    if (!foundMultiplier) {
      EditDistance ed = new EditDistance();
      for (Map.Entry<String, Double> stringDoubleEntry : moneyMultipliers.entrySet()) {
        String moneyTag = stringDoubleEntry.getKey();
        if(isOneSubstitutionMatch(origSSplit[origSSplit.length - 1], moneyTag, ed)) {
          s = s.replaceAll(moneyTag, "");
          multiplier *= stringDoubleEntry.getValue();
        }
      }
    }

    if (DEBUG2) log.info("Looking for number words in |" + s + "|; multiplier is " + multiplier);

    // handle numbers written in words
    String[] parts = s.split("[ -]");
    boolean processed = false;
    double dd = 0.0;
    for (String part : parts) {
      if (wordsToValues.containsKey(part)) {
        dd += wordsToValues.getCount(part);
        processed = true;
      } else {
        String partMatch = getOneSubstitutionMatch(part, wordsToValues.keySet());
        if(partMatch != null) {
          dd += wordsToValues.getCount(partMatch);
          processed = true;
        }
      }
    }
    if (processed) {
      dd *= multiplier;
      return Double.toString(dd);
    }

    // handle numbers written as numbers
   //  s = s.replaceAll("-", ""); //This is bad: it lets 22-7 be the number 227!
    s = s.replaceAll("[A-Za-z]", "");

    // handle scores or range
    Matcher m2 = scorePattern.matcher(s);
    if (m2.matches()) {
      double d1 = Double.parseDouble(m2.group(1));
      double d2 = Double.parseDouble(m2.group(2));
      return Double.toString(d1) + " - " + Double.toString(d2);
    }

    // check for hyphenated word like 4-Ghz: delete final -
    if (s.endsWith("-")) {
      s = s.substring(0, s.length() - 1);
    }

    Matcher m = moneyPattern.matcher(s);
    if (m.matches()) {
      if (DEBUG2) {
        log.info("Number matched with |" + m.group(2) + "| |" +
            m.group(3) + '|');
      }
      try {
        double d = 0.0;
        if (m.group(2) != null && !m.group(2).isEmpty()) {
          d = Double.parseDouble(m.group(2));
        }
        if (m.group(3) != null && !m.group(3).isEmpty()) {
          d += Double.parseDouble(m.group(3));
        }
        if (d == 0.0 && multiplier != 1.0) {
          // we'd found a multiplier
          d = 1.0;
        }
        d *= multiplier;
        return Double.toString(d);
      } catch (Exception e) {
        if (DEBUG2) {
          log.warn(e);
        }
        return null;
      }
    } else if (multiplier != 1.0) {
      // we found a multiplier, so we have something
      return Double.toString(multiplier);
    } else {
      return null;
    }
  }

  public static String normalizedOrdinalString(String s, Number numberFromSUTime) {
    if (DEBUG2) { log.info("normalizedOrdinalString: normalizing "+s); }
    return normalizedOrdinalStringQuiet(s, numberFromSUTime);
  }

  private static final Pattern numberPattern = Pattern.compile("([0-9.]+)");

  private static String normalizedOrdinalStringQuiet(String s, Number numberFromSUTime) {
    // clean up string
    s = s.replaceAll("[ \t\n\0\f\r,]", "");
    // remove parenthesis around numbers
    // if PTBTokenized, this next bit should be a no-op
    // in some contexts parentheses might indicate a negative number, but ignore that.
    if (s.startsWith("(") && s.endsWith(")")) {
      s = s.substring(1, s.length() - 1);
      if (DEBUG2) log.info("Deleted (): " + s);
    }
    s = s.toLowerCase();

    if (DEBUG2) log.info("Looking for ordinal words in |" + s + '|');
    if (Character.isDigit(s.charAt(0))) {
      Matcher matcher = numberPattern.matcher(s);
      matcher.find();
      // just parse number part, assuming last two letters are st/nd/rd
      return normalizedNumberStringQuiet(matcher.group(), 1.0, "", numberFromSUTime);
    } else if (ordinalsToValues.containsKey(s)) {
      return Double.toString(ordinalsToValues.getCount(s));
    } else {
      String val = getOneSubstitutionMatch(s, ordinalsToValues.keySet());
      if(val != null)
        return Double.toString(ordinalsToValues.getCount(val));
      else
        return null;
    }
  }

  public static String normalizedPercentString(String s, Number numberFromSUTime) {
    if (DEBUG2) {
      log.info("normalizedPercentString: " + s);
    }
    s = s.replaceAll("\\s", "");
    s = s.toLowerCase();
    if (s.contains("%") || s.contains("percent")) {
      s = s.replaceAll("percent|%", "");
    }
    String norm = normalizedNumberStringQuiet(s, 1.0, "", numberFromSUTime);
    if (norm == null) {
      return null;
    }
    return '%' + norm;
  }

  /** Fetches the first encountered Number set by SUTime */
  private static <E extends CoreMap> Number fetchNumberFromSUTime(List<E> l) {
    for(E e: l) {
      if(e.containsKey(CoreAnnotations.NumericCompositeValueAnnotation.class)){
        return e.get(CoreAnnotations.NumericCompositeValueAnnotation.class);
      }
    }
    return null;
  }

  private static <E extends CoreMap> Timex fetchTimexFromSUTime(List<E> l) {
    for(E e: l) {
      if(e.containsKey(TimeAnnotations.TimexAnnotation.class)){
        return e.get(TimeAnnotations.TimexAnnotation.class);
      }
    }
    return null;
  }

  private static <E extends CoreMap> List<E> processEntity(List<E> l,
        String entityType, String compModifier, String nextWord) {
    assert(quantifiable.contains(entityType));
    if (DEBUG) {
      log.info("Quantifiable.processEntity: " + l);
    }
    String s;
    if (entityType.equals("TIME")) {
      s = timeEntityToString(l);
    } else {
      s = singleEntityToString(l);
    }

    Number numberFromSUTime = fetchNumberFromSUTime(l);
    Timex timexFromSUTime = fetchTimexFromSUTime(l);

    if (DEBUG) log.info("Quantifiable: working on " + s);
    String p = null;
    switch (entityType) {
      case "NUMBER": {
        p = "";
        if (compModifier != null) {
          p = compModifier;
        }
        String q = normalizedNumberString(s, nextWord, numberFromSUTime);
        if (q != null) {
          p = p.concat(q);
        } else {
          p = null;
        }
        break;
      }
      case "ORDINAL":
        p = normalizedOrdinalString(s, numberFromSUTime);
        break;
      case "DURATION":
        // SUTime marks some ordinals, e.g., "22nd time", as durations
        p = normalizedDurationString(s, timexFromSUTime);
        break;
      case "MONEY": {
        p = "";
        if (compModifier != null) {
          p = compModifier;
        }
        String q = normalizedMoneyString(s, numberFromSUTime);
        if (q != null) {
          p = p.concat(q);
        } else {
          p = null;
        }
        break;
      }
      case "DATE":
        p = normalizedDateString(s, timexFromSUTime);
        break;
      case "TIME": {
        p = "";
        if (compModifier != null && !compModifier.matches("am|pm")) {
          p = compModifier;
        }
        String q = normalizedTimeString(s, compModifier != null ? compModifier : "", timexFromSUTime);
        if (q != null && q.length() == 1 && !q.equals("D")) {
          p = p.concat(q);
        } else {
          p = q;
        }
        break;
      }
      case "PERCENT": {
        p = "";
        if (compModifier != null) {
          p = compModifier;
        }
        String q = normalizedPercentString(s, numberFromSUTime);
        if (q != null) {
          p = p.concat(q);
        } else {
          p = null;
        }
        break;
      }
    }
    if (DEBUG) {
      log.info("Quantifiable: Processed '" + s + "' as '" + p + '\'');
    }

    int i = 0;
    for (E wi : l) {
      if (p != null) {
        if (DEBUG) {
          log.info("#4: Changing normalized NER from " + wi.get(CoreAnnotations.NormalizedNamedEntityTagAnnotation.class) + " to " + p + " at index " + i);
        }
        wi.set(CoreAnnotations.NormalizedNamedEntityTagAnnotation.class, p);
      }
      //currently we also write this into the answers;
      //wi.setAnswer(wi.get(CoreAnnotations.AnswerAnnotation.class)+"("+p+")");
      i++;
    }
    return l;
  }


  /** @param l The list of tokens in a time entity
   *  @return the word in the time word list that should be normalized
   */
  private static <E extends CoreMap> String timeEntityToString(List<E> l) {
    String entityType = l.get(0).get(CoreAnnotations.AnswerAnnotation.class);
    int size = l.size();
    for (E w : l) {
      assert(w.get(CoreAnnotations.AnswerAnnotation.class) == null ||
          w.get(CoreAnnotations.AnswerAnnotation.class).equals(entityType));
      Matcher m = timePattern.matcher(w.get(CoreAnnotations.TextAnnotation.class));
      if (m.matches())
        return w.get(CoreAnnotations.TextAnnotation.class);
    }
    if (DEBUG) {
      log.info("default: " + l.get(size-1).get(CoreAnnotations.TextAnnotation.class));
    }
    return l.get(size-1).get(CoreAnnotations.TextAnnotation.class);
  }


  /**
   * Takes the output of an {@link AbstractSequenceClassifier} and marks up
   * each document by normalizing quantities. Each {@link CoreLabel} in any
   * of the documents which is normalizable will receive a "normalizedQuantity"
   * attribute.
   *
   * @param l a {@link List} of {@link List}s of {@link CoreLabel}s
   * @return The list with normalized entity fields filled in
   */
  public static List<List<CoreLabel>> normalizeClassifierOutput(List<List<CoreLabel>> l){
    for (List<CoreLabel> doc: l) {
      addNormalizedQuantitiesToEntities(doc);
    }
    return l;
  }

  private static final String lessEqualThreeWords = "no (?:more|greater|higher) than|as (?:many|much) as";
  private static final String greaterEqualThreeWords = "no (?:less|fewer) than|as few as";

  private static final String greaterThanTwoWords = "(?:more|greater|larger|higher) than";
  private static final String lessThanTwoWords = "(?:less|fewer|smaller) than|at most";
  private static final String lessEqualTwoWords = "no (?:more|greater)_than|or less|up to";
  private static final String greaterEqualTwoWords = "no (?:less|fewer)_than|or more|at least";
  private static final String approxTwoWords = "just (?:over|under)|or so";

  private static final String greaterThanOneWord = "(?:above|over|more_than|greater_than)";
  private static final String lessThanOneWord = "(?:below|under|less_than)";
  private static final String lessEqualOneWord = "(?:up_to|within)";
  // note that ones like "nearly" or "almost" can be above or below:
  // "almost 500 killed", "almost zero inflation"
  private static final String approxOneWord = "(?:approximately|estimated|nearly|around|about|almost|just_over|just_under)";
  private static final String other = "other";

  /**
   * Takes the strings of the three previous and next words to a quantity and
   * detects a
   * quantity modifier like "less than", "more than", etc.
   * Any of these words may be {@code null} or an empty String.
   */
  private static <E extends CoreMap> String detectQuantityModifier(List<E> list, int beforeIndex, int afterIndex) {
    String prev = (beforeIndex >= 0) ? list.get(beforeIndex).get(CoreAnnotations.TextAnnotation.class).toLowerCase(): "";
    String prev2 = (beforeIndex - 1 >= 0) ? list.get(beforeIndex - 1).get(CoreAnnotations.TextAnnotation.class).toLowerCase(): "";
    String prev3 = (beforeIndex - 2 >= 0) ? list.get(beforeIndex - 2).get(CoreAnnotations.TextAnnotation.class).toLowerCase(): "";
    int sz = list.size();
    String next = (afterIndex < sz) ? list.get(afterIndex).get(CoreAnnotations.TextAnnotation.class).toLowerCase(): "";
    String next2 = (afterIndex + 1 < sz) ? list.get(afterIndex + 1).get(CoreAnnotations.TextAnnotation.class).toLowerCase(): "";
    String next3 = (afterIndex + 2 < sz) ? list.get(afterIndex + 2).get(CoreAnnotations.TextAnnotation.class).toLowerCase(): "";

    if (DEBUG) {
      log.info("Quantifiable: previous: " + prev3 + ' ' + prev2+ ' ' + prev);
      log.info("Quantifiable: next: " + next + ' ' + next2 + ' ' + next3);
    }

    String longPrev = prev3 + ' ' + prev2 + ' ' + prev;
    if (longPrev.matches(lessEqualThreeWords)) { return "<="; }
    if (longPrev.matches(greaterEqualThreeWords)) { return ">="; }

    longPrev = prev2 + ' ' + prev;
    if (longPrev.matches(greaterThanTwoWords)) { return ">"; }
    if (longPrev.matches(lessEqualTwoWords)) { return "<="; }
    if (longPrev.matches(greaterEqualTwoWords)) { return ">="; }
    if (longPrev.matches(lessThanTwoWords)) { return "<"; }
    if (longPrev.matches(approxTwoWords)) { return "~"; }

    String longNext = next + ' ' + next2;
    if (longNext.matches(greaterEqualTwoWords)) { return ">="; }
    if (longNext.matches(lessEqualTwoWords)) { return "<="; }

    if (prev.matches(greaterThanOneWord)) { return ">"; }
    if (prev.matches(lessThanOneWord)) { return "<"; }
    if (prev.matches(lessEqualOneWord)) { return "<="; }
    if (prev.matches(approxOneWord)) { return "~"; }

    if (next.matches(other)) { return ">="; }

    if (DEBUG) { log.info("Quantifiable: not a quantity modifier"); }
    return null;
  }


  private static final String earlyOneWord = "early";
  private static final String earlyTwoWords = "(?:dawn|eve|beginning) of";
  private static final String earlyThreeWords = "early in the";
  private static final String lateOneWord = "late";
  private static final String lateTwoWords = "late at|end of";
  private static final String lateThreeWords = "end of the";
  private static final String middleTwoWords = "(?:middle|midst) of";
  private static final String middleThreeWords = "(?:middle|midst) of the";

  private static final String amOneWord = "[Aa]\\.?[Mm]\\.?";
  private static final String pmOneWord = "[Pp]\\.?[Mm]\\.?";
  private static final String amThreeWords = "in the morning";
  private static final String pmTwoWords = "at night";
  private static final String pmThreeWords = "in the (?:afternoon|evening)";


  /**
   * Takes the strings of the three previous words to a quantity and detects a
   * quantity modifier like "less than", "more than", etc.
   * Any of these words may be {@code null} or an empty String.
   */
  private static <E extends CoreMap> String detectTimeOfDayModifier(List<E> list, int beforeIndex, int afterIndex) {
    String prev = (beforeIndex >= 0) ? list.get(beforeIndex).get(CoreAnnotations.TextAnnotation.class).toLowerCase() : "";
    String prev2 = (beforeIndex - 1 >= 0) ? list.get(beforeIndex - 1).get(CoreAnnotations.TextAnnotation.class).toLowerCase() : "";
    String prev3 = (beforeIndex - 2 >= 0) ? list.get(beforeIndex - 2).get(CoreAnnotations.TextAnnotation.class).toLowerCase() : "";
    int sz = list.size();
    String next = (afterIndex < sz) ? list.get(afterIndex).get(CoreAnnotations.TextAnnotation.class).toLowerCase() : "";
    String next2 = (afterIndex + 1 < sz) ? list.get(afterIndex + 1).get(CoreAnnotations.TextAnnotation.class).toLowerCase() : "";
    String next3 = (afterIndex + 2 < sz) ? list.get(afterIndex + 2).get(CoreAnnotations.TextAnnotation.class).toLowerCase() : "";

    String longPrev = prev3 + ' ' + prev2 + ' ' + prev;
    if (longPrev.matches(earlyThreeWords)) {
      return "E";
    }
    else if (longPrev.matches(lateThreeWords)) {
      return "L";
    }
    else if (longPrev.matches(middleThreeWords)) {
      return "M";
    }

    longPrev = prev2 + ' ' + prev;
    if (longPrev.matches(earlyTwoWords)) {
      return "E";
    }
    else if (longPrev.matches(lateTwoWords)) {
      return "L";
    }
    else if (longPrev.matches(middleTwoWords)) {
      return "M";
    }

    if (prev.matches(earlyOneWord) || prev2.matches(earlyOneWord)) {
      return "E";
    }
    else if (prev.matches(lateOneWord) || prev2.matches(lateOneWord)) {
      return "L";
    }

    String longNext = next3 + ' ' + next2 + ' ' + next;
    if (longNext.matches(pmThreeWords)) {
      return "pm";
    }
    if (longNext.matches(amThreeWords)) {
      return "am";
    }

    longNext = next2 + ' ' + next;
    if (longNext.matches(pmTwoWords)) {
      return "pm";
    }

    if (next.matches(amOneWord) || next2.matches("morning") || next3.matches("morning")) {
      return "am";
    }
    if (next.matches(pmOneWord) || next2.matches("afternoon") || next3.matches("afternoon")
        || next2.matches("night") || next3.matches("night")
        || next2.matches("evening") || next3.matches("evening")) {
      return "pm";
    }

    return "";
  }

  /**
   * Identifies contiguous MONEY, TIME, DATE, or PERCENT entities
   * and tags each of their constituents with a "normalizedQuantity"
   * label which contains the appropriate normalized string corresponding to
   * the full quantity. Quantities are not concatenated
   *
   * @param l A list of {@link CoreMap}s representing a single
   *      document.  Note: the Labels are updated in place.
   */
  public static <E extends CoreMap> void addNormalizedQuantitiesToEntities(List<E> l) {
    addNormalizedQuantitiesToEntities(l, false, false);
  }

  public static <E extends CoreMap> void addNormalizedQuantitiesToEntities(List<E> l, boolean concatenate) {
    addNormalizedQuantitiesToEntities(l, concatenate, false);
  }

  public static <E extends CoreMap> boolean isCompatible(String tag, E prev, E cur) {
    if ("NUMBER".equals(tag) || "ORDINAL".equals(tag) || "PERCENT".equals(tag)) {
      // Get NumericCompositeValueAnnotation and say two entities are incompatible if they are different
      Number n1 = cur.get(CoreAnnotations.NumericCompositeValueAnnotation.class);
      Number n2 = prev.get(CoreAnnotations.NumericCompositeValueAnnotation.class);

      // Special case for % sign
      if ("PERCENT".equals(tag) && n1 == null) return true;

      boolean compatible = Objects.equals(n1, n2);
      if (!compatible) return false;
    }

    if ("TIME".equals(tag) || "SET".equals(tag) || "DATE".equals(tag) || "DURATION".equals(tag)) {
      // Check timex...
      Timex timex1 = cur.get(TimeAnnotations.TimexAnnotation.class);
      Timex timex2 = prev.get(TimeAnnotations.TimexAnnotation.class);
      String tid1 = (timex1 != null)? timex1.tid():null;
      String tid2 = (timex2 != null)? timex2.tid():null;
      boolean compatible = Objects.equals(tid1, tid2);
      if (!compatible) return false;
    }

    return true;
  }

  /**
   * Identifies contiguous MONEY, TIME, DATE, or PERCENT entities
   * and tags each of their constituents with a "normalizedQuantity"
   * label which contains the appropriate normalized string corresponding to
   * the full quantity.
   *
   * @param list A list of {@link CoreMap}s representing a single
   *      document.  Note: the Labels are updated in place.
   * @param concatenate true if quantities should be concatenated into one label, false otherwise
   */
  public static <E extends CoreMap> void addNormalizedQuantitiesToEntities(List<E> list, boolean concatenate, boolean usesSUTime) {
    List<E> toRemove = new ArrayList<>(); // list for storing those objects we're going to remove at the end (e.g., if concatenate, we replace 3 November with 3_November, have to remove one of the originals)

    // Goes through tokens and tries to fix up NER annotations
    fixupNerBeforeNormalization(list);

    // Now that NER tags has been fixed up, we do another pass to add the normalization
    String prevNerTag = BACKGROUND_SYMBOL;
    String timeModifier = "";
    int beforeIndex = -1;
    ArrayList<E> collector = new ArrayList<>();
    for (int i = 0, sz = list.size(); i <= sz; i++) {
      E wi = null;
      String currNerTag = null;
      String nextWord = "";
      if (i < list.size()) {
        wi = list.get(i);
        if (DEBUG) { log.info("addNormalizedQuantitiesToEntities: wi is " + wi + "; collector is " + collector); }
        if ((i+1) < sz) {
          nextWord = list.get(i+1).get(CoreAnnotations.TextAnnotation.class);
          if(nextWord == null) nextWord = "";
        }

        currNerTag = wi.get(CoreAnnotations.NamedEntityTagAnnotation.class);
        if ("TIME".equals(currNerTag)) {
          if (timeModifier.isEmpty()) {
            timeModifier = detectTimeOfDayModifier(list, i-1, i+1);
          }
        }
      }

      E wprev = (i > 0)? list.get(i-1):null;
      // if the current wi is a non-continuation and the last one was a
      // quantity, we close and process the last segment.
      if ((currNerTag == null || ! currNerTag.equals(prevNerTag) || !isCompatible(prevNerTag, wprev, wi)) && quantifiable.contains(prevNerTag)) {
        String compModifier = null;
        // special handling of TIME
        switch (prevNerTag) {
          case "TIME":
            processEntity(collector, prevNerTag, timeModifier, nextWord);
            break;
          case ("DATE"):
            //detect date range modifiers by looking at nearby words
            E prev = (beforeIndex >= 0) ? list.get(beforeIndex) : null;
            if (usesSUTime) {
              // If sutime was used don't do any weird relabeling of more things as DATE
              compModifier = detectDateRangeModifier(prev);
            } else {
              compModifier = detectDateRangeModifier(collector, list, beforeIndex, i);
            }
            if (!compModifier.equals(ISODateInstance.BOUNDED_RANGE))
              processEntity(collector, prevNerTag, compModifier, nextWord);
            //now repair this date if it's more than one word
            //doesn't really matter which one we keep ideally we should be doing lemma/etc matching anyway
            //but we vaguely try to deal with this by choosing the NNP or the CD
            if (concatenate)
              concatenateNumericString(collector, toRemove);
            break;
          default:
            // detect "more than", "nearly", etc. by looking at nearby words.
            if (prevNerTag.equals("MONEY") || prevNerTag.equals("NUMBER") ||
                prevNerTag.equals("PERCENT")) {
              compModifier = detectQuantityModifier(list, beforeIndex, i);
            }
            processEntity(collector, prevNerTag, compModifier, nextWord);
            if (concatenate) {
              concatenateNumericString(collector, toRemove);
            }
            break;
        }

        collector = new ArrayList<>();
        timeModifier = "";
      }

      // if the current wi is a quantity, we add it to the collector.
      // if its the first word in a quantity, we record index before it
      if (quantifiable.contains(currNerTag)) {
        if (collector.isEmpty()) {
          beforeIndex = i - 1;
        }
        collector.add(wi);
      }
      prevNerTag = currNerTag;
    }
    if (concatenate) {
      list.removeAll(toRemove);
    }
    List<E> moreRemoves = new ArrayList<>();
    for (int i = 0, sz = list.size(); i < sz; i++) {
      E wi = list.get(i);
      moreRemoves.addAll(detectTwoSidedRangeModifier(wi, list, i-1, i+1, concatenate));
    }
    if (concatenate) {
      list.removeAll(moreRemoves);
    }
  }

  private static <E extends CoreMap> void fixupNerBeforeNormalization(List<E> list) {
    // Goes through tokens and tries to fix up NER annotations
    String prevNerTag = BACKGROUND_SYMBOL;
    String prevNumericType = null;
    Timex prevTimex = null;
    for (int i = 0, sz = list.size(); i < sz; i++) {
      E wi = list.get(i);
      Timex timex = wi.get(TimeAnnotations.TimexAnnotation.class);
      String numericType = wi.get(CoreAnnotations.NumericCompositeTypeAnnotation.class);

      String curWord = (wi.get(CoreAnnotations.TextAnnotation.class) != null ? wi.get(CoreAnnotations.TextAnnotation.class) : "");
      String currNerTag = wi.get(CoreAnnotations.NamedEntityTagAnnotation.class);

      if (DEBUG) { log.info("fixupNerBeforeNormalization: wi is " + wi); }
      // Attempts repairs to NER tags only if not marked by SUTime already
      if (timex == null && numericType == null) {
        // repairs commas in between dates...  String constant first in equals() in case key has null value....
        if ((i+1) < sz && ",".equals(wi.get(CoreAnnotations.TextAnnotation.class)) && "DATE".equals(prevNerTag)) {
          if (prevTimex == null && prevNumericType == null) {
            E nextToken = list.get(i+1);
            String nextNER = nextToken.get(CoreAnnotations.NamedEntityTagAnnotation.class);
            if (nextNER != null && nextNER.equals("DATE")) {
              wi.set(CoreAnnotations.NamedEntityTagAnnotation.class, "DATE");
            }
          }
        }

        //repairs mistagged multipliers after a numeric quantity
        if ( ! curWord.isEmpty() && (moneyMultipliers.containsKey(curWord) ||
                (getOneSubstitutionMatch(curWord, moneyMultipliers.keySet()) != null)) &&
                prevNerTag != null && (prevNerTag.equals("MONEY") || prevNerTag.equals("NUMBER"))) {
          wi.set(CoreAnnotations.NamedEntityTagAnnotation.class, prevNerTag);
        }

        //repairs four digit ranges (2002-2004) that have not been tagged as years - maybe bad? (empirically useful)
        if (curWord.contains("-")) {
          String[] sides = curWord.split("-");
          if (sides.length == 2) {
            try {
              int first = Integer.parseInt(sides[0]);
              int second = Integer.parseInt(sides[1]);
              //they're both integers, see if they're both between 1000-3000 (likely years)
              if (1000 <= first && first <= 3000 && 1000 <= second && second <= 3000) {
                wi.set(CoreAnnotations.NamedEntityTagAnnotation.class, "DATE");
                String dateStr = new ISODateInstance(new ISODateInstance(sides[0]), new ISODateInstance(sides[1])).getDateString();
                if (DEBUG) {
                  log.info("#5: Changing normalized NER from " +
                          wi.get(CoreAnnotations.NormalizedNamedEntityTagAnnotation.class) + " to " + dateStr + " at index " + i);
                }
                wi.set(CoreAnnotations.NormalizedNamedEntityTagAnnotation.class, dateStr);
                continue;
              }
            } catch (Exception e) {
              // they weren't numbers.
            }
          }
        }

        // Marks time units as DURATION if they are preceded by a NUMBER tag.  e.g. "two years" or "5 minutes"
        if ( timeUnitWords.contains(curWord) &&
                (currNerTag == null || !"DURATION".equals(currNerTag) ) &&
                ("NUMBER".equals(prevNerTag))) {
          wi.set(CoreAnnotations.NamedEntityTagAnnotation.class, "DURATION");
          for (int j = i-1; j > 0; j--) {
            E prev = list.get(j);
            if ("NUMBER".equals(prev.get(CoreAnnotations.NamedEntityTagAnnotation.class))) {
              prev.set(CoreAnnotations.NamedEntityTagAnnotation.class, "DURATION");
            }
          }
        }
      } else {
        // Fixup SUTime marking of twenty-second
        if ("DURATION".equals(currNerTag) && ordinalsToValues.containsKey(curWord)
                && curWord.endsWith("second") && timex.text().equals(curWord)) {
          wi.set(CoreAnnotations.NamedEntityTagAnnotation.class, "ORDINAL");
        }
      }

      prevNerTag = currNerTag;
      prevNumericType = numericType;
      prevTimex = timex;
    }
  }

    /**
     * Runs a deterministic named entity classifier which is good at recognizing
     * numbers and money and date expressions not recognized by our statistical
     * NER.  It then changes any BACKGROUND_SYMBOL's from the list to
     * the value tagged by this deterministic NER.
     * It then adds normalized values for quantifiable entities.
     *
     * @param l A document to label
     * @return The list with results of 'specialized' (rule-governed) NER filled in
     */
  public static <E extends CoreLabel> List<E> applySpecializedNER(List<E> l) {
    int sz = l.size();
    // copy l
    List<CoreLabel> copyL = new ArrayList<>(sz);
    for (int i = 0; i < sz; i++) {
      if (DEBUG2) {
        if (i == 1) {
          String tag = l.get(i).get(CoreAnnotations.PartOfSpeechAnnotation.class);
          if (tag == null || tag.isEmpty()) {
            log.warn("Quantifiable: error! tag is " + tag);
          }
        }
      }
      copyL.add(new CoreLabel(l.get(i)));
    }
    // run NumberSequenceClassifier
    AbstractSequenceClassifier<CoreLabel> nsc = new NumberSequenceClassifier();
    copyL = nsc.classify(copyL);
    // update entity only if it was not O
    for (int i = 0; i < sz; i++) {
      E before = l.get(i);
      CoreLabel nscAnswer = copyL.get(i);
      if (before.get(CoreAnnotations.NamedEntityTagAnnotation.class) == null && before.get(CoreAnnotations.NamedEntityTagAnnotation.class).equals(BACKGROUND_SYMBOL) &&
          (nscAnswer.get(CoreAnnotations.AnswerAnnotation.class) != null && !nscAnswer.get(CoreAnnotations.AnswerAnnotation.class).equals(BACKGROUND_SYMBOL))) {
        log.info("Quantifiable: updating class for " +
            before.get(CoreAnnotations.TextAnnotation.class) + '/' +
            before.get(CoreAnnotations.NamedEntityTagAnnotation.class) + " to " + nscAnswer.get(CoreAnnotations.AnswerAnnotation.class));
        before.set(CoreAnnotations.NamedEntityTagAnnotation.class, nscAnswer.get(CoreAnnotations.AnswerAnnotation.class));
      }
    }

    addNormalizedQuantitiesToEntities(l);
    return l;
  } // end applySpecializedNER

}
