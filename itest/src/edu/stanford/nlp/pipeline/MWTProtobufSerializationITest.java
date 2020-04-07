package edu.stanford.nlp.pipeline;

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
    ProtobufAnnotationSerializerSlowITest.sameAsRead(sampleDocument.annotation(), readAnnotation);
  }

}
