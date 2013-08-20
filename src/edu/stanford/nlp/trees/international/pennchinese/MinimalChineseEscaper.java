package edu.stanford.nlp.trees.international.pennchinese;

import java.util.ArrayList;
import java.util.List;

import edu.stanford.nlp.ling.HasWord;
import edu.stanford.nlp.util.Function;
import edu.stanford.nlp.util.StringUtils;


/**
 * An Escaper for Chinese normalization.  This only escapes ASCII parentheses
 * to full width parentheses so they don't ruin the penn-format tree output.
 * (In general, we recommend converting all ASCII range characters to full-width
 * as is done in ChineseEscaper, as that better matches the Chinese parser
 * training material, and so the parses may be better, but you may not wish to.
 * This one does the minimal normalization to make sure the output parse trees
 * are well-formed.)
 *
 * @author Christopher Manning
 */
public class MinimalChineseEscaper implements Function<List<HasWord>, List<HasWord>> {


  /** <i>Note:</i> At present this clobbers the input list items.
   *  This should be fixed.
   */
  public List<HasWord> apply(List<HasWord> arg) {
    List<HasWord> ans = new ArrayList<HasWord>(arg);
    for (HasWord wd : ans) {
      String w = wd.word();
      String newW = StringUtils.tr(w, "()", "\uFF08\uFF09");
      wd.setWord(newW);
    }
    return ans;
  }

}

