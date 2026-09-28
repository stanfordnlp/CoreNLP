package edu.stanford.nlp.classify;
import edu.stanford.nlp.util.logging.Redwood;

import java.lang.reflect.Array;
import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.CountDownLatch;

import edu.stanford.nlp.ling.Datum;
import edu.stanford.nlp.math.ADMath;
import edu.stanford.nlp.math.ArrayMath;
import edu.stanford.nlp.math.DoubleAD;
import edu.stanford.nlp.optimization.AbstractStochasticCachingDiffUpdateFunction;
import edu.stanford.nlp.optimization.StochasticCalculateMethods;
import edu.stanford.nlp.util.ArgumentParser;
import edu.stanford.nlp.util.Index;
import edu.stanford.nlp.util.RuntimeInterruptedException;


/**
 * Maximizes the conditional likelihood with a given prior.
 *
 * @author Dan Klein
 * @author Galen Andrew
 * @author Chris Cox (merged w/ SumConditionalObjectiveFunction, 2/16/05)
 * @author Sarah Spikes (Templatization, allowing an {@code Iterable<Datum<L, F>>} to be passed in instead of a {@code GeneralDataset<L, F>})
 * @author Angel Chang (support in place SGD - extend AbstractStochasticCachingDiffUpdateFunction)
 * @author Christopher Manning (cleaned out the cruft and sped it up in 2014)
 * @author Keenon Werling added some multithreading to the batch evaluations
 *
 * @param <L> The type of the labels
 * @param <F> The type of the features
 */

public class LogConditionalObjectiveFunction<L, F> extends AbstractStochasticCachingDiffUpdateFunction  {

  /** A logger for this class */
  private static Redwood.RedwoodChannels log = Redwood.channels(LogConditionalObjectiveFunction.class);

  /** The prior (regularizer) added to the objective. */
  protected final LogPrior prior;

  /** The number of features. */
  protected final int numFeatures;
  /** The number of classes. */
  protected final int numClasses;

  /** Normally, this contains the data. The first index is the datum number,
   *  and then there is an array of feature indices for each datum.
   */
  protected final int[][] data;
  /** Alternatively, the data may be available from an Iterable in not yet
   *  indexed form.  (In 2014, it's not clear any code actually uses this option.)
   *  And then you need an index for both.
   */
  protected final Iterable<Datum<L, F>> dataIterable;
  /** The label index for {@link #dataIterable}; null otherwise. */
  protected final Index<L> labelIndex;
  /** The feature index for {@link #dataIterable}; null otherwise. */
  protected final Index<F> featureIndex;

  /** Same size as data if the features have values; null if the features are binary. */
  protected final double[][] values;
  /** The label of each data index. */
  protected final int[] labels;

  /** The weight of each datum, or null if all datums have weight 1. */
  protected final float[] dataWeights;

  /** Whether to use summed conditional likelihood rather than (product) conditional likelihood. */
  protected final boolean useSummedConditionalLikelihood; //whether to use sumConditional or logConditional

  /** This is used to cache the numerator in batch methods. */
  protected double[] derivativeNumerator = null;

  /** The only reason this is around is because the Prior Functions don't handle stochastic calculations yet. */
  protected double [] priorDerivative = null;

  /** The flag to tell the gradient computations to multithread over the data.
   *  Only used when {@link #threads} is greater than 1.
   */
  protected boolean parallelGradientCalculation = true;

  /** The number of threads to use when {@link #parallelGradientCalculation} is set. */
  protected int threads = ArgumentParser.threads;

  @Override
  public int domainDimension() {
    return numFeatures * numClasses;
  }

  @Override
  public int dataDimension(){
    return data.length;
  }

  private int classOf(int index) {
    return index % numClasses;
  }

  private int featureOf(int index) {
    return index / numClasses;
  }

  /**
   * Converts a Phi feature number and class index into an f(x,y) feature index.
   *
   * @param f The feature number
   * @param c The class index
   * @return The index of that (feature, class) pair in the weight vector
   */
  // [cdm2014: Tried inline this; no big gains.]
  protected int indexOf(int f, int c) {
    return f * numClasses + c;
  }

  /**
   * Reshapes a weight vector into a feature by class matrix.
   *
   * @param x A weight vector of size {@link #domainDimension()}
   * @return A new {@code numFeatures x numClasses} array
   */
  public double[][] to2D(double[] x) {
    double[][] x2 = new double[numFeatures][numClasses];
    for (int i = 0; i < numFeatures; i++) {
      for (int j = 0; j < numClasses; j++) {
        x2[i][j] = x[indexOf(i, j)];
      }
    }
    return x2;
  }

  /**
   * Calculate the conditional likelihood.
   * If {@code useSummedConditionalLikelihood} is {@code false} (the default),
   * this calculates standard(product) CL, otherwise this calculates summed CL.
   * What's the difference?  See Klein and Manning's 2002 EMNLP paper.
   */
  @Override
  protected void calculate(double[] x) {
    //If the batchSize is 0 then use the regular calculate methods
    if (useSummedConditionalLikelihood) {
      calculateSCL(x);
    } else {
      calculateCL(x);
    }
  }


  /**
   * This function is used to come up with an estimate of the value / gradient based on only a small
   * portion of the data (referred to as the batchSize for lack of a better term.  In this case batch does
   * not mean All!!  It should be thought of in the sense of "a small batch of the data".
   */
  @Override
  public void calculateStochastic(double[] x, double[] v, int[] batch) {

    if(method.calculatesHessianVectorProduct() && v != null){
      //  This is used for Stochastic Methods that involve second order information (SMD for example)
      if(method.equals(StochasticCalculateMethods.AlgorithmicDifferentiation)){
        calculateStochasticAlgorithmicDifferentiation(x,v,batch);
      }else if(method.equals(StochasticCalculateMethods.IncorporatedFiniteDifference)){
        calculateStochasticFiniteDifference(x,v,finiteDifferenceStepSize,batch);
      }
    } else{
      //This is used for Stochastic Methods that don't need anything but the gradient (SGD)
      calculateStochasticGradientLocal(x,batch);
    }

  }


  /**
   * Calculate the summed conditional likelihood of this data by summing
   * conditional estimates.
   *
   */
  private void calculateSCL(double[] x) {
    //System.out.println("Checking at: "+x[0]+" "+x[1]+" "+x[2]);
    value = 0.0;
    Arrays.fill(derivative, 0.0);
    double[] sums = new double[numClasses];
    double[] probs = new double[numClasses];
    // double[] counts = new double[numClasses];
    // Arrays.fill(counts, 0.0); // not needed; Java arrays zero initialized
    for (int d = 0; d < data.length; d++) {
      int[] features = data[d];
      // activation
      Arrays.fill(sums, 0.0);
      for (int c = 0; c < numClasses; c++) {
        for (int feature : features) {
          int i = indexOf(feature, c);
          sums[c] += x[i];
        }
      }
      // expectation (slower routine replaced by fast way)
      // double total = Double.NEGATIVE_INFINITY;
      // for (int c=0; c<numClasses; c++) {
      //   total = SloppyMath.logAdd(total, sums[c]);
      // }
      double total = ArrayMath.logSum(sums);
      int ld = labels[d];
      for (int c = 0; c < numClasses; c++) {
        probs[c] = Math.exp(sums[c] - total);
        for (int feature : features) {
          int i = indexOf(feature, c);
          derivative[i] += probs[ld] * probs[c];
        }
      }
      // observed
      for (int feature : features) {
        int i = indexOf(feature, labels[d]);
        derivative[i] -= probs[ld];
      }
      value -= probs[ld];
    }
    // priors
    if (true) {
      for (int i = 0; i < x.length; i++) {
        double k = 1.0;
        double w = x[i];
        value += k * w * w / 2.0;
        derivative[i] += k * w;
      }
    }
  }

  /**
   * Calculate the conditional likelihood of this data by multiplying
   * conditional estimates. Full dataset batch estimation.
   */
  private void calculateCL(double[] x) {
    if (values != null) {
      rvfcalculate(x);
    } else if (dataIterable != null) {
      calculateCLiterable(x);
    } else {
      calculateCLbatch(x);
    }
  }

  private class CLBatchDerivativeCalculation implements Runnable {
    int numThreads;
    int threadIdx;
    double localValue = 0.0;
    double[] x;
    int[] batch;
    double[] localDerivative;
    CountDownLatch latch;

    public CLBatchDerivativeCalculation(int numThreads, int threadIdx, int[] batch, double[] x, int derivativeSize, CountDownLatch latch) {
      this.numThreads = numThreads;
      this.threadIdx = threadIdx;
      this.x = x;
      this.batch = batch;
      this.localDerivative = new double[derivativeSize];
      this.latch = latch;
    }

    @Override
    public void run() {
      double[] sums = new double[numClasses];
      double[] probs = new double[numClasses];

      // TODO: could probably get slightly better speedup if threads took linear subsequences, for cacheing
      int batchSize = batch == null ? data.length : batch.length;
      for (int m = threadIdx; m < batchSize; m += numThreads) {
        int d = batch == null ? m : batch[m];

        // activation
        Arrays.fill(sums, 0.0);

        int[] featuresArr = data[d];

        for (int c = 0; c < numClasses; c++) {
          for (int feature : featuresArr) {
            int i = indexOf(feature, c);
            sums[c] += x[i];
          }
        }
        // expectation (slower routine replaced by fast way)
        // double total = Double.NEGATIVE_INFINITY;
        // for (int c=0; c<numClasses; c++) {
        //   total = SloppyMath.logAdd(total, sums[c]);
        // }
        double total = ArrayMath.logSum(sums);
        for (int c = 0; c < numClasses; c++) {
          probs[c] = Math.exp(sums[c] - total);
          if (dataWeights != null) {
            probs[c] *= dataWeights[d];
          }
        }

        for (int c = 0; c < numClasses; c++) {
          for (int feature : featuresArr) {
            int i = indexOf(feature, c);
            localDerivative[i] += probs[c];
          }
        }

        int labelindex = labels[d];
        double dV = sums[labelindex] - total;
        if (dataWeights != null) {
          dV *= dataWeights[d];
        }
        localValue -= dV;
      }

      latch.countDown();
    }
  }

  private void calculateCLbatch(double[] x) {
    //System.out.println("Checking at: "+x[0]+" "+x[1]+" "+x[2]);
    value = 0.0;
    // [cdm Mar 2014] This next bit seems unnecessary: derivative is allocated by ensure() in AbstractCachingDiffFunction
    // before calculate() is called; and after the next block, derivativeNumerator is copied into it.
    // if (derivative == null) {
    //   derivative = new double[x.length];
    // } else {
    //   Arrays.fill(derivative, 0.0);
    // }

    if (derivativeNumerator == null) {
      derivativeNumerator = new double[x.length];
      for (int d = 0; d < data.length; d++) {
        int[] features = data[d];
        for (int feature : features) {
          int i = indexOf(feature, labels[d]);
          if (dataWeights == null) {
            derivativeNumerator[i] -= 1;
          } else {
            derivativeNumerator[i] -= dataWeights[d];
          }
        }
      }
    }

    copy(derivative, derivativeNumerator);
    //    Arrays.fill(derivative, 0.0);
    //    double[] counts = new double[numClasses];
    //    Arrays.fill(counts, 0.0);

    if (parallelGradientCalculation && threads > 1) {
      // Launch several threads (reused out of our fixed pool) to handle the computation
      @SuppressWarnings("unchecked")
      CLBatchDerivativeCalculation[] runnables = (CLBatchDerivativeCalculation[])Array.newInstance(CLBatchDerivativeCalculation.class, threads);
      CountDownLatch latch = new CountDownLatch(threads);
      for (int i = 0; i < threads; i++) {
        runnables[i] = new CLBatchDerivativeCalculation(threads, i, null, x, derivative.length, latch);
        new Thread(runnables[i]).start();
      }
      try {
        latch.await();
      } catch (InterruptedException e) {
        throw new RuntimeInterruptedException(e);
      }

      for (int i = 0; i < threads; i++) {
        value += runnables[i].localValue;
        for (int j = 0; j < derivative.length; j++) {
          derivative[j] += runnables[i].localDerivative[j];
        }
      }
    }
    else {
      double[] sums = new double[numClasses];
      double[] probs = new double[numClasses];

      for (int d = 0; d < data.length; d++) {
        // activation
        Arrays.fill(sums, 0.0);

        int[] featuresArr = data[d];

        for (int feature : featuresArr) {
          for (int c = 0; c < numClasses; c++) {
            int i = indexOf(feature, c);
            sums[c] += x[i];
          }
        }
        // expectation (slower routine replaced by fast way)
        // double total = Double.NEGATIVE_INFINITY;
        // for (int c=0; c<numClasses; c++) {
        //   total = SloppyMath.logAdd(total, sums[c]);
        // }
        double total = ArrayMath.logSum(sums);
        for (int c = 0; c < numClasses; c++) {
          probs[c] = Math.exp(sums[c] - total);
          if (dataWeights != null) {
            probs[c] *= dataWeights[d];
          }
        }

        for (int feature : featuresArr) {
          for (int c = 0; c < numClasses; c++) {
            int i = indexOf(feature, c);
            derivative[i] += probs[c];
          }
        }

        int labelindex = labels[d];
        double dV = sums[labelindex] - total;
        if (dataWeights != null) {
          dV *= dataWeights[d];
        }
        value -= dV;
      }
    }

    value += prior.compute(x, derivative);
  }


  private void calculateCLiterable(double[] x) {
    //System.out.println("Checking at: "+x[0]+" "+x[1]+" "+x[2]);
    value = 0.0;
    // [cdm Mar 2014] This next bit seems unnecessary: derivative is allocated by ensure() in AbstractCachingDiffFunction
    // before calculate() is called; and after the next block, derivativeNumerator is copied into it.
    // if (derivative == null) {
    //   derivative = new double[x.length];
    // } else {
    //   Arrays.fill(derivative, 0.0);
    // }

    if (derivativeNumerator == null) {
      derivativeNumerator = new double[x.length];
      //use dataIterable if data is null & vice versa
      //TODO: Make sure this work as expected!!
      //int index = 0;
      for (Datum<L, F> datum : dataIterable) {
        Collection<F> features = datum.asFeatures();
        for (F feature : features) {
          int i = indexOf(featureIndex.indexOf(feature), labelIndex.indexOf(datum.label()));
          if (dataWeights == null) {
            derivativeNumerator[i] -= 1;
          } /*else {
              derivativeNumerator[i] -= dataWeights[index];
            }*/
        }
      }
    }

    copy(derivative, derivativeNumerator);
    //    Arrays.fill(derivative, 0.0);
    double[] sums = new double[numClasses];
    double[] probs = new double[numClasses];
    //    double[] counts = new double[numClasses];
    //    Arrays.fill(counts, 0.0);

    for (Datum<L, F> datum : dataIterable) {
      // activation
      Arrays.fill(sums, 0.0);
      Collection<F> features = datum.asFeatures();
      for (F feature : features) {
        for (int c = 0; c < numClasses; c++) {
          int i = indexOf(featureIndex.indexOf(feature), c);
          sums[c] += x[i];
        }
      }
      // expectation (slower routine replaced by fast way)
      // double total = Double.NEGATIVE_INFINITY;
      // for (int c=0; c<numClasses; c++) {
      //   total = SloppyMath.logAdd(total, sums[c]);
      // }
      double total = ArrayMath.logSum(sums);
      for (int c = 0; c < numClasses; c++) {
        probs[c] = Math.exp(sums[c] - total);
      }

      for (F feature : features) {
        for (int c = 0; c < numClasses; c++) {
          int i = indexOf(featureIndex.indexOf(feature), c);
          derivative[i] += probs[c];
        }
      }

      int label = this.labelIndex.indexOf(datum.label());
      double dV = sums[label] - total;
      value -= dV;
    }

    value += prior.compute(x, derivative);
  }


  /**
   * Calculate the value and gradient on a batch of the data, along with
   * an approximation of the Hessian-vector product {@code H.v},
   * stored in {@code HdotV}, computed by finite differences.
   * For real-valued features, this falls back to the full-data
   * calculation of {@link #rvfcalculate(double[])} instead.
   *
   * @param x The point at which to calculate
   * @param v The vector to multiply by the Hessian
   * @param h The finite difference step size
   * @param batch Indices of the datums to use
   */
  public void calculateStochasticFiniteDifference(double[] x,double[] v, double h, int[] batch){
    //  THOUGHTS:
    //  does applying the renormalization (g(x+hv)-g(x)) / h at each step along the way
    //  introduce too much error to makes this method numerically accurate?
    //  akleeman Feb 23 2007

    //  Answer to my own question:     Feb 25th
    //      Doesn't look like it!!  With h = 1e-4 it seems like the Finite Difference makes almost
    //     exactly the same step as the exact hessian vector product calculated through AD.
    //     That said it's probably (in the case of the Log Conditional Objective function) logical
    //     to only use finite difference.  Unless of course the function is somehow nearly singular,
    //     in which case finite difference could turn what is a convex problem into a singular proble... NOT GOOD.

    if (values != null) {
      rvfcalculate(x);
      return;
    }

    value = 0.0;

    if (priorDerivative == null) {
      priorDerivative = new double[x.length];
    }

    double priorFactor = batch.length/(data.length*prior.getSigma()*prior.getSigma());

    derivative = ArrayMath.multiply(x,priorFactor);
    HdotV = ArrayMath.multiply(v,priorFactor);

    //Arrays.fill(derivative, 0.0);
    double[] sums = new double[numClasses];
    double[] sumsV = new double[numClasses];
    double[] probs = new double[numClasses];
    double[] probsV = new double[numClasses];

    for (int m : batch) {

      //Sets the index based on the current batch
      int[] features = data[m];
      // activation

      Arrays.fill(sums, 0.0);
      Arrays.fill(sumsV, 0.0);

      for (int c = 0; c < numClasses; c++) {
        for (int feature : features) {
          int i = indexOf(feature, c);
          sums[c] += x[i];
          sumsV[c] += x[i] + h * v[i];
        }
      }

      double total = ArrayMath.logSum(sums);
      double totalV = ArrayMath.logSum(sumsV);

      for (int c = 0; c < numClasses; c++) {
        probs[c] = Math.exp(sums[c] - total);
        probsV[c] = Math.exp(sumsV[c] - totalV);

        if (dataWeights != null) {
          probs[c] *= dataWeights[m];
          probsV[c] *= dataWeights[m];
        }
        for (int feature : features) {
          int i = indexOf(feature, c);
          //derivative[i] += (-1);
          derivative[i] += probs[c];
          HdotV[i] += (probsV[c] - probs[c]) / h;
          if (c == labels[m]) {
            derivative[i] -= 1;
          }

        }
      }

      double dV = sums[labels[m]] - total;
      if (dataWeights != null) {
        dV *= dataWeights[m];
      }
      value -= dV;
    }

    //Why was this being copied?  -akleeman
    //double[] tmpDeriv = new double[derivative.length];
    //System.arraycopy(derivative,0,tmpDeriv,0,derivative.length);
    value += ((double) batch.length)/((double) data.length)*prior.compute(x,priorDerivative);
  }




  /**
   * Calculate the value and gradient on a batch of the data, with the
   * prior scaled by the fraction of the data in the batch.
   * For real-valued features, this falls back to the full-data
   * calculation of {@link #rvfcalculate(double[])} instead.
   *
   * @param x The point at which to calculate
   * @param batch Indices of the datums to use
   */
  public void calculateStochasticGradientLocal(double[] x, int[] batch) {
    if (values != null) {
      rvfcalculate(x);
      return;
    }

    value = 0.0;

    int batchSize = batch.length;

    if (priorDerivative == null) {
      priorDerivative = new double[x.length];
    }

    double priorFactor = batchSize/(data.length*prior.getSigma()*prior.getSigma());

    derivative = ArrayMath.multiply(x,priorFactor);

    //Arrays.fill(derivative, 0.0);
    double[] sums = new double[numClasses];
    //double[] sumsV = new double[numClasses];
    double[] probs = new double[numClasses];
    //double[] probsV = new double[numClasses];

    for (int m : batch) {

      //Sets the index based on the current batch
      int[] features = data[m];
      // activation

      Arrays.fill(sums, 0.0);
      //Arrays.fill(sumsV,0.0);

      for (int c = 0; c < numClasses; c++) {
        for (int feature : features) {
          int i = indexOf(feature, c);
          sums[c] += x[i];
        }
      }

      double total = ArrayMath.logSum(sums);
      //double totalV = ArrayMath.logSum(sumsV);

      for (int c = 0; c < numClasses; c++) {
        probs[c] = Math.exp(sums[c] - total);
        //probsV[c] = Math.exp(sumsV[c]- totalV);

        if (dataWeights != null) {
          probs[c] *= dataWeights[m];
          //probsV[c] *= dataWeights[m];
        }
        for (int feature : features) {
          int i = indexOf(feature, c);
          //derivative[i] += (-1);
          derivative[i] += probs[c];
          if (c == labels[m]) {
            derivative[i] -= 1;
          }

        }
      }

      double dV = sums[labels[m]] - total;
      if (dataWeights != null) {
        dV *= dataWeights[m];
      }
      value -= dV;
    }

    value += ((double) batchSize)/((double) data.length)*prior.compute(x,priorDerivative);
  }

  @Override
  public double valueAt(double[] x, double xscale, int[] batch) {
    value = 0.0;
    double[] sums = new double[numClasses];

    for (int m : batch) {
      //Sets the index based on the current batch
      int[] features = data[m];
      Arrays.fill(sums, 0.0);

      for (int c = 0; c < numClasses; c++) {
        for (int f = 0; f < features.length; f++) {
          int i = indexOf(features[f], c);
          if (values != null) {
            sums[c] += x[i] * xscale * values[m][f];
          } else {
            sums[c] += x[i] * xscale;
          }
        }
      }

      double total = ArrayMath.logSum(sums);
      double dV = sums[labels[m]] - total;
      if (dataWeights != null) {
        dV *= dataWeights[m];
      }
      value -= dV;
    }
    return value;
  }

  @Override
  public double calculateStochasticUpdate(double[] x, double xscale, int[] batch, double gain) {
    value = 0.0;

    // Double check that we don't have a mismatch between parallel and batch size settings

    if (parallelGradientCalculation && threads > 1) {
      int examplesPerProcessor = 50;
      if (batch.length <= Runtime.getRuntime().availableProcessors() * examplesPerProcessor) {
        log.info("\n\n***************");
        log.info("CONFIGURATION ERROR: YOUR BATCH SIZE DOESN'T MEET PARALLEL MINIMUM SIZE FOR PERFORMANCE");
        log.info("Batch size: " + batch.length);
        log.info("CPUS: " + Runtime.getRuntime().availableProcessors());
        log.info("Minimum batch size per CPU: " + examplesPerProcessor);
        log.info("MINIMIM BATCH SIZE ON THIS MACHINE: " + (Runtime.getRuntime().availableProcessors() * examplesPerProcessor));
        log.info("TURNING OFF PARALLEL GRADIENT COMPUTATION");
        log.info("***************\n");
        parallelGradientCalculation = false;
      }
    }

    if (parallelGradientCalculation && threads > 1) {
      // Launch several threads (reused out of our fixed pool) to handle the computation
      @SuppressWarnings("unchecked")
      CLBatchDerivativeCalculation[] runnables = (CLBatchDerivativeCalculation[])Array.newInstance(CLBatchDerivativeCalculation.class, threads);
      CountDownLatch latch = new CountDownLatch(threads);
      for (int i = 0; i < threads; i++) {
        runnables[i] = new CLBatchDerivativeCalculation(threads, i, batch, x, x.length, latch);
        new Thread(runnables[i]).start();
      }
      try {
        latch.await();
      } catch (InterruptedException e) {
        throw new RuntimeInterruptedException(e);
      }

      for (int i = 0; i < threads; i++) {
        value += runnables[i].localValue;
        for (int j = 0; j < x.length; j++) {
          x[j] += runnables[i].localDerivative[j] * xscale * gain;
        }
      }
    }
    else {
      double[] sums = new double[numClasses];
      double[] probs = new double[numClasses];

      for (int m : batch) {

        // Sets the index based on the current batch
        int[] features = data[m];
        // activation

        Arrays.fill(sums, 0.0);

        for (int c = 0; c < numClasses; c++) {
          for (int f = 0; f < features.length; f++) {
            int i = indexOf(features[f], c);
            if (values != null) {
              sums[c] += x[i] * xscale * values[m][f];
            } else {
              sums[c] += x[i] * xscale;
            }
          }
        }

        for (int f = 0; f < features.length; f++) {
          int i = indexOf(features[f], labels[m]);
          double v = (values != null) ? values[m][f] : 1;
          double delta = (dataWeights != null) ? dataWeights[m] * v : v;
          x[i] += delta * gain;
        }

        double total = ArrayMath.logSum(sums);

        for (int c = 0; c < numClasses; c++) {
          probs[c] = Math.exp(sums[c] - total);

          if (dataWeights != null) {
            probs[c] *= dataWeights[m];
          }
          for (int f = 0; f < features.length; f++) {
            int i = indexOf(features[f], c);
            double v = (values != null) ? values[m][f] : 1;
            double delta = probs[c] * v;
            x[i] -= delta * gain;
          }
        }

        double dV = sums[labels[m]] - total;
        if (dataWeights != null) {
          dV *= dataWeights[m];
        }
        value -= dV;
      }
    }
    return value;
  }

  @Override
  public void calculateStochasticGradient(double[] x, int[] batch) {
    if (derivative == null) {
      derivative = new double[domainDimension()];
    }
    Arrays.fill(derivative, 0.0);
    double[] sums = new double[numClasses];
    double[] probs = new double[numClasses];
    //double[] counts = new double[numClasses];
    // Arrays.fill(counts, 0.0); // not needed; Java arrays zero initialized
    for (int d : batch) {

      //Sets the index based on the current batch
      int[] features = data[d];
      // activation
      Arrays.fill(sums, 0.0);
      for (int c = 0; c < numClasses; c++) {
        for (int feature : features) {
          int i = indexOf(feature, c);
          sums[c] += x[i];
        }
      }
      // expectation (slower routine replaced by fast way)
      // double total = Double.NEGATIVE_INFINITY;
      // for (int c=0; c<numClasses; c++) {
      //   total = SloppyMath.logAdd(total, sums[c]);
      // }
      double total = ArrayMath.logSum(sums);
      int ld = labels[d];
      for (int c = 0; c < numClasses; c++) {
        probs[c] = Math.exp(sums[c] - total);
        for (int feature : features) {
          int i = indexOf(feature, c);
          derivative[i] += probs[ld] * probs[c];
        }
      }
      // observed
      for (int feature : features) {
        int i = indexOf(feature, labels[d]);
        derivative[i] -= probs[ld];
      }
    }
  }


  /**
   * Calculate the value and gradient on a batch of the data, along with
   * the Hessian-vector product {@code H.v}, stored in {@code HdotV},
   * computed by forward-mode algorithmic differentiation.
   * Uses {@code batch.length} consecutive datums starting from the
   * current batch position, rather than the indices in {@code batch}.
   *
   * @param x The point at which to calculate
   * @param v The vector to multiply by the Hessian
   * @param batch Determines the number of datums to use
   */
  protected void calculateStochasticAlgorithmicDifferentiation(double[] x, double[] v, int[] batch) {

    log.info("*");

    //Initialize
    value = 0.0;

    //initialize any variables
    DoubleAD[] derivativeAD = new DoubleAD[x.length];
    for (int i = 0; i < x.length;i++) {
      derivativeAD[i] = new DoubleAD(0.0,0.0);
    }

    DoubleAD[] xAD = new DoubleAD[x.length];
    for (int i = 0; i < x.length;i++){
      xAD[i] = new DoubleAD(x[i],v[i]);
    }

    // Initialize the sums
    DoubleAD[] sums = new DoubleAD[numClasses];
    for (int c = 0; c<numClasses;c++){
      sums[c] = new DoubleAD(0,0);
    }

    DoubleAD[] probs = new DoubleAD[numClasses];
    for (int c = 0; c<numClasses;c++) {
      probs[c] = new DoubleAD(0,0);
    }

    //long curTime = System.currentTimeMillis();
    // Copy the Derivative numerator, and set up the vector V to be used for Hess*V
    for (int i = 0; i < x.length;i++){
      xAD[i].set(x[i],v[i]);
      derivativeAD[i].set(0.0,0.0);
    }

    //log.info(System.currentTimeMillis() - curTime + " - ");
    //curTime = System.currentTimeMillis();

    for (int d = 0; d <batch.length ; d++) {

      //Sets the index based on the current batch
      int m = (curElement + d) % data.length;

      int[] features = data[m];

      for (int c = 0; c<numClasses;c++){
        sums[c].set(0.0,0.0);
      }


      for (int c = 0; c < numClasses; c++) {
        for (int feature : features) {
          int i = indexOf(feature, c);
          sums[c] = ADMath.plus(sums[c], xAD[i]);
        }
      }

      DoubleAD total = ADMath.logSum(sums);

      for (int c = 0; c < numClasses; c++) {
        probs[c] = ADMath.exp( ADMath.minus(sums[c], total) );
        if (dataWeights != null) {
          probs[c] = ADMath.multConst(probs[c], dataWeights[d]);
        }
        for (int feature : features) {
          int i = indexOf(feature, c);
          if (c == labels[m]) {
            derivativeAD[i].plusEqualsConst(-1.0);
          }
          derivativeAD[i].plusEquals(probs[c]);
        }
      }

      double dV = sums[labels[m]].getval() - total.getval();
      if (dataWeights != null) {
        dV *= dataWeights[d];
      }
      value -= dV;
    }

    // !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
    // DANGEROUS!!!!!!! Divide by Zero possible!!!!!!!!!!
    // !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
    // Need to modify the prior class to handle AD  -akleeman

    //log.info(System.currentTimeMillis() - curTime + " - ");
    //curTime = System.currentTimeMillis();

    double[] tmp = new double[x.length];
    for(int i = 0; i < x.length; i++){
      tmp[i] = derivativeAD[i].getval();
      derivativeAD[i].plusEquals(ADMath.multConst(xAD[i], batch.length/(data.length * prior.getSigma()*prior.getSigma())));
      derivative[i] = derivativeAD[i].getval();
      HdotV[i] = derivativeAD[i].getdot();
    }
    value += ((double) batch.length)/((double) data.length)*prior.compute(x, tmp);

    //log.info(System.currentTimeMillis() - curTime + " - ");
    //log.info("");
  }

  private class RVFDerivativeCalculation implements Runnable {
    int numThreads;
    int threadIdx;
    double localValue = 0.0;
    double[] x;
    double[] localDerivative;
    CountDownLatch latch;

    public RVFDerivativeCalculation(int numThreads, int threadIdx, double[] x, int derivativeSize, CountDownLatch latch) {
      this.numThreads = numThreads;
      this.threadIdx = threadIdx;
      this.x = x;
      this.localDerivative = new double[derivativeSize];
      this.latch = latch;
    }

    @Override
    public void run() {
      double[] sums = new double[numClasses];
      double[] probs = new double[numClasses];

      for (int d = threadIdx; d < data.length; d += numThreads) {
        final int[] features = data[d];
        final double[] vals = values[d];
        // activation
        Arrays.fill(sums, 0.0);

        for (int c = 0; c < numClasses; c++) {
          for (int f = 0; f < features.length; f++) {
            final int feature = features[f];
            final double val = vals[f];
            int i = indexOf(feature, c);
            sums[c] += x[i] * val;
          }
        }
        // expectation (slower routine replaced by fast way)
        // double total = Double.NEGATIVE_INFINITY;
        // for (int c=0; c<numClasses; c++) {
        //   total = SloppyMath.logAdd(total, sums[c]);
        // }
        // it is faster to split these two loops. More striding
        double total = ArrayMath.logSum(sums);
        for (int c = 0; c < numClasses; c++) {
          probs[c] = Math.exp(sums[c] - total);
          if (dataWeights != null) {
            probs[c] *= dataWeights[d];
          }
        }

        for (int c = 0; c < numClasses; c++) {
          for (int f = 0; f < features.length; f++) {
            final int feature = features[f];
            final double val = vals[f];
            int i = indexOf(feature, c);
            localDerivative[i] += probs[c] * val;
          }
        }

        double dV = sums[labels[d]] - total;
        if (dataWeights != null) {
          dV *= dataWeights[d];
        }
        localValue -= dV;
      }
      latch.countDown();
    }
  }

  /**
   * Calculate conditional likelihood for datasets with real-valued features.
   * Currently this can calculate CL only (no support for SCL).
   * TODO: sum-conditional obj. fun. with RVFs.
   *
   * @param x The point at which to calculate
   */
  protected void rvfcalculate(double[] x) {
    value = 0.0;
    // This is only calculated once per training run, not worth the effort to multi-thread properly
    if (derivativeNumerator == null) {
      derivativeNumerator = new double[x.length];
      for (int d = 0; d < data.length; d++) {
        final int[] features = data[d];
        final double[] vals = values[d];
        for (int f = 0; f < features.length; f++) {
          int i = indexOf(features[f], labels[d]);
          if (dataWeights == null) {
            derivativeNumerator[i] -= vals[f];
          } else {
            derivativeNumerator[i] -= dataWeights[d] * vals[f];
          }
        }
      }
    }
    copy(derivative, derivativeNumerator);
    //    Arrays.fill(derivative, 0.0);
    //    double[] counts = new double[numClasses];
    //    Arrays.fill(counts, 0.0);

    if (parallelGradientCalculation && threads > 1) {
      // Launch several threads (reused out of our fixed pool) to handle the computation
      @SuppressWarnings("unchecked")
      RVFDerivativeCalculation[] runnables = (RVFDerivativeCalculation[])Array.newInstance(RVFDerivativeCalculation.class, threads);
      CountDownLatch latch = new CountDownLatch(threads);
      for (int i = 0; i < threads; i++) {
        runnables[i] = new RVFDerivativeCalculation(threads, i, x, derivative.length, latch);
        new Thread(runnables[i]).start();
      }
      try {
        latch.await();
      } catch (InterruptedException e) {
        throw new RuntimeInterruptedException(e);
      }

      for (int i = 0; i < threads; i++) {
        value += runnables[i].localValue;
        for (int j = 0; j < derivative.length; j++) {
          derivative[j] += runnables[i].localDerivative[j];
        }
      }
    }
    else {
      // Do the calculation locally on this thread
      double[] sums = new double[numClasses];
      double[] probs = new double[numClasses];

      for (int d = 0; d < data.length; d++) {
        final int[] features = data[d];
        final double[] vals = values[d];
        // activation
        Arrays.fill(sums, 0.0);

        for (int f = 0; f < features.length; f++) {
          final int feature = features[f];
          final double val = vals[f];
          for (int c = 0; c < numClasses; c++) {
            int i = indexOf(feature, c);
            sums[c] += x[i] * val;
          }
        }
        // expectation (slower routine replaced by fast way)
        // double total = Double.NEGATIVE_INFINITY;
        // for (int c=0; c<numClasses; c++) {
        //   total = SloppyMath.logAdd(total, sums[c]);
        // }
        // it is faster to split these two loops. More striding
        double total = ArrayMath.logSum(sums);
        for (int c = 0; c < numClasses; c++) {
          probs[c] = Math.exp(sums[c] - total);
          if (dataWeights != null) {
            probs[c] *= dataWeights[d];
          }
        }

        for (int f = 0; f < features.length; f++) {
          final int feature = features[f];
          final double val = vals[f];
          for (int c = 0; c < numClasses; c++) {
            int i = indexOf(feature, c);
            derivative[i] += probs[c] * val;
          }
        }

        double dV = sums[labels[d]] - total;
        if (dataWeights != null) {
          dV *= dataWeights[d];
        }
        value -= dV;
      }
    }
    value += prior.compute(x, derivative);
  }


  /**
   * Conditional likelihood of a dataset with a quadratic prior.
   *
   * @param dataset The training data
   */
  public LogConditionalObjectiveFunction(GeneralDataset<L, F> dataset) {
    this(dataset, new LogPrior(LogPrior.LogPriorType.QUADRATIC));
  }

  /**
   * Conditional likelihood of a dataset with the given prior.
   *
   * @param dataset The training data
   * @param prior The prior
   */
  public LogConditionalObjectiveFunction(GeneralDataset<L, F> dataset, LogPrior prior) {
    this(dataset, prior, false);
  }

  /**
   * Conditional likelihood of a weighted dataset with the given prior.
   *
   * @param dataset The training data
   * @param dataWeights The weight of each datum (may be null)
   * @param prior The prior
   */
  public LogConditionalObjectiveFunction(GeneralDataset<L, F> dataset, float[] dataWeights, LogPrior prior) {
    this(dataset, prior, false, dataWeights);
  }

  /**
   * Conditional or summed conditional likelihood of a dataset with the given prior.
   *
   * @param dataset The training data
   * @param prior The prior
   * @param useSumCondObjFun Whether to use summed conditional likelihood
   */
  public LogConditionalObjectiveFunction(GeneralDataset<L, F> dataset, LogPrior prior, boolean useSumCondObjFun) {
    this(dataset, prior, useSumCondObjFun, null);
  }

  /**
   * Version passing in a GeneralDataset, which may be binary or real-valued features.
   *
   * @param dataset The training data
   * @param prior The prior
   * @param useSumCondObjFun Whether to use summed conditional likelihood
   * @param dataWeights The weight of each datum; if null, the weights of a
   *                    {@link WeightedDataset} or {@link WeightedRVFDataset}
   *                    are used, and otherwise the data are unweighted
   */
  public LogConditionalObjectiveFunction(GeneralDataset<L, F> dataset, LogPrior prior, boolean useSumCondObjFun,
                                         float[] dataWeights) {
    this.prior = prior;
    this.useSummedConditionalLikelihood = useSumCondObjFun;
    this.numFeatures = dataset.numFeatures();
    this.numClasses = dataset.numClasses();
    this.data = dataset.getDataArray();
    this.labels = dataset.getLabelsArray();
    this.values = dataset.getValuesArray();
    if (dataWeights != null) {
      this.dataWeights = dataWeights;
    } else if (dataset instanceof WeightedDataset<?,?>) {
      this.dataWeights = ((WeightedDataset<L, F>)dataset).getWeights();
    } else if (dataset instanceof WeightedRVFDataset<?,?>) {
      this.dataWeights = ((WeightedRVFDataset<L, F>)dataset).getWeights();
    } else {
      this.dataWeights = null;
    }
    this.labelIndex = null;
    this.featureIndex = null;
    this.dataIterable = null;
  }

  //TODO: test this [none of our code actually even uses it].
  /**
   * Version where an Iterable is passed in for the data. Doesn't support dataWeights.
   * Only the full-data calculation supports this form of data.
   *
   * @param dataIterable The training data
   * @param logPrior The prior
   * @param featureIndex Index used to look up the features of each datum
   * @param labelIndex Index used to look up the label of each datum
   */
  public LogConditionalObjectiveFunction(Iterable<Datum<L, F>> dataIterable, LogPrior logPrior, Index<F> featureIndex, Index<L> labelIndex) {
    this.prior = logPrior;
    this.useSummedConditionalLikelihood = false;
    this.numFeatures = featureIndex.size();
    this.numClasses = labelIndex.size();
    this.data = null;
    this.dataIterable = dataIterable;

    this.labelIndex = labelIndex;
    this.featureIndex = featureIndex;
    this.labels = null;//dataset.getLabelsArray();
    this.values = null;//dataset.getValuesArray();
    this.dataWeights = null;
  }

  /**
   * For binary features, with a quadratic prior.
   *
   * @param numFeatures The number of features
   * @param numClasses The number of classes
   * @param data The feature indices of each datum
   * @param labels The label index of each datum
   * @param useSumCondObjFun Whether to use summed conditional likelihood
   */
  public LogConditionalObjectiveFunction(int numFeatures, int numClasses, int[][] data, int[] labels, boolean useSumCondObjFun) {
    this(numFeatures, numClasses, data, labels, null, new LogPrior(LogPrior.LogPriorType.QUADRATIC), useSumCondObjFun);
  }

  /**
   * For binary features, with a quadratic prior.
   *
   * @param numFeatures The number of features
   * @param numClasses The number of classes
   * @param data The feature indices of each datum
   * @param labels The label index of each datum
   */
  public LogConditionalObjectiveFunction(int numFeatures, int numClasses, int[][] data, int[] labels) {
    this(numFeatures, numClasses, data, labels, new LogPrior(LogPrior.LogPriorType.QUADRATIC));
  }

  /**
   * For binary features.
   *
   * @param numFeatures The number of features
   * @param numClasses The number of classes
   * @param data The feature indices of each datum
   * @param labels The label index of each datum
   * @param prior The prior
   */
  public LogConditionalObjectiveFunction(int numFeatures, int numClasses, int[][] data, int[] labels, LogPrior prior) {
    this(numFeatures, numClasses, data, labels, null, prior);
  }

  /**
   * For weighted binary features, with a quadratic prior.
   *
   * @param numFeatures The number of features
   * @param numClasses The number of classes
   * @param data The feature indices of each datum
   * @param labels The label index of each datum
   * @param dataWeights The weight of each datum (may be null)
   */
  public LogConditionalObjectiveFunction(int numFeatures, int numClasses, int[][] data, int[] labels, float[] dataWeights) {
    this(numFeatures, numClasses, data, labels, dataWeights, new LogPrior(LogPrior.LogPriorType.QUADRATIC));
  }

  /**
   * For weighted binary features.
   *
   * @param numFeatures The number of features
   * @param numClasses The number of classes
   * @param data The feature indices of each datum
   * @param labels The label index of each datum
   * @param dataWeights The weight of each datum (may be null)
   * @param prior The prior
   */
  public LogConditionalObjectiveFunction(int numFeatures, int numClasses, int[][] data, int[] labels, float[] dataWeights, LogPrior prior) {
    this(numFeatures, numClasses, data, labels, dataWeights, prior, false);
  }

  /**
   * For binary features. Supports dataWeights.
   *
   * @param numFeatures The number of features
   * @param numClasses The number of classes
   * @param data The feature indices of each datum
   * @param labels The label index of each datum
   * @param dataWeights The weight of each datum (may be null)
   * @param prior The prior
   * @param useSummedConditionalLikelihood Whether to use summed conditional likelihood
   */
  public LogConditionalObjectiveFunction(int numFeatures, int numClasses, int[][] data, int[] labels,
                                         float[] dataWeights, LogPrior prior, boolean useSummedConditionalLikelihood) {
    this.numFeatures = numFeatures;
    this.numClasses = numClasses;
    this.data = data;
    this.values = null;
    this.labels = labels;
    this.prior = prior;
    this.dataWeights = dataWeights;
    this.labelIndex = null;
    this.featureIndex = null;
    this.dataIterable = null;
    this.useSummedConditionalLikelihood = useSummedConditionalLikelihood;
  }

  /**
   * For binary features.
   *
   * @param numFeatures The number of features
   * @param numClasses The number of classes
   * @param data The feature indices of each datum
   * @param labels The label index of each datum
   * @param intPrior The prior type, as the ordinal of a {@link LogPrior.LogPriorType}
   * @param sigma The sigma parameter of the prior
   * @param epsilon The epsilon parameter of the prior
   */
  public LogConditionalObjectiveFunction(int numFeatures, int numClasses, int[][] data, int[] labels, int intPrior, double sigma, double epsilon) {
    this(numFeatures, numClasses, data, null, labels, intPrior, sigma, epsilon);
  }

  /**
   * For real-valued features. Passing in processed data set.
   *
   * @param numFeatures The number of features
   * @param numClasses The number of classes
   * @param data The feature indices of each datum
   * @param values The value of each feature, parallel to {@code data};
   *               null for binary features
   * @param labels The label index of each datum
   * @param intPrior The prior type, as the ordinal of a {@link LogPrior.LogPriorType}
   * @param sigma The sigma parameter of the prior
   * @param epsilon The epsilon parameter of the prior
   */
  public LogConditionalObjectiveFunction(int numFeatures, int numClasses, int[][] data, double[][] values, int[] labels, int intPrior, double sigma, double epsilon) {
    this.numFeatures = numFeatures;
    this.numClasses = numClasses;
    this.data = data;
    this.values = values;
    this.labels = labels;
    this.prior = new LogPrior(intPrior, sigma, epsilon);
    this.labelIndex = null;
    this.featureIndex = null;
    this.dataIterable = null;
    this.useSummedConditionalLikelihood = false;
    this.dataWeights = null;
  }

}
