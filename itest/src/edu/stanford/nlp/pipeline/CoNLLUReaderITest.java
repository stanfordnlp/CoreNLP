package edu.stanford.nlp.pipeline;

import edu.stanford.nlp.ling.*;
import edu.stanford.nlp.semgraph.*;
import edu.stanford.nlp.util.*;

import static org.junit.Assert.assertEquals;

import java.io.*;
import java.util.Properties;

import org.junit.Before;
import org.junit.Test;

/**
 * Test reading in a CoNLL-U document.  Compare the Annotation created from file to a gold one.
 */
public class CoNLLUReaderITest {

  public String exampleDocument = "Pero la existencia de dos recién nacidos en la misma caja sólo podía deberse a un " +
      "descuido de fábrica.\nDe allí las rebajas.\n";
  public String examplePath = String.format("%s/stanford-corenlp/testing/data/conllu/es-example.conllu", TestPaths.testHome());
  public StanfordCoreNLP pipeline;
  public Annotation goldDocument;
  public Annotation readInDocument;

  @Before
  public void setUp() throws IOException {
    // set up the pipeline
    Properties props = LanguageInfo.getLanguageProperties("spanish");
    props.put("annotators", "tokenize,ssplit,mwt,pos,lemma,depparse");
    pipeline = new StanfordCoreNLP(props);
  }

  @Test
  public void testReadingInCoNLLUFile() throws ClassNotFoundException, IOException {
    goldDocument = pipeline.process(exampleDocument);
    readInDocument = new CoNLLUReader(new Properties()).readCoNLLUFile(examplePath).get(0);
    // make some changes for sake of comparison
    // remove AfterAnnotation from read in
    // remove ParentAnnotation from gold
    for (CoreLabel token : goldDocument.get(CoreAnnotations.TokensAnnotation.class)) {
      token.remove(CoreAnnotations.ParentAnnotation.class);
    }
    // compare gold vs. read in
    // compare document text
    assertEquals(goldDocument.get(CoreAnnotations.TextAnnotation.class),
        readInDocument.get(CoreAnnotations.TextAnnotation.class));
    // compare tokens lists
    AnnotationComparator.compareTokensLists(goldDocument, readInDocument);
    assertEquals(goldDocument.get(CoreAnnotations.TokensAnnotation.class),
        readInDocument.get(CoreAnnotations.TokensAnnotation.class));
    // compare sentences
    for (int i = 0; i < goldDocument.get(CoreAnnotations.SentencesAnnotation.class).size(); i++) {
      CoreMap goldSentence = goldDocument.get(CoreAnnotations.SentencesAnnotation.class).get(i);
      CoreMap readInSentence = readInDocument.get(CoreAnnotations.SentencesAnnotation.class).get(i);
      // compare sentence text
      assertEquals(goldSentence.get(CoreAnnotations.TextAnnotation.class),
          readInSentence.get(CoreAnnotations.TextAnnotation.class));
      // compare token lists
      assertEquals(goldSentence.get(CoreAnnotations.TokensAnnotation.class),
          readInSentence.get(CoreAnnotations.TokensAnnotation.class));
      // compare semantic graphs
      SemanticGraph goldGraph =
          goldDocument.get(CoreAnnotations.SentencesAnnotation.class).get(i).get(
              SemanticGraphCoreAnnotations.BasicDependenciesAnnotation.class);
      SemanticGraph readInGraph =
          goldDocument.get(CoreAnnotations.SentencesAnnotation.class).get(i).get(
              SemanticGraphCoreAnnotations.BasicDependenciesAnnotation.class);
      assertEquals(goldGraph.toList(), readInGraph.toList());
    }
  }
}
