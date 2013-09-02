package edu.stanford.nlp.stats;

import edu.stanford.nlp.classify.GeneralDataset;
import edu.stanford.nlp.classify.ProbabilisticClassifier;
import edu.stanford.nlp.ling.Datum;

import java.text.NumberFormat;


/**
 * @author Jenny Finkel
 */
public class BasicAccuracyStats<L> implements Scorer<L> {

  public <F> BasicAccuracyStats(ProbabilisticClassifier<L,F> classifier, GeneralDataset<L,F> data) {
    score(classifier, data);
  }

  public BasicAccuracyStats() {
  }

  int correct = 0;
  int total = 0;

  public <F> double score(ProbabilisticClassifier<L,F> classifier, GeneralDataset<L,F> data) {

    correct = 0;
    total = 0;

    for (int i = 0; i < data.size(); i++) {
      Datum<L, F> d = data.getRVFDatum(i);
      Object guess = classifier.classOf(d);
      Object label = d.label();
      if (guess.equals(label)) { correct++; }
      total++;
    }

    return score();
  }

  public double score() {
    return (double)correct / (double)total;
  }

  public String getDescription(int numDigits) {
    NumberFormat nf = NumberFormat.getNumberInstance();
    nf.setMaximumFractionDigits(numDigits);

    StringBuilder sb = new StringBuilder();
    double acc = score();
    sb.append("--- Basic Accuracy Stats ---").append("\n");
    sb.append("accuracy: ").append(nf.format(acc)).append(" (").append(correct).append("/").append(total).append(")");
    return sb.toString();
  }

}
