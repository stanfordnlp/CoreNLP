/**
 * Title:        StanfordMaxEnt<p>
 * Description:  A Maximum Entropy Toolkit<p>
 * Copyright:    Copyright (c) Kristina Toutanova<p>
 * Company:      Stanford University<p>
 * @author Kristina Toutanova
 * @version 1.0
 */

package edu.stanford.nlp.maxent;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map.Entry;


/**
 * Do not keep the whole array of double
 * keep a HashMap of non-zero values
 */
public class SparseDataDouble extends DataDouble {
  HashMap<Integer,Double> xM = new HashMap<Integer, Double>();


  public SparseDataDouble() {
    xM = new HashMap<Integer, Double>();
  }

  public SparseDataDouble(int size) {
    xM = new HashMap<Integer, Double>();
  }


  public SparseDataDouble(double[] x, int y) {

    for (int j = 0; j < x.length; j++) {

      if (x[j] != 0) {
        xM.put(Integer.valueOf(j), new Double(x[j]));

      }
    }

    this.y = y;
  }


  public Iterator<Entry<Integer,Double>> iterator() {
    return xM.entrySet().iterator(); // to iterate over non-zero elements pairs Integer, Double

  }


  @Override
  public void setX(int index, double val) {
    setX(index, new Double(val));
  }

  public void setX(int index, Double xi) {
    xM.put(Integer.valueOf(index), xi);
  }


  @Override
  public double getX(int index) {

    Double val = xM.get(Integer.valueOf(index));
    if (val == null) {
      return 0;
    }
    return val.doubleValue();

  }


}
