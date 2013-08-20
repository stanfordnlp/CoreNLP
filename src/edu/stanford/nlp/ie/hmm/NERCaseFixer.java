package edu.stanford.nlp.ie.hmm;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import edu.stanford.nlp.stats.IntCounter;
import edu.stanford.nlp.stats.Counters;
import edu.stanford.nlp.objectbank.ObjectBank;
import edu.stanford.nlp.util.CollectionUtils;
import edu.stanford.nlp.util.StringUtils;

/**
 * Kinda hacky code for fixing case in all caps blocks and at beginning of sentences.
 * Designed but not ultimately used on CoNLL 03.
 *
 * @author Joseph Smarr (jsmarr@stanford.edu)
 */
public class NERCaseFixer {
  // minimum number of times a sentence-initial, non-NNP word has to have
  // been seen with an uppercase first char to be kept uc (instead of being
  // converted to lowercase)
  private static final int minUCCount = 0;
  // whether to replace sentence-initial non-nnp words with their most common
  // capitalization pattern, or only if they haven't been found in
  // non-sentence-initial position at least minUCCount times
  private static final boolean useModeCapPatternForSentenceInitial = true;

  // whether to fix case of runs of all-caps words to their mode case
  private static final boolean fixAllCaps = true;

  private List<String> lines = new ArrayList<String>(); // raw input lines
  private List<Boolean> allCaps = new ArrayList<Boolean>(); // boolean for each line whether the word was all caps
  private Map<String,IntCounter<String>> casePatternsByLC = new HashMap<String,IntCounter<String>>(); // lc word -> case pattern word -> count

  /**
   * Reads in the given file and prints the fixed version to stdout.
   */
  public NERCaseFixer(File nerFile) throws Exception {
    // counts case patterns
    boolean prevBlank = false; // was previous line blank
    for(String line : ObjectBank.getLineIterator(nerFile)) {
      lines.add(line);
      String word = line.split(" ")[0];
      allCaps.add(Boolean.valueOf(isAllCaps(word)));
      if (line.length() == 0 || line.startsWith("-DOCSTART-")) {
        prevBlank = true;
        continue;
      }

      // ignore sentence-initial words with more than one char
      if (prevBlank) {
        prevBlank = false;
        if (word.length() > 0) {
          continue;
        }
      }

      // count case patterns of this word
      String lc = word.toLowerCase();
      IntCounter<String> casePatterns = casePatternsByLC.get(lc);
      if (casePatterns == null) {
        casePatterns = new IntCounter<String>();
      }
      int count = casePatterns.getIntCount(word);
      casePatterns.setCount(word, count  + 1);
      casePatternsByLC.put(lc, casePatterns);
    }

    for (int i = 0; i < lines.size(); i++) {
      String line = lines.get(i);
      if (line.length() == 0 || line.startsWith("-DOCSTART-")) {
        System.out.println(line);
        prevBlank = true;
        continue;
      }
      String[] tokens = line.split(" ", 4);
      String word = tokens[0];
      String pos = tokens[1];

      // normalizes strings of all-caps word to each word's mode case
      boolean curAllCaps = allCaps.get(i);
      if (fixAllCaps && curAllCaps && ((i > 0 && allCaps.get(i-1) || (i < lines.size() - 1 && allCaps.get(i+1))))) {
        tokens[0] = getModeCasePattern(word);
      } else if (prevBlank && !pos.equals("NNP") && Character.isUpperCase(word.charAt(0))) {
        // lowercases sentence-initial not-NNP words if they're normally lc
        IntCounter<String> casePatterns = casePatternsByLC.get(word.toLowerCase());
        //System.out.println("*** "+casePatterns);
        if (casePatterns != null) {
          if (useModeCapPatternForSentenceInitial) {
            tokens[0] = getModeCasePattern(word);
          } else {
            int ucFirstCount = casePatterns.getIntCount(word);
            if (ucFirstCount < minUCCount) {
              tokens[0] = word.toLowerCase();
            }
          }
        }
      }
      System.out.print(StringUtils.join(tokens, " "));
      //if(!word.equals(tokens[0])) System.out.print("\t-- "+word);
      System.out.println();

      prevBlank = false;
    }
  }

  /**
   * Returns the case pattern for the given word that occurred most frequently.
   * Returns the word itself if it was never seen before. Ties in case pattern
   * counts are broken randomly.
   */
  private String getModeCasePattern(String word) {
    IntCounter<String> casePatterns = casePatternsByLC.get(word.toLowerCase());
    if (casePatterns == null) {
      return word;
    }
    List<String> entries = new ArrayList<String>(casePatterns.keySet());
    Collections.sort(entries, Counters.toComparatorDescending(casePatterns));
    return entries.get(0);
  }


  /**
   * Returns true iff s.length()>0 and all of s's chars pass isUpperCase.
   */
  private static boolean isAllCaps(String s) {
    for (int i = 0; i < s.length(); i++) {
      if (!Character.isUpperCase(s.charAt(i))) {
        return (false);
      }
    }
    return (s.length() > 0);
  }

  /**
   * Usage: java NERCaseFixer nerfile.
   */
  public static void main(String[] args) throws Exception {
    new NERCaseFixer(new File(args[0]));
  }
}
