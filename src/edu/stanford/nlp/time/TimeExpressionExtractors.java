package edu.stanford.nlp.time;

import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.tokensregex.*;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.Function;
import edu.stanford.nlp.util.Interval;
import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormatter;

import java.util.List;
import java.util.logging.Logger;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Functions for helping in defining Time Expression rules
 * TODO: Remove once we replace EnglishTimeExpressionPatterns
 *
 * @author Angel Chang
 */
@Deprecated
public class TimeExpressionExtractors {
  private static final Logger logger = TimeExpressionExtractorImpl.logger;
  static interface TemporalExtractor extends Function<CoreMap, SUTime.Temporal>
  {
    public SUTime.Temporal apply(CoreMap in);
  }

  static class SequenceMatchExtractor implements Function<SequenceMatchResult<CoreMap>, TimeExpression>
  {
    Function<CoreMap, SUTime.Temporal> extractor;
    boolean includeNested;
    int group = 0;

    public SequenceMatchExtractor(TemporalExtractor extractor, boolean includeNested, int group) {
      this.extractor = extractor;
      this.includeNested = includeNested;
      this.group = group;
    }

    public TimeExpression apply(SequenceMatchResult<CoreMap> matched) {
      TimeExpression te = new TimeExpression(null, Interval.toInterval(matched.start(group), matched.end(group), Interval.INTERVAL_OPEN_END), extractor, 0, 0);
      te.setIncludeNested(includeNested);
      return te;
    }
  }

  static class StringMatchExtractor implements Function<MatchResult, TimeExpression>
  {
    Function<CoreMap, SUTime.Temporal> extractor;
    boolean includeNested;
    int group = 0;

    public StringMatchExtractor(TemporalExtractor extractor, boolean includeNested, int group) {
      this.extractor = extractor;
      this.includeNested = includeNested;
      this.group = group;
    }

    public TimeExpression apply(MatchResult matched) {
      TimeExpression te = new TimeExpression(Interval.toInterval(matched.start(group), matched.end(group),
              Interval.INTERVAL_OPEN_END), null, extractor, 0, 0);
      te.setIncludeNested(includeNested);
      return te;
    }
  }

  protected static SequenceMatchRules.SequencePatternExtractRule<CoreMap, TimeExpression>
  getSequencePatternExtractRule(Env env, String pattern, TemporalExtractor temporalFunc) {
    return new SequenceMatchRules.SequencePatternExtractRule<CoreMap, TimeExpression>(
            env, pattern,
            new SequenceMatchExtractor(temporalFunc, false, 0));
  }

  protected static SequenceMatchRules.SequencePatternExtractRule<CoreMap, TimeExpression>
  getSequencePatternExtractRule(TokenSequencePattern pattern, TemporalExtractor temporalFunc) {
    return new SequenceMatchRules.SequencePatternExtractRule<CoreMap, TimeExpression>(
            pattern,
            new SequenceMatchExtractor(temporalFunc, false, 0));
  }

  protected static  SequenceMatchRules.StringPatternExtractRule<TimeExpression>
  getStringPatternExtractRule(Env env, String pattern, TemporalExtractor temporalFunc) {
    return new SequenceMatchRules.StringPatternExtractRule<TimeExpression>(
            env, pattern,
            new StringMatchExtractor(temporalFunc, false, 0));
  }

  protected static  SequenceMatchRules.StringPatternExtractRule<TimeExpression>
  getStringPatternExtractRuleWithWordBoundary(Env env, String pattern, TemporalExtractor temporalFunc) {
    return new SequenceMatchRules.StringPatternExtractRule<TimeExpression>(
            env, pattern,
            new StringMatchExtractor(temporalFunc, false, 0), true);
  }

  protected static  SequenceMatchRules.StringPatternExtractRule<TimeExpression>
  getStringPatternExtractRule(Pattern pattern, TemporalExtractor temporalFunc) {
    return new SequenceMatchRules.StringPatternExtractRule<TimeExpression>(
            pattern,
            new StringMatchExtractor(temporalFunc, false, 0));
  }

  /**
   *  Takes in an expression assumed to be a duration expression and returns a
   *   duration value, such as P3D for "3 days"
   *  Duration Format: PnYnMnDTnHnMnS
   */

  static  class DurationRule implements TemporalExtractor  {
    EnglishTimeExpressionPatterns patterns;
    Pattern stringPattern;
    TokenSequencePattern tokenPattern;
    int exprGroup = 0;
    int valMatchGroup = -1;
    int valMatchGroup2 = -1;
    int unitMatchGroup = -1;
    int underspecifiedValMatchGroup = -1;
    String defaultUnderspecifiedValue;
    SUTime.Time beginTime;
    SUTime.Time endTime;

    private DurationRule(EnglishTimeExpressionPatterns patterns, int valMatchGroup, int unitMatchGroup, SUTime.Time beginTime, SUTime.Time endTime) {
      this.patterns = patterns;
      this.valMatchGroup = valMatchGroup;
      this.unitMatchGroup = unitMatchGroup;
      this.beginTime = beginTime;
      this.endTime = endTime;
    }

    public DurationRule(EnglishTimeExpressionPatterns patterns, Pattern p, int valMatchGroup, int unitMatchGroup) {
      this(patterns, p, valMatchGroup, unitMatchGroup, SUTime.TIME_NONE, SUTime.TIME_NONE);
    }

    public DurationRule(EnglishTimeExpressionPatterns patterns, Pattern p, int valMatchGroup, int unitMatchGroup, SUTime.Time beginTime, SUTime.Time endTime) {
      this(patterns, valMatchGroup, unitMatchGroup, beginTime, endTime);
      this.stringPattern = p;
    }

    public DurationRule(EnglishTimeExpressionPatterns patterns, Pattern p, int valMatchGroup, int valMatchGroup2, int unitMatchGroup, SUTime.Time beginTime, SUTime.Time endTime) {
      this(patterns, valMatchGroup, unitMatchGroup, beginTime, endTime);
      this.valMatchGroup2 = valMatchGroup2;
      this.stringPattern = p;
    }

    public DurationRule(EnglishTimeExpressionPatterns patterns, TokenSequencePattern p, int valMatchGroup, int unitMatchGroup) {
      this(patterns, p, valMatchGroup, unitMatchGroup, SUTime.TIME_NONE, SUTime.TIME_NONE);
    }

    public DurationRule(EnglishTimeExpressionPatterns patterns, TokenSequencePattern p, int valMatchGroup, int unitMatchGroup, SUTime.Time beginTime, SUTime.Time endTime) {
      this(patterns, valMatchGroup, unitMatchGroup, beginTime, endTime);
      this.tokenPattern = p;
    }

    public DurationRule(EnglishTimeExpressionPatterns patterns, TokenSequencePattern p, int valMatchGroup, int valMatchGroup2, int unitMatchGroup, SUTime.Time beginTime, SUTime.Time endTime) {
      this(patterns, valMatchGroup, unitMatchGroup, beginTime, endTime);
      this.valMatchGroup2 = valMatchGroup2;
      this.tokenPattern = p;
    }

    public boolean useTokens()
    {
      return (tokenPattern != null);
    }

    public void setUnderspecifiedValueMatchGroup(int matchGroup, String defaultValue) {
      underspecifiedValMatchGroup = matchGroup;
      defaultUnderspecifiedValue = defaultValue;
    }

    public SUTime.Temporal apply(CoreMap chunk) {
      if (tokenPattern != null) {
        return apply(chunk.get(CoreAnnotations.NumerizedTokensAnnotation.class));
        //          return apply(chunk.get(CoreAnnotations.TokensAnnotation.class));
      } else {
        return apply(chunk.get(CoreAnnotations.TextAnnotation.class));
      }
    }

    public SUTime.Temporal apply(String expression) {
      Matcher matcher = stringPattern.matcher(expression);
      if (matcher.find()) {
        return extract(matcher);
      }
      return null;
    }

    public SUTime.Temporal apply(List<? extends CoreMap> tokens) {
      TokenSequenceMatcher matcher = tokenPattern.getMatcher(tokens);
      if (matcher.find()) {
        return extract(matcher);
      }
      return null;
    }

    private SUTime.Temporal extract(MatchResult m)
    {
      String val = null;
      if (valMatchGroup >= 0) {
        val = m.group(valMatchGroup);
      }
      SUTime.Duration d = extractDuration(m, val);
      if (valMatchGroup2 >= 0) {
        String val2 = m.group(valMatchGroup2);
        if (val2 != null) {
          SUTime.Duration d2 = extractDuration(m, val2);
          if (val != null && d != null) {
            d = new SUTime.DurationRange(d, d2);
          } else {
            d = d2;
          }
        }
      }

      return addEndPoints(d);
    }

    private SUTime.Temporal addEndPoints(SUTime.Duration d)
    {
      SUTime.Temporal t = d;
      if (d != null && (beginTime != null || endTime != null)) {
        SUTime.Time b = beginTime;
        SUTime.Time e = endTime;
        // New so we get different time ids
        if (b == SUTime.TIME_REF_UNKNOWN) {
          b = new SUTime.RefTime("UNKNOWN");
        } else if (b == SUTime.TIME_UNKNOWN) {
          b = new SUTime.SimpleTime("UNKNOWN");
        }
        if (e == SUTime.TIME_REF_UNKNOWN) {
          e = new SUTime.RefTime("UNKNOWN");
        } else if (e == SUTime.TIME_UNKNOWN) {
          e = new SUTime.SimpleTime("UNKNOWN");
        }
        t = new SUTime.Range(b,e,d);
      }
      return t;
    }

    private SUTime.Duration extractDuration(MatchResult results, String val) {
      String unit = null;
      if (unitMatchGroup >= 0) unit = results.group(unitMatchGroup);
      if (val == null) {
        val = (unit.endsWith("s"))? "X":"1";
        //        val = "1";
      }
      if (underspecifiedValMatchGroup >= 0) {
        val = defaultUnderspecifiedValue;
        if (results.groupCount() >= underspecifiedValMatchGroup) {
          if (results.group(underspecifiedValMatchGroup) != null) {
            val = "X";
          }
        }
      }

      SUTime.Duration d = patterns.getDuration(val, unit);
      if (d == null) {
        logger.warning("Unable to get duration with: val=" + val + ", unit=" + unit + ", matched=" + results.group());
      }
      return d;
    }

  }

  static abstract class TimePatternExtractor implements TemporalExtractor  {
    Pattern stringPattern;
    TokenSequencePattern tokenPattern;

    public SUTime.Temporal apply(CoreMap chunk) {
      if (tokenPattern != null) {
        if (chunk.containsKey(TimeExpression.ChildrenAnnotation.class)) {
          return apply(chunk.get(TimeExpression.ChildrenAnnotation.class));
        } else {
          return apply(chunk.get(CoreAnnotations.NumerizedTokensAnnotation.class));
          //            return apply(chunk.get(CoreAnnotations.TokensAnnotation.class));
        }
      } else if (stringPattern != null) {
        return apply(chunk.get(CoreAnnotations.TextAnnotation.class));
      } else {
        return extract(null);
      }
    }

    public SUTime.Temporal apply(String expression) {
      Matcher matcher = stringPattern.matcher(expression);
      if (matcher.find()) {
        return extract(matcher);
      } else {
        return null;
      }
    }

    public SUTime.Temporal apply(List<? extends CoreMap> tokens) {
      TokenSequenceMatcher matcher = tokenPattern.getMatcher(tokens);
      if (matcher.find()) {
        return extract(matcher);
      } else {
        return null;
      }
    }

    public String toString()
    {
      StringBuilder sb = new StringBuilder(getClass().getName());
      if (stringPattern != null) {
        sb.append(" with string pattern=" + stringPattern.pattern());
      } else {
        sb.append(" with token pattern=" + tokenPattern.pattern());
      }
      return sb.toString();
    }

    protected abstract SUTime.Temporal extract(MatchResult results);
  }

  static class GenericTimePatternExtractor extends TimePatternExtractor  {
    Function<MatchResult,SUTime.Temporal> tempFunc;

    protected GenericTimePatternExtractor(
            Function<MatchResult, SUTime.Temporal> tempFunc)
    {
      this.tempFunc = tempFunc;
    }

    protected GenericTimePatternExtractor(
            TokenSequencePattern tokenPattern,
            Function<MatchResult, SUTime.Temporal> tempFunc)
    {
      this.tokenPattern = tokenPattern;
      this.tempFunc = tempFunc;
    }

    protected GenericTimePatternExtractor(
            Pattern stringPattern,
            Function<MatchResult, SUTime.Temporal> tempFunc)
    {
      this.stringPattern = stringPattern;
      this.tempFunc = tempFunc;
    }


    protected SUTime.Temporal extract(MatchResult results) {
      try {
        return tempFunc.apply(results);
      } catch(org.joda.time.IllegalFieldValueException e) {
        logger.warning("WARNING: found invalid temporal expression: \"" + e.getMessage() +"\". Will discard it...");
        return null;
      }
    }
  }

  private static TimeExpression getTimeExpression(List<? extends CoreMap> list, int index)
  {
    return list.get(index).get(TimeExpression.Annotation.class);
  }

  @SuppressWarnings("unused")
  private static String getText(List<? extends CoreMap> list, int index)
  {
    return list.get(index).get(CoreAnnotations.TextAnnotation.class);
  }

  // FUNCTIONS for getting temporals
  static class TemporalConstFunc implements Function<MatchResult,SUTime.Temporal>
  {
    SUTime.Temporal temporal;

    TemporalConstFunc(SUTime.Temporal temporal) {
      this.temporal = temporal;
    }

    public SUTime.Temporal apply(MatchResult in) {
      return temporal;
    }
  }

  static class TemporalLookupFunc implements Function<MatchResult,SUTime.Temporal>
  {
    EnglishTimeExpressionPatterns patterns;
    int group;

    TemporalLookupFunc(EnglishTimeExpressionPatterns patterns, int group) {
      this.patterns = patterns;
      this.group = group;
    }

    public SUTime.Temporal apply(MatchResult in) {
      if (group >= 0) {
        String expr = in.group(group);
        if (expr != null) {
          return patterns.lookupTemporal(expr);
        }
      }
      return null;
    }
  }

  static class TemporalGetTEFunc implements Function<MatchResult,SUTime.Temporal>
  {
    int group = 0;
    int nodeIndex = 0;

    TemporalGetTEFunc(int group, int nodeIndex) {
      this.group = group;
      this.nodeIndex = nodeIndex;
    }

    public SUTime.Temporal apply(MatchResult in) {
      if (in instanceof SequenceMatchResult) {
        SequenceMatchResult<CoreMap> mr = (SequenceMatchResult<CoreMap>) (in);
        if (group >= 0) {
          List<? extends CoreMap> matched = mr.groupNodes(group);
          if (matched != null) {
            int i = (nodeIndex >= 0)? 0: (matched.size() + nodeIndex);
            TimeExpression te = getTimeExpression(matched, i);
            if (te != null) { return te.getTemporal(); }
          }
        }
      }
      return null;
    }
  }

  static class TemporalOpConstFunc implements Function<MatchResult,SUTime.TemporalOp>
  {
    SUTime.TemporalOp temporalOp;

    TemporalOpConstFunc(SUTime.TemporalOp temporalOp) {
      this.temporalOp = temporalOp;
    }

    public SUTime.TemporalOp apply(MatchResult in) {
      return temporalOp;
    }
  }

  static class TemporalOpLookupFunc implements Function<MatchResult,SUTime.TemporalOp>
  {
    EnglishTimeExpressionPatterns patterns;
    int group;

    TemporalOpLookupFunc(EnglishTimeExpressionPatterns patterns, int group) {
      this.patterns = patterns;
      this.group = group;
    }

    public SUTime.TemporalOp apply(MatchResult in) {
      if (group >= 0) {
        String expr = in.group(group);
        if (expr != null) {
          return patterns.lookupTemporalOp(expr);
        }
      }
      return null;
    }
  }

  static class TemporalComposeFunc implements Function<MatchResult,SUTime.Temporal>
  {
    Function<MatchResult,SUTime.TemporalOp> opFunc;
    Function<MatchResult,? extends SUTime.Temporal>[] argFuncs;

    TemporalComposeFunc(Function<MatchResult, SUTime.TemporalOp> opFunc,
                        Function<MatchResult, ? extends SUTime.Temporal>... argFuncs) {
      this.opFunc = opFunc;
      this.argFuncs = argFuncs;
    }

    public SUTime.Temporal apply(MatchResult in) {
      SUTime.TemporalOp relOp = (opFunc != null)? opFunc.apply(in):null;
      SUTime.Temporal[] args = new SUTime.Temporal[argFuncs.length];
      for (int i = 0; i < argFuncs.length; i++) {
        args[i] = (argFuncs[i] != null)? argFuncs[i].apply(in):null;
      }
      return relOp.apply(args);
      //return new SUTime.RelativeTime((SUTime.Time) ref, relOp, relArg);
    }
  }

  static class TemporalComposeObjFunc implements Function<MatchResult,SUTime.Temporal>
  {
    Function<MatchResult,SUTime.TemporalOp> opFunc;
    Function<MatchResult,?>[] argFuncs;

    TemporalComposeObjFunc(Function<MatchResult, SUTime.TemporalOp> opFunc,
                           Function<MatchResult, ? extends Object>... argFuncs) {
      this.opFunc = opFunc;
      this.argFuncs = argFuncs;
    }

    public SUTime.Temporal apply(MatchResult in) {
      SUTime.TemporalOp relOp = (opFunc != null)? opFunc.apply(in):null;
      Object[] args = new Object[argFuncs.length];
      for (int i = 0; i < argFuncs.length; i++) {
        args[i] = (argFuncs[i] != null)? argFuncs[i].apply(in):null;
      }
      return relOp.apply(args);
      //return new SUTime.RelativeTime((SUTime.Time) ref, relOp, relArg);
    }
  }

  static TimePatternExtractor getTimeExtractor(SUTime.Temporal t)
  {
    return new GenericTimePatternExtractor(new TemporalConstFunc(t));
  }

  static TimePatternExtractor getTimeLookupExtractor(EnglishTimeExpressionPatterns patterns, Pattern pattern, int group)
  {
    return new GenericTimePatternExtractor(new TemporalLookupFunc(patterns, group));
  }

  static TimePatternExtractor getTimeLookupExtractor(EnglishTimeExpressionPatterns patterns, TokenSequencePattern pattern, int group)
  {
    return new GenericTimePatternExtractor(pattern, new TemporalLookupFunc(patterns, group));
  }

  static TimePatternExtractor getRelativeTimeExtractor(
          Pattern pattern,
          Function<MatchResult, SUTime.Temporal> refFunc,
          Function<MatchResult, SUTime.TemporalOp> relOpFunc,
          Function<MatchResult, SUTime.Temporal> relArgFunc)
  {
    return new GenericTimePatternExtractor(pattern, new TemporalComposeFunc(relOpFunc, refFunc, relArgFunc));
  }

  static TimePatternExtractor getRelativeTimeExtractor(
          TokenSequencePattern pattern,
          Function<MatchResult, SUTime.Temporal> refFunc,
          Function<MatchResult, SUTime.TemporalOp> relOpFunc,
          Function<MatchResult, SUTime.Temporal> relArgFunc)
  {
    return new GenericTimePatternExtractor(pattern, new TemporalComposeFunc(relOpFunc, refFunc, relArgFunc));
  }

  static TimePatternExtractor getRelativeTimeLookupExtractor(
          EnglishTimeExpressionPatterns patterns,
          Pattern pattern,
          SUTime.Temporal ref,
          SUTime.TemporalOp relOp,
          int relArgGroup)
  {
    return new GenericTimePatternExtractor(pattern, new TemporalComposeFunc(
            new TemporalOpConstFunc(relOp), new TemporalConstFunc(ref), new TemporalLookupFunc(patterns, relArgGroup)));
  }

  static TimePatternExtractor getRelativeTimeLookupExtractor(
          EnglishTimeExpressionPatterns patterns,
          TokenSequencePattern pattern,
          SUTime.Temporal ref,
          SUTime.TemporalOp relOp,
          int relArgGroup)
  {
    return new GenericTimePatternExtractor(pattern, new TemporalComposeFunc(
            new TemporalOpConstFunc(relOp), new TemporalConstFunc(ref), new TemporalLookupFunc(patterns, relArgGroup)));
  }

  public static TimePatternExtractor getIsoDateExtractor(TokenSequencePattern p, int yearGroup, int monthGroup, int dayGroup, boolean yearPartial)
  {
    return new GenericTimePatternExtractor(p,
            new IsoDateTimePatternFunc(yearGroup, monthGroup, dayGroup, -1, -1, -1, yearPartial));
  }

  public static TimePatternExtractor getIsoDateExtractor(Pattern p, int yearGroup, int monthGroup, int dayGroup, boolean yearPartial)
  {
    return new GenericTimePatternExtractor(p,
            new IsoDateTimePatternFunc( yearGroup, monthGroup, dayGroup, -1, -1, -1, yearPartial));
  }

  public static TimePatternExtractor getIsoTimeExtractor(TokenSequencePattern p, int hourGroup, int minuteGroup, int secGroup)
  {
    return new GenericTimePatternExtractor(p,
            new IsoDateTimePatternFunc( -1, -1, -1, hourGroup, minuteGroup, secGroup, false));
  }

  public static TimePatternExtractor getIsoTimeExtractor(Pattern p, int hourGroup, int minuteGroup, int secGroup)
  {
    return new GenericTimePatternExtractor(p,
            new IsoDateTimePatternFunc( -1, -1, -1, hourGroup, minuteGroup, secGroup, false));
  }

  public static TimePatternExtractor getIsoDateTimeExtractor(Pattern p, int yearGroup, int monthGroup, int dayGroup,
                                                             int hourGroup, int minuteGroup, int secGroup, boolean yearPartial)
  {
    return new GenericTimePatternExtractor(p,
            new IsoDateTimePatternFunc( yearGroup, monthGroup, dayGroup, hourGroup, minuteGroup, secGroup, yearPartial));
  }

  public static TimePatternExtractor getIsoDateTimeExtractor(TokenSequencePattern p, int yearGroup, int monthGroup, int dayGroup,
                                                             int hourGroup, int minuteGroup, int secGroup, boolean yearPartial)
  {
    return new GenericTimePatternExtractor(p,
            new IsoDateTimePatternFunc( yearGroup, monthGroup, dayGroup, hourGroup, minuteGroup, secGroup, yearPartial));
  }

  static class IsoDateTimePatternFunc implements Function<MatchResult, SUTime.Temporal>  {

    boolean partialYear = false;
    int yearGroup = -1;
    int monthGroup = -1;
    int dayGroup = -1;
    int hourGroup = -1;
    int minuteGroup = -1;
    int secGroup = -1;

    public IsoDateTimePatternFunc(int yearGroup, int monthGroup, int dayGroup, int hourGroup, int minuteGroup, int secGroup, boolean partialYear) {
      this.yearGroup = yearGroup;
      this.monthGroup = monthGroup;
      this.dayGroup = dayGroup;
      this.hourGroup = hourGroup;
      this.minuteGroup = minuteGroup;
      this.secGroup = secGroup;
      this.partialYear = partialYear;
    }

    public void setPartialYear(boolean partialYear) {
      this.partialYear = partialYear;
    }

    public SUTime.Temporal apply(MatchResult results) {
      SUTime.IsoTime isoTime = null;
      SUTime.IsoDate isoDate = null;
      boolean hasDate = (yearGroup >= 0 || monthGroup >= 0 || dayGroup >= 0);
      boolean hasTime = (hourGroup >= 0 || minuteGroup >= 0 || secGroup >= 0);
      if (hasTime) {
        String h = (hourGroup >= 0)? results.group(hourGroup):null;
        String m = (minuteGroup >= 0)? results.group(minuteGroup):null;
        String s = (secGroup >= 0)? results.group(secGroup):null;
        if (h != null || m != null || s != null) {
          isoTime = new SUTime.IsoTime(h,m,s);
        }
      }
      if (hasDate) {
        String yearStr = (yearGroup >= 0)? results.group(yearGroup):null;
        if (yearStr != null && yearStr.length() == 2 && partialYear) {
          yearStr = SUTime.PAD_FIELD_UNKNOWN2 + yearStr;
        }
        String m = (monthGroup >= 0)? results.group(monthGroup):null;
        String d = (dayGroup >= 0)? results.group(dayGroup):null;
        if (yearStr != null || m != null || d != null) {
          isoDate = EnglishTimeExpressionPatterns.createIsoDate(yearStr, m, d);
        }
      }
      if (isoTime != null && isoDate != null) {
        return new SUTime.IsoDateTime(isoDate, isoTime);
      } else if (isoTime != null) {
        return isoTime;
      } else if (isoDate != null) {
        return isoDate;
      } else {
        return null;
      }
    }
  }

  static class IsoDateTimeExtractor implements TemporalExtractor  {
    DateTimeFormatter formatter;
    boolean hasDate;
    boolean hasTime;

    public IsoDateTimeExtractor(DateTimeFormatter formatter, boolean hasDate, boolean hasTime)
    {
      this.formatter = formatter;
      this.hasDate = hasDate;
      this.hasTime = hasTime;
    }

    public SUTime.Temporal apply(CoreMap chunk) {
      return apply(chunk.get(CoreAnnotations.TextAnnotation.class));
    }

    public SUTime.Temporal apply(String text) {
      // TODO: TIMEZONE?
      DateTime dateTime = null;
      try {
        dateTime = formatter.parseDateTime(text);
      } catch(org.joda.time.IllegalFieldValueException e) {
        logger.warning("WARNING: Invalid temporal \"" + text + "\" (" + e.getMessage() + "). Skipping and continuing...");
        return null;
      }
      assert(dateTime != null);
      if (hasDate && hasTime) {
        if (dateTime.getZone() != null) {
          return new SUTime.GroundedTime(dateTime);
        } else {
          return new SUTime.IsoDateTime(
                  new SUTime.IsoDate(dateTime.getYear(), dateTime.getMonthOfYear(), dateTime.getDayOfMonth()),
                  new SUTime.IsoTime(dateTime.getHourOfDay(), dateTime.getMinuteOfHour(), dateTime.getSecondOfMinute()));
        }
      } else if (hasTime) {
        // TODO: Millisecs?
        return new SUTime.IsoTime(dateTime.getHourOfDay(), dateTime.getMinuteOfHour(), dateTime.getSecondOfMinute());
      } else if (hasDate) {
        return new SUTime.IsoDate(dateTime.getYear(), dateTime.getMonthOfYear(), dateTime.getDayOfMonth());
      } else {
        return null;
      }
    }

  }
}
