package edu.stanford.nlp.international.chinese.trees;

import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.tregex.TregexPattern;
import edu.stanford.nlp.util.Filter;

/**
 * Filters the fragments which end documents in Chinese Treebank
 */
public class FragmentTreeFilter implements Filter<Tree> {
  static final TregexPattern threeNodePattern = 
    TregexPattern.compile("FRAG=root <, (PU <: /（/) <2 (VV <: /完/) <- (PU=a <: /）/) <3 =a : =root !> (__ > __)");

  static final TregexPattern oneNodePattern =
    TregexPattern.compile("FRAG=root <: (VV <: /完/) : =root !> (__ > __)");

  public boolean accept(Tree tree) {
    if (threeNodePattern.matcher(tree).find()) {
      return false;
    }
    if (oneNodePattern.matcher(tree).find()) {
      return false;
    }
    return true;
  }

  private static final long serialVersionUID = 1L;
}
