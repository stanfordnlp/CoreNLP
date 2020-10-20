package edu.stanford.nlp.ling;

import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.Properties;

import org.junit.Test;

import edu.stanford.nlp.pipeline.CoreDocument;
import edu.stanford.nlp.pipeline.LanguageInfo;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;

/**
 * Basic testing of rebuilding text from CoreLabel(s).
 */


public class SentenceUtilsITest {

  @Test
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

  @Test
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
