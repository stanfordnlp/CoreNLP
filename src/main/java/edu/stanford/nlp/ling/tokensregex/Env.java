package edu.stanford.nlp.ling.tokensregex;

import edu.stanford.nlp.ling.tokensregex.types.Expressions;
import edu.stanford.nlp.ling.tokensregex.types.Tags;
import edu.stanford.nlp.pipeline.CoreMapAggregator;
import edu.stanford.nlp.pipeline.CoreMapAttributeAggregator;
import java.util.function.Function;

import edu.stanford.nlp.process.CoreLabelTokenFactory;
import edu.stanford.nlp.util.Pair;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Holds environment variables to be used for compiling string into a pattern.
 * Use {@link EnvLookup} to perform actual lookup (it will provide reasonable defaults).
 *
 * <p>
 * Some of the types of variables to bind are:
 * </p>
 * <ul>
 * <li>{@code SequencePattern} (compiled pattern)</li>
 * <li>{@code PatternExpr} (sequence pattern expression - precompiled)</li>
 * <li>{@code NodePattern} (pattern for matching one element)</li>
 * <li>{@code Class} (binding of CoreMap attribute to java Class)</li>
 * </ul>
 *
 * @author Angel Chang
 */
// Various of the public variables in this class are instantiated by reflection from TokensRegex rules
@SuppressWarnings({"WeakerAccess", "unused"})
public class Env {

  /**
   * Parser that converts a string into a SequencePattern.
   * @see edu.stanford.nlp.ling.tokensregex.parser.TokenSequenceParser
   */
  SequencePattern.Parser parser;

  /**
   * Mapping of variable names to their values
   */
  private Map<String, Object> variables = new HashMap<>();//Generics.newHashMap();

  /**
   * Mapping of per thread temporary variables to their values.
   */
  private ThreadLocal<Map<String,Object>> threadLocalVariables = new ThreadLocal<>();

  /**
   * Mapping of variables that can be expanded in a regular expression for strings,
   *   to their regular expressions.
   * The variable name must start with "$" and include only the alphanumeric characters
   *   (it should follow the pattern {@code $[A-Za-z0-9_]+}).
   * Each variable is mapped to a pair, consisting of the {@code Pattern} representing
   *   the name of the variable to be replaced, and a {@code String} representing the
   *   regular expression (escaped) that is used to replace the name of the variable.
   */
  private Map<String, Pair<Pattern,String>> stringRegexVariables = new HashMap<>(); //Generics.newHashMap();

  /**
   * Default parameters (used when reading in rules for {@link SequenceMatchRules}.
   */
  public Map<String, Object> defaults = new HashMap<>();//Generics.newHashMap();

  /**
   * Default flags to use for string regular expressions match
   * @see java.util.regex.Pattern#compile(String,int)
   */
  public int defaultStringPatternFlags = 0;

  /**
   * Default flags to use for string literal match
   * @see NodePattern#CASE_INSENSITIVE
   */
  public int defaultStringMatchFlags = 0;

  public Class sequenceMatchResultExtractor;
  public Class stringMatchResultExtractor;

  /**
   * Annotation key to use to getting tokens (default is CoreAnnotations.TokensAnnotation.class)
   */
  public Class defaultTokensAnnotationKey;

  /**
   * Annotation key to use to getting text (default is CoreAnnotations.TextAnnotation.class)
   */
  public Class defaultTextAnnotationKey;

  /**
   * List of keys indicating the per-token annotations (default is null).
   * If specified, each token will be annotated with the extracted results from the
   *   {@link #defaultResultsAnnotationExtractor}.
   * If null, then individual tokens that are matched are not annotated.
   */
  public List<Class> defaultTokensResultAnnotationKey;

  /**
   * List of keys indicating what fields should be annotated for the aggregated CoreMap.
   * If specified, the aggregated CoreMap is annotated with the extracted results from the
   *   {@link #defaultResultsAnnotationExtractor}.
   * If null, then the aggregated CoreMap is not annotated.
   */
  public List<Class> defaultResultAnnotationKey;

  /**
   * Annotation key to use during composite phase for storing matched sequences and to match against.
   */
  public Class defaultNestedResultsAnnotationKey;

  /**
   * How should the tokens be aggregated when collapsing a sequence of tokens into one CoreMap
   */
  public Map<Class, CoreMapAttributeAggregator> defaultTokensAggregators;

  private CoreMapAggregator defaultTokensAggregator;

  /**
   * Whether we should merge and output CoreLabels or not.
   */
  public boolean aggregateToTokens;


   /**
   * How annotations are extracted from the MatchedExpression.
   * If the result type is a List and more than one annotation key is specified,
   * then the result is paired with the annotation key.
   * Example: If annotation key is [ner,normalized] and result is [CITY,San Francisco]
   *            then the final CoreMap will have ner=CITY, normalized=San Francisco.
   * Otherwise, the result is treated as one object (all keys will be assigned that value).
   */
  Function<MatchedExpression,?> defaultResultsAnnotationExtractor;

  /**
   * Interface for performing custom binding of values to the environment
   */
  public interface Binder {
    void init(String prefix, Properties props);
    void bind(Env env);
  }

  public Env(SequencePattern.Parser p) { this.parser = p; }

  public void initDefaultBindings() {
    bind("FALSE", Expressions.FALSE);
    bind("TRUE", Expressions.TRUE);
    bind("NIL", Expressions.NIL);
    bind("ENV", this);
    bind("tags", Tags.TagsAnnotation.class);
  }

  public Map<String, Object> getDefaults() {
    return defaults;
  }

  public void setDefaults(Map<String, Object> defaults) {
    this.defaults = defaults;
  }

  public Map<Class, CoreMapAttributeAggregator> getDefaultTokensAggregators() {
    return defaultTokensAggregators;
  }

  public void setDefaultTokensAggregators(Map<Class, CoreMapAttributeAggregator> defaultTokensAggregators) {
    this.defaultTokensAggregators = defaultTokensAggregators;
  }

  public CoreMapAggregator getDefaultTokensAggregator() {
    if (defaultTokensAggregator == null && (defaultTokensAggregators != null || aggregateToTokens)) {
      CoreLabelTokenFactory tokenFactory = (aggregateToTokens) ? new CoreLabelTokenFactory() : null;
      Map<Class, CoreMapAttributeAggregator> aggregators = defaultTokensAggregators;
      if (aggregators == null) {
        aggregators = CoreMapAttributeAggregator.DEFAULT_NUMERIC_TOKENS_AGGREGATORS;
      }
      defaultTokensAggregator = CoreMapAggregator.getAggregator(aggregators, null, tokenFactory);
    }
    return defaultTokensAggregator;
  }

  public Class getDefaultTextAnnotationKey() {
    return defaultTextAnnotationKey;
  }

  public void setDefaultTextAnnotationKey(Class defaultTextAnnotationKey) {
    this.defaultTextAnnotationKey = defaultTextAnnotationKey;
  }

  public Class getDefaultTokensAnnotationKey() {
    return defaultTokensAnnotationKey;
  }

  public void setDefaultTokensAnnotationKey(Class defaultTokensAnnotationKey) {
    this.defaultTokensAnnotationKey = defaultTokensAnnotationKey;
  }

  public List<Class> getDefaultTokensResultAnnotationKey() {
    return defaultTokensResultAnnotationKey;
  }

  public void setDefaultTokensResultAnnotationKey(Class... defaultTokensResultAnnotationKey) {
    this.defaultTokensResultAnnotationKey = Arrays.asList(defaultTokensResultAnnotationKey);
  }

  public void setDefaultTokensResultAnnotationKey(List<Class> defaultTokensResultAnnotationKey) {
    this.defaultTokensResultAnnotationKey = defaultTokensResultAnnotationKey;
  }

  public List<Class> getDefaultResultAnnotationKey() {
    return defaultResultAnnotationKey;
  }

  public void setDefaultResultAnnotationKey(Class... defaultResultAnnotationKey) {
    this.defaultResultAnnotationKey = Arrays.asList(defaultResultAnnotationKey);
  }

  public void setDefaultResultAnnotationKey(List<Class> defaultResultAnnotationKey) {
    this.defaultResultAnnotationKey = defaultResultAnnotationKey;
  }

  public Class getDefaultNestedResultsAnnotationKey() {
    return defaultNestedResultsAnnotationKey;
  }

  public void setDefaultNestedResultsAnnotationKey(Class defaultNestedResultsAnnotationKey) {
    this.defaultNestedResultsAnnotationKey = defaultNestedResultsAnnotationKey;
  }


  public Function<MatchedExpression, ?> getDefaultResultsAnnotationExtractor() {
    return defaultResultsAnnotationExtractor;
  }

  public void setDefaultResultsAnnotationExtractor(Function<MatchedExpression, ?> defaultResultsAnnotationExtractor) {
    this.defaultResultsAnnotationExtractor = defaultResultsAnnotationExtractor;
  }

  public Class getSequenceMatchResultExtractor() {
    return sequenceMatchResultExtractor;
  }

  public void setSequenceMatchResultExtractor(Class sequenceMatchResultExtractor) {
    this.sequenceMatchResultExtractor = sequenceMatchResultExtractor;
  }

  public Class getStringMatchResultExtractor() {
    return stringMatchResultExtractor;
  }

  public void setStringMatchResultExtractor(Class stringMatchResultExtractor) {
    this.stringMatchResultExtractor = stringMatchResultExtractor;
  }

  public Map<String, Object> getVariables() {
    return variables;
  }

  public void setVariables(Map<String, Object> variables) {
    this.variables = variables;
  }

  public void clearVariables() {
    this.variables.clear();
  }

  public int getDefaultStringPatternFlags() {
    return defaultStringPatternFlags;
  }

  public void setDefaultStringPatternFlags(int defaultStringPatternFlags) {
    this.defaultStringPatternFlags = defaultStringPatternFlags;
  }

  public int getDefaultStringMatchFlags() {
    return defaultStringMatchFlags;
  }

  public void setDefaultStringMatchFlags(int defaultStringMatchFlags) {
    this.defaultStringMatchFlags = defaultStringMatchFlags;
  }

  private static final Pattern STRING_REGEX_VAR_NAME_PATTERN = Pattern.compile("\\$[A-Za-z0-9_]+");
  public void bindStringRegex(String var, String regex)
  {
    // Enforce requirements on variable names ($alphanumeric_)
    if (!STRING_REGEX_VAR_NAME_PATTERN.matcher(var).matches()) {
      throw new IllegalArgumentException("StringRegex binding error: Invalid variable name " + var);
    }
    Pattern varPattern = Pattern.compile(Pattern.quote(var));
    String replace = Matcher.quoteReplacement(regex);
    stringRegexVariables.put(var, new Pair<>(varPattern, replace));
  }

  public String expandStringRegex(String regex) {
    // Replace all variables in regex
    String expanded = regex;
    for (Map.Entry<String, Pair<Pattern, String>> stringPairEntry : stringRegexVariables.entrySet()) {
      Pair<Pattern,String> p = stringPairEntry.getValue();
      expanded = p.first().matcher(expanded).replaceAll(p.second());
    }
    return expanded;
  }

  public Pattern getStringPattern(String regex) {
    String expanded = expandStringRegex(regex);
    return Pattern.compile(expanded, defaultStringPatternFlags);
  }

  public void bind(String name, Object obj) {
    if (obj != null) {
      variables.put(name, obj);
    } else {
      variables.remove(name);
    }
  }

  public void bind(String name, SequencePattern pattern) {
    bind(name, pattern.getPatternExpr());
  }

  public void unbind(String name) {
    bind(name, null);
  }

  public NodePattern getNodePattern(String name) {
    Object obj = variables.get(name);
    if (obj != null) {
      if (obj instanceof SequencePattern) {
        SequencePattern seqPattern = (SequencePattern) obj;
        if (seqPattern.getPatternExpr() instanceof SequencePattern.NodePatternExpr) {
          return ((SequencePattern.NodePatternExpr) seqPattern.getPatternExpr()).nodePattern;
        } else {
          throw new Error("Invalid node pattern class: " + seqPattern.getPatternExpr().getClass() + " for variable " + name);
        }
      } else if (obj instanceof SequencePattern.NodePatternExpr) {
        SequencePattern.NodePatternExpr pe = (SequencePattern.NodePatternExpr) obj;
        return pe.nodePattern;
      } else if (obj instanceof NodePattern) {
        return (NodePattern) obj;
      } else if (obj instanceof String) {
        try {
          SequencePattern.NodePatternExpr pe = (SequencePattern.NodePatternExpr) parser.parseNode(this, (String) obj);
          return pe.nodePattern;
        } catch (Exception pex) {
          throw new RuntimeException("Error parsing " + obj + " to node pattern", pex);
        }
      } else {
        throw new Error("Invalid node pattern variable class: " + obj.getClass() + " for variable " + name);
      }
    }
    return null;
  }

  public SequencePattern.PatternExpr getSequencePatternExpr(String name, boolean copy) {
    Object obj = variables.get(name);
    if (obj != null) {
      if (obj instanceof SequencePattern) {
        SequencePattern seqPattern = (SequencePattern) obj;
        return seqPattern.getPatternExpr();
      } else if (obj instanceof SequencePattern.PatternExpr) {
        SequencePattern.PatternExpr pe = (SequencePattern.PatternExpr) obj;
        return (copy)? pe.copy():pe;
      } else if (obj instanceof NodePattern) {
        return new SequencePattern.NodePatternExpr( (NodePattern) obj);
      } else if (obj instanceof String) {
        try {
          return parser.parseSequence(this, (String) obj);
        } catch (Exception pex) {
          throw new RuntimeException("Error parsing " + obj + " to sequence pattern", pex);
        }
      } else {
        throw new Error("Invalid sequence pattern variable class: " + obj.getClass());
      }
    }
    return null;
  }

  public Object get(String name)
  {
      return variables.get(name);
  }

  // Functions for storing temporary thread specific variables
  //  that are used when running tokensregex

  public void push(String name, Object value) {
    Map<String,Object> vars = threadLocalVariables.get();
    if (vars == null) {
      threadLocalVariables.set(vars = new HashMap<>()); //Generics.newHashMap());
    }
    Stack<Object> stack = (Stack<Object>) vars.get(name);
    if (stack == null) {
      vars.put(name, stack = new Stack<>());
    }
    stack.push(value);
  }

  public Object pop(String name) {
    Map<String,Object> vars = threadLocalVariables.get();
    if (vars == null) return null;
    Stack<Object> stack = (Stack<Object>) vars.get(name);
    if (stack == null || stack.isEmpty()) {
      return null;
    } else {
      return stack.pop();
    }
  }

  public Object peek(String name) {
    Map<String,Object> vars = threadLocalVariables.get();
    if (vars == null) return null;
    Stack<Object> stack = (Stack<Object>) vars.get(name);
    if (stack == null || stack.isEmpty()) {
      return null;
    } else {
      return stack.peek();
    }
  }

}
