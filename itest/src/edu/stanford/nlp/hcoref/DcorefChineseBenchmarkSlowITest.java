package edu.stanford.nlp.hcoref;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
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
public class DcorefChineseBenchmarkSlowITest extends TestCase {

  public static String runCorefTest(boolean deleteOnExit) throws Exception {
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

    String[] corefArgs = { "-props", "edu/stanford/nlp/hcoref/properties/zh-dcoref-conll-no-output.properties",
            '-' + CorefProperties.LOG_PROP, baseLogFile,
            '-' + CorefProperties.PATH_OUTPUT_PROP, WORK_DIR_FILE.toString()+File.separator };

    Properties props = StringUtils.argsToProperties(corefArgs);
    System.err.println("Running hcoref with arguments:");
    System.err.println(props);

    CorefSystem.runCoref(corefArgs);


    String actualResults = IOUtils.slurpFile(baseLogFile);
    return actualResults;
  }

  private static final String MENTION_TP = "Mention TP";
  private static final String MENTION_F1 = "Mention F1";
  private static final String MUC_TP = "MUC TP";
  private static final String MUC_F1 = "MUC F1";
  private static final String BCUBED_TP = "Bcubed TP";
  private static final String BCUBED_F1 = "Bcubed F1";
  private static final String CEAFM_TP = "CEAFm TP";
  private static final String CEAFM_F1 = "CEAFm F1";
  private static final String CEAFE_TP = "CEAFe TP";
  private static final String CEAFE_F1 = "CEAFe F1";
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

  private static void setAll(Counter<String> lowRes, Counter<String> highRes, Counter<String> expRes, String key, double val){
    lowRes.setCount(key, val);
    highRes.setCount(key, val);
    expRes.setCount(key, val);
  }

  private static void setLowHighExpected(Counter<String> lowRes, Counter<String> highRes, Counter<String> expRes, String key,
                                         double lowVal, double highVal, double expVal) {
    lowRes.setCount(key, lowVal);
    highRes.setCount(key, highVal);
    expRes.setCount(key, expVal);
  }

  public void testChineseDcoref() throws Exception {
    Counter<String> results = getCorefResults(runCorefTest(true));

    // So we can see them all at once to speed updating
    printResultsTSV(results, System.err);

    Counter<String> lowResults = new ClassicCounter<String>();
    Counter<String> highResults = new ClassicCounter<String>();
    Counter<String> expectedResults = new ClassicCounter<String>();

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

  private static Counter<String> getCorefResults(String resultsString) throws IOException {
    Counter<String> results = new ClassicCounter<String>();
    BufferedReader r = new BufferedReader(new StringReader(resultsString));
    for (String line; (line = r.readLine()) != null; ) {
      Matcher m1 = MENTION_PATTERN.matcher(line);
      if (m1.matches()) {
        results.setCount(MENTION_TP, Double.parseDouble(m1.group(1)));
        results.setCount(MENTION_F1, Double.parseDouble(m1.group(2)));
      }
      Matcher m2 = MUC_PATTERN.matcher(line);
      if (m2.matches()) {
        results.setCount(MUC_TP, Double.parseDouble(m2.group(1)));
        results.setCount(MUC_F1, Double.parseDouble(m2.group(2)));
      }
      Matcher m3 = BCUBED_PATTERN.matcher(line);
      if (m3.matches()) {
        results.setCount(BCUBED_TP, Double.parseDouble(m3.group(1)));
        results.setCount(BCUBED_F1, Double.parseDouble(m3.group(2)));
      }
      Matcher m4 = CEAFM_PATTERN.matcher(line);
      if (m4.matches()) {
        results.setCount(CEAFM_TP, Double.parseDouble(m4.group(1)));
        results.setCount(CEAFM_F1, Double.parseDouble(m4.group(2)));
      }
      Matcher m5 = CEAFE_PATTERN.matcher(line);
      if (m5.matches()) {
        results.setCount(CEAFE_TP, Double.parseDouble(m5.group(1)));
        results.setCount(CEAFE_F1, Double.parseDouble(m5.group(2)));
      }
      Matcher m6 = BLANC_PATTERN.matcher(line);
      if (m6.matches()) {
        results.setCount(BLANC_F1, Double.parseDouble(m6.group(1)));
      }
      Matcher m7 = CONLL_PATTERN.matcher(line);
      if (m7.matches()) {
        results.setCount(CONLL_SCORE, Double.parseDouble(m7.group(1)));
      }
    }

    return results;
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
    Counter<String> results = getCorefResults(actualResults);
    printResultsTSV(results, System.out);
  }

}
