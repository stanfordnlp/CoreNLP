package edu.stanford.nlp.optimization;

import edu.stanford.nlp.math.ArrayMath;

import java.util.Random;
import java.util.ArrayList;
import java.util.List;
import java.text.NumberFormat;
import java.text.DecimalFormat;
import java.io.PrintWriter;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * @author Alex Kleeman
 */
public class StochasticDiffFunctionTester {
  static private double EPS = 1e-8;
  static private boolean quiet = false;

  protected int testBatchSize;
  protected int numBatches;
  protected AbstractStochasticCachingDiffFunction thisFunc;

  double[]  approxGrad,fullGrad,diff,Hv,HvFD,v,curGrad,gradFD;
  double diffNorm,diffValue,fullValue,approxValue,diffGrad,maxGradDiff = 0.0,maxHvDiff = 0.0;
  Random generator;
  private static NumberFormat nf = new DecimalFormat("00.0");

  public StochasticDiffFunctionTester(Function function){

    // check for derivatives
    if (!(function instanceof AbstractStochasticCachingDiffFunction)) {
      System.err.println("Attempt to test non stochastic function using StochasticDiffFunctionTester");
      throw new UnsupportedOperationException();
    }

    thisFunc = (AbstractStochasticCachingDiffFunction) function; // Make sure the function is Stochastic

    generator = new Random(System.currentTimeMillis());  // used to generate random test vectors

    //  Look for a good batchSize to test with by getting factors
    testBatchSize = (int) getTestBatchSize(thisFunc.dataDimension());

    //  Again make sure that our calculated batchSize is actually valid
    if(testBatchSize < 0 || testBatchSize > thisFunc.dataDimension() || (thisFunc.dataDimension()%testBatchSize != 0)){
      System.err.println("Invalid testBatchSize found, testing aborted.  Data size: " + thisFunc.dataDimension() + " batchSize: " + testBatchSize);
      System.exit(1);
    }

    numBatches = thisFunc.dataDimension()/testBatchSize;

    sayln("StochasticDiffFunctionTester created with:");
    sayln("   data dimension  = " + thisFunc.dataDimension());
    sayln("   batch size = " + testBatchSize);
    sayln("   number of batches = " + numBatches);

  }



  private void sayln(String s) {
    if (!quiet) {
      System.err.println(s);
    }
  }








  //  Get Prime Factors of an integer ....
  //  Code was originally from    http://www.idinews.com/sourcecode/IntegerFunction.html
  //  Decompose integer into prime factors
  //  ------------------------------------

  //  Upon return result[0] contains the number of factors (0 if N is 0), and
  //  result[1] . . . result[result[0]] contain the factors in ascending order.

  private static long[] primeFactors(long N)
  {long [] fctr = new long[64];       //  Result array
    long n = Math.abs(N);              //  Guard against negative

    short fctrIndex = 0;

    if (n > 0) {                       //  Guard against zero

      //  First do special cases 2 and 3

      while (n % 2 == 0)  {fctr[++fctrIndex] = 2; n /= 2;}
      while (n % 3 == 0)  {fctr[++fctrIndex] = 3; n /= 3;}

      //  Then every 6n-1 and 6n+1 until the divisor exceeds the square root
      //  of the current quotient.  NOTE:  Some trial divisors will be
      //  non-primes, e.g. 25, 35, 49, 55.  They have no effect, however,
      //  since their prime factors will already have been tried.

      for (int k = 5; k*k <= n; k += 6)
        for (int dvsr = k; dvsr <= k+2; dvsr+=2)
        { while (n % dvsr == 0)
        {fctr[++fctrIndex] = dvsr;  n /= dvsr;}
        }

      if (n > 1) fctr[++fctrIndex] = n; //  Store final factor, if any
    }

    fctr[0] = fctrIndex;                //  Store number of factors
    return fctr;
  }


  /**
   *      getTestBatchSize - This function takes as input the size of the data and returns the largest factor of the data size
   *    this is done so that when testing the function we are gaurenteed to have equally sized batches, and that the fewest
   *    number of evaluations needs to be made in order to test the function.
   *
   * @param size   - The size of the current data set
   * @return         The largest factor of the data size
   */
  private static long getTestBatchSize(long size){

    long testBatchSize = 1;

    long[] factors = primeFactors( size );

    long factorCount = factors[0];

    // Calculate the batchsize for the factors
    if( factorCount == 0 ){
      System.err.println("Attempt to test function on data of prime dimension.  This would involve a batchSize of 1 and may take a very long time.");
      System.exit(1);
    }else if (factorCount == 2){
      testBatchSize = (int) factors[1];
    }else {
      //  find the largest factor.
      for( int f = 1; f< factorCount;f++){
        testBatchSize *= factors[f];
      }
    }

    return testBatchSize;
  }







  /**
   *
   * This function tests to make sure that the sum of the stochastic calculated gradients is equal to the
   *  full gradient.  This requires using ordered sampling, so if the ObjectiveFunction itself randomizes
   *  the inputs this function will likely fail.
   *
   *
   * @param x   is the point to evaluate the function at
   * @param functionTolerance   is the tolerance to place on the infinity norm of the gradient and value
   * @return  boolean indicating success or failure.
   */

  public boolean testSumOfBatches(double[] x, double functionTolerance){
    boolean ret = false;
    System.err.println("Making sure that the sum of stochastic gradients equals the full gradient");


    AbstractStochasticCachingDiffFunction.SamplingMethod tmpSampleMethod = thisFunc.sampleMethod;
    StochasticCalculateMethods tmpMethod = thisFunc.method;

    //Make sure that our function is using ordered sampling.  Otherwise we have no gaurentees.
    thisFunc.sampleMethod = AbstractStochasticCachingDiffFunction.SamplingMethod.Ordered;

    if(thisFunc.method==StochasticCalculateMethods.NoneSpecified){
      System.err.println("No calculate method has been specified");
    }

    approxValue = 0;
    approxGrad = new double[x.length];
    curGrad = new double[x.length];
    fullGrad = new double[x.length];
    double percent = 0.0;

    //This loop runs through all the batches and sums of the calculations to compare against the full gradient
    for (int i = 0; i < numBatches ; i ++){

      percent = 100*((double) i)/(numBatches);


      //  update the value
      approxValue += thisFunc.valueAt(x,v,testBatchSize);

      //  update the gradient
      thisFunc.returnPreviousValues = true;
      System.arraycopy(thisFunc.derivativeAt(x,v,testBatchSize ), 0,curGrad, 0, curGrad.length);

      //Update Approximate
      approxGrad = ArrayMath.pairwiseAdd(approxGrad,curGrad);
      double norm = ArrayMath.norm(approxGrad);

      System.err.printf("%5.1f percent complete  %6.2f \n",percent,norm);

    }

    // !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
    // Get the full gradient and value, these should equal the approximates
    // !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
    System.err.println("About to calculate the full derivative and value");
    System.arraycopy(thisFunc.derivativeAt(x),0,fullGrad,0,fullGrad.length);
    thisFunc.returnPreviousValues = true;
    fullValue = thisFunc.valueAt(x);

    diff = new double[x.length];

    if( (ArrayMath.norm_inf(diff = ArrayMath.pairwiseSubtract(fullGrad,approxGrad))) < functionTolerance){
      sayln("");
      sayln("Success: sum of batch gradients equals full gradient");
      ret = true;
    }else{
      diffNorm = ArrayMath.norm(diff);
      sayln("");
      sayln("Failure: sum of batch gradients minus full gradient has norm " + diffNorm);
      ret = false;
    }


    if(Math.abs(approxValue - fullValue) < functionTolerance){
      sayln("");
      sayln("Success: sum of batch values equals full value");
      ret = true;
    }else{
      sayln("");
      sayln("Failure: sum of batch values minus full value has norm " + Math.abs(approxValue - fullValue));
      ret = false;
    }

    thisFunc.sampleMethod = tmpSampleMethod;
    thisFunc.method = tmpMethod;

    return ret;
  }








  /**
   *
   * This function tests to make sure that the sum of the stochastic calculated gradients is equal to the
   *  full gradient.  This requires using ordered sampling, so if the ObjectiveFunction itself randomizes
   *  the inputs this function will likely fail.
   *
   *
   * @param x   is the point to evaluate the function at
   * @param functionTolerance   is the tolerance to place on the infinity norm of the gradient and value
   * @return  boolean indicating success or failure.
   */

  public boolean testDerivatives(double[] x, double functionTolerance){
    boolean ret = false;
    boolean compareHess = true;
    System.err.println("Making sure that the stochastic derivatives are ok.");


    AbstractStochasticCachingDiffFunction.SamplingMethod tmpSampleMethod = thisFunc.sampleMethod;
    StochasticCalculateMethods tmpMethod = thisFunc.method;

    //Make sure that our function is using ordered sampling.  Otherwise we have no gaurentees.
    thisFunc.sampleMethod = AbstractStochasticCachingDiffFunction.SamplingMethod.Ordered;

    if(thisFunc.method==StochasticCalculateMethods.NoneSpecified){
      System.err.println("No calculate method has been specified");
    } else if( !thisFunc.method.calculatesHessianVectorProduct() ){
      compareHess = false;
    }

    approxValue = 0;
    approxGrad = new double[x.length];
    curGrad = new double[x.length];
    Hv = new double[x.length];


    double percent = 0.0;

    //This loop runs through all the batches and sums of the calculations to compare against the full gradient
    for (int i = 0; i < numBatches ; i ++){

      percent = 100*((double) i)/(numBatches);

      //Can't figure out how to get a carriage return???  ohh well
      System.err.printf("%5.1f percent complete\n",percent);


      //  update the "hopefully" correct Hessian
      thisFunc.method = tmpMethod;
      System.arraycopy(thisFunc.HdotVAt(x,v,testBatchSize),0,Hv,0,Hv.length);

      //  Now get the hessian through finite difference
      thisFunc.method = StochasticCalculateMethods.ExternalFiniteDifference;
      System.arraycopy(thisFunc.derivativeAt(x,v,testBatchSize ), 0,gradFD, 0, gradFD.length);
      thisFunc.recalculatePrevBatch = true;
      System.arraycopy(thisFunc.HdotVAt(x,v,gradFD,testBatchSize),0,HvFD,0,HvFD.length);

      //Compare the difference
      double DiffHv = ArrayMath.norm_inf(ArrayMath.pairwiseSubtract(Hv,HvFD));

      //Keep track of the biggest H.v error
      if (DiffHv > maxHvDiff){maxHvDiff = DiffHv;}

    }

    if( maxHvDiff < functionTolerance){
      sayln("");
      sayln("Success: Hessian approximations lined up");
      ret = true;
    }else{
      sayln("");
      sayln("Failure: Hessian approximation at somepoint was off by " + maxHvDiff);
      ret = false;
    }

    thisFunc.sampleMethod = tmpSampleMethod;
    thisFunc.method = tmpMethod;

    return ret;
  }


  /*
  This function is used to get a lower bound on the condition number.  as it stands this is pretty straight forward:

    a random point (x) and vector (v) are generated, the Raleigh quotient ( v.H(x).v / v.v ) is then taken which provides both
    a lower bound on the largest eigenvalue, and an upper bound on the smallest eigenvalue.  This can then be used to
    come up with a lower bound on the condition number of the hessian.
   */

  public double testConditionNumber(int samples){
    double maxSeen = 0.0;
    double minSeen = 0.0;
    double[] thisV = new double[ thisFunc.domainDimension() ];
    double[] thisX = new double[thisV.length];
    gradFD = new double[thisV.length];
    HvFD = new double[thisV.length];

    double thisVHV;
    boolean isNeg = false;
    boolean isPos = false;
    boolean isSemi = false;

    thisFunc.method = StochasticCalculateMethods.ExternalFiniteDifference;

    for(int j=0;j<samples;j++){

      for (int i=0; i< thisV.length; i++){
        thisV[i] = generator.nextDouble();
      }
      for (int i=0; i< thisX.length; i++){
        thisX[i] = generator.nextDouble();
      }

      System.err.println("Evaluating Hessian Product");
      System.arraycopy(thisFunc.derivativeAt(thisX,thisV,testBatchSize ), 0,gradFD, 0, gradFD.length);
      thisFunc.recalculatePrevBatch = true;
      System.arraycopy(thisFunc.HdotVAt(thisX,thisV,gradFD,testBatchSize),0,HvFD,0,HvFD.length);

      thisVHV = ArrayMath.innerProduct(thisV,HvFD);

      if( Math.abs(thisVHV) > maxSeen){
        maxSeen = Math.abs(thisVHV);
      }

      if( Math.abs(thisVHV) < minSeen){
        minSeen = Math.abs(thisVHV);
      }

      if( thisVHV < 0 ){
        isNeg = true;
      }

      if( thisVHV > 0){
        isPos = true;
      }

      if( thisVHV ==0 ){
        isSemi = true;
      }

      System.err.println("It:" + j + "  C:" + maxSeen/minSeen + "N:" + isNeg + "P:" + isPos + "S:" + isSemi);
    }

    System.out.println("Condition Number of: " + maxSeen/minSeen);
    System.out.println("Is negative: " + isNeg);
    System.out.println("Is positive: " + isPos);
    System.out.println("Is semi:     " + isSemi);

    return maxSeen/minSeen;
  }


  public double[] getVariance(double[] x){
    return getVariance(x,testBatchSize);
  }

  public double[] getVariance(double[] x, int batchSize){

    double[] ret = new double[4];
    double[] fullHx = new double[thisFunc.domainDimension()];
    double[] thisHx = new double[x.length];
    double[] thisGrad = new double[x.length];
    List<double[]> HxList = new ArrayList<double[]>();

    /*
    PrintWriter file = null;
    NumberFormat nf = new DecimalFormat("0.000E0");

    try{
      file = new PrintWriter(new FileOutputStream("var.out"),true);
    }
    catch (IOException e){
      System.err.println("Caught IOException outputing List to file: " + e.getMessage());
      System.exit(1);
    }
    */

    //get the full hessian
    thisFunc.sampleMethod = AbstractStochasticCachingDiffFunction.SamplingMethod.Ordered;
    System.arraycopy(thisFunc.derivativeAt(x,x,thisFunc.dataDimension()),0,thisGrad,0,thisGrad.length);
    System.arraycopy(thisFunc.HdotVAt(x,x,thisGrad,thisFunc.dataDimension()),0,fullHx,0,fullHx.length);
    double fullNorm = ArrayMath.norm(fullHx);
    double hessScale = ((double) thisFunc.dataDimension()) / ((double) batchSize);
    thisFunc.sampleMethod = AbstractStochasticCachingDiffFunction.SamplingMethod.RandomWithReplacement;

    int n = 100;
    double simDelta;
    double ratDelta;
    double simMean = 0;
    double ratMean = 0;
    double simS = 0;
    double ratS = 0;
    int k = 0;
    System.err.println(fullHx[4] +"  " + x[4]);
    for(int i = 0; i<n; i++){
      System.arraycopy(thisFunc.derivativeAt(x,x,batchSize),0,thisGrad,0,thisGrad.length);
      System.arraycopy(thisFunc.HdotVAt(x,x,thisGrad,batchSize),0,thisHx,0,thisHx.length);
      ArrayMath.multiplyInPlace(thisHx,hessScale);

      double thisNorm = ArrayMath.norm(thisHx);
      double sim = ArrayMath.innerProduct(thisHx,fullHx)/(thisNorm*fullNorm);
      double rat = thisNorm/fullNorm;
      k += 1;

      simDelta = sim - simMean;
      simMean += simDelta/k;
      simS += simDelta*(sim-simMean);

      ratDelta = rat-ratMean;
      ratMean += ratDelta/k;
      ratS += ratDelta*(rat-ratMean);

      //file.println( nf.format(sim) + " , " + nf.format(rat));

    }

    double simVar = simS/(k-1);
    double ratVar = ratS/(k-1);


    //file.close();

    ret[0]=simMean;
    ret[1]=simVar;
    ret[2]=ratMean;
    ret[3]=ratVar;
    return ret;
  }


  public void testVariance(double[] x){

    int[] batchSizes = {10,20,35,50,75,150,300,500,750,1000,5000,10000};
    double[] varResult;

    PrintWriter file = null;
    NumberFormat nf = new DecimalFormat("0.000E0");

    try{
      file = new PrintWriter(new FileOutputStream("var.out"),true);
    }
    catch (IOException e){
      System.err.println("Caught IOException outputing List to file: " + e.getMessage());
      System.exit(1);
    }

    for(int bSize:batchSizes){

      varResult = getVariance(x,bSize);
      file.println(bSize + "," + nf.format(varResult[0]) + "," + nf.format(varResult[1]) + "," + nf.format(varResult[2]) + "," + nf.format(varResult[3]));
      System.err.println("Batch size of: " + bSize + "   " + varResult[0] + "," + nf.format(varResult[1]) + "," + nf.format(varResult[2]) + "," + nf.format(varResult[3]));
    }


    file.close();

  }

  /*
  public double getNormVariance(List<double[]> thisList){
    double[] ratio = new double[thisList.size()];
    double[] mean = new double[thisList.get(0).length];
    double sizeInv = 1/( (double) thisList.size() );

    for(double[] arr:thisList){
      for(int i=0;i<arr.length;i++){
        mean[i] += arr[i]*sizeInv;
      }
    }

    double meanNorm = ArrayMath.norm(mean);

    for(int i=0;i<thisList.size();i++){
      ratio[i] = (ArrayMath.norm(thisList.get(i))/ meanNorm);
    }

    arrayToFile(ratio,"ratio.out");

    return ArrayMath.variance(ratio);

  }

  public double getSimVariance(List<double[]> thisList){

    double[] ang = new double[thisList.size()];
    double[] mean = new double[thisList.get(0).length];
    double sizeInv = 1/( (double) thisList.size() );

    for(double[] arr:thisList){
      for(int i=0;i<arr.length;i++){
        mean[i] += arr[i]*sizeInv;
      }
    }

    double meanNorm = ArrayMath.norm(mean);

    for(int i=0;i<thisList.size();i++){
      ang[i] = ArrayMath.innerProduct(thisList.get(i),mean);
      ang[i] = ang[i]/ ( meanNorm * ArrayMath.norm(thisList.get(i)));
    }

    arrayToFile(ang,"angle.out");

    return ArrayMath.variance(ang);
  }
  */

  public void listToFile(List<double[]> thisList,String fileName){
    PrintWriter file = null;
    NumberFormat nf = new DecimalFormat("0.000E0");

    try{
      file = new PrintWriter(new FileOutputStream(fileName),true);
    }
    catch (IOException e){
      System.err.println("Caught IOException outputing List to file: " + e.getMessage());
      System.exit(1);
    }

    for(double[] element:thisList){
      for(double val:element){
        file.print(nf.format(val) + "  ");
      }
      file.println("");
    }

    file.close();

  }

  public void arrayToFile(double[] thisArray,String fileName){
    PrintWriter file = null;
    NumberFormat nf = new DecimalFormat("0.000E0");

    try{
      file = new PrintWriter(new FileOutputStream(fileName),true);
    }
    catch (IOException e){
      System.err.println("Caught IOException outputing List to file: " + e.getMessage());
      System.exit(1);
    }

    for(double element:thisArray){
      file.print(nf.format(element) + "  ");
    }

    file.close();

  }

  /**
   * testObjectiveFunction
   *  This function was written to provide a test for accuracy of stochastic objective functions.  The test
   *  checks for the following properties:
   *
   * 1) The sum of the value over each batch equals the full value
   * 2) The sum of the gradients over each batch equals the full gradient
   * 3) The gradient calculated using Incorporated Finite Difference is never more than functionTolerance from the
   *        gradient using External Finite Difference
   * 4) The hessian vector also does not varry between Incorporated and External Finite Difference
   *
   * @param function          The function to test
   * @param x                 The point to use for testing (v is generated randomly
   * @param functionTolerance The tolerance
   */

  /*
  public boolean testObjectiveFunction(Function function, double[] x, double functionTolerance){



    approxGrad = new double[x.length];
    curGrad = new double[x.length];
    approxValue = 0;

    //Generate the initial vectors
    for (int i = 0; i < x.length; i ++){
      approxGrad[i] = 0;
      v[i] = generator.nextDouble() ;
    }


    //This loop runs through all the batches and sums of the calculations to compare against the full gradient
    for (int i = 0; i < numBatches ; i ++){

// !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
// Perform calculation using IncorporatedFiniteDifference
// !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!

      dfunction.method = StochasticCalculateMethods.IncorporatedFiniteDifference;

      //  update the value
      approxValue += dfunction.valueAt(x,v,testBatchSize);

      //  update the gradient
      dfunction.returnPreviousValues = true;
      System.arraycopy(dfunction.derivativeAt(x,v,testBatchSize ), 0,curGrad, 0, curGrad.length);

      //  update the Hessian
      dfunction.returnPreviousValues = true;
      System.arraycopy(dfunction.HdotVAt(x,v,testBatchSize),0,HvAD,0,HvAD.length);

      //Update Approximate
      approxGrad = ArrayMath.pairwiseAdd(approxGrad,curGrad);

 // !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
 // Perform calculations using external finite difference
 // !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!

      dfunction.method = StochasticCalculateMethods.ExternalFiniteDifference;

      dfunction.recalculatePrevBatch = true;
      System.arraycopy(dfunction.derivativeAt(x,v,testBatchSize ), 0,gradFD, 0, gradFD.length);

      dfunction.recalculatePrevBatch = true;
      System.arraycopy(dfunction.HdotVAt(x,v,gradFD,testBatchSize),0,HvFD,0,HvFD.length);

      double DiffGrad = ArrayMath.norm_inf(ArrayMath.pairwiseSubtract(gradFD,curGrad));

      // Keep track of the biggest error.
      if (DiffGrad > maxGradDiff){maxGradDiff = DiffGrad;}

      double DiffHv = ArrayMath.norm_inf(ArrayMath.pairwiseSubtract(HvAD,HvFD));

      //Keep track of the biggest H.v error
      if (DiffHv > maxHvDiff){maxHvDiff = DiffHv;}
    }

 // !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
 // Get the full gradient and value, these should equal the approximates
 // !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
    System.arraycopy(dfunction.derivativeAt(x),0,fullGrad,0,fullGrad.length);
    fullValue = dfunction.valueAt(x);


    if(ArrayMath.norm_inf(ArrayMath.pairwiseSubtract(fullGrad,approxGrad)) < functionTolerance){
      sayln("");
      sayln("  Gradient is looking good");
    }else{
      diff = new double[x.length];
      diff = ArrayMath.pairwiseSubtract(approxGrad,fullGrad);
      diffNorm = ArrayMath.norm(diff);
      sayln("");
      sayln("  Seems there is a problem.  Gradient is off by norm of " + diffNorm);
    };

    if( maxGradDiff < functionTolerance ){
      sayln("");
      sayln("  Both gradients are the same");
    }else{
      diffValue = approxValue - fullValue;
      sayln("");
      sayln("  Seems there is a problem.  The two methods of calculating the gradient are different  max |AD-FD|_inf Error of " + maxGradDiff);
    };


    if( Math.abs(fullValue - approxValue) < functionTolerance){
      sayln("");
      sayln("  Value is looking good");
    }else{
      diffValue = approxValue - fullValue;
      sayln("");
      sayln("  Seems there is a problem.  Value is off by " + diffValue);
    };

    if(maxHvDiff < functionTolerance){
      sayln("");
      sayln("  Hv Approimations line up well");
    }else{
      sayln("");
      sayln("    Seems there is a problem.  Hv approximations aren't quite close enough -- max |AD-FD|_inf Error of " + maxHvDiff);
    }

    return true;
  }
*/

}
