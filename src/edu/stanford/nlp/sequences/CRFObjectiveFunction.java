package edu.stanford.nlp.sequences;

import edu.stanford.nlp.classify.LogPrior;
import edu.stanford.nlp.math.ArrayMath;

import java.util.*;
import java.io.*;

/**
 * This class represents the function to minimize in order to
 * learn the weights for a max-entropy CMM classifier.  See
 * superclass for additional notes.
 *
 * @author Jenny Finkel (jrfinkel@cs)
 */

public class CRFObjectiveFunction extends ObjectiveFunction {

  private double[] priorDerivative = null;
  
  @Override
  public void init(MultiDocumentCliqueDataset dataset) {
    super.init(dataset);
    initModels();
  }
  
  @Override
  public void init(MultiDocumentCliqueDataset dataset, LogPrior prior) {
    super.init(dataset, prior);
    initModels();
  }

  private void initModels() {
    models = new CRF[dataset.numDocuments()];

    for (int docNum = 0; docNum < dataset.numDocuments(); docNum++) {
      CliqueDataset doc = dataset.getDocument(docNum);
      models[docNum] = new CRF(doc);
    }
  }
  
  CRF[] models;
 
  public void clear() {
    models = null;
  }

  protected void resetValue() {
    value = 0.0;
  }

  protected void resetDerivative(){
    for(int i=0;i<derivative.length;i++){
      derivative[i] = 0.0;
    }
  }
  
  protected void updateForDoc(CRF model, CliqueDataset doc) {
    model.setParameters(weights);

    for (int datumNum = 0; datumNum < doc.features.length; datumNum++) {
      LabeledClique trueMaxCliqueLabel = doc.maxCliqueLabels[datumNum];
        
      List<LabeledClique> otherLabels = doc.getMaxCliqueLabels(datumNum);
      int size = otherLabels.size();

      for (int i = 0; i < size; i++) {
        LabeledClique otherLC = otherLabels.get(i);
        double prob = model.probOf(datumNum, otherLC);
        if (Double.isNaN(prob)) {
          System.err.println("---------------------");
          System.err.println(datumNum);
          System.err.println(otherLC);
          System.err.println(prob);
          System.err.println(model.logProbOf(datumNum, otherLC));
          System.err.println("---------------------");
        }
        Features p  = doc.features[datumNum].get(otherLC);
        int[] features = p.features;
          
        for (int featureIndex = 0; featureIndex < features.length; featureIndex++) {
          E[features[featureIndex]] += prob * p.value(featureIndex);
        }
      }
      value -= model.logProbOf(datumNum, trueMaxCliqueLabel);
    }

    model.clearFactors();
  }
    
  @Override
  public void calculate(double[] x) {

    setWeights(x);
    resetValue();
    resetE();

    for (int docNum = 0; docNum < dataset.numDocuments(); docNum++) {
      CliqueDataset doc = dataset.getDocument(docNum);
      CRF model = models[docNum];
      updateForDoc(model, doc);
    }

    setDerivative();
    value += prior.compute(x, derivative);
  }

  @Override
  public void calculateStochastic(double[] x, double[] v, int[] batch){
    setWeights(x);
    resetValue();
    resetE();
    
    for (int bInd = 0; bInd < batch.length; bInd++) {
      int docNum = batch[bInd];
      CliqueDataset doc = dataset.getDocument(docNum);
      CRF model = models[docNum];
      updateForDoc(model, doc);
    }
    
    setDerivativeStochastic(batch);
    
    //This is somewhat of a hack, the LogPrior class should probably be updated to allow scaling, but for now.... -ak
    if (priorDerivative == null) {
      priorDerivative = new double[x.length];
    }
    System.arraycopy(derivative, 0, priorDerivative, 0, derivative.length);
    double priorFactor = (batch.length)/((this.dataDimension())*prior.getSigma()*prior.getSigma());
    derivative = ArrayMath.pairwiseAdd(derivative,ArrayMath.multiply(x,priorFactor));
   
    //passing priorDerivative makes sure the actual derivative is untouched.
    
    value += prior.compute(x, priorDerivative);
    
  }


  // NOTE: This function update the weights in place!!!!!  
  protected static double updateWeightsForDoc(CRF model, CliqueDataset doc, double value, double[] weights, double weightsScale, double gain) {
    model.setParameters(weights,weightsScale);

    // Calibration already done, update of weights will not affect calibrated tree
    for (int datumNum = 0; datumNum < doc.features.length; datumNum++) {
      LabeledClique trueMaxCliqueLabel = doc.maxCliqueLabels[datumNum];

      List<LabeledClique> otherLabels = doc.getMaxCliqueLabels(datumNum);
      int size = otherLabels.size();

      for (int i = 0; i < size; i++) {
        LabeledClique otherLC = otherLabels.get(i);
        double prob = model.probOf(datumNum, otherLC);
        if (Double.isNaN(prob)) {
          System.err.println("---------------------");
          System.err.println(datumNum);
          System.err.println(otherLC);
          System.err.println(prob);
          System.err.println(model.logProbOf(datumNum, otherLC));
          System.err.println("---------------------");
        }
        Features p  = doc.features[datumNum].get(otherLC);
        int[] features = p.features;

        for (int featureIndex = 0; featureIndex < features.length; featureIndex++) {
          weights[features[featureIndex]] -= gain* prob * p.value(featureIndex);
        }
      }
      value -= model.logProbOf(datumNum, trueMaxCliqueLabel);
    }

    model.clearFactors();
    return value;
  }

  protected static void updateWeightsForDataset(CliqueDataset data, double[] weights, double weightsScale, double gain) {
    for (int datumNum = 0; datumNum < data.features.length; datumNum++) {
      Features d = data.features[datumNum].get(data.maxCliqueLabels[datumNum]);
      int[] features = d.features;

      for (int f = 0; f < features.length; f++) {
        weights[features[f]] += gain*d.value(f);
      }
    }
  }

  @Override
  public double calculateStochasticUpdate(double[] x, double xscale, int[] batch, double gain) {
    resetValue();

    for (int bInd = 0; bInd < batch.length; bInd++) {
      int docNum = batch[bInd];
      CliqueDataset doc = dataset.getDocument(docNum);
      CRF model = models[docNum];
      value = updateWeightsForDoc(model, doc, value, x, xscale, gain);

      CliqueDataset data = dataset.getDocument(docNum);
      updateWeightsForDataset(data, x, xscale, gain);
    }

    return value;
  }

  protected static double updateValueForDoc(CRF model, CliqueDataset doc, double value, double[] weights, double weightsScale) {
    model.setParameters(weights, weightsScale);

    for (int datumNum = 0; datumNum < doc.features.length; datumNum++) {
      LabeledClique trueMaxCliqueLabel = doc.maxCliqueLabels[datumNum];
      value -= model.logProbOf(datumNum, trueMaxCliqueLabel);
    }

    model.clearFactors();
    return value;
  }

  @Override
  public double valueAt(double[] x, double xscale, int[] batch) {
    resetValue();
    for (int bInd = 0; bInd < batch.length; bInd++) {
      int docNum = batch[bInd];
      CliqueDataset doc = dataset.getDocument(docNum);
      CRF model = models[docNum];
      value = updateValueForDoc(model, doc, value, x, xscale);
    }
    return value;
  }

  @Override
  public int dataDimension(){
    return dataset.numDocuments();
  }

  /** Moved from DistributedCRFObjectiveFunction, which is now defunct.  
    * Only used in MultiDocumentCliqueDataset.  acvogel.
    */
  protected static class FindFilter implements FilenameFilter {
	  String lookingFor;
		FindFilter(String l) {
      lookingFor = l;
		}
		public boolean accept(File dir, String name) {
      return name.contains(lookingFor);
		}
  }
  
}
