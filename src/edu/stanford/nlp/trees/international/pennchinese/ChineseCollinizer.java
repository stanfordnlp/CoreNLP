package edu.stanford.nlp.trees.international.pennchinese;

import edu.stanford.nlp.ling.StringLabel;
import edu.stanford.nlp.trees.LabeledScoredTreeFactory;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.TreeFactory;
import edu.stanford.nlp.trees.TreeTransformer;

import java.util.ArrayList;
import java.util.List;

/**
 * Performs collinization operations on Chinese trees similar to
 * those for English Namely: <ul>
 * <li> strips all functional &amp; automatically-added tags
 * <li> strips all punctuation
 * <li> merges PRN and ADVP
 * <li> eliminates ROOT (note that there are a few non-unary ROOT nodes;
 * these are not eliminated)
 * </ul>
 *
 * @author Roger Levy
 * @author Christopher Manning
 */
public class ChineseCollinizer implements TreeTransformer {

  private final static boolean VERBOSE = false;

  private final boolean deletePunct;
  ChineseTreebankLanguagePack ctlp;

  protected TreeFactory tf = new LabeledScoredTreeFactory();


  public ChineseCollinizer(ChineseTreebankLanguagePack ctlp) {
    this(ctlp, true);
  }

  public ChineseCollinizer(ChineseTreebankLanguagePack ctlp, boolean deletePunct) {
    this.deletePunct = deletePunct;
    this.ctlp = ctlp;
  }


  public Tree transformTree(Tree tree) {
    return transformTree(tree, true);
  }

  private Tree transformTree(Tree tree, boolean isRoot) {
    String label = tree.label().value();

    // System.err.println("ChineseCollinizer: Node label is " + label);

    if (tree.isLeaf()) {
      if (deletePunct && ctlp.isPunctuationWord(label)) {
        return null;
      } else {
        return tf.newLeaf(new StringLabel(label));
      }
    }
    if (tree.isPreTerminal() && deletePunct && ctlp.isPunctuationTag(label)) {
      // System.out.println("Deleting punctuation");
      return null;
    }
    List<Tree> children = new ArrayList<Tree>();

    if (label.matches("ROOT.*") && tree.numChildren() == 1) { // keep non-unary roots for now
      return transformTree(tree.children()[0], true);
    }

    //System.out.println("Enhanced label is " + label);

    // remove all functional and machine-generated annotations
    label = label.replaceFirst("[^A-Z].*$", "");
    // merge parentheticals with adverb phrases
    label = label.replaceFirst("PRN", "ADVP");

    //System.out.println("New label is " + label);

    for (int cNum = 0; cNum < tree.children().length; cNum++) {
      Tree child = tree.children()[cNum];
      Tree newChild = transformTree(child, false);
      if (newChild != null) {
        children.add(newChild);
      }
    }
    // We don't delete the root because there are trees in the
    // Chinese treebank that only have punctuation in them!!!
    if (children.isEmpty() && ! isRoot) {
      if (VERBOSE) {
        System.err.println("ChineseCollinizer: all children of " + label +
                           " deleted; returning null");
      }
      return null;
    }
    return tf.newTreeNode(new StringLabel(label), children);
  }

}
