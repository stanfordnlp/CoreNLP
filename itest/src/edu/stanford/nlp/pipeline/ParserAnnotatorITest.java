package edu.stanford.nlp.pipeline;

import junit.framework.TestCase;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.Label;
import edu.stanford.nlp.parser.common.ParserConstraint;
import edu.stanford.nlp.parser.common.ParserAnnotations.ConstraintAnnotation;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.TreeCoreAnnotations;
import edu.stanford.nlp.util.CoreMap;

/**
 * A really weak-sauce test for the ParserAnnotator.
 *
 * @author dramage
 */
public class ParserAnnotatorITest extends TestCase {
  private static AnnotationPipeline pipeline = null;
  private static AnnotationPipeline noPOSPipeline = null;

  private static AnnotationPipeline noParserPipeline = null;
  private static AnnotationPipeline parserOnlyPipeline = null;

  private static ParserAnnotator parser = null;

  // TODO: kind of silly to make so many copies of the ParserAnnotator
  private static AnnotationPipeline timeoutPipeline = null;
  private static AnnotationPipeline threaded3TimeoutPipeline = null;
  private static AnnotationPipeline threaded4TimeoutPipeline = null;

  private static AnnotationPipeline threaded3Pipeline = null;
  private static AnnotationPipeline threaded4Pipeline = null;

  /** this one will flatten all the trees */
  private static AnnotationPipeline flatPipeline = null;

  public void setUp() throws Exception {
    synchronized(ParserAnnotatorITest.class) {
      if (pipeline != null)
        return;

      parser = new ParserAnnotator(false, -1);
      pipeline = new AnnotationPipeline();
      pipeline.addAnnotator(new TokenizerAnnotator(false, "en"));
      pipeline.addAnnotator(new WordsToSentencesAnnotator(false));
      pipeline.addAnnotator(new POSTaggerAnnotator(false));
      pipeline.addAnnotator(parser);

      noPOSPipeline = new AnnotationPipeline();
      noPOSPipeline.addAnnotator(new TokenizerAnnotator(false, "en"));
      noPOSPipeline.addAnnotator(new WordsToSentencesAnnotator(false));
      noPOSPipeline.addAnnotator(parser);

      noParserPipeline = new AnnotationPipeline();
      noParserPipeline.addAnnotator(new TokenizerAnnotator(false, "en"));
      noParserPipeline.addAnnotator(new WordsToSentencesAnnotator(false));

      parserOnlyPipeline = new AnnotationPipeline();
      parserOnlyPipeline.addAnnotator(parser);

      Properties props = new Properties();
      props.setProperty("parse.maxtime", "1");
      props.setProperty("annotators", "tokenize, ssplit, parse");
      timeoutPipeline = new StanfordCoreNLP(props);

      props = new Properties();
      props.setProperty("parse.maxtime", "1");
      props.setProperty("parse.nthreads", "3");
      props.setProperty("annotators", "tokenize, ssplit, parse");
      threaded3TimeoutPipeline = new StanfordCoreNLP(props);

      props.setProperty("parse.nthreads", "4");
      threaded4TimeoutPipeline = new StanfordCoreNLP(props);

      props.setProperty("annotators", "tokenize, ssplit, pos, parse");
      props.setProperty("parse.maxtime", "-1");
      threaded4Pipeline = new StanfordCoreNLP(props);

      props.setProperty("annotators", "tokenize, ssplit, pos, parse");
      props.setProperty("parse.nthreads", "3");
      threaded3Pipeline = new StanfordCoreNLP(props);

      props = new Properties();
      props.setProperty("parse.maxheight", "1");      
      props.setProperty("annotators", "tokenize, ssplit, parse");
      flatPipeline = new StanfordCoreNLP(props);
    }
  }

  public void testNoPOSParserAnnotator() throws Exception {
    Annotation document = new Annotation("John Bauer works at Stanford.");
    noPOSPipeline.annotate(document);
    assertEquals(1, document.get(CoreAnnotations.SentencesAnnotation.class).size());
    CoreMap sentence = document.get(CoreAnnotations.SentencesAnnotation.class).get(0);
    Tree parse = sentence.get(TreeCoreAnnotations.TreeAnnotation.class);
    assertEquals("(ROOT (S (NP (NNP John) (NNP Bauer)) (VP (VBZ works) (PP (IN at) (NP (NNP Stanford)))) (. .)))", parse.toString());
    List<CoreLabel> tokens = sentence.get(CoreAnnotations.TokensAnnotation.class);
    List<Label> leaves = parse.yield();
    assertEquals(6, tokens.size());
    assertEquals(6, leaves.size());
    String[] expectedTags = {"NNP", "NNP", "VBZ", "IN", "NNP", "."};
    for (int i = 0; i < tokens.size(); ++i) {
      assertEquals(expectedTags[i], tokens.get(i).tag());
      assertTrue(leaves.get(i) instanceof CoreLabel);
      assertEquals(expectedTags[i], ((CoreLabel) leaves.get(i)).tag());
    }
  }

  public void testParserAnnotator() {
    Annotation document = new Annotation(TEXT);
    pipeline.annotate(document);

    int i = 0;
    for (CoreMap sentence : document.get(CoreAnnotations.SentencesAnnotation.class)) {
      Tree parse = sentence.get(TreeCoreAnnotations.TreeAnnotation.class);
      assertEquals(parse.toString(), ANSWER[i++]);
    }
  }

  public void testThreadedAnnotator() {
    Annotation document = new Annotation(TEXT + TEXT + TEXT + TEXT + TEXT);
    threaded4Pipeline.annotate(document);
    verifyAnswers(document, ANSWER);

    document = new Annotation(TEXT + TEXT + TEXT + TEXT + TEXT);
    threaded3Pipeline.annotate(document);
    verifyAnswers(document, ANSWER);

    document = new Annotation(TEXT);
    threaded4Pipeline.annotate(document);
    verifyAnswers(document, ANSWER);
  }

  public void testMaxLen() {
    Properties props = new Properties();
    props.setProperty("annotators", "tokenize, ssplit, parse");
    props.setProperty("parse.maxlen", "7");
    StanfordCoreNLP pipeline = new StanfordCoreNLP(props);
    Annotation document = new Annotation(TEXT);
    pipeline.annotate(document);

    verifyAnswers(document, XPARSES);

    props.setProperty("annotators", "tokenize, ssplit, pos, parse");
    props.setProperty("parse.maxlen", "8");
    pipeline = new StanfordCoreNLP(props);
    document = new Annotation(TEXT);
    pipeline.annotate(document);
    assertEquals(ANSWER[0], document.get(CoreAnnotations.SentencesAnnotation.class).get(0).get(TreeCoreAnnotations.TreeAnnotation.class).toString());

    props.setProperty("annotators", "tokenize, ssplit, parse");
    props.setProperty("parse.maxlen", "8");
    pipeline = new StanfordCoreNLP(props);
    document = new Annotation(TEXT);
    pipeline.annotate(document);

    assertEquals(XPARSES[1], document.get(CoreAnnotations.SentencesAnnotation.class).get(1).get(TreeCoreAnnotations.TreeAnnotation.class).toString());
    assertEquals(XPARSES[2], document.get(CoreAnnotations.SentencesAnnotation.class).get(2).get(TreeCoreAnnotations.TreeAnnotation.class).toString());
  }

  /**
   * Test what happens if you put a constraint on the parse
   */
  public void testConstraints() {
    String expectedResult = "(ROOT (S (NP (PRP$ My) (NN dog)) (ADVP (RB also)) (VP (VBZ likes) (S (VP (VBG eating) (NP (NN sausage))))) (. .)))";
    Annotation annotation = new Annotation("My dog also likes eating sausage.");
    noParserPipeline.annotate(annotation);
    CoreMap sentence = annotation.get(CoreAnnotations.SentencesAnnotation.class).get(0);

    parserOnlyPipeline.annotate(annotation);
    assertEquals(expectedResult, sentence.get(TreeCoreAnnotations.TreeAnnotation.class).toString());

    ParserConstraint constraint = new ParserConstraint(0, 2, "INTJ");
    List<ParserConstraint> constraints = new ArrayList<>();
    constraints.add(constraint);
    sentence.set(ConstraintAnnotation.class, constraints);

    parserOnlyPipeline.annotate(annotation);
    String result = sentence.get(TreeCoreAnnotations.TreeAnnotation.class).toString();
    assertFalse("Tree should not match the original tree any more",
                expectedResult.equals(result));
    assertTrue("Tree should be forced to contain INTJ",
               result.indexOf("INTJ") >= 0);
  }

  /**
   * Tests that if you run a parser annotator with an absurdly low
   * timeout, all sentences are successfully labeled with X trees, as
   * opposed to null trees or not timing out
   */
  public void testTimeout() {
    Annotation document = new Annotation(TEXT);
    timeoutPipeline.annotate(document);
    verifyAnswers(document, XPARSES);
  }

  /**
   * Tests that if you run a threaded parser annotator on input text,
   * all sentences get successfully converted into X trees after they
   * time out.  Incidentally, this sort of tests that the threaded
   * parser annotator adds output in the right order.
   */
  public void testThreadedTimeout() {
    for (int i = 0; i < 20; ++i) {
      Annotation document = new Annotation(TEXT + TEXT);
      threaded3TimeoutPipeline.annotate(document);
      verifyAnswers(document, XPARSES);

      document = new Annotation(TEXT + TEXT + TEXT + TEXT + TEXT);
      threaded4TimeoutPipeline.annotate(document);
      verifyAnswers(document, XPARSES);
    }
  }


  /**
   * Tests that if you get parses which are too tall, the annotator flattens them
   */
  public void testFlatten() {
    Annotation document = new Annotation(TEXT);
    flatPipeline.annotate(document);
    verifyAnswers(document, TAGGED_XPARSES);
  }


  private void assertParseOK(ParserAnnotator parser) {
    AnnotationPipeline pipeline = new AnnotationPipeline();
    pipeline.addAnnotator(new TokenizerAnnotator(false, "en"));
    pipeline.addAnnotator(new WordsToSentencesAnnotator(false));
    pipeline.addAnnotator(parser);
    Annotation document = new Annotation("John Bauer works at Stanford.");
    pipeline.annotate(document);
    assertEquals(1, document.get(CoreAnnotations.SentencesAnnotation.class).size());
    CoreMap sentence = document.get(CoreAnnotations.SentencesAnnotation.class).get(0);
    Tree parse = sentence.get(TreeCoreAnnotations.TreeAnnotation.class);
    assertEquals("(ROOT (S (NP (NNP John) (NNP Bauer)) (VP (VBZ works) (PP (IN at) (NP (NNP Stanford)))) (. .)))", parse.toString());
    List<CoreLabel> tokens = sentence.get(CoreAnnotations.TokensAnnotation.class);
    List<Label> leaves = parse.yield();
    assertEquals(6, tokens.size());
    assertEquals(6, leaves.size());
    String[] expectedTags = {"NNP", "NNP", "VBZ", "IN", "NNP", "."};
    for (int i = 0; i < tokens.size(); ++i) {
      assertEquals(expectedTags[i], tokens.get(i).tag());
      assertTrue(leaves.get(i) instanceof CoreLabel);
      assertEquals(expectedTags[i], ((CoreLabel) leaves.get(i)).tag());
    }

  }

  /**
   * Test various ways of creating an annotator, making sure they don't crash.
   */
  public void testAnnotatorConstructors() {
    assertParseOK(new ParserAnnotator(false, -1));
    assertParseOK(new ParserAnnotator(false, 100));

    Properties props = new Properties();
    props.setProperty("annotators", "parse");
    assertParseOK(new ParserAnnotator("parse", props));

  }


  public void verifyAnswers(Annotation document, String[] expected) {
    int i = 0;
    for (CoreMap sentence : document.get(CoreAnnotations.SentencesAnnotation.class)) {
      Tree parse = sentence.get(TreeCoreAnnotations.TreeAnnotation.class);
      assertFalse("Sentence " + i + " was null", parse == null);
      assertEquals(expected[i++ % expected.length], parse.toString());
    }
  }


  static final String TEXT = "I saw him ordering them to saw. Jack 's father has n't played\ngolf since 20 years ago . I 'm going to the\nbookstore to return a book Jack and his friends bought me .  ";

  static final String[] ANSWER = {
      // TODO: this is actually the wrong parse!
      "(ROOT (S (NP (PRP I)) (VP (VBD saw) (NP (PRP him)) (S (VP (VBG ordering) (NP (PRP them)) (S (VP (TO to) (VP (VB saw))))))) (. .)))",
      "(ROOT (S (NP (NP (NNP Jack) (POS 's)) (NN father)) (VP (VBZ has) (RB n't) (VP (VBN played) (NP (NN golf)) (ADVP (IN since) (NP (CD 20) (NNS years)) (RB ago)))) (. .)))",
      "(ROOT (S (NP (PRP I)) (VP (VBP 'm) (VP (VBG going) (PP (IN to) (NP (DT the) (NN bookstore))) (S (VP (TO to) (VP (VB return) (NP (NP (DT a) (NN book)) (SBAR (S (NP (NP (NNP Jack)) (CC and) (NP (PRP$ his) (NNS friends))) (VP (VBD bought) (NP (PRP me))))))))))) (. .)))"
  };

  static final String[] TAGGED_XPARSES = {
      "(X (PRP I) (VBD saw) (PRP him) (VBG ordering) (PRP them) (IN to) (NN saw) (. .))",
      "(X (NNP Jack) (POS 's) (NN father) (VBZ has) (RB n't) (VBN played) (NN golf) (IN since) (CD 20) (NNS years) (RB ago) (. .))",
      "(X (PRP I) (VBP 'm) (VBG going) (IN to) (DT the) (NN bookstore) (TO to) (VB return) (DT a) (NN book) (NN Jack) (CC and) (PRP$ his) (NNS friends) (VBD bought) (PRP me) (. .))"
  };


  static final String[] XPARSES = {
    "(X (XX I) (XX saw) (XX him) (XX ordering) (XX them) (XX to) (XX saw) (XX .))",
    "(X (XX Jack) (XX 's) (XX father) (XX has) (XX n't) (XX played) (XX golf) (XX since) (XX 20) (XX years) (XX ago) (XX .))",
    "(X (XX I) (XX 'm) (XX going) (XX to) (XX the) (XX bookstore) (XX to) (XX return) (XX a) (XX book) (XX Jack) (XX and) (XX his) (XX friends) (XX bought) (XX me) (XX .))"
  };
}


