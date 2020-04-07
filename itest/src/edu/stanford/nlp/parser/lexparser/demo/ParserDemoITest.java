package edu.stanford.nlp.parser.lexparser.demo;

import junit.framework.TestCase;

import edu.stanford.nlp.parser.lexparser.LexicalizedParser;
import java.io.*;

public class ParserDemoITest extends TestCase {

  private static LexicalizedParser parser; // = null;

  @Override
  public void setUp() throws Exception {
    synchronized(ParserDemoITest.class) {
      if (parser == null) {
        parser = LexicalizedParser.loadModel();
      }
    }
  }

  public void testAPI() {
    ParserDemo.demoAPI(parser);
  }

  public void testDP() throws IOException {
    File temp = File.createTempFile("ParserDemoITest", "txt");
    BufferedWriter out = new BufferedWriter(new FileWriter(temp));
    out.write("This is a small test file.\n");
    out.close();

    ParserDemo.demoDP(parser, temp.getPath());
  }

}
