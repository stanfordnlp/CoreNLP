package edu.stanford.nlp.parser.shiftreduce;

import java.io.Serializable;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.Set;

import edu.stanford.nlp.parser.common.ParserConstraint;
import edu.stanford.nlp.tagger.common.Tagger;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.Treebank;
import edu.stanford.nlp.util.Index;
import edu.stanford.nlp.util.ScoredObject;

public abstract class BaseModel implements Serializable {

  final ShiftReduceOptions op;

  final Index<Transition> transitionIndex;
  final Set<String> knownStates; // the set of goal categories of a reduce = the set of phrasal categories in a grammar
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

  /*
  // This chunk of code can read a model where the BinaryTransitions
  // aren't protected from going to root in the middle of a tree and
  // fix those models so that can't happen
  private void readObject(ObjectInputStream in)
    throws IOException, ClassNotFoundException
  {
    ObjectInputStream.GetField fields = in.readFields();
    this.op = ErasureUtils.uncheckedCast(fields.get("op", null));

    Index<Transition> badIndex = ErasureUtils.uncheckedCast(fields.get("transitionIndex", null));
    this.knownStates = ErasureUtils.uncheckedCast(fields.get("knownStates", null));
    this.rootStates = ErasureUtils.uncheckedCast(fields.get("rootStates", null));
    this.rootOnlyStates = ErasureUtils.uncheckedCast(fields.get("rootOnlyStates", null));
    this.transitionIndex = new HashIndex<>();
    for (Transition t : badIndex) {
      if (!(t instanceof BinaryTransition)) {
        transitionIndex.add(t);
        continue;
      }
      BinaryTransition bt = (BinaryTransition) t;
      boolean isRoot = ((bt.isBinarized() && rootOnlyStates.contains(bt.label.substring(1))) ||
                        rootOnlyStates.contains(bt.label));
      if (isRoot) {
        System.out.println("Updating a transition: " + bt);
      }
      transitionIndex.add(new BinaryTransition(bt.label, bt.side, isRoot));
    }
  }
  */
  
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

    // TODO: don't emergency transition away from ROOT.
    // Emergency transition to the ROOT instead
    // TODO: all of the above transitions need to be checked to make
    // sure they aren't creating an emergency transition to a ROOT in
    // the middle of a tree
    if (ShiftReduceUtils.isTemporary(state.stack.peek())) {
      String newState = state.stack.peek().value().substring(1);
      boolean isRoot = rootOnlyStates.contains(newState);
      if (isRoot && state.endOfQueue() && state.stack.size() == 2) {
        newState = state.stack.peek().value();
      }
      return new BinaryTransition(newState, BinaryTransition.Side.RIGHT, isRoot);
    }

    if (ShiftReduceUtils.isTemporary(state.stack.pop().peek())) {
      String newState = state.stack.pop().peek().value().substring(1);
      boolean isRoot = rootOnlyStates.contains(newState);
      if (isRoot && state.endOfQueue() && state.stack.size() == 2) {
        newState = state.stack.pop().peek().value();
      }
      return new BinaryTransition(newState, BinaryTransition.Side.LEFT, isRoot);
    }

    return null;
  }


  public abstract Collection<ScoredObject<Integer>> findHighestScoringTransitions(State state, boolean requireLegal, int numTransitions, List<ParserConstraint> constraints);

  abstract Set<String> tagSet();

  private static final long serialVersionUID = -175375535849840611L;
}
