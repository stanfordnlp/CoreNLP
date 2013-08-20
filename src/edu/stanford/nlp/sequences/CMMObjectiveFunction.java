package edu.stanford.nlp.sequences;

import java.util.*;

/**
 * This class represents the function to minimize in order to
 * learn the weights for a max-entropy CMM classifier.  See
 * superclass for additional notes.
 *
 * @author Jenny Finkel (jrfinkel@cs)
 */

public class CMMObjectiveFunction extends ObjectiveFunction {

  @Override
  public void calculate(double[] x) {

    setWeights(x);

    value = 0.0;
    resetE();

    for (int docNum = 0; docNum < dataset.numDocuments(); docNum++) {

      CliqueDataset doc = dataset.getDocument(docNum);

      CMM model = new CMM(doc, weights);
      
      for (int datumNum = 0; datumNum < doc.features.length; datumNum++) {
        LabeledClique trueMaxCliqueLabel = doc.maxCliqueLabels[datumNum];
        
        List<LabeledClique> otherLabels = doc.getMaxCliqueConditionalLabels(datumNum, trueMaxCliqueLabel);
        
        for (LabeledClique otherLC : otherLabels) {
          double condProb = model.conditionalProbOf(datumNum, otherLC);
          Features p  = doc.features[datumNum].get(otherLC);
          int[] features = p.features;
          
          for (int featureIndex = 0; featureIndex < features.length; featureIndex++) {
            E[features[featureIndex]] += condProb * p.value(featureIndex);
          }
        }
        value -= model.logConditionalProbOf(datumNum, trueMaxCliqueLabel);
      }
    }

    setDerivative();
    value += prior.compute(x, derivative);

  }

  @Override
  public double calculateStochasticUpdate(double[] x, double xscale, int[] batch, double gain) {
    throw new UnsupportedOperationException();
  }

  @Override
  public double valueAt(double[] x, double xscale, int[] batch) {
    throw new UnsupportedOperationException();
  }

}
