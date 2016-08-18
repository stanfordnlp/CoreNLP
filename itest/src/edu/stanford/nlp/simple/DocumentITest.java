package edu.stanford.nlp.simple;

import org.junit.Test;

import java.util.Map;

import edu.stanford.nlp.coref.data.CorefChain;

import static org.junit.Assert.*;

/**
 * A test for {@link edu.stanford.nlp.simple.Document}, using the NLP models.
 *
 * @author Gabor Angeli
 */
public class DocumentITest {

  @Test
  public void testCoref() {
    Document doc = new Document("John Bauer walked his dog, Cirrus.");
    assertNotNull(doc);
    Map<Integer, CorefChain> corefChains = doc.coref();
    assertNotNull(corefChains);
    assertTrue(corefChains.size() > 0);
  }
}
