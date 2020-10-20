package edu.stanford.nlp.trees;

import java.util.ArrayList;
import java.util.List;

import edu.stanford.nlp.ling.Label;

/**
 * A tool to recursively alter a tree in various ways.  For example,
 * {@link edu.stanford.nlp.trees.BasicCategoryTreeTransformer} 
 * turns all the non-leaf labels of a tree into their basic categories
 * given a set of treebank parameters which describe how to turn the
 * basic categories.
 * <br>
 * There are three easy places to override and implement the needed
 * behavior.  transformTerminalLabel changes the labels of the
 * terminals, transformNonterminalLabel changes the labels of the
 * non-terminals, and transformLabel changes all labels.  If the tree
 * needs to be changed in different ways, transformTerminal or
 * transformNonterminal can be used instead.
 * 
 * @author John Bauer
 */
public abstract class RecursiveTreeTransformer implements TreeTransformer {
  @Override
  public Tree transformTree(Tree tree) {
    return transformHelper(tree);
  }

  public Tree transformHelper(Tree tree) {  
    if (tree.isLeaf()) {
      return transformTerminal(tree);
    } else {
      return transformNonterminal(tree);
    }
  }

  public Tree transformTerminal(Tree tree) {
    return tree.treeFactory().newLeaf(transformTerminalLabel(tree));
  }

  public Tree transformNonterminal(Tree tree) {
    List<Tree> children = new ArrayList<>(tree.children().length);
    for (Tree child : tree.children()) {
      children.add(transformHelper(child));
    }
    return tree.treeFactory().newTreeNode(transformNonterminalLabel(tree), children);
  }

  public Label transformTerminalLabel(Tree tree) {
    return transformLabel(tree);
  }

  public Label transformNonterminalLabel(Tree tree) {
    return transformLabel(tree);
  }

  public Label transformLabel(Tree tree) {
    if (tree.label() == null) {
      return null;
    }

    return tree.label().labelFactory().newLabel(tree.label());
  }
}


