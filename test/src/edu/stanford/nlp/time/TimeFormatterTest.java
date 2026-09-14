package edu.stanford.nlp.time;

import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.tokensregex.types.Value;
import edu.stanford.nlp.util.ArrayCoreMap;
import edu.stanford.nlp.util.CoreMap;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.format.DateTimeFormat;

import org.junit.Test;

import java.util.TimeZone;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Unit tests for {@link TimeFormatter}.
 *
 * <p>Like the other non-ITest files here, these need no models and no pipeline. Every
 * case builds an extractor from a pattern string and feeds it text directly, so the
 * suite runs in milliseconds and compiles with joda-time as the only external
 * dependency.
 *
 * <p>{@code TimeFormatter} is the third place joda types are load-bearing, after
 * {@code JodaTimeUtils} and the {@code SUTime} temporals. It maps custom date-format
 * patterns onto {@code DateTimeFieldType} constants, builds a regex per pattern, and
 * assembles a {@code Partial} from whatever the regex captured. A replacement has to
 * reproduce both the generated regex and the resulting temporal.
 *
 * <h2>What is deliberately pinned</h2>
 *
 * <p>Several behaviours here are wrong rather than merely surprising, and are marked
 * QUIRK: two-digit years never reach a century, a day-of-year field is parsed and then
 * discarded, and an explicit era prefixes the year with a sign. These are asserted as
 * they currently behave so that a rewrite is a deliberate change rather than an accident.
 *
 * <h2>Time zones</h2>
 *
 * <p>{@link TimeFormatter.CustomDateFormatExtractor} yields a {@code PartialTime} and is
 * unaffected by the default zone. The other two extractors yield a {@code GroundedTime}
 * and are not, so those tests either pin the zone on the formatter or set and restore the
 * JVM default around the assertion.
 */
public class TimeFormatterTest {

  // ---------------------------------------------------------------- helpers

  private static TimeFormatter.CustomDateFormatExtractor extractor(String pattern) {
    return new TimeFormatter.CustomDateFormatExtractor(pattern, "en");
  }

  /** Apply a pattern to some text and render whatever temporal comes back. */
  private static String parse(String pattern, String text) {
    return render(extractor(pattern).apply(text));
  }

  private static String render(Value v) {
    if (v == null) {
      return null;
    }
    Object o = v.get();
    return (o instanceof SUTime.Temporal) ? ((SUTime.Temporal) o).toISOString() : String.valueOf(o);
  }

  private static CoreMap textOf(String s) {
    CoreMap m = new ArrayCoreMap();
    m.set(CoreAnnotations.TextAnnotation.class, s);
    return m;
  }

  // ------------------------------------------------------- numeric formats

  @Test
  public void testIsoStyleDates() {
    assertEquals("2017-06-15", parse("yyyy-MM-dd", "2017-06-15"));
    assertEquals("1999-12-31", parse("yyyy-MM-dd", "1999-12-31"));
    assertEquals("2017-06-15", parse("yyyy/MM/dd", "2017/06/15"));
  }

  @Test
  public void testFieldOrderFollowsThePattern() {
    assertEquals("2017-06-15", parse("MM/dd/yyyy", "06/15/2017"));
    assertEquals("1999-12-31", parse("MM/dd/yyyy", "12/31/1999"));
    assertEquals("2017-06-15", parse("dd.MM.yyyy", "15.06.2017"));
  }

  @Test
  public void testPartialDatePatterns() {
    assertEquals("2017", parse("yyyy", "2017"));
    assertEquals("2017-06", parse("yyyy-MM", "2017-06"));
    // With no year in the pattern there is no year in the result.
    assertEquals("XXXX-06-15", parse("MM-dd", "06-15"));
  }

  @Test
  public void testGeneratedRegexIsAnchoredOnWordBoundaries() {
    assertEquals("\\b(\\d\\d\\d\\d)\\Q-\\E(\\d\\d)\\Q-\\E(\\d\\d)\\b",
            extractor("yyyy-MM-dd").getTextPattern().pattern());
    // Literal separators are quoted, so a regex metacharacter in the pattern is inert.
    assertTrue(extractor("yyyy/MM/dd").getTextPattern().pattern().contains("\\Q/\\E"));
  }

  // ---------------------------------------------------------- text formats

  @Test
  public void testTextMonths() {
    assertEquals("1999-12-01", parse("MMMM d, yyyy", "December 1, 1999"));
    assertEquals("1999-12-01", parse("MMM d yyyy", "Dec 1 1999"));
    assertEquals("2017-06-15", parse("MMMM dd, yyyy", "June 15, 2017"));
  }

  @Test
  public void testTextDaysOfWeek() {
    assertEquals("XXXX-WXX-4", parse("EEEE", "Thursday"));
    assertEquals("XXXX-WXX-7", parse("EEEE", "Sunday"));
    assertEquals("XXXX-WXX-4", parse("EEE", "Thu"));
    assertEquals("XXXX-WXX-7", parse("EEE", "Sun"));
  }

  @Test
  public void testNumericDayOfWeek() {
    assertEquals("XXXX-WXX-4", parse("e", "4"));
  }

  // ------------------------------------------------------------- 24h times

  @Test
  public void testTwentyFourHourTimes() {
    assertEquals("T10:30", parse("HH:mm", "10:30"));
    assertEquals("T00:00", parse("HH:mm", "00:00"));
    assertEquals("T23:59", parse("HH:mm", "23:59"));
    assertEquals("T10:30:45", parse("HH:mm:ss", "10:30:45"));
  }

  @Test
  public void testCombinedDateAndTime() {
    assertEquals("2017-06-15T10:30", parse("yyyy-MM-dd HH:mm", "2017-06-15 10:30"));
  }

  @Test
  public void testHourOfHalfdayIsCorrect() {
    // KK is hourOfHalfday (0..11) and goes through a different branch from hh below.
    assertEquals("T10:30", parse("KK:mm a", "10:30 AM"));
    assertEquals("T00:30", parse("KK:mm a", "00:30 AM"));
  }

  @Test
  public void testClockhourOfDay() {
    assertEquals("T10:30", parse("kk:mm", "10:30"));
    assertEquals("T01:00", parse("kk:mm", "01:00"));
    // With no AM/PM marker the halfday normalisation never runs, so 24 keeps its
    // clockhour rather than folding to hour 0.
    assertEquals("T24:00", parse("kk:mm", "24:00"));
  }

  @Test
  public void testClockhourOfDayWithAHalfdayMarker() {
    // Adding a marker sends the value through JodaTimeUtils.combine. See
    // JodaTimeUtilsTest.testCombineNormalisesClockhourOfDay.
    assertEquals("T10:30", parse("kk:mm a", "10:30 AM"));
    assertEquals("T22:30", parse("kk:mm a", "10:30 PM"));
    assertEquals("T00:00", parse("kk:mm a", "12:00 AM"));
    assertEquals("T12:00", parse("kk:mm a", "12:00 PM"));
  }

  // ------------------------------------------------------------- 12h times

  @Test
  public void testTwelveHourClock() {
    // hh is clockhourOfHalfday; the conversion to a 24-hour clock happens in
    // JodaTimeUtils.combine. See JodaTimeUtilsTest.testCombineNormalisesClockhourOfHalfday.
    assertEquals("T10:30", parse("hh:mm a", "10:30 AM"));
    assertEquals("T22:30", parse("hh:mm a", "10:30 PM"));
    assertEquals("T01:00", parse("hh:mm a", "01:00 AM"));
    assertEquals("T11:00", parse("hh:mm a", "11:00 AM"));
    assertEquals("T23:00", parse("hh:mm a", "11:00 PM"));
  }

  @Test
  public void testTwelveHourMidnightAndNoon() {
    assertEquals("T00:30", parse("hh:mm a", "12:30 AM"));
    assertEquals("T12:30", parse("hh:mm a", "12:30 PM"));
  }

  @Test
  public void testAmPmMarkerIsCaseInsensitive() {
    assertEquals("T13:05", parse("h:mm a", "1:05 pm"));
  }

  // -------------------------------------------------- fixed-width tokens

  @Test
  public void testSingleLetterTokensMatchOneOrTwoDigits() {
    // A one-character field token means "one or two digits", as it does in
    // SimpleDateFormat, so "MMMM d, yyyy" parses ordinary dates of the month.
    assertEquals("\\b(\\d{1,2})\\b", extractor("d").getTextPattern().pattern());
    assertEquals("XXXX-XX-05", parse("d", "5"));
    assertEquals("XXXX-XX-15", parse("d", "15"));
    assertEquals("2017-06-15", parse("MMMM d, yyyy", "June 15, 2017"));
    assertEquals("1999-12-01", parse("MMMM d, yyyy", "December 1, 1999"));
    // The two-character form is still fixed width.
    assertEquals("\\b(\\d\\d)\\b", extractor("dd").getTextPattern().pattern());
    assertNull(parse("dd", "5"));
  }

  @Test
  public void testAnExplicitQuantifierOverridesTheDefaultWidth() {
    // When the pattern supplies its own quantifier it is appended to the field's regex,
    // so the field must not also widen itself: "S{1,3}" has to come out as \d{1,3} and
    // not as \d{1,2}{1,3}. The shipped ISO rules depend on this.
    assertEquals("\\b(\\d{1,3})\\b", extractor("S{1,3}").getTextPattern().pattern());
    assertEquals("2017-06-15T10:30:45.123",
            parse("yyyy-MM-dd'T'HH:mm:ss[.,]S{1,3}", "2017-06-15T10:30:45.123"));
    // The optional-second-digit idiom used throughout the rule files still works.
    assertEquals("\\b(\\d\\d?)\\b", extractor("dd?").getTextPattern().pattern());
    assertEquals("XXXX-XX-05", parse("dd?", "5"));
    assertEquals("XXXX-XX-15", parse("dd?", "15"));
  }

  // -------------------------------------------------------- two-digit years

  @Test
  public void testTwoDigitYearsAreNotResolved() {
    // QUIRK: yy captures a yearOfCentury and nothing ever supplies the century, so the
    // year renders with an X-padded prefix instead of resolving against a pivot. The
    // pattern builder does compute a pivot for the joda formatter, but the regex path
    // used by apply() never consults it.
    assertEquals("XX17-06-15", parse("yy-MM-dd", "17-06-15"));
    assertEquals("XX99-12-31", parse("yy-MM-dd", "99-12-31"));
    assertEquals("XX17-06-15", parse("MM/dd/yy", "06/15/17"));
  }

  // ------------------------------------------------ week and ordinal dates

  @Test
  public void testWeekOfYear() {
    assertEquals("2017-W24", parse("yyyy-'W'ww", "2017-W24"));
  }

  @Test
  public void testDayOfYearIsParsedThenDiscarded() {
    // QUIRK: the regex captures the day of year and the value is accepted, but nothing
    // survives into the temporal, so an ordinal date degrades to a bare year.
    assertEquals("2017", parse("yyyy-DDD", "2017-166"));
  }

  // ----------------------------------------------------- era and century

  @Test
  public void testEra() {
    // QUIRK: an explicit era prefixes the year with a sign, so the rendering differs
    // from the same year parsed without one ("2017").
    assertEquals("+2017", parse("yyyy G", "2017 AD"));
    // A BC year of fewer than four digits cannot match, since yyyy is fixed width.
    assertNull(parse("yyyy G", "44 BC"));
  }

  @Test
  public void testCenturyOfEra() {
    assertEquals("20XX", parse("CC", "20"));
  }

  // --------------------------------------------------------- time zones

  @Test
  public void testNumericTimeZoneOffsets() {
    assertEquals("2017-06-15T10:30+0100", parse("yyyy-MM-dd HH:mm Z", "2017-06-15 10:30 +0100"));
    assertEquals("2017-06-15T10:30-0800", parse("yyyy-MM-dd HH:mm Z", "2017-06-15 10:30 -0800"));
    // ZZ accepts the colon-separated form and renders it the same way.
    assertEquals("2017-06-15T10:30+0100", parse("yyyy-MM-dd HH:mm ZZ", "2017-06-15 10:30 +01:00"));
  }

  @Test
  public void testNamedTimeZones() {
    assertEquals("2017-06-15T10:30-0800", parse("yyyy-MM-dd HH:mm z", "2017-06-15 10:30 PST"));
    assertEquals("2017-06-15T10:30+0000", parse("yyyy-MM-dd HH:mm z", "2017-06-15 10:30 UTC"));
  }

  @Test
  public void testQuotedLiteralBetweenNumericFields() {
    // The pattern shipped in english.sutime.txt for French-style times such as "10h30".
    // The h has to be quoted: an unquoted h is the clockhourOfHalfday field, and "''"
    // is an escaped apostrophe rather than a literal h.
    assertEquals("\\b(\\d\\d?)\\Qh\\E(\\d\\d)\\b", extractor("HH?'h'mm").getTextPattern().pattern());
    assertEquals("T10:30", parse("HH?'h'mm", "10h30"));
    assertEquals("T09:30", parse("HH?'h'mm", "9h30"));
    assertEquals("T05:05", parse("HH?'h'mm", "5h05"));
    assertEquals("T00:00", parse("HH?'h'mm", "00h00"));
    assertEquals("T23:59", parse("HH?'h'mm", "23h59"));
    // Quoted literals are matched case insensitively, like the rest of the pattern.
    assertEquals("T10:30", parse("HH?'h'mm", "10H30"));
  }

  @Test
  public void testQuotedLiteralPatternRejectsNearMisses() {
    // Word boundaries and field widths keep the pattern from firing on neighbouring text.
    assertNull("minutes are two digits", parse("HH?'h'mm", "10h3"));
    assertNull(parse("HH?'h'mm", "10h300"));
    assertNull(parse("HH?'h'mm", "1000h30"));
    assertNull("hours run 0..23", parse("HH?'h'mm", "25h30"));
    assertNull("minutes run 0..59", parse("HH?'h'mm", "10h60"));
    assertNull(parse("HH?'h'mm", "x10h30"));
    assertNull(parse("HH?'h'mm", "10h30x"));
  }

  @Test
  public void testUnquotedHourFieldIsNotALiteral() {
    // Kept as a reminder of what the broken form of the rule above actually meant:
    // "''" is an escaped apostrophe and the following h is a field, so the pattern
    // wanted an apostrophe in the text and read the next digits as an hour.
    assertEquals("\\b(\\d\\d?)\\Q'\\E(\\d{1,2})(\\d\\d)\\b",
            extractor("HH?''hmm").getTextPattern().pattern());
    assertNull(parse("HH?''hmm", "10h30"));
  }

  // ----------------------------------------- literals, groups, alternation

  @Test
  public void testLiteralText() {
    assertEquals("2017-06-15", parse("'on' yyyy-MM-dd", "on 2017-06-15"));
    assertNull("the literal is required, not optional", parse("'on' yyyy-MM-dd", "2017-06-15"));
  }

  @Test
  public void testOptionalGroups() {
    assertEquals("2017-06-15", parse("yyyy-MM-dd( HH:mm)?", "2017-06-15"));
    assertEquals("2017-06-15T10:30", parse("yyyy-MM-dd( HH:mm)?", "2017-06-15 10:30"));
  }

  @Test
  public void testAlternation() {
    assertEquals("2017-06-15", parse("MM/dd/yyyy|dd.MM.yyyy", "06/15/2017"));
  }

  // ------------------------------------------------------ non-matching input

  @Test
  public void testNonMatchingInputReturnsNull() {
    assertNull(parse("yyyy-MM-dd", ""));
    assertNull(parse("yyyy-MM-dd", "not a date"));
    assertNull("separators are required", parse("yyyy-MM-dd", "20170615"));
    assertNull("single-digit components do not match a fixed-width pattern",
            parse("yyyy-MM-dd", "2017-6-15"));
  }

  @Test
  public void testOutOfRangeFieldsAreRejected() {
    // The regex matches but the field values are out of range, so apply gives up.
    assertNull(parse("yyyy-MM-dd", "2017-13-45"));
  }

  // ------------------------------------------------------------ value shape

  @Test
  public void testCustomExtractorProducesAPartialTime() {
    Value v = extractor("yyyy-MM-dd").apply("2017-06-15");
    assertNotNull(v);
    assertEquals("Temporal", v.getType());
    assertTrue(v.get() instanceof SUTime.PartialTime);
  }

  // ------------------------------------------------------- joda extractor

  @Test
  public void testJodaExtractorProducesAGroundedTime() {
    TimeFormatter.JodaDateTimeFormatExtractor ex = new TimeFormatter.JodaDateTimeFormatExtractor(
            DateTimeFormat.forPattern("yyyy-MM-dd").withZoneUTC());
    Value v = ex.apply(textOf("2017-06-15"));
    assertNotNull(v);
    assertEquals("GroundedTime", v.getType());
    assertTrue(v.get() instanceof SUTime.GroundedTime);
    assertEquals("2017-06-15T00:00:00.000Z", ((SUTime.GroundedTime) v.get()).toISOString());
  }

  @Test
  public void testJodaExtractorWithTime() {
    TimeFormatter.JodaDateTimeFormatExtractor ex = new TimeFormatter.JodaDateTimeFormatExtractor(
            DateTimeFormat.forPattern("yyyy-MM-dd HH:mm").withZoneUTC());
    assertEquals("2017-06-15T10:30:00.000Z",
            ((SUTime.GroundedTime) ex.apply(textOf("2017-06-15 10:30")).get()).toISOString());
  }

  @Test
  public void testJodaExtractorRejectsUnparseableText() {
    TimeFormatter.JodaDateTimeFormatExtractor ex =
            new TimeFormatter.JodaDateTimeFormatExtractor("yyyy-MM-dd");
    assertNull(ex.apply(textOf("nonsense")));
    assertNull(ex.apply(textOf("")));
  }

  @Test
  public void testJodaExtractorFromPatternStringUsesTheDefaultZone() {
    // The String constructor takes whatever zone is current, so pin it for the assertion.
    DateTimeZone previous = DateTimeZone.getDefault();
    try {
      DateTimeZone.setDefault(DateTimeZone.UTC);
      TimeFormatter.JodaDateTimeFormatExtractor ex =
              new TimeFormatter.JodaDateTimeFormatExtractor("yyyy-MM-dd");
      assertEquals("2017-06-15T00:00:00.000Z",
              ((SUTime.GroundedTime) ex.apply(textOf("2017-06-15")).get()).toISOString());
    } finally {
      DateTimeZone.setDefault(previous);
    }
  }

  // ------------------------------------------------------- java extractor

  @Test
  public void testJavaExtractorProducesAGroundedTime() {
    TimeFormatter.JavaDateFormatExtractor ex =
            new TimeFormatter.JavaDateFormatExtractor("yyyy-MM-dd");
    Value v = ex.apply(textOf("2017-06-15"));
    assertNotNull(v);
    assertEquals("GroundedTime", v.getType());
    assertTrue(v.get() instanceof SUTime.GroundedTime);
  }

  @Test
  public void testJavaExtractorParsesInTheDefaultZone() {
    // SimpleDateFormat reads the JVM default zone rather than joda's, so both are set
    // here. The instant that results is zone-dependent; that is the behaviour, not a
    // detail of the test.
    TimeZone previousJvm = TimeZone.getDefault();
    DateTimeZone previousJoda = DateTimeZone.getDefault();
    try {
      TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
      DateTimeZone.setDefault(DateTimeZone.UTC);
      TimeFormatter.JavaDateFormatExtractor ex =
              new TimeFormatter.JavaDateFormatExtractor("yyyy-MM-dd");
      SUTime.GroundedTime t = (SUTime.GroundedTime) ex.apply(textOf("2017-06-15")).get();
      assertEquals(new DateTime("2017-06-15T00:00:00Z", DateTimeZone.UTC).toInstant(),
              t.getJodaTimeInstant());
    } finally {
      TimeZone.setDefault(previousJvm);
      DateTimeZone.setDefault(previousJoda);
    }
  }

  @Test
  public void testJavaExtractorRejectsUnparseableText() {
    TimeFormatter.JavaDateFormatExtractor ex =
            new TimeFormatter.JavaDateFormatExtractor("yyyy-MM-dd");
    assertNull(ex.apply(textOf("nonsense")));
  }

}
