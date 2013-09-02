package edu.stanford.nlp.maxent;

import edu.stanford.nlp.classify.LikelihoodPriorObjectiveFunction;
import edu.stanford.nlp.maxent.iis.LambdaSolve;
import edu.stanford.nlp.optimization.DiffFunction;
import edu.stanford.nlp.optimization.Minimizer;
import edu.stanford.nlp.optimization.QNMinimizer;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.stats.Counters;
import edu.stanford.nlp.stats.TwoDimensionalCounter;
import edu.stanford.nlp.util.Index;
import edu.stanford.nlp.util.Pair;

import java.io.PrintWriter;
import java.io.Serializable;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.*;

/** A linear classifier for Type2 Datums.
 *  These are ones where the features are defined over a data, category
 *  pair, and the category set may not be fixed, may be represented in terms
 *  of features and may be infinite.
 *
 *  @author Kristina Toutanova
 *  @version Sep 13, 2004
 *
 */
public class LinearType2Classifier<L,F> implements Serializable {
  ClassicCounter<F> weights;

  public LinearType2Classifier(ClassicCounter<F> weights) {
    this.weights = weights;
    //System.out.println(weights.toString());
  }


  public double getWeight(F feature) {
    return weights.getCount(feature);
  }

  public void setWeight(F feature, double weight) {
    weights.setCount(feature, weight);
  }

  public ClassicCounter<L> scoresOf(Type2Datum<L,F> d) {
    ClassicCounter<L> scores = new ClassicCounter<L>();
    Set<L> classes = d.classes();
    for (L key : classes) {
      scores.incrementCount(key, 0);
    }
    TwoDimensionalCounter<L,F> classFeatures = d.classFeatureCounts;
    for (L thisClass : classes) {
      double score = 0;
      Counter<F> thisClassFeatures = classFeatures.getCounter(thisClass);
      for (F thisFeature : thisClassFeatures.keySet()) {
        double thisFeatureValue = thisClassFeatures.getCount(thisFeature);
        double thisFeatureWeight = weights.getCount(thisFeature);
        score += thisFeatureValue * thisFeatureWeight;
      }
      scores.incrementCount(thisClass, score);
    }
    return scores;
  }

  public L classOf(Type2Datum<L,F> d) {
    ClassicCounter<L> c = scoresOf(d);
    //System.err.println(c.toString());
    return Counters.argmax(c);
  }


  @Override
  public String toString() {
    return weights.toString();
  }

  public String toShortString() {
    return "number of weights " + weights.size() + " total count " + weights.totalCount();
  }

  public void justificationOf(Type2Datum<L,F> d) {
    //System.err.println("justifying ");
    justificationOf(d, new PrintWriter(System.err, true));
  }

  /**
   * Prints the justification of a decision.
   * Dumps for all classes all active features with their values and weights.
   *
   */
  public void justificationOf(Type2Datum<L, F> d, PrintWriter pw) {
    String space = " ";
    String value = "val.";
    NumberFormat nf = NumberFormat.getNumberInstance();
    nf.setMinimumFractionDigits(2);
    nf.setMaximumFractionDigits(2);
    if (nf instanceof DecimalFormat) {
      ((DecimalFormat) nf).setPositivePrefix(" ");
    }
    ClassicCounter<Object> scores = new ClassicCounter<Object>();
    Set<L> classes = d.classes();
    for (L key : classes) {
      scores.incrementCount(key, 0);
    }
    TwoDimensionalCounter<L, F> classFeatures = d.classFeatureCounts;
    pw.println("justifying example " + d.toShortString());
    for (L thisClass : classes) {
      double score = 0;
      StringBuilder sb = new StringBuilder();
      sb.append("scores for class ").append(thisClass).append("\t");
      Counter<F> thisClassFeatures = classFeatures.getCounter(thisClass);
      for (F thisFeature : thisClassFeatures.keySet()) {
        double thisFeatureValue = thisClassFeatures.getCount(thisFeature);
        double thisFeatureWeight = weights.getCount(thisFeature);
        score += thisFeatureValue * thisFeatureWeight;
        sb.append(thisFeature);
        sb.append(space);
        sb.append(value);
        sb.append(space);
        sb.append(nf.format(thisFeatureValue));
        sb.append(space);
        sb.append(nf.format(thisFeatureWeight));
        sb.append(space);
      }
      scores.incrementCount(thisClass, score);
      sb.append("total ").append(score);
      pw.println(sb.toString());
      //System.err.println("dumping "+sb);
    }
  }

  /** Prints weights for all classes and features to specified
   * <code>PrintWriter</code>.
   */
  public void printWeights(PrintWriter pw) {
//    if (false) {
//      for (F feat : ErasureUtils.sortedIfPossible(weights.keySet())) {
//        pw.println(feat.toString() + ": " + Double.toString(weights.getCount(feat)));
//      }
//    }
    pw.println(weights.toString());
  }

  /**
   * A quick implementation of a default log-linear model given sigma, Quasi-newton, quadratic prior
   * the features with count strictly greater than the given ones are included
   * if you want to add all possible features (no thresholding) give thresholds lowerEmpCount = -1,  lowerAllCount=0
   *
   */
  public static <L,F> LinearType2Classifier<L,Pair<L, F>> trainClassifier(Type2Corpus<L,F> data, int lowerEmpCount, int lowerAllCount, double sigma) {
    data.createFeatureIndex(lowerEmpCount, lowerAllCount);
    data.summaryStatistics();
    //save the feature index and forget it
    //String tmpIndexFile = "c:/tmp/index.tmp";
    String tmpIndexFile = "/tmp/type2ClassifierIndex.tmp";

    data.saveIndexAndForget(tmpIndexFile);
    DiffFunction logLik = new LikelihoodPriorObjectiveFunction(data, sigma);
    Minimizer<DiffFunction> m = new QNMinimizer(5);
    double[] initial = new double[data.domainDimension()];
    double[] parameters = m.minimize(logLik, 1e-3, initial);
    ClassicCounter<Pair<L, F>> weights = new ClassicCounter<Pair<L, F>>();
    //read the feature index
    data.readIndex(tmpIndexFile);
    for (int ind = 0; ind < parameters.length; ind++) {
      weights.incrementCount(data.getFeature(ind), parameters[ind]);
    }
    data.clear();
    return new LinearType2Classifier<L, Pair<L, F>>(weights);
  }

  /**
   * A quick implementation of a default log-linear model, Quasi-newton, sigma = 1
   *
   */
  public static <L, F> LinearType2Classifier<L,F> trainClassifier(Type2Dataset<L, F> data) {
    return trainClassifier(data, data.featureIndex(), 1);
  }

  /**
   * A quick implementation of a default log-linear model, Quasi-newton
   *
   */
  private static <L,F> LinearType2Classifier<L,F> trainClassifier(Type2Dataset<L, F> data, Index<F> featureIndex, double sigma) {
    Problem p = data.toProblem();
    LambdaSolve prob = new LambdaSolve(p, .0001, .0001);
    prob.setNonBinary();
    String path = "/tmp" + "/temp_lambdas";
    System.out.println(" path is " + path);
    CGRunner runner = new CGRunner(prob, path, 1e-3, sigma);
    runner.solve();
    ClassicCounter<F> weights = new ClassicCounter<F>();
    for (int ind = 0; ind < prob.lambda.length; ind++) {
      weights.incrementCount(featureIndex.get(ind), prob.lambda[ind]);
    }
    return new LinearType2Classifier<L, F>(weights);
  }

  private static final long serialVersionUID = 3018892419663189770L;

}
