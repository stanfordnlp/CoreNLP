package edu.stanford.nlp.time;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.time.temporal.Temporal;
import java.time.temporal.TemporalAmount;
import java.time.temporal.TemporalUnit;
import java.time.temporal.UnsupportedTemporalTypeException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * A quantity of time held as a value per calendar field, over the eight fields
 * {@code YEARS}, {@code MONTHS}, {@code WEEKS}, {@code DAYS}, {@code HOURS},
 * {@code MINUTES}, {@code SECONDS} and {@code MILLIS}.
 *
 * <p>SUTime needs a period that keeps its fields apart rather than reducing them to a
 * single elapsed amount, because the field a duration is expressed in is part of what the
 * duration means: "a week" and "seven days" produce different TIMEX3 values, and the most
 * specific non-zero field is what SUTime uses as a granularity. The period is also the
 * TIMEX3 duration value itself, so {@link #toString} is ISO-8601.
 *
 * <p>{@code java.time} splits this role across types that each drop part of it.
 * {@link java.time.Period} carries years, months and days, folds weeks into days and holds
 * no time fields; {@link java.time.Duration} carries an elapsed count of seconds and has no
 * calendar fields at all; threeten-extra's {@code PeriodDuration} pairs the two but still
 * folds weeks into days and offers no access to the fields in order. So this class keeps
 * the eight fields itself.
 *
 * <p>Instances are immutable. Field values may be negative, and are neither normalized nor
 * carried between fields: {@code P1Y} minus {@code P1M} is {@code P1Y-1M}, which is what
 * SUTime's offset notation means by it.
 *
 * <p>This is a {@link TemporalAmount}, so it can be added to or subtracted from any
 * {@link Temporal} that supports the fields it uses.
 *
 * @author John Bauer
 */
public final class SUTimePeriod implements TemporalAmount, Comparable<SUTimePeriod> {

  /** The eight fields of a period, ordered from most general to most specific. */
  private static final TemporalUnit[] UNITS = {
      ChronoUnit.YEARS, ChronoUnit.MONTHS, ChronoUnit.WEEKS, ChronoUnit.DAYS,
      ChronoUnit.HOURS, ChronoUnit.MINUTES, ChronoUnit.SECONDS, ChronoUnit.MILLIS,
  };

  private static final List<TemporalUnit> UNIT_LIST =
      Collections.unmodifiableList(Arrays.asList(UNITS));

  /** The ISO-8601 designator of each field, in the order of {@link #UNITS}. */
  private static final char[] DESIGNATORS = { 'Y', 'M', 'W', 'D', 'H', 'M', 'S', 'S' };

  private static final int YEARS = 0;
  private static final int MONTHS = 1;
  private static final int WEEKS = 2;
  private static final int DAYS = 3;
  private static final int HOURS = 4;
  private static final int MINUTES = 5;
  private static final int SECONDS = 6;
  private static final int MILLIS = 7;

  /** The index at which the time-of-day fields begin, and so where {@code T} is written. */
  private static final int FIRST_TIME_FIELD = HOURS;

  /** Every field zero. Its ISO-8601 form is {@code PT0S}. */
  public static final SUTimePeriod ZERO = new SUTimePeriod(new long[UNITS.length]);

  private final long[] values;

  private SUTimePeriod(long[] values) {
    this.values = values;
  }

  /** A period of {@code amount} of one field. */
  public static SUTimePeriod of(TemporalUnit unit, long amount) {
    return ZERO.with(unit, amount);
  }

  public static SUTimePeriod ofYears(long years) {
    return of(ChronoUnit.YEARS, years);
  }

  public static SUTimePeriod ofMonths(long months) {
    return of(ChronoUnit.MONTHS, months);
  }

  public static SUTimePeriod ofWeeks(long weeks) {
    return of(ChronoUnit.WEEKS, weeks);
  }

  public static SUTimePeriod ofDays(long days) {
    return of(ChronoUnit.DAYS, days);
  }

  public static SUTimePeriod ofHours(long hours) {
    return of(ChronoUnit.HOURS, hours);
  }

  public static SUTimePeriod ofMinutes(long minutes) {
    return of(ChronoUnit.MINUTES, minutes);
  }

  public static SUTimePeriod ofSeconds(long seconds) {
    return of(ChronoUnit.SECONDS, seconds);
  }

  public static SUTimePeriod ofMillis(long millis) {
    return of(ChronoUnit.MILLIS, millis);
  }

  /**
   * The index of {@code unit} among the eight fields, or -1 if a period has no such field.
   * {@code HALF_DAYS}, {@code DECADES}, {@code CENTURIES} and the rest are not fields of a
   * period; a duration of ten years is {@code P10Y}.
   */
  private static int indexOf(TemporalUnit unit) {
    for (int i = 0; i < UNITS.length; i++) {
      if (UNITS[i].equals(unit)) {
        return i;
      }
    }
    return -1;
  }

  /** Whether a period has a field for {@code unit}. */
  public static boolean isSupportedUnit(TemporalUnit unit) {
    return indexOf(unit) >= 0;
  }

  private static int requireIndexOf(TemporalUnit unit) {
    int index = indexOf(unit);
    if (index < 0) {
      throw new UnsupportedTemporalTypeException("Period does not support field '" + unit + '\'');
    }
    return index;
  }

  /** The number of fields a period has, which is always eight. */
  public int size() {
    return UNITS.length;
  }

  /** The field at {@code index}, counting from the most general. */
  public TemporalUnit getUnit(int index) {
    return UNITS[index];
  }

  /** The value of the field at {@code index}, counting from the most general. */
  public long getValue(int index) {
    return values[index];
  }

  @Override
  public long get(TemporalUnit unit) {
    return values[requireIndexOf(unit)];
  }

  @Override
  public List<TemporalUnit> getUnits() {
    return UNIT_LIST;
  }

  /** This period with the field for {@code unit} set to {@code amount}. */
  public SUTimePeriod with(TemporalUnit unit, long amount) {
    int index = requireIndexOf(unit);
    if (values[index] == amount) {
      return this;
    }
    long[] copy = values.clone();
    copy[index] = amount;
    return new SUTimePeriod(copy);
  }

  /** This period with {@code amount} added to the field for {@code unit}. */
  public SUTimePeriod plus(TemporalUnit unit, long amount) {
    int index = requireIndexOf(unit);
    return with(unit, values[index] + amount);
  }

  /** This period with every field of {@code other} added to the matching field. */
  public SUTimePeriod plus(SUTimePeriod other) {
    long[] sum = new long[UNITS.length];
    for (int i = 0; i < UNITS.length; i++) {
      sum[i] = values[i] + other.values[i];
    }
    return new SUTimePeriod(sum);
  }

  /** This period with every field of {@code other} subtracted from the matching field. */
  public SUTimePeriod minus(SUTimePeriod other) {
    long[] difference = new long[UNITS.length];
    for (int i = 0; i < UNITS.length; i++) {
      difference[i] = values[i] - other.values[i];
    }
    return new SUTimePeriod(difference);
  }

  /** This period with every field multiplied by {@code scalar}. */
  public SUTimePeriod multipliedBy(long scalar) {
    if (scalar == 1) {
      return this;
    }
    long[] scaled = new long[UNITS.length];
    for (int i = 0; i < UNITS.length; i++) {
      scaled[i] = values[i] * scalar;
    }
    return new SUTimePeriod(scaled);
  }

  /** This period with the sign of every field flipped. */
  public SUTimePeriod negated() {
    return multipliedBy(-1);
  }

  /** Whether every field is zero. */
  public boolean isZero() {
    for (long value : values) {
      if (value != 0) {
        return false;
      }
    }
    return true;
  }

  /** The most general non-zero field, or null if every field is zero. */
  public TemporalUnit getMostGeneralUnit() {
    for (int i = 0; i < UNITS.length; i++) {
      if (values[i] != 0) {
        return UNITS[i];
      }
    }
    return null;
  }

  /** The most specific non-zero field, or null if every field is zero. */
  public TemporalUnit getMostSpecificUnit() {
    for (int i = UNITS.length - 1; i >= 0; i--) {
      if (values[i] != 0) {
        return UNITS[i];
      }
    }
    return null;
  }

  /**
   * This period with every field more specific than {@code unit} dropped. A field is kept
   * when it is at least as long as {@code unit}, so a period discarded at {@code DAYS}
   * keeps years, months, weeks and days.
   */
  public SUTimePeriod discardMoreSpecificFields(TemporalUnit unit) {
    long[] kept = new long[UNITS.length];
    Duration bound = unit.getDuration();
    for (int i = 0; i < UNITS.length; i++) {
      if (UNITS[i].getDuration().compareTo(bound) >= 0) {
        kept[i] = values[i];
      }
    }
    return new SUTimePeriod(kept);
  }

  @Override
  public Temporal addTo(Temporal temporal) {
    return adjust(temporal, 1);
  }

  @Override
  public Temporal subtractFrom(Temporal temporal) {
    return adjust(temporal, -1);
  }

  /**
   * Adds every non-zero field to {@code temporal}, most general field first, each field
   * scaled by {@code sign}. Adding the fields in order is what makes the result depend on
   * the field a period is written in: a month added to January 31st lands on February 28th,
   * so adding a month and then a day differs from adding 31 days.
   */
  private Temporal adjust(Temporal temporal, int sign) {
    Temporal result = temporal;
    for (int i = 0; i < UNITS.length; i++) {
      if (values[i] != 0) {
        result = result.plus(sign * values[i], UNITS[i]);
      }
    }
    return result;
  }

  /**
   * The elapsed time from {@code instant} to the instant this period after it, measured in
   * the ISO calendar at UTC.
   */
  public Duration toDurationFrom(Instant instant) {
    return toDurationFrom(instant, ZoneOffset.UTC);
  }

  /** The elapsed time from {@code instant} to the instant this period after it, in {@code zone}. */
  public Duration toDurationFrom(Instant instant, ZoneId zone) {
    ZonedDateTime start = instant.atZone(zone);
    return Duration.between(start, (ZonedDateTime) addTo(start));
  }

  /**
   * The elapsed time from the instant this period before {@code instant} to {@code instant},
   * measured in the ISO calendar at UTC. This differs from {@link #toDurationFrom} wherever
   * a field is not a fixed length: a month before March 31st is 31 days, a month after it is
   * 30.
   */
  public Duration toDurationTo(Instant instant) {
    return toDurationTo(instant, ZoneOffset.UTC);
  }

  /** The elapsed time from the instant this period before {@code instant} to {@code instant}, in {@code zone}. */
  public Duration toDurationTo(Instant instant, ZoneId zone) {
    ZonedDateTime end = instant.atZone(zone);
    return Duration.between((ZonedDateTime) subtractFrom(end), end);
  }

  /**
   * The total elapsed time of this period measured from the epoch, which is how SUTime
   * orders durations against each other.
   */
  public Duration toDuration() {
    return toDurationFrom(Instant.EPOCH);
  }

  @Override
  public int compareTo(SUTimePeriod other) {
    return toDuration().compareTo(other.toDuration());
  }

  /**
   * The ISO-8601 form of this period, such as {@code P1Y2M}, {@code P1W} or
   * {@code PT5H6M7.008S}. Fields that are zero are left out, and a period with no non-zero
   * field is {@code PT0S}. The seconds and milliseconds fields are written together as one
   * decimal number of seconds, so a period of 1 second and -500 milliseconds is
   * {@code PT0.500S}.
   */
  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder("P");
    for (int i = 0; i < FIRST_TIME_FIELD; i++) {
      if (values[i] != 0) {
        sb.append(values[i]).append(DESIGNATORS[i]);
      }
    }
    long totalMillis = values[SECONDS] * 1000 + values[MILLIS];
    boolean anyTimeField = values[HOURS] != 0 || values[MINUTES] != 0 || totalMillis != 0;
    if (anyTimeField) {
      sb.append('T');
      if (values[HOURS] != 0) {
        sb.append(values[HOURS]).append('H');
      }
      if (values[MINUTES] != 0) {
        sb.append(values[MINUTES]).append('M');
      }
      if (totalMillis != 0) {
        appendSeconds(sb, totalMillis);
      }
    } else if (sb.length() == 1) {
      sb.append("T0S");
    }
    return sb.toString();
  }

  /**
   * Writes {@code totalMillis} as a decimal number of seconds, keeping the sign on the
   * whole number even where the whole part is zero, as in {@code -0.008}.
   */
  private static void appendSeconds(StringBuilder sb, long totalMillis) {
    long whole = totalMillis / 1000;
    long fraction = Math.abs(totalMillis % 1000);
    if (whole == 0 && totalMillis < 0) {
      sb.append('-');
    }
    sb.append(whole);
    if (fraction != 0) {
      sb.append('.');
      if (fraction < 100) {
        sb.append('0');
      }
      if (fraction < 10) {
        sb.append('0');
      }
      sb.append(fraction);
    }
    sb.append('S');
  }

  @Override
  public boolean equals(Object other) {
    if (this == other) {
      return true;
    }
    if (!(other instanceof SUTimePeriod)) {
      return false;
    }
    return Arrays.equals(values, ((SUTimePeriod) other).values);
  }

  @Override
  public int hashCode() {
    return Arrays.hashCode(values);
  }

}
