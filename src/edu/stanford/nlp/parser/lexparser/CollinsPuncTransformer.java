package edu.stanford.nlp.parser.lexparser;

import java.util.*;

import edu.stanford.nlp.trees.TreeTransformer;
import edu.stanford.nlp.trees.TreebankLanguagePack;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.TreeFactory;


/** This class manipulates punctuation in trees (used with training trees)
 *  in the same manner that Collins manipulated punctuation in trees when
 *  building his parsing model.  This is the same punctuation that is
 *  the punctuation ignored in the standard EvalB evaluation is promoted
 *  as high in the tree as possible.
 *
 *  @author Dan Klein
 *  @author Christopher Manning
 */
public class CollinsPuncTransformer implements TreeTransformer {

  private TreebankLanguagePack tlp;

  boolean isPunc(Tree t) {
    if (t.isPreTerminal()) {
      String s = t.label().value();
      if (tlp.isEvalBIgnoredPunctuationTag(s)) {
        return true;
      }
    }
    return false;
  }

  static LinkedList<Tree> preTerms(Tree t) {
    LinkedList<Tree> l = new LinkedList<Tree>();
    preTermHelper(t, l);
    return l;
  }

  static void preTermHelper(Tree t, List<Tree> l) {
    if (t.isLeaf()) {
      return;
    }
    if (t.isPreTerminal()) {
      l.add(t);
      return;
    }
    Tree[] children = t.children();
    for (Tree child : children) {
      preTermHelper(child, l);
    }
  }

  Tree transformRoot(Tree tree, TreeFactory tf) {
    // XXXX TODO: use tlp and don't assume 1 daughter of ROOT!
    // leave the root intact
    // if (tlp.isStartSymbol(tlp.basicCategory(tree.label().value())))
    if (tree.label().toString().startsWith("ROOT")) {
      return tf.newTreeNode(tree.label(), Collections.singletonList(transformNode(tree.children()[0], tf)));
    }
    return transformNode(tree, tf);
  }

  Tree transformNode(Tree tree, TreeFactory tf) {
    if (tree.isLeaf()) {
      return tf.newLeaf(tree.label());
    }
    if (tree.isPreTerminal()) {
      return tf.newTreeNode(tree.label(), Collections.singletonList(tf.newLeaf(tree.children()[0].label())));
    }
    List<Tree> children = tree.getChildrenAsList();
    LinkedList<Tree> newChildren = new LinkedList<Tree>();
    // promote lower punctuation
    for (Tree child : children) {
      LinkedList<Tree> preTerms = preTerms(child);
      while (!preTerms.isEmpty() && isPunc(preTerms.getFirst())) {
        newChildren.add(preTerms.getFirst());
        preTerms.removeFirst();
      }
      Tree newChild = transformNode(child, tf);
      LinkedList<Tree> temp = new LinkedList<Tree>();
      if (newChild.children().length > 0) {
        newChildren.add(newChild);
      }
      while (!preTerms.isEmpty() && isPunc(preTerms.getLast())) {
        temp.addFirst(preTerms.getLast());
        preTerms.removeLast();
      }
      newChildren.addAll(temp);
    }
    // remove local punctuation
    while (!newChildren.isEmpty() && isPunc(newChildren.getFirst())) {
      newChildren.removeFirst();
    }
    while (!newChildren.isEmpty() && isPunc(newChildren.getLast())) {
      newChildren.removeLast();
    }
    return tf.newTreeNode(tree.label(), newChildren);
  }


//   public Tree transformTree(Tree tree) {
//     //System.out.println("PUNCTUATION TRANSFORM:");
//     //tree.pennPrint();
//     //System.out.println("BECOMES:");
//     //transformRoot(tree, tf).pennPrint();
//     return transformRoot(tree, tf);
//   }

   public Tree transformTree(Tree tree) {
     return transformRoot(tree, tree.treeFactory());
   }

  public CollinsPuncTransformer(TreebankLanguagePack tlp) {
    this.tlp = tlp;
  }

}
