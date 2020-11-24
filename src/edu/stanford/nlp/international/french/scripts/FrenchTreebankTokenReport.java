package edu.stanford.nlp.international.french.scripts;

import edu.stanford.nlp.stats.*;
import edu.stanford.nlp.trees.*;
import edu.stanford.nlp.trees.tregex.*;

import java.io.*;
import java.util.*;


public class FrenchTreebankTokenReport {

  public static HashMap<String,TregexPattern> mwtStringToPattern = new HashMap<>();
  public static Counter<String> mwtPatternCounts = new ClassicCounter<>();
  public static List<String> patternsInOrder = Arrays.asList(
      "/^(A|a)u$/",
      "/^(A|a)ux$/",
      "/^(D|d)u$/",
      "/^(D|d)es$/",
      "/^(A|a)uxquelles$/",
      "---",
      "P < /^(A|a)u$/",
      "P < /^(A|a)ux$/",
      "P < /^(D|d)u$/",
      "P < /^(D|d)es$/",
      "---",
      "DET < /^(A|a)u$/",
      "DET < /^(A|a)ux$/",
      "DET < /^(D|d)u$/",
      "DET < /^(D|d)es$/",
      "---",
      "PUNC < /^-$/",
      "PREF < /.*-/"
  );

  static {

    for (String pattern : patternsInOrder) {
      mwtPatternCounts.setCount(pattern,0);
    }

    for (String pattern : mwtPatternCounts.keySet()) {
      mwtStringToPattern.put(pattern, TregexPattern.compile(pattern));
    }
  }

  public static void main(String[] args) throws IOException {
    /** set up tree reader **/
    TreeFactory tf = new LabeledScoredTreeFactory();
    Reader r = new BufferedReader(new InputStreamReader(new FileInputStream(args[0]), "UTF-8"));
    TreeReader tr = new PennTreeReader(r, tf);
    /** iterate through trees **/
    Tree fullTree = tr.readTree();
    while (fullTree != null) {
      for (String patternString : mwtStringToPattern.keySet()) {
        TregexMatcher matcher = mwtStringToPattern.get(patternString).matcher(fullTree);
        while (matcher.find()) {
          mwtPatternCounts.incrementCount(patternString);
        }
      }
      fullTree = tr.readTree();
    }
    /** print report **/
    for (String patternString : patternsInOrder) {
      if (!patternString.equals("---")) {
        System.out.println(patternString + ": " + Math.round(mwtPatternCounts.getCount(patternString)));
      } else {
        System.out.println("---");
      }
    }

  }

}
