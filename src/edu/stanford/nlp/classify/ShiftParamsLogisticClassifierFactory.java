package edu.stanford.nlp.classify;

import java.util.Arrays;
import java.util.List;

import edu.stanford.nlp.classify.LogPrior.LogPriorType;
import edu.stanford.nlp.ling.RVFDatum;
import edu.stanford.nlp.optimization.DiffFunction;
import edu.stanford.nlp.optimization.QNMinimizer;
import edu.stanford.nlp.util.logging.Redwood;

/**
 * @author jtibs
 */
public class ShiftParamsLogisticClassifierFactory<L, F> implements ClassifierFactory<L, F, MultinomialLogisticClassifier<L, F>> {
  private static final long serialVersionUID = -8977510677251295037L;
  
  private int[][] data;
  private double[][] dataValues;
  private int[] labels;
  private int numClasses;
  private int numFeatures;
  private LogPrior prior;
  private double lambda;
  
  public ShiftParamsLogisticClassifierFactory() {
    this(new LogPrior(LogPriorType.NULL), 0.1);
  }
  
  public ShiftParamsLogisticClassifierFactory(double lambda) {
    this(new LogPrior(LogPriorType.NULL), lambda);
  }
  
  // NOTE: the current implementation only supports quadratic priors (or no prior)
  public ShiftParamsLogisticClassifierFactory(LogPrior prior, double lambda) {
    this.prior = prior;
    this.lambda = lambda;
  }
  
  public MultinomialLogisticClassifier<L, F> trainClassifier(GeneralDataset<L, F> dataset) {
    numClasses = dataset.numClasses();
    numFeatures = dataset.numFeatures();
    
    data = dataset.getDataArray();
    if (dataset instanceof RVFDataset<?, ?>) {
      dataValues = dataset.getValuesArray();
    } else {
      dataValues = LogisticUtils.initializeDataValues(data);
    }
    augmentFeatureMatrix(data, dataValues);

    labels = dataset.getLabelsArray();

    return new MultinomialLogisticClassifier<>(trainWeights(), dataset.featureIndex, dataset.labelIndex);
  }
  
  private double[][] trainWeights() {
    QNMinimizer minimizer = new QNMinimizer(15, true);
    minimizer.useOWLQN(true, lambda);
    
    DiffFunction objective = new ShiftParamsLogisticObjectiveFunction(data, dataValues,
        convertLabels(labels), numClasses, numFeatures + data.length, numFeatures, prior);
    
    double[] augmentedThetas = new double[(numClasses - 1) * (numFeatures + data.length)];
    augmentedThetas = minimizer.minimize(objective, 1e-4, augmentedThetas);

    // calculate number of non-zero parameters, for debugging
    int count = 0;
    for (int j = numFeatures; j < augmentedThetas.length; j++) {
      if (augmentedThetas[j] != 0) count++;
    }
    Redwood.log("NUM NONZERO PARAMETERS: " + count);

    double[][] thetas = new double[numClasses - 1][numFeatures];
    LogisticUtils.unflatten(augmentedThetas, thetas);

    return thetas;
  }
  
  // augments the feature matrix to account for shift parameters, setting X := [X|I]
  private void augmentFeatureMatrix(int[][] data, double[][] dataValues) {
    for (int i = 0; i < data.length; i++) {
      int newLength = data[i].length + 1;
      data[i] = Arrays.copyOf(data[i], newLength);
      data[i][newLength - 1] = i + numFeatures;
      
      dataValues[i] = Arrays.copyOf(dataValues[i], newLength);
      dataValues[i][newLength - 1] = 1;
    }
  }
  
  // convert labels to form that the objective function expects
  private int[][] convertLabels(int[] labels) {
    int[][] result = new int[labels.length][numClasses];
    for (int i = 0; i < labels.length; i++) {
      result[i][labels[i]] = 1;
    }
    return result;
  }

}
