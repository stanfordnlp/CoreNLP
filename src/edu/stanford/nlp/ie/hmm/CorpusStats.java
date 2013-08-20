package edu.stanford.nlp.ie.hmm;

import edu.stanford.nlp.ie.Corpus;
import edu.stanford.nlp.ie.TypedTaggedDocument;
import edu.stanford.nlp.ling.TypedTaggedWord;

import java.util.*;

/**
 * Standalone utility class for investigating lexical frequencies.
 *
 * @author Joseph Smarr (jsmarr@stanford.edu)
 */
public class CorpusStats {
  /**
   * Counts the occurrences of words in the target and bg state and reports probs.
   * <pre>Usage: java CorpusState corpus targetField word+</pre>
   */
  public static void main(String[] args) {
    if (args.length < 3) {
      System.err.println("Usage: java CorpusStats corpus targetField word+");
      System.exit(1);
    }

    // words we want to count
    Set words = new HashSet(args.length - 2);
    for (int i = 2; i < args.length; i++) {
      words.add(args[i]);
    }

    Corpus docs = new Corpus(args[0], args[1]);
    Map targetCounts = new HashMap();
    Map backgroundCounts = new HashMap();
    for (int i = 0; i < docs.size(); i++) {
      TypedTaggedDocument doc = (TypedTaggedDocument) docs.get(i);
      for (int j = 0; j < doc.size(); j++) {
        TypedTaggedWord ttw = (TypedTaggedWord) doc.get(j);
        if (words.contains(ttw.word())) {
          if (ttw.type() == 0) {
            incrementCount(backgroundCounts, ttw.word());
          } else {
            incrementCount(targetCounts, ttw.word());
          }
        }
        if (ttw.type() == 0) {
          incrementCount(backgroundCounts, "ALLWORDS");
        } else {
          incrementCount(targetCounts, "ALLWORDS");
        }
      }
    }
    System.err.println("Target counts: " + targetCounts);
    System.err.println("Background counts: " + backgroundCounts);

    int numTargetWords = ((Integer) targetCounts.get("ALLWORDS")).intValue();
    int numBackgroundWords = ((Integer) backgroundCounts.get("ALLWORDS")).intValue();
    Iterator iter = words.iterator();
    while (iter.hasNext()) {
      String word = (String) iter.next();
      System.out.println();

      Integer targetCount = (Integer) targetCounts.get(word);
      if (targetCount == null) {
        targetCount = Integer.valueOf(0);
      }
      int tc = targetCount.intValue();
      System.out.println("P(" + word + "|" + args[1] + ") = " + (1.0 * tc / numTargetWords));

      Integer backgroundCount = (Integer) backgroundCounts.get(word);
      if (backgroundCount == null) {
        backgroundCount = Integer.valueOf(0);
      }
      int bc = backgroundCount.intValue();
      System.out.println("P(" + word + "|background) = " + (1.0 * bc / numBackgroundWords));

      System.out.println("P(" + args[1] + "|" + word + ") = " + (1.0 * tc / (tc + bc)));
      System.out.println("P(background|" + word + ") = " + (1.0 * bc / (tc + bc)));
    }

  }

  /**
   * Adds one to the Integer count stored in the given map for the given key.
   */
  private static void incrementCount(Map map, String key) {
    Integer count = (Integer) map.get(key);
    if (count == null) {
      count = Integer.valueOf(0);
    }
    map.put(key, Integer.valueOf(count.intValue() + 1));
  }
}

