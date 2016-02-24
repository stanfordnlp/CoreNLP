package edu.stanford.nlp.ie;

import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.ling.CoreAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.tokensregex.CoreMapExpressionExtractor;
import edu.stanford.nlp.ling.tokensregex.Env;
import edu.stanford.nlp.ling.tokensregex.MatchedExpression;
import edu.stanford.nlp.ling.tokensregex.TokenSequencePattern;
import edu.stanford.nlp.simple.Sentence;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.Pair;
import edu.stanford.nlp.util.logging.Redwood;

import java.io.File;
import java.util.*;

/**
 * A tokensregex extractor for KBP.
 *
 * IMPORTANT: Don't rename this class without updating the rules defs file.
 *
 * @author Gabor Angeli
 */
public class KBPTokensregexExtractor implements KBPRelationExtractor {
  protected final Redwood.RedwoodChannels logger = Redwood.channels(KBPTokensregexExtractor.class);

  private final Map<RelationType, CoreMapExpressionExtractor> rules = new HashMap<>();

  /**
   * IMPORTANT: Don't rename this class without updating the rules defs file.
   */
  public static class Subject implements CoreAnnotation<String> {
    public Class<String> getType() { return String.class; }
  }

  /**
   * IMPORTANT: Don't rename this class without updating the rules defs file.
   */
  public static class Object implements CoreAnnotation<String> {
    public Class<String> getType() { return String.class; }
  }

  public KBPTokensregexExtractor(String tokensregexDir) {
    logger.log("Creating TokensRegexExtractor");
    // Create extractors
    for (RelationType rel : RelationType.values()) {
      String path = tokensregexDir + File.separator + rel.canonicalName.replaceAll("/", "SLASH") + ".rules";
      if (IOUtils.existsInClasspathOrFileSystem(path)) {
        List<String> listFiles = new ArrayList<>();
        listFiles.add(tokensregexDir + File.separator + "defs.rules");
        listFiles.add(path);
        logger.log("Rule files for relation " + rel + " is " + path);
        Env env = TokenSequencePattern.getNewEnv();
        env.bind("collapseExtractionRules", true);
        CoreMapExpressionExtractor extr = CoreMapExpressionExtractor.createExtractorFromFiles(env, listFiles).keepTemporaryTags();
        rules.put(rel, extr);
      }
    }
  }


  @Override
  public Pair<String, Double> classify(KBPStatisticalExtractor.FeaturizerInput input) {

    // Annotate Sentence
    CoreMap sentenceAsMap = input.sentence.asCoreMap(Sentence::nerTags);
    List<CoreLabel> tokens = sentenceAsMap.get(CoreAnnotations.TokensAnnotation.class);
    // Annotate where the subject is
    for (int i : input.subjectSpan) {
      tokens.get(i).set(Subject.class, "true");
      if ("O".equals(tokens.get(i).ner())) {
        tokens.get(i).setNER(input.subjectType.name);
      }
    }

    // Annotate where the object is
    for (int i : input.objectSpan) {
      tokens.get(i).set(Object.class, "true");
      if ("O".equals(tokens.get(i).ner())) {
        tokens.get(i).setNER(input.objectType.name);
      }
    }

    // Run Rules
    for (RelationType rel : RelationType.values()) {
      if (rules.containsKey(rel) &&
          rel.entityType == input.subjectType &&
          rel.validNamedEntityLabels.contains(input.objectType)) {
        CoreMapExpressionExtractor extractor = rules.get(rel);

        @SuppressWarnings("unchecked")
        List<MatchedExpression> extractions = extractor.extractExpressions(sentenceAsMap);
        if (extractions != null && extractions.size() > 0) {
          MatchedExpression best = MatchedExpression.getBestMatched(extractions, MatchedExpression.EXPR_WEIGHT_SCORER);
          // Un-Annotate Sentence
          for (CoreLabel token : tokens) {
            token.remove(Subject.class);
            token.remove(Object.class);
          }
          return Pair.makePair(rel.canonicalName, best.getWeight());
        }
      }
    }

    // Un-Annotate Sentence
    for (CoreLabel token : tokens) {
      token.remove(Subject.class);
      token.remove(Object.class);
    }
    return Pair.makePair(NO_RELATION, 1.0);
  }

}
