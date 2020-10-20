package edu.stanford.nlp.ling.tokensregex;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Pattern;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.AnnotationPipeline;
import edu.stanford.nlp.pipeline.NumberAnnotator;
import edu.stanford.nlp.pipeline.POSTaggerAnnotator;
import edu.stanford.nlp.pipeline.TokenizerAnnotator;
import edu.stanford.nlp.pipeline.WordsToSentencesAnnotator;
import edu.stanford.nlp.process.CoreLabelTokenFactory;
import edu.stanford.nlp.process.PTBTokenizer;
import edu.stanford.nlp.process.TokenizerFactory;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.Pair;
import edu.stanford.nlp.util.Timing;

public class TokenSequenceMatcherITest {

  private static AnnotationPipeline pipeline = null;

  @Before
  public void setUp() throws Exception {
    synchronized(TokenSequenceMatcherITest.class) {
      if (pipeline == null) {
        pipeline = new AnnotationPipeline();
        pipeline.addAnnotator(new TokenizerAnnotator(false, "en", "invertible,splitHyphenated=false"));
        pipeline.addAnnotator(new WordsToSentencesAnnotator(false));
        pipeline.addAnnotator(new POSTaggerAnnotator(false));
        pipeline.addAnnotator(new NumberAnnotator(false, false));
//        pipeline.addAnnotator(new QuantifiableEntityNormalizingAnnotator(false));
      }
    }
  }

  private static CoreMap createDocument(String text) {
    Annotation annotation = new Annotation(text);
    pipeline.annotate(annotation);
    return annotation;
  }

  private static SequencePattern.PatternExpr getSequencePatternExpr(String... textRegex) {
    List<SequencePattern.PatternExpr> patterns = new ArrayList<SequencePattern.PatternExpr>(textRegex.length);
    for (String s:textRegex) {
      patterns.add(new SequencePattern.NodePatternExpr(CoreMapNodePattern.valueOf(s)));
    }
    return new SequencePattern.SequencePatternExpr(patterns);
  }

  private static SequencePattern.PatternExpr getOrPatternExpr(Pair<String,Object>... textRegex) {
    List<SequencePattern.PatternExpr> patterns = new ArrayList<SequencePattern.PatternExpr>(textRegex.length);
    for (Pair<String,Object> p:textRegex) {
      SequencePattern.PatternExpr pe = new SequencePattern.NodePatternExpr(CoreMapNodePattern.valueOf(p.first()));
      if (p.second() != null) {
        pe = new SequencePattern.ValuePatternExpr(pe, p.second());
      }
      patterns.add(pe);
    }
    return new SequencePattern.OrPatternExpr(patterns);
  }

  private static SequencePattern.PatternExpr getNodePatternExpr(String textRegex) {
    return new SequencePattern.NodePatternExpr(CoreMapNodePattern.valueOf(textRegex));
  }

  private static final String testText = "the number were one, two and fifty.";

  @Test
  public void testTokenSequenceMatcherValue() throws IOException {
    CoreMap doc = createDocument(testText);

    // Test simple sequence with value
    TokenSequencePattern p = TokenSequencePattern.compile(getOrPatternExpr(
            new Pair<String,Object>("one", 1), new Pair<String,Object>("two", null), new Pair<String,Object>("fifty", 50)));
    TokenSequenceMatcher m = p.getMatcher(doc.get(CoreAnnotations.TokensAnnotation.class));

    boolean match = m.find();
    assertTrue(match);
    assertEquals("one", m.group());
    assertEquals(1, m.groupValue());

    match = m.find();
    assertTrue(match);
    assertEquals("two", m.group());
    assertNull(m.groupValue());

    match = m.find();
    assertTrue(match);
    assertEquals("fifty", m.group());
    assertEquals(50, m.groupValue());

    match = m.find();
    assertFalse(match);
  }

  @Test
  public void testTokenSequenceMatcherBeginEnd() throws IOException {
    CoreMap doc = createDocument(testText);

    // Test simple sequence with begin sequence matching
    TokenSequencePattern p = TokenSequencePattern.compile("^ [] []");
    TokenSequenceMatcher m = p.getMatcher(doc.get(CoreAnnotations.TokensAnnotation.class));

    boolean match = m.find();
    assertTrue(match);
    assertEquals("the number", m.group());

    match = m.find();
    assertFalse(match);

    // Test simple sequence with end sequence matching
    p = TokenSequencePattern.compile("[] [] $");
    m = p.getMatcher(doc.get(CoreAnnotations.TokensAnnotation.class));

    match = m.find();
    assertTrue(match);
    assertEquals("fifty.", m.group());

    match = m.find();
    assertFalse(match);

    // Test simple sequence with begin and end sequence matching
    p = TokenSequencePattern.compile("^ [] [] $");
    m = p.getMatcher(doc.get(CoreAnnotations.TokensAnnotation.class));

    match = m.find();
    assertFalse(match);

    // Test simple sequence with ^$ in a string regular expression
    p = TokenSequencePattern.compile("/^number$/");
    m = p.getMatcher(doc.get(CoreAnnotations.TokensAnnotation.class));

    match = m.find();
    assertTrue(match);
    assertEquals("number", m.group());

    match = m.find();
    assertFalse(match);
  }

  private static final String testText1 = "Mellitus was the first Bishop of London, the third Archbishop of Canterbury, and a member of the Gregorian mission  sent to England to convert the Anglo-Saxons. He arrived in 601 AD, and was consecrated as Bishop of London in 604.";

  @Test
  public void testTokenSequenceMatcher1() throws IOException {
    CoreMap doc = createDocument(testText1);

    // Test simple sequence
    TokenSequencePattern p = TokenSequencePattern.compile(getSequencePatternExpr("Archbishop", "of", "Canterbury"));
    TokenSequenceMatcher m = p.getMatcher(doc.get(CoreAnnotations.TokensAnnotation.class));
    boolean match = m.find();
    assertTrue(match);
    assertEquals("Archbishop of Canterbury", m.group());
    match = m.find();
    assertFalse(match);

    m.reset();
    match = m.find();
    assertTrue(match);
    assertEquals("Archbishop of Canterbury", m.group());

    m.reset();
    match = m.matches();
    assertFalse(match);

    // Test sequence with or
    p = TokenSequencePattern.compile(
            new SequencePattern.OrPatternExpr(
                    getSequencePatternExpr("Archbishop", "of", "Canterbury"),
                    getSequencePatternExpr("Bishop", "of", "London")
            ));
    m = p.getMatcher(doc.get(CoreAnnotations.TokensAnnotation.class));
    match = m.find();
    assertTrue(match);
    assertEquals(0, m.groupCount());
    assertEquals("Bishop of London", m.group());
    match = m.find();
    assertTrue(match);
    assertEquals(0, m.groupCount());
    assertEquals("Archbishop of Canterbury", m.group());
    match = m.find();
    assertTrue(match);
    assertEquals(0, m.groupCount());
    assertEquals("Bishop of London", m.group());
    match = m.find();
    assertFalse(match);

    p = TokenSequencePattern.compile(
              new SequencePattern.SequencePatternExpr(
                    SequencePattern.SEQ_BEGIN_PATTERN_EXPR,
                    getSequencePatternExpr("Archbishop", "of", "Canterbury")
            ));
    m = p.getMatcher(doc.get(CoreAnnotations.TokensAnnotation.class));
    match = m.find();
    assertFalse(match);

    p = TokenSequencePattern.compile(
            new SequencePattern.SequencePatternExpr(
                    SequencePattern.SEQ_BEGIN_PATTERN_EXPR,
                    getSequencePatternExpr("Mellitus", "was", "the")
            ));
    m = p.getMatcher(doc.get(CoreAnnotations.TokensAnnotation.class));
    match = m.find();
    assertTrue(match);
    assertEquals(0, m.groupCount());
    assertEquals("Mellitus was the", m.group());
    match = m.find();
    assertFalse(match);


    p = TokenSequencePattern.compile(
            new SequencePattern.SequencePatternExpr(
                    getSequencePatternExpr("Archbishop", "of", "Canterbury"),
                    SequencePattern.SEQ_END_PATTERN_EXPR
                    ));
    m = p.getMatcher(doc.get(CoreAnnotations.TokensAnnotation.class));
    match = m.find();
    assertFalse(match);

    p = TokenSequencePattern.compile(
            new SequencePattern.SequencePatternExpr(
                    getSequencePatternExpr("London", "in", "604", "."),
                    SequencePattern.SEQ_END_PATTERN_EXPR
                    ));
    m = p.getMatcher(doc.get(CoreAnnotations.TokensAnnotation.class));
    match = m.find();
    assertTrue(match);
    assertEquals(0, m.groupCount());
    assertEquals("London in 604.", m.group());
    match = m.find();
    assertFalse(match);
  }

  @Test
  public void testTokenSequenceMatcher2() throws IOException {
    CoreMap doc = createDocument(testText1);
    TokenSequencePattern p = TokenSequencePattern.compile(
                    getSequencePatternExpr(".*", ".*", "of", ".*"));

    TokenSequenceMatcher m = p.getMatcher(doc.get(CoreAnnotations.TokensAnnotation.class));
    boolean match = m.find();
    assertTrue(match);
    assertEquals(0, m.groupCount());
    assertEquals("first Bishop of London", m.group());
    match = m.find();
    assertTrue(match);
    assertEquals(0, m.groupCount());
    assertEquals("third Archbishop of Canterbury", m.group());
    match = m.find();
    assertTrue(match);
    assertEquals(0, m.groupCount());
    assertEquals("a member of the", m.group());
    match = m.find();
    assertTrue(match);
    assertEquals(0, m.groupCount());
    assertEquals("as Bishop of London", m.group());
    match = m.find();
    assertFalse(match);

    // Test sequence with groups
    p = TokenSequencePattern.compile(
                    new SequencePattern.SequencePatternExpr(
                      new SequencePattern.GroupPatternExpr(
                            getSequencePatternExpr(".*", ".*")),
                      getNodePatternExpr("of"),
                      new SequencePattern.GroupPatternExpr(
                            getSequencePatternExpr(".*"))));

    m = p.getMatcher(doc.get(CoreAnnotations.TokensAnnotation.class));
    match = m.find();
    assertTrue(match);
    assertEquals(2, m.groupCount());
    assertEquals("first Bishop of London", m.group());
    assertEquals("first Bishop", m.group(1));
    assertEquals("London", m.group(2));
    match = m.find();
    assertTrue(match);
    assertEquals(2, m.groupCount());
    assertEquals("third Archbishop of Canterbury", m.group());
    assertEquals("third Archbishop", m.group(1));
    assertEquals("Canterbury", m.group(2));
    match = m.find();
    assertTrue(match);
    assertEquals(2, m.groupCount());
    assertEquals("a member of the", m.group());
    assertEquals("a member", m.group(1));
    assertEquals("the", m.group(2));
    match = m.find();
    assertTrue(match);
    assertEquals(2, m.groupCount());
    assertEquals("as Bishop of London", m.group());
    assertEquals("as Bishop", m.group(1));
    assertEquals("London", m.group(2));
    match = m.find();
    assertFalse(match);

  }

  @Test
  public void testTokenSequenceMatcher3() throws IOException {
    CoreMap doc = createDocument(testText1);

    // Test sequence with groups
    TokenSequencePattern p = TokenSequencePattern.compile(
        new SequencePattern.SequencePatternExpr(
            new SequencePattern.GroupPatternExpr(
                new SequencePattern.RepeatPatternExpr(
                    getSequencePatternExpr("[A-Za-z]+"), 1, 2)),
            getNodePatternExpr("of"),
            new SequencePattern.GroupPatternExpr(
                new SequencePattern.RepeatPatternExpr(
                    getSequencePatternExpr("[A-Za-z]+"), 1, 3))));

    TokenSequenceMatcher m = p.getMatcher(doc.get(CoreAnnotations.TokensAnnotation.class));
    boolean match = m.find();
    assertTrue(match);
    assertEquals(2, m.groupCount());
    assertEquals("first Bishop of London", m.group());
    assertEquals("first Bishop", m.group(1));
    assertEquals("London", m.group(2));
    match = m.find();
    assertTrue(match);
    assertEquals(2, m.groupCount());
    assertEquals("third Archbishop of Canterbury", m.group());
    assertEquals("third Archbishop", m.group(1));
    assertEquals("Canterbury", m.group(2));
    match = m.find();
    assertTrue(match);
    assertEquals(2, m.groupCount());
    assertEquals("a member of the Gregorian mission", m.group());
    assertEquals("a member", m.group(1));
    assertEquals("the Gregorian mission", m.group(2));
    match = m.find();
    assertTrue(match);
    assertEquals(2, m.groupCount());
    assertEquals("as Bishop of London in", m.group());
    assertEquals("as Bishop", m.group(1));
    assertEquals("London in", m.group(2));
    match = m.find();
    assertFalse(match);

    p = TokenSequencePattern.compile(
        new SequencePattern.SequencePatternExpr(
            new SequencePattern.GroupPatternExpr(
                new SequencePattern.RepeatPatternExpr(
                    getNodePatternExpr("[A-Za-z]+"), 2, 2)),
            getNodePatternExpr("of"),
            new SequencePattern.GroupPatternExpr(
                new SequencePattern.RepeatPatternExpr(
                    getNodePatternExpr("[A-Za-z]+"), 1, 3, false))));

    m = p.getMatcher(doc.get(CoreAnnotations.TokensAnnotation.class));
    match = m.find();
    assertTrue(match);
    assertEquals(2, m.groupCount());
    assertEquals("first Bishop of London", m.group());
    assertEquals("first Bishop", m.group(1));
    assertEquals("London", m.group(2));
    match = m.find();
    assertTrue(match);
    assertEquals(2, m.groupCount());
    assertEquals("third Archbishop of Canterbury", m.group());
    assertEquals("third Archbishop", m.group(1));
    assertEquals("Canterbury", m.group(2));
    match = m.find();
    assertTrue(match);
    assertEquals(2, m.groupCount());
    assertEquals("a member of the", m.group());
    assertEquals("a member", m.group(1));
    assertEquals("the", m.group(2));
    match = m.find();
    assertTrue(match);
    assertEquals(2, m.groupCount());
    assertEquals("as Bishop of London", m.group());
    assertEquals("as Bishop", m.group(1));
    assertEquals("London", m.group(2));
    match = m.find();
    assertFalse(match);
  }

  @Test
  public void testTokenSequenceMatcherConj() throws IOException {
    CoreMap doc = createDocument(testText1);
    TokenSequencePattern p = TokenSequencePattern.compile(
                  new SequencePattern.AndPatternExpr(
                    new SequencePattern.SequencePatternExpr(
                      new SequencePattern.GroupPatternExpr(
                            new SequencePattern.RepeatPatternExpr(
                                    getNodePatternExpr("[A-Za-z]+"), 2, 2)),
                      getNodePatternExpr("of"),
                      new SequencePattern.GroupPatternExpr(
                            new SequencePattern.RepeatPatternExpr(
                                    getNodePatternExpr("[A-Za-z]+"), 1, 3, false))),
                    new SequencePattern.SequencePatternExpr(
                      new SequencePattern.GroupPatternExpr(
                        new SequencePattern.RepeatPatternExpr(
                            getNodePatternExpr(".*"), 0, -1)),
                      getNodePatternExpr("Bishop"),
                      new SequencePattern.RepeatPatternExpr(
                            getNodePatternExpr(".*"), 0, -1)
                    )));

    TokenSequenceMatcher m = p.getMatcher(doc.get(CoreAnnotations.TokensAnnotation.class));
    boolean match = m.find();
    assertTrue(match);
    assertEquals(3, m.groupCount());
    assertEquals("first Bishop of London", m.group());
    assertEquals("first Bishop", m.group(1));
    assertEquals("London", m.group(2));
    assertEquals("first", m.group(3));
    match = m.find();
    assertTrue(match);
    assertEquals(3, m.groupCount());
    // TODO: This conjunction has both a greedy and nongreedy pattern
    //  - the greedy will try to match as much as possible
    //  - while the non greedy will try to match less
    //  - currently the greedy overrides the nongreedy so we get an additional in...
    assertEquals("as Bishop of London in", m.group());
    assertEquals("as Bishop", m.group(1));
    assertEquals("London in", m.group(2));
    assertEquals("as", m.group(3));
    match = m.find();
    assertFalse(match);


    // Same as before, but both non-greedy now...
    p = TokenSequencePattern.compile(
                  new SequencePattern.AndPatternExpr(
                    new SequencePattern.SequencePatternExpr(
                      new SequencePattern.GroupPatternExpr(
                            new SequencePattern.RepeatPatternExpr(
                                    getNodePatternExpr("[A-Za-z]+"), 2, 2)),
                      getNodePatternExpr("of"),
                      new SequencePattern.GroupPatternExpr(
                            new SequencePattern.RepeatPatternExpr(
                                    getNodePatternExpr("[A-Za-z]+"), 1, 3, false))),
                    new SequencePattern.SequencePatternExpr(
                      new SequencePattern.GroupPatternExpr(
                        new SequencePattern.RepeatPatternExpr(
                            getNodePatternExpr(".*"), 0, -1)),
                      getNodePatternExpr("Bishop"),
                      new SequencePattern.RepeatPatternExpr(
                            getNodePatternExpr(".*"), 0, -1, false)
                    )));

    m = p.getMatcher(doc.get(CoreAnnotations.TokensAnnotation.class));
    match = m.find();
    assertTrue(match);
    assertEquals(3, m.groupCount());
    assertEquals("first Bishop of London", m.group());
    assertEquals("first Bishop", m.group(1));
    assertEquals("London", m.group(2));
    assertEquals("first", m.group(3));
    match = m.find();
    assertTrue(match);
    assertEquals(3, m.groupCount());
    assertEquals("as Bishop of London", m.group());
    assertEquals("as Bishop", m.group(1));
    assertEquals("London", m.group(2));
    assertEquals("as", m.group(3));
    match = m.find();
    assertFalse(match);


    // Same as before, but compiled from string
    p = TokenSequencePattern.compile(
            "(?: (/[A-Za-z]+/{2,2}) /of/ (/[A-Za-z]+/{1,3}?) ) & (?: (/.*/*) /Bishop/ /.*/*? )");

    m = p.getMatcher(doc.get(CoreAnnotations.TokensAnnotation.class));
    match = m.find();
    assertTrue(match);
    assertEquals(3, m.groupCount());
    assertEquals("first Bishop of London", m.group());
    assertEquals("first Bishop", m.group(1));
    assertEquals("London", m.group(2));
    assertEquals("first", m.group(3));
    match = m.find();
    assertTrue(match);
    assertEquals(3, m.groupCount());
    assertEquals("as Bishop of London", m.group());
    assertEquals("as Bishop", m.group(1));
    assertEquals("London", m.group(2));
    assertEquals("as", m.group(3));
    match = m.find();
    assertFalse(match);
  }

  @Test
  public void testTokenSequenceMatcherConj2() throws IOException {
    String content = "The cat is sleeping on the floor.";
    String greedyPattern = "(?: ([]* cat []*) & ([]* sleeping []*))";

    TokenizerFactory tf = PTBTokenizer.factory(new CoreLabelTokenFactory(), "");
    List<CoreLabel> tokens = tf.getTokenizer(new StringReader(content)).tokenize();
    TokenSequencePattern seqPattern = TokenSequencePattern.compile(greedyPattern);
    TokenSequenceMatcher matcher = seqPattern.getMatcher(tokens);

    boolean entireMatch = matcher.matches();
    assertTrue(entireMatch);

    boolean match = matcher.find();
    assertTrue(match);
    assertEquals("The cat is sleeping on the floor.", matcher.group());

    String reluctantPattern = "(?: ([]*? cat []*?) & ([]*? sleeping []*?))";
    TokenSequencePattern seqPattern2 = TokenSequencePattern.compile(reluctantPattern);
    TokenSequenceMatcher matcher2 = seqPattern2.getMatcher(tokens);

    match = matcher2.find();
    assertTrue(match);
    assertEquals("The cat is sleeping", matcher2.group());
  }

  @Test
  public void testTokenSequenceMatcherConjAll() throws IOException {
    CoreMap doc = createDocument(testText1);
    TokenSequencePattern p = TokenSequencePattern.compile(
            "(?: (/[A-Za-z]+/{1,2}) /of/ (/[A-Za-z]+/{1,3}?) ) & (?: (/.*/*) /Bishop/ /.*/*? )");

    TokenSequenceMatcher m = p.getMatcher(doc.get(CoreAnnotations.TokensAnnotation.class));
    m.setFindType(SequenceMatcher.FindType.FIND_ALL);
    // Test finding of ALL matching sequences with conjunctions
    // todo: Not all sequences are found for some reason - missing sequences starting with just Bishop....
    boolean match = m.find();
    assertTrue(match);
    assertEquals(3, m.groupCount());
    assertEquals("first Bishop of London", m.group());
    assertEquals("first Bishop", m.group(1));
    assertEquals("London", m.group(2));
    assertEquals("first", m.group(3));
    match = m.find();
    assertTrue(match);
    assertEquals(3, m.groupCount());
    assertEquals("Bishop of London", m.group());
    assertEquals("Bishop", m.group(1));
    assertEquals("London", m.group(2));
    assertEquals("", m.group(3));
    match = m.find();
    assertTrue(match);
    assertEquals(3, m.groupCount());
    assertEquals("as Bishop of London", m.group());
    assertEquals("as Bishop", m.group(1));
    assertEquals("London", m.group(2));
    assertEquals("as", m.group(3));
    match = m.find();
    assertTrue(match);
    assertEquals(3, m.groupCount());
    assertEquals("as Bishop of London in", m.group());
    assertEquals("as Bishop", m.group(1));
    assertEquals("London in", m.group(2));
    assertEquals("as", m.group(3));
    match = m.find();
    assertTrue(match);
    assertEquals(3, m.groupCount());
    assertEquals("Bishop of London", m.group());
    assertEquals("Bishop", m.group(1));
    assertEquals("London", m.group(2));
    assertEquals("", m.group(3));
    match = m.find();
    assertTrue(match);
    assertEquals(3, m.groupCount());
    assertEquals("Bishop of London in", m.group());
    assertEquals("Bishop", m.group(1));
    assertEquals("London in", m.group(2));
    assertEquals("", m.group(3));
    match = m.find();
    assertFalse(match);
  }

  @Test
  public void testTokenSequenceMatcherAll() throws IOException {
    CoreMap doc = createDocument(testText1);
    TokenSequencePattern p = TokenSequencePattern.compile(
            "(/[A-Za-z]+/{1,2}) /of/ (/[A-Za-z]+/{1,3}?) ");

    TokenSequenceMatcher m = p.getMatcher(doc.get(CoreAnnotations.TokensAnnotation.class));
    m.setFindType(SequenceMatcher.FindType.FIND_ALL);
    // Test finding of ALL matching sequences
    // NOTE: when using FIND_ALL greedy/reluctant modifiers are not enforced
    //       perhaps should add syntax where some of them are enforced...
    boolean match = m.find();
    assertTrue(match);
    assertEquals(2, m.groupCount());
    assertEquals("first Bishop of London", m.group());
    assertEquals("first Bishop", m.group(1));
    assertEquals("London", m.group(2));
    match = m.find();
    assertTrue(match);
    assertEquals(2, m.groupCount());
    assertEquals("Bishop of London", m.group());
    assertEquals("Bishop", m.group(1));
    assertEquals("London", m.group(2));
    match = m.find();
    assertTrue(match);
    assertEquals(2, m.groupCount());
    assertEquals("third Archbishop of Canterbury", m.group());
    assertEquals("third Archbishop", m.group(1));
    assertEquals("Canterbury", m.group(2));
    match = m.find();
    assertTrue(match);
    assertEquals(2, m.groupCount());
    assertEquals("Archbishop of Canterbury", m.group());
    assertEquals("Archbishop", m.group(1));
    assertEquals("Canterbury", m.group(2));
    match = m.find();
    assertTrue(match);
    assertEquals(2, m.groupCount());
    assertEquals("a member of the", m.group());
    assertEquals("a member", m.group(1));
    assertEquals("the", m.group(2));
    match = m.find();
    assertTrue(match);
    assertEquals(2, m.groupCount());
    assertEquals("a member of the Gregorian", m.group());
    assertEquals("a member", m.group(1));
    assertEquals("the Gregorian", m.group(2));
    match = m.find();
    assertTrue(match);
    assertEquals(2, m.groupCount());
    assertEquals("a member of the Gregorian mission", m.group());
    assertEquals("a member", m.group(1));
    assertEquals("the Gregorian mission", m.group(2));
    match = m.find();
    assertTrue(match);
    assertEquals(2, m.groupCount());
    assertEquals("member of the", m.group());
    assertEquals("member", m.group(1));
    assertEquals("the", m.group(2));
    match = m.find();
    assertTrue(match);
    assertEquals(2, m.groupCount());
    assertEquals("member of the Gregorian", m.group());
    assertEquals("member", m.group(1));
    assertEquals("the Gregorian", m.group(2));
    match = m.find();
    assertTrue(match);
    assertEquals(2, m.groupCount());
    assertEquals("member of the Gregorian mission", m.group());
    assertEquals("member", m.group(1));
    assertEquals("the Gregorian mission", m.group(2));
    match = m.find();
    assertTrue(match);
    assertEquals(2, m.groupCount());
    assertEquals("as Bishop of London", m.group());
    assertEquals("as Bishop", m.group(1));
    assertEquals("London", m.group(2));
    match = m.find();
    assertTrue(match);
    assertEquals(2, m.groupCount());
    assertEquals("as Bishop of London in", m.group());
    assertEquals("as Bishop", m.group(1));
    assertEquals("London in", m.group(2));
    match = m.find();
    assertTrue(match);
    assertEquals(2, m.groupCount());
    assertEquals("Bishop of London", m.group());
    assertEquals("Bishop", m.group(1));
    assertEquals("London", m.group(2));
    match = m.find();
    assertTrue(match);
    assertEquals(2, m.groupCount());
    assertEquals("Bishop of London in", m.group());
    assertEquals("Bishop", m.group(1));
    assertEquals("London in", m.group(2));
    match = m.find();
    assertFalse(match);
  }

  @Test
  public void testTokenSequenceMatcherAll2() throws IOException {
    String text = "DATE1 PROD1 PRICE1 PROD2 PRICE2 PROD3 PRICE3 DATE2 PROD4 PRICE4 PROD5 PRICE5 PROD6 PRICE6";
    CoreMap doc = createDocument(text);
    TokenSequencePattern p = TokenSequencePattern.compile(
        "(/DATE.*/) (?: /PROD.*/ /PRICE.*/)* (/PROD.*/) (/PRICE.*/)");

    TokenSequenceMatcher m = p.getMatcher(doc.get(CoreAnnotations.TokensAnnotation.class));
    m.setFindType(SequenceMatcher.FindType.FIND_ALL);
    // Test finding of ALL matching sequences
    boolean match = m.find();
    assertTrue(match);
    assertEquals(3, m.groupCount());
    assertEquals("DATE1", m.group(1));
    assertEquals("PROD3", m.group(2));
    assertEquals("PRICE3", m.group(3));
    match = m.find();
    assertTrue(match);
    assertEquals(3, m.groupCount());
    assertEquals("DATE1", m.group(1));
    assertEquals("PROD2", m.group(2));
    assertEquals("PRICE2", m.group(3));
    match = m.find();
    assertTrue(match);
    assertEquals(3, m.groupCount());
    assertEquals("DATE1", m.group(1));
    assertEquals("PROD1", m.group(2));
    assertEquals("PRICE1", m.group(3));
    match = m.find();
    assertTrue(match);
    assertEquals(3, m.groupCount());
    assertEquals("DATE2", m.group(1));
    assertEquals("PROD6", m.group(2));
    assertEquals("PRICE6", m.group(3));
    match = m.find();
    assertTrue(match);
    assertEquals(3, m.groupCount());
    assertEquals("DATE2", m.group(1));
    assertEquals("PROD5", m.group(2));
    assertEquals("PRICE5", m.group(3));
    match = m.find();
    assertTrue(match);
    assertEquals(3, m.groupCount());
    assertEquals("DATE2", m.group(1));
    assertEquals("PROD4", m.group(2));
    assertEquals("PRICE4", m.group(3));
    match = m.find();
    assertFalse(match);
  }

  @Test
  public void testTokenSequenceMatcherNonOverlapping() throws IOException {
    String text = "DATE1 PROD1 PRICE1 PROD2 PRICE2 PROD3 PRICE3 DATE2 PROD4 PRICE4 PROD5 PRICE5 PROD6 PRICE6";
    CoreMap doc = createDocument(text);
    TokenSequencePattern p = TokenSequencePattern.compile(
        "(/DATE.*/) ((/PROD.*/ /PRICE.*/)+)");

    TokenSequenceMatcher m = p.getMatcher(doc.get(CoreAnnotations.TokensAnnotation.class));
    boolean match = m.find();
    assertTrue(match);
    assertEquals(3, m.groupCount());
    assertEquals("DATE1", m.group(1));
    assertEquals("PROD1 PRICE1 PROD2 PRICE2 PROD3 PRICE3", m.group(2));
    assertEquals("PROD3 PRICE3", m.group(3));
    match = m.find();
    assertTrue(match);
    assertEquals(3, m.groupCount());
    assertEquals("DATE2", m.group(1));
    assertEquals("PROD4 PRICE4 PROD5 PRICE5 PROD6 PRICE6", m.group(2));
    assertEquals("PROD6 PRICE6", m.group(3));
    match = m.find();
    assertFalse(match);
  }

  @Test
  public void testTokenSequenceMatcher4() throws IOException {
    CoreMap doc = createDocument(testText1);

    // Test sequence with groups
    TokenSequencePattern p = TokenSequencePattern.compile(
                      new SequencePattern.RepeatPatternExpr(
                                    getSequencePatternExpr("[A-Za-z]+"), 1, -1));

    TokenSequenceMatcher m = p.getMatcher(doc.get(CoreAnnotations.TokensAnnotation.class));
    boolean match = m.find();
    assertTrue(match);
    assertEquals(0, m.groupCount());
    assertEquals("Mellitus was the first Bishop of London", m.group());

    match = m.find();
    assertTrue(match);
    assertEquals(0, m.groupCount());
    assertEquals("the third Archbishop of Canterbury", m.group());

    p = TokenSequencePattern.compile(
            new SequencePattern.SequencePatternExpr(
                      new SequencePattern.RepeatPatternExpr(
                              getSequencePatternExpr("[A-Za-z]+"), 0, -1),
                      getSequencePatternExpr("Mellitus", "was")));

    m = p.getMatcher(doc.get(CoreAnnotations.TokensAnnotation.class));
    match = m.find();
    assertTrue(match);
    assertEquals(0, m.groupCount());
    assertEquals("Mellitus was", m.group());
    match = m.find();
    assertFalse(match);

    p = TokenSequencePattern.compile(
            new SequencePattern.SequencePatternExpr(
                      new SequencePattern.RepeatPatternExpr(
                              getSequencePatternExpr("[A-Za-z]+"), 1, -1),
                      getSequencePatternExpr("Mellitus", "was")));

    m = p.getMatcher(doc.get(CoreAnnotations.TokensAnnotation.class));
    match = m.find();
    assertFalse(match);

  }

  @Test
  public void testTokenSequenceMatcher5() throws IOException {
    CoreMap doc = createDocument(testText1);

    // Test simple sequence
    TokenSequencePattern p = TokenSequencePattern.compile(" [ { word:\"Archbishop\" } ]  [ { word:\"of\" } ]  [ { word:\"Canterbury\" } ]");
    TokenSequenceMatcher m = p.getMatcher(doc.get(CoreAnnotations.TokensAnnotation.class));
    boolean match = m.find();
    assertTrue(match);
    assertEquals("Archbishop of Canterbury", m.group());
    match = m.find();
    assertFalse(match);

    m.reset();
    match = m.find();
    assertTrue(match);
    assertEquals("Archbishop of Canterbury", m.group());

    m.reset();
    match = m.matches();
    assertFalse(match);


    p = TokenSequencePattern.compile(" [ \"Archbishop\" ]  [ \"of\"  ]  [ \"Canterbury\"  ]");
    m = p.getMatcher(doc.get(CoreAnnotations.TokensAnnotation.class));
    match = m.find();
    assertTrue(match);
    assertEquals("Archbishop of Canterbury", m.group());
    match = m.find();
    assertFalse(match);

    m.reset();
    match = m.find();
    assertTrue(match);
    assertEquals("Archbishop of Canterbury", m.group());

    m.reset();
    match = m.matches();
    assertFalse(match);

    // Test sequence with or
    p = TokenSequencePattern.compile(" [ \"Archbishop\"] [\"of\"] [\"Canterbury\"] |  [ \"Bishop\" ] [ \"of\" ]  [ \"London\" ] ");
    m = p.getMatcher(doc.get(CoreAnnotations.TokensAnnotation.class));
    match = m.find();
    assertTrue(match);
    assertEquals(0, m.groupCount());
    assertEquals("Bishop of London", m.group());
    match = m.find();
    assertTrue(match);
    assertEquals(0, m.groupCount());
    assertEquals("Archbishop of Canterbury", m.group());
    match = m.find();
    assertTrue(match);
    assertEquals(0, m.groupCount());
    assertEquals("Bishop of London", m.group());
    match = m.find();
    assertFalse(match);

  }

  @Test
  public void testTokenSequenceMatcher6() throws IOException {
    CoreMap doc = createDocument(testText1);
    TokenSequencePattern p = TokenSequencePattern.compile("[ /.*/ ] [ /.*/ ] [/of/] [/.*/]");
    TokenSequenceMatcher m = p.getMatcher(doc.get(CoreAnnotations.TokensAnnotation.class));
    boolean match = m.find();
    assertTrue(match);
    assertEquals(0, m.groupCount());
    assertEquals("first Bishop of London", m.group());
    match = m.find();
    assertTrue(match);
    assertEquals(0, m.groupCount());
    assertEquals("third Archbishop of Canterbury", m.group());
    match = m.find();
    assertTrue(match);
    assertEquals(0, m.groupCount());
    assertEquals("a member of the", m.group());
    match = m.find();
    assertTrue(match);
    assertEquals(0, m.groupCount());
    assertEquals("as Bishop of London", m.group());
    match = m.find();
    assertFalse(match);

    p = TokenSequencePattern.compile("([ /.*/ ] [ /.*/ ]) [/of/] ([/.*/])");
    m = p.getMatcher(doc.get(CoreAnnotations.TokensAnnotation.class));
    match = m.find();
    assertTrue(match);
    assertEquals(2, m.groupCount());
    assertEquals("first Bishop of London", m.group());
    assertEquals("first Bishop", m.group(1));
    assertEquals("London", m.group(2));
    match = m.find();
    assertTrue(match);
    assertEquals(2, m.groupCount());
    assertEquals("third Archbishop of Canterbury", m.group());
    assertEquals("third Archbishop", m.group(1));
    assertEquals("Canterbury", m.group(2));
    match = m.find();
    assertTrue(match);
    assertEquals(2, m.groupCount());
    assertEquals("a member of the", m.group());
    assertEquals("a member", m.group(1));
    assertEquals("the", m.group(2));
    match = m.find();
    assertTrue(match);
    assertEquals(2, m.groupCount());
    assertEquals("as Bishop of London", m.group());
    assertEquals("as Bishop", m.group(1));
    assertEquals("London", m.group(2));
    match = m.find();
    assertFalse(match);

  }

  @Test
  public void testTokenSequenceMatcher7() throws IOException {
    CoreMap doc = createDocument(testText1);

    // Test sequence with groups
    TokenSequencePattern p = TokenSequencePattern.compile(" ( [ /[A-Za-z]+/ ]{1,2} )  [ /of/ ] ( [ /[A-Za-z]+/ ]{1,3} )");
    TokenSequenceMatcher m = p.getMatcher(doc.get(CoreAnnotations.TokensAnnotation.class));
    boolean match = m.find();
    assertTrue(match);
    assertEquals(2, m.groupCount());
    assertEquals("first Bishop of London", m.group());
    assertEquals("first Bishop", m.group(1));
    assertEquals("London", m.group(2));
    match = m.find();
    assertTrue(match);
    assertEquals(2, m.groupCount());
    assertEquals("third Archbishop of Canterbury", m.group());
    assertEquals("third Archbishop", m.group(1));
    assertEquals("Canterbury", m.group(2));
    match = m.find();
    assertTrue(match);
    assertEquals(2, m.groupCount());
    assertEquals("a member of the Gregorian mission", m.group());
    assertEquals("a member", m.group(1));
    assertEquals("the Gregorian mission", m.group(2));
    match = m.find();
    assertTrue(match);
    assertEquals(2, m.groupCount());
    assertEquals("as Bishop of London in", m.group());
    assertEquals("as Bishop", m.group(1));
    assertEquals("London in", m.group(2));
    match = m.find();
    assertFalse(match);

    p = TokenSequencePattern.compile( " ( [ /[A-Za-z]+/ ]{2,2} )  [ /of/ ] ( [ /[A-Za-z]+/ ]{1,3}? )");

    m = p.getMatcher(doc.get(CoreAnnotations.TokensAnnotation.class));
    match = m.find();
    assertTrue(match);
    assertEquals(2, m.groupCount());
    assertEquals("first Bishop of London", m.group());
    assertEquals("first Bishop", m.group(1));
    assertEquals("London", m.group(2));
    match = m.find();
    assertTrue(match);
    assertEquals(2, m.groupCount());
    assertEquals("third Archbishop of Canterbury", m.group());
    assertEquals("third Archbishop", m.group(1));
    assertEquals("Canterbury", m.group(2));
    match = m.find();
    assertTrue(match);
    assertEquals(2, m.groupCount());
    assertEquals("a member of the", m.group());
    assertEquals("a member", m.group(1));
    assertEquals("the", m.group(2));
    match = m.find();
    assertTrue(match);
    assertEquals(2, m.groupCount());
    assertEquals("as Bishop of London", m.group());
    assertEquals("as Bishop", m.group(1));
    assertEquals("London", m.group(2));
    match = m.find();
    assertFalse(match);
  }

  @Test
  public void testTokenSequenceMatcher8() throws IOException {
    CoreMap doc = createDocument(testText1);

    // Test sequence with groups
    TokenSequencePattern p = TokenSequencePattern.compile( "[ /[A-Za-z]+/ ]*");

    TokenSequenceMatcher m = p.getMatcher(doc.get(CoreAnnotations.TokensAnnotation.class));
    boolean match = m.find();
    assertTrue(match);
    assertEquals(0, m.groupCount());
    assertEquals("Mellitus was the first Bishop of London", m.group());
    match = m.find();
    assertTrue(match);
    assertEquals(0, m.groupCount());
    assertEquals("the third Archbishop of Canterbury", m.group());

    p = TokenSequencePattern.compile( "[ /[A-Za-z]+/ ]*  [\"Mellitus\"] [ \"was\"]");

    m = p.getMatcher(doc.get(CoreAnnotations.TokensAnnotation.class));
    match = m.find();
    assertTrue(match);
    assertEquals(0, m.groupCount());
    assertEquals("Mellitus was", m.group());
    match = m.find();
    assertFalse(match);

    p = TokenSequencePattern.compile( "[ /[A-Za-z]+/ ]+  [\"Mellitus\"] [ \"was\"]");

    m = p.getMatcher(doc.get(CoreAnnotations.TokensAnnotation.class));
    match = m.find();
    assertFalse(match);

  }

  @Test
  public void testTokenSequenceMatcher9() throws IOException {
    CoreMap doc = createDocument(testText1);

    // Test sequence with groups
//    TokenSequencePattern p = TokenSequencePattern.compile( "(?$contextprev /.*/) (?$treat [{{treat}} & /.*/]) (?$contextnext [/.*/])");
    TokenSequencePattern p = TokenSequencePattern.compile("(?$contextprev /.*/) (?$test [{tag:NNP} & /.*/]) (?$contextnext [/.*/])");

    TokenSequenceMatcher m = p.getMatcher(doc.get(CoreAnnotations.TokensAnnotation.class));
    boolean match = m.find();
    assertTrue(match);
    assertEquals(3, m.groupCount());
    assertEquals("first Bishop of", m.group());

    assertEquals("first", m.group(1));
    assertEquals("Bishop", m.group(2));
    assertEquals("of", m.group(3));
    assertEquals("first", m.group("$contextprev"));
    assertEquals("Bishop", m.group("$test"));
    assertEquals("of", m.group("$contextnext"));
    assertEquals("first", m.group(" $contextprev"));
    assertEquals("Bishop", m.group("$test "));
    assertEquals(null, m.group("$contex tnext"));

    assertEquals(3, m.start("$contextprev"));
    assertEquals(4, m.end("$contextprev"));
    assertEquals(4, m.start("$test"));
    assertEquals(5, m.end("$test"));
    assertEquals(5, m.start("$contextnext"));
    assertEquals(6, m.end("$contextnext"));
  }

  @Test
  public void testTokenSequenceMatcher10() throws IOException {
    CoreMap doc = createDocument("the number is five or 5 or 5.0 or but not 5x or -5 or 5L.");

    // Test simplified pattern with number
    TokenSequencePattern p = TokenSequencePattern.compile( "(five|5|5x|5.0|-5|5L)");

    TokenSequenceMatcher m = p.getMatcher(doc.get(CoreAnnotations.TokensAnnotation.class));
    boolean match = m.find();
    assertTrue(match);
    assertEquals(1, m.groupCount());
    assertEquals("five", m.group(1));

    match = m.find();
    assertTrue(match);
    assertEquals(1, m.groupCount());
    assertEquals("5", m.group(1));

    match = m.find();
    assertTrue(match);
    assertEquals(1, m.groupCount());
    assertEquals("5.0", m.group(1));

    match = m.find();
    assertTrue(match);
    assertEquals(1, m.groupCount());
    assertEquals("5x", m.group(1));

    match = m.find();
    assertTrue(match);
    assertEquals(1, m.groupCount());
    assertEquals("-5", m.group(1));

    match = m.find();
    assertTrue(match);
    assertEquals(1, m.groupCount());
    assertEquals("5L", m.group(1));

    match = m.find();
    assertFalse(match);
  }

  @Test
  public void testTokenSequenceOptimizeOrString() throws IOException {
    CoreMap doc = createDocument("atropine we need to have many many words here but we don't sweating");

    // Test simplified pattern with number
    TokenSequencePattern p = TokenSequencePattern.compile( "(?$dt \"atropine\") []{0,15} " +
            "(?$se  \"social\" \"avoidant\" \"behaviour\"|\"dysuria\"|\"hyperglycaemia\"| \"mental\" \"disorder\"|\"vertigo\"|\"flutter\"| \"chest\" \"pain\"| \"elevated\" \"blood\" \"pressure\"|\"mania\"| \"rash\" \"erythematous\"|\"manic\"| \"papular\" \"rash\"|\"death\"| \"atrial\" \"arrhythmia\"| \"dry\" \"eyes\"| \"loss\" \"of\" \"libido\"| \"rash\" \"papular\"|\"hypersensitivity\"| \"blood\" \"pressure\" \"increased\"|\"dyspepsia\"| \"accommodation\" \"disorder\"| \"reflexes\" \"increased\"|\"lesions\"|\"asthenia\"| \"gastrointestinal\" \"pain\"|\"excitement\"| \"breast\" \"feeding\"|\"hypokalaemia\"| \"cerebellar\" \"syndrome\"|\"nervousness\"| \"pulmonary\" \"oedema\"| \"inspiratory\" \"stridor\"| \"taste\" \"altered\"|\"paranoia\"| \"psychotic\" \"disorder\"| \"open\" \"angle\" \"glaucoma\"|\"photophobia\"| \"dry\" \"eye\"|\"osteoarthritis\"| \"keratoconjunctivitis\" \"sicca\"| \"haemoglobin\" \"increased\"| \"ventricular\" \"extrasystoles\"|\"hallucinations\"|\"conjunctivitis\"|\"paralysis\"| \"qrs\" \"complex\"|\"anxiety\"| \"conjunctival\" \"disorder\"|\"coma\"|\"strabismus\"|\"thirst\"|\"para\"| \"sicca\" \"syndrome\"| \"atrioventricular\" \"dissociation\"|\"desquamation\"|\"crusting\"| \"abdominal\" \"distension\"|\"blindness\"|\"hypotension\"|\"dermatitis\"| \"sinus\" \"tachycardia\"| \"abdominal\" \"distention\"| \"lacrimation\" \"decreased\"|\"sicca\"| \"paralytic\" \"ileus\"| \"urinary\" \"hesitation\"|\"withdrawn\"| \"erectile\" \"dysfunction\"|\"keratoconjunctivitis\"|\"anaphylaxis\"| \"psychiatric\" \"disorders\"| \"altered\" \"taste\"|\"somnolence\"|\"extrasystoles\"|\"ageusia\"| \"intraocular\" \"pressure\" \"increased\"| \"left\" \"ventricular\" \"failure\"|\"impotence\"|\"drowsiness\"|\"conjunctiva\"| \"delayed\" \"gastric\" \"emptying\"| \"gastrointestinal\" \"sounds\" \"abnormal\"| \"qt\" \"prolonged\"| \"supraventricular\" \"tachycardia\"|\"weakness\"|\"hypertonia\"| \"confusional\" \"state\"|\"anhidrosis\"|\"myopia\"|\"dyspnoea\"| \"speech\" \"impairment\" \"nos\"| \"rash\" \"maculo\" \"papular\"|\"petechiae\"|\"tachypnea\"| \"acute\" \"angle\" \"closure\" \"glaucoma\"| \"gastrooesophageal\" \"reflux\" \"disease\"|\"hypokalemia\"| \"left\" \"heart\" \"failure\"| \"myocardial\" \"infarction\"| \"site\" \"reaction\"| \"ventricular\" \"fibrillation\"|\"fibrillation\"| \"maculopapular\" \"rash\"| \"impaired\" \"gastric\" \"emptying\"|\"amnesia\"| \"labored\" \"respirations\"| \"decreased\" \"lacrimation\"|\"mydriasis\"|\"headache\"| \"dry\" \"mouth\"|\"scab\"| \"cardiac\" \"syncope\"| \"visual\" \"acuity\" \"reduced\"|\"tension\"| \"blurred\" \"vision\"| \"bloated\" \"feeling\"| \"labored\" \"breathing\"| \"stridor\" \"inspiratory\"| \"skin\" \"exfoliation\"| \"memory\" \"loss\"|\"syncope\"| \"rash\" \"scarlatiniform\"|\"hyperpyrexia\"| \"cardiac\" \"flutter\"|\"heartburn\"| \"bowel\" \"sounds\" \"decreased\"|\"blepharitis\"|\"tachycardia\"| \"excessive\" \"thirst\"|\"confusion\"| \"rash\" \"macular\"| \"taste\" \"loss\"| \"respiratory\" \"failure\"|\"hesitancy\"|\"dysmetria\"|\"disorientation\"| \"decreased\" \"hemoglobin\"| \"atrial\" \"fibrillation\"| \"urinary\" \"retention\"| \"dry\" \"skin\"|\"dehydration\"|\"hyponatraemia\"|\"dysgeusia\"|\"disorder\"| \"increased\" \"intraocular\" \"pressure\"| \"speech\" \"disorder\"| \"feeling\" \"abnormal\"|\"pain\"| \"anaphylactic\" \"shock\"|\"hallucination\"| \"abdominal\" \"pain\"| \"junctional\" \"tachycardia\"| \"bun\" \"increased\"| \"ventricular\" \"flutter\"| \"scarlatiniform\" \"rash\"|\"agitation\"| \"feeling\" \"hot\"|\"hyponatremia\"| \"decreased\" \"bowel\" \"sounds\"|\"cyanosis\"|\"dysarthria\"| \"heat\" \"intolerance\"|\"hyperglycemia\"|\"reflux\"| \"angle\" \"closure\" \"glaucoma\"| \"electrocardiogram\" \"qt\" \"prolonged\"| \"vision\" \"blurred\"| \"blood\" \"urea\" \"increased\"|\"dizziness\"|\"arrhythmia\"|\"erythema\"|\"vomiting\"| \"difficulty\" \"in\" \"micturition\"|\"infarction\"|\"laryngospasm\"|\"hypoglycaemia\"|\"hypoglycemia\"| \"elevated\" \"hemoglobin\"| \"skin\" \"warm\"| \"ventricular\" \"arrhythmia\"|\"dissociation\"| \"warm\" \"skin\"| \"follicular\" \"conjunctivitis\"|\"urticaria\"|\"fatigue\"| \"cardiac\" \"fibrillation\"| \"decreased\" \"sweating\"| \"decreased\" \"visual\" \"acuity\"|\"lethargy\"| \"acute\" \"angle\" \"closure\" \"glaucoma\"| \"nodal\" \"rhythm\"|\"borborygmi\"|\"hyperreflexia\"| \"respiratory\" \"depression\"|\"diarrhea\"|\"leukocytosis\"| \"speech\" \"disturbance\"|\"ataxia\"|\"cycloplegia\"|\"tachypnoea\"|\"eczema\"| \"supraventricular\" \"extrasystoles\"|\"ileus\"| \"cardiac\" \"arrest\"| \"ventricular\" \"tachycardia\"|\"laryngitis\"|\"delirium\"|\"lactation\"|\"glaucoma\"|\"obstruction\"|\"hypohidrosis\"|\"parity\"|\"palpitations\"| \"temperature\" \"intolerance\"|\"constipation\"|\"cyclophoria\"| \"acute\" \"coronary\" \"syndrome\"| \"arrhythmia\" \"supraventricular\"|\"arrest\"|\"lesion\"|\"nausea\"| \"sweating\" \"decreased\"|\"keratitis\"|\"dyskinesia\"| \"pulmonary\" \"function\" \"test\" \"decreased\"|\"stridor\"|\"swelling\"|\"dysphagia\"| \"haemoglobin\" \"decreased\"|\"diarrhoea\"| \"ileus\" \"paralytic\"|\"clonus\"|\"insomnia\"| \"electrocardiogram\" \"qrs\" \"complex\"| \"nasal\" \"congestion\"| \"nasal\" \"dryness\"|\"sweating\"|\"rash\"| \"nodal\" \"arrhythmia\"|\"irritability\"|\"hyperhidrosis\"| \"ventricular\" \"failure\")");

    Timing timing = new Timing();
    timing.start();
    for (int i = 0; i < 100; i++) {
      TokenSequenceMatcher m = p.getMatcher(doc.get(CoreAnnotations.TokensAnnotation.class));
      boolean match = m.find();
      assertTrue(match);
      assertEquals("atropine we need to have many many words here but we don't sweating", m.group(0));

      match = m.find();
      assertFalse(match);
    }
    timing.stop("testTokenSequenceOptimizeOrString matched");


    CoreMap docNoMatch = createDocument("atropine we need to have many many words here but we don't, many many many words but still no match");
    timing.start();
    for (int i = 0; i < 100; i++) {
      TokenSequenceMatcher m = p.getMatcher(docNoMatch.get(CoreAnnotations.TokensAnnotation.class));
      boolean match = m.find();
      assertFalse(match);
    }
    timing.stop("testTokenSequenceOptimizeOrString no match");
  }

  @Test
  public void testMultiplePatterns() throws IOException {
    TokenSequencePattern p1 = TokenSequencePattern.compile("(?$dt \"atropine\") []{0,15} " +
        "(?$se  \"social\" \"avoidant\" \"behaviour\"|\"dysuria\"|\"hyperglycaemia\"| \"mental\" \"disorder\"|\"vertigo\"|\"flutter\"| \"chest\" \"pain\"| \"elevated\" \"blood\" \"pressure\"|\"mania\"| \"rash\" \"erythematous\"|\"manic\"| \"papular\" \"rash\"|\"death\"| \"atrial\" \"arrhythmia\"| \"dry\" \"eyes\"| \"loss\" \"of\" \"libido\"| \"rash\" \"papular\"|\"hypersensitivity\"| \"blood\" \"pressure\" \"increased\"|\"dyspepsia\"| \"accommodation\" \"disorder\"| \"reflexes\" \"increased\"|\"lesions\"|\"asthenia\"| \"gastrointestinal\" \"pain\"|\"excitement\"| \"breast\" \"feeding\"|\"hypokalaemia\"| \"cerebellar\" \"syndrome\"|\"nervousness\"| \"pulmonary\" \"oedema\"| \"inspiratory\" \"stridor\"| \"taste\" \"altered\"|\"paranoia\"| \"psychotic\" \"disorder\"| \"open\" \"angle\" \"glaucoma\"|\"photophobia\"| \"dry\" \"eye\"|\"osteoarthritis\"| \"keratoconjunctivitis\" \"sicca\"| \"haemoglobin\" \"increased\"| \"ventricular\" \"extrasystoles\"|\"hallucinations\"|\"conjunctivitis\"|\"paralysis\"| \"qrs\" \"complex\"|\"anxiety\"| \"conjunctival\" \"disorder\"|\"coma\"|\"strabismus\"|\"thirst\"|\"para\"| \"sicca\" \"syndrome\"| \"atrioventricular\" \"dissociation\"|\"desquamation\"|\"crusting\"| \"abdominal\" \"distension\"|\"blindness\"|\"hypotension\"|\"dermatitis\"| \"sinus\" \"tachycardia\"| \"abdominal\" \"distention\"| \"lacrimation\" \"decreased\"|\"sicca\"| \"paralytic\" \"ileus\"| \"urinary\" \"hesitation\"|\"withdrawn\"| \"erectile\" \"dysfunction\"|\"keratoconjunctivitis\"|\"anaphylaxis\"| \"psychiatric\" \"disorders\"| \"altered\" \"taste\"|\"somnolence\"|\"extrasystoles\"|\"ageusia\"| \"intraocular\" \"pressure\" \"increased\"| \"left\" \"ventricular\" \"failure\"|\"impotence\"|\"drowsiness\"|\"conjunctiva\"| \"delayed\" \"gastric\" \"emptying\"| \"gastrointestinal\" \"sounds\" \"abnormal\"| \"qt\" \"prolonged\"| \"supraventricular\" \"tachycardia\"|\"weakness\"|\"hypertonia\"| \"confusional\" \"state\"|\"anhidrosis\"|\"myopia\"|\"dyspnoea\"| \"speech\" \"impairment\" \"nos\"| \"rash\" \"maculo\" \"papular\"|\"petechiae\"|\"tachypnea\"| \"acute\" \"angle\" \"closure\" \"glaucoma\"| \"gastrooesophageal\" \"reflux\" \"disease\"|\"hypokalemia\"| \"left\" \"heart\" \"failure\"| \"myocardial\" \"infarction\"| \"site\" \"reaction\"| \"ventricular\" \"fibrillation\"|\"fibrillation\"| \"maculopapular\" \"rash\"| \"impaired\" \"gastric\" \"emptying\"|\"amnesia\"| \"labored\" \"respirations\"| \"decreased\" \"lacrimation\"|\"mydriasis\"|\"headache\"| \"dry\" \"mouth\"|\"scab\"| \"cardiac\" \"syncope\"| \"visual\" \"acuity\" \"reduced\"|\"tension\"| \"blurred\" \"vision\"| \"bloated\" \"feeling\"| \"labored\" \"breathing\"| \"stridor\" \"inspiratory\"| \"skin\" \"exfoliation\"| \"memory\" \"loss\"|\"syncope\"| \"rash\" \"scarlatiniform\"|\"hyperpyrexia\"| \"cardiac\" \"flutter\"|\"heartburn\"| \"bowel\" \"sounds\" \"decreased\"|\"blepharitis\"|\"tachycardia\"| \"excessive\" \"thirst\"|\"confusion\"| \"rash\" \"macular\"| \"taste\" \"loss\"| \"respiratory\" \"failure\"|\"hesitancy\"|\"dysmetria\"|\"disorientation\"| \"decreased\" \"hemoglobin\"| \"atrial\" \"fibrillation\"| \"urinary\" \"retention\"| \"dry\" \"skin\"|\"dehydration\"|\"hyponatraemia\"|\"dysgeusia\"|\"disorder\"| \"increased\" \"intraocular\" \"pressure\"| \"speech\" \"disorder\"| \"feeling\" \"abnormal\"|\"pain\"| \"anaphylactic\" \"shock\"|\"hallucination\"| \"abdominal\" \"pain\"| \"junctional\" \"tachycardia\"| \"bun\" \"increased\"| \"ventricular\" \"flutter\"| \"scarlatiniform\" \"rash\"|\"agitation\"| \"feeling\" \"hot\"|\"hyponatremia\"| \"decreased\" \"bowel\" \"sounds\"|\"cyanosis\"|\"dysarthria\"| \"heat\" \"intolerance\"|\"hyperglycemia\"|\"reflux\"| \"angle\" \"closure\" \"glaucoma\"| \"electrocardiogram\" \"qt\" \"prolonged\"| \"vision\" \"blurred\"| \"blood\" \"urea\" \"increased\"|\"dizziness\"|\"arrhythmia\"|\"erythema\"|\"vomiting\"| \"difficulty\" \"in\" \"micturition\"|\"infarction\"|\"laryngospasm\"|\"hypoglycaemia\"|\"hypoglycemia\"| \"elevated\" \"hemoglobin\"| \"skin\" \"warm\"| \"ventricular\" \"arrhythmia\"|\"dissociation\"| \"warm\" \"skin\"| \"follicular\" \"conjunctivitis\"|\"urticaria\"|\"fatigue\"| \"cardiac\" \"fibrillation\"| \"decreased\" \"sweating\"| \"decreased\" \"visual\" \"acuity\"|\"lethargy\"| \"acute\" \"angle\" \"closure\" \"glaucoma\"| \"nodal\" \"rhythm\"|\"borborygmi\"|\"hyperreflexia\"| \"respiratory\" \"depression\"|\"diarrhea\"|\"leukocytosis\"| \"speech\" \"disturbance\"|\"ataxia\"|\"cycloplegia\"|\"tachypnoea\"|\"eczema\"| \"supraventricular\" \"extrasystoles\"|\"ileus\"| \"cardiac\" \"arrest\"| \"ventricular\" \"tachycardia\"|\"laryngitis\"|\"delirium\"|\"lactation\"|\"glaucoma\"|\"obstruction\"|\"hypohidrosis\"|\"parity\"|\"palpitations\"| \"temperature\" \"intolerance\"|\"constipation\"|\"cyclophoria\"| \"acute\" \"coronary\" \"syndrome\"| \"arrhythmia\" \"supraventricular\"|\"arrest\"|\"lesion\"|\"nausea\"| \"sweating\" \"decreased\"|\"keratitis\"|\"dyskinesia\"| \"pulmonary\" \"function\" \"test\" \"decreased\"|\"stridor\"|\"swelling\"|\"dysphagia\"| \"haemoglobin\" \"decreased\"|\"diarrhoea\"| \"ileus\" \"paralytic\"|\"clonus\"|\"insomnia\"| \"electrocardiogram\" \"qrs\" \"complex\"| \"nasal\" \"congestion\"| \"nasal\" \"dryness\"|\"sweating\"|\"rash\"| \"nodal\" \"arrhythmia\"|\"irritability\"|\"hyperhidrosis\"| \"ventricular\" \"failure\")");
    TokenSequencePattern p2 = TokenSequencePattern.compile( "(?$dt \"disease\") []{0,15} " +
            "(?$se  \"social\" \"avoidant\" \"behaviour\"|\"dysuria\"|\"hyperglycaemia\"| \"mental\" \"disorder\"|\"vertigo\"|\"flutter\"| \"chest\" \"pain\"| \"elevated\" \"blood\" \"pressure\"|\"mania\"| \"rash\" \"erythematous\"|\"manic\"| \"papular\" \"rash\"|\"death\"| \"atrial\" \"arrhythmia\"| \"dry\" \"eyes\"| \"loss\" \"of\" \"libido\"| \"rash\" \"papular\"|\"hypersensitivity\"| \"blood\" \"pressure\" \"increased\"|\"dyspepsia\"| \"accommodation\" \"disorder\"| \"reflexes\" \"increased\"|\"lesions\"|\"asthenia\"| \"gastrointestinal\" \"pain\"|\"excitement\"| \"breast\" \"feeding\"|\"hypokalaemia\"| \"cerebellar\" \"syndrome\"|\"nervousness\"| \"pulmonary\" \"oedema\"| \"inspiratory\" \"stridor\"| \"taste\" \"altered\"|\"paranoia\"| \"psychotic\" \"disorder\"| \"open\" \"angle\" \"glaucoma\"|\"photophobia\"| \"dry\" \"eye\"|\"osteoarthritis\"| \"keratoconjunctivitis\" \"sicca\"| \"haemoglobin\" \"increased\"| \"ventricular\" \"extrasystoles\"|\"hallucinations\"|\"conjunctivitis\"|\"paralysis\"| \"qrs\" \"complex\"|\"anxiety\"| \"conjunctival\" \"disorder\"|\"coma\"|\"strabismus\"|\"thirst\"|\"para\"| \"sicca\" \"syndrome\"| \"atrioventricular\" \"dissociation\"|\"desquamation\"|\"crusting\"| \"abdominal\" \"distension\"|\"blindness\"|\"hypotension\"|\"dermatitis\"| \"sinus\" \"tachycardia\"| \"abdominal\" \"distention\"| \"lacrimation\" \"decreased\"|\"sicca\"| \"paralytic\" \"ileus\"| \"urinary\" \"hesitation\"|\"withdrawn\"| \"erectile\" \"dysfunction\"|\"keratoconjunctivitis\"|\"anaphylaxis\"| \"psychiatric\" \"disorders\"| \"altered\" \"taste\"|\"somnolence\"|\"extrasystoles\"|\"ageusia\"| \"intraocular\" \"pressure\" \"increased\"| \"left\" \"ventricular\" \"failure\"|\"impotence\"|\"drowsiness\"|\"conjunctiva\"| \"delayed\" \"gastric\" \"emptying\"| \"gastrointestinal\" \"sounds\" \"abnormal\"| \"qt\" \"prolonged\"| \"supraventricular\" \"tachycardia\"|\"weakness\"|\"hypertonia\"| \"confusional\" \"state\"|\"anhidrosis\"|\"myopia\"|\"dyspnoea\"| \"speech\" \"impairment\" \"nos\"| \"rash\" \"maculo\" \"papular\"|\"petechiae\"|\"tachypnea\"| \"acute\" \"angle\" \"closure\" \"glaucoma\"| \"gastrooesophageal\" \"reflux\" \"disease\"|\"hypokalemia\"| \"left\" \"heart\" \"failure\"| \"myocardial\" \"infarction\"| \"site\" \"reaction\"| \"ventricular\" \"fibrillation\"|\"fibrillation\"| \"maculopapular\" \"rash\"| \"impaired\" \"gastric\" \"emptying\"|\"amnesia\"| \"labored\" \"respirations\"| \"decreased\" \"lacrimation\"|\"mydriasis\"|\"headache\"| \"dry\" \"mouth\"|\"scab\"| \"cardiac\" \"syncope\"| \"visual\" \"acuity\" \"reduced\"|\"tension\"| \"blurred\" \"vision\"| \"bloated\" \"feeling\"| \"labored\" \"breathing\"| \"stridor\" \"inspiratory\"| \"skin\" \"exfoliation\"| \"memory\" \"loss\"|\"syncope\"| \"rash\" \"scarlatiniform\"|\"hyperpyrexia\"| \"cardiac\" \"flutter\"|\"heartburn\"| \"bowel\" \"sounds\" \"decreased\"|\"blepharitis\"|\"tachycardia\"| \"excessive\" \"thirst\"|\"confusion\"| \"rash\" \"macular\"| \"taste\" \"loss\"| \"respiratory\" \"failure\"|\"hesitancy\"|\"dysmetria\"|\"disorientation\"| \"decreased\" \"hemoglobin\"| \"atrial\" \"fibrillation\"| \"urinary\" \"retention\"| \"dry\" \"skin\"|\"dehydration\"|\"hyponatraemia\"|\"dysgeusia\"|\"disorder\"| \"increased\" \"intraocular\" \"pressure\"| \"speech\" \"disorder\"| \"feeling\" \"abnormal\"|\"pain\"| \"anaphylactic\" \"shock\"|\"hallucination\"| \"abdominal\" \"pain\"| \"junctional\" \"tachycardia\"| \"bun\" \"increased\"| \"ventricular\" \"flutter\"| \"scarlatiniform\" \"rash\"|\"agitation\"| \"feeling\" \"hot\"|\"hyponatremia\"| \"decreased\" \"bowel\" \"sounds\"|\"cyanosis\"|\"dysarthria\"| \"heat\" \"intolerance\"|\"hyperglycemia\"|\"reflux\"| \"angle\" \"closure\" \"glaucoma\"| \"electrocardiogram\" \"qt\" \"prolonged\"| \"vision\" \"blurred\"| \"blood\" \"urea\" \"increased\"|\"dizziness\"|\"arrhythmia\"|\"erythema\"|\"vomiting\"| \"difficulty\" \"in\" \"micturition\"|\"infarction\"|\"laryngospasm\"|\"hypoglycaemia\"|\"hypoglycemia\"| \"elevated\" \"hemoglobin\"| \"skin\" \"warm\"| \"ventricular\" \"arrhythmia\"|\"dissociation\"| \"warm\" \"skin\"| \"follicular\" \"conjunctivitis\"|\"urticaria\"|\"fatigue\"| \"cardiac\" \"fibrillation\"| \"decreased\" \"sweating\"| \"decreased\" \"visual\" \"acuity\"|\"lethargy\"| \"acute\" \"angle\" \"closure\" \"glaucoma\"| \"nodal\" \"rhythm\"|\"borborygmi\"|\"hyperreflexia\"| \"respiratory\" \"depression\"|\"diarrhea\"|\"leukocytosis\"| \"speech\" \"disturbance\"|\"ataxia\"|\"cycloplegia\"|\"tachypnoea\"|\"eczema\"| \"supraventricular\" \"extrasystoles\"|\"ileus\"| \"cardiac\" \"arrest\"| \"ventricular\" \"tachycardia\"|\"laryngitis\"|\"delirium\"|\"lactation\"|\"glaucoma\"|\"obstruction\"|\"hypohidrosis\"|\"parity\"|\"palpitations\"| \"temperature\" \"intolerance\"|\"constipation\"|\"cyclophoria\"| \"acute\" \"coronary\" \"syndrome\"| \"arrhythmia\" \"supraventricular\"|\"arrest\"|\"lesion\"|\"nausea\"| \"sweating\" \"decreased\"|\"keratitis\"|\"dyskinesia\"| \"pulmonary\" \"function\" \"test\" \"decreased\"|\"stridor\"|\"swelling\"|\"dysphagia\"| \"haemoglobin\" \"decreased\"|\"diarrhoea\"| \"ileus\" \"paralytic\"|\"clonus\"|\"insomnia\"| \"electrocardiogram\" \"qrs\" \"complex\"| \"nasal\" \"congestion\"| \"nasal\" \"dryness\"|\"sweating\"|\"rash\"| \"nodal\" \"arrhythmia\"|\"irritability\"|\"hyperhidrosis\"| \"ventricular\" \"failure\")");
    CoreMap doc = createDocument("atropine we need to have many many words here but we don't sweating");
    MultiPatternMatcher<CoreMap> multiPatternMatcher = TokenSequencePattern.getMultiPatternMatcher(p1, p2);
    List<String> expected = new ArrayList<String>();
    expected.add("atropine we need to have many many words here but we don't sweating");
    Iterator<String> expectedIter = expected.iterator();

    Iterable<SequenceMatchResult<CoreMap>> matches =
            multiPatternMatcher.findAllNonOverlappingMatchesPerPattern(doc.get(CoreAnnotations.TokensAnnotation.class));
    for (SequenceMatchResult<CoreMap> match:matches) {
     assertEquals(expectedIter.next(), match.group());
    }
    assertFalse(expectedIter.hasNext());
  }

  @Test
  public void testTokenSequenceMatcherPosNNP() throws IOException {
    CoreMap doc = createDocument(testText1);

    // Test sequence with groups
    TokenSequencePattern p = TokenSequencePattern.compile( "[ { tag:\"NNP\" } ]+");
    TokenSequenceMatcher m = p.getMatcher(doc.get(CoreAnnotations.TokensAnnotation.class));
    boolean match = m.find();
    assertTrue(match);
    assertEquals(0, m.groupCount());
    assertEquals("Mellitus", m.group());

    p = TokenSequencePattern.compile( "[ { tag:\"NNP\" } ] [ /is|was/ ] []*? [ { tag:\"NNP\" } ]+ ");
    m = p.getMatcher(doc.get(CoreAnnotations.TokensAnnotation.class));
    match = m.find();
    assertTrue(match);
    assertEquals(0, m.groupCount());
    assertEquals("Mellitus was the first Bishop", m.group());

    TokenSequencePattern nnpPattern = TokenSequencePattern.compile( "[ { tag:\"NNP\" } ]" );
    Env env = TokenSequencePattern.getNewEnv();
    env.bind("$NNP", nnpPattern);
    p = TokenSequencePattern.compile(env, " $NNP [ /is|was/ ] []*? $NNP+ [ \"of\" ] $NNP+ ");
    m = p.getMatcher(doc.get(CoreAnnotations.TokensAnnotation.class));
    match = m.find();
    assertTrue(match);
    assertEquals(0, m.groupCount());
    assertEquals("Mellitus was the first Bishop of London", m.group());

    p = TokenSequencePattern.compile(env, " ($NNP) /is|was/ []*? ($NNP)+ \"of\" ($NNP)+ ");
    m = p.getMatcher(doc.get(CoreAnnotations.TokensAnnotation.class));
    match = m.find();
    assertTrue(match);
    assertEquals(3, m.groupCount());
    assertEquals("Mellitus was the first Bishop of London", m.group());
    assertEquals("Mellitus", m.group(1));
    assertEquals("Bishop", m.group(2));
    assertEquals("London", m.group(3));


    nnpPattern = TokenSequencePattern.compile( " ( [ { tag:\"NNP\" } ] )" );
    env.bind("$NNP", nnpPattern);
    p = TokenSequencePattern.compile(env, " $NNP /is|was/ []*? $NNP+ \"of\" $NNP+ ");
    m = p.getMatcher(doc.get(CoreAnnotations.TokensAnnotation.class));
    match = m.find();
    assertTrue(match);
    assertEquals(3, m.groupCount());
    assertEquals("Mellitus was the first Bishop of London", m.group());
    assertEquals("Mellitus", m.group(1));
    assertEquals("Bishop", m.group(2));
    assertEquals("London", m.group(3));


    // Same as above but without extra "{}"
    nnpPattern = TokenSequencePattern.compile( " ( [ tag:\"NNP\" ] )" );
    env.bind("$NNP", nnpPattern);
    p = TokenSequencePattern.compile(env, " $NNP /is|was/ []*? $NNP+ \"of\" $NNP+ ");
    m = p.getMatcher(doc.get(CoreAnnotations.TokensAnnotation.class));
    match = m.find();
    assertTrue(match);
    assertEquals(3, m.groupCount());
    assertEquals("Mellitus was the first Bishop of London", m.group());
    assertEquals("Mellitus", m.group(1));
    assertEquals("Bishop", m.group(2));
    assertEquals("London", m.group(3));

    // Same as above but using "pos"
    nnpPattern = TokenSequencePattern.compile( " ( [ pos:\"NNP\" ] )" );
    env.bind("$NNP", nnpPattern);
    p = TokenSequencePattern.compile(env, " $NNP /is|was/ []*? $NNP+ \"of\" $NNP+ ");
    m = p.getMatcher(doc.get(CoreAnnotations.TokensAnnotation.class));
    match = m.find();
    assertTrue(match);
    assertEquals(3, m.groupCount());
    assertEquals("Mellitus was the first Bishop of London", m.group());
    assertEquals("Mellitus", m.group(1));
    assertEquals("Bishop", m.group(2));
    assertEquals("London", m.group(3));
  }

  @Test
  public void testTokenSequenceMatcherNumber() throws IOException {
    CoreMap doc = createDocument("It happened on January 3, 2002");

    // Test sequence with groups
    TokenSequencePattern p = TokenSequencePattern.compile( "[ { word::IS_NUM } ]+");
    TokenSequenceMatcher m = p.getMatcher(doc.get(CoreAnnotations.TokensAnnotation.class));
    boolean match = m.find();
    assertTrue(match);
    assertEquals(0, m.groupCount());
    assertEquals("3", m.group());
    match = m.find();
    assertTrue(match);
    assertEquals(0, m.groupCount());
    assertEquals("2002", m.group());
    match = m.find();
    assertFalse(match);

    p = TokenSequencePattern.compile( "[ { word>=2002 } ]+");
    m = p.getMatcher(doc.get(CoreAnnotations.TokensAnnotation.class));
    match = m.find();
    assertTrue(match);
    assertEquals(0, m.groupCount());
    assertEquals("2002", m.group());
    match = m.find();
    assertFalse(match);

    p = TokenSequencePattern.compile( "[ { word>2002 } ]+");
    m = p.getMatcher(doc.get(CoreAnnotations.TokensAnnotation.class));
    match = m.find();
    assertFalse(match);

    // Check no {} with or
    p = TokenSequencePattern.compile( "[ word > 2002 | word==2002 ]+");
    m = p.getMatcher(doc.get(CoreAnnotations.TokensAnnotation.class));
    match = m.find();
    assertTrue(match);
    assertEquals(0, m.groupCount());
    assertEquals("2002", m.group());
    match = m.find();
    assertFalse(match);

    // Check no {} with and
    p = TokenSequencePattern.compile( "[ word>2002 & word==2002 ]+");
    m = p.getMatcher(doc.get(CoreAnnotations.TokensAnnotation.class));
    match = m.find();
    assertFalse(match);

    p = TokenSequencePattern.compile( "[ { word>2000 } ]+");
    m = p.getMatcher(doc.get(CoreAnnotations.TokensAnnotation.class));
    match = m.find();
    assertTrue(match);
    assertEquals(0, m.groupCount());
    assertEquals("2002", m.group());
    match = m.find();
    assertFalse(match);

    p = TokenSequencePattern.compile( "[ { word<=2002 } ]+");
    m = p.getMatcher(doc.get(CoreAnnotations.TokensAnnotation.class));
    match = m.find();
    assertTrue(match);
    assertEquals(0, m.groupCount());
    assertEquals("3", m.group());
    match = m.find();
    assertTrue(match);
    assertEquals(0, m.groupCount());
    assertEquals("2002", m.group());
    match = m.find();
    assertFalse(match);

    p = TokenSequencePattern.compile( "[ { word<2002 } ]+");
    m = p.getMatcher(doc.get(CoreAnnotations.TokensAnnotation.class));
    match = m.find();
    assertTrue(match);
    assertEquals(0, m.groupCount());
    assertEquals("3", m.group());
    match = m.find();
    assertFalse(match);

    p = TokenSequencePattern.compile( "[ { word==2002 } ]+");
    m = p.getMatcher(doc.get(CoreAnnotations.TokensAnnotation.class));
    match = m.find();
    assertTrue(match);
    assertEquals(0, m.groupCount());
    assertEquals("2002", m.group());
    match = m.find();
    assertFalse(match);

    p = TokenSequencePattern.compile( "[ { ner:DATE } ]+");
    m = p.getMatcher(doc.get(CoreAnnotations.TokensAnnotation.class));
    match = m.find();
    assertTrue(match);
    assertEquals(0, m.groupCount());
    assertEquals("January 3, 2002", m.group());
    match = m.find();
    assertFalse(match);

    p = TokenSequencePattern.compile( "[ { ner::NOT_NIL } ]+");
    m = p.getMatcher(doc.get(CoreAnnotations.TokensAnnotation.class));
    match = m.find();
    assertTrue(match);
    assertEquals(0, m.groupCount());
    assertEquals("January 3, 2002", m.group());
    match = m.find();
    assertFalse(match);

    p = TokenSequencePattern.compile( "[ { ner::IS_NIL } ]+");
    m = p.getMatcher(doc.get(CoreAnnotations.TokensAnnotation.class));
    match = m.find();
    assertTrue(match);
    assertEquals(0, m.groupCount());
    assertEquals("It happened on", m.group());
    match = m.find();
    assertFalse(match);

    p = TokenSequencePattern.compile( "[ {{ word=~/2002/ }} ]+");
    m = p.getMatcher(doc.get(CoreAnnotations.TokensAnnotation.class));
    match = m.find();
    assertTrue(match);
    assertEquals(0, m.groupCount());
    assertEquals("2002", m.group());
    match = m.find();
    assertFalse(match);
  }

  @Test
  public void testTokenSequenceMatcherNested() throws IOException {
    CoreMap doc = createDocument("A A A B B B B B B C C");

    // Test sequence with groups
    TokenSequencePattern p = TokenSequencePattern.compile( "( /B/+ )+");
    TokenSequenceMatcher m = p.getMatcher(doc.get(CoreAnnotations.TokensAnnotation.class));
    boolean match = m.find();
    assertTrue(match);
    assertEquals(1, m.groupCount());
    assertEquals("B B B B B B", m.group());
    assertEquals("B B B B B B", m.group(1));
    match = m.find();
    assertFalse(match);
  }

  @Test
  public void testTokenSequenceMatcherAAs() throws IOException {
    StringBuilder s = new StringBuilder();
 //   Timing timing = new Timing();
    for (int i = 1; i <= 10; i++) {
      s.append("A ");
      CoreMap doc = createDocument(s.toString());
      TokenSequencePattern p = TokenSequencePattern.compile("(A?)" + "{" + i + "} " + "A" + "{" + i + "}");
//      TokenSequencePattern p = TokenSequencePattern.compile( "(A?)" + "{" + i + "}");
      TokenSequenceMatcher m = p.getMatcher(doc.get(CoreAnnotations.TokensAnnotation.class));
//      timing.start();
      boolean match = m.matches();
      assertTrue(match);
//      timing.stop("matched: " + match + " " + i);
    }
  }

  @Test
  @Ignore
  public void testTokenSequenceFindsWildcard() throws IOException {
    CoreMap doc = createDocument("word1 word2");

    // Test sequence with groups
    TokenSequencePattern p = TokenSequencePattern.compile( "[]{2}|[]");
    TokenSequenceMatcher m = p.getMatcher(doc.get(CoreAnnotations.TokensAnnotation.class));
    boolean match = m.find();
    assertTrue(match);
    assertEquals(0, m.groupCount());
    assertEquals("word1 word2", m.group());
    match = m.find();
    assertFalse(match);

    // Reverse order
    p = TokenSequencePattern.compile( "[]|[]{2}");
    m = p.getMatcher(doc.get(CoreAnnotations.TokensAnnotation.class));
    match = m.find();
    assertTrue(match);
    assertEquals(0, m.groupCount());
    assertEquals("word1 word2", m.group());
    match = m.find();
    assertFalse(match);

    // Using {1,2}
    p = TokenSequencePattern.compile( "[]{2}");
    m = p.getMatcher(doc.get(CoreAnnotations.TokensAnnotation.class));
    match = m.find();
    assertTrue(match);
    assertEquals(0, m.groupCount());
    assertEquals("word1 word2", m.group());
    match = m.find();
    assertFalse(match);
  }

  @Test
  public void testTokenSequenceMatchesWildcard() throws IOException {
    CoreMap doc = createDocument("word1 word2");

    // Test sequence with groups
    TokenSequencePattern p = TokenSequencePattern.compile( "[]{2}|[]");
    TokenSequenceMatcher m = p.getMatcher(doc.get(CoreAnnotations.TokensAnnotation.class));
    boolean matches = m.matches();
    assertTrue(matches);

    // Reverse order
    p = TokenSequencePattern.compile( "[]|[]{2}");
    m = p.getMatcher(doc.get(CoreAnnotations.TokensAnnotation.class));
    matches = m.matches();
    assertTrue(matches);

    // Using {1,2}
    p = TokenSequencePattern.compile( "[]{1,2}");
    m = p.getMatcher(doc.get(CoreAnnotations.TokensAnnotation.class));
    matches = m.matches();
    assertTrue(matches);
  }

  @Test
  public void testTokenSequenceMatcherABs() throws IOException {
    CoreMap doc = createDocument("A A A A A A A B A A B A C A E A A A A A A A A A A A B A A A");

    // Test sequence with groups
    TokenSequencePattern p = TokenSequencePattern.compile( "/A/+ B");
    TokenSequenceMatcher m = p.getMatcher(doc.get(CoreAnnotations.TokensAnnotation.class));
    boolean match = m.find();
    assertTrue(match);
    assertEquals(0, m.groupCount());
    assertEquals("A A A A A A A B", m.group());
    match = m.find();
    assertTrue(match);
    assertEquals(0, m.groupCount());
    assertEquals("A A B", m.group());
    match = m.find();
    assertTrue(match);
    assertEquals(0, m.groupCount());
    assertEquals("A A A A A A A A A A A B", m.group());
    match = m.find();
    assertFalse(match);

    p = TokenSequencePattern.compile( "(/A/+ B)+");
    m = p.getMatcher(doc.get(CoreAnnotations.TokensAnnotation.class));
    match = m.find();
    assertTrue(match);
    assertEquals(1, m.groupCount());
    assertEquals("A A A A A A A B A A B", m.group());
    match = m.find();
    assertTrue(match);
    assertEquals(1, m.groupCount());
    assertEquals("A A A A A A A A A A A B", m.group());
    match = m.find();
    assertFalse(match);

    p = TokenSequencePattern.compile( "( A+ ( /B/+ )? )*");
    m = p.getMatcher(doc.get(CoreAnnotations.TokensAnnotation.class));
    match = m.find();
    assertTrue(match);
    assertEquals(2, m.groupCount());
    assertEquals("A A A A A A A B A A B A", m.group());
    match = m.find();
    assertTrue(match);
    assertEquals(2, m.groupCount());
    assertEquals("A", m.group());
    match = m.find();
    assertTrue(match);
    assertEquals(2, m.groupCount());
    assertEquals("A A A A A A A A A A A B A A A", m.group());
    match = m.find();
    assertFalse(match);

    p = TokenSequencePattern.compile( "(/A/+ /B/+ )+");
    m = p.getMatcher(doc.get(CoreAnnotations.TokensAnnotation.class));
    match = m.find();
    assertTrue(match);
    assertEquals(1, m.groupCount());
    assertEquals("A A A A A A A B A A B", m.group());
    match = m.find();
    assertTrue(match);
    assertEquals(1, m.groupCount());
    assertEquals("A A A A A A A A A A A B", m.group());
    match = m.find();
    assertFalse(match);

    p = TokenSequencePattern.compile( "(/A/+ /C/? /A/* )+");
    m = p.getMatcher(doc.get(CoreAnnotations.TokensAnnotation.class));
    match = m.find();
    assertTrue(match);
    assertEquals(1, m.groupCount());
    assertEquals("A A A A A A A", m.group());
    match = m.find();
    assertTrue(match);
    assertEquals(1, m.groupCount());
    assertEquals("A A", m.group());
    match = m.find();
    assertTrue(match);
    assertEquals(1, m.groupCount());
    assertEquals("A C A", m.group());
    match = m.find();
    assertTrue(match);
    assertEquals(1, m.groupCount());
    assertEquals("A A A A A A A A A A A", m.group());
    match = m.find();
    assertTrue(match);
    assertEquals(1, m.groupCount());
    assertEquals("A A A", m.group());
    match = m.find();
    assertFalse(match);
  }

  @Test
  public void testTokenSequenceMatcherMultiNodePattern() throws IOException {
    CoreMap doc = createDocument("blah four-years blah blah four - years");

    // Test sequence with groups
    CoreMapNodePattern nodePattern  = CoreMapNodePattern.valueOf("four\\s*-?\\s*years");
    SequencePattern.MultiNodePatternExpr expr = new SequencePattern.MultiNodePatternExpr(
            new MultiCoreMapNodePattern(nodePattern));
    TokenSequencePattern p = TokenSequencePattern.compile(expr);
    TokenSequenceMatcher m = p.getMatcher(doc.get(CoreAnnotations.TokensAnnotation.class));
    boolean match = m.find();
    assertTrue(match);
    assertEquals(0, m.groupCount());
    assertEquals("four-years", m.group());
    match = m.find();
    assertTrue(match);
    assertEquals(0, m.groupCount());
    assertEquals("four - years", m.group());
    match = m.find();
    assertFalse(match);

    p = TokenSequencePattern.compile("(?m) /four\\s*-?\\s*years/");
    m = p.getMatcher(doc.get(CoreAnnotations.TokensAnnotation.class));
    match = m.find();
    assertTrue(match);
    assertEquals(0, m.groupCount());
    assertEquals("four-years", m.group());
    match = m.find();
    assertTrue(match);
    assertEquals(0, m.groupCount());
    assertEquals("four - years", m.group());
    match = m.find();
    assertFalse(match);

    p = TokenSequencePattern.compile("(?m){2,3} /four\\s*-?\\s*years/");
    m = p.getMatcher(doc.get(CoreAnnotations.TokensAnnotation.class));
    match = m.find();
    assertTrue(match);
    assertEquals(0, m.groupCount());
    assertEquals("four - years", m.group());
    match = m.find();
    assertFalse(match);

    p = TokenSequencePattern.compile( "(?m){1,2} /four\\s*-?\\s*years/");
    m = p.getMatcher(doc.get(CoreAnnotations.TokensAnnotation.class));
    match = m.find();
    assertTrue(match);
    assertEquals(0, m.groupCount());
    assertEquals("four-years", m.group());
    match = m.find();
    assertFalse(match);

    p = TokenSequencePattern.compile("(?m){1,3} /four\\s*-?\\s*years/ ==> &annotate( { ner=YEAR } )");
    m = p.getMatcher(doc.get(CoreAnnotations.TokensAnnotation.class));
    match = m.find();
    assertTrue(match);
    assertEquals(0, m.groupCount());
    assertEquals("four-years", m.group());
    p.getAction().apply(m, 0);
    match = m.find();
    assertTrue(match);
    assertEquals(0, m.groupCount());
    assertEquals("four - years", m.group());
    SequenceMatchResult<CoreMap> res = p.getAction().apply(m, 0);
    match = m.find();
    assertFalse(match);

    p = TokenSequencePattern.compile( "[ { ner:YEAR } ]+");
    m = p.getMatcher(res.elements());
    match = m.find();
    assertTrue(match);
    assertEquals(0, m.groupCount());
    assertEquals("four-years", m.group());
    match = m.find();
    assertTrue(match);
    assertEquals(0, m.groupCount());
    assertEquals("four - years", m.group());
    match = m.find();
    assertFalse(match);
  }

  @Test
  public void testTokenSequenceMatcherMultiNodePattern2() throws IOException {
    CoreMap doc = createDocument("Replace the lamp with model wss.32dc55c3e945384dbc5e533ab711fd24");

    // Greedy
    TokenSequencePattern p = TokenSequencePattern.compile("/model/ ((?m){1,4}/\\w+\\.\\w+/)");
    TokenSequenceMatcher m = p.getMatcher(doc.get(CoreAnnotations.TokensAnnotation.class));
    boolean match = m.find();
    assertTrue(match);
    assertEquals(1, m.groupCount());
    assertEquals("model wss.32dc55c3e945384dbc5e533ab711fd24", m.group());
    assertEquals("wss.32dc55c3e945384dbc5e533ab711fd24", m.group(1));
    match = m.find();
    assertFalse(match);

    // Reluctant
    p = TokenSequencePattern.compile("/model/ ((?m){1,4}?/\\w+\\.\\w+/)");
    m = p.getMatcher(doc.get(CoreAnnotations.TokensAnnotation.class));
    match = m.find();
    assertTrue(match);
    assertEquals(1, m.groupCount());
    assertEquals("model wss.32", m.group());
    assertEquals("wss.32", m.group(1));
    match = m.find();
    assertFalse(match);
  }

  @Test
  public void testTokenSequenceMatcherBackRef() throws IOException {
    CoreMap doc = createDocument("A A A A A A A B A A B A C A E A A A A A A A A A A A B A A A");

    // Test sequence with groups
    TokenSequencePattern p = TokenSequencePattern.compile( "(/A/+) B \\1");
    TokenSequenceMatcher m = p.getMatcher(doc.get(CoreAnnotations.TokensAnnotation.class));
    boolean match = m.find();
    assertTrue(match);
    assertEquals(1, m.groupCount());
    assertEquals("A A B A A", m.group());
    match = m.find();
    assertTrue(match);
    assertEquals(1, m.groupCount());
    assertEquals("A A A B A A A", m.group());
    match = m.find();
    assertFalse(match);

  }

  @Test
  public void testMultiPatternMatcher() throws IOException {
    CoreMap doc = createDocument(testText1);

    // Test simple sequence
    TokenSequencePattern p1 = TokenSequencePattern.compile("/Archbishop/ /of/ /Canterbury/");
    p1.setPriority(1);
    TokenSequencePattern p2 = TokenSequencePattern.compile("/[a-zA-Z]+/{1,2}  /of/ /[a-zA-Z]+/+");
    MultiPatternMatcher<CoreMap> m = new MultiPatternMatcher<CoreMap>(p2,p1);
    List<SequenceMatchResult<CoreMap>> matched = m.findNonOverlapping(doc.get(CoreAnnotations.TokensAnnotation.class));
    assertEquals(4, matched.size());
    assertEquals("first Bishop of London", matched.get(0).group());
    assertEquals("Archbishop of Canterbury", matched.get(1).group());
    assertEquals("a member of the Gregorian mission sent to England to convert the", matched.get(2).group());
    assertEquals("as Bishop of London in", matched.get(3).group());
  }

  @Test
  public void testStringPatternMatchCaseInsensitive() throws IOException {
    CoreMap doc = createDocument(testText1);

    // Test simple sequence
    Env env = TokenSequencePattern.getNewEnv();
    env.setDefaultStringPatternFlags(Pattern.CASE_INSENSITIVE);
    TokenSequencePattern p = TokenSequencePattern.compile(env, "/archbishop/ /of/ /canterbury/");
    TokenSequenceMatcher m = p.getMatcher(doc.get(CoreAnnotations.TokensAnnotation.class));
    assertTrue(m.find());
    assertEquals("Archbishop of Canterbury", m.group());
    assertFalse(m.find());

    p = TokenSequencePattern.compile(env, "/ARCHBISHOP/ /OF/ /CANTERBURY/");
    m = p.getMatcher(doc.get(CoreAnnotations.TokensAnnotation.class));
    assertTrue(m.find());
    assertEquals("Archbishop of Canterbury", m.group());
    assertFalse(m.find());
  }

  @Test
  public void testStringMatchCaseInsensitive() throws IOException {
    CoreMap doc = createDocument(testText1);

    // Test simple sequence
    Env env = TokenSequencePattern.getNewEnv();
    env.setDefaultStringMatchFlags(NodePattern.CASE_INSENSITIVE);
    TokenSequencePattern p = TokenSequencePattern.compile(env, "archbishop of canterbury");
    TokenSequenceMatcher m = p.getMatcher(doc.get(CoreAnnotations.TokensAnnotation.class));
    assertTrue(m.find());
    assertEquals("Archbishop of Canterbury", m.group());
    assertFalse(m.find());

    p = TokenSequencePattern.compile(env, "ARCHBISHOP OF CANTERBURY");
    m = p.getMatcher(doc.get(CoreAnnotations.TokensAnnotation.class));
    assertTrue(m.find());
    assertEquals("Archbishop of Canterbury", m.group());
    assertFalse(m.find());
  }

  //just to test if a pattern is compiling or not
  @Test
  public void testCompile() {
    String s = "(?$se \"matching\" \"this\"|\"don't\")";
    CoreMap doc = createDocument("does this do matching this");
    TokenSequencePattern p = TokenSequencePattern.compile(s);
    TokenSequenceMatcher m = p.getMatcher(doc.get(CoreAnnotations.TokensAnnotation.class));
    boolean match = m.find();
    assertTrue(match);
    //assertEquals(m.group(), "matching this");
  }

  @Test
  public void testBindingCompile(){
    Env env = TokenSequencePattern.getNewEnv();
    env.bind("wordname",CoreAnnotations.TextAnnotation.class);
    String s = "[wordname:\"name\"]{1,2}";
    TokenSequencePattern p = TokenSequencePattern.compile(env, s);
  }

// // This does not work!!!
//  @Test
//  public void testNoBindingCompile(){
//    Env env = TokenSequencePattern.getNewEnv();
//    String s = "[" + CoreAnnotations.TextAnnotation.class.getName()+":\"name\"]{1,2}";
//    TokenSequencePattern p = TokenSequencePattern.compile(env, s);
//  }

  @Test
  public void testCaseInsensitive1(){
    Env env = TokenSequencePattern.getNewEnv();
    env.setDefaultStringPatternFlags(Pattern.CASE_INSENSITIVE);
    env.setDefaultStringMatchFlags(NodePattern.CASE_INSENSITIVE);
    String s = "for /President/";
    CoreMap doc = createDocument("for president");
    TokenSequencePattern p = TokenSequencePattern.compile(env, s);
    TokenSequenceMatcher m = p.getMatcher(doc.get(CoreAnnotations.TokensAnnotation.class));
    boolean match = m.find();
    assertTrue(match);
  }

  @Test
  public void testCaseInsensitive2(){
    Env env = TokenSequencePattern.getNewEnv();
    env.setDefaultStringPatternFlags(Pattern.CASE_INSENSITIVE);
    env.setDefaultStringMatchFlags(NodePattern.CASE_INSENSITIVE);

    String s = "for president";
    CoreMap doc = createDocument("for President");

    TokenSequencePattern p = TokenSequencePattern.compile(env, s);
    TokenSequenceMatcher m = p.getMatcher(doc.get(CoreAnnotations.TokensAnnotation.class));
    boolean match = m.find();
    assertTrue(match);
  }
}
