package edu.stanford.nlp.dcoref;

import java.io.BufferedReader;
import java.io.File;
import java.io.StringReader;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import junit.framework.TestCase;

import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.io.RuntimeIOException;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.util.StringUtils;

/**
 * Run the dcoref system using the exact properties we distribute as
 * an example.  Check that the output does not change markedly from expected.
 * If performance numbers change, we'll notice and be able to verify
 * that it's intended.
 *
 * @author John Bauer
 * @author Christopher Manning
 */
public class DcorefBenchmarkSlowITest extends TestCase {

  public static String runCorefTest(boolean deleteOnExit) throws Exception {
    final File WORK_DIR_FILE = File.createTempFile("DcorefBenchmarkTest", "");
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

    String[] corefArgs = { "-props", "edu/stanford/nlp/dcoref/coref.properties",
            '-' + Constants.LOG_PROP, baseLogFile,
            '-' + Constants.CONLL_OUTPUT_PROP, WORK_DIR_FILE.toString() };

    Properties props = StringUtils.argsToProperties(corefArgs);
    System.err.println("Running dcoref with properties:");
    System.err.println(props);

    String logFile = SieveCoreferenceSystem.initializeAndRunCoref(props);
    System.err.println(logFile);

    String actualResults = IOUtils.slurpFile(logFile);
    return actualResults;
  }

  private static final String MENTION_TP = "Mention TP";
  private static final String MENTION_F1 = "Mention F1";
  private static final String MUC_TP = "Mention TP";
  private static final String MUC_F1 = "Mention F1";
  private static final String BCUBED_TP = "Mention TP";
  private static final String BCUBED_F1 = "Mention F1";
  private static final String CEAFM_TP = "Mention TP";
  private static final String CEAFM_F1 = "Mention F1";
  private static final String CEAFE_TP = "Mention TP";
  private static final String CEAFE_F1 = "Mention F1";
  private static final String BLANC_F1 = "BLANC F1";
  private static final String CONLL_SCORE = "CoNLL score";

  private static final Pattern MENTION_PATTERN =
          Pattern.compile("Identification of Mentions: Recall: \\(((?:\\d|\\.)+).*F1: ((?:\\d|\\.)+)%.*");
  private static final Pattern MUC_PATTERN =
          Pattern.compile("METRIC muc:Coreference: Recall: \\(((?:\\d|\\.)+).*F1: ((?:\\d|\\.)+)%.*");
  private static final Pattern BCUBED_PATTERN =
          Pattern.compile("METRIC bcub:Coreference: Recall: \\(((?:\\d|\\.)+).*F1: ((?:\\d|\\.)+)%.*");
  private static final Pattern CEAFM_PATTERN =
          Pattern.compile("METRIC ceafm:Coreference: Recall: \\(((?:\\d|\\.)+).*F1: ((?:\\d|\\.)+)%.*");
  private static final Pattern CEAFE_PATTERN =
          Pattern.compile("METRIC ceafe:Coreference: Recall: \\(((?:\\d|\\.)+).*F1: ((?:\\d|\\.)+)%.*");
  private static final Pattern BLANC_PATTERN =
          Pattern.compile("BLANC: .*F1: ((?:\\d|\\.)+)%.*");
  private static final Pattern CONLL_PATTERN =
          Pattern.compile("Final conll score .* = ((?:\\d|\\.)+).*");

  public void testDcoref() throws Exception {
    Counter<String> lowResults = new ClassicCounter<String>();
    Counter<String> highResults = new ClassicCounter<String>();
    Counter<String> expectedResults = new ClassicCounter<String>();

    lowResults.setCount(MENTION_TP, 12400);
    expectedResults.setCount(MENTION_TP, 12405);
    highResults.setCount(MENTION_TP, 12410);
    lowResults.setCount(MENTION_F1, 50.4);
    expectedResults.setCount(MENTION_F1, 50.42);
    highResults.setCount(MENTION_F1, 50.45);

    lowResults.setCount(MUC_TP, 6250);
    expectedResults.setCount(MUC_TP, 6253);
    highResults.setCount(MUC_TP, 6260);
    lowResults.setCount(MUC_F1, 60.65);
    expectedResults.setCount(MUC_F1, 60.67);
    highResults.setCount(MUC_F1, 60.7);

    lowResults.setCount(BCUBED_TP, 12450);
    expectedResults.setCount(BCUBED_TP, 12457.63);
    highResults.setCount(BCUBED_TP, 12460);
    lowResults.setCount(BCUBED_F1, 70.8);
    expectedResults.setCount(BCUBED_F1, 70.81);
    highResults.setCount(BCUBED_F1, 70.85);

    lowResults.setCount(CEAFM_TP, 10920);
    expectedResults.setCount(CEAFM_TP, 10927);
    highResults.setCount(CEAFM_TP, 10930);
    lowResults.setCount(CEAFM_F1, 59.4);
    expectedResults.setCount(CEAFM_F1, 59.44);
    highResults.setCount(CEAFM_F1, 59.5);

    lowResults.setCount(CEAFE_TP, 3830);
    expectedResults.setCount(CEAFE_TP, 3833.81);
    highResults.setCount(CEAFE_TP, 3840);
    lowResults.setCount(CEAFE_F1, 47.4);
    expectedResults.setCount(CEAFE_F1, 47.46);
    highResults.setCount(CEAFE_F1, 47.5);

    lowResults.setCount(BLANC_F1, 75.35);
    expectedResults.setCount(BLANC_F1, 75.39);
    highResults.setCount(BLANC_F1, 75.42);

    lowResults.setCount(CONLL_SCORE, 59.6);
    expectedResults.setCount(CONLL_SCORE, 59.65);
    highResults.setCount(CONLL_SCORE, 59.7);

    Counter<String> results = new ClassicCounter<String>();
    BufferedReader r = new BufferedReader(new StringReader(runCorefTest(true)));
    for (String line; (line = r.readLine()) != null; ) {
      Matcher m1 = MENTION_PATTERN.matcher(line);
      if (m1.matches() ){
        results.setCount(MENTION_TP, Double.parseDouble(m1.group(1)));
        results.setCount(MENTION_F1, Double.parseDouble(m1.group(2)));
      }
      Matcher m2 = MUC_PATTERN.matcher(line);
      if (m2.matches() ){
        results.setCount(MUC_TP, Double.parseDouble(m2.group(1)));
        results.setCount(MUC_F1, Double.parseDouble(m2.group(2)));
      }
      Matcher m3 = BCUBED_PATTERN.matcher(line);
      if (m3.matches() ){
        results.setCount(BCUBED_TP, Double.parseDouble(m3.group(1)));
        results.setCount(BCUBED_F1, Double.parseDouble(m3.group(2)));
      }
      Matcher m4 = CEAFM_PATTERN.matcher(line);
      if (m4.matches() ){
        results.setCount(CEAFM_TP, Double.parseDouble(m4.group(1)));
        results.setCount(CEAFM_F1, Double.parseDouble(m4.group(2)));
      }
      Matcher m5 = CEAFE_PATTERN.matcher(line);
      if (m5.matches() ){
        results.setCount(CEAFE_TP, Double.parseDouble(m5.group(1)));
        results.setCount(CEAFE_F1, Double.parseDouble(m5.group(2)));
      }
      Matcher m6 = BLANC_PATTERN.matcher(line);
      if (m6.matches() ){
        results.setCount(BLANC_F1, Double.parseDouble(m6.group(1)));
      }
      Matcher m7 = CONLL_PATTERN.matcher(line);
      if (m7.matches() ){
        results.setCount(CONLL_SCORE, Double.parseDouble(m7.group(1)));
      }
    }

    for (String key : results.keySet()) {
      double val = results.getCount(key);
      double high = highResults.getCount(key);
      double low = lowResults.getCount(key);
      double expected = expectedResults.getCount(key);
      assertTrue("Value for " + key + " = " + val + " is lower than expected minimum " + low, val >= low);
      assertTrue("Value for " + key + " = " + val + " is higher than expected maximum " + high +
          " [not a bug, but a breakthrough!]", val <= high);
      if (val < (expected - 1e-4)) {
        System.err.println("Value for " + key + " = " + val + " is fractionally lower than expected " + expected);
      } else if (val > (expected + 1e-4)) {
          System.err.println("Value for " + key + " = " + val + " is fractionally higher than expected " + expected);
      } else {
        System.err.println("Value for " + key + " = " + val + " is as expected");
      }
    }
  }

  public static void main(String[] args) throws Exception {
    runCorefTest(false);
  }

}
