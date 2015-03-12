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

  // This is shared with the owning ShiftReduceParser (for now, at least)
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

  /**
   * Train a new model.  This is the method to override for new models
   * such that the ShiftReduceParser will fill in the model.  Given a
   * collection of training trees and some other various information,
   * this should train a new model.  The model is expected to already
   * know about the possible transitions and which states are eligible
   * to be root states via the BaseModel constructor.
   *
   * @param serializedPath Where serialized models go.  If the appropriate options are set, the method can use this to save intermediate models.
   * @param tagger The tagger to use when evaluating devTreebank.  TODO: it would make more sense for ShiftReduceParser to retag the trees first
   * @param random A random number generator to use for any random numbers.  Useful to make sure results can be reproduced.
   * @param binarizedTrainTrees The treebank to train from.
   * @param transitionLists binarizedTrainTrees converted into lists of transitions that will reproduce the same tree.
   * @param devTreebank a set of trees which can be used for dev testing (assuming the user provided a dev treebank)
   * @param nThreads how many threads the model can use for training
   */
  public abstract void trainModel(String serializedPath, Tagger tagger, Random random, List<Tree> binarizedTrainTrees, List<List<Transition>> transitionLists, Treebank devTreebank, int nThreads);

  abstract Set<String> tagSet();

  private static final long serialVersionUID = -175375535849840611L;
}
