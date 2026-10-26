package edu.stanford.nlp.time;

import org.junit.Test;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoField;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalField;
import java.time.temporal.WeekFields;

import static org.junit.Assert.assertEquals;

/**
 * Tests {@link TimexValues}, which names a span of time or a duration the way TIMEX3 does.
 *
 * <p>The expected values were taken from the joda-time implementation these replaced, so
 * that the published values do not change.
 *
 * @author John Bauer
 */
public class TimexValuesTest {

  private static final TemporalField WEEK_OF_YEAR = WeekFields.ISO.weekOfWeekBasedYear();

  /** A time from an ISO instant, which is what the expectations are written in. */
  private static ZonedDateTime utc(String iso) {
    return Instant.parse(iso).atZone(ZoneOffset.UTC);
  }

  @Test
  public void testMinimumAndMaximumValue() {
    assertEquals(1, TimexValues.minimumValue(ChronoField.DAY_OF_MONTH, utc("2017-06-15T00:00:00Z")));
    assertEquals(30, TimexValues.maximumValue(ChronoField.DAY_OF_MONTH, utc("2017-06-15T00:00:00Z")));
    assertEquals(1, TimexValues.minimumValue(ChronoField.MONTH_OF_YEAR, utc("2017-06-15T00:00:00Z")));
    assertEquals(12, TimexValues.maximumValue(ChronoField.MONTH_OF_YEAR, utc("2017-06-15T00:00:00Z")));
    assertEquals(23, TimexValues.maximumValue(ChronoField.HOUR_OF_DAY, utc("2017-06-15T00:00:00Z")));
  }

  @Test
  public void testMaximumValueIsRelativeToTheReference() {
    assertEquals("February 2016 is a leap February",
            29, TimexValues.maximumValue(ChronoField.DAY_OF_MONTH, utc("2016-02-10T00:00:00Z")));
    assertEquals(28, TimexValues.maximumValue(ChronoField.DAY_OF_MONTH, utc("2017-02-10T00:00:00Z")));
    assertEquals(52, TimexValues.maximumValue(WEEK_OF_YEAR, utc("2017-06-15T00:00:00Z")));
    assertEquals("2015 is a 53-week ISO year",
            53, TimexValues.maximumValue(WEEK_OF_YEAR, utc("2015-06-15T00:00:00Z")));
  }

  @Test
  public void testTimexTimeValue() {
    assertEquals("2017-06-15T10:30", TimexValues.timexTimeValue(utc("2017-06-15T10:30:00Z")));
    assertEquals("2017-01-05T00:00", TimexValues.timexTimeValue(utc("2017-01-05T00:00:00Z")));
    assertEquals("2017-01-05T09:05", TimexValues.timexTimeValue(utc("2017-01-05T09:05:00Z")));
  }

  @Test
  public void testTimexDateValueForAlignedRanges() {
    assertEquals("2017-06-15",
            TimexValues.timexDateValue(utc("2017-06-15T00:00:00Z"), utc("2017-06-16T00:00:00Z")));
    assertEquals("2017-06",
            TimexValues.timexDateValue(utc("2017-06-01T00:00:00Z"), utc("2017-07-01T00:00:00Z")));
    assertEquals("2017",
            TimexValues.timexDateValue(utc("2017-01-01T00:00:00Z"), utc("2018-01-01T00:00:00Z")));
    assertEquals("2017-W24",
            TimexValues.timexDateValue(utc("2017-06-12T00:00:00Z"), utc("2017-06-19T00:00:00Z")));
    assertEquals("2017-Q2",
            TimexValues.timexDateValue(utc("2017-04-01T00:00:00Z"), utc("2017-07-01T00:00:00Z")));
    assertEquals("2017-H1",
            TimexValues.timexDateValue(utc("2017-01-01T00:00:00Z"), utc("2017-07-01T00:00:00Z")));
  }

  @Test
  public void testTimexDateValueForDecadesAndCenturies() {
    assertEquals("201",
            TimexValues.timexDateValue(utc("2010-01-01T00:00:00Z"), utc("2020-01-01T00:00:00Z")));
    assertEquals("19XX",
            TimexValues.timexDateValue(utc("1900-01-01T00:00:00Z"), utc("2000-01-01T00:00:00Z")));
    assertEquals("20XX",
            TimexValues.timexDateValue(utc("2000-01-01T00:00:00Z"), utc("2100-01-01T00:00:00Z")));
  }

  @Test
  public void testTimexDateValueForSubDayRanges() {
    assertEquals("2017-06-15T10:30",
            TimexValues.timexDateValue(utc("2017-06-15T10:30:00Z"), utc("2017-06-15T10:31:00Z")));
    assertEquals("2017-06-15T10",
            TimexValues.timexDateValue(utc("2017-06-15T10:00:00Z"), utc("2017-06-15T11:00:00Z")));
    assertEquals("2017-06-15T00",
            TimexValues.timexDateValue(utc("2017-06-15T00:00:00Z"), utc("2017-06-15T01:00:00Z")));
    // The last hour of a day is 23, not 24.
    assertEquals("2017-06-15T23",
            TimexValues.timexDateValue(utc("2017-06-15T23:00:00Z"), utc("2017-06-16T00:00:00Z")));
  }

  @Test
  public void testTimexDateValueForIdenticalEndpoints() {
    assertEquals("2017-06-15T10:30",
            TimexValues.timexDateValue(utc("2017-06-15T10:30:00Z"), utc("2017-06-15T10:30:00Z")));
  }

  @Test
  public void testTimexDateValueFallsBackToDurationForUnalignedRanges() {
    // Three days is not a calendar unit, so without forceDate the result is a duration.
    assertEquals("PT72H",
            TimexValues.timexDateValue(utc("2017-06-15T00:00:00Z"), utc("2017-06-18T00:00:00Z")));
    assertEquals("PT1464H",
            TimexValues.timexDateValue(utc("2017-06-15T00:00:00Z"), utc("2017-08-15T00:00:00Z")));
  }

  @Test
  public void testTimexDateValueForceDateUsesTheStartDate() {
    TimexValues.ConversionOptions opts = new TimexValues.ConversionOptions();
    opts.forceDate = true;
    assertEquals("2017-06-15",
            TimexValues.timexDateValue(utc("2017-06-15T00:00:00Z"), utc("2017-06-18T00:00:00Z"), opts));
    assertEquals("2017-06-15",
            TimexValues.timexDateValue(utc("2017-06-15T00:00:00Z"), utc("2017-08-15T00:00:00Z"), opts));
    // Ranges that already align are unaffected.
    assertEquals("2017-06",
            TimexValues.timexDateValue(utc("2017-06-01T00:00:00Z"), utc("2017-07-01T00:00:00Z"), opts));
  }

  @Test
  public void testTimexDateValueReferenceSentinels() {
    assertEquals("PAST_REF", TimexValues.timexDateValue(
            ZonedDateTime.of(-200000, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC), utc("2017-06-15T00:00:00Z")));
    assertEquals("FUTURE_REF", TimexValues.timexDateValue(
            utc("2017-06-15T00:00:00Z"), ZonedDateTime.of(200000, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC)));
  }

  @Test
  public void testTimexDurationValueSimpleUnits() {
    assertEquals("P1Y", TimexValues.timexDurationValue(SUTimePeriod.ofYears(1)));
    assertEquals("P5Y", TimexValues.timexDurationValue(SUTimePeriod.ofYears(5)));
    assertEquals("P1M", TimexValues.timexDurationValue(SUTimePeriod.ofMonths(1)));
    assertEquals("P4M", TimexValues.timexDurationValue(SUTimePeriod.ofMonths(4)));
    assertEquals("P1W", TimexValues.timexDurationValue(SUTimePeriod.ofWeeks(1)));
    assertEquals("P30D", TimexValues.timexDurationValue(SUTimePeriod.ofDays(30)));
    assertEquals("PT1H", TimexValues.timexDurationValue(SUTimePeriod.ofHours(1)));
    assertEquals("PT30M", TimexValues.timexDurationValue(SUTimePeriod.ofMinutes(30)));
    assertEquals("PT45S", TimexValues.timexDurationValue(SUTimePeriod.ofSeconds(45)));
  }

  @Test
  public void testTimexDurationValuePromotesLargeYearCounts() {
    assertEquals("P1E", TimexValues.timexDurationValue(SUTimePeriod.ofYears(10)));
    assertEquals("P2E", TimexValues.timexDurationValue(SUTimePeriod.ofYears(20)));
    assertEquals("P1C", TimexValues.timexDurationValue(SUTimePeriod.ofYears(100)));
    assertEquals("P2C", TimexValues.timexDurationValue(SUTimePeriod.ofYears(200)));
    assertEquals("P1L", TimexValues.timexDurationValue(SUTimePeriod.ofYears(1000)));
    assertEquals("P1L2C3E4Y", TimexValues.timexDurationValue(SUTimePeriod.ofYears(1234)));
  }

  @Test
  public void testTimexDurationValuePromotesWholeQuarters() {
    assertEquals("P1Q", TimexValues.timexDurationValue(SUTimePeriod.ofMonths(3)));
    assertEquals("P2Q", TimexValues.timexDurationValue(SUTimePeriod.ofMonths(6)));
  }

  @Test
  public void testTimexDurationValueCompoundPeriods() {
    assertEquals("P1Y2Q3D", TimexValues.timexDurationValue(SUTimePeriod.ofYears(1).with(ChronoUnit.MONTHS, 6).with(ChronoUnit.DAYS, 3)));
    assertEquals("PT2H30M", TimexValues.timexDurationValue(SUTimePeriod.ofHours(2).with(ChronoUnit.MINUTES, 30)));
    assertEquals("P1DT12H", TimexValues.timexDurationValue(SUTimePeriod.ofDays(1).with(ChronoUnit.HOURS, 12)));
  }

  @Test
  public void testTimexDurationValueOfZero() {
    // ISO 8601 requires at least one component, so the zero duration is PT0S rather
    // than a bare "P". This matches what joda's own Period rendering produces.
    assertEquals("PT0S", TimexValues.timexDurationValue(SUTimePeriod.ZERO));
    assertEquals("PT0S", TimexValues.timexDurationValue(SUTimePeriod.ofSeconds(0)));
    assertEquals("PT0S", TimexValues.timexDurationValue(SUTimePeriod.ofDays(0)));
    assertEquals("PT0S", SUTimePeriod.ZERO.toString());
  }

  @Test
  public void testTimexDurationValueSubSecond() {
    // Sub-second amounts have no field of their own in ISO 8601 and are written as a
    // fraction of the seconds field.
    assertEquals("PT0.500S", TimexValues.timexDurationValue(SUTimePeriod.ofMillis(500)));
    assertEquals("PT1.500S", TimexValues.timexDurationValue(SUTimePeriod.ofSeconds(1).with(ChronoUnit.MILLIS, 500)));
    assertEquals("PT2M0.250S", TimexValues.timexDurationValue(SUTimePeriod.ofMinutes(2).with(ChronoUnit.MILLIS, 250)));
    assertEquals("P1DT0.001S", TimexValues.timexDurationValue(SUTimePeriod.ofDays(1).with(ChronoUnit.MILLIS, 1)));
    // The sign sits on the seconds, as it does in joda.
    assertEquals("PT-1.500S", TimexValues.timexDurationValue(SUTimePeriod.ofSeconds(-1).with(ChronoUnit.MILLIS, -500)));
  }

  @Test
  public void testTimexDurationValueAgreesWithJodaOnSubSecondPeriods() {
    // The two renderings diverge on the larger units, where the timex form promotes
    // years to L/C/E and months to Q, but they agree at and below the second.
    for (SUTimePeriod p : new SUTimePeriod[] {SUTimePeriod.ZERO, SUTimePeriod.ofMillis(1), SUTimePeriod.ofMillis(500),
            SUTimePeriod.ofSeconds(45), SUTimePeriod.ofSeconds(1).with(ChronoUnit.MILLIS, 500),
            SUTimePeriod.ofMinutes(2).with(ChronoUnit.MILLIS, 250)}) {
      assertEquals(p.toString(), TimexValues.timexDurationValue(p));
    }
  }

  @Test
  public void testTimexDurationValueApproximate() {
    TimexValues.ConversionOptions opts = new TimexValues.ConversionOptions();
    opts.approximate = true;
    assertEquals("PXY", TimexValues.timexDurationValue(SUTimePeriod.ofYears(1), opts));
    assertEquals("PXE", TimexValues.timexDurationValue(SUTimePeriod.ofYears(20), opts));
    assertEquals("PXLXCXEXY", TimexValues.timexDurationValue(SUTimePeriod.ofYears(1234), opts));
    assertEquals("PTXHXM", TimexValues.timexDurationValue(SUTimePeriod.ofHours(2).with(ChronoUnit.MINUTES, 30), opts));
  }

  @Test
  public void testTimexDurationValueForcedUnits() {
    TimexValues.ConversionOptions opts = new TimexValues.ConversionOptions();
    opts.forceUnits = new String[] {"Y"};
    assertEquals("P10Y", TimexValues.timexDurationValue(SUTimePeriod.ofYears(10), opts));
    assertEquals("P100Y", TimexValues.timexDurationValue(SUTimePeriod.ofYears(100), opts));
    assertEquals("P1234Y", TimexValues.timexDurationValue(SUTimePeriod.ofYears(1234), opts));
    // QUIRK: forceUnits only gates the year-level promotions, so months still collapse
    // into quarters even when Y is the only forced unit.
    assertEquals("P1Q", TimexValues.timexDurationValue(SUTimePeriod.ofMonths(3), opts));
  }

  @Test
  public void testTimexDurationValueBetweenTwoTimes() {
    // The period is built from a millisecond count, so it only ever has time fields:
    // a calendar day comes out as 24 hours rather than P1D.
    assertEquals("PT24H", TimexValues.timexDurationValue(
            utc("2017-06-15T00:00:00Z"), utc("2017-06-16T00:00:00Z")));
    assertEquals("PT2H", TimexValues.timexDurationValue(
            utc("2017-06-15T00:00:00Z"), utc("2017-06-15T02:00:00Z")));
  }

}
