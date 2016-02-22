package edu.stanford.nlp.ie;

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
import java.io.FilenameFilter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * A tokensregex extractor for KBP.
 *
 * @author Gabor Angeli
 */
public class KBPTokensregexExtractor implements KBPRelationExtractor {
  protected final Redwood.RedwoodChannels logger = Redwood.channels(KBPTokensregexExtractor.class);

  private final Map<RelationType, CoreMapExpressionExtractor> rules = new HashMap<>();

  public static class KBPEntity implements CoreAnnotation<String> {
    public Class<String> getType() { return String.class; }
  }

  public static class KBPSlotFill implements CoreAnnotation<String> {
    public Class<String> getType() { return String.class; }
  }

  public KBPTokensregexExtractor(String tokensregexDir) {
    logger.log("Creating TokensRegexExtractor");
    // Create extractors
    for (RelationType rel : RelationType.values()) {
      FilenameFilter filter = (dir, name) -> name.matches(rel.canonicalName.replaceAll("/", "SLASH") + ".*.rules");

      File[] ruleFiles = new File(tokensregexDir).listFiles(filter);

      if(ruleFiles != null && ruleFiles.length > 0){
        List<String> listfiles = new ArrayList<>();
        listfiles.add(tokensregexDir + File.separator + "defs.rules");
        listfiles.addAll(Arrays.asList(ruleFiles).stream().map(File::getAbsolutePath).collect(Collectors.toList()));
        logger.log("Rule files for relation " + rel + " are " + listfiles);
        Env env = TokenSequencePattern.getNewEnv();
        env.bind("collapseExtractionRules", true);
        CoreMapExpressionExtractor extr = CoreMapExpressionExtractor.createExtractorFromFiles(env, listfiles).keepTemporaryTags();
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
      tokens.get(i).set(KBPEntity.class, "true");
      if ("O".equals(tokens.get(i).ner())) {
        tokens.get(i).setNER(input.subjectType.name);
      }
    }

    // Annotate where the object is
    for (int i : input.objectSpan) {
      tokens.get(i).set(KBPSlotFill.class, "true");
      if ("O".equals(tokens.get(i).ner())) {
        tokens.get(i).setNER(input.objectType.name);
      }
    }

    // Run Rules
    for (RelationType rel : RelationType.values()) {
      if (rules.containsKey(rel) && rel.entityType == input.subjectType &&
          rel.validNamedEntityLabels.contains(input.objectType)) {
        CoreMapExpressionExtractor extractor = rules.get(rel);

        @SuppressWarnings("unchecked")
        List<MatchedExpression> extractions = extractor.extractExpressions(sentenceAsMap);
        if (extractions != null && extractions.size() > 0) {
          MatchedExpression best = MatchedExpression.getBestMatched(extractions, MatchedExpression.EXPR_WEIGHT_SCORER);
          // Un-Annotate Sentence
          for (CoreLabel token : tokens) {
            token.remove(KBPEntity.class);
            token.remove(KBPSlotFill.class);
          }
          return Pair.makePair(rel.canonicalName, best.getWeight());
        }
      }
    }

    // Un-Annotate Sentence
    for (CoreLabel token : tokens) {
      token.remove(KBPEntity.class);
      token.remove(KBPSlotFill.class);
    }
    return Pair.makePair(NO_RELATION, 1.0);
  }

}
