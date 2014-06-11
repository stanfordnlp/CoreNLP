package edu.stanford.nlp.parser.shiftreduce;

import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.trees.LabeledScoredTreeNode;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.TreeCoreAnnotations;
import edu.stanford.nlp.util.TreeShapedStack;

/**
 * Transition that makes a binary parse node in a partially finished tree.
 */
public class BinaryTransition implements Transition {
  public final String label;

  /** Which side the head is on */
  public final Side side;

  public enum Side {
    LEFT, RIGHT
  }

  public BinaryTransition(String label, Side side) {
    this.label = label;
    this.side = side;
  }

  /**
   * Legal as long as there are at least two items on the state's stack.
   *
   * TODO: implement other measures of legality
   */
  public boolean isLegal(State state) {
    if (state.finished) {
      return false;
    }
    if (state.stack.size() <= 1) {
      return false;
    }
    return true;
  }

  /**
   * Add a binary node to the existing node on top of the stack
   */
  public State apply(State state) {
    TreeShapedStack<Tree> stack = state.stack;
    Tree right = stack.peek();
    stack = stack.pop();
    Tree left = stack.peek();
    stack = stack.pop();
    
    Tree head;
    switch(side) {
    case LEFT:
      head = left;
      break;
    case RIGHT:
      head = right;
      break;
    default:
      throw new IllegalArgumentException("Unknown side " + side);
    }
    
    if (!(head.label() instanceof CoreLabel)) {
      throw new IllegalArgumentException("Stack should have CoreLabel nodes");
    }
    CoreLabel headLabel = (CoreLabel) head.label();

    CoreLabel production = new CoreLabel();
    production.setValue(label);
    production.set(TreeCoreAnnotations.HeadWordAnnotation.class, headLabel.get(TreeCoreAnnotations.HeadWordAnnotation.class));
    production.set(TreeCoreAnnotations.HeadTagAnnotation.class, headLabel.get(TreeCoreAnnotations.HeadTagAnnotation.class));
    Tree newTop = new LabeledScoredTreeNode(production);
    newTop.addChild(left);
    newTop.addChild(right);

    stack = stack.push(newTop);
    return new State(stack, state.sentence, state.tokenPosition, state.score, false);    
  }

  @Override
  public boolean equals(Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof BinaryTransition)) {
      return false;
    }
    String otherLabel = ((BinaryTransition) o).label;
    Side otherSide = ((BinaryTransition) o).side;
    return otherSide.equals(side) && label.equals(otherLabel);
  }

  @Override
  public int hashCode() {
    switch(side) {
    case LEFT:
      return 97197711 ^ label.hashCode();
    case RIGHT:
      return 97197711 ^ label.hashCode();
    default:
      throw new IllegalArgumentException("Unknown side " + side);
    }
  }

  @Override
  public String toString() {
    switch(side) {
    case LEFT:
      return "LeftBinary(" + label + ")";
    case RIGHT:
      return "RightBinary(" + label + ")";
    default:
      throw new IllegalArgumentException("Unknown side " + side);
    }
  }

  private static final long serialVersionUID = 1;  
}
