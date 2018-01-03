package edu.stanford.nlp.ie.crf;

import junit.framework.TestCase;

import java.util.Properties;

/**
 * Test that the CRFClassifier works when multiple classifiers are run
 * in multiple threads.
 *
 *  @author John Bauer
 */
public class ThreadedCRFClassifierITest extends TestCase {

  Properties props;

  private static final String german1 =
    "edu/stanford/nlp/models/ner/german.conll.germeval2014.hgc_175m_600.crf.ser.gz";
  /** -- We're no longer supporting this one
  private String german2 =
    "/u/nlp/data/ner/goodClassifiers/german.dewac_175m_600.crf.ser.gz";
  */
  private static final String germanTestFile = "/u/nlp/data/german/ner/2016/deu.io.f15.utf8.testa";

  private static final String english1 =
    "/u/nlp/data/ner/goodClassifiers/english.all.3class.nodistsim.crf.ser.gz";
  private static final String english2 =
    "/u/nlp/data/ner/goodClassifiers/english.conll.4class.distsim.crf.ser.gz";
  private static final String englishTestFile = "/u/nlp/data/ner/column_data/conll.4class.testa";

  private static final String germanEncoding = "utf-8";
  private static final String englishEncoding = "utf-8";

  @Override
  public void setUp() {
    props = new Properties();
  }

  public void testOneEnglishCRF() {
    props.setProperty("crf1", english1);
    props.setProperty("testFile", englishTestFile);
    props.setProperty("inputEncoding", englishEncoding);
    TestThreadedCRFClassifier.runTest(props);
  }

  public void testOneGermanCRF() {
    props.setProperty("crf1", german1);
    props.setProperty("testFile", germanTestFile);
    props.setProperty("inputEncoding", germanEncoding);
    TestThreadedCRFClassifier.runTest(props);
  }

  public void testTwoEnglishCRFs() {
    props.setProperty("crf1", english1);
    props.setProperty("crf2", english2);
    props.setProperty("testFile", englishTestFile);
    props.setProperty("inputEncoding", englishEncoding);
    TestThreadedCRFClassifier.runTest(props);
  }

}
