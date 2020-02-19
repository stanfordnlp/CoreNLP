package edu.stanford.nlp.pipeline;

import edu.stanford.nlp.ling.*;

import junit.framework.TestCase;

import java.util.*;
import java.util.stream.*;

public class PreTokenizedSentenceSplitITest extends TestCase {

  /** Test that using tokenize.whitespace and ssplit.eolonly creates 2 sentences in basic example **/
  public void testBasicExample() {
    // basic example
    String text = "I am a sentence .\nI am another sentence .";
    // add gold tokens
    List<List<String>> expectedTokens = new ArrayList<>();
    expectedTokens.add(Arrays.asList("I", "am", "a", "sentence", "."));
    expectedTokens.add(Arrays.asList("I", "am", "another", "sentence", "."));
    // build pipeline
    Properties props = new Properties();
    props.setProperty("annotators", "tokenize,ssplit");
    props.setProperty("tokenize.whitespace", "true");
    props.setProperty("ssplit.eolonly", "true");
    StanfordCoreNLP pipeline = new StanfordCoreNLP(props);
    // annotate
    CoreDocument doc = new CoreDocument(pipeline.process(text));
    // check results
    assertEquals(2, doc.sentences().size());
    assertEquals(expectedTokens.get(0),
        doc.sentences().get(0).tokens().stream().map(t -> t.word()).collect(Collectors.toList()));
    assertEquals(expectedTokens.get(1),
        doc.sentences().get(1).tokens().stream().map(t -> t.word()).collect(Collectors.toList()));
    assertEquals("", doc.tokens().get(4).before());
    assertEquals(null, doc.tokens().get(4).get(CoreAnnotations.AfterAnnotation.class));
  }

}
