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
import java.util.Map;
import java.util.Set;

import edu.stanford.nlp.util.IntTuple;


/**
 * A class to represent the non-zero valued features for an instance.
 * Given a class y and a feature number f, it should return f(y)
 * Also one should be able to iterate over non-zero valued things.
 */

public class FeaturesMap {
  HashMap<IntTuple, Double> valuesMap; // IntPair of y,fNo -> double
  static double nullvalue = 0;


  public FeaturesMap() {
    valuesMap = new HashMap<IntTuple, Double>();
  }

  /**
   * read in the map from a line
   */
  public FeaturesMap(String line) {

    valuesMap = new HashMap<IntTuple, Double>();
    int indSp = -1;
    while ((indSp = line.indexOf(" ")) > -1) {
      int y = Integer.parseInt(line.substring(0, indSp));
      line = line.substring(indSp + 1);
      indSp = line.indexOf(" ");
      if (indSp == -1) {
        indSp = line.length();
      }
      int fno = Integer.parseInt(line.substring(0, indSp));
      line = line.substring(indSp + 1);
      indSp = line.indexOf(" ");
      if (indSp == -1) {
        indSp = line.length();
      }
      double val = Double.parseDouble(line.substring(0, indSp));

      if (indSp < line.length()) {
        line = line.substring(indSp + 1);
      }
      edu.stanford.nlp.util.IntTuple iT = edu.stanford.nlp.util.IntTuple.getIntTuple(2);
      iT.set(0, y);
      iT.set(1, fno);
      valuesMap.put(iT, new Double(val));

    }


  }


  public void add(int y, int fno, double val) {
    edu.stanford.nlp.util.IntTuple iT = edu.stanford.nlp.util.IntTuple.getIntTuple(2);
    iT.set(0, y);
    iT.set(1, fno);
    valuesMap.put(iT, new Double(val));

  }


  public double value(int y, int fNo) {
    edu.stanford.nlp.util.IntTuple iT = edu.stanford.nlp.util.IntTuple.getIntTuple(2);
    iT.set(0, y);
    iT.set(1, fNo);
    Double val = valuesMap.get(iT);
    if (val == null) {
      return nullvalue;
    }
    return val.doubleValue();


  }

  public Set<Map.Entry<IntTuple, Double>> entries() {

    return valuesMap.entrySet();
  }


}
