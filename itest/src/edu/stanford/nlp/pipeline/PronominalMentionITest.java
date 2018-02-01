package edu.stanford.nlp.pipeline;

import edu.stanford.nlp.ling.*;
import edu.stanford.nlp.util.*;
import java.util.Properties;
import junit.framework.TestCase;

public class PronominalMentionITest extends TestCase {

  public String sampleText = "President Barack Obama was born in August. His birthday was August 4th, 1961.";
  public StanfordCoreNLP pipeline;


  @Override
  public void setUp() {
  }

  public void testCorefExample() {
    // set up pipeline
    Properties props = new Properties();
    props.setProperty("annotators", "tokenize,ssplit,pos,lemma,ner,depparse,coref");
    pipeline = new StanfordCoreNLP(props);
    // set up annotation
    CoreDocument sampleDoc = new CoreDocument(sampleText);
    // annotate
    pipeline.annotate(sampleDoc);
    // check second annotation is "Barack Obama"
    assertEquals("Barack Obama", sampleDoc.entityMentions().get(1).text());
    // check "His" has canonical entity mention set to "Barack Obama"
    CoreMap pronominalMention = sampleDoc.entityMentions().get(4).coreMap();
    assertEquals("His", pronominalMention.get(CoreAnnotations.TextAnnotation.class));
    assertEquals(1, (int) pronominalMention.get(CoreAnnotations.CanonicalEntityMentionIndexAnnotation.class));
  }

}
