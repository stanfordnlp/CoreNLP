package edu.stanford.nlp.tagger.maxent;

import junit.framework.TestCase;

import java.io.*;

import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.sequences.PlainTextDocumentReaderAndWriter.OutputStyle;
import edu.stanford.nlp.util.StringUtils;

/**
 * Test that you get the same output, free of crashes or other
 * nonsense, when running the MaxentTagger in single core and
 * multicore modes.  Doesn't check the output for correctness.
 *
 * @author John Bauer
 */
public class MulticoreMaxentTaggerITest extends TestCase {

  private static MaxentTagger singleTagger = null;
  private static MaxentTagger multiTagger = null;

  private static final String taggedText = "data/edu/stanford/nlp/tagger/sample_tagged.txt";
  private static final String plainText = "data/edu/stanford/nlp/tagger/sample_plain.txt";
  private static final String xmlText = "data/edu/stanford/nlp/tagger/sample_xml.xml";

  @Override
  public void setUp() throws Exception {
    synchronized(MulticoreMaxentTaggerITest.class) {
      if (singleTagger == null) {
        singleTagger = new MaxentTagger(MaxentTagger.DEFAULT_JAR_PATH);
      }
      if (multiTagger == null) {
        multiTagger = new MaxentTagger(MaxentTagger.DEFAULT_JAR_PATH, StringUtils.argsToProperties(new String[] {"-model", MaxentTagger.DEFAULT_JAR_PATH, "-nthreads", "4"}));
      }
    }
  }

  public void testXML() throws IOException {
    BufferedInputStream is = new BufferedInputStream(new FileInputStream(xmlText));
    StringWriter sout = new StringWriter();
    singleTagger.tagFromXML(is, sout, "p");

    String singleOutput = sout.toString();

    is = new BufferedInputStream(new FileInputStream(xmlText));
    sout = new StringWriter();
    multiTagger.tagFromXML(is, sout, "p");

    String multiOutput = sout.toString();

    assertEquals(singleOutput, multiOutput);
  }

  public void testPlainText() throws IOException {
    BufferedReader bin = IOUtils.readerFromString(plainText, "utf-8");
    StringWriter sout = new StringWriter();
    BufferedWriter bout = new BufferedWriter(sout);
    singleTagger.runTagger(bin, bout, "", OutputStyle.SLASH_TAGS);
    bout.flush();

    String singleOutput = sout.toString();

    bin = IOUtils.readerFromString(plainText, "utf-8");
    sout = new StringWriter();
    bout = new BufferedWriter(sout);
    multiTagger.runTagger(bin, bout, "", OutputStyle.SLASH_TAGS);
    bout.flush();

    String multiOutput = sout.toString();

    assertEquals(singleOutput, multiOutput);
  }

  public void testTagged() throws IOException {
    PrintStream oldOut = System.out;
    PrintStream oldErr = System.err;

    ByteArrayOutputStream outStream = new ByteArrayOutputStream();
    ByteArrayOutputStream errStream = new ByteArrayOutputStream();
    PrintStream outPrint = new PrintStream(outStream);
    PrintStream errPrint = new PrintStream(errStream);
    System.setOut(outPrint);
    System.setErr(errPrint);

    TestClassifier tc = new TestClassifier(singleTagger, taggedText);

    outPrint.flush();
    errPrint.flush();

    String singleOutput = outStream.toString();

    outStream = new ByteArrayOutputStream();
    errStream = new ByteArrayOutputStream();
    outPrint = new PrintStream(outStream);
    errPrint = new PrintStream(errStream);
    System.setOut(outPrint);
    System.setErr(errPrint);

    tc = new TestClassifier(multiTagger, taggedText);

    outPrint.flush();
    errPrint.flush();

    String multiOutput = outStream.toString();

    assertEquals(singleOutput, multiOutput);

    System.setOut(oldOut);
    System.setErr(oldErr);
  }

}
