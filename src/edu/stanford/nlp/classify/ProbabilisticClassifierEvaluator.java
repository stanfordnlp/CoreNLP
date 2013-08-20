package edu.stanford.nlp.classify;

import edu.stanford.nlp.optimization.Evaluator;
import edu.stanford.nlp.stats.Scorer;

import java.io.PrintWriter;

/**
 * Evaluates Classifier on a set of data during minimization
 *
 * @author Angel Chang
 */
public class ProbabilisticClassifierEvaluator<L,F> implements Evaluator {
  ProbabilisticClassifierCreator<L,F> classifierCreator;
  GeneralDataset<L,F> dataset;
  Scorer<L> scorer;
  PrintWriter out;
  protected String description;

  public ProbabilisticClassifierEvaluator(String description,
                                          ProbabilisticClassifierCreator<L,F> classifierCreator,
                                          GeneralDataset<L,F> dataset,
                                          Scorer<L> scorer,
                                          PrintWriter out)
  {
    this.description = description;
    this.classifierCreator = classifierCreator;
    this.dataset = dataset;
    this.scorer = scorer;
    this.out = out;
  }

  public String toString() {
    return description;
  }

  public double evaluate(double[] x) {
    ProbabilisticClassifier<L,F> classifier = classifierCreator.createProbabilisticClassifier(x);
    double score = scorer.score(classifier, dataset);
    if (out != null) {
      out.println(scorer.getDescription(4));
      out.flush();
    }
    return score;
  }
}
