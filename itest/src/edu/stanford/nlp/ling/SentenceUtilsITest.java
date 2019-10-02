package edu.stanford.nlp.ling;

import edu.stanford.nlp.pipeline.*;

import junit.framework.TestCase;

import java.io.*;
import java.util.*;

/**
 * Basic testing of rebuilding text from CoreLabel(s).
 */


public class SentenceUtilsITest extends TestCase {

  public void testRebuildingMWTText() throws IOException {
    // set up French properties
    Properties frenchProperties = LanguageInfo.getLanguageProperties("french");
    frenchProperties.setProperty("annotators", "tokenize,ssplit,mwt");
    StanfordCoreNLP frenchPipeline = new StanfordCoreNLP(frenchProperties);
    String frenchText = "Le but des bandes de roulement est d'augmenter la traction.";
    CoreDocument frenchDoc = new CoreDocument(frenchPipeline.process(frenchText));
    String rebuiltFrenchText = SentenceUtils.listToOriginalTextString(frenchDoc.tokens());
    assertTrue(frenchText.equals(rebuiltFrenchText));
  }

  public void testRebuildingText() {
    // set up basic English pipeline
    Properties basicProperties = new Properties();
    basicProperties.setProperty("annotators", "tokenize,ssplit");
    StanfordCoreNLP pipeline = new StanfordCoreNLP(basicProperties);
    String text = "Let's hope this doesn't not work properly.  Especially across sentences. ";
    CoreDocument doc = new CoreDocument(pipeline.process(text));
    String rebuiltText = SentenceUtils.listToOriginalTextString(doc.tokens());
    assertTrue(text.equals(rebuiltText));
  }

}
