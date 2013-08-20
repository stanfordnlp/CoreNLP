package edu.stanford.nlp.classify;

import java.util.*;

import edu.stanford.nlp.ling.RVFDatum;
import edu.stanford.nlp.optimization.QNMinimizer;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.util.Index;
import edu.stanford.nlp.util.Pair;


/** A class that has training, dev, and test set and dumps all needed
 *  statistics on the test set.
 *  Has some options for training and fitting the hyper parameters.
 *
 * @author  Kristina Toutanova
 * @version May 23, 2005
 */
public class LogisticRegressionTrainerAndTester<L, F> {

  LinearClassifier<L, F> lC;
  Dataset<L, F> test;
  private double TOL = 1e-5;
  private double SIGMA = 1.0;
  double min = .1;
  double max = 10.0;
  final boolean accuracy = false;


  /**
   * in this case we hold out 30% for validation and then retrain on the whole set
   * uisng the fitted sigma
   *
   */
  public LogisticRegressionTrainerAndTester(GeneralDataset<L, F> training) {
    LinearClassifierFactory<L, F> lCFactory = new LinearClassifierFactory<L, F>(new QNMinimizer(10), TOL, false, SIGMA);
    lC = lCFactory.trainClassifierV(training, min, max, accuracy);
  }

  public LogisticRegressionTrainerAndTester(GeneralDataset<L, F> training, GeneralDataset<L, F> dev) {
    LinearClassifierFactory<L, F> lCFactory = new LinearClassifierFactory<L, F>(new QNMinimizer(10), TOL, false, SIGMA);
    lC = lCFactory.trainClassifierV(training, dev, min, max, accuracy);
  }

  public LogisticRegressionTrainerAndTester(LinearClassifier<L, F> lc) {
    lC = lc;
  }

  public AccuracyStatistics getStats(GeneralDataset<L, F> test) {
    Index<L> labels = test.labelIndex();
    L labelOne = labels.get(1);
    List<Pair<Double, Integer>> dataScores = new ArrayList<Pair<Double, Integer>>();
    for (int i = 0; i < test.size(); i++) {
      RVFDatum<L, F> d = test.getRVFDatum(i);
      Counter<L> scores = lC.logProbabilityOf(d);
      int labelD = d.label().equals(labelOne) ? 1 : 0;
      dataScores.add(new Pair<Double, Integer>(Math.exp(scores.getCount(labelOne)), labelD));
    }

    PRCurve prc = new PRCurve(dataScores);
    AccuracyStatistics aS = new AccuracyStatistics();
    aS.confWeightedAccuracy = prc.cwa();
    aS.accuracy = prc.accuracy();
    aS.optAccuracy = prc.optimalAccuracy();
    aS.optConfWeightedAccuracy = prc.optimalCwa();
    aS.logLikelihood = prc.logLikelihood();
    aS.accrecall = prc.cwaArray();
    aS.optaccrecall = prc.optimalCwaArray();
    return aS;
  }

  /**
   * Train and test a model reading from files
   *
   */
  public static void main(String[] args) {
    //need to know whether these are RVFDatasets or Datasets
    //is there a separate dev set or not , for now not
    boolean rvf = true;
    String trainFile = null;
    String testFile = null;
    String devFile = null;
    boolean devset = false;
    int start = 0;

    while (start < args.length) {
      if (args[start].equals("-binary")) {
        rvf = false;
        start++;
        continue;
      }
      if (args[start].equals("-dev")) {
        devset = true;
        start++;
        continue;
      }
      trainFile = args[start];
      start++;
      break;
    }

    if (devset) {
      devFile = args[start++];
    }
    testFile = args[start];

    LogisticRegressionTrainerAndTester<String, String> lRTT = null;
    GeneralDataset<String, String> train = null;
    GeneralDataset<String, String> test = null;
    GeneralDataset<String, String> dev = null;
    if (rvf) {
      train = RVFDataset.readSVMLightFormat(trainFile);
      test = RVFDataset.readSVMLightFormat(testFile, train.featureIndex(), train.labelIndex());
      if (devset) {
        dev = RVFDataset.readSVMLightFormat(devFile, train.featureIndex(), train.labelIndex());
      }
    } else {
      train = Dataset.readSVMLightFormat(trainFile);
      test =  Dataset.readSVMLightFormat(testFile, train.featureIndex(), train.labelIndex());
      if (devset) {
        dev = Dataset.readSVMLightFormat(devFile, train.featureIndex(), train.labelIndex());
      }
    }

    if (devset) {
      lRTT = new LogisticRegressionTrainerAndTester<String, String>(train, dev);
    } else {
      lRTT = new LogisticRegressionTrainerAndTester<String, String>(train);
    }

    AccuracyStatistics aS = lRTT.getStats(test);
    System.err.println("statistics " + aS);

  }

}

/**
 * statistics collected out of a single test-set
 */
class AccuracyStatistics {
  double confWeightedAccuracy;
  double accuracy;
  double optAccuracy;
  double optConfWeightedAccuracy;
  double logLikelihood;
  int[] accrecall;
  int[] optaccrecall;

  /**
   * dumping accuracy at various recall levels
   *
   */
  public static String toStringArr(int[] acc) {
    StringBuilder sb = new StringBuilder();
    int total = acc.length;
    for (int i = 0; i < acc.length; i++) {
      double coverage = (i + 1) / (double) total;
      double accuracy = acc[i] / (double) (i + 1);
      coverage *= 1000000;
      accuracy *= 1000000;
      coverage = (int) coverage;
      accuracy = (int) accuracy;
      coverage /= 10000;
      accuracy /= 10000;
      sb.append(coverage);
      sb.append("\t");
      sb.append(accuracy);
      sb.append("\n");
    }
    return sb.toString();
  }

  @Override
  public String toString() {
    return "accuracy " + accuracy + " optimal fn accuracy " + optAccuracy + " confidence weighted accuracy " + confWeightedAccuracy + " optimal confidence weighted accuracy " + optConfWeightedAccuracy + " log-likelihood " + logLikelihood +
        "\n" + "accuracy coverage " + toStringArr(accrecall) + "optimal accuracy coverage \n" + toStringArr(optaccrecall);
  }

}
