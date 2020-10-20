package edu.stanford.nlp.trees;

import edu.stanford.nlp.ling.Label;
import java.util.function.Function;

/**
 * Applies a Function to the labels in a tree.  
 *
 * @author John Bauer
 */
public class TreeLeafLabelTransformer implements TreeTransformer {
  Function<String, String> transform;

  public TreeLeafLabelTransformer(Function<String, String> transform) {
    this.transform = transform;
  }

  public Tree transformTree(Tree tree) {
    for (Tree leaf : tree.getLeaves()) {
      Label label = leaf.label();
      label.setValue(transform.apply(label.value()));
    }
    return tree;
  }
}
