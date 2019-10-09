package edu.stanford.nlp.pipeline;

import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.Pair;
import junit.framework.TestCase;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

public class MWTProtobufSerializationITest extends TestCase {

  public String sampleText = "Le but des bandes de roulement est d'augmenter la traction. Elle est présidente du conseil " +
      "d'administration.";

  public StanfordCoreNLP pipeline;

  AnnotationSerializer serializer;

  public void findCoreMapDifference(CoreMap originalCoreMap, CoreMap readCoreMap) {
    if (!originalCoreMap.get(CoreAnnotations.TokensAnnotation.class).equals(
        readCoreMap.get(CoreAnnotations.TokensAnnotation.class)))
      System.err.println("tokens annotation difference detected!");
    if (!originalCoreMap.get(CoreAnnotations.TextAnnotation.class).equals(
        readCoreMap.get(CoreAnnotations.TextAnnotation.class)))
      System.err.println("text annotation difference detected!");
    if (!originalCoreMap.get(CoreAnnotations.SentencesAnnotation.class).equals(
        readCoreMap.get(CoreAnnotations.SentencesAnnotation.class)))
      System.err.println("sentences annotation difference detected!");
  }


  @Override
  public void setUp() {
    // set up pipeline and serializer
    pipeline = new StanfordCoreNLP("french");
    serializer = new ProtobufAnnotationSerializer();
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
    //findCoreMapDifference(sampleDocument.annotation(), readAnnotation);
    /*for (int i = 0 ; i < readAnnotation.get(CoreAnnotations.TokensAnnotation.class).size() ; i++) {
      if (!readAnnotation.get(CoreAnnotations.TokensAnnotation.class).get(i).equals(
          sampleDocument.tokens().get(i))) {
        System.out.println(sampleDocument.tokens().get(i));
      }
    }*/
    ProtobufAnnotationSerializerSlowITest.sameAsRead(sampleDocument.annotation(), readAnnotation);
  }

}
