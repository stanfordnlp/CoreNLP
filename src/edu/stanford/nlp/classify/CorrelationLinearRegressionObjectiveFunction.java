package edu.stanford.nlp.classify;

import java.util.Arrays;

import edu.stanford.nlp.math.ArrayMath;
import edu.stanford.nlp.optimization.AbstractCachingDiffFunction;
import edu.stanford.nlp.util.Index;


/**
 * Maximizes the correlation between model output and true output unlike standard linear regression which optimizes squared error.
 * The new objective function is:
 * 
 * min_w -\sum_i(y'_i - avg(y'))(y_i-avg(y)) + 0.5\lambda\sum_i(y_i' - avg(y'))^2
 * 
 * where index i is over training examples, y'_i is the model output for datum i,
 * y_i is the true output for datum i, and avg(y) is the average of the output over all examples.
 * 
 * The first term is the negative covariance between the model and truth while 
 * the second term is the variance of the model output. In other words, 
 * we are minimizing negative covariance of the model output w.r.t true output 
 * while minimizing the variance of the model output,
 * which has the same effect as maximizing the correlation. 
 * 
 * 
 * Using y'_i = w^Tx_i, the objective function can be rewritten as:
 * 
 *    min_w -\sum_i w^T(x_i - avg(x))(y_i-avg(y)) + 0.5\lambda\sum_i(w^T(x_i - avg(x)))^2 
 * =  min_w -\sum_i w^T\delta(x_i)\delta(y_i) + 0.5\lambda\sum_i(w^T\delta(x_i))^2
 * 
 * where \delta(x_i) = x_i - avg(x_i). And the gradient is:
 * 
 *  \grad f(w) = -\sum_i \delta(x_i)\delta(y_i) + \lambda\sum_i (w^T\delta(x_i))\delta(x_i)
 *  
 * This implementation exploits symmetry, sparsity and uses caching of sufficient statistics
 * to speed up the computation.
 * 
 * @author Ramesh Nallapati (nmramesh@cs.stanford.edu)
 * 
 */

public class CorrelationLinearRegressionObjectiveFunction<F> extends AbstractCachingDiffFunction {

  protected int numFeatures = 0;
  protected int numData = 0;
  protected int[][] data = null;
  protected double[][] values = null;
  protected double[] y = null;
  
  protected double[] sumDeltaXDeltaY;
  protected double[] xAvg; 
  
  protected double regularizationCoeff = 1.0;

  protected Index<F> featureIndex = null;


  @Override
  public int domainDimension() {
    return numFeatures;
  }

 
  
  public int dataDimension(){
    return data.length;
  }
 
  @Override
  protected void calculate(double[] w) {    
    value = 0.0;
    if (derivative == null) {
      derivative = new double[w.length];
    } else {
      Arrays.fill(derivative, 0.0);
    }
    
    //initialize the values of value and derivative from suffstats.
    ArrayMath.addMultInPlace(derivative,sumDeltaXDeltaY,-1.0);    
    value -= ArrayMath.innerProduct(w, sumDeltaXDeltaY);
    
    for(int i = 0; i < numData; i++){
      double[] deltaX = new double[numFeatures];
      for(int j = 0;  j < numFeatures; j++)
        deltaX[j] = -xAvg[j];
      for(int j = 0; j < data[i].length; j++)
        deltaX[data[i][j]] += values[i][j];
      double wTDeltaX = ArrayMath.innerProduct(w, deltaX);
      value += 0.5*regularizationCoeff*Math.pow(wTDeltaX,2);      
      ArrayMath.addMultInPlace(derivative, deltaX, regularizationCoeff*wTDeltaX);
    }
           
  }
  
 
  private void computeSuffStats(){
   xAvg = new double[numFeatures];
   Arrays.fill(xAvg, 0);
   sumDeltaXDeltaY = new double[numFeatures];
   Arrays.fill(sumDeltaXDeltaY, 0);
   
   //first compute avgX
   for(int i = 0; i < numData; i++){
     for(int j = 0; j < data[i].length; j++)
       xAvg[data[i][j]]+=values[i][j];
   }
   ArrayMath.multiplyInPlace(xAvg, 1.0/numData);
   
   //now compute sumDeltaXDeltaY
   double yAvg = ArrayMath.average(y);
   double[] deltaX = new double[numFeatures];
   for(int i = 0; i < numData; i++){
     for(int j = 0;  j < numFeatures; j++)
       deltaX[j] = -xAvg[j];
     for(int j = 0; j < data[i].length; j++)
       deltaX[data[i][j]] += values[i][j];
     double deltaY = y[i] - yAvg;
     ArrayMath.addMultInPlace(sumDeltaXDeltaY, deltaX, deltaY);
   }
   
  }

  public CorrelationLinearRegressionObjectiveFunction(GeneralDataset<Double, F> dataset) {
    this(dataset, 1.0);
  }

  public CorrelationLinearRegressionObjectiveFunction(GeneralDataset<Double, F> dataset, double regularizationCoeff) {
    this.numFeatures = dataset.numFeatures();
    this.numData = dataset.size;
    this.data = dataset.getDataArray();
    this.values = dataset.getValuesArray();

   /*
    * The following part is kinda weird because GeneralDataset indexes the functional values y as labelIndex,
    * which makes no sense since each y_i can be potentially unique.
    * However, we only have GeneralDataset to use for now, so let's maintain status quo for now.
    */ 
    int[] labels  = dataset.getLabelsArray();
    y = new double[data.length];
    for(int n = 0; n < data.length; n++)
      y[n] = dataset.labelIndex.get(labels[n]);        
    this.regularizationCoeff = regularizationCoeff;
    computeSuffStats();
  }
  
  /**
   * This is only for debugging purposes.
   */
  private void printSuffStats(){
    
  }

  /**
   * This constructor is only for testing.
   * @param y
   * @param data
   * @param numFeatures
   * @param regularizer
   */
  private CorrelationLinearRegressionObjectiveFunction(double[] y, int[][] data, double[][] values, int numFeatures, double regularizer){
    this.y = y;
    this.data = data;
    this.values = values;
    this.regularizationCoeff = regularizer;
    this.numFeatures = numFeatures;
    this.numData = y.length;
    computeSuffStats();
    printSuffStats();
    
    double[] w = {-1.0,1.0};
    //Arrays.fill(w, 1.0);
    calculate(w);
    System.out.printf("value:%f\nderivatives:\n",value);
    for(int i = 0; i < numFeatures; i++)
      System.out.printf("%f ", derivative[i]);
    System.out.printf("\nDone!\n");
  }
  
  public static void main(String[] args){
    int numFeatures = 2;
    int[][] data = { {0,1},
                     {0}
                   };
    double[][] values = {
                          {1.0,2.0},
                          {1.0}
                        };
    double [] y = { 1.0, -1.0 };
    double regularizer = 1.0;
    new CorrelationLinearRegressionObjectiveFunction<Integer>(y, data, values, numFeatures, regularizer);
  }
}
