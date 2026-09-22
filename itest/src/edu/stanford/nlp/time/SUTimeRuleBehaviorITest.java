package edu.stanford.nlp.time;

import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.process.CoreLabelTokenFactory;
import edu.stanford.nlp.process.PTBTokenizer;
import edu.stanford.nlp.util.ArrayCoreMap;
import edu.stanford.nlp.util.CoreMap;

import org.junit.Test;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import static org.junit.Assert.assertEquals;

/**
 * Text-in, TIMEX-out tests for SUTime behaviour that the lower-level unit tests reach only
 * through the joda-time API.
 *
 * <p>Most of {@code JodaTimeUtilsTest} and part of {@code SUTimeTemporalTest} build their
 * inputs from joda types, so replacing joda-time rewrites those tests along with the code
 * they guard. The cases here go in as text and come out as TIMEX strings, and survive that
 * change untouched. Each group pins behaviour that mutation testing found was otherwise
 * guarded after such a rewrite by nothing but the golden file:
 *
 * <ul>
 *   <li>{@code JodaTimeUtils.isCompatible}, which decides whether two partial dates can be
 *       merged: ordinal weekdays within a month, and adjacent dates in a list.
 *   <li>{@code JodaTimeUtils.getMostSpecific}, which fixes the granularity of an expression:
 *       hour-precision ISO dates, and a day within a relative week.
 *   <li>{@code JollyDayHolidays}, which had no test at all. Floating holidays are used
 *       because their dates are computed, and that computation is what a change of date
 *       library or of jollyday version would disturb.
 *   <li>Week-valued durations, since {@code java.time.Period} has no weeks field and renders
 *       one week as {@code P7D}.
 * </ul>
 *
 * <p>The rules and the holiday calendar are read from the source tree, so these tests run
 * without the models jar and exercise the copies being edited. They expect to run from the
 * repository root. Tokens carry no part-of-speech tags, so the tense rules in
 * english.sutime.txt never fire; that is why one extractor can safely be shared by every
 * case (see issue #1061 for what happens when they do).
 */
public class SUTimeRuleBehaviorITest {

  private static final String RULES = "src/edu/stanford/nlp/time/rules/";
  private static final String HOLIDAYS = "src/edu/stanford/nlp/time/holidays/Holidays_sutime.xml";

  private static final TimeExpressionExtractor EXTRACTOR = newExtractor();

  private static TimeExpressionExtractor newExtractor() {
    Properties props = new Properties();
    props.setProperty("sutime.rules", RULES + "defs.sutime.txt," + RULES + "english.sutime.txt,"
            + RULES + "english.holidays.sutime.txt");
    props.setProperty("sutime.binders", "1");
    props.setProperty("sutime.binder.1", "edu.stanford.nlp.time.JollyDayHolidays");
    props.setProperty("sutime.binder.1.xml", new java.io.File(HOLIDAYS).getAbsolutePath());
    props.setProperty("sutime.binder.1.pathtype", "file");
    return new TimeExpressionExtractorImpl("sutime", props);
  }

  private static CoreMap sentenceOf(String text) {
    List<CoreLabel> tokens = new PTBTokenizer<>(new StringReader(text),
            new CoreLabelTokenFactory(), "splitHyphenated=false").tokenize();
    for (int i = 0; i < tokens.size(); i++) {
      tokens.get(i).setIndex(i + 1);
    }
    CoreMap sentence = new ArrayCoreMap();
    sentence.set(CoreAnnotations.TextAnnotation.class, text);
    sentence.set(CoreAnnotations.TokensAnnotation.class, tokens);
    sentence.set(CoreAnnotations.CharacterOffsetBeginAnnotation.class, 0);
    sentence.set(CoreAnnotations.CharacterOffsetEndAnnotation.class, text.length());
    sentence.set(CoreAnnotations.TokenBeginAnnotation.class, 0);
    sentence.set(CoreAnnotations.TokenEndAnnotation.class, tokens.size());
    return sentence;
  }

  /** One expression: its text, TIMEX type and value (or alt_value, for functions). */
  private static final class Found {
    final String text;
    final Map<String, String> attributes;

    Found(String text, Map<String, String> attributes) {
      this.text = text;
      this.attributes = attributes;
    }

    String value() {
      String v = attributes.get("value");
      return v != null ? v : attributes.get("alt_value");
    }

    String type() {
      return attributes.get("type");
    }
  }

  /** Extract every expression, resolving against {@code docDate} (yyyy-MM-dd, or null). */
  private static List<Found> extract(String text, String docDate) {
    CoreMap document = new ArrayCoreMap();
    if (docDate != null) {
      document.set(CoreAnnotations.DocDateAnnotation.class, docDate);
    }
    List<Found> out = new ArrayList<>();
    for (CoreMap timex : EXTRACTOR.extractTimeExpressionCoreMaps(sentenceOf(text), document)) {
      SUTime.Temporal temporal = timex.get(TimeExpression.Annotation.class).getTemporal();
      out.add(new Found(timex.get(CoreAnnotations.TextAnnotation.class),
              temporal.getTimexAttributes(new SUTime.TimeIndex())));
    }
    return out;
  }

  /** Assert that exactly one expression is found, with the given text, type and value. */
  private static void assertSingle(String text, String docDate,
                                   String expectedText, String expectedType, String expectedValue) {
    List<Found> found = extract(text, docDate);
    assertEquals("expressions found in: " + text, 1, found.size());
    assertEquals(expectedText, found.get(0).text);
    assertEquals(expectedType, found.get(0).type());
    assertEquals(expectedValue, found.get(0).value());
  }

  // ------------------------------------------- merging partial dates (isCompatible)

  @Test
  public void testOrdinalWeekdayWithinAMonth() {
    // The third Wednesday of November 2009. The Wednesdays are the 4th, 11th, 18th and
    // 25th; picking the 11th would mean the day-of-week and month were combined wrongly.
    assertSingle("3rd wednesday in november.", "2009-08-12",
            "3rd wednesday in november", "DATE", "2009-11-18");
  }

  @Test
  public void testAdjacentDatesInAListStaySeparate() {
    // Two unrelated dates separated by a comma are two expressions, not one.
    List<Found> found = extract("ISO date is 6/12/2008, 1988-02-17.", null);
    assertEquals(2, found.size());
    assertEquals("6/12/2008", found.get(0).text);
    assertEquals("2008-06-12", found.get(0).value());
    assertEquals("1988-02-17", found.get(1).text);
    assertEquals("1988-02-17", found.get(1).value());
  }

  @Test
  public void testWeekWithinAMonthIsLeftAsAFunction() {
    // "the last week of October" cannot be pinned to a single ISO value, so it is
    // reported as a temporal function over the month rather than collapsed to a week of
    // the reference year.
    assertSingle("The story broke in the last week of October.", "2005-08-12",
            "the last week of October", "DATE", "PREV_IMMEDIATE P1W INTERSECT XXXX-10");
  }

  // --------------------------------------------- granularity (getMostSpecific)

  @Test
  public void testHourPrecisionIsoDateIsATime() {
    // An ISO date carrying only an hour is a TIME at hour precision, not a DATE.
    assertSingle("ISO partial datetime 2008-05-16T09.", null,
            "2008-05-16T09", "TIME", "2008-05-16T09");
    assertSingle("ISO partial datetime 2008-05-16T09.", "2010-02-17",
            "2008-05-16T09", "TIME", "2008-05-16T09");
  }

  @Test
  public void testDayWithinARelativeWeek() {
    // The fourth day of the week before the reference: 12 August 2009 is a Wednesday, so
    // the previous week runs 3-9 August and its fourth day is Thursday the 6th.
    assertSingle("4th day last week.", "2009-08-12", "4th day last week", "DATE", "2009-08-06");
  }

  // ------------------------------------------------------------- holidays

  @Test
  public void testFloatingHolidaysResolveToTheirCalendarDates() {
    // Each of these is computed rather than fixed, so the dates are checked against the
    // real calendar rather than against earlier SUTime output.
    assertSingle("We met on Thanksgiving.", "2010-02-17", "Thanksgiving", "DATE", "2010-11-25");
    assertSingle("We met on Easter.", "2010-02-17", "Easter", "DATE", "2010-04-04");
    assertSingle("We met on Labor Day.", "2010-02-17", "Labor Day", "DATE", "2010-09-06");
    assertSingle("We met on Memorial Day.", "2010-02-17", "Memorial Day", "DATE", "2010-05-31");
    assertSingle("We met on Mother's Day.", "2010-02-17", "Mother's Day", "DATE", "2010-05-09");
    assertSingle("We met on Martin Luther King Day.", "2010-02-17",
            "Martin Luther King Day", "DATE", "2010-01-18");
  }

  @Test
  public void testFloatingHolidaysMoveWithTheReferenceYear() {
    assertSingle("We met on Thanksgiving.", "2011-06-03", "Thanksgiving", "DATE", "2011-11-24");
    assertSingle("We met on Easter.", "2011-06-03", "Easter", "DATE", "2011-04-24");
    assertSingle("We met on Labor Day.", "2011-06-03", "Labor Day", "DATE", "2011-09-05");
  }

  @Test
  public void testFixedHolidayResolvesToTheNearestOccurrence() {
    // In February 2010 the nearest Christmas is the one just past.
    assertSingle("We met on Christmas.", "2010-02-17", "Christmas", "DATE", "2009-12-25");
  }

  @Test
  public void testHolidayWithoutAReferenceIsSymbolic() {
    assertSingle("We met on Thanksgiving.", null, "Thanksgiving", "DATE", "THANKSGIVING");
    assertSingle("We met on Easter.", null, "Easter", "DATE", "EASTER");
  }

  // ------------------------------------- a weekday within a named month

  @Test
  public void testOrdinalWeekdayWithinAMonthResolvesToADate() {
    // An ordinal picks out one day, so these have a single ISO value.
    assertSingle("We met on the first Monday in June.", "2009-08-12",
            "the first Monday in June", "DATE", "2009-06-01");
    assertSingle("We met on the second Tuesday of March.", "2009-08-12",
            "the second Tuesday of March", "DATE", "2009-03-10");
  }

  @Test
  public void testLastWeekdayOfAMonthKeepsTheMonth() {
    // "last" has no fixed ordinal, so this is reported as a function over the month.
    // The month has to survive into the value.
    assertSingle("We met on the last Friday in November.", "2009-08-12",
            "the last Friday in November", "DATE",
            "PREV_IMMEDIATE XXXX-WXX-5 INTERSECT XXXX-11");
  }

  // --------------------------------------------------------- week durations

  @Test
  public void testWeekDurationsKeepTheirWeeksField() {
    // ISO 8601 and TIMEX both allow a weeks designator, and SUTime keeps it. A period
    // type without a weeks field would render these as P7D and P14D.
    assertSingle("It lasted a week.", null, "a week", "DURATION", "P1W");
    assertSingle("It lasted two weeks.", null, "two weeks", "DURATION", "P2W");
  }

}
