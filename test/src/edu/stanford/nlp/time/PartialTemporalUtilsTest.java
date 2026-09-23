package edu.stanford.nlp.time;

import org.junit.Test;
import org.threeten.extra.PartialTemporal;
import org.threeten.extra.TemporalFields;

import java.time.temporal.ChronoField;
import java.time.temporal.ChronoUnit;
import java.time.temporal.IsoFields;
import java.time.temporal.TemporalField;
import java.time.temporal.WeekFields;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Tests for {@link PartialTemporalUtils}, the partial-date algebra.
 *
 * <p>These values also match joda's over an 865-case differential run covering the same
 * shapes; this file is what remains once joda is gone.
 */
public class PartialTemporalUtilsTest {

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

  private static String show(PartialTemporal p) {
    StringBuilder sb = new StringBuilder();
    p.getFields().forEach((f, v) -> {
      if (sb.length() > 0) {
        sb.append(", ");
      }
      sb.append(f).append('=').append(v);
    });
    return sb.toString();
  }

  // ------------------------------------------------------------ predicates

  @Test
  public void testHasFieldToleratesNull() {
    assertFalse(PartialTemporalUtils.hasField(null, ChronoField.YEAR));
    assertTrue(PartialTemporalUtils.hasField(ymd(2017, 6, 15), ChronoField.YEAR));
    assertFalse(PartialTemporalUtils.hasField(ymd(2017, 6, 15), ChronoField.HOUR_OF_DAY));
  }

  @Test
  public void testSetFieldToleratesNull() {
    assertEquals("Year=2017", show(PartialTemporalUtils.setField(null, ChronoField.YEAR, 2017)));
  }

  @Test
  public void testFullDatePredicates() {
    assertTrue(PartialTemporalUtils.hasYYYYMMDD(ymd(2017, 6, 15)));
    assertFalse(PartialTemporalUtils.hasYYYYMMDD(of(ChronoField.YEAR, 2017)));
    assertTrue(PartialTemporalUtils.hasYYMMDD(of(SUTimeFields.YEAR_OF_CENTURY, 97,
            ChronoField.MONTH_OF_YEAR, 6, ChronoField.DAY_OF_MONTH, 15)));
    assertFalse(PartialTemporalUtils.hasYYMMDD(ymd(2017, 6, 15)));
  }

  // ------------------------------------------------------- field ordering

  @Test
  public void testCoarsestAndFinestField() {
    assertEquals(ChronoField.YEAR, PartialTemporalUtils.getMostGeneral(ymd(2017, 6, 15)));
    assertEquals(ChronoField.DAY_OF_MONTH, PartialTemporalUtils.getMostSpecific(ymd(2017, 6, 15)));
    assertNull(PartialTemporalUtils.getMostGeneral(PartialTemporal.empty()));
    assertNull(PartialTemporalUtils.getMostSpecific(PartialTemporal.empty()));
  }

  @Test
  public void testGenerality() {
    assertTrue(PartialTemporalUtils.isMoreGeneral(ChronoField.YEAR, ChronoField.MONTH_OF_YEAR));
    assertTrue(PartialTemporalUtils.isMoreGeneral(ChronoField.MONTH_OF_YEAR, ChronoField.DAY_OF_MONTH));
    assertTrue(PartialTemporalUtils.isMoreSpecific(ChronoField.DAY_OF_MONTH, ChronoField.MONTH_OF_YEAR));
    assertFalse(PartialTemporalUtils.isMoreGeneral(ChronoField.YEAR, ChronoField.YEAR));
  }

  @Test
  public void testNothingIsMoreGeneralThanAYear() {
    // A year is bounded by no larger unit, so the comparison gives up rather than ranking
    // a century above it. combineMoreGeneralFields relies on this.
    assertFalse(PartialTemporalUtils.isMoreGeneral(SUTimeFields.CENTURY_OF_ERA, ChronoField.YEAR));
    assertFalse(PartialTemporalUtils.isMoreGeneral(SUTimeFields.DECADE_OF_CENTURY, ChronoField.YEAR));
    assertFalse(PartialTemporalUtils.isMoreSpecific(ChronoField.YEAR, SUTimeFields.CENTURY_OF_ERA));
  }

  // ------------------------------------------------------------ compatibility

  @Test
  public void testCompatibility() {
    assertTrue(PartialTemporalUtils.isCompatible(null, ymd(2017, 6, 15)));
    assertTrue(PartialTemporalUtils.isCompatible(of(ChronoField.YEAR, 2017), of(ChronoField.MONTH_OF_YEAR, 6)));
    assertTrue(PartialTemporalUtils.isCompatible(of(ChronoField.YEAR, 2017), of(ChronoField.YEAR, 2017)));
    assertFalse(PartialTemporalUtils.isCompatible(of(ChronoField.YEAR, 2017), of(ChronoField.YEAR, 2018)));
  }

  // ---------------------------------------------------------------- combine

  @Test
  public void testCombineFillsGapsAndKeepsWhatIsThere() {
    assertEquals("Year=2017, MonthOfYear=6, DayOfMonth=15",
            show(PartialTemporalUtils.combine(
                    of(ChronoField.MONTH_OF_YEAR, 6, ChronoField.DAY_OF_MONTH, 15),
                    of(ChronoField.YEAR, 2017))));
    assertEquals("Year=2017", show(PartialTemporalUtils.combine(
            of(ChronoField.YEAR, 2017), of(ChronoField.YEAR, 1999))));
  }

  @Test
  public void testCombineResolvesATwoDigitYear() {
    // '97 against 2017 reads as 1997, since 2097 is still to come.
    assertEquals("Year=1997", show(PartialTemporalUtils.combine(
            of(SUTimeFields.YEAR_OF_CENTURY, 97), of(ChronoField.YEAR, 2017))));
    assertEquals("Year=2017", show(PartialTemporalUtils.combine(
            of(SUTimeFields.YEAR_OF_CENTURY, 17), of(ChronoField.YEAR, 2017))));
  }

  @Test
  public void testCombineCollapsesCenturyAndYearOfCentury() {
    assertEquals("Year=1997", show(PartialTemporalUtils.combine(
            of(SUTimeFields.CENTURY_OF_ERA, 19, SUTimeFields.YEAR_OF_CENTURY, 97),
            PartialTemporal.empty())));
  }

  @Test
  public void testCombineMoreGeneralFieldsTakesOnlyCoarserFields() {
    assertEquals("Year=2017, MonthOfYear=6, DayOfMonth=15",
            show(PartialTemporalUtils.combineMoreGeneralFields(
                    of(ChronoField.MONTH_OF_YEAR, 6, ChronoField.DAY_OF_MONTH, 15),
                    ymd(2017, 1, 2))));
    assertEquals("Year=2017, MonthOfYear=1, DayOfMonth=15",
            show(PartialTemporalUtils.combineMoreGeneralFields(
                    of(ChronoField.DAY_OF_MONTH, 15), ymd(2017, 1, 2))));
  }

  @Test
  public void testCombineMoreGeneralFieldsLeavesAYearAlone() {
    // Nothing is coarser than a year, so a century in the reference is not pulled across.
    assertEquals("Year=2017, MonthOfYear=6, DayOfMonth=15",
            show(PartialTemporalUtils.combineMoreGeneralFields(ymd(2017, 6, 15),
                    of(SUTimeFields.CENTURY_OF_ERA, 19, SUTimeFields.YEAR_OF_CENTURY, 97))));
  }

  @Test
  public void testCombineMoreGeneralFieldsResolvesADecade() {
    // The nineties against 2017 sit in the twentieth century, not the twenty-first.
    assertEquals("CenturyOfEra=19, DecadeOfCentury=9",
            show(PartialTemporalUtils.combineMoreGeneralFields(
                    of(SUTimeFields.DECADE_OF_CENTURY, 9), of(ChronoField.YEAR, 2017))));
  }

  // ---------------------------------------------------------------- trimming

  @Test
  public void testDiscardMoreSpecificFields() {
    PartialTemporal full = ymd(2017, 6, 15)
            .withField(ChronoField.HOUR_OF_DAY, 10).withField(ChronoField.MINUTE_OF_HOUR, 30);
    assertEquals("Year=2017, MonthOfYear=6, DayOfMonth=15",
            show(PartialTemporalUtils.discardMoreSpecificFields(full, ChronoField.DAY_OF_MONTH)));
    assertEquals("Year=2017", show(PartialTemporalUtils.discardMoreSpecificFields(full, ChronoField.YEAR)));
    assertEquals("Year=2017, MonthOfYear=6, DayOfMonth=15, HourOfDay=10",
            show(PartialTemporalUtils.discardMoreSpecificFields(full, ChronoUnit.HOURS)));
  }

  @Test
  public void testDiscardKeepsTheCenturyBehindADecade() {
    assertEquals("CenturyOfEra=19, DecadeOfCentury=9",
            show(PartialTemporalUtils.discardMoreSpecificFields(
                    of(ChronoField.YEAR, 1997, SUTimeFields.DECADE_OF_CENTURY, 9),
                    SUTimeFields.DECADE_OF_CENTURY)));
  }

  // ----------------------------------------------------------------- padding

  @Test
  public void testPaddingFillsOutToMilliseconds() {
    assertEquals("Year=2017, MonthOfYear=1, DayOfMonth=1, HourOfDay=0, MinuteOfHour=0, "
                    + "SecondOfMinute=0, MilliOfSecond=0",
            show(PartialTemporalUtils.padMoreSpecificFields(of(ChronoField.YEAR, 2017), null)));
  }

  @Test
  public void testPaddingStopsAtTheGivenGranularity() {
    assertEquals("Year=2017, MonthOfYear=6, DayOfMonth=1",
            show(PartialTemporalUtils.padMoreSpecificFields(
                    of(ChronoField.YEAR, 2017, ChronoField.MONTH_OF_YEAR, 6), ChronoUnit.DAYS)));
    assertEquals("Year=2017, MonthOfYear=6",
            show(PartialTemporalUtils.padMoreSpecificFields(
                    of(ChronoField.YEAR, 2017, ChronoField.MONTH_OF_YEAR, 6), ChronoUnit.MONTHS)));
  }

  @Test
  public void testAQuarterSuppliesTheMonthItStartsIn() {
    assertTrue(show(PartialTemporalUtils.padMoreSpecificFields(
            of(ChronoField.YEAR, 2017, IsoFields.QUARTER_OF_YEAR, 3), ChronoUnit.DAYS))
            .contains("MonthOfYear=7"));
    assertTrue(show(PartialTemporalUtils.padMoreSpecificFields(
            of(ChronoField.YEAR, 2017, TemporalFields.HALF_OF_YEAR, 2), ChronoUnit.DAYS))
            .contains("MonthOfYear=7"));
  }

  @Test
  public void testACenturyAndDecadeBecomeTheYearTheyOpen() {
    assertEquals("Year=1990, MonthOfYear=1, DayOfMonth=1",
            show(PartialTemporalUtils.padMoreSpecificFields(
                    of(SUTimeFields.CENTURY_OF_ERA, 19, SUTimeFields.DECADE_OF_CENTURY, 9),
                    ChronoUnit.DAYS)));
    assertEquals("Year=2000, MonthOfYear=1, DayOfMonth=1",
            show(PartialTemporalUtils.padMoreSpecificFields(
                    of(SUTimeFields.CENTURY_OF_ERA, 20), ChronoUnit.DAYS)));
  }

  @Test
  public void testAWeekPartialIsPaddedWithADayOfTheWeek() {
    PartialTemporal padded = PartialTemporalUtils.padMoreSpecificFields(
            of(ChronoField.YEAR, 2017, WeekFields.ISO.weekOfWeekBasedYear(), 25), ChronoUnit.DAYS);
    assertEquals(3, padded.size());
    assertEquals(2017, padded.getLong(ChronoField.YEAR));
    assertEquals(25, padded.getLong(WeekFields.ISO.weekOfWeekBasedYear()));
    assertEquals(1, padded.getLong(ChronoField.DAY_OF_WEEK));
    assertFalse("a week partial takes a day of the week, not a day of the month",
            padded.isSupported(ChronoField.DAY_OF_MONTH));
  }

  @Test
  public void testPaddingAnEmptyPartialGivesTheEpochFields() {
    // joda throws here, dereferencing a most-specific field that does not exist. There is
    // nothing to be gained from reproducing that.
    assertEquals("Year=0, MonthOfYear=1, DayOfMonth=1, HourOfDay=0, MinuteOfHour=0, "
                    + "SecondOfMinute=0, MilliOfSecond=0",
            show(PartialTemporalUtils.padMoreSpecificFields(PartialTemporal.empty(), null)));
  }

}
