package edu.stanford.nlp.parser.shiftreduce;

import java.util.List;

import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.parser.common.ParserConstraint;
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
   */
  public boolean isLegal(State state, List<ParserConstraint> constraints) {
    // some of these quotes come directly from Zhang Clark 09
    if (state.finished) {
      return false;
    }
    if (state.stack.size() <= 1) {
      return false;
    }
    // at least one of the two nodes on top of stack must be non-temporary
    if (ShiftReduceUtils.isTemporary(state.stack.peek()) && ShiftReduceUtils.isTemporary(state.stack.pop().peek())) {
      return false;
    }
    if (ShiftReduceUtils.isTemporary(state.stack.peek())) {
      if (side == Side.LEFT) {
        return false;
      }
      if (!ShiftReduceUtils.isEquivalentCategory(label, state.stack.peek().value())) {
        return false;
      }
    }
    if (ShiftReduceUtils.isTemporary(state.stack.pop().peek())) {
      if (side == Side.RIGHT) {
        return false;
      }
      if (!ShiftReduceUtils.isEquivalentCategory(label, state.stack.pop().peek().value())) {
        return false;
      }
    }
    // don't allow binarized labels if it makes the state have a stack
    // of size 1 and a queue of size 0
    if (state.stack.size() == 2 && isBinarized() && state.endOfQueue()) {
      return false;
    }
    // when the stack contains only two nodes, temporary resulting
    // nodes from binary reduce must be left-headed
    if (state.stack.size() == 2 && isBinarized() && side == Side.RIGHT) {
      return false;
    }
    // when the queue is empty and the stack contains more than two
    // nodes, with the third node from the top being temporary, binary
    // reduce can be applied only if the resulting node is non-temporary
    if (state.endOfQueue() && state.stack.size() > 2 && ShiftReduceUtils.isTemporary(state.stack.pop().pop().peek()) && isBinarized()) {
      return false;
    }
    // when the stack contains more than two nodes, with the third
    // node from the top being temporary, temporary resulting nodes
    // from binary reduce must be left-headed
    if (state.stack.size() > 2 && ShiftReduceUtils.isTemporary(state.stack.pop().pop().peek()) && isBinarized() && side == Side.RIGHT) {
      return false;
    }

    if (constraints == null) {
      return true;
    }

    final Tree top = state.stack.peek();
    final int leftTop = ShiftReduceUtils.leftIndex(top);
    final int rightTop = ShiftReduceUtils.rightIndex(top);
    final Tree next = state.stack.pop().peek();
    final int leftNext = ShiftReduceUtils.leftIndex(next);
    // The binary transitions are affected by constraints in the
    // following two circumstances.  If a transition would cross the
    // left boundary of a constraint, that is illegal.  If the
    // transition is exactly the right size for the constraint and
    // would make a temporary node, that is also illegal.
    for (ParserConstraint constraint : constraints) {
      if (leftTop == constraint.start) {
        // can't binary reduce away from a tree which doesn't match a constraint
        if (rightTop == constraint.end - 1) {
          if (!ShiftReduceUtils.constraintMatchesTreeTop(top, constraint)) {
            return false;
          } else {
            continue;
          }
        } else if (rightTop >= constraint.end) {
          continue;
        } else {
          // can't binary reduce if it would make the tree cross the left boundary
          return false;
        }
      }
      // top element is further left than the constraint, so
      // there's no harm to be done by binary reduce
      if (leftTop < constraint.start) {
        continue;
      }
      // top element is past the end of the constraint, so it must already be satisfied
      if (leftTop >= constraint.end) {
        continue;
      }
      // now leftTop > constraint.start and < constraint.end, eg inside the constraint
      // the next case is no good because it crosses the boundary
      if (leftNext < constraint.start) {
        return false;
      }
      if (leftNext > constraint.start) {
        continue;
      }
      // can't transition to a binarized node when there's a constraint that matches.
      if (rightTop == constraint.end - 1 && isBinarized()) {
        return false;
      }
    }

    return true;
  }

  public boolean isBinarized() {
    return (label.charAt(0) == '@');
  }

  /**
   * Add a binary node to the existing node on top of the stack
   */
  public State apply(State state) {
    return apply(state, 0.0);
  }

  /**
   * Add a binary node to the existing node on top of the stack
   */
  public State apply(State state, double scoreDelta) {
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
    production.set(TreeCoreAnnotations.HeadWordLabelAnnotation.class, headLabel.get(TreeCoreAnnotations.HeadWordLabelAnnotation.class));
    production.set(TreeCoreAnnotations.HeadTagLabelAnnotation.class, headLabel.get(TreeCoreAnnotations.HeadTagLabelAnnotation.class));
    Tree newTop = new LabeledScoredTreeNode(production);
    newTop.addChild(left);
    newTop.addChild(right);

    stack = stack.push(newTop);

    return new State(stack, state.transitions.push(this), state.separators, state.sentence, state.tokenPosition, state.score + scoreDelta, false);    
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
    // TODO: fix the hashcode for the side?  would require rebuilding all models
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
