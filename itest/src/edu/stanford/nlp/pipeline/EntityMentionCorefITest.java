package edu.stanford.nlp.pipeline;

import edu.stanford.nlp.ling.*;
import edu.stanford.nlp.util.*;
import java.util.*;
import junit.framework.TestCase;

public class EntityMentionCorefITest extends TestCase {

  public String apostropheText = "President Barack Obama's presidency started in 2009. " +
      "Before that Obama defeated John McCain in the 2008 presidential election. " +
      "Barack Obama served two terms.";

  public StanfordCoreNLP pipeline;

  AnnotationSerializer serializer;

  @Override
  public void setUp() {
    // set up pipeline and serializer
    Properties props = new Properties();
    props.setProperty("annotators", "tokenize,ssplit,pos,lemma,ner,depparse,coref");
    pipeline = new StanfordCoreNLP(props);
    serializer = new ProtobufAnnotationSerializer();
  }

  public void testApostropheMatch() {
    // build annotation
    Annotation sampleAnnotation = new Annotation(apostropheText);
    // annotate
    pipeline.annotate(sampleAnnotation);
    // check there is a link
    CoreMap entityMention =
        sampleAnnotation.get(CoreAnnotations.MentionsAnnotation.class).get(3);
    int matchingEntityMentionIndex =
        entityMention.get(CoreAnnotations.CanonicalEntityMentionIndexAnnotation.class);
    assertEquals(1, matchingEntityMentionIndex);
  }

}
