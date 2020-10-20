package edu.stanford.nlp.pipeline;

import java.util.Properties;
import junit.framework.TestCase;

public class QuoteAttributionAnnotatorITest extends TestCase {

  public String COREF_EXAMPLE = "Joe Smith decided to get lunch.  He said, \"I am going to order a pizza.\"";
  public String COREF_EXAMPLE_TWO =
      "Chris Anderson went to the store.  Chris said, \"I'd like to order that item.\"";
  public StanfordCoreNLP pipeline;


  @Override
  public void setUp() {
  }

  public void testCorefExample() {
    // set up pipeline
    Properties props = new Properties();
    props.setProperty("annotators", "tokenize,ssplit,pos,lemma,ner,depparse,coref,quote");
    pipeline = new StanfordCoreNLP(props);
    // set up annotations
    CoreDocument corefDocOne = new CoreDocument(COREF_EXAMPLE);
    CoreDocument corefDocTwo = new CoreDocument(COREF_EXAMPLE_TWO);
    // annotate
    pipeline.annotate(corefDocOne);
    pipeline.annotate(corefDocTwo);
    // check quote
    CoreQuote quoteOne = corefDocOne.quotes().get(0);
    CoreQuote quoteTwo = corefDocTwo.quotes().get(0);
    // quote one
    assertEquals("\"I am going to order a pizza.\"", quoteOne.text());
    assertEquals("Joe Smith", quoteOne.speaker().get());
    assertEquals("Joe Smith", quoteOne.canonicalSpeaker().get());
    assertEquals(1,corefDocOne.quotes().size());
    // quote two
    assertEquals("\"I'd like to order that item.\"", quoteTwo.text());
    assertEquals("Chris", quoteTwo.speaker().get());
    assertEquals("Chris Anderson", quoteTwo.canonicalSpeaker().get());
    assertEquals(1,corefDocTwo.quotes().size());
  }

}
