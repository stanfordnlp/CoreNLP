package edu.stanford.nlp.trees.international.pennchinese;

import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.tregex.TregexPattern;

import java.io.Serializable;
import java.util.function.Predicate;

/**
 * Filters the fragments which end documents in Chinese Treebank
 */
public class FragmentTreeFilter implements Predicate<Tree>, Serializable {
  static final TregexPattern threeNodePattern = 
    TregexPattern.compile("FRAG=root <, (PU <: /（/) <2 (VV <: /完/) <- (PU=a <: /）/) <3 =a : =root !> (__ > __)");

  static final TregexPattern oneNodePattern =
    TregexPattern.compile("FRAG=root <: (VV <: /完/) : =root !> (__ > __)");

  static final TregexPattern automaticInitialPattern =
    TregexPattern.compile("automatic=root <: (initial !< __) : =root !> __");

  static final TregexPattern manuallySegmentedPattern =
    TregexPattern.compile("manually=root <: (segmented !< __) : =root !> __");

  static final TregexPattern onthewayPattern =
    TregexPattern.compile("FRAG=root <: (NR <: (ontheway !< __)) : =root !> (__ > __)");

  static final TregexPattern singlePuncFragPattern =
    TregexPattern.compile("__ !> __ <: (PU=punc <: __)");

  static final TregexPattern singlePuncPattern =
    TregexPattern.compile("PU=punc !> __ <: __");

  static final TregexPattern metaPattern =
    TregexPattern.compile("META !> __ <: NN");

  // The ctb tree reader uses CHTBTokenizer, which filters out SGML
  // and accidentally catches five trees in ctb7.  
  // TODO: One alternative would be to get rid of the specialized tokenizer
  static final TregexPattern bracketPattern =
    TregexPattern.compile("/[<>]/");

  static final TregexPattern[] patterns = { threeNodePattern, oneNodePattern, automaticInitialPattern, manuallySegmentedPattern, onthewayPattern, singlePuncFragPattern, singlePuncPattern, metaPattern, bracketPattern };

  public boolean test(Tree tree) {
    for (TregexPattern pattern : patterns) {
      if (pattern.matcher(tree).find()) {
        return false;
      }
    }
    return true;
  }

  private static final long serialVersionUID = 1L;
}
