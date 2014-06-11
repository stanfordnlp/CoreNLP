package edu.stanford.nlp.dcoref;

import java.io.File;
import java.util.Properties;

import junit.framework.TestCase;

import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.util.StringUtils;

/**
 * Run the dcoref system using the exact properties we distribute as
 * an example.  Check that the output does not change from expected.
 * If performance numbers change, we'll notice and be able to verify
 * that it's intended.
 *
 * @author John Bauer
 */
public class DcorefBenchmarkSlowITest extends TestCase {
  public void testDcoref() throws Exception {
    final File WORK_DIR_FILE = File.createTempFile("DcorefBenchmarkTest", "");
    WORK_DIR_FILE.delete();
    WORK_DIR_FILE.mkdir();
    WORK_DIR_FILE.deleteOnExit();

    String baseLogFile = WORK_DIR_FILE + File.separator + "log";

    System.err.println("Base log file name: " + WORK_DIR_FILE);
    String current = new java.io.File( "." ).getCanonicalPath();
    System.err.println("Current dir:"+current);
    String currentDir = System.getProperty("user.dir");
    System.err.println("Current dir using System:" +currentDir);

    String expectedResults = IOUtils.slurpFile("edu/stanford/nlp/dcoref/expected.txt");

    String[] corefArgs = { "-props", "edu/stanford/nlp/dcoref/coref.properties",
                           "-" + Constants.LOG_PROP, baseLogFile,
                           "-" + Constants.CONLL_OUTPUT_PROP, WORK_DIR_FILE.toString() };

    Properties props = StringUtils.argsToProperties(corefArgs);
    System.err.println("Running dcoref with properties:");
    System.err.println(props);

    String logFile = SieveCoreferenceSystem.initializeAndRunCoref(props);
    System.err.println(logFile);

    String actualResults = IOUtils.slurpFile(logFile);

    String[] expectedLines = expectedResults.trim().split("[\n\r]+");
    String[] actualLines = actualResults.trim().split("[\n\r]+");

    int line;
    String lastLine = expectedLines[expectedLines.length - 1];
    for (line = actualLines.length - 1; line >= 0; --line) {
      if (actualLines[line].equals(lastLine)) {
        break;
      }
    }
    assertTrue(line >= 0);
    for (int i = 0; i < expectedLines.length; ++i) {
      String expectedLine = expectedLines[expectedLines.length - 1 - i].trim().replaceAll("\\s+", " ");
      String actualLine = actualLines[line - i].trim().replaceAll("\\s+", " ");
      assertEquals(expectedLine, actualLine);
    }
    System.err.println(line);
  }
}
