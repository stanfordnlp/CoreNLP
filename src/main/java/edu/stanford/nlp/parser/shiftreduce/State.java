package edu.stanford.nlp.parser.shiftreduce;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;

import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.util.Generics;
import edu.stanford.nlp.util.Scored;
import edu.stanford.nlp.util.TreeShapedStack;

/**
 * A class which encodes the current state of the parsing.  This can
 * be used either for direct search or for beam search.
 * <br>
 * Important information which needs to be encoded:
 * <ul>
 *
 * <li>A stack.  This needs to be updatable in O(1) time to keep the
 * parser's run time linear.  This is done by using a linked-list type
 * stack in which new states are created by the <code>push</code>
 * operation. 
 *
 * <li>A queue.  This also needs to be updatable in O(1) time.  This
 * is accomplished by having all the states share the same list of
 * queued items, with different states only changing an index into the
 * queue.
 *
 * <li>The score of the current state.  This is useful in beam searches.
 *
 * <li>Whether or not the current state is "finalized".  If so, the
 * only thing that can be done from now on is to idle.
 *
 * </ul>
 */
public class State implements Scored {
  /**
   * Expects a list of preterminals.  The preterminals should be built
   * with CoreLabels and have HeadWord and HeadTag annotations set.
   */
  public State(List<Tree> sentence) {
    this(new TreeShapedStack<>(), new TreeShapedStack<>(), findSeparators(sentence), sentence, 0, 0.0, false);
  }

  State(TreeShapedStack<Tree> stack, TreeShapedStack<Transition> transitions, TreeMap<Integer, String> separators,
        List<Tree> sentence, int tokenPosition, double score, boolean finished) {
    this.stack = stack;
    this.transitions = transitions;
    this.separators = separators;
    this.sentence = sentence;
    this.tokenPosition = tokenPosition;
    this.score = score;
    this.finished = finished;
  }

  /**
   * The stack of Tree pieces we have already assembled.
   */
  final TreeShapedStack<Tree> stack;

  /**
   * The transition sequence used to get to the current position
   */
  final TreeShapedStack<Transition> transitions;

  /**
   * Used to describe the relative location of separators to the head of a subtree
   */
  public enum HeadPosition { NONE, LEFT, RIGHT, BOTH, HEAD };

  /**
   * A description of where the separators such as ,;:- are in a
   * subtree, relative to the head of the subtree
   */
  final TreeMap<Integer, String> separators;

  Tree getStackNode(int depth) {
    if (depth >= stack.size()) {
      return null;
    }
    TreeShapedStack<Tree> node = stack;
    for (int i = 0; i < depth; ++i) {
      node = node.pop();
    }
    return node.peek();
  }

  Tree getQueueNode(int depth) {
    if (tokenPosition + depth >= sentence.size()) {
      return null;
    }
    return sentence.get(tokenPosition + depth);
  }

  /**
   * Returns the first separator between two nodes or returns null if
   * such a thing does not exist
   */
  String getSeparatorBetween(int right, int left) {
    if (right >= left) {
      throw new AssertionError("Expected right < left");
    }
    return getSeparatorBetween(getStackNode(right), getStackNode(left));
  }

  String getSeparatorBetween(Tree right, Tree left) {
    if (right == null || left == null) {
      return null;
    }
    int leftHead = ShiftReduceUtils.headIndex(left);
    int rightHead = ShiftReduceUtils.headIndex(right);
    Map.Entry<Integer, String> nextSeparator = separators.ceilingEntry(leftHead);
    if (nextSeparator == null || nextSeparator.getKey() > rightHead) {
      return null;
    }
    return nextSeparator.getValue().substring(0, 1);
  }

  /**
   * Returns the separator count between two nodes 
   * (0 if any of the nodes don't exist)
   */
  int getSeparatorCount(int right, int left) {
    if (right >= left) {
      throw new AssertionError("Expected right < left");
    }
    return getSeparatorCount(getStackNode(right), getStackNode(left));
  }

  int getSeparatorCount(Tree right, Tree left) {
    if (right == null || left == null) {
      return 0;
    }
    int leftHead = ShiftReduceUtils.headIndex(left);
    int rightHead = ShiftReduceUtils.headIndex(right);
    Integer nextSeparator = separators.higherKey(leftHead);
    int count = 0;
    while (nextSeparator != null && nextSeparator < rightHead) {
      ++count;
      nextSeparator = separators.higherKey(nextSeparator);
    }
    return count;
  }

  HeadPosition getSeparator(int nodeNum) {
    if (nodeNum >= stack.size()) {
      return null;
    }
    TreeShapedStack<Tree> stack = this.stack;
    for (int i = 0; i < nodeNum; ++i) {
      stack = stack.pop();
    }
    Tree node = stack.peek();
    int head = ShiftReduceUtils.headIndex(node);
    if (separators.get(head) != null) {
      return HeadPosition.HEAD;
    }
    int left = ShiftReduceUtils.leftIndex(node);
    Integer nextLeft = separators.floorKey(head);
    boolean hasLeft = (nextLeft != null && nextLeft >= left);

    int right = ShiftReduceUtils.rightIndex(node);
    Integer nextRight = separators.ceilingKey(head);
    boolean hasRight = (nextRight != null && nextRight <= right);

    if (hasLeft && hasRight) {
      return HeadPosition.BOTH;
    } else if (hasLeft) {
      return HeadPosition.LEFT;
    } else if (hasRight) {
      return HeadPosition.RIGHT;
    } else {
      return HeadPosition.NONE;
    }
  }

  static final Pattern separatorRegex = Pattern.compile("^[,;:-]+$");

  static final char[][] equivalentSeparators = { { '，', ',' },
                                                 { '；', ';' },
                                                 { '：', ':' } };

  static TreeMap<Integer, String> findSeparators(List<Tree> sentence) {
    TreeMap<Integer, String> separators = Generics.newTreeMap();
    for (int index = 0; index < sentence.size(); ++index) {
      Tree leaf = sentence.get(index).children()[0];
      String value = leaf.value();
      for (char[] equivalentSeparator : equivalentSeparators) {
        value = value.replace(equivalentSeparator[0], equivalentSeparator[1]);
      }
      if (separatorRegex.matcher(value).matches()) {
        // TODO: put "value" instead?  Perhaps do this next time we rebuild all models
        separators.put(index, leaf.value());
      }
    }
    return separators;
  }

  /**
   * The words we are parsing.  They need to be tagged before we can
   * parse.  The words are stored as preterminal Trees whose only
   * nodes are the tag node and the word node.
   */
  final List<Tree> sentence;

  /**
   * Essentially, the position in the queue part of the state.  
   * 0 represents that we are at the start of the queue and nothing
   * has been shifted yet.
   */
  final int tokenPosition;

  /**
   * The score of the current state based on the transitions that were
   * used to create it.
   */
  final double score;

  @Override
  public double score() { return score; }

  /**
   * Whether or not processing has finished.  Once that is true, only
   * idle transitions are allowed.
   */ 
  final boolean finished;

  public boolean isFinished() { return finished; }

  public boolean endOfQueue() {
    return tokenPosition == sentence.size();
  }

  @Override
  public String toString() {
    StringBuilder result = new StringBuilder();
    result.append("State summary\n");
    result.append("  Tokens: " + sentence + "\n");
    result.append("  Token position: " + tokenPosition + "\n");
    result.append("  Current stack contents: " + stack.toString("\n") + "\n");
    result.append("  Component transitions: " + transitions + "\n");
    result.append("  Score: " + score + "\n");
    result.append("  " + ((finished) ? "" : "not ") + "finished\n");
    return result.toString();
  }

  /**
   * Whether or not the transitions that built the two states are
   * equal.  Doesn't check anything else.  Useful for training using
   * an agenda, for example, when you know the underlying information
   * such as the words are the same and all you care about checking is
   * the transition sequence
   */
  public boolean areTransitionsEqual(State other) {
    return transitions.equals(other.transitions);
  }
}

