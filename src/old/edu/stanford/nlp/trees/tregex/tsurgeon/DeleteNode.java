package old.edu.stanford.nlp.trees.tregex.tsurgeon;

import java.util.List;
import old.edu.stanford.nlp.trees.Tree;
import old.edu.stanford.nlp.trees.Trees;
import old.edu.stanford.nlp.trees.tregex.TregexMatcher;

/**
 * @author Roger Levy (rog@stanford.edu)
 */
class DeleteNode extends TsurgeonPattern {

  public DeleteNode(TsurgeonPattern[] children) {
    super("delete", children);
  }

  public DeleteNode(List<TsurgeonPattern> children) {
    this(children.toArray(new TsurgeonPattern[children.size()]));
  }


  @Override
  public Tree evaluate(Tree t, TregexMatcher m) {
    Tree result = t;
    for (TsurgeonPattern child : children) {
      Tree nodeToDelete = child.evaluate(t, m);
      if (nodeToDelete == t) {
        result = null;
      }
      Tree parent = nodeToDelete.parent(t);
      parent.removeChild(Trees.objectEqualityIndexOf(parent,nodeToDelete));
    }
    return result;
  }

}
