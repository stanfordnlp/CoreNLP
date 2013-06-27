package edu.stanford.nlp.stats;

import java.text.NumberFormat;
import java.util.ArrayList;

import edu.stanford.nlp.classify.GeneralDataset;
import edu.stanford.nlp.classify.PRCurve;
import edu.stanford.nlp.classify.ProbabilisticClassifier;
import edu.stanford.nlp.ling.Datum;
import edu.stanford.nlp.util.Pair;
import edu.stanford.nlp.util.StringUtils;

/**
 * Utility class for aggregating counts of true positives, false positives, and
 * false negatives and computing precision/recall/F1 stats. Can be used for a single
 * collection of stats, or to aggregate stats from a bunch of runs.
 *
 * @author Kristina Toutanova
 * @author Jenny Finkel
 */
public class AccuracyStats<L> implements Scorer<L> {

  double confWeightedAccuracy;
  double accuracy;
  double optAccuracy;
  double optConfWeightedAccuracy;
  double logLikelihood;
  int[] accrecall;
  int[] optaccrecall;

  L posLabel;

  String saveFile; // = null;
  static int saveIndex = 1;

  public <F> AccuracyStats(ProbabilisticClassifier<L,F> classifier, GeneralDataset<L,F> data, L posLabel) {
    this.posLabel = posLabel;
    score(classifier, data);
  }

  public AccuracyStats(L posLabel, String saveFile) {
    this.posLabel = posLabel;
    this.saveFile = saveFile;
  }

  public <F> double score(ProbabilisticClassifier<L,F> classifier, GeneralDataset<L,F> data) {

    ArrayList<Pair<Double, Integer>> dataScores = new ArrayList<Pair<Double, Integer>>();
    for (int i = 0; i < data.size(); i++) {
      Datum<L,F> d = data.getRVFDatum(i);
      Counter<L> scores = classifier.logProbabilityOf(d);
      int labelD = d.label().equals(posLabel) ? 1 : 0;
      dataScores.add(new Pair<Double, Integer>(Math.exp(scores.getCount(posLabel)), labelD));
    }

    PRCurve prc = new PRCurve(dataScores);

    confWeightedAccuracy = prc.cwa();
    accuracy = prc.accuracy();
    optAccuracy = prc.optimalAccuracy();
    optConfWeightedAccuracy = prc.optimalCwa();
    logLikelihood = prc.logLikelihood();
    accrecall = prc.cwaArray();
    optaccrecall = prc.optimalCwaArray();

    return accuracy;
  }

  public String getDescription(int numDigits) {
    NumberFormat nf = NumberFormat.getNumberInstance();
    nf.setMaximumFractionDigits(numDigits);

    StringBuilder sb = new StringBuilder();
    sb.append("--- Accuracy Stats ---").append('\n');
    sb.append("accuracy: ").append(nf.format(accuracy)).append('\n');
    sb.append("optimal fn accuracy: ").append(nf.format(optAccuracy)).append('\n');
    sb.append("confidence weighted accuracy :").append(nf.format(confWeightedAccuracy)).append('\n');
    sb.append("optimal confidence weighted accuracy: ").append(nf.format(optConfWeightedAccuracy)).append('\n');
    sb.append("log-likelihood: ").append(logLikelihood).append('\n');
    if (saveFile != null) {
      String f = saveFile + '-' + saveIndex;
      sb.append("saving accuracy info to ").append(f).append(".accuracy\n");
      StringUtils.printToFile(f + ".accuracy", toStringArr(accrecall));
      sb.append("saving optimal accuracy info to ").append(f).append(".optimal_accuracy\n");
      StringUtils.printToFile(f + ".optimal_accuracy", toStringArr(optaccrecall));
      saveIndex++;
      //sb.append("accuracy coverage: ").append(toStringArr(accrecall)).append("\n");
      //sb.append("optimal accuracy coverage: ").append(toStringArr(optaccrecall));
    }
    return sb.toString();
  }

  public static String toStringArr(int[] acc) {
    StringBuilder sb = new StringBuilder();
    int total = acc.length;
    for (int i = 0; i < acc.length; i++) {
      double coverage = (i + 1) / (double) total;
      double accuracy = acc[i] / (double) (i + 1);
      coverage *= 1000000;
      accuracy *= 1000000;
      sb.append(((int) coverage) / 10000);
      sb.append('\t');
      sb.append(((int) accuracy) / 10000);
      sb.append('\n');
    }
    return sb.toString();
  }

}
