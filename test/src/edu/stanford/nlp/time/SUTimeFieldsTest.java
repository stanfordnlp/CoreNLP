package edu.stanford.nlp.time;

import org.junit.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.IsoFields;
import java.time.temporal.UnsupportedTemporalTypeException;
import java.time.temporal.ValueRange;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Tests for {@link SUTimeFields}, the calendar fields SUTime needs that java.time and
 * threeten-extra do not supply.
 *
 * <p>The expected values are the arithmetic each field is defined by, written out here
 * rather than taken from previous output, so a failure means the implementation is wrong
 * rather than merely changed. Every field is also checked against joda-time over every day
 * from 1900 to 2100 by a separate comparison run; this file is what remains once joda is
 * gone.
 */
public class SUTimeFieldsTest {

  private static LocalDate date(int year, int month, int day) {
    return LocalDate.of(year, month, day);
  }

  // ------------------------------------------------------ month within a quarter

  @Test
  public void testMonthOfQuarterCyclesEveryThreeMonths() {
    for (int month = 1; month <= 12; month++) {
      assertEquals("month " + month, ((month - 1) % 3) + 1,
              date(2017, month, 15).getLong(SUTimeFields.MONTH_OF_QUARTER));
    }
  }

  @Test
  public void testMonthOfQuarterKnownValues() {
    assertEquals(1, date(2017, 1, 15).getLong(SUTimeFields.MONTH_OF_QUARTER));
    assertEquals(3, date(2017, 3, 31).getLong(SUTimeFields.MONTH_OF_QUARTER));
    assertEquals(1, date(2017, 4, 1).getLong(SUTimeFields.MONTH_OF_QUARTER));
    assertEquals(3, date(2017, 12, 31).getLong(SUTimeFields.MONTH_OF_QUARTER));
  }

  @Test
  public void testMonthOfQuarterSetsTheMonthWithinItsQuarter() {
    // November is in Q4, so the first month of that quarter is October.
    assertEquals(date(2017, 10, 15), date(2017, 11, 15).with(SUTimeFields.MONTH_OF_QUARTER, 1));
    assertEquals(date(2017, 12, 15), date(2017, 11, 15).with(SUTimeFields.MONTH_OF_QUARTER, 3));
    // Setting the value a date already has leaves it alone.
    assertEquals(date(2017, 11, 15), date(2017, 11, 15).with(SUTimeFields.MONTH_OF_QUARTER, 2));
  }

  // --------------------------------------------------- month within a half year

  @Test
  public void testMonthOfHalfYearCyclesEverySixMonths() {
    for (int month = 1; month <= 12; month++) {
      assertEquals("month " + month, ((month - 1) % 6) + 1,
              date(2017, month, 15).getLong(SUTimeFields.MONTH_OF_HALF_YEAR));
    }
  }

  @Test
  public void testMonthOfHalfYearSetsTheMonthWithinItsHalf() {
    assertEquals(date(2017, 7, 15), date(2017, 9, 15).with(SUTimeFields.MONTH_OF_HALF_YEAR, 1));
    assertEquals(date(2017, 1, 15), date(2017, 3, 15).with(SUTimeFields.MONTH_OF_HALF_YEAR, 1));
  }

  // -------------------------------------------------------------- week of month

  @Test
  public void testWeekOfMonthIsTheIsoWeekModuloFour() {
    // Despite the name this counts ISO weeks of the year in groups of four, so it does not
    // restart with each month. The fourth of July 2017 is in ISO week 27, giving 3.
    assertEquals(2, date(2017, 1, 15).getLong(SUTimeFields.WEEK_OF_MONTH));
    assertEquals(1, date(2017, 4, 1).getLong(SUTimeFields.WEEK_OF_MONTH));
    assertEquals(3, date(2017, 7, 4).getLong(SUTimeFields.WEEK_OF_MONTH));
    assertEquals(4, date(2017, 12, 31).getLong(SUTimeFields.WEEK_OF_MONTH));
  }

  @Test
  public void testWeekOfMonthStaysWithinItsRange() {
    LocalDate day = date(2017, 1, 1);
    while (day.getYear() < 2020) {
      long value = day.getLong(SUTimeFields.WEEK_OF_MONTH);
      assertTrue("out of range on " + day, value >= 1 && value <= 4);
      day = day.plusDays(1);
    }
  }

  @Test
  public void testWeekOfMonthCannotBeSet() {
    // The field throws away which group of four weeks it came from, so setting it has no
    // well defined meaning.
    try {
      date(2017, 7, 4).with(SUTimeFields.WEEK_OF_MONTH, 2);
      fail("expected setting WeekOfMonth to be rejected");
    } catch (UnsupportedTemporalTypeException expected) {
      // as intended
    }
  }

  // --------------------------------------------------- decade and year of decade

  @Test
  public void testDecadeOfCentury() {
    assertEquals(9, date(1997, 6, 15).getLong(SUTimeFields.DECADE_OF_CENTURY));
    assertEquals(0, date(2000, 2, 29).getLong(SUTimeFields.DECADE_OF_CENTURY));
    assertEquals(1, date(2017, 1, 15).getLong(SUTimeFields.DECADE_OF_CENTURY));
    assertEquals(2, date(2023, 11, 8).getLong(SUTimeFields.DECADE_OF_CENTURY));
  }

  @Test
  public void testYearOfDecade() {
    assertEquals(7, date(1997, 6, 15).getLong(SUTimeFields.YEAR_OF_DECADE));
    assertEquals(0, date(2000, 2, 29).getLong(SUTimeFields.YEAR_OF_DECADE));
    assertEquals(7, date(2017, 1, 15).getLong(SUTimeFields.YEAR_OF_DECADE));
    assertEquals(3, date(2023, 11, 8).getLong(SUTimeFields.YEAR_OF_DECADE));
  }

  @Test
  public void testDecadeAndYearOfDecadeAreDistinct() {
    // These two were once defined identically. The decade is the quotient, the year the
    // remainder.
    LocalDate day = date(1997, 6, 15);
    assertEquals(9, day.getLong(SUTimeFields.DECADE_OF_CENTURY));
    assertEquals(7, day.getLong(SUTimeFields.YEAR_OF_DECADE));
  }

  @Test
  public void testSettingTheDecadeKeepsTheYearWithinIt() {
    assertEquals(date(1927, 6, 15), date(1997, 6, 15).with(SUTimeFields.DECADE_OF_CENTURY, 2));
    assertEquals(date(1907, 6, 15), date(1997, 6, 15).with(SUTimeFields.DECADE_OF_CENTURY, 0));
  }

  @Test
  public void testSettingTheYearOfDecadeKeepsTheDecade() {
    assertEquals(date(1993, 6, 15), date(1997, 6, 15).with(SUTimeFields.YEAR_OF_DECADE, 3));
    assertEquals(date(1990, 6, 15), date(1997, 6, 15).with(SUTimeFields.YEAR_OF_DECADE, 0));
  }

  // --------------------------------------------------------------- field metadata

  @Test
  public void testRanges() {
    assertEquals(ValueRange.of(1, 3), SUTimeFields.MONTH_OF_QUARTER.range());
    assertEquals(ValueRange.of(1, 6), SUTimeFields.MONTH_OF_HALF_YEAR.range());
    assertEquals(ValueRange.of(1, 4), SUTimeFields.WEEK_OF_MONTH.range());
    assertEquals(ValueRange.of(0, 9), SUTimeFields.DECADE_OF_CENTURY.range());
    assertEquals(ValueRange.of(0, 9), SUTimeFields.YEAR_OF_DECADE.range());
  }

  @Test
  public void testUnitsPlaceEachFieldInTheRightHierarchy() {
    assertEquals(IsoFields.QUARTER_YEARS, SUTimeFields.MONTH_OF_QUARTER.getRangeUnit());
    assertEquals(org.threeten.extra.TemporalFields.HALF_YEARS,
            SUTimeFields.MONTH_OF_HALF_YEAR.getRangeUnit());
    assertEquals(java.time.temporal.ChronoUnit.MONTHS, SUTimeFields.WEEK_OF_MONTH.getRangeUnit());
    assertEquals(java.time.temporal.ChronoUnit.CENTURIES,
            SUTimeFields.DECADE_OF_CENTURY.getRangeUnit());
    assertEquals(java.time.temporal.ChronoUnit.DECADES, SUTimeFields.YEAR_OF_DECADE.getRangeUnit());
  }

  @Test
  public void testFieldsAreDateBased() {
    assertTrue(SUTimeFields.MONTH_OF_QUARTER.isDateBased());
    assertFalse(SUTimeFields.MONTH_OF_QUARTER.isTimeBased());
    assertTrue(SUTimeFields.DECADE_OF_CENTURY.isDateBased());
  }

  @Test
  public void testATemporalWithoutTheUnderlyingFieldsIsUnsupported() {
    LocalTime noon = LocalTime.NOON;
    assertFalse(SUTimeFields.MONTH_OF_QUARTER.isSupportedBy(noon));
    assertFalse(SUTimeFields.DECADE_OF_CENTURY.isSupportedBy(noon));
    try {
      noon.getLong(SUTimeFields.MONTH_OF_QUARTER);
      fail("expected an unsupported field on a time-only temporal");
    } catch (UnsupportedTemporalTypeException expected) {
      // as intended
    }
  }

  @Test
  public void testNamesAreStable() {
    // These appear in error messages and in any serialized form.
    assertEquals("MonthOfQuarter", SUTimeFields.MONTH_OF_QUARTER.toString());
    assertEquals("MonthOfHalfYear", SUTimeFields.MONTH_OF_HALF_YEAR.toString());
    assertEquals("WeekOfMonth", SUTimeFields.WEEK_OF_MONTH.toString());
    assertEquals("DecadeOfCentury", SUTimeFields.DECADE_OF_CENTURY.toString());
    assertEquals("YearOfDecade", SUTimeFields.YEAR_OF_DECADE.toString());
  }

}
