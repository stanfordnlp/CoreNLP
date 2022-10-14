/**
 * Simple test that when you send a MorphologyRequest to a
 * ProcessMorphologyRequest, you get back a MorphologyResponse with
 * the right number of tokens
 *
 * @author John Bauer
 */
package edu.stanford.nlp.process;

import org.junit.Assert;
import org.junit.Test;

import edu.stanford.nlp.pipeline.CoreNLPProtos;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

public class ProcessMorphologyRequestTest {
  private final String[][] INPUTS = {
    { "John", "NNP", },
    { "antennae", "NNS", },
    { "licked", "VBN", },
  };

  private final String[][] OUTPUTS = {
    { "John", "NNP", "John", },
    { "antennae", "NNS", "antenna" },
    { "licked", "VBN", "lick" },
  };

  public static CoreNLPProtos.MorphologyRequest buildFakeRequest(String[][] inputs) {
    CoreNLPProtos.MorphologyRequest.Builder request = CoreNLPProtos.MorphologyRequest.newBuilder();
    for (String[] tw : inputs) {
      CoreNLPProtos.MorphologyRequest.TaggedWord.Builder twBuilder = CoreNLPProtos.MorphologyRequest.TaggedWord.newBuilder();
      twBuilder.setWord(tw[0]);
      twBuilder.setXpos(tw[1]);
      request.addWords(twBuilder.build());
    }
    return request.build();
  }

  public static void checkResponse(CoreNLPProtos.MorphologyResponse response, String[][] outputs) {
    Assert.assertEquals("Expected " + outputs.length + " outputs", outputs.length, response.getWordsList().size());
    int idx = 0;
    for (CoreNLPProtos.MorphologyResponse.WordTagLemma wtl : response.getWordsList()) {
      Assert.assertEquals(outputs[idx][0], wtl.getWord());
      Assert.assertEquals(outputs[idx][1], wtl.getXpos());
      Assert.assertEquals(outputs[idx][2], wtl.getLemma());

      idx++;
    }
  }

  @Test
  public void testSimpleRequest() {
    ProcessMorphologyRequest processor = new ProcessMorphologyRequest();
    CoreNLPProtos.MorphologyRequest request = buildFakeRequest(INPUTS);
    CoreNLPProtos.MorphologyResponse response = processor.processRequest(request);
    checkResponse(response, OUTPUTS);
  }
}
