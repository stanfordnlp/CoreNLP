package edu.stanford.nlp.time;

import edu.stanford.nlp.ling.tokensregex.types.Expressions;
import edu.stanford.nlp.util.CollectionUtils;
import edu.stanford.nlp.util.FuzzyInterval;
import edu.stanford.nlp.util.Generics;
import edu.stanford.nlp.util.HasInterval;
import edu.stanford.nlp.util.HashIndex;
import edu.stanford.nlp.util.Index;
import edu.stanford.nlp.util.Interval;
import edu.stanford.nlp.util.Pair;
import edu.stanford.nlp.util.StringUtils;
// import edu.stanford.nlp.util.logging.Redwood;

import org.joda.time.*;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.DateTimeFormatterBuilder;

import java.io.Serializable;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.time.format.SignStyle;
import java.time.temporal.ChronoField;
import java.time.temporal.TemporalAccessor;
import java.time.temporal.TemporalQueries;
import java.time.temporal.UnsupportedTemporalTypeException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.time.temporal.ChronoField.*;

/**
 * SUTime is a collection of data structures to represent various temporal
 * concepts and operations between them.
 *
 * Different types of time expressions:
 * <ul>
 * <li>Time - A time point on a time scale  In most cases, we only know partial information
 *        (with a certain granularity) about a point in time (8:00pm)</li>
 * <li>Duration - A length of time (3 days) </li>
 * <li>Interval - A range of time with start and end points</li>
 * <li>Set - A set of time: Can be periodic (Friday every week) or union (Thursday or Friday)</li>
 * </ul>
 *
 * <p>
 * Use {@link TimeAnnotator} to annotate documents within an Annotation pipeline such as CoreNLP.
 * Use {@link SUTimeMain} for standalone testing.
 *
 * @author Angel Chang
 */
public class SUTime  {

  /** A logger for this class */
  // private static Redwood.RedwoodChannels log = Redwood.channels(SUTime.class);

  // TODO:
  // 1. Decrease dependency on JodaTime...
  // 2. Number parsing
  // - Improve Number detection/normalization
  // - Handle four-years, one thousand two hundred and sixty years
  // - Currently custom word to number combo - integrate with Number classifier,
  // QuantifiableEntityNormalizer
  // - Stop repeated conversions of word to numbers
  // 3. Durations
  // - Underspecified durations
  // 4. Date Time
  // - Patterns
  // -- 1st/last week(end) of blah blah
  // -- Don't treat all 3 to 5 as times
  // - Holidays
  // - Too many classes - reduce number of classes
  // 5. Nest time expressions
  // - Before annotating: Can remove nested time expressions
  // - After annotating: types to combine time expressions
  // 6. Set of times (Timex3 standard is weird, timex2 makes more sense?)
  // - freq, quant
  // 7. Ground with respect to reference time - figure out what is reference
  // time to use for what
  // - news... things happen in the past, so favor resolving to past?
  // - Use heuristics from GUTime to figure out direction to resolve to
  // - tids for anchor times...., valueFromFunctions for resolved relative times
  // (option to keep some nested times)?
  // 8. Composite time patterns
  // - Composite time operators
  // 9. Ranges
  // - comparing times (before, after, ...
  // - intersect, mid, resolving
  // - specify clear start/end for range (sonal)
  // 10. Clean up formatting
  // 11. ISO/Timex3/Custom
  // 12. Keep modifiers
  // 13. Handle mid- (token not separated)
  // 14. future, plurals
  // 15. Resolve to future.... with year specified....
  // 16. Check recursive calls
  // 17. Add TimeWithFields (that doesn't use jodatime and is only field based?

  private SUTime() {
  }

  public enum TimexType {
    DATE, TIME, DURATION, SET
  }

  public enum TimexMod {
    BEFORE("<"), AFTER(">"), ON_OR_BEFORE("<="), ON_OR_AFTER("<="), LESS_THAN("<"), MORE_THAN(">"),
    EQUAL_OR_LESS("<="), EQUAL_OR_MORE(">="), START, MID, END, APPROX("~"), EARLY /* GUTIME */, LATE; /* GUTIME */
    private String symbol;

    TimexMod() { }

    TimexMod(String symbol) {
      this.symbol = symbol;
    }

    public String getSymbol() {
      return symbol;
    }
  }

  public enum TimexDocFunc {
    CREATION_TIME, EXPIRATION_TIME, MODIFICATION_TIME, PUBLICATION_TIME, RELEASE_TIME, RECEPTION_TIME, NONE
  }

  public enum TimexAttr {
    type, value, tid, beginPoint, endPoint, quant, freq, mod, anchorTimeID, comment, valueFromFunction, temporalFunction, functionInDocument
  }

  public static final String PAD_FIELD_UNKNOWN = "X";
  public static final String PAD_FIELD_UNKNOWN2 = "XX";
  public static final String PAD_FIELD_UNKNOWN4 = "XXXX";

  // Flags for how to resolve a time expression
  public static final int RESOLVE_NOW = 0x01;
  public static final int RESOLVE_TO_THIS = 0x20;
  public static final int RESOLVE_TO_PAST = 0x40; // Resolve to a past time
  public static final int RESOLVE_TO_FUTURE = 0x80; // Resolve to a future time
  public static final int RESOLVE_TO_CLOSEST = 0x200; // Resolve to closest time
  public static final int DUR_RESOLVE_TO_AS_REF = 0x1000;
  public static final int DUR_RESOLVE_FROM_AS_REF = 0x2000;
  public static final int RANGE_RESOLVE_TIME_REF = 0x100000;

  public static final int RELATIVE_OFFSET_INEXACT = 0x0100;

  public static final int RANGE_OFFSET_BEGIN = 0x0001;
  public static final int RANGE_OFFSET_END = 0x0002;
  public static final int RANGE_EXPAND_FIX_BEGIN = 0x0010;
  public static final int RANGE_EXPAND_FIX_END = 0x0020;

  /** Flags for how to pad when converting times into ranges */
  public static final int RANGE_FLAGS_PAD_MASK = 0x000f; // Pad type
  /** Simple range (without padding) */
  public static final int RANGE_FLAGS_PAD_NONE = 0x0001;
  /** Automatic range (whatever padding we think is most appropriate, default) */
  public static final int RANGE_FLAGS_PAD_AUTO = 0x0002;
  /** Pad to most specific (whatever that is) */
  public static final int RANGE_FLAGS_PAD_FINEST = 0x0003;
  /** Pad to specified granularity */
  public static final int RANGE_FLAGS_PAD_SPECIFIED = 0x0004;

  public static final int FORMAT_ISO = 0x01;
  public static final int FORMAT_TIMEX3_VALUE = 0x02;
  public static final int FORMAT_FULL = 0x04;
  public static final int FORMAT_PAD_UNKNOWN = 0x1000;

  protected static final int timexVersion = 3;

  public static SUTime.Time getCurrentTime() {
    return new GroundedTime(new DateTime());
  }

  // Index of time id to temporal object
  public static class TimeIndex {
    Index<TimeExpression> temporalExprIndex = new HashIndex<>();
    Index<Temporal> temporalIndex = new HashIndex<>();
    Index<Temporal> temporalFuncIndex = new HashIndex<>();

    SUTime.Time docDate;

    public TimeIndex() {
      addTemporal(SUTime.TIME_REF);
    }

    public void clear() {
      temporalExprIndex.clear();
      temporalIndex.clear();
      temporalFuncIndex.clear();
      // t0 is the document date (reserve)
      temporalExprIndex.add(null);
      addTemporal(SUTime.TIME_REF);
    }

    public int getNumberOfTemporals() { return temporalIndex.size(); }
    public int getNumberOfTemporalExprs() { return temporalExprIndex.size(); }
    public int getNumberOfTemporalFuncs() { return temporalFuncIndex.size(); }

    private static final Pattern ID_PATTERN = Pattern.compile("([a-zA-Z]*)(\\d+)");

    public TimeExpression getTemporalExpr(String s) {
      Matcher m = ID_PATTERN.matcher(s);
      if (m.matches()) {
        String prefix = m.group(1);
        int id = Integer.parseInt(m.group(2));
        if ("t".equals(prefix) || prefix.isEmpty()) {
          return temporalExprIndex.get(id);
        }
      }
      return null;
    }

    public Temporal getTemporal(String s) {
      Matcher m = ID_PATTERN.matcher(s);
      if (m.matches()) {
        String prefix = m.group(1);
        int id = Integer.parseInt(m.group(2));
        if ("t".equals(prefix)) {
          TimeExpression te = temporalExprIndex.get(id);
          return (te != null)? te.getTemporal(): null;
        } else if (prefix.isEmpty()) {
          return temporalIndex.get(id);
        }
      }
      return null;
    }

    public TimeExpression getTemporalExpr(int i) {
      return temporalExprIndex.get(i);
    }

    public Temporal getTemporal(int i) {
      return temporalIndex.get(i);
    }

    public Temporal getTemporalFunc(int i) {
      return temporalFuncIndex.get(i);
    }

    public boolean addTemporalExpr(TimeExpression t) {
      Temporal temp = t.getTemporal();
      if (temp != null) {
        addTemporal(temp);
      }
      return temporalExprIndex.add(t);
    }

    public boolean addTemporal(Temporal t) {
      return temporalIndex.add(t);
    }

    public boolean addTemporalFunc(Temporal t) {
      return temporalFuncIndex.add(t);
    }

    public int addToIndexTemporalExpr(TimeExpression t) {
      return temporalExprIndex.addToIndex(t);
    }

    public int addToIndexTemporal(Temporal t) {
      return temporalIndex.addToIndex(t);
    }

    public int addToIndexTemporalFunc(Temporal t) {
      return temporalFuncIndex.addToIndex(t);
    }

    public String toString() {
      StringBuilder builder = new StringBuilder();

      builder.append("Temporal expressions:");
      int index = 0;
      for (TimeExpression exp : temporalExprIndex) {
        builder.append("\n  ").append(index++).append(": ").append(exp);
      }

      builder.append("\nTemporals:");
      index = 0;
      for (Temporal exp : temporalIndex) {
        builder.append("\n  ").append(index++).append(": ").append(exp);
      }

      builder.append("\nTemporal functions:");
      index = 0;
      for (Temporal exp : temporalFuncIndex) {
        builder.append("\n  ").append(index++).append(": ").append(exp);
      }
      return builder.toString();
    }
  }

  /**
   * Basic temporal object.
   *
   * <p>
   * There are 4 main types of temporal objects
   * <ol>
   * <li>Time - Conceptually a point in time
   * <br>NOTE: Due to limitation in precision, it is
   * difficult to get an exact point in time
   * </li>
   * <li>Duration - Amount of time in a time interval
   *  <ul><li>DurationWithMillis - Duration specified in milliseconds
   *          (wrapper around JodaTime Duration)</li>
   *      <li>DurationWithFields - Duration specified with
   *         fields like day, year, etc (wrapper around JodaTime Period)</lI>
   *      <li>DurationRange - A duration that falls in a particular range (with min to max)</li>
   *  </ul>
   * </li>
   * <li>Range - Time Interval with a start time, end time, and duration</li>
   * <li>TemporalSet - A set of temporal objects
   *  <ul><li>ExplicitTemporalSet - Explicit set of temporals (not used)
   *         <br>Ex: Tuesday 1-2pm, Wednesday night</li>
   *      <li>PeriodicTemporalSet - Reoccurring times
   *         <br>Ex: Every Tuesday</li>
   *  </ul>
   * </li>
   * </ol>
   */
  public abstract static class Temporal implements Cloneable, Serializable {
    public String mod;
    public boolean approx;
    StandardTemporalType standardTemporalType;
    public String timeLabel;
    // Duration after which the time is uncertain (what is there is an estimate)
    public Duration uncertaintyGranularity;

    public Temporal() {
    }

    public Temporal(Temporal t) {
      this.mod = t.mod;
      this.approx = t.approx;
      this.uncertaintyGranularity = t.uncertaintyGranularity;
//      this.standardTimeType = t.standardTimeType;
//      this.timeLabel = t.timeLabel;
    }

    public abstract boolean isGrounded();

    // Returns time representation for Temporal (if available)
    public abstract Time getTime();

    // Returns duration (estimate of how long the temporal expression is for)
    public abstract Duration getDuration();

    // Returns range (start/end points of temporal, automatic granularity)
    public Range getRange() {
      return getRange(RANGE_FLAGS_PAD_AUTO);
    }

    // Returns range (start/end points of temporal)
    public Range getRange(int flags) {
      return getRange(flags, null);
    }

    // Returns range (start/end points of temporal), using specified flags
    public abstract Range getRange(int flags, Duration granularity);

    // Returns how often this time would repeat
    // Ex: friday repeat weekly, hour repeat hourly, hour in a day repeat daily
    public Duration getPeriod() {
  /*    TimeLabel tl = getTimeLabel();
      if (tl != null) {
        return tl.getPeriod();
      } */
      StandardTemporalType tlt = getStandardTemporalType();
      if (tlt != null) {
        return tlt.getPeriod();
      }
      return null;
    }

    // Returns the granularity to which this time or duration is specified
    // Typically the most specific time unit
    public Duration getGranularity() {
      StandardTemporalType tlt = getStandardTemporalType();
      if (tlt != null) {
        return tlt.getGranularity();
      }
      return null;
    }

    public Duration getUncertaintyGranularity() {
      if (uncertaintyGranularity != null) return uncertaintyGranularity;
      return getGranularity();
    }

    // Resolves this temporal expression with respect to the specified reference
    // time using flags
    public Temporal resolve(Time refTime) {
      return resolve(refTime, 0);
    }

    public abstract Temporal resolve(Time refTime, int flags);

    public StandardTemporalType getStandardTemporalType() {
      return standardTemporalType;
    }

    // Returns if the current temporal expression is an reference
    public boolean isRef() {
      return false;
    }

    // Return sif the current temporal expression is approximate
    public boolean isApprox() {
      return approx;
    }

    // TIMEX related functions
    public int getTid(TimeIndex timeIndex) {
      return timeIndex.addToIndexTemporal(this);
    }

    public String getTidString(TimeIndex timeIndex) {
      return "t" + getTid(timeIndex);
    }

    public int getTfid(TimeIndex timeIndex) {
      return timeIndex.addToIndexTemporalFunc(this);
    }

    public String getTfidString(TimeIndex timeIndex) {
      return "tf" + getTfid(timeIndex);
    }

    // Returns attributes to convert this temporal expression into timex object
    public boolean includeTimexAltValue() {
      return false;
    }

    public Map<String, String> getTimexAttributes(TimeIndex timeIndex) {
      Map<String, String> map = new LinkedHashMap<>();
      map.put(TimexAttr.tid.name(), getTidString(timeIndex));
      // NOTE: GUTime used "VAL" instead of TIMEX3 standard "value"
      // NOTE: attributes are case sensitive, GUTIME used mostly upper case
      // attributes....
      String val = getTimexValue();
      if (val != null) {
        map.put(TimexAttr.value.name(), val);
      }
      if (val == null || includeTimexAltValue()) {
        String str = toFormattedString(FORMAT_FULL);
        if (str != null) {
          map.put("alt_value", str);
        }
      }
      /*     Range r = getRange();
           if (r != null) map.put("range", r.toString());    */
      /*     map.put("str", toString());        */
      map.put(TimexAttr.type.name(), getTimexType().name());
      if (mod != null) {
        map.put(TimexAttr.mod.name(), mod);
      }
      return map;
    }

    // Returns the timex type
    public TimexType getTimexType() {
      if (getStandardTemporalType() != null) {
        return getStandardTemporalType().getTimexType();
      } else {
        return null;
      }
    }

    // Returns timex value (by default it is the ISO string representation of
    // this object)
    public String getTimexValue() {
      return toFormattedString(FORMAT_TIMEX3_VALUE);
    }

    public String toISOString() {
      return toFormattedString(FORMAT_ISO);
    }

    public String toString() {
      // TODO: Full string representation
      return toFormattedString(FORMAT_FULL);
    }

    public String getTimeLabel() {
      return timeLabel;
    }

    public String toFormattedString(int flags) {
      return getTimeLabel();
    }

    // Temporal operations...
    public static Temporal setTimeZone(Temporal t, DateTimeZone tz) {
      if (t == null) return null;
      return t.setTimeZone(tz);
    }

    public Temporal setTimeZone(DateTimeZone tz) {
      return this;
    }

    public Temporal setTimeZone(int offsetHours) {
      return setTimeZone(DateTimeZone.forOffsetHours(offsetHours));
    }

    // public abstract Temporal add(Duration offset);
    public Temporal next() {
      Duration per = getPeriod();
      if (per != null) {
        if (this instanceof Duration) {
          return new RelativeTime(new RelativeTime(TemporalOp.THIS, this, DUR_RESOLVE_TO_AS_REF), TemporalOp.OFFSET, per);
        } else {
          // return new RelativeTime(new RelativeTime(TemporalOp.THIS, this),
          // TemporalOp.OFFSET, per);
          return TemporalOp.OFFSET.apply(this, per);
        }
      }
      return null;
    }

    public Temporal prev() {
      Duration per = getPeriod();
      if (per != null) {
        if (this instanceof Duration) {
          return new RelativeTime(new RelativeTime(TemporalOp.THIS, this, DUR_RESOLVE_FROM_AS_REF), TemporalOp.OFFSET, per.multiplyBy(-1));
        } else {
          // return new RelativeTime(new RelativeTime(TemporalOp.THIS, this),
          // TemporalOp.OFFSET, per.multiplyBy(-1));
          return TemporalOp.OFFSET.apply(this, per.multiplyBy(-1));
        }
      }
      return null;
    }

    public/* abstract*/Temporal intersect(Temporal t) {
      return null;
    }

    public String getMod() {
      return mod;
    }

    /*   public void setMod(String mod) {
         this.mod = mod;
       } */

    public Temporal addMod(String mod) {
      try {
        Temporal t = (Temporal) this.clone();
        t.mod = mod;
        return t;
      } catch (CloneNotSupportedException ex) {
        throw new RuntimeException(ex);
      }
    }

    public Temporal addModApprox(String mod, boolean approx) {
      try {
        Temporal t = (Temporal) this.clone();
        t.mod = mod;
        t.approx = approx;
        return t;
      } catch (CloneNotSupportedException ex) {
        throw new RuntimeException(ex);
      }
    }

    private static final long serialVersionUID = 1;
  }

  public static <T extends Temporal> T createTemporal(StandardTemporalType timeType, T temporal) {
    temporal.standardTemporalType = timeType;
    return temporal;
  }

  public static <T extends Temporal> T createTemporal(StandardTemporalType timeType, String label, T temporal) {
    temporal.standardTemporalType = timeType;
    temporal.timeLabel = label;
    return temporal;
  }

  public static <T extends Temporal> T createTemporal(StandardTemporalType timeType, String label, String mod, T temporal) {
    temporal.standardTemporalType = timeType;
    temporal.timeLabel = label;
    temporal.mod = mod;
    return temporal;
  }
  // Basic time units (durations)

  public static final Duration YEAR = new DurationWithFields(Period.years(1)) {
    @Override
    public DateTimeFieldType[] getDateTimeFields() {
      return new DateTimeFieldType[] { DateTimeFieldType.year(), DateTimeFieldType.yearOfCentury(), DateTimeFieldType.yearOfEra() };
    }
    private static final long serialVersionUID = 1;
  };

  public static final Duration DAY = new DurationWithFields(Period.days(1)) {
    @Override
    public DateTimeFieldType[] getDateTimeFields() {
      return new DateTimeFieldType[] { DateTimeFieldType.dayOfMonth(), DateTimeFieldType.dayOfWeek(), DateTimeFieldType.dayOfYear() };
    }
    private static final long serialVersionUID = 1;
  };

  public static final Duration WEEK = new DurationWithFields(Period.weeks(1)) {
    @Override
    public DateTimeFieldType[] getDateTimeFields() {
      return new DateTimeFieldType[] { DateTimeFieldType.weekOfWeekyear() };
    }
    private static final long serialVersionUID = 1;
  };

  public static final Duration FORTNIGHT = new DurationWithFields(Period.weeks(2));

  public static final Duration MONTH = new DurationWithFields(Period.months(1)) {
    @Override
    public DateTimeFieldType[] getDateTimeFields() {
      return new DateTimeFieldType[] { DateTimeFieldType.monthOfYear() };
    }
    private static final long serialVersionUID = 1;
  };

  // public static final Duration QUARTER = new DurationWithFields(new
  // Period(JodaTimeUtils.Quarters)) {
  public static final Duration QUARTER = new DurationWithFields(Period.months(3)) {
    @Override
    public DateTimeFieldType[] getDateTimeFields() {
      return new DateTimeFieldType[] { JodaTimeUtils.QuarterOfYear };
    }
    private static final long serialVersionUID = 1;
  };

  public static final Duration HALFYEAR = new DurationWithFields(Period.months(6)) {
    @Override
    public DateTimeFieldType[] getDateTimeFields() {
      return new DateTimeFieldType[] { JodaTimeUtils.HalfYearOfYear };
    }
    private static final long serialVersionUID = 1;
  };

  public static final Duration MILLIS = new DurationWithFields(Period.millis(1)) {
    @Override
    public DateTimeFieldType[] getDateTimeFields() {
      return new DateTimeFieldType[] { DateTimeFieldType.millisOfSecond(), DateTimeFieldType.millisOfDay() };
    }
    private static final long serialVersionUID = 1;
  };

  public static final Duration SECOND = new DurationWithFields(Period.seconds(1)) {
    @Override
    public DateTimeFieldType[] getDateTimeFields() {
      return new DateTimeFieldType[] { DateTimeFieldType.secondOfMinute(), DateTimeFieldType.secondOfDay() };
    }
    private static final long serialVersionUID = 1;
  };

  public static final Duration MINUTE = new DurationWithFields(Period.minutes(1)) {
    @Override
    public DateTimeFieldType[] getDateTimeFields() {
      return new DateTimeFieldType[] { DateTimeFieldType.minuteOfHour(), DateTimeFieldType.minuteOfDay() };
    }
    private static final long serialVersionUID = 1;
  };

  public static final Duration HOUR = new DurationWithFields(Period.hours(1)) {
    @Override
    public DateTimeFieldType[] getDateTimeFields() {
      return new DateTimeFieldType[] { DateTimeFieldType.hourOfDay(), DateTimeFieldType.hourOfHalfday() };
    }
    private static final long serialVersionUID = 1;
  };

  public static final Duration HALFHOUR = new DurationWithFields(Period.minutes(30));

  public static final Duration QUARTERHOUR = new DurationWithFields(Period.minutes(15));

  public static final Duration DECADE = new DurationWithFields(Period.years(10)) {
    @Override
    public DateTimeFieldType[] getDateTimeFields() {
      return new DateTimeFieldType[] { JodaTimeUtils.DecadeOfCentury };
    }
    private static final long serialVersionUID = 1;
  };

  public static final Duration CENTURY = new DurationWithFields(Period.years(100)) {
    @Override
    public DateTimeFieldType[] getDateTimeFields() {
      return new DateTimeFieldType[] { DateTimeFieldType.centuryOfEra() };
    }
    private static final long serialVersionUID = 1;
  };

  public static final Duration MILLENNIUM = new DurationWithFields(Period.years(1000));

  public static final Time TIME_REF = new RefTime("REF") {
    private static final long serialVersionUID = 1;
  };
  public static final Time TIME_REF_UNKNOWN = new RefTime("UNKNOWN");
  public static final Time TIME_UNKNOWN = new SimpleTime("UNKNOWN");
  public static final Time TIME_NONE = null; // No time
  public static final Time TIME_NONE_OK = new SimpleTime("NOTIME");

  // The special time of now
  public static final Time TIME_NOW = new RefTime(StandardTemporalType.REFTIME, "PRESENT_REF", "NOW");
  public static final Time TIME_PRESENT = createTemporal(StandardTemporalType.REFDATE, "PRESENT_REF", new InexactTime(new Range(TIME_NOW, TIME_NOW)));
  public static final Time TIME_PAST = createTemporal(StandardTemporalType.REFDATE, "PAST_REF",new InexactTime(new Range(TIME_UNKNOWN, TIME_NOW)));
  public static final Time TIME_FUTURE = createTemporal(StandardTemporalType.REFDATE, "FUTURE_REF", new InexactTime(new Range(TIME_NOW, TIME_UNKNOWN)));

  public static final Duration DURATION_UNKNOWN = new DurationWithFields();
  public static final Duration DURATION_NONE = new DurationWithFields(Period.ZERO);

  // Basic dates/times

  // Day of week
  // Use constructors rather than calls to
  // StandardTemporalType.createTemporal because sometimes the class
  // loader seems to load objects in an incorrect order, resulting in
  // an exception.  This is especially evident when deserializing
  public static final Time MONDAY = new PartialTime(StandardTemporalType.DAY_OF_WEEK, new Partial(DateTimeFieldType.dayOfWeek(), 1));
  public static final Time TUESDAY = new PartialTime(StandardTemporalType.DAY_OF_WEEK, new Partial(DateTimeFieldType.dayOfWeek(), 2));
  public static final Time WEDNESDAY = new PartialTime(StandardTemporalType.DAY_OF_WEEK, new Partial(DateTimeFieldType.dayOfWeek(), 3));
  public static final Time THURSDAY = new PartialTime(StandardTemporalType.DAY_OF_WEEK, new Partial(DateTimeFieldType.dayOfWeek(), 4));
  public static final Time FRIDAY = new PartialTime(StandardTemporalType.DAY_OF_WEEK, new Partial(DateTimeFieldType.dayOfWeek(), 5));
  public static final Time SATURDAY = new PartialTime(StandardTemporalType.DAY_OF_WEEK, new Partial(DateTimeFieldType.dayOfWeek(), 6));
  public static final Time SUNDAY = new PartialTime(StandardTemporalType.DAY_OF_WEEK, new Partial(DateTimeFieldType.dayOfWeek(), 7));

  public static final Time WEEKDAY = createTemporal(StandardTemporalType.DAYS_OF_WEEK, "WD",
          new InexactTime(null, SUTime.DAY, new SUTime.Range(SUTime.MONDAY, SUTime.FRIDAY)) {
            @Override
            public Duration getDuration() {
              return SUTime.DAY;
            }
            private static final long serialVersionUID = 1;
          });
  public static final Time WEEKEND = createTemporal(StandardTemporalType.DAYS_OF_WEEK, "WE",
          new TimeWithRange(new SUTime.Range(SUTime.SATURDAY, SUTime.SUNDAY, SUTime.DAY.multiplyBy(2))));

  // Months
  // Use constructors rather than calls to
  // StandardTemporalType.createTemporal because sometimes the class
  // loader seems to load objects in an incorrect order, resulting in
  // an exception.  This is especially evident when deserializing
  public static final Time JANUARY = new IsoDate(StandardTemporalType.MONTH_OF_YEAR, -1, 1, -1);
  public static final Time FEBRUARY = new IsoDate(StandardTemporalType.MONTH_OF_YEAR, -1, 2, -1);
  public static final Time MARCH = new IsoDate(StandardTemporalType.MONTH_OF_YEAR, -1, 3, -1);
  public static final Time APRIL = new IsoDate(StandardTemporalType.MONTH_OF_YEAR, -1, 4, -1);
  public static final Time MAY = new IsoDate(StandardTemporalType.MONTH_OF_YEAR, -1, 5, -1);
  public static final Time JUNE = new IsoDate(StandardTemporalType.MONTH_OF_YEAR, -1, 6, -1);
  public static final Time JULY = new IsoDate(StandardTemporalType.MONTH_OF_YEAR, -1, 7, -1);
  public static final Time AUGUST = new IsoDate(StandardTemporalType.MONTH_OF_YEAR, -1, 8, -1);
  public static final Time SEPTEMBER = new IsoDate(StandardTemporalType.MONTH_OF_YEAR, -1, 9, -1);
  public static final Time OCTOBER = new IsoDate(StandardTemporalType.MONTH_OF_YEAR, -1, 10, -1);
  public static final Time NOVEMBER = new IsoDate(StandardTemporalType.MONTH_OF_YEAR, -1, 11, -1);
  public static final Time DECEMBER = new IsoDate(StandardTemporalType.MONTH_OF_YEAR, -1, 12, -1);

  // Dates are rough with respect to northern hemisphere (actual
  // solstice/equinox days depend on the year)
  public static final Time SPRING_EQUINOX = createTemporal(StandardTemporalType.DAY_OF_YEAR, "SP", new SUTime.InexactTime(new SUTime.Range(new IsoDate(-1, 3, 20), new IsoDate(-1, 3, 21))));
  public static final Time SUMMER_SOLSTICE = createTemporal(StandardTemporalType.DAY_OF_YEAR, "SU", new SUTime.InexactTime(new SUTime.Range(new IsoDate(-1, 6, 20), new IsoDate(-1, 6, 21))));
  public static final Time WINTER_SOLSTICE = createTemporal(StandardTemporalType.DAY_OF_YEAR, "WI", new SUTime.InexactTime(new SUTime.Range(new IsoDate(-1, 12, 21), new IsoDate(-1, 12, 22))));
  public static final Time FALL_EQUINOX = createTemporal(StandardTemporalType.DAY_OF_YEAR, "FA", new SUTime.InexactTime(new SUTime.Range(new IsoDate(-1, 9, 22), new IsoDate(-1, 9, 23))));

  // Dates for seasons are rough with respect to northern hemisphere
  public static final Time SPRING = createTemporal(StandardTemporalType.SEASON_OF_YEAR, "SP",
           new SUTime.InexactTime(SPRING_EQUINOX, QUARTER, new SUTime.Range(SUTime.MARCH, SUTime.JUNE, SUTime.QUARTER)));
  public static final Time SUMMER = createTemporal(StandardTemporalType.SEASON_OF_YEAR, "SU",
           new SUTime.InexactTime(SUMMER_SOLSTICE, QUARTER, new SUTime.Range(SUTime.JUNE, SUTime.SEPTEMBER, SUTime.QUARTER)));
  public static final Time FALL = createTemporal(StandardTemporalType.SEASON_OF_YEAR, "FA",
          new SUTime.InexactTime(FALL_EQUINOX, QUARTER, new SUTime.Range(SUTime.SEPTEMBER, SUTime.DECEMBER, SUTime.QUARTER)));
  public static final Time WINTER = createTemporal(StandardTemporalType.SEASON_OF_YEAR, "WI",
          new SUTime.InexactTime(WINTER_SOLSTICE, QUARTER, new SUTime.Range(SUTime.DECEMBER, SUTime.MARCH, SUTime.QUARTER)));

  // Time of day
  public static final PartialTime NOON = createTemporal(StandardTemporalType.TIME_OF_DAY, "MI", new IsoTime(12, 0, -1));
  public static final PartialTime MIDNIGHT = createTemporal(StandardTemporalType.TIME_OF_DAY, new IsoTime(0, 0, -1));
  public static final Time MORNING = createTemporal(StandardTemporalType.TIME_OF_DAY, "MO", new InexactTime(new Range(new InexactTime(new Partial(DateTimeFieldType.hourOfDay(), 6)), NOON)));
  public static final Time AFTERNOON = createTemporal(StandardTemporalType.TIME_OF_DAY, "AF", new InexactTime(new Range(NOON, new InexactTime(new Partial(DateTimeFieldType.hourOfDay(), 18)))));
  public static final Time EVENING = createTemporal(StandardTemporalType.TIME_OF_DAY, "EV", new InexactTime(new Range(new InexactTime(new Partial(DateTimeFieldType.hourOfDay(), 18)), new InexactTime(new Partial(DateTimeFieldType
      .hourOfDay(), 20)))));
  public static final Time NIGHT = createTemporal(StandardTemporalType.TIME_OF_DAY, "NI",
          new InexactTime(MIDNIGHT, new Range(new InexactTime(new Partial(DateTimeFieldType.hourOfDay(), 14)), HOUR.multiplyBy(10))));
  public static final Time SUNRISE = createTemporal(StandardTemporalType.TIME_OF_DAY, "MO", TimexMod.EARLY.name(), new PartialTime());
  public static final Time SUNSET = createTemporal(StandardTemporalType.TIME_OF_DAY, "EV", TimexMod.EARLY.name(), new PartialTime());
  public static final Time DAWN = createTemporal(StandardTemporalType.TIME_OF_DAY, "MO", TimexMod.EARLY.name(), new PartialTime());
  public static final Time DUSK = createTemporal(StandardTemporalType.TIME_OF_DAY, "EV", new PartialTime());
  public static final Time DAYTIME = createTemporal(StandardTemporalType.TIME_OF_DAY, "DT", new InexactTime(new Range(SUNRISE, SUNSET)));
  public static final Time LUNCHTIME = createTemporal(StandardTemporalType.TIME_OF_DAY, "MI", new InexactTime(new Range(new InexactTime(new Partial(DateTimeFieldType.hourOfDay(), 12)), new InexactTime(new Partial(DateTimeFieldType
      .hourOfDay(), 14)))));
  public static final Time TEATIME = createTemporal(StandardTemporalType.TIME_OF_DAY, "AF", new InexactTime(new Range(new InexactTime(new Partial(DateTimeFieldType.hourOfDay(), 15)), new InexactTime(new Partial(DateTimeFieldType
      .hourOfDay(), 17)))));
  public static final Time DINNERTIME = createTemporal(StandardTemporalType.TIME_OF_DAY, "EV", new InexactTime(new Range(new InexactTime(new Partial(DateTimeFieldType.hourOfDay(), 18)), new InexactTime(new Partial(DateTimeFieldType
      .hourOfDay(), 20)))));
  public static final Time WORKDAY = createTemporal(StandardTemporalType.TIME_OF_DAY, "WH", new InexactTime(new Range(new InexactTime(new Partial(DateTimeFieldType.hourOfDay(), 9)),
          new InexactTime(new Partial(DateTimeFieldType.hourOfDay(), 17)))));

  public static final Time MORNING_TWILIGHT = createTemporal(StandardTemporalType.TIME_OF_DAY, "MO", new InexactTime(new Range(DAWN, SUNRISE)));
  public static final Time EVENING_TWILIGHT = createTemporal(StandardTemporalType.TIME_OF_DAY, "EV", new InexactTime(new Range(SUNSET, DUSK)));
  public static final TemporalSet TWILIGHT = createTemporal(StandardTemporalType.TIME_OF_DAY, "NI", new ExplicitTemporalSet(EVENING_TWILIGHT, MORNING_TWILIGHT));

  // Relative days
  public static final RelativeTime YESTERDAY = new RelativeTime(DAY.multiplyBy(-1));
  public static final RelativeTime TOMORROW = new RelativeTime(DAY.multiplyBy(+1));
  public static final RelativeTime TODAY = new RelativeTime(TemporalOp.THIS, SUTime.DAY);
  public static final RelativeTime TONIGHT = new RelativeTime(TemporalOp.THIS, SUTime.NIGHT);

  public enum TimeUnit {
    // Basic time units
    MILLIS(SUTime.MILLIS), SECOND(SUTime.SECOND), MINUTE(SUTime.MINUTE), HOUR(SUTime.HOUR),
    DAY(SUTime.DAY), WEEK(SUTime.WEEK), MONTH(SUTime.MONTH), QUARTER(SUTime.QUARTER), HALFYEAR(SUTime.HALFYEAR),
    YEAR(SUTime.YEAR), DECADE(SUTime.DECADE), CENTURY(SUTime.CENTURY), MILLENNIUM(SUTime.MILLENNIUM),
    UNKNOWN(SUTime.DURATION_UNKNOWN);

    private final Duration duration;

    TimeUnit(Duration d) {
      this.duration = d;
    }

    public Duration getDuration() {
      return duration;
    } // How long does this time last?

    public Duration getPeriod() {
      return duration;
    } // How often does this type of time occur?

    public Duration getGranularity() {
      return duration;
    } // What is the granularity of this time?

    public Temporal createTemporal(int n) {
      return duration.multiplyBy(n);
    }
  }

  public enum StandardTemporalType {
    REFDATE(TimexType.DATE),
    REFTIME(TimexType.TIME),
 /*   MILLIS(TimexType.TIME, TimeUnit.MILLIS),
    SECOND(TimexType.TIME, TimeUnit.SECOND),
    MINUTE(TimexType.TIME, TimeUnit.MINUTE),
    HOUR(TimexType.TIME, TimeUnit.HOUR),
    DAY(TimexType.TIME, TimeUnit.DAY),
    WEEK(TimexType.TIME, TimeUnit.WEEK),
    MONTH(TimexType.TIME, TimeUnit.MONTH),
    QUARTER(TimexType.TIME, TimeUnit.QUARTER),
    YEAR(TimexType.TIME, TimeUnit.YEAR),  */
    TIME_OF_DAY(TimexType.TIME, TimeUnit.HOUR, SUTime.DAY) {
      @Override
      public Duration getDuration() {
        return SUTime.HOUR.makeInexact();
      }
    },
    DAY_OF_YEAR(TimexType.DATE, TimeUnit.DAY, SUTime.YEAR) {
      @Override
      protected Time _createTemporal(int n) {
        return new PartialTime(new Partial(DateTimeFieldType.dayOfYear(), n));
      }
    },
    DAY_OF_WEEK(TimexType.DATE, TimeUnit.DAY, SUTime.WEEK) {
      @Override
      protected Time _createTemporal(int n) {
        return new PartialTime(new Partial(DateTimeFieldType.dayOfWeek(), n));
      }
    },
    DAYS_OF_WEEK(TimexType.DATE, TimeUnit.DAY, SUTime.WEEK) {
      @Override
      public Duration getDuration() {
        return SUTime.DAY.makeInexact();
      }
    },
    WEEK_OF_YEAR(TimexType.DATE, TimeUnit.WEEK, SUTime.YEAR) {
      @Override
      protected Time _createTemporal(int n) {
        return new PartialTime(new Partial(DateTimeFieldType.weekOfWeekyear(), n));
      }
    },
    MONTH_OF_YEAR(TimexType.DATE, TimeUnit.MONTH, SUTime.YEAR) {
      @Override
      protected Time _createTemporal(int n) {
        //return new PartialTime(new Partial(DateTimeFieldType.monthOfYear(), n));
        return new IsoDate(-1, n, -1);
      }
    },
    PART_OF_YEAR(TimexType.DATE, TimeUnit.DAY, SUTime.YEAR) {
      @Override
      public Duration getDuration() {
        return SUTime.DAY.makeInexact();
      }
    },
    SEASON_OF_YEAR(TimexType.DATE, TimeUnit.QUARTER, SUTime.YEAR),

    QUARTER_OF_YEAR(TimexType.DATE, TimeUnit.QUARTER, SUTime.YEAR) {
      @Override
      protected Time _createTemporal(int n) {
        return new PartialTime(new Partial(JodaTimeUtils.QuarterOfYear, n));
      }
    },

    HALF_OF_YEAR(TimexType.DATE, TimeUnit.HALFYEAR, SUTime.YEAR) {
      @Override
      protected Time _createTemporal(int n) {
        return new PartialTime(new Partial(JodaTimeUtils.HalfYearOfYear, n));
      }
    };

    final TimexType timexType;
    TimeUnit unit = TimeUnit.UNKNOWN;
    Duration period = SUTime.DURATION_NONE;

    StandardTemporalType(TimexType timexType) {
      this.timexType = timexType;
    }

    StandardTemporalType(TimexType timexType, TimeUnit unit) {
      this.timexType = timexType;
      this.unit = unit;
      this.period = unit.getPeriod();
    }

    StandardTemporalType(TimexType timexType, TimeUnit unit, Duration period) {
      this.timexType = timexType;
      this.unit = unit;
      this.period = period;
    }

    public TimexType getTimexType() {
      return timexType;
    }

    public Duration getDuration() {
      return unit.getDuration();
    } // How long does this time last?

    public Duration getPeriod() {
      return period;
    } // How often does this type of time occur?

    public Duration getGranularity() {
      return unit.getGranularity();
    } // What is the granularity of this time?

    protected Temporal _createTemporal(int n) {
      return null;
    }

    public Temporal createTemporal(int n) {
      Temporal t = _createTemporal(n);
      if (t != null) {
        t.standardTemporalType = this;
      }
      return t;
    }

    public static Temporal create(Expressions.CompositeValue compositeValue) {
      StandardTemporalType temporalType = compositeValue.get("type");
      String label = compositeValue.get("label");
      String modifier = compositeValue.get("modifier");
      Temporal temporal = compositeValue.get("value");
      if (temporal == null) {
        temporal = new PartialTime();
      }
      return SUTime.createTemporal(temporalType,  label, modifier, temporal);
    }
  }



  // Temporal operators (currently operates on two temporals and returns another
  // temporal)
  // Can add operators for:
  // lookup of temporal from string
  // creating durations, dates
  // public interface TemporalOp extends Function<Temporal,Temporal>();
  public enum TemporalOp {
    // For durations: possible interpretation of next/prev:
    // next month, next week
    // NEXT: on Thursday, next week = week starting on next monday
    // ??? on Thursday, next week = one week starting from now
    // prev month, prev week
    // PREV: on Thursday, last week = week starting on the monday one week
    // before this monday
    // ??? on Thursday, last week = one week going back starting from now
    // NEXT: on June 19, next month = July 1 to July 31
    // ???:  on June 19, next month = July 19 to August 19
    //
    //
    // For partial dates: two kind of next
    // next tuesday, next winter, next january
    // NEXT (PARENT UNIT, FAVOR): Example: on monday, next tuesday = tuesday of
    // the week after this
    // NEXT IMMEDIATE (NOT FAVORED): Example: on monday, next saturday =
    // saturday of this week
    // last saturday, last winter, last january
    // PREV (PARENT UNIT, FAVOR): Example: on wednesday, last tuesday = tuesday
    // of the week before this
    // PREV IMMEDIATE (NOT FAVORED): Example: on saturday, last tuesday =
    // tuesday of this week

    // (successor) Next week/day/...
    NEXT {
      @Override
      public Temporal apply(Temporal arg1, Temporal arg2, int flags) {
        if (arg2 == null) {
          return arg1;
        }
        Temporal arg2Next = arg2.next();
        if (arg1 == null || arg2Next == null) {
          return arg2Next;
        }
        if (arg1 instanceof Time) {
          // TODO: flags?
          Temporal resolved = arg2Next.resolve((Time) arg1, 0 /* RESOLVE_TO_FUTURE */);
          return resolved;
        } else {
          throw new UnsupportedOperationException("NEXT not implemented for arg1=" + arg1.getClass() + ", arg2=" + arg2.getClass());
        }
      }
    },
    // This coming week/friday
    NEXT_IMMEDIATE {
      @Override
      public Temporal apply(Temporal arg1, Temporal arg2, int flags) {
        if (arg1 == null) {
          return new RelativeTime(NEXT_IMMEDIATE, arg2);
        }
        if (arg2 == null) {
          return arg1;
        }
        // Temporal arg2Next = arg2.next();
        // if (arg1 == null || arg2Next == null) { return arg2Next; }
        if (arg1 instanceof Time) {
          Time t = (Time) arg1;
          if (arg2 instanceof Duration) {
            return ((Duration) arg2).toTime(t, flags | RESOLVE_TO_FUTURE);
          } else {
            // TODO: flags?
            Temporal resolvedThis = arg2.resolve(t, RESOLVE_TO_FUTURE);
            if (resolvedThis != null) {
              if (resolvedThis instanceof Time) {
                if (((Time) resolvedThis).compareTo(t) <= 0) {
                  return NEXT.apply(arg1, arg2);
                }
              }
            }
            return resolvedThis;
          }
        } else {
          throw new UnsupportedOperationException("NEXT_IMMEDIATE not implemented for arg1=" + arg1.getClass() + ", arg2=" + arg2.getClass());
        }
      }
    },
    // Use arg1 as reference to resolve arg2 (take more general fields from arg1
    // and apply to arg2)
    THIS {
      @Override
      public Temporal apply(Temporal arg1, Temporal arg2, int flags) {
        if (arg1 == null) {
          return new RelativeTime(THIS, arg2, flags);
        }
        if (arg1 instanceof Time) {
          if (arg2 instanceof Duration) {
            return ((Duration) arg2).toTime((Time) arg1, flags);
          } else {
            // TODO: flags?
            return arg2.resolve((Time) arg1, flags | RESOLVE_TO_THIS);
          }
        } else {
          throw new UnsupportedOperationException("THIS not implemented for arg1=" + arg1.getClass() + ", arg2=" + arg2.getClass());
        }
      }
    },
    // (predecessor) Previous week/day/...
    PREV {
      @Override
      public Temporal apply(Temporal arg1, Temporal arg2, int flags) {
        if (arg2 == null) {
          return arg1;
        }
        Temporal arg2Prev = arg2.prev();
        if (arg1 == null || arg2Prev == null) {
          return arg2Prev;
        }
        if (arg1 instanceof Time) {
          // TODO: flags?
          Temporal resolved = arg2Prev.resolve((Time) arg1, 0 /*RESOLVE_TO_PAST */);
          return resolved;
        } else {
          throw new UnsupportedOperationException("PREV not implemented for arg1=" + arg1.getClass() + ", arg2=" + arg2.getClass());
        }
      }
    },
    // This past week/friday
    PREV_IMMEDIATE {
      @Override
      public Temporal apply(Temporal arg1, Temporal arg2, int flags) {
        if (arg1 == null) {
          return new RelativeTime(PREV_IMMEDIATE, arg2);
        }
        if (arg2 == null) {
          return arg1;
        }
        // Temporal arg2Prev = arg2.prev();
        // if (arg1 == null || arg2Prev == null) { return arg2Prev; }
        if (arg1 instanceof Time) {
          Time t = (Time) arg1;
          if (arg2 instanceof Duration) {
            return ((Duration) arg2).toTime(t, flags | RESOLVE_TO_PAST);
          } else {
            // TODO: flags?
            Temporal resolvedThis = arg2.resolve(t, RESOLVE_TO_PAST);
            if (resolvedThis != null) {
              if (resolvedThis instanceof Time) {
                if (((Time) resolvedThis).compareTo(t) >= 0) {
                  return PREV.apply(arg1, arg2);
                }
              }
            }
            return resolvedThis;
          }
        } else {
          throw new UnsupportedOperationException("PREV_IMMEDIATE not implemented for arg1=" + arg1.getClass() + ", arg2=" + arg2.getClass());
        }
      }
    },
    UNION {
      @Override
      public Temporal apply(Temporal arg1, Temporal arg2, int flags) {
        if (arg1 == null) {
          return arg2;
        }
        if (arg2 == null) {
          return arg1;
        }
        // return arg1.union(arg2);
        throw new UnsupportedOperationException("UNION not implemented for arg1=" + arg1.getClass() + ", arg2=" + arg2.getClass());
      }
    },
    INTERSECT {
      @Override
      public Temporal apply(Temporal arg1, Temporal arg2, int flags) {
        if (arg1 == null) {
          return arg2;
        }
        if (arg2 == null) {
          return arg1;
        }
        Temporal t = arg1.intersect(arg2);
        if (t == null) {
          t = arg2.intersect(arg1);
        }
        return t;
        // throw new
        // UnsupportedOperationException("INTERSECT not implemented for arg1=" +
        // arg1.getClass() + ", arg2="+arg2.getClass());
      }
    },
    // arg2 is "in" arg1, composite datetime
    IN {
      @Override
      public Temporal apply(Temporal arg1, Temporal arg2, int flags) {
        if (arg1 == null) {
          return arg2;
        }
        if (arg1 instanceof Time) {
          // TODO: flags?
          return arg2.intersect(arg1);
        } else {
          throw new UnsupportedOperationException("IN not implemented for arg1=" + arg1.getClass() + ", arg2=" + arg2.getClass());
        }
      }
    },
    OFFSET {
      // There is inexact offset where we remove anything from the result that is more granular than the duration
      @Override
      public Temporal apply(Temporal arg1, Temporal arg2, int flags) {
        if (arg1 == null) {
          return new RelativeTime(OFFSET, arg2);
        }
        if (arg1 instanceof Time && arg2 instanceof Duration) {
          return ((Time) arg1).offset((Duration) arg2, flags | RELATIVE_OFFSET_INEXACT);
        } else if (arg1 instanceof Range && arg2 instanceof Duration) {
          return ((Range) arg1).offset((Duration) arg2, flags | RELATIVE_OFFSET_INEXACT);
        } else {
          throw new UnsupportedOperationException("OFFSET not implemented for arg1=" + arg1.getClass() + ", arg2=" + arg2.getClass());
        }
      }
    },
    MINUS {
      @Override
      public Temporal apply(Temporal arg1, Temporal arg2, int flags) {
        if (arg1 == null) {
          return arg2;
        }
        if (arg2 == null) {
          return arg1;
        }
        if (arg1 instanceof Duration && arg2 instanceof Duration) {
          return ((Duration) arg1).subtract((Duration) arg2);
        } else if (arg1 instanceof Time && arg2 instanceof Duration) {
          return ((Time) arg1).subtract((Duration) arg2);
        } else if (arg1 instanceof Range && arg2 instanceof Duration) {
          return ((Range) arg1).subtract((Duration) arg2);
        } else {
          throw new UnsupportedOperationException("MINUS not implemented for arg1=" + arg1.getClass() + ", arg2=" + arg2.getClass());
        }
      }
    },
    PLUS {
      @Override
      public Temporal apply(Temporal arg1, Temporal arg2, int flags) {
        if (arg1 == null) {
          return arg2;
        }
        if (arg2 == null) {
          return arg1;
        }
        if (arg1 instanceof Duration && arg2 instanceof Duration) {
          return ((Duration) arg1).add((Duration) arg2);
        } else if (arg1 instanceof Time && arg2 instanceof Duration) {
          return ((Time) arg1).add((Duration) arg2);
        } else if (arg1 instanceof Range && arg2 instanceof Duration) {
          return ((Range) arg1).add((Duration) arg2);
        } else {
          throw new UnsupportedOperationException("PLUS not implemented for arg1=" + arg1.getClass() + ", arg2=" + arg2.getClass());
        }
      }
    },
    MIN {
      @Override
      public Temporal apply(Temporal arg1, Temporal arg2, int flags) {
        if (arg1 == null) {
          return arg2;
        }
        if (arg2 == null) {
          return arg1;
        }
        if (arg1 instanceof Time && arg2 instanceof Time) {
          return Time.min((Time) arg1, (Time) arg2);
        } else if (arg1 instanceof Duration && arg2 instanceof Duration) {
          return Duration.min((Duration) arg1, (Duration) arg2);
        } else {
          throw new UnsupportedOperationException("MIN not implemented for arg1=" + arg1.getClass() + ", arg2=" + arg2.getClass());
        }
      }
    },
    MAX {
      @Override
      public Temporal apply(Temporal arg1, Temporal arg2, int flags) {
        if (arg1 == null) {
          return arg2;
        }
        if (arg2 == null) {
          return arg1;
        }
        if (arg1 instanceof Time && arg2 instanceof Time) {
          return Time.max((Time) arg1, (Time) arg2);
        } else if (arg1 instanceof Duration && arg2 instanceof Duration) {
          return Duration.max((Duration) arg1, (Duration) arg2);
        } else {
          throw new UnsupportedOperationException("MAX not implemented for arg1=" + arg1.getClass() + ", arg2=" + arg2.getClass());
        }
      }
    },
    MULTIPLY {
      public Temporal apply(Duration d, int scale) {
        if (d == null)
          return null;
        if (scale == 1) return d;
        return d.multiplyBy(scale);
      }

      public Temporal apply(PeriodicTemporalSet d, int scale) {
        if (d == null)
          return null;
        if (scale == 1) return d;
        return d.multiplyDurationBy(scale);
      }

      @Override
      public Temporal apply(Object... args) {
        if (args.length == 2) {
          if (args[0] instanceof Duration && (args[1] instanceof Integer || args[1] instanceof Long)) {
            return apply((Duration) args[0], ((Number) args[1]).intValue());
          }
          if (args[0] instanceof PeriodicTemporalSet && (args[1] instanceof Integer || args[1] instanceof Long)) {
            return apply((PeriodicTemporalSet) args[0], ((Number) args[1]).intValue());
          }
        }
        throw new UnsupportedOperationException("apply(Object...) not implemented for TemporalOp " + this);
      }
    },
    DIVIDE {
      public Temporal apply(Duration d, int scale) {
        if (d == null)
          return null;
        if (scale == 1) return d;
        return d.divideBy(scale);
      }
      public Temporal apply(PeriodicTemporalSet d, int scale) {
        if (d == null)
          return null;
        if (scale == 1) return d;
        return d.divideDurationBy(scale);
      }

      @Override
      public Temporal apply(Object... args) {
        if (args.length == 2) {
          if (args[0] instanceof Duration && (args[1] instanceof Integer || args[1] instanceof Long)) {
            return apply((Duration) args[0], ((Number) args[1]).intValue());
          }
          if (args[0] instanceof PeriodicTemporalSet && (args[1] instanceof Integer || args[1] instanceof Long)) {
            return apply((PeriodicTemporalSet) args[0], ((Number) args[1]).intValue());
          }
        }
        throw new UnsupportedOperationException("apply(Object...) not implemented for TemporalOp " + this);
      }
    },
    CREATE {
      public Temporal apply(TimeUnit tu, int n) {
        return tu.createTemporal(n);
      }

      @Override
      public Temporal apply(Object... args) {
        if (args.length == 2) {
          if (args[0] instanceof TimeUnit && args[1] instanceof Number) {
            return apply((TimeUnit) args[0], ((Number) args[1]).intValue());
          }
          else if (args[0] instanceof StandardTemporalType && args[1] instanceof Number) {
            return ((StandardTemporalType) args[0]).createTemporal(((Number) args[1]).intValue());
          }
          else if (args[0] instanceof Temporal && args[1] instanceof Number) {
            return new OrdinalTime((Temporal) args[0], ((Number) args[1]).intValue());
          }
        }
        throw new UnsupportedOperationException("apply(Object...) not implemented for TemporalOp " + this);
      }
    },
    ADD_MODIFIER {
      public Temporal apply(Temporal t, String modifier) {
        return t.addMod(modifier);
      }

      @Override
      public Temporal apply(Object... args) {
        if (args.length == 2) {
          if (args[0] instanceof Temporal && args[1] instanceof String) {
            return apply((Temporal) args[0], (String) args[1]);
          }
        }
        throw new UnsupportedOperationException("apply(Object...) not implemented for TemporalOp " + this);
      }
    },
    OFFSET_EXACT {
      // There is exact offset (more granular parts than the duration are kept)
      @Override
      public Temporal apply(Temporal arg1, Temporal arg2, int flags) {
        if (arg1 == null) {
          return new RelativeTime(OFFSET_EXACT, arg2);
        }
        if (arg1 instanceof Time && arg2 instanceof Duration) {
          return ((Time) arg1).offset((Duration) arg2, flags);
        } else if (arg1 instanceof Range && arg2 instanceof Duration) {
          return ((Range) arg1).offset((Duration) arg2, flags);
        } else {
          throw new UnsupportedOperationException("OFFSET_EXACT not implemented for arg1=" + arg1.getClass() + ", arg2=" + arg2.getClass());
        }
      }
    };


    public Temporal apply(Temporal arg1, Temporal arg2, int flags) {
      throw new UnsupportedOperationException("apply(Temporal, Temporal, int) not implemented for TemporalOp " + this);
    }

    public Temporal apply(Temporal arg1, Temporal arg2) {
      return apply(arg1, arg2, 0);
    }

    public Temporal apply(Temporal... args) {
      if (args.length == 2) {
        return apply(args[0], args[1]);
      }
      throw new UnsupportedOperationException("apply(Temporal...) not implemented for TemporalOp " + this);
    }

    public Temporal apply(Object... args) {
      throw new UnsupportedOperationException("apply(Object...) not implemented for TemporalOp " + this);
    }
  }

  /**
   * Time represents a time point on some time scale.
   * It is the base class for representing various types of time points.
   * Typically, since most time scales have marks with certain granularity
   *   each time point can be represented as an interval.
   */
  public abstract static class Time extends Temporal implements FuzzyInterval.FuzzyComparable<Time>, HasInterval<Time> {

    public Time() {
    }

    public Time(Time t) {
      super(t); /*this.hasTime = t.hasTime; */
    }

    // Represents a point in time - there is typically some
    // uncertainty/imprecision in the exact time
    @Override
    public boolean isGrounded() {
      return false;
    }

    // A time is defined by a begin and end point, and a duration
    @Override
    public Time getTime() {
      return this;
    }

    // Default is a instant in time with same begin and end point
    // Every time should return a non-null range
    @Override
    public Range getRange(int flags, Duration granularity) {
      return new Range(this, this);
    }

    // Default duration is zero
    @Override
    public Duration getDuration() {
      return DURATION_NONE;
    }

    @Override
    public Duration getGranularity() {
      StandardTemporalType tlt = getStandardTemporalType();
      if (tlt != null) {
        return tlt.getGranularity();
      }
      Partial p = this.getJodaTimePartial();
      return Duration.getDuration(JodaTimeUtils.getJodaTimePeriod(p));
    }

    @Override
    public Interval<Time> getInterval() {
      Range r = getRange();
      if (r != null) {
        return r.getInterval();
      } else
        return null;
    }

    @Override
    public boolean isComparable(Time t) {
      Instant i = this.getJodaTimeInstant();
      Instant i2 = t.getJodaTimeInstant();
      return (i != null && i2 != null);
    }

    @Override
    public int compareTo(Time t) {
      Instant i = this.getJodaTimeInstant();
      Instant i2 = t.getJodaTimeInstant();
      return i.compareTo(i2);
    }

    public boolean hasTime() {
      return false;
    }

    @Override
    public TimexType getTimexType() {
      if (getStandardTemporalType() != null) {
        return getStandardTemporalType().getTimexType();
      }
      return (hasTime()) ? TimexType.TIME : TimexType.DATE;
    }

    // Time operations
    public boolean contains(Time t) {
      // Check if this time contains other time
      return getRange().contains(t.getRange());
    }

    // public boolean isBefore(Time t);
    // public boolean isAfter(Time t);
    // public boolean overlaps(Time t);
    public Time reduceGranularityTo(Duration d) {
      return this;
    }

    // Add duration to time
    public abstract Time add(Duration offset);

    public Time offset(Duration offset, int flags) {
      Time res = add(offset);
      if ((flags & RELATIVE_OFFSET_INEXACT) != 0) {
        // Mark as uncertain anything not as granular as the granularity of the offset
        res.uncertaintyGranularity = offset.getGranularity();
        return res;
      } else {
        return res;
      }
    }

    public Time subtract(Duration offset) {
      return add(offset.multiplyBy(-1));
    }

    // Return closest time
    public static Time closest(Time ref, Time... times) {
      Time res = null;
      long refMillis = ref.getJodaTimeInstant().getMillis();
      long min = 0;
      for (Time t:times) {
        long d = Math.abs(refMillis - t.getJodaTimeInstant().getMillis());
        if (res == null || d < min) {
          res = t;
          min = d;
        }
      }
      return res;
    }

    // Get absolute difference between times
    public static Duration distance(Time t1, Time t2) {
      if (t1.compareTo(t2) < 0) {
        return difference(t1,t2);
      } else {
        return difference(t2,t1);
      }
    }

    // Get difference between times
    public static Duration difference(Time t1, Time t2) {
      // TODO: Difference does not work between days of the week
      // Get duration from this t1 to t2
      if (t1 == null || t2 == null)
        return null;
      Instant i1 = t1.getJodaTimeInstant();
      Instant i2 = t2.getJodaTimeInstant();
      if (i1 == null || i2 == null)
        return null;
      Duration d = new DurationWithMillis(i2.getMillis() - i1.getMillis());
      Duration g1 = t1.getGranularity();
      Duration g2 = t2.getGranularity();
      Duration g = Duration.max(g1, g2);
      if (g != null) {
        Period p = g.getJodaTimePeriod();
        p = p.normalizedStandard();
        Period p2 = JodaTimeUtils.discardMoreSpecificFields(d.getJodaTimePeriod(), p.getFieldType(p.size() - 1), i1.getChronology());
        return new DurationWithFields(p2);
      } else {
        return d;
      }
    }

    public static CompositePartialTime makeComposite(PartialTime pt, Time t) {
      CompositePartialTime cp = null;
      StandardTemporalType tlt = t.getStandardTemporalType();
      if (tlt != null) {
        switch (tlt) {
        case TIME_OF_DAY:
          cp = new CompositePartialTime(pt, null, null, t);
          break;
        case PART_OF_YEAR:
        case QUARTER_OF_YEAR:
        case SEASON_OF_YEAR:
          cp = new CompositePartialTime(pt, t, null, null);
          break;
        case DAYS_OF_WEEK:
          cp = new CompositePartialTime(pt, null, t, null);
          break;
        }
      }
      return cp;
    }

    @Override
    public Temporal resolve(Time t, int flags) {
      return this;
    }

    @Override
    public Temporal intersect(Temporal t) {
      if (t == null)
        return this;
      if (t == TIME_UNKNOWN || t == DURATION_UNKNOWN)
        return this;
      if (t instanceof Time) {
        return intersect((Time) t);
      } else if (t instanceof Range) {
        return t.intersect(this);
      } else if (t instanceof Duration) {
        return new RelativeTime(this, TemporalOp.INTERSECT, t);
      }
      return null;
    }

    protected Time intersect(Time t) {
      return null; //new RelativeTime(this, TemporalOp.INTERSECT, t);
    }

    protected static Time intersect(Time t1, Time t2) {
      if (t1 == null)
        return t2;
      if (t2 == null)
        return t1;
      return t1.intersect(t2);
    }

    public static Time min(Time t1, Time t2) {
      if (t2 == null)
        return t1;
      if (t1 == null)
        return t2;
      if (t1.isComparable(t2)) {
        int c = t1.compareTo(t2);
        return (c < 0) ? t1 : t2;
      }
      return t1;
    }

    public static Time max(Time t1, Time t2) {
      if (t1 == null)
        return t2;
      if (t2 == null)
        return t1;
      if (t1.isComparable(t2)) {
        int c = t1.compareTo(t2);
        return (c >= 0) ? t1 : t2;
      }
      return t2;
    }

    // Conversions to joda time
    public Instant getJodaTimeInstant() {
      return null;
    }

    public Partial getJodaTimePartial() {
      return null;
    }

    private static final long serialVersionUID = 1;
  }

  /** Reference time (some kind of reference time). */
  public static class RefTime extends Time {
    String label;

    public RefTime(String label) {
      this.label = label;
    }

    public RefTime(StandardTemporalType timeType, String timeLabel, String label) {
      this.standardTemporalType = timeType;
      this.timeLabel = timeLabel;
      this.label = label;
    }

    @Override
    public boolean isRef() {
      return true;
    }

    @Override
    public String toFormattedString(int flags) {
      if (getTimeLabel() != null) {
        return getTimeLabel();
      }
      if ((flags & FORMAT_ISO) != 0) {
        return null;
      } // TODO: is there iso standard?
      return label;
    }

    @Override
    public Time add(Duration offset) {
      return new RelativeTime(this, TemporalOp.OFFSET_EXACT, offset);
    }

    @Override
    public Time offset(Duration offset, int offsetFlags) {
      if ((offsetFlags & RELATIVE_OFFSET_INEXACT) != 0) {
        return new RelativeTime(this, TemporalOp.OFFSET, offset);
      } else {
        return new RelativeTime(this, TemporalOp.OFFSET_EXACT, offset);
      }
    }

    @Override
    public Time resolve(Time refTime, int flags) {
      if (this == TIME_REF) {
        return refTime;
      } else if (this == TIME_NOW && (flags & RESOLVE_NOW) != 0) {
        return refTime;
      } else {
        return this;
      }
    }

    private static final long serialVersionUID = 1;
  }

  /**
   * Simple time (vague time that we don't really know what to do with)
   **/
  public static class SimpleTime extends Time {
    String label;

    public SimpleTime(String label) {
      this.label = label;
    }

    @Override
    public String toFormattedString(int flags) {
      if (getTimeLabel() != null) {
        return getTimeLabel();
      }
      if ((flags & FORMAT_ISO) != 0) {
        return null;
      } // TODO: is there iso standard?
      return label;
    }

    @Override
    public Time add(Duration offset) {
      Time t = new RelativeTime(this, TemporalOp.OFFSET_EXACT, offset);
      // t.approx = this.approx;
      // t.mod = this.mod;
      return t;
    }

    private static final long serialVersionUID = 1;
  }

  // Composite time - like PartialTime but with more, approximate fields
  public static class CompositePartialTime extends PartialTime {
    // Summer weekend morning in June
    Time tod; // Time of day
    Time dow; // Day of week
    Time poy; // Part of year

    // Duration duration; // Underspecified time (like day in June)

    public CompositePartialTime(PartialTime t, Time poy, Time dow, Time tod) {
      super(t);
      this.poy = poy;
      this.dow = dow;
      this.tod = tod;
    }

    public CompositePartialTime(PartialTime t, Partial p, Time poy, Time dow, Time tod) {
      this(t, poy, dow, tod);
      this.base = p;
    }

    @Override
    public Instant getJodaTimeInstant() {
      Partial p = base;
      if (tod != null) {
        Partial p2 = tod.getJodaTimePartial();
        if (p2 != null && JodaTimeUtils.isCompatible(p, p2)) {
          p = JodaTimeUtils.combine(p, p2);
        }
      }
      if (dow != null) {
        Partial p2 = dow.getJodaTimePartial();
        if (p2 != null && JodaTimeUtils.isCompatible(p, p2)) {
          p = JodaTimeUtils.combine(p, p2);
        }
      }
      if (poy != null) {
        Partial p2 = poy.getJodaTimePartial();
        if (p2 != null && JodaTimeUtils.isCompatible(p, p2)) {
          p = JodaTimeUtils.combine(p, p2);
        }
      }
      return JodaTimeUtils.getInstant(p);
    }

    @Override
    public Duration getDuration() {
/*      TimeLabel tl = getTimeLabel();
      if (tl != null) {
        return tl.getDuration();
      } */
      StandardTemporalType tlt = getStandardTemporalType();
      if (tlt != null) {
        return tlt.getDuration();
      }

      Duration bd = (base != null) ? Duration.getDuration(JodaTimeUtils.getJodaTimePeriod(base)) : null;
      if (bd != null) {
        if (tod != null) {
          Duration d = tod.getDuration();
          return (bd.compareTo(d) < 0) ? bd : d;
        }
        if (dow != null) {
          Duration d = dow.getDuration();
          return (bd.compareTo(d) < 0) ? bd : d;
        }
        if (poy != null) {
          Duration d = poy.getDuration();
          return (bd.compareTo(d) < 0) ? bd : d;
        }
      }
      return bd;
    }

    @Override
    public Duration getPeriod() {
  /*    TimeLabel tl = getTimeLabel();
      if (tl != null) {
        return tl.getPeriod();
      } */
      StandardTemporalType tlt = getStandardTemporalType();
      if (tlt != null) {
        return tlt.getPeriod();
      }

      Duration bd = null;
      if (base != null) {
        DateTimeFieldType mostGeneral = JodaTimeUtils.getMostGeneral(base);
        DurationFieldType df = mostGeneral.getRangeDurationType();
        if (df == null) {
          df = mostGeneral.getDurationType();
        }
        if (df != null) {
          bd = new DurationWithFields(new Period().withField(df, 1));
        }
      }

      if (bd != null) {
        if (poy != null) {
          Duration d = poy.getPeriod();
          return (bd.compareTo(d) > 0) ? bd : d;
        }
        if (dow != null) {
          Duration d = dow.getPeriod();
          return (bd.compareTo(d) > 0) ? bd : d;
        }
        if (tod != null) {
          Duration d = tod.getPeriod();
          return (bd.compareTo(d) > 0) ? bd : d;
        }
      }
      return bd;
    }

    private static Range getIntersectedRange(CompositePartialTime cpt, Range r, Duration d) {
      Time beginTime = r.beginTime();
      Time endTime = r.endTime();
      if (beginTime != TIME_UNKNOWN && endTime != TIME_UNKNOWN) {
        Time t1 = cpt.intersect(r.beginTime());
        if (t1 instanceof PartialTime) {
          ((PartialTime) t1).withStandardFields();
        }
        Time t2 = cpt.intersect(r.endTime());
        if (t2 instanceof PartialTime) {
          ((PartialTime) t2).withStandardFields();
        }
        return new Range(t1, t2, d);
      } else if (beginTime != TIME_UNKNOWN && endTime == TIME_UNKNOWN) {
        Time t1 = cpt.intersect(r.beginTime());
        if (t1 instanceof PartialTime) {
          ((PartialTime) t1).withStandardFields();
        }
        Time t2 = t1.add(d);
        if (t2 instanceof PartialTime) {
          ((PartialTime) t2).withStandardFields();
        }
        return new Range(t1, t2, d);
      } else {
        throw new RuntimeException("Unsupported range: " + r);
      }
    }

    @Override
    public Range getRange(int flags, Duration granularity) {
      Duration d = getDuration();
      if (tod != null) {
        Range r = tod.getRange(flags, granularity);
        if (r != null) {
          CompositePartialTime cpt = new CompositePartialTime(this, poy, dow, null);
          return getIntersectedRange(cpt, r, d);
        } else {
          return super.getRange(flags, granularity);
        }
      }
      if (dow != null) {
        Range r = dow.getRange(flags, granularity);
        if (r != null) {
          CompositePartialTime cpt = new CompositePartialTime(this, poy, dow, null);
          return getIntersectedRange(cpt, r, d);
        } else {
          return super.getRange(flags, granularity);
        }
      }
      if (poy != null) {
        Range r = poy.getRange(flags, granularity);
        if (r != null) {
          CompositePartialTime cpt = new CompositePartialTime(this, poy, null, null);
          return getIntersectedRange(cpt, r, d);
        } else {
          return super.getRange(flags, granularity);
        }
      }
      return super.getRange(flags, granularity);
    }

    @Override
    public Time intersect(Time t) {
      if (t == null || t == TIME_UNKNOWN)
        return this;
      if (base == null)
        return t;
      if (t instanceof PartialTime) {
        Pair<PartialTime,PartialTime> compatible = getCompatible(this, (PartialTime) t);
        if (compatible == null) {
          return null;
        }
        Partial p = JodaTimeUtils.combine(compatible.first.base, compatible.second.base);
        if (t instanceof CompositePartialTime) {
          CompositePartialTime cpt = (CompositePartialTime) t;
          Time ntod = Time.intersect(tod, cpt.tod);
          Time ndow = Time.intersect(dow, cpt.dow);
          Time npoy = Time.intersect(poy, cpt.poy);
          if (ntod == null && (tod != null || cpt.tod != null))
            return null;
          if (ndow == null && (dow != null || cpt.dow != null))
            return null;
          if (npoy == null && (poy != null || cpt.poy != null))
            return null;
          return new CompositePartialTime(this, p, npoy, ndow, ntod);
        } else {
          return new CompositePartialTime(this, p, poy, dow, tod);
        }
      } else {
        return super.intersect(t);
      }
    }

    @Override
    protected PartialTime addSupported(Period p, int scalar) {
      return new CompositePartialTime(this, base.withPeriodAdded(p, 1), poy, dow, tod);
    }

    @Override
    protected PartialTime addUnsupported(Period p, int scalar) {
      return new CompositePartialTime(this, JodaTimeUtils.addForce(base, p, scalar), poy, dow, tod);
    }

    @Override
    public PartialTime reduceGranularityTo(Duration granularity) {
      Partial p = JodaTimeUtils.discardMoreSpecificFields( base,
        JodaTimeUtils.getMostSpecific(granularity.getJodaTimePeriod()) );
      return new CompositePartialTime(this, p,
        poy.reduceGranularityTo(granularity),
        dow.reduceGranularityTo(granularity),
        tod.reduceGranularityTo(granularity));
    }

    @Override
    public Time resolve(Time ref, int flags) {
      if (ref == null || ref == TIME_UNKNOWN || ref == TIME_REF) {
        return this;
      }
      if (this == TIME_REF) {
        return ref;
      }
      if (this == TIME_UNKNOWN) {
        return this;
      }
      Partial partialRef = ref.getJodaTimePartial();
      if (partialRef == null) {
        throw new UnsupportedOperationException("Cannot resolve if reftime is of class: " + ref.getClass());
      }
      DateTimeFieldType mgf = null;
      if (poy != null)
        mgf = JodaTimeUtils.QuarterOfYear;
      else if (dow != null)
        mgf = DateTimeFieldType.dayOfWeek();
      else if (tod != null)
        mgf = DateTimeFieldType.halfdayOfDay();
      Partial p = (base != null) ? JodaTimeUtils.combineMoreGeneralFields(base, partialRef, mgf) : partialRef;
      if (p.isSupported(DateTimeFieldType.dayOfWeek())) {
        p = JodaTimeUtils.resolveDowToDay(p, partialRef);
      } else if (dow != null) {
        p = JodaTimeUtils.resolveWeek(p, partialRef);
      }
      if (p == base) {
        return this;
      } else {
        return new CompositePartialTime(this, p, poy, dow, tod);
      }
    }

    @Override
    public DateTimeFormatter getFormatter(int flags) {
      DateTimeFormatterBuilder builder = new DateTimeFormatterBuilder();
      boolean hasDate = appendDateFormats(builder, flags);
      if (poy != null) {
        if (!JodaTimeUtils.hasField(base, DateTimeFieldType.monthOfYear())) {
          // Assume poy is compatible with whatever was built and
          // poy.toISOString() does the correct thing
          builder.appendLiteral("-");
          builder.appendLiteral(poy.toISOString());
          hasDate = true;
        }
      }
      if (dow != null) {
        if (!JodaTimeUtils.hasField(base, DateTimeFieldType.monthOfYear()) && !JodaTimeUtils.hasField(base, DateTimeFieldType.dayOfWeek())) {
          builder.appendLiteral("-");
          builder.appendLiteral(dow.toISOString());
          hasDate = true;
        }
      }
      if (hasTime()) {
        if (!hasDate) {
          builder.clear();
        }
        appendTimeFormats(builder, flags);
      } else if (tod != null) {
        if (!hasDate) {
          builder.clear();
        }
        // Assume tod is compatible with whatever was built and
        // tod.toISOString() does the correct thing
        builder.appendLiteral("T");
        builder.appendLiteral(tod.toISOString());
      }
      return builder.toFormatter();
    }

    @Override
    public TimexType getTimexType() {
      if (tod != null) return TimexType.TIME;
      return super.getTimexType();
    }

    private static final long serialVersionUID = 1;
  }

  /** The nth temporal.
   *  Example: The tenth week (of something, don't know yet)
   * The second friday
   */
  public static class OrdinalTime extends Time {
    Temporal base;
    int n;

    public OrdinalTime(Temporal base, int n) {
      this.base = base;
      this.n = n;
    }

    public OrdinalTime(Temporal base, long n) {
      this.base = base;
      this.n = (int) n;
    }

    @Override
    public Time add(Duration offset) {
      return new RelativeTime(this, TemporalOp.OFFSET_EXACT, offset);
    }

    @Override
    public String toFormattedString(int flags) {
      if (getTimeLabel() != null) {
        return getTimeLabel();
      }
      if ((flags & FORMAT_ISO) != 0) {
        return null;
      } // TODO: is there iso standard?
      if ((flags & FORMAT_TIMEX3_VALUE) != 0) {
        return null;
      } // TODO: is there timex3 standard?
      if (base != null) {
        String str = base.toFormattedString(flags);
        if (str != null) {
          return str + "-#" + n;
        }
      }
      return null;
    }

    @Override
    public Time intersect(Time t) {
      if (base instanceof PartialTime && t instanceof PartialTime) {
        return new OrdinalTime(base.intersect(t), n);
      } else {
        return new RelativeTime(t, TemporalOp.INTERSECT, this);
      }
    }

    @Override
    public Temporal resolve(Time t, int flags) {
      if (t == null) return this; // No resolving to be done?
      if (base instanceof PartialTime) {
        PartialTime pt = (PartialTime) base.resolve(t,flags);
        List<Temporal> list = pt.toList();
        if (list != null && list.size() >= n) {
          return list.get(n-1);
        }
      } else if (base instanceof Duration) {
        Duration d = ((Duration) base).multiplyBy(n-1);
        Time temp = t.getRange().begin();
        return temp.offset(d,0).reduceGranularityTo(d.getDuration());
      }
      return this;
    }

    private static final long serialVersionUID = 1;

  } // end static class OrdinalTim


  // Time with a range (most times have a range...)
  public static class TimeWithRange extends Time {
    Range range; // guess at range

    public TimeWithRange(TimeWithRange t, Range range) {
      super(t);
      this.range = range;
    }

    public TimeWithRange(Range range) {
      this.range = range;
    }

    @Override
    public TimeWithRange setTimeZone(DateTimeZone tz) {
      return new TimeWithRange(this, (Range) Temporal.setTimeZone(range, tz));
    }

    @Override
    public Duration getDuration() {
      if (range != null)
        return range.getDuration();
      else
        return null;
    }

    @Override
    public Range getRange(int flags, Duration granularity) {
      if (range != null) {
        return range.getRange(flags, granularity);
      } else {
        return null;
      }
    }

    @Override
    public Time add(Duration offset) {
      // TODO: Check logic
//      if (getTimeLabel() != null) {
        if (getStandardTemporalType() != null) {
        // Time has some meaning, keep as is
        return new RelativeTime(this, TemporalOp.OFFSET_EXACT, offset);
      } else
        return new TimeWithRange(this, range.offset(offset,0));
    }

    @Override
    public Time intersect(Time t) {
      if (t == null || t == TIME_UNKNOWN)
        return this;
      if (t instanceof CompositePartialTime) {
        return t.intersect(this);
      } else if (t instanceof PartialTime) {
        return t.intersect(this);
      } else if (t instanceof GroundedTime) {
        return t.intersect(this);
      } else {
        return new TimeWithRange((Range) range.intersect(t));
      }
    }

    @Override
    public Time resolve(Time refTime, int flags) {
      CompositePartialTime cpt = makeComposite(new PartialTime(new Partial()), this);
      if (cpt != null) {
        return cpt.resolve(refTime, flags);
      }
      Range groundedRange = null;
      if (range != null) {
        groundedRange = range.resolve(refTime, flags).getRange();
      }
      return createTemporal(standardTemporalType, timeLabel, new TimeWithRange(this, groundedRange));
      //return new TimeWithRange(this, groundedRange);
    }

    @Override
    public String toFormattedString(int flags) {
      if (getTimeLabel() != null) {
        return getTimeLabel();
      }
      if ((flags & FORMAT_TIMEX3_VALUE) != 0) {
        flags |= FORMAT_ISO;
      }
      return range.toFormattedString(flags);
    }

    private static final long serialVersionUID = 1;
  }

  /**
   * Inexact time, not sure when this is, but have some guesses.
   */
  public static class InexactTime extends Time {
    Time base; // best guess
    Duration duration; // how long the time lasts
    Range range; // guess at range in which the time occurs

    public InexactTime(Partial partial) {
      this.base = new PartialTime(partial);
      this.range = base.getRange();
      this.approx = true;
    }

    public InexactTime(Time base, Duration duration, Range range) {
      this.base = base;
      this.duration = duration;
      this.range = range;
      this.approx = true;
    }

    public InexactTime(Time base, Range range) {
      this.base = base;
      this.range = range;
      this.approx = true;
    }

    public InexactTime(InexactTime t, Time base, Duration duration, Range range) {
      super(t);
      this.base = base;
      this.duration = duration;
      this.range = range;
      this.approx = true;
    }

    public InexactTime(Range range) {
      this.base = range.mid();
      this.range = range;
      this.approx = true;
    }

    @Override
    public int compareTo(Time t) {
      if (this.base != null) return (this.base.compareTo(t));
      if (this.range != null) {
        if (this.range.begin() != null && this.range.begin().compareTo(t) > 0) return 1;
        else if (this.range.end() != null &&  this.range.end().compareTo(t) < 0) return -1;
        else return this.range.getTime().compareTo(t);
      }
      return 0;
    }

    @Override
    public InexactTime setTimeZone(DateTimeZone tz) {
      return new InexactTime(this,
              (Time) Temporal.setTimeZone(base, tz), duration,
              (Range) Temporal.setTimeZone(range, tz));
    }

    @Override
    public Time getTime() {
      return this;
    }

    @Override
    public Duration getDuration() {
      if (duration != null)
        return duration;
      if (range != null)
        return range.getDuration();
      else if (base != null)
        return base.getDuration();
      else
        return null;
    }

    @Override
    public Range getRange(int flags, Duration granularity) {
      if (range != null) {
        return range.getRange(flags, granularity);
      } else if (base != null) {
        return base.getRange(flags, granularity);
      } else
        return null;
    }

    @Override
    public Time add(Duration offset) {
      //if (getTimeLabel() != null) {
      if (getStandardTemporalType() != null) {
        // Time has some meaning, keep as is
        return new RelativeTime(this, TemporalOp.OFFSET_EXACT, offset);
      } else {
        // Some other time, who know what it means
        // Try to do offset
        return new InexactTime(this, (Time) TemporalOp.OFFSET_EXACT.apply(base, offset), duration, (Range) TemporalOp.OFFSET_EXACT.apply(range, offset));
      }
    }

    @Override
    public Time resolve(Time refTime, int flags) {
      CompositePartialTime cpt = makeComposite(new PartialTime(this, new Partial()), this);
      if (cpt != null) {
        return cpt.resolve(refTime, flags);
      }
      Time groundedBase = null;
      if (base == TIME_REF) {
        groundedBase = refTime;
      } else if (base != null) {
        groundedBase = base.resolve(refTime, flags).getTime();
      }
      Range groundedRange = null;
      if (range != null) {
        groundedRange = range.resolve(refTime, flags).getRange();
      }
      /*    if (groundedRange == range && groundedBase == base) {
            return this;
          } */
      return createTemporal(standardTemporalType, timeLabel, mod, new InexactTime(groundedBase, duration, groundedRange));
      //return new InexactTime(groundedBase, duration, groundedRange);
    }

    @Override
    public Instant getJodaTimeInstant() {
      Instant p = null;
      if (base != null) {
        p = base.getJodaTimeInstant();
      }
      if (p == null && range != null) {
        p = range.mid().getJodaTimeInstant();
      }
      return p;
    }

    @Override
    public Partial getJodaTimePartial() {
      Partial p = null;
      if (base != null) {
        p = base.getJodaTimePartial();
      }
      if (p == null && range != null && range.mid() != null) {
        p = range.mid().getJodaTimePartial();
      }
      return p;
    }

    @Override
    public String toFormattedString(int flags) {
      if (getTimeLabel() != null) {
        return getTimeLabel();
      }

      if ((flags & FORMAT_ISO) != 0) {
        return null;
      } // TODO: is there iso standard?
      if ((flags & FORMAT_TIMEX3_VALUE) != 0) {
        return null;
      } // TODO: is there timex3 standard?
      StringBuilder sb = new StringBuilder();
      sb.append("~(");
      if (base != null) {
        sb.append(base.toFormattedString(flags));
      }
      if (duration != null) {
        sb.append(":");
        sb.append(duration.toFormattedString(flags));
      }
      if (range != null) {
        sb.append(" IN ");
        sb.append(range.toFormattedString(flags));
      }
      sb.append(")");
      return sb.toString();
    }

    private static final long serialVersionUID = 1;
  }

  /** Relative Time (something not quite resolved). */
  public static class RelativeTime extends Time {

    private Time base = TIME_REF;
    private TemporalOp tempOp;
    private Temporal tempArg;
    private int opFlags;

    public RelativeTime(Time base, TemporalOp tempOp, Temporal tempArg, int flags) {
      super(base);
      this.base = base;
      this.tempOp = tempOp;
      this.tempArg = tempArg;
      this.opFlags = flags;
    }

    public RelativeTime(Time base, TemporalOp tempOp, Temporal tempArg) {
      super(base);
      this.base = base;
      this.tempOp = tempOp;
      this.tempArg = tempArg;
    }

    public RelativeTime(TemporalOp tempOp, Temporal tempArg) {
      this.tempOp = tempOp;
      this.tempArg = tempArg;
    }

    public RelativeTime(TemporalOp tempOp, Temporal tempArg, int flags) {
      this.tempOp = tempOp;
      this.tempArg = tempArg;
      this.opFlags = flags;
    }

    public RelativeTime(Duration offset) {
      this(TIME_REF, TemporalOp.OFFSET, offset);
    }

    public RelativeTime(Time base, Duration offset) {
      this(base, TemporalOp.OFFSET, offset);
    }

    public RelativeTime(Time base) {
      super(base);
      this.base = base;
    }

    public Time getBase() {
      return base;
    }

    public TemporalOp getTemporalOp() {
      return tempOp;
    }

    public Temporal getTemporalArg() {
      return tempArg;
    }

    public int getOpFlags() {
      return opFlags;
    }

    @Override
    public boolean isGrounded() {
      return (base != null) && base.isGrounded();
    }

    // TODO: compute duration/range => uncertainty of this time
    @Override
    public Duration getDuration() {
      return null;
    }

    @Override
    public Range getRange(int flags, Duration granularity) {
      return new Range(this, this);
    }

    @Override
    public Map<String, String> getTimexAttributes(TimeIndex timeIndex) {
      Map<String, String> map = super.getTimexAttributes(timeIndex);
      String tfid = getTfidString(timeIndex);
      map.put(TimexAttr.temporalFunction.name(), "true");
      map.put(TimexAttr.valueFromFunction.name(), tfid);
      if (base != null) {
        map.put(TimexAttr.anchorTimeID.name(), base.getTidString(timeIndex));
      }
      return map;
    }

    // / NOTE: This is not ISO or timex standard
    @Override
    public String toFormattedString(int flags) {
      if (getTimeLabel() != null) {
        return getTimeLabel();
      }
      if ((flags & FORMAT_ISO) != 0) {
        return null;
      } // TODO: is there iso standard?
      if ((flags & FORMAT_TIMEX3_VALUE) != 0) {
        return null;
      } // TODO: is there timex3 standard?
      StringBuilder sb = new StringBuilder();
      if (base != null && base != TIME_REF) {
        sb.append(base.toFormattedString(flags));
      }
      if (tempOp != null) {
        if (sb.length() > 0) {
          sb.append(" ");
        }
        sb.append(tempOp);
        if (tempArg != null) {
          sb.append(" ").append(tempArg.toFormattedString(flags));
        }
      }
      return sb.toString();
    }

    @Override
    public Temporal resolve(Time refTime, int flags) {
      Temporal groundedBase = null;
      if (base == TIME_REF) {
        groundedBase = refTime;
      } else if (base != null) {
        groundedBase = base.resolve(refTime, flags);
      }
      if (tempOp != null) {
        // NOTE: Should be always safe to resolve and then apply since
        // we will terminate here (no looping hopefully)
        Temporal t = tempOp.apply(groundedBase, tempArg, opFlags);
        if (t != null) {
          t = t.addModApprox(mod, approx);
          return t;
        } else {
          // NOTE: this can be difficult if applying op
          // gives back same stuff as before
          // Try applying op and then resolving
          t = tempOp.apply(base, tempArg, opFlags);
          if (t != null) {
            t = t.addModApprox(mod, approx);
            if (!this.equals(t)) {
              return t.resolve(refTime, flags);
            } else {
              // Applying op doesn't do much....
              return this;
            }
          } else {
            return null;
          }
        }
      } else {
        return (groundedBase != null) ? groundedBase.addModApprox(mod, approx) : null;
      }
    }

    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }

      RelativeTime that = (RelativeTime) o;

      if (opFlags != that.opFlags) {
        return false;
      }
      if (base != null ? !base.equals(that.base) : that.base != null) {
        return false;
      }
      if (tempArg != null ? !tempArg.equals(that.tempArg) : that.tempArg != null) {
        return false;
      }
      if (tempOp != that.tempOp) {
        return false;
      }

      return true;
    }

    public int hashCode() {
      int result = base != null ? base.hashCode() : 0;
      result = 31 * result + (tempOp != null ? tempOp.hashCode() : 0);
      result = 31 * result + (tempArg != null ? tempArg.hashCode() : 0);
      result = 31 * result + opFlags;
      return result;
    }

    @Override
    public Time add(Duration offset) {
      Time t;
      Duration d = offset;
      if (this.tempOp == null) {
        t = new RelativeTime(base, d);
        t.approx = this.approx;
        t.mod = this.mod;
      } else if (this.tempOp == TemporalOp.OFFSET) {
        d = ((Duration) this.tempArg).add(offset);
        t = new RelativeTime(base, d);
        t.approx = this.approx;
        t.mod = this.mod;
      } else {
        t = new RelativeTime(this, d);
      }
      return t;
    }

    @Override
    public Temporal intersect(Temporal t) {
      return new RelativeTime(this, TemporalOp.INTERSECT, t);
    }

    @Override
    public Time intersect(Time t) {
      if (base == TIME_REF || base == null) {
        if (t instanceof PartialTime && tempOp == TemporalOp.OFFSET) {
          RelativeTime rt = new RelativeTime(this, tempOp, tempArg);
          rt.base = t;
          return rt;
        }
      }
      return new RelativeTime(this, TemporalOp.INTERSECT, t);
    }

    private static final long serialVersionUID = 1;

  } // end static class RelativeTime

  // Partial time with Joda Time fields
  public static class PartialTime extends Time {
    // There is typically some uncertainty/imprecision in the time
    Partial base; // For representing partial absolute time
    DateTimeZone dateTimeZone; // Datetime zone associated with this time

    // private static DateTimeFormatter isoDateFormatter =
    // ISODateTimeFormat.basicDate();
    // private static DateTimeFormatter isoDateTimeFormatter =
    // ISODateTimeFormat.basicDateTimeNoMillis();
    // private static DateTimeFormatter isoTimeFormatter =
    // ISODateTimeFormat.basicTTimeNoMillis();
    // private static DateTimeFormatter isoDateFormatter =
    // ISODateTimeFormat.date();
    // private static DateTimeFormatter isoDateTimeFormatter =
    // ISODateTimeFormat.dateTimeNoMillis();
    // private static DateTimeFormatter isoTimeFormatter =
    // ISODateTimeFormat.tTimeNoMillis();

    public PartialTime(Time t, Partial p) {
      super(t);
      if (t instanceof PartialTime) {
        this.dateTimeZone = ((PartialTime) t).dateTimeZone;
      }
      this.base = p;
    }

    public PartialTime(PartialTime pt) {
      super(pt);
      this.dateTimeZone = pt.dateTimeZone;
      this.base = pt.base;
    }

    // public PartialTime(Partial base, String mod) { this.base = base; this.mod
    // = mod; }
    public PartialTime(Partial base) {
      this.base = base;
    }

    public PartialTime(StandardTemporalType temporalType, Partial base) {
      this.base = base;
      this.standardTemporalType = temporalType;
    }

    public PartialTime() {
    }

    @Override
    public PartialTime setTimeZone(DateTimeZone tz) {
      PartialTime tzPt = new PartialTime(this, base);
      tzPt.dateTimeZone = tz;
      return tzPt;
    }

    @Override
    public Instant getJodaTimeInstant() {
      return JodaTimeUtils.getInstant(base);
    }

    @Override
    public Partial getJodaTimePartial() {
      return base;
    }

    @Override
    public boolean hasTime() {
      if (base == null)
        return false;
      DateTimeFieldType sdft = JodaTimeUtils.getMostSpecific(base);
      if (sdft != null && JodaTimeUtils.isMoreGeneral(DateTimeFieldType.dayOfMonth(), sdft, base.getChronology())) {
        return true;
      } else {
        return false;
      }
    }

    @Override
    public TimexType getTimexType() {
      if (base == null) return null;
      return super.getTimexType();
    }

    protected boolean appendDateFormats(DateTimeFormatterBuilder builder, int flags) {
      boolean alwaysPad = ((flags & FORMAT_PAD_UNKNOWN) != 0);
      boolean hasDate = true;
      boolean isISO = ((flags & FORMAT_ISO) != 0);
      boolean isTimex3 = ((flags & FORMAT_TIMEX3_VALUE) != 0);
      // ERA
      if (JodaTimeUtils.hasField(base, DateTimeFieldType.era())) {
        int era = base.get(DateTimeFieldType.era());
        if (era == 0) {
          builder.appendLiteral('-');
        } else if (era == 1) {
          builder.appendLiteral('+');
        }
      }
      // YEAR
      if (JodaTimeUtils.hasField(base, DateTimeFieldType.centuryOfEra()) || JodaTimeUtils.hasField(base, JodaTimeUtils.DecadeOfCentury)
          || JodaTimeUtils.hasField(base, DateTimeFieldType.yearOfCentury())) {
        if (JodaTimeUtils.hasField(base, DateTimeFieldType.centuryOfEra())) {
          builder.appendCenturyOfEra(2, 2);
        } else {
          builder.appendLiteral(PAD_FIELD_UNKNOWN2);
        }
        if (JodaTimeUtils.hasField(base, JodaTimeUtils.DecadeOfCentury)) {
          builder.appendDecimal(JodaTimeUtils.DecadeOfCentury, 1, 1);
          builder.appendLiteral(PAD_FIELD_UNKNOWN);
        } else if (JodaTimeUtils.hasField(base, DateTimeFieldType.yearOfCentury())) {
          builder.appendYearOfCentury(2, 2);
        } else {
          builder.appendLiteral(PAD_FIELD_UNKNOWN2);
        }
      } else if (JodaTimeUtils.hasField(base, DateTimeFieldType.year())) {
        builder.appendYear(4, 4);
      } else if (JodaTimeUtils.hasField(base, DateTimeFieldType.weekyear())) {
        builder.appendWeekyear(4, 4);
      } else {
        builder.appendLiteral(PAD_FIELD_UNKNOWN4);
        hasDate = false;
      }
      // Decide whether to include HALF, QUARTER, MONTH/DAY, or WEEK/WEEKDAY
      boolean appendHalf = false;
      boolean appendQuarter = false;
      boolean appendMonthDay = false;
      boolean appendWeekDay = false;
      if (isISO || isTimex3) {
        if (JodaTimeUtils.hasField(base, DateTimeFieldType.monthOfYear()) && JodaTimeUtils.hasField(base, DateTimeFieldType.dayOfMonth())) {
          appendMonthDay = true;
        } else if (JodaTimeUtils.hasField(base, DateTimeFieldType.weekOfWeekyear()) || JodaTimeUtils.hasField(base, DateTimeFieldType.dayOfWeek())) {
          appendWeekDay = true;
        } else if (JodaTimeUtils.hasField(base, DateTimeFieldType.monthOfYear()) || JodaTimeUtils.hasField(base, DateTimeFieldType.dayOfMonth())) {
          appendMonthDay = true;
        } else if (JodaTimeUtils.hasField(base, JodaTimeUtils.QuarterOfYear)) {
          if (!isISO) appendQuarter = true;
        } else if (JodaTimeUtils.hasField(base, JodaTimeUtils.HalfYearOfYear)) {
          if (!isISO) appendHalf = true;
        }
      } else {
        appendHalf = true;
        appendQuarter = true;
        appendMonthDay = true;
        appendWeekDay = true;
      }

      // Half - Not ISO standard
      if (appendHalf && JodaTimeUtils.hasField(base, JodaTimeUtils.HalfYearOfYear)) {
        builder.appendLiteral("-H");
        builder.appendDecimal(JodaTimeUtils.HalfYearOfYear, 1, 1);
      }
      // Quarter  - Not ISO standard
      if (appendQuarter && JodaTimeUtils.hasField(base, JodaTimeUtils.QuarterOfYear)) {
        builder.appendLiteral("-Q");
        builder.appendDecimal(JodaTimeUtils.QuarterOfYear, 1, 1);
      }
      // MONTH
      if (appendMonthDay && (JodaTimeUtils.hasField(base, DateTimeFieldType.monthOfYear()) || JodaTimeUtils.hasField(base, DateTimeFieldType.dayOfMonth()))) {
        hasDate = true;
        builder.appendLiteral('-');
        if (JodaTimeUtils.hasField(base, DateTimeFieldType.monthOfYear())) {
          builder.appendMonthOfYear(2);
        } else {
          builder.appendLiteral(PAD_FIELD_UNKNOWN2);
        }
        // Don't indicate day of month if not specified
        if (JodaTimeUtils.hasField(base, DateTimeFieldType.dayOfMonth())) {
          builder.appendLiteral('-');
          builder.appendDayOfMonth(2);
        } else if (alwaysPad) {
          builder.appendLiteral(PAD_FIELD_UNKNOWN2);
        }
      }
      if (appendWeekDay && (JodaTimeUtils.hasField(base, DateTimeFieldType.weekOfWeekyear()) || JodaTimeUtils.hasField(base, DateTimeFieldType.dayOfWeek()))) {
        hasDate = true;
        builder.appendLiteral("-W");
        if (JodaTimeUtils.hasField(base, DateTimeFieldType.weekOfWeekyear())) {
          builder.appendWeekOfWeekyear(2);
        } else {
          builder.appendLiteral(PAD_FIELD_UNKNOWN2);
        }
        // Don't indicate the day of the week if not specified
        if (JodaTimeUtils.hasField(base, DateTimeFieldType.dayOfWeek())) {
          builder.appendLiteral("-");
          builder.appendDayOfWeek(1);
        }
      }
      return hasDate;
    }

    protected boolean appendTimeFormats(DateTimeFormatterBuilder builder, int flags) {
      boolean alwaysPad = ((flags & FORMAT_PAD_UNKNOWN) != 0);
      boolean hasTime = hasTime();
      DateTimeFieldType sdft = JodaTimeUtils.getMostSpecific(base);
      if (hasTime) {
        builder.appendLiteral("T");
        if (JodaTimeUtils.hasField(base, DateTimeFieldType.hourOfDay())) {
          builder.appendHourOfDay(2);
        } else if (JodaTimeUtils.hasField(base, DateTimeFieldType.clockhourOfDay())) {
          builder.appendClockhourOfDay(2);
        } else {
          builder.appendLiteral(PAD_FIELD_UNKNOWN2);
        }
        if (JodaTimeUtils.hasField(base, DateTimeFieldType.minuteOfHour())) {
          builder.appendLiteral(":");
          builder.appendMinuteOfHour(2);
        } else if (alwaysPad || JodaTimeUtils.isMoreGeneral(DateTimeFieldType.minuteOfHour(), sdft, base.getChronology())) {
          builder.appendLiteral(":");
          builder.appendLiteral(PAD_FIELD_UNKNOWN2);
        }
        if (JodaTimeUtils.hasField(base, DateTimeFieldType.secondOfMinute())) {
          builder.appendLiteral(":");
          builder.appendSecondOfMinute(2);
        } else if (alwaysPad || JodaTimeUtils.isMoreGeneral(DateTimeFieldType.secondOfMinute(), sdft, base.getChronology())) {
          builder.appendLiteral(":");
          builder.appendLiteral(PAD_FIELD_UNKNOWN2);
        }
        if (JodaTimeUtils.hasField(base, DateTimeFieldType.millisOfSecond())) {
          builder.appendLiteral(".");
          builder.appendMillisOfSecond(3);
        }
        // builder.append(isoTimeFormatter);
      }
      return hasTime;
    }

    protected DateTimeFormatter getFormatter(int flags) {
      DateTimeFormatterBuilder builder = new DateTimeFormatterBuilder();
      boolean hasDate = appendDateFormats(builder, flags);
      boolean hasTime = hasTime();
      if (hasTime) {
        if (!hasDate) {
          builder.clear();
        }
        appendTimeFormats(builder, flags);
      }
      return builder.toFormatter();
    }

    @Override
    public boolean isGrounded() {
      return false;
    }

    // TODO: compute duration/range => uncertainty of this time
    @Override
    public Duration getDuration() {
/*      TimeLabel tl = getTimeLabel();
      if (tl != null) {
        return tl.getDuration();
      } */
      StandardTemporalType tlt = getStandardTemporalType();
      if (tlt != null) {
        return tlt.getDuration();
      }
      return Duration.getDuration(JodaTimeUtils.getJodaTimePeriod(base));
    }

    @Override
    public Range getRange(int flags, Duration inputGranularity) {
      Duration d = getDuration();
      if (d != null) {
        int padType = (flags & RANGE_FLAGS_PAD_MASK);
        Time start; // initialized in switch;
        Duration granularity = inputGranularity;
        switch (padType) {
        case RANGE_FLAGS_PAD_NONE:
          // The most basic range
          start = this;
          break;
        case RANGE_FLAGS_PAD_AUTO:
          // More complex range
          if (hasTime()) {
            granularity = SUTime.MILLIS;
          } else {
            granularity = SUTime.DAY;
          }
          start = padMoreSpecificFields(granularity);
          break;
        case RANGE_FLAGS_PAD_FINEST:
          granularity = SUTime.MILLIS;
          start = padMoreSpecificFields(granularity);
          break;
        case RANGE_FLAGS_PAD_SPECIFIED:
          start = padMoreSpecificFields(granularity);
          break;
        default:
          throw new UnsupportedOperationException("Unsupported pad type for getRange: " + flags);
        }
        if (start instanceof PartialTime) {
          ((PartialTime) start).withStandardFields();
        }
        Time end = start.add(d);
        if (granularity != null) {
          end = end.subtract(granularity);
        }
        return new Range(start, end, d);
      } else {
        return new Range(this, this);
      }
    }

    protected void withStandardFields() {
      if (base.isSupported(DateTimeFieldType.dayOfWeek())) {
        base = JodaTimeUtils.resolveDowToDay(base);
      } else if (base.isSupported(DateTimeFieldType.monthOfYear()) && base.isSupported(DateTimeFieldType.dayOfMonth())) {
        if (base.isSupported(DateTimeFieldType.weekOfWeekyear())) {
          base = base.without(DateTimeFieldType.weekOfWeekyear());
        }
        if (base.isSupported(DateTimeFieldType.dayOfWeek())) {
          base = base.without(DateTimeFieldType.dayOfWeek());
        }
      }
    }

    @Override
    public PartialTime reduceGranularityTo(Duration granularity) {
      Partial pbase = base;
      if (JodaTimeUtils.hasField(granularity.getJodaTimePeriod(), DurationFieldType.weeks())) {
        // Make sure the partial time has weeks in it
        if (!JodaTimeUtils.hasField(pbase, DateTimeFieldType.weekOfWeekyear())) {
          // Add week year to it
          pbase = JodaTimeUtils.resolveWeek(pbase);
        }
      }
      Partial p = JodaTimeUtils.discardMoreSpecificFields( pbase,
        JodaTimeUtils.getMostSpecific(granularity.getJodaTimePeriod()) );
      return new PartialTime(this,p);
    }

    public PartialTime padMoreSpecificFields(Duration granularity) {
      Period period = null;
      if (granularity != null) {
        period = granularity.getJodaTimePeriod();
      }
      Partial p = JodaTimeUtils.padMoreSpecificFields(base, period);
      return new PartialTime(this,p);
    }

    @Override
    public String toFormattedString(int flags) {
      if (getTimeLabel() != null) {
        return getTimeLabel();
      }
      String s; // Initialized below
      if (base != null) {
        // String s = ISODateTimeFormat.basicDateTime().print(base);
        // return s.replace('\ufffd', 'X');
        DateTimeFormatter formatter = getFormatter(flags);
        s = formatter.print(base);
      } else {
        s = "XXXX-XX-XX";
      }
      if (dateTimeZone != null) {
        DateTimeFormatter formatter = DateTimeFormat.forPattern("Z");
        formatter = formatter.withZone(dateTimeZone);
        s = s + formatter.print(0);
      }
      return s;
    }

    @Override
    public Time resolve(Time ref, int flags) {
      if (ref == null || ref == TIME_UNKNOWN || ref == TIME_REF) {
        return this;
      }
      if (this == TIME_REF) {
        return ref;
      }
      if (this == TIME_UNKNOWN) {
        return this;
      }
      Partial partialRef = ref.getJodaTimePartial();
      if (partialRef == null) {
        throw new UnsupportedOperationException("Cannot resolve if reftime is of class: " + ref.getClass());
      }

      Partial p = (base != null) ? JodaTimeUtils.combineMoreGeneralFields(base, partialRef) : partialRef;
      p = JodaTimeUtils.resolveDowToDay(p, partialRef);

      Time resolved;
      if (p == base) {
        resolved = this;
      } else {
        resolved = new PartialTime(this, p);
        // log.info("Resolved " + this + " to " + resolved + ", ref=" + ref);
      }

      Duration resolvedGranularity = resolved.getGranularity();
      Duration refGranularity = ref.getGranularity();
      // log.info("refGranularity is " + refGranularity);
      // log.info("resolvedGranularity is " + resolvedGranularity);
      if (resolvedGranularity != null && refGranularity != null && resolvedGranularity.compareTo(refGranularity) >= 0) {
        if ((flags & RESOLVE_TO_PAST) != 0) {
          if (resolved.compareTo(ref) > 0) {
            Time t = (Time) this.prev();
            if (t != null) {
              resolved = (Time) t.resolve(ref, 0);
            }
          }
          // log.info("Resolved " + this + " to past " + resolved + ", ref=" + ref);
        } else if ((flags & RESOLVE_TO_FUTURE) != 0) {
          if (resolved.compareTo(ref) < 0) {
            Time t = (Time) this.next();
            if (t != null) {
              resolved = (Time) t.resolve(ref, 0);
            }
          }
          // log.info("Resolved " + this + " to future " + resolved + ", ref=" + ref);
        } else if ((flags & RESOLVE_TO_CLOSEST) != 0) {
          if (resolved.compareTo(ref) > 0) {
            Time t = (Time) this.prev();
            if (t != null) {
              Time resolved2 = (Time) t.resolve(ref, 0);
              resolved = Time.closest(ref, resolved, resolved2);
            }
          } if (resolved.compareTo(ref) < 0) {
            Time t = (Time) this.next();
            if (t != null) {
              Time resolved2 = (Time) t.resolve(ref, 0);
              resolved = Time.closest(ref, resolved, resolved2);
            }
          }
          // log.info("Resolved " + this + " to closest " + resolved + ", ref=" + ref);
        }
      }

      return resolved;
    }

    public boolean isCompatible(PartialTime time) {
      return JodaTimeUtils.isCompatible(base, time.base);
    }

    public static Pair<PartialTime, PartialTime> getCompatible(PartialTime t1, PartialTime t2) {
      // Incompatible timezones
      if (t1.dateTimeZone != null && t2.dateTimeZone != null &&
          !t1.dateTimeZone.equals(t2.dateTimeZone))
        return null;
      if (t1.isCompatible(t2)) return Pair.makePair(t1,t2);
      if (t1.uncertaintyGranularity != null && t2.uncertaintyGranularity == null) {
        if (t1.uncertaintyGranularity.compareTo(t2.getDuration()) > 0) {
          // Drop the uncertain fields from t1
          Duration d = t1.uncertaintyGranularity;
          PartialTime t1b = t1.reduceGranularityTo(d);
          if (t1b.isCompatible(t2)) return Pair.makePair(t1b,t2);
        }
      } else if (t1.uncertaintyGranularity == null && t2.uncertaintyGranularity != null) {
        if (t2.uncertaintyGranularity.compareTo(t1.getDuration()) > 0) {
          // Drop the uncertain fields from t2
          Duration d = t2.uncertaintyGranularity;
          PartialTime t2b = t2.reduceGranularityTo(d);
          if (t1.isCompatible(t2b)) return Pair.makePair(t1,t2b);
        }
      } else if (t1.uncertaintyGranularity != null && t2.uncertaintyGranularity != null) {
        Duration d1 = Duration.max(t1.uncertaintyGranularity, t2.getDuration());
        Duration d2 = Duration.max(t2.uncertaintyGranularity, t1.getDuration());
        PartialTime t1b = t1.reduceGranularityTo(d1);
        PartialTime t2b = t2.reduceGranularityTo(d2);
        if (t1b.isCompatible(t2b)) return Pair.makePair(t1b,t2b);
      }
      return null;
    }

    @Override
    public Duration getPeriod() {
  /*    TimeLabel tl = getTimeLabel();
      if (tl != null) {
        return tl.getPeriod();
      } */
      StandardTemporalType tlt = getStandardTemporalType();
      if (tlt != null) {
        return tlt.getPeriod();
      }
      if (base == null) {
        return null;
      }
      DateTimeFieldType mostGeneral = JodaTimeUtils.getMostGeneral(base);
      DurationFieldType df = mostGeneral.getRangeDurationType();
      // if (df == null) {
      // df = mostGeneral.getDurationType();
      // }
      if (df != null) {
        try {
          return new DurationWithFields(new Period().withField(df, 1));
        } catch (Exception ex) {
          // TODO: Do something intelligent here
        }
      }
      return null;
    }

    public List<Temporal> toList() {
      if (JodaTimeUtils.hasField(base, DateTimeFieldType.year())
         && JodaTimeUtils.hasField(base, DateTimeFieldType.monthOfYear())
         && JodaTimeUtils.hasField(base, DateTimeFieldType.dayOfWeek())) {
        List<Temporal> list = new ArrayList<>();
        Partial pt = new Partial();
        pt = JodaTimeUtils.setField(pt, DateTimeFieldType.year(), base.get(DateTimeFieldType.year()));
        pt = JodaTimeUtils.setField(pt, DateTimeFieldType.monthOfYear(), base.get(DateTimeFieldType.monthOfYear()));
        pt = JodaTimeUtils.setField(pt, DateTimeFieldType.dayOfMonth(), 1);

        Partial candidate = JodaTimeUtils.resolveDowToDay(base, pt);
        if (candidate.get(DateTimeFieldType.monthOfYear()) != base.get(DateTimeFieldType.monthOfYear())) {
          pt = JodaTimeUtils.setField(pt, DateTimeFieldType.dayOfMonth(), 8);
          candidate = JodaTimeUtils.resolveDowToDay(base, pt);
          if (candidate.get(DateTimeFieldType.monthOfYear()) != base.get(DateTimeFieldType.monthOfYear())) {
            // give up
            return null;
          }
        }
        try {
          while (candidate.get(DateTimeFieldType.monthOfYear()) == base.get(DateTimeFieldType.monthOfYear())) {
            list.add(new PartialTime(this, candidate));
            pt = JodaTimeUtils.setField(pt, DateTimeFieldType.dayOfMonth(), pt.get(DateTimeFieldType.dayOfMonth()) + 7);
            candidate = JodaTimeUtils.resolveDowToDay(base, pt);
          }
        } catch (IllegalFieldValueException ex) {}
        return list;
      } else {
        return null;
      }
    }

    @Override
    public Time intersect(Time t) {
      if (t == null || t == TIME_UNKNOWN)
        return this;
      if (base == null) {
        if (dateTimeZone != null) {
          return (Time) t.setTimeZone(dateTimeZone);
        } else {
          return t;
        }
      }
      if (t instanceof CompositePartialTime) {
        return t.intersect(this);
      } else if (t instanceof PartialTime) {
        Pair<PartialTime,PartialTime> compatible = getCompatible(this, (PartialTime) t);
        if (compatible == null) {
          return null;
        }
        Partial p = JodaTimeUtils.combine(compatible.first.base, compatible.second.base);
        // Take timezone if there is one
        DateTimeZone dtz = (dateTimeZone != null)? dateTimeZone: ((PartialTime) t).dateTimeZone;
        PartialTime res = new PartialTime(p);
        if (dtz != null) return res.setTimeZone(dtz);
        else return res;
      } else if (t instanceof OrdinalTime) {
        Temporal temp = t.resolve(this);
        if (temp instanceof PartialTime) return (Time) temp;
        else return t.intersect(this);
      } else if (t instanceof GroundedTime) {
        return t.intersect(this);
      } else if (t instanceof RelativeTime) {
        return t.intersect(this);
      } else {
        Time cpt = makeComposite(this, t);
        if (cpt != null) {
          return cpt;
        }
        if (t instanceof InexactTime) {
          return t.intersect(this);
        }
      }
      return null;
      // return new RelativeTime(this, TemporalOp.INTERSECT, t);
    }

    /*public Temporal intersect(Temporal t) {
      if (t == null)
        return this;
      if (t == TIME_UNKNOWN || t == DURATION_UNKNOWN)
        return this;
      if (base == null)
        return t;
      if (t instanceof Time) {
        return intersect((Time) t);
      } else if (t instanceof Range) {
        return t.intersect(this);
      } else if (t instanceof Duration) {
        return new RelativeTime(this, TemporalOp.INTERSECT, t);
      }
      return null;
    }        */

    protected PartialTime addSupported(Period p, int scalar) {
      return new PartialTime(base.withPeriodAdded(p, scalar));
    }

    protected PartialTime addUnsupported(Period p, int scalar) {
      return new PartialTime(this, JodaTimeUtils.addForce(base, p, scalar));
    }

    @Override
    public Time add(Duration offset) {
      if (base == null) {
        return this;
      }
      Period per = offset.getJodaTimePeriod();
      PartialTime p = addSupported(per, 1);
      Period unsupported = JodaTimeUtils.getUnsupportedDurationPeriod(p.base, per);
      Time t = p;
      if (unsupported != null) {
        if (/*unsupported.size() == 1 && */JodaTimeUtils.hasField(unsupported, DurationFieldType.weeks()) && JodaTimeUtils.hasField(p.base, DateTimeFieldType.year())
            && JodaTimeUtils.hasField(p.base, DateTimeFieldType.monthOfYear()) && JodaTimeUtils.hasField(p.base, DateTimeFieldType.dayOfMonth())) {
          // What if there are other unsupported fields...
          t = p.addUnsupported(per, 1);
        } else {
          if (JodaTimeUtils.hasField(unsupported, DurationFieldType.months()) && unsupported.getMonths() % 3 == 0 && JodaTimeUtils.hasField(p.base, JodaTimeUtils.QuarterOfYear)) {
            Partial p2 = p.base.withFieldAddWrapped(JodaTimeUtils.Quarters, unsupported.getMonths() / 3);
            p = new PartialTime(p, p2);
            unsupported = unsupported.withMonths(0);
          }
          if (JodaTimeUtils.hasField(unsupported, DurationFieldType.months()) && unsupported.getMonths() % 6 == 0 && JodaTimeUtils.hasField(p.base, JodaTimeUtils.HalfYearOfYear)) {
            Partial p2 = p.base.withFieldAddWrapped(JodaTimeUtils.HalfYears, unsupported.getMonths() / 6);
            p = new PartialTime(p, p2);
            unsupported = unsupported.withMonths(0);
          }
          if (JodaTimeUtils.hasField(unsupported, DurationFieldType.years()) && unsupported.getYears() % 10 == 0 && JodaTimeUtils.hasField(p.base, JodaTimeUtils.DecadeOfCentury)) {
            Partial p2 = p.base.withFieldAddWrapped(JodaTimeUtils.Decades, unsupported.getYears() / 10);
            p = new PartialTime(p, p2);
            unsupported = unsupported.withYears(0);
          }
          if (JodaTimeUtils.hasField(unsupported, DurationFieldType.years()) && unsupported.getYears() % 100 == 0
              && JodaTimeUtils.hasField(p.base, DateTimeFieldType.centuryOfEra())) {
            Partial p2 = p.base.withField(DateTimeFieldType.centuryOfEra(), p.base.get(DateTimeFieldType.centuryOfEra()) + unsupported.getYears() / 100);
            p = new PartialTime(p, p2);
            unsupported = unsupported.withYears(0);
          }
//          if (unsupported.getDays() != 0 && !JodaTimeUtils.hasField(p.base, DateTimeFieldType.dayOfYear()) && !JodaTimeUtils.hasField(p.base, DateTimeFieldType.dayOfMonth())
//              && !JodaTimeUtils.hasField(p.base, DateTimeFieldType.dayOfWeek()) && JodaTimeUtils.hasField(p.base, DateTimeFieldType.monthOfYear())) {
//            if (p.getGranularity().compareTo(DAY) <= 0) {
//              // We are granular enough for this
//              Partial p2 = p.base.with(DateTimeFieldType.dayOfMonth(), unsupported.getDays());
//              p = new PartialTime(p, p2);
//              unsupported = unsupported.withDays(0);
//            }
//          }
          if (!unsupported.equals(Period.ZERO)) {
            t = new RelativeTime(p, new DurationWithFields(unsupported));
            t.approx = this.approx;
            t.mod = this.mod;
          } else {
            t = p;
          }
        }
      }
      return t;
    }

    private static final long serialVersionUID = 1;
  }

  public static final int ERA_BC = 0;
  public static final int ERA_AD = 1;
  public static final int ERA_UNKNOWN = -1;
  /*
   * This is mostly a helper class but it is also the most standard type of date that people are
   * used to working with.
   */
  public static class IsoDate extends PartialTime {
    // TODO: We are also using this class for partial dates
    //       with just decade or century, but it is difficult
    //       to get that information out without using the underlying joda classes
    /** Era: BC is era 0, AD is era 1, Unknown is -1  */
    public int era = ERA_UNKNOWN;
    /** Year of Era */
    public int year = -1;
    /** Month of Year */
    public int month = -1;
    /** Day of Month */
    public int day = -1;

    public IsoDate(int y, int m, int d) {
      this(null, y, m, d);
    }

    public IsoDate(StandardTemporalType temporalType, int y, int m, int d) {
      this.year = y;
      this.month = m;
      this.day = d;
      initBase();
      this.standardTemporalType = temporalType;
    }

    // TODO: Added for grammar parsing
    public IsoDate(Number y, Number m, Number d) {
      this(y,m,d,null,null);
    }

    public IsoDate(Number y, Number m, Number d, Number era, Boolean yearEraAdjustNeeded) {
      this.year = (y != null)? y.intValue():-1;
      this.month = (m != null)? m.intValue():-1;
      this.day = (d != null)? d.intValue():-1;
      this.era = (era != null)? era.intValue():ERA_UNKNOWN;
      if (yearEraAdjustNeeded != null && yearEraAdjustNeeded && this.era == ERA_BC) {
        if (this.year > 0) {
          this.year--;
        }
      }
      initBase();
    }


    // Assumes y, m, d are ISO formatted
    public IsoDate(String y, String m, String d) {
      if (y != null && !PAD_FIELD_UNKNOWN4.equals(y)) {
        if (!y.matches("[+-]?[0-9X]{4}")) {
          throw new IllegalArgumentException("Year not in ISO format " + y);
        }
        if (y.startsWith("-")) {
          y = y.substring(1);
          era = ERA_BC; // BC
        } else if (y.startsWith("+")) {
          y = y.substring(1);
          era = ERA_AD; // AD
        }
        if (y.contains(PAD_FIELD_UNKNOWN)) {
        } else {
          year = Integer.parseInt(y);
        }
      } else {
        y = PAD_FIELD_UNKNOWN4;
      }
      if (m != null && !PAD_FIELD_UNKNOWN2.equals(m)) {
        month = Integer.parseInt(m);
      } else {
        m = PAD_FIELD_UNKNOWN2;
      }
      if (d != null && !PAD_FIELD_UNKNOWN2.equals(d)) {
        day = Integer.parseInt(d);
      } else {
        d = PAD_FIELD_UNKNOWN2;
      }

      initBase();
      if (year < 0 && !PAD_FIELD_UNKNOWN4.equals(y)) {
        if (Character.isDigit(y.charAt(0)) && Character.isDigit(y.charAt(1))) {
          int century = Integer.parseInt(y.substring(0, 2));
          base = JodaTimeUtils.setField(base, DateTimeFieldType.centuryOfEra(), century);
        }
        if (Character.isDigit(y.charAt(2)) && Character.isDigit(y.charAt(3))) {
          int cy = Integer.parseInt(y.substring(2, 4));
          base = JodaTimeUtils.setField(base, DateTimeFieldType.yearOfCentury(), cy);
        } else if (Character.isDigit(y.charAt(2))) {
          int decade = Integer.parseInt(y.substring(2, 3));
          base = JodaTimeUtils.setField(base, JodaTimeUtils.DecadeOfCentury, decade);
        }
      }
    }

    private void initBase() {
      if (era >= 0 )
        base = JodaTimeUtils.setField(base, DateTimeFieldType.era(), era);
      if (year >= 0)
        base = JodaTimeUtils.setField(base, DateTimeFieldType.year(), year);
      if (month >= 0)
        base = JodaTimeUtils.setField(base, DateTimeFieldType.monthOfYear(), month);
      if (day >= 0)
        base = JodaTimeUtils.setField(base, DateTimeFieldType.dayOfMonth(), day);
    }

    public String toString() {
      // TODO: is the right way to print this object?
      StringBuilder os = new StringBuilder();
      if (era == ERA_BC) {
        os.append("-");
      } else if (era == ERA_AD) {
        os.append("+");
      }
      if (year >= 0)
        os.append(year);
      else
        os.append("XXXX");
      os.append("-");
      if (month >= 0)
        os.append(month);
      else
        os.append("XX");
      os.append("-");
      if (day >= 0)
        os.append(day);
      else
        os.append("XX");
      return os.toString();
    }

    public int getYear() {
      return year;
    }

    // TODO: Should we allow setters??? Most time classes are immutable
    public void setYear(int y) {
      this.year = y;
      initBase();
    }

    public int getMonth() {
      return month;
    }

    // TODO: Should we allow setters??? Most time classes are immutable
    public void setMonth(int m) {
      this.month = m;
      initBase();
    }

    public int getDay() {
      return day;
    }

    // TODO: Should we allow setters??? Most time classes are immutable
    public void setDay(int d) {
      this.day = d;
      initBase();
    }

    // TODO: Should we allow setters??? Most time classes are immutable
    public void setDate(int y, int m, int d) {
      this.year = y;
      this.month = m;
      this.day = d;
      initBase();
    }

    private static final long serialVersionUID = 1;
  }

  public static final int HALFDAY_AM = 0;
  public static final int HALFDAY_PM = 1;
  public static final int HALFDAY_UNKNOWN = -1;

  // Helper time class
  protected static class IsoTime extends PartialTime {
    public int hour = -1;
    public int minute = -1;
    public int second = -1;
    public int millis = -1;
    public int halfday = HALFDAY_UNKNOWN; // 0 = am, 1 = pm

    public IsoTime(int h, int m, int s) {
      this(h, m, s, -1, -1);
    }

    // TODO: Added for reading types from file
    public IsoTime(Number h, Number m, Number s) {
      this(h, m, s, null, null);
    }

    public IsoTime(int h, int m, int s, int ms, int halfday) {
      this.hour = h;
      this.minute = m;
      this.second = s;
      this.millis = ms;
      this.halfday = halfday;
      // Some error checks
      second += millis / 1000;
      millis = millis % 1000;
      minute += second / 60;
      second = second % 60;
      hour += hour / 60;
      minute = minute % 60;
      // Error checks done
      initBase();
    }

    // TODO: Added for reading types from file
    public IsoTime(Number h, Number m, Number s, Number ms, Number halfday) {
      this(
          (h != null)? h.intValue():-1,
          (m != null)? m.intValue():-1,
          (s != null)? s.intValue():-1,
          (ms != null)? ms.intValue():-1,
          (halfday != null)? halfday.intValue():-1);
    }

    public IsoTime(String h, String m, String s) {
      this(h, m, s, null);
    }

    public IsoTime(String h, String m, String s, String ms) {
      if (h != null) {
        hour = Integer.parseInt(h);
      }
      if (m != null) {
        minute = Integer.parseInt(m);
      }
      if (s != null) {
        second = Integer.parseInt(s);
      }
      if (ms != null) {
        millis = Integer.parseInt(s);
      }
      initBase();
    }

    @Override
    public boolean hasTime() {
      return true;
    }

    private void initBase() {
      if (hour >= 0) {
        if (hour < 24) {
          base = JodaTimeUtils.setField(base, DateTimeFieldType.hourOfDay(), hour);
        } else {
          base = JodaTimeUtils.setField(base, DateTimeFieldType.clockhourOfDay(), hour);
        }
      }
      if (minute >= 0)
        base = JodaTimeUtils.setField(base, DateTimeFieldType.minuteOfHour(), minute);
      if (second >= 0)
        base = JodaTimeUtils.setField(base, DateTimeFieldType.secondOfMinute(), second);
      if (millis >= 0)
        base = JodaTimeUtils.setField(base, DateTimeFieldType.millisOfSecond(), millis);
      if (halfday >= 0) {
        base = JodaTimeUtils.setField(base, DateTimeFieldType.halfdayOfDay(), halfday);
      }
    }
    private static final long serialVersionUID = 1;
  }


  protected static class IsoDateTime extends PartialTime {
    private final IsoDate date;
    private final IsoTime time;

    public IsoDateTime(IsoDate date, IsoTime time) {
      this.date = date;
      this.time = time;
      base = JodaTimeUtils.combine(date.base, time.base);
    }

    @Override
    public boolean hasTime() {
      return (time != null);
    }

    /*    public String toISOString()
        {
          return date.toISOString() + time.toISOString();
        }  */

    private static final long serialVersionUID = 1;
  }

  // TODO: Timezone...
  private static final Pattern PATTERN_ISO = Pattern.compile("(\\d\\d\\d\\d)-?(\\d\\d?)-?(\\d\\d?)(-?(?:T(\\d\\d):?(\\d\\d)?:?(\\d\\d)?(?:[.,](\\d{1,3}))?([+-]\\d\\d:?\\d\\d)?))?");
  private static final Pattern PATTERN_ISO_DATETIME = Pattern.compile("(\\d\\d\\d\\d)(\\d\\d)(\\d\\d):(\\d\\d)(\\d\\d)");
  private static final Pattern PATTERN_ISO_TIME = Pattern.compile("T(\\d\\d):?(\\d\\d)?:?(\\d\\d)?(?:[.,](\\d{1,3}))?([+-]\\d\\d:?\\d\\d)?");
  private static final Pattern PATTERN_ISO_DATE_1 = Pattern.compile(".*(\\d\\d\\d\\d)/(\\d\\d?)/(\\d\\d?).*");
  private static final Pattern PATTERN_ISO_DATE_2 = Pattern.compile(".*(\\d\\d\\d\\d)-(\\d\\d?)-(\\d\\d?).*");
  private static final Pattern PATTERN_ISO_DATE_PARTIAL = Pattern.compile("([0-9X]{4})[-]?([0-9X][0-9X])[-]?([0-9X][0-9X])");

  // Ambiguous pattern - interpret as MM/DD/YY(YY)
  private static final Pattern PATTERN_ISO_AMBIGUOUS_1 = Pattern.compile(".*(\\d\\d?)/(\\d\\d?)/(\\d\\d(\\d\\d)?).*");

  // Ambiguous pattern - interpret as MM-DD-YY(YY)
  private static final Pattern PATTERN_ISO_AMBIGUOUS_2 = Pattern.compile(".*(\\d\\d?)-(\\d\\d?)-(\\d\\d(\\d\\d)?).*");

  // Euro date
  // Ambiguous pattern - interpret as DD.MM.YY(YY)
  private static final Pattern PATTERN_ISO_AMBIGUOUS_3 = Pattern.compile(".*(\\d\\d?)\\.(\\d\\d?)\\.(\\d\\d(\\d\\d)?).*");
  private static final Pattern PATTERN_ISO_TIME_OF_DAY = Pattern.compile(".*(\\d?\\d):(\\d\\d)(:(\\d\\d)(\\.\\d+)?)?(\\s*([AP])\\.?M\\.?)?(\\s+([+\\-]\\d+|[A-Z][SD]T|GMT([+\\-]\\d+)?))?.*");


  /**
   * A bunch of formats to parse into
   */
  private static final List<java.time.format.DateTimeFormatter> DATE_TIME_FORMATS = Arrays.asList(
      java.time.format.DateTimeFormatter.ISO_OFFSET_DATE_TIME,
      java.time.format.DateTimeFormatter.ISO_DATE_TIME,
      java.time.format.DateTimeFormatter.ISO_ZONED_DATE_TIME,
      java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME,
      java.time.format.DateTimeFormatter.ISO_INSTANT,
      java.time.format.DateTimeFormatter.ISO_OFFSET_DATE,
      java.time.format.DateTimeFormatter.ISO_DATE,
      java.time.format.DateTimeFormatter.ISO_LOCAL_DATE,
      java.time.format.DateTimeFormatter.ISO_OFFSET_DATE,
      java.time.format.DateTimeFormatter.ISO_LOCAL_TIME,
      new java.time.format.DateTimeFormatterBuilder()
          .appendValue(ChronoField.YEAR, 4, 10, SignStyle.EXCEEDS_PAD)
          .appendValue(MONTH_OF_YEAR, 2)
          .appendValue(DAY_OF_MONTH, 2)
          .appendLiteral("T")
          .appendValue(HOUR_OF_DAY, 2)
          .appendValue(MINUTE_OF_HOUR, 2)
          .appendValue(SECOND_OF_MINUTE, 2)
          .toFormatter(),
      new java.time.format.DateTimeFormatterBuilder()
          .appendValue(ChronoField.YEAR, 4, 10, SignStyle.EXCEEDS_PAD)
          .appendValue(MONTH_OF_YEAR, 2)
          .appendValue(DAY_OF_MONTH, 2)
          .appendLiteral("T")
          .appendValue(HOUR_OF_DAY, 2)
          .appendValue(MINUTE_OF_HOUR, 2)
          .appendValue(SECOND_OF_MINUTE, 2)
          .appendZoneOrOffsetId()
          .toFormatter(),
      new java.time.format.DateTimeFormatterBuilder()
          .appendValue(ChronoField.YEAR, 4, 10, SignStyle.EXCEEDS_PAD)
          .appendValue(MONTH_OF_YEAR, 2)
          .appendValue(DAY_OF_MONTH, 2)
          .toFormatter()
  );


  /**
   * Try parsing a given string into an {@link Instant} in as many ways as we know how.
   * Dates will be normalized to the start of their days.
   *
   * @param value The instant we are parsing.
   * @param timezone The timezone, if none is given in the instant.
   *
   * @return An instant corresponding to the value, if it could be parsed.
   */
  public static Optional<java.time.Instant> parseInstant(String value, Optional<ZoneId> timezone) {
    for (java.time.format.DateTimeFormatter formatter : DATE_TIME_FORMATS) {
      try {
        TemporalAccessor datetime = formatter.parse(value);
        ZoneId parsedTimezone = datetime.query(TemporalQueries.zoneId());
        ZoneOffset parsedOffset = datetime.query(TemporalQueries.offset());
        if (parsedTimezone != null) {
          return Optional.of(java.time.Instant.from(datetime));
        } else if (parsedOffset != null) {
          try {
            return Optional.of(java.time.Instant.ofEpochSecond(datetime.getLong(ChronoField.INSTANT_SECONDS)));
          } catch (UnsupportedTemporalTypeException e) {
            return Optional.of(java.time.LocalDate.of(
                datetime.get(ChronoField.YEAR),
                datetime.get(ChronoField.MONTH_OF_YEAR),
                datetime.get(ChronoField.DAY_OF_MONTH)
            ).atStartOfDay().toInstant(parsedOffset));
          }
        } else {
          if (timezone.isPresent()) {
            java.time.Instant reference = java.time.LocalDate.of(
                datetime.get(ChronoField.YEAR),
                datetime.get(ChronoField.MONTH_OF_YEAR),
                datetime.get(ChronoField.DAY_OF_MONTH)
            ).atStartOfDay().toInstant(ZoneOffset.UTC);
            ZoneOffset currentOffsetForMyZone = timezone.get().getRules().getOffset(reference);
            try {
              return Optional.of(java.time.LocalDateTime.of(
                  datetime.get(ChronoField.YEAR),
                  datetime.get(ChronoField.MONTH_OF_YEAR),
                  datetime.get(ChronoField.DAY_OF_MONTH),
                  datetime.get(ChronoField.HOUR_OF_DAY),
                  datetime.get(ChronoField.MINUTE_OF_HOUR),
                  datetime.get(ChronoField.SECOND_OF_MINUTE)
              ).toInstant(currentOffsetForMyZone));
            } catch (UnsupportedTemporalTypeException e) {
              return Optional.of(java.time.LocalDate.of(
                  datetime.get(ChronoField.YEAR),
                  datetime.get(ChronoField.MONTH_OF_YEAR),
                  datetime.get(ChronoField.DAY_OF_MONTH)
              ).atStartOfDay().toInstant(currentOffsetForMyZone));
            }
          }
        }
      } catch (DateTimeParseException ignored) { }
    }
    return Optional.empty();
  }



  /**
   * Converts a string that represents some kind of date into ISO 8601 format and
   *  returns it as a SUTime.Time
   *   YYYYMMDDThhmmss
   *
   * @param dateStr The serialized date we are parsing to a document date.
   * @param allowPartial (allow partial ISO)
   */
  public static SUTime.Time parseDateTime(String dateStr, boolean allowPartial) {
    if (dateStr == null) return null;

    Optional<java.time.Instant> refInstant = parseInstant(dateStr, Optional.empty());
    if (refInstant.isPresent()) {
      return new SUTime.GroundedTime(new Instant(refInstant.get().toEpochMilli()));
    }

    Matcher m = PATTERN_ISO.matcher(dateStr);
    if (m.matches()) {
      String time = m.group(4);
      SUTime.IsoDate isoDate = new SUTime.IsoDate(m.group(1), m.group(2), m.group(3));
      if (time != null) {
        SUTime.IsoTime isoTime = new SUTime.IsoTime(m.group(5), m.group(6), m.group(7), m.group(8));
        return new SUTime.IsoDateTime(isoDate,isoTime);
      } else {
        return isoDate;
      }
    }

    m = PATTERN_ISO_DATETIME.matcher(dateStr);
    if (m.matches()) {
      SUTime.IsoDate date = new SUTime.IsoDate(m.group(1), m.group(2), m.group(3));
      SUTime.IsoTime time = new SUTime.IsoTime(m.group(4), m.group(5), null);
      return new SUTime.IsoDateTime(date,time);
    }

    m = PATTERN_ISO_TIME.matcher(dateStr);
    if (m.matches()) {
      return new SUTime.IsoTime(m.group(1), m.group(2), m.group(3), m.group(4));
    }

    SUTime.IsoDate isoDate = null;
    if (isoDate == null) {
      m = PATTERN_ISO_DATE_1.matcher(dateStr);

      if (m.matches()) {
        isoDate = new SUTime.IsoDate(m.group(1), m.group(2), m.group(3));
      }
    }

    if (isoDate == null) {
      m = PATTERN_ISO_DATE_2.matcher(dateStr);
      if (m.matches()) {
        isoDate = new SUTime.IsoDate(m.group(1), m.group(2), m.group(3));
      }
    }

    if (allowPartial) {
      m = PATTERN_ISO_DATE_PARTIAL.matcher(dateStr);
      if (m.matches()) {
        if (!(m.group(1).equals("XXXX") && m.group(2).equals("XX") && m.group(3).equals("XX"))) {
          isoDate = new SUTime.IsoDate(m.group(1), m.group(2), m.group(3));
        }
      }
    }

    if (isoDate == null) {
      m = PATTERN_ISO_AMBIGUOUS_1.matcher(dateStr);

      if (m.matches()) {
        isoDate = new SUTime.IsoDate(m.group(3), m.group(1), m.group(2));
      }
    }

    if (isoDate == null) {
      m = PATTERN_ISO_AMBIGUOUS_2.matcher(dateStr);
      if (m.matches()) {
        isoDate = new SUTime.IsoDate(m.group(3), m.group(1), m.group(2));
      }
    }

    if (isoDate == null) {
      m = PATTERN_ISO_AMBIGUOUS_3.matcher(dateStr);
      if (m.matches()) {
        isoDate = new SUTime.IsoDate(m.group(3), m.group(2), m.group(1));
      }
    }

    // Now add Time of Day
    SUTime.IsoTime isoTime = null;
    if (isoTime == null) {
      m = PATTERN_ISO_TIME_OF_DAY.matcher(dateStr);
      if (m.matches()) {
        // TODO: Fix
        isoTime = new SUTime.IsoTime(m.group(1), m.group(2), m.group(4));
      }
    }

    if (isoDate != null && isoTime != null) {
      return new SUTime.IsoDateTime(isoDate, isoTime);
    } else if (isoDate != null) {
      return isoDate;
    } else {
      return isoTime;
    }
  }

  public static SUTime.Time parseDateTime(String dateStr) {
    return parseDateTime(dateStr, false);
  }

  public static class GroundedTime extends Time {
    // Represents an absolute time
    ReadableInstant base;

    public GroundedTime(Time p, ReadableInstant base) {
      super(p);
      this.base = base;
    }

    public GroundedTime(ReadableInstant base) {
      this.base = base;
    }

    @Override
    public GroundedTime setTimeZone(DateTimeZone tz) {
      MutableDateTime tzBase = base.toInstant().toMutableDateTime();
      tzBase.setZone(tz);           // TODO: setZoneRetainFields?
      return new GroundedTime(this, tzBase);
    }

    @Override
    public boolean hasTime() {
      return true;
    }

    @Override
    public boolean isGrounded() {
      return true;
    }

    @Override
    public Duration getDuration() {
      return DURATION_NONE;
    }

    @Override
    public Range getRange(int flags, Duration granularity) {
      return new Range(this, this);
    }

    @Override
    public String toFormattedString(int flags) {
      return base.toString();
    }

    @Override
    public Time resolve(Time refTime, int flags) {
      return this;
    }

    @Override
    public Time add(Duration offset) {
      Period p = offset.getJodaTimePeriod();
      GroundedTime g = new GroundedTime(base.toInstant().withDurationAdded(p.toDurationFrom(base), 1));
      g.approx = this.approx;
      g.mod = this.mod;
      return g;
    }

    @Override
    public Time intersect(Time t) {
      if (t.getRange().contains(this.getRange())) {
        return this;
      } else {
        return null;
      }
    }

    @Override
    public Temporal intersect(Temporal other) {
      if (other == null)
        return this;
      if (other == TIME_UNKNOWN)
        return this;
      if (other.getRange().contains(this.getRange())) {
        return this;
      } else {
        return null;
      }
    }

    @Override
    public Instant getJodaTimeInstant() {
      return base.toInstant();
    }

    @Override
    public Partial getJodaTimePartial() {
      return JodaTimeUtils.getPartial(base.toInstant(), JodaTimeUtils.EMPTY_ISO_PARTIAL);
    }

    private static final long serialVersionUID = 1;
  }

  // Duration classes
  /**
   * A Duration represents a period of time (without endpoints).
   * <br>
   * We have 3 types of durations:
   * <ol>
   * <li> DurationWithFields - corresponds to JodaTime Period,
   * where we have fields like hours, weeks, etc </li>
   * <li> DurationWithMillis -
   * corresponds to JodaTime Duration, where the duration is specified in millis
   * this gets rid of certain ambiguities such as a month with can be 28, 30, or
   * 31 days </li>
   * <li>InexactDuration - duration that is under determined (like a few
   * days)</li>
   * </ol>
   */
  public abstract static class Duration extends Temporal implements FuzzyInterval.FuzzyComparable<Duration> {

    public Duration() {
    }

    public Duration(Duration d) {
      super(d);
    }

    public static Duration getDuration(ReadablePeriod p) {
      return new DurationWithFields(p);
    }

    public static Duration getDuration(org.joda.time.Duration d) {
      return new DurationWithMillis(d);
    }

    public static Duration getInexactDuration(ReadablePeriod p) {
      return new InexactDuration(p);
    }

    public static Duration getInexactDuration(org.joda.time.Duration d) {
      return new InexactDuration(d.toPeriod());
    }

    // Returns the inexact version of the duration
    public InexactDuration makeInexact() {
      return new InexactDuration(getJodaTimePeriod());
    }

    public DateTimeFieldType[] getDateTimeFields() {
      return null;
    }

    @Override
    public boolean isGrounded() {
      return false;
    }

    @Override
    public Time getTime() {
      return null;
    } // There is no time associated with a duration?

    public Time toTime(Time refTime) {
      return toTime(refTime, 0);
    }

    public Time toTime(Time refTime, int flags) {
      // if ((flags & (DUR_RESOLVE_FROM_AS_REF | DUR_RESOLVE_TO_AS_REF)) == 0)
      {
        Partial p = refTime.getJodaTimePartial();
        if (p != null) {
          // For durations that have corresponding date time fields
          // this = current time without more specific fields than the duration
          DateTimeFieldType[] dtFieldTypes = getDateTimeFields();
          if (dtFieldTypes != null) {
            Time t = null;
            for (DateTimeFieldType dtft : dtFieldTypes) {
              if (p.isSupported(dtft)) {
                t = new PartialTime(JodaTimeUtils.discardMoreSpecificFields(p, dtft));
              }
            }
            if (t == null) {
              Instant instant = refTime.getJodaTimeInstant();
              if (instant != null) {
                for (DateTimeFieldType dtft : dtFieldTypes) {
                  if (instant.isSupported(dtft)) {
                    Partial p2 = JodaTimeUtils.getPartial(instant, p.with(dtft, 1));
                    t = new PartialTime(JodaTimeUtils.discardMoreSpecificFields(p2, dtft));
                  }
                }
              }
            }
            if (t != null) {
              if ((flags & RESOLVE_TO_PAST) != 0) {
                // Check if this time is in the past, if not, subtract duration
                if (t.compareTo(refTime) >= 0) {
                  return t.subtract(this);
                }
              } else if ((flags & RESOLVE_TO_FUTURE) != 0) {
                // Check if this time is in the future, if not, subtract
                // duration
                if (t.compareTo(refTime) <= 0) {
                  return t.add(this);
                }
              }
            }
            return t;
          }
        }
      }
      Time minTime = refTime.subtract(this);
      Time maxTime = refTime.add(this);
      Range likelyRange; // initialized below
      if ((flags & (DUR_RESOLVE_FROM_AS_REF | RESOLVE_TO_FUTURE)) != 0) {
        likelyRange = new Range(refTime, maxTime, this);
      } else if ((flags & (DUR_RESOLVE_TO_AS_REF | RESOLVE_TO_PAST)) != 0) {
        likelyRange = new Range(minTime, refTime, this);
      } else {
        Duration halfDuration = this.divideBy(2);
        likelyRange = new Range(refTime.subtract(halfDuration), refTime.add(halfDuration), this);
      }
      return new TimeWithRange(likelyRange);
//      if ((flags & (RESOLVE_TO_FUTURE | RESOLVE_TO_PAST)) != 0) {
//        return new TimeWithRange(likelyRange);
//      }
//      Range r = new Range(minTime, maxTime, this.multiplyBy(2));
//      return new InexactTime(new TimeWithRange(likelyRange), this, r);
    }

    @Override
    public Duration getDuration() {
      return this;
    }

    @Override
    public Range getRange(int flags, Duration granularity) {
      return new Range(null, null, this);
    } // Unanchored range

    @Override
    public TimexType getTimexType() {
      return TimexType.DURATION;
    }

    public abstract Period getJodaTimePeriod();

    public abstract org.joda.time.Duration getJodaTimeDuration();

    @Override
    public String toFormattedString(int flags) {
      if (getTimeLabel() != null) {
        return getTimeLabel();
      }
      Period p = getJodaTimePeriod();
      String s = (p != null) ? p.toString() : "PXX";
      if ((flags & (FORMAT_ISO | FORMAT_TIMEX3_VALUE)) == 0) {
        String m = getMod();
        if (m != null) {
          try {
            TimexMod tm = TimexMod.valueOf(m);
            if (tm.getSymbol() != null) {
              s = tm.getSymbol() + s;
            }
          } catch (Exception ex) {
          }
        }
      }
      return s;
    }

    @Override
    public Duration getPeriod() {
  /*    TimeLabel tl = getTimeLabel();
      if (tl != null) {
        return tl.getPeriod();
      } */
      StandardTemporalType tlt = getStandardTemporalType();
      if (tlt != null) {
        return tlt.getPeriod();
      }
      return this;
    }

    // Rough approximate ordering of durations
    @Override
    public int compareTo(Duration d) {
      org.joda.time.Duration d1 = getJodaTimeDuration();
      org.joda.time.Duration d2 = d.getJodaTimeDuration();
      if (d1 == null && d2 == null) {
        return 0;
      } else if (d1 == null) {
        return 1;
      } else if (d2 == null) {
        return -1;
      }

      int cmp = d1.compareTo(d2);
      if (cmp == 0) {
        if (d.isApprox() && !this.isApprox()) {
          // Put exact in front of approx
          return -1;
        } else if (!d.isApprox() && this.isApprox()) {
          return 1;
        } else {
          return 0;
        }
      } else {
        return cmp;
      }
    }

    @Override
    public boolean isComparable(Duration d) {
      // TODO: When is two durations comparable?
      return true;
    }

    // Operations with durations
    public abstract Duration add(Duration d);

    public abstract Duration multiplyBy(int m);

    public abstract Duration divideBy(int m);

    public Duration subtract(Duration d) {
      return add(d.multiplyBy(-1));
    }

    @Override
    public Duration resolve(Time refTime, int flags) {
      return this;
    }

    @Override
    public Temporal intersect(Temporal t) {
      if (t == null)
        return this;
      if (t == TIME_UNKNOWN || t == DURATION_UNKNOWN)
        return this;
      if (t instanceof Time) {
        RelativeTime rt = new RelativeTime((Time) t, TemporalOp.INTERSECT, this);
        rt = (RelativeTime) rt.addMod(this.getMod());
        return rt;
      } else if (t instanceof Range) {
        // return new TemporalSet(t, TemporalOp.INTERSECT, this);
      } else if (t instanceof Duration) {
        Duration d = (Duration) t;
        return intersect(d);
      }
      return null;
    }

    public Duration intersect(Duration d) {
      if (d == null || d == DURATION_UNKNOWN)
        return this;
      int cmp = compareTo(d);
      if (cmp < 0) {
        return this;
      } else {
        return d;
      }
    }

    public static Duration min(Duration d1, Duration d2) {
      if (d2 == null)
        return d1;
      if (d1 == null)
        return d2;
      if (d1.isComparable(d2)) {
        int c = d1.compareTo(d2);
        return (c < 0) ? d1 : d2;
      }
      return d1;
    }

    public static Duration max(Duration d1, Duration d2) {
      if (d1 == null)
        return d2;
      if (d2 == null)
        return d1;
      if (d1.isComparable(d2)) {
        int c = d1.compareTo(d2);
        return (c >= 0) ? d1 : d2;
      }
      return d2;
    }

    private static final long serialVersionUID = 1;
  }

  /**
   * Duration that is specified using fields such as milliseconds, days, etc.
   */
  public static class DurationWithFields extends Duration {
    // Use Inexact duration to be able to specify duration with uncertain number
    // Like a few years
    ReadablePeriod period;

    public DurationWithFields() {
      this.period = null;
    }

    public DurationWithFields(ReadablePeriod period) {
      this.period = period;
    }

    public DurationWithFields(Duration d, ReadablePeriod period) {
      super(d);
      this.period = period;
    }

    @Override
    public Duration multiplyBy(int m) {
      if (m == 1 || period == null) {
        return this;
      } else {
        MutablePeriod p = period.toMutablePeriod();
        for (int i = 0; i < period.size(); i++) {
          p.setValue(i, period.getValue(i) * m);
        }
        return new DurationWithFields(p);
      }
    }

    @Override
    public Duration divideBy(int m) {
      if (m == 1 || period == null) {
        return this;
      } else {
        MutablePeriod p = new MutablePeriod();
        for (int i = 0; i < period.size(); i++) {
          int oldVal = period.getValue(i);
          DurationFieldType field = period.getFieldType(i);
          int remainder = oldVal % m;
          p.add(field, oldVal - remainder);
          if (remainder != 0) {
            DurationFieldType f;
            int standardUnit; // initialized below
            // TODO: This seems silly, how to do this with jodatime???
            if (DurationFieldType.centuries().equals(field)) {
              f = DurationFieldType.years();
              standardUnit = 100;
            } else if (DurationFieldType.years().equals(field)) {
              f = DurationFieldType.months();
              standardUnit = 12;
            } else if (DurationFieldType.halfdays().equals(field)) {
              f = DurationFieldType.hours();
              standardUnit = 12;
            } else if (DurationFieldType.days().equals(field)) {
              f = DurationFieldType.hours();
              standardUnit = 24;
            } else if (DurationFieldType.hours().equals(field)) {
              f = DurationFieldType.minutes();
              standardUnit = 60;
            } else if (DurationFieldType.minutes().equals(field)) {
              f = DurationFieldType.seconds();
              standardUnit = 60;
            } else if (DurationFieldType.seconds().equals(field)) {
              f = DurationFieldType.millis();
              standardUnit = 1000;
            } else if (DurationFieldType.months().equals(field)) {
              f = DurationFieldType.days();
              standardUnit = 30;
            } else if (DurationFieldType.weeks().equals(field)) {
              f = DurationFieldType.days();
              standardUnit = 7;
            } else if (DurationFieldType.millis().equals(field)) {
              // No more granularity units....
              f = DurationFieldType.millis();
              standardUnit = 0;
            } else {
              throw new UnsupportedOperationException("Unsupported duration type: " + field + " when dividing");
            }
            p.add(f, standardUnit * remainder);
          }
        }
        for (int i = 0; i < p.size(); i++) {
          p.setValue(i, p.getValue(i) / m);
        }
        return new DurationWithFields(p);
      }
    }

    @Override
    public Period getJodaTimePeriod() {
      return (period != null) ? period.toPeriod() : null;
    }

    @Override
    public org.joda.time.Duration getJodaTimeDuration() {
      return (period != null) ? period.toPeriod().toDurationFrom(JodaTimeUtils.INSTANT_ZERO) : null;
    }

    @Override
    public Duration resolve(Time refTime, int flags) {
      Instant instant = (refTime != null) ? refTime.getJodaTimeInstant() : null;
      if (instant != null) {
        if ((flags & DUR_RESOLVE_FROM_AS_REF) != 0) {
          return new DurationWithMillis(this, period.toPeriod().toDurationFrom(instant));
        } else if ((flags & DUR_RESOLVE_TO_AS_REF) != 0) {
          return new DurationWithMillis(this, period.toPeriod().toDurationTo(instant));
        }
      }
      return this;
    }

    @Override
    public Duration add(Duration d) {
      Period p = period.toPeriod().plus(d.getJodaTimePeriod());
      if (this instanceof InexactDuration || d instanceof InexactDuration) {
        return new InexactDuration(this, p);
      } else {
        return new DurationWithFields(this, p);
      }
    }

    @Override
    public Duration getGranularity() {
      Period res = new Period();
      res = res.withField(JodaTimeUtils.getMostSpecific(getJodaTimePeriod()), 1);
      return Duration.getDuration(res);
    }

    private static final long serialVersionUID = 1;
  }

  /**
   * Duration specified in terms of milliseconds.
   */
  public static class DurationWithMillis extends Duration {
    private final ReadableDuration base;

    public DurationWithMillis(long ms) {
      this.base = new org.joda.time.Duration(ms);
    }

    public DurationWithMillis(ReadableDuration base) {
      this.base = base;
    }

    public DurationWithMillis(Duration d, ReadableDuration base) {
      super(d);
      this.base = base;
    }

    @Override
    public Duration multiplyBy(int m) {
      if (m == 1) {
        return this;
      } else {
        long ms = base.getMillis();
        return new DurationWithMillis(ms * m);
      }
    }

    @Override
    public Duration divideBy(int m) {
      if (m == 1) {
        return this;
      } else {
        long ms = base.getMillis();
        return new DurationWithMillis(ms / m);
      }
    }

    @Override
    public Period getJodaTimePeriod() {
      return base.toPeriod();
    }

    @Override
    public org.joda.time.Duration getJodaTimeDuration() {
      return base.toDuration();
    }

    @Override
    public Duration add(Duration d) {
      if (d instanceof DurationWithMillis) {
        return new DurationWithMillis(this, base.toDuration().plus(((DurationWithMillis) d).base));
      } else if (d instanceof DurationWithFields) {
        return d.add(this);
      } else {
        throw new UnsupportedOperationException("Unknown duration type in add: " + d.getClass());
      }
    }

    private static final long serialVersionUID = 1;
  }

  /**
   * A range of durations.  For instance, 2 to 3 days.
   */
  public static class DurationRange extends Duration {
    private final Duration minDuration;
    private final Duration maxDuration;

    public DurationRange(DurationRange d, Duration min, Duration max) {
      super(d);
      this.minDuration = min;
      this.maxDuration = max;
    }

    public DurationRange(Duration min, Duration max) {
      this.minDuration = min;
      this.maxDuration = max;
    }

    @Override
    public boolean includeTimexAltValue() {
      return true;
    }

    @Override
    public String toFormattedString(int flags) {
      if ((flags & (FORMAT_ISO | FORMAT_TIMEX3_VALUE)) != 0) {
        // return super.toFormattedString(flags);
        return null;
      }
      StringBuilder sb = new StringBuilder();
      if (minDuration != null)
        sb.append(minDuration.toFormattedString(flags));
      sb.append("/");
      if (maxDuration != null)
        sb.append(maxDuration.toFormattedString(flags));
      return sb.toString();
    }

    @Override
    public Period getJodaTimePeriod() {
      if (minDuration == null)
        return maxDuration.getJodaTimePeriod();
      if (maxDuration == null)
        return minDuration.getJodaTimePeriod();
      Duration mid = minDuration.add(maxDuration).divideBy(2);
      return mid.getJodaTimePeriod();
    }

    @Override
    public org.joda.time.Duration getJodaTimeDuration() {
      if (minDuration == null)
        return maxDuration.getJodaTimeDuration();
      if (maxDuration == null)
        return minDuration.getJodaTimeDuration();
      Duration mid = minDuration.add(maxDuration).divideBy(2);
      return mid.getJodaTimeDuration();
    }

    @Override
    public Duration add(Duration d) {
      Duration min2 = (minDuration != null) ? minDuration.add(d) : null;
      Duration max2 = (maxDuration != null) ? maxDuration.add(d) : null;
      return new DurationRange(this, min2, max2);
    }

    @Override
    public Duration multiplyBy(int m) {
      Duration min2 = (minDuration != null) ? minDuration.multiplyBy(m) : null;
      Duration max2 = (maxDuration != null) ? maxDuration.multiplyBy(m) : null;
      return new DurationRange(this, min2, max2);
    }

    @Override
    public Duration divideBy(int m) {
      Duration min2 = (minDuration != null) ? minDuration.divideBy(m) : null;
      Duration max2 = (maxDuration != null) ? maxDuration.divideBy(m) : null;
      return new DurationRange(this, min2, max2);
    }

    private static final long serialVersionUID = 1;
  }

  /**
   * Duration that is inexact.  Use for durations such as "several days"
   * in which case, we know the field is DAY, but we don't know the exact
   * number of days
   */
  public static class InexactDuration extends DurationWithFields {
    // Original duration is estimate of how long this duration is
    // but since some aspects of it is unknown....
    // for now all fields are inexact

    // TODO: Have inexact duration in which some fields are exact
    // add/toISOString
    // boolean[] exactFields;
    public InexactDuration(ReadablePeriod period) {
      this.period = period;
      // exactFields = new boolean[period.size()];
      this.approx = true;
    }

    public InexactDuration(Duration d) {
      super(d, d.getJodaTimePeriod());
      this.approx = true;
    }

    public InexactDuration(Duration d, ReadablePeriod period) {
      super(d, period);
      this.approx = true;
    }

    @Override
    public String toFormattedString(int flags) {
      String s = super.toFormattedString(flags);
      return s.replaceAll("\\d+", PAD_FIELD_UNKNOWN);
    }

    private static final long serialVersionUID = 1;
  }

  /**
   * A time interval
   */
  public static class Range extends Temporal implements HasInterval<Time> {
    private final Time begin; // = TIME_UNKNOWN;
    private final Time end; // = TIME_UNKNOWN;
    private final Duration duration; // = DURATION_UNKNOWN;

    public Range(Time begin, Time end) {
      this.begin = begin;
      this.end = end;
      this.duration = Time.difference(begin, end);
    }

    public Range(Time begin, Time end, Duration duration) {
      this.begin = begin;
      this.end = end;
      this.duration = duration;
    }

    public Range(Time begin, Duration duration) {
      this.begin = begin;
      this.end = TIME_UNKNOWN;
      this.duration = duration;
    }

    public Range(Range r, Time begin, Time end, Duration duration) {
      super(r);
      this.begin = begin;
      this.end = end;
      this.duration = duration;
    }

    @Override
    public Range setTimeZone(DateTimeZone tz) {
      return new Range(this, (Time) Temporal.setTimeZone(begin, tz), (Time) Temporal.setTimeZone(end, tz), duration);
    }

    @Override
    public Interval<Time> getInterval() {
      return FuzzyInterval.toInterval(begin, end);
    }

    public org.joda.time.Interval getJodaTimeInterval() {
      return new org.joda.time.Interval(begin.getJodaTimeInstant(), end.getJodaTimeInstant());
    }

    @Override
    public boolean isGrounded() {
      return begin.isGrounded() && end.isGrounded();
    }

    @Override
    public Time getTime() {
      return begin;
    } // TODO: return something that makes sense for time...

    @Override
    public Duration getDuration() {
      return duration;
    }

    @Override
    public Range getRange(int flags, Duration granularity) {
      return this;
    }

    @Override
    public TimexType getTimexType() {
      return TimexType.DURATION;
    }

    @Override
    public Map<String, String> getTimexAttributes(TimeIndex timeIndex) {
      String beginTidStr = (begin != null) ? begin.getTidString(timeIndex) : null;
      String endTidStr = (end != null) ? end.getTidString(timeIndex) : null;
      Map<String, String> map = super.getTimexAttributes(timeIndex);
      if (beginTidStr != null) {
        map.put(TimexAttr.beginPoint.name(), beginTidStr);
      }
      if (endTidStr != null) {
        map.put(TimexAttr.endPoint.name(), endTidStr);
      }
      return map;
    }

    // public boolean includeTimexAltValue() { return true; }
    @Override
    public String toFormattedString(int flags) {
      if ((flags & (FORMAT_ISO | FORMAT_TIMEX3_VALUE)) != 0) {
        if (getTimeLabel() != null) {
          return getTimeLabel();
        }
        String beginStr = (begin != null) ? begin.toFormattedString(flags) : null;
        String endStr = (end != null) ? end.toFormattedString(flags) : null;
        String durationStr = (duration != null) ? duration.toFormattedString(flags) : null;
        if ((flags & FORMAT_ISO) != 0) {
          if (beginStr != null && endStr != null) {
            return beginStr + "/" + endStr;
          } else if (beginStr != null && durationStr != null) {
            return beginStr + "/" + durationStr;
          } else if (durationStr != null && endStr != null) {
            return durationStr + "/" + endStr;
          }
        }
        return durationStr;
      } else {
        StringBuilder sb = new StringBuilder();
        sb.append("(");
        if (begin != null)
          sb.append(begin);
        sb.append(",");
        if (end != null)
          sb.append(end);
        sb.append(",");
        if (duration != null)
          sb.append(duration);
        sb.append(")");
        return sb.toString();
      }
    }

    @Override
    public Range resolve(Time refTime, int flags) {
      if (refTime == null) {
        return this;
      }
      if (isGrounded())
        return this;
      if ((flags & RANGE_RESOLVE_TIME_REF) != 0 && (begin == TIME_REF || end == TIME_REF)) {
        Time groundedBegin = begin;
        Duration groundedDuration = duration;
        if (begin == TIME_REF) {
          groundedBegin = (Time) begin.resolve(refTime, flags);
          groundedDuration = (duration != null) ? duration.resolve(refTime, flags | DUR_RESOLVE_FROM_AS_REF) : null;
        }
        Time groundedEnd = end;
        if (end == TIME_REF) {
          groundedEnd = (Time) end.resolve(refTime, flags);
          groundedDuration = (duration != null) ? duration.resolve(refTime, flags | DUR_RESOLVE_TO_AS_REF) : null;
        }
        return new Range(this, groundedBegin, groundedEnd, groundedDuration);
      } else {
        return this;
      }
    }

    // TODO: Implement some range operations....
    public Range offset(Duration d, int offsetFlags) {
      return offset(d, offsetFlags, RANGE_OFFSET_BEGIN | RANGE_OFFSET_END);
    }

    public Range offset(Duration d, int offsetFlags, int rangeFlags) {
      Time b2 = begin;
      if ((rangeFlags & RANGE_OFFSET_BEGIN) != 0) {
        b2 = (begin != null) ? begin.offset(d,offsetFlags) : null;
      }
      Time e2 = end;
      if ((rangeFlags & RANGE_OFFSET_END) != 0) {
        e2 = (end != null) ? end.offset(d,offsetFlags) : null;
      }
      return new Range(this, b2, e2, duration);
    }

    public Range subtract(Duration d) {
      return subtract(d, RANGE_EXPAND_FIX_BEGIN);
    }

    public Range subtract(Duration d, int flags) {
      return add(d.multiplyBy(-1), RANGE_EXPAND_FIX_BEGIN);
    }

    public Range add(Duration d) {
      return add(d, RANGE_EXPAND_FIX_BEGIN);
    }

    public Range add(Duration d, int flags) {
      Duration d2 = duration.add(d);
      Time b2 = begin;
      Time e2 = end;
      if ((flags & RANGE_EXPAND_FIX_BEGIN) == 0) {
        b2 = (end != null) ? end.offset(d2.multiplyBy(-1),0) : null;
      } else if ((flags & RANGE_EXPAND_FIX_END) == 0) {
        e2 = (begin != null) ? begin.offset(d2,0) : null;
      }
      return new Range(this, b2, e2, d2);
    }

    public Time begin() {
      return begin;
    }

    public Time end() {
      return end;
    }

    public Time beginTime() {
      if (begin != null) {
        Range r = begin.getRange();
        if (r != null && !begin.equals(r.begin)) {
          return r.begin;
        }
      }
      return begin;
    }

    public Time endTime() {
      /*    if (end != null) {
            Range r = end.getRange();
            if (r != null && !end.equals(r.end)) {
              //return r.endTime();
              return r.end;
            }
          }        */
      return end;
    }

    public Time mid() {
      if (duration != null && begin != null) {
        Time b = begin.getRange(RANGE_FLAGS_PAD_SPECIFIED,duration.getGranularity()).begin();
        return b.add(duration.divideBy(2));
      } else if (duration != null && end != null) {
        return end.subtract(duration.divideBy(2));
      } else if (begin != null && end != null) {
        // TODO: ....
      } else if (begin != null) {
        return begin;
      } else if (end != null) {
        return end;
      }
      return null;
    }

    // TODO: correct implementation
    @Override
    public Temporal intersect(Temporal t) {
      if (t instanceof Time) {
        return new RelativeTime((Time) t, TemporalOp.INTERSECT, this);
      } else if (t instanceof Range) {
        Range rt = (Range) t;
        // Assume begin/end defined (TODO: handle if duration defined)
        Time b = Time.max(begin, rt.begin);
        Time e = Time.min(end, rt.end);
        return new Range(b, e);
      } else if (t instanceof Duration) {
        return new InexactTime(null, (Duration) t, this);
      }
      return null;
    }

    /**
     * Checks if the provided range r is within the current range.
     * Note that equal ranges also returns true.
     *
     * @param r range
     * @return true if range r is contained in r
     */
    public boolean contains(Range r) {
      if ((this.beginTime().getJodaTimeInstant().isBefore(r.beginTime().getJodaTimeInstant())
                      || this.beginTime().getJodaTimeInstant().isEqual(r.beginTime().getJodaTimeInstant()))
              && (this.endTime().getJodaTimeInstant().isAfter(r.endTime().getJodaTimeInstant())
                      || this.endTime().getJodaTimeInstant().isEqual(r.endTime().getJodaTimeInstant()))) {
        return true;
      }
      return false;
    }


    /**
     * Checks if the provided time is within the current range.
     * @param t A time to check containment for
     * @return Returns whether the provided time is within the current range
     */
    public boolean contains(Time t) {
    	return this.getJodaTimeInterval().contains(t.getJodaTimeInstant());
    }


    private static final long serialVersionUID = 1;
  }


  /**
   * Exciting set of times
   */
  public abstract static class TemporalSet extends Temporal {
    public TemporalSet() {
    }

    public TemporalSet(TemporalSet t) {
      super(t);
    }

    // public boolean includeTimexAltValue() { return true; }
    @Override
    public TimexType getTimexType() {
      return TimexType.SET;
    }

    private static final long serialVersionUID = 1;
  }

  /**
   * Explicit set of times: like tomorrow and next week, not really used
   */
  public static class ExplicitTemporalSet extends TemporalSet {
    private final Set<Temporal> temporals;

    public ExplicitTemporalSet(Temporal... temporals) {
      this.temporals = CollectionUtils.asSet(temporals);
    }

    public ExplicitTemporalSet(Set<Temporal> temporals) {
      this.temporals = temporals;
    }

    public ExplicitTemporalSet(ExplicitTemporalSet p, Set<Temporal> temporals) {
      super(p);
      this.temporals = temporals;
    }

    @Override
    public ExplicitTemporalSet setTimeZone(DateTimeZone tz) {
      Set<Temporal> tzTemporals = Generics.newHashSet(temporals.size());
      for (Temporal t:temporals) {
        tzTemporals.add(Temporal.setTimeZone(t, tz));
      }
      return new ExplicitTemporalSet(this, tzTemporals);
    }

    @Override
    public boolean isGrounded() {
      return false;
    }

    @Override
    public Time getTime() {
      return null;
    }

    @Override
    public Duration getDuration() {
      // TODO: Return difference between min/max of set
      return null;
    }

    @Override
    public Range getRange(int flags, Duration granularity) {
      // TODO: Return min/max of set
      return null;
    }

    @Override
    public Temporal resolve(Time refTime, int flags) {
      Temporal[] newTemporals = new Temporal[temporals.size()];
      int i = 0;
      for (Temporal t : temporals) {
        newTemporals[i] = t.resolve(refTime, flags);
        i++;
      }
      return new ExplicitTemporalSet(newTemporals);
    }

    @Override
    public String toFormattedString(int flags) {
      if (getTimeLabel() != null) {
        return getTimeLabel();
      }
      if ((flags & FORMAT_ISO) != 0) {
        // TODO: is there iso standard?
        return null;
      }
      if ((flags & FORMAT_TIMEX3_VALUE) != 0) {
        // TODO: is there timex3 standard?
        return null;
      }
      return "{" + StringUtils.join(temporals, ", ") + "}";
    }

    @Override
    public Temporal intersect(Temporal other) {
      if (other == null)
        return this;
      if (other == TIME_UNKNOWN || other == DURATION_UNKNOWN)
        return this;
      Set<Temporal> newTemporals = Generics.newHashSet();
      for (Temporal t : temporals) {
        Temporal t2 = t.intersect(other);
        if (t2 != null)
          newTemporals.add(t2);
      }
      return new ExplicitTemporalSet(newTemporals);
    }

    private static final long serialVersionUID = 1;
  }


  public static final PeriodicTemporalSet HOURLY = new PeriodicTemporalSet(null, HOUR, "EVERY", "P1X");
  public static final PeriodicTemporalSet NIGHTLY = new PeriodicTemporalSet(NIGHT, DAY, "EVERY", "P1X");
  public static final PeriodicTemporalSet DAILY = new PeriodicTemporalSet(null, DAY, "EVERY", "P1X");
  public static final PeriodicTemporalSet MONTHLY = new PeriodicTemporalSet(null, MONTH, "EVERY", "P1X");
  public static final PeriodicTemporalSet QUARTERLY = new PeriodicTemporalSet(null, QUARTER, "EVERY", "P1X");
  public static final PeriodicTemporalSet YEARLY = new PeriodicTemporalSet(null, YEAR, "EVERY", "P1X");
  public static final PeriodicTemporalSet WEEKLY = new PeriodicTemporalSet(null, WEEK, "EVERY", "P1X");

  /**
   * PeriodicTemporalSet represent a set of times that occurs with some frequency.
   * Example: At 2-3pm every friday from September 1, 2011 to December 30, 2011.
   */
  public static class PeriodicTemporalSet extends TemporalSet {
    /** Start and end times for when this set of times is suppose to be happening
     *  (e.g. 2011-09-01 to 2011-12-30) */
    Range occursIn;

    /** Temporal that re-occurs (e.g. Friday 2-3pm) */
    Temporal base;

    /** The periodicity of re-occurrence (e.g. week) */
    Duration periodicity;

    // How often (once, twice)
    // int count;

    /** Quantifier - every, every other */
    String quant;

    /** String representation of frequency (3 days = P3D, 3 times = P3X) */
    String freq;

    // public ExplicitTemporalSet toExplicitTemporalSet();
    public PeriodicTemporalSet(Temporal base, Duration periodicity, String quant, String freq) {
      this.base = base;
      this.periodicity = periodicity;
      this.quant = quant;
      this.freq = freq;
    }

    public PeriodicTemporalSet(PeriodicTemporalSet p, Temporal base, Duration periodicity, Range range, String quant, String freq) {
      super(p);
      this.occursIn = range;
      this.base = base;
      this.periodicity = periodicity;
      this.quant = quant;
      this.freq = freq;
    }

    @Override
    public PeriodicTemporalSet setTimeZone(DateTimeZone tz) {
      return new PeriodicTemporalSet(this, Temporal.setTimeZone(base, tz), periodicity,
              (Range) Temporal.setTimeZone(occursIn, tz), quant, freq);
    }

    public PeriodicTemporalSet multiplyDurationBy(int scale) {
      return new PeriodicTemporalSet(this, this.base, periodicity.multiplyBy(scale), this.occursIn, this.quant, this.freq);
    }

    public PeriodicTemporalSet divideDurationBy(int scale) {
      return new PeriodicTemporalSet(this, this.base, periodicity.divideBy(scale), this.occursIn, this.quant, this.freq);
    }

    @Override
    public boolean isGrounded() {
      return (occursIn != null && occursIn.isGrounded());
    }

    @Override
    public Duration getPeriod() {
      return periodicity;
    }

    @Override
    public Time getTime() {
      return null;
    }

    @Override
    public Duration getDuration() {
      return null;
    }

    @Override
    public Range getRange(int flags, Duration granularity) {
      return occursIn;
    }

    @Override
    public Map<String, String> getTimexAttributes(TimeIndex timeIndex) {
      Map<String, String> map = super.getTimexAttributes(timeIndex);
      if (quant != null) {
        map.put(TimexAttr.quant.name(), quant);
      }
      if (freq != null) {
        map.put(TimexAttr.freq.name(), freq);
      }
      if (periodicity != null) {
        map.put("periodicity", periodicity.getTimexValue());
      }
      return map;
    }

    @Override
    public Temporal resolve(Time refTime, int flags) {
      Range resolvedOccursIn = (occursIn != null) ? occursIn.resolve(refTime, flags) : null;
      Temporal resolvedBase = (base != null) ? base.resolve(null, 0) : null;
      return new PeriodicTemporalSet(this, resolvedBase, this.periodicity, resolvedOccursIn, this.quant, this.freq);
    }

    @Override
    public String toFormattedString(int flags) {
      if (getTimeLabel() != null) {
        return getTimeLabel();
      }
      if ((flags & FORMAT_ISO) != 0) {
        // TODO: is there iso standard?
        return null;
      }
      if (base != null) {
        return base.toFormattedString(flags);
      } else {
        if (periodicity != null) {
          return periodicity.toFormattedString(flags);
        }
      }
      return null;
    }

    @Override
    public Temporal intersect(Temporal t) {
      if (t instanceof Range) {
        if (occursIn == null) {
          return new PeriodicTemporalSet(this, base, periodicity, (Range) t, quant, freq);
        }
      } else if (base != null) {
        Temporal merged = base.intersect(t);
        return new PeriodicTemporalSet(this, merged, periodicity, occursIn, quant, freq);
      } else {
        return new PeriodicTemporalSet(this, t, periodicity, occursIn, quant, freq);
      }
      return null;
    }

    private static final long serialVersionUID = 1;
  }

}
