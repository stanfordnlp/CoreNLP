package edu.stanford.nlp.ie.crf;

import java.io.File;
import java.util.List;
import java.util.Scanner;

import org.junit.Assert;
import org.junit.Test;

import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.util.PropertiesUtils;
import edu.stanford.nlp.util.logging.StanfordRedwoodConfiguration;


public class TrainCRFClassifierSlowITest {

  private static final String crfTrainingWorkingDir = "/u/scr/nlp/data/stanford-corenlp-testing/crf-classifier-training";


  @SuppressWarnings("ResultOfMethodCallIgnored")
  @Test
  public void testGermanCRFClassifierTraining() throws Exception {
    StanfordRedwoodConfiguration.apply(PropertiesUtils.asProperties(
            "log.file", crfTrainingWorkingDir + "/german-crf.results"));
    // delete the model if present
    File originalModelFile = new File(crfTrainingWorkingDir, "german.hgc_175m_600.crf.ser.gz");
    originalModelFile.delete();
    // train the new model
    CRFClassifier.main(new String[] {
            "-props", "edu/stanford/nlp/models/ner/german-2018.hgc_175m_600.prop",
            "-serializeTo", crfTrainingWorkingDir+"/german.hgc_175m_600.crf.ser.gz"
    });
    // check for lack of quality drop
    // CRFClassifier.main(new String[]{"-props",
    //         "/scr/nlp/data/stanford-corenlp-testing/crf-classifier-training/german-crf-example-test.prop"});
    List<String> germanTrainingResults = IOUtils.linesFromFile(crfTrainingWorkingDir + "/german-crf.results");
    String lastLineOfResults = germanTrainingResults.get(germanTrainingResults.size() - 1);
    //System.err.println("last line: "+lastLineOfResults.trim());
    Scanner scanner = new Scanner(lastLineOfResults);
    // ignore word "Totals"
    scanner.next();
    double p = scanner.nextDouble();
    Assert.assertEquals("Precision outside target range", 0.8364, p, 0.001);
    double r = scanner.nextDouble();
    Assert.assertEquals("Recall outside target range", 0.6924, r, 0.001);
    double f1 = scanner.nextDouble();
    Assert.assertEquals("Precision outside target range", 0.7576, f1, 0.001);
  }

  // Previous results (Totals on CoNLL 2003 testa)
  // P          R       F1      TP      FP      FN
  // 0.8404	0.6743	0.7482	3259	619	1574    2016 and 2017
  // 0.8364	0.6924	0.7576	3334	652	1481    2018

}
