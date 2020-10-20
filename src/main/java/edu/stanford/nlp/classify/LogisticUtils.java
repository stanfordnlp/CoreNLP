package edu.stanford.nlp.classify;

import java.util.Arrays;
import java.util.Collection;

import edu.stanford.nlp.math.ArrayMath;
import edu.stanford.nlp.util.Index;

/**
 * A central place for utility functions used when training robust logistic models.
 * @author jtibs
 */
public class LogisticUtils {
  
  public static int[][] identityMatrix(int n) {
    int[][] result = new int[n][1];
    for (int i = 0; i < n; i++)
      result[i][0] = i;
    return result;
  }
  
  public static double[] flatten(double[][] input) {
    int length = 0;
    for (double[] array : input)
      length += array.length;
    
    double[] result = new double[length];
    int count = 0;
    for (double[] array : input) {
      for (double value : array)
        result[count++] = value;
    }
    return result;
  }
  
  public static void unflatten(double[] input, double[][] output) {
    int count = 0;
    for (int i = 0; i < output.length; i++) {
      for (int j = 0; j < output[i].length; j++) {
        output[i][j] = input[count++];
      }
    }
  }
  
  public static double dotProduct(double[] array, int[] indices, double[] values) {
    double result = 0;
    for (int i = 0; i < indices.length; i++) {
      if (indices[i] == -1) continue;
      result += array[indices[i]] * values[i];
    }
    return result;
  }
  
  public static double[][] initializeDataValues(int[][] data) {
    double[][] result = new double[data.length][];
    for (int i = 0; i < data.length; i++) {
      result[i] = new double[data[i].length];
      Arrays.fill(result[i], 1.0);
    }
    return result;
  }
  
  public static <T> int[] indicesOf(Collection<T> input, Index<T> index) {
    int[] result = new int[input.size()];
    int count = 0;
    for (T element : input)
      result[count++] = index.indexOf(element);
    
    return result;
  }
  
  public static double[] convertToArray(Collection<Double> input) {
    double[] result = new double[input.size()];
    int count = 0;
    for (double d : input) {
      result[count++] = d;
    }
    
    return result;
  }
 
  public static double[] calculateSums(double[][] weights, int[] featureIndices,
      double[] featureValues) {
    int numClasses = weights.length + 1;
    
    double[] result = new double[numClasses];
    result[0] = 0.0;
    for (int c = 1; c < numClasses; c++) {
      result[c] = -dotProduct(weights[c - 1], featureIndices, featureValues);
    }
    double total = ArrayMath.logSum(result);
    for (int c = 0; c < numClasses; c++) {
      result[c] -= total;
    }
    
    return result;
  }
  
  public static double[] calculateSums(double[][] weights, int[] featureIndices,
      double[] featureValues, double[] intercepts) {
    int numClasses = weights.length + 1;
    
    double[] result = new double[numClasses];
    result[0] = 0.0;
    for (int c = 1; c < numClasses; c++) {
      result[c] = -dotProduct(weights[c - 1], featureIndices, featureValues) - intercepts[c - 1];
    }
    double total = ArrayMath.logSum(result);
    for (int c = 0; c < numClasses; c++) {
      result[c] -= total;
    }
    
    return result;
  }
  
  public static double[] calculateSigmoids(double[][] weights, int[] featureIndices,
      double[] featureValues) {
    return ArrayMath.exp(calculateSums(weights, featureIndices, featureValues));
  }
  
  public static double getValue(double[][] weights, LogPrior prior) {
    double[] flatWeights = flatten(weights);
    return prior.compute(flatWeights, new double[flatWeights.length]);
  }
  
  public static int sample(double[] sigmoids) {
    double probability = Math.random();
    System.out.println("sigmoids: " + Arrays.toString(sigmoids));
    System.out.println("probability: " + probability);
    double offset = 0.0;
    for (int c = 0; c < sigmoids.length; c++) {
      if (probability - offset <= sigmoids[c])
        return c;
      offset += sigmoids[c];
    }
    return sigmoids.length - 1;  // should never be reached
  }
  
  public static void prettyPrint(double[][] gammas, double[][] thetas, double[][] zprobs) {
    prettyPrint("GAMMAS", gammas);
    prettyPrint("THETAS", thetas);
    prettyPrint("ZPROBS", zprobs);
  }
  
  public static void prettyPrint(String name, double[][] matrix) {
    prettyPrint(name, matrix, matrix.length);
  }
  
  public static void prettyPrint(String name, double[][] matrix, int maxCount) {
    System.out.println(name + ": ");
    for (double[] array : matrix) {
      System.out.println(Arrays.toString(array));
      if (maxCount-- < 0) break;
    }
    System.out.println();
  }
}

