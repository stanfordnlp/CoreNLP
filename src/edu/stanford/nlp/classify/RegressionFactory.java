package edu.stanford.nlp.classify;


/**
 * Interface for a regression factory
 * author Ramesh (nmramesh@cs.stanford.edu)
 */
public interface RegressionFactory<F> {
  

  public Regressor<F> train(GeneralDataset<Double,F> dataset);
  
  
}
