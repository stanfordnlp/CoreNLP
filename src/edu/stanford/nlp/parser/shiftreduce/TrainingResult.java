package edu.stanford.nlp.parser.shiftreduce;

import java.util.Collections;
import java.util.List;

import edu.stanford.nlp.util.Pair;

public class TrainingResult {
  public TrainingResult(List<TrainingUpdate> updates, int numCorrect, int numWrong,
                        List<Pair<Integer, Integer>> firstErrors,
                        int reorderSuccess, int reorderFail) {
    this.updates = updates;

    this.numCorrect = numCorrect;
    this.numWrong = numWrong;

    this.firstErrors = firstErrors;

    this.reorderSuccess = reorderSuccess;
    this.reorderFail = reorderFail;
  }

  public TrainingResult(List<TrainingUpdate> updates, int numCorrect, int numWrong,
                        Pair<Integer, Integer> firstError, int reorderSuccess, int reorderFail) {
    this(updates, numCorrect, numWrong, Collections.singletonList(firstError), reorderSuccess, reorderFail);
  }

  public TrainingResult(List<TrainingUpdate> updates, int numCorrect, int numWrong) {
    this(updates, numCorrect, numWrong, Collections.emptyList(), 0, 0);
  }

  List<TrainingUpdate> updates;
  
  final int numCorrect;
  final int numWrong;

  List<Pair<Integer, Integer>> firstErrors;

  final int reorderSuccess;
  final int reorderFail;
}
