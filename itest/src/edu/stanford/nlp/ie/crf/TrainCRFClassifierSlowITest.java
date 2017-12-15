package edu.stanford.nlp.ie.crf;

import junit.framework.TestCase;

import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.sequences.SeqClassifierFlags;
import edu.stanford.nlp.util.StringUtils;

import java.io.*;
import java.nio.file.*;
import java.util.Properties;


public class TrainCRFClassifierSlowITest extends TestCase {

  public static String crfTrainingWorkingDir =
      "/scr/nlp/data/stanford-corenlp-testing/crf-classifier-training";

  public static String expectedGermanPerformanceLine = "Totals\t0.8408\t0.6752\t0.7489\t3263\t618\t1570";

  public void testCRFClassifierTraining() throws Exception {
    // delete the model if present
    String originalModelPath =
        "/scr/nlp/data/stanford-corenlp-testing/crf-classifier-training/german.hgc_175m_600.crf.ser.gz";
    File originalModelFile = new File(originalModelPath);
    originalModelFile.delete();
    // train the new model
    CRFClassifier.main(new String[]{"-props",
        "/scr/nlp/data/stanford-corenlp-testing/crf-classifier-training/german-crf-example-train.prop"});
    // check for lack of quality drop
    ByteArrayOutputStream germanCRFTestBAOS = new ByteArrayOutputStream();
    PrintStream germanCRFPrintStream = new PrintStream(germanCRFTestBAOS);
    PrintStream originalSystemErr = System.err;
    System.setErr(germanCRFPrintStream);
    CRFClassifier.main(new String[]{"-props",
        "/scr/nlp/data/stanford-corenlp-testing/crf-classifier-training/german-crf-example-test.prop"});
    System.err.flush();
    System.setErr(originalSystemErr);
    String germanTrainingResults = germanCRFTestBAOS.toString();
    System.out.println("---");
    System.out.println("German CRF Training Results");
    System.out.println(germanTrainingResults);
    String[] resultLines = germanTrainingResults.split("\n");
    int finalLineLength = resultLines[10].length();
    System.out.println("last line: "+resultLines[10].substring(70,finalLineLength));
    System.out.println("last line: "+expectedGermanPerformanceLine);
    assertEquals(resultLines[10].substring(70,finalLineLength), expectedGermanPerformanceLine);
  }

}
