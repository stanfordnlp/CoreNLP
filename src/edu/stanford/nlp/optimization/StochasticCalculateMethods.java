package edu.stanford.nlp.optimization;

/**
 * This enumeratin was created to organize the selection of different methods for stochastic
 * calculations.  It was also created for use with Stochastic Meta Descent (SMDMinimizer) due
 * to the need for Hessian Vector Products, and the inefficiency of continuing to calculate these
 * vector products in other minimization methods like Stochastic Gradient Descent (SGDMinimizer)
 *
 * @author Alex Kleeman (akleeman@stanford.edu)
 */
public enum StochasticCalculateMethods {

  NoneSpecified(false),
  /*  Used for procedures like Stochastic Gradient Descent */
  GradientOnly (false),
  /*  This is used with the Objective Function can handle calculations using Algorithmic Differentiation*/
  AlgorithmicDifferentiation (true),
  /*  It is often more efficient to calculate the Finite difference within one single for loop,
      if the objective function can handle this, this method should be used instead of
       ExternalFiniteDifference
   */
  IncorporatedFiniteDifference (true),
  /*  ExternalFiniteDifference uses two calls to the objective function to come up with an approximation of
      the H.v
   */
  ExternalFiniteDifference (false);


  /*
  *This boolean is true if the Objective Function is required to calculate the hessian vector product
  *   In the case of ExternalFiniteDifference this is false since two calls are made to the objective
  *   function.
  */
  private boolean objFuncCalculatesHdotV;

  StochasticCalculateMethods(boolean ObjectiveFunctionCalculatesHdotV){
    this.objFuncCalculatesHdotV = ObjectiveFunctionCalculatesHdotV;
  }

  public boolean calculatesHessianVectorProduct(){
    return objFuncCalculatesHdotV;
  }

  public static StochasticCalculateMethods parseMethod(String method) {
    if (method.equalsIgnoreCase("AlgorithmicDifferentiation")){
      return StochasticCalculateMethods.AlgorithmicDifferentiation;
    } else if(method.equalsIgnoreCase("IncorporatedFiniteDifference")){
      return StochasticCalculateMethods.IncorporatedFiniteDifference ;
    } else if(method.equalsIgnoreCase("ExternalFinitedifference")){
      return StochasticCalculateMethods.ExternalFiniteDifference ;
    } else {
      return null;
    }
  }

}
