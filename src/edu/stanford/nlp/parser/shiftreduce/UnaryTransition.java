package edu.stanford.nlp.parser.shiftreduce;

import java.util.Collections;
import java.util.List;
import java.util.Set;

import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.parser.common.ParserConstraint;
import edu.stanford.nlp.trees.LabeledScoredTreeNode;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.TreeCoreAnnotations;
import edu.stanford.nlp.util.TreeShapedStack;

/**
 * Transition that makes a unary parse node in a partially finished tree.
 */
public class UnaryTransition implements Transition {
  public final String label;

  /** root transitions are illegal in the middle of the tree, naturally */
  public final boolean isRoot;

  /** do not unary transition from punctuation tags */
  public final Set<String> punctuationTags;

  public UnaryTransition(String label, boolean isRoot, Set<String> punctuationTags) {
    this.label = label;
    this.isRoot = isRoot;
    this.punctuationTags = (punctuationTags == null) ? Collections.emptySet() : punctuationTags;
  }

  /**
   * Legal as long as there is at least one item on the state's stack.
   * <br>
   * However, transitioning to a root state is not allowed if we are
   * not the last item in the parse.
   * <br>
   * Also, unary transition from a punctuation node is disallowed.
   */
  public boolean isLegal(State state, List<ParserConstraint> constraints) {
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
    if (isRoot && (state.stack.size() > 1 || !state.endOfQueue())) {
      return false;
    }
    if (punctuationTags.contains(top.label().value()) &&
        (state.stack.size() > 1 || !state.endOfQueue())) {
      // if the state stack size is 1 and we are at the end of the
      // queue, then we should allow unary transitions such as to the
      // ROOT state
      return false;
    }

    // UnaryTransition actually doesn't care about the constraints.
    // If the constraint winds up unsatisfied, we'll get stuck and
    // have to do an "emergency transition" to fix the situation.
    return true;
  }

  /**
   * Add a unary node to the existing node on top of the stack
   */
  public State apply(State state) {
    return apply(state, 0.0);
  }

  static Tree addUnaryNode(Tree top, String label) {
    if (!(top.label() instanceof CoreLabel)) {
      throw new IllegalArgumentException("Stack should have CoreLabel nodes");
    }
    Tree newTop = createNode(top, label, top);
    return newTop;
  }

  static Tree createNode(Tree top, String label, Tree ... children) {
    CoreLabel headLabel = (CoreLabel) top.label();
    CoreLabel production = new CoreLabel();
    production.setValue(label);
    production.set(TreeCoreAnnotations.HeadWordLabelAnnotation.class, headLabel.get(TreeCoreAnnotations.HeadWordLabelAnnotation.class));
    production.set(TreeCoreAnnotations.HeadTagLabelAnnotation.class, headLabel.get(TreeCoreAnnotations.HeadTagLabelAnnotation.class));
    Tree newTop = new LabeledScoredTreeNode(production);
    for (Tree child : children) {
      newTop.addChild(child);
    }
    return newTop;
  }

  /**
   * Add a unary node to the existing node on top of the stack
   */
  public State apply(State state, double scoreDelta) {
    Tree top = state.stack.peek();
    Tree newTop = addUnaryNode(top, label);

    TreeShapedStack<Tree> stack = state.stack.pop();
    stack = stack.push(newTop);
    return new State(stack, state.transitions.push(this), state.separators, state.sentence, state.tokenPosition, state.score + scoreDelta, false);    
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
    return "Unary" + (isRoot ? "*" : "") + "(" + label + ")";
  }

  private static final long serialVersionUID = 1;  
}

