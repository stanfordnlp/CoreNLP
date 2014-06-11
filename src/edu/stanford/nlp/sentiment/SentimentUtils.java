package edu.stanford.nlp.sentiment;

import java.util.List;

import edu.stanford.nlp.rnn.RNNCoreAnnotations;
import edu.stanford.nlp.trees.MemoryTreebank;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.util.Generics;

/**
 * In the Sentiment dataset converted to tree form, the labels on the
 * intermediate nodes are the sentiment scores and the leaves are the
 * text of the sentence.  This class provides routines to read a file
 * of those trees and attach the sentiment score as the GoldLabel
 * annotation.
 *
 * @author John Bauer
 */
public class SentimentUtils {
  private SentimentUtils() {} // static methods only

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

  /**
   * Given a file name, reads in those trees and returns them as a List
   */
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
