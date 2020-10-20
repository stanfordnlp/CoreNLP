package edu.stanford.nlp.ling.tokensregex;

import edu.stanford.nlp.ling.tokensregex.types.AssignableExpression;
import edu.stanford.nlp.ling.tokensregex.types.Expression;
import edu.stanford.nlp.ling.tokensregex.types.Expressions;
import edu.stanford.nlp.ling.tokensregex.types.Value;
import edu.stanford.nlp.util.*;

import java.io.Serializable;
import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Rules for matching sequences using regular expressions.
 * <p>
 * There are 2 types of rules:
 *
 * <ol>
 * <li><b>Assignment rules</b> which assign a value to a variable for later use.
 * </li>
 * <li><b>Extraction rules</b> which specifies how regular expression patterns are to be matched against text,
 *   which matched text expressions are to extracted, and what value to assign to the matched expression.</li>
 * </ol>
 *
 * NOTE: {@code #} or {@code //} can be used to indicates one-line comments.
 * <p>
 * <b>Assignment Rules</b> are used to assign values to variables.
 *     The basic format is: {@code variable = value}.
 * <p>
 * <em>Variable Names</em>:
 *   <ul>
 *     <li>Variable names should follow the pattern [A-Za-z_][A-Za-z0-9_]*</li>
 *     <li>Variable names for use in regular expressions (to be expanded later) must start with {@code $}</li>
 *   </ul>
 * <p>
 * <em>Value Types</em>:
 * <table>
 * <caption>Value Types</caption>
 *   <tr><th>Type</th><th>Format</th><th>Example</th><th>Description</th></tr>
 *   <tr><td>{@code BOOLEAN}</td><td>{@code TRUE | FALSE}</td><td>{@code TRUE}</td><td></td></tr>
 *   <tr><td>{@code STRING}</td><td>{@code "..."}</td><td>{@code "red"}</td><td></td></tr>
 *   <tr><td>{@code INTEGER}</td><td>{@code [+-]\d+}</td><td>{@code 1500}</td><td></td></tr>
 *   <tr><td>{@code LONG}</td><td>{@code [+-]\d+L}</td><td>{@code 1500000000000L}</td><td></td></tr>
 *   <tr><td>{@code DOUBLE}</td><td>{@code [+-]\d*\.\d+}</td><td>{@code 6.98}</td><td></td></tr>
 *   <tr><td>{@code REGEX}</td><td>{@code /.../}</td><td>{@code /[Aa]pril/}</td>
 *       <td>String regular expression {@link Pattern}</td></tr>
 *   <tr><td>{@code TOKENS_REGEX}</td><td>{@code ( [...] [...] ... ) }</td><td>{@code ( /up/ /to/ /4/ /months/ )}</td>
 *       <td>Tokens regular expression {@link TokenSequencePattern}</td></tr>
 *   <tr><td>{@code LIST}</td><td>{@code ( [item1] , [item2], ... )}</td><td>{@code ("red", "blue", "yellow" )}</td>
 *       <td></td></tr>
 * </table>
 * <p>
 * Some typical uses and examples for assignment rules include:
 * <ol>
 *  <li>Assignment of value to variables for use in later rules</li>
 *  <li>Binding of text key to annotation key (as {@code Class}).
 *    <pre>
 *      tokens = { type: "CLASS", value: "edu.stanford.nlp.ling.CoreAnnotations$TokensAnnotation" }
 *    </pre>
 *  </li>
 *  <li>Defining regular expressions macros to be embedded in other regular expressions
 *    <pre>
 *      $SEASON = "/spring|summer|fall|autumn|winter/"
 *      $NUM = ( [ { numcomptype:NUMBER } ] )
 *    </pre>
 *  </li>
 *  <li>Setting default environment variables.
 *      Rules are applied with respect to an environment ({@link Env}), which can be accessed using the variable {@code ENV}.
 *      Members of the Environment can be set as needed.
 *    <pre>
 *      # Set default parameters to be used when reading rules
 *      ENV.defaults["ruleType"] = "tokens"
 *      # Set default string pattern flags (to case-insensitive)
 *      ENV.defaultStringPatternFlags = 2
 *      # Specifies that the result should go into the {@code tokens}  key (as defined above).
 *      ENV.defaultResultAnnotationKey = tokens
 *    </pre>
 *  </li>
 *  <li>Defining options</li>
 * </ol>
 * <p>
 * Predefined values are:
 * <table>
 * <caption>Predefined values</caption>
 *   <tr><th>Variable</th><th>Type</th><th>Description</th></tr>
 *   <tr><td>{@code ENV}</td><td>{@link Env}</td><td>The environment with respect to which the rules are applied.</td></tr>
 *   <tr><td>{@code TRUE}</td><td>{@code BOOLEAN}</td><td>The {@code Boolean}  value {@code true}.</td></tr>
 *   <tr><td>{@code FALSE}</td><td>{@code BOOLEAN}</td><td>The {@code Boolean} value {@code false}.</td></tr>
 *   <tr><td>{@code NIL}</td><td>{@code}</td><td>The {@code null} value.</td></tr>
 *   <tr><td>{@code tags}</td><td>{@code Class}</td><td>The annotation key {@link edu.stanford.nlp.ling.tokensregex.types.Tags.TagsAnnotation}.</td></tr>
 * </table>
 *
 * <p>
 * <b>Extraction Rules</b> specifies how regular expression patterns are to be matched against text.
 * See {@link CoreMapExpressionExtractor} for more information on the types of the rules, and in what sequence the rules are applied.
 * A basic rule can be specified using the following template:
 * <pre>
 *   {
 *     # Type of the rule
 *     ruleType: "tokens" | "text" | "composite" | "filter",
 *     # Pattern to match against
 *     pattern: ( &lt;TokenSequencePattern&gt; ) | /&lt;TextPattern&gt;/,
 *     # Resulting value to go into the resulting annotation
 *     result: ...
 *
 *     # More fields following...
 *   }
 * </pre>
 * Example:
 * <pre>
 *   {
 *     ruleType: "tokens",
 *     pattern: ( /one/ ),
 *     result: 1
 *   }
 * </pre>
 *
 * <p>
 * Extraction rule fields (most fields are optional):
 * <table>
 * <caption>Extraction rule fields</caption>
 *   <tr><th>Field</th><th>Values</th><th>Example</th><th>Description</th></tr>
 *   <tr><td>{@code ruleType}</td><td>{@code "tokens" | "text" | "composite" | "filter" }</td>
 *      <td>{@code tokens}</td><td>Type of the rule (required).</td></tr>
 *   <tr><td>{@code pattern}</td><td>{@code <Token Sequence Pattern> = (...) | <Text Pattern> = /.../}</td>
 *      <td>{@code ( /winter/ /of/ $YEAR )}</td><td>Pattern to match against.
 *      See {@link TokenSequencePattern} and {@link Pattern} for
 *      how to specify patterns over tokens and strings (required).</td></tr>
 *   <tr><td>{@code action}</td><td>{@code <Action List> = (...)}</td>
 *      <td>{@code ( Annotate($0, ner, "DATE") )}</td><td>List of actions to apply when the pattern is triggered.
 *      Each action is a {@link Expressions TokensRegex Expression}</td></tr>
 *   <tr><td>{@code result}</td><td>{@code <Expression>}</td>
 *      <td>{@code}</td><td>Resulting value to go into the resulting annotation.  See {@link Expressions} for how to specify the result.</td></tr>
 *   <tr><td>{@code name}</td><td>{@code STRING}</td>
 *      <td>{@code}</td><td>Name to identify the extraction rule.</td></tr>
 *   <tr><td>{@code stage}</td><td>{@code INTEGER}</td>
 *      <td>{@code}</td><td>Stage at which the rule is to be applied.  Rules are grouped in stages, which are applied from lowest to highest.</td></tr>
 *   <tr><td>{@code active}</td><td>{@code Boolean}</td>
 *      <td>{@code}</td><td>Whether this rule is enabled (active) or not (default true).</td></tr>
 *   <tr><td>{@code priority}</td><td>{@code DOUBLE}</td>
 *      <td>{@code}</td><td>Priority of rule.  Within a stage, matches from higher priority rules are preferred.</td></tr>
 *   <tr><td>{@code weight}</td><td>{@code DOUBLE}</td>
 *      <td>{@code}</td><td>Weight of rule (not currently used).</td></tr>
 *   <tr><td>{@code over}</td><td>{@code CLASS}</td>
 *      <td>{@code}</td><td>Annotation field to check pattern against.</td></tr>
 *   <tr><td>{@code matchFindType}</td><td>{@code FIND_NONOVERLAPPING | FIND_ALL}</td>
 *      <td>{@code}</td><td>Whether to find all matched expression or just the nonoverlapping ones (default {@code FIND_NONOVERLAPPING}).</td></tr>
 *   <tr><td>{@code matchWithResults}</td><td>{@code Boolean}</td>
 *      <td>{@code}</td><td>Whether results of the matches should be returned (default false).
 *        Set to true to access captured groups of embedded regular expressions.</td></tr>
 *   <tr><td>{@code matchedExpressionGroup}</td><td>{@code Integer}</td>
 *      <td>{@code 2}</td><td>What group should be treated as the matched expression group (default 0).</td></tr>
 * </table>
 *
 * @author Angel Chang
 * @see CoreMapExpressionExtractor
 * @see TokenSequencePattern
 */
public class SequenceMatchRules {

  private SequenceMatchRules() { } // static class with inner classes


  /** A sequence match rule. */
  public interface Rule {
  }


  /**
   * Rule that specifies what value to assign to a variable.
   */
  public static class AssignmentRule implements Rule {
    final Expression expr;

    public AssignmentRule(AssignableExpression varExpr, Expression value) {
      expr = varExpr.assign(value);
    }

    public void evaluate(Env env) {
      expr.evaluate(env);
    }
  }


  /**
   * Rule that specifies how to extract sequence of MatchedExpression from an annotation (CoreMap).
   *
   * @param <T> Output type (MatchedExpression)
   */
  public static class AnnotationExtractRule<S, T extends MatchedExpression> implements Rule, ExtractRule<S,T>, Predicate<T>, Serializable {

    private static final long serialVersionUID = -2148125332223720424L;

    /** Name of the rule */
    public String name;
    /** Stage in which this rule should be applied with respect to others */
    public int stage = 1;
    /** Priority in which this rule should be applied with respect to others */
    public double priority;
    /** Weight given to the rule (how likely is this rule to fire) */
    public double weight;
    /** Annotation field to apply rule over: text or tokens or numerizedtokens */
    public Class annotationField;
    public Class tokensAnnotationField;
    /**  Annotation field(s) on individual tokens to put new annotation */
    public List<Class> tokensResultAnnotationField;
    /**  Annotation field(s) to put new annotation */
    public List<Class> resultAnnotationField;
    /** Annotation field for child/nested annotations */
    public Class resultNestedAnnotationField;
    public SequenceMatcher.FindType matchFindType;
    /** Which group to take as the matched expression - default is 0 */
    public int matchedExpressionGroup;
    public boolean matchWithResults;
    // TODO: Combine ruleType and isComposite
    /** Type of rule to apply: token string match, pattern string match */
    public String ruleType;
    public boolean isComposite;
    public boolean includeNested = true;  // TODO: Get parameter from somewhere....
    public boolean active = true;
    /** Actual rule performing the extraction (converting annotation to MatchedExpression) */
    public ExtractRule<S, T> extractRule;
    public Predicate<T> filterRule;
    /** Pattern - the type of which is dependent on the rule type */
    public Object pattern;
    public Expression result;

    public void update(Env env, Map<String, Object> attributes) {
      for (Map.Entry<String, Object> stringObjectEntry : attributes.entrySet()) {
        String key = stringObjectEntry.getKey();
        Object obj = stringObjectEntry.getValue();
        switch (key) {
          case "name":
            name = (String) Expressions.asObject(env, obj);
            break;
          case "priority":
            priority = ((Number) Expressions.asObject(env, obj)).doubleValue();
            break;
          case "stage":
            stage = ((Number) Expressions.asObject(env, obj)).intValue();
            break;
          case "weight":
            weight = ((Number) Expressions.asObject(env, obj)).doubleValue();
            break;
          case "over":
            Object annoKey = Expressions.asObject(env, obj);
            if (annoKey instanceof Class) {
              annotationField = (Class) annoKey;
            } else if (annoKey instanceof String) {
              annotationField = EnvLookup.lookupAnnotationKeyWithClassname(env, (String) annoKey);
            } else if (annotationField == null) {
              annotationField = CoreMap.class;
            } else {
              throw new IllegalArgumentException("Invalid annotation key " + annoKey);
            }
            break;
          case "active":
            active = (Boolean) Expressions.asObject(env, obj);
            break;
          case "ruleType":
            ruleType = (String) Expressions.asObject(env, obj);
            break;
          case "matchFindType":
            matchFindType = SequenceMatcher.FindType.valueOf((String) Expressions.asObject(env, obj));
            break;
          case "matchWithResults":
            matchWithResults = ((Boolean) Expressions.asObject(env, obj)).booleanValue();
            break;
          case "matchedExpressionGroup":
            matchedExpressionGroup = ((Number) Expressions.asObject(env, obj)).intValue();
            break;
        }
      }
    }

    @Override
    public boolean extract(S in, List<T> out) {
      return extractRule.extract(in, out);
    }

    @Override
    public boolean test(T obj) {
      return filterRule.test(obj);
    }

    public boolean isMostlyCompatible(AnnotationExtractRule<S, T> aer) {
      // TODO: Check tokensResultAnnotationField, resultAnnotationField, resultNestedAnnotationField?
      return (stage == aer.stage
        && Objects.equals(annotationField, aer.annotationField)
        && Objects.equals(tokensAnnotationField, aer.tokensAnnotationField)
        && matchedExpressionGroup == 0
        && aer.matchedExpressionGroup == 0
        && matchWithResults == aer.matchWithResults
        && Objects.equals(ruleType, aer.ruleType)
        && isComposite == aer.isComposite
        && active == aer.active
        && Objects.equals(result, aer.result));
    }

    public boolean hasTokensRegexPattern() {
      return pattern != null && pattern instanceof TokenSequencePattern;
    }

    public String toString() { return getClass().getSimpleName() + '[' + pattern.toString() + ']'; }

  } // end static class AnnotationExtractRule


  public static AssignmentRule createAssignmentRule(Env env, AssignableExpression var, Expression result) {
    AssignmentRule ar = new AssignmentRule(var, result);
    ar.evaluate(env);
    return ar;
  }

  public static Rule createRule(Env env, Expressions.CompositeValue cv) {
    Map<String, Object> attributes;
    cv = cv.simplifyNoTypeConversion(env);
    attributes = new HashMap<>();//Generics.newHashMap();
    for (String s:cv.getAttributes()) {
      attributes.put(s, cv.getExpression(s));
    }
    return createExtractionRule(env, attributes);
  }

  protected static AnnotationExtractRule createExtractionRule(Env env, Map<String,Object> attributes) {
    String ruleType = (String) Expressions.asObject(env, attributes.get("ruleType"));
    if (ruleType == null && env != null) {
      ruleType = (String) env.getDefaults().get("ruleType");
    }
    AnnotationExtractRuleCreator ruleCreator = lookupExtractRuleCreator(env, ruleType);
    if (ruleCreator != null) {
      return ruleCreator.create(env, attributes);
    } else {
      throw new IllegalArgumentException("Unknown rule type: " + ruleType);
    }
  }

  public static AnnotationExtractRule createExtractionRule(Env env, String ruleType, Object pattern, Expression result) {
    if (ruleType == null && env != null) {
      ruleType = (String) env.getDefaults().get("ruleType");
    }
    AnnotationExtractRuleCreator ruleCreator = lookupExtractRuleCreator(env, ruleType);
    if (ruleCreator != null) {
      Map<String,Object> attributes = new HashMap<>();//Generics.newHashMap();
      attributes.put("ruleType", ruleType);
      attributes.put("pattern", pattern);
      attributes.put("result", result);
      return ruleCreator.create(env, attributes);
    } else {
      throw new IllegalArgumentException("Unknown rule type: " + ruleType);
    }
  }

  public static final String COMPOSITE_RULE_TYPE = "composite";
  public static final String TOKEN_PATTERN_RULE_TYPE = "tokens";
  public static final String TEXT_PATTERN_RULE_TYPE = "text";
  public static final String FILTER_RULE_TYPE = "filter";
  public static final TokenPatternExtractRuleCreator TOKEN_PATTERN_EXTRACT_RULE_CREATOR = new TokenPatternExtractRuleCreator();
  public static final CompositeExtractRuleCreator COMPOSITE_EXTRACT_RULE_CREATOR = new CompositeExtractRuleCreator();
  public static final TextPatternExtractRuleCreator TEXT_PATTERN_EXTRACT_RULE_CREATOR = new TextPatternExtractRuleCreator();
  public static final MultiTokenPatternExtractRuleCreator MULTI_TOKEN_PATTERN_EXTRACT_RULE_CREATOR = new MultiTokenPatternExtractRuleCreator();
  public static final AnnotationExtractRuleCreator DEFAULT_EXTRACT_RULE_CREATOR = TOKEN_PATTERN_EXTRACT_RULE_CREATOR;
  private static final Map<String, AnnotationExtractRuleCreator> registeredRuleTypes = new HashMap<>();//Generics.newHashMap();
  static {
    registeredRuleTypes.put(TOKEN_PATTERN_RULE_TYPE, TOKEN_PATTERN_EXTRACT_RULE_CREATOR);
    registeredRuleTypes.put(COMPOSITE_RULE_TYPE, COMPOSITE_EXTRACT_RULE_CREATOR);
    registeredRuleTypes.put(TEXT_PATTERN_RULE_TYPE, TEXT_PATTERN_EXTRACT_RULE_CREATOR);
    registeredRuleTypes.put(FILTER_RULE_TYPE, TOKEN_PATTERN_EXTRACT_RULE_CREATOR);
  }

  private static AnnotationExtractRuleCreator lookupExtractRuleCreator(Env env, String ruleType) {
    if (env != null) {
      Object obj = env.get(ruleType);
      if (obj != null && obj instanceof AnnotationExtractRuleCreator) {
        return (AnnotationExtractRuleCreator) obj;
      }
    }
    if (ruleType == null) {
      return DEFAULT_EXTRACT_RULE_CREATOR;
    } else {
      return registeredRuleTypes.get(ruleType);
    }
  }

  public static AnnotationExtractRule createTokenPatternRule(Env env, SequencePattern.PatternExpr expr, Expression result) {
    return TOKEN_PATTERN_EXTRACT_RULE_CREATOR.create(env, expr, result);
  }

  public static AnnotationExtractRule createTextPatternRule(Env env, String expr, Expression result) {
    return TEXT_PATTERN_EXTRACT_RULE_CREATOR.create(env, expr, result);
  }

  public static AnnotationExtractRule createMultiTokenPatternRule(Env env, AnnotationExtractRule template, List<TokenSequencePattern> patterns) {
    return MULTI_TOKEN_PATTERN_EXTRACT_RULE_CREATOR.create(env, template, patterns);
  }


  public static class AnnotationExtractRuleCreator {
    public AnnotationExtractRule create(Env env) {
      AnnotationExtractRule r = new AnnotationExtractRule();
      r.resultAnnotationField = EnvLookup.getDefaultResultAnnotationKey(env);
      r.resultNestedAnnotationField = EnvLookup.getDefaultNestedResultsAnnotationKey(env);
      r.tokensAnnotationField = EnvLookup.getDefaultTokensAnnotationKey(env);
      r.tokensResultAnnotationField = EnvLookup.getDefaultTokensResultAnnotationKey(env);
      if (env != null) {
        r.update(env, env.getDefaults());
      }
      return r;
    }

    public AnnotationExtractRule create(Env env, Map<String,Object> attributes) {
      // Get default annotation extract rule from env
      AnnotationExtractRule r = create(env);
      if (attributes != null) {
        r.update(env, attributes);
      }
      return r;
    }
  }


  public static MatchedExpression.SingleAnnotationExtractor createAnnotationExtractor(Env env, AnnotationExtractRule r) {
    MatchedExpression.SingleAnnotationExtractor extractor = new MatchedExpression.SingleAnnotationExtractor();
    extractor.name = r.name;
    extractor.tokensAnnotationField = r.tokensAnnotationField;
    extractor.tokensResultAnnotationField = r.tokensResultAnnotationField;
    extractor.resultAnnotationField = r.resultAnnotationField;
    extractor.resultNestedAnnotationField = r.resultNestedAnnotationField;
    extractor.priority = r.priority;
    extractor.weight = r.weight;
    extractor.includeNested = r.includeNested;
    extractor.resultAnnotationExtractor = EnvLookup.getDefaultResultAnnotationExtractor(env);
    extractor.tokensAggregator = EnvLookup.getDefaultTokensAggregator(env);
    return extractor;
  }


  public static class CompositeExtractRuleCreator extends AnnotationExtractRuleCreator {

    protected static void updateExtractRule(AnnotationExtractRule r,
                                     Env env,
                                     SequencePattern.PatternExpr expr,
                                     Expression action,
                                     Expression result) {
      TokenSequencePattern pattern = TokenSequencePattern.compile(expr);
      updateExtractRule(r, env, pattern, action, result);
    }

    protected static void updateExtractRule(AnnotationExtractRule r,
                                     Env env,
                                     TokenSequencePattern pattern,
                                     Expression action,
                                     Expression result) {
      MatchedExpression.SingleAnnotationExtractor annotationExtractor = createAnnotationExtractor(env, r);
      SequenceMatchResultExtractor<CoreMap> valueExtractor = new SequenceMatchResultExtractor<>(env, action, result);
      SequencePatternExtractRule<CoreMap,Value> valueExtractRule = new SequencePatternExtractRule<>(pattern, valueExtractor, r.matchFindType, r.matchWithResults);
      SequenceMatchedExpressionExtractor exprExtractor = new SequenceMatchedExpressionExtractor( annotationExtractor, r.matchedExpressionGroup );
      SequencePatternExtractRule<CoreMap, MatchedExpression> exprExtractRule =
        new SequencePatternExtractRule<>(pattern, exprExtractor, r.matchFindType, r.matchWithResults);

      annotationExtractor.expressionToValue = matched -> {
        if (matched != null && matched.context != null && matched.context instanceof SequenceMatchResult ) {
          return valueExtractor.apply( (SequenceMatchResult<CoreMap>) matched.context);
        } else return null;
      };
      annotationExtractor.valueExtractor = new CoreMapFunctionApplier<>(env, r.annotationField, valueExtractRule);
      r.extractRule = exprExtractRule;
      r.filterRule = new AnnotationMatchedFilter(annotationExtractor);
      r.pattern = pattern;
      r.result = result;
      pattern.weight = r.weight;
      pattern.priority = r.priority;
    }

    protected AnnotationExtractRule create(Env env, SequencePattern.PatternExpr expr, Expression result) {
      AnnotationExtractRule r = super.create(env, null);
      r.isComposite = true;
      if (r.annotationField == null) { r.annotationField = r.resultNestedAnnotationField;  }
      if (r.annotationField == null) { throw new IllegalArgumentException("Error creating composite rule: no annotation field"); }
      r.ruleType = TOKEN_PATTERN_RULE_TYPE;
      updateExtractRule(r, env, expr, null, result);
      return r;
    }

    @Override
    public AnnotationExtractRule create(Env env, Map<String,Object> attributes) {
      AnnotationExtractRule r = super.create(env, attributes);
      r.isComposite = true;
      if (r.annotationField == null) { r.annotationField = r.resultNestedAnnotationField;  }
      if (r.annotationField == null) { throw new IllegalArgumentException("Error creating composite rule: no annotation field"); }
      if (r.ruleType == null) { r.ruleType = TOKEN_PATTERN_RULE_TYPE; }
      //SequencePattern.PatternExpr expr = (SequencePattern.PatternExpr) attributes.get("pattern");
      TokenSequencePattern expr = (TokenSequencePattern) Expressions.asObject(env, attributes.get("pattern"));
      Expression action = Expressions.asExpression(env, attributes.get("action"));
      Expression result = Expressions.asExpression(env, attributes.get("result"));
      updateExtractRule(r, env, expr, action, result);
      return r;
    }

  }


  public static class TokenPatternExtractRuleCreator extends AnnotationExtractRuleCreator {

    protected static void updateExtractRule(AnnotationExtractRule r,
                                     Env env,
                                     SequencePattern.PatternExpr expr,
                                     Expression action,
                                     Expression result) {
      TokenSequencePattern pattern = TokenSequencePattern.compile(expr);
      updateExtractRule(r, env, pattern, action, result);
    }

    protected static void updateExtractRule(AnnotationExtractRule r,
                                     Env env,
                                     TokenSequencePattern pattern,
                                     Expression action,
                                     Expression result) {
      MatchedExpression.SingleAnnotationExtractor annotationExtractor = createAnnotationExtractor(env, r);
      SequenceMatchResultExtractor<CoreMap> valueExtractor = new SequenceMatchResultExtractor<>(env, action, result);
      SequencePatternExtractRule<CoreMap,Value> valueExtractRule = new SequencePatternExtractRule<>(pattern, valueExtractor, r.matchFindType, r.matchWithResults);
      SequenceMatchedExpressionExtractor exprExtractor = new SequenceMatchedExpressionExtractor( annotationExtractor, r.matchedExpressionGroup );
      SequencePatternExtractRule<CoreMap, MatchedExpression> exprExtractRule =
        new SequencePatternExtractRule<>(pattern, exprExtractor, r.matchFindType, r.matchWithResults);

      annotationExtractor.expressionToValue = matched -> {
        if (matched != null && matched.context != null && matched.context instanceof SequenceMatchResult ) {
          return valueExtractor.apply( (SequenceMatchResult<CoreMap>) matched.context);
        } else return null;
      };
      if (r.annotationField != null && r.annotationField != CoreMap.class) {
        annotationExtractor.valueExtractor = new CoreMapFunctionApplier<>(env, r.annotationField, valueExtractRule);
        r.extractRule = new CoreMapExtractRule<>(env, r.annotationField, exprExtractRule);
      } else {
        annotationExtractor.valueExtractor = new CoreMapToListFunctionApplier<>(env, valueExtractRule);
        r.extractRule = new CoreMapToListExtractRule<>(exprExtractRule);
      }
      r.filterRule = new AnnotationMatchedFilter(annotationExtractor);
      r.pattern = pattern;
      r.result = result;
      pattern.weight = r.weight;
      pattern.priority = r.priority;
    }

    protected AnnotationExtractRule create(Env env, SequencePattern.PatternExpr expr, Expression result) {
      AnnotationExtractRule r = super.create(env, null);
      if (r.annotationField == null) { r.annotationField = r.tokensAnnotationField;  }
      r.ruleType = TOKEN_PATTERN_RULE_TYPE;
      updateExtractRule(r, env, expr, null, result);
      return r;
    }

    @Override
    public AnnotationExtractRule create(Env env, Map<String,Object> attributes) {
      AnnotationExtractRule r = super.create(env, attributes);
      if (r.annotationField == null) { r.annotationField = r.tokensAnnotationField;  }
      if (r.ruleType == null) { r.ruleType = TOKEN_PATTERN_RULE_TYPE; }
      //SequencePattern.PatternExpr expr = (SequencePattern.PatternExpr) attributes.get("pattern");
      TokenSequencePattern expr = (TokenSequencePattern) Expressions.asObject(env, attributes.get("pattern"));
      Expression action = Expressions.asExpression(env, attributes.get("action"));
      Expression result = Expressions.asExpression(env, attributes.get("result"));
      updateExtractRule(r, env, expr, action, result);
      return r;
    }
  }


  public static class MultiTokenPatternExtractRuleCreator extends AnnotationExtractRuleCreator {

    protected static void updateExtractRule(AnnotationExtractRule r,
                                     Env env,
                                     MultiPatternMatcher<CoreMap> pattern,
                                     Expression action,
                                     Expression result) {
      MatchedExpression.SingleAnnotationExtractor annotationExtractor = createAnnotationExtractor(env, r);
      SequenceMatchResultExtractor<CoreMap> valueExtractor = new SequenceMatchResultExtractor<>(env, action, result);
      MultiSequencePatternExtractRule<CoreMap,Value> valueExtractRule = new MultiSequencePatternExtractRule<>(pattern, valueExtractor);
      SequenceMatchedExpressionExtractor exprExtractor = new SequenceMatchedExpressionExtractor( annotationExtractor, r.matchedExpressionGroup );
      MultiSequencePatternExtractRule<CoreMap, MatchedExpression> exprExtractRule =
        new MultiSequencePatternExtractRule<>(pattern, exprExtractor);

      annotationExtractor.expressionToValue = matched -> {
        if (matched != null && matched.context != null && matched.context instanceof SequenceMatchResult ) {
          return valueExtractor.apply( (SequenceMatchResult<CoreMap>) matched.context);
        } else return null;
      };
      if (r.annotationField != null && r.annotationField != CoreMap.class) {
        annotationExtractor.valueExtractor = new CoreMapFunctionApplier<>(env, r.annotationField, valueExtractRule);
        r.extractRule = new CoreMapExtractRule<>(env, r.annotationField, exprExtractRule);
      } else {
        annotationExtractor.valueExtractor = new CoreMapToListFunctionApplier<>(env, valueExtractRule);
        r.extractRule = new CoreMapToListExtractRule<>(exprExtractRule);
      }
      r.filterRule = new AnnotationMatchedFilter(annotationExtractor);
      r.pattern = pattern;
      r.result = result;
    }

    protected static AnnotationExtractRule create(Env env, SequenceMatchRules.AnnotationExtractRule aerTemplate, List<TokenSequencePattern> patterns) {
      AnnotationExtractRule r = new AnnotationExtractRule();
      r.stage = aerTemplate.stage;
      r.active = aerTemplate.active;
      r.priority = Double.NaN; // Priority from patterns?
      r.weight = Double.NaN;  // weight from patterns?
      r.annotationField = aerTemplate.annotationField;
      r.tokensAnnotationField = aerTemplate.tokensAnnotationField;
      r.tokensResultAnnotationField = aerTemplate.tokensResultAnnotationField;
      r.resultAnnotationField = aerTemplate.resultAnnotationField;
      r.resultNestedAnnotationField = aerTemplate.resultNestedAnnotationField;
      r.matchFindType = aerTemplate.matchFindType;
      r.matchedExpressionGroup = aerTemplate.matchedExpressionGroup;
      r.matchWithResults = aerTemplate.matchWithResults;
      r.ruleType = aerTemplate.ruleType;
      r.isComposite = aerTemplate.isComposite;
      r.includeNested = aerTemplate.includeNested;
      r.active = aerTemplate.active;
      r.result = aerTemplate.result;

      if (r.annotationField == null) { r.annotationField = r.tokensAnnotationField;  }
      r.ruleType = TOKEN_PATTERN_RULE_TYPE;
      MultiPatternMatcher<CoreMap> multiPatternMatcher = TokenSequencePattern.getMultiPatternMatcher(patterns);
      multiPatternMatcher.setMatchWithResult(r.matchWithResults);
      updateExtractRule(r, env, multiPatternMatcher, null, r.result);
      return r;
    }

    @Override
    public AnnotationExtractRule create(Env env, Map<String,Object> attributes) {
      throw new UnsupportedOperationException();
    }
  }


  public static class TextPatternExtractRuleCreator extends AnnotationExtractRuleCreator {

    protected static void updateExtractRule(AnnotationExtractRule r,
                                     Env env,
                                     String expr,
                                     Expression action,
                                     Expression result) {
      final MatchedExpression.SingleAnnotationExtractor annotationExtractor = createAnnotationExtractor(env, r);
      Pattern pattern = env.getStringPattern(expr);
      StringMatchResultExtractor valueExtractor = new StringMatchResultExtractor(env, action, result);
      StringPatternExtractRule<Value> valueExtractRule = new StringPatternExtractRule<>(pattern, valueExtractor);
      StringMatchedExpressionExtractor exprExtractor = new StringMatchedExpressionExtractor( annotationExtractor, r.matchedExpressionGroup );
      StringPatternExtractRule<MatchedExpression> exprExtractRule = new StringPatternExtractRule<>(pattern, exprExtractor);

      annotationExtractor.valueExtractor = new CoreMapFunctionApplier<>(env, r.annotationField, valueExtractRule);
      r.extractRule = new CoreMapExtractRule<>(env, r.annotationField, exprExtractRule);
      r.filterRule = new AnnotationMatchedFilter(annotationExtractor);
      r.pattern = pattern;
      r.result = result;
    }

    protected AnnotationExtractRule create(Env env, String expr, Expression result) {
      AnnotationExtractRule r = super.create(env, null);
      if (r.annotationField == null) { r.annotationField = EnvLookup.getDefaultTextAnnotationKey(env);  }
      r.ruleType = TEXT_PATTERN_RULE_TYPE;
      updateExtractRule(r, env, expr, null, result);
      return r;
    }

    @Override
    public AnnotationExtractRule create(Env env, Map<String,Object> attributes) {
      AnnotationExtractRule r = super.create(env, attributes);
      if (r.annotationField == null) { r.annotationField = EnvLookup.getDefaultTextAnnotationKey(env);  }
      if (r.ruleType == null) { r.ruleType = TEXT_PATTERN_RULE_TYPE; }
      String expr = (String) Expressions.asObject(env, attributes.get("pattern"));
      Expression action = Expressions.asExpression(env, attributes.get("action"));
      Expression result = Expressions.asExpression(env, attributes.get("result"));
      updateExtractRule(r, env, expr, action, result);
      return r;
    }
  }


  public static class AnnotationMatchedFilter implements Predicate<MatchedExpression>, Serializable {

    private static final long serialVersionUID = -2085736376364259354L;

    final MatchedExpression.SingleAnnotationExtractor extractor;

    public AnnotationMatchedFilter(MatchedExpression.SingleAnnotationExtractor extractor) {
      this.extractor = extractor;
    }

    @Override
    public boolean test(MatchedExpression me) {
      CoreMap cm = me.getAnnotation();
      Value v = extractor.apply(cm);
      if (v != null) {
        if (v.get() == null) {
          return true;
        } else {
          extractor.annotate(me);
          return false;
        }
        //return v.get() == null;
      } else {
        return false;
      }
    }
  }


  public static class StringMatchResultExtractor implements Function<MatchResult,Value> {
    final Env env;
    final Expression action;
    final Expression result;

    public StringMatchResultExtractor(Env env, Expression action, Expression result) {
      this.env = env;
      this.action = action;
      this.result = result;
    }

    public StringMatchResultExtractor(Env env, Expression result) {
      this(env, null, result);
    }

    @Override
    public Value apply(MatchResult matchResult) {
      Value v = null;
      if (action != null) {
        action.evaluate(env, matchResult);
      }
      if (result != null) {
        v = result.evaluate(env, matchResult);
      }
      return v;
    }
  }


  public static class SequenceMatchResultExtractor<T> implements Function<SequenceMatchResult<T>,Value> {
    final Env env;
    final Expression action;
    final Expression result;

    public SequenceMatchResultExtractor(Env env, Expression action, Expression result) {
      this.env = env;
      this.action = action;
      this.result = result;
    }

    public SequenceMatchResultExtractor(Env env, Expression result) {
      this(env, null, result);
    }

    @Override
    public Value apply(SequenceMatchResult<T> matchResult) {
      Value v = null;
      if (action != null) {
        action.evaluate(env, matchResult);
      }
      if (result != null) {
        v = result.evaluate(env, matchResult);
      }
      return v;
    }
  }


  /**
   * Interface for a rule that extracts a list of matched items from an input.
   *
   * @param <I> input type
   * @param <O> output type
   */
  public interface ExtractRule<I,O> {

    boolean extract(I in, List<O> out);

  }


  /**
   * Extraction rule that filters the input before passing it on to the next extractor.
   *
   * @param <I> input type
   * @param <O> output type
   */
  public static class FilterExtractRule<I,O> implements ExtractRule<I,O> {

    final Predicate<I> filter;
    final ExtractRule<I,O> rule;

    public FilterExtractRule(Predicate<I> filter, ExtractRule<I,O> rule) {
      this.filter = filter;
      this.rule = rule;
    }

    @SafeVarargs
    public FilterExtractRule(Predicate<I> filter, ExtractRule<I,O>... rules) {
      this.filter = filter;
      this.rule = new ListExtractRule<>(rules);
    }

    @Override
    public boolean extract(I in, List<O> out) {
      if (filter.test(in)) {
        return rule.extract(in,out);
      } else {
        return false;
      }
    }
  }


  /**
   * Extraction rule that applies a list of rules in sequence and aggregates
   * all matches found.
   *
   * @param <I> input type
   * @param <O> output type
   */
  public static class ListExtractRule<I,O> implements ExtractRule<I,O> {

    final List<ExtractRule<I,O>> rules;

    public ListExtractRule(Collection<ExtractRule<I,O>> rules) {
      this.rules = new ArrayList<>(rules);
    }

    @SafeVarargs
    public ListExtractRule(ExtractRule<I,O>... rules) {
      this.rules = new ArrayList<>(rules.length);
      Collections.addAll(this.rules, rules);
    }

    @Override
    public boolean extract(I in, List<O> out) {
      boolean extracted = false;
      for (ExtractRule<I,O> rule:rules) {
        if (rule.extract(in,out)) {
          extracted = true;
        }
      }
      return extracted;
    }

    @SafeVarargs
    public final void addRules(ExtractRule<I, O>... rules) {
      Collections.addAll(this.rules, rules);
    }

    public void addRules(Collection<ExtractRule<I,O>> rules) {
      this.rules.addAll(rules);
    }

    public String ruleList() {
      List<String> names = new ArrayList<>();
      for (ExtractRule rule: rules) {
        if (rule instanceof AnnotationExtractRule) {
          AnnotationExtractRule aer = (AnnotationExtractRule) rule;
          String ruleString; // initialized below
          if (aer.pattern != null) {
            ruleString = aer.pattern.toString();
          } else if (aer.extractRule != null) {
            ruleString = aer.extractRule.toString();
          } else if (aer.filterRule != null) {
            ruleString = aer.filterRule.toString();
          } else {
            ruleString = aer.toString();
          }
          names.add(ruleString);
        } else {
          names.add(rule.getClass().getName());
        }
      }
      return names.toString();
    }

    public String toString() {
      return "ListExtractRule[" + ruleList() + ']';
    }

  }


  /**
   * Extraction rule to apply a extraction rule on a particular CoreMap field.
   * Input is of type CoreMap, output is templated type O.
   *
   * @param <T> type of the annotation field
   * @param <O> output type
   */
  public static class CoreMapExtractRule<T,O> implements ExtractRule<CoreMap, O> {

    final Env env;
    final Class annotationField;
    final ExtractRule<T,O> extractRule;

    public CoreMapExtractRule(Env env, Class annotationField, ExtractRule<T,O> extractRule) {
      this.annotationField = annotationField;
      this.extractRule = extractRule;
      this.env = env;
    }

    @Override
    public boolean extract(CoreMap cm, List<O> out) {
      env.push(Expressions.VAR_SELF, cm);
      try {
        T field = (T) cm.get(annotationField);
        return extractRule.extract(field, out);
      } finally {
        env.pop(Expressions.VAR_SELF);
      }
    }
  }


  /**
   * Extraction rule that treats a single CoreMap as a list/sequence of CoreMaps.
   * (A convenience class, for use with BasicSequenceExtractRule.)
   * Input is of type CoreMap, output is templated type O.
   *
   * @param <O> output type
   */
  public static class CoreMapToListExtractRule<O> implements ExtractRule<CoreMap, O> {
    final ExtractRule<List<? extends CoreMap>,O> extractRule;

    public CoreMapToListExtractRule(ExtractRule<List<? extends CoreMap>,O> extractRule) {
      this.extractRule = extractRule;
    }

    @Override
    public boolean extract(CoreMap cm, List<O> out) {
      return extractRule.extract(Arrays.asList(cm), out);
    }
  }


  /**
   * Extraction rule.
   * Input is of type CoreMap, output is MatchedExpression.
   */
  public static class BasicSequenceExtractRule implements ExtractRule< List<? extends CoreMap>, MatchedExpression> {

    final MatchedExpression.SingleAnnotationExtractor extractor;

    public BasicSequenceExtractRule(MatchedExpression.SingleAnnotationExtractor extractor) {
      this.extractor = extractor;
    }

    @Override
    public boolean extract(List<? extends CoreMap> seq, List<MatchedExpression> out) {
      boolean extracted = false;
      for (int i = 0; i < seq.size(); i++) {
        CoreMap t = seq.get(i);
        Value v = extractor.apply(t);
        if (v != null) {
          MatchedExpression te = extractor.createMatchedExpression(Interval.toInterval(i, i + 1, Interval.INTERVAL_OPEN_END), null);
          out.add(te);
          extracted = true;
        }
      }
      return extracted;
    }
  }


  public static class SequencePatternExtractRule<T,O> implements ExtractRule< List<? extends T>, O>, Function<List<? extends T>, O> {

    final SequencePattern<T> pattern;
    final Function<SequenceMatchResult<T>, O> extractor;
    final SequenceMatcher.FindType findType;
    final boolean matchWithResult;

    public SequencePatternExtractRule(Env env, String regex, Function<SequenceMatchResult<T>, O> extractor) {
      this(SequencePattern.compile(env, regex), extractor);
    }

    public SequencePatternExtractRule(SequencePattern<T> p, Function<SequenceMatchResult<T>, O> extractor) {
      this(p, extractor, null, false);
    }

    public SequencePatternExtractRule(SequencePattern<T> p, Function<SequenceMatchResult<T>, O> extractor,
                                      SequenceMatcher.FindType findType, boolean matchWithResult) {
      this.extractor = extractor;
      this.pattern = p;
      this.findType = findType;
      this.matchWithResult = matchWithResult;
    }

    @Override
    public boolean extract(List<? extends T> seq, List<O> out) {
      if (seq == null) return false;
      boolean extracted = false;
      SequenceMatcher<T> m = pattern.getMatcher(seq);
      if (findType != null) {
        m.setFindType(findType);
      }
      m.setMatchWithResult(matchWithResult);
      while (m.find()) {
        out.add(extractor.apply(m));
        extracted = true;
      }
      // System.err.println("SequencePattern " + pattern + " of type " + pattern.getClass() + " matched on " + extracted);
      return extracted;
    }

    @Override
    public O apply(List<? extends T> seq) {
      if (seq == null) return null;
      SequenceMatcher<T> m = pattern.getMatcher(seq);
      m.setMatchWithResult(matchWithResult);
      if (m.matches()) {
        return extractor.apply(m);
      } else {
        return null;
      }
    }

  } // end static class SequencePatternExtractRule


  public static class MultiSequencePatternExtractRule<T,O> implements ExtractRule< List<? extends T>, O>, Function<List<? extends T>, O> {

    final MultiPatternMatcher<T> matcher;
    final Function<SequenceMatchResult<T>, O> extractor;

    public MultiSequencePatternExtractRule(MultiPatternMatcher<T> matcher, Function<SequenceMatchResult<T>, O> extractor) {
      this.extractor = extractor;
      this.matcher = matcher;
    }

    @Override
    public boolean extract(List<? extends T> seq, List<O> out) {
      if (seq == null) return false;
      boolean extracted = false;
      List<SequenceMatchResult<T>> matched = matcher.findNonOverlappingMaxScore(seq);
      for (SequenceMatchResult<T> m : matched) {
        out.add(extractor.apply(m));
        extracted = true;
      }
      return extracted;
    }

    @Override
    public O apply(List<? extends T> seq) {
      if (seq == null) return null;
      List<SequenceMatchResult<T>> matched = matcher.findNonOverlappingMaxScore(seq);
      if ( ! matched.isEmpty()) {
        return extractor.apply(matched.get(0));
      } else {
        return null;
      }
    }
  }


  public static class StringPatternExtractRule<O> implements ExtractRule<String, O>, Function<String, O> {

    private final Pattern pattern;
    private final Function<MatchResult, O> extractor;

    public StringPatternExtractRule(Pattern pattern, Function<MatchResult, O> extractor) {
      this.pattern = pattern;
      this.extractor = extractor;
    }

    public StringPatternExtractRule(Env env, String regex, Function<MatchResult, O> extractor) {
      this(env, regex, extractor, false);
    }

    public StringPatternExtractRule(String regex, Function<MatchResult, O> extractor) {
      this(null, regex, extractor, false);
    }

    public StringPatternExtractRule(Env env, String regex, Function<MatchResult, O> extractor,
                                    boolean addWordBoundaries) {
      this.extractor = extractor;
      if (addWordBoundaries) { regex = "\\b(?:" + regex + ")\\b"; }
      if (env != null) {
        pattern = env.getStringPattern(regex);
      } else {
        pattern = Pattern.compile(regex);
      }
    }

    @Override
    public boolean extract(String str, List<O> out) {
      if (str == null) return false;
      boolean extracted = false;
      Matcher m = pattern.matcher(str);
      while (m.find()) {
        out.add(extractor.apply( m ));
        // System.err.println("StringPatternExtractRule: " + pattern + " extracted " + out.get(out.size() - 1)); // XXXX
        extracted = true;
      }
      return extracted;
    }

    @Override
    public O apply(String str) {
      if (str == null) return null;
      Matcher m = pattern.matcher(str);
      if (m.matches()) {
        return extractor.apply(m);
      } else {
        return null;
      }
    }

  } // end static class StringPatternExtractRule


  public static class StringMatchedExpressionExtractor implements Function<MatchResult, MatchedExpression> {

    final MatchedExpression.SingleAnnotationExtractor extractor;
    final int group;

    public StringMatchedExpressionExtractor(MatchedExpression.SingleAnnotationExtractor extractor, int group) {
      this.extractor = extractor;
      this.group = group;
    }

    @Override
    public MatchedExpression apply(MatchResult matched) {
      MatchedExpression te = extractor.createMatchedExpression(Interval.toInterval(matched.start(group), matched.end(group), Interval.INTERVAL_OPEN_END), null);
      return te;
    }
  }


  public static class SequenceMatchedExpressionExtractor implements Function<SequenceMatchResult<CoreMap>, MatchedExpression> {

    final MatchedExpression.SingleAnnotationExtractor extractor;
    final int group;

    public SequenceMatchedExpressionExtractor(MatchedExpression.SingleAnnotationExtractor extractor, int group) {
      this.extractor = extractor;
      this.group = group;
    }

    @Override
    public MatchedExpression apply(SequenceMatchResult<CoreMap> matched) {
      MatchedExpression te = extractor.createMatchedExpression(null, Interval.toInterval(matched.start(group), matched.end(group), Interval.INTERVAL_OPEN_END));
      if (Double.isNaN(te.priority)) {
        te.priority = matched.priority();
      }
      if (Double.isNaN(te.weight)) {
        te.weight = matched.score();
      }
      if (this.group != 0) {
        // Save context so value evaluation can happen
        te.context = matched.toBasicSequenceMatchResult();
      }
      return te;
    }
  }


  public static class CoreMapFunctionApplier<T,O> implements Function<CoreMap, O> {

    final Env env;
    final Class annotationField;
    final Function<T,O> func;

    public CoreMapFunctionApplier(Env env, Class annotationField, Function<T,O> func) {
      this.annotationField = annotationField;
      if (annotationField == null) {
        throw new IllegalArgumentException("Annotation field cannot be null");
      }
      this.func = func;
      this.env = env;
    }

    @Override
    public O apply(CoreMap cm) {
      if (env != null) {
        env.push(Expressions.VAR_SELF, cm);
      }
      try {
        T field = (T) cm.get(annotationField);
        return func.apply(field);
      }  finally {
        if (env != null) {
          env.pop(Expressions.VAR_SELF);
        }
      }
    }
  }


  public static class CoreMapToListFunctionApplier<O> implements Function<CoreMap, O> {

    final Env env;
    final Function<List<? extends CoreMap>,O> func;

    public CoreMapToListFunctionApplier(Env env, Function<List<? extends CoreMap>,O> func) {
      this.func = func;
      this.env = env;
    }

    @Override
    public O apply(CoreMap cm) {
      if (env != null) {
        env.push(Expressions.VAR_SELF, cm);
      }
      try {
        return func.apply(Collections.singletonList(cm));
      } finally {
        if (env != null) {
          env.pop(Expressions.VAR_SELF);
        }
      }
    }

  } // end static class CoreMapToListFunctionApplier

}
