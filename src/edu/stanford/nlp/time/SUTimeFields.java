package edu.stanford.nlp.time;

import org.threeten.extra.TemporalFields;

import java.time.temporal.ChronoField;
import java.time.temporal.ChronoUnit;
import java.time.temporal.IsoFields;
import java.time.temporal.Temporal;
import java.time.temporal.TemporalAccessor;
import java.time.temporal.TemporalField;
import java.time.temporal.TemporalUnit;
import java.time.temporal.UnsupportedTemporalTypeException;
import java.time.temporal.ValueRange;
import java.time.temporal.WeekFields;

/**
 * The calendar fields SUTime needs that {@code java.time} and threeten-extra do not supply.
 *
 * <p>Two of the fields SUTime uses already exist and are referenced here rather than
 * reimplemented: {@link IsoFields#QUARTER_OF_YEAR} and
 * {@link TemporalFields#HALF_OF_YEAR}. The five below have no counterpart.
 *
 * <p>Each one reproduces the arithmetic SUTime has always used, which is not always what the
 * name suggests. {@link #WEEK_OF_MONTH} in particular is the ISO week of the year counted
 * modulo four, so the fourth of July 2017 falls in ISO week 27 and the field reads 3. It is
 * not the week's position within its month, and {@code WeekFields.ISO.weekOfMonth()} is not
 * a substitute for it.
 */
public final class SUTimeFields {

  private SUTimeFields() { }

  /** Which month within its quarter, 1 to 3. */
  public static final TemporalField MONTH_OF_QUARTER = Field.MONTH_OF_QUARTER;

  /** Which month within its half year, 1 to 6. */
  public static final TemporalField MONTH_OF_HALF_YEAR = Field.MONTH_OF_HALF_YEAR;

  /** The ISO week of the year counted modulo four, 1 to 4. */
  public static final TemporalField WEEK_OF_MONTH = Field.WEEK_OF_MONTH;

  /** Which decade within its century, 0 to 9. */
  public static final TemporalField DECADE_OF_CENTURY = Field.DECADE_OF_CENTURY;

  /** Which year within its decade, 0 to 9. */
  public static final TemporalField YEAR_OF_DECADE = Field.YEAR_OF_DECADE;

  /** Which year within its century, 0 to 99. The 17 of 2017. */
  public static final TemporalField YEAR_OF_CENTURY = Field.YEAR_OF_CENTURY;

  /** Which century, counting from zero. The 20 of 2017. */
  public static final TemporalField CENTURY_OF_ERA = Field.CENTURY_OF_ERA;

  private enum Field implements TemporalField {

    MONTH_OF_QUARTER("MonthOfQuarter", ChronoUnit.MONTHS, IsoFields.QUARTER_YEARS,
            ValueRange.of(1, 3)) {
      @Override
      public long getFrom(TemporalAccessor temporal) {
        check(temporal);
        return ((temporal.getLong(ChronoField.MONTH_OF_YEAR) - 1) % 3) + 1;
      }

      @Override
      public <R extends Temporal> R adjustInto(R temporal, long newValue) {
        long month = temporal.getLong(ChronoField.MONTH_OF_YEAR);
        long quarterStart = ((month - 1) / 3) * 3;
        return withMonth(temporal, quarterStart + range().checkValidValue(newValue, this));
      }

      @Override
      boolean supportedBy(TemporalAccessor temporal) {
        return temporal.isSupported(ChronoField.MONTH_OF_YEAR);
      }
    },

    MONTH_OF_HALF_YEAR("MonthOfHalfYear", ChronoUnit.MONTHS, TemporalFields.HALF_YEARS,
            ValueRange.of(1, 6)) {
      @Override
      public long getFrom(TemporalAccessor temporal) {
        check(temporal);
        return ((temporal.getLong(ChronoField.MONTH_OF_YEAR) - 1) % 6) + 1;
      }

      @Override
      public <R extends Temporal> R adjustInto(R temporal, long newValue) {
        long month = temporal.getLong(ChronoField.MONTH_OF_YEAR);
        long halfStart = ((month - 1) / 6) * 6;
        return withMonth(temporal, halfStart + range().checkValidValue(newValue, this));
      }

      @Override
      boolean supportedBy(TemporalAccessor temporal) {
        return temporal.isSupported(ChronoField.MONTH_OF_YEAR);
      }
    },

    WEEK_OF_MONTH("WeekOfMonth", ChronoUnit.WEEKS, ChronoUnit.MONTHS, ValueRange.of(1, 4)) {
      @Override
      public long getFrom(TemporalAccessor temporal) {
        check(temporal);
        return ((temporal.getLong(WeekFields.ISO.weekOfWeekBasedYear()) - 1) % 4) + 1;
      }

      @Override
      public <R extends Temporal> R adjustInto(R temporal, long newValue) {
        // The field discards which group of four weeks it came from, so the original
        // value cannot be recovered and the adjustment is not well defined.
        throw new UnsupportedTemporalTypeException("WeekOfMonth cannot be set");
      }

      @Override
      boolean supportedBy(TemporalAccessor temporal) {
        return temporal.isSupported(WeekFields.ISO.weekOfWeekBasedYear());
      }
    },

    DECADE_OF_CENTURY("DecadeOfCentury", ChronoUnit.DECADES, ChronoUnit.CENTURIES,
            ValueRange.of(0, 9)) {
      @Override
      public long getFrom(TemporalAccessor temporal) {
        check(temporal);
        return yearOfCentury(temporal) / 10;
      }

      @Override
      public <R extends Temporal> R adjustInto(R temporal, long newValue) {
        long value = range().checkValidValue(newValue, this);
        long year = temporal.getLong(ChronoField.YEAR);
        long century = Math.floorDiv(year, 100);
        long yearInCentury = Math.floorMod(year, 100);
        return withYear(temporal, century * 100 + value * 10 + (yearInCentury % 10));
      }

      @Override
      boolean supportedBy(TemporalAccessor temporal) {
        return temporal.isSupported(ChronoField.YEAR);
      }
    },

    YEAR_OF_DECADE("YearOfDecade", ChronoUnit.YEARS, ChronoUnit.DECADES, ValueRange.of(0, 9)) {
      @Override
      public long getFrom(TemporalAccessor temporal) {
        check(temporal);
        return yearOfCentury(temporal) % 10;
      }

      @Override
      public <R extends Temporal> R adjustInto(R temporal, long newValue) {
        long value = range().checkValidValue(newValue, this);
        long year = temporal.getLong(ChronoField.YEAR);
        return withYear(temporal, Math.floorDiv(year, 10) * 10 + value);
      }

      @Override
      boolean supportedBy(TemporalAccessor temporal) {
        return temporal.isSupported(ChronoField.YEAR);
      }
    },

    YEAR_OF_CENTURY("YearOfCentury", ChronoUnit.YEARS, ChronoUnit.CENTURIES,
            ValueRange.of(0, 99)) {
      @Override
      public long getFrom(TemporalAccessor temporal) {
        check(temporal);
        return yearOfCentury(temporal);
      }

      @Override
      public <R extends Temporal> R adjustInto(R temporal, long newValue) {
        long value = range().checkValidValue(newValue, this);
        long year = temporal.getLong(ChronoField.YEAR);
        return withYear(temporal, Math.floorDiv(year, 100) * 100 + value);
      }

      @Override
      boolean supportedBy(TemporalAccessor temporal) {
        return temporal.isSupported(ChronoField.YEAR);
      }
    },

    CENTURY_OF_ERA("CenturyOfEra", ChronoUnit.CENTURIES, ChronoUnit.ERAS,
            ValueRange.of(0, 2922789)) {
      @Override
      public long getFrom(TemporalAccessor temporal) {
        check(temporal);
        return Math.floorDiv(temporal.getLong(ChronoField.YEAR), 100);
      }

      @Override
      public <R extends Temporal> R adjustInto(R temporal, long newValue) {
        long year = temporal.getLong(ChronoField.YEAR);
        return withYear(temporal, newValue * 100 + Math.floorMod(year, 100));
      }

      @Override
      boolean supportedBy(TemporalAccessor temporal) {
        return temporal.isSupported(ChronoField.YEAR);
      }
    };

    private final String name;
    private final TemporalUnit baseUnit;
    private final TemporalUnit rangeUnit;
    private final ValueRange range;

    Field(String name, TemporalUnit baseUnit, TemporalUnit rangeUnit, ValueRange range) {
      this.name = name;
      this.baseUnit = baseUnit;
      this.rangeUnit = rangeUnit;
      this.range = range;
    }

    /** Whether the fields this one is derived from are present. */
    abstract boolean supportedBy(TemporalAccessor temporal);

    void check(TemporalAccessor temporal) {
      if ( ! supportedBy(temporal)) {
        throw new UnsupportedTemporalTypeException("Unsupported field: " + name);
      }
    }

    static long yearOfCentury(TemporalAccessor temporal) {
      return Math.floorMod(temporal.getLong(ChronoField.YEAR), 100);
    }

    @SuppressWarnings("unchecked")
    static <R extends Temporal> R withMonth(R temporal, long month) {
      return (R) temporal.with(ChronoField.MONTH_OF_YEAR, month);
    }

    @SuppressWarnings("unchecked")
    static <R extends Temporal> R withYear(R temporal, long year) {
      return (R) temporal.with(ChronoField.YEAR, year);
    }

    @Override
    public TemporalUnit getBaseUnit() {
      return baseUnit;
    }

    @Override
    public TemporalUnit getRangeUnit() {
      return rangeUnit;
    }

    @Override
    public ValueRange range() {
      return range;
    }

    @Override
    public boolean isDateBased() {
      return true;
    }

    @Override
    public boolean isTimeBased() {
      return false;
    }

    @Override
    public boolean isSupportedBy(TemporalAccessor temporal) {
      return supportedBy(temporal);
    }

    @Override
    public ValueRange rangeRefinedBy(TemporalAccessor temporal) {
      check(temporal);
      return range();
    }

    @Override
    public String toString() {
      return name;
    }
  }

}
