package edu.stanford.nlp.pipeline;

import junit.framework.TestCase;

import java.util.List;
import edu.stanford.nlp.ling.CoreLabel;

public class StringToAnnotatedWordsPipelineITest extends TestCase {
  StringToAnnotatedWordsPipeline staw = null;

  @Override
  public void setUp() {
    synchronized(StringToAnnotatedWordsPipelineITest.class) {
      if (staw == null) {
        staw = new StringToAnnotatedWordsPipeline();
      }
    }
  }

  public void testString() {
    List<CoreLabel> results = staw.processText("This is a simple test.");
    assertEquals(6, results.size());
    String[] expectedSplit = {"This", "is", "a", "simple", "test", "."};
    String[] expectedTags = {"DT", "VBZ", "DT", "JJ", "NN", "."};
    String[] expectedLemmas = {"this", "be", "a", "simple", "test", "."};
    for (int i = 0; i < 6; ++i) {
      assertEquals(expectedSplit[i], results.get(i).word());
      assertEquals(expectedTags[i], results.get(i).tag());
      assertEquals(expectedLemmas[i], results.get(i).lemma());
    }    
  }
}

