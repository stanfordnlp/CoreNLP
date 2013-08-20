package edu.stanford.nlp.parser.lexparser;

import java.util.List;

import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.TreeTransformer;

/**
 * Removes a boundary symbol (Lexicon.BOUNDARY_TAG or Lexicon.BOUNDARY), which
 * is the rightmost daughter of a tree.  Otherwise does nothing.
 * This is needed because the dependency parser uses such symbols.
 * <p/>
 * <i>Note:</i> This method is a function and not destructive. A new root tree is returned.
 *
 * @author Christopher Manning
 */
public class BoundaryRemover implements TreeTransformer {

  public BoundaryRemover() {
  }

  @Override
  public Tree transformTree(Tree tree) {
    Tree last = tree.lastChild();
    if (last.label().value().equals(Lexicon.BOUNDARY_TAG) ||
        last.label().value().equals(Lexicon.BOUNDARY)) {
      List<Tree> childList = tree.getChildrenAsList();
      List<Tree> lastGoneList = childList.subList(0, childList.size() - 1);
      return tree.treeFactory().newTreeNode(tree.label(), lastGoneList);
    } else {
      return tree;
    }
  }

} // end class BoundaryRemover
