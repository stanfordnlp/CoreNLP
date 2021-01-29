package edu.stanford.nlp.parser.shiftreduce;

import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;
import java.util.Set;

import edu.stanford.nlp.util.Generics;
import edu.stanford.nlp.util.Index;

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

  final Index<Transition> transitionIndex;

  public ReorderingOracle(ShiftReduceOptions op, Set<String> rootOnlyStates, Index<Transition> transitionIndex) {
    this.op = op;
    this.rootOnlyStates = rootOnlyStates;
    this.transitionIndex = transitionIndex;
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

    if (goldTransition instanceof RemoveUnaryTransition) {
      // There are four basic possibilities here.
      // - If a different RemoveUnaryTransition was chosen, we can
      //   keep going despite this
      // - If a Unary was chosen, we can put the RemoveUnary next.
      //   If this Unary is supposed to be there, we can remove it
      //   from the list
      // - If a Binary was chosen, we skip the RemoveUnary and
      //   keep going as if the gold transition is the next
      //   transition in the sequence
      // - If a Shift was chosen, and there was supposed to be a Shift
      //   after the RemoveUnary, we keep going until an equal number
      //   of Binary transitions, then put the RemoveUnary
      if (chosenTransition instanceof RemoveUnaryTransition) {
        transitions.remove(0);
        return true;
      }
      if ((chosenTransition instanceof UnaryTransition) || (chosenTransition instanceof CompoundUnaryTransition)) {
        // avoid infinite loops
        if (state.transitions.size() > 0) {
          Transition previous = state.transitions.peek();
          if ((previous instanceof UnaryTransition) || (previous instanceof CompoundUnaryTransition)) {
            return false;
          }
        }
        // check to see if transitions[1] is the same unary.  if so,
        // we can remove it.  if it is a different unary, we have to
        // abort as we do not want multiple unary in a row
        // this case will probably fail anyway if it continues to
        // choose the wrong transition here
        if (transitions.size() > 1) {
          Transition next = transitions.get(1);
          if ((next instanceof UnaryTransition) || (next instanceof CompoundUnaryTransition)) {
            if (next.equals(chosenTransition)) {
              transitions.remove(1);
            } else {
              return false;
            }
          } else {
            return true;
          }
        }
      }
      if (chosenTransition instanceof BinaryTransition) {
        // this covered up the potential RemoveUnary.  We skip it and
        // assess the situation where the BinaryUnary was chosen
        // instead of some other potential transition.
        transitions.remove(0);
        return reorder(state, chosenTransition, transitions);
      }
      if (chosenTransition instanceof ShiftTransition) {
        // In this case, what we can do is eat transitions until we
        // are back to the current location on the stack, then try
        // again.
        // Note that this strategy only works if we were actually
        // supposed to Shift after doing the RemoveUnary.
        // TODO: technically you can also skip Unary
        transitions.remove(0);
        if (!(transitions.get(0) instanceof ShiftTransition)) {
          return false;
        }
        // note that since we've done the ShiftTransition early, we
        // remove the actual Shift from when it was supposed to happen
        transitions.remove(0);
        int shiftCount = 1;
        ListIterator<Transition> cursor = transitions.listIterator();
        Transition next = null;
        while (cursor.hasNext()) {
          next = cursor.next();
          if (next instanceof ShiftTransition) {
            ++shiftCount;
          } else if (next instanceof BinaryTransition) {
            --shiftCount;
            if (shiftCount < 1) {
              break;
            }
          }
        }
        cursor.previous();
        cursor.add(goldTransition);
        return true;
      }
      return false;
    }

    // If the chosen transition was an incorrect Unary/CompoundUnary
    // transition, skip past it and hope to continue the gold
    // transition sequence.  However, if we have Unary/CompoundUnary
    // in a row, we have to return false to prevent loops.
    // Also, if the state stack size is 0, can't keep going
    if ((chosenTransition instanceof UnaryTransition) || (chosenTransition instanceof CompoundUnaryTransition)) {
      if (state.stack.size() == 0) {
        return false;
      }
      if (state.transitions.size() > 0) {
        Transition previous = state.transitions.peek();
        if ((previous instanceof UnaryTransition) || (previous instanceof CompoundUnaryTransition)) {
          return false;
        }
      }
      // one possible fix is that if there is a shift, followed by
      // some number of binary transitions, and the model is being
      // built with a RemoveUnaryTransition, we can try to add that
      // and fix the Unary error at that point
      // List<Transition> original = new ArrayList<>(transitions);
      boolean success = addRemoveUnary(transitions, chosenTransition);
      // if (success) {
      //   System.out.println("\n  Chose " + chosenTransition + ", gold was " + goldTransition + ", Oracle got: " + original +
      //                      "\n  Currently executed transitions: " + state.transitions + "\n    Transformed into: " + transitions);
      // } else {
      //   System.out.println("\n  Chose " + chosenTransition + ", gold was " + goldTransition + ", Oracle got: " + original +
      //                      "\n  Currently executed transitions: " + state.transitions + "\n    Failed in addRemoveUnary");
      // }
      return success;
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

    // TODO: fix this for the BinaryRemoveUnaryTransition...
    // for that matter, fix it in general
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

  boolean addRemoveUnary(List<Transition> transitions, Transition chosenTransition) {
    if (!(transitions.get(0) instanceof ShiftTransition))
      return true;
    final Transition removeUnary;
    if (chosenTransition instanceof UnaryTransition) {
      removeUnary = new RemoveUnaryTransition((UnaryTransition) chosenTransition);
    } else if (chosenTransition instanceof CompoundUnaryTransition) {
      removeUnary = new RemoveUnaryTransition((CompoundUnaryTransition) chosenTransition);
    } else {
      throw new AssertionError("Should not reach here with anything other than a (Compound)UnaryTransition");
    }
    if (!transitionIndex.contains(removeUnary)) {
      // this model doesn't know how to use this RemoveUnaryTransition
      return true;
    }

    int shiftCount = 0;
    ListIterator<Transition> cursor = transitions.listIterator();

    Transition next = null;
    while (cursor.hasNext()) {
      next = cursor.next();
      if (next instanceof ShiftTransition) {
        ++shiftCount;
      } else if (next instanceof BinaryTransition) {
        --shiftCount;
        if (shiftCount <= 1) {
          break;
        }
      }
    }
    // actually this shouldn't happen.  the transition sequence must be broken
    if (!cursor.hasNext()) {
      throw new AssertionError("went through the whole transition sequence without closing the current Shift");
    }

    if (next == null) {
      throw new AssertionError("there was no transition sequence... this should be impossible");
    }

    if (!(next instanceof BinaryTransition)) {
      throw new AssertionError("should have only decreased shiftCount for a BinaryTransition, instead had " + next);
    }

    // if shiftCount == 0, then we hit a BinaryTransition immediately
    // after shifting.  Perhaps the model will get enough information
    // for this new position to recognize it made a mistake...
    if (shiftCount == 0) {
      Transition prev = cursor.previous();
      // TODO: scroll backwards to before a Unary or CompoundUnary, if necessary??
      // while ((prev instanceof UnaryTransition) || (prev instanceof CompoundUnaryTransition)) {
      //   prev = cursor.previous();
      // }
      cursor.add(removeUnary);
    } else {
      // the previous Binary put us in the following situation: there
      // is now one node on the stack after the Unary we should not
      // have made, and there is some new information from the new
      // structure.  Perhaps here the model can learn to remove.
      // Note that this remove has to occur after the BinaryTransition
      cursor.add(removeUnary);
    }

    //System.out.println("  Updated to: " + transitions);

    return true;
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
   * <br>
   * TODO: add a transition which allows rearranging A+B / C into
   * A / B+C?
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
      shiftCount = shiftCount + next.stackSizeChange();
      if (shiftCount < 0) {
        // TODO: look for potential alternative Binary types
        lastBinary = (BinaryTransition) next;
        cursor.remove();
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
