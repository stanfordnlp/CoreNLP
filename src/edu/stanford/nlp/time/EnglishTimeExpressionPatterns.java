package edu.stanford.nlp.time;

import edu.stanford.nlp.ie.NumberNormalizer;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.tokensregex.*;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.Filter;
import edu.stanford.nlp.util.Filters;
import edu.stanford.nlp.util.Function;
import org.joda.time.format.ISODateTimeFormat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static edu.stanford.nlp.time.EnglishTimeExpressionPatterns.PatternType.TOKENS;
import static edu.stanford.nlp.time.EnglishTimeExpressionPatterns.PatternType.STRING;

/**
 * This class contains rules/patterns for transforming
 * time related English expressions into temporal representations.
 * Many of the rules are based on expressions from GUTime (2.00)
 *
 * @author Angel Chang
 */
public class EnglishTimeExpressionPatterns implements TimeExpressionPatterns {
  private static final Logger logger = Logger.getLogger(EnglishTimeExpressionPatterns.class.getName());
  protected static enum PatternType { TOKENS, STRING }

  Env env;
  SequenceMatchRules.ExtractRule< CoreMap, TimeExpression> timeExtractionRule;
  SequenceMatchRules.ExtractRule< List<? extends CoreMap>, TimeExpression> compositeTimeExtractionRule;
  // Rules for filtering valid time expressions
  List<Filter<TimeExpression>> filterRules;

  Options options;

  public EnglishTimeExpressionPatterns(Options options) {
    this.options = options;
    initTimeUnitsMap();
    initTemporalMap();
    initTemporalOpMap();
    initEnv();
    initRules();
  }

  public CoreMapExpressionExtractor createExtractor() {
    CoreMapExpressionExtractor expressionExtractor = new CoreMapExpressionExtractor(env);
    expressionExtractor.setExtractRules(
            getTimeExtractionRule(),
            getCompositeTimeExtractionRule(),
            getFilterRule());
    return expressionExtractor;
  }

  private static final Pattern teUnit = Pattern.compile("(second|minute|hour|day|month|quarter|year|week|decade|centur(y|ie)|millenn?i(um|a))", Pattern.CASE_INSENSITIVE);
  private static final Pattern numTerm = Pattern.compile("(one|two|three|four|five|six|seven|eight|nine|ten|eleven|twelve|thirteen|fourteen|fifteen|sixteen|seventeen|eighteen|nineteen|twenty|thirty|forty|fifty|sixty|seventy|eighty|ninety|hundred|thousand|million|billion|trillion|first|second|third|fourth|fifth|sixth|seventh|eighth|ninth|tenth|eleventh|twelfth|thirteenth|fourteenth|fifteenth|sixteenth|seventeenth|eighteenth|nineteenth|twentieth|thirtieth|fortieth|fiftieth|sixtieth|seventieth|eightieth|ninetieth|hundreth|thousandth|millionth|billionth|trillionth)", Pattern.CASE_INSENSITIVE);
  private static final Pattern numOrdTerm = Pattern.compile("(first|second|third|fourth|fifth|sixth|seventh|eighth|ninth|tenth|eleventh|twelfth|thirteenth|fourteenth|fifteenth|sixteenth|seventeenth|eighteenth|nineteenth|twentieth|thirtieth|fortieth|fiftieth|sixtieth|seventieth|eightieth|ninetieth|hundreth|thousandth|millionth|billionth|trillionth)", Pattern.CASE_INSENSITIVE);
  private static final Pattern numNoOrdTerm = Pattern.compile("(one|two|three|four|five|six|seven|eight|nine|ten|eleven|twelve|thirteen|fourteen|fifteen|sixteen|seventeen|eighteen|nineteen|twenty|thirty|forty|fifty|sixty|seventy|eighty|ninety|hundred|thousand|million|billion|trillion)", Pattern.CASE_INSENSITIVE);
  private static final Pattern teDay = Pattern.compile("(monday|tuesday|wednesday|thursday|friday|saturday|sunday)", Pattern.CASE_INSENSITIVE);
  private static final Pattern teDayAbbr = Pattern.compile("(mon\\.?|tue\\?|wed\\.?|thu\\.?|fri\\.?|sat\\.?|\\sun\\.?)", Pattern.CASE_INSENSITIVE);

  private static final Pattern teMonth = Pattern.compile("(january|february|march|april|may|june|july|august|september|october|november|december)", Pattern.CASE_INSENSITIVE);
  private static final Pattern teMonthAbbr  = Pattern.compile("(jan|feb|mar|apr|may|jun|jul|aug|sep|sept|oct|nov|dec)", Pattern.CASE_INSENSITIVE);
  private static final Pattern teOrdinalWords = Pattern.compile("(tenth|eleventh|twelfth|thirteenth|fourteenth|fifteenth|sixteenth|seventeenth|eighteenth|nineteenth|twentieth|twenty-first|twenty-second|twenty-third|twenty-fourth|twenty-fifth|twenty-sixth|twenty-seventh|twenty-eighth|twenty-ninth|thirtieth|thirty-first|first|second|third|fourth|fifth|sixth|seventh|eighth|ninth)", Pattern.CASE_INSENSITIVE);
  private static final Pattern teNumOrds = Pattern.compile("([23]?1-?st|11-?th|[23]?2-?nd|12-?th|[12]?3-?rd|13-?th|[12]?[4-90]-?th|30-?th)", Pattern.CASE_INSENSITIVE);

  private static class TimexTypeMatchNodePattern extends NodePattern<TimeExpression> {
      SUTime.TimexType type;
      public TimexTypeMatchNodePattern(SUTime.TimexType type) { this.type = type; }
      public boolean match(TimeExpression te) {
        if (te != null) {
          SUTime.Temporal t = te.getTemporal();
          if (t != null) {
            return type.equals(t.getTimexType());
          }
        }
        return false;
      }
  }

  private void initEnv()
  {
    env = TokenSequencePattern.getNewEnv();
    env.setDefaultResultsAnnotationExtractor(TimeExpression.TimeExpressionConverter);
    env.setDefaultTokensAnnotationKey(CoreAnnotations.NumerizedTokensAnnotation.class);
    env.setDefaultResultAnnotationKey(TimeExpression.Annotation.class);
    env.setDefaultNestedResultsAnnotationKey(TimeExpression.ChildrenAnnotation.class);
    env.bind("time", new TimeFormatter.TimePatternExtractRuleCreator());
    // Do case insensitive matching
    env.setDefaultStringPatternFlags(Pattern.CASE_INSENSITIVE);

    env.bind("numcomptype", CoreAnnotations.NumericCompositeTypeAnnotation.class);
    env.bind("numcompvalue", CoreAnnotations.NumericCompositeValueAnnotation.class);

    env.bind("temporal", TimeExpression.Annotation.class);
    env.bind("::IS_TIMEX_DATE", new TimexTypeMatchNodePattern(SUTime.TimexType.DATE));
    env.bind("::IS_TIMEX_DURATION", new TimexTypeMatchNodePattern(SUTime.TimexType.DURATION));
    env.bind("::IS_TIMEX_TIME", new TimexTypeMatchNodePattern(SUTime.TimexType.TIME));
    env.bind("::IS_TIMEX_SET", new TimexTypeMatchNodePattern(SUTime.TimexType.SET));

    env.bind("$RELDAY", "/today|yesterday|tomorrow|tonight|tonite/");
    env.bind("$SEASON", "/spring|summer|fall|autumn|winter/");
    env.bind("$TIMEOFDAY", "/morning|afternoon|evening|night|noon|midnight|teatime|lunchtime|dinnertime|suppertime|afternoon|midday|dusk|dawn|sunup|sunrise|sundown|twilight|daybreak/");
    env.bind("$TEDAY", "/" + teDay.pattern() + "|" + teDayAbbr.pattern() + "/");
    env.bind("$TEDAYS", "/" + teDay.pattern() + "s?|" + teDayAbbr.pattern() + "/");
    env.bind("$TEMONTH", "/" + teMonth.pattern() + "|" + teMonthAbbr.pattern() + "/");
    env.bind("$TEMONTHS", "/" + teMonth.pattern() +  "s?|" + teMonthAbbr.pattern() + "\\.?s?/");
    env.bind("$TEUNITS", "/" + teUnit.pattern() + "s?/");
    env.bind("$TEUNIT", "/" + teUnit.pattern() + "/");

    env.bind("$NUM", TokenSequencePattern.compile(env, "[ { numcomptype:NUMBER } ]"));
    env.bind("$INT", TokenSequencePattern.compile(env, " [ { numcomptype:NUMBER } & !{ word:/.*\\.\\d+.*/} & !{ word:/.*,.*/ } ] "));  // TODO: Only recognize integers
    env.bind("$INT1000TO3000", TokenSequencePattern.compile(env, "[ $INT & !{ word:/\\+.*/} & { numcompvalue>1000 } & { numcompvalue<3000 } ] "));
    env.bind("$INT1TO31", TokenSequencePattern.compile(env, "[ $INT & !{ word:/\\+.*/} & { numcompvalue>=1 } & { numcompvalue<=31 } ] "));
    env.bind("$NUM_ORD", TokenSequencePattern.compile(env, "[ { numcomptype:ORDINAL } ]"));

//    env.bind("$NUM", TokenSequencePattern.compile(env, "[ { numcomptype:NUMBER } ]+"));
//    env.bind("$INT", TokenSequencePattern.compile(env, " [ { numcomptype:NUMBER } & !{ word:/.*\\.\\d+.*/} ]+ "));  // TODO: Only recognize integers
//    env.bind("$INT1000TO3000", TokenSequencePattern.compile(env, "[ { numcomptype:NUMBER; numcompvalue>1000 } & { numcompvalue<3000 } & !{ word:/.*\\.\\d+.*/} ]+"));
//    env.bind("$NUM_ORD", TokenSequencePattern.compile(env, "[ { numcomptype:ORDINAL } ]+"));
    env.bind("$INT_TIMES", TokenSequencePattern.compile(env, " $INT /times/ | once | twice | thrice "));
    env.bind("$REL_MOD", TokenSequencePattern.compile(env, "/the/? /next|following|last|previous/ | /this/ /coming|past/? | /the/ /coming|past/"));
    env.bind("$FREQ_MOD", TokenSequencePattern.compile(env, "/each/ | /every/ $NUM_ORD | /every/ /other|alternate|alternating/? | /alternate|alternating/ "));
    env.bind("$EARLY_LATE_MOD", TokenSequencePattern.compile(env, "/late|early|mid-?/ | /the/? /beginning|start|dawn|middle|end/ /of/"));
    env.bind("$APPROX_MOD", TokenSequencePattern.compile(env, "/about|around|some|exactly|precisely/"));
    env.bind("$YEAR", "/[012]\\d\\d\\d|'\\d\\d/ | /\\w+teen/ [ { numcompvalue<=100 } & { numcompvalue>0 } & $INT ] ");
    env.bind("$POSSIBLE_YEAR", " $YEAR | $INT /a\\.?d\\.?|b\\.?c\\.?/ | $INT1000TO3000 ");
    env.bind("$TEUNITS_NODE", TokenSequencePattern.compile(env, "[ " + "/" + teUnit.pattern() + "s?/" + " & { tag:/NN.*/ } ]"));
//    env.bind("$POSSIBLE_YEAR", TokenSequencePattern.compile(env, "/\\d\\d\\d\\d|'\\d\\d/ | /\\w+teen/ [ { nner<=100; nner>0 } ] | $NUM+ "));

    // Regex string patterns
    env.bindStringRegex("$NUM_TERM", numTerm.pattern());
    env.bindStringRegex("$NUM_ORD_TERM", numOrdTerm.pattern());
    env.bindStringRegex("$NUM_NO_ORD_TERM", numNoOrdTerm.pattern());
    env.bindStringRegex("$TEmonth", teMonth.pattern());
    env.bindStringRegex("$TEmonthabbr", teMonthAbbr.pattern());
    env.bindStringRegex("$TEUnits", teUnit.pattern());
    env.bindStringRegex("$OT", "\\\\b");
    env.bindStringRegex("$CT", "\\\\b");
    env.bindStringRegex("$TEOrdinalWords", teOrdinalWords.pattern());
    env.bindStringRegex("$TENumOrds", teNumOrds.pattern());
  }

  @SuppressWarnings("unchecked")
  private void initRules()
  {
    initDurationRules();
    initDateTimeRules();
    final SequenceMatchRules.ListExtractRule<String, TimeExpression> stringExtractRule
            = new SequenceMatchRules.ListExtractRule<String, TimeExpression>();
    final SequenceMatchRules.ListExtractRule<List<? extends CoreMap>, TimeExpression> tokenSeqExtractRule
            = new SequenceMatchRules.ListExtractRule<List<? extends CoreMap>, TimeExpression>();

    for (TimeExpressionExtractors.DurationRule durationRule:durationRules) {
      if (durationRule.useTokens()) {
        SequenceMatchRules.SequencePatternExtractRule<CoreMap, TimeExpression> r =
                new SequenceMatchRules.SequencePatternExtractRule<CoreMap, TimeExpression>(
                durationRule.tokenPattern,
                new TimeExpressionExtractors.SequenceMatchExtractor(durationRule, false, durationRule.exprGroup));
        //r.group = durationRule.exprGroup;
        tokenSeqExtractRule.addRules(r);
      } else {
        SequenceMatchRules.StringPatternExtractRule<TimeExpression> r =
                new SequenceMatchRules.StringPatternExtractRule<TimeExpression>(env,
                durationRule.stringPattern.pattern(),
                new TimeExpressionExtractors.StringMatchExtractor(durationRule, false, durationRule.exprGroup), true);
       // r.group = durationRule.exprGroup;
        stringExtractRule.addRules(r);
      }
    }
    stringExtractRule.addRules(dateTimeStringRules);
    tokenSeqExtractRule.addRules(dateTimeTokenSeqRules);

    timeExtractionRule = new
            SequenceMatchRules.ExtractRule< CoreMap, TimeExpression>() {
              public boolean extract(CoreMap in, List<TimeExpression> out) {
                List<CoreMap> tokens = in.get(CoreAnnotations.NumerizedTokensAnnotation.class);
                boolean tsex = (tokens != null) && tokenSeqExtractRule.extract(tokens, out);
//                boolean tsex = tokenSeqExtractRule.extract(in.get(CoreAnnotations.TokensAnnotation.class), out);
                String text = in.get(CoreAnnotations.TextAnnotation.class);
                boolean strex = (text != null) && stringExtractRule.extract(text, out);
                return tsex || strex;
              }
            };

    // Compositional rules
    TimeExpressionExtractors.TimePatternExtractor adjTimeExtractor = new TimeExpressionExtractors.GenericTimePatternExtractor(
//      TokenSequencePattern.compile(env, " ( [ { temporal::IS_TIMEX_SET } | { temporal::IS_TIMEX_TIME } | { temporal::IS_TIMEX_DATE } ] ) /,|of|in/? ( [ { temporal::IS_TIMEX_DATE } | { temporal::IS_TIMEX_TIME } ] ) | " +
        TokenSequencePattern.compile(env, " /the/? ( ( [ { temporal::EXISTS } ] ) /,|of|in/? ( [ { temporal::IS_TIMEX_DATE } | { temporal::IS_TIMEX_TIME } ] ) | " +
              " ( [ { temporal::IS_TIMEX_DATE } ] ) /at/  ( [ { temporal::IS_TIMEX_TIME } ] ) | " +
              " ( [ { temporal::IS_TIMEX_TIME } | { temporal::IS_TIMEX_DURATION } ] ) /on/  ( [ { temporal::IS_TIMEX_DATE } ] ) | " +
              " ( [ { temporal::IS_TIMEX_DATE } | { temporal::IS_TIMEX_TIME } ] ) (/'s/ | /'/ /s/) ( [ { temporal::EXISTS } ] ) )"
      ),
      new TimeExpressionExtractors.TemporalComposeFunc(
              new TimeExpressionExtractors.TemporalOpConstFunc(SUTime.TemporalOp.INTERSECT),
              new TimeExpressionExtractors.TemporalGetTEFunc(1,0),
              new TimeExpressionExtractors.TemporalGetTEFunc(1,-1)
      )
    );
    TimeExpressionExtractors.TimePatternExtractor dateTodayExtractor = new TimeExpressionExtractors.GenericTimePatternExtractor(
      TokenSequencePattern.compile(env, " ( [ { temporal::IS_TIMEX_DATE } | { temporal::IS_TIMEX_TIME } ] )  (/today|tonight/)"),
            new TimeExpressionExtractors.TemporalGetTEFunc(0,0)
    );
    TimeExpressionExtractors.TimePatternExtractor relTimeExtractor = new TimeExpressionExtractors.GenericTimePatternExtractor(
      TokenSequencePattern.compile(env, " ( [ { temporal::IS_TIMEX_DURATION } ] )  (/before|from|since|after/ | /prior/ /to/) ( [ ({ temporal::IS_TIMEX_TIME }  |  { temporal::IS_TIMEX_DATE }) ] )"),
            new TimeExpressionExtractors.TemporalComposeFunc(
                    new TimeExpressionExtractors.TemporalOpConstFunc(SUTime.TemporalOp.OFFSET),
                    new TimeExpressionExtractors.TemporalGetTEFunc(0,-1),
                    new TimeExpressionExtractors.TemporalComposeObjFunc(
                            new TimeExpressionExtractors.TemporalOpConstFunc(SUTime.TemporalOp.MULTIPLY),
                            new TimeExpressionExtractors.TemporalGetTEFunc(0,0),
                            new Function<MatchResult,Integer>() {
                              public Integer apply(MatchResult in) {
                                String rel = in.group(2).toLowerCase();
                                if ("before".equals(rel) || "prior to".equals(rel)) {
                                  return -1;
                                } else {
                                  return 1;
                                }
                              }
                            })
            )
    );

    // expand: timex later|earlier|late => one timex
    TimeExpressionExtractors.TimePatternExtractor relDurationExtractor = new TimeExpressionExtractors.GenericTimePatternExtractor(
      TokenSequencePattern.compile(env, " ( [ { temporal::IS_TIMEX_DURATION } ] )  (/earlier|later|ago|hence/ | /from/ /now/) "),
            new TimeExpressionExtractors.TemporalComposeFunc(
                    new TimeExpressionExtractors.TemporalOpConstFunc(SUTime.TemporalOp.OFFSET),
                    new TimeExpressionExtractors.TemporalConstFunc(SUTime.TIME_REF),
                    new TimeExpressionExtractors.TemporalComposeObjFunc(
                            new TimeExpressionExtractors.TemporalOpConstFunc(SUTime.TemporalOp.MULTIPLY),
                            new TimeExpressionExtractors.TemporalGetTEFunc(0,0),
                            new Function<MatchResult,Integer>() {
                              public Integer apply(MatchResult in) {
                                String rel = in.group(2).toLowerCase();
                                if ("earlier".equals(rel) || "ago".equals(rel)) {
                                  return -1;
                                } else {
                                  return 1;
                                }
                              }
                            })
            )
    );
    // expand: timex later|earlier|late => one timex
    TimeExpressionExtractors.TimePatternExtractor relTimeExtractor2 = new TimeExpressionExtractors.GenericTimePatternExtractor(
      TokenSequencePattern.compile(env, " ( [ { temporal::EXISTS } & !{ temporal::IS_TIMEX_DURATION } ] )  (/earlier|later|late|ago|hence/ | /from/ /now/) "),
      new TimeExpressionExtractors.TemporalGetTEFunc(0,0)
    );
    // expand: (this|about|nearly|early|later|earlier|late) timex => one timex
    // expand: more than| up to| less than timex => one timex
    TimeExpressionExtractors.TimePatternExtractor relTimeExtractor3 = new TimeExpressionExtractors.GenericTimePatternExtractor(
      TokenSequencePattern.compile(env, " ( /this|about|nearly|early|later|earlier|late/ | " +
              " /more/ /than/ | /up/ /to/ | /less/ /than/ ) ( [ { temporal::EXISTS } ] ) "),
      new TimeExpressionExtractors.TemporalGetTEFunc(0,-1)
    );
    TimeExpressionExtractors.TimePatternExtractor setTimeExtractor = new TimeExpressionExtractors.GenericTimePatternExtractor(
      TokenSequencePattern.compile(env, " ( $FREQ_MOD ) ( [ { temporal::EXISTS } & !{ temporal::IS_TIMEX_SET } ] ) "),
      new Function<MatchResult, SUTime.Temporal>() {
        public SUTime.Temporal apply(MatchResult in) {
          Function<MatchResult,SUTime.Temporal> tFunc = new TimeExpressionExtractors.TemporalGetTEFunc(0,-1);
          SUTime.Temporal t = tFunc.apply(in);
          return makeSet(t, in.group(1));
        }
      }
    );
    TimeExpressionExtractors.TimePatternExtractor rangeTimeExtractor = new TimeExpressionExtractors.GenericTimePatternExtractor(
      TokenSequencePattern.compile(env, " /from/? ( [ { temporal::IS_TIMEX_TIME } | { temporal::IS_TIMEX_DATE } ] ) /to|-/ ( [ { temporal::IS_TIMEX_TIME } | { temporal::IS_TIMEX_DATE } ] ) "),
      new Function<MatchResult, SUTime.Temporal>() {
        public SUTime.Temporal apply(MatchResult in) {
          Function<MatchResult,SUTime.Temporal> t1Func = new TimeExpressionExtractors.TemporalGetTEFunc(1,0);
          Function<MatchResult,SUTime.Temporal> t2Func = new TimeExpressionExtractors.TemporalGetTEFunc(2,0);
          SUTime.Temporal t1 = t1Func.apply(in);
          SUTime.Temporal t2 = t2Func.apply(in);
          return new SUTime.Range((SUTime.Time) t1,(SUTime.Time) t2);
        }
      }
    );
    SequenceMatchRules.ListExtractRule< List<? extends CoreMap>, TimeExpression>
            compositeRules = new SequenceMatchRules.ListExtractRule< List<? extends CoreMap>, TimeExpression>(
      TimeExpressionExtractors.getSequencePatternExtractRule(relTimeExtractor.tokenPattern, relTimeExtractor),
      TimeExpressionExtractors.getSequencePatternExtractRule(adjTimeExtractor.tokenPattern, adjTimeExtractor),
      TimeExpressionExtractors.getSequencePatternExtractRule(dateTodayExtractor.tokenPattern, dateTodayExtractor),
            TimeExpressionExtractors.getSequencePatternExtractRule(relDurationExtractor.tokenPattern, relDurationExtractor),
      TimeExpressionExtractors.getSequencePatternExtractRule(relTimeExtractor2.tokenPattern, relTimeExtractor2),
      TimeExpressionExtractors.getSequencePatternExtractRule(relTimeExtractor3.tokenPattern, relTimeExtractor3),
      TimeExpressionExtractors.getSequencePatternExtractRule(setTimeExtractor.tokenPattern, setTimeExtractor)
    );
    if (options.markTimeRanges) {
      TimeExpressionExtractors.SequenceMatchExtractor extractor =
              new TimeExpressionExtractors.SequenceMatchExtractor(rangeTimeExtractor, true, 0);
      SequenceMatchRules.SequencePatternExtractRule<CoreMap, TimeExpression> r =
              new SequenceMatchRules.SequencePatternExtractRule<CoreMap, TimeExpression>(
                      rangeTimeExtractor.tokenPattern, extractor);
      compositeRules.addRules(r);
    }
    compositeTimeExtractionRule = compositeRules;

    filterRules = new ArrayList<Filter<TimeExpression>>();
    filterRules.add(
      new TimeExpressionTokenSeqFilter(TokenSequencePattern.compile(
              env, "[ { word:/fall|spring|second|march|may/ } & !{ tag:/NN.*/ } ]"), true));
    filterRules.add(
            new TimeExpressionTokenSeqFilter(TokenSequencePattern.compile(
                    env, "/good/ /morning|evening|day|afternoon|night/"), true));
  }

  /**
   * Checks time expression against list of invalid time expressions
   * @param timeExpr
   */
  protected boolean checkTimeExpression(TimeExpression timeExpr)
  {
    for (Filter<TimeExpression> filterRule:filterRules) {
      if (!filterRule.accept(timeExpr)) {
        return false;
      }
    }
    return true;
  }

  protected SequenceMatchRules.ExtractRule< CoreMap, TimeExpression> getTimeExtractionRule()
  {
    return timeExtractionRule;
  }

  protected SequenceMatchRules.ExtractRule< List<? extends CoreMap>, TimeExpression> getCompositeTimeExtractionRule()
  {
    return compositeTimeExtractionRule;
  }

  protected Filter<TimeExpression> getFilterRule()
  {
    return new Filters.DisjFilter<TimeExpression>(filterRules);
  }

  private TimeExpressionExtractors.DurationRule createDurationRule(PatternType patternType, String regex, int valMatchGroup, int unitMatchGroup)
  {
    return createDurationRule(patternType, regex, valMatchGroup, unitMatchGroup, SUTime.TIME_NONE, SUTime.TIME_NONE);
  }

  private TimeExpressionExtractors.DurationRule createDurationRule(PatternType patternType, String regex, int valMatchGroup, int valMatchGroup2, int unitMatchGroup)
  {
    return createDurationRule(patternType, regex, valMatchGroup, valMatchGroup2, unitMatchGroup, SUTime.TIME_NONE, SUTime.TIME_NONE);
  }

  private TimeExpressionExtractors.DurationRule createDurationRule(PatternType patternType, String regex, int valMatchGroup,
                                                                  int unitMatchGroup, SUTime.Time beginTime, SUTime.Time endTime)
  {
    return createDurationRule(patternType, regex, valMatchGroup, -1, unitMatchGroup, beginTime, endTime);
  }

  private TimeExpressionExtractors.DurationRule createDurationRule(PatternType patternType, String regex, int valMatchGroup, int valMatchGroup2,
                                                                  int unitMatchGroup, SUTime.Time beginTime, SUTime.Time endTime)
  {
    switch (patternType) {
      case TOKENS:
        TokenSequencePattern tp = TokenSequencePattern.compile(env, regex);
        return new TimeExpressionExtractors.DurationRule(this, tp, valMatchGroup, valMatchGroup2, unitMatchGroup, beginTime, endTime);
      case STRING:
        Pattern p = getPattern(regex);
        return new TimeExpressionExtractors.DurationRule(this, p, valMatchGroup, valMatchGroup2, unitMatchGroup, beginTime, endTime);
      default:
        throw new UnsupportedOperationException("Unknown pattern type: " + patternType);
    }
  }

  List<TimeExpressionExtractors.DurationRule> durationRules = new ArrayList<TimeExpressionExtractors.DurationRule>();
  private void initDurationRules()
  {
    TimeExpressionExtractors.DurationRule rule;

    durationRules.clear();
    // i.e. "the past twenty four years"
    durationRules.add(rule = createDurationRule(TOKENS, "/the/ /past|last/ (?: ($NUM) /to|-/ )? ($NUM)? ($TEUNITS)", 1, 2, 3, SUTime.TIME_UNKNOWN, SUTime.TIME_REF));
    durationRules.add(rule = createDurationRule(TOKENS, "/the/ /next|following/ (?: ($NUM) /to|-/ )? ($NUM)? ($TEUNITS)", 1, 2, 3, SUTime.TIME_REF, SUTime.TIME_UNKNOWN));

    // i.e. "another 3 years", "another thirteen months"
    durationRules.add(rule = createDurationRule(TOKENS, "/another/ (?: ($NUM) /to|-/ )? ($NUM)? ($TEUNITS)", 1, 2, 3,  SUTime.TIME_UNKNOWN, SUTime.TIME_REF));

    //i.e. "the 2 months following the crash", "for ten days before leaving"
    // TODO: NEED TO FIX THIS, right now it doesn't include "the crash" or "leaving"...need to be able to recognize NPs and VPs using POS tags
    // regexes.add("/the/ $NUM TEUNITS (since|after|following|before|prior to|previous to)");
    //############  NEED TO FIX THIS SO A NP OR VP COMES AT END
    //#i.e. "for ten minutes following"
    //durationRules.add(new DurationRule("^(the|for)\s(\d+)\s($TEUnits)(s)?\s(since|after|following|before|prior\sto|previous\sto)$/"));
    // TODO: (since|after|following) => $beginPoint= $unspecTIDVal
    // TODO: (before|prior to|previous to) => $endPoint= $unspecTIDVal
    durationRules.add(rule = createDurationRule(TOKENS, "/the/ (?: ($NUM) /to|-/ )? ($NUM) ($TEUNITS_NODE)", 1, 2, 3));


    // i.e. "the first 9 months of 1997"
    //regexes.add("/the/ /first|initial|last|final/ $NUM $TEUNITS of");
    // TODO: NEED TO FIX THIS, RIGHT NOW NEEDS TO INCLUDE THE FOLLOWING, like "1997" or "December" or "her life"
    //###########  NEED TO FIX THIS SO THAT SOMETHING COMES AFTER "OF"  ###############
    //i.e. "the first six months of", "the last 7 minutes of" - note that "first" gets translated to "1"
    // pattern too vague for time end points
    durationRules.add(rule = createDurationRule(TOKENS, "/the/ /first|initial|last|final/ (?: ($NUM) /to|-/ )? ($NUM)? ($TEUNITS)", 1, 2, 3));

    // i.e. "the fifth straight year", "the third straight month in a row", "the ninth day consecutively"
    // i.e. "the eighth consecutive day in a row"
    // i.e. "the twenty ninth day straight"
    durationRules.add(rule = createDurationRule(TOKENS,
            "/the/ ($NUM_ORD) /straight|consecutive/ ($TEUNIT) (?: /in/ /a/ /row/ | /consecutively/ )?",
            1, 2, SUTime.TIME_UNKNOWN, SUTime.TIME_REF));
    durationRules.add(rule = createDurationRule(TOKENS,
            "/the/ ($NUM_ORD) /straight|consecutive/? ($TEUNIT) (?: /in/ /a/ /row/ | /consecutively/ )",
            1, 2, SUTime.TIME_UNKNOWN, SUTime.TIME_REF));

    //jbp i.e. "no more than 60 days" "no more than 20 years"
    durationRules.add(rule = createDurationRule(TOKENS, "/no/? /more/ /than/ (?: ($NUM) /to|-/ )? ($NUM) ($TEUNITS)", 1, 2, 3/*, SUTime.TIME_REF, SUTime.TIME_UNKNOWN*/));
    durationRules.add(rule = createDurationRule(TOKENS, "/no/? /less/ /than/ (?: ($NUM) /to|-/ )? ($NUM) ($TEUNITS)", 1, 2, 3/*, SUTime.TIME_REF, SUTime.TIME_UNKNOWN*/));

    //jbp i.e. "at least sixty days"
    durationRules.add(rule = createDurationRule(TOKENS, "/at/ /least/ (?: ($NUM) /to|-/ )? ($NUM) /more/? ($TEUNITS)", 1, 2, 3/*, SUTime.TIME_REF, SUTime.TIME_UNKNOWN*/));

    // hundreds of years
    durationRules.add(rule = createDurationRule(TOKENS, "(/(ten|hundred|thousand|million|billion|trillion)s/) /of/ ($TEUNITS)", 1, -1, 2));

    //i.e. "recent weeks", "several days"
    // pattern too vague for time end points
    durationRules.add(rule = createDurationRule(TOKENS, "(/recent|several/) /-/? ($TEUNITS)", -1, 2));
    rule.setUnderspecifiedValueMatchGroup(1, "1");

    // i.e. 3-months old, "four years", "four minutes"
    // pattern too vague for time end points
    durationRules.add(rule = createDurationRule(TOKENS, "($NUM) /to|-/ ($NUM) [ \"-\" ]? ($TEUNITS_NODE)  (?: [ \"-\" ]? /old/ )? ", 1, 2, 3));
    durationRules.add(rule = createDurationRule(TOKENS, "($NUM) [ \"-\" ]? ($TEUNITS_NODE)  (?: [ \"-\" ]? /old/ )? ", 1, 2));
    durationRules.add(rule = createDurationRule(STRING, "(\\d+)[-\\s]($TEUnits)(s)?([-\\s]old)?", 1, 2));
    durationRules.add(rule = createDurationRule(STRING, "$NUM_NO_ORD_TERM[-\\s]($TEUnits)(s)?([-\\s]old)?", 1, 2));

    //i.e. "a decade", "a few decades", NOT "a few hundred decades"
    durationRules.add(rule = createDurationRule(TOKENS, "(?: /the/ /past|next|following|coming|last|first|final/ | /a|an/ )? (/couple/ /of/? ) ($TEUNITS)", -1, 2));
    rule.setUnderspecifiedValueMatchGroup(3, "2");
    durationRules.add(rule = createDurationRule(TOKENS, "(?: /the/ /past|next|following|coming|last|first|final/ | /a|an/ )? (/few/ ) ($TEUNITS)", -1, 2));
    rule.setUnderspecifiedValueMatchGroup(1, "1");

    durationRules.add(rule = createDurationRule(TOKENS, "/a|an/ ($TEUNITS)", -1, 1));

    durationRules.add(rule = createDurationRule(TOKENS, "/the/ [ { tag:JJ } ]+ ($TEUNITS_NODE)", -1, 1));
    rule.tokenPattern.setPriority(-1);
    durationRules.add(rule = createDurationRule(TOKENS, "($TEUNITS_NODE)", -1, 1));
    rule.setUnderspecifiedValueMatchGroup(0, "X");
    rule.tokenPattern.setPriority(-1);
  }

  static class PatternFilter implements Filter<String>
  {
    private static final long serialVersionUID = 802406900800457283L;
    Pattern pattern;

    PatternFilter(String regex) { this.pattern = Pattern.compile(regex); }
    PatternFilter(Pattern pattern) { this.pattern = pattern; }

    public boolean accept(String obj) {
      return pattern.matcher(obj).find();
    }
   }

  static class TokenSeqPatternFilter implements Filter<List<? extends CoreMap>>
  {
    private static final long serialVersionUID = -596297552394987521L;
    TokenSequencePattern pattern;

    TokenSeqPatternFilter(TokenSequencePattern pattern) { this.pattern = pattern; }

    public boolean accept(List<? extends CoreMap> obj) {
      return pattern.getMatcher(obj).find();
    }
   }

  static class TimeExpressionTokenSeqFilter implements Filter<TimeExpression>
  {
    private static final long serialVersionUID = 1L;
    TokenSequencePattern pattern;
    boolean acceptPattern = true;

    TimeExpressionTokenSeqFilter(TokenSequencePattern pattern,
                                 boolean acceptPattern)
    {
      this.pattern = pattern;
      this.acceptPattern = acceptPattern;
    }

    public boolean accept(TimeExpression obj) {
      boolean matched = pattern.getMatcher(obj.getAnnotation().get(CoreAnnotations.TokensAnnotation.class)).matches();
      if (acceptPattern) {
        return matched;
      } else {
        return !matched;
      }
    }

  }


  public Pattern getPattern(String regex)
  {
    return env.getStringPattern(regex);
  }


  private static SUTime.Temporal makeSet(SUTime.Temporal t, String freq)
  {
    if (freq == null) return t;
    // Make into set
    String quant = null;
    int scale = 1;
    SUTime.Duration p = t.getPeriod();
    if (freq != null) {
      freq = freq.toLowerCase();
      if (freq.equals("alternate") || freq.contains("other")) {
        quant = freq;
        scale = 2;
      } else if (freq.equals("each") || freq.equals("every")) {
        quant = freq;
      } else if (freq.startsWith("every")) {
        quant = freq;
        Number n = NumberNormalizer.wordToNumber(quant.substring(6));
        if (n != null) {
          scale = n.intValue();
        }
      }
    }
    if (p != null && scale != 1) {
      p = p.multiplyBy(scale);
    }
    return new SUTime.PeriodicTemporalSet(t,p,quant,null/*"P1X"*/);
  }

  private SUTime.Temporal makeRelative(SUTime.Temporal t, String rel)
  {
    if (rel != null) {
      SUTime.TemporalOp seqOp = lookupTemporalOp(rel);
      if (rel != null) {
        t = new SUTime.RelativeTime(seqOp, t);
      }
    }
    return t;
  }

  final List<SequenceMatchRules.ExtractRule<String, TimeExpression>> dateTimeStringRules
          = new ArrayList<SequenceMatchRules.ExtractRule<String, TimeExpression>>();
  final List<SequenceMatchRules.ExtractRule<List<? extends CoreMap>, TimeExpression>> dateTimeTokenSeqRules
          = new ArrayList<SequenceMatchRules.ExtractRule<List<? extends CoreMap>, TimeExpression>>();
  @SuppressWarnings("unchecked")
  private void initDateTimeRules()
  {
    @SuppressWarnings("unused")
    SequenceMatchRules.ExtractRule<String,TimeExpression> srule = null;
    @SuppressWarnings("unused")
    SequenceMatchRules.SequencePatternExtractRule trule = null;
    TimeExpressionExtractors.TimePatternExtractor timePatternExtractor = null;

    // two digit year
    timePatternExtractor = TimeExpressionExtractors.getIsoDateExtractor(
            getPattern("'(\\d\\d)\\b"), 1, -1, -1, true);
    dateTimeStringRules.add(srule = TimeExpressionExtractors.getStringPatternExtractRule(env, "('\\d\\d)\\b", timePatternExtractor));

    // ISO date/times
    dateTimeStringRules.add(srule = TimeExpressionExtractors.getStringPatternExtractRuleWithWordBoundary(
            env, "(\\d\\d\\d\\d-?\\d\\d-?\\d\\d-?T\\d\\d(:?\\d\\d)?(:?\\d\\d)?(?:[.,](\\d{1,3}))?([+-]\\d\\d:?\\d\\d)?)",
            new TimeExpressionExtractors.IsoDateTimeExtractor(ISODateTimeFormat.dateTimeParser(), true, true)));
    dateTimeStringRules.add(srule = TimeExpressionExtractors.getStringPatternExtractRuleWithWordBoundary(
            env, "(\\d\\d\\d\\d-\\d\\d-\\d\\d)",
            new TimeExpressionExtractors.IsoDateTimeExtractor(ISODateTimeFormat.dateParser(), true, false)));
    dateTimeStringRules.add(srule = TimeExpressionExtractors.getStringPatternExtractRuleWithWordBoundary(
            env, "(T\\d\\d(:?\\d\\d)?(:?\\d\\d)?(?:[.,](\\d{1,3}))?([+-]\\d\\d:?\\d\\d)?)",
            new TimeExpressionExtractors.IsoDateTimeExtractor(ISODateTimeFormat.timeParser(), false, true)));

    // Slash notation (tokenizer add \\ to / )
    timePatternExtractor = TimeExpressionExtractors.getIsoDateTimeExtractor(
            getPattern("(?:(\\d\\d?):?(\\d\\d)(:?(\\d\\d))?)?.*?(\\d\\d?)\\\\?[-/](\\d\\d?)\\\\?[-/](\\d\\d(?:\\d\\d)?)"), 7, 5, 6, 1, 2, 4, true);
    dateTimeTokenSeqRules.add(trule = TimeExpressionExtractors.getSequencePatternExtractRule(env,
            "/\\d\\d?:?\\d\\d(:?\\d\\d)?/?  /on/? /\\d\\d?(-|\\\\?\\/)\\d\\d?(-|\\\\?\\/)\\d\\d(\\d\\d)?/", timePatternExtractor));
    timePatternExtractor = TimeExpressionExtractors.getIsoTimeExtractor(
            getPattern("(\\d\\d?):?(\\d\\d)(:?(\\d\\d))?"), 1, 2, 4);
    dateTimeTokenSeqRules.add(trule = TimeExpressionExtractors.getSequencePatternExtractRule(env,
            "/\\d\\d?:\\d\\d(:\\d\\d)?/", timePatternExtractor));
    timePatternExtractor = TimeExpressionExtractors.getIsoDateExtractor(
            getPattern("(\\d\\d\\d\\d)-(\\d\\d)"), 1, 2, -1, false);
    dateTimeTokenSeqRules.add(trule = TimeExpressionExtractors.getSequencePatternExtractRule(env,
            "/\\d\\d\\d\\d-\\d\\d/", timePatternExtractor));

    // Euro - Ambiguous pattern - interpret as DD.MM.YY(YY)
    timePatternExtractor = TimeExpressionExtractors.getIsoDateExtractor(
            getPattern("(\\d\\d?)\\.(\\d\\d?)\\.(\\d\\d(\\d\\d)?)"), 3, 2, 1, true);
    dateTimeStringRules.add(srule = TimeExpressionExtractors.getStringPatternExtractRuleWithWordBoundary(
            env, "\\d\\d?\\.\\d\\d?\\.\\d\\d(\\d\\d)?", timePatternExtractor));

    // day of week
    // TODO: also can have next at end
    timePatternExtractor = new TimeExpressionExtractors.GenericTimePatternExtractor(
            TokenSequencePattern.compile(env, "(?: ($FREQ_MOD) | ($REL_MOD) )? " +
            "($TEDAYS) (?: /the/ (?$day $NUM_ORD) )? (?$tod /(morning|afternoon|evening|night)s?/)?"),
            new Function<MatchResult, SUTime.Temporal>() {
              public SUTime.Temporal apply(MatchResult in) {
                // group1: frequency
                // group2: relative
                // group3: dayofweek
                // group4: date | time of day | next
                boolean isPlural = false;
                String freq = in.group(1);
                String rel = in.group(2);
                String dow = in.group(3);
                String tod = ((SequenceMatchResult) in).group("$tod");
                String day = ((SequenceMatchResult) in).group("$day");
                SUTime.Temporal temporal = lookupTemporal(dow);
                if (temporal != null) {
                  temporal = makeRelative(temporal, rel);
                  if (day != null) {
                    SUTime.Time t = createIsoDate(null, null, day);
                    temporal = new SUTime.RelativeTime(temporal.getTime(), SUTime.TemporalOp.INTERSECT, t);
                  }
                  if (tod != null) {
                    // add morning/afternoon/evening/night/the 3rd
                    SUTime.Temporal t = lookupTemporal(tod);
                    temporal = new SUTime.RelativeTime(temporal.getTime(), SUTime.TemporalOp.INTERSECT, t);
                    if (tod.endsWith("s") || tod.endsWith("S")) {
                      isPlural = true;
                    }
                  }
                  if (freq != null) {
                    temporal = makeSet(temporal, freq);
                  } else  {
                    if (dow.endsWith("s") || dow.endsWith("S")) {
                      isPlural = true;
                    }
                    if (isPlural) {
                      temporal = makeSet(temporal, "");
                    }
                  }
                }
                return temporal;
              }
            });
    dateTimeTokenSeqRules.add(trule = TimeExpressionExtractors.getSequencePatternExtractRule(
            timePatternExtractor.tokenPattern, timePatternExtractor));
    timePatternExtractor = TimeExpressionExtractors.getTimeLookupExtractor(
            this, TokenSequencePattern.compile(env, "/good/ (/morning|evening|day|afternoon|night/)"), 1);
    dateTimeTokenSeqRules.add(trule = TimeExpressionExtractors.getSequencePatternExtractRule(
            timePatternExtractor.tokenPattern, timePatternExtractor));

    // yesterday/today/tomorrow
    timePatternExtractor = new TimeExpressionExtractors.GenericTimePatternExtractor(
            TokenSequencePattern.compile(env, " (?: /the/? /day/ (/before|after/))? ($RELDAY) (morning|afternoon|evening|night)?"),
            new Function<MatchResult, SUTime.Temporal>() {
              public SUTime.Temporal apply(MatchResult in) {
                // group1: before/after
                // group2: relday
                // group3: time of day
                String relday = in.group(2).toLowerCase();
                SUTime.Temporal t = lookupTemporal(relday);
                SUTime.Time tm = t.getTime();
                // morning/afternoon/evening/night
                String str = in.group(3);
                if (str != null) {
                  SUTime.Temporal tod = lookupTemporal(str);
                  if (tod != null) {
                    // TemporalOp.IN ?
                    tm = new SUTime.RelativeTime(tm, SUTime.TemporalOp.INTERSECT, tod);
                  }
                }
                // before/after
                str = in.group(1);
                if (str != null) {
                  if (str.equalsIgnoreCase("before")) {
                    tm = tm.add(SUTime.DAY.multiplyBy(-1));
                  } else if (str.equalsIgnoreCase("after")) {
                    tm = tm.add(SUTime.DAY);
                  }
                  }
                return tm;
              }
            });
    dateTimeTokenSeqRules.add(trule = TimeExpressionExtractors.getSequencePatternExtractRule(
            timePatternExtractor.tokenPattern, timePatternExtractor));

    // this (morning/afternoon/evening)
    timePatternExtractor = TimeExpressionExtractors.getRelativeTimeLookupExtractor(
            this,
            TokenSequencePattern.compile(env, "(/early|late/)? /this/? (/morning|afternoon|evening/)"),
            SUTime.TIME_REF, SUTime.TemporalOp.THIS, 2);
    dateTimeTokenSeqRules.add(trule = TimeExpressionExtractors.getSequencePatternExtractRule(
            timePatternExtractor.tokenPattern, timePatternExtractor));

    timePatternExtractor = TimeExpressionExtractors.getRelativeTimeLookupExtractor(
            this,
            TokenSequencePattern.compile(env, "(/early|late/)? /last/ (/night/)"),
            SUTime.TIME_REF, SUTime.TemporalOp.PREV, 2);
    dateTimeTokenSeqRules.add(trule = TimeExpressionExtractors.getSequencePatternExtractRule(
            timePatternExtractor.tokenPattern, timePatternExtractor));

    timePatternExtractor = new TimeExpressionExtractors.GenericTimePatternExtractor(
            TokenSequencePattern.compile(env, " (/every/ $NUM_ORD) ($TEMONTHS)"),
            new Function<MatchResult, SUTime.Temporal>() {
              public SUTime.Temporal apply(MatchResult in) {
                SUTime.Temporal t = lookupTemporal(in.group(2));
                return makeSet(t, in.group(1));
              }
            }
    );
    dateTimeTokenSeqRules.add(trule = TimeExpressionExtractors.getSequencePatternExtractRule(
            timePatternExtractor.tokenPattern, timePatternExtractor));

    // Crazy date expressions
    // (next/last) (OrdinalWord of|DayofMonth) MonthName
    //      (DayOfMonth|OrdinalWord|OrdinalNum) ((of) year)
    // TODO: mid- in same token, monthabbr.
    timePatternExtractor = new TimeExpressionExtractors.GenericTimePatternExtractor(
            TokenSequencePattern.compile(env, " ((/early|late/) /in|on/?)?  " +
                    "(?: /the/? ( (?$weeknum ($NUM_ORD|last)? /week(end)?/ ) | (?$tod /morning|day|afternoon|evening|night/) | " +
                      " (?$mod /beginning|start|middle|end|ides|nones/) ) /of|in/? )? " +
                    "/the/? (?: (?$rel $REL_MOD) | /mid-?/ | (?$day $NUM_ORD) /of/? | (?$day /\\d\\d?/ & $INT1TO31) )?" +
                    "(?$month $TEMONTHS)  (?$day $NUM_ORD|/\\d\\d?/ & $INT1TO31)? (?: /of|,/? (?$year $POSSIBLE_YEAR))?"),
            new Function<MatchResult, SUTime.Temporal>() {
              public SUTime.Temporal apply(MatchResult in) {
                if (in instanceof SequenceMatchResult) {
                  SequenceMatchResult<CoreMap> r = (SequenceMatchResult<CoreMap>) in;
                  String weeknum = r.group("$weeknum");
                  String tod = r.group("$tod");
                  String month = r.group("$month");
                  String year = r.group("$year");
                  String day = r.group("$day");
                  String rel = r.group("$rel");
                  if (weeknum == null && tod == null && year == null && day == null) {
                    List<? extends CoreMap> mnodes = r.groupNodes("$month");
                    if (!mnodes.get(0).get(CoreAnnotations.PartOfSpeechAnnotation.class).startsWith("NN")) {
                      return SUTime.TIME_NONE_OK;
                    }
                  }

                  if (day == null) {
                    String mod = r.group("$mod");
                    if (mod != null) {
                      if (mod.equalsIgnoreCase("ides") || mod.equalsIgnoreCase("nones")) {
                        day = mod;
                      }
                    }
                  }
                  SUTime.Temporal t = createIsoDate(year, month, day);;
                  if (rel != null) {
                    t = makeRelative(t, rel);
                  }
                  if (weeknum != null) {
                    // weeknum
                    int i = weeknum.lastIndexOf(" ");
                    if (i >= 0) {
                     String ord = weeknum.substring(0,i);
                     String week = weeknum.substring(i);
                     SUTime.Temporal weekTemp = lookupTemporal(week);
                     Number ordNum = ("last".equalsIgnoreCase(ord))? -1: NumberNormalizer.wordToNumber(ord);
                      if (ordNum != null) {
                        weekTemp = new SUTime.OrdinalTime(weekTemp, ordNum.intValue());
                        t = SUTime.TemporalOp.IN.apply(t, weekTemp);
                      } else {
                        t = SUTime.TemporalOp.INTERSECT.apply(t, weekTemp);
                      }
                    } else {
                      t = SUTime.TemporalOp.INTERSECT.apply(t, lookupTemporal(weeknum));
                    }
                  }
                  if (tod != null) {
                    SUTime.Temporal temporal = lookupTemporal(tod);
                    if (temporal != null) {
                      t = SUTime.TemporalOp.INTERSECT.apply(t, temporal);
                    }
                  }
                  return t;
                }
                return null;
              }
            }
    );
    dateTimeTokenSeqRules.add(trule = TimeExpressionExtractors.getSequencePatternExtractRule(
            timePatternExtractor.tokenPattern, timePatternExtractor));

    // !the (this(past)/next/last/)
    // (week|month|quarter|year|decade|century|spring|summer|winter|fall|autumn)
    timePatternExtractor = new TimeExpressionExtractors.GenericTimePatternExtractor(
            TokenSequencePattern.compile(env, "( $EARLY_LATE_MOD )? (?: ($FREQ_MOD) | ($REL_MOD) ) " +
                   "(/millisecond|second|minute|hour|weekend|week|fortnight|month|quarter|year|decade|century|millenn?ium|spring|summer|winter|fall|autumn/)"),
            new Function<MatchResult, SUTime.Temporal>() {
              public SUTime.Temporal apply(MatchResult in) {
                // group1: early/mod
                // group2: freq mod
                // group3: relative mod
                // group4: duration
                String freq = in.group(2);
                String rel = in.group(3);
                String dur = in.group(4);

                SUTime.Temporal t = lookupTemporal(dur);
                if (t != null) {
                  t = makeRelative(t, rel);
                  t = makeSet(t, freq);
                }
                return t;
              }
            });
    dateTimeTokenSeqRules.add(trule = TimeExpressionExtractors.getSequencePatternExtractRule(
            timePatternExtractor.tokenPattern, timePatternExtractor));

    // seasons
    timePatternExtractor = TimeExpressionExtractors.getRelativeTimeExtractor(
            TokenSequencePattern.compile(env, "( /each/ | /late|early/  /in/?  | /every/ (?: /other/ | $NUM_ORD)? | /the/? /beginning|start|dawn|middle|end/ /of/ )? (?: /the/ )? ($SEASON) /of/? ($POSSIBLE_YEAR)?"),
            new TimeExpressionExtractors.IsoDateTimePatternFunc(3,-1,-1,-1,-1,-1,true),
            new TimeExpressionExtractors.TemporalOpConstFunc(SUTime.TemporalOp.INTERSECT),   // Op.IN?
            new TimeExpressionExtractors.TemporalLookupFunc(this, 2));

    dateTimeTokenSeqRules.add(trule = TimeExpressionExtractors.getSequencePatternExtractRule(
            timePatternExtractor.tokenPattern, timePatternExtractor));

    // holidays
    // Day/Eve
 /*   dateTimeStringExtractRules.add(rule =

            createDateTimeRule("(($OT+(this($CT+\\s+$OT+(coming|past))?|next|last|each|every($CT+\\s+$OT+(other|$TENumOrds|$TEOrdinalWords))?)$CT+\\s+)?$OT+(Xmas|Christmas|Thanksgiving)$CT+(\\s*$OT+(Day|Eve)$CT+)?(($OT+,$CT+)?\\s+($OT+of$CT+\\s+)?$OT+\\d\\d\\d\\d$CT+)?)", SUTime.TimexType.DATE));

    // Possessive's Day
    dateTimeStringExtractRules.add(rule = new TimeExpressionExtractors.FilterRule<String,IntPair>(
            new PatternFilter("\\bday\\b"),
            createDateTimeRule("(($OT+(this($CT+\\s+$OT+(coming|past))?|next|last|each|every($CT+\\s+$OT+(other|$TENumOrds|$TEOrdinalWords))?)$CT+\\s+)?$OT+((Saint|St$CT*$OT*\\.)$CT+\\s+$OT+(\\w+|Jean$CT+\\s+$OT+Baptiste)|Ground$CT+\\s+$OT+Hog|April$CT+\\s+$OT+Fool|Valentine|Mother|Father|Veteran|President)($CT*$OT*\\'?$CT*$OT*s)?$CT+\\s+$OT+Day$CT+(($OT+,$CT+)?\\s+($OT+of$CT+\\s+)?$OT+\\d\\d\\d\\d$CT+)?)", SUTime.TimexType.DATE)));
    dateTimeStringExtractRules.add(rule = new TimeExpressionExtractors.FilterRule<String,IntPair>(
            new PatternFilter("\\bday\\b"),
            createDateTimeRule("(($OT+(this($CT+\\s+$OT+(coming|past))?|next|last|each|every($CT+\\s+$OT+(other|$TENumOrds|$TEOrdinalWords))?)$CT+\\s+)?$OT+(Flag|Memorial|Independence|Labor|Columbus|Bastille|Canberra|Dominion|Canada|Boxing|Election|Inauguration|Guy$CT+\\s+$OT+Fawkes|MLK|(Martin$CT+\\s+$OT+Luther$CT+\\s+$OT+)?King|May|All$CT+\\s+$OT+(Saint|Soul)s($CT*$OT*\\')?)$CT+\\s*$OT+Day$CT+(($OT+,$CT+)?\\s+($OT+of$CT+\\s+)?$OT+\\d\\d\\d\\d$CT+)?)", SUTime.TimexType.DATE)));
    */
    // Birthdays
    timePatternExtractor = new TimeExpressionExtractors.GenericTimePatternExtractor(
            TokenSequencePattern.compile(env, "[ { tag:NNP } ]+ [ { tag:POS } ] /birthday/"),
            new Function<MatchResult, SUTime.Temporal>() {
              public SUTime.Temporal apply(MatchResult in) {
                return new SUTime.SimpleTime(in.group());
              }
            }
    );
    dateTimeTokenSeqRules.add(trule = TimeExpressionExtractors.getSequencePatternExtractRule(
            timePatternExtractor.tokenPattern, timePatternExtractor));

  /*  //new year
    dateTimeStringExtractRules.add(rule =
            createDateTimeRule("(($OT+(this($CT+\\s+$OT+(coming|past))?|next|last|each|every($CT+\\s+$OT+(other|$TENumOrds|$TEOrdinalWords))?)$CT+\\s+)?$OT+New$CT+\\s*$OT+Year$CT+$OT+\\'$CT*$OT*s$CT+\\s*$OT+(Day|Eve)$CT+(($OT+,$CT+)?\\s+$OT+\\d{4}$CT+)?)", SUTime.TimexType.DATE));
    // holidays that are not X Day
    dateTimeTokenSeqRules.add(srule = TimeExpressionExtractors.getSequencePatternExtractRule("(($OT+(this($CT+\\s+$OT+(coming|past))?|next|last|each|every($CT+\\s+$OT+(other|$TENumOrds|$TEOrdinalWords))?)$CT+\\s+)?$OT+(Halloween|Allhallow(ma)?s|C?Hanukk?ah|Rosh$CT+\\s*$OT+Hashanah|Yom$CT+\\s*$OT+Kippur|Passover|Ramadan|Cinco$CT+\\s+$OT+de$CT+\\s+$OT+Mayo|tet|diwali|kwanzaa|Easter($CT+\\s+$OT+Sunday)?|palm$CT+\\s+$OT+sunday|mardis$CT+\\s+$OT+gras|shrove$CT+\\s+$OT+tuesday|ash$CT+\\s+$OT+wednesday|good$CT+\\s+$OT+friday|walpurgisnacht|beltane|candlemas|day$CT+\\s+$OT+of$CT+\\s+$OT+the$CT+\\s+$OT+dead)$CT+(($OT+,$CT+)?\\s+($OT+of$CT+\\s+)?$OT+\\d\\d\\d\\d$CT+)?)", SUTime.TimexType.DATE));
  */
    // Generic decade
    timePatternExtractor = TimeExpressionExtractors.getIsoDateExtractor(
            TokenSequencePattern.compile(env, "/the/? $EARLY_LATE_MOD? ( /'/ /\\d0s/ | /'\\d0s/ | /\\w+teen/? /(twen|thir|for|fif|six|seven|eigh|nine)ties/ | /\\d\\d\\d\\ds/ )"),
            1, -1, -1, true);
    dateTimeTokenSeqRules.add(trule = TimeExpressionExtractors.getSequencePatternExtractRule(
            timePatternExtractor.tokenPattern, timePatternExtractor));

    //generic season -regular season, baseball season, the holiday season
//    dateTimeTokenSeqRules.add(srule = TimeExpressionExtractors.getSequencePatternExtractRule("($OT+(this|the|last|next|a|regular|last|sports|four)$CT+\\s+$OT+(season)$CT)", SUTime.TimexType.DATE));

    // some century expressions
    timePatternExtractor = new TimeExpressionExtractors.GenericTimePatternExtractor(
            TokenSequencePattern.compile(env, "/the/? $EARLY_LATE_MOD? ($NUM_ORD) /-/? /century/ (/a\\.?d\\.?|b\\.?c\\.?/)?"),
            new Function<MatchResult, SUTime.Temporal>() {
              public SUTime.Temporal apply(MatchResult in) {
                Number num = NumberNormalizer.wordToNumber(in.group(1));
                if (num != null) {
                 // return new SUTime.OrdinalTime(SUTime.CENTURY, num.intValue());
                  String s = (num.intValue()-1) + "00s";
                  String era = in.group(2);
                  if (era != null) { s = s + " " + era; }
                  return createIsoDate(s, null, null);
                }
                return null;
              }
            });
    dateTimeTokenSeqRules.add(trule = TimeExpressionExtractors.getSequencePatternExtractRule(timePatternExtractor.tokenPattern, timePatternExtractor));

    // some quarter expressions - need to add year refs
    timePatternExtractor = new TimeExpressionExtractors.GenericTimePatternExtractor(
            TokenSequencePattern.compile(env, "/the/? [{tag:JJ}]? ($NUM_ORD) /-/? [{tag:JJ}]? /quarter/"),
            new Function<MatchResult, SUTime.Temporal>() {
              public SUTime.Temporal apply(MatchResult in) {
                Number num = NumberNormalizer.wordToNumber(in.group(1));
                if (num != null) {
            //      return new SUTime.OrdinalTime(SUTime.QUARTER, num.intValue());
                  return SUTime.StandardTemporalType.QUARTER_OF_YEAR.createTemporal(num.intValue());
                }
                return null;
              }
            });
    dateTimeTokenSeqRules.add(trule = TimeExpressionExtractors.getSequencePatternExtractRule(timePatternExtractor.tokenPattern, timePatternExtractor));
    timePatternExtractor = new TimeExpressionExtractors.GenericTimePatternExtractor(
            getPattern("$NUM_ORD_TERM-quarter"),
            new Function<MatchResult, SUTime.Temporal>() {
              public SUTime.Temporal apply(MatchResult in) {
                Number num = NumberNormalizer.wordToNumber(in.group(1));
                if (num != null) {
            //      return new SUTime.OrdinalTime(SUTime.QUARTER, num.intValue());
                  return SUTime.StandardTemporalType.QUARTER_OF_YEAR.createTemporal(num.intValue());
                }
                return null;
              }
            });
//    dateTimeTokenSeqRules.add(trule = TimeExpressionExtractors.getSequencePatternExtractRule(
//            TokenSequencePattern.compile(env, "/the/? [{tag:JJ}]? /" + numOrdTerm.pattern() + "-quarter/"), timePatternExtractor));
    dateTimeStringRules.add(srule = TimeExpressionExtractors.getStringPatternExtractRule(timePatternExtractor.stringPattern, timePatternExtractor));

    // past|present|future refs   "The past" is special case
    timePatternExtractor = TimeExpressionExtractors.getTimeLookupExtractor(
            this,
            TokenSequencePattern.compile(env, "/current|once|medieval/ | /the/ /future/"), 0);
    dateTimeTokenSeqRules.add(trule = TimeExpressionExtractors.getSequencePatternExtractRule(timePatternExtractor.tokenPattern, timePatternExtractor));

    // jfrank changes here - originally there was a ? after "(\s*$OT+(week|fortnight...autumn)$CT+)" below
    // - this gets rid of "the past" as a special case, but this was conflicting with duration constructions such as "the past three weeks"
    // - therefore, the few lines of code following the next line deal with the special case of "the past"
    // Covered by pattern from before...
/*    timePatternExtractor = TimeExpressionExtractors.getRelativeTimeLookupExtractor(
            TokenSequencePattern.compile(env, "/the/ /past/ (week|fortnight|month|quarter|year|decade|century|spring|summer|winter|fall|autumn)"),
            SUTime.TIME_REF, SUTime.TemporalOp.PREV, 1);
    dateTimeTokenSeqRules.add(trule = TimeExpressionExtractors.getSequencePatternExtractRule(timePatternExtractor.tokenPattern, timePatternExtractor));
    */
    // special case of "the past" - this needs to come after the durations processing so it doesn't conflict with cases like "the past 12 days"
    /*   dateTimeRules.add(rule = new DateTimeRule() {
  public String transform(String str) {
    while ($string =~ /(($OT+)the$CT+\s$OT+past$CT+)/gi){
      my $currentThePastPattern = $1;
      my $currentOpeningTags = $2;
      unless ($currentOpeningTags =~ /<TIMEX/){
        $string =~ s/$currentThePastPattern/<TIMEX$tever TYPE=\"DATE\">$currentThePastPattern<\/TIMEX$tever>/g;
      }
    }
  }  */

    // each|every unit - covered somwhere
    //$string =~ s/($OT+(alternate|each|every($CT+\s+$OT+(other|$TENumOrds|$TEOrdinalWords))?)$CT+\s+$OT+(minute|hour|day|week|month|year)s?$CT+)/<TIMEX$tever TYPE=\"DATE\">$1<\/TIMEX$tever>/gosi;
 /*   timePatternExtractor = new TimeExpressionExtractors.GenericTimePatternExtractor(
            TokenSequencePattern.compile(env, "($FREQ_MOD) (/(second|minute|hour|day|week|month|year|decade|century|millenn?i(?:um|a))s?/)"),
            new Function<MatchResult, SUTime.Temporal>() {
              public SUTime.Temporal apply(MatchResult in) {
                SUTime.Temporal t = lookupTemporal(in.group(2));
                return makeSet(t, in.group(1));
              }
            });
    dateTimeTokenSeqRules.add(trule = TimeExpressionExtractors.getSequencePatternExtractRule(
            timePatternExtractor.tokenPattern, timePatternExtractor));    */

    // (unit)ly
    timePatternExtractor = new TimeExpressionExtractors.GenericTimePatternExtractor(
            TokenSequencePattern.compile(env, "(?m){1,3} /((bi|semi)\\s*-?\\s*)?((annual|year|month|week|dai|hour|night|quarter)ly|annual)/"),
            new Function<MatchResult, SUTime.Temporal>() {
              public SUTime.Temporal apply(MatchResult in) {
                int scale = 1;
                boolean divide = false;
                String g = in.group().toLowerCase();
                if (g.startsWith("bi")) {
                  scale = 2;
                  g = g.replaceFirst("^bi\\s*\\-?\\s*","");
                } else if (g.startsWith("semi")) {
                  scale = 2;
                  divide = true;
                  g = g.replaceFirst("^semi\\s*\\-?\\s*","");
                }
                SUTime.Temporal t = lookupTemporal(g);
                if (t != null && scale != 1) {
                  t = (divide)? ((SUTime.PeriodicTemporalSet) t).divideDurationBy(scale):
                          ((SUTime.PeriodicTemporalSet) t).multiplyDurationBy(scale);
                }
                return t;
              }
            });
    dateTimeTokenSeqRules.add(trule = TimeExpressionExtractors.getSequencePatternExtractRule(
            timePatternExtractor.tokenPattern, timePatternExtractor));

    // (unit) before last/after next
 //   dateTimeTokenSeqRules.add(srule = TimeExpressionExtractors.getSequencePatternExtractRule("/the/? (/year|month|week|day|night/)"
 //           "(($OT+the$CT+\\s+$OT+)?$OT+(year|month|week|day|night)$CT+\\s+$OT+(before$CT+\\s+$OT+last|after$CT+\\s+$OT+next)$CT+)", SUTime.TimexType.DATE));

    // some interval expressions
    timePatternExtractor = new TimeExpressionExtractors.GenericTimePatternExtractor(
            Pattern.compile("\b(\\d{4})\\s*(?:-|to)\\s*(\\d{4})\b"),
            new Function<MatchResult, SUTime.Temporal>() {
              public SUTime.Temporal apply(MatchResult in) {
                String y1 = in.group(1);
                String y2 = in.group(2);
                return new SUTime.Range(createIsoDate(y1,null,null), createIsoDate(y2,null,null));
              }
            });

    dateTimeStringRules.add(srule = TimeExpressionExtractors.getStringPatternExtractRule(timePatternExtractor.stringPattern, timePatternExtractor));
    timePatternExtractor = new TimeExpressionExtractors.GenericTimePatternExtractor(
            TokenSequencePattern.compile(env, "/the/ /weekend/"),
            new TimeExpressionExtractors.TemporalConstFunc(SUTime.WEEKEND));
    dateTimeTokenSeqRules.add(trule = TimeExpressionExtractors.getSequencePatternExtractRule(timePatternExtractor.tokenPattern, timePatternExtractor));

    // year|month|week|day ago|from_now
   // dateTimeTokenSeqRules.add(srule = TimeExpressionExtractors.getSequencePatternExtractRule("", SUTime.TimexType.DATE));
    /*
    dateTimeStringExtractRules.add(rule = new TimeExpressionExtractors.FilterRule<String,IntPair>(
            new PatternFilter("\\b(ago|hence|from)\\b"),
            createDateTimeRule("(($OT+(about|around|some)$CT+\\s+)?($OT*$OTCD$OT*\\S+$CT+\\s+($OT+and$CT+\\s+)?)*($OT*$OTCD$OT*\\S+$CT+\\s+)$OT+(year|month|week|day|decade|cenutur(y|ie))s?$CT+\\s+$OT+(ago($CT+\\s+$OT+(today|tomorrow|yesterday|$TEday))?|hence|from$CT+\\s$OT+(now|today|tomorrow|yesterday|$TEday))$CT+)", SUTime.TimexType.DATE),
            createDateTimeRule("(($OT+(about|around|some)$CT+\\s+)?$OT+(a($CT+\\s+$OT+few)?|several|some|many)$CT+\\s+$OT+(year|month|fortnight|moon|week|day|((little|long)$CT+\\s+$OT+)?while|decade|centur(y|ie)|(((really|very)$CT+\\s+$OT+)?((long$CT+$OT+,$CT+\\s+$OT+)*long|short)$CT+\\s+$OT+)?(life)?time)s?$CT+\\s+$OT+(ago($CT+\\s+$OT+(today|tomorrow|yesterday|$TEday))?|hence|from$CT+\\s$OT+(now|today|tomorrow|yesterday|$TEday))$CT+)", SUTime.TimexType.DATE),
            //new StringPatternRule("<TIMEX$tever[^>]*>($OT+(today|tomorrow|yesterday|$TEday)$CT+<\\/TIMEX$tever>)$CT*<\\/TIMEX$tever>", "$1"),
            createDateTimeRule("($OT+(ages|long)$CT+\\s+$OT+ago$CT+)", SUTime.TimexType.DATE)));
       */

    // Now a few time expressions

    // 24 hour Euro time
    timePatternExtractor = TimeExpressionExtractors.getIsoTimeExtractor(
            getPattern(".*(\\d\\d?)h(\\d\\d).*"), 1, 2, -1);
    dateTimeTokenSeqRules.add(trule = TimeExpressionExtractors.getSequencePatternExtractRule(
            env, "/about|around|some/?  /\\d\\d?h\\d\\d/", timePatternExtractor));

    dateTimeTokenSeqRules.add(new SequenceMatchRules.FilterExtractRule<List<? extends CoreMap>,TimeExpression>(
            new TokenSeqPatternFilter(TokenSequencePattern.compile(env, "/time/")),
            TimeExpressionExtractors.getSequencePatternExtractRule(env, "(/\\d{4}/ /hours/?)? (/universal|zulu/ | /[a-z]+/ /standard|daylight/) /time/",  null),
            TimeExpressionExtractors.getSequencePatternExtractRule(env, "(/\\d\\d?/ /hours/?) (/\\d\\d?/ /minutes/?)? (/universal|zulu/ | /[a-z]+/ /standard|daylight/) /time/", null)));

    timePatternExtractor = TimeExpressionExtractors.getIsoTimeExtractor(
            getPattern("(\\d\\d):?(\\d\\d)(:?(\\d\\d))?\\s*h(ou)/rs?"), 1,2,3);
    dateTimeStringRules.add(srule = TimeExpressionExtractors.getStringPatternExtractRule(timePatternExtractor.stringPattern, timePatternExtractor));

    // Crazy long time expression
    // o'clock, the hour of, bunch of stuff
    // TODO: Add optional timezone
    // TODO: Fix 3 to 7
    timePatternExtractor = new TimeExpressionExtractors.GenericTimePatternExtractor(
            TokenSequencePattern.compile(env,
                    "$APPROX_MOD? (?: (/a/? /quarter/| /half/ | [ $INT & { numcompvalue<=60 } ] /minutes?/? ) (/past|after|of|before|to|until/))? " +
                    "(/noon/|/midnight/|/the/ /hour/ (?: /of/ [ $INT & { numcompvalue<=24 } ] )?|/\\d\\d?:\\d\\d/|[ $INT & { numcompvalue<=24 } ] /o'?clock/?) " +
                    "(/in/ /the/ /morning|afternoon|evening/| /at/ /night/|/a\\.?m\\.?/|/p\\.?m\\.?/)? " +
                    "(?: /sharp/|/exactly/|/precisely/|/on/ /the/ /dot/)?"),
            new Function<MatchResult, SUTime.Temporal>() {
              TokenSequencePattern bareNumToNumPattern =
                      TokenSequencePattern.compile(env, " $INT (/of|to/ $INT)? ");
              public SUTime.Temporal apply(MatchResult in) {
                // TODO: Build conjunction into generic SequencePatterns... and do filtering when looking for patterns instead of extracting time...
                // Don't recognize 3 to 4 as time (it may be if we have enough context)
                SequenceMatchResult<CoreMap> smr = (SequenceMatchResult<CoreMap>) in;
                TokenSequenceMatcher bm = bareNumToNumPattern.getMatcher(smr.groupNodes());
                if (bm.matches()) {
                  return SUTime.TIME_NONE_OK;
                } /*else if (bm.find()) {
                  if (bm.start() == 0) {
                    if (smr.start() > 0) {
                      CoreMap prev = smr.elements().get(smr.start() - 1);
                      String prevW = prev.get(CoreAnnotations.TextAnnotation.class);
                      if (prevW.equalsIgnoreCase("from")) {
                        return SUTime.TIME_NONE_OK;
                      }
                    }
                  }
                }   */

                // group1: minutes
                // group2: past/after => AFTER,  of/before/to/until => BEFORE
                // group3: hour
                // group4: morning/afternoon/evening/night/am/pm

                int minutes = 0;
                SUTime.Time t = null;
                // Figure out minutes (group1)
                String str = in.group(1);
                if (str != null) {
                  str = str.toLowerCase();
                  if (str.contains("quarter")) {
                    minutes = 15;
                  } else if (str.contains("half")) {
                    minutes = 30;
                  } else {
                    str = str.replaceAll("minutes?", "");
                    minutes = NumberNormalizer.wordToNumber(str).intValue();
                  }
                  str = in.group(2).toLowerCase();
                  if (str.equals("past") || str.equals("after")) {
                    // after - keep minutes as is
                  } else {
                    // before
                    minutes = -minutes;
                  }
                }
                str = in.group(3);
                if (str != null) {
                  str = str.toLowerCase();
                  if (str.contains("noon")) {
                    t = SUTime.NOON;
                  } else if (str.contains("midnight")) {
                    t = SUTime.MIDNIGHT;
                  } else if (str.matches("\\d\\d?:\\d\\d")) {
                    String[] fields = str.split(":");
                    int h = NumberNormalizer.wordToNumber(fields[0]).intValue();
                    int m = NumberNormalizer.wordToNumber(fields[1]).intValue();
                    str = in.group(4);
                    if (str != null) {
                      str = str.toLowerCase();
                      if (str.contains("night")) {
                        // 0 - 4 at night probably means a.m.
                        if (h > 4 && h < 12) {
                          h+=12;
                        }
                      } else if (str.contains("afternoon") ||
                              str.contains("evening") || str.matches(".*p\\.?m\\.?.*")) {
                        if (h < 12) {
                          h+=12;
                        }
                      }
                    }
                    t = new SUTime.IsoTime(h,m,-1);
                  } else {
                    String orig = str;
                    str = str.replaceAll(".*\bof\b", "");
                    str = str.replaceAll("o'?clock", "");
                    if (str.equals(orig)) {
                      // Bare number
                      // Need at least one of the other phrases
                      if (in.group(1) == null && in.group(2) == null && in.group(4) == null) {
                        return SUTime.TIME_NONE_OK;
                      }
                    }
                    int h = NumberNormalizer.wordToNumber(str).intValue();
                    str = in.group(4);
                    if (str != null) {
                      str = str.toLowerCase();
                      if (str.contains("night")) {
                        // 0 - 4 at night probably means a.m.
                        if (h > 4 && h < 12) {
                          h+=12;
                        }
                      } else if (str.contains("afternoon") ||
                              str.contains("evening") || str.matches(".*p\\.?m\\.?.*")) {
                        if (h < 12) {
                          h+=12;
                        }
                      }
                    }
                    t = new SUTime.IsoTime(h, 0, -1);
                  }
                  if (minutes != 0) {
                    t = t.add(SUTime.MINUTE.multiplyBy(minutes));
                  }
                }
                return t;
              }
            });
    dateTimeTokenSeqRules.add(trule = TimeExpressionExtractors.getSequencePatternExtractRule(
            timePatternExtractor.tokenPattern, timePatternExtractor));
/*
    // X hours|minutes ago
    dateTimeStringExtractRules.add(rule = new TimeExpressionExtractors.FilterRule<String,IntPair>(
            new PatternFilter("\\b(ago|hence|from)\\b"),
            createDateTimeRule("(($OT+(about|around|some)$CT+\\s+)?($OT*$OTCD$OT*\\S+$CT+\\s+($OT+and$CT+\\s+)?)*$OT*$OTCD$OT*\\S+$CT+\\s+($OT+and$CT+\\s+$OT+a$CT+\\s+$OT+half$CT+\\s+)?$OT+hours?$CT+\\s+$OT+(ago|hence|from$CT+\\s+$OT+now)$CT)", SUTime.TimexType.TIME),
            createDateTimeRule("($OT+(a$CT+\\s+$OT+few|several|some)$CT+\\s+$OT+hours$CT+\\s+$OT+(ago|hence|from$CT+\\s+$OT+now)$CT)", SUTime.TimexType.TIME),
            createDateTimeRule("(($OT+about$CT+\\s+)?($OT+an?$CT+\\s+)?($OT+(half($CT+\\s+$OT+an)?|few)$CT+\\s+)?$OT+hour$CT+\\s+($OT+and$CT+\\s+$OT+a$CT+\\s+$OT+half$CT+\\s+)?$OT+(ago|hence|from$CT+\\s+$OT+now)$CT)", SUTime.TimexType.TIME),
            createDateTimeRule("(($OT*(($OT*$OTCD$OT*\\S+$CT+\\s+($OT+and$CT+\\s+)?)*$OT*$OTCD$OT*\\S+|$OT+(a|several|some)($CT+\\s+$OT+few)?)$CT+\\s+)+$OT+minutes?$CT+\\s+$OT+(ago|hence|from$CT+\\s+$OT+now)$CT)", SUTime.TimexType.TIME)));

    // right now and Now (by itself) */
    dateTimeTokenSeqRules.add(trule = TimeExpressionExtractors.getSequencePatternExtractRule(
            env, " /right/? /now/ ", TimeExpressionExtractors.getTimeExtractor(SUTime.TIME_PRESENT)));
    dateTimeTokenSeqRules.add(trule = TimeExpressionExtractors.getSequencePatternExtractRule(
            env, " /the/ /near/ /future/ ", TimeExpressionExtractors.getTimeExtractor(SUTime.TIME_FUTURE)));

    timePatternExtractor = TimeExpressionExtractors.getIsoDateExtractor(
            TokenSequencePattern.compile(env, "$POSSIBLE_YEAR"), 0,-1,-1, true);
    dateTimeTokenSeqRules.add(trule = TimeExpressionExtractors.getSequencePatternExtractRule(
             TokenSequencePattern.compile(env, "/the/? /year/ ($POSSIBLE_YEAR)"), timePatternExtractor));

    timePatternExtractor = TimeExpressionExtractors.getIsoDateExtractor(
            TokenSequencePattern.compile(env, "[ { ner::IS_NIL } | { ner:DATE } | { ner:O } | { ner:NUMBER } ]+"), 0,-1,-1, true);
    dateTimeTokenSeqRules.add(trule = TimeExpressionExtractors.getSequencePatternExtractRule(
             TokenSequencePattern.compile(env, "($POSSIBLE_YEAR)"), timePatternExtractor));
/*    timePatternExtractor = TimeExpressionExtractors.getRelativeTimeExtractor(
            TokenSequencePattern.compile(env, "/the/? $TIMEOFDAY"),
            new TimeExpressionExtractors.TemporalConstFunc(SUTime.TIME_REF_UNKNOWN),
            new TimeExpressionExtractors.TemporalOpConstFunc(SUTime.TemporalOp.INTERSECT),
            new TimeExpressionExtractors.TemporalLookupFunc(0));     */
    timePatternExtractor = TimeExpressionExtractors.getTimeLookupExtractor(
            this, TokenSequencePattern.compile(env, "/the/? ($TIMEOFDAY)"), 1);
    dateTimeTokenSeqRules.add(trule = TimeExpressionExtractors.getSequencePatternExtractRule(
            timePatternExtractor.tokenPattern, timePatternExtractor));
    timePatternExtractor = TimeExpressionExtractors.getRelativeTimeLookupExtractor(
            this,
            TokenSequencePattern.compile(env, "/the/ [ { tag:JJ } ]* ($TEUNITS_NODE)"),
            null, SUTime.TemporalOp.THIS, 1);
    dateTimeTokenSeqRules.add(trule = TimeExpressionExtractors.getSequencePatternExtractRule(
            timePatternExtractor.tokenPattern, timePatternExtractor));

    timePatternExtractor = new TimeExpressionExtractors.GenericTimePatternExtractor(
            TokenSequencePattern.compile(env, "/the/ /past/ | /recently/ | /previously/"),
            new TimeExpressionExtractors.TemporalConstFunc(SUTime.TIME_PAST));
    dateTimeTokenSeqRules.add(trule = TimeExpressionExtractors.getSequencePatternExtractRule(timePatternExtractor.tokenPattern, timePatternExtractor));


    timePatternExtractor = new TimeExpressionExtractors.GenericTimePatternExtractor(
            TokenSequencePattern.compile(env, "/currently/)"),
            new TimeExpressionExtractors.TemporalConstFunc(SUTime.TIME_PRESENT));
    dateTimeTokenSeqRules.add(trule = TimeExpressionExtractors.getSequencePatternExtractRule(timePatternExtractor.tokenPattern, timePatternExtractor));
  }

  public SUTime.Duration getDuration(String unit)
  {
    unit = unit.toLowerCase();
    if (unit.endsWith(".")) {
      // Remove trailing period in abbreviations
      unit = unit.substring(0, unit.length()-1);
    }
    return abbToTimeUnit.get(unit);
  }
  public SUTime.Duration getDuration(String val, String unit)
  {
    unit = unit.toLowerCase();
    if (unit.endsWith(".")) {
      // Remove trailing period in abbreviations
      unit = unit.substring(0, unit.length()-1);
    }
    // TODO: What if not in map?
    SUTime.Duration d = abbToTimeUnit.get(unit);
    if (d != null) {
      if ("X".equals(val)) {
        return d.makeInexact();
      }
      if (val == null) {
        return null;
      }
      Matcher matcher = Pattern.compile("(\\d+)").matcher(val);
      if (matcher.find()) {
        int m = Integer.parseInt(matcher.group());
        d = d.multiplyBy(m);
      } else {
        Number n = null;
        n = NumberNormalizer.wordToNumber(val);
        if (n != null) {
          int m = n.intValue();
          d = d.multiplyBy(m);
        } else {
          // TODO: unspecified...
        }
      }
    }
    return d;
  }

  // Determine flags for resolving expressions (from GUTime, doesn't seem to do much */
/*  public int determineRelFlags(SequenceMatchResult<CoreMap> m)
  {
		// Handle relative expressions requiring relative direction
    int flags = 0;
		if(teHeurLevel > 1) {
		  String reason = "";
		  String vb2  = "";
		  String vb2Pos   = "";
      String vbPos = "";
      String vb = "";
      String precedingWord = "";

		  // find the relevant verb and POS
      // TODO: figure out what lead really is, for now use tokens before matched expression
      //       also, gutime looks at "string" which may not always be after
      List<? extends CoreMap> lead = m.groupNodes(SequenceMatchResult.GROUP_BEFORE_MATCH);
      if (lead.size() > 0) {
        precedingWord = lead.get(lead.size()-1).get(CoreAnnotations.TextAnnotation.class);
      }
      List<? extends CoreMap> after = m.groupNodes(SequenceMatchResult.GROUP_AFTER_MATCH);
      TokenSequenceMatcher vbMatcher = VB_NODE_PATTERN1.getMatcher(lead);
      boolean found = vbMatcher.find();
      if (!found) {
        vbMatcher = VB_NODE_PATTERN1.getMatcher(after);
        found = vbMatcher.find();
        if (!found) {
          vbMatcher = VB_NODE_PATTERN1.getMatcher(m.elements());
          found = vbMatcher.find();
        }
      }
      if (found) {
        vb = vbMatcher.groupNodes().get(0).get(CoreAnnotations.TextAnnotation.class);
        vbPos = vbMatcher.groupNodes().get(0).get(CoreAnnotations.PartOfSpeechAnnotation.class).toLowerCase();
        vbMatcher = VB_NODE_PATTERN2.getMatcher(vbMatcher.groupNodes(SequenceMatchResult.GROUP_AFTER_MATCH));
        if (vbMatcher.find()) {
          vb2 = vbMatcher.groupNodes().get(0).get(CoreAnnotations.TextAnnotation.class);
          vb2Pos = vbMatcher.groupNodes().get(0).get(CoreAnnotations.PartOfSpeechAnnotation.class).toLowerCase();
        }
      } else {
        vbPos = "X";
        vb = "NoVerb";
      }

		  if (vbPos.matches("(VBP|VBZ|MD)")) {
        vbMatcher = GOING_TO.getMatcher(m.elements());
        if (vbMatcher.find()) {
          vbPos = "MD";
          vb = "going_to";
        }
      }                                       */

  private final TokenSequencePattern VB_NODE_PATTERN1 = TokenSequencePattern.compile("[ { tag:/VBP|VBZ|VBD|MD/ } ]");
  private static final TokenSequencePattern VB_NODE_PATTERN2 = TokenSequencePattern.compile("[ { tag:/VB[A-Z]/ } ]");
  private static final TokenSequencePattern GOING_TO = TokenSequencePattern.compile("/(?i)going/ /(?i)to/");
  public int determineRelFlags(CoreMap annotation, TimeExpression te)
  {
		// Handle relative expressions requiring relative direction
    int flags = 0;
    String reason = "";
    if (options.teRelHeurLevel != Options.RelativeHeuristicLevel.NONE) {
      String vb2  = "";
      String vb2Pos   = "";
      String vbPos = "";
      String vb = "";
      String precedingWord = "";
      int limit = 2;

      // find the relevant verb and POS
      // TODO: figure out what lead really is, for now use tokens before matched expression
      //       also, gutime looks at "string" which may not always be after
      List<CoreLabel> tokens = annotation.get(CoreAnnotations.TokensAnnotation.class);
      Integer tokenOffset = annotation.get(CoreAnnotations.TokenBeginAnnotation.class);
      if (tokenOffset == null) tokenOffset = 0;
      int tokenBegin = te.getTokenOffsets().getBegin() - tokenOffset;
      int tokenEnd = te.getTokenOffsets().getEnd() - tokenOffset;
      List<? extends CoreMap> lead = tokens.subList(Math.max(0, tokenBegin-limit), tokenBegin);
      if (lead.size() > 0) {
        precedingWord = lead.get(lead.size()-1).get(CoreAnnotations.TextAnnotation.class);
      }
      List<? extends CoreMap> after = tokens.subList(tokenEnd, Math.min(tokens.size(), tokenEnd+limit));
      TokenSequenceMatcher vbMatcher = VB_NODE_PATTERN1.getMatcher(lead);
      boolean found = vbMatcher.find();
      if (!found) {
        vbMatcher = VB_NODE_PATTERN1.getMatcher(after);
        found = vbMatcher.find();
        if (limit < 0 && !found) {
          vbMatcher = VB_NODE_PATTERN1.getMatcher(tokens);
          found = vbMatcher.find();
        }
      }
      if (found) {
        vb = vbMatcher.groupNodes().get(0).get(CoreAnnotations.TextAnnotation.class).toLowerCase();
        vbPos = vbMatcher.groupNodes().get(0).get(CoreAnnotations.PartOfSpeechAnnotation.class).toUpperCase();

        vbMatcher = VB_NODE_PATTERN2.getMatcher(vbMatcher.groupNodes(SequenceMatchResult.GROUP_AFTER_MATCH));
        if (vbMatcher.find()) {
          vb2 = vbMatcher.groupNodes().get(0).get(CoreAnnotations.TextAnnotation.class).toLowerCase();
          vb2Pos = vbMatcher.groupNodes().get(0).get(CoreAnnotations.PartOfSpeechAnnotation.class).toUpperCase();
        }
      } else {
        vbPos = "X";
        vb = "NoVerb";
      }

      if (vbPos.matches("(VBP|VBZ|MD)")) {
        vbMatcher = GOING_TO.getMatcher(tokens);
        if (vbMatcher.find()) {
          vbPos = "MD";
          vb = "going_to";
        }
      }

      if ("VBD".equals(vbPos)) {
        flags |= SUTime.RESOLVE_TO_PAST;
        reason = vbPos;
      } else if("MD".equals(vbPos)) {
			  if(vb.matches(".*(will|'ll|going_to).*")) {
          flags |= SUTime.RESOLVE_TO_FUTURE;
			    reason = vbPos + ":" + vb;
        } else if("have".equals(vb2)) {
          flags |= SUTime.RESOLVE_TO_PAST;
          reason = vbPos + ":" + vb;
  			} else if (vb.matches(".*(?:would|could|should|'d)")) {
          flags |= SUTime.RESOLVE_TO_FUTURE;
			    reason = vbPos + ":" + vb;
  			}
      }

      // Heuristic Level > 2
      if ((options.teRelHeurLevel == Options.RelativeHeuristicLevel.MORE) &&
          (flags == 0)) {
        // since / until
        if("since".equals(precedingWord)) {
          flags |= SUTime.RESOLVE_TO_PAST;
			    reason = "since";
        } else if("until".equals(precedingWord)) {
          flags |= SUTime.RESOLVE_TO_FUTURE;
			    reason = "until";
			  }
      }
    }
    if (flags != 0) {
      logger.warning("Should resolve " + te + " using flags " + flags + " due to reason " + reason);
      logger.warning("Resolution context " + annotation.get(CoreAnnotations.TextAnnotation.class));
    } else {
      if (te.getTemporal() instanceof SUTime.PartialTime) {
        flags = SUTime.RESOLVE_TO_CLOSEST;
      }
    }
    return flags;
  }

  public SUTime.TemporalOp lookupTemporalOp(String expr)
  {
    expr = expr.toLowerCase();
    expr = expr.replaceAll("the\\s+","");
    expr = expr.replaceAll("[^a-z]","");
    return wordToTemporalOp.get(expr);
  }

  public SUTime.Temporal lookupTemporal(String expr)
  {
    expr = expr.toLowerCase();
    expr = expr.replaceAll("[^a-z]","");
    SUTime.Temporal temp = wordToTemporal.get(expr);
    if (temp == null && expr.endsWith("s")) {
      temp = wordToTemporal.get(expr.substring(0, expr.length()-1));
    }
    return temp;
  }

  Map<String,SUTime.TemporalOp> wordToTemporalOp = new HashMap<String,SUTime.TemporalOp>();
  private void initTemporalOpMap()
  {
    wordToTemporalOp.put("thiscoming", SUTime.TemporalOp.NEXT_IMMEDIATE);
    wordToTemporalOp.put("thispast", SUTime.TemporalOp.PREV_IMMEDIATE);
    wordToTemporalOp.put("this", SUTime.TemporalOp.THIS);
    wordToTemporalOp.put("next", SUTime.TemporalOp.NEXT);
    wordToTemporalOp.put("following", SUTime.TemporalOp.NEXT);
    wordToTemporalOp.put("previous", SUTime.TemporalOp.PREV);
    wordToTemporalOp.put("last", SUTime.TemporalOp.PREV);
  }

  Map<String,SUTime.Temporal> wordToTemporal = new HashMap<String,SUTime.Temporal>();
  private void initTemporalMap()
  {
    // Periodic Set
    wordToTemporal.put("annual", SUTime.YEARLY);
    wordToTemporal.put("annually", SUTime.YEARLY);
    wordToTemporal.put("yearly", SUTime.YEARLY);
    wordToTemporal.put("hourly", SUTime.HOURLY);
    wordToTemporal.put("nightly", SUTime.NIGHTLY);
    wordToTemporal.put("daily", SUTime.DAILY);
    wordToTemporal.put("weekly", SUTime.WEEKLY);
    wordToTemporal.put("monthly", SUTime.MONTHLY);
    wordToTemporal.put("quarterly", SUTime.QUARTERLY);

    // Time of Day
    wordToTemporal.put("morning", SUTime.MORNING);
    wordToTemporal.put("afternoon", SUTime.AFTERNOON);
    wordToTemporal.put("evening", SUTime.EVENING);
    wordToTemporal.put("dusk", SUTime.DUSK);
    wordToTemporal.put("twilight", SUTime.TWILIGHT);
    wordToTemporal.put("dawn", SUTime.DAWN);
    wordToTemporal.put("daybreak", SUTime.DAWN);
    wordToTemporal.put("sunrise", SUTime.SUNRISE);
    wordToTemporal.put("sunup", SUTime.SUNRISE);
    wordToTemporal.put("sundown", SUTime.SUNSET);
    wordToTemporal.put("sunset", SUTime.SUNSET);
    wordToTemporal.put("midday", SUTime.NOON);
    wordToTemporal.put("noon", SUTime.NOON);
    wordToTemporal.put("midnight", SUTime.MIDNIGHT);
    wordToTemporal.put("teatime", SUTime.TEATIME);
    wordToTemporal.put("lunchtime", SUTime.LUNCHTIME);
    wordToTemporal.put("dinnertime", SUTime.DINNERTIME);
    wordToTemporal.put("suppertime", SUTime.DINNERTIME);
    wordToTemporal.put("daylight", SUTime.DAYTIME);
    wordToTemporal.put("day", SUTime.DAYTIME);
    wordToTemporal.put("daytime", SUTime.DAYTIME);
    wordToTemporal.put("nighttime", SUTime.NIGHT);
    wordToTemporal.put("night", SUTime.NIGHT);

    wordToTemporal.put("summer", SUTime.SUMMER);
    wordToTemporal.put("winter", SUTime.WINTER);
    wordToTemporal.put("fall", SUTime.FALL);
    wordToTemporal.put("autumn", SUTime.FALL);
    wordToTemporal.put("spring", SUTime.SPRING);

    wordToTemporal.put("yesterday", SUTime.YESTERDAY);
    wordToTemporal.put("today", SUTime.TODAY);
    wordToTemporal.put("tomorrow", SUTime.TOMORROW);
    wordToTemporal.put("tonight", SUTime.TONIGHT);
    wordToTemporal.put("tonite", SUTime.TONIGHT);

    // Day of Week
    wordToTemporal.put("monday", SUTime.MONDAY);
    wordToTemporal.put("tuesday", SUTime.TUESDAY);
    wordToTemporal.put("wednesday", SUTime.WEDNESDAY);
    wordToTemporal.put("thursday", SUTime.THURSDAY);
    wordToTemporal.put("friday", SUTime.FRIDAY);
    wordToTemporal.put("saturday", SUTime.SATURDAY);
    wordToTemporal.put("sunday", SUTime.SUNDAY);
    wordToTemporal.put("mon", SUTime.MONDAY);
    wordToTemporal.put("tue", SUTime.TUESDAY);
    wordToTemporal.put("wed", SUTime.WEDNESDAY);
    wordToTemporal.put("thu", SUTime.THURSDAY);
    wordToTemporal.put("fri", SUTime.FRIDAY);
    wordToTemporal.put("sat", SUTime.SATURDAY);
    wordToTemporal.put("sun", SUTime.SUNDAY);

    // Month
    wordToTemporal.put("january", SUTime.JANUARY);
    wordToTemporal.put("february", SUTime.FEBRUARY);
    wordToTemporal.put("march", SUTime.MARCH);
    wordToTemporal.put("april", SUTime.APRIL);
    wordToTemporal.put("may", SUTime.MAY);
    wordToTemporal.put("june", SUTime.JUNE);
    wordToTemporal.put("july", SUTime.JULY);
    wordToTemporal.put("august", SUTime.AUGUST);
    wordToTemporal.put("september", SUTime.SEPTEMBER);
    wordToTemporal.put("october", SUTime.OCTOBER);
    wordToTemporal.put("november", SUTime.NOVEMBER);
    wordToTemporal.put("december", SUTime.DECEMBER);

    wordToTemporal.put("weekend", SUTime.WEEKEND);
 //   wordToTemporal.put("weekday", SUTime.WEEKDAY);

    // Durations
    wordToTemporal.put("week", SUTime.WEEK);
    wordToTemporal.put("fortnight", SUTime.FORTNIGHT);
    wordToTemporal.put("month", SUTime.MONTH);
    wordToTemporal.put("quarter", SUTime.QUARTER);
    wordToTemporal.put("year", SUTime.YEAR);
    wordToTemporal.put("decade", SUTime.DECADE);
    wordToTemporal.put("century", SUTime.CENTURY);
    wordToTemporal.put("millennium", SUTime.MILLENNIUM);
    wordToTemporal.put("millennia", SUTime.MILLENNIUM);
    wordToTemporal.put("millenium", SUTime.MILLENNIUM); // spelling error but common
    wordToTemporal.put("millenia", SUTime.MILLENNIUM);

    // Vague times
    wordToTemporal.put("past", SUTime.TIME_PAST);
    wordToTemporal.put("present", SUTime.TIME_PRESENT);
    wordToTemporal.put("current", SUTime.TIME_PRESENT);
    wordToTemporal.put("once", SUTime.TIME_PAST);
    wordToTemporal.put("future", SUTime.TIME_FUTURE);
    wordToTemporal.put("thefuture", SUTime.TIME_FUTURE);
  }

  Map<String,SUTime.Duration> abbToTimeUnit = new HashMap<String,SUTime.Duration>();
  private void initTimeUnitsMap() {
    // note - some of the incorrect spelling in this hash is due to the generalized matching which will match things like "centurys"
    // Are the mapped to units used elsewhere?
    abbToTimeUnit.put("years", SUTime.YEAR);
    abbToTimeUnit.put("year", SUTime.YEAR);
    abbToTimeUnit.put("yrs", SUTime.YEAR);
    abbToTimeUnit.put("yr", SUTime.YEAR);
    abbToTimeUnit.put("months", SUTime.MONTH);
    abbToTimeUnit.put("month", SUTime.MONTH);
    abbToTimeUnit.put("mo",SUTime.MONTH);
    abbToTimeUnit.put("mos",SUTime.MONTH);
    abbToTimeUnit.put("days",SUTime.DAY);
    abbToTimeUnit.put("day",SUTime.DAY);
    abbToTimeUnit.put("hours",SUTime.HOUR);
    abbToTimeUnit.put("hour", SUTime.HOUR);
    abbToTimeUnit.put("hrs", SUTime.HOUR);
    abbToTimeUnit.put("hr", SUTime.HOUR);
    abbToTimeUnit.put("minutes",SUTime.MINUTE);
    abbToTimeUnit.put("minute", SUTime.MINUTE);
    abbToTimeUnit.put("mins", SUTime.MINUTE);
    abbToTimeUnit.put("min", SUTime.MINUTE);
    abbToTimeUnit.put("seconds", SUTime.SECOND);
    abbToTimeUnit.put("second", SUTime.SECOND);
    abbToTimeUnit.put("secs", SUTime.SECOND);
    abbToTimeUnit.put("sec", SUTime.SECOND);
    abbToTimeUnit.put("weeks", SUTime.WEEK);
    abbToTimeUnit.put("week", SUTime.WEEK);
    abbToTimeUnit.put("wks", SUTime.WEEK);
    abbToTimeUnit.put("wk", SUTime.WEEK);
    abbToTimeUnit.put("quarter", SUTime.QUARTER);
    abbToTimeUnit.put("quarters", SUTime.QUARTER);
    abbToTimeUnit.put("decades", SUTime.DECADE);
    abbToTimeUnit.put("decade", SUTime.DECADE);
    abbToTimeUnit.put("decs", SUTime.DECADE);
    abbToTimeUnit.put("dec", SUTime.DECADE);
    abbToTimeUnit.put("centurys", SUTime.CENTURY);
    abbToTimeUnit.put("century",  SUTime.CENTURY);
    abbToTimeUnit.put("centuries",  SUTime.CENTURY);
    abbToTimeUnit.put("centurie",  SUTime.CENTURY);
    abbToTimeUnit.put("millennias",  SUTime.MILLENNIUM);
    abbToTimeUnit.put("millennia", SUTime.MILLENNIUM);
    abbToTimeUnit.put("millenniums", SUTime.MILLENNIUM);
    abbToTimeUnit.put("millennium", SUTime.MILLENNIUM);
    abbToTimeUnit.put("millenias",  SUTime.MILLENNIUM);
    abbToTimeUnit.put("millenia", SUTime.MILLENNIUM);
    abbToTimeUnit.put("milleniums", SUTime.MILLENNIUM);
    abbToTimeUnit.put("millenium", SUTime.MILLENNIUM);
  }

  protected SUTime.Temporal addSet(String expression, SUTime.Temporal temporal)
  {
    return null;
  }

  protected static SUTime.Temporal addMod(String expression, SUTime.Temporal temporal)
  {
	  // Add  MOD\
	  if (expression.matches("(?i).*\\b(late|end)\\b.*")) {
      // NOTE: TIMEX3 standard has END, not LATE
      return temporal.addMod(SUTime.TimexMod.LATE.name());
  	} else if (expression.matches("(?i).*\\bno\\s+more\\s+than\\b.*") || expression.matches("(?i).*\\bup\\s+to\\b.*")) {
 	    return temporal.addMod(SUTime.TimexMod.EQUAL_OR_LESS.name());
    } else if (expression.matches("(?i).*\\bmore\\s+than\\b.*")) {
      return temporal.addMod(SUTime.TimexMod.MORE_THAN.name());
    } else if (expression.matches("(?i).*\\bno\\s+less\\s+than\\b.*")) {
      return temporal.addMod(SUTime.TimexMod.EQUAL_OR_MORE.name());
    } else if (expression.matches("(?i).*\\bless\\s+than\\b.*")) {
      return temporal.addMod(SUTime.TimexMod.LESS_THAN.name());
    } else if (expression.matches("(?i).*\\b(early|start|beginning|dawn\\s+of)\\b.*")) {
      // NOTE: TIMEX3 standard has START, not EARLY
      return temporal.addMod(SUTime.TimexMod.EARLY.name());
    } else if (expression.matches("(?i).*\\bmid(dle)?\\b.*")) {
      return temporal.addMod(SUTime.TimexMod.MID.name());
    } else if (expression.matches("(?i).*\\bat\\s+least\\b.*")) {
      return temporal.addMod(SUTime.TimexMod.EQUAL_OR_MORE.name());
    } else if (expression.matches("(?i).*\\b(about|around|some)\\b.*")) {
      // In GUTIME, this was extra MOD attribute but XML standard doesn't really allow for multiple attributes with same name...
      return temporal.addMod(SUTime.TimexMod.APPROX.name());
    }
    return temporal;
  }

  /**
   * Converts a string that represents some kind of date into ISO 8601 format and
   *  returns it as a SUTime.Time
   *   YYYYMMDDThhmmss
   *
   * @param dateStr
   */
  public SUTime.Time parseDateTime(String dateStr)
  {
    if (dateStr == null) return null;
    // Already ISO
    // TODO: Timezone...
    Pattern p = Pattern.compile("(\\d\\d\\d\\d)-?(\\d\\d)-?(\\d\\d)(-?(?:T(\\d\\d):?(\\d\\d)?:?(\\d\\d)?(?:[.,](\\d{1,3}))?([+-]\\d\\d:?\\d\\d)?))?");
    Matcher m = p.matcher(dateStr);
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

    // ACE Format
    p = Pattern.compile("(\\d\\d\\d\\d)(\\d\\d)(\\d\\d):(\\d\\d)(\\d\\d)");
    m = p.matcher(dateStr);
    if (m.matches()) {
      SUTime.IsoDate date = createIsoDate(m.group(1), m.group(2), m.group(3));
      SUTime.IsoTime time = new SUTime.IsoTime(m.group(4), m.group(5), null);
      return new SUTime.IsoDateTime(date,time);
    }

    p = Pattern.compile("T(\\d\\d):?(\\d\\d)?:?(\\d\\d)?(?:[.,](\\d{1,3}))?([+-]\\d\\d:?\\d\\d)?");
    m = p.matcher(dateStr);
    if (m.matches()) {
      return new SUTime.IsoTime(m.group(1), m.group(2), m.group(3), m.group(4));
    }

    SUTime.IsoDate isoDate = null;
    p = getPattern(".*(\\d\\d?)\\s+($TEmonth|$TEmonthabbr\\.?),?\\s+(\\d\\d(\\s|\\Z)|\\d{4}\\b).*");
    m = p.matcher(dateStr);
    if (m.matches()) {
      isoDate = createIsoDate(m.group(5), m.group(2), m.group(1));
    }

    if (isoDate == null) {
      p = getPattern(".*($TEmonth|$TEmonthabbr\\.?)\\s+(\\d\\d?|$TEOrdinalWords)\\b,?\\s*(\\d\\d(\\s|\\Z)|\\d{4}\\b).*");
      m = p.matcher(dateStr);
      if (m.matches()) {
        isoDate = createIsoDate(m.group(6), m.group(1), m.group(4));
      }
    }

    if (isoDate == null) {
      p = Pattern.compile(".*(\\d\\d\\d\\d)\\/(\\d\\d?)\\/(\\d\\d?).*");
      m = p.matcher(dateStr);
      if (m.matches()) {
        isoDate = new SUTime.IsoDate(m.group(1), m.group(2), m.group(3));
      }
    }

    if (isoDate == null) {
      p = Pattern.compile(".*(\\d\\d\\d\\d)\\-(\\d\\d?)\\-(\\d\\d?).*");
      m = p.matcher(dateStr);
      if (m.matches()) {
        isoDate = new SUTime.IsoDate(m.group(1), m.group(2), m.group(3));
      }
    }

    if (isoDate == null) {
      // Ambiguous pattern - interpret as MM/DD/YY(YY)
      p = Pattern.compile(".*(\\d\\d?)\\/(\\d\\d?)\\/(\\d\\d(\\d\\d)?).*");
      m = p.matcher(dateStr);
      if (m.matches()) {
        isoDate = new SUTime.IsoDate(m.group(3), m.group(1), m.group(2));
      }
    }
    if (isoDate == null) {
        // Ambiguous pattern - interpret as MM-DD-YY(YY)
      p = Pattern.compile(".*(\\d\\d?)\\-(\\d\\d?)\\-(\\d\\d(\\d\\d)?).*");
      m = p.matcher(dateStr);
      if (m.matches()) {
        isoDate = new SUTime.IsoDate(m.group(3), m.group(1), m.group(2));
      }
    }
    if (isoDate == null) {
      // Euro date
      // Ambiguous pattern - interpret as DD.MM.YY(YY)
      p = Pattern.compile(".*(\\d\\d?)\\.(\\d\\d?)\\.(\\d\\d(\\d\\d)?).*");
      m = p.matcher(dateStr);
      if (m.matches()) {
        isoDate = new SUTime.IsoDate(m.group(3), m.group(2), m.group(1));
      }
    }
    if (isoDate == null) {
      p = getPattern(".*($TEmonth|$TEmonthabbr\\.?)\\s+(\\d\\d?).+(\\d\\d\\d\\d)\\b.*");
      m = p.matcher(dateStr);
      if (m.matches()) {
        int year = Integer.parseInt(m.group(5));
        if (year >= 1900 && year <= 2100) {
          isoDate = createIsoDate(m.group(5), m.group(1), m.group(4));
        } else {
          isoDate = createIsoDate(null, m.group(1), m.group(4));
        }
      }
    }


    // Now add Time of Day
    SUTime.IsoTime isoTime = null;
    if (isoTime == null) {
      p = Pattern.compile(".*(\\d?\\d):(\\d\\d)(:(\\d\\d)(\\.\\d+)?)?(\\s*([AP])\\.?M\\.?)?(\\s+([+\\-]\\d+|[A-Z][SD]T|GMT([+\\-]\\d+)?))?.*");
      m = p.matcher(dateStr);
      if (m.matches()) {
        // TODO: Fix
  //      isoTime = new SUTime.IsoTime(m.group(1), m.group(2), m.group(4), m.group(5), m.group(7), m.group(9));
      }
    }

    if (isoTime == null) {
      p = Pattern.compile("");
      m = p.matcher(dateStr);
      if (m.matches()) {
        isoTime = new SUTime.IsoTime(m.group(1), m.group(2), m.group(3));
      }
    }
    /*

      if($string =~ //oi) {
    $H = $1;  $m = $2;  $S = $4; $FS = $5; $AMPM = $7; $zone = $9;
    if((defined($AMPM)) && ($AMPM =~ /P/oi)) { $H += 12; }
    if(defined($zone)) {
        if($zone =~ /(GMT)([+\-]\d+)/o) { $zone = $2; }
        elsif($zone =~ /GMT/o) { $zone = "Z"; }
        elsif($zone =~ /([A-Z])([SD])T/o) {
      if(defined($TE_TimeZones{$1})) {
          $Z = $TE_TimeZones{$1};
          if($2 eq "D") { $Z++; }
          if($Z<0) { $zone = sprintf("-%02d", -1*$Z); }
          else { $zone = sprintf("+%02d", $Z); }
      }
        }
    }
      } elsif($string =~ /(\d\d)(\d\d)\s+(h(ou)?r|(on\s+)?\d\d?\/\d)/oi) {
    $H = $1;  $m = $2;
      }

      if(defined($H)) {
    if(defined($FS)) {
      $FS =~ s/\.//;
      $TOD = sprintf("T%02d:%02d:%02d.%02d", $H, $m, $S, $FS); }
    elsif(defined($S)) { $TOD = sprintf("T%02d:%02d:%02d", $H, $m, $S); }
    elsif(defined($m)) { $TOD = sprintf("T%02d:%02d", $H, $m); }
    else { $TOD = sprintf("T%02d", $H); }
    $ISO .= $TOD;
    if(defined($zone)) {
        if($zone =~ /\A\s+/o) { $zone = $'; }
        $ISO .= $zone; }
      }

      return($ISO); */
    if (isoDate != null && isoTime != null) {
      return new SUTime.IsoDateTime(isoDate, isoTime);
    } else if (isoDate != null) {
      return isoDate;
    } else {
      return isoTime;
    }
  }  // Date2ISO

  public static SUTime.IsoDate createIsoDate(String year, String month, String day) {
    String y = EnglishDateTimeUtils.year2Iso(year);
    String m = EnglishDateTimeUtils.month2Iso(month);
    String d = EnglishDateTimeUtils.day2Iso(m, day);
    return new SUTime.IsoDate(y,m,d);
  }
}
