package edu.stanford.nlp.trees;

import edu.stanford.nlp.ling.CategoryWordTagFactory;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.StringLabel;
import edu.stanford.nlp.ling.StringLabelFactory;
import java.util.function.Function;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * This is a utility class which vends tree transformers to translate
 * trees from one factory type to trees of another.  For example,
 * StringLabel trees need to be made into CategoryWordTag trees before
 * they can be head-percolated.  Enter
 * LabeledTreeToCategoryWordTagTreeFunction.
 *
 * @author <a href="mailto:klein@cs.stanford.edu">Dan Klein</a>
 * @version 1.0
 * @since 1.0
 */

public class TreeFunctions {

  private TreeFunctions() {
  }


  private static class LabeledTreeToStringLabeledTreeFunction implements Function<Tree, Tree> {

    protected TreeFactory tf = new LabeledScoredTreeFactory();

    public Tree helper(Tree t) {
      if (t == null) {
        return null;
      }
      if (t.isLeaf()) {
        return tf.newLeaf(new StringLabel(t.label().value()));
      }
      if (t.isPreTerminal()) {
        return tf.newTreeNode(new StringLabel(t.label().value()), Collections.singletonList(helper(t.children()[0])));
      }
      int numKids = t.numChildren();
      List<Tree> children = new ArrayList<>(numKids);
      for (int k = 0; k < numKids; k++) {
        children.add(helper(t.children()[k]));
      }
      return tf.newTreeNode(new StringLabel(t.label().value()), children);
    }

    public Tree apply(Tree t) {
      return helper(t);
    }
  } // end static class


  /**
   * Return an Function that maps from Label-labeled trees (any
   * implementing class) to LabeledScored trees with a StringLabel
   * label.
   *
   * @return The Function object
   */
  public static Function<Tree, Tree> getLabeledTreeToStringLabeledTreeFunction() {
    return new LabeledTreeToStringLabeledTreeFunction();
  }


  private static class LabeledTreeToCategoryWordTagTreeFunction implements Function<Tree, Tree> {

    protected TreeFactory tf =
      new LabeledScoredTreeFactory(new CategoryWordTagFactory());

    public Tree helper(Tree t) {
      if (t == null) {
        return null;
      } else if (t.isLeaf()) {
        return tf.newLeaf(t.label().value());
      } else if (t.isPreTerminal()) {
        return tf.newTreeNode(t.label().value(), Collections.singletonList(helper(t.children()[0])));
      } else {
        int numKids = t.numChildren();
        List<Tree> children = new ArrayList<>(numKids);
        for (int k = 0; k < numKids; k++) {
          children.add(helper(t.children()[k]));
        }
        return tf.newTreeNode(t.label().value(), children);
      }
    }

    public Tree apply(Tree o) {
      return helper(o);
    }

  } // end static class

  /**
   * Return a Function that maps from StringLabel labeled trees to
   * LabeledScoredTrees with a CategoryWordTag label.
   *
   * @return The Function object
   */
  public static Function<Tree, Tree> getLabeledTreeToCategoryWordTagTreeFunction() {
    return new LabeledTreeToCategoryWordTagTreeFunction();
  }

  /**
   * This function recursively goes through the tree and builds a new
   * copy with CoreLabels containing the toString() of the original label.
   */
  private static class LabeledToDescriptiveCoreLabelTreeFunction
    implements Function<Tree, Tree> {

    protected TreeFactory tf =
      new LabeledScoredTreeFactory(CoreLabel.factory());

    public Tree apply(Tree t) {
      if (t == null) {
        return null;
      } else if (t.isLeaf()) {
        return tf.newLeaf(t.label().toString());
      } else if (t.isPreTerminal()) {
        return tf.newTreeNode(t.label().toString(),
                         Collections.singletonList(apply(t.children()[0])));
      } else {
        int numKids = t.numChildren();
        List<Tree> children = new ArrayList<>(numKids);
        for (int k = 0; k < numKids; k++) {
          children.add(apply(t.children()[k]));
        }
        return tf.newTreeNode(t.label().toString(), children);
      }
    }
  }

  /**
   * Returns a function which takes a tree with any label class
   * where the labels might have an interesting description, such
   * as a CategoryWordTag which goes "cat [T/W]", and returns a new
   * tree with CoreLabels which contain the toString() of each of
   * the input labels.
   */
  public static Function<Tree, Tree>
    getLabeledToDescriptiveCoreLabelTreeFunction() {
    return new LabeledToDescriptiveCoreLabelTreeFunction();
  }


  /**
   * This method just tests the functionality of the included transformers.
   */
  public static void main(String[] args) {
    //TreeFactory tf = new LabeledScoredTreeFactory();
    Tree stringyTree = null;
    try {
      stringyTree = (new PennTreeReader(new StringReader("(S (VP (VBZ Try) (NP (DT this))) (. .))"), new LabeledScoredTreeFactory(new StringLabelFactory()))).readTree();
    } catch (IOException e) {
      // do nothing
    }
    System.out.println(stringyTree);
    Function<Tree, Tree> a = getLabeledTreeToCategoryWordTagTreeFunction();
    Tree adaptyTree = a.apply(stringyTree);
    System.out.println(adaptyTree);
    adaptyTree.percolateHeads(new CollinsHeadFinder());
    System.out.println(adaptyTree);

    Function<Tree, Tree> b = getLabeledTreeToStringLabeledTreeFunction();
    Tree stringLabelTree = b.apply(adaptyTree);
    System.out.println(stringLabelTree);

  }

}
