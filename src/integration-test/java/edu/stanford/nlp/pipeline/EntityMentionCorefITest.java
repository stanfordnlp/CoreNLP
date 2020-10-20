package edu.stanford.nlp.pipeline;

import edu.stanford.nlp.ling.*;
import edu.stanford.nlp.util.*;
import java.util.*;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

/**
 * @author Jason Bolton
 * @author Christopher Manning
 */
public class EntityMentionCorefITest {

  private static final String apostropheText = "President Barack Obama's presidency started in 2009. " +
      "Before that Obama defeated John McCain in the 2008 presidential election. " +
      "Barack Obama served two terms.";

  private StanfordCoreNLP pipeline;

  // private AnnotationSerializer serializer;

  @Before
  public void setUp() {
    // set up pipeline and serializer
    Properties props = new Properties();
    props.setProperty("annotators", "tokenize,ssplit,pos,lemma,ner,depparse,coref");
    pipeline = new StanfordCoreNLP(props);
    // serializer = new ProtobufAnnotationSerializer();
  }

  @Test
  public void testApostropheMatch() {
    // build annotation
    Annotation sampleAnnotation = new Annotation(apostropheText);
    // annotate
    pipeline.annotate(sampleAnnotation);
    // check there is an "Obama" coref link in one direction or the other but be agnostic as to which mention is canonical
    // System.err.println(sampleAnnotation.get(CoreAnnotations.MentionsAnnotation.class));
    CoreMap entityMention =
        sampleAnnotation.get(CoreAnnotations.MentionsAnnotation.class).get(3); // gets "Obama" in second (index 1) sentence
    for (CoreLabel word : entityMention.get(CoreAnnotations.TokensAnnotation.class)) {
      Assert.assertEquals("Obama", word.word());
      Assert.assertEquals("PERSON", word.ner());
    }
    int matchingEntityMentionIndex =
        entityMention.get(CoreAnnotations.CanonicalEntityMentionIndexAnnotation.class);
    Assert.assertTrue(matchingEntityMentionIndex == 1 || matchingEntityMentionIndex == 3);

    CoreMap entityMention2 =
            sampleAnnotation.get(CoreAnnotations.MentionsAnnotation.class).get(1); // gets "Obama" in first (index 0) sentence
    for (CoreLabel word : entityMention2.get(CoreAnnotations.TokensAnnotation.class)) {
      Assert.assertTrue("Obama".equals(word.word()) || "Barack".equals(word.word()));
      Assert.assertEquals("PERSON", word.ner());
    }
    int matchingEntityMentionIndex2 =
            entityMention2.get(CoreAnnotations.CanonicalEntityMentionIndexAnnotation.class);

    Assert.assertTrue(matchingEntityMentionIndex2 == 1 || matchingEntityMentionIndex2 == 3);
  }

}
