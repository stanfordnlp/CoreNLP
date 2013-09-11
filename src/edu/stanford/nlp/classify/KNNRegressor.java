package edu.stanford.nlp.classify;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import edu.stanford.nlp.classify.SVMLightRegressionFactory.KernelType;
import edu.stanford.nlp.ling.Datum;
import edu.stanford.nlp.ling.RVFDatum;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.stats.Counters;
import edu.stanford.nlp.stats.TwoDimensionalCounter;
import edu.stanford.nlp.util.Pair;
import edu.stanford.nlp.util.Triple;

/**
 * KNN regression model.
 * Use <code>KNNRegressionFactory</code> to train the model.
 * @author Ramesh (nmramesh@cs.stanford.edu)
 *
 * @param <F>
 */
public class KNNRegressor<F> implements Regressor<F>{
  /**
   * 
   */
	
  private static final long serialVersionUID = 8367022834638633057L;
  GeneralDataset<Double,F> dataset;
  List<String> docsList;
  int numNeighbors;
  private KNNRegressionFactory.SimilarityType simType;
  
  public KNNRegressor(GeneralDataset<Double,F> dataset, List<String> docsList, int numNeighbors, KNNRegressionFactory.SimilarityType sim){
  	this.dataset = dataset;
  	this.docsList = docsList;
  	this.numNeighbors = numNeighbors;
  	this.simType = sim;
  }

  /**
   * 
   * @param datum
   * @return the regressed output value of the datum.
   */
  public double valueOf(Datum<Double,F> datum){
    if(datum instanceof RVFDatum)
     return valueOfRVFDatum((RVFDatum<Double,F>)datum);
    else
    	throw new RuntimeException("unsupported type");
    /*
    Counter<Integer> nearestNeighbors = new ClassicCounter<Integer>();
    Counter<F> fCounter = new ClassicCounter<F>();    
    for(F feature : datum.asFeatures())fCounter.incrementCount(feature);
    for(int i = 0; i < dataset.size(); i++){
    	RVFDatum<Double,F> datumi = dataset.getRVFDatum(i);
    	double cosine = Counters.cosine(datumi.asFeaturesCounter(), fCounter);
    	nearestNeighbors.incrementCount(i,cosine);
    }
    Counters.retainTop(nearestNeighbors, numNeighbors);
    Counters.normalize(nearestNeighbors);
    double output = 0;
    for(int i : nearestNeighbors.keySet()){
    	output += dataset.getDatum(i).label()*nearestNeighbors.getCount(i);
    }
    return output;
    */
  }

  /**
   * returns RBF Similarity between the two counters.
   * Assumes that the features are already transformed to Gaussian scale with unit variance.
   * @param c1
   * @param c2
   * @return
   */
  private double getRBFSimilarity(Counter<F> c1, Counter<F> c2){
  	double sim = 0;
  	Set<F> allFeatures = new HashSet<F>(c1.keySet());
  	allFeatures.addAll(c2.keySet());
  	for(F feat: allFeatures){
  		sim+= Math.pow((c1.getCount(feat) - c2.getCount(feat)) , 2.0);
  	}
  	return Math.exp(-sim);
  }
  
  private double valueOfRVFDatum(RVFDatum<Double,F> datum){
    Counter<F> fCounter = datum.asFeaturesCounter();
    Counter<Integer> nearestNeighbors = new ClassicCounter<Integer>();
    for(int i = 0; i < dataset.size(); i++){
    	RVFDatum<Double,F> datumi = dataset.getRVFDatum(i);
    	double sim = 0;
    	if(simType == KNNRegressionFactory.SimilarityType.Cosine)
    		sim = Counters.cosine(datumi.asFeaturesCounter(), fCounter);
    	else if(simType == KNNRegressionFactory.SimilarityType.RBF)
    		sim = getRBFSimilarity(datumi.asFeaturesCounter(), fCounter);
    	nearestNeighbors.incrementCount(i,sim);
    }
    Counters.retainTop(nearestNeighbors, numNeighbors);
    Counters.normalize(nearestNeighbors);
    double output = 0;
    for(int i : nearestNeighbors.keySet()){
    	output += dataset.getDatum(i).label()*nearestNeighbors.getCount(i);
    }
    return output;
  }
  

  public List<Double> valuesOf(GeneralDataset<Double, F> data) {    
    List<Double> values = new ArrayList<Double>();
    for (int i = 0; i < data.size(); i++) {
      values.add(valueOf(data.getDatum(i)));
    }
    return values;
  }
 
  public Triple<TwoDimensionalCounter<String,F>,Counter<String>,Counter<String>> justificationOf(Datum<Double,F> datum){
  	if(datum instanceof RVFDatum)
  		return justificationOfRVFDatum((RVFDatum<Double,F>)datum);
  	else{
  		throw new RuntimeException("datum type not supported yet!");
  		//TODO: implement this later.
  	}
  }
  
  private Triple<TwoDimensionalCounter<String,F>,Counter<String>,Counter<String>> justificationOfRVFDatum(RVFDatum<Double,F> datum){
  	TwoDimensionalCounter<String,F> justificationCounter = new TwoDimensionalCounter<String,F>();
  	Counter<F> fCounter = datum.asFeaturesCounter();
    Counter<Integer> nearestNeighborIDs = new ClassicCounter<Integer>(); 
    for(int i = 0; i < dataset.size(); i++){
    	RVFDatum<Double,F> datumi = dataset.getRVFDatum(i);
    	double cosine = Counters.cosine(datumi.asFeaturesCounter(), fCounter);
    	nearestNeighborIDs.incrementCount(i,cosine);
    }
    Counters.retainTop(nearestNeighborIDs, numNeighbors);
    for(int docID : Counters.toSortedList(nearestNeighborIDs)){
    	String docName = docsList.get(docID);
    	Counter<F> neighborFCounter = dataset.getRVFDatum(docID).asFeaturesCounter();    	
    	for(F feature: fCounter.keySet()){
    		if(neighborFCounter.containsKey(feature))
    			justificationCounter.incrementCount(docName, feature, fCounter.getCount(feature)*neighborFCounter.getCount(feature));
    	}    	
    }
    Counter<String> nearestNeighborSims = new ClassicCounter<String>();
    Counter<String> nearestNeighborRatings = new ClassicCounter<String>();
    for(int docID: nearestNeighborIDs.keySet()){
    	nearestNeighborSims.incrementCount(docsList.get(docID), nearestNeighborIDs.getCount(docID));
    	nearestNeighborRatings.incrementCount(docsList.get(docID),dataset.getDatum(docID).label());
    }
    return new Triple<TwoDimensionalCounter<String,F>,Counter<String>,Counter<String>>(justificationCounter,nearestNeighborSims,nearestNeighborRatings);
  }
 
}
