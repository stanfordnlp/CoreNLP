package edu.stanford.nlp.time;

import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.process.CoreLabelTokenFactory;
import edu.stanford.nlp.process.PTBTokenizer;
import edu.stanford.nlp.util.ArrayCoreMap;
import edu.stanford.nlp.util.CoreMap;

import org.junit.Test;

import java.io.File;
import java.io.StringReader;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.Month;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.function.IntFunction;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * Checks every holiday that SUTime resolves through jollyday, for each year from 1995 to
 * 2030, against dates computed here from the calendar rules.
 *
 * <p>Eleven holidays in english.holidays.sutime.txt map to a {@code JH_} variable, which
 * means their dates come from jollyday rather than from a fixed month and day in the rule
 * file. Those are the ones a change of jollyday version, or of the date library underneath
 * it, can move. Fixed-date holidays such as Christmas and Halloween are written directly as
 * {@code IsoDate} in the rules and never reach jollyday, so they are not covered here.
 *
 * <p>The expected dates are worked out in this file using {@code java.time} adjusters and,
 * for the movable feasts, the anonymous Gregorian algorithm for Easter. They are not
 * recorded from previous SUTime output, so a failure means one of the two calculations is
 * wrong rather than merely that something changed.
 *
 * <p>The rules and holiday calendar are read from the source tree, so this runs without the
 * models jar and expects the repository root as its working directory.
 */
public class SUTimeHolidayITest {

  private static final int FIRST_YEAR = 1995;
  private static final int LAST_YEAR = 2030;

  private static final String RULES = "src/edu/stanford/nlp/time/rules/";
  private static final String HOLIDAYS = "src/edu/stanford/nlp/time/holidays/Holidays_sutime.xml";

  private static final TimeExpressionExtractor EXTRACTOR = newExtractor();

  private static TimeExpressionExtractor newExtractor() {
    Properties props = new Properties();
    props.setProperty("sutime.rules", RULES + "defs.sutime.txt," + RULES + "english.sutime.txt,"
            + RULES + "english.holidays.sutime.txt");
    props.setProperty("sutime.binders", "1");
    props.setProperty("sutime.binder.1", "edu.stanford.nlp.time.JollyDayHolidays");
    props.setProperty("sutime.binder.1.xml", new File(HOLIDAYS).getAbsolutePath());
    props.setProperty("sutime.binder.1.pathtype", "file");
    return new TimeExpressionExtractorImpl("sutime", props);
  }

  // ------------------------------------------------ the calendar rules

  /** Easter Sunday in the Gregorian calendar, by the anonymous algorithm. */
  static LocalDate easter(int year) {
    int a = year % 19;
    int b = year / 100;
    int c = year % 100;
    int d = b / 4;
    int e = b % 4;
    int f = (b + 8) / 25;
    int g = (b - f + 1) / 3;
    int h = (19 * a + b - d - g + 15) % 30;
    int i = c / 4;
    int k = c % 4;
    int l = (32 + 2 * e + 2 * i - h - k) % 7;
    int m = (a + 11 * h + 22 * l) / 451;
    int month = (h + l - 7 * m + 114) / 31;
    int day = ((h + l - 7 * m + 114) % 31) + 1;
    return LocalDate.of(year, month, day);
  }

  private static LocalDate nth(int year, Month month, DayOfWeek day, int n) {
    return LocalDate.of(year, month, 1).with(TemporalAdjusters.dayOfWeekInMonth(n, day));
  }

  private static LocalDate last(int year, Month month, DayOfWeek day) {
    return LocalDate.of(year, month, 1).with(TemporalAdjusters.lastInMonth(day));
  }

  // ---------------------------------------------------------- harness

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

  /** The TIMEX value of the single expression in "We met on <name> <year>." */
  private static String resolved(String name, int year) {
    String text = "We met on " + name + ' ' + year + '.';
    List<CoreMap> found =
            EXTRACTOR.extractTimeExpressionCoreMaps(sentenceOf(text), new ArrayCoreMap());
    assertEquals("expressions found in: " + text, 1, found.size());
    Map<String, String> attributes = found.get(0).get(TimeExpression.Annotation.class)
            .getTemporal().getTimexAttributes(new SUTime.TimeIndex());
    assertEquals("type for: " + text, "DATE", attributes.get("type"));
    String value = attributes.get("value");
    assertNotNull("no value for: " + text, value);
    return value;
  }

  /** Check one holiday across the whole year range. */
  private static void checkEveryYear(String name, IntFunction<LocalDate> expected) {
    for (int year = FIRST_YEAR; year <= LAST_YEAR; year++) {
      assertEquals(name + ' ' + year, expected.apply(year).toString(), resolved(name, year));
    }
  }

  // ------------------------------------------------ weekday-of-month holidays

  @Test
  public void testMartinLutherKingDayIsTheThirdMondayInJanuary() {
    checkEveryYear("Martin Luther King Day", y -> nth(y, Month.JANUARY, DayOfWeek.MONDAY, 3));
  }

  @Test
  public void testMemorialDayIsTheLastMondayInMay() {
    checkEveryYear("Memorial Day", y -> last(y, Month.MAY, DayOfWeek.MONDAY));
  }

  @Test
  public void testMothersDayIsTheSecondSundayInMay() {
    checkEveryYear("Mother's Day", y -> nth(y, Month.MAY, DayOfWeek.SUNDAY, 2));
  }

  @Test
  public void testFathersDayIsTheThirdSundayInJune() {
    checkEveryYear("Father's Day", y -> nth(y, Month.JUNE, DayOfWeek.SUNDAY, 3));
  }

  @Test
  public void testLaborDayIsTheFirstMondayInSeptember() {
    checkEveryYear("Labor Day", y -> nth(y, Month.SEPTEMBER, DayOfWeek.MONDAY, 1));
  }

  @Test
  public void testColumbusDayIsTheSecondMondayInOctober() {
    checkEveryYear("Columbus Day", y -> nth(y, Month.OCTOBER, DayOfWeek.MONDAY, 2));
  }

  @Test
  public void testThanksgivingIsTheFourthThursdayInNovember() {
    checkEveryYear("Thanksgiving", y -> nth(y, Month.NOVEMBER, DayOfWeek.THURSDAY, 4));
  }

  // ------------------------------------------------------- movable feasts

  @Test
  public void testEasterFollowsTheGregorianComputus() {
    checkEveryYear("Easter", SUTimeHolidayITest::easter);
  }

  @Test
  public void testGoodFridayIsTwoDaysBeforeEaster() {
    checkEveryYear("Good Friday", y -> easter(y).minusDays(2));
  }

  @Test
  public void testAshWednesdayIsFortySixDaysBeforeEaster() {
    checkEveryYear("Ash Wednesday", y -> easter(y).minusDays(46));
  }

  @Test
  public void testCleanMondayIsFortyEightDaysBeforeEaster() {
    // Clean Monday is an Orthodox observance, but the shipped calendar computes it from
    // the Gregorian date of Easter rather than the Julian one, so it lands 48 days before
    // Western Easter rather than before Orthodox Easter.
    checkEveryYear("Clean Monday", y -> easter(y).minusDays(48));
  }

  // ------------------------------------------------------- sanity checks

  @Test
  public void testTheEasterCalculationMatchesKnownDates() {
    // Guards the algorithm in this file, so a failure elsewhere points at SUTime.
    assertEquals(LocalDate.of(1995, 4, 16), easter(1995));
    assertEquals(LocalDate.of(2000, 4, 23), easter(2000));
    assertEquals(LocalDate.of(2010, 4, 4), easter(2010));
    assertEquals(LocalDate.of(2011, 4, 24), easter(2011));
    assertEquals(LocalDate.of(2024, 3, 31), easter(2024));
    assertEquals(LocalDate.of(2030, 4, 21), easter(2030));
  }

}
