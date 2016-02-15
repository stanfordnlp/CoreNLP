package edu.stanford.nlp.parser.lexparser;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;

import edu.stanford.nlp.trees.Tree;

public class EvalbFormatWriter {
  public final static String DEFAULT_GOLD_FILENAME = "parses.gld";
  public final static String DEFAULT_TEST_FILENAME = "parses.tst";
  private PrintWriter goldWriter;
  private PrintWriter testWriter;
  private int count = 0;
  private final static EvalbFormatWriter DEFAULT_WRITER = new EvalbFormatWriter();

  public void initFiles(TreebankLangParserParams tlpParams, String goldFilename, String testFilename) {
    try {
      goldWriter = tlpParams.pw(new FileOutputStream(goldFilename));
      testWriter = tlpParams.pw(new FileOutputStream(testFilename));
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
    count = 0;
  }

  public void initFiles(TreebankLangParserParams tlpParams, String testFilename) {
    try {
      goldWriter = null;
      testWriter = tlpParams.pw(new FileOutputStream(testFilename));
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
    count = 0;
  }

  public void writeTree(Tree test)
  {
    testWriter.println((test == null) ? "(())" : test.toString());
    count++;
//    System.err.println("Wrote EVALB lines.");
  }

  public void writeTrees(Tree gold, Tree test)
  {
    goldWriter.println((gold == null) ? "(())" : gold.toString());
    testWriter.println((test == null) ? "(())" : test.toString());
    count++;
//    System.err.println("Wrote EVALB lines.");
  }

  public void closeFiles() {
    if (goldWriter != null) goldWriter.close();
    if (testWriter != null) testWriter.close();
    System.err.println("Wrote " + count + " EVALB lines.");
  }

  public static void initEVALBfiles(TreebankLangParserParams tlpParams) {
    DEFAULT_WRITER.initFiles(tlpParams, DEFAULT_GOLD_FILENAME, DEFAULT_TEST_FILENAME);
  }

  public static void closeEVALBfiles() {
    DEFAULT_WRITER.closeFiles();
  }

  public static void writeEVALBline(Tree gold, Tree test) {
    DEFAULT_WRITER.writeTrees(gold, test);    
  }
}
