package edu.stanford.nlp.pipeline;

import edu.stanford.nlp.io.*;
import edu.stanford.nlp.util.*;

import junit.framework.TestCase;

import java.io.*;
import java.util.*;

public class StanfordCoreNLPSpeedSlowITest extends TestCase {

  public StanfordCoreNLP pipeline;
  public String WORKING_DIR = "/scr/nlp/data/stanford-corenlp-testing/speed-test";

  @Override
  public void setUp() {
    Properties props =
        StringUtils.argsToProperties(
            new String[]{"-props", WORKING_DIR+"/test.props"});
    pipeline = new StanfordCoreNLP(props);
  }

  public void testStanfordCoreNLPSpeed() {
    List<String> kbp2016FileList = IOUtils.linesFromFile(WORKING_DIR + "/kbp-2016-files.txt");
    long startTime = System.currentTimeMillis();
    for (String filePath : kbp2016FileList) {
      System.err.println("Processing file: "+filePath);
      CoreDocument currentDoc = new CoreDocument(IOUtils.stringFromFile(filePath));
      pipeline.annotate(currentDoc);
    }
    long endTime = System.currentTimeMillis();
    long duration = endTime - startTime;
    System.err.println("run time for single thread test: "+duration);
    assertTrue(duration < 390000);
  }

  public void testStanfordCoreNLPSpeedMultiThread() throws IOException {
    List<File> files = new ArrayList<File>();
    List<String> kbpFilePaths = IOUtils.linesFromFile(WORKING_DIR+"/kbp-2016-files.txt");
    for (String filePath : kbpFilePaths) {
      files.add(new File(filePath));
    }
    long startTime = System.currentTimeMillis();
    pipeline.processFiles(files, 4, false, Optional.empty());
    long endTime = System.currentTimeMillis();
    long duration = endTime - startTime;
    System.err.println("run time for multi-thread test: "+duration);
    assertTrue(duration < 390000);
  }

}
