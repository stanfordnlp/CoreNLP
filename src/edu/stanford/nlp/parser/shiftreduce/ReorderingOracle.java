package edu.stanford.nlp.parser.shiftreduce;

import java.util.List;

/**
 * A second attempt at making an oracle.  Instead of always trying to
 * return the best transition, it simply rearranges the transition
 * lists after an incorrect transition.  If this is not possible,
 * training will be halted as in the case of early update.
 *
 * @author John Bauer
 */
public class ReorderingOracle {
  /**
   * Given a predicted transition and a state, this method rearranges
   * the list of transitions and returns whether or not training can
   * continue.
   */
  static boolean reorder(State state, Transition chosenTransition, List<Transition> transitions) {
    if (transitions.size() == 0) {
      throw new AssertionError();
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
      if (!(goldTransition instanceof BinaryTransition)) {
        // TODO: perhaps there are ways to make it work for some
        // examples which should have been ShiftTransition
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

    return false;
  }
}
