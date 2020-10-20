package edu.stanford.nlp.time;

import org.joda.time.*;
import org.joda.time.chrono.ISOChronology;
import org.joda.time.field.DividedDateTimeField;
import org.joda.time.field.OffsetDateTimeField;
import org.joda.time.field.RemainderDateTimeField;
import org.joda.time.field.ScaledDurationField;

import java.time.ZoneId;
import java.util.Set;
import java.util.TimeZone;

import static org.joda.time.DateTimeFieldType.*;
import static org.joda.time.DurationFieldType.*;

import edu.stanford.nlp.util.Generics;

/**
 * Extensions to Joda time.
 *
 * @author Angel Chang
 * @author Gabor Angeli
 */
public class JodaTimeUtils {

  private JodaTimeUtils() {} // static methods only

  // Standard ISO fields
  protected static final ZoneId UTC = ZoneId.of("UTC");
  protected static final DateTimeFieldType[] standardISOFields = {
          DateTimeFieldType.year(),
          DateTimeFieldType.monthOfYear(),
          DateTimeFieldType.dayOfMonth(),
          DateTimeFieldType.hourOfDay(),
          DateTimeFieldType.minuteOfHour(),
          DateTimeFieldType.secondOfMinute(),
          DateTimeFieldType.millisOfSecond()
  };
  protected static final DateTimeFieldType[] standardISOWeekFields = {
          DateTimeFieldType.year(),  // should this be weekyear()?
          DateTimeFieldType.weekOfWeekyear(),
          DateTimeFieldType.dayOfWeek(),
          DateTimeFieldType.hourOfDay(),
          DateTimeFieldType.minuteOfHour(),
          DateTimeFieldType.secondOfMinute(),
          DateTimeFieldType.millisOfSecond()
  };
  protected static final DateTimeFieldType[] standardISODateFields = {
          DateTimeFieldType.year(),
          DateTimeFieldType.monthOfYear(),
          DateTimeFieldType.dayOfMonth(),
  };
  protected static final DateTimeFieldType[] standardISOTimeFields = {
          DateTimeFieldType.hourOfDay(),
          DateTimeFieldType.minuteOfHour(),
          DateTimeFieldType.secondOfMinute(),
          DateTimeFieldType.millisOfSecond()
  };
  public static final Partial EMPTY_ISO_PARTIAL = new Partial(standardISOFields, new int[]{0,1,1,0,0,0,0});
  public static final Partial EMPTY_ISO_WEEK_PARTIAL = new Partial(standardISOWeekFields, new int[]{0,1,1,0,0,0,0});
  public static final Partial EMPTY_ISO_DATE_PARTIAL = new Partial(standardISODateFields, new int[]{0,1,1});
  public static final Partial EMPTY_ISO_TIME_PARTIAL = new Partial(standardISOTimeFields, new int[]{0,0,0,0});
  public static final Instant INSTANT_ZERO = new Instant(0);


  // Extensions to Joda time fields
  // Duration Fields
  public static final DurationFieldType Quarters = new DurationFieldType("quarters") {
    private static final long serialVersionUID = -8167713675442491871L;

    @Override
    public DurationField getField(Chronology chronology) {
      return new ScaledDurationField(chronology.months(), Quarters, 3);
    }
  };

  public static final DurationFieldType HalfYears = new DurationFieldType("halfyear") {
    private static final long serialVersionUID = -8167713675442491872L;

    @Override
    public DurationField getField(Chronology chronology) {
      return new ScaledDurationField(chronology.months(), HalfYears, 6);
    }
  };

  public static final DurationFieldType Decades = new DurationFieldType("decades") {
    private static final long serialVersionUID = -4594189766036833410L;

    @Override
    public DurationField getField(Chronology chronology) {
      return new ScaledDurationField(chronology.years(), Decades, 10);
    }
  };

  public static final DurationFieldType Centuries = new DurationFieldType("centuries") {
    private static final long serialVersionUID = -7268694266711862790L;

    @Override
    public DurationField getField(Chronology chronology) {
      return new ScaledDurationField(chronology.years(), Centuries, 100);
    }
  };

  // DateTimeFields
  public static final DateTimeFieldType QuarterOfYear = new DateTimeFieldType("quarterOfYear") {
    private static final long serialVersionUID = -5677872459807379123L;

    @Override
    public DurationFieldType getDurationType() {
      return Quarters;
    }

    @Override
    public DurationFieldType getRangeDurationType() {
      return DurationFieldType.years();
    }

    @Override
    public DateTimeField getField(Chronology chronology) {
      return new OffsetDateTimeField(new DividedDateTimeField(new OffsetDateTimeField(chronology.monthOfYear(), -1), QuarterOfYear, 3), 1);
    }
  };

  public static final DateTimeFieldType HalfYearOfYear = new DateTimeFieldType("halfYearOfYear") {
    private static final long serialVersionUID = -5677872459807379123L;

    @Override
    public DurationFieldType getDurationType() {
      return HalfYears;
    }

    @Override
    public DurationFieldType getRangeDurationType() {
      return DurationFieldType.years();
    }

    @Override
    public DateTimeField getField(Chronology chronology) {
      return new OffsetDateTimeField(new DividedDateTimeField(new OffsetDateTimeField(chronology.monthOfYear(), -1), HalfYearOfYear, 6), 1);
    }
  };

  public static final DateTimeFieldType MonthOfQuarter = new DateTimeFieldType("monthOfQuarter") {
    private static final long serialVersionUID = -5677872459807379123L;

    @Override
    public DurationFieldType getDurationType() {
      return DurationFieldType.months();
    }

    @Override
    public DurationFieldType getRangeDurationType() {
      return Quarters;
    }

    @Override
    public DateTimeField getField(Chronology chronology) {
      return new OffsetDateTimeField(new RemainderDateTimeField(new OffsetDateTimeField(chronology.monthOfYear(), -1), MonthOfQuarter, 3), 1);
    }
  };

  public static final DateTimeFieldType MonthOfHalfYear = new DateTimeFieldType("monthOfHalfYear") {
    private static final long serialVersionUID = -5677872459807379123L;

    @Override
    public DurationFieldType getDurationType() {
      return DurationFieldType.months();
    }

    @Override
    public DurationFieldType getRangeDurationType() {
      return HalfYears;
    }

    @Override
    public DateTimeField getField(Chronology chronology) {
      return new OffsetDateTimeField(new RemainderDateTimeField(new OffsetDateTimeField(chronology.monthOfYear(), -1), MonthOfHalfYear, 6), 1);
    }
  };

  public static final DateTimeFieldType WeekOfMonth = new DateTimeFieldType("weekOfMonth") {
    private static final long serialVersionUID = 8676056306203579438L;

    @Override
    public DurationFieldType getDurationType() {
      return DurationFieldType.weeks();
    }

    @Override
    public DurationFieldType getRangeDurationType() {
      return DurationFieldType.months();
    }

    @Override
    public DateTimeField getField(Chronology chronology) {
      return new OffsetDateTimeField(new RemainderDateTimeField(new OffsetDateTimeField(chronology.weekOfWeekyear(), -1), WeekOfMonth, 4), 1);
    }
  };

  public static final DateTimeFieldType DecadeOfCentury = new DateTimeFieldType("decadeOfCentury") {
    private static final long serialVersionUID = 4301444712229535664L;

    @Override
    public DurationFieldType getDurationType() {
      return Decades;
    }

    @Override
    public DurationFieldType getRangeDurationType() {
      return DurationFieldType.centuries();
    }

    @Override
    public DateTimeField getField(Chronology chronology) {
      return new DividedDateTimeField(chronology.yearOfCentury(), DecadeOfCentury, 10);
    }
  };

  public static final DateTimeFieldType YearOfDecade = new DateTimeFieldType("yearOfDecade") {
    private static final long serialVersionUID = 4301444712229535664L;

    @Override
    public DurationFieldType getDurationType() {
      return DurationFieldType.years();
    }

    @Override
    public DurationFieldType getRangeDurationType() {
      return Decades;
    }

    @Override
    public DateTimeField getField(Chronology chronology) {
      return new DividedDateTimeField(chronology.yearOfCentury(), YearOfDecade, 10);
    }
  };

  // Helper functions for working with joda time type
  protected static boolean hasField(ReadablePartial base, DateTimeFieldType field)
  {
    if (base == null) {
      return false;
    } else {
      return base.isSupported(field);
    }
  }

  protected static boolean hasYYYYMMDD(ReadablePartial base)
  {
    if (base == null) {
      return false;
    } else {
      return base.isSupported(DateTimeFieldType.year()) &&
             base.isSupported(DateTimeFieldType.monthOfYear()) &&
             base.isSupported(DateTimeFieldType.dayOfMonth());
    }
  }

  protected static boolean hasYYMMDD(ReadablePartial base)
  {
    if (base == null) {
      return false;
    } else {
      return base.isSupported(DateTimeFieldType.yearOfCentury()) &&
             base.isSupported(DateTimeFieldType.monthOfYear()) &&
             base.isSupported(DateTimeFieldType.dayOfMonth());
    }
  }

  protected static boolean hasField(ReadablePeriod base, DurationFieldType field)
  {
    if (base == null) {
      return false;
    } else {
      return base.isSupported(field);
    }
  }

  protected static Partial setField(Partial base, DateTimeFieldType field, int value) {
    if (base == null) {
      return new Partial(field, value);
    } else {
      return base.with(field, value);
    }
  }

  public static Set<DurationFieldType> getSupportedDurationFields(Partial p)
  {
    Set<DurationFieldType> supportedDurations = Generics.newHashSet();
    for (int i = 0; i < p.size(); i++) {
      supportedDurations.add(p.getFieldType(i).getDurationType());
    }
    return supportedDurations;
  }
  public static Period getUnsupportedDurationPeriod(Partial p, Period offset)
  {
    if (offset == null) { return null; }
    Set<DurationFieldType> supported = getSupportedDurationFields(p);
    Period res = null;
    for (int i = 0; i < offset.size(); i++) {
      if (!supported.contains(offset.getFieldType(i))) {
        if (offset.getValue(i) != 0) {
          if (res == null) { res = new Period(); }
          res = res.withField(offset.getFieldType(i), offset.getValue(i));
        }
      }
    }
    return res;
  }
  public static Partial combine(Partial p1, Partial p2) {
    if (p1 == null) return p2;
    if (p2 == null) return p1;
    Partial p = p1;
    for (int i = 0; i < p2.size(); i++) {
      DateTimeFieldType fieldType = p2.getFieldType(i);
      if (fieldType == DateTimeFieldType.year()) {
        if (p.isSupported(DateTimeFieldType.yearOfCentury())) {
          if (!p.isSupported(DateTimeFieldType.centuryOfEra())) {
            int yoc = p.get(DateTimeFieldType.yearOfCentury());
            int refYear = p2.getValue(i);
            int century = refYear / 100;
            int y2 = yoc + century*100;
            // TODO: Figure out which way to go
            if (refYear < y2) {
              y2 -= 100;
            }
            p = p.without(DateTimeFieldType.yearOfCentury());
            p = p.with(DateTimeFieldType.year(), y2);
          }
          continue;
        } else if (p.isSupported(DateTimeFieldType.centuryOfEra())) {
          continue;
        }
      } else if (fieldType == DateTimeFieldType.yearOfCentury()) {
        if (p.isSupported(DateTimeFieldType.year())) {
          continue;
        }
      } else if (fieldType == DateTimeFieldType.centuryOfEra()) {
        if (p.isSupported(DateTimeFieldType.year())) {
          continue;
        }
      }
      if (!p.isSupported(fieldType)) {
        p = p.with(fieldType, p2.getValue(i));
      }
    }
    if (!p.isSupported(DateTimeFieldType.year())) {
      if (p.isSupported(DateTimeFieldType.yearOfCentury()) && p.isSupported(DateTimeFieldType.centuryOfEra())) {
        int year = p.get(DateTimeFieldType.yearOfCentury()) + p.get(DateTimeFieldType.centuryOfEra())*100;
        p = p.with(DateTimeFieldType.year(), year);
        p = p.without(DateTimeFieldType.yearOfCentury());
        p = p.without(DateTimeFieldType.centuryOfEra());
      }
    }
    if (p.isSupported(DateTimeFieldType.halfdayOfDay())) {
      int hour = -1;
      if (p.isSupported(DateTimeFieldType.hourOfHalfday())) {
        hour = p.get(DateTimeFieldType.hourOfHalfday());
        p = p.without(DateTimeFieldType.hourOfHalfday());
      } else if (p.isSupported(DateTimeFieldType.clockhourOfHalfday())) {
        hour = p.get(DateTimeFieldType.clockhourOfHalfday())-1;
        p = p.without(DateTimeFieldType.clockhourOfHalfday());
      } else if (p.isSupported(DateTimeFieldType.clockhourOfDay())) {
        hour = p.get(DateTimeFieldType.clockhourOfDay())-1;
        p = p.without(DateTimeFieldType.clockhourOfDay());
      } else if (p.isSupported(DateTimeFieldType.hourOfDay())) {
        hour = p.get(DateTimeFieldType.hourOfDay());
        p = p.without(DateTimeFieldType.hourOfDay());
      }
      if (hour >= 0) {
        if (p.get(DateTimeFieldType.halfdayOfDay()) == SUTime.HALFDAY_PM) {
          if (hour < 12) {
            hour = hour+12;
          }
        } else if (hour == 12) {
          hour = 0;
        }
        if (hour < 24) {
          p = p.with(DateTimeFieldType.hourOfDay(), hour);
        } else {
          p = p.with(DateTimeFieldType.clockhourOfDay(), hour);
        }
      }
    }
    return p;
  }
  protected static DateTimeFieldType getMostGeneral(Partial p)
  {
    if (p.size() > 0) { return p.getFieldType(0); }
    return null;
  }
  protected static DateTimeFieldType getMostSpecific(Partial p)
  {
    if (p.size() > 0) { return p.getFieldType(p.size()-1); }
    return null;
  }
  protected static DurationFieldType getMostGeneral(Period p)
  {
    for (int i = 0; i < p.size(); i++) {
      if (p.getValue(i) != 0) {
        return p.getFieldType(i);
      }
    }
    return null;
  }
  protected static DurationFieldType getMostSpecific(Period p)
  {
    for (int i = p.size()-1; i >= 0; i--) {
      if (p.getValue(i) != 0) {
        return p.getFieldType(i);
      }
    }
    return null;
  }
  protected static Period getJodaTimePeriod(Partial p)
  {
    if (p.size() > 0) {
      DateTimeFieldType dtType = p.getFieldType(p.size()-1);
      DurationFieldType dType = dtType.getDurationType();
      Period period = new Period();
      if (period.isSupported(dType)) {
       return period.withField(dType, 1);
      } else {
        DurationField df = dType.getField(p.getChronology());
        if (df instanceof ScaledDurationField) {
          ScaledDurationField sdf = (ScaledDurationField) df;
          return period.withField(sdf.getWrappedField().getType(), sdf.getScalar());
        }
       // PeriodType.forFields(new DurationFieldType[]{dType});
       // return new Period(df.getUnitMillis(), PeriodType.forFields(new DurationFieldType[]{dType}));

      }
    }
    return null;
  }
  public static Partial combineMoreGeneralFields(Partial p1, Partial p2) {
    return combineMoreGeneralFields(p1, p2, null);
  }

  // Combines more general fields from p2 to p1
  public static Partial combineMoreGeneralFields(Partial p1, Partial p2, DateTimeFieldType mgf) {
    Partial p = p1;
    Chronology c1 = p1.getChronology();
    Chronology c2 = p2.getChronology();
    if (!c1.equals(c2)) {
      throw new RuntimeException("Different chronology: c1=" + c1 + ", c2=" + c2);
    }
    DateTimeFieldType p1MostGeneralField = null;
    if (p1.size() > 0) {
      p1MostGeneralField = p1.getFieldType(0);    // Assume fields ordered from most general to least....
    }
    if (mgf == null || (p1MostGeneralField != null && isMoreGeneral(p1MostGeneralField, mgf, c1))) {
      mgf = p1MostGeneralField;
    }
    for (int i = 0; i < p2.size(); i++) {
      DateTimeFieldType fieldType = p2.getFieldType(i);
      if (fieldType == DateTimeFieldType.year()) {
        if (p.isSupported(DateTimeFieldType.yearOfCentury())) {
          if (!p.isSupported(DateTimeFieldType.centuryOfEra())) {
            int yoc = p.get(DateTimeFieldType.yearOfCentury());
            int refYear = p2.getValue(i);
            int century = refYear / 100;
            int y2 = yoc + century*100;
            // TODO: Figure out which way to go
            if (refYear < y2) {
              y2 -= 100;
            }
            p = p.without(DateTimeFieldType.yearOfCentury());
            p = p.with(DateTimeFieldType.year(), y2);
          }
          continue;
        } else if (p.isSupported(JodaTimeUtils.DecadeOfCentury)) {
          if (!p.isSupported(DateTimeFieldType.centuryOfEra())) {
            int decade = p.get(JodaTimeUtils.DecadeOfCentury);
            int refYear = p2.getValue(i);
            int century = refYear / 100;
            int y2 = decade*10 + century*100;
            // TODO: Figure out which way to go
            if (refYear < y2) {
              century--;
            }
            p = p.with(DateTimeFieldType.centuryOfEra(), century);
          }
          continue;
        }
      }
      if (mgf == null || isMoreGeneral(fieldType, mgf, c1)) {
        if (!p.isSupported(fieldType)) {
          p = p.with(fieldType, p2.getValue(i));
        }
      } else {
        break;
      }
    }
    if (!p.isSupported(DateTimeFieldType.year())) {
      if (p.isSupported(DateTimeFieldType.yearOfCentury()) && p.isSupported(DateTimeFieldType.centuryOfEra())) {
        int year = p.get(DateTimeFieldType.yearOfCentury()) + p.get(DateTimeFieldType.centuryOfEra())*100;
        p = p.with(DateTimeFieldType.year(), year);
        p = p.without(DateTimeFieldType.yearOfCentury());
        p = p.without(DateTimeFieldType.centuryOfEra());
      }
    }
    return p;
  }

  public static Partial discardMoreSpecificFields(Partial p, DateTimeFieldType d)
  {
    Partial res = new Partial();
    for (int i = 0; i < p.size(); i++) {
      DateTimeFieldType fieldType = p.getFieldType(i);
      if (fieldType.equals(d) || isMoreGeneral(fieldType, d, p.getChronology())) {
        res = res.with(fieldType, p.getValue(i));
      }
    }
    if (res.isSupported(JodaTimeUtils.DecadeOfCentury) && !res.isSupported(DateTimeFieldType.centuryOfEra())) {
      if (p.isSupported(DateTimeFieldType.year())) {
        res = res.with(DateTimeFieldType.centuryOfEra(), p.get(DateTimeFieldType.year()) / 100);
      }
    }
    return res;
  }

  public static Partial discardMoreSpecificFields(Partial p, DurationFieldType dft)
  {
    DurationField df = dft.getField(p.getChronology());
    Partial res = new Partial();
    for (int i = 0; i < p.size(); i++) {
      DateTimeFieldType fieldType = p.getFieldType(i);
      DurationField f = fieldType.getDurationType().getField(p.getChronology());
      int cmp = df.compareTo(f);
      if (cmp <= 0) {
        res = res.with(fieldType, p.getValue(i));
      }
    }
    return res;
  }

  public static Period discardMoreSpecificFields(Period p, DurationFieldType dft, Chronology chronology)
  {
    DurationField df = dft.getField(chronology);
    Period res = new Period();
    for (int i = 0; i < p.size(); i++) {
      DurationFieldType fieldType = p.getFieldType(i);
      DurationField f = fieldType.getField(chronology);
      int cmp = df.compareTo(f);
      if (cmp <= 0) {
        res = res.withField(fieldType, p.getValue(i));
      }
    }
    return res;
  }

  public static Partial padMoreSpecificFields(Partial p, Period granularity)
  {
    DateTimeFieldType msf = getMostSpecific(p);
    if (isMoreGeneral(msf, DateTimeFieldType.year(), p.getChronology()) ||
            isMoreGeneral(msf, DateTimeFieldType.yearOfCentury(), p.getChronology())) {
      if (p.isSupported(DateTimeFieldType.yearOfCentury())) {
        // OKAY
      } else {
        if (p.isSupported(JodaTimeUtils.DecadeOfCentury)) {
          if (p.isSupported(DateTimeFieldType.centuryOfEra())) {
            int year = p.get(DateTimeFieldType.centuryOfEra()) * 100 + p.get(JodaTimeUtils.DecadeOfCentury)*10;
            p = p.without(JodaTimeUtils.DecadeOfCentury);
            p = p.without(DateTimeFieldType.centuryOfEra());
            p = p.with(DateTimeFieldType.year(), year);
          } else {
            int year = p.get(JodaTimeUtils.DecadeOfCentury)*10;
            p = p.without(JodaTimeUtils.DecadeOfCentury);
            p = p.with(DateTimeFieldType.yearOfCentury(), year);
          }
        } else {
          if (p.isSupported(DateTimeFieldType.centuryOfEra())) {
            int year = p.get(DateTimeFieldType.centuryOfEra()) * 100;
            p = p.without(DateTimeFieldType.centuryOfEra());
            p = p.with(DateTimeFieldType.year(), year);
          }
        }
      }
    }
    boolean useWeek = false;
    if (p.isSupported(DateTimeFieldType.weekOfWeekyear())) {
      if (!p.isSupported(DateTimeFieldType.dayOfMonth()) && !p.isSupported(DateTimeFieldType.dayOfWeek())) {
        p = p.with(DateTimeFieldType.dayOfWeek(), 1);
        if (p.isSupported(DateTimeFieldType.monthOfYear())) {
          p = p.without(DateTimeFieldType.monthOfYear());
        }
      }
      useWeek = true;
    }
    Partial p2 = useWeek? EMPTY_ISO_WEEK_PARTIAL:EMPTY_ISO_PARTIAL;
    for (int i = 0; i < p2.size(); i++) {
      DateTimeFieldType fieldType = p2.getFieldType(i);
      if (msf == null || isMoreSpecific(fieldType, msf, p.getChronology())) {
        if (!p.isSupported(fieldType)) {
          if (fieldType == DateTimeFieldType.monthOfYear()) {
            if (p.isSupported(QuarterOfYear)) {
              p = p.with(DateTimeFieldType.monthOfYear(), (p.get(QuarterOfYear)-1)*3+1);
              continue;
            } else if (p.isSupported(HalfYearOfYear)) {
              p = p.with(DateTimeFieldType.monthOfYear(), (p.get(HalfYearOfYear)-1)*6+1);
              continue;
            }
          }
          p = p.with(fieldType, p2.getValue(i));
        }
      }
    }
    if (granularity != null) {
      DurationFieldType mostSpecific = getMostSpecific(granularity);
      p = discardMoreSpecificFields(p, mostSpecific);
    }
    return p;
  }

  public static boolean isCompatible(Partial p1, Partial p2) {
    if (p1 == null) return true;
    if (p2 == null) return true;
    for (int i = 0; i < p1.size(); i++) {
      DateTimeFieldType type = p1.getFieldType(i);
      int v = p1.getValue(i);
      if (JodaTimeUtils.hasField(p2,type)) {
        if (v != p2.get(type)) {
          return false;
        }
      }
    }
    return true;
  }
  // Uses p2 to resolve dow for p1
  public static Partial resolveDowToDay(Partial p1, Partial p2)
  {
    // Discard anything that's more specific than dayOfMonth for p2
    p2 = JodaTimeUtils.discardMoreSpecificFields(p2, DateTimeFieldType.dayOfMonth());
    if (isCompatible(p1,p2)) {
      if (p1.isSupported(DateTimeFieldType.dayOfWeek())) {
        if (!p1.isSupported(DateTimeFieldType.dayOfMonth())) {
          if (p2.isSupported(DateTimeFieldType.dayOfMonth()) && p2.isSupported(DateTimeFieldType.monthOfYear()) && p2.isSupported(DateTimeFieldType.year())) {
            Instant t2 = getInstant(p2);
            DateTime t1 = p1.toDateTime(t2);
            return getPartial(t1.toInstant(), p1.with(DateTimeFieldType.dayOfMonth(), 1)/*.with(DateTimeFieldType.weekOfWeekyear(), 1) */);
          }
        }
      }
    }
    return p1;
  }

  public static Partial withWeekYear(Partial p)
  {
    Partial res = new Partial();
    for (int i = 0; i < p.size(); i++) {
      DateTimeFieldType fieldType = p.getFieldType(i);
      if (fieldType == DateTimeFieldType.year()) {
        res = res.with(DateTimeFieldType.weekyear(), p.getValue(i));
      } else {
        res = res.with(fieldType, p.getValue(i));
      }
    }
    return res;
  }

  // Resolve dow for p1
  public static Partial resolveDowToDay(Partial p)
  {
    if (p.isSupported(DateTimeFieldType.dayOfWeek())) {
      if (!p.isSupported(DateTimeFieldType.dayOfMonth())) {
        if (p.isSupported(DateTimeFieldType.weekOfWeekyear()) && (p.isSupported(DateTimeFieldType.year()))) {
          // Convert from year to weekyear (to avoid weirdness when the weekyear and year don't match at the beginning of the year)
          Partial pwy = withWeekYear(p);
          Instant t2 = getInstant(pwy);
          DateTime t1 = pwy.toDateTime(t2);
          Partial res = getPartial(t1.toInstant(), EMPTY_ISO_PARTIAL);
          DateTimeFieldType mostSpecific = getMostSpecific(p);
          res = discardMoreSpecificFields(res, mostSpecific.getDurationType());
          return res;
        }
      }
    }
    return p;
  }

  // Uses p2 to resolve week for p1
  public static Partial resolveWeek(Partial p1, Partial p2)
  {
    if (isCompatible(p1,p2)) {
        if (!p1.isSupported(DateTimeFieldType.dayOfMonth())) {
          if (p2.isSupported(DateTimeFieldType.dayOfMonth()) && p2.isSupported(DateTimeFieldType.monthOfYear()) && p2.isSupported(DateTimeFieldType.year())) {
            Instant t2 = getInstant(p2);
            DateTime t1 = p1.toDateTime(t2);
            return getPartial(t1.toInstant(), p1.without(DateTimeFieldType.dayOfMonth()).without(DateTimeFieldType.monthOfYear()).with(DateTimeFieldType.weekOfWeekyear(), 1));
          }
      }
    }
    return p1;
  }
  public static Partial resolveWeek(Partial p)
  {
    // Figure out week
    if (p.isSupported(DateTimeFieldType.dayOfMonth()) && p.isSupported(DateTimeFieldType.monthOfYear()) && p.isSupported(DateTimeFieldType.year())) {
      Instant t = getInstant(p);
//      return getPartial(t.toInstant(), p.without(DateTimeFieldType.dayOfMonth()).without(DateTimeFieldType.monthOfYear()).with(DateTimeFieldType.weekOfWeekyear(), 1));
      return getPartial(t.toInstant(), p.with(DateTimeFieldType.weekOfWeekyear(), 1));
    } else return p;
  }

  public static Instant getInstant(Partial p)
  {
    return getInstant(p, UTC);
  }


  public static Instant getInstant(Partial p, ZoneId timezone)
  {
    if (p == null) return null;
    int year = p.isSupported(DateTimeFieldType.year())? p.get(DateTimeFieldType.year()):0;
    if (!p.isSupported(DateTimeFieldType.year())) {
      if (p.isSupported(DateTimeFieldType.centuryOfEra())) {
        year += 100*p.get(DateTimeFieldType.centuryOfEra());
      }
      if (p.isSupported(DateTimeFieldType.yearOfCentury())) {
        year += p.get(DateTimeFieldType.yearOfCentury());
      } else if (p.isSupported(DecadeOfCentury)) {
        year += 10*p.get(DecadeOfCentury);
      }
    }
    int moy = p.isSupported(DateTimeFieldType.monthOfYear())? p.get(DateTimeFieldType.monthOfYear()):1;
    if (!p.isSupported(DateTimeFieldType.monthOfYear())) {
      if (p.isSupported(QuarterOfYear)) {
        moy += 3*(p.get(QuarterOfYear)-1);
      }
    }
    int dom = p.isSupported(DateTimeFieldType.dayOfMonth())? p.get(DateTimeFieldType.dayOfMonth()):1;
    int hod = p.isSupported(DateTimeFieldType.hourOfDay())? p.get(DateTimeFieldType.hourOfDay()):0;
    int moh = p.isSupported(DateTimeFieldType.minuteOfHour())? p.get(DateTimeFieldType.minuteOfHour()):0;
    int som = p.isSupported(DateTimeFieldType.secondOfMinute())? p.get(DateTimeFieldType.secondOfMinute()):0;
    int msos = p.isSupported(DateTimeFieldType.millisOfSecond())? p.get(DateTimeFieldType.millisOfSecond()):0;
    return new DateTime(year, moy, dom, hod, moh, som, msos, fromTimezone(timezone)).toInstant();
  }

  private static ISOChronology fromTimezone(ZoneId timezone) {
    if (timezone == UTC) {
      return ISOChronology.getInstanceUTC();
    } else {
      return ISOChronology.getInstance(DateTimeZone.forTimeZone(TimeZone.getTimeZone(timezone)));  // <-- Jesus Christ, Java...
    }
  }

  public static Partial getPartial(Instant t, Partial p)
  {
    Partial res = new Partial(p);
    for (int i = 0; i < p.size(); i++) {
      res = res.withField(p.getFieldType(i), t.get(p.getFieldType(i)));
    }
    return res;
  }

  // Add duration to partial
  public static Partial addForce(Partial p, Period d, int scalar)
  {
    Instant t = getInstant(p);
    t = t.withDurationAdded(d.toDurationFrom(INSTANT_ZERO), scalar);
    return getPartial(t, p);
  }

  // Returns if df1 is more general than df2
  public static boolean isMoreGeneral(DateTimeFieldType df1, DateTimeFieldType df2, Chronology chronology)
  {
    DurationFieldType df1DurationFieldType = df1.getDurationType();
    DurationFieldType df2DurationFieldType = df2.getDurationType();
    if (!df2DurationFieldType.equals(df1DurationFieldType)) {
      DurationField df1Unit = df1DurationFieldType.getField(chronology);
      DurationFieldType p = df2.getRangeDurationType();
      if (p != null) {
        DurationField df2Unit = df2DurationFieldType.getField(chronology);
        int cmp = df1Unit.compareTo(df2Unit);
        if (cmp > 0) {
          return true;
        }
      }
    }
    return false;
  }

  // Returns if df1 is more specific than df2
  public static boolean isMoreSpecific(DateTimeFieldType df1, DateTimeFieldType df2, Chronology chronology)
  {
    DurationFieldType df1DurationFieldType = df1.getDurationType();
    DurationFieldType df2DurationFieldType = df2.getDurationType();
    if (!df2DurationFieldType.equals(df1DurationFieldType)) {
      DurationField df2Unit = df2DurationFieldType.getField(chronology);
      DurationFieldType p = df1.getRangeDurationType();
      if (p != null) {
        DurationField df1Unit = df1DurationFieldType.getField(chronology);
        int cmp = df1Unit.compareTo(df2Unit);
        if (cmp < 0) {
          return true;
        }
      }
    }
    return false;
  }

  private static String zeroPad(int value, int padding){
    StringBuilder b = new StringBuilder();
    b.append(value);
    while(b.length() < padding){
      b.insert(0,"0");
    }
    return b.toString();
  }

  private static boolean noFurtherFields(DateTimeFieldType smallestFieldSet, ReadableDateTime begin, ReadableDateTime end){
    //--Get Indices
    //(standard fields)
    int indexInStandard = -1;
    for(int i=0; i<standardISOFields.length; i++){
      if(standardISOFields[i] == smallestFieldSet){
        indexInStandard = i+1;
      }
    }
    //(week-based fields)
    int indexInWeek = -1;
    for(int i=0; i<standardISOWeekFields.length; i++){
      if(standardISOWeekFields[i] == smallestFieldSet){
        indexInWeek = i+1;
      }
    }
    //(special fields)
    if(smallestFieldSet == QuarterOfYear){
      for(int i=0; i<standardISOFields.length; i++){
        if(standardISOFields[i] == monthOfYear()){
          indexInStandard = i;
        }
      }
    }
    //(get data)
    int index = -1;
    DateTimeFieldType[] toCheck = null;
    if(indexInStandard >= 0){
      index = indexInStandard;
      toCheck = standardISOFields;
    } else if(indexInWeek >= 0){
        index = indexInWeek;
        toCheck = standardISOWeekFields;
    } else {
      throw new IllegalArgumentException("Field is not in my list of fields: " + smallestFieldSet);
    }
    //--Perform Check
    for(int i=index; i<toCheck.length; i++){
      int minValue = minimumValue(toCheck[i], begin) ;
      if(begin.get(toCheck[i]) != minValue || end.get(toCheck[i]) != minValue){
        return false;
      }
    }
    return true;
  }

  /**
   * Return the minimum value of a field, closest to the reference time
   */
  public static int minimumValue(DateTimeFieldType type, ReadableDateTime reference){
    return reference.toDateTime().property(type).getMinimumValue();
  }
  /**
   * Return the maximum value of a field, closest to the reference time
   */
  public static int maximumValue(DateTimeFieldType type, ReadableDateTime reference){
    return reference.toDateTime().property(type).getMaximumValue();
  }

  /**
   * Return the TIMEX string for the time given
   */
  public static String timexTimeValue(ReadableDateTime time){
    return String.valueOf(time.getYear()) + '-' + zeroPad(time.getMonthOfYear(), 2) + '-' + zeroPad(time.getDayOfMonth(), 2) + 'T' + zeroPad(time.getHourOfDay(), 2) + ':' + zeroPad(time.getMinuteOfHour(), 2);
  }

  public static class ConversionOptions{
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

  public static String timexDateValue(ReadableDateTime begin, ReadableDateTime end){
    return timexDateValue(begin,end,new ConversionOptions());
  }

  /**
   * Return the TIMEX string for the range of dates given.
   * For example, (2011-12-3:00:00,000 to 2011-12-4:00:00.000) would give 2011-12-3.
   * @param begin The begin time for the timex
   * @param end The end time for the timex
   * @param opts Tweaks in the heuristic conversion
   * @return The string representation of a DATE type Timex3 expression
   */
  public static String timexDateValue(ReadableDateTime begin, ReadableDateTime end, ConversionOptions opts){
    //--Special Cases
    if( begin.getYear() < -100000){
      return "PAST_REF";
    } else if(end.getYear() > 100000){
      return "FUTURE_REF";
    } else if(begin.equals(end)){
      return timexTimeValue(begin);
    }
    StringBuilder value = new StringBuilder();
    boolean shouldBeDone = false;
    //--Differences
    int monthDiff = (end.getMonthOfYear() - begin.getMonthOfYear()) + (end.getYear()-begin.getYear())*12;
    int weekDiff = end.getWeekOfWeekyear()-begin.getWeekOfWeekyear() + (end.getYear()-begin.getYear())*maximumValue(weekOfWeekyear(),begin);
    int dayDiff = end.getDayOfMonth()-begin.getDayOfMonth() + monthDiff*maximumValue(dayOfMonth(),begin);
    int hrDiff = end.getHourOfDay()-begin.getHourOfDay() + dayDiff*24;
    int minDiff = end.getMinuteOfHour()-begin.getMinuteOfHour() + hrDiff*60;
    int secDiff = end.getSecondOfMinute()-begin.getSecondOfMinute() + minDiff*60;
    //--Years
    if(noFurtherFields(year(), begin, end)){
      int diff = end.getYear()-begin.getYear();
      if(diff == 100 && (opts.forceDate || begin.getYear() % 100 == 0)){
        //(case: century)
        value.append(( begin.getYear() / 100)).append("XX");
      } else if(diff == 10 && (opts.forceDate || begin.getYear() % 10 == 0)){
        //(case: decade)
        value.append(( begin.getYear() / 10));
      } else if(diff == 1 || opts.forceDate){
        //(case: year)
        value.append(begin.getYear());
      } else {
        //(case: duration)
        return timexDurationValue(begin,end);
      }
      return value.toString();
    } else if(monthDiff < 12 || opts.forceDate) {
      //(case: year and more)
      value.append(begin.getYear());
    } else {
      //(case: treat as duration)
      return timexDurationValue(begin, end);
    }
    //--Week/Month/Quarters
    value.append("-");
    if(noFurtherFields(monthOfYear(), begin, end) || noFurtherFields(weekOfWeekyear(), begin, end)){
      boolean monthTerminal = noFurtherFields(monthOfYear(), begin, end);
      boolean weekTerminal = noFurtherFields(weekOfWeekyear(), begin, end);
      //(Month/Quarter)
      if(monthTerminal && monthDiff == 6 && (begin.getMonthOfYear()-1) % 6 == 0){
        //(case: half of year)
        value.append("H").append(( begin.getMonthOfYear()-1) / 6 + 1);
      } else if(monthTerminal && monthDiff == 3 && (begin.getMonthOfYear()-1) % 3 == 0){
        //(case: quarter of year)
        value.append("Q").append(( begin.getMonthOfYear()-1) / 3 + 1);
      } else if(monthTerminal && monthDiff == 3 && begin.getMonthOfYear() % 3 == 0){
        //(case: season)
        switch( begin.getMonthOfYear() ){
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
      } else if(weekTerminal && weekDiff == 1) {
        //(case: a week)
        value.append("W").append(zeroPad(begin.getWeekOfWeekyear(), 2));
      } else if(monthTerminal && monthDiff == 1 && weekDiff != 1 || opts.forceDate) {
        //(case: a month)
        value.append( zeroPad(begin.getMonthOfYear(),2) );
      } else {
        //(case: treat as duration)
        return timexDurationValue(begin, end);
      }
      return value.toString();
    } else if(noFurtherFields(dayOfWeek(), begin, end) && dayDiff == 2 && begin.getDayOfWeek() == 6){
      //(case: a weekend)
      value.append("W").append(zeroPad(begin.getWeekOfWeekyear(),2)).append("-WE");
      return value.toString();
    } else if(dayDiff < maximumValue(dayOfMonth(),begin) || opts.forceDate) {
      //(case: month and more)
      value.append(zeroPad(begin.getMonthOfYear(),2));
    } else {
      //(case: treat as duration)
      return timexDurationValue(begin, end);
    }
    //--Weekday/Day
    value.append("-");
    if(noFurtherFields(dayOfMonth(), begin, end)){
      if(dayDiff == 1 || opts.forceDate){
        //(case: a day)
        value.append(zeroPad(begin.getDayOfMonth(),2));
      } else {
        //(case: treat as duration)
        return timexDurationValue(begin, end);
      }
      return value.toString();
    } else if(hrDiff < 24 || opts.forceDate){
      //(case: day and more)
      value.append(zeroPad(begin.getDayOfMonth(),2));
    } else {
      //(case: treat as duration)
      return timexDurationValue(begin, end);
    }
    //--Hour/TimeOfDay
    value.append("T");
    if(noFurtherFields(hourOfDay(),begin,end)){
      //((case: half day)
      if(hrDiff == 12 && begin.getHourOfDay() == 0){
        value.append("H1");
      } else if(hrDiff == 12 && begin.getHourOfDay() == 12){
        value.append("H2");
      //(case: time of day)
      }else if(hrDiff == 4 && begin.getHourOfDay() == 8){
        value.append("MO");
      }else if(hrDiff == 4 && begin.getHourOfDay() == 12){
        value.append("AF");
      }else if(hrDiff == 4 && begin.getHourOfDay() == 16){
        value.append("EV");
      }else if(hrDiff == 4 && begin.getHourOfDay() == 20){
        value.append("NI");
      } else if(hrDiff == 1 || opts.forceDate){
        //(case: an hour)
        value.append(zeroPad(begin.getHourOfDay()+1,2));
      } else {
        //(case: treat as duration)
        return timexDurationValue(begin,end);
      }
      return value.toString();
    } else if(minDiff <= 60 || opts.forceDate){
      //(case: hour and more)
      value.append(zeroPad(begin.getHourOfDay(),2));
    } else {
      //(case: treat as duration)
      return timexDurationValue(begin, end);
    }
    //--Minute/Second
    value.append(":");
    value.append(zeroPad(begin.getMinuteOfHour(),2));
    return value.toString();
  }

  private static boolean consistentWithForced(String cand, String[] forcedList){
    //--Check If Forced
    for(String forced : forcedList){
      if(forced.equals(cand)){ return true; }
    }
    //--Get Ordering
    String[] ordering = {"L","C","E","Y","Q","M","W","D","H","m","S"};
    int candIndex = -1;
    for(int i=0; i<ordering.length; i++){
      if(ordering[i].equals(cand)){
        candIndex = i;
        break;
      }
    }
    assert candIndex >= 0;
    //--Check If Lower Priority Forced
    for(int candI=candIndex+1; candI < ordering.length; candI++){
      for(String forced : forcedList){
        if(ordering[candI].equals(forced)){
          return false;
        }
      }
    }
    //--OK
    return true;
  }

  /**
   * Return the TIMEX string for the duration represented by the given period; approximately if
   * approximate is set to true.
   * @param duration The JodaTime period representing this duration
   * @param opts Options for the conversion (e.g., mark duration as approximates)
   * @return The string representation of a DURATION type Timex3 expression
   */
  public static String timexDurationValue(ReadablePeriod duration, ConversionOptions opts){
    StringBuilder b = new StringBuilder().append("P");
    boolean seenTime = false;
    int years = duration.get(years());
    //(millenia)
    if(years >= 1000 && consistentWithForced("L",opts.forceUnits)){
      b.append(opts.approximate ? "X" : years / 1000).append("L");
      years = years % 1000;
    }
    //(centuries)
    if(years >= 100 && consistentWithForced("C", opts.forceUnits)){
      b.append(opts.approximate ? "X" : years / 100).append("C");
      years = years % 100;
    }
    //(decades)
    if(years >= 10 && consistentWithForced("E", opts.forceUnits)){
      b.append(opts.approximate ? "X" : years / 10).append("E");
      years = years % 10;
    }
    //(years)
    if(years != 0 && consistentWithForced("Y", opts.forceUnits)){
      b.append(opts.approximate ? "X" : years).append("Y");
    }
    //(months)
    int months = duration.get(months());
    if(months != 0){
      if(months % 3 == 0 && consistentWithForced("Q", opts.forceUnits)){
        b.append(opts.approximate ? "X" : months / 3).append("Q");
        months = months % 3;
      } else {
        b.append(opts.approximate ? "X" : months).append("M");
      }
    }
    //(weeks)
    if(duration.get(weeks()) != 0){
      b.append(opts.approximate ? "X" : duration.get(weeks())).append("W");
    }
    //(days)
    if(duration.get(days()) != 0){
      b.append(opts.approximate ? "X" : duration.get(days())).append("D");
    }
    //(hours)
    if(duration.get(hours()) != 0){
      if(!seenTime){ b.append("T"); seenTime = true; }
      b.append(opts.approximate ? "X" : duration.get(hours())).append("H");
    }
    //(minutes)
    if(duration.get(minutes()) != 0){
      if(!seenTime){ b.append("T"); seenTime = true; }
      b.append(opts.approximate ? "X" : duration.get(minutes())).append("M");
    }
    //(seconds)
    if(duration.get(seconds()) != 0){
      if(!seenTime){ b.append("T"); seenTime = true; }
      b.append(opts.approximate ? "X" : duration.get(seconds())).append("S");
    }
    return b.toString();
  }
  public static String timexDurationValue(ReadablePeriod duration){ return timexDurationValue(duration, new ConversionOptions()); }

  /**
   * Return the TIMEX string for the difference between two dates
   * TODO not really sure if this works...
   */
  public static String timexDurationValue(ReadableDateTime begin, ReadableDateTime end){
    return timexDurationValue( new Period(end.getMillis()-begin.getMillis()) );
  }

}
