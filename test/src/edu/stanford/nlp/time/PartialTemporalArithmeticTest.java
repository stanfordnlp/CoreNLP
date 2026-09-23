package edu.stanford.nlp.time;

import org.junit.Test;
import org.threeten.extra.PartialTemporal;
import org.threeten.extra.TemporalFields;

import java.time.temporal.ChronoField;
import java.time.temporal.ChronoUnit;
import java.time.temporal.IsoFields;
import java.time.temporal.TemporalField;

import static org.junit.Assert.assertEquals;

/**
 * Tests for {@link PartialTemporalArithmetic}.
 *
 * <p>The three rules under test are that only units the partial carries are added, that a
 * field running past its range carries into the next larger field the partial holds, and
 * that it wraps instead when there is no larger field. Dates go through
 * {@code LocalDate}, so month lengths and leap years apply.
 *
 * <p>These values also match joda's {@code Partial.withPeriodAdded} and
 * {@code withFieldAddWrapped} over a thousand-case comparison run covering the same shapes;
 * this file is what remains once joda is gone.
 */
public class PartialTemporalArithmeticTest {

  private static PartialTemporal of(Object... fieldsAndValues) {
    PartialTemporal partial = PartialTemporal.empty();
    for (int i = 0; i < fieldsAndValues.length; i += 2) {
      partial = partial.withField((TemporalField) fieldsAndValues[i],
              ((Number) fieldsAndValues[i + 1]).longValue());
    }
    return partial;
  }

  private static PartialTemporal ymd(int year, int month, int day) {
    return of(ChronoField.YEAR, year, ChronoField.MONTH_OF_YEAR, month,
            ChronoField.DAY_OF_MONTH, day);
  }

  private static String show(PartialTemporal partial) {
    StringBuilder sb = new StringBuilder();
    partial.getFields().forEach((field, value) -> {
      if (sb.length() > 0) {
        sb.append(", ");
      }
      sb.append(field).append('=').append(value);
    });
    return sb.toString();
  }

  private static void assertPlus(String expected, PartialTemporal partial,
                                 ChronoUnit unit, long amount) {
    assertEquals(expected, show(PartialTemporalArithmetic.plus(partial, unit, amount)));
  }

  // ------------------------------------------------------- units not present

  @Test
  public void testAUnitThePartialDoesNotCarryIsIgnored() {
    PartialTemporal yearMonth = of(ChronoField.YEAR, 2017, ChronoField.MONTH_OF_YEAR, 6);
    assertPlus("Year=2017, MonthOfYear=6", yearMonth, ChronoUnit.DAYS, 1);
    assertPlus("Year=2017, MonthOfYear=6", yearMonth, ChronoUnit.HOURS, 5);
  }

  @Test
  public void testWeeksAreIgnoredBecauseThereIsNoWeekField() {
    // SUTime handles week offsets separately; a partial has no field for them.
    assertPlus("Year=2017, MonthOfYear=6, DayOfMonth=15", ymd(2017, 6, 15), ChronoUnit.WEEKS, 1);
  }

  @Test
  public void testAddingZeroChangesNothing() {
    assertPlus("Year=2017, MonthOfYear=6, DayOfMonth=15", ymd(2017, 6, 15), ChronoUnit.DAYS, 0);
  }

  // ------------------------------------------------------------ carrying

  @Test
  public void testMonthCarriesIntoTheYear() {
    assertPlus("Year=2018, MonthOfYear=1",
            of(ChronoField.YEAR, 2017, ChronoField.MONTH_OF_YEAR, 12), ChronoUnit.MONTHS, 1);
    assertPlus("Year=2016, MonthOfYear=12",
            of(ChronoField.YEAR, 2017, ChronoField.MONTH_OF_YEAR, 1), ChronoUnit.MONTHS, -1);
    assertPlus("Year=2019, MonthOfYear=1",
            of(ChronoField.YEAR, 2017, ChronoField.MONTH_OF_YEAR, 1), ChronoUnit.MONTHS, 24);
  }

  @Test
  public void testQuarterAndHalfYearCarry() {
    assertEquals("Year=2018, QuarterOfYear=1",
            show(PartialTemporalArithmetic.plus(
                    of(ChronoField.YEAR, 2017, IsoFields.QUARTER_OF_YEAR, 4),
                    IsoFields.QUARTER_YEARS, 1)));
    assertEquals("Year=2019, QuarterOfYear=1",
            show(PartialTemporalArithmetic.plus(
                    of(ChronoField.YEAR, 2017, IsoFields.QUARTER_OF_YEAR, 4),
                    IsoFields.QUARTER_YEARS, 5)));
    assertEquals("Year=2015, QuarterOfYear=4",
            show(PartialTemporalArithmetic.plus(
                    of(ChronoField.YEAR, 2017, IsoFields.QUARTER_OF_YEAR, 1),
                    IsoFields.QUARTER_YEARS, -5)));
    assertEquals("Year=2018, HalfOfYear=1",
            show(PartialTemporalArithmetic.plus(
                    of(ChronoField.YEAR, 2017, TemporalFields.HALF_OF_YEAR, 2),
                    TemporalFields.HALF_YEARS, 1)));
  }

  @Test
  public void testMinutesCarryIntoHours() {
    assertPlus("HourOfDay=11, MinuteOfHour=0",
            of(ChronoField.HOUR_OF_DAY, 10, ChronoField.MINUTE_OF_HOUR, 30),
            ChronoUnit.MINUTES, 30);
    assertPlus("HourOfDay=9, MinuteOfHour=45",
            of(ChronoField.HOUR_OF_DAY, 10, ChronoField.MINUTE_OF_HOUR, 15),
            ChronoUnit.MINUTES, -30);
  }

  // ------------------------------------------------------------- wrapping

  @Test
  public void testAFieldWithNoLargerFieldWrapsInsteadOfCarrying() {
    assertPlus("MonthOfYear=1", of(ChronoField.MONTH_OF_YEAR, 12), ChronoUnit.MONTHS, 1);
    assertPlus("MonthOfYear=12", of(ChronoField.MONTH_OF_YEAR, 1), ChronoUnit.MONTHS, -1);
    assertEquals("QuarterOfYear=1",
            show(PartialTemporalArithmetic.plus(of(IsoFields.QUARTER_OF_YEAR, 4),
                    IsoFields.QUARTER_YEARS, 1)));
  }

  // ------------------------------------------------------- real calendar dates

  @Test
  public void testDaysRespectMonthLengths() {
    assertPlus("Year=2017, MonthOfYear=7, DayOfMonth=1", ymd(2017, 6, 30), ChronoUnit.DAYS, 1);
    assertPlus("Year=2018, MonthOfYear=1, DayOfMonth=1", ymd(2017, 12, 31), ChronoUnit.DAYS, 1);
    assertPlus("Year=2016, MonthOfYear=2, DayOfMonth=29", ymd(2016, 2, 28), ChronoUnit.DAYS, 1);
    assertPlus("Year=2017, MonthOfYear=3, DayOfMonth=1", ymd(2017, 2, 28), ChronoUnit.DAYS, 1);
  }

  @Test
  public void testMonthsClampTheDayWhereTheTargetMonthIsShorter() {
    assertPlus("Year=2017, MonthOfYear=2, DayOfMonth=28", ymd(2017, 1, 31), ChronoUnit.MONTHS, 1);
    assertPlus("Year=2016, MonthOfYear=2, DayOfMonth=29", ymd(2016, 1, 31), ChronoUnit.MONTHS, 1);
    assertPlus("Year=2017, MonthOfYear=4, DayOfMonth=30", ymd(2017, 3, 31), ChronoUnit.MONTHS, 1);
  }

  @Test
  public void testYearsClampTheLeapDay() {
    assertPlus("Year=2017, MonthOfYear=2, DayOfMonth=28", ymd(2016, 2, 29), ChronoUnit.YEARS, 1);
    assertPlus("Year=2020, MonthOfYear=2, DayOfMonth=29", ymd(2016, 2, 29), ChronoUnit.YEARS, 4);
  }

  @Test
  public void testDerivedDateFieldsFollowTheNewDate() {
    // A partial holding a quarter alongside a full date keeps them consistent.
    PartialTemporal withQuarter = ymd(2017, 3, 31).withField(IsoFields.QUARTER_OF_YEAR, 1);
    assertEquals("Year=2017, QuarterOfYear=2, MonthOfYear=4, DayOfMonth=30",
            show(PartialTemporalArithmetic.plus(withQuarter, ChronoUnit.MONTHS, 1)));
  }

  @Test
  public void testTimeFieldsSurviveADateChange() {
    PartialTemporal dateAndTime = ymd(2017, 6, 15)
            .withField(ChronoField.HOUR_OF_DAY, 10)
            .withField(ChronoField.MINUTE_OF_HOUR, 30);
    assertPlus("Year=2017, MonthOfYear=6, DayOfMonth=16, HourOfDay=10, MinuteOfHour=30",
            dateAndTime, ChronoUnit.DAYS, 1);
  }

}
