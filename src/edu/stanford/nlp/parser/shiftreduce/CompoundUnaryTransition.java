package edu.stanford.nlp.parser.shiftreduce;

import java.util.Arrays;
import java.util.List;

import edu.stanford.nlp.parser.common.ParserConstraint;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.util.TreeShapedStack;


/**
 * Transition that makes a compound unary parse node in a partially
 * finished tree.  It potentially adds multiple unary layers to the
 * current tree.
 *
 * @author John Bauer
 */
public class CompoundUnaryTransition implements Transition {
  /**
   * labels[0] is the top of the unary chain.
   * A unary chain that results in a ROOT will have labels[0] == ROOT, for example.
   */
  public final String[] labels;

  /** root transitions are illegal in the middle of the tree, naturally */
  public final boolean isRoot;

  public CompoundUnaryTransition(List<String> labels, boolean isRoot) {
    this.labels = new String[labels.size()];
    for (int i = 0; i < labels.size(); ++i) {
      this.labels[i] = labels.get(i);
    }
    this.isRoot = isRoot;
  }

  /**
   * Legal as long as there is at least one item on the state's stack
   * and that item has not already been unary transformed.
   */
  public boolean isLegal(State state, List<ParserConstraint> constraints) {
    if (state.finished) {
      return false;
    }
    if (state.stack.size() == 0) {
      return false;
    }
    final Tree top = state.stack.peek();
    if (top.children().length == 1 && !top.isPreTerminal()) {
      // Disallow unary transitions after we've already had a unary transition
      return false;
    }

    // we allow unary transitions back to self as sometimes that's
    // what is left after hidden notes are discarded
    //if (top.label().value().equals(labels[0])) {
    //  // Disallow unary transitions where the final label doesn't change
    //  return false;
    //}

    // TODO: need to think more about when a unary transition is
    // allowed if the top of the stack is temporary
    if (top.label().value().startsWith("@") && !labels[labels.length - 1].equals(top.label().value().substring(1))) {
      // Disallow a transition if the top is a binarized node and the
      // bottom of the unary transition chain isn't the same type
      return false;
    }
    if (isRoot && (state.stack.size() > 1 || !state.endOfQueue())) {
      return false;
    }

    // Now we check the constraints...
    // Constraints only apply to CompoundUnaryTransitions if the tree
    // is exactly the right size and the tree has not already been
    // constructed to match the constraint.  In that case, we check to
    // see if the candidate transition contains the desired label.
    if (constraints == null) {
      return true;
    }

    for (ParserConstraint constraint : constraints) {
      if (ShiftReduceUtils.leftIndex(top) != constraint.start || ShiftReduceUtils.rightIndex(top) != constraint.end - 1) {
        continue;
      }
      if (constraint.state.matcher(top.value()).matches()) {
        continue;
      }
      boolean found = false;
      for (String label : labels) {
        if (constraint.state.matcher(label).matches()) {
          found = true;
          break;
        }
      }
      if (!found) {
        return false;
      }
    }

    return true;
  }

  /**
   * Add a unary node to the existing node on top of the stack
   */
  public State apply(State state) {
    return apply(state, 0.0);
  }

  /**
   * Add a unary node to the existing node on top of the stack
   */
  public State apply(State state, double scoreDelta) {
    Tree top = state.stack.peek();
    for (int i = labels.length - 1; i >= 0; --i) {
      top = UnaryTransition.addUnaryNode(top, labels[i]);
    }

    TreeShapedStack<Tree> stack = state.stack.pop();
    stack = stack.push(top);
    return new State(stack, state.transitions.push(this), state.separators, state.sentence, state.tokenPosition, state.score + scoreDelta, false);
  }

  @Override
  public boolean equals(Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof CompoundUnaryTransition)) {
      return false;
    }
    String[] otherLabels = ((CompoundUnaryTransition) o).labels;
    return Arrays.equals(labels, otherLabels);
  }

  @Override
  public int hashCode() {
    return 29467607 ^ Arrays.hashCode(labels);
  }

  @Override
  public String toString() {
    return "CompoundUnary" + (isRoot ? "*" : "") + "(" + Arrays.asList(labels).toString() + ")";
  }

  private static final long serialVersionUID = 1;

}
