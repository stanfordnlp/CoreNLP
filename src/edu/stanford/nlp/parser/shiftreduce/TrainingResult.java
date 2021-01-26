package edu.stanford.nlp.parser.shiftreduce;

import java.util.Collections;
import java.util.List;

import edu.stanford.nlp.util.Pair;

public class TrainingResult {
  public TrainingResult(List<TrainingUpdate> updates, int numCorrect, int numWrong,
                        List<Pair<Integer, Integer>> firstErrors) {
    this.updates = updates;

    this.numCorrect = numCorrect;
    this.numWrong = numWrong;

    this.firstErrors = firstErrors;
  }

  public TrainingResult(List<TrainingUpdate> updates, int numCorrect, int numWrong,
                        Pair<Integer, Integer> firstError) {
    this(updates, numCorrect, numWrong, Collections.singletonList(firstError));
  }

  public TrainingResult(List<TrainingUpdate> updates, int numCorrect, int numWrong) {
    this(updates, numCorrect, numWrong, Collections.emptyList());
  }

  List<TrainingUpdate> updates;
  
  final int numCorrect;
  final int numWrong;

  List<Pair<Integer, Integer>> firstErrors;
}
