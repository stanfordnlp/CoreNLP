package edu.stanford.nlp.time;

import org.joda.time.Chronology;
import org.joda.time.DateTime;
import org.joda.time.DateTimeFieldType;
import org.joda.time.DateTimeZone;
import org.joda.time.DurationFieldType;
import org.joda.time.Instant;
import org.joda.time.Partial;
import org.joda.time.Period;
import org.joda.time.chrono.ISOChronology;

import org.junit.Test;

import java.time.ZoneId;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Unit tests for {@link JodaTimeUtils}.
 *
 * <p>These tests need no models, no rule files and no pipeline, so they are fast enough
 * to run on every build. They exist mainly as a safety net for replacing the joda-time
 * dependency: {@code JodaTimeUtils} is where SUTime's {@code Partial} arithmetic lives,
 * so pinning its behaviour down here means a reimplementation can be checked field by
 * field rather than only through end-to-end Timex strings.
 *
 * <p>Assertions on {@code Partial} results check the field set and the field values
 * rather than {@code toString()}, since the rendering of a partial date is a detail of
 * the underlying library and not part of what SUTime depends on. Methods that return a
 * string (the {@code timex*} family) are checked on the string, because that string is
 * the contract.
 *
 * <p>Several assertions record behaviour that is arguably wrong. Those are marked with a
 * QUIRK comment: the point is to notice if a rewrite changes them, not to bless them.
 */
public class JodaTimeUtilsTest {

  private static final DateTimeFieldType YEAR = DateTimeFieldType.year();
  private static final DateTimeFieldType MONTH = DateTimeFieldType.monthOfYear();
  private static final DateTimeFieldType DAY = DateTimeFieldType.dayOfMonth();
  private static final DateTimeFieldType HOUR = DateTimeFieldType.hourOfDay();
  private static final DateTimeFieldType MINUTE = DateTimeFieldType.minuteOfHour();
  private static final DateTimeFieldType SECOND = DateTimeFieldType.secondOfMinute();
  private static final DateTimeFieldType MILLIS = DateTimeFieldType.millisOfSecond();
  private static final DateTimeFieldType DOW = DateTimeFieldType.dayOfWeek();
  private static final DateTimeFieldType WEEK = DateTimeFieldType.weekOfWeekyear();
  private static final DateTimeFieldType WEEKYEAR = DateTimeFieldType.weekyear();
  private static final DateTimeFieldType YOC = DateTimeFieldType.yearOfCentury();
  private static final DateTimeFieldType CENTURY = DateTimeFieldType.centuryOfEra();
  private static final DateTimeFieldType HALFDAY = DateTimeFieldType.halfdayOfDay();
  private static final DateTimeFieldType CLOCKHOUR_HALFDAY = DateTimeFieldType.clockhourOfHalfday();
  private static final DateTimeFieldType HOUR_HALFDAY = DateTimeFieldType.hourOfHalfday();
  private static final DateTimeFieldType CLOCKHOUR_DAY = DateTimeFieldType.clockhourOfDay();

  private static final Chronology ISO = ISOChronology.getInstanceUTC();

  // ---------------------------------------------------------------- helpers

  /** Build a Partial from alternating field/value arguments. */
  private static Partial partial(Object... fieldsAndValues) {
    Partial p = new Partial();
    for (int i = 0; i < fieldsAndValues.length; i += 2) {
      p = p.with((DateTimeFieldType) fieldsAndValues[i], (Integer) fieldsAndValues[i + 1]);
    }
    return p;
  }

  /**
   * Assert that a Partial supports exactly the given fields with exactly the given values.
   * Field order is not checked, since joda keeps partials sorted most-general-first and a
   * replacement may not.
   */
  private static void assertPartial(Partial actual, Object... fieldsAndValues) {
    int expectedSize = fieldsAndValues.length / 2;
    assertEquals("wrong number of fields in " + describe(actual), expectedSize, actual.size());
    for (int i = 0; i < fieldsAndValues.length; i += 2) {
      DateTimeFieldType field = (DateTimeFieldType) fieldsAndValues[i];
      int value = (Integer) fieldsAndValues[i + 1];
      assertTrue("missing field " + field + " in " + describe(actual), actual.isSupported(field));
      assertEquals("wrong value for " + field + " in " + describe(actual), value, actual.get(field));
    }
  }

  /** Field-by-field rendering, so failures say which field is wrong rather than "2017-06". */
  private static String describe(Partial p) {
    StringBuilder sb = new StringBuilder("{");
    for (int i = 0; i < p.size(); i++) {
      if (i > 0) {
        sb.append(", ");
      }
      sb.append(p.getFieldType(i).getName()).append('=').append(p.getValue(i));
    }
    return sb.append('}').toString();
  }

  private static DateTime utc(String iso) {
    return new DateTime(iso, DateTimeZone.UTC);
  }

  private static Set<String> durationFieldNames(Set<DurationFieldType> types) {
    Set<String> names = new TreeSet<>();
    for (DurationFieldType type : types) {
      names.add(type.getName());
    }
    return names;
  }

  private static Set<String> nameSet(String... names) {
    Set<String> set = new TreeSet<>();
    for (String name : names) {
      set.add(name);
    }
    return set;
  }

  // -------------------------------------------------------------- constants

  @Test
  public void testEmptyIsoPartial() {
    assertPartial(JodaTimeUtils.EMPTY_ISO_PARTIAL,
            YEAR, 0, MONTH, 1, DAY, 1, HOUR, 0, MINUTE, 0, SECOND, 0, MILLIS, 0);
  }

  @Test
  public void testEmptyIsoWeekPartial() {
    assertPartial(JodaTimeUtils.EMPTY_ISO_WEEK_PARTIAL,
            YEAR, 0, WEEK, 1, DOW, 1, HOUR, 0, MINUTE, 0, SECOND, 0, MILLIS, 0);
  }

  @Test
  public void testEmptyIsoDatePartial() {
    assertPartial(JodaTimeUtils.EMPTY_ISO_DATE_PARTIAL, YEAR, 0, MONTH, 1, DAY, 1);
  }

  @Test
  public void testEmptyIsoTimePartial() {
    assertPartial(JodaTimeUtils.EMPTY_ISO_TIME_PARTIAL, HOUR, 0, MINUTE, 0, SECOND, 0, MILLIS, 0);
  }

  @Test
  public void testInstantZero() {
    assertEquals(0L, JodaTimeUtils.INSTANT_ZERO.getMillis());
  }

  // ------------------------------------------------ custom duration fields

  @Test
  public void testCustomDurationFieldsAreScaledCorrectly() {
    assertEquals(3 * ISO.months().getUnitMillis(), JodaTimeUtils.Quarters.getField(ISO).getUnitMillis());
    assertEquals(6 * ISO.months().getUnitMillis(), JodaTimeUtils.HalfYears.getField(ISO).getUnitMillis());
    assertEquals(10 * ISO.years().getUnitMillis(), JodaTimeUtils.Decades.getField(ISO).getUnitMillis());
    assertEquals(100 * ISO.years().getUnitMillis(), JodaTimeUtils.Centuries.getField(ISO).getUnitMillis());
  }

  @Test
  public void testCustomDurationFieldNames() {
    // These names end up in serialized output, so they are part of the contract.
    assertEquals("quarters", JodaTimeUtils.Quarters.getName());
    assertEquals("halfyear", JodaTimeUtils.HalfYears.getName());
    assertEquals("decades", JodaTimeUtils.Decades.getName());
    assertEquals("centuries", JodaTimeUtils.Centuries.getName());
  }

  @Test
  public void testCustomFieldTypeNames() {
    assertEquals("quarterOfYear", JodaTimeUtils.QuarterOfYear.getName());
    assertEquals("halfYearOfYear", JodaTimeUtils.HalfYearOfYear.getName());
    assertEquals("monthOfQuarter", JodaTimeUtils.MonthOfQuarter.getName());
    assertEquals("monthOfHalfYear", JodaTimeUtils.MonthOfHalfYear.getName());
    assertEquals("weekOfMonth", JodaTimeUtils.WeekOfMonth.getName());
    assertEquals("decadeOfCentury", JodaTimeUtils.DecadeOfCentury.getName());
    assertEquals("yearOfDecade", JodaTimeUtils.YearOfDecade.getName());
  }

  @Test
  public void testCustomFieldTypeDurations() {
    assertEquals(JodaTimeUtils.Quarters, JodaTimeUtils.QuarterOfYear.getDurationType());
    assertEquals(DurationFieldType.years(), JodaTimeUtils.QuarterOfYear.getRangeDurationType());
    assertEquals(JodaTimeUtils.HalfYears, JodaTimeUtils.HalfYearOfYear.getDurationType());
    assertEquals(DurationFieldType.months(), JodaTimeUtils.MonthOfQuarter.getDurationType());
    assertEquals(JodaTimeUtils.Quarters, JodaTimeUtils.MonthOfQuarter.getRangeDurationType());
    assertEquals(JodaTimeUtils.HalfYears, JodaTimeUtils.MonthOfHalfYear.getRangeDurationType());
    assertEquals(DurationFieldType.weeks(), JodaTimeUtils.WeekOfMonth.getDurationType());
    assertEquals(DurationFieldType.months(), JodaTimeUtils.WeekOfMonth.getRangeDurationType());
    assertEquals(JodaTimeUtils.Decades, JodaTimeUtils.DecadeOfCentury.getDurationType());
    assertEquals(DurationFieldType.centuries(), JodaTimeUtils.DecadeOfCentury.getRangeDurationType());
  }

  // --------------------------------------------------- custom field values

  @Test
  public void testQuarterOfYear() {
    assertEquals(1, utc("2017-01-15T00:00:00Z").get(JodaTimeUtils.QuarterOfYear));
    assertEquals(1, utc("2017-03-31T00:00:00Z").get(JodaTimeUtils.QuarterOfYear));
    assertEquals(2, utc("2017-04-01T00:00:00Z").get(JodaTimeUtils.QuarterOfYear));
    assertEquals(3, utc("2017-07-04T00:00:00Z").get(JodaTimeUtils.QuarterOfYear));
    assertEquals(4, utc("2017-12-31T00:00:00Z").get(JodaTimeUtils.QuarterOfYear));
  }

  @Test
  public void testHalfYearOfYear() {
    assertEquals(1, utc("2017-01-15T00:00:00Z").get(JodaTimeUtils.HalfYearOfYear));
    assertEquals(1, utc("2017-06-30T00:00:00Z").get(JodaTimeUtils.HalfYearOfYear));
    assertEquals(2, utc("2017-07-01T00:00:00Z").get(JodaTimeUtils.HalfYearOfYear));
    assertEquals(2, utc("2017-12-31T00:00:00Z").get(JodaTimeUtils.HalfYearOfYear));
  }

  @Test
  public void testMonthOfQuarter() {
    assertEquals(1, utc("2017-01-15T00:00:00Z").get(JodaTimeUtils.MonthOfQuarter));
    assertEquals(2, utc("2017-02-15T00:00:00Z").get(JodaTimeUtils.MonthOfQuarter));
    assertEquals(3, utc("2017-03-31T00:00:00Z").get(JodaTimeUtils.MonthOfQuarter));
    assertEquals(1, utc("2017-04-01T00:00:00Z").get(JodaTimeUtils.MonthOfQuarter));
    assertEquals(3, utc("2017-12-31T00:00:00Z").get(JodaTimeUtils.MonthOfQuarter));
  }

  @Test
  public void testMonthOfHalfYear() {
    assertEquals(1, utc("2017-01-15T00:00:00Z").get(JodaTimeUtils.MonthOfHalfYear));
    assertEquals(4, utc("2017-04-01T00:00:00Z").get(JodaTimeUtils.MonthOfHalfYear));
    assertEquals(1, utc("2017-07-04T00:00:00Z").get(JodaTimeUtils.MonthOfHalfYear));
    assertEquals(6, utc("2017-12-31T00:00:00Z").get(JodaTimeUtils.MonthOfHalfYear));
  }

  @Test
  public void testWeekOfMonth() {
    assertEquals(2, utc("2017-01-15T00:00:00Z").get(JodaTimeUtils.WeekOfMonth));
    assertEquals(1, utc("2017-04-01T00:00:00Z").get(JodaTimeUtils.WeekOfMonth));
    assertEquals(3, utc("2017-07-04T00:00:00Z").get(JodaTimeUtils.WeekOfMonth));
    assertEquals(4, utc("2017-12-31T00:00:00Z").get(JodaTimeUtils.WeekOfMonth));
  }

  @Test
  public void testDecadeOfCentury() {
    assertEquals(1, utc("2017-01-15T00:00:00Z").get(JodaTimeUtils.DecadeOfCentury));
    assertEquals(0, utc("2000-02-29T00:00:00Z").get(JodaTimeUtils.DecadeOfCentury));
    assertEquals(9, utc("1999-06-15T00:00:00Z").get(JodaTimeUtils.DecadeOfCentury));
    assertEquals(2, utc("2023-11-08T00:00:00Z").get(JodaTimeUtils.DecadeOfCentury));
  }

  @Test
  public void testYearOfDecadeDuplicatesDecadeOfCentury() {
    // QUIRK: YearOfDecade.getField() builds a DividedDateTimeField, exactly as
    // DecadeOfCentury does, so it returns the decade rather than the year within the
    // decade. A RemainderDateTimeField (as MonthOfQuarter uses) would give 7/0/9/3 here.
    // Nothing in SUTime reads YearOfDecade, so this is latent, but it is public API.
    assertEquals(1, utc("2017-01-15T00:00:00Z").get(JodaTimeUtils.YearOfDecade));
    assertEquals(0, utc("2000-02-29T00:00:00Z").get(JodaTimeUtils.YearOfDecade));
    assertEquals(9, utc("1999-06-15T00:00:00Z").get(JodaTimeUtils.YearOfDecade));
    assertEquals(2, utc("2023-11-08T00:00:00Z").get(JodaTimeUtils.YearOfDecade));
    for (String date : new String[] {"2017-01-15", "2000-02-29", "1999-06-15", "2023-11-08"}) {
      DateTime dt = utc(date + "T00:00:00Z");
      assertEquals(dt.get(JodaTimeUtils.DecadeOfCentury), dt.get(JodaTimeUtils.YearOfDecade));
    }
  }

  // ------------------------------------------------------- field predicates

  @Test
  public void testHasField() {
    Partial ymd = partial(YEAR, 2017, MONTH, 6, DAY, 15);
    assertTrue(JodaTimeUtils.hasField(ymd, YEAR));
    assertFalse(JodaTimeUtils.hasField(ymd, HOUR));
    assertFalse("null partial has no fields", JodaTimeUtils.hasField((Partial) null, YEAR));
  }

  @Test
  public void testHasFieldOnPeriod() {
    assertTrue(JodaTimeUtils.hasField(Period.years(1), DurationFieldType.years()));
    // A Period built from Period.years() still carries the full set of standard fields.
    assertTrue(JodaTimeUtils.hasField(Period.years(1), DurationFieldType.days()));
    assertFalse(JodaTimeUtils.hasField((Period) null, DurationFieldType.years()));
  }

  @Test
  public void testHasYYYYMMDD() {
    assertTrue(JodaTimeUtils.hasYYYYMMDD(partial(YEAR, 2017, MONTH, 6, DAY, 15)));
    assertFalse(JodaTimeUtils.hasYYYYMMDD(partial(YEAR, 2017, MONTH, 6)));
    assertFalse(JodaTimeUtils.hasYYYYMMDD(partial(YOC, 97, MONTH, 6, DAY, 15)));
    assertFalse(JodaTimeUtils.hasYYYYMMDD(null));
  }

  @Test
  public void testHasYYMMDD() {
    assertTrue(JodaTimeUtils.hasYYMMDD(partial(YOC, 97, MONTH, 6, DAY, 15)));
    assertFalse(JodaTimeUtils.hasYYMMDD(partial(YEAR, 2017, MONTH, 6, DAY, 15)));
    assertFalse(JodaTimeUtils.hasYYMMDD(null));
  }

  @Test
  public void testSetField() {
    assertPartial(JodaTimeUtils.setField(null, YEAR, 2017), YEAR, 2017);
    assertPartial(JodaTimeUtils.setField(partial(YEAR, 2017, MONTH, 6), DAY, 15),
            YEAR, 2017, MONTH, 6, DAY, 15);
    assertPartial(JodaTimeUtils.setField(partial(YEAR, 2017, MONTH, 6, DAY, 15), MONTH, 9),
            YEAR, 2017, MONTH, 9, DAY, 15);
  }

  // --------------------------------------------------- supported durations

  @Test
  public void testGetSupportedDurationFields() {
    assertEquals(nameSet("years", "months", "days"),
            durationFieldNames(JodaTimeUtils.getSupportedDurationFields(partial(YEAR, 2017, MONTH, 6, DAY, 15))));
    assertEquals(nameSet("hours", "minutes"),
            durationFieldNames(JodaTimeUtils.getSupportedDurationFields(partial(HOUR, 10, MINUTE, 30))));
    assertTrue(JodaTimeUtils.getSupportedDurationFields(new Partial()).isEmpty());
  }

  @Test
  public void testGetUnsupportedDurationPeriod() {
    assertNull(JodaTimeUtils.getUnsupportedDurationPeriod(partial(YEAR, 2017), null));
    // Both fields of the offset are covered by the partial, so nothing is left over.
    assertNull(JodaTimeUtils.getUnsupportedDurationPeriod(partial(YEAR, 2017, MONTH, 6),
            Period.years(1).withMonths(2)));
    assertEquals(Period.months(2),
            JodaTimeUtils.getUnsupportedDurationPeriod(partial(YEAR, 2017), Period.years(1).withMonths(2)));
    assertEquals(Period.days(3),
            JodaTimeUtils.getUnsupportedDurationPeriod(partial(YEAR, 2017), Period.days(3)));
    // Zero-valued unsupported fields are dropped rather than carried as zeroes.
    assertNull(JodaTimeUtils.getUnsupportedDurationPeriod(partial(YEAR, 2017), Period.years(1)));
  }

  // --------------------------------------------------------------- combine

  @Test
  public void testCombineWithNull() {
    assertPartial(JodaTimeUtils.combine(null, partial(YEAR, 2017)), YEAR, 2017);
    assertPartial(JodaTimeUtils.combine(partial(YEAR, 2017), null), YEAR, 2017);
  }

  @Test
  public void testCombineFillsMissingFields() {
    assertPartial(JodaTimeUtils.combine(partial(MONTH, 6, DAY, 15), partial(YEAR, 2017)),
            YEAR, 2017, MONTH, 6, DAY, 15);
    assertPartial(JodaTimeUtils.combine(partial(YEAR, 2017), partial(MONTH, 6, DAY, 15)),
            YEAR, 2017, MONTH, 6, DAY, 15);
  }

  @Test
  public void testCombineKeepsFieldsAlreadyPresent() {
    assertPartial(JodaTimeUtils.combine(partial(YEAR, 2017), partial(YEAR, 1999)), YEAR, 2017);
    assertPartial(JodaTimeUtils.combine(partial(YEAR, 2017, MONTH, 6, DAY, 15),
            partial(YEAR, 1999, MONTH, 1, DAY, 2)), YEAR, 2017, MONTH, 6, DAY, 15);
  }

  @Test
  public void testCombineResolvesTwoDigitYearAgainstReference() {
    // "'97" against 2017 resolves backwards into the 20th century, since 2097 is in the
    // future relative to the reference year.
    assertPartial(JodaTimeUtils.combine(partial(YOC, 97), partial(YEAR, 2017)), YEAR, 1997);
    // "'17" against 2017 stays in the current century.
    assertPartial(JodaTimeUtils.combine(partial(YOC, 17), partial(YEAR, 2017)), YEAR, 2017);
    // A full year already present wins over a two-digit year in the reference.
    assertPartial(JodaTimeUtils.combine(partial(YEAR, 2017), partial(YOC, 97)), YEAR, 2017);
  }

  // ------------------------------------------- combine: halfday normalisation

  @Test
  public void testCombineNormalisesHourOfHalfday() {
    // hourOfHalfday is 0..11 and is carried straight across, with 12 folding to 0.
    assertPartial(JodaTimeUtils.combine(partial(HOUR_HALFDAY, 10, HALFDAY, SUTime.HALFDAY_AM), new Partial()),
            HALFDAY, 0, HOUR, 10);
    assertPartial(JodaTimeUtils.combine(partial(HOUR_HALFDAY, 10, HALFDAY, SUTime.HALFDAY_PM), new Partial()),
            HALFDAY, 1, HOUR, 22);
    assertPartial(JodaTimeUtils.combine(partial(HOUR_HALFDAY, 0, HALFDAY, SUTime.HALFDAY_AM), new Partial()),
            HALFDAY, 0, HOUR, 0);
  }

  @Test
  public void testCombineNormalisesClockhourOfHalfday() {
    // clockhourOfHalfday runs 1..12, so the hour within the halfday is the value
    // modulo 12. Ordinary hours are carried across unchanged in the morning and
    // shifted by twelve in the afternoon.
    assertPartial(JodaTimeUtils.combine(partial(CLOCKHOUR_HALFDAY, 10, HALFDAY, SUTime.HALFDAY_AM), new Partial()),
            HALFDAY, 0, HOUR, 10);
    assertPartial(JodaTimeUtils.combine(partial(CLOCKHOUR_HALFDAY, 10, HALFDAY, SUTime.HALFDAY_PM), new Partial()),
            HALFDAY, 1, HOUR, 22);
    assertPartial(JodaTimeUtils.combine(partial(CLOCKHOUR_HALFDAY, 1, HALFDAY, SUTime.HALFDAY_AM), new Partial()),
            HALFDAY, 0, HOUR, 1);
    assertPartial(JodaTimeUtils.combine(partial(CLOCKHOUR_HALFDAY, 11, HALFDAY, SUTime.HALFDAY_PM), new Partial()),
            HALFDAY, 1, HOUR, 23);
  }

  @Test
  public void testCombineHandlesTwelveOClock() {
    // Twelve is the boundary case: it means the zero hour of its halfday, so 12 AM is
    // midnight and 12 PM is midday.
    assertPartial(JodaTimeUtils.combine(partial(CLOCKHOUR_HALFDAY, 12, HALFDAY, SUTime.HALFDAY_AM), new Partial()),
            HALFDAY, 0, HOUR, 0);
    assertPartial(JodaTimeUtils.combine(partial(CLOCKHOUR_HALFDAY, 12, HALFDAY, SUTime.HALFDAY_PM), new Partial()),
            HALFDAY, 1, HOUR, 12);
  }

  @Test
  public void testCombineNormalisesClockhourOfDay() {
    assertPartial(JodaTimeUtils.combine(partial(CLOCKHOUR_DAY, 13, HALFDAY, SUTime.HALFDAY_PM), new Partial()),
            HALFDAY, 1, HOUR, 12);
  }

  @Test
  public void testCombineLeavesHourOfDayAlone() {
    assertPartial(JodaTimeUtils.combine(partial(HOUR, 14, HALFDAY, SUTime.HALFDAY_PM), new Partial()),
            HALFDAY, 1, HOUR, 14);
  }

  @Test
  public void testCombineOnlyNormalisesWhenAHalfdayIsPresent() {
    // Without halfdayOfDay there is nothing to disambiguate against, so the clockhour
    // is left exactly as it was.
    assertPartial(JodaTimeUtils.combine(partial(CLOCKHOUR_HALFDAY, 10, MINUTE, 30), new Partial()),
            CLOCKHOUR_HALFDAY, 10, MINUTE, 30);
  }

  // ---------------------------------------------- generality of a partial

  @Test
  public void testGetMostGeneralAndMostSpecificPartial() {
    Partial ymd = partial(YEAR, 2017, MONTH, 6, DAY, 15);
    assertEquals(YEAR, JodaTimeUtils.getMostGeneral(ymd));
    assertEquals(DAY, JodaTimeUtils.getMostSpecific(ymd));
    assertEquals(SECOND, JodaTimeUtils.getMostSpecific(
            partial(YEAR, 2017, MONTH, 6, DAY, 15, HOUR, 10, MINUTE, 30, SECOND, 45)));
    assertNull(JodaTimeUtils.getMostGeneral(new Partial()));
    assertNull(JodaTimeUtils.getMostSpecific(new Partial()));
  }

  @Test
  public void testGetMostGeneralAndMostSpecificPeriod() {
    Period p = Period.years(1).withMonths(2).withDays(3);
    assertEquals(DurationFieldType.years(), JodaTimeUtils.getMostGeneral(p));
    assertEquals(DurationFieldType.days(), JodaTimeUtils.getMostSpecific(p));
    assertEquals(DurationFieldType.months(), JodaTimeUtils.getMostGeneral(Period.months(2)));
    assertEquals(DurationFieldType.months(), JodaTimeUtils.getMostSpecific(Period.months(2)));
  }

  @Test
  public void testGetJodaTimePeriodIsOneUnitOfTheFinestField() {
    assertEquals(Period.years(1), JodaTimeUtils.getJodaTimePeriod(partial(YEAR, 2017)));
    assertEquals(Period.months(1), JodaTimeUtils.getJodaTimePeriod(partial(YEAR, 2017, MONTH, 6)));
    assertEquals(Period.days(1), JodaTimeUtils.getJodaTimePeriod(partial(YEAR, 2017, MONTH, 6, DAY, 15)));
    assertEquals(Period.minutes(1),
            JodaTimeUtils.getJodaTimePeriod(partial(YEAR, 2017, MONTH, 6, DAY, 15, HOUR, 10, MINUTE, 30)));
  }

  // ------------------------------------------------ combineMoreGeneralFields

  @Test
  public void testCombineMoreGeneralFieldsAddsOnlyCoarserFields() {
    // Year is coarser than month, so it is taken from the reference.
    assertPartial(JodaTimeUtils.combineMoreGeneralFields(partial(MONTH, 6, DAY, 15),
            partial(YEAR, 2017, MONTH, 1, DAY, 2)), YEAR, 2017, MONTH, 6, DAY, 15);
    // With only a day, both year and month come from the reference.
    assertPartial(JodaTimeUtils.combineMoreGeneralFields(partial(DAY, 15),
            partial(YEAR, 2017, MONTH, 1, DAY, 2)), YEAR, 2017, MONTH, 1, DAY, 15);
    // A time-of-day partial picks up the whole reference date.
    assertPartial(JodaTimeUtils.combineMoreGeneralFields(partial(HOUR, 10, MINUTE, 30),
            partial(YEAR, 2017, MONTH, 1, DAY, 2)),
            YEAR, 2017, MONTH, 1, DAY, 2, HOUR, 10, MINUTE, 30);
  }

  @Test
  public void testCombineMoreGeneralFieldsLeavesExistingFieldsAlone() {
    assertPartial(JodaTimeUtils.combineMoreGeneralFields(partial(YEAR, 2017, MONTH, 6, DAY, 15),
            partial(YEAR, 1999, MONTH, 1, DAY, 2)), YEAR, 2017, MONTH, 6, DAY, 15);
  }

  @Test
  public void testCombineMoreGeneralFieldsFromEmptyPartial() {
    assertPartial(JodaTimeUtils.combineMoreGeneralFields(new Partial(),
            partial(YEAR, 2017, MONTH, 1, DAY, 2)), YEAR, 2017, MONTH, 1, DAY, 2);
  }

  @Test
  public void testCombineMoreGeneralFieldsHonoursExplicitBound() {
    // Bounding at MONTH still pulls in the year, because p1's own most general field
    // (monthOfYear) is not more general than the bound.
    assertPartial(JodaTimeUtils.combineMoreGeneralFields(partial(MONTH, 6, DAY, 15),
            partial(YEAR, 2017, MONTH, 1, DAY, 2), MONTH), YEAR, 2017, MONTH, 6, DAY, 15);
  }

  @Test
  public void testCombineMoreGeneralFieldsResolvesTwoDigitYear() {
    assertPartial(JodaTimeUtils.combineMoreGeneralFields(partial(YOC, 97), partial(YEAR, 2017)),
            YEAR, 1997);
  }

  @Test
  public void testCombineMoreGeneralFieldsMergesCenturyAndYearOfCentury() {
    // The trailing fixup collapses centuryOfEra + yearOfCentury into a single year.
    assertPartial(JodaTimeUtils.combineMoreGeneralFields(partial(CENTURY, 19, YOC, 97), new Partial()),
            YEAR, 1997);
  }

  @Test
  public void testCombineMoreGeneralFieldsResolvesDecade() {
    // "the nineties" against 2017: 2090 is in the future, so it steps back a century.
    // The result keeps decadeOfCentury rather than collapsing to a year.
    assertPartial(JodaTimeUtils.combineMoreGeneralFields(
            partial().with(JodaTimeUtils.DecadeOfCentury, 9), partial(YEAR, 2017)),
            CENTURY, 19, JodaTimeUtils.DecadeOfCentury, 9);
  }

  // ------------------------------------------------ discardMoreSpecificFields

  @Test
  public void testDiscardMoreSpecificFieldsByFieldType() {
    Partial full = partial(YEAR, 2017, MONTH, 6, DAY, 15, HOUR, 10, MINUTE, 30);
    assertPartial(JodaTimeUtils.discardMoreSpecificFields(full, DAY), YEAR, 2017, MONTH, 6, DAY, 15);
    assertPartial(JodaTimeUtils.discardMoreSpecificFields(full, MONTH), YEAR, 2017, MONTH, 6);
    assertPartial(JodaTimeUtils.discardMoreSpecificFields(full, YEAR), YEAR, 2017);
    // Cutting below the finest field present is a no-op.
    assertPartial(JodaTimeUtils.discardMoreSpecificFields(partial(YEAR, 2017, MONTH, 6, DAY, 15), HOUR),
            YEAR, 2017, MONTH, 6, DAY, 15);
    assertEquals(0, JodaTimeUtils.discardMoreSpecificFields(new Partial(), DAY).size());
  }

  @Test
  public void testDiscardMoreSpecificFieldsSuppliesCenturyForDecade() {
    // Keeping only the decade would lose the century, so it is recovered from the year.
    assertPartial(JodaTimeUtils.discardMoreSpecificFields(
            partial(YEAR, 1997).with(JodaTimeUtils.DecadeOfCentury, 9), JodaTimeUtils.DecadeOfCentury),
            CENTURY, 19, JodaTimeUtils.DecadeOfCentury, 9);
  }

  @Test
  public void testDiscardMoreSpecificFieldsByDurationType() {
    Partial full = partial(YEAR, 2017, MONTH, 6, DAY, 15, HOUR, 10, MINUTE, 30);
    assertPartial(JodaTimeUtils.discardMoreSpecificFields(full, DurationFieldType.days()),
            YEAR, 2017, MONTH, 6, DAY, 15);
    assertPartial(JodaTimeUtils.discardMoreSpecificFields(full, DurationFieldType.months()),
            YEAR, 2017, MONTH, 6);
    assertPartial(JodaTimeUtils.discardMoreSpecificFields(full, DurationFieldType.years()), YEAR, 2017);
    assertPartial(JodaTimeUtils.discardMoreSpecificFields(full, DurationFieldType.hours()),
            YEAR, 2017, MONTH, 6, DAY, 15, HOUR, 10);
  }

  @Test
  public void testDiscardMoreSpecificFieldsOnPeriod() {
    Period p = Period.years(1).withMonths(2).withDays(3);
    assertEquals(Period.years(1).withMonths(2),
            JodaTimeUtils.discardMoreSpecificFields(p, DurationFieldType.months(), ISO));
    assertEquals(Period.years(1),
            JodaTimeUtils.discardMoreSpecificFields(p, DurationFieldType.years(), ISO));
    assertEquals(p, JodaTimeUtils.discardMoreSpecificFields(p, DurationFieldType.days(), ISO));
  }

  // --------------------------------------------------- padMoreSpecificFields

  @Test
  public void testPadMoreSpecificFieldsFillsOutToMillis() {
    assertPartial(JodaTimeUtils.padMoreSpecificFields(partial(YEAR, 2017), null),
            YEAR, 2017, MONTH, 1, DAY, 1, HOUR, 0, MINUTE, 0, SECOND, 0, MILLIS, 0);
    assertPartial(JodaTimeUtils.padMoreSpecificFields(partial(YEAR, 2017, MONTH, 6), null),
            YEAR, 2017, MONTH, 6, DAY, 1, HOUR, 0, MINUTE, 0, SECOND, 0, MILLIS, 0);
    assertPartial(JodaTimeUtils.padMoreSpecificFields(partial(YEAR, 2017, MONTH, 6, DAY, 15), null),
            YEAR, 2017, MONTH, 6, DAY, 15, HOUR, 0, MINUTE, 0, SECOND, 0, MILLIS, 0);
  }

  @Test
  public void testPadMoreSpecificFieldsExpandsQuarterToMonth() {
    assertPartial(JodaTimeUtils.padMoreSpecificFields(
            partial(YEAR, 2017).with(JodaTimeUtils.QuarterOfYear, 3), null),
            YEAR, 2017, JodaTimeUtils.QuarterOfYear, 3, MONTH, 7, DAY, 1,
            HOUR, 0, MINUTE, 0, SECOND, 0, MILLIS, 0);
  }

  @Test
  public void testPadMoreSpecificFieldsExpandsHalfYearToMonth() {
    assertPartial(JodaTimeUtils.padMoreSpecificFields(
            partial(YEAR, 2017).with(JodaTimeUtils.HalfYearOfYear, 2), null),
            YEAR, 2017, JodaTimeUtils.HalfYearOfYear, 2, MONTH, 7, DAY, 1,
            HOUR, 0, MINUTE, 0, SECOND, 0, MILLIS, 0);
  }

  @Test
  public void testPadMoreSpecificFieldsUsesWeekFieldsForWeekPartials() {
    // A week-based partial is padded with dayOfWeek, not dayOfMonth.
    assertPartial(JodaTimeUtils.padMoreSpecificFields(partial(YEAR, 2017, WEEK, 25), null),
            YEAR, 2017, WEEK, 25, DOW, 1, HOUR, 0, MINUTE, 0, SECOND, 0, MILLIS, 0);
  }

  @Test
  public void testPadMoreSpecificFieldsCollapsesCenturyAndDecade() {
    assertPartial(JodaTimeUtils.padMoreSpecificFields(partial(CENTURY, 20), null),
            YEAR, 2000, MONTH, 1, DAY, 1, HOUR, 0, MINUTE, 0, SECOND, 0, MILLIS, 0);
    assertPartial(JodaTimeUtils.padMoreSpecificFields(
            partial(CENTURY, 19).with(JodaTimeUtils.DecadeOfCentury, 9), null),
            YEAR, 1990, MONTH, 1, DAY, 1, HOUR, 0, MINUTE, 0, SECOND, 0, MILLIS, 0);
  }

  @Test
  public void testPadMoreSpecificFieldsWithBareDecade() {
    // QUIRK: with no century to anchor it, a bare decade becomes yearOfCentury=90
    // rather than a year, so the result is not a usable absolute date.
    assertPartial(JodaTimeUtils.padMoreSpecificFields(
            partial().with(JodaTimeUtils.DecadeOfCentury, 9), null),
            YOC, 90, MONTH, 1, DAY, 1, HOUR, 0, MINUTE, 0, SECOND, 0, MILLIS, 0);
  }

  @Test
  public void testPadMoreSpecificFieldsRespectsGranularity() {
    assertPartial(JodaTimeUtils.padMoreSpecificFields(partial(YEAR, 2017, MONTH, 6), Period.days(1)),
            YEAR, 2017, MONTH, 6, DAY, 1);
    assertPartial(JodaTimeUtils.padMoreSpecificFields(partial(YEAR, 2017, MONTH, 6), Period.months(1)),
            YEAR, 2017, MONTH, 6);
    assertPartial(JodaTimeUtils.padMoreSpecificFields(
            partial(YEAR, 2017, MONTH, 6, DAY, 15), Period.minutes(1)),
            YEAR, 2017, MONTH, 6, DAY, 15, HOUR, 0, MINUTE, 0);
  }

  // ---------------------------------------------------------- isCompatible

  @Test
  public void testIsCompatible() {
    assertTrue(JodaTimeUtils.isCompatible(null, partial(YEAR, 2017)));
    assertTrue(JodaTimeUtils.isCompatible(partial(YEAR, 2017), null));
    assertTrue(JodaTimeUtils.isCompatible(partial(YEAR, 2017), partial(YEAR, 2017)));
    assertFalse(JodaTimeUtils.isCompatible(partial(YEAR, 2017), partial(YEAR, 2018)));
    // Disjoint field sets never conflict.
    assertTrue(JodaTimeUtils.isCompatible(partial(YEAR, 2017), partial(MONTH, 6)));
    assertTrue(JodaTimeUtils.isCompatible(partial(YEAR, 2017, MONTH, 6), partial(MONTH, 6, DAY, 15)));
    assertFalse(JodaTimeUtils.isCompatible(partial(YEAR, 2017, MONTH, 6), partial(MONTH, 7, DAY, 15)));
  }

  // ------------------------------------------------- day-of-week resolution

  @Test
  public void testResolveDowToDayAgainstReference() {
    // 2017-06-15 is a Thursday (dayOfWeek 4), so "Monday" resolves to the 12th and
    // "Sunday" to the 18th: the reference week runs 12-18 June.
    // QUIRK: only dayOfMonth is added, so the result has no year or month and cannot
    // stand alone as a date.
    Partial reference = partial(YEAR, 2017, MONTH, 6, DAY, 15);
    assertPartial(JodaTimeUtils.resolveDowToDay(partial(DOW, 4), reference), DAY, 15, DOW, 4);
    assertPartial(JodaTimeUtils.resolveDowToDay(partial(DOW, 1), reference), DAY, 12, DOW, 1);
    assertPartial(JodaTimeUtils.resolveDowToDay(partial(DOW, 7), reference), DAY, 18, DOW, 7);
  }

  @Test
  public void testResolveDowToDayLeavesCompleteDatesAlone() {
    assertPartial(JodaTimeUtils.resolveDowToDay(partial(YEAR, 2017, MONTH, 6, DAY, 15),
            partial(YEAR, 2017, MONTH, 1, DAY, 2)), YEAR, 2017, MONTH, 6, DAY, 15);
  }

  @Test
  public void testResolveDowToDayFromWeekOfYear() {
    // Thursday of week 24 of 2017 is 2017-06-15.
    assertPartial(JodaTimeUtils.resolveDowToDay(partial(YEAR, 2017, WEEK, 24, DOW, 4)),
            YEAR, 2017, MONTH, 6, DAY, 15);
    // Without a day of week there is nothing to resolve.
    assertPartial(JodaTimeUtils.resolveDowToDay(partial(YEAR, 2017, MONTH, 6, DAY, 15)),
            YEAR, 2017, MONTH, 6, DAY, 15);
  }

  @Test
  public void testWithWeekYear() {
    assertPartial(JodaTimeUtils.withWeekYear(partial(YEAR, 2017, MONTH, 6, DAY, 15)),
            WEEKYEAR, 2017, MONTH, 6, DAY, 15);
    assertPartial(JodaTimeUtils.withWeekYear(partial(YEAR, 2017, WEEK, 24)), WEEKYEAR, 2017, WEEK, 24);
  }

  @Test
  public void testResolveWeek() {
    // 2017-06-15 falls in ISO week 24.
    assertPartial(JodaTimeUtils.resolveWeek(partial(YEAR, 2017, MONTH, 6, DAY, 15)),
            YEAR, 2017, MONTH, 6, DAY, 15, WEEK, 24);
    // Nothing to compute without a full date.
    assertPartial(JodaTimeUtils.resolveWeek(partial(YEAR, 2017)), YEAR, 2017);
  }

  @Test
  public void testResolveWeekAgainstReference() {
    assertPartial(JodaTimeUtils.resolveWeek(partial(YEAR, 2017, WEEK, 24),
            partial(YEAR, 2017, MONTH, 6, DAY, 15)), YEAR, 2017, WEEK, 24);
  }

  // ------------------------------------------------------- partial <-> instant

  @Test
  public void testGetInstantDefaultsMissingFields() {
    assertEquals(utc("2017-06-15T00:00:00Z").toInstant(),
            JodaTimeUtils.getInstant(partial(YEAR, 2017, MONTH, 6, DAY, 15)));
    // Missing month and day default to January 1st, missing time to midnight.
    assertEquals(utc("2017-01-01T00:00:00Z").toInstant(),
            JodaTimeUtils.getInstant(partial(YEAR, 2017)));
    assertNull(JodaTimeUtils.getInstant(null));
  }

  @Test
  public void testGetInstantExpandsCoarseFields() {
    // Q3 becomes the first month of that quarter.
    assertEquals(utc("2017-07-01T00:00:00Z").toInstant(),
            JodaTimeUtils.getInstant(partial(YEAR, 2017).with(JodaTimeUtils.QuarterOfYear, 3)));
    assertEquals(utc("2017-01-01T00:00:00Z").toInstant(),
            JodaTimeUtils.getInstant(partial(CENTURY, 20, YOC, 17)));
    assertEquals(utc("1990-01-01T00:00:00Z").toInstant(),
            JodaTimeUtils.getInstant(partial(CENTURY, 19).with(JodaTimeUtils.DecadeOfCentury, 9)));
  }

  @Test
  public void testGetInstantAppliesTimeZone() {
    // Midnight in Los Angeles on a summer date is 07:00 UTC.
    assertEquals(utc("2017-06-15T07:00:00Z").toInstant(),
            JodaTimeUtils.getInstant(partial(YEAR, 2017, MONTH, 6, DAY, 15),
                    ZoneId.of("America/Los_Angeles")));
  }

  @Test
  public void testGetPartialKeepsTemplateFields() {
    Instant t = utc("2017-06-15T10:30:00Z").toInstant();
    assertPartial(JodaTimeUtils.getPartial(t, JodaTimeUtils.EMPTY_ISO_PARTIAL),
            YEAR, 2017, MONTH, 6, DAY, 15, HOUR, 10, MINUTE, 30, SECOND, 0, MILLIS, 0);
    // The template decides which fields survive, so a date template drops the time.
    assertPartial(JodaTimeUtils.getPartial(t, JodaTimeUtils.EMPTY_ISO_DATE_PARTIAL),
            YEAR, 2017, MONTH, 6, DAY, 15);
  }

  // -------------------------------------------------------------- addForce

  @Test
  public void testAddForce() {
    Partial date = partial(YEAR, 2017, MONTH, 6, DAY, 15);
    assertPartial(JodaTimeUtils.addForce(date, Period.days(1), 1), YEAR, 2017, MONTH, 6, DAY, 16);
    assertPartial(JodaTimeUtils.addForce(date, Period.days(1), -1), YEAR, 2017, MONTH, 6, DAY, 14);
    assertPartial(JodaTimeUtils.addForce(date, Period.days(0), 1), YEAR, 2017, MONTH, 6, DAY, 15);
    assertPartial(JodaTimeUtils.addForce(date, Period.days(2), 3), YEAR, 2017, MONTH, 6, DAY, 21);
  }

  @Test
  public void testAddForceRollsOverIntoCoarserFields() {
    assertPartial(JodaTimeUtils.addForce(partial(YEAR, 2017, MONTH, 12), Period.months(1), 1),
            YEAR, 2018, MONTH, 1);
  }

  @Test
  public void testAddForceUsesFixedLengthPeriods() {
    // QUIRK: the period is converted to a duration from the epoch first, so a "month" is
    // the average month length (about 30.44 days) rather than a calendar month. Adding a
    // month to 15 June lands on 16 July, not 15 July.
    assertPartial(JodaTimeUtils.addForce(partial(YEAR, 2017, MONTH, 6, DAY, 15), Period.months(1), 1),
            YEAR, 2017, MONTH, 7, DAY, 16);
    assertPartial(JodaTimeUtils.addForce(partial(YEAR, 2017, MONTH, 6, DAY, 15), Period.years(1), 1),
            YEAR, 2018, MONTH, 6, DAY, 15);
    // Leap day plus a year clamps to the 28th.
    assertPartial(JodaTimeUtils.addForce(partial(YEAR, 2000, MONTH, 2, DAY, 29), Period.years(1), 1),
            YEAR, 2001, MONTH, 2, DAY, 28);
  }

  // --------------------------------------------- generality of field types

  @Test
  public void testIsMoreGeneral() {
    assertTrue(JodaTimeUtils.isMoreGeneral(YEAR, MONTH, ISO));
    assertTrue(JodaTimeUtils.isMoreGeneral(YEAR, DAY, ISO));
    assertTrue(JodaTimeUtils.isMoreGeneral(MONTH, DAY, ISO));
    assertTrue(JodaTimeUtils.isMoreGeneral(DAY, HOUR, ISO));
    assertTrue(JodaTimeUtils.isMoreGeneral(HOUR, MINUTE, ISO));
    assertTrue(JodaTimeUtils.isMoreGeneral(MINUTE, SECOND, ISO));
    assertTrue(JodaTimeUtils.isMoreGeneral(YEAR, JodaTimeUtils.QuarterOfYear, ISO));
    assertFalse(JodaTimeUtils.isMoreGeneral(MONTH, YEAR, ISO));
    assertFalse("a field is not more general than itself", JodaTimeUtils.isMoreGeneral(YEAR, YEAR, ISO));
  }

  @Test
  public void testIsMoreSpecific() {
    assertTrue(JodaTimeUtils.isMoreSpecific(MONTH, YEAR, ISO));
    assertTrue(JodaTimeUtils.isMoreSpecific(DAY, MONTH, ISO));
    assertTrue(JodaTimeUtils.isMoreSpecific(HOUR, DAY, ISO));
    assertTrue(JodaTimeUtils.isMoreSpecific(MINUTE, HOUR, ISO));
    assertTrue(JodaTimeUtils.isMoreSpecific(MONTH, JodaTimeUtils.QuarterOfYear, ISO));
    assertTrue(JodaTimeUtils.isMoreSpecific(MONTH, JodaTimeUtils.DecadeOfCentury, ISO));
    assertFalse(JodaTimeUtils.isMoreSpecific(YEAR, MONTH, ISO));
    assertFalse("a field is not more specific than itself", JodaTimeUtils.isMoreSpecific(YEAR, YEAR, ISO));
  }

  @Test
  public void testFieldsSharingADurationAreNeitherGeneralNorSpecific() {
    // QUIRK: year, yearOfCentury and centuryOfEra all report the "years" duration in this
    // comparison, so the ordering is not total. Callers that sort by generality get no
    // information from these pairs.
    assertFalse(JodaTimeUtils.isMoreGeneral(YEAR, YOC, ISO));
    assertFalse(JodaTimeUtils.isMoreSpecific(YEAR, YOC, ISO));
    assertFalse(JodaTimeUtils.isMoreGeneral(YEAR, CENTURY, ISO));
    assertFalse(JodaTimeUtils.isMoreSpecific(YEAR, CENTURY, ISO));
    assertFalse(JodaTimeUtils.isMoreGeneral(YEAR, JodaTimeUtils.DecadeOfCentury, ISO));
    assertFalse(JodaTimeUtils.isMoreSpecific(YEAR, JodaTimeUtils.DecadeOfCentury, ISO));
  }

  // ------------------------------------------------- minimum/maximum values

  @Test
  public void testMinimumAndMaximumValue() {
    assertEquals(1, JodaTimeUtils.minimumValue(DAY, utc("2017-06-15T00:00:00Z")));
    assertEquals(30, JodaTimeUtils.maximumValue(DAY, utc("2017-06-15T00:00:00Z")));
    assertEquals(1, JodaTimeUtils.minimumValue(MONTH, utc("2017-06-15T00:00:00Z")));
    assertEquals(12, JodaTimeUtils.maximumValue(MONTH, utc("2017-06-15T00:00:00Z")));
    assertEquals(23, JodaTimeUtils.maximumValue(HOUR, utc("2017-06-15T00:00:00Z")));
  }

  @Test
  public void testMaximumValueIsRelativeToTheReference() {
    assertEquals("February 2016 is a leap February",
            29, JodaTimeUtils.maximumValue(DAY, utc("2016-02-10T00:00:00Z")));
    assertEquals(28, JodaTimeUtils.maximumValue(DAY, utc("2017-02-10T00:00:00Z")));
    assertEquals(52, JodaTimeUtils.maximumValue(WEEK, utc("2017-06-15T00:00:00Z")));
    assertEquals("2015 is a 53-week ISO year",
            53, JodaTimeUtils.maximumValue(WEEK, utc("2015-06-15T00:00:00Z")));
  }

  // ------------------------------------------------------- timexTimeValue

  @Test
  public void testTimexTimeValue() {
    assertEquals("2017-06-15T10:30", JodaTimeUtils.timexTimeValue(utc("2017-06-15T10:30:00Z")));
    assertEquals("2017-01-05T00:00", JodaTimeUtils.timexTimeValue(utc("2017-01-05T00:00:00Z")));
    assertEquals("2017-01-05T09:05", JodaTimeUtils.timexTimeValue(utc("2017-01-05T09:05:00Z")));
  }

  // ------------------------------------------------------- timexDateValue

  @Test
  public void testTimexDateValueForAlignedRanges() {
    assertEquals("2017-06-15",
            JodaTimeUtils.timexDateValue(utc("2017-06-15T00:00:00Z"), utc("2017-06-16T00:00:00Z")));
    assertEquals("2017-06",
            JodaTimeUtils.timexDateValue(utc("2017-06-01T00:00:00Z"), utc("2017-07-01T00:00:00Z")));
    assertEquals("2017",
            JodaTimeUtils.timexDateValue(utc("2017-01-01T00:00:00Z"), utc("2018-01-01T00:00:00Z")));
    assertEquals("2017-W24",
            JodaTimeUtils.timexDateValue(utc("2017-06-12T00:00:00Z"), utc("2017-06-19T00:00:00Z")));
    assertEquals("2017-Q2",
            JodaTimeUtils.timexDateValue(utc("2017-04-01T00:00:00Z"), utc("2017-07-01T00:00:00Z")));
    assertEquals("2017-H1",
            JodaTimeUtils.timexDateValue(utc("2017-01-01T00:00:00Z"), utc("2017-07-01T00:00:00Z")));
  }

  @Test
  public void testTimexDateValueForDecadesAndCenturies() {
    assertEquals("201",
            JodaTimeUtils.timexDateValue(utc("2010-01-01T00:00:00Z"), utc("2020-01-01T00:00:00Z")));
    assertEquals("19XX",
            JodaTimeUtils.timexDateValue(utc("1900-01-01T00:00:00Z"), utc("2000-01-01T00:00:00Z")));
    assertEquals("20XX",
            JodaTimeUtils.timexDateValue(utc("2000-01-01T00:00:00Z"), utc("2100-01-01T00:00:00Z")));
  }

  @Test
  public void testTimexDateValueForSubDayRanges() {
    assertEquals("2017-06-15T10:30",
            JodaTimeUtils.timexDateValue(utc("2017-06-15T10:30:00Z"), utc("2017-06-15T10:31:00Z")));
    // QUIRK: an hour-long range reports the end hour rather than the start hour, so
    // 10:00-11:00 comes out as 11 rather than 10.
    assertEquals("2017-06-15T11",
            JodaTimeUtils.timexDateValue(utc("2017-06-15T10:00:00Z"), utc("2017-06-15T11:00:00Z")));
  }

  @Test
  public void testTimexDateValueForIdenticalEndpoints() {
    assertEquals("2017-06-15T10:30",
            JodaTimeUtils.timexDateValue(utc("2017-06-15T10:30:00Z"), utc("2017-06-15T10:30:00Z")));
  }

  @Test
  public void testTimexDateValueFallsBackToDurationForUnalignedRanges() {
    // Three days is not a calendar unit, so without forceDate the result is a duration.
    assertEquals("PT72H",
            JodaTimeUtils.timexDateValue(utc("2017-06-15T00:00:00Z"), utc("2017-06-18T00:00:00Z")));
    assertEquals("PT1464H",
            JodaTimeUtils.timexDateValue(utc("2017-06-15T00:00:00Z"), utc("2017-08-15T00:00:00Z")));
  }

  @Test
  public void testTimexDateValueForceDateUsesTheStartDate() {
    JodaTimeUtils.ConversionOptions opts = new JodaTimeUtils.ConversionOptions();
    opts.forceDate = true;
    assertEquals("2017-06-15",
            JodaTimeUtils.timexDateValue(utc("2017-06-15T00:00:00Z"), utc("2017-06-18T00:00:00Z"), opts));
    assertEquals("2017-06-15",
            JodaTimeUtils.timexDateValue(utc("2017-06-15T00:00:00Z"), utc("2017-08-15T00:00:00Z"), opts));
    // Ranges that already align are unaffected.
    assertEquals("2017-06",
            JodaTimeUtils.timexDateValue(utc("2017-06-01T00:00:00Z"), utc("2017-07-01T00:00:00Z"), opts));
  }

  @Test
  public void testTimexDateValueReferenceSentinels() {
    assertEquals("PAST_REF", JodaTimeUtils.timexDateValue(
            new DateTime(-200000, 1, 1, 0, 0, DateTimeZone.UTC), utc("2017-06-15T00:00:00Z")));
    assertEquals("FUTURE_REF", JodaTimeUtils.timexDateValue(
            utc("2017-06-15T00:00:00Z"), new DateTime(200000, 1, 1, 0, 0, DateTimeZone.UTC)));
  }

  // --------------------------------------------------- timexDurationValue

  @Test
  public void testTimexDurationValueSimpleUnits() {
    assertEquals("P1Y", JodaTimeUtils.timexDurationValue(Period.years(1)));
    assertEquals("P5Y", JodaTimeUtils.timexDurationValue(Period.years(5)));
    assertEquals("P1M", JodaTimeUtils.timexDurationValue(Period.months(1)));
    assertEquals("P4M", JodaTimeUtils.timexDurationValue(Period.months(4)));
    assertEquals("P1W", JodaTimeUtils.timexDurationValue(Period.weeks(1)));
    assertEquals("P30D", JodaTimeUtils.timexDurationValue(Period.days(30)));
    assertEquals("PT1H", JodaTimeUtils.timexDurationValue(Period.hours(1)));
    assertEquals("PT30M", JodaTimeUtils.timexDurationValue(Period.minutes(30)));
    assertEquals("PT45S", JodaTimeUtils.timexDurationValue(Period.seconds(45)));
  }

  @Test
  public void testTimexDurationValuePromotesLargeYearCounts() {
    assertEquals("P1E", JodaTimeUtils.timexDurationValue(Period.years(10)));
    assertEquals("P2E", JodaTimeUtils.timexDurationValue(Period.years(20)));
    assertEquals("P1C", JodaTimeUtils.timexDurationValue(Period.years(100)));
    assertEquals("P2C", JodaTimeUtils.timexDurationValue(Period.years(200)));
    assertEquals("P1L", JodaTimeUtils.timexDurationValue(Period.years(1000)));
    assertEquals("P1L2C3E4Y", JodaTimeUtils.timexDurationValue(Period.years(1234)));
  }

  @Test
  public void testTimexDurationValuePromotesWholeQuarters() {
    assertEquals("P1Q", JodaTimeUtils.timexDurationValue(Period.months(3)));
    assertEquals("P2Q", JodaTimeUtils.timexDurationValue(Period.months(6)));
  }

  @Test
  public void testTimexDurationValueCompoundPeriods() {
    assertEquals("P1Y2Q3D", JodaTimeUtils.timexDurationValue(Period.years(1).withMonths(6).withDays(3)));
    assertEquals("PT2H30M", JodaTimeUtils.timexDurationValue(Period.hours(2).withMinutes(30)));
    assertEquals("P1DT12H", JodaTimeUtils.timexDurationValue(Period.days(1).withHours(12)));
  }

  @Test
  public void testTimexDurationValueOfZero() {
    // QUIRK: every field is zero, so nothing is appended and the result is a bare "P",
    // which is not a valid ISO 8601 duration.
    assertEquals("P", JodaTimeUtils.timexDurationValue(new Period(0)));
  }

  @Test
  public void testTimexDurationValueApproximate() {
    JodaTimeUtils.ConversionOptions opts = new JodaTimeUtils.ConversionOptions();
    opts.approximate = true;
    assertEquals("PXY", JodaTimeUtils.timexDurationValue(Period.years(1), opts));
    assertEquals("PXE", JodaTimeUtils.timexDurationValue(Period.years(20), opts));
    assertEquals("PXLXCXEXY", JodaTimeUtils.timexDurationValue(Period.years(1234), opts));
    assertEquals("PTXHXM", JodaTimeUtils.timexDurationValue(Period.hours(2).withMinutes(30), opts));
  }

  @Test
  public void testTimexDurationValueForcedUnits() {
    JodaTimeUtils.ConversionOptions opts = new JodaTimeUtils.ConversionOptions();
    opts.forceUnits = new String[] {"Y"};
    assertEquals("P10Y", JodaTimeUtils.timexDurationValue(Period.years(10), opts));
    assertEquals("P100Y", JodaTimeUtils.timexDurationValue(Period.years(100), opts));
    assertEquals("P1234Y", JodaTimeUtils.timexDurationValue(Period.years(1234), opts));
    // QUIRK: forceUnits only gates the year-level promotions, so months still collapse
    // into quarters even when Y is the only forced unit.
    assertEquals("P1Q", JodaTimeUtils.timexDurationValue(Period.months(3), opts));
  }

  @Test
  public void testTimexDurationValueBetweenTwoTimes() {
    // The period is built from a millisecond count, so it only ever has time fields:
    // a calendar day comes out as 24 hours rather than P1D.
    assertEquals("PT24H", JodaTimeUtils.timexDurationValue(
            utc("2017-06-15T00:00:00Z"), utc("2017-06-16T00:00:00Z")));
    assertEquals("PT2H", JodaTimeUtils.timexDurationValue(
            utc("2017-06-15T00:00:00Z"), utc("2017-06-15T02:00:00Z")));
  }

}
