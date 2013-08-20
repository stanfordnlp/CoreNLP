package edu.stanford.nlp.wsd.synsense;

import java.util.List;

/**
 * Class Disambiguator
 *
 * @author Teg Grenager
 */
public class Disambiguator {

  /**
   * Usage: java Disambiguator trainSet testSet
   *
   */
  public static void main(String[] args) throws Exception {
    List<Instance> trainSet = WSDParamSearcher.readInData(args[0]);
    List<Instance> testSet = WSDParamSearcher.readInData(args[1]);
    BasicModel model = new BasicModel();
    model.train(trainSet);
    List guesses = model.test(testSet);
    //  SynSense.evaluateSense(guesses, true);
  }
}
