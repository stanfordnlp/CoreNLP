package edu.stanford.nlp.classify;

import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;

import edu.stanford.nlp.ling.Datum;
import edu.stanford.nlp.math.ADMath;
import edu.stanford.nlp.math.ArrayMath;
import edu.stanford.nlp.math.DoubleAD;
import edu.stanford.nlp.optimization.AbstractStochasticCachingDiffUpdateFunction;
import edu.stanford.nlp.optimization.StochasticCalculateMethods;
import edu.stanford.nlp.util.Index;


/**
 * Maximizes the conditional likelihood with a given prior.
 *
 * @author Dan Klein
 * @author Galen Andrew
 * @author Chris Cox (merged w/ SumConditionalObjectiveFunction, 2/16/05)
 * @author Sarah Spikes (Templatization, allowing an Iterable<Datum<L, F>> to be passed in instead of a GeneralDataset<L, F>)
 * @author Angel Chang (support in place SGD - extend AbstractStochasticCachingDiffUpdateFunction)
 */

public class LogConditionalObjectiveFunction<L, F> extends AbstractStochasticCachingDiffUpdateFunction {

  public void setPrior(LogPrior prior) {
    this.prior = prior;
    clearCache();
  }

  protected LogPrior prior;

  protected int numFeatures = 0;
  protected int numClasses = 0;

  protected int[][] data = null;
  protected Iterable<Datum<L, F>> dataIterable = null;
  protected double[][] values = null;
  protected int[] labels = null;
  protected float[] dataweights = null;
  protected double[] derivativeNumerator = null;

  protected DoubleAD[] xAD = null;
  protected double [] priorDerivative = null; //The only reason this is around is because the Prior Functions don't handle stochastic calculations yet.
  protected DoubleAD[] derivativeAD = null;
  protected DoubleAD[] sums = null;
  protected DoubleAD[] probs = null;

  protected Index<L> labelIndex = null;
  protected Index<F> featureIndex = null;
  protected boolean useIterable = false;

  protected boolean useSummedConditionalLikelihood = false; //whether to use sumConditional or logConditional

  @Override
  public int domainDimension() {
    return numFeatures * numClasses;
  }

  @Override
  public int dataDimension(){
    return data.length;
  }

  int classOf(int index) {
    return index % numClasses;
  }

  int featureOf(int index) {
    return index / numClasses;
  }

  protected int indexOf(int f, int c) {
    return f * numClasses + c;
  }

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
   * If <code>useSummedConditionalLikelihood</code> is <code>false</code> (the default),
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



  /*
   *  This function is used to comme up with an estimate of the value / gradient based on only a small
   * portion of the data (refered to as the batchSize for lack of a better term.  In this case batch does
   * not mean All!!  It should be thought of in the sense of "a small batch of the data".
   */


  @Override
  public void calculateStochastic(double[] x, double[] v, int[] batch){

    if(method.calculatesHessianVectorProduct() && v != null){
      //  This is used for Stochastic Methods that involve second order information (SMD for example)
      if(method.equals(StochasticCalculateMethods.AlgorithmicDifferentiation)){
        calculateStochasticAlgorithmicDifferentiation(x,v,batch);
      }else if(method.equals(StochasticCalculateMethods.IncorporatedFiniteDifference)){
        calculateStochasticFiniteDifference(x,v,finiteDifferenceStepSize,batch);
      }
    } else{
      //This is used for Stochastic Methods that don't need anything but the gradient (SGD)
      calculateStochasticGradientOnly(x,batch);
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
    double[] counts = new double[numClasses];
    Arrays.fill(counts, 0.0);
    for (int d = 0; d < data.length; d++) {
      //       if (d == testMin) {
      //         d = testMax - 1;
      //         continue;
      //       }
      int[] features = data[d];
      // activation
      Arrays.fill(sums, 0.0);
      for (int c = 0; c < numClasses; c++) {
        for (int f = 0; f < features.length; f++) {
          int i = indexOf(features[f], c);
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
        for (int f = 0; f < features.length; f++) {
          int i = indexOf(features[f], c);
          derivative[i] += probs[ld] * probs[c];
        }
      }
      // observed
      for (int f = 0; f < features.length; f++) {
        int i = indexOf(features[f], labels[d]);
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
   * conditional estimates.
   *
   */
  private void calculateCL(double[] x) {
    if (values != null) {
      rvfcalculate(x);
      return;
    }
    //System.out.println("Checking at: "+x[0]+" "+x[1]+" "+x[2]);
    value = 0.0;
    if (derivative == null) {
      derivative = new double[x.length];
    } else {
      Arrays.fill(derivative, 0.0);
    }

    if (derivativeNumerator == null) {
      derivativeNumerator = new double[x.length];
      //use dataIterable if data is null & vice versa
      if(data != null) {
        for (int d = 0; d < data.length; d++) {
          //         if (d == testMin) {
          //           d = testMax - 1;
          //           continue;
          //         }
          int[] features = data[d];
          for (int f = 0; f < features.length; f++) {
            int i = indexOf(features[f], labels[d]);
            if (dataweights == null) {
              derivativeNumerator[i] -= 1;
            } else {
              derivativeNumerator[i] -= dataweights[d];
            }
          }
        }
      }
      //TODO: Make sure this work as expected!!
      else if(dataIterable != null) {
        //int index = 0;
        for (Datum<L, F> datum : dataIterable) {
          //         if (d == testMin) {
          //           d = testMax - 1;
          //           continue;
          //         }
          Collection<F> features = datum.asFeatures();
          for (F feature : features) {
            int i = indexOf(featureIndex.indexOf(feature), labelIndex.indexOf(datum.label()));
            if (dataweights == null) {
              derivativeNumerator[i] -= 1;
            } /*else {
              derivativeNumerator[i] -= dataweights[index];
            }*/
          }
        }
      }
      else {
        System.err.println("Both were null!  Couldn't calculate.");
        System.exit(-1);
      }
    }
    copy(derivative, derivativeNumerator);
    //    Arrays.fill(derivative, 0.0);
    double[] sums = new double[numClasses];
    double[] probs = new double[numClasses];
    //    double[] counts = new double[numClasses];
    //    Arrays.fill(counts, 0.0);

    Iterator<Datum<L, F>> iter = null;
    int d = -1;
    if(useIterable)
      iter = dataIterable.iterator();
    Datum<L, F> datum = null;
    while(true){
      if(useIterable) {
        if(!iter.hasNext()) break;
        datum = iter.next();
      } else {
        d++;
        if(d >= data.length) break;
      }

      //       if (d == testMin) {
      //         d = testMax - 1;
      //         continue;
      //       }

      // activation
      Arrays.fill(sums, 0.0);
      double total = 0;
      if(!useIterable) {
        int[] featuresArr = data[d];

        for (int c = 0; c < numClasses; c++) {
          for (int f = 0; f < featuresArr.length; f++) {
            int i = indexOf(featuresArr[f], c);
            sums[c] += x[i];
          }
        }
        // expectation (slower routine replaced by fast way)
        // double total = Double.NEGATIVE_INFINITY;
        // for (int c=0; c<numClasses; c++) {
        //   total = SloppyMath.logAdd(total, sums[c]);
        // }
        total = ArrayMath.logSum(sums);
        for (int c = 0; c < numClasses; c++) {
          probs[c] = Math.exp(sums[c] - total);
          if (dataweights != null) {
            probs[c] *= dataweights[d];
          }
          for (int f = 0; f < featuresArr.length; f++) {
            int i = indexOf(featuresArr[f], c);
            derivative[i] += probs[c];
          }
        }
      } else {
        Collection<F> features = datum.asFeatures();
        for (int c = 0; c < numClasses; c++) {
          for (F feature : features) {
            int i = indexOf(featureIndex.indexOf(feature), c);
            sums[c] += x[i];
          }
        }
        // expectation (slower routine replaced by fast way)
        // double total = Double.NEGATIVE_INFINITY;
        // for (int c=0; c<numClasses; c++) {
        //   total = SloppyMath.logAdd(total, sums[c]);
        // }
        total = ArrayMath.logSum(sums);
        for (int c = 0; c < numClasses; c++) {
          probs[c] = Math.exp(sums[c] - total);
          if (dataweights != null) {
            probs[c] *= dataweights[d];
          }
          for (F feature : features) {
            int i = indexOf(featureIndex.indexOf(feature), c);
            derivative[i] += probs[c];
          }
        }
      }

      int labelindex;
      if(useIterable)
        labelindex = labelIndex.indexOf(datum.label());
      else
        labelindex = labels[d];
      double dV = sums[labelindex] - total;
      if (dataweights != null) {
        dV *= dataweights[d];
      }
      value -= dV;

    }
    value += prior.compute(x, derivative);
  }



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

    for (int d = 0; d <batch.length; d++) {

      //Sets the index based on the current batch
      int m = batch[d];


      int[] features = data[m];
      // activation


      Arrays.fill(sums, 0.0);
      Arrays.fill(sumsV,0.0);

      for (int c = 0; c < numClasses; c++) {
        for (int f = 0; f < features.length; f++) {
          int i = indexOf(features[f], c);
          sums[c] += x[i];
          sumsV[c] += x[i] + h*v[i];
        }
      }



      double total = ArrayMath.logSum(sums);
      double totalV = ArrayMath.logSum(sumsV);

      for (int c = 0; c < numClasses; c++) {
        probs[c] = Math.exp(sums[c] - total);
        probsV[c] = Math.exp(sumsV[c]- totalV);

        if (dataweights != null) {
          probs[c] *= dataweights[m];
          probsV[c] *= dataweights[m];
        }
        for (int f = 0; f < features.length; f++) {
          int i = indexOf(features[f], c);
          //derivative[i] += (-1);
          derivative[i] += probs[c];
          HdotV[i] += (probsV[c] - probs[c])/h;
          if( c == labels[m]) {derivative[i] -= 1;}

        }
      }

      double dV = sums[labels[m]] - total;
      if (dataweights != null) {
        dV *= dataweights[m];
      }
      value -= dV;
    }

    //Why was this being copied?  -akleeman
    //double[] tmpDeriv = new double[derivative.length];
    //System.arraycopy(derivative,0,tmpDeriv,0,derivative.length);
    value += ((double) batch.length)/((double) data.length)*prior.compute(x,priorDerivative);
  }




  public void calculateStochasticGradientOnly(double[] x, int[] batch) {
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

    for (int d = 0; d <batchSize; d++) {

      //Sets the index based on the current batch
      int m = batch[d];

      int[] features = data[m];
      // activation

      Arrays.fill(sums, 0.0);
      //Arrays.fill(sumsV,0.0);

      for (int c = 0; c < numClasses; c++) {
        for (int f = 0; f < features.length; f++) {
          int i = indexOf(features[f], c);
          sums[c] += x[i];
        }
      }

      double total = ArrayMath.logSum(sums);
      //double totalV = ArrayMath.logSum(sumsV);

      for (int c = 0; c < numClasses; c++) {
        probs[c] = Math.exp(sums[c] - total);
        //probsV[c] = Math.exp(sumsV[c]- totalV);

        if (dataweights != null) {
          probs[c] *= dataweights[m];
          //probsV[c] *= dataweights[m];
        }
        for (int f = 0; f < features.length; f++) {
          int i = indexOf(features[f], c);
          //derivative[i] += (-1);
          derivative[i] += probs[c];
          if( c == labels[m]) {derivative[i] -= 1;}

        }
      }

      double dV = sums[labels[m]] - total;
      if (dataweights != null) {
        dV *= dataweights[m];
      }
      value -= dV;
    }


    value += ((double) batchSize)/((double) data.length)*prior.compute(x,priorDerivative);



  }

  @Override
  public double valueAt(double[] x, double xscale, int[] batch) {
    value = 0.0;
    int batchSize = batch.length;
    double[] sums = new double[numClasses];

    for (int d = 0; d <batchSize; d++) {
      //Sets the index based on the current batch
      int m = batch[d];
      int[] features = data[m];
      Arrays.fill(sums, 0.0);

      for (int c = 0; c < numClasses; c++) {
        for (int f = 0; f < features.length; f++) {
          int i = indexOf(features[f], c);
          if (values != null) {
             sums[c] += x[i]*xscale*values[m][f];
          } else {
             sums[c] += x[i]*xscale;
          }
        }
      }

      double total = ArrayMath.logSum(sums);
      double dV = sums[labels[m]] - total;
      if (dataweights != null) {
        dV *= dataweights[m];
      }
      value -= dV;
    }
    return value;
  }

  @Override
  public double calculateStochasticUpdate(double[] x, double xscale, int[] batch, double gain) {
    value = 0.0;

    int batchSize = batch.length;

    double[] sums = new double[numClasses];
    double[] probs = new double[numClasses];

    for (int d = 0; d <batchSize; d++) {

      //Sets the index based on the current batch
      int m = batch[d];

      int[] features = data[m];
      // activation

      Arrays.fill(sums, 0.0);

      for (int c = 0; c < numClasses; c++) {
        for (int f = 0; f < features.length; f++) {
          int i = indexOf(features[f], c);
          if (values != null) {
             sums[c] += x[i]*xscale*values[m][f];
          } else {
             sums[c] += x[i]*xscale;
          }
        }
      }

      for (int f = 0; f < features.length; f++) {
        int i = indexOf(features[f], labels[m]);
        double v = (values != null)? values[m][f]:1;
        double delta = (dataweights != null)? dataweights[m]*v:v;
        x[i] += delta*gain;
      }

      double total = ArrayMath.logSum(sums);

      for (int c = 0; c < numClasses; c++) {
        probs[c] = Math.exp(sums[c] - total);

        if (dataweights != null) {
          probs[c] *= dataweights[m];
        }
        for (int f = 0; f < features.length; f++) {
          int i = indexOf(features[f], c);
          double v = (values != null)? values[m][f]:1;
          double delta = probs[c]*v;
          x[i] -= delta*gain;
        }
      }

      double dV = sums[labels[m]] - total;
      if (dataweights != null) {
        dV *= dataweights[m];
      }
      value -= dV;
    }
    return value;
  }

  protected void calculateStochasticAlgorithmicDifferentiation(double[] x, double[] v, int[] batch) {

    System.err.print("*");

    //Initialize
    value = 0.0;

    if(derivativeAD == null){
      //initialize any variables
      derivativeAD = new DoubleAD[x.length];

      for (int i = 0; i < x.length;i++){
        derivativeAD[i] = new DoubleAD(0.0,0.0);
      }
    }


    if(xAD == null){
      xAD = new DoubleAD[x.length];

      for (int i = 0; i < x.length;i++){
        xAD[i] = new DoubleAD(x[i],v[i]);
      }
    }
    // Initialize the sums
    if(sums == null){
      sums = new DoubleAD[numClasses];

      for (int c = 0; c<numClasses;c++){
        sums[c] = new DoubleAD(0,0);
      }
    }

    if(probs == null) {
      probs = new DoubleAD[numClasses];

      for (int c = 0; c<numClasses;c++){
        probs[c] = new DoubleAD(0,0);
      }
    }

    //long curTime = System.currentTimeMillis();
    // Copy the Derivative numerator, and set up the vector V to be used for Hess*V
    for (int i = 0; i < x.length;i++){
      xAD[i].set(x[i],v[i]);
      derivativeAD[i].set(0.0,0.0);
    }

    //System.err.print(System.currentTimeMillis() - curTime + " - ");
    //curTime = System.currentTimeMillis();

    for (int d = 0; d <batch.length ; d++) {

      //Sets the index based on the current batch
      int m = (curElement + d) % data.length;

      int[] features = data[m];

      for (int c = 0; c<numClasses;c++){
        sums[c].set(0.0,0.0);
      }


      for (int c = 0; c < numClasses; c++) {
        for (int f = 0; f < features.length; f++) {
          int i = indexOf(features[f], c);
          sums[c] = ADMath.plus(sums[c],xAD[i]);
        }
      }

      DoubleAD total = ADMath.logSum(sums);

      for (int c = 0; c < numClasses; c++) {
        probs[c] = ADMath.exp( ADMath.minus(sums[c], total) );
        if (dataweights != null) {
          probs[c] = ADMath.multConst(probs[c], dataweights[d]);
        }
        for (int f = 0; f < features.length; f++) {
          int i = indexOf(features[f], c);
          if (c == labels[m]){derivativeAD[i].plusEqualsConst(-1.0);}
          derivativeAD[i].plusEquals(probs[c]);
        }
      }

      double dV = sums[labels[m]].getval() - total.getval();
      if (dataweights != null) {
        dV *= dataweights[d];
      }
      value -= dV;
    }

    // !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
    // DANGEROUS!!!!!!! Divide by Zero possible!!!!!!!!!!
    // !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
    // Need to modify the prior class to handle AD  -akleeman

    //System.err.print(System.currentTimeMillis() - curTime + " - ");
    //curTime = System.currentTimeMillis();

    double[] tmp = new double[x.length];
    for(int i = 0; i < x.length; i++){
      tmp[i] = derivativeAD[i].getval();
      derivativeAD[i].plusEquals(ADMath.multConst(xAD[i], batch.length/(data.length * prior.getSigma()*prior.getSigma())));
      derivative[i] = derivativeAD[i].getval();
      HdotV[i] = derivativeAD[i].getdot();
    }
    value += ((double) batch.length)/((double) data.length)*prior.compute(x, tmp);

    //System.err.print(System.currentTimeMillis() - curTime + " - ");
    //System.err.println("");


  }

  /**
   * Calculate conditional likelihood for datasets with real-valued features.
   * Currently this can calculate CL only (no support for SCL).
   * TODO: sum-conditional obj. fun. with RVFs.
   *
   */
  protected void rvfcalculate(double[] x) {
    value = 0.0;
    if (derivativeNumerator == null) {
      derivativeNumerator = new double[x.length];
      for (int d = 0; d < data.length; d++) {
        //         if (d == testMin) {
        //           d = testMax - 1;
        //           continue;
        //         }
        int[] features = data[d];
        for (int f = 0; f < features.length; f++) {
          int i = indexOf(features[f], labels[d]);
          if (dataweights == null) {
            derivativeNumerator[i] -= values[d][f];
          } else {
            derivativeNumerator[i] -= dataweights[d]*values[d][f];
          }
        }
      }
    }
    copy(derivative, derivativeNumerator);
    //    Arrays.fill(derivative, 0.0);
    double[] sums = new double[numClasses];
    double[] probs = new double[numClasses];
    //    double[] counts = new double[numClasses];
    //    Arrays.fill(counts, 0.0);
    for (int d = 0; d < data.length; d++) {
      //       if (d == testMin) {
      //         d = testMax - 1;
      //         continue;
      //       }
      int[] features = data[d];
      // activation
      Arrays.fill(sums, 0.0);

      for (int c = 0; c < numClasses; c++) {
        for (int f = 0; f < features.length; f++) {
          int i = indexOf(features[f], c);
          sums[c] += x[i] * values[d][f];
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
        if (dataweights != null) {
          probs[c] *= dataweights[d];
        }
        for (int f = 0; f < features.length; f++) {
          int i = indexOf(features[f], c);
          derivative[i] += probs[c] * values[d][f];
        }
      }

      double dV = sums[labels[d]] - total;
      if (dataweights != null) {
        dV *= dataweights[d];
      }
      value -= dV;
    }
    value += prior.compute(x, derivative);
  }

  //   public void setTestMinMax(int testMin, int testMax) {
  //     this.testMin = testMin;
  //     this.testMax = testMax;
  //   }

  public void setUseSumCondObjFun(boolean value) {
    this.useSummedConditionalLikelihood = value;
  }

  public LogConditionalObjectiveFunction(GeneralDataset<L, F> dataset) {
    this(dataset, new LogPrior(LogPrior.LogPriorType.QUADRATIC));
  }

  public LogConditionalObjectiveFunction(GeneralDataset<L, F> dataset, LogPrior prior) {
    this(dataset, prior, false);
  }

  public LogConditionalObjectiveFunction(GeneralDataset<L, F> dataset, float[] dataWeights, LogPrior prior) {
    this(dataset, prior, false);
    this.dataweights = dataWeights;
    System.err.println("correct constructor");
  }

  public LogConditionalObjectiveFunction(GeneralDataset<L, F> dataset, LogPrior prior, boolean useSumCondObjFun) {
    setPrior(prior);
    setUseSumCondObjFun(useSumCondObjFun);
    this.numFeatures = dataset.numFeatures();
    this.numClasses = dataset.numClasses();
    this.data = dataset.getDataArray();
    this.labels = dataset.getLabelsArray();
    this.values = dataset.getValuesArray();
    if (dataset instanceof WeightedDataset<?,?>) {
      this.dataweights = ((WeightedDataset<L, F>)dataset).getWeights();
    }
  }

  //TODO: test this
  public LogConditionalObjectiveFunction(Iterable<Datum<L, F>> dataIterable, LogPrior logPrior, Index<F> featureIndex, Index<L> labelIndex) {
    setPrior(logPrior);
    setUseSumCondObjFun(false);
    this.useIterable = true;
    this.numFeatures = featureIndex.size();
    this.numClasses = labelIndex.size();
    this.data = null;
    this.dataIterable = dataIterable;

    this.labelIndex = labelIndex;
    this.featureIndex = featureIndex;
    this.labels = null;//dataset.getLabelsArray();
    this.values = null;//dataset.getValuesArray();
    //this.dataweights //leave it null?
  }

  public LogConditionalObjectiveFunction(int numFeatures, int numClasses, int[][] data, int[] labels, boolean useSumCondObjFun) {
    this(numFeatures, numClasses, data, labels);
    this.useSummedConditionalLikelihood = useSumCondObjFun;
  }

  public LogConditionalObjectiveFunction(int numFeatures, int numClasses, int[][] data, int[] labels) {
    this(numFeatures, numClasses, data, labels, new LogPrior(LogPrior.LogPriorType.QUADRATIC));
  }

  public LogConditionalObjectiveFunction(int numFeatures, int numClasses, int[][] data, int[] labels, LogPrior prior) {
    this(numFeatures, numClasses, data, labels, null, prior);
  }

  public LogConditionalObjectiveFunction(int numFeatures, int numClasses, int[][] data, int[] labels, float[] dataweights) {
    this(numFeatures, numClasses, data, labels, dataweights, new LogPrior(LogPrior.LogPriorType.QUADRATIC));
  }

  public LogConditionalObjectiveFunction(int numFeatures, int numClasses, int[][] data, int[] labels, float[] dataweights, LogPrior prior) {
    this.numFeatures = numFeatures;
    this.numClasses = numClasses;
    this.data = data;
    this.labels = labels;
    this.prior = prior;
    this.dataweights = dataweights;
    //     this.testMin = data.length;
    //     this.testMax = data.length;
  }

  public LogConditionalObjectiveFunction(int numFeatures, int numClasses, int[][] data, int[] labels, int intPrior, double sigma, double epsilon) {
    this(numFeatures, numClasses, data, null, labels, intPrior, sigma, epsilon);
  }

  public LogConditionalObjectiveFunction(int numFeatures, int numClasses, int[][] data, double[][] values, int[] labels, int intPrior, double sigma, double epsilon) {
    this.numFeatures = numFeatures;
    this.numClasses = numClasses;
    this.data = data;
    this.values = values;
    this.labels = labels;
    this.prior = new LogPrior(intPrior, sigma, epsilon);
    //     this.testMin = data.length;
    //     this.testMax = data.length;
  }
}
