package edu.stanford.nlp.time;

import org.threeten.extra.PartialTemporal;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoField;
import java.time.temporal.IsoFields;
import java.time.temporal.TemporalField;
import java.time.temporal.WeekFields;
import java.util.Map;

/**
 * Turning partial dates into instants and back, and pinning down a day of the week.
 *
 * <p>The java.time counterpart of the resolution half of {@code JodaTimeUtils}. Everything
 * here goes through UTC unless a zone is given, which is what SUTime has always done.
 */
public final class PartialTemporalResolution {

  private PartialTemporalResolution() { }

  public static final ZoneId UTC = ZoneOffset.UTC;

  /**
   * A full date and time, as the set of fields to read off an instant. The values are
   * placeholders; {@link #getPartial} overwrites every one of them, and only which fields
   * are present matters.
   */
  public static final PartialTemporal EMPTY_ISO_PARTIAL = PartialTemporal.empty()
          .withField(ChronoField.YEAR, 0)
          .withField(ChronoField.MONTH_OF_YEAR, 1)
          .withField(ChronoField.DAY_OF_MONTH, 1)
          .withField(ChronoField.HOUR_OF_DAY, 0)
          .withField(ChronoField.MINUTE_OF_HOUR, 0)
          .withField(ChronoField.SECOND_OF_MINUTE, 0)
          .withField(ChronoField.MILLI_OF_SECOND, 0);

  /** The same fields as {@link #EMPTY_ISO_PARTIAL}, dated by week and weekday. */
  public static final PartialTemporal EMPTY_ISO_WEEK_PARTIAL = PartialTemporal.empty()
          .withField(ChronoField.YEAR, 0)
          .withField(WeekFields.ISO.weekOfWeekBasedYear(), 1)
          .withField(ChronoField.DAY_OF_WEEK, 1)
          .withField(ChronoField.HOUR_OF_DAY, 0)
          .withField(ChronoField.MINUTE_OF_HOUR, 0)
          .withField(ChronoField.SECOND_OF_MINUTE, 0)
          .withField(ChronoField.MILLI_OF_SECOND, 0);

  /** Just the date fields of {@link #EMPTY_ISO_PARTIAL}. */
  public static final PartialTemporal EMPTY_ISO_DATE_PARTIAL = PartialTemporal.empty()
          .withField(ChronoField.YEAR, 0)
          .withField(ChronoField.MONTH_OF_YEAR, 1)
          .withField(ChronoField.DAY_OF_MONTH, 1);

  /** Just the time fields of {@link #EMPTY_ISO_PARTIAL}. */
  public static final PartialTemporal EMPTY_ISO_TIME_PARTIAL = PartialTemporal.empty()
          .withField(ChronoField.HOUR_OF_DAY, 0)
          .withField(ChronoField.MINUTE_OF_HOUR, 0)
          .withField(ChronoField.SECOND_OF_MINUTE, 0)
          .withField(ChronoField.MILLI_OF_SECOND, 0);

  /**
   * The instant a partial names, filling in anything it does not say: a missing month and
   * day become January the first, a missing time becomes midnight. Coarser year fields are
   * read as the year they open, and a quarter supplies the month it starts in.
   */
  public static Instant getInstant(PartialTemporal partial) {
    return getInstant(partial, UTC);
  }

  public static Instant getInstant(PartialTemporal partial, ZoneId zone) {
    if (partial == null) {
      return null;
    }
    int year = 0;
    if (partial.isSupported(ChronoField.YEAR)) {
      year = (int) partial.getLong(ChronoField.YEAR);
    } else {
      if (partial.isSupported(SUTimeFields.CENTURY_OF_ERA)) {
        year += 100 * partial.getLong(SUTimeFields.CENTURY_OF_ERA);
      }
      if (partial.isSupported(SUTimeFields.YEAR_OF_CENTURY)) {
        year += partial.getLong(SUTimeFields.YEAR_OF_CENTURY);
      } else if (partial.isSupported(SUTimeFields.DECADE_OF_CENTURY)) {
        year += 10 * partial.getLong(SUTimeFields.DECADE_OF_CENTURY);
      }
    }
    int month = 1;
    if (partial.isSupported(ChronoField.MONTH_OF_YEAR)) {
      month = (int) partial.getLong(ChronoField.MONTH_OF_YEAR);
    } else if (partial.isSupported(IsoFields.QUARTER_OF_YEAR)) {
      month += 3 * (partial.getLong(IsoFields.QUARTER_OF_YEAR) - 1);
    }
    int day = value(partial, ChronoField.DAY_OF_MONTH, 1);
    int hour = value(partial, ChronoField.HOUR_OF_DAY, 0);
    int minute = value(partial, ChronoField.MINUTE_OF_HOUR, 0);
    int second = value(partial, ChronoField.SECOND_OF_MINUTE, 0);
    int milli = value(partial, ChronoField.MILLI_OF_SECOND, 0);

    LocalDateTime local = LocalDateTime.of(year, month, day, hour, minute, second,
            milli * 1_000_000);
    return ZonedDateTime.of(local, zone).toInstant();
  }

  private static int value(PartialTemporal partial, TemporalField field, int fallback) {
    return partial.isSupported(field) ? (int) partial.getLong(field) : fallback;
  }

  /**
   * Read the fields of {@code template} off {@code instant}, so the result carries the
   * same fields as the template with values taken from the instant.
   */
  public static PartialTemporal getPartial(Instant instant, PartialTemporal template) {
    ZonedDateTime at = instant.atZone(UTC);
    PartialTemporal result = template;
    for (TemporalField field : template.getFields().keySet()) {
      result = result.withField(field, at.getLong(field));
    }
    return result;
  }

  /** The same partial with its year read as a week-based year. */
  public static PartialTemporal withWeekYear(PartialTemporal partial) {
    if (!partial.isSupported(ChronoField.YEAR)) {
      return partial;
    }
    long year = partial.getLong(ChronoField.YEAR);
    return partial.withoutField(ChronoField.YEAR)
            .withField(WeekFields.ISO.weekBasedYear(), year);
  }

  /**
   * Turn a day of the week into a day of the month, when the partial says which week of
   * which year it falls in. Without that it is left alone, since there is nothing to
   * anchor it to.
   */
  public static PartialTemporal resolveDowToDay(PartialTemporal partial) {
    if (!partial.isSupported(ChronoField.DAY_OF_WEEK)
            || partial.isSupported(ChronoField.DAY_OF_MONTH)
            || !partial.isSupported(WeekFields.ISO.weekOfWeekBasedYear())
            || !partial.isSupported(ChronoField.YEAR)) {
      return partial;
    }
    long weekYear = partial.getLong(ChronoField.YEAR);
    long week = partial.getLong(WeekFields.ISO.weekOfWeekBasedYear());
    long dayOfWeek = partial.getLong(ChronoField.DAY_OF_WEEK);

    // Most years have 52 ISO weeks and some have 53. Asking for a week the year does not
    // have would otherwise roll quietly into the next year and invent a date, so reject it.
    java.time.LocalDate firstOfYear = java.time.LocalDate.of((int) weekYear, 1, 4)
            .with(ChronoField.DAY_OF_WEEK, 1);
    long weeksInYear = WeekFields.ISO.weekOfWeekBasedYear().rangeRefinedBy(firstOfYear).getMaximum();
    if (week < 1 || week > weeksInYear) {
      throw new java.time.DateTimeException("Value " + week + " for weekOfWeekBasedYear must not be"
              + " larger than " + weeksInYear + " in week-based year " + weekYear);
    }

    java.time.LocalDate date = java.time.LocalDate.now(UTC)
            .with(WeekFields.ISO.weekBasedYear(), weekYear)
            .with(WeekFields.ISO.weekOfWeekBasedYear(), week)
            .with(ChronoField.DAY_OF_WEEK, dayOfWeek);

    PartialTemporal full = EMPTY_ISO_PARTIAL
            .withField(ChronoField.YEAR, date.getYear())
            .withField(ChronoField.MONTH_OF_YEAR, date.getMonthValue())
            .withField(ChronoField.DAY_OF_MONTH, date.getDayOfMonth());
    TemporalField finest = PartialTemporalUtils.getMostSpecific(partial);
    return PartialTemporalUtils.discardMoreSpecificFields(full, finest.getBaseUnit());
  }

  /** Add the week of the year to a partial that names a full date. */
  public static PartialTemporal resolveWeek(PartialTemporal partial) {
    if (!PartialTemporalUtils.hasYYYYMMDD(partial)) {
      return partial;
    }
    Instant instant = getInstant(partial);
    return getPartial(instant, partial.withField(WeekFields.ISO.weekOfWeekBasedYear(), 1));
  }

  // ------------------------------------------------- resolving against a reference date

  /**
   * Set every field of {@code partial} on {@code base}, coarsest field first, and return
   * where that lands. This is how a partial is read against a reference date: the fields
   * the partial names override the reference, and the fields it leaves out keep the
   * reference's values.
   *
   * <p>The order matters, since a field is set relative to what the coarser fields already
   * say. A {@code PartialTemporal} keeps its fields sorted coarsest first, so iterating
   * them gives that order.
   */
  public static ZonedDateTime overlay(PartialTemporal partial, Instant base) {
    return overlay(partial, base, UTC);
  }

  public static ZonedDateTime overlay(PartialTemporal partial, Instant base, ZoneId zone) {
    ZonedDateTime result = base.atZone(zone);
    for (Map.Entry<TemporalField, Long> entry : partial.getFields().entrySet()) {
      result = result.with(entry.getKey(), entry.getValue());
    }
    return result;
  }

  /**
   * Turn the day of the week in {@code p1} into a day of the month, taking the week it
   * falls in from {@code p2}. The two have to agree on the fields they share, {@code p1}
   * has to name a weekday and not already name a day of the month, and {@code p2} has to
   * name a full date. Otherwise {@code p1} comes back untouched.
   */
  public static PartialTemporal resolveDowToDay(PartialTemporal p1, PartialTemporal p2) {
    if (p1 == null || p2 == null) {
      return p1;
    }
    // Anything in p2 finer than a day cannot help place a weekday, and would otherwise
    // make the two look incompatible.
    p2 = PartialTemporalUtils.discardMoreSpecificFields(p2, ChronoField.DAY_OF_MONTH);
    if (!PartialTemporalUtils.isCompatible(p1, p2)
            || !p1.isSupported(ChronoField.DAY_OF_WEEK)
            || p1.isSupported(ChronoField.DAY_OF_MONTH)
            || !PartialTemporalUtils.hasYYYYMMDD(p2)) {
      return p1;
    }
    ZonedDateTime resolved = overlay(p1, getInstant(p2));
    return getPartial(resolved.toInstant(), p1.withField(ChronoField.DAY_OF_MONTH, 1));
  }

  /**
   * Work out which week of the year {@code p1} falls in, taking the date from {@code p2}.
   * The result is dated by week rather than by month and day, so the month and day of the
   * month are dropped. As with {@link #resolveDowToDay}, {@code p1} comes back untouched
   * unless the two agree and {@code p2} names a full date.
   */
  public static PartialTemporal resolveWeek(PartialTemporal p1, PartialTemporal p2) {
    if (p1 == null || p2 == null) {
      return p1;
    }
    if (!PartialTemporalUtils.isCompatible(p1, p2)
            || p1.isSupported(ChronoField.DAY_OF_MONTH)
            || !PartialTemporalUtils.hasYYYYMMDD(p2)) {
      return p1;
    }
    ZonedDateTime resolved = overlay(p1, getInstant(p2));
    PartialTemporal template = p1
            .withoutField(ChronoField.DAY_OF_MONTH)
            .withoutField(ChronoField.MONTH_OF_YEAR)
            .withField(WeekFields.ISO.weekOfWeekBasedYear(), 1);
    return getPartial(resolved.toInstant(), template);
  }

}
