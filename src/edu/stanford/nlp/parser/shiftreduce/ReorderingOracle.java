package edu.stanford.nlp.parser.shiftreduce;

import java.util.List;
import java.util.ListIterator;
import java.util.Set;

import edu.stanford.nlp.util.Generics;

/**
 * A second attempt at making an oracle.  Instead of always trying to
 * return the best transition, it simply rearranges the transition
 * lists after an incorrect transition.  If this is not possible,
 * training will be halted as in the case of early update.
 *
 * @author John Bauer
 */
public class ReorderingOracle {
  final ShiftReduceOptions op;

  final Set<String> rootOnlyStates;

  public ReorderingOracle(ShiftReduceOptions op, Set<String> rootOnlyStates) {
    this.op = op;
    this.rootOnlyStates = rootOnlyStates;
  }

  /**
   * Given a predicted transition and a state, this method rearranges
   * the list of transitions and returns whether or not training can
   * continue.
   */
  boolean reorder(State state, Transition chosenTransition, List<Transition> transitions) {
    if (transitions.size() == 0) {
      throw new AssertionError();
    }

    if (chosenTransition == null) {
      return false;
    }

    if (!chosenTransition.isLegal(state, null)) {
      return false;
    }

    Transition goldTransition = transitions.get(0);

    // If the transition is gold, we are already satisfied.
    if (chosenTransition.equals(goldTransition)) {
      transitions.remove(0);
      return true;
    }

    // If the transition should have been a Unary/CompoundUnary
    // transition and it was something else or a different Unary
    // transition, see if the transition sequence can be continued
    // after skipping past the unary
    if ((goldTransition instanceof UnaryTransition) || (goldTransition instanceof CompoundUnaryTransition)) {
      transitions.remove(0);
      return reorder(state, chosenTransition, transitions);
    }

    // If the chosen transition was an incorrect Unary/CompoundUnary
    // transition, skip past it and hope to continue the gold
    // transition sequence.  However, if we have Unary/CompoundUnary
    // in a row, we have to return false to prevent loops.
    // Also, if the state stack size is 0, can't keep going
    if ((chosenTransition instanceof UnaryTransition) || (chosenTransition instanceof CompoundUnaryTransition)) {
      if (state.transitions.size() > 0) {
        Transition previous = state.transitions.peek();
        if ((previous instanceof UnaryTransition) || (previous instanceof CompoundUnaryTransition)) {
          return false;
        }
      }
      if (state.stack.size() == 0) {
        return false;
      }
      return true;
    }

    if (chosenTransition instanceof BinaryTransition) {
      if (state.stack.size() < 2) {
        return false;
      }

      if (goldTransition instanceof ShiftTransition) {
        // Helps, but adds quite a bit of size to the model and only helps a tiny bit
        return op.trainOptions().oracleBinaryToShift && reorderIncorrectBinaryTransition(transitions);
      }

      if (!(goldTransition instanceof BinaryTransition)) {
        return false;
      }

      BinaryTransition chosenBinary = (BinaryTransition) chosenTransition;
      BinaryTransition goldBinary = (BinaryTransition) goldTransition;
      if (chosenBinary.isBinarized()) {
        // Binarized labels only work (for now, at least) if the side
        // is wrong but the label itself is correct
        if (goldBinary.isBinarized() && chosenBinary.label.equals(goldBinary.label)) {
          transitions.remove(0);
          return true;
        } else {
          return false;
        }
      }

      // In all other binarized situations, essentially what has
      // happened is we added a bracket error, but future brackets can
      // still wind up being correct
      transitions.remove(0);
      return true;
    }

    if ((chosenTransition instanceof ShiftTransition) && (goldTransition instanceof BinaryTransition)) {
      // can't shift at the end of the queue
      if (state.endOfQueue()) {
        return false;
      }

      // doesn't help, sadly
      BinaryTransition goldBinary = (BinaryTransition) goldTransition;
      if (!goldBinary.isBinarized()) {
        return op.trainOptions().oracleShiftToBinary && reorderIncorrectShiftTransition(transitions);
      }
    }

    return false;
  }

  /**
   * We wanted to shift and build up a new subtree, call it C, but
   * instead we combined subtrees A and B into A+B.  The eventual goal
   * would have been to combine B+C and have the stack be A / B+C
   * <br>
   * What we do is we keep all of the transitions that build up C, so
   * that the stack is A+B / C, and the next transition was the one
   * that should have combined B+C
   * <br>
   * We then drop the transition that would have combined B+C
   * <br>
   * At that point, either A+B and C will be combined, which means A+B / C
   * was the only error, or we started building D and will continue to
   * produce more errors after that.  However, there's no better
   * solution for that situation anyway, unless we could decide that
   * A+B+C / D is better than A+B / C+D as a replacement for A / B+C+D
   */
  boolean reorderIncorrectBinaryTransition(List<Transition> transitions) {
    int shiftCount = 0;
    ListIterator<Transition> cursor = transitions.listIterator();
    do {
      if (!cursor.hasNext()) {
        return false;
      }
      Transition next = cursor.next();
      if (next instanceof ShiftTransition) {
        ++shiftCount;
      } else if (next instanceof BinaryTransition) {
        --shiftCount;
        if (shiftCount <= 0) {
          cursor.remove();
        }
      }
    } while (shiftCount > 0);

    if (!cursor.hasNext()) {
      return false;
    }
    Transition next = cursor.next();
    while ((next instanceof UnaryTransition) || (next instanceof CompoundUnaryTransition)) {
      cursor.remove();
      if (!cursor.hasNext()) {
        return false;
      }
      next = cursor.next();
    }

    // At this point, the rest of the transition sequence should suffice
    return true;
  }

  /**
   * In this case, we are starting to build a new subtree when instead
   * we should have been combining existing trees.  What we can do is
   * find the transitions that build up the next subtree in the gold
   * transition list, figure out how it gets applied to a
   * BinaryTransition, and make that the next BinaryTransition we
   * perform after finishing the subtree.  If there are multiple
   * BinaryTransitions in a row, we ignore any associated
   * UnaryTransitions (unfixable) and try to transition to the final
   * state.  The assumption is that we can't do anything about the
   * incorrect subtrees any more, so we skip them all.
   *<br>
   * Sadly, this does not seem to help - the parser gets worse when it
   * learns these states
   */
  boolean reorderIncorrectShiftTransition(List<Transition> transitions) {
    List<BinaryTransition> leftoverBinary = Generics.newArrayList();
    while (transitions.size() > 0) {
      Transition head = transitions.remove(0);
      if (head instanceof ShiftTransition) {
        break;
      }

      if (head instanceof BinaryTransition) {
        leftoverBinary.add((BinaryTransition) head);
      } 
    }
    if (transitions.size() == 0 || leftoverBinary.size() == 0) {
      // honestly this is an error we should probably just throw
      return false;
    }

    int shiftCount = 0;
    ListIterator<Transition> cursor = transitions.listIterator();
    BinaryTransition lastBinary = null;
    while (cursor.hasNext() && shiftCount >= 0) {
      Transition next = cursor.next();
      if (next instanceof ShiftTransition) {
        ++shiftCount;
      } else if (next instanceof BinaryTransition) {
        --shiftCount;
        if (shiftCount < 0) {
          lastBinary = (BinaryTransition) next;
          cursor.remove();
        }
      }
    }
    if (!cursor.hasNext() || lastBinary == null) {
      // once again, an error.  even if the sequence of tree altering
      // gold transitions ends with a BinaryTransition, there should
      // be a FinalizeTransition after that
      return false;
    }

    String label = lastBinary.label;
    if (lastBinary.isBinarized()) {
      label = label.substring(1);
    }
    if (lastBinary.side == BinaryTransition.Side.RIGHT) {
      // When we finally transition all the binary transitions, we
      // will want to have the new node be the right head.  Therefore,
      // we add a bunch of temporary binary transitions with a right
      // head, ending up with a binary transition with a right head
      for (int i = 0; i < leftoverBinary.size(); ++i) {
        cursor.add(new BinaryTransition("@" + label, BinaryTransition.Side.RIGHT, rootOnlyStates.contains(label)));
      }
      // use lastBinary.label in case the last transition is temporary
      cursor.add(new BinaryTransition(lastBinary.label, BinaryTransition.Side.RIGHT, rootOnlyStates.contains(label)));
    } else {
      cursor.add(new BinaryTransition("@" + label, BinaryTransition.Side.LEFT, rootOnlyStates.contains(label)));
      for (int i = 0; i < leftoverBinary.size() - 1; ++i) {
        cursor.add(new BinaryTransition("@" + label, leftoverBinary.get(i).side, rootOnlyStates.contains(label)));
      }
      cursor.add(new BinaryTransition(lastBinary.label, leftoverBinary.get(leftoverBinary.size() - 1).side, rootOnlyStates.contains(lastBinary.label)));
    }
    return true;
  }
}
