package edu.stanford.nlp.ie.crf;

import java.nio.file.Files;
import java.nio.file.Path;
import java.io.File;
import java.util.List;
import java.util.Scanner;

import org.junit.Assert;
import org.junit.Test;

import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.util.PropertiesUtils;
import edu.stanford.nlp.util.logging.Redwood;
import edu.stanford.nlp.util.logging.StanfordRedwoodConfiguration;


public class TrainCRFClassifierSlowITest {

  private static final Redwood.RedwoodChannels log = Redwood.channels(TrainCRFClassifierSlowITest.class);


  @Test
  public void testGermanCRFClassifierTraining() throws Exception {
    Path tempdir = Files.createTempDirectory("ner");
    tempdir.toFile().deleteOnExit();
    log.info("Temp directory: " + tempdir);

    StanfordRedwoodConfiguration.apply(PropertiesUtils.asProperties(
        "log.file", tempdir + "/german-crf.results"));

    // train the new model
    // requires the german ner model in the classpath
    CRFClassifier.main(new String[] {
        "-props", "edu/stanford/nlp/models/ner/german.distsim.prop",
        "-serializeTo", tempdir + "/german.distsim.crf.ser.gz"
    });
    List<String> germanTrainingResults = IOUtils.linesFromFile(tempdir + "/german-crf.results");
    String lastLineOfResults = germanTrainingResults.get(germanTrainingResults.size() - 1);
    Scanner scanner = new Scanner(lastLineOfResults);
    // ignore word "Totals"
    scanner.next();
    double p = scanner.nextDouble();
    Assert.assertEquals("Precision outside target range", 0.8628, p, 0.002);
    double r = scanner.nextDouble();
    Assert.assertEquals("Recall outside target range", 0.7406, r, 0.005);
    double f1 = scanner.nextDouble();
    Assert.assertEquals("F1 outside target range", 0.7969, f1, 0.002);
  }

  // Previous results (Totals on CoNLL 2003 testa)
  // P          R       F1      TP      FP      FN
  // 0.8404	0.6743	0.7482	3259	619	1574    2016 and 2017
  // 0.8364	0.6924	0.7576	3334	652	1481    2018

}
