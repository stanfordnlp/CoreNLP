package edu.stanford.nlp.optimization;


/**
 * Hybrid Minimizer is set up as a combination of two minimizers.  The first minimizer will ideally
 * quickly converge regardless of proximity to the true minimum, while the second minimizer would
 * generally be a quadratic method, that is only fully quadratic near the solution.
 *
 * If you read this, send me an e-mail saying, "Alex!  You should finish adding the description to
 * the Hybrid Minimizer!"
 *
 * @author <a href="mailto:akleeman@stanford.edu">Alex Kleeman</a>
 * @version 1.0
 * @since 1.0
 */
public class HybridMinimizer implements Minimizer<DiffFunction>, HasEvaluators {

  Minimizer<DiffFunction> firstMinimizer = new SMDMinimizer<DiffFunction>();
  Minimizer<DiffFunction> secondMinimizer = new QNMinimizer(15);
  int iterationCutoff = 1000;

  public HybridMinimizer(Minimizer<DiffFunction> minimizerOne, Minimizer<DiffFunction> minimizerTwo, int iterationCutoff){
    this.firstMinimizer = minimizerOne;
    this.secondMinimizer = minimizerTwo;
    this.iterationCutoff = iterationCutoff;
  }

  public void setEvaluators(int iters, Evaluator[] evaluators) {
    if (firstMinimizer instanceof HasEvaluators) {
      ((HasEvaluators) firstMinimizer).setEvaluators(iters, evaluators);
    }
    if (secondMinimizer instanceof HasEvaluators) {
      ((HasEvaluators) secondMinimizer).setEvaluators(iters, evaluators);
    }
  }


  public double[] minimize(DiffFunction function, double functionTolerance, double[] initial) {
    return minimize(function, functionTolerance, initial, -1);
  }


  public double[] minimize(DiffFunction function, double functionTolerance, double[] initial, int maxIterations) {
    double[] x = firstMinimizer.minimize(function,functionTolerance,initial,iterationCutoff);
    return secondMinimizer.minimize(function,functionTolerance,x,maxIterations);
  }

}

