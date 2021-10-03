package edu.stanford.nlp.ie.crf;

import java.util.Properties;

import org.junit.Before;
import org.junit.Test;

import edu.stanford.nlp.util.TestPaths;

/**
 * Test that the CRFClassifier works when multiple classifiers are run
 * in multiple threads.
 *
 *  @author John Bauer
 */
public class ThreadedCRFClassifierITest {

  Properties props;

  private static final String german1 =
    "edu/stanford/nlp/models/ner/german.distsim.crf.ser.gz";
  /** -- We're no longer supporting this one
  private String german2 =
    String.format("%s/ner/goodClassifiers/german.dewac_175m_600.crf.ser.gz", TestPaths.testHome());
  */
  private static final String germanTestFile = String.format("%s/german/ner/2016/deu.io.f15.utf8.testa", TestPaths.testHome());

  private static final String english1 = "edu/stanford/nlp/models/ner/english.all.3class.distsim.crf.ser.gz";
  private static final String english2 = "edu/stanford/nlp/models/ner/english.conll.4class.distsim.crf.ser.gz";
  private static final String englishTestFile = String.format("%s/ner/column_data/conll.4class.testa", TestPaths.testHome());

  private static final String germanEncoding = "utf-8";
  private static final String englishEncoding = "utf-8";

  @Before
  public void setUp() {
    props = new Properties();
  }

  @Test
  public void testOneEnglishCRF() {
    props.setProperty("crf1", english1);
    props.setProperty("testFile", englishTestFile);
    props.setProperty("inputEncoding", englishEncoding);
    TestThreadedCRFClassifier.runTest(props);
  }

  @Test
  public void testOneGermanCRF() {
    props.setProperty("crf1", german1);
    props.setProperty("testFile", germanTestFile);
    props.setProperty("inputEncoding", germanEncoding);
    TestThreadedCRFClassifier.runTest(props);
  }

  @Test
  public void testTwoEnglishCRFs() {
    props.setProperty("crf1", english1);
    props.setProperty("crf2", english2);
    props.setProperty("testFile", englishTestFile);
    props.setProperty("inputEncoding", englishEncoding);
    TestThreadedCRFClassifier.runTest(props);
  }

}
