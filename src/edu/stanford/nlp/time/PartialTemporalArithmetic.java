package edu.stanford.nlp.time;

import org.threeten.extra.PartialTemporal;
import org.threeten.extra.TemporalFields;

import java.time.LocalDate;
import java.time.temporal.ChronoField;
import java.time.temporal.ChronoUnit;
import java.time.temporal.IsoFields;
import java.time.temporal.TemporalField;
import java.time.temporal.TemporalUnit;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Arithmetic on {@link PartialTemporal}, which is a {@code TemporalAccessor} and so has
 * none of its own.
 *
 * <p>The rules follow what SUTime has always done, which is what joda's
 * {@code Partial.withPeriodAdded} and {@code Partial.withFieldAddWrapped} do:
 *
 * <ul>
 *   <li>Only units the partial carries are added. A day added to a year-and-month partial
 *       changes nothing, silently, because there is no day to change.
 *   <li>A field that runs past its range carries into the next larger field the partial
 *       holds, so December plus a month is January of the next year.
 *   <li>With no larger field present it wraps instead, so a bare December plus a month is
 *       January and nothing else moves.
 * </ul>
 *
 * <p>Days are done through {@link LocalDate} when the partial holds a full date, so that
 * month lengths and leap years come out right. Everything else is fixed-size and is done by
 * converting the field and its parent into a single count, adding, and splitting again.
 */
public final class PartialTemporalArithmetic {

  private PartialTemporalArithmetic() { }

  /**
   * A field that carries into a parent, and how many of it fit in one of the parent.
   * Ordered finest first, which is the order the carries have to happen in.
   */
  private static final Map<TemporalField, Carry> CARRIES = new LinkedHashMap<>();

  private static final class Carry {
    final TemporalField parent;
    final int perParent;
    final long min;

    Carry(TemporalField parent, int perParent, long min) {
      this.parent = parent;
      this.perParent = perParent;
      this.min = min;
    }
  }

  static {
    CARRIES.put(ChronoField.MILLI_OF_SECOND, new Carry(ChronoField.SECOND_OF_MINUTE, 1000, 0));
    CARRIES.put(ChronoField.SECOND_OF_MINUTE, new Carry(ChronoField.MINUTE_OF_HOUR, 60, 0));
    CARRIES.put(ChronoField.MINUTE_OF_HOUR, new Carry(ChronoField.HOUR_OF_DAY, 60, 0));
    CARRIES.put(ChronoField.HOUR_OF_DAY, new Carry(ChronoField.DAY_OF_MONTH, 24, 0));
    CARRIES.put(ChronoField.MONTH_OF_YEAR, new Carry(ChronoField.YEAR, 12, 1));
    CARRIES.put(IsoFields.QUARTER_OF_YEAR, new Carry(ChronoField.YEAR, 4, 1));
    CARRIES.put(TemporalFields.HALF_OF_YEAR, new Carry(ChronoField.YEAR, 2, 1));
    CARRIES.put(SUTimeFields.DECADE_OF_CENTURY, new Carry(SUTimeFields.YEAR_OF_DECADE, 10, 0));
  }

  /** The field each unit moves, for the units SUTime adds. */
  private static TemporalField fieldFor(TemporalUnit unit) {
    if (unit == ChronoUnit.MILLIS) {
      return ChronoField.MILLI_OF_SECOND;
    } else if (unit == ChronoUnit.SECONDS) {
      return ChronoField.SECOND_OF_MINUTE;
    } else if (unit == ChronoUnit.MINUTES) {
      return ChronoField.MINUTE_OF_HOUR;
    } else if (unit == ChronoUnit.HOURS) {
      return ChronoField.HOUR_OF_DAY;
    } else if (unit == ChronoUnit.DAYS) {
      return ChronoField.DAY_OF_MONTH;
    } else if (unit == ChronoUnit.MONTHS) {
      return ChronoField.MONTH_OF_YEAR;
    } else if (unit == ChronoUnit.YEARS) {
      return ChronoField.YEAR;
    } else if (unit == IsoFields.QUARTER_YEARS) {
      return IsoFields.QUARTER_OF_YEAR;
    } else if (unit == TemporalFields.HALF_YEARS) {
      return TemporalFields.HALF_OF_YEAR;
    } else if (unit == ChronoUnit.DECADES) {
      return SUTimeFields.DECADE_OF_CENTURY;
    }
    return null;
  }

  /** Whether the partial holds a complete calendar date. */
  private static boolean hasFullDate(PartialTemporal partial) {
    return partial.isSupported(ChronoField.YEAR)
            && partial.isSupported(ChronoField.MONTH_OF_YEAR)
            && partial.isSupported(ChronoField.DAY_OF_MONTH);
  }

  /**
   * Add {@code amount} of {@code unit}, carrying into larger fields where the partial has
   * them and wrapping where it does not. A unit the partial cannot represent is ignored.
   */
  public static PartialTemporal plus(PartialTemporal partial, TemporalUnit unit, long amount) {
    if (amount == 0) {
      return partial;
    }
    TemporalField field = fieldFor(unit);
    if (field == null || !partial.isSupported(field)) {
      return partial;
    }

    // With a full date present, go through LocalDate so that month lengths and leap years
    // are respected and the day is clamped where it has to be: 31 January plus a month is
    // 28 February. Weeks never reach here, since a partial has no week field to move.
    if (hasFullDate(partial)
            && (unit == ChronoUnit.DAYS || unit == ChronoUnit.MONTHS || unit == ChronoUnit.YEARS)) {
      return withDate(partial, localDate(partial).plus(amount, unit));
    }

    return addToField(partial, field, amount);
  }

  /** Add to one field, carrying or wrapping as the partial allows. */
  private static PartialTemporal addToField(PartialTemporal partial, TemporalField field, long amount) {
    Carry carry = CARRIES.get(field);
    long value = partial.getLong(field);

    if (carry == null || !partial.isSupported(carry.parent)) {
      if (carry == null) {
        // No parent defined for this field, so it simply takes the new value.
        return partial.withField(field, value + amount);
      }
      long wrapped = Math.floorMod(value - carry.min + amount, carry.perParent) + carry.min;
      return partial.withField(field, wrapped);
    }

    long parentValue = partial.getLong(carry.parent);
    long total = parentValue * carry.perParent + (value - carry.min) + amount;
    long newParent = Math.floorDiv(total, carry.perParent);
    long newValue = Math.floorMod(total, carry.perParent) + carry.min;

    PartialTemporal result = partial.withField(field, newValue);
    // The parent may itself overflow, so let it carry in turn.
    return addToField(result, carry.parent, newParent - parentValue);
  }

  private static LocalDate localDate(PartialTemporal partial) {
    return LocalDate.of((int) partial.getLong(ChronoField.YEAR),
            (int) partial.getLong(ChronoField.MONTH_OF_YEAR),
            (int) partial.getLong(ChronoField.DAY_OF_MONTH));
  }

  /** Copy the date fields of {@code date} onto the partial, leaving its other fields alone. */
  private static PartialTemporal withDate(PartialTemporal partial, LocalDate date) {
    PartialTemporal result = partial
            .withField(ChronoField.YEAR, date.getYear())
            .withField(ChronoField.MONTH_OF_YEAR, date.getMonthValue())
            .withField(ChronoField.DAY_OF_MONTH, date.getDayOfMonth());
    // Derived date fields, if the partial carries them, follow the new date.
    for (TemporalField derived : new TemporalField[] { ChronoField.DAY_OF_WEEK,
            IsoFields.QUARTER_OF_YEAR, TemporalFields.HALF_OF_YEAR,
            SUTimeFields.DECADE_OF_CENTURY, SUTimeFields.YEAR_OF_DECADE }) {
      if (partial.isSupported(derived)) {
        result = result.withField(derived, date.getLong(derived));
      }
    }
    return result;
  }

}
