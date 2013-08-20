package edu.stanford.nlp.classify;

import edu.stanford.nlp.optimization.DiffFunction;
import edu.stanford.nlp.optimization.Minimizer;
import edu.stanford.nlp.optimization.QNMinimizer;

/**
 * Class to train an L2 regularized linear regression model.
 * author Ramesh (nmramesh@cs.stanford.edu)
 */
public class LinearRegressionFactory<F> implements RegressionFactory<F>{
  
	private double regularizationCoeff = 1.0;
  /**
   * Trains the linear regression model with default regularization coefficient set to 1.0
   * @param dataset
   */
  public Regressor<F> train(GeneralDataset<Double,F> dataset){
    return train(dataset, regularizationCoeff);
  }
  
  public Regressor<F> train(GeneralDataset<Double,F> dataset, double regularizerCoefficient){
    return train(dataset,regularizerCoefficient, 1e-4);
  }
  
  
  public void setRegularizationCoeff(double regCoeff){
  	this.regularizationCoeff = regCoeff;
  }
  /**
   * maximizes the correlation between model output and true output
   * @param dataset
   * @param regularizerCoefficient
   */
  public Regressor<F> trainCorrelation(GeneralDataset<Double,F> dataset, double regularizerCoefficient){
    CorrelationLinearRegressionObjectiveFunction<F> clrf = new CorrelationLinearRegressionObjectiveFunction<F>(dataset,regularizerCoefficient);
    Minimizer<DiffFunction> minim = new QNMinimizer(clrf);
    return train(dataset,regularizerCoefficient,1e-4,minim);
  }
  
  public Regressor<F> train(GeneralDataset<Double,F> dataset, double regularizerCoefficient, double tolerance){
    LinearRegressionObjectiveFunction<F> lrf = new LinearRegressionObjectiveFunction<F>(dataset,regularizerCoefficient);
    Minimizer<DiffFunction> minim = new QNMinimizer(lrf);
    return train(dataset,regularizerCoefficient,tolerance,minim);
  }
  
  public Regressor<F> train(GeneralDataset<Double,F> dataset, double regularizerCoefficient, double tolerance , Minimizer<DiffFunction> minim){
    LinearRegressionObjectiveFunction<F> lrf = new LinearRegressionObjectiveFunction<F>(dataset,regularizerCoefficient);
    double[] weights = minim.minimize(lrf, tolerance, new double[dataset.numFeatures()]);
    LinearRegressor<F> lr = new LinearRegressor<F>(weights,dataset.featureIndex);
    return lr;
  }
  
}
