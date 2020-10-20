package edu.stanford.nlp.util;

/**
 * Simple utility class: reads the environment variable in
 * ENV_VARIABLE and provides a method that converts strings which
 * start with that environment variable to file paths.  For example,
 * you can send it
 * "$NLP_DATA_HOME/data/pos-tagger/wsj3t0-18-left3words"
 * and it will convert that to
 * "/u/nlp/data/pos-tagger/wsj3t0-18-left3words"
 * unless you have set $NLP_DATA_HOME to something else.
 *
 * The only environment variable expanded is that defined by
 * ENV_VARIABLE, and the only place in the string it is expanded is at
 * the start of the string.
 *
 * @author John Bauer
 */
public class DataFilePaths {

  private DataFilePaths() {}

  private static final String NLP_DATA_VARIABLE = "NLP_DATA_HOME";
  private static final String NLP_DATA_VARIABLE_PREFIX = '$' + NLP_DATA_VARIABLE;

  private static final String NLP_DATA_HOME =
    ((System.getenv(NLP_DATA_VARIABLE) != null) ?
     System.getenv(NLP_DATA_VARIABLE) : "/u/nlp");

  private static final String JAVANLP_VARIABLE = "JAVANLP_HOME";
  private static final String JAVANLP_VARIABLE_PREFIX = '$' + JAVANLP_VARIABLE;

  private static final String JAVANLP_HOME =
    ((System.getenv(JAVANLP_VARIABLE) != null) ?
     System.getenv(JAVANLP_VARIABLE) : ".");

  public static String convert(String path) {
    if (path.startsWith(NLP_DATA_VARIABLE_PREFIX))
      return NLP_DATA_HOME + path.substring(NLP_DATA_VARIABLE_PREFIX.length());
    if (path.startsWith(JAVANLP_VARIABLE_PREFIX))
      return JAVANLP_HOME + path.substring(JAVANLP_VARIABLE_PREFIX.length());
    return path;
  }

}