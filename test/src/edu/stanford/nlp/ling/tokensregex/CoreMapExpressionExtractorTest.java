package edu.stanford.nlp.ling.tokensregex;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import edu.stanford.nlp.util.CoreMap;

/**
 * Tests the stage options which control how overlapping matches are pruned
 * in {@link CoreMapExpressionExtractor}.
 */
public class CoreMapExpressionExtractorTest {

  private static StanfordCoreNLP pipeline;

  @BeforeClass
  public static void setUp() {
    Properties props = new Properties();
    props.setProperty("annotators", "tokenize,ssplit");
    pipeline = new StanfordCoreNLP(props);
  }

  private static final String KEEP_NESTED = "ENV.defaults[\"stage.keepNestedMatches\"] = TRUE\n";
  private static final String KEEP_OVERLAPPING = "ENV.defaults[\"stage.keepOverlappingMatches\"] = TRUE\n";

  private static final String BASIC_RULES =
    "{ name: \"city\",  pattern: ( /New/ /York/ ), result: \"CITY\" }\n" +
    "{ name: \"place\", pattern: ( /York/ /Times/ /Square/ ), result: \"PLACE\" }\n" +
    "{ name: \"paper\", pattern: ( /New/ /York/ /Times/ ), result: \"PAPER\" }\n";

  private static final String COMPOSITE_RULES = BASIC_RULES +
    "{ ruleType: \"composite\", name: \"read\", pattern: ( /read/ /the/ [ { word:/New York Times/ } ] ), result: \"READ\" }\n";

  /** A composite rule which matches its own output on every iteration */
  private static final String SELF_MATCHING_RULES =
    "{ ruleType: \"composite\", name: \"new\", pattern: ( [ { word:/New.*/ } ] ), result: \"NEW\" }\n";

  /**
   * Runs the rules over the text and returns each match as name:begin-end,
   * sorted by token offsets
   */
  private static List<String> extract(String rules, String text) throws Exception {
    Env env = TokenSequencePattern.getNewEnv();
    env.setDefaultNestedResultsAnnotationKey(CoreAnnotations.NumerizedTokensAnnotation.class);
    CoreMapExpressionExtractor<MatchedExpression> extractor = CoreMapExpressionExtractor.createExtractorFromString(env, rules);
    Annotation doc = new Annotation(text);
    pipeline.annotate(doc);
    CoreMap sentence = doc.get(CoreAnnotations.SentencesAnnotation.class).get(0);
    List<MatchedExpression> matches = extractor.extractExpressions(sentence);
    matches.sort(MatchedExpression.EXPR_TOKEN_OFFSET_COMPARATOR);
    List<String> results = new ArrayList<>();
    for (MatchedExpression match : matches) {
      results.add(match.getName() + ":" + match.getTokenOffsets().getBegin() + "-" + match.getTokenOffsets().getEnd());
    }
    return results;
  }

  @Test
  public void testDefaultPruning() throws Exception {
    Assert.assertEquals(Arrays.asList("paper:3-6"), extract(BASIC_RULES, "I read the New York Times today ."));
    Assert.assertEquals(Arrays.asList("place:4-7"), extract(BASIC_RULES, "I read the New York Times Square edition ."));
  }

  @Test
  public void testKeepNested() throws Exception {
    Assert.assertEquals(Arrays.asList("city:3-5", "paper:3-6"),
                        extract(KEEP_NESTED + BASIC_RULES, "I read the New York Times today ."));
    // city and paper both cross the preferred match, place, so they are still pruned
    Assert.assertEquals(Arrays.asList("place:4-7"),
                        extract(KEEP_NESTED + BASIC_RULES, "I read the New York Times Square edition ."));
  }

  @Test
  public void testKeepOverlapping() throws Exception {
    Assert.assertEquals(Arrays.asList("city:3-5", "paper:3-6", "place:4-7"),
                        extract(KEEP_OVERLAPPING + BASIC_RULES, "I read the New York Times Square edition ."));
  }

  @Test
  public void testKeepOverlappingFindAll() throws Exception {
    String rules = "{ name: \"aa\", pattern: ( /a/ /a/ ), result: \"AA\", matchFindType: \"FIND_ALL\" }\n";
    Assert.assertEquals(Arrays.asList("aa:0-2"), extract(rules, "a a a"));
    Assert.assertEquals(Arrays.asList("aa:0-2", "aa:1-3"), extract(KEEP_OVERLAPPING + rules, "a a a"));
  }

  /**
   * Composite rules should match against the same chunks whichever option is used,
   * so the composite match is found in each case
   */
  @Test
  public void testComposite() throws Exception {
    String text = "I read the New York Times today .";
    Assert.assertEquals(Arrays.asList("read:1-6"), extract(COMPOSITE_RULES, text));
    Assert.assertEquals(Arrays.asList("read:1-6", "city:3-5", "paper:3-6"), extract(KEEP_NESTED + COMPOSITE_RULES, text));
    Assert.assertEquals(Arrays.asList("read:1-6", "city:3-5", "paper:3-6"), extract(KEEP_OVERLAPPING + COMPOSITE_RULES, text));
  }

  /**
   * A composite rule which keeps matching the same tokens should not
   * produce one copy of the match per iteration
   */
  @Test
  public void testNoDuplicates() throws Exception {
    Assert.assertEquals(Arrays.asList("new:0-1"), extract(SELF_MATCHING_RULES, "New York"));
    Assert.assertEquals(Arrays.asList("new:0-1"), extract(KEEP_NESTED + SELF_MATCHING_RULES, "New York"));
    Assert.assertEquals(Arrays.asList("new:0-1"), extract(KEEP_OVERLAPPING + SELF_MATCHING_RULES, "New York"));
  }

}
