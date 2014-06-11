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
   *
   * TODO: implement other measures of legality, such as not allowing infinite unary transition cycles
   */
  public boolean isLegal(State state) {
    if (state.finished) {
      return false;
    }
    if (state.stack.size() == 0) {
      return false;
    }
    // TODO: check for multiple unary transitions
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
    CoreLabel topLabel = (CoreLabel) top.label();
    CoreLabel production = new CoreLabel();
    production.setValue(label);
    production.set(TreeCoreAnnotations.HeadWordAnnotation.class, topLabel.get(TreeCoreAnnotations.HeadWordAnnotation.class));
    production.set(TreeCoreAnnotations.HeadTagAnnotation.class, topLabel.get(TreeCoreAnnotations.HeadTagAnnotation.class));
    Tree newTop = new LabeledScoredTreeNode(production);
    newTop.addChild(top);

    TreeShapedStack<Tree> stack = state.stack.pop();
    stack = stack.push(newTop);
    return new State(stack, state.sentence, state.tokenPosition, state.score, false);    
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
}

