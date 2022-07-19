package edu.stanford.nlp.process;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import junit.framework.TestCase;

import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.objectbank.ObjectBank;


/** @author John Bauer */
public class PTBTokenizerITest extends TestCase {

  private static void compareResults(BufferedReader testReader,
                             List<String> goldResults) {
    PTBTokenizer<CoreLabel> tokenizer =
            new PTBTokenizer<>(testReader, new CoreLabelTokenFactory(), "ptb3Escaping=true");
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
      reader = new BufferedReader(new InputStreamReader(PTBTokenizerITest.class.getResourceAsStream(filename), charset));
    } catch (NullPointerException npe) {
      Map<String,String> env = System.getenv();
      String path = "data/edu/stanford/nlp/process" + File.separator + filename;
      String loc = env.get("JAVANLP_HOME");
      if (loc != null) {
        path = loc + File.separator + path;
      }
      reader = new BufferedReader(new InputStreamReader(new FileInputStream(path), charset));
    }
    return reader;
  }

  public void testLargeDataSet() throws IOException {
    BufferedReader goldReader = getReaderFromInJavaNlp("ptblexer.gold");
    List<String> goldResults = new ArrayList<>();
    for (String line; (line = goldReader.readLine()) != null; ) {
      goldResults.add(line.trim());
    }

    BufferedReader testReader = getReaderFromInJavaNlp("ptblexer.test");
    compareResults(testReader, goldResults);

    testReader = getReaderFromInJavaNlp("ptblexer.crlf.test");
    compareResults(testReader, goldResults);
  }

  private static final Pattern hexNumber = Pattern.compile("(\\s*[0-9A-Fa-f]+)\\s(.*)");

  public void testEmoji() throws IOException {
    int lineNumber = 0;
    for (String str : ObjectBank.getLineIterator(getReaderFromInJavaNlp("emoji-test.txt"))) {
      lineNumber++;
      String origString = str;
      if (str.trim().isEmpty() || str.startsWith("#")) {
        continue;
      }
      int[] codepoints = new int[20];
      int cpUpto = 0;
      while (true) {
        Matcher m = hexNumber.matcher(str);
        if (m.matches()) {
          String numStr = m.group(1);
          int cp = Integer.parseUnsignedInt(numStr, 16);
          codepoints[cpUpto] = cp;
          cpUpto++;
          str = m.group(2);
        } else {
          break;
        }
      }
      if (cpUpto == 0) {
        fail("No juice");
      }
      String emoji = "A " + new String(codepoints, 0, cpUpto) + " Z";
      Tokenizer<CoreLabel> tokenizer = PTBTokenizer.PTBTokenizerFactory.newCoreLabelTokenizerFactory("untokenizable=allKeep")
              .getTokenizer(new StringReader(emoji));
      CoreLabel cl;
      cl = tokenizer.next();
      assertEquals("Bad for " + origString, "A", cl.word());
      // cl =
      tokenizer.next();
      // System.out.println("Emoji: " + cl.word());
      cl = tokenizer.next();
      assertEquals("Line " + lineNumber + " didn't parse right; got " + cl.word() + " for " + origString, "Z", cl.word());
    }
  }

}
