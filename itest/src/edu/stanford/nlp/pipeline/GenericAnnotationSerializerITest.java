package edu.stanford.nlp.pipeline;

import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.util.Pair;
import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.Properties;

import static org.junit.Assert.*;

/**
 * Tests for GenericAnnotationSerializer: verifies that round-tripping an
 * Annotation through serialization/deserialization preserves key fields,
 * both with and without GZIP compression.
 */
public class GenericAnnotationSerializerITest {

  private static final String TEST_TEXT = "Dan Ramage is working for\nMicrosoft. He's in Seattle! \n";

  private Annotation document;

  @Before
  public void setUp() {
    Properties props = new Properties();
    props.setProperty("annotators", "tokenize,ssplit,pos,lemma,ner,parse");
    StanfordCoreNLP pipeline = new StanfordCoreNLP(props);
    document = new Annotation(TEST_TEXT);
    pipeline.annotate(document);
  }

  // -----------------------------------------------------------------------
  // Helpers
  // -----------------------------------------------------------------------

  /** Round-trip through the serializer and return the recovered Annotation. */
  private Annotation roundTrip(boolean compress) throws Exception {
    GenericAnnotationSerializer serializer = new GenericAnnotationSerializer(compress);

    // Serialize
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    OutputStream os = serializer.write(document, baos);
    // TODO: this is a really awkward feature of the function
    // of the serializer, that the caller needs to know
    // it will need to close() the internally opened stream
    os.flush();
    os.close();

    // Deserialize
    ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
    Pair<Annotation, ?> result = serializer.read(bais);
    assertNotNull("Deserialized result should not be null", result);
    return result.first();
  }

  // -----------------------------------------------------------------------
  // Core round-trip assertions, shared between compressed and uncompressed
  // -----------------------------------------------------------------------

  private void assertRoundTripCorrect(Annotation recovered) {
    // Top-level text is preserved
    assertEquals("Document text should survive round-trip",
        document.get(CoreAnnotations.TextAnnotation.class),
        recovered.get(CoreAnnotations.TextAnnotation.class));

    // Same number of sentences
    List<?> origSentences  = document.get(CoreAnnotations.SentencesAnnotation.class);
    List<?> recovSentences = recovered.get(CoreAnnotations.SentencesAnnotation.class);
    assertNotNull("Recovered sentences should not be null", recovSentences);
    assertEquals("Sentence count should be preserved",
        origSentences.size(), recovSentences.size());

    // Token-level fields are preserved
    List<CoreLabel> origTokens  = document.get(CoreAnnotations.TokensAnnotation.class);
    List<CoreLabel> recovTokens = recovered.get(CoreAnnotations.TokensAnnotation.class);
    assertNotNull("Recovered tokens should not be null", recovTokens);
    assertEquals("Token count should be preserved",
        origTokens.size(), recovTokens.size());

    for (int i = 0; i < origTokens.size(); i++) {
      CoreLabel orig  = origTokens.get(i);
      CoreLabel recov = recovTokens.get(i);

      assertEquals("Word at index " + i,
          orig.get(CoreAnnotations.TextAnnotation.class),
          recov.get(CoreAnnotations.TextAnnotation.class));

      assertEquals("POS tag at index " + i,
          orig.get(CoreAnnotations.PartOfSpeechAnnotation.class),
          recov.get(CoreAnnotations.PartOfSpeechAnnotation.class));

      assertEquals("Lemma at index " + i,
          orig.get(CoreAnnotations.LemmaAnnotation.class),
          recov.get(CoreAnnotations.LemmaAnnotation.class));

      assertEquals("NER tag at index " + i,
          orig.get(CoreAnnotations.NamedEntityTagAnnotation.class),
          recov.get(CoreAnnotations.NamedEntityTagAnnotation.class));

      assertEquals("Character offset begin at index " + i,
          orig.get(CoreAnnotations.CharacterOffsetBeginAnnotation.class),
          recov.get(CoreAnnotations.CharacterOffsetBeginAnnotation.class));

      assertEquals("Character offset end at index " + i,
          orig.get(CoreAnnotations.CharacterOffsetEndAnnotation.class),
          recov.get(CoreAnnotations.CharacterOffsetEndAnnotation.class));
    }
  }

  // -----------------------------------------------------------------------
  // Tests
  // -----------------------------------------------------------------------

  @Test
  public void testRoundTripUncompressed() throws Exception {
    assertRoundTripCorrect(roundTrip(false));
  }

  @Test
  public void testRoundTripCompressed() throws Exception {
    assertRoundTripCorrect(roundTrip(true));
  }

  @Test
  public void testMultipleAnnotationsReadFromOneStream() throws Exception {
    // TODO: an obvious use case to test would be writing multiple objects to the same stream.
    // However, multiple attempts at using the existing Serializer could not get it to work
    // writing multiple objects to one stream.
    // Possibly it was never fully tested?
    GenericAnnotationSerializer serializer = new GenericAnnotationSerializer(false);

    Annotation doc2 = new Annotation("Barack Obama was born in Hawaii.");
    Properties props = new Properties();
    props.setProperty("annotators", "tokenize,ssplit,pos,lemma,ner");
    new StanfordCoreNLP(props).annotate(doc2);

    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    ObjectOutputStream oos = new ObjectOutputStream(baos);
    oos.writeObject(document);
    oos.writeObject(doc2);
    oos.flush();

    ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
    Pair<Annotation, InputStream> r1 = serializer.read(bais);
    assertNotNull(r1);
    assertEquals(TEST_TEXT, r1.first().get(CoreAnnotations.TextAnnotation.class));

    Pair<Annotation, InputStream> r2 = serializer.read(r1.second());
    assertNotNull(r2);
    assertEquals("Barack Obama was born in Hawaii.",
                 r2.first().get(CoreAnnotations.TextAnnotation.class));
  }

  /**
   * Verify that reading from a stream that contains something other than an
   * Annotation throws ClassCastException (as documented by the read() contract).
   */
  @Test(expected = ClassCastException.class)
  public void testReadNonAnnotationThrows() throws Exception {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    ObjectOutputStream oos = new ObjectOutputStream(baos);
    oos.writeObject("This is not an Annotation");
    oos.close();

    GenericAnnotationSerializer serializer = new GenericAnnotationSerializer(false);
    serializer.read(new ByteArrayInputStream(baos.toByteArray()));
  }

}
