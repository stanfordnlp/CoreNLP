package edu.stanford.nlp.time;

import edu.stanford.nlp.time.SUTime.Duration;
import edu.stanford.nlp.time.SUTime.DurationRange;
import edu.stanford.nlp.time.SUTime.DurationWithMillis;
import edu.stanford.nlp.time.SUTime.InexactDuration;
import edu.stanford.nlp.time.SUTime.IsoDate;
import edu.stanford.nlp.time.SUTime.PartialTime;
import edu.stanford.nlp.time.SUTime.Range;
import edu.stanford.nlp.time.SUTime.StandardTemporalType;
import edu.stanford.nlp.time.SUTime.Time;
import edu.stanford.nlp.time.SUTime.TimexType;

import org.joda.time.DateTimeFieldType;
import org.joda.time.Partial;
import org.joda.time.Period;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Unit tests for the {@link SUTime} temporal classes: {@link PartialTime} and
 * {@link IsoDate}, the {@link Range} and {@link Duration} types, and reference
 * resolution.
 *
 * <p>Despite living next to {@code SUTimeITest}, these need no models, no rule files
 * and no annotation pipeline: every temporal here is built directly from a
 * {@code Partial} or from the constants on {@code SUTime}. They compile against the
 * source tree with joda-time as the only external dependency.
 *
 * <p>Their purpose is to pin down the layer that a joda-time replacement will move.
 * {@code PartialTime} carries a {@code Partial} as its state and nearly every method
 * here reads or rebuilds it, so a swapped-in {@code Partial} shows up first in these
 * strings. The ITests cover the same ground end to end, but when one of them breaks it
 * reports that a sentence changed, not which operation changed.
 *
 * <p>Assertions are on the ISO and TIMEX strings, since those are what SUTime promises
 * its callers and what a reimplementation has to reproduce. Behaviour that looks wrong
 * is marked QUIRK and pinned as it currently stands: these tests are here to notice a
 * change, not to endorse one.
 *
 * <p>The reference date throughout is Thursday 15 June 2017, chosen so that
 * day-of-week resolution has room on both sides within the same month.
 */
public class SUTimeTemporalTest {

  private static final DateTimeFieldType YEAR = DateTimeFieldType.year();
  private static final DateTimeFieldType MONTH = DateTimeFieldType.monthOfYear();
  private static final DateTimeFieldType DAY = DateTimeFieldType.dayOfMonth();
  private static final DateTimeFieldType HOUR = DateTimeFieldType.hourOfDay();
  private static final DateTimeFieldType MINUTE = DateTimeFieldType.minuteOfHour();
  private static final DateTimeFieldType SECOND = DateTimeFieldType.secondOfMinute();
  private static final DateTimeFieldType DOW = DateTimeFieldType.dayOfWeek();
  private static final DateTimeFieldType WEEK = DateTimeFieldType.weekOfWeekyear();
  private static final DateTimeFieldType YOC = DateTimeFieldType.yearOfCentury();
  private static final DateTimeFieldType CENTURY = DateTimeFieldType.centuryOfEra();

  /** Thursday 15 June 2017. */
  private static final PartialTime REFERENCE = time(YEAR, 2017, MONTH, 6, DAY, 15);

  /** Thursday 15 June 2017, 10:30. */
  private static final PartialTime REFERENCE_WITH_TIME =
          time(YEAR, 2017, MONTH, 6, DAY, 15, HOUR, 10, MINUTE, 30);

  // ---------------------------------------------------------------- helpers

  private static Partial partial(Object... fieldsAndValues) {
    Partial p = new Partial();
    for (int i = 0; i < fieldsAndValues.length; i += 2) {
      p = p.with((DateTimeFieldType) fieldsAndValues[i], (Integer) fieldsAndValues[i + 1]);
    }
    return p;
  }

  private static PartialTime time(Object... fieldsAndValues) {
    return new PartialTime(partial(fieldsAndValues));
  }

  /** Assert the ISO rendering, which is also what toString gives. */
  private static void assertIso(String expected, Time t) {
    assertEquals(expected, t.toISOString());
  }

  private static void assertResolvesTo(String expected, Time t, int flags) {
    assertEquals(expected, t.resolve(REFERENCE, flags).toISOString());
  }

  // ------------------------------------------------- PartialTime rendering

  @Test
  public void testFullySpecifiedDate() {
    PartialTime t = time(YEAR, 2017, MONTH, 6, DAY, 15);
    assertIso("2017-06-15", t);
    assertEquals("2017-06-15", t.getTimexValue());
    assertEquals(TimexType.DATE, t.getTimexType());
    assertEquals("P1D", t.getGranularity().getTimexValue());
  }

  @Test
  public void testCoarserDates() {
    assertIso("2017-06", time(YEAR, 2017, MONTH, 6));
    assertIso("2017", time(YEAR, 2017));
    assertEquals("P1M", time(YEAR, 2017, MONTH, 6).getGranularity().getTimexValue());
    assertEquals("P1Y", time(YEAR, 2017).getGranularity().getTimexValue());
  }

  @Test
  public void testMissingFieldsRenderAsX() {
    assertIso("XXXX-06", time(MONTH, 6));
    assertIso("XXXX-06-15", time(MONTH, 6, DAY, 15));
    assertIso("XXXX-XX-15", time(DAY, 15));
    assertIso("XXXX", time());
  }

  @Test
  public void testTimesOfDay() {
    PartialTime t = time(YEAR, 2017, MONTH, 6, DAY, 15, HOUR, 10, MINUTE, 30);
    assertIso("2017-06-15T10:30", t);
    assertEquals("Adding a time field promotes DATE to TIME", TimexType.TIME, t.getTimexType());
    assertEquals("PT1M", t.getGranularity().getTimexValue());
    assertIso("2017-06-15T10:30:45",
            time(YEAR, 2017, MONTH, 6, DAY, 15, HOUR, 10, MINUTE, 30, SECOND, 45));
    assertEquals("PT1S",
            time(YEAR, 2017, MONTH, 6, DAY, 15, HOUR, 10, MINUTE, 30, SECOND, 45)
                    .getGranularity().getTimexValue());
    // A bare time of day carries no date at all.
    assertIso("T10:30", time(HOUR, 10, MINUTE, 30));
    assertEquals(TimexType.TIME, time(HOUR, 10, MINUTE, 30).getTimexType());
  }

  @Test
  public void testWeekBasedDates() {
    assertIso("2017-W24", time(YEAR, 2017, WEEK, 24));
    assertEquals("P1W", time(YEAR, 2017, WEEK, 24).getGranularity().getTimexValue());
    assertIso("2017-W24-4", time(YEAR, 2017, WEEK, 24, DOW, 4));
    assertEquals("P1D", time(YEAR, 2017, WEEK, 24, DOW, 4).getGranularity().getTimexValue());
    // A bare day of week has neither a week number nor a year.
    assertIso("XXXX-WXX-4", time(DOW, 4));
  }

  @Test
  public void testCenturyAndYearOfCenturyCollapseToAYear() {
    assertIso("2017", time(CENTURY, 20, YOC, 17));
  }

  @Test
  public void testPadUnknownFormatting() {
    assertEquals("2017-06XX",
            time(YEAR, 2017, MONTH, 6).toFormattedString(SUTime.FORMAT_ISO | SUTime.FORMAT_PAD_UNKNOWN));
    assertEquals("2017-06-15T10:30:XX", REFERENCE_WITH_TIME
            .toFormattedString(SUTime.FORMAT_ISO | SUTime.FORMAT_PAD_UNKNOWN));
    // A year is already as padded as it gets; nothing is appended.
    assertEquals("2017",
            time(YEAR, 2017).toFormattedString(SUTime.FORMAT_ISO | SUTime.FORMAT_PAD_UNKNOWN));
  }

  @Test
  public void testPartialTimeIsNeverGrounded() {
    assertFalse(REFERENCE.isGrounded());
    assertFalse(REFERENCE_WITH_TIME.isGrounded());
  }

  // ------------------------------------------- quarters, half years, decades

  @Test
  public void testQuarterPartial() {
    PartialTime q3 = new PartialTime(partial(YEAR, 2017).with(JodaTimeUtils.QuarterOfYear, 3));
    // QUIRK: the ISO rendering drops the quarter entirely and reports the bare year,
    // while the TIMEX value keeps it. The two disagree about what this temporal is.
    assertIso("2017", q3);
    assertEquals("2017-Q3", q3.getTimexValue());
    assertEquals(TimexType.DATE, q3.getTimexType());
    assertEquals("P3M", q3.getGranularity().getTimexValue());
    assertEquals("2017-07-01/2017-09-30", q3.getRange().toISOString());
  }

  @Test
  public void testHalfYearPartial() {
    PartialTime h2 = new PartialTime(partial(YEAR, 2017).with(JodaTimeUtils.HalfYearOfYear, 2));
    // Same divergence as the quarter case above.
    assertIso("2017", h2);
    assertEquals("2017-H2", h2.getTimexValue());
    assertEquals("P6M", h2.getGranularity().getTimexValue());
  }

  @Test
  public void testDecadePartial() {
    PartialTime nineties =
            new PartialTime(partial(CENTURY, 19).with(JodaTimeUtils.DecadeOfCentury, 9));
    assertIso("199X", nineties);
    assertEquals("199X", nineties.getTimexValue());
    assertEquals("P10Y", nineties.getGranularity().getTimexValue());
    assertEquals("1990-01-01/1999-12-31", nineties.getRange().toISOString());
  }

  // ----------------------------------------------------- ranges of partials

  @Test
  public void testRangeOfAPartialCoversItsGranularity() {
    assertEquals("2017-06-01/2017-06-30", time(YEAR, 2017, MONTH, 6).getRange().toISOString());
    assertEquals("2017-01-01/2017-12-31", time(YEAR, 2017).getRange().toISOString());
    assertEquals("2017-06-12/2017-06-18", time(YEAR, 2017, WEEK, 24).getRange().toISOString());
    assertEquals("P1M", time(YEAR, 2017, MONTH, 6).getRange().getTimexValue());
    assertEquals("P1Y", time(YEAR, 2017).getRange().getTimexValue());
  }

  @Test
  public void testRangeOfAnEmptyPartialThrows() {
    // QUIRK: an empty partial has no most-specific field, and getRange dereferences it
    // without checking. Everything else on an empty PartialTime works ("XXXX"), so this
    // is an unguarded path rather than a deliberate rejection.
    try {
      time().getRange();
      fail("expected getRange on an empty partial to throw");
    } catch (NullPointerException expected) {
      // current behaviour
    }
  }

  // ---------------------------------------------------------------- IsoDate

  @Test
  public void testIsoDateFromNumbers() {
    assertIso("2017-06-15", new IsoDate(2017, 6, 15));
    assertEquals("2017-06-15", new IsoDate(2017, 6, 15).getTimexValue());
    // -1 marks a field as unspecified.
    assertIso("XXXX-06-15", new IsoDate(-1, 6, 15));
    assertIso("XXXX-06", new IsoDate(-1, 6, -1));
    assertIso("2017", new IsoDate(2017, -1, -1));
  }

  @Test
  public void testIsoDateFromStrings() {
    assertIso("2017-06-15", new IsoDate("2017", "06", "15"));
    assertIso("XXXX-06-15", new IsoDate("XXXX", "06", "15"));
  }

  @Test
  public void testIsoDateRejectsTwoDigitYears() {
    // A two-digit year has to be resolved against a reference before it gets here.
    try {
      new IsoDate("17", "06", "15");
      fail("expected a two-digit year to be rejected");
    } catch (IllegalArgumentException expected) {
      assertTrue(expected.getMessage().contains("17"));
    }
  }

  @Test
  public void testIsoDateRange() {
    assertEquals("P1D", new IsoDate(2017, 6, 15).getRange().getTimexValue());
  }

  // ------------------------------------------------------- named constants

  @Test
  public void testMonthConstants() {
    assertIso("XXXX-06", SUTime.JUNE);
    assertEquals("XXXX-06", SUTime.JUNE.getTimexValue());
    assertEquals(StandardTemporalType.MONTH_OF_YEAR, SUTime.JUNE.getStandardTemporalType());
    assertIso("XXXX-01", SUTime.JANUARY);
    assertIso("XXXX-12", SUTime.DECEMBER);
  }

  @Test
  public void testDayOfWeekConstants() {
    assertIso("XXXX-WXX-1", SUTime.MONDAY);
    assertIso("XXXX-WXX-7", SUTime.SUNDAY);
    assertEquals(StandardTemporalType.DAY_OF_WEEK, SUTime.MONDAY.getStandardTemporalType());
  }

  @Test
  public void testReferenceConstants() {
    assertEquals("PRESENT_REF", SUTime.TIME_NOW.getTimexValue());
    assertEquals("PRESENT_REF", SUTime.TIME_PRESENT.getTimexValue());
    assertEquals("PAST_REF", SUTime.TIME_PAST.getTimexValue());
    assertEquals("FUTURE_REF", SUTime.TIME_FUTURE.getTimexValue());
  }

  @Test
  public void testSymbolicConstants() {
    assertIso("WE", SUTime.WEEKEND);
    assertIso("WD", SUTime.WEEKDAY);
    assertIso("SP", SUTime.SPRING_EQUINOX);
    assertIso("SU", SUTime.SUMMER);
    assertIso("WI", SUTime.WINTER);
    assertIso("MO", SUTime.MORNING);
    assertIso("NI", SUTime.NIGHT);
    // TIMEX spells midday MI, and NOON is an alias for it.
    assertIso("MI", SUTime.NOON);
    assertIso("T00:00", SUTime.MIDNIGHT);
  }

  // ------------------------------------------------------------ resolution

  @Test
  public void testResolveMonthWithoutFlagsStaysInTheReferenceYear() {
    assertResolvesTo("2017-06", SUTime.JUNE, 0);
    assertResolvesTo("2017-01", SUTime.JANUARY, 0);
  }

  @Test
  public void testResolveMonthToPast() {
    // June is the reference month, so the past reading is the reference itself.
    assertResolvesTo("2017-06", SUTime.JUNE, SUTime.RESOLVE_TO_PAST);
    assertResolvesTo("2017-01", SUTime.JANUARY, SUTime.RESOLVE_TO_PAST);
    assertResolvesTo("2016-12", SUTime.DECEMBER, SUTime.RESOLVE_TO_PAST);
  }

  @Test
  public void testResolveMonthToFuture() {
    assertResolvesTo("2018-06", SUTime.JUNE, SUTime.RESOLVE_TO_FUTURE);
    assertResolvesTo("2018-01", SUTime.JANUARY, SUTime.RESOLVE_TO_FUTURE);
    assertResolvesTo("2017-12", SUTime.DECEMBER, SUTime.RESOLVE_TO_FUTURE);
  }

  @Test
  public void testResolveMonthToClosest() {
    assertResolvesTo("2017-06", SUTime.JUNE, SUTime.RESOLVE_TO_CLOSEST);
  }

  @Test
  public void testResolveDayOfWeek() {
    // The reference week runs Monday 12 June to Sunday 18 June.
    assertResolvesTo("2017-06-12", SUTime.MONDAY, 0);
    assertResolvesTo("2017-06-12", SUTime.MONDAY, SUTime.RESOLVE_TO_PAST);
    assertResolvesTo("2017-06-19", SUTime.MONDAY, SUTime.RESOLVE_TO_FUTURE);
    // The reference is a Thursday, so the previous Friday is in the week before.
    assertResolvesTo("2017-06-09", SUTime.FRIDAY, SUTime.RESOLVE_TO_PAST);
    assertResolvesTo("2017-06-16", SUTime.FRIDAY, SUTime.RESOLVE_TO_FUTURE);
  }

  @Test
  public void testResolveFillsInCoarserFieldsFromTheReference() {
    assertResolvesTo("2017-06-15", time(DAY, 15), 0);
    assertResolvesTo("2017-03-01", time(MONTH, 3, DAY, 1), 0);
    assertEquals("2017-06-15T14:00",
            time(HOUR, 14, MINUTE, 0).resolve(REFERENCE_WITH_TIME, 0).toISOString());
  }

  @Test
  public void testResolveLeavesCompleteDatesAlone() {
    assertEquals("2017-06-15",
            REFERENCE.resolve(time(YEAR, 1999, MONTH, 1, DAY, 1), 0).toISOString());
  }

  @Test
  public void testRelativeDayConstants() {
    assertResolvesTo("2017-06-15", SUTime.TODAY, 0);
    assertResolvesTo("2017-06-16", SUTime.TOMORROW, 0);
    assertResolvesTo("2017-06-14", SUTime.YESTERDAY, 0);
  }

  // ------------------------------------------------------- time arithmetic

  @Test
  public void testAddAndSubtractDays() {
    assertIso("2017-06-16", REFERENCE.add(SUTime.DAY));
    assertIso("2017-06-14", REFERENCE.subtract(SUTime.DAY));
    assertIso("2017-06-18", REFERENCE.add(SUTime.DAY.multiplyBy(3)));
    assertIso("2017-06-22", REFERENCE.add(SUTime.WEEK));
  }

  @Test
  public void testAddCalendarUnits() {
    assertIso("2017-07-15", REFERENCE.add(SUTime.MONTH));
    assertIso("2018-06-15", REFERENCE.add(SUTime.YEAR));
    // Adding a month at month granularity rolls the year over.
    assertIso("2018-01", time(YEAR, 2017, MONTH, 12).add(SUTime.MONTH));
    assertIso("2018", time(YEAR, 2017).add(SUTime.YEAR));
  }

  @Test
  public void testAddingBelowTheGranularityIsInvisible() {
    // QUIRK: the hour is added, but the partial has no hour field to render it in, so
    // the result is indistinguishable from the input.
    assertIso("2017-06-15", REFERENCE.add(SUTime.HOUR));
  }

  @Test
  public void testAddTimeUnits() {
    assertIso("2017-06-15T11:30", REFERENCE_WITH_TIME.add(SUTime.HOUR));
    assertIso("2017-06-15T11:15", REFERENCE_WITH_TIME.add(SUTime.MINUTE.multiplyBy(45)));
  }

  // ------------------------------------------------------------- durations

  @Test
  public void testDurationConstants() {
    assertEquals("P1Y", SUTime.YEAR.getTimexValue());
    assertEquals("P1M", SUTime.MONTH.getTimexValue());
    assertEquals("P1W", SUTime.WEEK.getTimexValue());
    assertEquals("P1D", SUTime.DAY.getTimexValue());
    assertEquals("PT1H", SUTime.HOUR.getTimexValue());
    assertEquals("PT1M", SUTime.MINUTE.getTimexValue());
    assertEquals("PT1S", SUTime.SECOND.getTimexValue());
    assertEquals("PT0.001S", SUTime.MILLIS.getTimexValue());
  }

  @Test
  public void testCompoundDurationConstants() {
    // Quarters and half years are carried as months, not as the custom joda field types.
    assertEquals("P3M", SUTime.QUARTER.getTimexValue());
    assertEquals("P6M", SUTime.HALFYEAR.getTimexValue());
    assertEquals("P2W", SUTime.FORTNIGHT.getTimexValue());
    assertEquals("PT30M", SUTime.HALFHOUR.getTimexValue());
    assertEquals("PT15M", SUTime.QUARTERHOUR.getTimexValue());
    assertEquals("P10Y", SUTime.DECADE.getTimexValue());
    assertEquals("P100Y", SUTime.CENTURY.getTimexValue());
    assertEquals("P1000Y", SUTime.MILLENNIUM.getTimexValue());
  }

  @Test
  public void testDurationsExposeTheirJodaPeriod() {
    assertEquals(Period.years(1), SUTime.YEAR.getJodaTimePeriod());
    assertEquals(Period.days(1), SUTime.DAY.getJodaTimePeriod());
    assertEquals(Period.months(3), SUTime.QUARTER.getJodaTimePeriod());
    assertEquals(TimexType.DURATION, SUTime.DAY.getTimexType());
  }

  @Test
  public void testUnknownAndZeroDurations() {
    assertEquals("PT0S", SUTime.DURATION_NONE.getTimexValue());
    assertEquals("PXX", SUTime.DURATION_UNKNOWN.getTimexValue());
    assertNull("an unknown duration has no period behind it",
            SUTime.DURATION_UNKNOWN.getJodaTimePeriod());
  }

  @Test
  public void testDurationArithmetic() {
    assertEquals("P3D", SUTime.DAY.multiplyBy(3).getTimexValue());
    assertEquals("P3M", SUTime.MONTH.multiplyBy(3).getTimexValue());
    assertEquals("PT12H", SUTime.DAY.divideBy(2).getTimexValue());
    assertEquals("P1Y1M", SUTime.YEAR.add(SUTime.MONTH).getTimexValue());
    assertEquals("P1W1D", SUTime.WEEK.add(SUTime.DAY).getTimexValue());
    assertEquals("PT1H1M", SUTime.HOUR.add(SUTime.MINUTE).getTimexValue());
  }

  @Test
  public void testDurationArithmeticDoesNotNormalise() {
    // QUIRK: subtraction and negative multipliers leave the sign on the individual
    // field rather than reducing the period, so these are not valid ISO 8601.
    assertEquals("P1Y-1M", SUTime.YEAR.subtract(SUTime.MONTH).getTimexValue());
    assertEquals("P-2D", SUTime.DAY.multiplyBy(-2).getTimexValue());
    // Multiplying by zero does collapse, but to a time-valued zero rather than P0D.
    assertEquals("PT0S", SUTime.DAY.multiplyBy(0).getTimexValue());
  }

  @Test
  public void testInexactDuration() {
    assertEquals("PXD", new InexactDuration(SUTime.DAY.getJodaTimePeriod()).getTimexValue());
  }

  @Test
  public void testDurationRangeHasNoTimexValue() {
    // QUIRK: a range of durations ("two to three days") produces no TIMEX value at all,
    // so the information is dropped on the way out.
    assertNull(new DurationRange(SUTime.DAY, SUTime.WEEK).getTimexValue());
  }

  @Test
  public void testDurationWithMillis() {
    assertEquals("PT1M30S", new DurationWithMillis(90000L).getTimexValue());
  }

  // ----------------------------------------------------------------- Range

  @Test
  public void testRangeEndpoints() {
    Range june = new Range(new IsoDate(2017, 6, 1), new IsoDate(2017, 6, 30));
    assertEquals("2017-06-01/2017-06-30", june.toISOString());
    assertIso("2017-06-01", june.begin());
    assertIso("2017-06-30", june.end());
    assertIso("2017-06-01", june.beginTime());
    assertIso("2017-06-30", june.endTime());
    assertIso("2017-06-15T12", june.mid());
    assertFalse(june.isGrounded());
  }

  @Test
  public void testRangeReportsItselfAsADuration() {
    // QUIRK: a Range between two dates is typed DURATION and its TIMEX value is the
    // elapsed hours, because the value is computed from a millisecond difference. A
    // 30-day month comes out as PT696H rather than anything month-shaped.
    Range june = new Range(new IsoDate(2017, 6, 1), new IsoDate(2017, 6, 30));
    assertEquals(TimexType.DURATION, june.getTimexType());
    assertEquals("PT696H", june.getTimexValue());
    assertEquals("PT696H", june.getDuration().getTimexValue());
    assertEquals("PT24H",
            new Range(new IsoDate(2017, 6, 15), new IsoDate(2017, 6, 16)).getTimexValue());
    assertEquals("PT168H",
            new Range(new IsoDate(2017, 6, 12), new IsoDate(2017, 6, 19)).getTimexValue());
    // Endpoints that are equal give a zero-length range rather than one day.
    assertEquals("PT0S",
            new Range(new IsoDate(2017, 6, 15), new IsoDate(2017, 6, 15)).getTimexValue());
  }

  @Test
  public void testRangeContains() {
    Range june = new Range(new IsoDate(2017, 6, 1), new IsoDate(2017, 6, 30));
    Range year = new Range(new IsoDate(2017, 1, 1), new IsoDate(2017, 12, 31));
    assertTrue(year.contains(june));
    assertFalse(june.contains(year));
    assertTrue(june.contains(june));
    assertTrue(june.contains(new IsoDate(2017, 6, 15)));
    assertFalse(june.contains(new IsoDate(2017, 7, 15)));
  }

  @Test
  public void testRangeIntersectionWhenOverlapping() {
    Range junJul = new Range(new IsoDate(2017, 6, 1), new IsoDate(2017, 7, 31));
    Range julAug = new Range(new IsoDate(2017, 7, 1), new IsoDate(2017, 8, 31));
    assertEquals("2017-07-01/2017-07-31", ((Range) junJul.intersect(julAug)).toISOString());
  }

  @Test
  public void testRangeIntersectionWhenDisjoint() {
    // QUIRK: disjoint ranges do not intersect to null or to an empty range. The result
    // is a backwards range whose end precedes its begin, with a negative duration.
    Range junJul = new Range(new IsoDate(2017, 6, 1), new IsoDate(2017, 7, 31));
    Range q1 = new Range(new IsoDate(2017, 1, 1), new IsoDate(2017, 3, 31));
    Range intersection = (Range) junJul.intersect(q1);
    assertEquals("2017-06-01/2017-03-31", intersection.toISOString());
    assertEquals("PT-1488H", intersection.getDuration().getTimexValue());
  }

  @Test
  public void testRangeArithmeticMovesOnlyTheEnd() {
    // QUIRK: adding a duration to a range extends the end and leaves the begin alone,
    // and the resulting duration is left unnormalised as P1DT696H.
    Range june = new Range(new IsoDate(2017, 6, 1), new IsoDate(2017, 6, 30));
    Range longer = june.add(SUTime.DAY);
    assertIso("2017-06-01", longer.begin());
    assertIso("2017-07-02", longer.end());
    assertEquals("P1DT696H", longer.getDuration().getTimexValue());
    Range shorter = june.subtract(SUTime.DAY);
    assertIso("2017-06-01", shorter.begin());
    assertIso("2017-06-28", shorter.end());
  }

  @Test
  public void testRangeOffsetMovesBothEnds() {
    Range june = new Range(new IsoDate(2017, 6, 1), new IsoDate(2017, 6, 30));
    Range shifted = june.offset(SUTime.MONTH,
            SUTime.RANGE_OFFSET_BEGIN | SUTime.RANGE_OFFSET_END);
    assertEquals("2017-07-01/2017-07-30", shifted.toISOString());
  }

  @Test
  public void testRangeBuiltFromADuration() {
    Range week = new Range(new IsoDate(2017, 6, 1), SUTime.WEEK);
    assertEquals("2017-06-01/P1W", week.toISOString());
    assertEquals("P1W", week.getTimexValue());
    // QUIRK: the end is never computed from begin plus duration. It stays the
    // TIME_UNKNOWN sentinel, which renders as null rather than as a date, even though
    // toISOString renders the range as a whole happily.
    assertSame(SUTime.TIME_UNKNOWN, week.end());
    assertNull(week.end().toISOString());
  }

}
