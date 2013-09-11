/**
 * Title:        StanfordMaxEnt<p>
 * Description:  A Maximum Entropy Toolkit<p>
 * Copyright:    Copyright (c) Kristina Toutanova<p>
 * Company:      Stanford University<p>
 */

package edu.stanford.nlp.maxent;

import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.stats.ClassicCounter;


/**
 * This class represents the training samples. It can return statistics of
 * them, for example the frequency of each x or y in the training data.
 * It differs from the base class Experiments in that one keeps a HashMap
 * for each x of the y-s occuring.
 * Used when some x can have lots of y's possible.
 *
 * @author Kristina Toutanova
 * @author Christopher Manning
 * @version 1.0
 */
public class DenseExperiments extends Experiments {


  private Counter<Integer>[] pXY;

  public DenseExperiments() {
  }


  public DenseExperiments(String filename) {
    super(filename);
  }

  @Override
  public String toString() {
    return "DenseExperiments" + pXY.toString();
  }

  @Override
  public int numY(int x) {
    return ySize;
  }


  /**
   * Calculate empirical probabilities.
   *
   * @param ySize is either a positive number that specifies a pregiven
   *              number of classes for y, or 0 and then the number is calculated
   *              from the data.
   */
  @SuppressWarnings({"unchecked"})
  @Override
  public void ptilde(int ySize) {
    int maxX = 0;
    int maxYY = 0;
    double inc = 1 / (double) getNumber();

    for (int i = 0, number = getNumber(); i < number; i++) {
      if (maxX < vArray[i][0]) {
        maxX = vArray[i][0];
      }
      if (maxYY < vArray[i][1]) {
        maxYY = vArray[i][1];
      }
    }
    xSize = maxX + 1;
    if (ySize == 0) {
      ySize = maxYY + 1;
    } else {
      ySize = ySize;
    }
    px = new int[xSize];
    py = new int[ySize];
    pXY = new Counter[xSize];
    for (int j = 0; j < xSize; j++) {
      pXY[j] = new ClassicCounter<Integer>();
    }

    for (int i = 0, number = getNumber(); i < number; i++) {
      int xC = vArray[i][0];
      int yC = vArray[i][1];
      px[xC]++;
      py[yC]++;

      Integer yCInt = Integer.valueOf(yC);
      pXY[xC].incrementCount(yCInt, inc);
    } // for i

    vArray = null; /* not sure that is ok to do */
  }


  @Override
  public double ptildeXY(int x, int y) {
    return pXY[x].getCount(Integer.valueOf(y));
  }

}
