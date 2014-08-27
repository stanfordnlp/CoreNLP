package edu.stanford.nlp.trees.international.pennchinese;

import java.util.List;
import java.util.ArrayList;
import java.util.regex.*;

import edu.stanford.nlp.ling.HasWord;
import edu.stanford.nlp.util.Function;
import edu.stanford.nlp.util.StringUtils;
import edu.stanford.nlp.util.UTF8EquivalenceFunction;

/**
 * An Escaper for Chinese normalization to match Treebank.
 * Currently normalizes "ASCII" characters into the full-width
 * range used inside the Penn Chinese Treebank.
 * <p/>
 * <i>Notes:</i> Smart quotes appear in CTB, and are left unchanged.
 * I think you get various hyphen types from U+2000 range too - certainly,
 * Roger lists them in LanguagePack.
 *
 * @author Christopher Manning
 */
public class ChineseEscaper implements Function<List<HasWord>, List<HasWord>> {

  /** IBM entity normalization patterns */
  private static final Pattern p2 = Pattern.compile("\\$[a-z]+_\\((.*?)\\|\\|.*?\\)");

  /** <i>Note:</i> At present this clobbers the input list items.
   *  This should be fixed.
   */
  public List<HasWord> apply(List<HasWord> arg) {
    List<HasWord> ans = new ArrayList<HasWord>(arg);
    for (HasWord wd : ans) {
      String w = wd.word();
      Matcher m2 = p2.matcher(w);
      // System.err.println("Escaper: w is " + w);
      if (m2.find()) {
        // System.err.println("  Found pattern.");
        w = m2.replaceAll("$1");
        // System.err.println("  Changed it to: " + w);
      }
      String newW = UTF8EquivalenceFunction.replaceAscii(w);
      wd.setWord(newW);
    }
    return ans;
  }

}

