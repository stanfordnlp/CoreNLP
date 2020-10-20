
/*
* 	@Author:  Danqi Chen
* 	@Email:  danqi@cs.stanford.edu
*	@Created:  2014-09-01
* 	@Last Modified:  2014-09-30
*/

package edu.stanford.nlp.parser.nndep;

import java.util.ArrayList;
import java.util.List;

/**
 * Defines a list of training / testing examples in multi-class classification setting.
 *
 * @author Danqi Chen
 */

public class Dataset {

  int n;
  final int numFeatures, numLabels;
  final List<Example> examples;

  Dataset(int numFeatures, int numLabels) {
    n = 0;
    this.numFeatures = numFeatures;
    this.numLabels = numLabels;
    examples = new ArrayList<>();
  }

  public void addExample(List<Integer> feature, List<Integer> label) {
    Example data = new Example(feature, label);
    n += 1;
    examples.add(data);
  }

}
