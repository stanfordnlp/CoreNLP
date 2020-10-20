package edu.stanford.nlp.patterns;

import edu.stanford.nlp.patterns.dep.DepPatternFactory;
import edu.stanford.nlp.patterns.surface.SurfacePatternFactory;
import edu.stanford.nlp.util.ArgumentParser;

import java.util.*;

/**
 * Created by sonalg on 10/27/14.
 */
public class PatternFactory {

  /**
   * allow to match stop words before a target term. This is to match something
   * like "I am on some X" if the pattern is "I am on X"
   */
  @ArgumentParser.Option(name = "useStopWordsBeforeTerm")
  public static boolean useStopWordsBeforeTerm = false;

  /**
   * Add NER restriction to the target phrase in the patterns
   */
  @ArgumentParser.Option(name = "useTargetNERRestriction")
  public static boolean useTargetNERRestriction = false;

  /**
   *
   */
  @ArgumentParser.Option(name="useNER")
  public static boolean useNER = true;

  /**
   * Can just write a number (if same for all labels) or "Label1,2;Label2,3;...."
   */
  @ArgumentParser.Option(name = "numWordsCompound")
  public static String numWordsCompound = "2";

  public static Map<String, Integer> numWordsCompoundMapped = new HashMap<>();

  public static int numWordsCompoundMax = 2;

  /**
   * Use lemma instead of words for the context tokens
   */
  @ArgumentParser.Option(name = "useLemmaContextTokens")
  public static boolean useLemmaContextTokens = true;



  public static List<String> fillerWords = Arrays.asList("a", "an", "the", "`", "``",
    "'", "''");


  /**
   * by default doesn't ignore anything. What phrases to ignore.
   */
  public static java.util.regex.Pattern ignoreWordRegex = java.util.regex.Pattern.compile("a^");

  public static void setUp(Properties props, PatternType patternType, Set<String> labels) {
    ArgumentParser.fillOptions(PatternFactory.class, props);
    numWordsCompoundMax = 0;
    if (numWordsCompound.contains(",") || numWordsCompound.contains(";")) {
      String[] toks = numWordsCompound.split(";");
      for(String t: toks) {
        String[] toks2 = t.split(",");
        int numWords = Integer.parseInt(toks2[1]);
        numWordsCompoundMapped.put(toks2[0], numWords);
        if(numWords > numWordsCompoundMax){
          numWordsCompoundMax = numWords;
        }
      }
    } else
    {
      numWordsCompoundMax = Integer.parseInt(numWordsCompound);
      for(String label: labels){
        numWordsCompoundMapped.put(label, Integer.parseInt(numWordsCompound));
      }
    }
    if(patternType.equals(PatternType.SURFACE))
      SurfacePatternFactory.setUp(props);
    else if(patternType.equals(PatternType.DEP))
      DepPatternFactory.setUp(props);
    else
      throw new UnsupportedOperationException();
  }

  public enum PatternType {SURFACE, DEP}

  public static boolean doNotUse(String word, Set<CandidatePhrase> stopWords) {
    return stopWords.contains(CandidatePhrase.createOrGet(word.toLowerCase()))
            || ignoreWordRegex.matcher(word).matches();
  }

  public static Map<Integer, Set> getPatternsAroundTokens(PatternType patternType, DataInstance sent, Set<CandidatePhrase> stopWords) {
      if(patternType.equals(PatternType.SURFACE)){
        return SurfacePatternFactory.getPatternsAroundTokens(sent, stopWords);
      } else if(patternType.equals(PatternType.DEP)){
        return (Map) DepPatternFactory.getPatternsAroundTokens(sent, stopWords);
      } else
        throw new UnsupportedOperationException();
  }

}
