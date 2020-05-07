package edu.stanford.nlp.simple;

import org.junit.Test;
import org.junit.Before;

import java.util.List;
import java.util.Optional;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertEquals;

/**
 * A test for aspects of {@link edu.stanford.nlp.simple.Document} which do not require loading the NLP models.
 *
 * @author Gabor Angeli
 */
public class DocumentTest {
  Document oneSentenceDocument;
  Document twoSentenceDocument;

  @Before
  public void prepareDocuments() {
    oneSentenceDocument = new Document("the quick brown fox jumped over the lazy dog");
    twoSentenceDocument = new Document("the quick brown fox jumped over the lazy dog. The lazy dog was not impressed.");
  }

  @Test
  public void testCreateFromText() {
    assertNotNull(oneSentenceDocument);
    assertNotNull(twoSentenceDocument);
  }

  @Test
  public void testText() {
    Document doc = oneSentenceDocument;
    assertEquals("the quick brown fox jumped over the lazy dog", doc.text());
  }

  @Test
  public void testDocid() {
    Document doc = oneSentenceDocument;
    assertEquals(Optional.<String>empty(), doc.docid());
    assertEquals(Optional.of("foo"), doc.setDocid("foo").docid());
  }

  @Test
  public void testSentences() {
    Document doc = twoSentenceDocument;
    List<Sentence> sentences = doc.sentences();
    assertEquals(2, sentences.size());
    assertEquals("the quick brown fox jumped over the lazy dog.", sentences.get(0).text());
    assertEquals("The lazy dog was not impressed.", sentences.get(1).text());
  }
}
