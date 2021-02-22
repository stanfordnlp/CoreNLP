package edu.stanford.nlp.parser.shiftreduce;

import java.util.Collection;

import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.parser.common.ParserConstraint;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.TreeCoreAnnotations;
import edu.stanford.nlp.util.ErasureUtils;

class ShiftReduceUtils {

  private ShiftReduceUtils() {} // static utility methods

  static BinaryTransition.Side getBinarySide(Tree tree) {
    if (tree.children().length != 2) {
      throw new AssertionError();
    }
    CoreLabel label = ErasureUtils.uncheckedCast(tree.label());
    CoreLabel childLabel = ErasureUtils.uncheckedCast(tree.children()[0].label());
    if (label.get(TreeCoreAnnotations.HeadWordLabelAnnotation.class) == childLabel.get(TreeCoreAnnotations.HeadWordLabelAnnotation.class)) {
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

  /** Returns a 0-based index of the head of the tree.  Assumes the leaves had been indexed from 1 */
  static int headIndex(Tree tree) {
    CoreLabel label = ErasureUtils.uncheckedCast(tree.label());
    CoreLabel headLabel = label.get(TreeCoreAnnotations.HeadWordLabelAnnotation.class);
    return headLabel.index() - 1;
  }

  /** Returns a 0-based index of the left leaf of the tree.  Assumes the leaves had been indexed from 1 */
  static int leftIndex(Tree tree) {
    if (tree.isLeaf()) {
      CoreLabel label = ErasureUtils.uncheckedCast(tree.label());
      return label.index() - 1;
    }

    return leftIndex(tree.children()[0]);
  }

  /** Returns a 0-based index of the right leaf of the tree.  Assumes the leaves had been indexed from 1 */
  static int rightIndex(Tree tree) {
    if (tree.isLeaf()) {
      CoreLabel label = ErasureUtils.uncheckedCast(tree.label());
      return label.index() - 1;
    }

    return rightIndex(tree.children()[tree.children().length - 1]);
  }

  /**
   * Note that this checks not just the top, but all unary descendants of the top
   */
  static boolean constraintMatchesTreeTop(Tree top, ParserConstraint constraint) {
    while (true) {
      if (constraint.state.matcher(top.value()).matches()) {
        return true;
      } else if (top.children().length == 1) {
        top = top.children()[0];
      } else {
        return false;
      }
    }
  }

  /**
   * Returns true iff the given {@code state} is present on the {@code agenda}
   */
  static boolean findStateOnAgenda(Collection<State> agenda, State state) {
    for (State other : agenda) {
      if (other.areTransitionsEqual(state)) {
        return true;
      }
    }
    return false;
  }

  static final String TRANSITION = "Transition";
  static String transitionShortName(Class<? extends Transition> t) {
    String className = t.toString();
    String[] pieces = className.split("[.]");
    className = pieces[pieces.length - 1];
    if (className.endsWith(TRANSITION) && className.length() > TRANSITION.length()) {
      return className.substring(0, className.length() - TRANSITION.length());
    }
    return className;
  }
}
