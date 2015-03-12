package edu.stanford.nlp.patterns.surface;

import edu.stanford.nlp.util.Execution;

import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.regex.*;

/**
 * Created by sonalg on 10/27/14.
 */
public class PatternFactory {
  //TODO: set this class
  /**
   * allow to match stop words before a target term. This is to match something
   * like "I am on some X" if the pattern is "I am on X"
   */
  @Execution.Option(name = "useStopWordsBeforeTerm")
  public static boolean useStopWordsBeforeTerm = false;

  /**
   * Add NER restriction to the target phrase in the patterns
   */
  @Execution.Option(name = "useTargetNERRestriction")
  public static boolean useTargetNERRestriction = false;

  @Execution.Option(name = "numWordsCompound")
  public static int numWordsCompound = 2;

  /**
   * Use lemma instead of words for the context tokens
   */
  @Execution.Option(name = "useLemmaContextTokens")
  public static boolean useLemmaContextTokens = true;



  public static List<String> fillerWords = Arrays.asList("a", "an", "the", "`", "``",
    "'", "''");


  /**
   * by default doesn't ignore anything. What phrases to ignore.
   */
  public static java.util.regex.Pattern ignoreWordRegex = java.util.regex.Pattern.compile("a^");

  public static void setUp(Properties props) {
    Execution.fillOptions(PatternFactory.class, props);
    SurfacePatternFactory.setUp(props);
  }

  public enum PatternType{SURFACE};

  public static boolean doNotUse(String word, Set<String> stopWords) {
    if (stopWords.contains(word.toLowerCase())
      || ignoreWordRegex.matcher(word).matches())
      return true;
    else
      return false;


  }
}
