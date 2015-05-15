package edu.stanford.nlp.simple;

import org.junit.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.Assert.*;

/**
 * A test for aspects of {@link edu.stanford.nlp.simple.Document} which do not require loading the NLP models.
 *
 * @author Gabor Angeli
 */
public class DocumentTest {

  @Test
  public void testCreateFromText() {
    Document doc = new Document("the quick brown fox jumped over the lazy dog");
    assertNotNull(doc);
  }

  @Test
  public void testText() {
    Document doc = new Document("the quick brown fox jumped over the lazy dog");
    assertEquals("the quick brown fox jumped over the lazy dog", doc.text());
  }

  @Test
  public void testDocid() {
    Document doc = new Document("the quick brown fox jumped over the lazy dog");
    assertEquals(Optional.<String>empty(), doc.docid());
    assertEquals(Optional.of("foo"), doc.setDocid("foo").docid());
  }

  @Test
  public void testSentences() {
    Document doc = new Document("the quick brown fox jumped over the lazy dog. The lazy dog was not impressed.");
    List<Sentence> sentences = doc.sentences();
    assertEquals(2, sentences.size());
    assertEquals("the quick brown fox jumped over the lazy dog.", sentences.get(0).text());
    assertEquals("The lazy dog was not impressed.", sentences.get(1).text());
  }

}
