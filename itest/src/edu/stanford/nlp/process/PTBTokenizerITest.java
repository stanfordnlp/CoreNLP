package edu.stanford.nlp.process;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import edu.stanford.nlp.ling.CoreLabel;
import junit.framework.TestCase;


/** @author John Bauer */
public class PTBTokenizerITest extends TestCase {

  private static void compareResults(BufferedReader testReader,
                             List<String> goldResults) {
    PTBTokenizer<CoreLabel> tokenizer =
            new PTBTokenizer<>(testReader, new CoreLabelTokenFactory(), "");
    List<String> testResults = new ArrayList<>();
    while (tokenizer.hasNext()) {
      CoreLabel w = tokenizer.next();
      testResults.add(w.word());
    }

    // Compare tokens before checking size so get better output if unequal
    int compareSize = Math.min(goldResults.size(), testResults.size());
    for (int i = 0; i < compareSize; ++i) {
      assertEquals(goldResults.get(i), testResults.get(i));
    }
    assertEquals(goldResults.size(), testResults.size());
  }

  private static BufferedReader getReaderFromInJavaNlp(String filename)
      throws IOException {
    final String charset = "utf-8";
    BufferedReader reader;
    try {
      reader = new BufferedReader
      (new InputStreamReader
       (PTBTokenizerITest.class.getResourceAsStream(filename), charset));
    } catch (NullPointerException npe) {
      Map<String,String> env = System.getenv();
      String path = "projects/core/data/edu/stanford/nlp/process" + File.separator + filename;
      String loc = env.get("JAVANLP_HOME");
      if (loc != null) {
        path = loc + File.separator + path;
      }
      reader = new BufferedReader(new InputStreamReader(new FileInputStream(path), charset));
    }
    return reader;
  }

  public void testLargeDataSet()
    throws IOException
  {
    BufferedReader goldReader = getReaderFromInJavaNlp("ptblexer.gold");
    List<String> goldResults = new ArrayList<String>();
    for (String line; (line = goldReader.readLine()) != null; ) {
      goldResults.add(line.trim());
    }

    BufferedReader testReader = getReaderFromInJavaNlp("ptblexer.test");
    compareResults(testReader, goldResults);

    testReader = getReaderFromInJavaNlp("ptblexer.crlf.test");
    compareResults(testReader, goldResults);
  }

}
