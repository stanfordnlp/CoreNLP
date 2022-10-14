/**
 * A module with a command line program for adding English lemmas to words with known POS
 *
 * @author John Bauer
 */

package edu.stanford.nlp.process;

import java.io.InputStream;
import java.io.IOException;
import java.io.OutputStream;

import edu.stanford.nlp.pipeline.CoreNLPProtos;
import edu.stanford.nlp.util.ProcessProtobufRequest;

public class ProcessMorphologyRequest extends ProcessProtobufRequest {
  /**
   * Turn a list of tagged words into a list of tagged words with lemma
   */
  public CoreNLPProtos.MorphologyResponse processRequest(CoreNLPProtos.MorphologyRequest request) {
    // create a Morphology here rather than as part of the object
    // so that each individual object is threadsafe
    Morphology morpha = new Morphology();

    CoreNLPProtos.MorphologyResponse.Builder responseBuilder = CoreNLPProtos.MorphologyResponse.newBuilder();
    for (CoreNLPProtos.MorphologyRequest.TaggedWord tw : request.getWordsList()) {
      String word = tw.getWord();
      String tag = tw.getXpos();

      String lemma = morpha.lemma(word, tag);

      CoreNLPProtos.MorphologyResponse.WordTagLemma.Builder wtlBuilder = CoreNLPProtos.MorphologyResponse.WordTagLemma.newBuilder();
      wtlBuilder.setWord(word);
      wtlBuilder.setXpos(tag);
      wtlBuilder.setLemma(lemma);
      responseBuilder.addWords(wtlBuilder.build());
    }
    return responseBuilder.build();
  }

  /**
   * Reads a single request from the InputStream, then writes back a single response.
   */
  @Override
  public void processInputStream(InputStream in, OutputStream out) throws IOException {
    CoreNLPProtos.MorphologyRequest request = CoreNLPProtos.MorphologyRequest.parseFrom(in);
    CoreNLPProtos.MorphologyResponse response = processRequest(request);
    response.writeTo(out);
  }

  /**
   * Command line tool for processing a Morphology request.
   * <br>
   * If -multiple is specified, will process multiple requests.
   */
  public static void main(String[] args) throws IOException {
    ProcessProtobufRequest.process(new ProcessMorphologyRequest(), args);
  }
}
