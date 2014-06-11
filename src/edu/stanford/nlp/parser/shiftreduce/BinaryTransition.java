package edu.stanford.nlp.parser.shiftreduce;

import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.trees.LabeledScoredTreeNode;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.util.TreeShapedStack;

/**
 * Transition that makes a binary parse node in a partially finished tree.
 */
public class BinaryTransition implements Transition {
  public final String label;

  public BinaryTransition(String label) {
    this.label = label;
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

    CoreLabel production = new CoreLabel();
    production.setValue(label);
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
    return label.equals(otherLabel);
  }

  @Override
  public int hashCode() {
    return 97197711 ^ label.hashCode();
  }

  @Override
  public String toString() {
    return "Binary(" + label + ")";
  }
}
