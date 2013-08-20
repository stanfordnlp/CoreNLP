package edu.stanford.nlp.trees;

import edu.stanford.nlp.ling.CyclicCoreLabel;
import edu.stanford.nlp.ling.Label;
import edu.stanford.nlp.ling.StringLabel;

/**
 * Convert all labels in the Tree to a CyclicCoreLabel
 * takes current label and makes this CATEGORY in CyclicCoreLabel
 *
 * @author Aria Haghighi (aria42@stanford.edu)
 * @author Anna Rafferty (based off of Aria's original MapLabelTransformer)
 */
public class CyclicCoreLabelTransformer implements TreeTransformer {

  public CyclicCoreLabelTransformer() {
  }

  public Tree transformTree(Tree tree) {
    tree = tree.treeSkeletonCopy();
    try {
      for (Tree subtree : tree) {
        Label oldLabel = subtree.label();
        if (subtree == tree) {
          if (oldLabel == null) oldLabel = new StringLabel("S1");
        }
        CyclicCoreLabel newLabel = new CyclicCoreLabel(oldLabel);
        subtree.setLabel(newLabel);
      }
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
    return tree;
  }
}
