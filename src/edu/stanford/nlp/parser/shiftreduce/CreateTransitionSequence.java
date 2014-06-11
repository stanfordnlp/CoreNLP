package edu.stanford.nlp.parser.shiftreduce;

import java.util.List;

import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.util.Generics;

public class CreateTransitionSequence {
  // static methods only.  
  // we could change this if we wanted to include options.
  private CreateTransitionSequence() {}

  public static List<Transition> createTransitionSequence(Tree tree) {
    List<Transition> transitions = Generics.newArrayList();

    createTransitionSequenceHelper(transitions, tree);
    transitions.add(new FinalizeTransition());
    transitions.add(new IdleTransition());

    return transitions;
  }

  private static void createTransitionSequenceHelper(List<Transition> transitions, Tree tree) {
    if (tree.isLeaf()) {
      // do nothing
    } else if (tree.isPreTerminal()) {
      transitions.add(new ShiftTransition());
    } else if (tree.children().length == 1) {
      createTransitionSequenceHelper(transitions, tree.children()[0]);
      transitions.add(new UnaryTransition(tree.label().value()));
    } else if (tree.children().length == 2) {
      createTransitionSequenceHelper(transitions, tree.children()[0]);
      createTransitionSequenceHelper(transitions, tree.children()[1]);
      // TODO: distinguish left & right heads?
      transitions.add(new BinaryTransition(tree.label().value()));
    } else {
      throw new IllegalArgumentException("Expected a binarized tree");
    }
  }
}
