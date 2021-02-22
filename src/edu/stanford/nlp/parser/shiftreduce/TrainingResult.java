package edu.stanford.nlp.parser.shiftreduce;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import edu.stanford.nlp.stats.IntCounter;
import edu.stanford.nlp.stats.TwoDimensionalIntCounter;
import edu.stanford.nlp.util.Pair;

public class TrainingResult {
  public TrainingResult(List<TrainingUpdate> updates, int numCorrect, int numWrong,
                        List<Pair<Integer, Integer>> firstErrors,
                        IntCounter<Class<? extends Transition>> correctTransitions,
                        TwoDimensionalIntCounter<Class<? extends Transition>, Class<? extends Transition>> wrongTransitions,
                        int reorderSuccess, int reorderFail) {
    this.updates = updates;

    this.numCorrect = numCorrect;
    this.numWrong = numWrong;

    this.firstErrors = firstErrors;
    this.correctTransitions = correctTransitions;
    this.wrongTransitions = wrongTransitions;

    this.reorderSuccess = reorderSuccess;
    this.reorderFail = reorderFail;
  }

  public TrainingResult(List<TrainingUpdate> updates, int numCorrect, int numWrong,
                        Pair<Integer, Integer> firstError,
                        IntCounter<Class<? extends Transition>> correctTransitions,
                        TwoDimensionalIntCounter<Class<? extends Transition>, Class<? extends Transition>> wrongTransitions,
                        int reorderSuccess, int reorderFail) {
    this(updates, numCorrect, numWrong,
         (firstError == null ? Collections.emptyList() : Collections.singletonList(firstError)),
         correctTransitions, wrongTransitions,
         reorderSuccess, reorderFail);
  }

  public TrainingResult(List<TrainingResult> results) {
    List<TrainingUpdate> updates = new ArrayList<>();
    List<Pair<Integer, Integer>> firstErrors = new ArrayList<>();
    int numCorrect = 0;
    int numWrong = 0;
    int reorderSuccess = 0;
    int reorderFail = 0;
    IntCounter<Class<? extends Transition>> correctTransitions = new IntCounter<>();
    TwoDimensionalIntCounter<Class<? extends Transition>, Class<? extends Transition>> wrongTransitions = new TwoDimensionalIntCounter<>();

    for (TrainingResult result : results) {
      updates.addAll(result.updates);
      numCorrect += result.numCorrect;
      numWrong += result.numWrong;
      firstErrors.addAll(result.firstErrors);
      correctTransitions.addAll(result.correctTransitions);
      wrongTransitions.addAll(result.wrongTransitions);
      reorderSuccess += result.reorderSuccess;
      reorderFail += result.reorderFail;
    }

    this.updates = Collections.unmodifiableList(updates);
    this.numCorrect = numCorrect;
    this.numWrong = numWrong;
    this.firstErrors = Collections.unmodifiableList(firstErrors);
    this.correctTransitions = correctTransitions;
    this.wrongTransitions = wrongTransitions;
    this.reorderSuccess = reorderSuccess;
    this.reorderFail = reorderFail;
  }

  List<TrainingUpdate> updates;

  final int numCorrect;
  final int numWrong;

  List<Pair<Integer, Integer>> firstErrors;

  IntCounter<Class<? extends Transition>> correctTransitions;
  TwoDimensionalIntCounter<Class<? extends Transition>, Class<? extends Transition>> wrongTransitions;

  final int reorderSuccess;
  final int reorderFail;
}
