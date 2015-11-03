package edu.stanford.nlp.parser.common;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import edu.stanford.nlp.ling.HasTag;
import edu.stanford.nlp.ling.HasWord;
import edu.stanford.nlp.trees.LabeledScoredTreeFactory;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.TreeFactory;


public class ParserUtils {
  /**
   * Construct a fall through tree in case we can't parse this sentence
   * @param words
   * @return a tree with X for all the internal nodes.  preterminals have the right tag if the words are tagged
   */
  public static Tree xTree(List<? extends HasWord> words) {
    TreeFactory lstf = new LabeledScoredTreeFactory();
    List<Tree> lst2 = new ArrayList<>();
    for (HasWord obj : words) {
      String s = obj.word();
      Tree t = lstf.newLeaf(s);
      String tag = "XX";
      if (obj instanceof HasTag) {
        if (((HasTag) obj).tag() != null) {
          tag = ((HasTag) obj).tag();
        }
      }
      Tree t2 = lstf.newTreeNode(tag, Collections.singletonList(t));
      lst2.add(t2);
    }
    return lstf.newTreeNode("X", lst2);
  }
}
