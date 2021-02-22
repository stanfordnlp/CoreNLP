package edu.stanford.nlp.parser.shiftreduce;

import java.util.ArrayList;
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

  public TrainingResult(List<TrainingResult> results) {
    List<TrainingUpdate> updates = new ArrayList<>();
    List<Pair<Integer, Integer>> firstErrors = new ArrayList<>();
    int numCorrect = 0;
    int numWrong = 0;
    int reorderSuccess = 0;
    int reorderFail = 0;

    for (TrainingResult result : results) {
      updates.addAll(result.updates);
      numCorrect += result.numCorrect;
      numWrong += result.numWrong;
      firstErrors.addAll(result.firstErrors);
      reorderSuccess += result.reorderSuccess;
      reorderFail += result.reorderFail;
    }

    this.updates = Collections.unmodifiableList(updates);
    this.numCorrect = numCorrect;
    this.numWrong = numWrong;
    this.firstErrors = Collections.unmodifiableList(firstErrors);
    this.reorderSuccess = reorderSuccess;
    this.reorderFail = reorderFail;
  }

  List<TrainingUpdate> updates;

  final int numCorrect;
  final int numWrong;

  List<Pair<Integer, Integer>> firstErrors;

  final int reorderSuccess;
  final int reorderFail;
}
