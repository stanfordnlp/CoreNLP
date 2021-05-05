package edu.stanford.nlp.parser.shiftreduce;

import edu.stanford.nlp.trees.Tree;

import java.util.LinkedList;
import java.util.List;

public class TrainingExample {
  public TrainingExample(Tree binarizedTree, List<Transition> transitions, int numSkips) {
    this.binarizedTree = binarizedTree;
    this.transitions = transitions;
    this.numSkips = numSkips;
  }
  
  Tree binarizedTree;
  List<Transition> transitions;
  int numSkips;

  /**
   * Returns a list of transitions after already skipping the first numSkip transitions.
   * <br>
   * To get the corresponding State, call initialState...
   */
  public List<Transition> trainTransitions() {
    List<Transition> t = new LinkedList<>(this.transitions);
    for (int i = 0; i < this.numSkips; ++i) {
      t.remove(0);
    }
    return t;
  }

  public State initialStateFromGoldTagTree() {
    State state = ShiftReduceParser.initialStateFromTaggedSentence(this.binarizedTree.taggedYield());
    for (int i = 0; i < this.numSkips; ++i) {
      state = this.transitions.get(i).apply(state);
    }
    return state;
  }

}
