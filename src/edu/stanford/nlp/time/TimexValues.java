package edu.stanford.nlp.time;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoField;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalField;
import java.time.temporal.WeekFields;

/**
 * TIMEX3 values for a time, a range of times, or a duration.
 *
 * <p>These read a span of time and name it the way TIMEX3 would: a range covering exactly
 * one calendar unit is written as that unit, so the whole of March 2011 is
 * {@code 2011-03} and the whole of a Saturday and Sunday is {@code 2011-W21-WE}, while a
 * span that lines up with nothing is written as a duration instead.
 *
 * <p>Nothing in SUTime itself calls these; SUTime builds its own values from the fields a
 * time pins down rather than from a range. They are here because they are published, and
 * are kept working on their own tests.
 *
 * @author John Bauer
 */
public class TimexValues {

  private TimexValues() { } // static methods only

  private static final TemporalField WEEK_OF_YEAR = WeekFields.ISO.weekOfWeekBasedYear();

  /** The fields of a full date and time, ordered from coarsest to finest. */
  private static final TemporalField[] STANDARD_FIELDS = {
      ChronoField.YEAR,
      ChronoField.MONTH_OF_YEAR,
      ChronoField.DAY_OF_MONTH,
      ChronoField.HOUR_OF_DAY,
      ChronoField.MINUTE_OF_HOUR,
      ChronoField.SECOND_OF_MINUTE,
      ChronoField.MILLI_OF_SECOND,
  };

  /** The same, dated by week and weekday. */
  private static final TemporalField[] WEEK_FIELDS = {
      ChronoField.YEAR,
      WEEK_OF_YEAR,
      ChronoField.DAY_OF_WEEK,
      ChronoField.HOUR_OF_DAY,
      ChronoField.MINUTE_OF_HOUR,
      ChronoField.SECOND_OF_MINUTE,
      ChronoField.MILLI_OF_SECOND,
  };

  /** How a range is turned into a value, where there is more than one reasonable answer. */
  public static class ConversionOptions {
    /**
     * If true, give a "best guess" of the right date; if false, backoff to giving a duration
     * for malformed dates.
     */
    public boolean forceDate = false;
    /**
     * Force particular units -- e.g., force 20Y to be 20Y (20 years) rather than 2E (2 decades)
     */
    public String[] forceUnits = new String[0];
    /**
     * Treat durations as approximate
     */
    public boolean approximate = false;
  }

  /**
   * The smallest value a field can take at {@code reference}, which for a day of the month
   * is 1 and for an hour is 0.
   */
  public static int minimumValue(TemporalField type, ZonedDateTime reference) {
    return (int) reference.range(type).getMinimum();
  }

  /**
   * The largest value a field can take at {@code reference}, which depends on where the
   * reference falls: a day of the month runs to 28 in February and to 31 in March.
   */
  public static int maximumValue(TemporalField type, ZonedDateTime reference) {
    return (int) reference.range(type).getMaximum();
  }

  /** The TIMEX string for the time given, to the minute. */
  public static String timexTimeValue(ZonedDateTime time) {
    return String.valueOf(time.getYear()) + '-' + zeroPad(time.getMonthValue(), 2) + '-'
            + zeroPad(time.getDayOfMonth(), 2) + 'T' + zeroPad(time.getHour(), 2) + ':'
            + zeroPad(time.getMinute(), 2);
  }

  public static String timexDateValue(ZonedDateTime begin, ZonedDateTime end) {
    return timexDateValue(begin, end, new ConversionOptions());
  }

  /**
   * Return the TIMEX string for the range of dates given.
   * For example, (2011-12-3:00:00,000 to 2011-12-4:00:00.000) would give 2011-12-3.
   * @param begin The begin time for the timex
   * @param end The end time for the timex
   * @param opts Tweaks in the heuristic conversion
   * @return The string representation of a DATE type Timex3 expression
   */
  public static String timexDateValue(ZonedDateTime begin, ZonedDateTime end, ConversionOptions opts) {
    //--Special Cases
    if (begin.getYear() < -100000) {
      return "PAST_REF";
    } else if (end.getYear() > 100000) {
      return "FUTURE_REF";
    } else if (begin.equals(end)) {
      return timexTimeValue(begin);
    }
    StringBuilder value = new StringBuilder();
    //--Differences
    int monthDiff = (end.getMonthValue() - begin.getMonthValue()) + (end.getYear() - begin.getYear()) * 12;
    int weekDiff = end.get(WEEK_OF_YEAR) - begin.get(WEEK_OF_YEAR)
            + (end.getYear() - begin.getYear()) * maximumValue(WEEK_OF_YEAR, begin);
    int dayDiff = end.getDayOfMonth() - begin.getDayOfMonth()
            + monthDiff * maximumValue(ChronoField.DAY_OF_MONTH, begin);
    int hrDiff = end.getHour() - begin.getHour() + dayDiff * 24;
    int minDiff = end.getMinute() - begin.getMinute() + hrDiff * 60;
    //--Years
    if (noFurtherFields(ChronoField.YEAR, begin, end)) {
      int diff = end.getYear() - begin.getYear();
      if (diff == 100 && (opts.forceDate || begin.getYear() % 100 == 0)) {
        //(case: century)
        value.append((begin.getYear() / 100)).append("XX");
      } else if (diff == 10 && (opts.forceDate || begin.getYear() % 10 == 0)) {
        //(case: decade)
        value.append((begin.getYear() / 10));
      } else if (diff == 1 || opts.forceDate) {
        //(case: year)
        value.append(begin.getYear());
      } else {
        //(case: duration)
        return timexDurationValue(begin, end);
      }
      return value.toString();
    } else if (monthDiff < 12 || opts.forceDate) {
      //(case: year and more)
      value.append(begin.getYear());
    } else {
      //(case: treat as duration)
      return timexDurationValue(begin, end);
    }
    //--Week/Month/Quarters
    value.append("-");
    if (noFurtherFields(ChronoField.MONTH_OF_YEAR, begin, end)
            || noFurtherFields(WEEK_OF_YEAR, begin, end)) {
      boolean monthTerminal = noFurtherFields(ChronoField.MONTH_OF_YEAR, begin, end);
      boolean weekTerminal = noFurtherFields(WEEK_OF_YEAR, begin, end);
      //(Month/Quarter)
      if (monthTerminal && monthDiff == 6 && (begin.getMonthValue() - 1) % 6 == 0) {
        //(case: half of year)
        value.append("H").append((begin.getMonthValue() - 1) / 6 + 1);
      } else if (monthTerminal && monthDiff == 3 && (begin.getMonthValue() - 1) % 3 == 0) {
        //(case: quarter of year)
        value.append("Q").append((begin.getMonthValue() - 1) / 3 + 1);
      } else if (monthTerminal && monthDiff == 3 && begin.getMonthValue() % 3 == 0) {
        //(case: season)
        switch (begin.getMonthValue()) {
          case 12:
            value.append("WI");
            break;
          case 3:
            value.append("SP");
            break;
          case 6:
            value.append("SU");
            break;
          case 9:
            value.append("FA");
            break;
          default:
            throw new IllegalStateException("Season start month is unknown");
        }
      } else if (weekTerminal && weekDiff == 1) {
        //(case: a week)
        value.append("W").append(zeroPad(begin.get(WEEK_OF_YEAR), 2));
      } else if (monthTerminal && monthDiff == 1 && weekDiff != 1 || opts.forceDate) {
        //(case: a month)
        value.append(zeroPad(begin.getMonthValue(), 2));
      } else {
        //(case: treat as duration)
        return timexDurationValue(begin, end);
      }
      return value.toString();
    } else if (noFurtherFields(ChronoField.DAY_OF_WEEK, begin, end) && dayDiff == 2
            && begin.get(ChronoField.DAY_OF_WEEK) == 6) {
      //(case: a weekend)
      value.append("W").append(zeroPad(begin.get(WEEK_OF_YEAR), 2)).append("-WE");
      return value.toString();
    } else if (dayDiff < maximumValue(ChronoField.DAY_OF_MONTH, begin) || opts.forceDate) {
      //(case: month and more)
      value.append(zeroPad(begin.getMonthValue(), 2));
    } else {
      //(case: treat as duration)
      return timexDurationValue(begin, end);
    }
    //--Weekday/Day
    value.append("-");
    if (noFurtherFields(ChronoField.DAY_OF_MONTH, begin, end)) {
      if (dayDiff == 1 || opts.forceDate) {
        //(case: a day)
        value.append(zeroPad(begin.getDayOfMonth(), 2));
      } else {
        //(case: treat as duration)
        return timexDurationValue(begin, end);
      }
      return value.toString();
    } else if (hrDiff < 24 || opts.forceDate) {
      //(case: day and more)
      value.append(zeroPad(begin.getDayOfMonth(), 2));
    } else {
      //(case: treat as duration)
      return timexDurationValue(begin, end);
    }
    //--Hour/TimeOfDay
    value.append("T");
    if (noFurtherFields(ChronoField.HOUR_OF_DAY, begin, end)) {
      //((case: half day)
      if (hrDiff == 12 && begin.getHour() == 0) {
        value.append("H1");
      } else if (hrDiff == 12 && begin.getHour() == 12) {
        value.append("H2");
      //(case: time of day)
      } else if (hrDiff == 4 && begin.getHour() == 8) {
        value.append("MO");
      } else if (hrDiff == 4 && begin.getHour() == 12) {
        value.append("AF");
      } else if (hrDiff == 4 && begin.getHour() == 16) {
        value.append("EV");
      } else if (hrDiff == 4 && begin.getHour() == 20) {
        value.append("NI");
      } else if (hrDiff == 1 || opts.forceDate) {
        //(case: an hour)
        value.append(zeroPad(begin.getHour(), 2));
      } else {
        //(case: treat as duration)
        return timexDurationValue(begin, end);
      }
      return value.toString();
    } else if (minDiff <= 60 || opts.forceDate) {
      //(case: hour and more)
      value.append(zeroPad(begin.getHour(), 2));
    } else {
      //(case: treat as duration)
      return timexDurationValue(begin, end);
    }
    //--Minute/Second
    value.append(":");
    value.append(zeroPad(begin.getMinute(), 2));
    return value.toString();
  }

  public static String timexDurationValue(SUTimePeriod duration) {
    return timexDurationValue(duration, new ConversionOptions());
  }

  /**
   * Return the TIMEX string for the duration represented by the given period; approximately if
   * approximate is set to true.
   * @param duration The period representing this duration
   * @param opts Options for the conversion (e.g., mark duration as approximates)
   * @return The string representation of a DURATION type Timex3 expression
   */
  public static String timexDurationValue(SUTimePeriod duration, ConversionOptions opts) {
    StringBuilder b = new StringBuilder().append("P");
    boolean seenTime = false;
    long years = duration.get(ChronoUnit.YEARS);
    //(millenia)
    if (years >= 1000 && consistentWithForced("L", opts.forceUnits)) {
      b.append(opts.approximate ? "X" : years / 1000).append("L");
      years = years % 1000;
    }
    //(centuries)
    if (years >= 100 && consistentWithForced("C", opts.forceUnits)) {
      b.append(opts.approximate ? "X" : years / 100).append("C");
      years = years % 100;
    }
    //(decades)
    if (years >= 10 && consistentWithForced("E", opts.forceUnits)) {
      b.append(opts.approximate ? "X" : years / 10).append("E");
      years = years % 10;
    }
    //(years)
    if (years != 0 && consistentWithForced("Y", opts.forceUnits)) {
      b.append(opts.approximate ? "X" : years).append("Y");
    }
    //(months)
    long months = duration.get(ChronoUnit.MONTHS);
    if (months != 0) {
      if (months % 3 == 0 && consistentWithForced("Q", opts.forceUnits)) {
        b.append(opts.approximate ? "X" : months / 3).append("Q");
        months = months % 3;
      } else {
        b.append(opts.approximate ? "X" : months).append("M");
      }
    }
    //(weeks)
    if (duration.get(ChronoUnit.WEEKS) != 0) {
      b.append(opts.approximate ? "X" : duration.get(ChronoUnit.WEEKS)).append("W");
    }
    //(days)
    if (duration.get(ChronoUnit.DAYS) != 0) {
      b.append(opts.approximate ? "X" : duration.get(ChronoUnit.DAYS)).append("D");
    }
    //(hours)
    if (duration.get(ChronoUnit.HOURS) != 0) {
      if (!seenTime) { b.append("T"); seenTime = true; }
      b.append(opts.approximate ? "X" : duration.get(ChronoUnit.HOURS)).append("H");
    }
    //(minutes)
    if (duration.get(ChronoUnit.MINUTES) != 0) {
      if (!seenTime) { b.append("T"); seenTime = true; }
      b.append(opts.approximate ? "X" : duration.get(ChronoUnit.MINUTES)).append("M");
    }
    //(seconds and milliseconds)
    // ISO 8601 has no field for sub-second amounts: they are written as a fraction of
    // the seconds field, so the two are emitted together.
    long secs = duration.get(ChronoUnit.SECONDS);
    long millis = duration.get(ChronoUnit.MILLIS);
    if (secs != 0 || millis != 0) {
      if (!seenTime) { b.append("T"); seenTime = true; }
      if (opts.approximate) {
        b.append("X").append("S");
      } else if (millis != 0) {
        b.append(secs).append('.').append(String.format("%03d", Math.abs(millis))).append("S");
      } else {
        b.append(secs).append("S");
      }
    }
    if (b.length() == 1) {
      // Nothing was emitted, so every field was zero. A bare "P" is not a duration;
      // ISO 8601 requires at least one component, and PT0S is the canonical zero.
      return "PT0S";
    }
    return b.toString();
  }

  /**
   * Return the TIMEX string for the difference between two dates
   * TODO not really sure if this works...
   */
  public static String timexDurationValue(ZonedDateTime begin, ZonedDateTime end) {
    return timexDurationValue(SUTimePeriod.from(Duration.between(begin, end)));
  }

  private static boolean consistentWithForced(String cand, String[] forcedList) {
    //--Check If Forced
    for (String forced : forcedList) {
      if (forced.equals(cand)) { return true; }
    }
    //--Get Ordering
    String[] ordering = {"L", "C", "E", "Y", "Q", "M", "W", "D", "H", "m", "S"};
    int candIndex = -1;
    for (int i = 0; i < ordering.length; i++) {
      if (ordering[i].equals(cand)) {
        candIndex = i;
        break;
      }
    }
    assert candIndex >= 0;
    //--Check If Lower Priority Forced
    for (int candI = candIndex + 1; candI < ordering.length; candI++) {
      for (String forced : forcedList) {
        if (ordering[candI].equals(forced)) {
          return false;
        }
      }
    }
    //--OK
    return true;
  }

  private static String zeroPad(int value, int padding) {
    StringBuilder b = new StringBuilder();
    b.append(value);
    while (b.length() < padding) {
      b.insert(0, "0");
    }
    return b.toString();
  }

  /**
   * Whether both ends of the range sit at the start of everything finer than
   * {@code smallestFieldSet}, which is what makes a range exactly one of that unit wide.
   */
  private static boolean noFurtherFields(TemporalField smallestFieldSet, ZonedDateTime begin,
                                         ZonedDateTime end) {
    //--Get Indices
    //(standard fields)
    int indexInStandard = -1;
    for (int i = 0; i < STANDARD_FIELDS.length; i++) {
      if (STANDARD_FIELDS[i].equals(smallestFieldSet)) {
        indexInStandard = i + 1;
      }
    }
    //(week-based fields)
    int indexInWeek = -1;
    for (int i = 0; i < WEEK_FIELDS.length; i++) {
      if (WEEK_FIELDS[i].equals(smallestFieldSet)) {
        indexInWeek = i + 1;
      }
    }
    //(special fields)
    if (smallestFieldSet.equals(java.time.temporal.IsoFields.QUARTER_OF_YEAR)) {
      for (int i = 0; i < STANDARD_FIELDS.length; i++) {
        if (STANDARD_FIELDS[i].equals(ChronoField.MONTH_OF_YEAR)) {
          indexInStandard = i;
        }
      }
    }
    //(get data)
    int index;
    TemporalField[] toCheck;
    if (indexInStandard >= 0) {
      index = indexInStandard;
      toCheck = STANDARD_FIELDS;
    } else if (indexInWeek >= 0) {
      index = indexInWeek;
      toCheck = WEEK_FIELDS;
    } else {
      throw new IllegalArgumentException("Field is not in my list of fields: " + smallestFieldSet);
    }
    //--Perform Check
    for (int i = index; i < toCheck.length; i++) {
      int minValue = minimumValue(toCheck[i], begin);
      if (begin.get(toCheck[i]) != minValue || end.get(toCheck[i]) != minValue) {
        return false;
      }
    }
    return true;
  }

}
