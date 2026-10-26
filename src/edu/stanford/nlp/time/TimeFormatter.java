package edu.stanford.nlp.time;

import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.tokensregex.*;
import edu.stanford.nlp.ling.tokensregex.types.Expression;
import edu.stanford.nlp.ling.tokensregex.types.Expressions;
import edu.stanford.nlp.ling.tokensregex.types.Value;
import edu.stanford.nlp.util.CollectionValuedMap;
import edu.stanford.nlp.util.CoreMap;
import java.util.function.Function;
import edu.stanford.nlp.util.Generics;
import org.threeten.extra.PartialTemporal;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAccessor;
import java.time.temporal.TemporalQueries;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.TextStyle;
import java.time.temporal.ChronoField;
import java.time.temporal.TemporalField;
import java.time.temporal.ValueRange;
import java.time.temporal.WeekFields;

import java.lang.reflect.Method;
import java.text.SimpleDateFormat;
import java.time.Year;
import java.util.*;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Time specific patterns and formatting
 *
 * @author Angel Chang
 */
public class TimeFormatter {

  private static final int MILLIS_PER_SECOND = 1000;
  private static final int MILLIS_PER_MINUTE = 60 * MILLIS_PER_SECOND;
  private static final int MILLIS_PER_HOUR = 60 * MILLIS_PER_MINUTE;

  private TimeFormatter() {} // static methods/classes


  public static class JavaDateFormatExtractor implements
          Function<CoreMap, Value> {

    private static final Class<CoreAnnotations.TextAnnotation> textAnnotationField = CoreAnnotations.TextAnnotation.class;
    private final SimpleDateFormat format;

    public JavaDateFormatExtractor(String pattern) {
      this.format = new SimpleDateFormat(pattern);
    }

    @Override
    public Value apply(CoreMap m) {
      try {
        // TODO: Allow specification of locale, pivot year (set2DigitYearStart) for interpreting 2 digit years
        String str = m.get(textAnnotationField);
        Date d = format.parse(str);
        return new Expressions.PrimitiveValue("GroundedTime", new SUTime.GroundedTime(java.time.Instant.ofEpochMilli(d.getTime())));
      } catch (java.text.ParseException ex) {
        return null;
      }
    }

  }


  /**
   * Reads a time from text with a {@link DateTimeFormatter}, giving a grounded time.
   *
   * <p>A pattern that names no time of day is read as the start of the day, and one that
   * names no zone is read in the zone the formatter carries, or the default zone if it
   * carries none. Text the pattern does not match gives null rather than throwing.
   */
  public static class DateTimeFormatExtractor implements
          Function<CoreMap, Value> {

    private static final Class<CoreAnnotations.TextAnnotation> textAnnotationField = CoreAnnotations.TextAnnotation.class;
    private final DateTimeFormatter formatter;

    public DateTimeFormatExtractor(DateTimeFormatter formatter) {
      this.formatter = formatter;
    }

    public DateTimeFormatExtractor(String pattern) {
      this.formatter = DateTimeFormatter.ofPattern(pattern);
    }

    @Override
    public Value apply(CoreMap m) {
      try {
        String str = m.get(textAnnotationField);
        // TODO: Allow specification of pivot year for interpreting 2 digit years
        TemporalAccessor parsed = formatter.parse(str);
        LocalDate date = parsed.query(TemporalQueries.localDate());
        if (date == null) {
          return null;
        }
        LocalTime time = parsed.query(TemporalQueries.localTime());
        if (time == null) {
          time = LocalTime.MIDNIGHT;
        }
        ZoneId zone = parsed.query(TemporalQueries.zone());
        if (zone == null) {
          zone = ZoneId.systemDefault();
        }
        return new Expressions.PrimitiveValue("GroundedTime",
                new SUTime.GroundedTime(ZonedDateTime.of(date, time, zone).toInstant()));
      } catch (DateTimeException | IllegalArgumentException ex) {
        return null;
      }
    }
  }


  static class ApplyActionWrapper<I,O> implements Function<I,O> {

    private final Env env;
    private final Function<I,O> base;
    private final Expression action;

    ApplyActionWrapper(Env env, Function<I,O> base, Expression action) {
      this.env = env;
      this.base = base;
      this.action = action;
    }

    @Override
    public O apply(I in) {
      O v = base.apply(in);
      if (action != null) {
        action.evaluate(env, v);
      }
      return v;
    }

  }


  static class TimePatternExtractRuleCreator extends SequenceMatchRules.AnnotationExtractRuleCreator {

    private static void updateExtractRule(SequenceMatchRules.AnnotationExtractRule r,
                                     Env env,
                                     Pattern pattern,
                                     Function<String, Value> extractor) {
      MatchedExpression.SingleAnnotationExtractor annotationExtractor = SequenceMatchRules.createAnnotationExtractor(env,r);
      annotationExtractor.valueExtractor =
              new SequenceMatchRules.CoreMapFunctionApplier<>(
                      env, r.annotationField,
                      extractor);
      r.extractRule = new SequenceMatchRules.CoreMapExtractRule<>(
              env, r.annotationField,
              new SequenceMatchRules.StringPatternExtractRule<>(pattern,
                      new SequenceMatchRules.StringMatchedExpressionExtractor(annotationExtractor, r.matchedExpressionGroup)));
      r.filterRule = new SequenceMatchRules.AnnotationMatchedFilter(annotationExtractor);
      r.pattern = pattern;
    }

    private static void updateExtractRule(SequenceMatchRules.AnnotationExtractRule r,
                                     Env env,
                                     Function<CoreMap, Value> extractor) {
      MatchedExpression.SingleAnnotationExtractor annotationExtractor = SequenceMatchRules.createAnnotationExtractor(env,r);
      annotationExtractor.valueExtractor = extractor;
      r.extractRule = new SequenceMatchRules.CoreMapExtractRule<>(
              env, r.annotationField,
              new SequenceMatchRules.BasicSequenceExtractRule(annotationExtractor));
      r.filterRule = new SequenceMatchRules.AnnotationMatchedFilter(annotationExtractor);
    }

    @Override
    public SequenceMatchRules.AnnotationExtractRule create(Env env, Map<String,Object> attributes) {
      SequenceMatchRules.AnnotationExtractRule r = super.create(env, attributes);
      if (r.ruleType == null) { r.ruleType = "time"; }
      String expr = Expressions.asObject(env, attributes.get("pattern"));
      String formatter = Expressions.asObject(env, attributes.get("formatter"));
      Expression action = Expressions.asExpression(env, attributes.get("action"));
      String localeString = Expressions.asObject(env, attributes.get("locale"));
      r.pattern = expr;
      if (formatter == null) {
        if (r.annotationField == null) { r.annotationField = EnvLookup.getDefaultTextAnnotationKey(env);  }
        /* Parse pattern and figure out what the result should be.... */
        CustomDateFormatExtractor formatExtractor = new CustomDateFormatExtractor(expr, localeString);
        //SequenceMatchRules.Expression result = (SequenceMatchRules.Expression) attributes.get("result");
        updateExtractRule(r, env, formatExtractor.getTextPattern(), new ApplyActionWrapper<>(env, formatExtractor, action));
      } else if ("java.time.format.DateTimeFormatter".equals(formatter)) {
        if (r.annotationField == null) { r.annotationField = r.tokensAnnotationField;  }
        updateExtractRule(r, env, new ApplyActionWrapper<>(env, new DateTimeFormatExtractor(expr), action));
      } else if ("org.joda.time.format.DateTimeFormat".equals(formatter)
              || "org.joda.time.format.ISODateTimeFormat".equals(formatter)) {
        throw new IllegalArgumentException("Formatter " + formatter + " is no longer available."
                + " Use formatter = \"java.time.format.DateTimeFormatter\" with a"
                + " java.time pattern, which differs from joda's in places.");
      } else if ("java.text.SimpleDateFormat".equals(formatter)) {
        if (r.annotationField == null) { r.annotationField = r.tokensAnnotationField;  }
        updateExtractRule(r, env, new ApplyActionWrapper<>(env, new JavaDateFormatExtractor(expr), action));
      } else {
        throw new IllegalArgumentException("Unsupported formatter: " + formatter);
      }
      return r;
    }
  }

  /*
   * Rules for parsing time specific patterns.
   * Patterns are similar to time patterns used by JodaTime combined with a simplified regex expression
   *
   # y       year                         year          1996                         y
   # M       month of year                month         July; Jul; 07                M
   # d       day of month                 number        10                           d
   # H       hour of day (0~23)           number        0                            H
   # k       clockhour of day (1~24)      number        24                           k
   # m       minute of hour               number        30                           m
   # s       second of minute             number        55                           s
   # S       fraction of second           number        978                          S (Millisecond)
   # a       half day of day marker       am/pm
   */

  /**
   * Converts time string pattern to text pattern.
   */
  public static class CustomDateFormatExtractor implements Function<String, Value> {

    private final FormatterBuilder builder;
    private final String timePattern;
    private final Pattern textPattern;

    public CustomDateFormatExtractor(String timePattern, String localeString) {
      Locale locale = (localeString != null)? new Locale(localeString): Locale.getDefault();
      this.timePattern = timePattern;
      builder = new FormatterBuilder();
      builder.locale = locale;
      parsePatternTo(builder, timePattern);
      textPattern = builder.toTextPattern();
    }

    public Pattern getTextPattern()
    {
      return textPattern;
    }

    public Value apply(String str) {
      Value v = null;
      Matcher m = textPattern.matcher(str);
      if (m.matches()) {
        return apply(m);
      }
      return v;
    }

    public Value apply(MatchResult m) {
      SUTime.Temporal t = new SUTime.PartialTime();
      for (FormatComponent fc:builder.pieces) {
        int group = fc.getGroup();
        if (group > 0) {
          String fieldValueStr = m.group(group);
          if (fieldValueStr != null) {
            try {
              t = fc.updateTemporal(t, fieldValueStr);
            } catch (IllegalArgumentException ex) {
              return null;
            }
          }
        }
      }
      return new Expressions.PrimitiveValue("Temporal", t);
    }

  }


  private abstract static class FormatComponent {

    int group = -1;
    String quantifier = null;

    public void appendQuantifier(String str) {
      if (quantifier != null) {
        quantifier = quantifier + str;
      } else {
        quantifier = str;
      }
    }

    public StringBuilder appendRegex(StringBuilder sb) {
      if (group > 0) {
        sb.append('(');
      }
      appendRegex0(sb);
      if (quantifier != null) {
        sb.append(quantifier);
      }
      if (group > 0) {
        sb.append(')');
      }
      return sb;
    }
    protected abstract StringBuilder appendRegex0(StringBuilder sb);

    public SUTime.Temporal updateTemporal(SUTime.Temporal t, String fieldValueStr) { return t; }
    public int getGroup() { return group; }

  }

  private abstract static class DateTimeFieldComponent extends FormatComponent {

    TemporalField fieldType;

    public Integer parseValue(String str) { return null; }
    public TemporalField getDateTimeFieldType() { return fieldType; }

    public SUTime.Temporal updateTemporal(SUTime.Temporal t, String fieldValueStr) {
      TemporalField dt = getDateTimeFieldType();
      if (fieldValueStr != null && dt != null) {
        Integer v = parseValue(fieldValueStr);
        if (v != null) {
          PartialTemporal pt = PartialTemporalUtils.setField(null, dt, v);
          t = t.intersect(new SUTime.PartialTime(pt));
        } else {
          throw new IllegalArgumentException("Cannot interpret " + fieldValueStr + " for " + fieldType);
        }
      }
      return t;
    }

  }


  private static class NumericDateComponent extends DateTimeFieldComponent {

    private final int minValue;
    private final int maxValue;
    private final int minDigits;
    private final int maxDigits;

    public NumericDateComponent(TemporalField fieldType, int minDigits, int maxDigits) {
      this.fieldType = fieldType;
      this.minDigits = minDigits;
      this.maxDigits = maxDigits;
      ValueRange range = fieldType.range();
      minValue = (int) Math.max(range.getMinimum(), Integer.MIN_VALUE);
      maxValue = (int) Math.min(range.getMaximum(), Integer.MAX_VALUE);
    }

    protected StringBuilder appendRegex0(StringBuilder sb) {
      if (minDigits == 1 && maxDigits == 1 && quantifier == null) {
        // A one-character field token means "one or two digits", as it does in
        // SimpleDateFormat. When the pattern supplies its own quantifier that
        // quantifier is appended to this regex, so leave the width alone and let
        // the pattern say what it wants.
        sb.append("\\d{1,2}");
      } else if (maxDigits > 5 || minDigits != maxDigits) {
        sb.append("\\d{").append(minDigits).append(',').append(maxDigits).append('}');
      } else {
        for (int i = 0; i < minDigits; i++) {
          sb.append("\\d");
        }
      }
      return sb;
    }

    public Integer parseValue(String str) {
      int v = Integer.parseInt(str);
      if (v >= minValue && v <= maxValue) {
        return v;
      } else {
        return null;
      }
    }

  }


  private static class RelaxedNumericDateComponent extends FormatComponent
  {
    NumericDateComponent[] possibleNumericDateComponents;
    int minDigits;
    int maxDigits;

    public RelaxedNumericDateComponent(TemporalField[] fieldTypes, int minDigits, int maxDigits)
    {
      this.minDigits = minDigits;
      this.maxDigits = maxDigits;
      possibleNumericDateComponents = new NumericDateComponent[fieldTypes.length];
      for (int i = 0; i < fieldTypes.length; i++) {
        possibleNumericDateComponents[i] = new NumericDateComponent(fieldTypes[i], minDigits, maxDigits);
      }
    }

    protected StringBuilder appendRegex0(StringBuilder sb) {
      if (maxDigits > 5 || minDigits != maxDigits) {
        sb.append("\\d{").append(minDigits).append(",").append(maxDigits).append("}");
      } else {
        for (int i = 0; i < minDigits; i++) {
          sb.append("\\d");
        }
      }
      return sb;
    }

    public SUTime.Temporal updateTemporal(SUTime.Temporal t, String fieldValueStr) {
      if (fieldValueStr != null) {
        for (NumericDateComponent c:possibleNumericDateComponents) {
          Integer v = c.parseValue(fieldValueStr);
          if (v != null) {
            t = c.updateTemporal(t, fieldValueStr);
            return t;
          }
        }
        throw new IllegalArgumentException("Cannot interpret " + fieldValueStr);
      }
      return t;
    }
  }

  private static final Comparator<String> STRING_LENGTH_REV_COMPARATOR = (o1, o2) -> {
    if (o1.length() > o2.length()) return -1;
    else if (o1.length() < o2.length()) return 1;
    else {
      return o1.compareToIgnoreCase(o2);
    }
  };


  private static class TextDateComponent extends DateTimeFieldComponent {

    Map<String, Integer> valueMapping;
    List<String> validValues;
    Locale locale;
    int minValue;
    int maxValue;
    Boolean isShort;

    public TextDateComponent() {}

    public TextDateComponent(TemporalField fieldType, Locale locale, Boolean isShort) {
      this.fieldType = fieldType;
      this.locale = locale;
      this.isShort = isShort;

      ValueRange range = fieldType.range();
      minValue = (int) range.getMinimum();
      maxValue = (int) range.getMaximum();
      this.validValues = new ArrayList<>(maxValue - minValue + 1);
      this.valueMapping = Generics.newHashMap();
      DateTimeFormatter shortText = textFormatter(fieldType, TextStyle.SHORT, locale);
      DateTimeFormatter longText = textFormatter(fieldType, TextStyle.FULL, locale);
      for (int i = minValue; i <= maxValue; i++) {
        PartialTemporal value = PartialTemporal.of(fieldType, i);
        if (isShort == null || isShort) {
          addValue(value.format(shortText), i);
        }
        if (isShort == null || !isShort) {
          addValue(value.format(longText), i);
        }
      }
      // Order by length for regex
      Collections.sort(validValues, STRING_LENGTH_REV_COMPARATOR);
    }

    /**
     * A formatter for the text of one field in a locale. An era is always written in its
     * short form, because that is the only form joda had for it, so a pattern asking for
     * the long form still matches BC and AD rather than Before Christ and Anno Domini.
     */
    private static DateTimeFormatter textFormatter(TemporalField field, TextStyle style,
                                                   Locale locale) {
      TextStyle actual = (field == ChronoField.ERA) ? TextStyle.SHORT : style;
      return new DateTimeFormatterBuilder().appendText(field, actual).toFormatter(locale);
    }

    public void addValue(String str, int v) {
      validValues.add(str);
      valueMapping.put(str.toLowerCase(locale), v);
    }

    @Override
    public Integer parseValue(String str) {
      str = str.toLowerCase(locale);
      Integer v = valueMapping.get(str);
      return v;
    }

    @Override
    protected StringBuilder appendRegex0(StringBuilder sb) {
      boolean first = true;
      for (String v:validValues) {
        if (first) {
          first = false;
        } else {
          sb.append("|");
        }
        sb.append(Pattern.quote(v));
      }
      return sb;
    }
  }

  private static class TimeZoneOffsetComponent extends FormatComponent
  {
    String zeroOffsetParseText;  // Text indicating timezone offset is zero

    // TimezoneOffset is + or - followed by
    // hh
    // hhmm
    // hhmmss
    // hhmmssSSS
    // hh:mm
    // hh:mm:ss
    // hh:mm:ss.SSS
    public TimeZoneOffsetComponent(String zeroOffsetParseText)
    {
      this.zeroOffsetParseText = zeroOffsetParseText;
    }

    protected StringBuilder appendRegex0(StringBuilder sb) {
      sb.append("[+-]\\d\\d(?::?\\d\\d(?::?\\d\\d(?:[.,]?\\d{1,3})?)?)?");
      if (zeroOffsetParseText != null) {
        sb.append("|").append(Pattern.quote(zeroOffsetParseText));
      }
      return sb;
    }

    private static int parseInteger(String str, int pos, int length) {
      return Integer.parseInt(str.substring(pos, pos+length));
    }

    public int parseOffsetMillis(String str) {
      int offset = 0;
      if (zeroOffsetParseText != null && str.equalsIgnoreCase(zeroOffsetParseText)) {
        return offset;
      }
      boolean negative = false;
      if (str.startsWith("+")) {
      } else if (str.startsWith("-")) {
        negative = true;
      } else {
        throw new IllegalArgumentException("Invalid date time zone offset " + str);
      }
      int pos = 1;
      // Parse hours
      offset += MILLIS_PER_HOUR * parseInteger(str, pos, 2);
      pos += 2;
      if (pos < str.length()) {
        // Parse minutes
        if (!Character.isDigit(str.charAt(pos))) { pos++; }
        offset += MILLIS_PER_MINUTE * parseInteger(str, pos, 2);
        pos += 2;
        if (pos < str.length()) {
          // Parse seconds
          if (!Character.isDigit(str.charAt(pos))) { pos++; }
          offset += MILLIS_PER_SECOND * parseInteger(str, pos, 2);
          pos += 2;
          if (pos < str.length()) {
            // Parse fraction of seconds
            if (!Character.isDigit(str.charAt(pos))) { pos++; }
            int digits = str.length()-pos;
            if (digits > 0) {
              if (digits <= 3) {
                int frac = parseInteger(str, pos, digits);
                if (digits == 1) {
                  offset += frac*100;
                } else if (digits == 2) {
                  offset += frac*10;
                } else if (digits == 3) {
                  offset += frac;
                }
              } else {
                throw new IllegalArgumentException("Invalid date time zone offset " + str);
              }
            }
          }
        }
      }
      if (negative) offset = -offset;
      return offset;
    }
    public SUTime.Temporal updateTemporal(SUTime.Temporal t, String fieldValueStr) {
      int offset = parseOffsetMillis(fieldValueStr);
      return t.setTimeZone(ZoneOffset.ofTotalSeconds(offset / 1000));
    }
  }

  private static String makeRegex(List<String> strs) {
    StringBuilder sb = new StringBuilder();
    boolean first = true;
    for (String v:strs) {
      if (first) {
        first = false;
      } else {
        sb.append("|");
      }
      sb.append(Pattern.quote(v));
    }
    return sb.toString();
  }

  // Timezones
  //  ID - US/Pacific
  //  Name - Pacific Standard Time (or Pacific Daylight Time)
  //  ShortName  PST (or PDT depending on input milliseconds)
  //  NameKey    PST (or PDT depending on input milliseconds)
  private static class TimeZoneIdComponent extends FormatComponent
  {
    static final Map<String, ZoneId> timeZonesById;
    static final List<String> timeZoneIds;
    static final String timeZoneIdsRegex;
    static {
      timeZoneIds = new ArrayList<>(ZoneId.getAvailableZoneIds());
      timeZonesById = Generics.newHashMap();
      for (String str:timeZoneIds) {
        ZoneId dtz = ZoneId.of(str);
        timeZonesById.put(str.toLowerCase(), dtz);
//        System.out.println(str);
//        long time = System.currentTimeMillis();
//        System.out.println(dtz.getShortName(time));
//        System.out.println(dtz.getName(time));
//        System.out.println(dtz.getNameKey(time));
//        System.out.println();
      }
      // Order by length for regex
      Collections.sort(timeZoneIds, STRING_LENGTH_REV_COMPARATOR);
      timeZoneIdsRegex = makeRegex(timeZoneIds);
    }

    public TimeZoneIdComponent()
    {
    }

    private static ZoneId parseDateTimeZone(String str) {
      str = str.toLowerCase();
      return timeZonesById.get(str);
    }

    protected StringBuilder appendRegex0(StringBuilder sb) {
      sb.append(timeZoneIdsRegex);
      return sb;
    }

    public SUTime.Temporal updateTemporal(SUTime.Temporal t, String fieldValueStr) {
      if (fieldValueStr != null) {
        ZoneId dtz = parseDateTimeZone(fieldValueStr);
        return t.setTimeZone(dtz);
      }
      return t;
    }
  }

  private static class TimeZoneComponent extends FormatComponent
  {
    Locale locale;

    static Map<Locale, CollectionValuedMap<String, ZoneId>> timeZonesByName = Generics.newHashMap();
    static Map<Locale, List<String>> timeZoneNames = Generics.newHashMap();
    static Map<Locale, String> timeZoneRegexes = Generics.newHashMap();

    public TimeZoneComponent(Locale locale)
    {
      this.locale = locale;
      synchronized (TimeZoneComponent.class) {
        String regex = timeZoneRegexes.get(locale);
        if (regex == null) {
          updateTimeZoneNames(locale);
        }
      }
    }

    private static void updateTimeZoneNames(Locale locale) {
      CollectionValuedMap<String,ZoneId> tzMap = new CollectionValuedMap<>();
      for (ZoneId dtz:TimeZoneIdComponent.timeZonesById.values()) {
        TimeZone tz = TimeZone.getTimeZone(dtz);
        // the standard names, and the daylight saving ones for zones that have them
        tzMap.add(tz.getDisplayName(false, TimeZone.SHORT, locale).toLowerCase(), dtz);
        tzMap.add(tz.getDisplayName(false, TimeZone.LONG, locale).toLowerCase(), dtz);
        tzMap.add(tz.getDisplayName(true, TimeZone.SHORT, locale).toLowerCase(), dtz);
        tzMap.add(tz.getDisplayName(true, TimeZone.LONG, locale).toLowerCase(), dtz);
      }
      // Order by length for regex
      List<String> tzNames = new ArrayList<>(tzMap.keySet());
      Collections.sort(tzNames, STRING_LENGTH_REV_COMPARATOR);
      String tzRegex = makeRegex(tzNames);
      synchronized (TimeZoneComponent.class) {
        timeZoneNames.put(locale,tzNames);
        timeZonesByName.put(locale,tzMap);
        timeZoneRegexes.put(locale,tzRegex);
      }
    }

    public ZoneId parseDateTimeZone(String str) {
      // TODO: do something about these multiple timezones that match the same name...
      // pick one based on location
      str = str.toLowerCase();
      CollectionValuedMap<String,ZoneId> tzMap = timeZonesByName.get(locale);
      Collection<ZoneId> v = tzMap.get(str);
      if (v == null || v.isEmpty()) return null;
      else return v.iterator().next();
    }

    protected StringBuilder appendRegex0(StringBuilder sb) {
      String regex = timeZoneRegexes.get(locale);
      sb.append(regex);
      return sb;
    }

    public SUTime.Temporal updateTemporal(SUTime.Temporal t, String fieldValueStr) {
      if (fieldValueStr != null) {
        ZoneId dtz = parseDateTimeZone(fieldValueStr);
        return t.setTimeZone(dtz);
      }
      return t;
    }
  }


  private static class LiteralComponent extends FormatComponent {

    private final String text;

    public LiteralComponent(String str) {
      this.text = str;
    }

    @Override
    protected StringBuilder appendRegex0(StringBuilder sb) {
      sb.append(Pattern.quote(text));
      return sb;
    }

  }


  private static class RegexComponent extends FormatComponent {

    private final String regex;

    public RegexComponent(String regex) {
      this.regex = regex;
    }

    @Override
    protected StringBuilder appendRegex0(StringBuilder sb) {
      sb.append(regex);
      return sb;
    }

  }


  private static class FormatterBuilder {

    boolean useRelaxedHour = true;
    Locale locale;
    List<FormatComponent> pieces = new ArrayList<>();
    int curGroup = 0;

    public String toTextRegex() {
      StringBuilder sb = new StringBuilder();
      sb.append("\\b");
      for (FormatComponent fc:pieces) {
        fc.appendRegex(sb);
      }
      sb.append("\\b");
      return sb.toString();
    }

    public Pattern toTextPattern() {
      return Pattern.compile(toTextRegex(), Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    }

    private void appendNumericFields(TemporalField[] fieldTypes, int digits) {
      appendNumericFields(fieldTypes, digits, digits);
    }

    private void appendNumericFields(TemporalField[] fieldTypes, int minDigits, int maxDigits) {
      appendComponent(new RelaxedNumericDateComponent(fieldTypes, minDigits, maxDigits), true);
    }

    private void appendNumericField(TemporalField fieldType, int digits) {
      appendNumericField(fieldType, digits, digits);
    }

    private void appendNumericField(TemporalField fieldType, int minDigits, int maxDigits) {
      appendComponent(new NumericDateComponent(fieldType, minDigits, maxDigits), true);
    }

    private void appendTextField(TemporalField fieldType, boolean isShort) {
      appendComponent(new TextDateComponent(fieldType, locale, isShort), true);
    }

    private void appendComponent(FormatComponent fc, boolean hasGroup)
    {
      if (hasGroup) {
        fc.group = ++curGroup;
      }
      pieces.add(fc);
    }

    private void appendLiteralField(String s) {
      appendComponent(new LiteralComponent(s), false);
    }

    private void appendRegexPart(String s) {
      appendComponent(new RegexComponent(s), false);
    }

    protected void appendEraText() {
      appendTextField(ChronoField.ERA, false);
    }
    protected void appendCenturyOfEra(int minDigits, int maxDigits) {
      appendNumericField(SUTimeFields.CENTURY_OF_ERA, minDigits, maxDigits);
    }
    protected void appendYearOfEra(int minDigits, int maxDigits) {
      appendNumericField(ChronoField.YEAR_OF_ERA, minDigits, maxDigits);
    }
    protected void appendYear(int minDigits, int maxDigits) {
      appendNumericField(ChronoField.YEAR, minDigits, maxDigits);
    }
    protected void appendTwoDigitYear(int pivot, boolean lenient) {
      appendNumericField(SUTimeFields.YEAR_OF_CENTURY, 2);
    }
    protected void appendWeekyear(int minDigits, int maxDigits) {
      appendNumericField(WeekFields.ISO.weekBasedYear(), minDigits, maxDigits);
    }
    protected void appendTwoDigitWeekyear(int pivot, boolean lenient) {
      appendNumericField(SUTimeFields.YEAR_OF_CENTURY, 2);
    }
    protected void appendWeekOfWeekyear(int digits) {
      appendNumericField(WeekFields.ISO.weekOfWeekBasedYear(), digits);
    }

    protected void appendMonthOfYear(int digits) {
      appendNumericField(ChronoField.MONTH_OF_YEAR, digits);
    }
    protected void appendMonthOfYearShortText() {
      appendTextField(ChronoField.MONTH_OF_YEAR, true);
    }
    protected void appendMonthOfYearText() {
      appendTextField(ChronoField.MONTH_OF_YEAR, false);
    }

    protected void appendDayOfYear(int digits) {
      appendNumericField(ChronoField.DAY_OF_YEAR, digits);
    }
    protected void appendDayOfMonth(int digits) {
      appendNumericField(ChronoField.DAY_OF_MONTH, digits);
    }
    protected void appendDayOfWeek(int digits) {
      appendNumericField(ChronoField.DAY_OF_WEEK, digits);
    }
    protected void appendDayOfWeekText() {
      appendTextField(ChronoField.DAY_OF_WEEK, false);
    }
    protected void appendDayOfWeekShortText() {
      appendTextField(ChronoField.DAY_OF_WEEK, true);
    }
    protected void appendHalfdayOfDayText() {
      appendTextField(ChronoField.AMPM_OF_DAY, false);
    }
    protected void appendClockhourOfDay(int digits) {
      appendNumericField(ChronoField.CLOCK_HOUR_OF_DAY, digits);
    }
    protected void appendClockhourOfHalfday(int digits) {
      appendNumericField(ChronoField.CLOCK_HOUR_OF_AMPM, digits);
    }
    protected void appendHourOfDay(int digits) {
      if (useRelaxedHour) {
        appendNumericFields(new TemporalField[]{ChronoField.HOUR_OF_DAY, ChronoField.CLOCK_HOUR_OF_DAY}, digits);
      } else {
        appendNumericField(ChronoField.HOUR_OF_DAY, digits);
      }
    }
    protected void appendHourOfHalfday(int digits) {
      appendNumericField(ChronoField.HOUR_OF_AMPM, digits);
    }
    protected void appendMinuteOfHour(int digits) {
      appendNumericField(ChronoField.MINUTE_OF_HOUR, digits);
    }
    protected void appendSecondOfMinute(int digits) {
      appendNumericField(ChronoField.SECOND_OF_MINUTE, digits);
    }
    protected void appendFractionOfSecond(int minDigits, int maxDigits) {
      appendNumericField(ChronoField.MILLI_OF_SECOND, minDigits, maxDigits);
    }

    protected void appendTimeZoneOffset(String zeroOffsetText, String zeroOffsetParseText, boolean showSeparators,
                                        int minFields, int maxFields) {
      appendComponent(new TimeZoneOffsetComponent(zeroOffsetParseText), true);
    }
    protected void appendTimeZoneId() {
      appendComponent(new TimeZoneIdComponent(), true);
    }
    protected void appendTimeZoneName() {
      // TODO: TimeZoneName
      appendComponent(new TimeZoneComponent(locale), true);
    }
    protected void appendTimeZoneShortName() {
      // TODO: TimeZoneName
      appendComponent(new TimeZoneComponent(locale), true);
    }

    protected void appendQuantifier(String str) {
      if (pieces.size() > 0) {
        FormatComponent last = pieces.get(pieces.size() - 1);
        last.appendQuantifier(str);
      } else {
        throw new IllegalArgumentException("Illegal quantifier at beginning of pattern: " + str);
      }
    }

    protected void appendGroupStart() { appendRegexPart("(?:");}

    protected void appendGroupEnd() { appendRegexPart(")"); }

    protected void appendLiteral(char c) {
      appendLiteralField(String.valueOf(c));}

    protected void appendLiteral(String s) {
      appendLiteralField(s); }
  }
  private static void parsePatternTo(FormatterBuilder builder, String pattern) {
    int length = pattern.length();
    int[] indexRef = new int[1];

    for (int i=0; i<length; i++) {
      indexRef[0] = i;
      String token = parseToken(pattern, indexRef);
      i = indexRef[0];

      int tokenLen = token.length();
      if (tokenLen == 0) {
        break;
      }
      char c = token.charAt(0);

      switch (c) {
        case 'G': // era designator (text)
          builder.appendEraText();
          break;
        case 'C': // century of era (number)
          builder.appendCenturyOfEra(tokenLen, tokenLen);
          break;
        case 'x': // weekyear (number)
        case 'y': // year (number)
        case 'Y': // year of era (number)
          if (tokenLen == 2) {
            boolean lenientParse = true;

            // Peek ahead to next token.
            if (i + 1 < length) {
              indexRef[0]++;
              if (isNumericToken(parseToken(pattern, indexRef))) {
                // If next token is a number, cannot support
                // lenient parse, because it will consume digits
                // that it should not.
                lenientParse = false;
              }
              indexRef[0]--;
            }

            // TODO: fixed pivots doesn't make sense, we want pivots that can change....
            // Use pivots which are compatible with SimpleDateFormat.
            switch (c) {
              case 'x':
                builder.appendTwoDigitWeekyear(LocalDate.now().get(WeekFields.ISO.weekBasedYear()) - 30, lenientParse);
                break;
              case 'y':
              case 'Y':
              default:
                builder.appendTwoDigitYear(LocalDate.now().getYear() - 30, lenientParse);
                break;
            }
          } else {
           /* // Try to support long year values.
            int maxDigits = 9;

            // Peek ahead to next token.
            if (i + 1 < length) {
              indexRef[0]++;
              if (isNumericToken(parseToken(pattern, indexRef))) {
                // If next token is a number, cannot support long years.
                maxDigits = tokenLen;
              }
              indexRef[0]--;
            } */
            int maxDigits = 4;

            switch (c) {
              case 'x':
                builder.appendWeekyear(tokenLen, maxDigits);
                break;
              case 'y':
                builder.appendYear(tokenLen, maxDigits);
                break;
              case 'Y':
                builder.appendYearOfEra(tokenLen, maxDigits);
                break;
            }
          }
          break;
        case 'M': // month of year (text and number)
          if (tokenLen >= 3) {
            if (tokenLen >= 4) {
              builder.appendMonthOfYearText();
            } else {
              builder.appendMonthOfYearShortText();
            }
          } else {
            builder.appendMonthOfYear(tokenLen);
          }
          break;
        case 'd': // day of month (number)
          builder.appendDayOfMonth(tokenLen);
          break;
        case 'a': // am/pm marker (text)
          builder.appendHalfdayOfDayText();
          break;
        case 'h': // clockhour of halfday (number, 1..12)
          builder.appendClockhourOfHalfday(tokenLen);
          break;
        case 'H': // hour of day (number, 0..23)
          builder.appendHourOfDay(tokenLen);
          break;
        case 'k': // clockhour of day (1..24)
          builder.appendClockhourOfDay(tokenLen);
          break;
        case 'K': // hour of halfday (0..11)
          builder.appendHourOfHalfday(tokenLen);
          break;
        case 'm': // minute of hour (number)
          builder.appendMinuteOfHour(tokenLen);
          break;
        case 's': // second of minute (number)
          builder.appendSecondOfMinute(tokenLen);
          break;
        case 'S': // fraction of second (number)
          builder.appendFractionOfSecond(tokenLen, tokenLen);
          break;
        case 'e': // day of week (number)
          builder.appendDayOfWeek(tokenLen);
          break;
        case 'E': // dayOfWeek (text)
          if (tokenLen >= 4) {
            builder.appendDayOfWeekText();
          } else {
            builder.appendDayOfWeekShortText();
          }
          break;
        case 'D': // day of year (number)
          builder.appendDayOfYear(tokenLen);
          break;
        case 'w': // week of weekyear (number)
          builder.appendWeekOfWeekyear(tokenLen);
          break;
        case 'z': // time zone (text)
          if (tokenLen >= 4) {
            builder.appendTimeZoneName();
          } else {
            builder.appendTimeZoneShortName();
          }
          break;
        case 'Z': // time zone offset
          if (tokenLen == 1) {
            builder.appendTimeZoneOffset(null, "Z", false, 2, 2);
          } else if (tokenLen == 2) {
            builder.appendTimeZoneOffset(null, "Z", true, 2, 2);
          } else {
            builder.appendTimeZoneId();
          }
          break;
        case '(':
          builder.appendGroupStart();
          break;
        case ')':
          builder.appendGroupEnd();
          break;
        case '{':
        case '*':
        case '?':
          builder.appendQuantifier(token);
          break;
        case '[':
        case '.':
        case '|':
        case '\\':
          builder.appendRegexPart(token);
          break;
        case '\'': // literal text
          String sub = token.substring(1);
          if (sub.length() == 1) {
            builder.appendLiteral(sub.charAt(0));
          } else {
            // Create copy of sub since otherwise the temporary quoted
            // string would still be referenced internally.
            builder.appendLiteral(new String(sub));
          }
          break;
        default:
          throw new IllegalArgumentException
                  ("Illegal pattern component: " + token);
      }
    }
  }

  private static final char[] SPECIAL_REGEX_CHARS = new char[]{'[', ']', '(', ')', '{', '}', '?', '*', '.', '|','\\'};
  private static boolean isSpecialRegexChar(char c)
  {
    for (char SPECIAL_REGEX_CHAR : SPECIAL_REGEX_CHARS) {
      if (c == SPECIAL_REGEX_CHAR) return true;
    }
    return false;
  }

  /**
   * Parses an individual token.
   *
   * @param pattern  the pattern string
   * @param indexRef  a single element array, where the input is the start
   *  location and the output is the location after parsing the token
   * @return the parsed token
   */
  private static String parseToken(String pattern, int[] indexRef) {
    StringBuilder buf = new StringBuilder();

    int i = indexRef[0];
    int length = pattern.length();

    char c = pattern.charAt(i);
    if (c >= 'A' && c <= 'Z' || c >= 'a' && c <= 'z') {
      // Scan a run of the same character, which indicates a time
      // pattern.
      buf.append(c);

      while (i + 1 < length) {
        char peek = pattern.charAt(i + 1);
        if (peek == c) {
          buf.append(c);
          i++;
        } else {
          break;
        }
      }
    } else if (isSpecialRegexChar(c)) {
      buf.append(c);
      if (c == '[') {
        // Look for end ']'
        // Assume no nesting
        i++;
        for (; i < length; i++) {
          c = pattern.charAt(i);
          buf.append(c);
          if (c == ']') {
            break;
          }
        }
      } else if (c == '{') {
        // Look for end '}'
        // Assume no nesting
        i++;
        for (; i < length; i++) {
          c = pattern.charAt(i);
          buf.append(c);
          if (c == '}') {
            break;
          }
        }
      } else if (c == '\\') {
        // Used to escape characters
        i++;
        if (i < length) {
          c = pattern.charAt(i);
          buf.append(c);
        }
      }
    } else {
      // This will identify token as text.
      buf.append('\'');

      boolean inLiteral = false;

      for (; i < length; i++) {
        c = pattern.charAt(i);

        if (c == '\'') {
          if (i + 1 < length && pattern.charAt(i + 1) == '\'') {
            // '' is treated as escaped '
            i++;
            buf.append(c);
          } else {
            inLiteral = !inLiteral;
          }
        } else if (!inLiteral &&
                (isSpecialRegexChar(c) ||
                (c >= 'A' && c <= 'Z' || c >= 'a' && c <= 'z'))) {
          i--;
          break;
        } else {
          buf.append(c);
        }
      }
    }

    indexRef[0] = i;
    return buf.toString();
  }

  /**
   * Returns true if token should be parsed as a numeric field.
   *
   * @param token  the token to parse
   * @return true if numeric field
   */
  private static boolean isNumericToken(String token) {
    int tokenLen = token.length();
    if (tokenLen > 0) {
      char c = token.charAt(0);
      switch (c) {
        case 'c': // century (number)
        case 'C': // century of era (number)
        case 'x': // weekyear (number)
        case 'y': // year (number)
        case 'Y': // year of era (number)
        case 'd': // day of month (number)
        case 'h': // hour of day (number, 1..12)
        case 'H': // hour of day (number, 0..23)
        case 'm': // minute of hour (number)
        case 's': // second of minute (number)
        case 'S': // fraction of second (number)
        case 'e': // day of week (number)
        case 'D': // day of year (number)
        case 'F': // day of week in month (number)
        case 'w': // week of year (number)
        case 'W': // week of month (number)
        case 'k': // hour of day (1..24)
        case 'K': // hour of day (0..11)
          return true;
        case 'M': // month of year (text and number)
          if (tokenLen <= 2) {
            return true;
          }
      }
    }

    return false;
  }

}
