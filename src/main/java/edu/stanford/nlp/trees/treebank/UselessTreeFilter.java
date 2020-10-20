package edu.stanford.nlp.trees.treebank;

import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.tregex.TregexMatcher;
import edu.stanford.nlp.trees.tregex.TregexPattern;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Deletes trees from the EWT which we deem to be useless.  Keeps one
 * alive to serve as an example to the rest.
 *<br>
 * Example trees removed:
 * <ul>
 * <li> ((NP (ADD http://www.your.mom)))
 * <li> ((NP (NNP Gritty)))
 * </ul>
 * Example usage:
 * <br>
 * java edu.stanford.nlp.trees.Treebanks -normalize -filter edu.stanford.nlp.trees.treebank.UselessTreeFilter -pennPrint /u/nlp/data/constituency-parser/models-4.0.0/data/ewt/ptb/train/ewt-train.mrg
 */
public class UselessTreeFilter implements Predicate<Tree> {
  Map<TregexPattern, Boolean> rootPatterns = new HashMap<TregexPattern, Boolean>() {{
      // For these patterns to work, the trees need to be normalized
      // or otherwise have ROOT at the top
      put(TregexPattern.compile("NP > (__ !> __) <: ADD"), false);
      put(TregexPattern.compile("NP > (__ !> __) <: NNP"), false);
      put(TregexPattern.compile("NP > (__ !> __) <1 NNP <2 NNP !<3 __"), false);
    }};

  Map<TregexPattern, Boolean> nullPatterns = new HashMap<TregexPattern, Boolean>() {{
      // These work if the trees start with null instead of ROOT
      put(TregexPattern.compile("NP !> __ <: ADD"), false);
      put(TregexPattern.compile("NP !> __ <: NNP"), false);
      put(TregexPattern.compile("NP !> __ <1 NNP <2 NNP !<3 __"), false);
    }};

  public boolean test(Tree tree) {
    final Map<TregexPattern, Boolean> patterns;
    if (tree.value() == null) {
      patterns = nullPatterns;
    } else {
      patterns = rootPatterns;
    }
    for (TregexPattern pattern : patterns.keySet()) {
      TregexMatcher matcher = pattern.matcher(tree);
      if (matcher.findNextMatchingNode()) {
        if (patterns.get(pattern)) {
          return false;
        } else {
          patterns.put(pattern, true);
        }
      }
    }
    return true;
  }
}

