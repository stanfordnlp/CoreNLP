package edu.stanford.nlp.simple;

import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import edu.stanford.nlp.util.CoreMap;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import static org.junit.Assert.*;

/**
 * A test for aspects of {@link edu.stanford.nlp.simple.Sentence} which do not require loading the NLP models.
 *
 * @author Gabor Angeli
 */
public class SentenceTest {
  @Test
  public void testCreateFromText() {
    Sentence sent = new Sentence("the quick brown fox jumped over the lazy dog");
    assertNotNull(sent);
  }

  @Test
  public void testText() {
    Sentence sent = new Sentence("the quick brown fox jumped over the lazy dog");
    assertEquals("the quick brown fox jumped over the lazy dog", sent.text());
  }

  @Test
  public void testLengthOfMultiWordSentence() {
    Sentence sent = new Sentence("the quick brown fox jumped over the lazy dog");
    assertEquals(9, sent.length());
  }

  @Test
  public void testLengthOfSpecialCharacterOnlySentence() {
    Sentence sent = new Sentence(".");
    assertEquals(1, sent.length());
  }

  @Test
  public void testLengthOfOneWordSentence() {
    Sentence sent = new Sentence("Aloha");
    assertEquals(1, sent.length());
  }

  @Test
  public void testLengthOfOneWordSentenceWithDot() {
    Sentence sent = new Sentence("Aloha.");
    assertEquals(2, sent.length());
  }

  @Test
  public void testDocumentLinking() {
    Sentence sent = new Sentence("the quick brown fox jumped over the lazy dog");
    assertEquals(sent, sent.document.sentence(0));
  }

  @Test
  public void testBasicTokenization() {
    Sentence sent = new Sentence("the quick brown fox jumped over the lazy dog.");
    assertEquals("the", sent.word(0));
    assertEquals("quick", sent.word(1));
    assertEquals("dog", sent.word(8));
    assertEquals(".", sent.word(9));
  }

  @Test
  public void testWeirdTokens() {
    Sentence sent = new Sentence("United States of America (USA) it's a country.");
    assertEquals("(", sent.word(4));
    assertEquals(")", sent.word(6));
    assertEquals("'s", sent.word(8));

    sent = new Sentence("Alice(female), Bob(male) are in contact.");
    assertEquals("(", sent.word(1));
    assertEquals(",", sent.word(4));
  }

  @Test
  public void testOriginalText() {
    Sentence sent = new Sentence("United States of America (USA) it's a country.");
    assertEquals("(", sent.originalText(4));
    assertEquals(")", sent.originalText(6));
    assertEquals("it", sent.originalText(7));
    assertEquals("'s", sent.originalText(8));
  }

  @Test
  public void testCharacterOffsets() {
    Sentence sent = new Sentence("United States of America (USA) it's a country.");
    assertEquals(0, sent.characterOffsetBegin(0));
    assertEquals(6, sent.characterOffsetEnd(0));
    assertEquals(7, sent.characterOffsetBegin(1));
    assertEquals(25, sent.characterOffsetBegin(4));
    assertEquals(26, sent.characterOffsetEnd(4));
  }

  @Test
  public void testSentenceIndex() {
    Sentence sent = new Sentence("the quick brown fox jumped over the lazy dog");
    assertEquals(0, sent.sentenceIndex());

    Document doc = new Document("the quick brown fox jumped over the lazy dog. The lazy dog was not impressed.");
    List<Sentence> sentences = doc.sentences();
    assertEquals(0, sentences.get(0).sentenceIndex());
    assertEquals(1, sentences.get(1).sentenceIndex());
  }

  @Test
  public void testSentenceTokenOffsets() {
    Sentence sent = new Sentence("the quick brown fox jumped over the lazy dog");
    assertEquals(0, sent.sentenceTokenOffsetBegin());

    Document doc = new Document("the quick brown fox jumped over the lazy dog. The lazy dog was not impressed.");
    List<Sentence> sentences = doc.sentences();
    assertEquals(0, sentences.get(0).sentenceTokenOffsetBegin());
    assertEquals(10, sentences.get(0).sentenceTokenOffsetEnd());
    assertEquals(10, sentences.get(1).sentenceTokenOffsetBegin());
    assertEquals(17, sentences.get(1).sentenceTokenOffsetEnd());
  }

  public Sentence tokenizeAndSplitAnnotation(Annotation ann) {
    StanfordCoreNLP pipeline = new StanfordCoreNLP(new Properties(){{
      setProperty("annotators", "tokenize,ssplit");
    }});

    pipeline.annotate(ann);
    CoreMap map = ann.get(CoreAnnotations.SentencesAnnotation.class).get(0);
    return new Sentence(map);
  }

  @Test
  public void testFromCoreMapCrashCheck() {
    Annotation ann = new Annotation("This is a sentence.");
    tokenizeAndSplitAnnotation(ann);
  }

  @Test
  public void testFromCoreMapCorrectnessCheck() {
	Annotation ann = new Annotation("This is a sentence.");
    Sentence s = tokenizeAndSplitAnnotation(ann);
    assertEquals(ann.get(CoreAnnotations.TextAnnotation.class), s.text());
    assertEquals("This", s.word(0));
    assertEquals(5, s.length());
  }

  @Test
  public void testTokenizeWhitespaceSimple() {
    Sentence s = new Sentence(new ArrayList<String>(){{add("foo"); add("bar");}});
    assertEquals("foo", s.word(0));
    assertEquals("bar", s.word(1));
  }

  @Test
  public void testTokenizeWhitespaceWithSpaces() {
    Sentence s = new Sentence(new ArrayList<String>(){{add("foo"); add("with whitespace"); add("baz");}});
    assertEquals("foo", s.word(0));
    assertEquals("with whitespace", s.word(1));
    assertEquals("baz", s.word(2));
  }

  @Test
  public void testStringRepresentation() {
    String initialSentence = "This is a sentence.";
    Sentence sut = new Sentence(initialSentence);
    String converted = sut.toString();
    assertEquals(initialSentence, converted);
  }

  @Test
  public void testSubstring() {
    String initialSentence = "This is a sentence.";
    Sentence sut = new Sentence(initialSentence);
    String substring = sut.substring(1, 4);
    assertEquals("is a sentence", substring);
  }

  @Test
  public void testSentenceFromDocument() {
    String initialDocument = "This is a sentence. "
                           + "This is another sentence. "
                           + "Together, they form a document!";
    Document d = new Document(initialDocument);
    int sentenceIndex = 2;
    Sentence sut = new Sentence(d, sentenceIndex);
    String sentence = sut.toString();
    assertEquals("Together, they form a document!", sentence);
  }

  @Test
  public void testSerializeToStream() throws IOException {
    final class MockOutputStream extends OutputStream {
      int writeCount = 0;
        boolean closed = false;

        protected int getWriteCount () {
          return writeCount;
        }

        protected boolean gotClosed() {
          return closed;
        }
        @Override
        public void write(int arg0) throws IOException {
          writeCount++;
        }
        @Override
        public void close () throws IOException {
          this.closed = true;
          super.close();
        }
    }
    Sentence sut = new Sentence("This is a sentence.");
    MockOutputStream mockedOutStream = new MockOutputStream();
    sut.serialize(mockedOutStream);

    /* The serialize method should write but should not close the
     * provided OutputStream as specified in Sentence.java. */
    boolean wasWrittenTo = mockedOutStream.getWriteCount() > 0;
    boolean wasNotClosed = !mockedOutStream.gotClosed();

    assertTrue(wasWrittenTo);
    assertTrue(wasNotClosed);
  }
}
