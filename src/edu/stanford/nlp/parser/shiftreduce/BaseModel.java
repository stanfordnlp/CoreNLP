package edu.stanford.nlp.parser.shiftreduce;

import java.io.Serializable;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import edu.stanford.nlp.parser.common.ParserConstraint;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.util.Index;
import edu.stanford.nlp.util.ScoredObject;

public abstract class BaseModel implements Serializable {
  final ShiftReduceOptions op;

  // This is shared with the owning ShiftReduceParser (for now, at least)
  final Index<Transition> transitionIndex;
  final Set<String> knownStates;
  final Set<String> rootStates;
  final Set<String> rootOnlyStates;

  public BaseModel(ShiftReduceOptions op, Index<Transition> transitionIndex,
                   Set<String> knownStates, Set<String> rootStates, Set<String> rootOnlyStates) {
    this.transitionIndex = transitionIndex;
    this.op = op;

    this.knownStates = knownStates;
    this.rootStates = rootStates;
    this.rootOnlyStates = rootOnlyStates;

  }

  public BaseModel(BaseModel other) {
    this.op = other.op;

    this.transitionIndex = other.transitionIndex;
    this.knownStates = other.knownStates;
    this.rootStates = other.rootStates;
    this.rootOnlyStates = other.rootOnlyStates;
  }

  /**
   * Returns a transition which might not even be part of the model,
   * but will hopefully allow progress in an otherwise stuck parse
   *
   * TODO: perhaps we want to create an EmergencyTransition class
   * which indicates that something has gone wrong
   */
  public Transition findEmergencyTransition(State state, List<ParserConstraint> constraints) {
    if (state.stack.size() == 0) {
      return null;
    }

    // See if there is a constraint whose boundaries match the end
    // points of the top node on the stack.  If so, we can apply a
    // UnaryTransition / CompoundUnaryTransition if that would solve
    // the constraint
    if (constraints != null) {
      final Tree top = state.stack.peek();
      for (ParserConstraint constraint : constraints) {
        if (ShiftReduceUtils.leftIndex(top) != constraint.start || ShiftReduceUtils.rightIndex(top) != constraint.end - 1) {
          continue;
        }
        if (ShiftReduceUtils.constraintMatchesTreeTop(top, constraint)) {
          continue;
        }
        // found an unmatched constraint that can be fixed with a unary transition
        // now we need to find a matching state for the transition
        for (String label : knownStates) {
          if (constraint.state.matcher(label).matches()) {
            return ((op.compoundUnaries) ?
                    new CompoundUnaryTransition(Collections.singletonList(label), false) :
                    new UnaryTransition(label, false));
          }
        }
      }
    }

    if (ShiftReduceUtils.isTemporary(state.stack.peek()) &&
        (state.stack.size() == 1 || ShiftReduceUtils.isTemporary(state.stack.pop().peek()))) {
      return ((op.compoundUnaries) ?
              new CompoundUnaryTransition(Collections.singletonList(state.stack.peek().value().substring(1)), false) :
              new UnaryTransition(state.stack.peek().value().substring(1), false));
    }

    if (state.stack.size() == 1 && state.tokenPosition >= state.sentence.size()) {
      // either need to finalize or transition to a root state
      if (!rootStates.contains(state.stack.peek().value())) {
        String root = rootStates.iterator().next();
        return ((op.compoundUnaries) ?
                new CompoundUnaryTransition(Collections.singletonList(root), false) :
                new UnaryTransition(root, false));
      }
    }

    if (state.stack.size() == 1) {
      return null;
    }

    if (ShiftReduceUtils.isTemporary(state.stack.peek())) {
      return new BinaryTransition(state.stack.peek().value().substring(1), BinaryTransition.Side.RIGHT);
    }

    if (ShiftReduceUtils.isTemporary(state.stack.pop().peek())) {
      return new BinaryTransition(state.stack.pop().peek().value().substring(1), BinaryTransition.Side.LEFT);
    }

    return null;
  }


  public abstract Collection<ScoredObject<Integer>> findHighestScoringTransitions(State state, boolean requireLegal, int numTransitions, List<ParserConstraint> constraints);

}
