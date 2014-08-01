package edu.stanford.nlp.parser.common;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import edu.stanford.nlp.ling.HasWord;
import edu.stanford.nlp.trees.LabeledScoredTreeFactory;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.TreeFactory;


public class ParserUtils {
  /**
   * Construct a fall through tree in case we can't parse this sentence
   * @param words
   * @return a tree with X for all the internal nodes
   */
  public static Tree xTree(List<? extends HasWord> words) {
    TreeFactory lstf = new LabeledScoredTreeFactory();
    List<Tree> lst2 = new ArrayList<Tree>();
    for (HasWord obj : words) {
      String s = obj.word();
      Tree t = lstf.newLeaf(s);
      Tree t2 = lstf.newTreeNode("X", Collections.singletonList(t));
      lst2.add(t2);
    }
    return lstf.newTreeNode("X", lst2);
  }
}
