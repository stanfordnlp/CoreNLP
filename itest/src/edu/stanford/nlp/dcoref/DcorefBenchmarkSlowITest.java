package edu.stanford.nlp.dcoref;

import static edu.stanford.nlp.util.BenchmarkingHelper.setLowHighExpected;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.util.List;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.Test;

import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.io.RuntimeIOException;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.util.BenchmarkingHelper;
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
public class DcorefBenchmarkSlowITest {

  private static Counter<String> runCorefTest(boolean deleteOnExit) throws Exception {
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
    System.err.println("Running dcoref with properties:\n" + props);

    String logFile = SieveCoreferenceSystem.initializeAndRunCoref(props);
    System.err.println("LOG FILE: " + logFile);

    String actualResults = IOUtils.slurpFile(logFile);
    return getCorefResults(actualResults);
  }


  public static final String MENTION_TP = "Mention TP";
  public static final String MENTION_F1 = "Mention F1";
  public static final String MUC_TP = "MUC TP";
  public static final String MUC_F1 = "MUC F1";
  public static final String BCUBED_TP = "Bcubed TP";
  public static final String BCUBED_F1 = "Bcubed F1";
  public static final String CEAFM_TP = "CEAFm TP";
  public static final String CEAFM_F1 = "CEAFm F1";
  public static final String CEAFE_TP = "CEAFe TP";
  public static final String CEAFE_F1 = "CEAFe F1";
  public static final String BLANC_F1 = "BLANC F1";
  public static final String CONLL_SCORE = "CoNLL score";

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

  public static Counter<String> getCorefResults(String resultsString) throws IOException {
    Counter<String> results = new ClassicCounter<>();
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

    if (results.keySet().isEmpty()) {
      List<String> lines = StringUtils.split(resultsString, "\\R");
      int start = Math.max(0, lines.size() - 20);
      lines = lines.subList(start, lines.size());
      String tail = StringUtils.join(lines, "\n");
      throw new RuntimeException("Coref output did not have any results in it!  The end of the log is as follows:\n" + tail);
    }

    return results;
  }


  @Test
  public void testDcoref() throws Exception {
    Counter<String> results = runCorefTest(true);

    Counter<String> lowResults = new ClassicCounter<>();
    Counter<String> highResults = new ClassicCounter<>();
    Counter<String> expectedResults = new ClassicCounter<>();

    setLowHighExpected(lowResults, highResults, expectedResults, MENTION_TP, 12400, 12410, 12405);
    setLowHighExpected(lowResults, highResults, expectedResults, MENTION_F1, 50.4, 50.45, 50.42);

    setLowHighExpected(lowResults, highResults, expectedResults, MUC_TP, 6245, 6255, 6250);
    setLowHighExpected(lowResults, highResults, expectedResults, MUC_F1, 60.65, 60.7, 60.66);

    setLowHighExpected(lowResults, highResults, expectedResults, BCUBED_TP, 12440, 12452.25, 12452.25);
    setLowHighExpected(lowResults, highResults, expectedResults, BCUBED_F1, 70.75, 70.85, 70.80);

    setLowHighExpected(lowResults, highResults, expectedResults, CEAFM_TP, 10915, 10930, 10920);
    setLowHighExpected(lowResults, highResults, expectedResults, CEAFM_F1, 59.4, 59.5, 59.42);

    setLowHighExpected(lowResults, highResults, expectedResults, CEAFE_TP, 3830, 3840, 3831.36);
    setLowHighExpected(lowResults, highResults, expectedResults, CEAFE_F1, 47.4, 47.5, 47.45);

    setLowHighExpected(lowResults, highResults, expectedResults, BLANC_F1, 75.35, 75.44, 75.38);

    setLowHighExpected(lowResults, highResults, expectedResults, CONLL_SCORE, 59.6, 59.7, 59.64);

    BenchmarkingHelper.benchmarkResults(results, lowResults, highResults, expectedResults);
  }


  public static void main(String[] args) throws Exception {
    runCorefTest(false);
  }

}
