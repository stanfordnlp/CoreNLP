package edu.stanford.nlp.parser.metrics;

import edu.stanford.nlp.util.Pair;

/**
 * A general class that scores precision, recall, and F1. The package supports incremental
 * computation of these statistics via the {@link #update} method. Formatted output reflecting
 * the current values of the statistics may be obtained via the {@link #toString} method.
 *
 * @see    Evalb
 * @author Spence Green
 */
public class EvaluationMetric {
  private double numTestInstances = 0.0;
  private double exact = 0.0;

  private double precisions = 0.0;
  private double precisions2 = 0.0;

  private double recalls = 0.0;
  private double recalls2 = 0.0;

  private double pnums2 = 0.0;
  private double rnums2 = 0.0;

  private double f1s = 0.0;

  /**
   * Updates the evaluation statistics. Should be called once for each test example
   * (e.g., sentence, parse tree, etc.).
   *
   * @param curP     Precision of the current test example
   * @param curPnum  The denominator used to calculate the current precision
   * @param curR     Recall of the current test example
   * @param curRnum  The denominator used to calculate the current recall
   */
  public void update(double curP, double curPnum, double curR, double curRnum) {
    numTestInstances += 1.0;

    double curF1 = (curP > 0.0 && curR > 0.0) ? 2.0 / ((1.0 / curP) + (1.0 / curR)) : 0.0;
    if(curF1 >= 0.9999)
      exact += 1.0;

    precisions += curP;
    recalls += curR;
    f1s += curF1;

    precisions2 += curPnum * curP;
    pnums2 += curPnum;

    recalls2 += curRnum * curR;
    rnums2 += curRnum;

    //Update for the toString() method to be called during running average output
    lastP = curP;
    lastR = curR;
    lastF1 = curF1;
  }

  /**
   * Returns the components of the precision.
   *
   * @return A {@link Pair} with the numerator of the precision in the first element
   * and the denominator of the precision in the second element.
   */
  public Pair<Double,Double> getPFractionals() {
    return new Pair<>(precisions2, pnums2);
  }

  /**
   * Returns the components of the recall.
   *
   * @return A {@link Pair} with the numerator of the recall in the first element
   * and the denominator of the recall in the second element.
   */
  public Pair<Double,Double> getRFractionals() {
    return new Pair<>(recalls2, rnums2);
  }

  /**
   * Returns the number of test instances (e.g., parse trees or sentences) used in
   * the calculation of the statistics. This value corresponds to the number of calls
   * to {@link #update}.
   *
   * @return The number of test instances
   */
  public double getTestInstances() {
    return numTestInstances;
  }

  /**
   * A convenience method that returns the number of true positive examples from
   * among the test instances. Mathematically, this value is the denominator of the recall.
   *
   * @return Number of true positive examples
   */
  public double numRelevantExamples() {
    return rnums2;
  }

  private double lastP = 0.0;
  private double lastR = 0.0;
  private double lastF1 = 0.0;

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    double pSent = (numTestInstances > 0.0) ? precisions / numTestInstances : 0.0;
    double pEvalB = (pnums2 > 0.0) ? precisions2 / pnums2 : 0.0;
    sb.append(String.format("P: %.2f (sent ave: %.2f) (evalb: %.2f)%n", lastP*100.0, pSent*100.0, pEvalB*100.0));

    double rSent = (numTestInstances > 0.0) ? recalls / numTestInstances : 0.0;
    double rEvalB = (rnums2 > 0.0) ? recalls2 / rnums2 : 0.0;
    sb.append(String.format("R: %.2f (sent ave: %.2f) (evalb: %.2f)%n", lastR*100.0, rSent*100.0, rEvalB*100.0));

    double f1Sent = (numTestInstances > 0.0) ? f1s / numTestInstances : 0.0;
    double f1EvalB = (pEvalB > 0.0 && rEvalB > 0.0) ? 2.0 / ((1.0 / pEvalB) + (1.0 / rEvalB)) : 0.0;
    sb.append(String.format("F1: %.2f (sent ave: %.2f) (evalb: %.2f)%n", lastF1*100.0, f1Sent*100.0, f1EvalB*100.0));

    sb.append(String.format("Num:\t%.2f (test instances)%n", numTestInstances));
    sb.append(String.format("Rel:\t%.0f (relevant examples)%n", rnums2));
    sb.append(String.format("Exact:\t%.2f (test instances)%n", exact));

    return sb.toString();
  }
}

