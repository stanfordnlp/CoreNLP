package edu.stanford.nlp.io;

import java.io.IOException;

import junit.framework.TestCase;

/**
 * @author Christopher Manning
 */
public class IOUtilsITest extends TestCase {

  /**
   * Tests that slurpFile can get files from within the classpath
   */
  public void testSlurpFile() {
    String contents;
    try {
      contents = IOUtils.slurpFile("edu/stanford/nlp/io/test.txt", "utf-8");
    } catch (IOException e) {
      throw new RuntimeIOException(e);
    }

    assertEquals("This is a test sentence.", contents.trim());

    try {
      contents = IOUtils.slurpFile("edu/stanford/nlp/io/test.txt");
    } catch (IOException e) {
      throw new RuntimeIOException(e);
    }

    assertEquals("This is a test sentence.", contents.trim());

    try {
      contents = IOUtils.slurpFile("edu/stanford/nlp/io/test.txtzzz");
      throw new AssertionError("Should not have found unknown file");
    } catch (IOException e) {
      // yay
    }

    contents = IOUtils.slurpFileNoExceptions("edu/stanford/nlp/io/test.txt");
    assertEquals("This is a test sentence.", contents.trim());


    try {
      contents = IOUtils.slurpFileNoExceptions("edu/stanford/nlp/io/test.txtzzz");
      throw new AssertionError("Should not have found unknown file");
    } catch (RuntimeIOException e) {
      // yay
    }
  }

}