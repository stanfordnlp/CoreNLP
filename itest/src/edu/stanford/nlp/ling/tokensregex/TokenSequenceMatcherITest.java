package edu.stanford.nlp.ling.tokensregex;

import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.pipeline.*;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.Pair;
import junit.framework.TestCase;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class TokenSequenceMatcherITest extends TestCase {

  private static AnnotationPipeline pipeline = null;

  @Override
  public void setUp() throws Exception {
    synchronized(TokenSequenceMatcherITest.class) {
      if (pipeline == null) {
        pipeline = new AnnotationPipeline();
        pipeline.addAnnotator(new PTBTokenizerAnnotator(false));
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

  private static String testText = "the number were one, two and fifty.";
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

  private static String testText1 = "Mellitus was the first Bishop of London, the third Archbishop of Canterbury, and a member of the Gregorian mission  sent to England to convert the Anglo-Saxons. He arrived in 601 AD, and was consecrated as Bishop of London in 604.";
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
    assertEquals("London in 604 .", m.group());
    match = m.find();
    assertFalse(match);
  }

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

  public void testTokenSequenceMatcherConjAll() throws IOException {
    CoreMap doc = createDocument(testText1);
    TokenSequencePattern p = TokenSequencePattern.compile(
            "(?: (/[A-Za-z]+/{1,2}) /of/ (/[A-Za-z]+/{1,3}?) ) & (?: (/.*/*) /Bishop/ /.*/*? )");

    TokenSequenceMatcher m = p.getMatcher(doc.get(CoreAnnotations.TokensAnnotation.class));
    m.setFindType(SequenceMatcher.FindType.FIND_ALL);
    // Test finding of ALL matching sequences with conjunctions
    // NOTE: Not all sequences are found for some reason - missing sequences starting with just Bishop....
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
    assertFalse(match);
  }

  public void testTokenSequenceMatcherAll() throws IOException {
    CoreMap doc = createDocument(testText1);
    TokenSequencePattern p = TokenSequencePattern.compile(
            "(/[A-Za-z]+/{1,2}) /of/ (/[A-Za-z]+/{1,3}?) ");

    TokenSequenceMatcher m = p.getMatcher(doc.get(CoreAnnotations.TokensAnnotation.class));
    m.setFindType(SequenceMatcher.FindType.FIND_ALL);
    // Test finding of ALL matching sequences
    // NOTE: when using FIND_ALL greedy/recluctant modifiers are not enforced
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

  public void testTokenSequenceMatcher4() throws IOException {
    CoreMap doc = createDocument(testText1);

    // Test sequence with groups
    TokenSequencePattern p = TokenSequencePattern.compile(
                      new SequencePattern.RepeatPatternExpr(
                                    getSequencePatternExpr("[A-Za-z]+"), 0, -1));

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

  public void testTokenSequenceMatcher7() throws IOException {
    CoreMap doc = createDocument(testText1);

    // Test sequence with groups
    TokenSequencePattern p = TokenSequencePattern.compile( " ( [ /[A-Za-z]+/ ]{1,2} )  [ /of/ ] ( [ /[A-Za-z]+/ ]{1,3} )");
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
  }

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

    p = TokenSequencePattern.compile( "[ { word>=2000 } ]+");
    m = p.getMatcher(doc.get(CoreAnnotations.TokensAnnotation.class));
    match = m.find();
    assertTrue(match);
    assertEquals(0, m.groupCount());
    assertEquals("2002", m.group());
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

    p = TokenSequencePattern.compile( "[ { word<=2000 } ]+");
    m = p.getMatcher(doc.get(CoreAnnotations.TokensAnnotation.class));
    match = m.find();
    assertTrue(match);
    assertEquals(0, m.groupCount());
    assertEquals("3", m.group());
    match = m.find();
    assertFalse(match);

    p = TokenSequencePattern.compile( "[ { word<2000 } ]+");
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
    assertEquals("January 3 , 2002", m.group());
    match = m.find();
    assertFalse(match);

    p = TokenSequencePattern.compile( "[ { ner::NOT_NIL } ]+");
    m = p.getMatcher(doc.get(CoreAnnotations.TokensAnnotation.class));
    match = m.find();
    assertTrue(match);
    assertEquals(0, m.groupCount());
    assertEquals("January 3 , 2002", m.group());
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

  /*  p = TokenSequencePattern.compile( "( A+ ( /B/+ )? )*");
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
    assertFalse(match);              */

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

    p = TokenSequencePattern.compile( "(?m) /four\\s*-?\\s*years/");
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

    p = TokenSequencePattern.compile( "(?m){2,3} /four\\s*-?\\s*years/");
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

    p = TokenSequencePattern.compile( "(?m){1,3} /four\\s*-?\\s*years/ ==> &annotate( { ner=YEAR } )");
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

}
