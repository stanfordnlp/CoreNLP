package edu.stanford.nlp.pipeline;

import edu.stanford.nlp.ling.CoreAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.tokensregex.CoreMapExpressionExtractor;
import edu.stanford.nlp.ling.tokensregex.Env;
import edu.stanford.nlp.ling.tokensregex.EnvLookup;
import edu.stanford.nlp.ling.tokensregex.MatchedExpression;
import edu.stanford.nlp.ling.tokensregex.TokenSequencePattern;
import edu.stanford.nlp.util.*;
import edu.stanford.nlp.util.logging.Redwood;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.Set;

/**
 * Uses TokensRegex patterns to annotate tokens.
 *
 * <p>
 *   Configuration:
 *   <ul>
 *     <li>{@code rules} - Name of file containing extraction rules
 *        (see {@link CoreMapExpressionExtractor} and {@link edu.stanford.nlp.ling.tokensregex.SequenceMatchRules}</li>
 *   </ul>
 *   Other options (can be set in rules file using {@code options.xxx = ...})
 *   <ul>
 *     <li>{@code setTokenOffsets} - whether to explicit set the token offsets of individual tokens (needed to token sequence matches to work)</li>
 *     <li>{@code extractWithTokens} - whether to return unmatched tokens as well</li>
 *     <li>{@code flatten} - whether to flatten matched expressions into individual tokens</li>
 *     <li>{@code matchedExpressionsAnnotationKey} - Annotation key where matched expressions are stored as a list</li>
 *   </ul>
 * </p>
 * <p>Multiple {@code TokensRegexAnnotator} can be configured using the same properties file by specifying
 * difference prefix for the {@code TokensRegexAnnotator}</p>
 *
 * @author Angel Chang
 */
public class TokensRegexAnnotator implements Annotator {

  private final Env env;
  private final CoreMapExpressionExtractor<MatchedExpression> extractor;
  private final Options options = new Options();
  private final boolean verbose;


  // Make public so can be accessed and set via reflection
  public static class Options {
    public Class matchedExpressionsAnnotationKey;
    public boolean setTokenOffsets;
    public boolean extractWithTokens;
    public boolean flatten;
  }


  public TokensRegexAnnotator(String... files) {
    env = TokenSequencePattern.getNewEnv();
    extractor = CoreMapExpressionExtractor.createExtractorFromFiles(env, files);
    verbose = false;
  }

  public TokensRegexAnnotator(String name, Properties props) {
    String prefix = (name == null)? "": name + '.';
    String[] files  = PropertiesUtils.getStringArray(props, prefix + "rules");
    if (files.length == 0) {
      throw new RuntimeException("No rules specified for TokensRegexAnnotator " + name + ", check " + prefix + "rules property");
    }
    env = TokenSequencePattern.getNewEnv();
    env.bind("options", options);
    extractor = CoreMapExpressionExtractor.createExtractorFromFiles(env, files);
    verbose = PropertiesUtils.getBool(props, prefix + "verbose", false);
    options.setTokenOffsets = PropertiesUtils.getBool(props, prefix + "setTokenOffsets", options.setTokenOffsets);
    options.extractWithTokens = PropertiesUtils.getBool(props, prefix + "extractWithTokens", options.extractWithTokens);
    options.flatten = PropertiesUtils.getBool(props, prefix + "flatten", options.flatten);
    String matchedExpressionsAnnotationKeyName = props.getProperty(prefix + "matchedExpressionsAnnotationKey");
    if (matchedExpressionsAnnotationKeyName != null) {
      options.matchedExpressionsAnnotationKey = EnvLookup.lookupAnnotationKeyWithClassname(env, matchedExpressionsAnnotationKeyName);
      if (options.matchedExpressionsAnnotationKey == null) {
        String propName = prefix + "matchedExpressionsAnnotationKey";
        throw new RuntimeException("Cannot determine annotation key for " + propName + '=' + matchedExpressionsAnnotationKeyName);
      }
    }
  }

  public TokensRegexAnnotator(Properties props) {
    this(null, props);
  }


  private static void addTokenOffsets(CoreMap annotation) {
    // We are going to mark the token begin and token end for each token
    Integer startTokenOffset = annotation.get(CoreAnnotations.TokenBeginAnnotation.class);
    if (startTokenOffset == null) {
      startTokenOffset = 0;
    }
    //set token offsets
    int i = 0;
    for (CoreMap c:annotation.get(CoreAnnotations.TokensAnnotation.class)) {
      //set token begin
      c.set(CoreAnnotations.TokenBeginAnnotation.class, i+startTokenOffset);
      i++;
      //set token end
      c.set(CoreAnnotations.TokenEndAnnotation.class, i+startTokenOffset);
    }
  }

  private List<CoreMap> extract(CoreMap annotation) {
    List<CoreMap> cms;
    if (options.extractWithTokens) {
      cms = extractor.extractCoreMapsMergedWithTokens(annotation);
    } else {
      cms = extractor.extractCoreMaps(annotation);
    }
    if (options.flatten) {
      return extractor.flatten(cms);
    } else {
      return cms;
    }
  }

  @Override
  public void annotate(Annotation annotation) {
    if (verbose) {
      Redwood.log(Redwood.DBG, "Adding TokensRegexAnnotator annotation...");
    }

    if (options.setTokenOffsets) {
      addTokenOffsets(annotation);
    }
    List<CoreMap> allMatched;
    if (annotation.containsKey(CoreAnnotations.SentencesAnnotation.class)) {
      allMatched = new ArrayList<>();
      List<CoreMap> sentences = annotation.get(CoreAnnotations.SentencesAnnotation.class);
      for (CoreMap sentence : sentences) {
        List<CoreMap> matched = extract(sentence);
        if (matched != null && options.matchedExpressionsAnnotationKey != null) {
          allMatched.addAll(matched);
          sentence.set(options.matchedExpressionsAnnotationKey, matched);
          for (CoreMap cm:matched) {
            cm.set(CoreAnnotations.SentenceIndexAnnotation.class, sentence.get(CoreAnnotations.SentenceIndexAnnotation.class));
          }
        }
      }
    } else {
      allMatched = extract(annotation);
    }
    if (options.matchedExpressionsAnnotationKey != null) {
      annotation.set(options.matchedExpressionsAnnotationKey, allMatched);
    }

    if (verbose) {
      Redwood.log(Redwood.DBG, "done.");
    }
  }

  @Override
  public Set<Class<? extends CoreAnnotation>> requires() {
    return Collections.singleton(CoreAnnotations.TokensAnnotation.class);
  }

  @Override
  public Set<Class<? extends CoreAnnotation>> requirementsSatisfied() {
    // TODO: not sure what goes here
    return Collections.emptySet();
  }

}
