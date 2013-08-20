
/**
 * Title:        StanfordMaxEnt<p>
 * Description:  A Maximum Entropy Toolkit<p>
 * Copyright:    Copyright (c) Kristina Toutanova<p>
 * Company:      Stanford University<p>
 * @author Kristina Toutanova
 * @version 1.0
 */


package edu.stanford.nlp.maxent;


/**
 * This class is used when we are sure the feature will have a value in 0-255 for each data pair
 */
public class ByteFeature extends Feature {

  /**
   * These are the non-zero values we want to keep for the points is indexedValues.
   */

  @SuppressWarnings("hiding")
  public byte[] valuesI;


  @Override
  public boolean isEmpty() {
    return (indexedValues.length == 0);
  }

  void getValues() {
  }

  public ByteFeature() {
  }


  public ByteFeature(Experiments e, int numElems) {
    Feature.domain = e;
    indexedValues = new int[numElems];
    this.valuesI = new byte[numElems];

  }




  /**
   * This is if we are given an array of double with a value for each training sample in the order of their occurence.
   */


  /**
   * @param vals a value for each (x,y) pair
   */
  public ByteFeature(Experiments e, byte[][] vals) {
    domain = e;
    int num = 0;
    for (int x = 0; x < e.xSize; x++) {
      for (int y = 0; y < e.ySize; y++) {
        if (vals[x][y] != 0) {
          num++;
        }
      }
    }
    indexedValues = new int[num];
    valuesI = new byte[num];
    int current = 0;
    for (int x = 0; x < e.xSize; x++) {
      for (int y = 0; y < e.ySize; y++) {
        if (vals[x][y] != 0) {
          indexedValues[current] = x * e.ySize + y;
          valuesI[current] = vals[x][y];
          current++;
        }//if
      }//for
    }
  }

  /**
   * @param indexes The pairs (x,y) for which the feature is non-zero. They are coded as x*ySize+y
   * @param vals    The values at these points.
   */
  public ByteFeature(Experiments e, int[] indexes, byte[] vals) {
    domain = e;
    indexedValues = indexes;
    this.valuesI = vals;
  }


  /**
   * used to sequentially set the values of a feature -- index is the pace in the arrays ; key goes into
   * indexedValues, and value goes into valuesI
   */
  public void setValue(int index, int key, byte value) {
    indexedValues[index] = key;
    this.valuesI[index] = value;

  }


  @Override
  public void setSum() {
    for (int i = 0; i < valuesI.length; i++) {
      sum += valuesI[i];
    }
  }


  @Override
  public double sumValues() {
    return sum;
  }


  @Override
  public double getVal(int index) {
    return this.valuesI[index];

  }

  @Override
  public double ftilde() {
    double s = 0.0;
    int x, y;
    for (int i = 0; i < indexedValues.length; i++) {
      x = indexedValues[i] / domain.ySize;
      y = indexedValues[i] - x * domain.ySize;
      s = s + domain.ptildeXY(x, y) * getVal(i);
    }

    return s;
  }
}
