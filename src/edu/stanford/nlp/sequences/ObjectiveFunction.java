package edu.stanford.nlp.sequences;

import edu.stanford.nlp.classify.LogPrior;
import edu.stanford.nlp.optimization.AbstractStochasticCachingDiffUpdateFunction;

import java.util.*;

/**
 * This abstract class contains a lot of the more general code for 
 * writing optimization functions for sequence classifiers.  Specificically,
 * you should see {@link CMMObjectiveFunction} and {@link CRFObjectiveFunction}.
 * Instances of subclasses 
 * should be fed to an optimization class, such as
 * edu.stanford.nlp.optimization.QNMinimze to learn
 * the weights.  The weights will be a single dimentional array,
 * due to the structure of the optimizers, so you can feed the
 * learned weights into the ObjectiveFunction.to2D() function.
 * You end up with a weight for each feature/cliqueLabel pair, 
 * so the way you index into this 2D array is weights[featureIndex][cliqueLabelIndex].
 * The feature index and clique label index come from the {@link edu.stanford.nlp.util.Index}s in the
 * {@link MultiDocumentCliqueDataset} given on construction.  It is important
 * to remember that each clique has it's own index for possible labels.
 *
 * @author Jenny Finkel {jrfinkel@cs}
 */

public abstract class ObjectiveFunction extends AbstractStochasticCachingDiffUpdateFunction
    implements ObjectiveFunctionInterface {

  public void init(MultiDocumentCliqueDataset dataset) {
    init(dataset, new LogPrior(LogPrior.LogPriorType.QUADRATIC, 1.0, 0.0));
  }
  
  public void init(MultiDocumentCliqueDataset dataset, LogPrior prior) {
    this.dataset = dataset;
    numLabels = dataset.metaInfo().numLabels();
    numFeatures = dataset.metaInfo().numFeatures();

    setPrior(prior);
    setEhat();
  }
    
  protected MultiDocumentCliqueDataset dataset;
  protected int numLabels = 0;
  protected int numFeatures = 0;

  protected double[] Ehat = null;
  protected double[] EhatStochastic = null;
  protected double[] E = null;
  protected double[] weights = null;

  protected LogPrior prior = null;

  protected int domainDimension = 0;

  protected boolean lastEvalWasStochastic = false;
  
  @Override
  public int dataDimension(){
    throw new UnsupportedOperationException("Stochastic functionality must be done in the function implementing the ObjectiveFunction");
  }
  
  @Override
  public void calculateStochastic(double[] x, double[] v, int[] batch){
    throw new UnsupportedOperationException("Stochastic functionality must be done in the function implementing the ObjectiveFunction");
  }
  
  @Override
  public int domainDimension() {
    return Ehat.length;
  }

  /**
   * Specify the {@link LogPrior} to use.
   */
  public void setPrior(LogPrior prior) {
    this.prior = prior;
    clearCache();
  }

  
  
  /**
   * Ehat is the actual feature/label counts.  This is needed for both the
   * CRF and the CMM to calculate the derivative.  This function only needs 
   * to be called once, since the
   * actual counts never change.
   * 
   * it does change however when using stochastic optimization
   * in which case you should use setEhat(int[] batch)
   */
  protected void setEhat() {
    System.err.print("setting Ehat...");
    System.err.println(numFeatures);
    initEhat();
    
    for (int docNum = 0; docNum < dataset.numDocuments(); docNum++) {
      CliqueDataset data = dataset.getDocument(docNum);
			updateEhatForDataset(Ehat, data);
    }
    System.err.println("done.");
  }
  
  
  protected void initEhat() {
    Ehat = new double[numFeatures];
    E = new double[numFeatures];
    weights = new double[numFeatures];
  }
  
  protected static void updateEhatForDataset(double[] Ehat, CliqueDataset data) {
    for (int datumNum = 0; datumNum < data.features.length; datumNum++) {
      Features d = data.features[datumNum].get(data.maxCliqueLabels[datumNum]);
      int[] features = d.features;
      
      for (int f = 0; f < features.length; f++) {
        Ehat[features[f]] += d.value(f);
      }
    }
  }
  
  /**
   * empties the E[] array, which holds the expected values
   * for each feature.
   */
  protected void resetE() {
    Arrays.fill(E, 0);
  }

  /**
   * computes the partial derivatives, which are just E - Ehat, and are used
   * by both the CMM and the CRF.
   */
  protected void setDerivative() {
    for (int i = 0; i < derivative.length; i++) {
      derivative[i] = E[i] - Ehat[i];
    }
  }

  
  /**
   * computes the partial derivatives, just as setDerivative(), except
   *  it ensures Ehat is used only on document indicies provided in batch
   */
  protected void setDerivativeStochastic(int[] batch){
    
    if( EhatStochastic == null){
      EhatStochastic = new double[numFeatures];
    } else{
      Arrays.fill(EhatStochastic, 0.0);
    }

    for (int b = 0; b < batch.length; b++) {
      int docNum = batch[b];
      CliqueDataset data = dataset.getDocument(docNum);
            updateEhatForDataset(EhatStochastic, data);
    }
    
    for (int i = 0; i < derivative.length; i++) {
      derivative[i] = E[i] - EhatStochastic[i];
    }
    
  }
  
  /**
   * Puts the 1D weights array that the optimizer uses, into a two dimensional array
   * which can be easily indexted into - weights[feature][cliqueLabel].
   */
  protected void setWeights(double[] x) {
    System.arraycopy(x, 0, weights, 0, x.length);
  }

}
