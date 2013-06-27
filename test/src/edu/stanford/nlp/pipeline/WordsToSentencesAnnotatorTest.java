package edu.stanford.nlp.pipeline;

import java.util.*;

import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.util.CoreMap;

import junit.framework.Assert;
import junit.framework.TestCase;


/** @author Adam Vogel */
public class WordsToSentencesAnnotatorTest extends TestCase {

  public void test() {
    String text = "I saw Dr. Spock yesterday, he was speaking with Mr. McCoy.  They were walking down Mullholand Dr. talking about www.google.com.  Dr. Spock returns!";
    testSentence(text, 3);
  
    // XXX: this test currently fails.
    //text = "I visited Yahoo! research.  Dr. Spock, Ph.D., was working there and said it's an awful place!  What a waste of Ms. Pacman's last remaining life.";
    //testSentence(text, 3);
  }

  public boolean testSentence(String text, int num_sentences) {
    Annotation doc = new Annotation(text);
    Properties props = new Properties();
    props.setProperty("annotators", "tokenize,ssplit");
    //Annotator annotator = new PTBTokenizerAnnotator();
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
}
