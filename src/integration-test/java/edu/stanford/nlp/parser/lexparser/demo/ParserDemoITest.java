package edu.stanford.nlp.parser.lexparser.demo;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

import org.junit.Before;
import org.junit.Test;

import edu.stanford.nlp.parser.lexparser.LexicalizedParser;

public class ParserDemoITest {

  private static LexicalizedParser parser; // = null;

  @Before
  public void setUp() throws Exception {
    synchronized(ParserDemoITest.class) {
      if (parser == null) {
        parser = LexicalizedParser.loadModel();
      }
    }
  }

  @Test
  public void testAPI() {
    ParserDemo.demoAPI(parser);
  }

  @Test
  public void testDP() throws IOException {
    File temp = File.createTempFile("ParserDemoITest", "txt");
    BufferedWriter out = new BufferedWriter(new FileWriter(temp));
    out.write("This is a small test file.\n");
    out.close();

    ParserDemo.demoDP(parser, temp.getPath());
  }

}
