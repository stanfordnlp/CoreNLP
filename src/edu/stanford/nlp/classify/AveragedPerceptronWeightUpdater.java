package edu.stanford.nlp.classify;

import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.stats.ClassicCounter;

import java.util.HashMap;
import java.util.Map;

/**
	An implementation of the Averaged Perceptron algorithm
	@author Gabor Angeli (angeli@cs.stanford.edu)
*/
public class AveragedPerceptronWeightUpdater<F,ID> 
		implements CounterWeightUpdater<F,ID>{
	
	private HashMap<F,Info> infoMap;
	private int time;
	private double sumChange = Double.NaN;

	private static final class Info {
		private double weight;
		private double averagedWeight;
		private int lastUpdateTime;

		public void refresh(int time){
			averagedWeight += (time - lastUpdateTime) * weight;
			lastUpdateTime = time;
		}
		public double get(){
			return averagedWeight;
		}
		public Info clone() {
			Info info = new Info();
			info.weight = weight;
			info.averagedWeight = averagedWeight;
			info.lastUpdateTime = lastUpdateTime;
			return info;
		}
	}

	/**
		Create a new Averaged Perceptron Weight Updater
	*/
	public AveragedPerceptronWeightUpdater(){
		this.time = 0;
		this.infoMap = new HashMap<F,Info>();
	}

	/**
		Updates the weight {@link Counter}, 
			returning the resulting {@link Counter}
		@param weights The initial weights to update
		@param goldVectors The true feature vectors
		@param guessedVectors The guessed feature vectors
		@param losses Ignored
		@param datumIDs Ignored, save to get the length of the dataset
		@param itersSinceLastUpdate The number of extra timesteps to skip
			for this update of the parameters (usually 0)
		@return A counter representing the new feature weights.
	*/
	public Counter<F> getUpdate(Counter<F> weights, Counter<F>[] goldVectors, 
			Counter<F>[] guessedVectors, double[] losses, ID[] datumIDs,
			int itersSinceLastUpdate){
		this.time += itersSinceLastUpdate;
		this.update(weights,goldVectors,guessedVectors,losses,datumIDs);
		return getWeights();
	}

	/**
		Updates the weight {@link Counter} silently
		@param weights The initial weights to update
		@param goldVectors The true feature vectors
		@param guessedVectors The guessed feature vectors
		@param losses Ignored
		@param datumIDs Ignored, save to get the length of the dataset
	*/
	public void update(Counter<F> weights, Counter<F>[] goldVectors,
			Counter<F>[] guessedVectors, double[] losses, ID[] datumIDs){
		assert datumIDs.length == goldVectors.length;
		assert datumIDs.length == guessedVectors.length;
		//--Time Step
		this.time += 1;
		this.sumChange = 0.0;
		//--Get Change
		ClassicCounter<F> change = new ClassicCounter<F>();
		for (int i = 0; i < datumIDs.length; i++) {
			//(decrement guessed)
			for(F key : guessedVectors[i].keySet()){
				double guessed = guessedVectors[i].getCount(key);
				if(!goldVectors[i].containsKey(key)){ //debug: sum change in weights
					sumChange += Math.abs(guessed);
				}
				change.incrementCount(key, -guessed);
			}
			//(increment gold)
			for(F key : goldVectors[i].keySet()){
				double gold = goldVectors[i].getCount(key);
				sumChange += Math.abs(gold - guessedVectors[i].getCount(key));
				change.incrementCount(key, gold);
			}
		}
		//--Update Weights
		for(F key : change.keySet()){
			//(get weight info)
			Info inf = this.infoMap.get(key);
			if(inf == null){
				this.infoMap.put(key, inf = new Info());
			}
			//(refresh weight)
			inf.refresh(time);
			inf.averagedWeight += change.getCount(key);
			inf.weight += change.getCount(key);
		}

	}

	/**
		Returns the weights at the current stage of the algorithm.
		Usually paired with the update() method.
		@return A Counter representing the feature weight.
	*/
	public Counter<F> getWeights(){
		Counter<F> rtn = new ClassicCounter<F>();
		for(F key : infoMap.keySet()){
			rtn.setCount(key, infoMap.get(key).averagedWeight);
		}
		return rtn;
	}

	/**
		Get the change in the weight vectors after the last iteration
		@return The sum change of the weight vectors
	*/
	public double getSumChange(){
		return sumChange;
	}

	/**
		Ends an epoch. Does nothing in this implementation
	*/
	public void endEpoch(){
		//do nothing?
	}

	/**
		Returns a copy of this updater, deep cloning all of the feature
		weight.
		@return A deep clone of this object.
	*/
	public AveragedPerceptronWeightUpdater<F,ID> clone(){
		AveragedPerceptronWeightUpdater<F,ID> rtn 
			= new AveragedPerceptronWeightUpdater<F,ID>();
		rtn.infoMap = new HashMap<F,Info>();
		for(Map.Entry<F,Info> e : infoMap.entrySet()){
			rtn.infoMap.put(e.getKey(), e.getValue().clone());
		}
		rtn.time = this.time;
		return rtn;
	}

}
