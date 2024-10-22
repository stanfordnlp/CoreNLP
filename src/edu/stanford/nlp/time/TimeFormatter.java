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
import org.joda.time.*;
import org.joda.time.format.*;

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
        return new Expressions.PrimitiveValue("GroundedTime", new SUTime.GroundedTime(new Instant(d.getTime())));
      } catch (java.text.ParseException ex) {
        return null;
      }
    }

  }


  public static class JodaDateTimeFormatExtractor implements
          Function<CoreMap, Value> {

    private static final Class<CoreAnnotations.TextAnnotation> textAnnotationField = CoreAnnotations.TextAnnotation.class;
    private final DateTimeFormatter formatter;

    public JodaDateTimeFormatExtractor(DateTimeFormatter formatter) {
      this.formatter = formatter;
    }

    public JodaDateTimeFormatExtractor(String pattern) {
      this.formatter = DateTimeFormat.forPattern(pattern);
    }

    @Override
    public Value apply(CoreMap m) {
      try {
        String str = m.get(textAnnotationField);
        // TODO: Allow specification of pivot year (withPivotYear) for interpreting 2 digit years
        DateTime d = formatter.parseDateTime(str);
        return new Expressions.PrimitiveValue("GroundedTime", new SUTime.GroundedTime(d));
      } catch (IllegalArgumentException ex) {
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
      } else if ("org.joda.time.format.DateTimeFormat".equals(formatter)) {
        if (r.annotationField == null) { r.annotationField = r.tokensAnnotationField;  }
        updateExtractRule(r, env, new ApplyActionWrapper<>(env, new JodaDateTimeFormatExtractor(expr), action));
      } else if ("org.joda.time.format.ISODateTimeFormat".equals(formatter)) {
        if (r.annotationField == null) { r.annotationField = r.tokensAnnotationField;  }
        try {
          Method m = ISODateTimeFormat.class.getMethod(expr);
          DateTimeFormatter dtf = (DateTimeFormatter) m.invoke(null);
          updateExtractRule(r, env, new ApplyActionWrapper<>(env, new JodaDateTimeFormatExtractor(expr), action));
        } catch (Exception ex) {
          throw new RuntimeException("Error creating DateTimeFormatter", ex);
        }
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

    DateTimeFieldType fieldType;

    public Integer parseValue(String str) { return null; }
    public DateTimeFieldType getDateTimeFieldType() { return fieldType; }

    public SUTime.Temporal updateTemporal(SUTime.Temporal t, String fieldValueStr) {
      DateTimeFieldType dt = getDateTimeFieldType();
      if (fieldValueStr != null && dt != null) {
        Integer v = parseValue(fieldValueStr);
        if (v != null) {
          Partial pt = new Partial();
          pt = JodaTimeUtils.setField(pt, dt, v);
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

    public NumericDateComponent(DateTimeFieldType fieldType, int minDigits, int maxDigits) {
      this.fieldType = fieldType;
      this.minDigits = minDigits;
      this.maxDigits = maxDigits;
      MutableDateTime dt = new MutableDateTime(0L, DateTimeZone.UTC);
      MutableDateTime.Property property = dt.property(fieldType);
      minValue = property.getMinimumValueOverall();
      maxValue = property.getMaximumValueOverall();
    }

    protected StringBuilder appendRegex0(StringBuilder sb) {
      if (maxDigits > 5 || minDigits != maxDigits) {
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

    public RelaxedNumericDateComponent(DateTimeFieldType[] fieldTypes, int minDigits, int maxDigits)
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

    public TextDateComponent(DateTimeFieldType fieldType, Locale locale, Boolean isShort) {
      this.fieldType = fieldType;
      this.locale = locale;
      this.isShort = isShort;

      MutableDateTime dt = new MutableDateTime(0L, DateTimeZone.UTC);
      MutableDateTime.Property property = dt.property(fieldType);
      minValue = property.getMinimumValueOverall();
      maxValue = property.getMaximumValueOverall();
      this.validValues = new ArrayList<>(maxValue - minValue + 1);
      this.valueMapping = Generics.newHashMap();
      for (int i = minValue; i <= maxValue; i++) {
        property.set(i);
        if (isShort != null) {
          if (isShort) {
            addValue(property.getAsShortText(locale), i);
          } else {
            addValue(property.getAsText(locale), i);
          }
        } else {
          addValue(property.getAsShortText(locale), i);
          addValue(property.getAsText(locale), i);
        }
      }
      // Order by length for regex
      Collections.sort(validValues, STRING_LENGTH_REV_COMPARATOR);
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
      offset += DateTimeConstants.MILLIS_PER_HOUR * parseInteger(str, pos, 2);
      pos += 2;
      if (pos < str.length()) {
        // Parse minutes
        if (!Character.isDigit(str.charAt(pos))) { pos++; }
        offset += DateTimeConstants.MILLIS_PER_MINUTE * parseInteger(str, pos, 2);
        pos += 2;
        if (pos < str.length()) {
          // Parse seconds
          if (!Character.isDigit(str.charAt(pos))) { pos++; }
          offset += DateTimeConstants.MILLIS_PER_SECOND * parseInteger(str, pos, 2);
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
      DateTimeZone dtz = DateTimeZone.forOffsetMillis(offset);
      return t.setTimeZone(dtz);
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
    static final Map<String, DateTimeZone> timeZonesById;
    static final List<String> timeZoneIds;
    static final String timeZoneIdsRegex;
    static {
      timeZoneIds = new ArrayList<>(DateTimeZone.getAvailableIDs());
      timeZonesById = Generics.newHashMap();
      for (String str:timeZoneIds) {
        DateTimeZone dtz = DateTimeZone.forID(str);
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

    private static DateTimeZone parseDateTimeZone(String str) {
      str = str.toLowerCase();
      DateTimeZone v = timeZonesById.get(str);
      return v;
    }

    protected StringBuilder appendRegex0(StringBuilder sb) {
      sb.append(timeZoneIdsRegex);
      return sb;
    }

    public SUTime.Temporal updateTemporal(SUTime.Temporal t, String fieldValueStr) {
      if (fieldValueStr != null) {
        DateTimeZone dtz = parseDateTimeZone(fieldValueStr);
        return t.setTimeZone(dtz);
      }
      return t;
    }
  }

  private static class TimeZoneComponent extends FormatComponent
  {
    Locale locale;

    static Map<Locale, CollectionValuedMap<String, DateTimeZone>> timeZonesByName = Generics.newHashMap();
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
      int year = Year.now().getValue();
      long time1 = new SUTime.IsoDate(year,1,1).getJodaTimeInstant().getMillis();
      long time2 = new SUTime.IsoDate(year,6,1).getJodaTimeInstant().getMillis();
      CollectionValuedMap<String,DateTimeZone> tzMap = new CollectionValuedMap<>();
      for (DateTimeZone dtz:TimeZoneIdComponent.timeZonesById.values()) {
        // standard timezones
        tzMap.add(dtz.getShortName(time1, locale).toLowerCase(), dtz);
        tzMap.add(dtz.getName(time1, locale).toLowerCase(), dtz);
        // Add about half a year to get day light savings timezones...
        tzMap.add(dtz.getShortName(time2, locale).toLowerCase(), dtz);
        tzMap.add(dtz.getName(time2, locale).toLowerCase(), dtz);
//      tzMap.add(dtz.getNameKey(time).toLowerCase(), dtz);
//      tzMap.add(dtz.getID().toLowerCase(), dtz);
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

    public DateTimeZone parseDateTimeZone(String str) {
      // TODO: do something about these multiple timezones that match the same name...
      // pick one based on location
      str = str.toLowerCase();
      CollectionValuedMap<String,DateTimeZone> tzMap = timeZonesByName.get(locale);
      Collection<DateTimeZone> v = tzMap.get(str);
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
        DateTimeZone dtz = parseDateTimeZone(fieldValueStr);
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
    DateTimeFormatterBuilder builder = new DateTimeFormatterBuilder();
    List<FormatComponent> pieces = new ArrayList<>();
    int curGroup = 0;

    public DateTimeFormatter toFormatter() {
      return builder.toFormatter();
    }

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

    private void appendNumericFields(DateTimeFieldType[] fieldTypes, int digits) {
      appendNumericFields(fieldTypes, digits, digits);
    }

    private void appendNumericFields(DateTimeFieldType[] fieldTypes, int minDigits, int maxDigits) {
      appendComponent(new RelaxedNumericDateComponent(fieldTypes, minDigits, maxDigits), true);
    }

    private void appendNumericField(DateTimeFieldType fieldType, int digits) {
      appendNumericField(fieldType, digits, digits);
    }

    private void appendNumericField(DateTimeFieldType fieldType, int minDigits, int maxDigits) {
      appendComponent(new NumericDateComponent(fieldType, minDigits, maxDigits), true);
    }

    private void appendTextField(DateTimeFieldType fieldType, boolean isShort) {
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
      builder.appendEraText();
      appendTextField(DateTimeFieldType.era(), false);
    }
    protected void appendCenturyOfEra(int minDigits, int maxDigits) {
      builder.appendCenturyOfEra(minDigits, maxDigits);
      appendNumericField(DateTimeFieldType.centuryOfEra(), minDigits, maxDigits);
    }
    protected void appendYearOfEra(int minDigits, int maxDigits) {
      builder.appendYearOfEra(minDigits, maxDigits);
      appendNumericField(DateTimeFieldType.yearOfEra(), minDigits, maxDigits);
    }
    protected void appendYear(int minDigits, int maxDigits) {
      builder.appendYear(minDigits, maxDigits);
      appendNumericField(DateTimeFieldType.year(), minDigits, maxDigits);
    }
    protected void appendTwoDigitYear(int pivot, boolean lenient) {
      builder.appendTwoDigitYear(pivot, lenient);
      appendNumericField(DateTimeFieldType.yearOfCentury(), 2);
    }
    protected void appendWeekyear(int minDigits, int maxDigits) {
      builder.appendWeekyear(minDigits, maxDigits);
      appendNumericField(DateTimeFieldType.weekyear(), minDigits, maxDigits);
    }
    protected void appendTwoDigitWeekyear(int pivot, boolean lenient) {
      builder.appendTwoDigitYear(pivot, lenient);
      appendNumericField(DateTimeFieldType.yearOfCentury(), 2);
    }
    protected void appendWeekOfWeekyear(int digits) {
      builder.appendWeekOfWeekyear(digits);
      appendNumericField(DateTimeFieldType.weekOfWeekyear(), digits);
    }

    protected void appendMonthOfYear(int digits) {
      builder.appendMonthOfYear(digits);
      appendNumericField(DateTimeFieldType.monthOfYear(), digits);
    }
    protected void appendMonthOfYearShortText() {
      builder.appendMonthOfYearShortText();
      appendTextField(DateTimeFieldType.monthOfYear(), true);
    }
    protected void appendMonthOfYearText() {
      builder.appendMonthOfYearText();
      appendTextField(DateTimeFieldType.monthOfYear(), false);
    }

    protected void appendDayOfYear(int digits) {
      builder.appendDayOfYear(digits);
      appendNumericField(DateTimeFieldType.dayOfYear(), digits);
    }
    protected void appendDayOfMonth(int digits) {
      builder.appendDayOfMonth(digits);
      appendNumericField(DateTimeFieldType.dayOfMonth(), digits);
    }
    protected void appendDayOfWeek(int digits) {
      builder.appendDayOfWeek(digits);
      appendNumericField(DateTimeFieldType.dayOfWeek(), digits);
    }
    protected void appendDayOfWeekText() {
      builder.appendDayOfWeekText();
      appendTextField(DateTimeFieldType.dayOfWeek(), false);
    }
    protected void appendDayOfWeekShortText() {
      builder.appendDayOfWeekShortText();
      appendTextField(DateTimeFieldType.dayOfWeek(), true);
    }
    protected void appendHalfdayOfDayText() {
      builder.appendHalfdayOfDayText();
      appendTextField(DateTimeFieldType.halfdayOfDay(), false);
    }
    protected void appendClockhourOfDay(int digits) {
      builder.appendDayOfYear(digits);
      appendNumericField(DateTimeFieldType.clockhourOfDay(), digits);
    }
    protected void appendClockhourOfHalfday(int digits) {
      builder.appendClockhourOfHalfday(digits);
      appendNumericField(DateTimeFieldType.clockhourOfHalfday(), digits);
    }
    protected void appendHourOfDay(int digits) {
      if (useRelaxedHour) {
        builder.appendHourOfDay(digits);
        appendNumericFields(new DateTimeFieldType[]{DateTimeFieldType.hourOfDay(), DateTimeFieldType.clockhourOfDay()}, digits);
      } else {
        builder.appendHourOfDay(digits);
        appendNumericField(DateTimeFieldType.hourOfDay(), digits);
      }
    }
    protected void appendHourOfHalfday(int digits) {
      builder.appendHourOfHalfday(digits);
      appendNumericField(DateTimeFieldType.hourOfHalfday(), digits);
    }
    protected void appendMinuteOfHour(int digits) {
      builder.appendMinuteOfHour(digits);
      appendNumericField(DateTimeFieldType.minuteOfHour(), digits);
    }
    protected void appendSecondOfMinute(int digits) {
      builder.appendSecondOfMinute(digits);
      appendNumericField(DateTimeFieldType.secondOfMinute(), digits);
    }
    protected void appendFractionOfSecond(int minDigits, int maxDigits) {
      builder.appendFractionOfSecond(minDigits, maxDigits);
      appendNumericField(DateTimeFieldType.millisOfSecond(), minDigits, maxDigits);
    }

    protected void appendTimeZoneOffset(String zeroOffsetText, String zeroOffsetParseText, boolean showSeparators,
                                        int minFields, int maxFields) {
      builder.appendTimeZoneOffset(zeroOffsetText, zeroOffsetParseText, showSeparators, minFields, maxFields);
      appendComponent(new TimeZoneOffsetComponent(zeroOffsetParseText), true);
    }
    protected void appendTimeZoneId() {
      builder.appendTimeZoneId();
      appendComponent(new TimeZoneIdComponent(), true);
    }
    protected void appendTimeZoneName() {
      builder.appendTimeZoneName();
      // TODO: TimeZoneName
      appendComponent(new TimeZoneComponent(locale), true);
    }
    protected void appendTimeZoneShortName() {
      builder.appendTimeZoneShortName();
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
      builder.appendLiteral(c);
      appendLiteralField(String.valueOf(c));}

    protected void appendLiteral(String s) {
      builder.appendLiteral(s);
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
                builder.appendTwoDigitWeekyear(new DateTime().getWeekyear() - 30, lenientParse);
                break;
              case 'y':
              case 'Y':
              default:
                builder.appendTwoDigitYear(new DateTime().getYear() - 30, lenientParse);
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
