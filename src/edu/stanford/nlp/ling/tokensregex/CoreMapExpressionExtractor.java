package edu.stanford.nlp.ling.tokensregex;

import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.ling.CoreAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.tokensregex.parser.ParseException;
import edu.stanford.nlp.ling.tokensregex.parser.TokenSequenceParseException;
import edu.stanford.nlp.ling.tokensregex.parser.TokenSequenceParser;
import edu.stanford.nlp.ling.tokensregex.types.Expression;
import edu.stanford.nlp.ling.tokensregex.types.Tags;
import edu.stanford.nlp.ling.tokensregex.types.Value;
import edu.stanford.nlp.util.*;
import edu.stanford.nlp.util.logging.Redwood;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.*;
import java.util.function.Predicate;


/**
 * <p>Represents a list of assignment and extraction rules over sequence patterns.
 *    See {@link SequenceMatchRules} for the syntax of rules.
 * </p>
 *
 * <p>Assignment rules are used to assign a value to a variable for later use in
 * extraction rules or for expansions in patterns.</p>
 * <p>Extraction rules are used to extract text/tokens matching regular expressions.
 * Extraction rules are grouped into stages, with each stage consisting of the following:
 * <ol>
 *   <li>Matching of rules over <b>text</b> and <b>tokens</b>.  These rules are applied directly on the <b>text</b> and <b>tokens</b> fields of the {@code CoreMap}.</li>
 *   <li>Matching of <b>composite</b> rules.  Matched expression are merged, and composite rules
 *       are applied recursively until no more changes to the matched expressions are detected.</li>
 *   <li><b>Filtering</b> of an invalid expression.  In the final phase, a final filtering stage filters out invalid expressions.</li>
 * </ol>
 * The different stages are numbered and are applied in numeric order.
 * </p>
 *
 * @author Angel Chang
 * @see SequenceMatchRules
 */
public class CoreMapExpressionExtractor<T extends MatchedExpression>  {

  /** A logger for this class */
  private static final Redwood.RedwoodChannels log = Redwood.channels(CoreMapExpressionExtractor.class);

  private static boolean verbose = false;

  // TODO: Remove templating of MatchedExpressions<?>  (keep for now until TimeExpression rules can be decoupled)

  private final Env env;
  /* Keeps temporary tags created by extractor */
  private boolean keepTags = false;
  /* Collapses extraction rules - use with care */
  private boolean collapseExtractionRules = false;
  private final Class<CoreAnnotation<List<? extends CoreMap>>> tokensAnnotationKey;
  private final Map<Integer, Stage<T>> stages;

  /**
   * Describes one stage of extraction.
   * @param <T>
   */
  public static class Stage<T> {
    /** Whether to clear matched expressions from previous stages or not */
    boolean clearMatched = false;
    /**
     * Limit the number of iterations for which the composite rules are applied
     * (prevents badly formed rules from iterating forever)
     */
    int limitIters = 50;
    /**
     * Stage id (stages are applied in numeric order from low to high)
     */
    int stageId;
    /** Rules to extract matched  expressions directly from tokens */
    SequenceMatchRules.ExtractRule<CoreMap, T> basicExtractRule;
    /** Rules to extract composite expressions (grouped in stages) */
    SequenceMatchRules.ExtractRule<List<? extends CoreMap>, T> compositeExtractRule;
    /** Filtering rule */
    Predicate<T> filterRule;

    private static <I,O> SequenceMatchRules.ExtractRule<I,O> addRule(SequenceMatchRules.ExtractRule<I, O> origRule,
                                                                     SequenceMatchRules.ExtractRule<I, O> rule) {
      SequenceMatchRules.ListExtractRule<I,O> r;
      if (origRule instanceof SequenceMatchRules.ListExtractRule) {
        r = (SequenceMatchRules.ListExtractRule<I,O>) origRule;
      } else {
        r = new SequenceMatchRules.ListExtractRule<>();
        if (origRule != null)
        r.addRules(origRule);
      }
      r.addRules(rule);
      return r;
    }

    private void addCompositeRule(SequenceMatchRules.ExtractRule<List<? extends CoreMap>, T> rule) {
      compositeExtractRule = addRule(compositeExtractRule, rule);
    }

    private void addBasicRule(SequenceMatchRules.ExtractRule<CoreMap, T> rule) {
      basicExtractRule = addRule(basicExtractRule, rule);
    }

    private void addFilterRule(Predicate<T> rule) {
      Filters.DisjFilter<T> r;
      if (filterRule instanceof Filters.DisjFilter) {
        r = (Filters.DisjFilter<T>) filterRule;
        r.addFilter(rule);
      } else {
        if (filterRule == null) {
          r = new Filters.DisjFilter<>(rule);
        } else {
          r = new Filters.DisjFilter<>(filterRule, rule);
        }
        filterRule = r;
      }
    }
  }

  /**
   * Creates an empty instance with no rules.
   */
  public CoreMapExpressionExtractor() {
    this(null);
  }

  /**
   * Creates a default instance with the specified environment.
   *   (use the default tokens annotation key as specified in the environment)
   * @param env Environment to use for binding variables and applying rules
   */
  public CoreMapExpressionExtractor(Env env) {
    this.stages = new HashMap<>();//Generics.newHashMap();
    this.env = env;
    this.tokensAnnotationKey = EnvLookup.getDefaultTokensAnnotationKey(env);
    this.collapseExtractionRules = false;
    if (env != null) {
      this.collapseExtractionRules = Objects.equals((Boolean) env.get("collapseExtractionRules"), true);
      if (env.get("verbose") != null)
        verbose =  (env.get("verbose") != null) &&
                Objects.equals((Boolean) env.get("verbose"), true);
    }
  }

  /**
   * Creates an instance with the specified environment and list of rules
   * @param env Environment to use for binding variables and applying rules
   * @param rules List of rules for this extractor
   */
  public CoreMapExpressionExtractor(Env env, List<SequenceMatchRules.Rule> rules) {
    this(env);
    appendRules(rules);
  }

  /**
   * Add specified rules to this extractor.
   *
   * @param rules
   */
  public void appendRules(List<SequenceMatchRules.Rule> rules)
  {
    if (verbose)
      log.info("Read " + rules.size() + " rules");
    // Put rules into stages
    if (collapseExtractionRules) {
      rules = collapse(rules);
      if (verbose)
        log.info("Collapsing into " + rules.size() + " rules");
    }
    for (SequenceMatchRules.Rule r:rules) {
      if (r instanceof SequenceMatchRules.AssignmentRule) {
        // Nothing to do
        // Assignments are added to environment as they are parsed
        ((SequenceMatchRules.AssignmentRule) r).evaluate(env);
      } else if (r instanceof SequenceMatchRules.AnnotationExtractRule) {
        SequenceMatchRules.AnnotationExtractRule aer = (SequenceMatchRules.AnnotationExtractRule) r;
        Stage<T> stage = stages.get(aer.stage);
        if (stage == null) {
          stages.put(aer.stage, stage = new Stage<>());
          stage.stageId = aer.stage;
          Boolean clearMatched = (Boolean) env.getDefaults().get("stage.clearMatched");
          if (clearMatched != null) {
            stage.clearMatched = clearMatched;
          }
          Integer limitIters = (Integer) env.getDefaults().get("stage.limitIters");
          if (limitIters != null) {
            stage.limitIters = limitIters;
          }
        }
        if (aer.active) {
          if (SequenceMatchRules.FILTER_RULE_TYPE.equals(aer.ruleType)) {
            stage.addFilterRule(aer);
          } else {
            if (aer.isComposite) {
//            if (SequenceMatchRules.COMPOSITE_RULE_TYPE.equals(aer.ruleType)) {
              stage.addCompositeRule(aer);
            } else {
              stage.addBasicRule(aer);
            }
          }
        } else {
          log.debug("Ignoring inactive rule: " + aer.name); // used to be INFO but annoyed Chris/users
        }
      }
    }
  }

  private SequenceMatchRules.AnnotationExtractRule createMergedRule(SequenceMatchRules.AnnotationExtractRule aerTemplate, List<TokenSequencePattern> patterns) {
    return SequenceMatchRules.createMultiTokenPatternRule(env, aerTemplate, patterns);
  }

  private List<SequenceMatchRules.Rule> collapse(List<SequenceMatchRules.Rule> rules) {
    List<SequenceMatchRules.Rule> collapsed = new ArrayList<>();
    List<TokenSequencePattern> patterns = null;
    SequenceMatchRules.AnnotationExtractRule aerTemplate = null;
    for (SequenceMatchRules.Rule rule:rules) {
      boolean ruleHandled = false;
      if (rule instanceof SequenceMatchRules.AnnotationExtractRule) {
        SequenceMatchRules.AnnotationExtractRule aer = (SequenceMatchRules.AnnotationExtractRule) rule;
        if (aer.hasTokensRegexPattern()) {
          if (aerTemplate == null || aerTemplate.isMostlyCompatible(aer)) {
            if (aerTemplate == null) {
              aerTemplate = aer;
            }
            if (patterns == null) {
              patterns = new ArrayList<>();
            }
            patterns.add((TokenSequencePattern) aer.pattern);
            ruleHandled = true;
          }
        }
      }

      // Did we handle this rule?
      if (!ruleHandled) {
        if (aerTemplate != null) {
          SequenceMatchRules.AnnotationExtractRule merged = createMergedRule(aerTemplate, patterns);
          collapsed.add(merged);
          aerTemplate = null;
          patterns = null;
        }
        collapsed.add(rule);
      }
    }
    if (aerTemplate != null) {
      SequenceMatchRules.AnnotationExtractRule merged = createMergedRule(aerTemplate, patterns);
      collapsed.add(merged);
    }
    return collapsed;
  }

  public Env getEnv() {
    return env;
  }

  public void setExtractRules(SequenceMatchRules.ExtractRule<CoreMap, T> basicExtractRule,
                              SequenceMatchRules.ExtractRule<List<? extends CoreMap>, T> compositeExtractRule,
                              Predicate<T> filterRule)
  {
    Stage<T> stage = new Stage<>();
    stage.basicExtractRule = basicExtractRule;
    stage.compositeExtractRule = compositeExtractRule;
    stage.filterRule = filterRule;
    this.stages.clear();
    this.stages.put(1, stage);
  }

  /**
   * Creates an extractor using the specified environment, and reading the rules from the given filenames.
   * @param env
   * @param filenames
   * @throws RuntimeException
   */
  public static <M extends MatchedExpression> CoreMapExpressionExtractor<M> createExtractorFromFiles(Env env, String... filenames) throws RuntimeException {
    return createExtractorFromFiles(env, Arrays.asList(filenames));
  }

  /**
   * Creates an extractor using the specified environment, and reading the rules from the given filenames.
   * @param env
   * @param filenames
   * @throws RuntimeException
   */
  public static <M extends MatchedExpression> CoreMapExpressionExtractor<M> createExtractorFromFiles(Env env, List<String> filenames) throws RuntimeException {
    CoreMapExpressionExtractor<M> extractor = new CoreMapExpressionExtractor<>(env);
    for (String filename:filenames) {
      try {
        if (verbose)
          log.info("Reading TokensRegex rules from " + filename);
        BufferedReader br = IOUtils.readerFromString(filename);
        TokenSequenceParser parser = new TokenSequenceParser();
        parser.updateExpressionExtractor(extractor, br);
        IOUtils.closeIgnoringExceptions(br);
      } catch (Exception ex) {
        throw new RuntimeException("Error parsing file: " + filename, ex);
      }
    }
    return extractor;
  }

  /**
   * Creates an extractor using the specified environment, and reading the rules from the given filename.
   * @param env
   * @param filename
   * @throws RuntimeException
   */
  public static CoreMapExpressionExtractor createExtractorFromFile(Env env, String filename) throws RuntimeException {
    return createExtractorFromFiles(env, Collections.singletonList(filename));
  }

  /**
   * Creates an extractor using the specified environment, and reading the rules from the given string
   * @param env
   * @param str
   * @throws IOException, ParseException
   */
  public static CoreMapExpressionExtractor createExtractorFromString(Env env, String str) throws IOException, ParseException, TokenSequenceParseException {
    TokenSequenceParser parser = new TokenSequenceParser();
    CoreMapExpressionExtractor extractor = parser.getExpressionExtractor(env, new StringReader(str));
    return extractor;
  }

  public Value getValue(String varname)
  {
    Expression expr = (Expression) env.get(varname);
    if (expr != null) {
      return expr.evaluate(env);
    } else {
      throw new RuntimeException("Unable get expression for variable " + varname);
    }
  }

  private List<CoreMap> extractCoreMapsToList(List<CoreMap> res, CoreMap annotation) {
    List<T> exprs = extractExpressions(annotation);
    for (T expr : exprs) {
      res.add(expr.getAnnotation());
    }
    return res;
  }

  /**
   * Returns list of coremaps that matches the specified rules.
   *
   * @param annotation
   */
  public List<CoreMap> extractCoreMaps(CoreMap annotation) {
    List<CoreMap> res = new ArrayList<>();
    return extractCoreMapsToList(res, annotation);
  }

  /**
   * Returns list of merged tokens and original tokens.
   *
   * @param annotation
   */
  public List<CoreMap> extractCoreMapsMergedWithTokens(CoreMap annotation) {
    List<CoreMap> res = extractCoreMaps(annotation);
    Integer startTokenOffset = annotation.get(CoreAnnotations.TokenBeginAnnotation.class);
    if (startTokenOffset == null) {
      startTokenOffset = 0;
    }
    final Integer startTokenOffsetFinal = startTokenOffset;
    List<CoreMap> merged = CollectionUtils.mergeListWithSortedMatchedPreAggregated(
            annotation.get(tokensAnnotationKey), res, (CoreMap in) -> Interval.toInterval(in.get(CoreAnnotations.TokenBeginAnnotation.class) - startTokenOffsetFinal,
                    in.get(CoreAnnotations.TokenEndAnnotation.class) - startTokenOffsetFinal)
    );
    return merged;
  }

  public List<CoreMap> flatten(List<CoreMap> cms) {
    return flatten(cms, tokensAnnotationKey);
  }

  private static List<CoreMap> flatten(List<CoreMap> cms, Class key) {
    List<CoreMap> res = new ArrayList<>();
    for (CoreMap cm : cms) {
      if (cm.get(key) != null) {
        res.addAll( (List<CoreMap>) cm.get(key));
      } else {
        res.add(cm);
      }
    }
    return res;
  }

  private void cleanupTags(Collection objs, Map<Object, Boolean> cleaned) {
    for (Object obj:objs) {
      if (!cleaned.containsKey(obj)) {
        cleaned.put(obj, false);
        if (obj instanceof CoreMap) {
          cleanupTags((CoreMap) obj, cleaned);
        } else if (obj instanceof Collection) {
          cleanupTags((Collection) obj, cleaned);
        }
        cleaned.put(obj, true);
      }
    }
  }

  private void cleanupTags(CoreMap cm) {
    cleanupTags(cm, new IdentityHashMap<>());
  }

  private void cleanupTags(CoreMap cm, Map<Object, Boolean> cleaned) {
    cm.remove(Tags.TagsAnnotation.class);
    for (Class key:cm.keySet()) {
      Object obj = cm.get(key);
      if (!cleaned.containsKey(obj)) {
        cleaned.put(obj, false);
        if (obj instanceof CoreMap) {
          cleanupTags((CoreMap) obj, cleaned);
        } else if (obj instanceof Collection) {
          cleanupTags((Collection) obj, cleaned);
        }
        cleaned.put(obj, true);
      }
    }
  }

  private Pair<List<? extends CoreMap>, List<T>> applyCompositeRule(
          SequenceMatchRules.ExtractRule<List<? extends CoreMap>, T> compositeExtractRule,
          List<? extends CoreMap> merged,
          List<T> matchedExpressions, int limit) {
    // Apply higher order rules
    boolean done = false;
    // Limit of number of times rules are applied just in case
    int maxIters = limit;
    int iters = 0;
    while (! done) {
      List<T> newExprs = new ArrayList<>();
      boolean extracted = compositeExtractRule.extract(merged, newExprs);
      if (verbose && extracted) log.info("applyCompositeRule() extracting with " + compositeExtractRule + " from " + merged + " gives " + newExprs);
      if (extracted) {
        annotateExpressions(merged, newExprs);
        newExprs = MatchedExpression.removeNullValues(newExprs);
        if ( ! newExprs.isEmpty()) {
          newExprs = MatchedExpression.removeNested(newExprs);
          newExprs = MatchedExpression.removeOverlapping(newExprs);
          merged = MatchedExpression.replaceMerged(merged, newExprs);
          // Favor newly matched expressions over older ones
          newExprs.addAll(matchedExpressions);
          matchedExpressions = MatchedExpression.removeNested(newExprs);
          matchedExpressions = MatchedExpression.removeOverlapping(matchedExpressions);
        } else {
          extracted = false;
        }
      }
      done = ! extracted;
      iters++;
      if (maxIters > 0 && iters >= maxIters) {
        if (verbose) {
          log.warn("Aborting application of composite rules: Maximum iteration " + maxIters + " reached");
        }
        break;
      }
    }
    return new Pair<>(merged, matchedExpressions);
  }

  private static class CompositeMatchState<T> {
    List<? extends CoreMap> merged;
    List<T> matched;
    int iters;

    private CompositeMatchState(List<? extends CoreMap> merged, List<T> matched, int iters) {
      this.merged = merged;
      this.matched = matched;
      this.iters = iters;
    }
  }

  public List<T> extractExpressions(CoreMap annotation) {
    // Extract potential expressions
    List<T> matchedExpressions = new ArrayList<>();
    List<Integer> stageIds = new ArrayList<>(stages.keySet());
    Collections.sort(stageIds);
    for (int stageId : stageIds) {
      Stage<T> stage = stages.get(stageId);
      SequenceMatchRules.ExtractRule<CoreMap, T> basicExtractRule = stage.basicExtractRule;
      if (stage.clearMatched) {
        matchedExpressions.clear();
      }
      if (basicExtractRule != null) {
        basicExtractRule.extract(annotation, matchedExpressions);
        if (verbose && matchedExpressions != null) {
          log.info("extractExpressions() extracting with " + basicExtractRule + " from " + annotation + " gives " + matchedExpressions);
        }
        annotateExpressions(annotation, matchedExpressions);
        matchedExpressions = MatchedExpression.removeNullValues(matchedExpressions);
        matchedExpressions = MatchedExpression.removeNested(matchedExpressions);
        matchedExpressions = MatchedExpression.removeOverlapping(matchedExpressions);
      }

      List<? extends CoreMap> merged = MatchedExpression.replaceMergedUsingTokenOffsets(annotation.get(tokensAnnotationKey), matchedExpressions);
      SequenceMatchRules.ExtractRule<List<? extends CoreMap>, T> compositeExtractRule = stage.compositeExtractRule;
      if (compositeExtractRule != null) {
        Pair<List<? extends CoreMap>, List<T>> p = applyCompositeRule(
                compositeExtractRule, merged, matchedExpressions, stage.limitIters);
        merged = p.first();
        matchedExpressions = p.second();
      }
      matchedExpressions = filterInvalidExpressions(stage.filterRule, matchedExpressions);
    }
    Collections.sort(matchedExpressions, MatchedExpression.EXPR_TOKEN_OFFSETS_NESTED_FIRST_COMPARATOR);
    if (!keepTags) {
      cleanupTags(annotation);
    }
    return matchedExpressions;
  }

  private void annotateExpressions(CoreMap annotation, List<T> expressions) {
    // TODO: Logging can be excessive
    List<T> toDiscard = new ArrayList<>();
    for (T te : expressions) {
      // Add attributes and all
      if (te.annotation == null) {
        try {
          boolean extractOkay = te.extractAnnotation(env, annotation);
          if (verbose && extractOkay) {
            log.info("annotateExpressions() matched " + te + " from " + annotation);
          }
          if (!extractOkay) {
            // Things didn't turn out so well
            toDiscard.add(te);
            log.warn("Error extracting annotation from " + te /*+ ", " + te.getExtractErrorMessage() */);
          }
        } catch (Exception ex) {
          if (verbose) {
            log.warn("Error extracting annotation from " + te);
            log.warn(ex);
          }
        }
      }
    }
    expressions.removeAll(toDiscard);
  }

  private void annotateExpressions(List<? extends CoreMap> chunks, List<T> expressions) {
    // TODO: Logging can be excessive
    List<T> toDiscard = new ArrayList<>();
    for (T te : expressions) {
      // Add attributes and all
      try {
        boolean extractOkay = te.extractAnnotation(env, chunks);
        if (verbose && extractOkay) {
          log.info("annotateExpressions() matched " + te + " from " + chunks);
        }
        if (!extractOkay) {
          // Things didn't turn out so well
          toDiscard.add(te);
          log.warn("Error extracting annotation from " + te /*+ ", " + te.getExtractErrorMessage() */);
        }
      } catch (Exception ex) {
        if (verbose) {
          log.warn("Error extracting annotation from " + te);
          log.warn(ex);
        }
      }
    }
    expressions.removeAll(toDiscard);
  }

  private List<T> filterInvalidExpressions(Predicate<T> filterRule, List<T> expressions) {
    if (filterRule == null) return expressions;
    if (expressions.isEmpty()) return expressions;
    int nfiltered = 0;
    List<T> kept = new ArrayList<>(expressions.size());   // Approximate size
    for (T expr : expressions) {
      if (!filterRule.test(expr)) {
        kept.add(expr);
      } else {
        nfiltered++;
//        logger.warning("Filtering out " + expr.getText());
      }
    }
    if (nfiltered > 0 && verbose) {
      log.debug("Filtered " + nfiltered);
    }
    return kept;
  }

  /**
   * Keeps the temporary tags on the sentence after extraction has finished.
   * This can have potentially unexpected results if you run the same sentence through multiple extractors;
   * but, it makes the extraction process 20+% faster.
   *
   * @return This object
   */
  public CoreMapExpressionExtractor keepTemporaryTags() {
    this.keepTags = true;
    return this;
  }

  public static void setVerbose(boolean v) {
    verbose = v;
  }

}
