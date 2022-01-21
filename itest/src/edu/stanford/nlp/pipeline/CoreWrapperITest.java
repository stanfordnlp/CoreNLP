package edu.stanford.nlp.pipeline;

import edu.stanford.nlp.ling.*;
import edu.stanford.nlp.ie.util.RelationTriple;
import edu.stanford.nlp.util.Pair;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.*;

import org.junit.Test;

public class CoreWrapperITest {

  @Test
  public void testPipeline() throws Exception {
    // set up pipeline
    Properties props = new Properties();
    props.setProperty("annotators", "tokenize,ssplit,pos,lemma,ner,parse,depparse,coref,kbp,quote");
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
    // sentence has correct words
    List<String> expectedWords =
            Arrays.asList("Barack", "Obama", "was", "born", "in", "Hawaii", "on", "August", "4", ",", "1961", "." );
    assertEquals(expectedWords, firstSentence.tokensAsStrings());
    // lemmas
    List<String> expectedLemmas =
            Arrays.asList("Barack", "Obama", "be", "bear", "in", "Hawaii", "on", "August", "4", ",", "1961", ".");
    assertEquals(expectedLemmas, firstSentence.lemmas());
    // part-of-speech tags
    List<String> expectedPartOfSpeechTags = Arrays.asList("NNP", "NNP", "VBD", "VBN", "IN", "NNP", "IN", "NNP", "CD", ",",
            "CD", ".");
    assertEquals(expectedPartOfSpeechTags, firstSentence.posTags());
    // named entity tags
    List<String> expectedNamedEntityTags = Arrays.asList("PERSON", "PERSON", "O", "O", "O", "STATE_OR_PROVINCE", "O",
            "DATE", "DATE", "DATE", "DATE", "O");
    assertEquals(expectedNamedEntityTags, firstSentence.nerTags());
    // constituency parse
    String expectedParse = "(ROOT\n" +
            "  (S\n" +
            "    (NP (NNP Barack) (NNP Obama))\n" +
            "    (VP (VBD was)\n" +
            "      (VP (VBN born)\n" +
            "        (PP (IN in)\n" +
            "          (NP (NNP Hawaii)))\n" +
            "        (PP (IN on)\n" +
            "          (NP (NNP August) (CD 4) (, ,) (CD 1961)))))\n" +
            "    (. .)))\n";
    assertEquals(expectedParse, firstSentence.constituencyParse().pennString());
    // dependency parse
    String expectedDependencyParse = "-> born/VBN (root)\n" +
            "  -> Obama/NNP (nsubj:pass)\n" +
            "    -> Barack/NNP (compound)\n" +
            "  -> was/VBD (aux:pass)\n" +
            "  -> Hawaii/NNP (obl:in)\n" +
            "    -> in/IN (case)\n" +
            "  -> August/NNP (obl:on)\n" +
            "    -> on/IN (case)\n" +
            "    -> 4/CD (nummod)\n" +
            "    -> ,/, (punct)\n" +
            "    -> 1961/CD (nummod)\n" +
            "  -> ./. (punct)\n";
    assertEquals(expectedDependencyParse, firstSentence.dependencyParse().toString());
    // coref info
    String expectedCoref =
            "{19=CHAIN19-[\"us\" in sentence 3, \"our\" in sentence 3], 21=CHAIN21-[\"Barack Obama\" in sentence 1, " +
                    "\"He\" in sentence 2, \"Obama\" in sentence 3, \"My\" in sentence 3, \"I\" in sentence 3, \"his\" " +
                    "in sentence 3]}";
    assertEquals(expectedCoref, exampleDocument.corefChains().toString());
    // mention info from sentences
    assertEquals(6, secondSentence.entityMentions().size());
    CoreEntityMention arizonaMentionFromSentence = secondSentence.entityMentions().get(2);
    CoreEntityMention arizonaMentionFromDocument = exampleDocument.entityMentions().get(5);
    assertEquals("Arizona", arizonaMentionFromSentence.text());
    assertSame(arizonaMentionFromSentence, arizonaMentionFromDocument);
    // noun phrases and verb phrases from sentence
    List<String> expectedNounPhrases =
            Arrays.asList("He", "president in 2008", "president", "2008", "Arizona senator John McCain");
    List<String> expectedVerbPhrases =
            Arrays.asList("was elected president in 2008, defeating Arizona senator John McCain",
                    "elected president in 2008, defeating Arizona senator John McCain",
                    "defeating Arizona senator John McCain");
    assertEquals(expectedNounPhrases, secondSentence.nounPhrases());
    assertEquals(expectedVerbPhrases, secondSentence.verbPhrases());
    // retrieve general tregex pattern
    String tregexPattern = "NP < (NP $ PP)";
    String expectedResultTree = "(NP\n  (NP (NN president))\n  (PP (IN in)\n    (NP (CD 2008))))\n";
    assertEquals(expectedResultTree, secondSentence.tregexResultTrees(tregexPattern).get(0).pennString());
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
