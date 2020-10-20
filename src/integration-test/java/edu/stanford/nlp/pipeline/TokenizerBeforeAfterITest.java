package edu.stanford.nlp.pipeline;

import junit.framework.TestCase;

import java.util.*;

public class TokenizerBeforeAfterITest extends TestCase {

  /** Test that using standard tokenization with ssplit.eolonly works properly **/
  public void testBasicExample() {
    // basic example
    String text = "I am a sentence.\nI am another sentence.";
    // build pipeline
    Properties props = new Properties();
    props.setProperty("annotators", "tokenize,ssplit");
    props.setProperty("ssplit.eolonly", "true");
    StanfordCoreNLP pipeline = new StanfordCoreNLP(props);
    // annotate
    CoreDocument doc = new CoreDocument(pipeline.process(text));
    // check results
    assertEquals("\n", doc.tokens().get(4).after());
    assertEquals("\n", doc.tokens().get(5).before());
  }
}
