package edu.stanford.nlp.math;

import java.util.Collection;

import it.unimi.dsi.fastutil.doubles.DoubleCollection;
import it.unimi.dsi.fastutil.doubles.DoubleIterator;

/**
 * Math applied to fastutil collections and java.util.Collections.
 * 
 * Note that the fastutil versions avoid some boxing and unboxing of numbers
 * and so should be preferred for speed-critical code. 
 * 
 * @author dramage
 * @author bethard
 */
public class CollectionsMath {

  /**
   * Returns the mean of the values in the collection.
   */
  public static double mean(DoubleCollection values) {
    double sum = 0;
    int count = 0;
    
    for (DoubleIterator it = values.iterator(); it.hasNext();) {
      sum += it.nextDouble();
      count++;
    }
    
    return sum / count;
  }
  
  /**
   * Returns the variance of the values in the collection.
   */
  public static double variance(DoubleCollection values) {
    final double mean = mean(values);
    double result = 0;
    
    for (DoubleIterator it = values.iterator(); it.hasNext();) {
      final double diff = it.nextDouble() - mean;
      result += (diff * diff);
    }
    
    return result / (values.size() - 1);
  }
  
  /**
   * Returns the standard deviation of the values in the collection.
   */
  public static double stdev(DoubleCollection values) {
    return Math.sqrt(variance(values));
  }
  
  /**
   * Returns the minimum of the values in the collection.
   */
  public static double min(Collection<Double> values) {
    if (values.size() == 0) {
      throw new IllegalArgumentException("min of empty collection");
    }
    double min = Double.MAX_VALUE;
    for (double value: values) {
      if (value < min) {
        min = value;
      }
    }
    return min;
  }

  /**
   * Returns the maximum of the values in the collection.
   */
  public static double max(Collection<Double> values) {
    if (values.size() == 0) {
      throw new IllegalArgumentException("max of empty collection");
    }
    double max = Double.MIN_VALUE;
    for (double value: values) {
      if (value > max) {
        max = value;
      }
    }
    return max;
  }

  /**
   * Returns the mean of the values in the collection.
   */
  public static double mean(Collection<Double> values) {
    double sum = 0;
    for (double value: values) {
      sum += value;
    }
    return sum / values.size();
  }
  
  /**
   * Returns the variance of the values in the collection.
   */
  public static double variance(Collection<Double> values) {
    double mean = mean(values);
    double result = 0;
    for (double value: values) {
      double diff = value - mean;
      result += diff * diff;
    }
    return result / (values.size() - 1);
  }

  /**
   * Returns the standard deviation of the values in the collection.
   */
  public static double stdev(Collection<Double> values) {
    return Math.sqrt(variance(values));
  }
  
}
