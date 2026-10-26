package edu.stanford.nlp.time;

import org.junit.Test;
import org.threeten.extra.PartialTemporal;
import org.threeten.extra.TemporalFields;

import java.time.temporal.ChronoField;
import java.time.temporal.ChronoUnit;
import java.time.temporal.IsoFields;
import java.time.temporal.TemporalField;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

/**
 * Tests the parts of the partial-date layer that {@link SUTime} calls and that the rest of
 * the {@code PartialTemporal} classes did not yet cover: the partial templates, a partial's
 * granularity as a period, the fields of an offset a partial cannot hold, resolving a
 * weekday or a week against a reference date, and adding an offset by force.
 *
 * <p>Expected values come from joda-time 2.14.0, so that SUTime keeps producing the TIMEX3
 * values it always did.
 *
 * @author John Bauer
 */
public class PartialTemporalGapsTest {

  private static final TemporalField WEEK = WeekFields.ISO.weekOfWeekBasedYear();

  private static PartialTemporal date(int year, int month, int day) {
    return PartialTemporal.empty()
            .withField(ChronoField.YEAR, year)
            .withField(ChronoField.MONTH_OF_YEAR, month)
            .withField(ChronoField.DAY_OF_MONTH, day);
  }

  /** The fields of a partial under joda's names for them, so a whole shape can be asserted. */
  private static String render(PartialTemporal partial) {
    if (partial == null) {
      return "null";
    }
    List<String> parts = new ArrayList<>();
    for (Map.Entry<TemporalField, Long> entry : partial.getFields().entrySet()) {
      parts.add(name(entry.getKey()) + "=" + entry.getValue());
    }
    return String.join(" ", parts);
  }

  private static String name(TemporalField field) {
    if (field.equals(ChronoField.YEAR)) {
      return "year";
    }
    if (field.equals(ChronoField.MONTH_OF_YEAR)) {
      return "monthOfYear";
    }
    if (field.equals(ChronoField.DAY_OF_MONTH)) {
      return "dayOfMonth";
    }
    if (field.equals(ChronoField.DAY_OF_WEEK)) {
      return "dayOfWeek";
    }
    if (field.equals(WEEK)) {
      return "weekOfWeekyear";
    }
    if (field.equals(ChronoField.HOUR_OF_DAY)) {
      return "hourOfDay";
    }
    if (field.equals(ChronoField.MINUTE_OF_HOUR)) {
      return "minuteOfHour";
    }
    if (field.equals(ChronoField.SECOND_OF_MINUTE)) {
      return "secondOfMinute";
    }
    if (field.equals(ChronoField.MILLI_OF_SECOND)) {
      return "millisOfSecond";
    }
    return field.toString();
  }

  // ---- the field templates ----

  @Test
  public void testIsoTemplateFields() {
    assertEquals("year=0 monthOfYear=1 dayOfMonth=1 hourOfDay=0 minuteOfHour=0"
                    + " secondOfMinute=0 millisOfSecond=0",
            render(PartialTemporalResolution.EMPTY_ISO_PARTIAL));
    assertEquals("year=0 weekOfWeekyear=1 dayOfWeek=1 hourOfDay=0 minuteOfHour=0"
                    + " secondOfMinute=0 millisOfSecond=0",
            render(PartialTemporalResolution.EMPTY_ISO_WEEK_PARTIAL));
    assertEquals("year=0 monthOfYear=1 dayOfMonth=1",
            render(PartialTemporalResolution.EMPTY_ISO_DATE_PARTIAL));
    assertEquals("hourOfDay=0 minuteOfHour=0 secondOfMinute=0 millisOfSecond=0",
            render(PartialTemporalResolution.EMPTY_ISO_TIME_PARTIAL));
  }

  // ---- a partial's granularity, as a period ----

  @Test
  public void testGranularityOfEachField() {
    assertEquals("P1Y", granularity(PartialTemporal.of(ChronoField.YEAR, 2017)));
    assertEquals("P1M", granularity(PartialTemporal.of(ChronoField.MONTH_OF_YEAR, 6)));
    assertEquals("P1D", granularity(PartialTemporal.of(ChronoField.DAY_OF_MONTH, 15)));
    assertEquals("P1D", granularity(PartialTemporal.of(ChronoField.DAY_OF_WEEK, 3)));
    assertEquals("P1W", granularity(PartialTemporal.of(WEEK, 24)));
    assertEquals("PT1H", granularity(PartialTemporal.of(ChronoField.HOUR_OF_DAY, 10)));
    assertEquals("PT1M", granularity(PartialTemporal.of(ChronoField.MINUTE_OF_HOUR, 30)));
    assertEquals("PT1S", granularity(PartialTemporal.of(ChronoField.SECOND_OF_MINUTE, 45)));
    assertEquals("PT0.001S", granularity(PartialTemporal.of(ChronoField.MILLI_OF_SECOND, 123)));
  }

  /** A field coarser than any period field is given in the coarsest field that holds it. */
  @Test
  public void testGranularityOfCoarseFields() {
    assertEquals("P3M", granularity(PartialTemporal.of(IsoFields.QUARTER_OF_YEAR, 2)));
    assertEquals("P6M", granularity(PartialTemporal.of(TemporalFields.HALF_OF_YEAR, 1)));
    assertEquals("P10Y", granularity(PartialTemporal.of(SUTimeFields.DECADE_OF_CENTURY, 1)));
    assertEquals("P100Y", granularity(PartialTemporal.of(SUTimeFields.CENTURY_OF_ERA, 20)));
    assertEquals("P1Y", granularity(PartialTemporal.of(SUTimeFields.YEAR_OF_CENTURY, 17)));
    assertEquals("P1Y", granularity(PartialTemporal.of(SUTimeFields.YEAR_OF_DECADE, 7)));
    assertEquals("P1M", granularity(PartialTemporal.of(SUTimeFields.MONTH_OF_QUARTER, 2)));
    assertEquals("P1W", granularity(PartialTemporal.of(SUTimeFields.WEEK_OF_MONTH, 2)));
  }

  /** The granularity is the finest field's, so adding coarser fields does not change it. */
  @Test
  public void testGranularityTakesTheFinestField() {
    assertEquals("P1D", granularity(date(2017, 6, 15)));
    assertEquals("P1M", granularity(date(2017, 6, 15).withoutField(ChronoField.DAY_OF_MONTH)));
    assertEquals("PT1M", granularity(date(2017, 6, 15)
            .withField(ChronoField.HOUR_OF_DAY, 10)
            .withField(ChronoField.MINUTE_OF_HOUR, 30)));
  }

  /** A half day has no expression as a period, and neither has an empty partial. */
  @Test
  public void testGranularityWithoutAPeriodIsNull() {
    assertNull(PartialTemporalUtils.getGranularity(PartialTemporal.of(ChronoField.AMPM_OF_DAY, 1)));
    assertNull(PartialTemporalUtils.getGranularity(PartialTemporal.empty()));
    assertNull(PartialTemporalUtils.getGranularity(null));
  }

  private static String granularity(PartialTemporal partial) {
    SUTimePeriod period = PartialTemporalUtils.getGranularity(partial);
    return period == null ? "null" : period.toString();
  }

  // ---- the fields of an offset a partial cannot hold ----

  @Test
  public void testUnsupportedPeriodKeepsOnlyWhatCannotLand() {
    PartialTemporal yearOnly = PartialTemporal.of(ChronoField.YEAR, 2017);
    SUTimePeriod offset = SUTimePeriod.ofYears(1)
            .with(ChronoUnit.MONTHS, 2).with(ChronoUnit.DAYS, 3);
    assertEquals("P2M3D",
            PartialTemporalUtils.getUnsupportedPeriod(yearOnly, offset).toString());
  }

  @Test
  public void testUnsupportedPeriodIsNullWhenEverythingLands() {
    SUTimePeriod offset = SUTimePeriod.ofYears(1)
            .with(ChronoUnit.MONTHS, 2).with(ChronoUnit.DAYS, 3);
    assertNull(PartialTemporalUtils.getUnsupportedPeriod(date(2017, 1, 31), offset));
  }

  @Test
  public void testUnsupportedPeriodOfTimeFieldsAgainstADate() {
    assertEquals("PT5H", PartialTemporalUtils
            .getUnsupportedPeriod(date(2017, 1, 31), SUTimePeriod.ofHours(5)).toString());
  }

  /** A field set to zero has nothing to drop, so it is not reported. */
  @Test
  public void testUnsupportedPeriodIgnoresZeroFields() {
    assertNull(PartialTemporalUtils.getUnsupportedPeriod(
            PartialTemporal.of(ChronoField.YEAR, 2017), SUTimePeriod.ofDays(0)));
    assertNull(PartialTemporalUtils.getUnsupportedPeriod(
            PartialTemporal.of(ChronoField.YEAR, 2017), SUTimePeriod.ZERO));
  }

  @Test
  public void testUnsupportedPeriodOfNull() {
    assertNull(PartialTemporalUtils.getUnsupportedPeriod(date(2017, 1, 31), null));
  }

  // ---- resolving a weekday against a reference date ----

  @Test
  public void testResolveDowToDayAgainstAReferenceDate() {
    PartialTemporal wednesday = PartialTemporal.of(ChronoField.DAY_OF_WEEK, 3);
    // 15 June 2017 is a Thursday, so the Wednesday of that week is the 14th. The fields
    // come back coarsest first, and a day of the month is coarser than a day of the week
    // because it sits inside a month rather than inside a week.
    assertEquals("dayOfMonth=14 dayOfWeek=3",
            render(PartialTemporalResolution.resolveDowToDay(wednesday, date(2017, 6, 15))));
  }

  /** Without a weekday there is nothing to place, and a day of the month is already placed. */
  @Test
  public void testResolveDowToDayLeavesOtherPartialsAlone() {
    PartialTemporal yearOnly = PartialTemporal.of(ChronoField.YEAR, 2017);
    assertSame(yearOnly, PartialTemporalResolution.resolveDowToDay(yearOnly, date(2017, 6, 15)));

    PartialTemporal full = date(2017, 6, 15);
    assertSame(full, PartialTemporalResolution.resolveDowToDay(full, date(2017, 6, 15)));
  }

  /** A reference date that is only a year cannot place a weekday. */
  @Test
  public void testResolveDowToDayNeedsAFullReferenceDate() {
    PartialTemporal wednesday = PartialTemporal.of(ChronoField.DAY_OF_WEEK, 3);
    PartialTemporal yearOnly = PartialTemporal.of(ChronoField.YEAR, 2017);
    assertSame(wednesday, PartialTemporalResolution.resolveDowToDay(wednesday, yearOnly));
  }

  /** The two have to agree on the fields they share. */
  @Test
  public void testResolveDowToDayRequiresCompatibility() {
    PartialTemporal wednesdayOf2016 = PartialTemporal.empty()
            .withField(ChronoField.YEAR, 2016)
            .withField(ChronoField.DAY_OF_WEEK, 3);
    assertSame(wednesdayOf2016,
            PartialTemporalResolution.resolveDowToDay(wednesdayOf2016, date(2017, 6, 15)));
  }

  @Test
  public void testResolveDowToDayOfNull() {
    assertNull(PartialTemporalResolution.resolveDowToDay(null, date(2017, 6, 15)));
    PartialTemporal wednesday = PartialTemporal.of(ChronoField.DAY_OF_WEEK, 3);
    assertSame(wednesday, PartialTemporalResolution.resolveDowToDay(wednesday, null));
  }

  // ---- resolving a week against a reference date ----

  @Test
  public void testResolveWeekAgainstAReferenceDate() {
    PartialTemporal yearOnly = PartialTemporal.of(ChronoField.YEAR, 2017);
    // 15 June 2017 falls in ISO week 24
    assertEquals("year=2017 weekOfWeekyear=24",
            render(PartialTemporalResolution.resolveWeek(yearOnly, date(2017, 6, 15))));
  }

  /** The result is dated by week, so the month and day of the month are dropped. */
  @Test
  public void testResolveWeekDropsMonthAndDay() {
    PartialTemporal monthOnly = PartialTemporal.empty()
            .withField(ChronoField.YEAR, 2017)
            .withField(ChronoField.MONTH_OF_YEAR, 6);
    String resolved = render(PartialTemporalResolution.resolveWeek(monthOnly, date(2017, 6, 15)));
    assertEquals("year=2017 weekOfWeekyear=24", resolved);
  }

  @Test
  public void testResolveWeekLeavesAFullDateAlone() {
    PartialTemporal full = date(2017, 6, 15);
    assertSame(full, PartialTemporalResolution.resolveWeek(full, date(2017, 6, 15)));
  }

  @Test
  public void testResolveWeekOfNull() {
    assertNull(PartialTemporalResolution.resolveWeek(null, date(2017, 6, 15)));
    PartialTemporal yearOnly = PartialTemporal.of(ChronoField.YEAR, 2017);
    assertSame(yearOnly, PartialTemporalResolution.resolveWeek(yearOnly, null));
  }

  // ---- overlaying a partial on a reference instant ----

  @Test
  public void testOverlaySetsTheFieldsThePartialNames() {
    PartialTemporal june = PartialTemporal.of(ChronoField.MONTH_OF_YEAR, 6);
    assertEquals("2017-06-15T00:00Z",
            PartialTemporalResolution.overlay(june,
                    PartialTemporalResolution.getInstant(date(2017, 1, 15))).toString());
  }

  // ---- adding an offset by force ----

  /**
   * The offset is a fixed length of time taken from the epoch, not a calendar field added
   * to the partial. A month is however long January is, so a month past 31 January is 31
   * days past it, which is 3 March.
   */
  @Test
  public void testAddForceMeasuresTheOffsetFromTheEpoch() {
    assertEquals("year=2017 monthOfYear=3 dayOfMonth=3",
            render(PartialTemporalArithmetic.addForce(date(2017, 1, 31),
                    SUTimePeriod.ofMonths(1), 1)));
    assertEquals("year=2016 monthOfYear=12 dayOfMonth=31",
            render(PartialTemporalArithmetic.addForce(date(2017, 1, 31),
                    SUTimePeriod.ofMonths(1), -1)));
  }

  /** A year is 365 days on the same reckoning. */
  @Test
  public void testAddForceAYear() {
    assertEquals("year=2018 monthOfYear=1 dayOfMonth=31",
            render(PartialTemporalArithmetic.addForce(date(2017, 1, 31),
                    SUTimePeriod.ofYears(1), 1)));
  }

  @Test
  public void testAddForceAWeekAndADay() {
    assertEquals("year=2017 monthOfYear=2 dayOfMonth=7",
            render(PartialTemporalArithmetic.addForce(date(2017, 1, 31),
                    SUTimePeriod.ofWeeks(1), 1)));
    assertEquals("year=2017 monthOfYear=2 dayOfMonth=1",
            render(PartialTemporalArithmetic.addForce(date(2017, 1, 31),
                    SUTimePeriod.ofDays(1), 1)));
  }

  /** The scalar multiplies the offset. */
  @Test
  public void testAddForceScalar() {
    assertEquals("year=2017 monthOfYear=2 dayOfMonth=14",
            render(PartialTemporalArithmetic.addForce(date(2017, 1, 31),
                    SUTimePeriod.ofWeeks(1), 2)));
  }

  /**
   * An offset in a field the partial does not carry still moves the partial, because the
   * arithmetic happens on the instant. This is what separates it from
   * {@link PartialTemporalArithmetic#plus}, which would drop the offset.
   */
  @Test
  public void testAddForceAppliesFieldsThePartialDoesNotCarry() {
    PartialTemporal monthOnly = PartialTemporal.empty()
            .withField(ChronoField.YEAR, 2017)
            .withField(ChronoField.MONTH_OF_YEAR, 1);
    assertEquals("year=2017 monthOfYear=2",
            render(PartialTemporalArithmetic.addForce(monthOnly, SUTimePeriod.ofDays(40), 1)));
  }

  @Test
  public void testAddForceOfNothing() {
    PartialTemporal full = date(2017, 1, 31);
    assertSame(full, PartialTemporalArithmetic.addForce(full, SUTimePeriod.ofMonths(1), 0));
    assertSame(full, PartialTemporalArithmetic.addForce(full, null, 1));
    assertNull(PartialTemporalArithmetic.addForce(null, SUTimePeriod.ofMonths(1), 1));
  }

}
