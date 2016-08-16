package edu.stanford.nlp.coref.hybrid;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.util.Properties;

import edu.stanford.nlp.dcoref.DcorefBenchmarkSlowITest;
import edu.stanford.nlp.util.BenchmarkingHelper;
import junit.framework.TestCase;

import edu.stanford.nlp.coref.CorefProperties;
import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.io.RuntimeIOException;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.util.StringUtils;

import static edu.stanford.nlp.dcoref.DcorefBenchmarkSlowITest.*;
import static edu.stanford.nlp.util.BenchmarkingHelper.setLowHighExpected;


/**
 * Run the Chinese dcoref system using the exact properties we distribute as
 * an example.  Check that the output does not change markedly from expected.
 * If performance numbers change, we'll notice and be able to verify
 * that it's intended.
 *
 * @author John Bauer
 * @author Christopher Manning
 */
public class DcorefChineseBenchmarkSlowITest extends TestCase {

  private static String runCorefTest(boolean deleteOnExit) throws Exception {
    final File WORK_DIR_FILE = File.createTempFile("DcorefChineseBenchmarkTest", "");
    if ( ! (WORK_DIR_FILE.delete() && WORK_DIR_FILE.mkdir())) {
      throw new RuntimeIOException("Couldn't create temp directory " + WORK_DIR_FILE);
    }
    if (deleteOnExit) {
      WORK_DIR_FILE.deleteOnExit();
    }

    String baseLogFile = WORK_DIR_FILE + File.separator + "log";

    System.err.println("Base log file name: " + WORK_DIR_FILE);
    String current = new java.io.File( "." ).getCanonicalPath();
    System.err.println("Current dir:"+current);
    String currentDir = System.getProperty("user.dir");
    System.err.println("Current dir using System:" +currentDir);

    String[] corefArgs = { "-props", "edu/stanford/nlp/coref/hybrid/properties/zh-coref-default.properties",
                           '-' + HybridCorefProperties.LOG_PROP, baseLogFile,
                           '-' + CorefProperties.OUTPUT_PATH_PROP, WORK_DIR_FILE + File.separator,
                           '-' + "coref.doScore", "true",
                           '-' + "coref.scorer", "/scr/nlp/data/conll-2012/scorer/v8.01/scorer.pl",
                           '-' + "coref.data", "/scr/nlp/data/conll-2012/",
                           '-' + "parse.model", "edu/stanford/nlp/models/srparser/chineseSR.ser.gz",
                           '-' + "coref.useConstituencyTree", "true"
    };

    Properties props = StringUtils.argsToProperties(corefArgs);
    System.err.println("Running hcoref with arguments:");
    System.err.println(props);

    HybridCorefSystem.runCoref(corefArgs);

    String actualResults = IOUtils.slurpFile(baseLogFile);
    return actualResults;
  }


  public void testChineseDcoref() throws Exception {
    Counter<String> results = DcorefBenchmarkSlowITest.getCorefResults(runCorefTest(true));

    // So we can see them all at once to speed updating
    printResultsTSV(results, System.err);

    Counter<String> lowResults = new ClassicCounter<>();
    Counter<String> highResults = new ClassicCounter<>();
    Counter<String> expectedResults = new ClassicCounter<>();

    setLowHighExpected(lowResults, highResults, expectedResults, MENTION_TP, 12550, 12700, 12600); // In 2015 was: 12370
    setLowHighExpected(lowResults, highResults, expectedResults, MENTION_F1, 55.7, 56.0, 55.88); // In 2015 was: 55.59

    setLowHighExpected(lowResults, highResults, expectedResults, MUC_TP, 6050, 6100, 6063);  // In 2015 was: 5958
    setLowHighExpected(lowResults, highResults, expectedResults, MUC_F1, 58.30, 58.80, 58.48); // In 2015 was: 57.87

    setLowHighExpected(lowResults, highResults, expectedResults, BCUBED_TP, 6990, 7110.00, 7100.92); // In 2015 was: 6936.32
    setLowHighExpected(lowResults, highResults, expectedResults, BCUBED_F1, 51.60, 52.00, 51.86); // In 2015 was: 51.07

    setLowHighExpected(lowResults, highResults, expectedResults, CEAFM_TP, 8220, 8260, 8242); // In 2015 was: 8074
    setLowHighExpected(lowResults, highResults, expectedResults, CEAFM_F1, 55.50, 56.00, 55.77); // In 2015 was: 55.10

    setLowHighExpected(lowResults, highResults, expectedResults, CEAFE_TP, 2250.00, 2300.00, 2272.52); // In 2015 was: 2205.72
    setLowHighExpected(lowResults, highResults, expectedResults, CEAFE_F1, 51.50, 52.00, 51.52); // In 2015 was: 50.62

    setLowHighExpected(lowResults, highResults, expectedResults, BLANC_F1, 46.75, 47.25, 47.00); // In 2015 was: 46.19

    setLowHighExpected(lowResults, highResults, expectedResults, CONLL_SCORE, 53.75, 54.00, 53.95); // In 2015 was: 53.19

    BenchmarkingHelper.benchmarkResults(results, lowResults, highResults, expectedResults);
  }


  private static void printResultsTSV(Counter<String> results, PrintStream where) {
    where.printf("%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s%n" +
            "%.0f\t%.2f\t%.0f\t%.2f\t%.2f\t%.2f\t%.0f\t%.2f\t%.2f\t%.2f\t%.2f\t%.2f%n",
            MENTION_TP, MENTION_F1, MUC_TP, MUC_F1, BCUBED_TP, BCUBED_F1,
            CEAFM_TP, CEAFM_F1, CEAFE_TP, CEAFE_F1, BLANC_F1, CONLL_SCORE,
            results.getCount(MENTION_TP), results.getCount(MENTION_F1), results.getCount(MUC_TP), results.getCount(MUC_F1),
            results.getCount(BCUBED_TP), results.getCount(BCUBED_F1), results.getCount(CEAFM_TP), results.getCount(CEAFM_F1),
            results.getCount(CEAFE_TP), results.getCount(CEAFE_F1), results.getCount(BLANC_F1), results.getCount(CONLL_SCORE));
  }


  public static void main(String[] args) throws IOException {
    String actualResults = IOUtils.slurpFile(args[0]);
    Counter<String> results = DcorefBenchmarkSlowITest.getCorefResults(actualResults);
    printResultsTSV(results, System.out);
  }

}
