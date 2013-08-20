package edu.stanford.nlp.classify;

import java.util.List;



/**
 * Class to train a KNN regression model.
 * author Ramesh (nmramesh@cs.stanford.edu)
 */
public class KNNRegressionFactory<F> implements RegressionFactory<F>{
  
  /**
   * Trains the linear regression model with default regularization coefficient set to 1.0
   * @param dataset
   */
	
	private int numNeighbors = 1;
	private List<String> docsList;
	private SimilarityType simType = SimilarityType.Cosine;

	public enum SimilarityType {
    Cosine(0),
    RBF(1);
    
    private int value;

    SimilarityType(int value) {
      this.value = value;
    }
    
    public int getValue() {
      return this.value;
    }
  }
	
	public void setSimilarityType(SimilarityType simType) {
    this.simType = simType;
  }
	
	public void setNumNeighbors(int nn){
		numNeighbors = nn;
	}
	
	public void setDocsList(List<String> docsList){
		this.docsList = docsList;
	}
	
  public Regressor<F> train(GeneralDataset<Double,F> dataset){
    return train(dataset, docsList,numNeighbors,simType);
  }
  
  
  public Regressor<F> train(GeneralDataset<Double,F> dataset, List<String> docsList, int numNeighbors, SimilarityType simType){
  	return new KNNRegressor<F>(dataset,docsList,numNeighbors,simType);
  }
    
}
