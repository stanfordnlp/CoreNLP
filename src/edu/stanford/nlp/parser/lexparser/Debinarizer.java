package edu.stanford.nlp.parser.lexparser;

import java.util.*;

import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.LabelFactory;
import edu.stanford.nlp.trees.LabeledScoredTreeFactory;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.TreeFactory;
import edu.stanford.nlp.trees.TreeTransformer;

/** Debinarizes a binary tree from the parser.
 *  Node values with a '@' in them anywhere are assumed to be inserted
 *  nodes for the purpose of binarization, and are removed.
 *  The code also removes the last child of the root node, assuming
 *  that is an inserted dependency root.
 */
public class Debinarizer implements TreeTransformer {

  private final TreeFactory tf;
  private final boolean forceCNF;


  protected Tree transformTreeHelper(Tree t) {
    if (t.isLeaf()) {
      Tree leaf = tf.newLeaf(t.label());
      leaf.setScore(t.score());
      return leaf;
    }
    List<Tree> newChildren = new ArrayList<Tree>(20);
    for (int childNum = 0, numKids = t.numChildren(); childNum < numKids; childNum++) {
      Tree child = t.getChild(childNum);
      Tree newChild = transformTreeHelper(child);
      if ((!newChild.isLeaf()) && newChild.label().value().indexOf('@') >= 0) {
        newChildren.addAll(newChild.getChildrenAsList());
      } else {
        newChildren.add(newChild);
      }
    }
    Tree node = tf.newTreeNode(t.label(), newChildren);
    node.setScore(t.score());
    return node;
  }

  public Tree transformTree(Tree t) {
    Tree result = transformTreeHelper(t);
    if (forceCNF) {
      result = new CNFTransformers.FromCNFTransformer().transformTree(result);
    }
    return new BoundaryRemover().transformTree(result);
  }

  public Debinarizer(boolean forceCNF) {
    this(forceCNF, CoreLabel.factory());
  }

  public Debinarizer(boolean forceCNF, LabelFactory lf) {
    this.forceCNF = forceCNF;
    tf = new LabeledScoredTreeFactory(lf);
  }

} // end class Debinarizer
