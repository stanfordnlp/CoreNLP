/**
 * Title:        StanfordMaxEnt<p>
 * Description:  A Maximum Entropy Toolkit<p>
 * Copyright:    Copyright (c) Kristina Toutanova<p>
 * Company:      Stanford University<p>
 * @author Kristina Toutanova
 * @version 1.0
 */


/**
 * This class is an interface to the MaxEnt paramter estimation.
 * The user can create a problem and receive the optimal paramters of the MaxEnt model.
 * Used only for binary features.
 */

package edu.stanford.nlp.maxent;

/**
 * A Problem with binary features
 */
public class BinaryProblem extends Problem {

  public BinaryProblem() {
  }

  /**
   * The format of the file is with the XML tags.
   */

  public BinaryProblem(String filename) {
    data = new Experiments(filename);
    functions = new BinaryFeatures(filename, data);
    this.fSize = functions.size();
    this.exSize = data.size();
  }

  public static void main(String[] args) {

  }

}
