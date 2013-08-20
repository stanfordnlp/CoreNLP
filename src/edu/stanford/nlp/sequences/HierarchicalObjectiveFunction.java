package edu.stanford.nlp.sequences;

import edu.stanford.nlp.optimization.AbstractCachingDiffFunction;
import edu.stanford.nlp.classify.LogPrior;
import edu.stanford.nlp.util.Index;
import edu.stanford.nlp.util.HashIndex;

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

public class HierarchicalObjectiveFunction extends AbstractCachingDiffFunction {

  private Map<String,ObjectiveFunctionInterface> objFuncs;
  private DatasetMetaInfo metaInfo;
  private Index<String> domainIndex = new HashIndex();
  private LogPrior prior;
  private Map<String,LogPrior> basePriors = new HashMap();
  private LogPrior basePrior;
  private final int numFeatures;

  public HierarchicalObjectiveFunction(Map<String,ObjectiveFunctionInterface> objFuncs, DatasetMetaInfo metaInfo, double sigma, Map<String,Double> adaptSigmas) {
    this.objFuncs = objFuncs;
    this.metaInfo = metaInfo;
    domainIndex.addAll(objFuncs.keySet());
    numFeatures = metaInfo.numFeatures();

    prior = new LogPrior(LogPrior.LogPriorType.QUADRATIC, sigma, 0.0);

    for (String domainName : adaptSigmas.keySet()) {
      basePriors.put(domainName,new LogPrior(LogPrior.LogPriorType.QUADRATIC, adaptSigmas.get(domainName), 0.0));
    }
  }
  
  public void setPrior(LogPrior prior) {
    this.prior = prior;
  }
  
  public int domainDimension() {
    return numFeatures * (objFuncs.size() + 1);
  }

  public void calculate(double[] x) {

    value = 0.0;
    if (derivative == null) { derivative = new double[domainDimension()]; }
    else { Arrays.fill(derivative, 0.0); }

    Map<String,double[]> weightMap = convertWeights(x);
    double[] phi = weightMap.get(null);

    double[] der = new double[numFeatures];
    value += prior.compute(phi, der);

    LogPrior adaptPrior;
    
    for (int i = 0; i < objFuncs.size(); i++) {

      String domainName = domainIndex.get(i);
      double[] weights = weightMap.get(domainName);
      ObjectiveFunctionInterface objFunc = objFuncs.get(domainName);
      LogPrior basePrior = basePriors.get(domainName);
      adaptPrior = LogPrior.getAdaptationPrior(phi, basePrior);
      objFunc.setPrior(adaptPrior);
      double v = objFunc.valueAt(weights);
      value += v;
//      System.err.println(v);
      System.arraycopy(objFunc.derivativeAt(weights), 0, derivative, i*numFeatures, numFeatures);

//       for (int j = 0; j < phi.length; j++) {
//         phi[j] *= -1;
//         x1[j] *= -1;
//       }
      adaptPrior = LogPrior.getAdaptationPrior(weights, basePrior);
//      value += adaptPrior.compute(phi, der);
      // not adding to value, because value is added on the lower level objective
      // function and this is just computing the partial deivative for the
      // top level parameters.  i think this is right.  -JRF
      adaptPrior.compute(phi, der);
      
    }
    System.arraycopy(der, 0, derivative, objFuncs.size()*numFeatures, numFeatures);
  }

  public Map<String,double[]> convertWeights(double[] x) {
    
    Map<String,double[]> weightMap = new HashMap();
    for (int i = 0; i < domainIndex.size(); i++) {
      String domainName = domainIndex.get(i);
      double[] weights = new double[numFeatures];
      System.arraycopy(x, i*numFeatures, weights, 0, numFeatures);
      weightMap.put(domainName, weights);
    }
    double[] weights = new double[numFeatures];
    System.arraycopy(x, domainIndex.size()*numFeatures, weights, 0, numFeatures);
    weightMap.put(null, weights);

    return weightMap;    
  }
  
}
