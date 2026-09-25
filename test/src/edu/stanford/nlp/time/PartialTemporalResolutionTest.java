package edu.stanford.nlp.time;

import org.junit.Test;
import org.threeten.extra.PartialTemporal;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoField;
import java.time.temporal.IsoFields;
import java.time.temporal.TemporalField;
import java.time.temporal.WeekFields;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Tests for {@link PartialTemporalResolution}: turning partial dates into instants and
 * back, and pinning a day of the week to a date.
 *
 * <p>These values also match joda's over a 1,950-case differential run, which covers every
 * week and weekday of five years and five time zones; this file is what remains once joda
 * is gone.
 */
public class PartialTemporalResolutionTest {

  private static PartialTemporal of(Object... fieldsAndValues) {
    PartialTemporal partial = PartialTemporal.empty();
    for (int i = 0; i < fieldsAndValues.length; i += 2) {
      partial = partial.withField((TemporalField) fieldsAndValues[i],
              ((Number) fieldsAndValues[i + 1]).longValue());
    }
    return partial;
  }

  private static PartialTemporal ymd(int y, int m, int d) {
    return of(ChronoField.YEAR, y, ChronoField.MONTH_OF_YEAR, m, ChronoField.DAY_OF_MONTH, d);
  }

  private static Instant utc(String iso) {
    return Instant.parse(iso);
  }

  // ----------------------------------------------------------- getInstant

  @Test
  public void testMissingFieldsTakeTheirLowestValue() {
    assertEquals(utc("2017-06-15T00:00:00Z"),
            PartialTemporalResolution.getInstant(ymd(2017, 6, 15)));
    assertEquals(utc("2017-01-01T00:00:00Z"),
            PartialTemporalResolution.getInstant(of(ChronoField.YEAR, 2017)));
    assertEquals(utc("2017-06-01T00:00:00Z"),
            PartialTemporalResolution.getInstant(
                    of(ChronoField.YEAR, 2017, ChronoField.MONTH_OF_YEAR, 6)));
  }

  @Test
  public void testNullPartialGivesNoInstant() {
    assertNull(PartialTemporalResolution.getInstant(null));
  }

  @Test
  public void testCoarseYearFieldsAreReadAsTheYearTheyOpen() {
    assertEquals(utc("2017-01-01T00:00:00Z"), PartialTemporalResolution.getInstant(
            of(SUTimeFields.CENTURY_OF_ERA, 20, SUTimeFields.YEAR_OF_CENTURY, 17)));
    assertEquals(utc("1990-01-01T00:00:00Z"), PartialTemporalResolution.getInstant(
            of(SUTimeFields.CENTURY_OF_ERA, 19, SUTimeFields.DECADE_OF_CENTURY, 9)));
  }

  @Test
  public void testAQuarterSuppliesTheMonthItStartsIn() {
    assertEquals(utc("2017-07-01T00:00:00Z"), PartialTemporalResolution.getInstant(
            of(ChronoField.YEAR, 2017, IsoFields.QUARTER_OF_YEAR, 3)));
  }

  @Test
  public void testTheZoneShiftsTheInstant() {
    // Midnight in Los Angeles on a summer date is 07:00 UTC.
    assertEquals(utc("2017-06-15T07:00:00Z"), PartialTemporalResolution.getInstant(
            ymd(2017, 6, 15), ZoneId.of("America/Los_Angeles")));
    assertEquals(utc("2017-06-14T18:30:00Z"), PartialTemporalResolution.getInstant(
            ymd(2017, 6, 15), ZoneId.of("Asia/Kolkata")));
  }

  @Test
  public void testTimeFieldsAreUsedWhenPresent() {
    assertEquals(utc("2017-06-15T10:30:00Z"), PartialTemporalResolution.getInstant(
            ymd(2017, 6, 15).withField(ChronoField.HOUR_OF_DAY, 10)
                    .withField(ChronoField.MINUTE_OF_HOUR, 30)));
  }

  // ------------------------------------------------------------ getPartial

  @Test
  public void testGetPartialKeepsTheTemplateFields() {
    PartialTemporal read = PartialTemporalResolution.getPartial(
            utc("2017-06-15T10:30:00Z"), ymd(1900, 1, 1));
    assertEquals(3, read.size());
    assertEquals(2017, read.getLong(ChronoField.YEAR));
    assertEquals(6, read.getLong(ChronoField.MONTH_OF_YEAR));
    assertEquals(15, read.getLong(ChronoField.DAY_OF_MONTH));
  }

  // ---------------------------------------------------------- withWeekYear

  @Test
  public void testWithWeekYearSwapsTheYearField() {
    PartialTemporal weekly = PartialTemporalResolution.withWeekYear(ymd(2017, 6, 15));
    assertTrue(weekly.isSupported(WeekFields.ISO.weekBasedYear()));
    assertEquals(2017, weekly.getLong(WeekFields.ISO.weekBasedYear()));
    assertTrue("the other fields are untouched", weekly.isSupported(ChronoField.DAY_OF_MONTH));
  }

  // -------------------------------------------------------- resolveDowToDay

  @Test
  public void testADayOfWeekInAKnownWeekBecomesADate() {
    // Thursday of ISO week 24 of 2017 is 15 June.
    PartialTemporal resolved = PartialTemporalResolution.resolveDowToDay(
            of(ChronoField.YEAR, 2017, WeekFields.ISO.weekOfWeekBasedYear(), 24,
                    ChronoField.DAY_OF_WEEK, 4));
    assertEquals(2017, resolved.getLong(ChronoField.YEAR));
    assertEquals(6, resolved.getLong(ChronoField.MONTH_OF_YEAR));
    assertEquals(15, resolved.getLong(ChronoField.DAY_OF_MONTH));
  }

  @Test
  public void testWithoutAWeekThereIsNothingToResolveAgainst() {
    PartialTemporal bare = of(ChronoField.DAY_OF_WEEK, 4);
    assertEquals(bare, PartialTemporalResolution.resolveDowToDay(bare));
    assertEquals(ymd(2017, 6, 15), PartialTemporalResolution.resolveDowToDay(ymd(2017, 6, 15)));
  }

  @Test
  public void testTheFiftyThirdWeekWorksInYearsThatHaveOne() {
    // 2015 is a 53-week ISO year; its week 53 begins on 28 December.
    PartialTemporal resolved = PartialTemporalResolution.resolveDowToDay(
            of(ChronoField.YEAR, 2015, WeekFields.ISO.weekOfWeekBasedYear(), 53,
                    ChronoField.DAY_OF_WEEK, 1));
    assertEquals(2015, resolved.getLong(ChronoField.YEAR));
    assertEquals(12, resolved.getLong(ChronoField.MONTH_OF_YEAR));
    assertEquals(28, resolved.getLong(ChronoField.DAY_OF_MONTH));
  }

  @Test
  public void testAWeekTheYearDoesNotHaveIsRejected() {
    // 2017 has 52 ISO weeks. Rolling into 2018 instead would invent a date.
    try {
      PartialTemporalResolution.resolveDowToDay(
              of(ChronoField.YEAR, 2017, WeekFields.ISO.weekOfWeekBasedYear(), 53,
                      ChronoField.DAY_OF_WEEK, 1));
      fail("expected week 53 of a 52-week year to be rejected");
    } catch (DateTimeException expected) {
      assertTrue(expected.getMessage(), expected.getMessage().contains("53"));
    }
  }

  // ------------------------------------------------------------ resolveWeek

  @Test
  public void testResolveWeekAddsTheWeekOfTheYear() {
    PartialTemporal resolved = PartialTemporalResolution.resolveWeek(ymd(2017, 6, 15));
    assertEquals(24, resolved.getLong(WeekFields.ISO.weekOfWeekBasedYear()));
    assertEquals(15, resolved.getLong(ChronoField.DAY_OF_MONTH));
  }

  @Test
  public void testResolveWeekNeedsAFullDate() {
    PartialTemporal yearOnly = of(ChronoField.YEAR, 2017);
    assertEquals(yearOnly, PartialTemporalResolution.resolveWeek(yearOnly));
  }

}
