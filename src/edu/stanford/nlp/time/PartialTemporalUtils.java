package edu.stanford.nlp.time;

import org.threeten.extra.PartialTemporal;
import org.threeten.extra.TemporalFields;

import java.time.temporal.ChronoField;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalField;
import java.time.temporal.IsoFields;
import java.time.temporal.TemporalUnit;
import java.time.temporal.WeekFields;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * The partial-date algebra SUTime is built on, over {@link PartialTemporal}.
 *
 * <p>This is the java.time counterpart of the field-manipulation half of
 * {@code JodaTimeUtils}: deciding which of two fields is coarser, merging two partial dates,
 * and trimming one to a given precision. The resolution and TIMEX-formatting halves live
 * elsewhere.
 *
 * <p>Ordering fields by coarseness comes free here. A {@code PartialTemporal} keeps its
 * fields in a {@link java.util.NavigableMap} sorted coarsest first, so the coarsest and
 * finest field are the first and last keys, and trimming to a precision is a
 * {@code headMap}. Joda had no such guarantee and the original walks the fields by index.
 *
 * <p>Two-digit years need care and are handled the way SUTime has always handled them: a
 * partial carrying a year-of-century is resolved against a reference year, moving back a
 * century when the naive reading would land in the future. A century and a year-of-century
 * sitting together collapse into a plain year.
 */
public final class PartialTemporalUtils {

  private PartialTemporalUtils() { }

  // ------------------------------------------------------------- predicates

  public static boolean hasField(PartialTemporal partial, TemporalField field) {
    return partial != null && partial.isSupported(field);
  }

  /** Set a field, treating a null partial as empty. */
  public static PartialTemporal setField(PartialTemporal partial, TemporalField field, long value) {
    return (partial == null ? PartialTemporal.empty() : partial).withField(field, value);
  }

  public static boolean hasYYYYMMDD(PartialTemporal partial) {
    return hasField(partial, ChronoField.YEAR)
            && hasField(partial, ChronoField.MONTH_OF_YEAR)
            && hasField(partial, ChronoField.DAY_OF_MONTH);
  }

  public static boolean hasYYMMDD(PartialTemporal partial) {
    return hasField(partial, SUTimeFields.YEAR_OF_CENTURY)
            && hasField(partial, ChronoField.MONTH_OF_YEAR)
            && hasField(partial, ChronoField.DAY_OF_MONTH);
  }

  // ---------------------------------------------------------- field ordering

  /** The coarsest field present, or null if there are none. */
  public static TemporalField getMostGeneral(PartialTemporal partial) {
    return (partial == null || partial.size() == 0) ? null : partial.getFields().firstKey();
  }

  /** The finest field present, or null if there are none. */
  public static TemporalField getMostSpecific(PartialTemporal partial) {
    return (partial == null || partial.size() == 0) ? null : partial.getFields().lastKey();
  }

  /**
   * Whether {@code a} spans a longer stretch of time than {@code b}.
   * <p>
   * Two fields with base units of the same length are neither, so year and year-of-century
   * are unordered. So are any two fields where the one being compared against is bounded by
   * nothing: a year is not enclosed by any larger unit, which makes nothing more general
   * than a year, not even a century. That is the rule SUTime has always used, and
   * {@code combineMoreGeneralFields} depends on it to avoid pulling a century onto a
   * partial that already has a year.
   */
  public static boolean isMoreGeneral(TemporalField a, TemporalField b) {
    if (b.getRangeUnit() == ChronoUnit.FOREVER) {
      return false;
    }
    return a.getBaseUnit().getDuration().compareTo(b.getBaseUnit().getDuration()) > 0;
  }

  public static boolean isMoreSpecific(TemporalField a, TemporalField b) {
    if (a.getRangeUnit() == ChronoUnit.FOREVER) {
      return false;
    }
    return a.getBaseUnit().getDuration().compareTo(b.getBaseUnit().getDuration()) < 0;
  }

  /** The base units of every field present, coarsest first. */
  public static Set<TemporalUnit> getSupportedUnits(PartialTemporal partial) {
    Set<TemporalUnit> units = new LinkedHashSet<>();
    if (partial != null) {
      for (TemporalField field : partial.getFields().keySet()) {
        units.add(field.getBaseUnit());
      }
    }
    return units;
  }

  // ------------------------------------------------------------ compatibility

  /** Whether the two agree on every field they both carry. */
  public static boolean isCompatible(PartialTemporal p1, PartialTemporal p2) {
    if (p1 == null || p2 == null) {
      return true;
    }
    for (Map.Entry<TemporalField, Long> entry : p1.getFields().entrySet()) {
      if (p2.isSupported(entry.getKey())
              && p2.getLong(entry.getKey()) != entry.getValue()) {
        return false;
      }
    }
    return true;
  }

  // ---------------------------------------------------------------- merging

  /**
   * Fill in fields of {@code p1} from {@code p2}. Fields {@code p1} already carries win.
   * A year in {@code p2} resolves a year-of-century in {@code p1} rather than being added
   * alongside it.
   */
  public static PartialTemporal combine(PartialTemporal p1, PartialTemporal p2) {
    if (p1 == null) {
      return p2;
    }
    if (p2 == null) {
      return p1;
    }
    PartialTemporal result = p1;
    for (Map.Entry<TemporalField, Long> entry : p2.getFields().entrySet()) {
      TemporalField field = entry.getKey();
      long value = entry.getValue();
      if (field == ChronoField.YEAR) {
        if (result.isSupported(SUTimeFields.YEAR_OF_CENTURY)) {
          if (!result.isSupported(SUTimeFields.CENTURY_OF_ERA)) {
            result = resolveTwoDigitYear(result, value);
          }
          continue;
        } else if (result.isSupported(SUTimeFields.CENTURY_OF_ERA)) {
          continue;
        }
      } else if (field == SUTimeFields.YEAR_OF_CENTURY || field == SUTimeFields.CENTURY_OF_ERA) {
        if (result.isSupported(ChronoField.YEAR)) {
          continue;
        }
      }
      if (!result.isSupported(field)) {
        result = result.withField(field, value);
      }
    }
    return collapseCenturyAndYearOfCentury(result);
  }

  /**
   * Turn a year-of-century into a full year, using {@code referenceYear} to pick the
   * century. The naive reading is preferred unless it falls after the reference, in which
   * case the previous century is used: '97 against 2017 is 1997, '17 against 2017 is 2017.
   */
  private static PartialTemporal resolveTwoDigitYear(PartialTemporal partial, long referenceYear) {
    long yearOfCentury = partial.getLong(SUTimeFields.YEAR_OF_CENTURY);
    long century = referenceYear / 100;
    long year = yearOfCentury + century * 100;
    if (referenceYear < year) {
      year -= 100;
    }
    return partial.withoutField(SUTimeFields.YEAR_OF_CENTURY).withField(ChronoField.YEAR, year);
  }

  /** A century beside a year-of-century is just a year. */
  private static PartialTemporal collapseCenturyAndYearOfCentury(PartialTemporal partial) {
    if (!partial.isSupported(ChronoField.YEAR)
            && partial.isSupported(SUTimeFields.YEAR_OF_CENTURY)
            && partial.isSupported(SUTimeFields.CENTURY_OF_ERA)) {
      long year = partial.getLong(SUTimeFields.YEAR_OF_CENTURY)
              + partial.getLong(SUTimeFields.CENTURY_OF_ERA) * 100;
      return partial.withoutField(SUTimeFields.YEAR_OF_CENTURY)
              .withoutField(SUTimeFields.CENTURY_OF_ERA)
              .withField(ChronoField.YEAR, year);
    }
    return partial;
  }

  public static PartialTemporal combineMoreGeneralFields(PartialTemporal p1, PartialTemporal p2) {
    return combineMoreGeneralFields(p1, p2, null);
  }

  /**
   * Fill in only those fields of {@code p2} coarser than {@code p1} already reaches, so a
   * month and day pick up a year from the reference but not another month. Stops at the
   * first field that is not coarser, since the fields arrive coarsest first.
   *
   * <p>{@code bound} caps how coarse to go; passing null uses {@code p1}'s own coarsest
   * field, and a bound coarser than that is ignored.
   */
  public static PartialTemporal combineMoreGeneralFields(PartialTemporal p1, PartialTemporal p2,
                                                         TemporalField bound) {
    PartialTemporal result = p1;
    TemporalField coarsestOfP1 = getMostGeneral(p1);
    if (bound == null || (coarsestOfP1 != null && isMoreGeneral(coarsestOfP1, bound))) {
      bound = coarsestOfP1;
    }
    for (Map.Entry<TemporalField, Long> entry : p2.getFields().entrySet()) {
      TemporalField field = entry.getKey();
      long value = entry.getValue();
      if (field == ChronoField.YEAR) {
        if (result.isSupported(SUTimeFields.YEAR_OF_CENTURY)) {
          if (!result.isSupported(SUTimeFields.CENTURY_OF_ERA)) {
            result = resolveTwoDigitYear(result, value);
          }
          continue;
        } else if (result.isSupported(SUTimeFields.DECADE_OF_CENTURY)) {
          if (!result.isSupported(SUTimeFields.CENTURY_OF_ERA)) {
            result = resolveDecade(result, value);
          }
          continue;
        }
      }
      if (bound == null || isMoreGeneral(field, bound)) {
        if (!result.isSupported(field)) {
          result = result.withField(field, value);
        }
      } else {
        break;
      }
    }
    return collapseCenturyAndYearOfCentury(result);
  }

  /** Give a bare decade a century, chosen so it does not fall after the reference year. */
  private static PartialTemporal resolveDecade(PartialTemporal partial, long referenceYear) {
    long decade = partial.getLong(SUTimeFields.DECADE_OF_CENTURY);
    long century = referenceYear / 100;
    if (referenceYear < decade * 10 + century * 100) {
      century--;
    }
    return partial.withField(SUTimeFields.CENTURY_OF_ERA, century);
  }

  // ----------------------------------------------------------- trimming

  /** Drop every field finer than {@code cutoff}, keeping {@code cutoff} itself. */
  public static PartialTemporal discardMoreSpecificFields(PartialTemporal partial,
                                                          TemporalField cutoff) {
    PartialTemporal result = PartialTemporal.empty();
    for (Map.Entry<TemporalField, Long> entry : partial.getFields().entrySet()) {
      TemporalField field = entry.getKey();
      if (field.equals(cutoff) || isMoreGeneral(field, cutoff)) {
        result = result.withField(field, entry.getValue());
      }
    }
    // Keeping only a decade would lose which century it belongs to, so recover it.
    if (result.isSupported(SUTimeFields.DECADE_OF_CENTURY)
            && !result.isSupported(SUTimeFields.CENTURY_OF_ERA)
            && partial.isSupported(ChronoField.YEAR)) {
      result = result.withField(SUTimeFields.CENTURY_OF_ERA,
              Math.floorDiv(partial.getLong(ChronoField.YEAR), 100));
    }
    return result;
  }

  /** Drop every field finer than one whose base unit is {@code cutoff}. */
  public static PartialTemporal discardMoreSpecificFields(PartialTemporal partial,
                                                          TemporalUnit cutoff) {
    PartialTemporal result = PartialTemporal.empty();
    for (Map.Entry<TemporalField, Long> entry : partial.getFields().entrySet()) {
      TemporalField field = entry.getKey();
      if (field.getBaseUnit().getDuration().compareTo(cutoff.getDuration()) >= 0) {
        result = result.withField(field, entry.getValue());
      }
    }
    return result;
  }


  // ------------------------------------------------------------- padding

  /** Year, month, day, hour, minute, second, milli, all at their lowest value. */
  private static final TemporalField[] ISO_TEMPLATE = {
      ChronoField.YEAR, ChronoField.MONTH_OF_YEAR, ChronoField.DAY_OF_MONTH,
      ChronoField.HOUR_OF_DAY, ChronoField.MINUTE_OF_HOUR,
      ChronoField.SECOND_OF_MINUTE, ChronoField.MILLI_OF_SECOND };

  /** The same, for a partial counting weeks rather than months. */
  private static final TemporalField[] ISO_WEEK_TEMPLATE = {
      ChronoField.YEAR, WeekFields.ISO.weekOfWeekBasedYear(), ChronoField.DAY_OF_WEEK,
      ChronoField.HOUR_OF_DAY, ChronoField.MINUTE_OF_HOUR,
      ChronoField.SECOND_OF_MINUTE, ChronoField.MILLI_OF_SECOND };

  private static long templateValue(TemporalField field) {
    return (field == ChronoField.MONTH_OF_YEAR || field == ChronoField.DAY_OF_MONTH
            || field == ChronoField.DAY_OF_WEEK
            || field == WeekFields.ISO.weekOfWeekBasedYear()) ? 1 : 0;
  }

  /**
   * Fill in every field finer than the partial already reaches, so that it names a single
   * instant rather than a span: a bare year becomes the first millisecond of that year.
   *
   * <p>Coarse year-like fields are turned into a year first, so a century and a decade
   * become the year that opens the decade. A quarter or half year supplies the month it
   * starts in. A partial counting weeks is padded with a day of the week rather than a day
   * of the month.
   *
   * <p>{@code granularity} stops the padding early: pass one day and the result reaches the
   * day and no further. Null pads all the way to milliseconds.
   */
  public static PartialTemporal padMoreSpecificFields(PartialTemporal partial,
                                                      TemporalUnit granularity) {
    PartialTemporal result = partial;
    TemporalField finest = getMostSpecific(result);

    if (finest != null && (isMoreGeneral(finest, ChronoField.YEAR)
            || isMoreGeneral(finest, SUTimeFields.YEAR_OF_CENTURY))) {
      result = coarseYearFieldsToYear(result);
    }

    boolean useWeek = false;
    if (result.isSupported(WeekFields.ISO.weekOfWeekBasedYear())) {
      if (!result.isSupported(ChronoField.DAY_OF_MONTH)
              && !result.isSupported(ChronoField.DAY_OF_WEEK)) {
        result = result.withField(ChronoField.DAY_OF_WEEK, 1);
        if (result.isSupported(ChronoField.MONTH_OF_YEAR)) {
          result = result.withoutField(ChronoField.MONTH_OF_YEAR);
        }
      }
      useWeek = true;
    }

    for (TemporalField field : useWeek ? ISO_WEEK_TEMPLATE : ISO_TEMPLATE) {
      if (finest != null && !isMoreSpecific(field, finest)) {
        continue;
      }
      if (result.isSupported(field)) {
        continue;
      }
      if (field == ChronoField.MONTH_OF_YEAR) {
        if (result.isSupported(IsoFields.QUARTER_OF_YEAR)) {
          result = result.withField(field, (result.getLong(IsoFields.QUARTER_OF_YEAR) - 1) * 3 + 1);
          continue;
        } else if (result.isSupported(TemporalFields.HALF_OF_YEAR)) {
          result = result.withField(field, (result.getLong(TemporalFields.HALF_OF_YEAR) - 1) * 6 + 1);
          continue;
        }
      }
      result = result.withField(field, templateValue(field));
    }

    if (granularity != null) {
      result = discardMoreSpecificFields(result, granularity);
    }
    return result;
  }

  /** Turn a century and decade into the year they open, so the date can be pinned down. */
  private static PartialTemporal coarseYearFieldsToYear(PartialTemporal partial) {
    if (partial.isSupported(SUTimeFields.YEAR_OF_CENTURY)) {
      return partial;
    }
    if (partial.isSupported(SUTimeFields.DECADE_OF_CENTURY)) {
      long decade = partial.getLong(SUTimeFields.DECADE_OF_CENTURY);
      if (partial.isSupported(SUTimeFields.CENTURY_OF_ERA)) {
        long year = partial.getLong(SUTimeFields.CENTURY_OF_ERA) * 100 + decade * 10;
        return partial.withoutField(SUTimeFields.DECADE_OF_CENTURY)
                .withoutField(SUTimeFields.CENTURY_OF_ERA)
                .withField(ChronoField.YEAR, year);
      }
      return partial.withoutField(SUTimeFields.DECADE_OF_CENTURY)
              .withField(SUTimeFields.YEAR_OF_CENTURY, decade * 10);
    }
    if (partial.isSupported(SUTimeFields.CENTURY_OF_ERA)) {
      long year = partial.getLong(SUTimeFields.CENTURY_OF_ERA) * 100;
      return partial.withoutField(SUTimeFields.CENTURY_OF_ERA).withField(ChronoField.YEAR, year);
    }
    return partial;
  }

  /** Whether the partial reaches at least the precision of {@code unit}. */
  public static boolean isAtLeastAsSpecificAs(PartialTemporal partial, TemporalUnit unit) {
    TemporalField finest = getMostSpecific(partial);
    return finest != null
            && finest.getBaseUnit().getDuration().compareTo(unit.getDuration()) <= 0;
  }

}
