package edu.stanford.nlp.classify;

import java.util.ArrayList;
import java.util.List;

import edu.stanford.nlp.ling.Datum;
import edu.stanford.nlp.ling.RVFDatum;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.stats.Counters;
import edu.stanford.nlp.util.Index;

/**
 * An L2 regularized linear regression model.
 * Use <code>LinearRegressionFactory</code> to train the model.
 * @author Ramesh (nmramesh@cs.stanford.edu)
 *
 * @param <F>
 */
public class LinearRegressor<F> implements Regressor<F>{
  /**
   * 
   */
  private static final long serialVersionUID = 8367022834638633057L;
  private double[] weights;
  private Index<F> featureIndex;
  
  public LinearRegressor(double[] weights, Index<F> featureIndex){
    this.weights = weights;
    this.featureIndex = featureIndex;
  }
  
  public double[] weights() {
    return weights;
  }
  public Index<F> featureIndex(){
    return featureIndex;
  }

  /**
   * 
   * @param datum
   * @return the regressed output value of the datum.
   */
  public double valueOf(Datum<Double,F> datum){
    if(datum instanceof RVFDatum)
      return valueOfRVFDatum((RVFDatum<Double,F>)datum);
    double output = 0;
    for(F feature : datum.asFeatures()){
      int fID = featureIndex.indexOf(feature);
      if(fID>=0)
        output += weights[fID];
    }
    return output;
  }

  /**
   * 
   * @param datum
   * @return a counter of weights for the features in the datum.
   */
  public Counter<F> justificationOf(Datum<Double,F> datum){
    if(datum instanceof RVFDatum)
      return justificationOfRVFDatum((RVFDatum<Double,F>)datum);
    Counter<F> featureWeights = new ClassicCounter <F>();
    for(F feature : datum.asFeatures()){
      int fID = featureIndex.indexOf(feature);
      if(fID>=0)
        featureWeights.incrementCount(feature, weights[fID]);
    }
    return featureWeights;
  }
  
  private Counter<F> justificationOfRVFDatum(RVFDatum<Double,F> datum){
    Counter<F> featureWeights = new ClassicCounter <F>();
    Counter<F> datumCounter = datum.asFeaturesCounter();
    for(F feature : datumCounter.keySet()){
      int fID = featureIndex.indexOf(feature);
      if(fID>=0){
        double weight = weights[fID]*datumCounter.getCount(feature);
        featureWeights.incrementCount(feature, weight);
      }
    }
    return featureWeights;
  }
  
  /**
   * 
   * @param numFeatures the number of top features to be returned.
   * @return a counter of top features with weights.
   */
  public Counter<F> getTopFeatures(int numFeatures){
    Counter<F> topWeights = getFeatureWeights();
    Counters.retainTop(topWeights,numFeatures);
    return topWeights;
  }

  
  public Counter<F> getFeatureWeights(){
    Counter<F> weightsCtr = new ClassicCounter<F>();
    for(int i = 0; i < featureIndex.size(); i++){
      F feature = featureIndex.get(i);
      weightsCtr.incrementCount(feature,weights[i]);
    }    
    return weightsCtr;
  }
  
  /**
   * 
   * @return a counter of all feature-weights
   */
  public Counter<F> getTopFeatures(){
    return getTopFeatures(featureIndex.size());
  }
  
  public List<Double> valuesOf(GeneralDataset<Double, F> data) {    
    List<Double> values = new ArrayList<Double>();
    for (int i = 0; i < data.size(); i++) {
      values.add(valueOf(data.getDatum(i)));
    }
    return values;
  }
  
  private double valueOfRVFDatum(RVFDatum<Double,F> datum){
    double output = 0;
    Counter<F> fCounter = datum.asFeaturesCounter();
    for(F feature : fCounter.keySet()){
      int fID = featureIndex.indexOf(feature);
      if(fID>=0)
        output += weights[fID]*fCounter.getCount(feature);
    }
    return output;
  }
}
