package edu.stanford.nlp.maxent;

import edu.stanford.nlp.optimization.Minimizer;
import edu.stanford.nlp.optimization.DiffFunction;
import edu.stanford.nlp.optimization.QNMinimizer;
import edu.stanford.nlp.classify.LikelihoodPriorObjectiveFunction;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.util.Pair;

import java.util.HashMap;


/**
 * A factory for LinearType2Classifiers.
 *
 * @author Kristina Toutanova
 * @version Apr 4, 2005
 */
  @SuppressWarnings("unused")
public class LinearType2ClassifierFactory<L, F> {

  private double TOL = 1e-4;
  private double sigma = 1.0;
  private int mem = 15;
  private boolean verbose = false;
  private double epsilon = 0.0;
  private Minimizer<DiffFunction> minimizer;
  private boolean useSum = false;
  private boolean tuneSigmaHeldOut = false;
  private boolean tuneSigmaCV = false;
  private boolean resetWeight = false;
  private int folds;


  public LinearType2ClassifierFactory() {
    minimizer = new QNMinimizer(mem);
  }


  public LinearType2ClassifierFactory(double sigma, int mem, double tolerance) {
    this.sigma = sigma;
    this.mem = mem;
    minimizer = new QNMinimizer(mem);
    TOL = tolerance;
  }

  public LinearType2Classifier<L, Pair<L, F>> trainClassifier(Type2Corpus<L, F> data) {
    return trainClassifier(data, -1, 0);
  }


  public LinearType2Classifier<L, Pair<L, F>> trainClassifier(Type2Corpus<L, F> data, int lowerEmpCount, int lowerAllCount) {
    return trainClassifier(data, lowerEmpCount, lowerAllCount, null);
  }

  /**
   * A quick implementation of a default log-linear model given sigma, Quasi-newton, quadratic prior
   *
   */
  public LinearType2Classifier<L, Pair<L, F>> trainClassifier(Type2Corpus<L, F> data, int lowerEmpCount, int lowerAllCount, HashMap<Pair<L, F>, Pair<Double, Double>> specialPriors) {
    data.createFeatureIndex(lowerEmpCount, lowerAllCount);
    data.summaryStatistics();
    HashMap<Integer, Pair<Double, Double>> mappedSpecialPriors = new HashMap<Integer, Pair<Double, Double>>();
    if (specialPriors != null) {
      for (Pair<L, F> next : specialPriors.keySet()) {
        int fIndex = data.getIndex(next);
        System.err.println("index of " + next + " is " + fIndex);
        if (fIndex > -1) {
          mappedSpecialPriors.put(Integer.valueOf(fIndex), specialPriors.get(next));
        }


      }
    }
    DiffFunction logLik = null;
    if (specialPriors == null) {
      logLik = new LikelihoodPriorObjectiveFunction(data, sigma);
    } else {
      logLik = new LikelihoodPriorObjectiveFunction(data, LikelihoodPriorObjectiveFunction.QUADRATIC_PRIOR, sigma, 0, mappedSpecialPriors);
    }
    double[] initial = new double[data.domainDimension()];
    double[] parameters = minimizer.minimize(logLik, TOL, initial);
    ClassicCounter<Pair<L, F>> weights = new ClassicCounter<Pair<L, F>>();
    for (int ind = 0; ind < parameters.length; ind++) {
      weights.incrementCount(data.getFeature(ind), parameters[ind]);
    }
    return new LinearType2Classifier<L, Pair<L, F>>(weights);
  }

}
