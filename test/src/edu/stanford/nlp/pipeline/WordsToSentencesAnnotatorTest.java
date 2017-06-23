package edu.stanford.nlp.pipeline;

import java.util.*;

import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.util.CoreMap;

import edu.stanford.nlp.util.PropertiesUtils;
import junit.framework.Assert;
import junit.framework.TestCase;


/** @author Adam Vogel */
public class WordsToSentencesAnnotatorTest extends TestCase {

  public void testAnnotator() {
    String text = "I saw Dr. Spock yesterday, he was speaking with Mr. McCoy.  They were walking down Mullholand Dr. talking about www.google.com.  Dr. Spock returns!";
    runSentence(text, 3);

    // This would fail for "Yahoo! Research", since we don't yet know to chunk "Yahoo!"
    text = "I visited Google Research.  Dr. Spock, Ph.D., was working there and said it's an awful place!  What a waste of Ms. Pacman's last remaining life.";
    runSentence(text, 3);
  }

  public static boolean runSentence(String text, int num_sentences) {
    Annotation doc = new Annotation(text);
    Properties props = new Properties();
    props.setProperty("annotators", "tokenize,ssplit");
    props.setProperty("tokenize.language", "en");
    //Annotator annotator = new TokenizerAnnotator("en");
    StanfordCoreNLP pipeline = new StanfordCoreNLP(props);
    pipeline.annotate(doc);

    // now check what's up...
    List<CoreMap> sentences = doc.get(CoreAnnotations.SentencesAnnotation.class);
    Assert.assertNotNull(sentences);
    Assert.assertEquals(num_sentences, sentences.size());
    /*
    for(CoreMap s : sentences) {
      String position = s.get(SentencePositionAnnotation.class); // what's wrong here?
      System.out.print("position: ");
      System.out.println(position);
      //throw new RuntimeException(position);
    }
    */
    return true;
  }

  public void testSentenceSplitting() {
    String text = "Date :\n01/02/2012\nContent :\nSome words are here .\n";
    // System.out.println(text);
    Properties props = new Properties();
    props.setProperty("annotators", "tokenize, ssplit");
    props.setProperty("ssplit.eolonly", "true");
    props.setProperty("tokenize.whitespace", "true");
    StanfordCoreNLP pipeline = new StanfordCoreNLP(props);

    Annotation document1 = new Annotation(text);
    pipeline.annotate(document1);
    List<CoreMap> sentences = document1.get(CoreAnnotations.SentencesAnnotation.class);
    // System.out.println("* Num of sentences in text = "+sentences.size());
    // System.out.println("Sentences is " + sentences);
    assertEquals(4, sentences.size());
  }

  public void testTokenizeNLsDoesntChangeSsplitResults() {
    String text = "This is one sentence\n\nThis is not another with default ssplit settings.";
    Properties props = new Properties();
    props.setProperty("annotators", "tokenize, ssplit");
    props.setProperty("tokenize.options", "tokenizeNLs");
    StanfordCoreNLP pipeline = new StanfordCoreNLP(props);

    Annotation document1 = new Annotation(text);
    pipeline.annotate(document1);
    List<CoreMap> sentences = document1.get(CoreAnnotations.SentencesAnnotation.class);
    assertEquals(1, sentences.size());

    // make sure that there are the correct # of tokens
    // (does NOT contain NL tokens)
    List<CoreLabel> tokens = document1.get(CoreAnnotations.TokensAnnotation.class);
    assertEquals(15, tokens.size());
  }

  public void testDefaultNewlineIsSentenceBreakSettings() {
    String text = "This is one sentence\n\nThis is not another with default ssplit settings.";
    Properties props = new Properties();
    props.setProperty("annotators", "tokenize, ssplit");
    StanfordCoreNLP pipeline = new StanfordCoreNLP(props);

    Annotation document1 = new Annotation(text);
    pipeline.annotate(document1);
    List<CoreMap> sentences = document1.get(CoreAnnotations.SentencesAnnotation.class);
    assertEquals(1, sentences.size());

    // make sure that there are the correct # of tokens
    // (does NOT contain NL tokens)
    List<CoreLabel> tokens = document1.get(CoreAnnotations.TokensAnnotation.class);
    assertEquals(13, tokens.size());
  }

  public void testTwoNewlineIsSentenceBreakSettings() {
    String text = "This is \none sentence\n\nThis is not another.";
    Properties props = new Properties();
    props.setProperty("annotators", "tokenize, ssplit");
    props.setProperty("ssplit.newlineIsSentenceBreak", "two");
    StanfordCoreNLP pipeline = new StanfordCoreNLP(props);

    Annotation document1 = new Annotation(text);
    pipeline.annotate(document1);
    List<CoreMap> sentences = document1.get(CoreAnnotations.SentencesAnnotation.class);
    assertEquals(2, sentences.size());

    // make sure that there are the correct # of tokens (does contain NL tokens)
    List<CoreLabel> tokens = document1.get(CoreAnnotations.TokensAnnotation.class);
    assertEquals(12, tokens.size());
  }

  public void testAlwaysNewlineIsSentenceBreakSettings() {
    String text = "This is \none sentence\n\nThis is not another.";
    Properties props = new Properties();
    props.setProperty("annotators", "tokenize, ssplit");
    props.setProperty("ssplit.newlineIsSentenceBreak", "always");
    StanfordCoreNLP pipeline = new StanfordCoreNLP(props);

    Annotation document1 = new Annotation(text);
    pipeline.annotate(document1);
    List<CoreMap> sentences = document1.get(CoreAnnotations.SentencesAnnotation.class);
    assertEquals(3, sentences.size());

    // make sure that there are the correct # of tokens (does contain NL tokens)
    List<CoreLabel> tokens = document1.get(CoreAnnotations.TokensAnnotation.class);
    assertEquals(12, tokens.size());
  }


}
