package edu.stanford.nlp.parser.shiftreduce;

import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.TreeCoreAnnotations;
import edu.stanford.nlp.util.ErasureUtils;

class ShiftReduceUtils {
  static BinaryTransition.Side getBinarySide(Tree tree) {
    if (tree.children().length != 2) {
      throw new AssertionError();
    }
    CoreLabel label = ErasureUtils.uncheckedCast(tree.label());
    CoreLabel childLabel = ErasureUtils.uncheckedCast(tree.children()[0].label());
    if (label.get(TreeCoreAnnotations.HeadWordAnnotation.class) == childLabel.get(TreeCoreAnnotations.HeadWordAnnotation.class)) {
      return BinaryTransition.Side.LEFT;
    } else {
      return BinaryTransition.Side.RIGHT;
    }
  }  

  static boolean isTemporary(Tree tree) {
    String label = tree.value();
    return label.startsWith("@");
  }

  static boolean isEquivalentCategory(String l1, String l2) {
    if (l1.startsWith("@")) l1 = l1.substring(1);
    if (l2.startsWith("@")) l2 = l2.substring(1);
    return l1.equals(l2);
  }

  static int headIndex(Tree tree) {
    CoreLabel label = ErasureUtils.uncheckedCast(tree.label());
    Tree head = label.get(TreeCoreAnnotations.HeadWordAnnotation.class);
    CoreLabel headLabel = ErasureUtils.uncheckedCast(head.label());
    return headLabel.index();
  }

  static int leftIndex(Tree tree) {
    if (tree.isLeaf()) {
      CoreLabel label = ErasureUtils.uncheckedCast(tree.label());
      return label.index();
    }

    return leftIndex(tree.children()[0]);
  }
    
  static int rightIndex(Tree tree) {
    if (tree.isLeaf()) {
      CoreLabel label = ErasureUtils.uncheckedCast(tree.label());
      return label.index();
    }

    return rightIndex(tree.children()[tree.children().length - 1]);
  }
}
