package edu.stanford.nlp.neural;

import org.junit.Test;

import java.io.File;
import java.io.IOException;

import static org.junit.Assert.assertEquals;

/**
 * Test out word vector loading.
 *
 * @author Gabor Angeli
 */
public class VectorMapITest {

  @Test
  public void testReadWord2Vec() throws IOException {
    VectorMap vec = VectorMap.readWord2Vec("/scr/nlp/data/coref/wordvectors/en/vectors.txt.gz");
    File tmp = File.createTempFile("word2vec", ".ser.gz");
    System.err.println(tmp.getPath());
    //tmp.deleteOnExit();
    vec.serialize(tmp.getPath());
    VectorMap reread = VectorMap.deserialize(tmp.getPath());
    assertEquals(vec, reread);
  }

}
