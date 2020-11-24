package edu.stanford.nlp.pipeline;

import edu.stanford.nlp.coref.*;
import edu.stanford.nlp.ling.*;
import edu.stanford.nlp.semgraph.SemanticGraphCoreAnnotations;
import edu.stanford.nlp.util.*;
import java.io.*;
import java.util.*;
import junit.framework.TestCase;


public class ProtobufSerializationSanityITest extends TestCase {

  public String sampleText = "Joe Smith works at the post office.  He said, \"I love working there.\"" +
      " Postal worker Joe is good friends with Chris Anderson.  Chris and Joe like to eat lunch together every day." +
      " On Sunday, January 21st 2018, Chris and Joe Smith decided to get pizza at 3:00pm.  Joe said, \"I love pizza.\"" +
      " Chris Anderson was born in California.";

  public StanfordCoreNLP pipeline;

  AnnotationSerializer serializer;

  @Override
  public void setUp() {
    // set up pipeline and serializer
    Properties props = new Properties();
    props.setProperty("annotators", "tokenize,ssplit,pos,lemma,ner,parse,depparse,coref," +
                      "natlog,openie,entitylink,kbp,sentiment,quote");
    props.setProperty("tokenize.codepoint", "true");
    pipeline = new StanfordCoreNLP(props);
    serializer = new ProtobufAnnotationSerializer();
  }

  public void compareTokensLists(Annotation originalDoc, Annotation newDoc) {
    List<CoreLabel> originalTokens = originalDoc.get(CoreAnnotations.TokensAnnotation.class);
    List<CoreLabel> newTokens = newDoc.get(CoreAnnotations.TokensAnnotation.class);
    if (originalTokens.size() != newTokens.size()) {
      System.err.println("---");
      System.err.println("token size mismatch!");
      System.err.println("original token size: "+originalTokens.size());
      System.err.println("new token size; "+newTokens.size());
    } else {
      for (int i = 0 ; i < originalTokens.size() ; i++) {
        if (!originalTokens.get(i).equals(newTokens.get(i))) {
          System.err.println("---");
          System.err.println("token mismatch detected!");
          System.err.println("token number: "+i);
          System.err.println(originalTokens.get(i));
        }
      }
    }
  }

  public void compareEntityMentionsLists(Annotation originalDoc, Annotation newDoc) {
    List<CoreMap> originalMentions = originalDoc.get(CoreAnnotations.MentionsAnnotation.class);
    List<CoreMap> newMentions = newDoc.get(CoreAnnotations.MentionsAnnotation.class);
    if (originalMentions.size() != newMentions.size()) {
      System.err.println("---");
      System.err.println("entity mentions size mismatch!");
      System.err.println("original entity mention size: "+originalMentions.size());
      System.err.println("new entity mention size: "+newMentions.size());
    } else {
      for (int i = 0 ; i < originalMentions.size() ; i++) {
        if (!originalMentions.get(i).equals(newMentions.get(i))) {
          System.err.println("---");
          System.err.println("entity mention mismatch detected!");
          System.err.println("entity mention number: "+i);
          System.err.println(originalMentions.get(i));
        }
      }
    }
  }

  public void compareQuotesLists(Annotation originalDoc, Annotation newDoc) {
    List<CoreMap> originalQuotes = QuoteAnnotator.gatherQuotes(originalDoc);
    List<CoreMap> newQuotes = QuoteAnnotator.gatherQuotes(newDoc);
    // remove non-serialized quote stuff
    for (CoreMap quote : originalQuotes) {
      quote.remove(SemanticGraphCoreAnnotations.EnhancedPlusPlusDependenciesAnnotation.class);
      quote.remove(CoreAnnotations.QuotationsAnnotation.class);
    }
    if (originalQuotes.size() != newQuotes.size()) {
      System.err.println("---");
      System.err.println("quotes size mismatch!");
      System.err.println("original quotes size: "+originalQuotes.size());
      System.err.println("new quotes size: "+newQuotes.size());
    } else {
      for (int i = 0 ; i < originalQuotes.size() ; i++) {
        if (!originalQuotes.get(i).equals(newQuotes.get(i))) {
          System.err.println("---");
          System.err.println("quote mismatch detected!");
          System.err.println("quote number: "+i);
          System.err.println(originalQuotes.get(i));
        }
      }
    }
  }

  public void findCoreMapDifference(CoreMap originalCoreMap, CoreMap readCoreMap) {
    if (!originalCoreMap.get(CoreAnnotations.TokensAnnotation.class).equals(
        readCoreMap.get(CoreAnnotations.TokensAnnotation.class)))
      System.err.println("tokens annotation difference detected!");
    if (!originalCoreMap.get(CoreAnnotations.TextAnnotation.class).equals(
        readCoreMap.get(CoreAnnotations.TextAnnotation.class)))
      System.err.println("text annotation difference detected!");
    if (!originalCoreMap.get(CorefCoreAnnotations.CorefChainAnnotation.class).equals(
        readCoreMap.get(CorefCoreAnnotations.CorefChainAnnotation.class)))
      System.err.println("coref chain annotation difference detected!");
    if (!originalCoreMap.get(CorefCoreAnnotations.CorefMentionsAnnotation.class).equals(
        readCoreMap.get(CorefCoreAnnotations.CorefMentionsAnnotation.class)))
      System.err.println("coref mentions difference detected!");
    if (!originalCoreMap.get(CoreAnnotations.EntityMentionToCorefMentionMappingAnnotation.class).equals(
        readCoreMap.get(CoreAnnotations.EntityMentionToCorefMentionMappingAnnotation.class)))
      System.err.println("entity mention to coref mapping difference detected!");
    if (!originalCoreMap.get(CoreAnnotations.CorefMentionToEntityMentionMappingAnnotation.class).equals(
        readCoreMap.get(CoreAnnotations.CorefMentionToEntityMentionMappingAnnotation.class)))
      System.err.println("coref mention to entity mapping difference detected!");
    if (!originalCoreMap.get(CoreAnnotations.MentionsAnnotation.class).equals(
        readCoreMap.get(CoreAnnotations.MentionsAnnotation.class)))
      System.err.println("mentions annotation difference detected!");
    if (!originalCoreMap.get(CoreAnnotations.SentencesAnnotation.class).equals(
        readCoreMap.get(CoreAnnotations.SentencesAnnotation.class)))
      System.err.println("sentences annotation difference detected!");
  }

  public void testBasicExample() throws ClassNotFoundException, IOException {
    // set up document
    CoreDocument sampleDocument = new CoreDocument(sampleText);
    // annotate
    pipeline.annotate(sampleDocument);
    // serialize
    ByteArrayOutputStream ks = new ByteArrayOutputStream();
    serializer.writeCoreDocument(sampleDocument, ks).close();
    // Read
    InputStream kis = new ByteArrayInputStream(ks.toByteArray());
    Pair<Annotation, InputStream> pair = serializer.read(kis);
    pair.second.close();
    Annotation readAnnotation = pair.first;
    kis.close();
    // test if the same
    compareTokensLists(sampleDocument.annotation(), readAnnotation);
    compareEntityMentionsLists(sampleDocument.annotation(), readAnnotation);
    compareQuotesLists(sampleDocument.annotation(), readAnnotation);
    findCoreMapDifference(sampleDocument.annotation(), readAnnotation);
    ProtobufAnnotationSerializerSlowITest.sameAsRead(sampleDocument.annotation(), readAnnotation);
  }


}
