package edu.stanford.nlp.time;

import edu.stanford.nlp.ling.*;
import edu.stanford.nlp.pipeline.*;

import junit.framework.*;

import java.util.*;


public class SUTimeBritishITest extends TestCase {

  public StanfordCoreNLP pipeline;

  @Override
  public void setUp() {
    Properties props = new Properties();
    props.setProperty("annotators", "tokenize,ssplit,pos,lemma,ner");
    props.setProperty("sutime.language", "british");
    pipeline = new StanfordCoreNLP(props);
  }

  public String normalizedDate(CoreDocument doc) {
    return doc.entityMentions().get(0).tokens().get(0).get(CoreAnnotations.NormalizedNamedEntityTagAnnotation.class);
  }

  public void testBritishStyleDateRecognition() {
    Properties props = new Properties();
    // sample text
    CoreDocument britishDocumentOne = new CoreDocument("The event will be on 10/12/2017.");
    CoreDocument britishDocumentTwo = new CoreDocument("The event will be on 30/12/2017.");
    CoreDocument americanDocumentOne = new CoreDocument("The event will be on 10/12/2017.");
    CoreDocument americanDocumentTwo = new CoreDocument("The event will be on 30/12/2017.");
    // run british pipeline
    props.setProperty("annotators", "tokenize,ssplit,pos,lemma,ner");
    props.setProperty("sutime.language", "british");
    pipeline = new StanfordCoreNLP(props);
    pipeline.annotate(britishDocumentOne);
    pipeline.annotate(britishDocumentTwo);
    // run american pipeline
    props.setProperty("annotators", "tokenize,ssplit,pos,lemma,ner");
    props.setProperty("sutime.language", "english");
    pipeline = new StanfordCoreNLP(props);
    pipeline.annotate(americanDocumentOne);
    pipeline.annotate(americanDocumentTwo);
    System.err.println("---");
    System.err.println("doc 1: "+britishDocumentOne.text());
    System.err.println("doc 2: "+britishDocumentTwo.text());
    System.err.println("british date 1: "+normalizedDate(britishDocumentOne));
    System.err.println("british date 2: "+normalizedDate(britishDocumentTwo));
    System.err.println("american date 1: "+normalizedDate(americanDocumentOne));
    System.err.println("american date 2: "+normalizedDate(americanDocumentTwo));
    assertEquals(normalizedDate(britishDocumentOne), "2017-12-10");
    assertEquals(normalizedDate(britishDocumentTwo), "2017-12-30");
    assertEquals(normalizedDate(americanDocumentOne), "2017-10-12");
    assertEquals(normalizedDate(americanDocumentTwo), null);
  }


}
