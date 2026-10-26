package edu.stanford.nlp.time;

import org.junit.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.time.temporal.UnsupportedTemporalTypeException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Tests {@link SUTimePeriod}, the eight-field period SUTime's durations are built on.
 *
 * <p>The expected values here were taken from joda-time 2.14.0, which SUTime used before,
 * so that a duration keeps producing the TIMEX3 value it always did. The two places this
 * class deliberately differs from joda are marked {@code DEVIATION}.
 *
 * @author John Bauer
 */
public class SUTimePeriodTest {

  private static final Instant EPOCH = Instant.EPOCH;
  private static final Instant JAN_31 = Instant.parse("2017-01-31T00:00:00Z");

  // ---- ISO-8601 output, which is the TIMEX3 duration value ----

  @Test
  public void testIsoSingleFields() {
    assertEquals("P1Y", SUTimePeriod.ofYears(1).toString());
    assertEquals("P3M", SUTimePeriod.ofMonths(3).toString());
    assertEquals("P1W", SUTimePeriod.ofWeeks(1).toString());
    assertEquals("P10D", SUTimePeriod.ofDays(10).toString());
    assertEquals("PT1H", SUTimePeriod.ofHours(1).toString());
    assertEquals("PT30M", SUTimePeriod.ofMinutes(30).toString());
    assertEquals("PT90S", SUTimePeriod.ofSeconds(90).toString());
  }

  /** A week stays a week rather than becoming seven days, which java.time.Period cannot do. */
  @Test
  public void testWeeksAreNotFoldedIntoDays() {
    assertEquals("P1W", SUTimePeriod.ofWeeks(1).toString());
    assertEquals("P2W", SUTimePeriod.ofWeeks(2).toString());
    assertEquals("P7D", SUTimePeriod.ofDays(7).toString());
    assertEquals(ChronoUnit.WEEKS, SUTimePeriod.ofWeeks(1).getMostSpecificUnit());
  }

  @Test
  public void testIsoZero() {
    assertEquals("PT0S", SUTimePeriod.ZERO.toString());
    assertTrue(SUTimePeriod.ZERO.isZero());
  }

  @Test
  public void testIsoCombinedFields() {
    assertEquals("P1Y2M3W4D",
            SUTimePeriod.ofYears(1).with(ChronoUnit.MONTHS, 2)
                    .with(ChronoUnit.WEEKS, 3).with(ChronoUnit.DAYS, 4).toString());
    assertEquals("P1Y1M1W1DT1H1M1.001S",
            SUTimePeriod.ofYears(1).with(ChronoUnit.MONTHS, 1).with(ChronoUnit.WEEKS, 1)
                    .with(ChronoUnit.DAYS, 1).with(ChronoUnit.HOURS, 1)
                    .with(ChronoUnit.MINUTES, 1).with(ChronoUnit.SECONDS, 1)
                    .with(ChronoUnit.MILLIS, 1).toString());
  }

  /** Milliseconds join the seconds field as a decimal fraction, as joda writes them. */
  @Test
  public void testIsoMillisJoinSeconds() {
    assertEquals("PT1.500S", SUTimePeriod.ofMillis(1500).toString());
    assertEquals("PT0.005S", SUTimePeriod.ofMillis(5).toString());
    assertEquals("PT1.005S", SUTimePeriod.ofMillis(1005).toString());
    assertEquals("PT5H6M7.008S",
            SUTimePeriod.ofHours(5).with(ChronoUnit.MINUTES, 6)
                    .with(ChronoUnit.SECONDS, 7).with(ChronoUnit.MILLIS, 8).toString());
  }

  /** Seconds and milliseconds are written as one signed total, so their signs can cancel. */
  @Test
  public void testIsoSecondsAndMillisShareASign() {
    assertEquals("PT-7.008S",
            SUTimePeriod.ofSeconds(-7).with(ChronoUnit.MILLIS, -8).toString());
    assertEquals("PT-0.008S", SUTimePeriod.ofMillis(-8).toString());
    assertEquals("PT-0.500S",
            SUTimePeriod.ofSeconds(-1).with(ChronoUnit.MILLIS, 500).toString());
    assertEquals("PT0.500S",
            SUTimePeriod.ofSeconds(1).with(ChronoUnit.MILLIS, -500).toString());
  }

  /**
   * DEVIATION: joda writes a bare {@code "P"} when the seconds and milliseconds cancel to
   * zero but the fields themselves are not zero, because it decides whether to write
   * {@code PT0S} from the fields rather than from what it wrote. A bare {@code "P"} is not
   * a valid ISO-8601 duration, and joda's own parser reads it back as {@code PT0S}, so
   * that is what this writes.
   */
  @Test
  public void testIsoCancellingSecondsAndMillis() {
    assertEquals("PT0S", SUTimePeriod.ofSeconds(-1).with(ChronoUnit.MILLIS, 1000).toString());
    assertEquals("PT0S", SUTimePeriod.ofSeconds(1).with(ChronoUnit.MILLIS, -1000).toString());
  }

  /** Negative fields keep their sign where they sit, rather than moving it to the front. */
  @Test
  public void testIsoNegativeFields() {
    assertEquals("P-1M", SUTimePeriod.ofMonths(-1).toString());
    assertEquals("P-1Y-2M", SUTimePeriod.ofYears(-1).with(ChronoUnit.MONTHS, -2).toString());
  }

  /** SUTime's offset notation for a backwards offset, and the mixed sign of issue #1518. */
  @Test
  public void testIsoMixedSigns() {
    assertEquals("P1Y-1M", SUTimePeriod.ofYears(1).minus(SUTimePeriod.ofMonths(1)).toString());
    assertEquals("P1W-1D", SUTimePeriod.ofWeeks(1).minus(SUTimePeriod.ofDays(1)).toString());
    assertEquals("PT1H-1M", SUTimePeriod.ofHours(1).minus(SUTimePeriod.ofMinutes(1)).toString());
  }

  // ---- fields in order, which is how SUTime finds a duration's granularity ----

  @Test
  public void testFieldOrder() {
    SUTimePeriod p = SUTimePeriod.ZERO;
    assertEquals(8, p.size());
    assertEquals(ChronoUnit.YEARS, p.getUnit(0));
    assertEquals(ChronoUnit.MONTHS, p.getUnit(1));
    assertEquals(ChronoUnit.WEEKS, p.getUnit(2));
    assertEquals(ChronoUnit.DAYS, p.getUnit(3));
    assertEquals(ChronoUnit.HOURS, p.getUnit(4));
    assertEquals(ChronoUnit.MINUTES, p.getUnit(5));
    assertEquals(ChronoUnit.SECONDS, p.getUnit(6));
    assertEquals(ChronoUnit.MILLIS, p.getUnit(7));
  }

  @Test
  public void testMostGeneralAndMostSpecificSkipZeroFields() {
    SUTimePeriod p = SUTimePeriod.ofMonths(2).with(ChronoUnit.MINUTES, 5);
    assertEquals(ChronoUnit.MONTHS, p.getMostGeneralUnit());
    assertEquals(ChronoUnit.MINUTES, p.getMostSpecificUnit());

    assertEquals(ChronoUnit.YEARS, SUTimePeriod.ofYears(1).getMostGeneralUnit());
    assertEquals(ChronoUnit.YEARS, SUTimePeriod.ofYears(1).getMostSpecificUnit());
  }

  @Test
  public void testMostGeneralAndMostSpecificOfZeroAreNull() {
    assertNull(SUTimePeriod.ZERO.getMostGeneralUnit());
    assertNull(SUTimePeriod.ZERO.getMostSpecificUnit());
  }

  /** A field set to zero is still not a non-zero field, so it is skipped. */
  @Test
  public void testExplicitZeroIsSkipped() {
    SUTimePeriod p = SUTimePeriod.ofYears(0).with(ChronoUnit.DAYS, 3);
    assertEquals(ChronoUnit.DAYS, p.getMostGeneralUnit());
    assertEquals("P3D", p.toString());
  }

  // ---- fields a period does not have ----

  /**
   * A period has no field for a decade, a century or a half day; a duration of ten years
   * is {@code P10Y}. joda throws {@code IllegalArgumentException} for these, and this
   * throws its {@code java.time} counterpart.
   */
  @Test
  public void testUnsupportedUnits() {
    assertFalse(SUTimePeriod.isSupportedUnit(ChronoUnit.DECADES));
    assertFalse(SUTimePeriod.isSupportedUnit(ChronoUnit.CENTURIES));
    assertFalse(SUTimePeriod.isSupportedUnit(ChronoUnit.HALF_DAYS));
    assertFalse(SUTimePeriod.isSupportedUnit(ChronoUnit.NANOS));
    assertTrue(SUTimePeriod.isSupportedUnit(ChronoUnit.WEEKS));
    assertTrue(SUTimePeriod.isSupportedUnit(ChronoUnit.MILLIS));

    try {
      SUTimePeriod.of(ChronoUnit.DECADES, 1);
      fail("expected an UnsupportedTemporalTypeException for decades");
    } catch (UnsupportedTemporalTypeException expected) {
      // a period has no decades field
    }
  }

  @Test
  public void testDecadeAndCenturyAreYears() {
    assertEquals("P10Y", SUTimePeriod.ofYears(10).toString());
    assertEquals("P100Y", SUTimePeriod.ofYears(100).toString());
  }

  // ---- arithmetic ----

  @Test
  public void testPlusAndMinusActPerField() {
    SUTimePeriod p = SUTimePeriod.ofYears(1).with(ChronoUnit.DAYS, 5);
    assertEquals("P3Y5D", p.plus(SUTimePeriod.ofYears(2)).toString());
    assertEquals("P1Y2D", p.minus(SUTimePeriod.ofDays(3)).toString());
    assertEquals("P2Y10D", p.plus(p).toString());
  }

  @Test
  public void testPlusDoesNotCarryBetweenFields() {
    assertEquals("P13M", SUTimePeriod.ofMonths(12).plus(SUTimePeriod.ofMonths(1)).toString());
    assertEquals("PT70M", SUTimePeriod.ofMinutes(70).toString());
  }

  @Test
  public void testMultipliedBy() {
    SUTimePeriod p = SUTimePeriod.ofYears(1).with(ChronoUnit.MONTHS, 2);
    assertEquals("P2Y4M", p.multipliedBy(2).toString());
    assertEquals("P-1Y-2M", p.multipliedBy(-1).toString());
    assertEquals("PT0S", p.multipliedBy(0).toString());
    assertSame(p, p.multipliedBy(1));
  }

  @Test
  public void testNegated() {
    assertEquals("P-1W", SUTimePeriod.ofWeeks(1).negated().toString());
  }

  @Test
  public void testDiscardMoreSpecificFields() {
    SUTimePeriod p = SUTimePeriod.ofYears(1).with(ChronoUnit.MONTHS, 2)
            .with(ChronoUnit.DAYS, 3).with(ChronoUnit.HOURS, 4).with(ChronoUnit.MILLIS, 5);
    assertEquals("P1Y2M3D", p.discardMoreSpecificFields(ChronoUnit.DAYS).toString());
    assertEquals("P1Y", p.discardMoreSpecificFields(ChronoUnit.YEARS).toString());
    assertEquals("P1Y2M3DT4H", p.discardMoreSpecificFields(ChronoUnit.HOURS).toString());
  }

  // ---- anchored conversion to an elapsed duration ----

  @Test
  public void testToDurationFromEpoch() {
    assertEquals(Duration.ofDays(31), SUTimePeriod.ofMonths(1).toDurationFrom(EPOCH));
    assertEquals(Duration.ofDays(7), SUTimePeriod.ofWeeks(1).toDurationFrom(EPOCH));
    assertEquals(Duration.ofDays(365), SUTimePeriod.ofYears(1).toDurationFrom(EPOCH));
    assertEquals(Duration.ofHours(1), SUTimePeriod.ofHours(1).toDurationFrom(EPOCH));
  }

  /**
   * A month is as long as the month it spans, so the same period measured forwards from a
   * date and backwards to it can differ.
   */
  @Test
  public void testToDurationFromAndToDifferOnUnevenFields() {
    assertEquals(Duration.ofDays(28), SUTimePeriod.ofMonths(1).toDurationFrom(JAN_31));
    assertEquals(Duration.ofDays(31), SUTimePeriod.ofMonths(1).toDurationTo(JAN_31));

    // a week is always a week
    assertEquals(Duration.ofDays(7), SUTimePeriod.ofWeeks(1).toDurationFrom(JAN_31));
    assertEquals(Duration.ofDays(7), SUTimePeriod.ofWeeks(1).toDurationTo(JAN_31));
  }

  /** The fields are added most general first, so a month lands before the days are added. */
  @Test
  public void testToDurationAppliesFieldsInOrder() {
    SUTimePeriod p = SUTimePeriod.ofYears(1).with(ChronoUnit.MONTHS, 1)
            .with(ChronoUnit.WEEKS, 1).with(ChronoUnit.DAYS, 1);
    assertEquals(Duration.ofMillis(34646400000L), p.toDurationFrom(JAN_31));
  }

  @Test
  public void testToDurationOfZeroIsZero() {
    assertEquals(Duration.ZERO, SUTimePeriod.ZERO.toDurationFrom(JAN_31));
  }

  // ---- ordering, which SUTime uses to compare durations roughly ----

  @Test
  public void testCompareTo() {
    assertTrue(SUTimePeriod.ofDays(1).compareTo(SUTimePeriod.ofHours(1)) > 0);
    assertTrue(SUTimePeriod.ofDays(1).compareTo(SUTimePeriod.ofWeeks(1)) < 0);
    assertEquals(0, SUTimePeriod.ofWeeks(1).compareTo(SUTimePeriod.ofDays(7)));
  }

  // ---- use as a java.time TemporalAmount ----

  @Test
  public void testAddToAndSubtractFromATemporal() {
    ZonedDateTime jan31 = JAN_31.atZone(ZoneOffset.UTC);
    assertEquals(Instant.parse("2017-02-28T00:00:00Z"),
            jan31.plus(SUTimePeriod.ofMonths(1)).toInstant());
    assertEquals(Instant.parse("2017-02-07T00:00:00Z"),
            jan31.plus(SUTimePeriod.ofWeeks(1)).toInstant());
    assertEquals(Instant.parse("2016-12-31T00:00:00Z"),
            jan31.minus(SUTimePeriod.ofMonths(1)).toInstant());
  }

  /**
   * A bare {@link Instant} carries no calendar, so it supports only the fields below a day.
   * A period with a calendar field has to be added to a {@link ZonedDateTime} or another
   * date-aware temporal; {@link SUTimePeriod#toDurationFrom} does that itself.
   */
  @Test
  public void testAddToRejectsACalendarFieldOnABareInstant() {
    assertEquals(Instant.parse("2017-01-31T01:00:00Z"), JAN_31.plus(SUTimePeriod.ofHours(1)));
    try {
      JAN_31.plus(SUTimePeriod.ofMonths(1));
      fail("expected an UnsupportedTemporalTypeException for months on an Instant");
    } catch (UnsupportedTemporalTypeException expected) {
      // an Instant has no months
    }
  }

  // ---- value semantics ----

  @Test
  public void testEqualsAndHashCode() {
    assertEquals(SUTimePeriod.ofWeeks(1), SUTimePeriod.of(ChronoUnit.WEEKS, 1));
    assertEquals(SUTimePeriod.ofWeeks(1).hashCode(), SUTimePeriod.of(ChronoUnit.WEEKS, 1).hashCode());
    assertFalse(SUTimePeriod.ofWeeks(1).equals(SUTimePeriod.ofDays(7)));
    assertEquals(SUTimePeriod.ZERO, SUTimePeriod.ofYears(0));
  }

  @Test
  public void testWithIsImmutable() {
    SUTimePeriod p = SUTimePeriod.ofYears(1);
    SUTimePeriod q = p.with(ChronoUnit.MONTHS, 6);
    assertEquals("P1Y", p.toString());
    assertEquals("P1Y6M", q.toString());
    assertSame(p, p.with(ChronoUnit.YEARS, 1));
  }

}
