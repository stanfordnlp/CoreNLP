package edu.stanford.nlp.trees;

import edu.stanford.nlp.ling.Label;
import edu.stanford.nlp.util.Function;

import java.util.List;
import java.util.ArrayList;

/** A Function that does nothing but alter some node labels.
 *
 * @author Roger Levy (rog@stanford.edu)
 */
public abstract class LabelAlteringTreeFunction implements Function<Tree,Tree> {

  public Tree apply(Tree t) {
    return apply(t,t);
  }

  private Tree apply(Tree t, Tree root) {
    if(t.isLeaf())
      return t.treeFactory().newLeaf(transformLabel(t,root));
    else {
      Tree[] kids = t.children();
      List<Tree> newKids = new ArrayList<Tree>(kids.length);
      for(Tree kid : kids) {
        newKids.add(apply(kid,root));
      }
      return t.treeFactory().newTreeNode(transformLabel(t,root),newKids);
    }
  }

  /**
   * Transforms the Label of a Tree node.
   */
  public abstract Label transformLabel(Tree node, Tree root);
}
