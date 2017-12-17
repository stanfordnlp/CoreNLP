package edu.stanford.nlp.ie.crf;

import junit.framework.TestCase;

import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.sequences.SeqClassifierFlags;
import edu.stanford.nlp.util.*;
import edu.stanford.nlp.util.logging.*;
import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.util.StringUtils;

import java.io.*;
import java.nio.file.*;
import java.util.List;
import java.util.Properties;


public class TrainCRFClassifierSlowITest extends TestCase {

  public static String crfTrainingWorkingDir =
      "/scr/nlp/data/stanford-corenlp-testing/crf-classifier-training";

  public static String expectedGermanPerformanceLine = "Totals\t0.8416\t0.6760\t0.7497\t3267\t615\t1566";

  
  public void testCRFClassifierTraining() throws Exception {
    StanfordRedwoodConfiguration.apply(PropertiesUtils.asProperties("log.file", "/scr/nlp/data/stanford-corenlp-testing/german-crf.results"));
    // delete the model if present
    String originalModelPath =
        "/scr/nlp/data/stanford-corenlp-testing/crf-classifier-training/german.hgc_175m_600.crf.ser.gz";
    File originalModelFile = new File(originalModelPath);
    originalModelFile.delete();
    // train the new model
    CRFClassifier.main(new String[]{"-props",
        "/scr/nlp/data/stanford-corenlp-testing/crf-classifier-training/german-crf-example-train.prop"});
    // check for lack of quality drop
    CRFClassifier.main(new String[]{"-props",
        "/scr/nlp/data/stanford-corenlp-testing/crf-classifier-training/german-crf-example-test.prop"});
    List<String> germanTrainingResults = IOUtils.linesFromFile("/scr/nlp/data/stanford-corenlp-testing/german-crf.results");
    String lastLineOfResults = germanTrainingResults.get(10);
    System.out.println("last line: "+lastLineOfResults.trim());
    System.out.println("last line: "+expectedGermanPerformanceLine);
    assertEquals(lastLineOfResults.trim(), expectedGermanPerformanceLine);
  }

}
