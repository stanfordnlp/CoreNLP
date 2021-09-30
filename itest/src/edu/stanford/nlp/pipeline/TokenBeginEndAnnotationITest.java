package edu.stanford.nlp.pipeline;

import edu.stanford.nlp.io.*;
import edu.stanford.nlp.ling.*;
import edu.stanford.nlp.util.*;

import junit.framework.TestCase;

import java.util.*;

public class TokenBeginEndAnnotationITest extends TestCase {

  public String basicText = "Joe Smith went to Hawaii. His vacation lasted three weeks. He had a great time.";

  public String basicNewlineText =
      "\nJoe Smith went to Hawaii.\n\n\nHis vacation lasted three weeks.  He had a great time.\nHe plans to go again.";

  public String xmlDocPath = String.format("%s/stanford-corenlp-testing/test-docs/ENG_DF_001471_20160410_G00A00PY2.xml", TestPaths.testHome());

  public String xmlDocPipelineProps = String.format("%s/stanford-corenlp-testing/test-props/kbp_2016.properties", TestPaths.testHome());

  public StanfordCoreNLP pipeline;

  public StanfordCoreNLP xmlPipeline;


  @Override
  public void setUp() {
    // set up pipeline and serializer
    Properties props = new Properties();
    props.setProperty("annotators", "tokenize,ssplit,pos,lemma,ner");
    props.setProperty("tokenize.options", "tokenizeNLs,invertible,ptb3Escaping=true");
    pipeline = new StanfordCoreNLP(props);
    // set up pipeline for XML doc
    String xmlDocContents = IOUtils.stringFromFile(xmlDocPath);
    Properties pipelineProps = StringUtils.argsToProperties("-props", xmlDocPipelineProps);
    pipelineProps.setProperty("annotators", "tokenize,cleanxml,ssplit,pos,lemma,ner");
    xmlPipeline = new StanfordCoreNLP(pipelineProps);
  }

  public void checkSentenceTokenAlignmentCorrectness(Annotation annotation) {
    int i = 0;
    // check tokens
    for (CoreLabel token : annotation.get(CoreAnnotations.TokensAnnotation.class)) {
      if (!token.isNewline()) {
        assertEquals(i, (int) token.get(CoreAnnotations.TokenBeginAnnotation.class));
        assertEquals(i + 1, (int) token.get(CoreAnnotations.TokenEndAnnotation.class));
      }
      i++;
    }
    // check sentences match tokens
    for (CoreMap sentence : annotation.get(CoreAnnotations.SentencesAnnotation.class)) {
      List<CoreLabel> sentenceTokens = sentence.get(CoreAnnotations.TokensAnnotation.class);
      // check sentence's token begin annotation matches index of first token
      assertEquals(
          sentence.get(CoreAnnotations.TokenBeginAnnotation.class),
          sentenceTokens.get(0).get(CoreAnnotations.TokenBeginAnnotation.class));
      // check sentence's token end annotation matches index of last token + 1
      assertEquals(
          (int) sentence.get(CoreAnnotations.TokenEndAnnotation.class),
          ((int) sentenceTokens.get(sentenceTokens.size()-1).get(CoreAnnotations.TokenBeginAnnotation.class))+1);

    }
  }

  public void testBasicExample() {
    // annotate basic example
    Annotation basicExample = new Annotation(basicText);
    pipeline.annotate(basicExample);
    // check all tokens and sentences
    checkSentenceTokenAlignmentCorrectness(basicExample);
    // check sentence token boundaries
    CoreMap sentenceOne = basicExample.get(CoreAnnotations.SentencesAnnotation.class).get(0);
    assertEquals(0, (int) sentenceOne.get(CoreAnnotations.TokenBeginAnnotation.class));
    assertEquals(6, (int) sentenceOne.get(CoreAnnotations.TokenEndAnnotation.class));
    CoreMap sentenceTwo = basicExample.get(CoreAnnotations.SentencesAnnotation.class).get(1);
    assertEquals(6, (int) sentenceTwo.get(CoreAnnotations.TokenBeginAnnotation.class));
    assertEquals(12, (int) sentenceTwo.get(CoreAnnotations.TokenEndAnnotation.class));
    CoreMap sentenceThree = basicExample.get(CoreAnnotations.SentencesAnnotation.class).get(2);
    assertEquals(12, (int) sentenceThree.get(CoreAnnotations.TokenBeginAnnotation.class));
    assertEquals(18, (int) sentenceThree.get(CoreAnnotations.TokenEndAnnotation.class));
    // check some tokens
    CoreLabel tokenIndexFour = basicExample.get(CoreAnnotations.TokensAnnotation.class).get(4);
    assertEquals("Hawaii", tokenIndexFour.originalText());
    CoreLabel tokenIndexSeven = basicExample.get(CoreAnnotations.TokensAnnotation.class).get(7);
    assertEquals("vacation", tokenIndexSeven.originalText());
    CoreLabel tokenIndexFifteen = basicExample.get(CoreAnnotations.TokensAnnotation.class).get(15);
    assertEquals("great", tokenIndexFifteen.originalText());
  }

  public void testBasicNewlineExample() {
    // annotate basic newline example
    Annotation basicNewlineExample = new Annotation(basicNewlineText);
    pipeline.annotate(basicNewlineExample);
    // check all tokens and sentences
    checkSentenceTokenAlignmentCorrectness(basicNewlineExample);
    // check sentence token boundaries
    CoreMap sentenceOne = basicNewlineExample.get(CoreAnnotations.SentencesAnnotation.class).get(0);
    assertEquals(0, (int) sentenceOne.get(CoreAnnotations.TokenBeginAnnotation.class));
    assertEquals(6, (int) sentenceOne.get(CoreAnnotations.TokenEndAnnotation.class));
    CoreMap sentenceTwo = basicNewlineExample.get(CoreAnnotations.SentencesAnnotation.class).get(1);
    assertEquals(6, (int) sentenceTwo.get(CoreAnnotations.TokenBeginAnnotation.class));
    assertEquals(12, (int) sentenceTwo.get(CoreAnnotations.TokenEndAnnotation.class));
    CoreMap sentenceThree = basicNewlineExample.get(CoreAnnotations.SentencesAnnotation.class).get(2);
    assertEquals(12, (int) sentenceThree.get(CoreAnnotations.TokenBeginAnnotation.class));
    assertEquals(18, (int) sentenceThree.get(CoreAnnotations.TokenEndAnnotation.class));
    CoreMap sentenceFour = basicNewlineExample.get(CoreAnnotations.SentencesAnnotation.class).get(3);
    assertEquals(18, (int) sentenceFour.get(CoreAnnotations.TokenBeginAnnotation.class));
    assertEquals(24, (int) sentenceFour.get(CoreAnnotations.TokenEndAnnotation.class));
    // check some tokens
    CoreLabel tokenIndexFour = basicNewlineExample.get(CoreAnnotations.TokensAnnotation.class).get(4);
    assertEquals("Hawaii", tokenIndexFour.originalText());
    CoreLabel tokenIndexSeven = basicNewlineExample.get(CoreAnnotations.TokensAnnotation.class).get(7);
    assertEquals("vacation", tokenIndexSeven.originalText());
    assertTrue(!tokenIndexSeven.isNewline());
    CoreLabel tokenIndexFifteen = basicNewlineExample.get(CoreAnnotations.TokensAnnotation.class).get(15);
    assertEquals("great", tokenIndexFifteen.originalText());
  }

  public void testXMLDoc() {
    // annotate xml doc example
    Annotation xmlDocAnnotation = new Annotation(IOUtils.stringFromFile(xmlDocPath));
    xmlPipeline.annotate(xmlDocAnnotation);
    // check all tokens and sentences
    checkSentenceTokenAlignmentCorrectness(xmlDocAnnotation);
    // check sentence token boundaries
    System.err.println("finished...");
    // check some tokens
    CoreLabel tokenIndexOne = xmlDocAnnotation.get(CoreAnnotations.TokensAnnotation.class).get(1);
    assertEquals("iPhone", tokenIndexOne.originalText());
    CoreLabel tokenIndexFour = xmlDocAnnotation.get(CoreAnnotations.TokensAnnotation.class).get(4);
    assertEquals("Have", tokenIndexFour.originalText());
    assertTrue(!tokenIndexFour.isNewline());
    CoreLabel tokenIndexNinetySix = xmlDocAnnotation.get(CoreAnnotations.TokensAnnotation.class).get(96);
    assertEquals("so", tokenIndexNinetySix.originalText());
    CoreLabel tokenIndexTwoNinetyThree = xmlDocAnnotation.get(CoreAnnotations.TokensAnnotation.class).get(293);
    assertEquals("them", tokenIndexTwoNinetyThree.originalText());
    assertTrue(!tokenIndexTwoNinetyThree.isNewline());
    CoreLabel tokenIndexFiveFortyTwo = xmlDocAnnotation.get(CoreAnnotations.TokensAnnotation.class).get(542);
    assertEquals("location", tokenIndexFiveFortyTwo.originalText());
    CoreLabel tokenIndexFiveFiftyFour = xmlDocAnnotation.get(CoreAnnotations.TokensAnnotation.class).get(543);
    assertEquals(".", tokenIndexFiveFiftyFour.originalText());
  }

}
