/**
 * Title:        StanfordMaxEnt<p>
 * Description:  A Maximum Entropy Toolkit<p>
 * Copyright:    Copyright (c) Kristina Toutanova<p>
 * Company:      Stanford University<p>
 * @author Kristina Toutanova
 * @version 1.0
 */

package edu.stanford.nlp.maxent;


import edu.stanford.nlp.util.Index;
import edu.stanford.nlp.util.IntPair;

import java.util.HashSet;

/**
 * This is used when only binary features are needed. We don't need to keep the array of values valuesI
 * any more (unlike the parent class Feature)
 */

public class BinaryFeature extends Feature {

  /**
   * This is used for collecting the non-zero points (x,y)
   */
  private HashSet<Integer> setValues = new HashSet<Integer>();


  public BinaryFeature() {
  }


  /**
   * @return true if the feature does not have a non-zero value at any data point
   */
  @Override
  public boolean isEmpty() {
    return (setValues.isEmpty() && (indexedValues.length == 0));
  }

  /**
   * @param vals will have length equal to the number of training samples (x,y)
   *             vals contains only 0s and 1s
   */

  public BinaryFeature(Experiments e, double[] vals, Index<IntPair> instanceIndex) {
    this.instanceIndex = instanceIndex;
    domain = e;
    for (int i = 0; i < vals.length; i++) {
      if ((int) vals[i] == 1) {
        setValues.add(Integer.valueOf(indexOf(e.get(i)[0], e.get(i)[1])));
      }
    }
    getIndexed();
  }


  public BinaryFeature(Experiments e, int[] indVals, Index<IntPair> instanceIndex) {
    this.instanceIndex = instanceIndex;
    domain = e;
    this.indexedValues = indVals;
  }


  public BinaryFeature(Experiments domain, double[][] vals, Index<IntPair> instanceIndex) {
    this.instanceIndex = instanceIndex;
    Feature.domain = domain;
    for (int i = 0; i < domain.xSize; i++) {
      for (int j = 0; j < domain.ySize; j++) {
        if ((int) vals[i][j] == 1) {
          setValues.add(Integer.valueOf(indexOf(i, j)));
        }
      }
    }
    getIndexed();
  }


  public void getValues() {
    getIndexed();
    setValues.clear();

  }

  /**
   * Convert the setValues into an array of indexes of non-zero pairs indexedValues.
   */
  public void getIndexed() {
    Object[] indexedValues1 = setValues.toArray();
    indexedValues = new int[indexedValues1.length];
    for (int i = 0; i < indexedValues1.length; i++) {
      indexedValues[i] = ((Integer) indexedValues1[i]).intValue();

    }
  }

  /**
   * Print put the pairs for which the feature is true
   */
  @Override
  public void print() {

    System.out.println(" True for : ");
    if (indexedValues == null) {
      return;
    }
    for (int i = 0; i < indexedValues.length; i++) {
      System.out.println(getX(i) + " " + getY(i));
    }


  }


  @Override
  public double getVal(int index) {
    return 1.0;

  }


  /**
   * This does sequential search in indexedValues. It is slow.
   *
   * @param x the history Id
   * @param y the outcome Id
   * @return the value of the feature at the point (x,y)
   */
  @Override
  public double getVal(int x, int y) {
    int p = indexOf(x, y);
    for (int i = 0; i < indexedValues.length; i++) {
      if (p == indexedValues[i]) {
        return 1;
      }
    }
    return 0;

  }

}



