package edu.stanford.nlp.trees.tregex.tsurgeon;

import java.util.List;

import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.Label;
import edu.stanford.nlp.ling.LabelFactory;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.tregex.TregexMatcher;
import edu.stanford.nlp.util.Generics;

/**
 * Given the start and end children of a particular node, takes all
 * children between start and end (including the endpoints) and
 * combines them in a new node with the given label.
 *
 * @author John Bauer
 */
public class CreateSubtreeNode extends TsurgeonPattern {
  private final String newLabel;

  public CreateSubtreeNode(TsurgeonPattern start, String newLabel) {
    super("combineSubtrees", new TsurgeonPattern[] { start });
    this.newLabel = newLabel;
  }

  public CreateSubtreeNode(TsurgeonPattern start, TsurgeonPattern end, String newLabel) {
    super("combineSubtrees", new TsurgeonPattern[] { start, end });
    this.newLabel = newLabel;
  }

  /**
   * Combines all nodes between start and end into one subtree, then
   * replaces those nodes with the new subtree in the corresponding
   * location under parent
   */
  @Override
  public Tree evaluate(Tree t, TregexMatcher tm) {
    Tree startChild = children[0].evaluate(t, tm);
    Tree endChild = (children.length == 2) ? children[1].evaluate(t, tm) : startChild;
    
    Tree parent = startChild.parent(t);

    // sanity check
    if (parent != endChild.parent(t)) {
      throw new TsurgeonRuntimeException("Parents did not match for trees when applied to " + this);
    }

    // prepare a new label
    Label label;
    LabelFactory lf = parent.labelFactory();
    if (lf == null) {
      lf = t.labelFactory();
    }
    if (lf == null) {
      label = new CoreLabel();
      label.setValue(newLabel);
    } else {
      label = lf.newLabel(newLabel);
    }

    // Collect all the children of the parent of the node we care
    // about.  If the child is one of the nodes we care about, or
    // between those two nodes, we add it to a list of inner children.
    // When we reach the second endpoint, we turn that list of inner
    // children into a new node using the newly created label.  All
    // other children are kept in an outer list, with the new node
    // added at the appropriate location.
    List<Tree> children = Generics.newArrayList();
    List<Tree> innerChildren = Generics.newArrayList();
    boolean insideSpan = false;
    for (Tree child : parent.children()) {
      if (child == startChild || child == endChild) {
        if (!insideSpan && startChild != endChild) {
          insideSpan = true;
          innerChildren.add(child);
        } else {
          insideSpan = false;
          innerChildren.add(child);
          Tree newNode = t.treeFactory().newTreeNode(label, innerChildren);
          children.add(newNode);
        }
      } else if (insideSpan) {
        innerChildren.add(child);
      } else {
        children.add(child);
      }
    }

    parent.setChildren(children);

    return t;
  }
}
