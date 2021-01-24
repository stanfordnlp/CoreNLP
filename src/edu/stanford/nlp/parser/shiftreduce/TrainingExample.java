package edu.stanford.nlp.parser.shiftreduce;

import edu.stanford.nlp.trees.Tree;

import java.util.LinkedList;
import java.util.List;

public class TrainingExample {
  public TrainingExample(Tree binarizedTree, List<Transition> transitions) {
    this.binarizedTree = binarizedTree;
    this.transitions = transitions;
  }
  
  Tree binarizedTree;
  List<Transition> transitions;
}
