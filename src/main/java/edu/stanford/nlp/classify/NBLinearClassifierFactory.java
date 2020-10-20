package edu.stanford.nlp.classify;

import java.util.function.DoubleUnaryOperator;

import edu.stanford.nlp.ling.BasicDatum;
import edu.stanford.nlp.optimization.GoldenSectionLineSearch;
import edu.stanford.nlp.util.logging.Redwood;

/**
 * Provides a medium-weight implementation of Bernoulli (or binary)
 * Naive Bayes via a linear classifier.  It's medium weight in that
 * it uses dense arrays for counts and calculation (but, hey, NB is
 * efficient to estimate).  Each feature is treated as an independent
 * binary variable.
 * <p>
 * CDM Jun 2003: I added a dirty trick so that if there is a feature
 * that is always on in input examples, then its weight is turned into
 * a prior feature!  (This will work well iff it is also always on at
 * test time.)  In fact, this is done for each such feature, so by
 * having several such features, one can even get an integral prior
 * boost out of this.
 *
 * @author Dan Klein
 * @author Sarah Spikes (sdspikes@cs.stanford.edu) (Templatization)
 *
 * @param <L> The type of the labels in the Classifier
 * @param <F> The type of the features in the Classifier
 */
public class NBLinearClassifierFactory<L, F> extends AbstractLinearClassifierFactory<L, F>  {

  /** A logger for this class */
  private static final Redwood.RedwoodChannels log = Redwood.channels(NBLinearClassifierFactory.class);

  private static final boolean VERBOSE = false;

  private double sigma;     // amount of add-k smoothing of evidence
  private final boolean interpretAlwaysOnFeatureAsPrior;
  private static final double epsilon = 1e-30;   // fudge to keep nonzero
  private boolean tuneSigma = false;
  private int folds;


  @Override
  protected double[][] trainWeights(GeneralDataset<L, F> data) {
    return trainWeights(data.getDataArray(), data.getLabelsArray());
  }

  /**
   * Train weights.
   * If tuneSigma is true, the optimal sigma value is found using cross-validation:
   * the number of folds is determined by the {@code folds} variable,
   * if there are less training examples than folds,
   * leave-one-out is used.
   */
  double[][] trainWeights(int[][] data, int[] labels) {
    if (tuneSigma) {
      tuneSigma(data, labels);
    }
    if (VERBOSE) {
      log.info("NB CF: " + data.length + " data items ");
      for (int i = 0; i < data.length; i++) {
        log.info("Datum " + i + ": " + labels[i] + ':');
        for (int j = 0; j < data[i].length; j++) {
          log.info(" " + data[i][j]);
        }
        log.info("");
      }
    }
    int numFeatures = numFeatures();
    int numClasses = numClasses();
    double[][] weights = new double[numFeatures][numClasses];
    // find P(C|F)/P(C)
    int num = 0;
    double[] numc = new double[numClasses];
    double n = 0;   // num active features in whole dataset
    double[] n_c = new double[numClasses];  // num active features in class c items
    double[] n_f = new double[numFeatures]; // num data items for which feature is active
    double[][] n_fc = new double[numFeatures][numClasses];  // num times feature active in class c
    for (int d = 0; d < data.length; d++) {
      num++;
      numc[labels[d]]++;
      for (int i = 0; i < data[d].length; i++) {
        n++;
        n_c[labels[d]]++;
        n_f[data[d][i]]++;
        n_fc[data[d][i]][labels[d]]++;
      }
    }
    for (int c = 0; c < numClasses; c++) {
      for (int f = 0; f < numFeatures; f++) {
        if (interpretAlwaysOnFeatureAsPrior && n_f[f] == data.length) {
          // interpret always on feature as prior!
          weights[f][c] = Math.log(numc[c] / num);
        } else {
          // p_c_f = (N(f,c)+k)/(N(f)+|C|k) = Paddk(c|f)
          // set lambda = log (P()/P())
          double p_c = (n_c[c] + epsilon) / (n + numClasses * epsilon);
          double p_c_f = (n_fc[f][c] + sigma) / (n_f[f] + sigma * numClasses);
          if (VERBOSE) {
            log.info("Prob ratio(f=" + f + ",c=" + c + ") = " + p_c_f / p_c + " (nc=" + n_c[c] + ", nf=" + n_f[f] + ", nfc=" + n_fc[f][c] + ')');
          }
          weights[f][c] = Math.log(p_c_f / p_c);
        }
      }
    }
    return weights;
  }

  double[][] weights(int[][] data, int[] labels, int testMin, int testMax, double trialSigma, int foldSize) {
    int numFeatures = numFeatures();
    int numClasses = numClasses();
    double[][] weights = new double[numFeatures][numClasses];
    // find P(C|F)/P(C)
    int num = 0;
    double[] numc = new double[numClasses];
    double n = 0;   // num active features in whole dataset
    double[] n_c = new double[numClasses];  // num active features in class c items
    double[] n_f = new double[numFeatures]; // num data items for which feature is active
    double[][] n_fc = new double[numFeatures][numClasses];  // num times feature active in class c
    for (int d = 0; d < data.length; d++) {
      if (d == testMin) {
        d = testMax - 1;
        continue;
      }
      num++;
      numc[labels[d]]++;
      for (int i = 0; i < data[d].length; i++) {
        if (i == testMin) {
          i = testMax - 1;
          continue;
        }
        n++;
        n_c[labels[d]]++;
        n_f[data[d][i]]++;
        n_fc[data[d][i]][labels[d]]++;
      }
    }
    for (int c = 0; c < numClasses; c++) {
      for (int f = 0; f < numFeatures; f++) {
        if (interpretAlwaysOnFeatureAsPrior && n_f[f] == data.length - foldSize) {
          // interpret always on feature as prior!
          weights[f][c] = Math.log(numc[c] / num);
        } else {
          // p_c_f = (N(f,c)+k)/(N(f)+|C|k) = Paddk(c|f)
          // set lambda = log (P()/P())
          double p_c = (n_c[c] + epsilon) / (n + numClasses * epsilon);
          double p_c_f = (n_fc[f][c] + trialSigma) / (n_f[f] + trialSigma * numClasses);
          weights[f][c] = Math.log(p_c_f / p_c);
        }
      }
    }
    return weights;
  }


  private void tuneSigma(final int[][] data, final int[] labels) {

    DoubleUnaryOperator CVSigmaToPerplexity = trialSigma -> {
      double score = 0.0;
      double sumScore = 0.0;
      int foldSize, nbCV;
      log.info("Trying sigma = " + trialSigma);
      //test if enough training data
      if (data.length >= folds) {
        foldSize = data.length / folds;
        nbCV = folds;
      } else { //leave-one-out
        foldSize = 1;
        nbCV = data.length;
      }

      for (int j = 0; j < nbCV; j++) {
        //System.out.println("CV j: "+ j);
        int testMin = j * foldSize;
        int testMax = testMin + foldSize;

        LinearClassifier<L, F> c = new LinearClassifier<>(weights(data, labels, testMin, testMax, trialSigma, foldSize), featureIndex, labelIndex);
        for (int i = testMin; i < testMax; i++) {
          //System.out.println("test i: "+ i + " "+ new BasicDatum(featureIndex.objects(data[i])));
          score -= c.logProbabilityOf(new BasicDatum<>(featureIndex.objects(data[i]))).getCount(labelIndex.get(labels[i]));
        }
        //System.err.printf("%d: %8g%n", j, score);
        sumScore += score;
      }
      System.err.printf(": %8g%n", sumScore);
      return sumScore;
    };

    GoldenSectionLineSearch gsls = new GoldenSectionLineSearch(true);
    sigma = gsls.minimize(CVSigmaToPerplexity, 0.01, 0.0001, 2.0);
    System.out.println("Sigma used: " + sigma);
  }

  /**
   * Create a ClassifierFactory.
   */
  public NBLinearClassifierFactory() {
    this(1.0);
  }

  /**
   * Create a ClassifierFactory.
   *
   * @param sigma The amount of add-sigma smoothing of evidence
   */
  public NBLinearClassifierFactory(double sigma) {
    this(sigma, false);
  }

  /**
   * Create a ClassifierFactory.
   *
   * @param sigma The amount of add-sigma smoothing of evidence
   * @param interpretAlwaysOnFeatureAsPrior If true, a feature that is in every
   *              data item is interpreted as an indication to include a prior
   *              factor over classes.  (If there are multiple such features, an
   *              integral "prior boost" will occur.)  If false, an always on
   *              feature is interpreted as an evidence feature (and, following
   *              the standard math) will have no effect on the model.

   */
  public NBLinearClassifierFactory(double sigma, boolean interpretAlwaysOnFeatureAsPrior) {
    this.sigma = sigma;
    this.interpretAlwaysOnFeatureAsPrior = interpretAlwaysOnFeatureAsPrior;
  }

  /**
   * setTuneSigmaCV sets the {@code tuneSigma} flag: when turned on,
   * the sigma is tuned by cross-validation.
   * If there is less data than the number of folds, leave-one-out is used.
   * The default for tuneSigma is false.
   *
   * @param folds Number of folds for cross validation
   */
  public void setTuneSigmaCV(int folds) {
    tuneSigma = true;
    this.folds = folds;
  }

  private static final long serialVersionUID = 1;

}
