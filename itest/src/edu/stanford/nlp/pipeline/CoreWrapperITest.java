package edu.stanford.nlp.pipeline;

import edu.stanford.nlp.ling.*;
import edu.stanford.nlp.ie.util.RelationTriple;
import edu.stanford.nlp.util.Pair;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.*;
import junit.framework.TestCase;


public class CoreWrapperITest extends TestCase {

  public void testPipeline() throws Exception {
    // set up pipeline
    Properties props = new Properties();
    props.setProperty("annotators", "tokenize,ssplit,pos,lemma,ner,depparse,coref,kbp,quote");
    StanfordCoreNLP pipeline = new StanfordCoreNLP(props);
    // make a basic document
    CoreDocument exampleDocument =
        new CoreDocument(
            "Barack Obama was born in Hawaii on August 4, 1961. " +
                "He was elected president in 2008, defeating Arizona senator John McCain. " +
                "Obama said, \"My fellow citizens:  I stand here today humbled by the task before us, grateful for the " +
                "trust you've bestowed, mindful of the sacrifices borne by our ancestors\" to begin his inaugural " +
                "address.");
    // annotate document
    pipeline.annotate(exampleDocument);
    // examine output
    // sentences
    // correct number of sentences
    assertEquals(3, exampleDocument.sentences().size());
    CoreSentence firstSentence = exampleDocument.sentences().get(0);
    CoreSentence secondSentence = exampleDocument.sentences().get(1);
    int sentenceCount = 0;
    for (CoreSentence coreSentence : exampleDocument.sentences()) {
      assertEquals(coreSentence.coreMap(),
          exampleDocument.annotation().get(CoreAnnotations.SentencesAnnotation.class).get(sentenceCount));
      sentenceCount++;
    }
    // sentence has correct text
    assertEquals("He was elected president in 2008, defeating Arizona senator John McCain.",
        secondSentence.text());
    // sentence has correct link to document
    assertSame(secondSentence.document(), exampleDocument);
    // char offsets
    Pair<Integer,Integer> sentenceTwoOffsets = new Pair<>(51,123);
    assertEquals(sentenceTwoOffsets, secondSentence.charOffsets());
    // mention info from sentences
    assertEquals(6, secondSentence.entityMentions().size());
    CoreEntityMention arizonaMentionFromSentence = secondSentence.entityMentions().get(2);
    CoreEntityMention arizonaMentionFromDocument = exampleDocument.entityMentions().get(5);
    assertEquals("Arizona", arizonaMentionFromSentence.text());
    assertSame(arizonaMentionFromSentence, arizonaMentionFromDocument);
    // kbp relation info from sentences
    List<RelationTriple> kbpRelationsFromSentenceOne = firstSentence.relations();
    String sentenceOneRelationOne = '(' +kbpRelationsFromSentenceOne.get(0).subjectGloss()+ ',' +
            kbpRelationsFromSentenceOne.get(0).relationGloss()+ ',' +
            kbpRelationsFromSentenceOne.get(0).objectGloss()+ ')';
    String goldSentenceOneRelationOne = "(Barack Obama,per:stateorprovince_of_birth,Hawaii)";
    assertEquals(goldSentenceOneRelationOne, sentenceOneRelationOne);
    // entity mentions
    CoreEntityMention firstEntityMention = exampleDocument.entityMentions().get(0);
    CoreEntityMention fifthEntityMention = exampleDocument.entityMentions().get(4);
    assertEquals(12, exampleDocument.entityMentions().size());
    assertEquals("Barack Obama", firstEntityMention.text());
    assertEquals("Barack Obama was born in Hawaii on August 4, 1961.",
        firstEntityMention.sentence().text());
    Pair<Integer,Integer> goldFirstEntityMentionCharOffsets = new Pair<>(0,12);
    assertEquals(goldFirstEntityMentionCharOffsets, firstEntityMention.charOffsets());
    assertEquals("PERSON", firstEntityMention.entityType());
    assertEquals("2008", fifthEntityMention.text());
    assertEquals("He was elected president in 2008, defeating Arizona senator John McCain.",
        fifthEntityMention.sentence().text());
    Pair<Integer,Integer> goldFifthEntityMentionCharOffsets = new Pair<>(79,83);
    assertEquals(goldFifthEntityMentionCharOffsets, fifthEntityMention.charOffsets());
    assertEquals("DATE", fifthEntityMention.entityType());
    // quotes
    CoreQuote firstQuote = exampleDocument.quotes().get(0);
    assertEquals("\"My fellow citizens:  I stand here today humbled by the task before us, grateful for the trust " +
        "you've bestowed, mindful of the sacrifices borne by our ancestors\"", firstQuote.text());
    assertEquals("Obama", firstQuote.speaker().get());
    assertEquals("Barack Obama", firstQuote.canonicalSpeaker().get());
    // serialization
    // serialize
    AnnotationSerializer serializer = new ProtobufAnnotationSerializer();
    // write
    ByteArrayOutputStream ks = new ByteArrayOutputStream();
    serializer.writeCoreDocument(exampleDocument, ks).close();
    // read
    InputStream kis = new ByteArrayInputStream(ks.toByteArray());
    Pair<Annotation, InputStream> pair = serializer.read(kis);
    pair.second.close();
    Annotation readAnnotation = pair.first;
    kis.close();
    // check if same
    CoreDocument readCoreDocument = new CoreDocument(readAnnotation);
    ProtobufAnnotationSerializerSlowITest.sameAsRead(exampleDocument.annotation(),
        readCoreDocument.annotation());
  }

}
