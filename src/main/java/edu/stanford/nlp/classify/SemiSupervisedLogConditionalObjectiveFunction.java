package edu.stanford.nlp.classify;

import edu.stanford.nlp.optimization.AbstractCachingDiffFunction;


/**
 * Maximizes the conditional likelihood with a given prior.
 *
 * @author Jenny Finkel
 * @author Sarah Spikes (Templatization)
 * @author Ramesh Nallapati (Made the function more general to support other AbstractCachingDiffFunctions involving the summation of two objective functions)
 */

public class SemiSupervisedLogConditionalObjectiveFunction extends AbstractCachingDiffFunction {

  AbstractCachingDiffFunction objFunc;
  //BiasedLogConditionalObjectiveFunction biasedObjFunc;  
  AbstractCachingDiffFunction biasedObjFunc; 
  double convexComboFrac = 0.5;

  LogPrior prior;
  
  public void setPrior(LogPrior prior) {
    this.prior = prior;
  }
  
  @Override
  public int domainDimension() {
    return objFunc.domainDimension();
  }

  @Override
  protected void calculate(double[] x) {
    if (derivative == null) {
      derivative = new double[domainDimension()];
    }
    
    value = convexComboFrac*objFunc.valueAt(x) + (1.0-convexComboFrac)*biasedObjFunc.valueAt(x);
    //value = objFunc.valueAt(x) + biasedObjFunc.valueAt(x);
    double[] d1 = objFunc.derivativeAt(x);
    double[] d2 = biasedObjFunc.derivativeAt(x);

    for (int i = 0; i < domainDimension(); i++) {
      derivative[i] = convexComboFrac*d1[i] + (1.0-convexComboFrac)*d2[i];
      //derivative[i] = d1[i] + d2[i];
    }
    if(prior != null)
      value += prior.compute(x, derivative);
  }

  public SemiSupervisedLogConditionalObjectiveFunction(AbstractCachingDiffFunction objFunc, AbstractCachingDiffFunction biasedObjFunc, LogPrior prior, double convexComboFrac) {
    this.objFunc = objFunc;
    this.biasedObjFunc = biasedObjFunc;
    this.prior = prior;
    this.convexComboFrac = convexComboFrac;    
    if(convexComboFrac < 0 || convexComboFrac > 1.0)
      throw new RuntimeException ("convexComboFrac has to lie between 0 and 1 (both inclusive).");
  }

  public SemiSupervisedLogConditionalObjectiveFunction(AbstractCachingDiffFunction objFunc, AbstractCachingDiffFunction biasedObjFunc, LogPrior prior) {
    //this.objFunc = objFunc;
    //this.biasedObjFunc = biasedObjFunc;
    //this.prior = prior;
    this(objFunc,biasedObjFunc,prior,0.5);
  }

}
