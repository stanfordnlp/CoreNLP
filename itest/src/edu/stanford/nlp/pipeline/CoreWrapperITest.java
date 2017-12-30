package edu.stanford.nlp.pipeline;

import java.util.*;
import org.junit.Test;


public class CoreWrapperITest {

  @Test
  public void testPipeline() throws Exception {
    // set up pipeline
    Properties props = new Properties();
    props.setProperty("annotators", "tokenize,ssplit,pos,lemma,ner,depparse");
    StanfordCoreNLP pipeline = new StanfordCoreNLP(props);
    // make a basic document
    CoreDocument exampleDocument =
        new CoreDocument("Barack Obama was born in Hawaii.  He was elected president in 2008.");
    // annotate document
    pipeline.annotate(exampleDocument);
    // examine output
    System.err.println("---");
    System.err.println("document: ");
    System.err.println();
    System.err.println(exampleDocument.text());
    System.err.println("---");
    System.err.println("sentences: ");
    for (CoreSentence sentence : exampleDocument.sentences()) {
      System.err.println();
      System.err.println("sentence text: " + sentence.text());
      System.err.println("dependency parse: ");
      System.err.println(sentence.dependencyParse().toList());
      System.err.println("entity mentions: ");
      for (CoreEntityMention em : sentence.entityMentions())
        System.err.println(em.text());
    }
    System.err.println("---");
    System.err.println("entity mentions: ");
    for (CoreEntityMention entityMention : exampleDocument.entityMentions()) {
      System.err.println(entityMention.text());
    }
  }
}
