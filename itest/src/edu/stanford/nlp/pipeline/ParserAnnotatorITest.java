package edu.stanford.nlp.pipeline;

import junit.framework.TestCase;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import edu.stanford.nlp.dcoref.Constants;
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

  public void setUp() throws Exception {
    synchronized(ParserAnnotatorITest.class) {
      if (pipeline != null)
        return;

      parser = new ParserAnnotator(false, -1);
      pipeline = new AnnotationPipeline();
      pipeline.addAnnotator(new PTBTokenizerAnnotator(false));
      pipeline.addAnnotator(new WordsToSentencesAnnotator(false));
      pipeline.addAnnotator(new POSTaggerAnnotator(false));
      pipeline.addAnnotator(parser);

      noPOSPipeline = new AnnotationPipeline();
      noPOSPipeline.addAnnotator(new PTBTokenizerAnnotator(false));
      noPOSPipeline.addAnnotator(new WordsToSentencesAnnotator(false));
      noPOSPipeline.addAnnotator(parser);

      noParserPipeline = new AnnotationPipeline();
      noParserPipeline.addAnnotator(new PTBTokenizerAnnotator(false));
      noParserPipeline.addAnnotator(new WordsToSentencesAnnotator(false));

      parserOnlyPipeline = new AnnotationPipeline();
      parserOnlyPipeline.addAnnotator(parser);
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

  public void testParserAnnotator() throws Exception {    
    Annotation document = new Annotation(TEXT);    
    pipeline.annotate(document);
    
    int i = 0;
    for (CoreMap sentence : document.get(CoreAnnotations.SentencesAnnotation.class)) {
      Tree parse = sentence.get(TreeCoreAnnotations.TreeAnnotation.class);
      assertEquals(parse.toString(), ANSWER[i++]);
    }
  }

  public void testMaxLen() {
    Properties props = new Properties();
    props.setProperty("annotators", "tokenize, ssplit, parse");
    props.setProperty("parse.maxlen", "7");
    StanfordCoreNLP pipeline = new StanfordCoreNLP(props);
    Annotation document = new Annotation(TEXT);
    pipeline.annotate(document);
    
    int i = 0;
    for (CoreMap sentence : document.get(CoreAnnotations.SentencesAnnotation.class)) {
      Tree parse = sentence.get(TreeCoreAnnotations.TreeAnnotation.class);
      assertEquals(XPARSES[i++], parse.toString());
    } 

    props.setProperty("parse.maxlen", "8");
    pipeline = new StanfordCoreNLP(props);
    document = new Annotation(TEXT);
    pipeline.annotate(document);

    assertEquals(ANSWER[0], document.get(CoreAnnotations.SentencesAnnotation.class).get(0).get(TreeCoreAnnotations.TreeAnnotation.class).toString());
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

    ParserConstraint constraint = new ParserConstraint(0, 2, "SBAR|SBAR[^a-zA-Z].*");
    List<ParserConstraint> constraints = new ArrayList<ParserConstraint>();
    constraints.add(constraint);
    sentence.set(ConstraintAnnotation.class, constraints);

    parserOnlyPipeline.annotate(annotation);
    String result = sentence.get(TreeCoreAnnotations.TreeAnnotation.class).toString();
    assertFalse("Tree should not match the original tree any more",
                expectedResult.equals(result));
    assertTrue("Tree should be forced to contain SBAR",
               result.indexOf("SBAR") >= 0);
  }


  private void assertParseOK(ParserAnnotator parser) {
    AnnotationPipeline pipeline = new AnnotationPipeline();
    pipeline.addAnnotator(new PTBTokenizerAnnotator(false));
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

  static final String TEXT = "I saw him ordering them to saw. Jack 's father has n't played\ngolf since 20 years ago . I 'm going to the\nbookstore to return a book Jack and his friends bought me .";

  static final String[] ANSWER = {
      // TODO: this is actually the wrong parse!
      "(ROOT (S (NP (PRP I)) (VP (VBD saw) (S (NP (PRP him)) (VP (VBG ordering) (NP (PRP them)) (PP (TO to) (NP (NN saw)))))) (. .)))",

      "(ROOT (S (NP (NP (NNP Jack) (POS 's)) (NN father)) (VP (VBZ has) (RB n't) (VP (VBN played) (NP (NN golf)) (PP (IN since) (ADVP (NP (CD 20) (NNS years)) (RB ago))))) (. .)))",

      "(ROOT (S (NP (PRP I)) (VP (VBP 'm) (VP (VBG going) (PP (TO to) (NP (DT the) (NN bookstore))) (S (VP (TO to) (VP (VB return) (NP (NP (DT a) (NN book)) (SBAR (S (NP (NP (NNP Jack)) (CC and) (NP (PRP$ his) (NNS friends))) (VP (VBD bought) (NP (PRP me))))))))))) (. .)))"
  };

  static final String[] XPARSES = {
    "(X (X I) (X saw) (X him) (X ordering) (X them) (X to) (X saw) (X .))",
    "(X (X Jack) (X 's) (X father) (X has) (X n't) (X played) (X golf) (X since) (X 20) (X years) (X ago) (X .))",
    "(X (X I) (X 'm) (X going) (X to) (X the) (X bookstore) (X to) (X return) (X a) (X book) (X Jack) (X and) (X his) (X friends) (X bought) (X me) (X .))"
  };
}


