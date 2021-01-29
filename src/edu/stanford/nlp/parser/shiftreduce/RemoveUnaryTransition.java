package edu.stanford.nlp.parser.shiftreduce;

import java.util.Arrays;
import java.util.List;

import edu.stanford.nlp.parser.common.ParserConstraint;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.util.TreeShapedStack;

/**
 * Removes the unary transitions from stack position n-1.  Potentially
 * the model has learned enough about the node to fix an early
 * incorrect Unary / CompoundUnary transition.
 */
public class RemoveUnaryTransition implements Transition {
  /** which labels to remove */
  public final String[] labels;

  public RemoveUnaryTransition(String ... labels) {
    this.labels = new String[labels.length];
    for (int i = 0; i < labels.length; ++i) {
      this.labels[i] = labels[i];
    }
  }

  public RemoveUnaryTransition(UnaryTransition t) {
    this(t.label);
  }

  public RemoveUnaryTransition(CompoundUnaryTransition t) {
    this(t.labels);
  }

  /**
   * Whether or not it is legal to apply this transition to this state.
   */
  public boolean isLegal(State state, List<ParserConstraint> constraints) {
    if (state.finished) {
      return false;
    }
    // can't remove unaries from stack element n-1 if there is no such element
    if (state.stack.size() <= 1) {
      return false;
    }

    Tree prevNode = state.stack.pop().peek();

    Tree node = prevNode;
    for (int i = labels.length - 1; i >= 0; --i) {
      // there can only be unary transitions to remove if the top node
      // is a unary
      if (node.children().length > 1) {
        return false;
      }
      // there were no unary transitions if the node in question is a
      // preterminal or leaf
      if (node.isLeaf() || node.isPreTerminal()) {
        return false;
      }
      if (!node.label().value().equals(labels[i])) {
        return false;
      }
      node = node.children()[0];
    }

    if (constraints == null) {
      return true;
    }

    // TODO: test constraints
    final int prevLeft = ShiftReduceUtils.leftIndex(prevNode);
    final int prevRight = ShiftReduceUtils.rightIndex(prevNode);
    Tree prevBottom = prevNode;
    while (prevBottom.children().length == 1 && !prevBottom.children()[0].isPreTerminal()) {
      prevBottom = prevBottom.children()[0];
    }
    for (ParserConstraint constraint : constraints) {
      if (prevLeft == constraint.start && prevRight == constraint.end - 1) {
        // this constraint matched the shape of the tree.  check that
        // it doesn't stop matching if we remove the unary transitions
        if (ShiftReduceUtils.constraintMatchesTreeTop(prevNode, constraint) &&
            !ShiftReduceUtils.constraintMatchesTreeTop(prevBottom, constraint)) {
          return false;
        }
      }
    }

    return true;
  }

  public State apply(State state) {
    return apply(state, 0.0);
  }

  /**
   * Removes the unary transitions from the n-1 node on the stack
   */
  public State apply(State state, double scoreDelta) {
    TreeShapedStack<Tree> stack = state.stack;
    Tree right = stack.peek();
    stack = stack.pop();
    Tree left = stack.peek();
    stack = stack.pop();

    for (int i = 0; i < labels.length; ++i) {
      if (left.children().length != 1 || left.isPreTerminal()) {
        // uh oh... something went wrong.  pretend nothing happened
        break;
      }
      left = left.children()[0];
    }

    stack = stack.push(left);
    stack = stack.push(right);

    // then use the new nodes to make a new state, as if the unaries had never existed...
    state = new State(stack, state.transitions.push(this), state.separators, state.sentence, state.tokenPosition, state.score + scoreDelta, false);
    return state;
  }

  /**
   * How much the stack size changes because of this transition.
   * Multiple processes in these models tries to estimate when the
   * stack will be empty, for example
   */
  public int stackSizeChange() {
    return 0;
  }

  @Override
  public boolean equals(Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof RemoveUnaryTransition)) {
      return false;
    }
    String[] otherLabels = ((RemoveUnaryTransition) o).labels;
    return Arrays.equals(labels, otherLabels);
  }

  @Override
  public int hashCode() {
    return 796253456 ^ Arrays.hashCode(labels); // a random int
  }

  @Override
  public String toString() {
    return "RemoveUnary(" + Arrays.asList(labels).toString() + ")";
  }

  private static final long serialVersionUID = 2517806537914809385L;
}
