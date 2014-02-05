package edu.stanford.nlp.optimization;

import edu.stanford.nlp.math.ArrayMath;
import edu.stanford.nlp.util.Timing;

import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.*;

/**
 * Stochastic Gradient Descent With AdaGrad and FOBOS in batch mode.
 * Optionally, user can also turn on AdaDelta via option "useAdaDelta"
 * Similar to SGDMinimizer, regularization is done in the minimizer, not in the objective function.
 * This version is not efficient for online setting. For online variant, consider implementing SparseAdaGradMinimizer.java
 *
 * @author Mengqiu Wang
 */
public class SGDWithAdaGradAndFOBOS<T extends DiffFunction> implements Minimizer<T>, HasEvaluators {

  protected double[] x;
  protected double initRate;  // Initial stochastic iteration count
  protected double lambda;
  // when alpha = 1, sg-lasso is just lasso; when alpha = 0, sg-lasso is g-lasso
  protected double alpha = 1.0;
  protected boolean quiet = false;
  private static final int DEFAULT_NUM_PASSES = 50;
  protected final int numPasses; //-1;
  protected int bSize = 1;  // NOTE: If bSize does not divide evenly into total number of samples,
                            // some samples may get accounted for twice in one pass
  private static final int DEFAULT_TUNING_SAMPLES = Integer.MAX_VALUE;
  private static final int DEFAULT_BATCH_SIZE = 1000;
  private double eps = 1e-3;
  private double TOL = 1e-4;

  // fields for approximating Hessian to feed to QN
  public List<double[]> yList = null;
  public List<double[]> sList = null;
  public double[] diag;
  private int hessSampleSize = -1;
  private double[] s,y = null;

  protected Random gen = new Random(1);
  protected long maxTime = Long.MAX_VALUE;

  private int evaluateIters = 0;    // Evaluate every x iterations (0 = no evaluation)
  private Evaluator[] evaluators;  // separate set of evaluators to check how optimization is going
  private Prior prior = Prior.LASSO;

  private boolean useEvalImprovement = false;
  private boolean useAvgImprovement = false;
  private boolean suppressTestPrompt = false;
  private int terminateOnEvalImprovementNumOfEpoch = 1;
  private double bestEvalSoFar = Double.NEGATIVE_INFINITY;
  private double[] xBest;
  private int noImproveItrCount = 0;

  private boolean useAdaDelta = false;
  private boolean useAdaDiff = false;
  private double rho = 0.95;
  private double[] sumGradSquare;
  private double[] prevGrad, prevDeltaX;
  private double[] sumDeltaXSquare;

  public void setHessSampleSize(int hessSize) {
    this.hessSampleSize = hessSize;
    // (TODO) should initialize relevant data structure as well
  }

  public void terminateOnEvalImprovement(boolean toTerminate) {
    useEvalImprovement = toTerminate;
  }

  public void terminateOnAvgImprovement(boolean toTerminate, double tolerance) {
    useAvgImprovement = toTerminate;
    TOL = tolerance;
  }

  public void suppressTestPrompt(boolean suppressTestPrompt) {
    this.suppressTestPrompt = suppressTestPrompt;
  }

  public void setTerminateOnEvalImprovementNumOfEpoch(int terminateOnEvalImprovementNumOfEpoch) {
    this.terminateOnEvalImprovementNumOfEpoch = terminateOnEvalImprovementNumOfEpoch;
  }

  public boolean toContinue(double[] x, double currEval) {
    if (currEval >= bestEvalSoFar) {
      bestEvalSoFar = currEval;
      noImproveItrCount = 0;
      if (xBest == null)
        xBest = Arrays.copyOf(x, x.length);
      else
        System.arraycopy( x, 0, xBest, 0, x.length );
      return true;
    } else {
      noImproveItrCount += 1;
      return noImproveItrCount <= terminateOnEvalImprovementNumOfEpoch;
    }
  }

  public enum Prior {
    LASSO, RIDGE, GAUSSIAN, aeLASSO, gLASSO, sgLASSO, NONE
  }

  private static Prior getPrior(String priorType) {
    if (priorType.equals("none"))
      return Prior.NONE;
    else if (priorType.equals("lasso"))
      return Prior.LASSO;
    else if (priorType.equals("ridge"))
      return Prior.RIDGE;
    else if (priorType.equals("gaussian"))
      return Prior.GAUSSIAN;
    else if (priorType.equals("ae-lasso"))
      return Prior.aeLASSO;
    else if (priorType.equals("g-lasso"))
      return Prior.gLASSO;
    else if (priorType.equals("sg-lasso"))
      return Prior.sgLASSO;
    else
      throw new IllegalArgumentException("prior type " + priorType + " not recognized; supported priors "+
       "are: lasso, ridge, gaussian, ae-lasso, g-lasso, and sg-lasso");
  }

  public SGDWithAdaGradAndFOBOS(double initRate, double lambda, int numPasses) {
    this(initRate, lambda, numPasses, -1);
  }

  public SGDWithAdaGradAndFOBOS(double initRate, double lambda, int numPasses, int batchSize) {
    this(initRate, lambda, numPasses, batchSize, "lasso", 1.0, false, false, 1e-3, 0.95);
  }


  public SGDWithAdaGradAndFOBOS(double initRate, double lambda, int numPasses, 
      int batchSize, String priorType, double alpha, boolean useAdaDelta, boolean useAdaDiff, double adaGradEps, double adaDeltaRho)
  {
    this.initRate = initRate;
    this.prior = getPrior(priorType);
    this.bSize = batchSize;
    this.lambda = lambda;
    this.eps = adaGradEps;
    this.rho = adaDeltaRho;
    this.useAdaDelta = useAdaDelta;
    this.useAdaDiff = useAdaDiff;
    this.alpha = alpha;
    if (numPasses >= 0) {
      this.numPasses = numPasses;
    } else {
      this.numPasses = DEFAULT_NUM_PASSES;
      sayln("  SGDWithAdaGradAndFOBOS: numPasses=" + numPasses + ", defaulting to " + this.numPasses);
    }
  }

  public void shutUp() {
    this.quiet = true;
  }

  private static final NumberFormat nf = new DecimalFormat("0.000E0");

  protected String getName() {
    return "SGDWithAdaGradAndFOBOS" + bSize + "_lambda" + nf.format(lambda) + "_alpha" + nf.format(alpha);
  }

  @Override
  public void setEvaluators(int iters, Evaluator[] evaluators)
  {
    this.evaluateIters = iters;
    this.evaluators = evaluators;
  }

  // really this is the the L2 norm....
  private static double getNorm(double[] w)
  {
    double norm = 0;
    for (int i = 0; i < w.length; i++) {
      norm += w[i]*w[i];
    }
    return Math.sqrt(norm);
  }

  private double doEvaluation(double[] x) {
    // Evaluate solution
    if (evaluators == null) return Double.NEGATIVE_INFINITY;
    double score = Double.NEGATIVE_INFINITY;
    for (Evaluator eval:evaluators) {
      if (!suppressTestPrompt)
        sayln("  Evaluating: " + eval.toString());
      double aScore = eval.evaluate(x);
      if (aScore != Double.NEGATIVE_INFINITY)
        score = aScore;
    }
    return score;
  }

  private static double pospart(double number) {
    return number > 0.0 ? number : 0.0;
  }

  /*
  private void approxHessian(double[] newX) {
    for(int i = 0; i < x.length; i++){
      double thisGain = fixedGain*gainSchedule(k,5*numBatches)/(diag[i]);
      newX[i] = x[i] - thisGain*grad[i];
    }

    //Get a new pair...
    say(" A ");
    if (hessSampleSize > 0 && sList.size() == hessSampleSize || sList.size() == hessSampleSize) {
      s = sList.remove(0);
      y = yList.remove(0);
    } else {
      s = new double[x.length];
      y = new double[x.length];
    }

    s = prevDeltaX;

    s = ArrayMath.pairwiseSubtract(newX, x);
    dfunction.recalculatePrevBatch = true;
    System.arraycopy(dfunction.derivativeAt(newX,bSize),0,y,0,grad.length);

    ArrayMath.pairwiseSubtractInPlace(y,newGrad);  // newY = newY-newGrad
    double[] comp = new double[x.length];

    sList.add(s);
    yList.add(y);
    ScaledSGDMinimizer.updateDiagBFGS(diag,s,y);
  }
  */

  private double computeLearningRate(int index, double grad) {
    // double eps2 = 1e-12;
    double currentRate = Double.NEGATIVE_INFINITY;
    double prevG = prevGrad[index];
    double gradDiff = grad-prevG;
    if (useAdaDelta) {
      double deltaXt = prevDeltaX[index];
      sumDeltaXSquare[index] = sumDeltaXSquare[index] * rho + (1-rho) * deltaXt * deltaXt;

      if (useAdaDiff) {
        sumGradSquare[index] = sumGradSquare[index] * rho + (1-rho) * (gradDiff) * (gradDiff);
      } else {
        sumGradSquare[index] = sumGradSquare[index] * rho + (1-rho) * grad * grad;
      }

      // double nominator = initRate;
      // if (sumDeltaXSquare[index] > 0) {
      //   nominator = Math.sqrt(sumDeltaXSquare[index]+eps);
      // }
      // currentRate = nominator / Math.sqrt(sumGradSquare[index]+eps);
      currentRate = Math.sqrt(sumDeltaXSquare[index]+eps) / Math.sqrt(sumGradSquare[index]+eps);

      // double deltaXt = currentRate * grad;
      // sumDeltaXSquare[index] = sumDeltaXSquare[index] * rho + (1-rho) * deltaXt * deltaXt;
    } else {
      if (useAdaDiff) {
        sumGradSquare[index] += gradDiff * gradDiff;
      } else {
        sumGradSquare[index] += grad * grad;
      }
      // apply AdaGrad
      currentRate = initRate / Math.sqrt(sumGradSquare[index]+eps);
    }
    // prevDeltaX[index] = grad * currentRate;
    return currentRate;
  }

  private void updateX(double[] x, int index, double realUpdate) {
    prevDeltaX[index] = realUpdate - x[index];
    x[index] = realUpdate;
  }

  @Override
  public double[] minimize(DiffFunction function, double functionTolerance, double[] initial) {
    return minimize(function, functionTolerance, initial, -1);
  }

  @Override
  public double[] minimize(DiffFunction f, double functionTolerance, double[] initial, int maxIterations) {
    int totalSamples = 0;
    sayln("Using lambda=" + lambda);
    if (f instanceof AbstractStochasticCachingDiffUpdateFunction) {
      AbstractStochasticCachingDiffUpdateFunction func  = (AbstractStochasticCachingDiffUpdateFunction) f;
      func.sampleMethod = AbstractStochasticCachingDiffFunction.SamplingMethod.Shuffled;
      totalSamples = func.dataDimension();
      if (bSize > totalSamples) {
        System.err.println("WARNING: Total number of samples=" + totalSamples +
                " is smaller than requested batch size=" + bSize + "!!!");
        bSize = totalSamples;
        sayln("Using batch size=" + bSize);
      }
      if (bSize <= 0) {
        System.err.println("WARNING: Requested batch size=" + bSize + " <= 0 !!!");
        bSize = totalSamples;
        sayln("Using batch size=" + bSize);
      }
    }

    x = new double[initial.length];
    double[] testUpdateCache = null, currentRateCache = null, bCache = null;
    sumGradSquare = new double[initial.length];
    prevGrad = new double[initial.length];
    prevDeltaX = new double[initial.length];
    if (useAdaDelta) {
      sumDeltaXSquare = new double[initial.length];
      if (prior != Prior.NONE && prior != Prior.GAUSSIAN) {
        throw new UnsupportedOperationException("useAdaDelta is currently only supported for Prior.NONE or Prior.GAUSSIAN"); 
      }
    }

    int[][] featureGrouping = null;
    if (prior != Prior.LASSO && prior != Prior.NONE) {
      testUpdateCache = new double[initial.length];
      currentRateCache = new double[initial.length];
    }
    if (prior != Prior.LASSO && prior != Prior.RIDGE && prior != Prior.GAUSSIAN) {
      if (!(f instanceof HasFeatureGrouping)) {
        throw new UnsupportedOperationException("prior is specified to be ae-lasso or g-lasso, but function does not support feature grouping");
      }
      featureGrouping = ((HasFeatureGrouping)f).getFeatureGrouping();
    }
    if (prior == Prior.sgLASSO) {
      bCache = new double[initial.length];
    }

    System.arraycopy(initial, 0, x, 0, x.length);

    int numBatches =  1;
    if (f instanceof AbstractStochasticCachingDiffUpdateFunction) {
      if (totalSamples > 0)
        numBatches = totalSamples / bSize;
    }

    boolean have_max = (maxIterations > 0 || numPasses > 0);

    if (!have_max){
      throw new UnsupportedOperationException("No maximum number of iterations has been specified.");
    } else{
      maxIterations = Math.max(maxIterations, numPasses*numBatches);
    }

    sayln("       Batch size of: " + bSize);
    sayln("       Data dimension of: " + totalSamples );
    sayln("       Batches per pass through data:  " + numBatches );
    sayln("       Number of passes is = " + numPasses);
    sayln("       Max iterations is = " + maxIterations);

    //!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
    //!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
    //            Loop
    //!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
    //!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!

    Timing total = new Timing();
    Timing current = new Timing();
    total.start();
    current.start();
    int iters = 0;
    double gValue = 0;
    double wValue = 0;
    double currentRate = 0, testUpdate = 0, realUpdate = 0;

    List<Double> values = null; 
    double oldObjVal = 0;
    for (int pass = 0; pass < numPasses; pass++)  {
      boolean doEval = (pass > 0 && evaluateIters > 0 && pass % evaluateIters == 0);
      double evalScore = Double.NEGATIVE_INFINITY;
      if (doEval) {
        evalScore = doEvaluation(x);
        if (useEvalImprovement && !toContinue(x, evalScore))
          break;
      }
      // TODO: currently objVal is only updated for GAUSSIAN prior
      // when other priors are used, objVal only reflects the un-regularized obj value
      double objVal = Double.NEGATIVE_INFINITY;
      double objDelta = Double.NEGATIVE_INFINITY;

      say("Iter: " + iters + " pass " + pass + " batch 1 ... ");
      int numOfNonZero = 0, numOfNonZeroGroup = 0;
      String gSizeStr = "";
      for (int batch = 0; batch < numBatches; batch++) {
        iters++;

        //Get the next gradients
        // System.err.println("getting gradients");
        double[] gradients = null;
        if (f instanceof AbstractStochasticCachingDiffUpdateFunction) {
          AbstractStochasticCachingDiffUpdateFunction func = (AbstractStochasticCachingDiffUpdateFunction) f;
          if (bSize == totalSamples) {
            objVal = func.valueAt(x);
            gradients = func.getDerivative();
            objDelta = objVal-oldObjVal;
            oldObjVal = objVal;
            if (values == null)
              values = new ArrayList<Double>();
            values.add(objVal);
          } else {
            func.calculateStochasticGradient(x, bSize);
            gradients = func.getDerivative();
          }
        } else if (f instanceof AbstractCachingDiffFunction) {
          AbstractCachingDiffFunction func = (AbstractCachingDiffFunction) f;
          gradients = func.derivativeAt(x);
        }

        // System.err.println("applying regularization");
        if (prior == Prior.NONE || prior == Prior.GAUSSIAN) { // Gaussian prior is also handled in objective
          for (int index = 0; index < x.length; index++) {
            gValue = gradients[index];
            currentRate = computeLearningRate(index, gValue);
            // arrive at x(t+1/2)
            wValue = x[index];
            testUpdate = wValue - (currentRate * gValue);
            realUpdate = testUpdate;
            updateX(x, index, realUpdate);
            // x[index] = testUpdate;
          }
        } else if (prior == Prior.LASSO || prior == Prior.RIDGE) {
          double testUpdateSquaredSum = 0;
          Set<Integer> paramRange = null;
          if (f instanceof HasRegularizerParamRange) {
            paramRange = ((HasRegularizerParamRange)f).getRegularizerParamRange(x);
          } else {
            paramRange = new HashSet<Integer>();
            for (int i = 0; i < x.length; i++)
              paramRange.add(i);
          }

          for (int index : paramRange) {
            gValue = gradients[index];
            currentRate = computeLearningRate(index, gValue);
            // arrive at x(t+1/2)
            wValue = x[index];
            testUpdate = wValue - (currentRate * gValue);
            double currentLambda = currentRate * lambda;
            // apply FOBOS
            if (prior == Prior.LASSO) {
              realUpdate = Math.signum(testUpdate) * pospart(Math.abs(testUpdate) - currentLambda);
              updateX(x, index, realUpdate);
              if (realUpdate != 0)
                numOfNonZero++;
            } else if (prior == Prior.RIDGE) {
              testUpdateSquaredSum += testUpdate*testUpdate;
              testUpdateCache[index] = testUpdate;
              currentRateCache[index] = currentRate;
            // } else if (prior == Prior.GAUSSIAN) { // GAUSSIAN prior is assumed to be handled in the objective directly
            //   realUpdate = testUpdate / (1 + currentLambda);
            //   updateX(x, index, realUpdate);
            //   // update objVal
            //   objVal += currentLambda * wValue * wValue;
            }
          }
          if (prior == Prior.RIDGE) {
            double testUpdateNorm = Math.sqrt(testUpdateSquaredSum);
            for (int index = 0 ; index < testUpdateCache.length; index++) {
              realUpdate = testUpdateCache[index] * pospart( 1 - currentRateCache[index] * lambda / testUpdateNorm );
              updateX(x, index, realUpdate);
              if (realUpdate != 0)
                numOfNonZero++;
            }
          }
        } else {
          // System.err.println("featureGroup.length: " + featureGrouping.length);
          for (int gIndex = 0; gIndex < featureGrouping.length; gIndex++) {
            int[] gFeatureIndices = featureGrouping[gIndex];
            // if (gIndex % 100 == 0) System.err.print(gIndex+" ");
            double testUpdateSquaredSum = 0;
            double testUpdateAbsSum = 0;
            double M = gFeatureIndices.length;
            double dm = Math.log(M);
            for (int index : gFeatureIndices) {
              gValue = gradients[index];
              currentRate = computeLearningRate(index, gValue);
              // arrive at x(t+1/2)
              wValue = x[index];
              testUpdate = wValue - (currentRate * gValue);
              testUpdateSquaredSum += testUpdate*testUpdate;
              testUpdateAbsSum += Math.abs(testUpdate);
              testUpdateCache[index] = testUpdate;
              currentRateCache[index] = currentRate;
            }
            if (prior == Prior.gLASSO) {
              double testUpdateNorm = Math.sqrt(testUpdateSquaredSum);
              boolean groupHasNonZero = false;
              for (int index : gFeatureIndices) {
                realUpdate = testUpdateCache[index] * pospart( 1 - currentRateCache[index] * lambda * dm / testUpdateNorm );
                updateX(x, index, realUpdate);
                if (realUpdate != 0) {
                  numOfNonZero++;
                  groupHasNonZero = true;
                }
              }
              if (groupHasNonZero)
                numOfNonZeroGroup++;
            } else if (prior == Prior.aeLASSO) {
              int nonZeroCount = 0;
              boolean groupHasNonZero = false;
              for (int index : gFeatureIndices) {
                double tau = currentRateCache[index] * lambda / (1 + currentRateCache[index] * lambda * M) * testUpdateAbsSum;
                realUpdate = Math.signum(testUpdateCache[index]) * pospart(Math.abs(testUpdateCache[index]) - tau);
                updateX(x, index, realUpdate);
                if (realUpdate != 0) {
                  numOfNonZero++;
                  nonZeroCount++;
                  groupHasNonZero = true;
                }
              }
              if (groupHasNonZero)
                numOfNonZeroGroup++;
              // gSizeStr += nonZeroCount+",";
            } else if (prior == Prior.sgLASSO) {
              double bSquaredSum = 0, b = 0;
              for (int index : gFeatureIndices) {
                b = Math.signum(testUpdateCache[index]) * pospart(Math.abs(testUpdateCache[index]) -
                  currentRateCache[index] * alpha * lambda);
                bCache[index] = b;
                bSquaredSum += b * b;
              }
              double bNorm = Math.sqrt(bSquaredSum);
              int nonZeroCount = 0;
              boolean groupHasNonZero = false;
              for (int index : gFeatureIndices) {
                realUpdate = bCache[index] * pospart( 1 - currentRateCache[index] * (1.0-alpha) * lambda * dm / bNorm );
                updateX(x, index, realUpdate);
                if (realUpdate != 0) {
                  numOfNonZero++;
                  nonZeroCount++;
                  groupHasNonZero = true;
                }
              }
              if (groupHasNonZero) {
                numOfNonZeroGroup++;
                // gSizeStr += nonZeroCount+",";
              }
            }
          }
          // System.err.println();
        }

        // update gradient and lastX
        for (int index = 0; index < x.length; index++) {
          prevGrad[index] = gradients[index];
        }

        // if (hessSampleSize > 0) {
        //   approxHessian();
        // }
      }

      try {
        ArrayMath.assertFinite(x,"x");
      } catch (ArrayMath.InvalidElementException e) {
        System.err.println(e.toString());
        for(int i=0;i<x.length;i++){ x[i]=Double.NaN; }
        break;
      }
      sayln(String.valueOf(numBatches)+", n0-fCount:" + numOfNonZero + ((prior != Prior.LASSO && prior != Prior.RIDGE)? ", n0-gCount:"+numOfNonZeroGroup : "") + ((evalScore != Double.NEGATIVE_INFINITY) ? ", evalScore:"+evalScore : "") + (objVal != Double.NEGATIVE_INFINITY ? ", obj_val:" + nf.format(objVal) + ", obj_delta:" + objDelta : "") );

      if (values != null && useAvgImprovement && iters > 5) {
        int size = values.size();
        double previousVal = (size >= 10 ? values.get(size - 10) : values.get(0));
        double averageImprovement = (previousVal - objVal) / (size >= 10 ? 10 : size);
        if (Math.abs(averageImprovement / objVal) < TOL) {
          sayln("Online Optmization completed, due to average improvement: | newest_val - previous_val | / |newestVal| < TOL ");
          break;
        }
      }

      if (iters >= maxIterations) {
        sayln("Online Optimization complete.  Stopped after max iterations");
        break;
      }

      if (total.report() >= maxTime){
        sayln("Online Optimization complete.  Stopped after max time");
        break;
      }
    }
    if (evaluateIters > 0) {
      // do final evaluation
      double evalScore = (useEvalImprovement ? doEvaluation(xBest) : doEvaluation(x));
      sayln("final evalScore is: " + evalScore);
    }

    sayln("Completed in: " + Timing.toSecondsString(total.report()) + " s");

    return (useEvalImprovement ? xBest : x);
  }

  protected void sayln(String s) {
    if (!quiet) {
      System.err.println(s);
    }
  }

  protected void say(String s) {
    if (!quiet) {
      System.err.print(s);
    }
  }

}
