package edu.stanford.nlp.pipeline;

import edu.stanford.nlp.ling.*;
import edu.stanford.nlp.util.*;
import java.util.*;
import junit.framework.TestCase;

public class CoreQuoteSanityITest extends TestCase {

  public String testDocText = "In the summer Joe Smith decided to go on vacation.  " +
      "He said \"I'm going to go to Hawaii.\"  That July, vacationer Joe went to Hawaii. ";

  public StanfordCoreNLP pipeline;

  @Override
  public void setUp() {
    // set up pipeline and serializer
    Properties props = new Properties();
    props.setProperty("annotators", "tokenize,ssplit,pos,lemma,ner,depparse,coref,quote");
    pipeline = new StanfordCoreNLP(props);
  }

  public void testCoreQuote() {
    // make the core document
    CoreDocument testDoc = new CoreDocument(testDocText);
    // annotate
    pipeline.annotate(testDoc);
    // test canonical entity mention is correct
    // "Joe Smith" should be first entity mention
    CoreMap canonicalEntityMention =
        testDoc.annotation().get(CoreAnnotations.MentionsAnnotation.class).get(1);
    // test canonical mention is correct
    assertEquals("Joe Smith", canonicalEntityMention.get(CoreAnnotations.TextAnnotation.class));
    assertEquals(14,
        canonicalEntityMention.get(CoreAnnotations.CharacterOffsetBeginAnnotation.class).intValue());
    assertEquals(23,
        canonicalEntityMention.get(CoreAnnotations.CharacterOffsetEndAnnotation.class).intValue());
    // test the CoreQuote has the correct entity mention for the canonical speaker
    assertEquals(canonicalEntityMention, testDoc.quotes().get(0).canonicalSpeakerEntityMention().get().coreMap());
  }

}
