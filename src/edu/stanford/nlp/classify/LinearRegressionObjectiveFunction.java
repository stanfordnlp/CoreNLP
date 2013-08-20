package edu.stanford.nlp.classify;

import java.util.Arrays;

import edu.stanford.nlp.math.ArrayMath;
import edu.stanford.nlp.optimization.AbstractCachingDiffFunction;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.stats.TwoDimensionalCounter;
import edu.stanford.nlp.util.Index;


/**
 * Minimizes the regularized least squares error defined as follows:
 * <code>min_w (y-Xw)'(y-Xw) + lambda*w'w</code>.
 * where <code>X</code> is an <code>N x K</code> matrix of N examples and K features,
 * <code>y</code> is an <code>N x 1</code> column vector of functional values for each example and,
 * <code>w</code> is the learned </code>K x 1</code> weight vector.
 * 
 * This implementation exploits symmetry, sparsity and uses caching of sufficient statistics
 * to speed up the computation.
 * 
 * @author Ramesh Nallapati (nmramesh@cs.stanford.edu)
 * 
 */

public class LinearRegressionObjectiveFunction<F> extends AbstractCachingDiffFunction {

  protected int numFeatures = 0;
  protected int numData = 0;
  protected int[][] data = null;
  protected double[][] values = null;
  protected double[] y = null;

  protected double yTy = 0; //y'y, computed only once.

  protected TwoDimensionalCounter<Integer,Integer> xTx; //X'X, a K x K matrix, computed only once. 
  protected double[] xTy; //X'y, a K x 1 matrix, computed only once.  
  protected double regularizationCoeff = 0;

  protected Index<F> featureIndex = null;


  @Override
  public int domainDimension() {
    return numFeatures;
  }

  /**
   * we store only upper triangular entries of the X'X matrix.
   * we return the values in the lower triangular part by exploiting its symmetry.
   * @param i
   * @param j
   * @return the value of the element X'X[i][j]
   */
  private double xTxGetValueAt(int i, int j){
    if(i <= j)return xTx.getCount(i, j);
    return xTx.getCount(j, i);
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
    /*
     * value = (y-Xw)^2 + lambda*w'w 
     */
    
    //first compute lambda*w'w
    value = regularizationCoeff*ArrayMath.innerProduct(w, w);

    //now add (y-Xw)'(y-Xw)
    double[] yMinusXw = new double[numData]; //holds y - Xw      
    for(int i = 0; i < numData; i++){
      yMinusXw[i] = y[i];
      for(int j = 0; j < data[i].length; j++){
        int fID = data[i][j];
        yMinusXw[i] -= values[i][j]*w[fID];
      }
    }
    value+= ArrayMath.innerProduct(yMinusXw, yMinusXw);
    
    
    /* value = y'y - 2w'X'y + w'X'Xw + lambda*w'w
     * derivative = 2*((X'X + \lambda*I)w - X'y).
     * complexity is O(K^2) where K is the number of features.
     * but the actual complexity is made substantially lower by exploiting of sparsity.
     */     
    
    //first compute 2X'Xw term 
    for(int i :xTx.firstKeySet()){
      Counter<Integer> secondKeyCounter = xTx.getCounter(i);
      for(int j : secondKeyCounter.keySet()){
        derivative[i] += 2*xTxGetValueAt(i, j)*w[j];
        if(i != j)
          derivative[j] += 2*xTxGetValueAt(i, j)*w[i]; 
        //this line is to take care of the terms corresponding to the lower triangular part of the X'X matrix.
      }      
    }
    
    //now compute 2\lambda*I*w - X'y
    for(int i= 0; i < numFeatures; i++)
      derivative[i] += 2*(regularizationCoeff*w[i] - xTy[i]);
    
  }
  
  /**
   * computes and saves yTy, xTx and xTy. Has a sparse representation for xTx
   * they are re-used for all subsequent computations because they don't depend on w.
   */
  private void computeSuffStats(){
    yTy = ArrayMath.innerProduct(y, y); 
    xTx = new TwoDimensionalCounter<Integer,Integer>();
    xTy = new double[numFeatures];
    Arrays.fill(xTy,0);
    if(values == null){ //this is not a real valued dataset.
      for(int n = 0; n < data.length; n++){
        for(int i = 0; i < data[n].length; i++){
          int featureAti = data[n][i];
          xTy[featureAti] += y[n];
          for(int j = i; j < data[n].length; j++){ //don't start j from 0 because the xTx matrix is symmetric
            {              
              int featureAtj = data[n][j];
              if(featureAti < featureAtj)
                xTx.incrementCount(featureAti, featureAtj);
              else
                xTx.incrementCount(featureAtj, featureAti);
            }
            
          }
        }
      }
    }
    else{
      for(int n = 0; n < data.length; n++){
        for(int i = 0; i < data[n].length; i++){
          int featureAti = data[n][i];
          xTy[featureAti] += y[n]*values[n][i];
          for(int j = i; j < data[n].length; j++){ //don't start j from 0 because the xTx matrix is symmetric            
            int featureAtj = data[n][j];
            double value = values[n][i]*values[n][j];
            if(featureAti < featureAtj)
              xTx.incrementCount(featureAti, featureAtj,value);
            else
              xTx.incrementCount(featureAtj, featureAti,value);
          }
        }
      }
    }   
  }

  public LinearRegressionObjectiveFunction(GeneralDataset<Double, F> dataset) {
    this(dataset, 1.0);
  }

  public LinearRegressionObjectiveFunction(GeneralDataset<Double, F> dataset, double regularizationCoeff) {
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
    //we need y'y for computing the objective function value in each iteration, so save it as a class-level field.
    this.regularizationCoeff = regularizationCoeff;
    computeSuffStats();
  }
  
  /**
   * This is only for debugging purposes.
   */
  private void printSuffStats(){
    System.out.println(">>> Printing X'X:");
    for(int i = 0; i < numFeatures; i++){
      for(int j = 0; j < numFeatures; j++){
       System.out.printf("%f ", xTxGetValueAt(i, j)); 
      }
      System.out.println();
    }
    System.out.println(">>> Printing X'y:");
    for(int i = 0; i < numFeatures; i++)
      System.out.printf("%f ", xTy[i]);
    System.out.println();
    System.out.printf(">>> Printing y'y: %f\n",yTy);
  }

  /**
   * This constructor is only for testing.
   * @param y
   * @param data
   * @param numFeatures
   * @param regularizer
   */
  private LinearRegressionObjectiveFunction(double[] y, int[][] data, double[][] values, int numFeatures, double regularizer){
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
    new LinearRegressionObjectiveFunction<Integer>(y, data, values, numFeatures, regularizer);
  }
}
