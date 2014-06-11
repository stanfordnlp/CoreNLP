package edu.stanford.nlp.parser.shiftreduce;

import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.trees.LabeledScoredTreeNode;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.TreeCoreAnnotations;
import edu.stanford.nlp.util.TreeShapedStack;

/**
 * Transition that makes a unary parse node in a partially finished tree.
 */
public class UnaryTransition implements Transition {
  public final String label;

  public UnaryTransition(String label) {
    this.label = label;
  }

  /**
   * Legal as long as there is at least one item on the state's stack.
   */
  public boolean isLegal(State state) {
    if (state.finished) {
      return false;
    }
    if (state.stack.size() == 0) {
      return false;
    }
    Tree top = state.stack.peek();
    if (top.label().value().equals(label)) {
      // Disallow unary transitions where the label doesn't change
      return false;
    }
    if (top.label().value().startsWith("@") && !label.equals(top.label().value().substring(1))) {
      return false;
    }
    if (top.children().length == 1) {
      Tree child = top.children()[0];
      if (child.children().length == 1) {
        Tree grandChild = child.children()[0];
        if (grandChild.children().length == 1) {
          // Three consecutive unary trees.  Not legal to keep adding unaries.
          // TODO: do preterminals count in that equation?
          return false;
        }
      }
    }
    return true;
  }

  /**
   * Add a unary node to the existing node on top of the stack
   */
  public State apply(State state) {
    Tree top = state.stack.peek();
    if (!(top.label() instanceof CoreLabel)) {
      throw new IllegalArgumentException("Stack should have CoreLabel nodes");
    }
    CoreLabel headLabel = (CoreLabel) top.label();
    CoreLabel production = new CoreLabel();
    production.setValue(label);
    production.set(TreeCoreAnnotations.HeadWordAnnotation.class, headLabel.get(TreeCoreAnnotations.HeadWordAnnotation.class));
    production.set(TreeCoreAnnotations.HeadTagAnnotation.class, headLabel.get(TreeCoreAnnotations.HeadTagAnnotation.class));
    Tree newTop = new LabeledScoredTreeNode(production);
    newTop.addChild(top);

    TreeShapedStack<Tree> stack = state.stack.pop();
    stack = stack.push(newTop);
    return new State(stack, state.transitions.push(this), state.sentence, state.tokenPosition, state.score, false);    
  }

  @Override
  public boolean equals(Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof UnaryTransition)) {
      return false;
    }
    String otherLabel = ((UnaryTransition) o).label;
    return label.equals(otherLabel);
  }

  @Override
  public int hashCode() {
    return 29467607 ^ label.hashCode();
  }

  @Override
  public String toString() {
    return "Unary(" + label + ")";
  }

  private static final long serialVersionUID = 1;  
}

