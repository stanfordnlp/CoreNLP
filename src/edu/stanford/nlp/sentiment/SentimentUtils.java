package edu.stanford.nlp.sentiment;

import java.util.List;

import edu.stanford.nlp.rnn.RNNCoreAnnotations;
import edu.stanford.nlp.trees.MemoryTreebank;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.util.Generics;

public class SentimentUtils {
  public static void attachGoldLabels(Tree tree) {
    if (tree.isLeaf()) {
      return;
    }
    for (Tree child : tree.children()) {
      attachGoldLabels(child);
    }

    // In the sentiment data set, the node labels are simply the gold
    // class labels.  There are no categories encoded.
    RNNCoreAnnotations.setGoldClass(tree, Integer.valueOf(tree.label().value()));
  }

  public static List<Tree> readTreesWithGoldLabels(String path) {
    List<Tree> trees = Generics.newArrayList();
    MemoryTreebank treebank = new MemoryTreebank();
    treebank.loadPath(path, null);
    for (Tree tree : treebank) {
      attachGoldLabels(tree);
      trees.add(tree);
    }
    return trees;
  }


}
