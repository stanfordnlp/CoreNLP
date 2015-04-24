package edu.stanford.nlp.simple;

import org.junit.Test;

import java.util.List;

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
  public void testLength() {
    Sentence sent = new Sentence("the quick brown fox jumped over the lazy dog");
    assertEquals(9, sent.length());
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
    assertEquals("-LRB-", sent.word(4));
    assertEquals("-RRB-", sent.word(6));
    assertEquals("'s", sent.word(8));
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
}
