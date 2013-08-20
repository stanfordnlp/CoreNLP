package edu.stanford.nlp.classify;

import java.util.ArrayList;
import java.util.List;

import edu.stanford.nlp.ling.Datum;

/**
 *
 * a bag of regressors which returns the average of outputs of the regressors.
 * @param <F>
 */
public class BaggingRegressor<F> implements Regressor<F>{
  /**
   * 
   */
  private static final long serialVersionUID = 8367022834638633057L;
  List<Regressor<F>> bagOfRegressors;
  
  public BaggingRegressor(List<Regressor<F>> bag){
    this.bagOfRegressors = bag;
  }

  /**
   * 
   * @param datum
   * @return the regressed output value of the datum.
   */
  public double valueOf(Datum<Double,F> datum){
    double value = 0;
    for(Regressor<F> regressor : bagOfRegressors){
      value += regressor.valueOf(datum);
    }
    return value/bagOfRegressors.size();
  }

  
  public List<Double> valuesOf(GeneralDataset<Double, F> data) {
    List<Double> avgValues = new ArrayList<Double>();
    for(int i = 0; i < data.size(); i++)avgValues.add(0.0);
    for(Regressor<F> regressor: bagOfRegressors){
      List<Double> values =  regressor.valuesOf(data);
      for(int i = 0; i < values.size(); i++)
        avgValues.set(i, avgValues.get(i)+values.get(i));
    }
    for(int i = 0; i < avgValues.size(); i++)
      avgValues.set(i, avgValues.get(i)/bagOfRegressors.size());
    return avgValues;
  }
  
  
}
