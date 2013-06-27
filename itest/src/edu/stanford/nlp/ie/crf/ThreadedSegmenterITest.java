package edu.stanford.nlp.ie.crf;

import junit.framework.TestCase;

import java.util.Properties;

/** 
 * Test that the CRFClassifier works when multiple classifiers are run
 * in multiple threads, in Chinese Segmentation mode.
 * <br>
 * Incidentally, tests that various data used by the segmenter doesn't
 * get blown away somehow.
 * <br>
 * command line we want to run:
 * <br>
 * java -mx3g edu.stanford.nlp.ie.crf.CRFClassifier -sighanCorporaDict /u/nlp/data/gale/segtool/stanford-seg/data -loadClassifier /u/nlp/data/gale/segtool/stanford-seg/classifiers-2010/pk-chris6.lex.gz -testFile /u/nlp/segtool/stanford-seg/data/Sighan2006/CTB_train_test/test/CTB.utf8.simp -inputEncoding utf-8 -sighanPostProcessing true -serDictionary /u/nlp/data/gale/segtool/stanford-seg/classifiers/dict-chris6.ser.gz -keepAllWhitespaces false
 *  @author John Bauer
 */
public class ThreadedSegmenterITest extends TestCase {
  Properties props;

  static final String crf1 = 
    "/u/nlp/data/gale/segtool/stanford-seg/classifiers-2010/pk-chris6.lex.gz";
  
  static final String crf2 = "/u/nlp/data/gale/segtool/stanford-seg/classifiers-2010/05202008-ctb6.processed-chris6.lex.gz";

  @Override
  public void setUp() {
    props = new Properties();
    props.setProperty("sighanCorporaDict", 
                      "/u/nlp/data/gale/segtool/stanford-seg/data");
    props.setProperty("testFile",
                      "/u/nlp/segtool/stanford-seg/data/Sighan2006/CTB_train_test/test/CTB.utf8.simp");
    props.setProperty("inputEncoding", "utf-8");
    props.setProperty("sighanPostProcessing", "true");
    props.setProperty("serDictionary", 
                      "/u/nlp/data/gale/segtool/stanford-seg/classifiers/dict-chris6.ser.gz");
    props.setProperty("keepAllWhitespaces", "false");
  }


  public void testPkuCRF() {
    System.out.println("Testing PKU segmenter");
    System.out.println("=====================");
    props.setProperty("crf1", crf1);
    TestThreadedCRFClassifier.runTest(props);
  }

  public void testCtbCRF() {
    System.out.println("Testing CTB segmenter");
    System.out.println("=====================");
    props.setProperty("crf1", crf2);
    TestThreadedCRFClassifier.runTest(props);
  }

  public void testTwoCRFs() {
    System.out.println("Testing two segmenters");
    System.out.println("======================");
    props.setProperty("crf1", crf1);
    props.setProperty("crf2", crf2);
    TestThreadedCRFClassifier.runTest(props);
  }  
}
