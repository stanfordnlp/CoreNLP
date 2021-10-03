package edu.stanford.nlp.util;

/**
 * Class for storing info on test paths
 */

public class TestPaths {

  private static String testHome;

  static {
    testHome = System.getenv().get("CORENLP_TEST_HOME");
  }

  public static String testHome() {
    return testHome;
  }

}
