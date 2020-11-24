package edu.stanford.nlp.classify;

import static org.junit.Assert.assertEquals;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

import edu.stanford.nlp.io.IOUtils;

/**
 * This is a really rough ColumnDataClassifierITest.
 *
 * It works by calling the main method of ColumnDataClassifier
 * directly with various args, capturing the stdout, and comparing
 * it to the expected stdout for those commands.
 *
 * This expects the output to be exactly the same, which means that if
 * numbers change (which can happen for various reasons) the test will
 * fail and no one will look at this comment to figure out why.
 *
 * If the only problem is that the numbers are slightly different, you
 * can always just check the new output of the CDC commands and update
 * the gold data files if the changed numbers still look reasonable.
 * It is easy enough to figure out what the command line to run
 * is... just use the args in the various test cases as the command
 * line flags.
 *
 * See more/.../ColumnDataClassifierITest2.java for more testing fun.
 *
 * @author John Bauer
 */
public class ColumnDataClassifierITest {
  public static void runAndTestCDC(String goldFileName,
                                   String ... args)
    throws IOException
  {
    PrintStream oldOut = System.out;
    PrintStream oldErr = System.err;

    ByteArrayOutputStream outStream = new ByteArrayOutputStream();
    ByteArrayOutputStream errStream = new ByteArrayOutputStream();
    PrintStream outPrint = new PrintStream(outStream);
    PrintStream errPrint = new PrintStream(errStream);
    System.setOut(outPrint);
    System.setErr(errPrint);

    ColumnDataClassifier.main(args);

    System.setOut(oldOut);
    System.setErr(oldErr);

    BufferedReader goldFile = IOUtils.readerFromString(goldFileName);
    List<String> lines = new ArrayList<>();

    for (String line; (line = goldFile.readLine()) != null; ) {
      lines.add(line.trim().replaceAll("\\s+", " "));
    }

    String[] result = outStream.toString().trim().split("\n");
    assertEquals(lines.size(), result.length);
    for (int i = 0; i < result.length; ++i) {
      String goldLine = lines.get(i);
      String resultLine = result[i].trim().replaceAll("\\s+", " ");
      assertEquals(goldLine, resultLine);
    }
  }

  @Test
  public void testNoArgClassify()
    throws IOException {
    runAndTestCDC("edu/stanford/nlp/classify/iris.gold",
                  "-prop",
                  "edu/stanford/nlp/classify/iris.prop");
  }

}
