package edu.stanford.nlp.sentiment;

import edu.stanford.nlp.ling.CoreAnnotation;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.Label;
import java.util.List;

import edu.stanford.nlp.neural.rnn.RNNCoreAnnotations;
import edu.stanford.nlp.trees.MemoryTreebank;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.util.CollectionUtils;
import java.util.function.Predicate;
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
  private SentimentUtils() {
  } // static methods only

    public static void attachLabels(Tree tree, Class<? extends CoreAnnotation<Integer>> annotationClass) {
        if (tree.isLeaf()) {
            return;
        }
        for (Tree child : tree.children()) {
            attachLabels(child, annotationClass);
        }
        // In the sentiment data set, the node labels are simply the gold
        // class labels.  There are no categories encoded.
        int numericLabel = Integer.valueOf(tree.label().value());
        Label label = tree.label();
        if (!(label instanceof CoreLabel)) {
            throw new IllegalArgumentException("CoreLabels required!");
        }
        ((CoreLabel) label).set(annotationClass, numericLabel);

    }

    /**
     * Given a file name, reads in those trees and returns them as a List
     */
    public static List<Tree> readTreesWithGoldLabels(String path) {
        return readTreesWithLabels(path, RNNCoreAnnotations.GoldClass.class);
    }

    /**
     * Given a file name, reads in those trees and returns them as list with
     * labels attached as predictions
     */
    public static List<Tree> readTreesWithPredictedLabels(String path) {
        return readTreesWithLabels(path, RNNCoreAnnotations.PredictedClass.class);
    }

    /**
     * Given a file name, reads in those trees and returns them as a List
     */
    public static List<Tree> readTreesWithLabels(String path, Class<? extends CoreAnnotation<Integer>> annotationClass) {
        List<Tree> trees = Generics.newArrayList();
        MemoryTreebank treebank = new MemoryTreebank("utf-8");
        treebank.loadPath(path, null);
        for (Tree tree : treebank) {
            attachLabels(tree, annotationClass);
            trees.add(tree);
        }
        return trees;
    }

  static final Predicate<Tree> UNKNOWN_ROOT_FILTER = tree -> {
    int gold = RNNCoreAnnotations.getGoldClass(tree);
    return gold != -1;
  };

  public static List<Tree> filterUnknownRoots(List<Tree> trees) {
    return CollectionUtils.filterAsList(trees, UNKNOWN_ROOT_FILTER);
  }

  public static String sentimentString(SentimentModel model, int sentiment) {
    String[] classNames = model.op.classNames;
    if (sentiment < 0 || sentiment >= classNames.length) {
      return "Unknown sentiment label " + sentiment;
    }
    return classNames[sentiment];
  }
}
