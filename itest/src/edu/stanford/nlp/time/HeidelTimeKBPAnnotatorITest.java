package edu.stanford.nlp.time;

import edu.stanford.nlp.pipeline.*;
import edu.stanford.nlp.io.*;
import edu.stanford.nlp.util.*;
import java.util.*;
import junit.framework.TestCase;


public class HeidelTimeKBPAnnotatorITest extends TestCase {

  public StanfordCoreNLP pipeline;
  public String WORKING_DIR = "/u/scr/nlp/data/stanford-corenlp-testing/spanish-heideltime";
  public Set<String> GOLD_RESULTS = new HashSet<>(
      Arrays.asList("Rusia\tCOUNTRY", "Japón\tCOUNTRY", "hoy\tDATE","rusa\tLOCATION", "Vicente Fox\tPERSON",
          "el 2 de julio de 1942\tDATE", "Esta semana\tDATE", "ING\tORGANIZATION",
          "14\tNUMBER", "12\tNUMBER"));

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
    Set<String> outputResults = new HashSet<>();
    for (CoreEntityMention em : testDocument.entityMentions())
      outputResults.add(em.text()+"\t"+em.entityType());
    assertEquals(GOLD_RESULTS,outputResults);
  }

}
