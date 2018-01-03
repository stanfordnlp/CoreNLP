package edu.stanford.nlp.time;

import edu.stanford.nlp.pipeline.*;
import edu.stanford.nlp.io.*;
import edu.stanford.nlp.util.*;

import junit.framework.TestCase;

import java.util.*;

public class HeidelTimeKBPAnnotatorITest extends TestCase {

  public StanfordCoreNLP pipeline;
  public String WORKING_DIR = "/scr/nlp/data/stanford-corenlp-testing/spanish-heideltime";
  public String GOLD_RESULTS = "Rusia\tCOUNTRY\n" +
      "Japón\tCOUNTRY\n" +
      "hoy\tDATE\n" +
      "rusa\tLOCATION\n" +
      "Vicente Fox\tPERSON\n" +
      "el 2 de julio de 1942\tDATE\n" +
      "Esta semana\tDATE\n" +
      "ING\tORGANIZATION\n" +
      "14\tNUMBER\n" +
      "12\tNUMBER";

  @Override
  public void setUp() {
    Properties props =
        StringUtils.argsToProperties(
            new String[]{"-props", WORKING_DIR+"/test.props"});
    pipeline = new StanfordCoreNLP(props);
  }

  public void testHeidelTimeKBPAnnotatorITest() {
    String testFileContents = IOUtils.stringFromFile(WORKING_DIR+"/example-sentences.txt");
    CoreDocument testDocument = new CoreDocument(testFileContents);
    pipeline.annotate(testDocument);
    String outputResults = "";
    for (CoreEntityMention em : testDocument.entityMentions())
      outputResults += (em.text()+"\t"+em.entityType()+"\n");
    outputResults = outputResults.trim();
    assertEquals(outputResults,GOLD_RESULTS);
  }

}
