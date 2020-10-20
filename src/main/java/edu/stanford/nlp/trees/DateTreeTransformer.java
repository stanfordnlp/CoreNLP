package edu.stanford.nlp.trees;

import edu.stanford.nlp.trees.tregex.TregexMatcher;
import edu.stanford.nlp.trees.tregex.TregexPattern;
import edu.stanford.nlp.trees.tregex.TregexPatternCompiler;

/**
 * Flattens the following two structures:
 * <br>
 * (NP (NP (NNP Month) (CD Day) )
 * (, ,) 
 * (NP (CD Year) ))
 * <br>
 * becomes
 * <br>
 * (NP (NNP Month) (CD Day) (, ,) (CD Year) )
 * <br>
 * (NP (NP (NNP Month) )
 * (NP (CD Year) ))
 * <br>
 * becomes
 * <br>
 * (NP (NNP Month) (CD Year))
 *
 * @author John Bauer
 */
public class DateTreeTransformer implements TreeTransformer {
  static final String MONTH_REGEX = "January|February|March|April|May|June|July|August|September|October|November|December|Jan\\.|Feb\\.|Mar\\.|Apr\\.|Aug\\.|Sep\\.|Sept\\.|Oct\\.|Nov\\.|Dec\\.";
  static final TregexPattern tregexMonthYear = TregexPatternCompiler.defaultCompiler.compile("NP=root <1 (NP <: (NNP=month <: /" + MONTH_REGEX + "/)) <2 (NP=yearnp <: (CD=year <: __)) : =root <- =yearnp");
  static final TregexPattern tregexMonthDayYear = TregexPatternCompiler.defaultCompiler.compile("NP=root <1 (NP=monthdayroot <1 (NNP=month <: /" + MONTH_REGEX +"/) <2 (CD=day <: __)) <2 (/^,$/=comma <: /^,$/) <3 (NP=yearroot <: (CD=year <: __)) : (=root <- =yearroot) : (=monthdayroot <- =day)");

  public Tree transformTree(Tree t) {
    TregexMatcher matcher = tregexMonthYear.matcher(t);
    while (matcher.find()) {
      Tree root = matcher.getNode("root");
      Tree month = matcher.getNode("month");
      Tree year = matcher.getNode("year");
      Tree[] children = new Tree[] {month, year};
      root.setChildren(children);
      matcher = tregexMonthYear.matcher(t);
    }
    matcher = tregexMonthDayYear.matcher(t);
    while (matcher.find()) {
      Tree root = matcher.getNode("root");
      Tree month = matcher.getNode("month");
      Tree day = matcher.getNode("day");
      Tree comma = matcher.getNode("comma");
      Tree year = matcher.getNode("year");
      Tree[] children = new Tree[] {month, day, comma, year};
      root.setChildren(children);
      matcher = tregexMonthDayYear.matcher(t);
    }
    return t;
  }
}
