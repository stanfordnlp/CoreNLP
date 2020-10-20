package edu.stanford.nlp.ling.tokensregex;

import java.util.*;
import java.util.stream.Collectors;

import edu.stanford.nlp.ling.tokensregex.parser.TokenSequenceParser;
import edu.stanford.nlp.util.*;

/**
 * Token Sequence Pattern for regular expressions over sequences of tokens (each represented as a {@code CoreMap}).
 * Sequences over tokens can be matched like strings.
 * <p>
 * To use:
 * </p>
 * <pre>{@code
 *   TokenSequencePattern p = TokenSequencePattern.compile("....");
 *   TokenSequenceMatcher m = p.getMatcher(tokens);
 *   while (m.find()) ....
 * }</pre>
 *
 * <p>
 * Supports the following:
 * <ul>
 *  <li>Concatenation: {@code X Y}</li>
 *  <li>Or: {@code X | Y}</li>
 *  <li>And: {@code X & Y}</li>
 *  <li>Groups:
 *     <ul>
 *     <li>capturing: {@code (X)} (with numeric group id)</li>
 *     <li>capturing: {@code (?$var X)} (with group name "$var")</li>
 *     <li>noncapturing: {@code (?:X)}</li>
 *     </ul>
 *  Capturing groups can be retrieved with group id or group variable, as matched string
 *     ({@code m.group()}) or list of tokens ({@code m.groupNodes()}).
 *  <ul>
 *     <li>To retrieve group using id: {@code m.group(id)} or {@code m.groupNodes(id)}
 *     <br> NOTE: Capturing groups are indexed from left to right, starting at one.  Group zero is the entire matched sequence.
 *     </li>
 *     <li>To retrieve group using bound variable name: {@code m.group("$var")} or {@code m.groupNodes("$var")}
 *     </li>
 *  </ul>
 *  See {@link SequenceMatchResult} for more accessor functions to retrieve matches.
 * </li>
 * <li>Greedy Quantifiers:  {@code X+, X?, X*, X{n,m}, X{n}, X{n,}}</li>
 * <li>Reluctant Quantifiers: {@code X+?, X??, X*?, X{n,m}?, X{n}?, X{n,}?}</li>
 * <li>Back references: {@code \captureid} </li>
 * <li>Value binding for groups: {@code [pattern] => [value]}.
 *   Value for matched expression can be accessed using {@code m.groupValue()}
 *   <br>Example: {@code ( one => 1 | two => 2 | three => 3 | ...)}
 * </li>
 * </ul>
 *
 * <p>
 * Individual tokens are marked by {@code "[" TOKEN_EXPR "]" }
 * <br>Possible {@code TOKEN_EXPR}:
 * </p>
 * <ul>
 * <li> All specified token attributes match:
 * <br> For Strings:
 *     {@code { lemma:/.../; tag:"NNP" } } = attributes that need to all match.
 *     If only one attribute, the {} can be dropped.
 * <br> See {@link edu.stanford.nlp.ling.AnnotationLookup AnnotationLookup} for a list of predefined token attribute names.
 * <br> Additional attributes can be bound using the environment (see below).
 * <br> NOTE: {@code /.../} used for regular expressions,
 *            {@code "..."} for exact string matches
 * <br> For Numbers:
 *      {@code { word>=2 }}
 * <br> NOTE: Relation can be {@code ">=", "<=", ">", "<",} or {@code "=="}
 * <br> Others:
 *      {@code { word::IS_NUM } , { word::IS_NIL } } or
 *      {@code { word::NOT_EXISTS }, { word::NOT_NIL } } or {@code { word::EXISTS } }
 * </li>
 * <li>Short hand for just word/text match:
 *     {@code /.../ }  or  {@code "..." }
 * </li>
 * <li>
 *  Negation:
 *     {@code !{...} }
 * </li>
 * <li>
 *  Conjunction or Disjunction:
 *     {@code {...} & {...} }   or  {@code {...} | {...} }
 * </li>
 * </ul>
 *
 * <p>
 * Special tokens:
 *   Any token: {@code []}
 * </p>
 *
 * <p>
 * String pattern match across multiple tokens:
 *   {@code (?m){min,max} /pattern/}
 * </p>
 *
 * <p>
 * Special expressions: indicated by double braces: {@code {{ expr }}}
 *   <br> See {@link edu.stanford.nlp.ling.tokensregex.types.Expressions} for syntax.
 * </p>
 *
 * <p>
 * Binding of variables for use in compiling patterns:
 * </p>
 * <ol>
 * <li> Use  {@code Env env = TokenSequencePattern.getNewEnv()} to create a new environment for binding </li>
 * <li> Bind string to attribute key (Class) lookup:
 *    {@code env.bind("numtype", CoreAnnotations.NumericTypeAnnotation.class);}
 * </li>
 * <li> Bind patterns / strings for compiling patterns
 *    <pre>{@code
 *    // Bind string for later compilation using: compile("/it/ /was/ $RELDAY");
 *    env.bind("$RELDAY", "/today|yesterday|tomorrow|tonight|tonite/");
 *    // Bind pre-compiled patter for later compilation using: compile("/it/ /was/ $RELDAY");
 *    env.bind("$RELDAY", TokenSequencePattern.compile(env, "/today|yesterday|tomorrow|tonight|tonite/"));
 *    }</pre>
 * </li>
 * <li> Bind custom node pattern functions (currently no arguments are supported)
 *    <pre>{@code
 *    // Bind node pattern so we can do patterns like: compile("... temporal::IS_TIMEX_DATE ...");
 *    //   (TimexTypeMatchNodePattern is a NodePattern that implements some custom logic)
 *    env.bind("::IS_TIMEX_DATE", new TimexTypeMatchNodePattern(SUTime.TimexType.DATE));
 *   }</pre>
 * </li>
 * </ol>
 *
 * <p>
 * Actions (partially implemented)
 * </p>
 * <ul>
 * <li> {@code pattern ==> action} </li>
 * <li> Supported action:
 *    {@code &annotate( { ner="DATE" } ) } </li>
 * <li> Not applied automatically, associated with a pattern.</li>
 * <li> To apply, call {@code pattern.getAction().apply(match, groupid)}</li>
 * </ul>
 *
 * @author Angel Chang
 * @see TokenSequenceMatcher
 */
public class TokenSequencePattern extends SequencePattern<CoreMap> {

  private static final long serialVersionUID = -4760710834202406916L;

  public static final TokenSequencePattern ANY_NODE_PATTERN = TokenSequencePattern.compile(ANY_NODE_PATTERN_EXPR);

  private static final Env DEFAULT_ENV = getNewEnv();

  public TokenSequencePattern(String patternStr, SequencePattern.PatternExpr nodeSequencePattern) {
    super(patternStr, nodeSequencePattern);
  }

  public TokenSequencePattern(String patternStr, SequencePattern.PatternExpr nodeSequencePattern,
                                 SequenceMatchAction<CoreMap> action) {
    super(patternStr, nodeSequencePattern, action);
  }

  public static Env getNewEnv() {
    Env env =  new Env(new TokenSequenceParser());
    env.initDefaultBindings();
    return env;
  }

  /**
   * Compiles a regular expression over tokens into a TokenSequencePattern
   * using the default environment.
   *
   * @param string Regular expression to be compiled
   * @return Compiled TokenSequencePattern
   */
  public static TokenSequencePattern compile(String string)
  {
    return compile(DEFAULT_ENV, string);
  }

  /**
   * Compiles a regular expression over tokens into a TokenSequencePattern
   * using the specified environment.
   *
   * @param env Environment to use
   * @param string Regular expression to be compiled
   * @return Compiled TokenSequencePattern
   */
  public static TokenSequencePattern compile(Env env, String string) {
    try {
//      SequencePattern.PatternExpr nodeSequencePattern = TokenSequenceParser.parseSequence(env, string);
//      return new TokenSequencePattern(string, nodeSequencePattern);
      // TODO: Check token sequence parser?
      Pair<PatternExpr, SequenceMatchAction<CoreMap>> p = env.parser.parseSequenceWithAction(env, string);
      return new TokenSequencePattern(string, p.first(), p.second());
    } catch (Exception ex) {
      throw new RuntimeException("Error when parsing " + string, ex);
    }
  }

  /**
   * Compiles a sequence of regular expressions into a TokenSequencePattern
   * using the default environment.
   *
   * @param strings List of regular expression to be compiled
   * @return Compiled TokenSequencePattern
   */
  public static TokenSequencePattern compile(String... strings)
  {
    return compile(DEFAULT_ENV, strings);
  }

  /**
   * Compiles a sequence of regular expressions into a TokenSequencePattern
   * using the specified environment.
   *
   * @param env Environment to use
   * @param strings List of regular expression to be compiled
   * @return Compiled TokenSequencePattern
   */
  public static TokenSequencePattern compile(Env env, String... strings)
  {
    try {
      List<SequencePattern.PatternExpr> patterns = new ArrayList<>();
      for (String string:strings) {
        // TODO: Check token sequence parser?
        SequencePattern.PatternExpr pattern = env.parser.parseSequence(env, string);
        patterns.add(pattern);
      }
      SequencePattern.PatternExpr nodeSequencePattern = new SequencePattern.SequencePatternExpr(patterns);
      return new TokenSequencePattern(StringUtils.join(strings), nodeSequencePattern);
    } catch (Exception ex) {
      throw new RuntimeException(ex);
    }
  }

  /**
   * Compiles a PatternExpr into a TokenSequencePattern.
   *
   * @param nodeSequencePattern A sequence pattern expression (before translation into a NFA)
   * @return Compiled TokenSequencePattern
   */
  public static TokenSequencePattern compile(SequencePattern.PatternExpr nodeSequencePattern) {
    return new TokenSequencePattern(null, nodeSequencePattern);
  }

  /**
   * Returns a TokenSequenceMatcher that can be used to match this pattern
   * against the specified list of tokens.
   *
   * @param tokens List of tokens to match against
   * @return TokenSequenceMatcher
   */
  @Override
  public TokenSequenceMatcher getMatcher(List<? extends CoreMap> tokens) {
    return new TokenSequenceMatcher(this, tokens);
  }

  /**
   * Returns a TokenSequenceMatcher that can be used to match this pattern
   * against the specified list of tokens.
   *
   * @param tokens List of tokens to match against
   * @return TokenSequenceMatcher
   */
  public TokenSequenceMatcher matcher(List<? extends CoreMap> tokens) {
    return getMatcher(tokens);
  }

  /** Returns a String representation of the TokenSequencePattern.
   *
   * @return A String representation of the TokenSequencePattern
   */
  @Override
  public String toString(){
    return this.pattern();
  }


  /**
   * Create a multi-pattern matcher for matching across multiple TokensRegex patterns.
   *
   * @param patterns Collection of input patterns
   * @return A MultiPatternMatcher
   */
  public static MultiPatternMatcher<CoreMap> getMultiPatternMatcher(Collection<TokenSequencePattern> patterns) {
    return new MultiPatternMatcher<>(
            new MultiPatternMatcher.BasicSequencePatternTrigger<>(new CoreMapNodePatternTrigger(patterns)),
            patterns);
  }

  /**
   * Create a multi-pattern matcher for matching across multiple TokensRegex patterns.
   *
   * @param patterns Input patterns
   * @return A MultiPatternMatcher
   */
  public static MultiPatternMatcher<CoreMap> getMultiPatternMatcher(TokenSequencePattern... patterns) {
    return new MultiPatternMatcher<>(
            new MultiPatternMatcher.BasicSequencePatternTrigger<>(new CoreMapNodePatternTrigger(patterns)),
            patterns);
  }

  /**
   * Create a multi-pattern matcher for matching across multiple TokensRegex patterns from Strings.
   *
   * @param patterns Input patterns in String format
   * @return A MultiPatternMatcher
   */
  public static MultiPatternMatcher<CoreMap> getMultiPatternMatcher(String... patterns) {
    List<TokenSequencePattern> tokenSequencePatterns = Arrays.stream(patterns).map(TokenSequencePattern::compile)
            .collect(Collectors.toList());
    return TokenSequencePattern.getMultiPatternMatcher(tokenSequencePatterns);
  }

}
