package edu.stanford.nlp.classify;

import java.util.ArrayList;
import java.util.List;


/**
 * Class to train a bagged regression model.
 * author Ramesh (nmramesh@cs.stanford.edu)
 */
public class BaggingRegressionFactory<F> implements RegressionFactory<F>{
  
  private List<RegressionFactory<F>> factories;
  
  private boolean subsampleDataForTraining = true;
  private double bootstrapSampleFrac = 0.90;
  
  public BaggingRegressionFactory(List<RegressionFactory<F>> factories){
    this.factories = factories;
  }
  
  public BaggingRegressionFactory(List<RegressionFactory<F>> factories, boolean subsample){
    this.factories = factories;
    this.subsampleDataForTraining = subsample;
  }
  
  public BaggingRegressionFactory(List<RegressionFactory<F>> factories, double bootstrapFrac){
    this.factories = factories;
    bootstrapSampleFrac = bootstrapFrac;
  }
  
  public Regressor<F> train(GeneralDataset<Double,F> dataset) {
    List<Regressor<F>> bagOfRegressors = new ArrayList<Regressor<F>>(); 
    for(int i = 0; i < factories.size(); i++){
    	if(subsampleDataForTraining){
    		GeneralDataset<Double,F> subset = dataset.sampleDataset(i, bootstrapSampleFrac, true);
    		bagOfRegressors.add(factories.get(i).train(subset));      
    	}
    	else
    		bagOfRegressors.add(factories.get(i).train(dataset));  
    }
    BaggingRegressor<F> regressor = new BaggingRegressor<F>(bagOfRegressors);
    return regressor;
  }  
}
