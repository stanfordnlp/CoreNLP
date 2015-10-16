package edu.stanford.nlp.neural;

import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;

import static org.junit.Assert.assertEquals;

/**
 * Test out word vector loading.
 *
 * @author Gabor Angeli
 */
public class WordVectorsITest {

  @Test
  public void testReadWord2Vec() throws IOException {
    WordVectors vec = WordVectors.readWord2Vec("/scr/nlp/data/coref/wordvectors/en/vectors.txt.gz");
    File tmp = File.createTempFile("word2vec", ".ser.gz");
    System.err.println(tmp.getPath());
    //tmp.deleteOnExit();
    vec.serialize(tmp.getPath());
    WordVectors reread = WordVectors.deserialize(tmp.getPath());
    assertEquals(vec, reread);
  }

}
