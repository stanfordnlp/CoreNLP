/**
 * Title:        StanfordMaxEnt<p>
 * Description:  A Maximum Entropy Toolkit<p>
 * Copyright:    Copyright (c) Kristina Toutanova<p>
 * Company:      Stanford University<p>
 * @author Kristina Toutanova
 * @version 1.0
 */


package edu.stanford.nlp.maxent;

import edu.stanford.nlp.maxent.iis.LambdaSolve;

/**
 * This class is used to train loglinear models.
 * At present it can do this given a specification of a maxent problem - the training data, and the features.
 */
public class LoglinearFactory {
  private boolean hasPriors = false;
  // this flag is true when feature zero depends only on the calss y . if so, we are going to save the values of f0 in the classifier
  //to avoid huge test files and repeats of information



  /**
   * This method trains a loglinear model given two files specifying the problem - an experiments file, and a features file
   * The experiments file has format <x,y> per line - it shows the sequence of inputs x and outputs y as occuring in the training data
   * The features file specifies the for each feature, the set of pairs <x,y> for which it has a non-zero value
   * For more information on the file formats , see edu.stanford.nlp.maxent.iis.Experiments and edu.stanford.nlp.maxent.iis.Features
   */
  public LoglinearClassifier trainClassifier(String experimentsFile, String featuresFile) {
    Experiments e = new DenseExperiments(experimentsFile);
    Features feat = new Features(featuresFile, e);
    Problem p = new Problem(e, feat);
    //make LambdaSolve
    LambdaSolve prob = new LambdaSolve(p, .0001, .0001);
    prob.setNonBinary();
    int index = experimentsFile.lastIndexOf('/');
    String path = experimentsFile.substring(0, index) + "/temp_lambdas";
    System.out.println(" path is " + path);
    CGRunner runner = new CGRunner(prob, path, 1e-4, 1.0);

    runner.solve();
    LoglinearClassifier classifier = new LoglinearClassifier(e.ySize, feat.size(), prob.lambda);
    if (hasPriors) {
      double[] vals = new double[e.ySize];
      //iterate over the valuyes of f0
      Feature f = feat.get(0);
      for (int j = 0; j < f.len(); j++) {
        int y = f.getY(j);
        vals[y] = f.getVal(j);
      }

      classifier.setPriors(vals);

    }
    classifier.print(System.out);
    return classifier;
  }


  /**
   * Test the code
   * @param args three arguments - experiments file, features file, classifier save file
   */
  public static void main(String[] args) {
    LoglinearFactory factory = new LoglinearFactory();

    if (args[0].equals("-train")) {
      LoglinearClassifier cl = factory.trainClassifier(args[1], args[2]);
      //System.out.println(" Accuracy "+cl.accuracy(args[2],args[3]));
      cl.save(args[3]);
    }

    if (args[0].equals("-test")) {
      LoglinearClassifier cl1 = new LoglinearClassifier();
      cl1.read(args[1]);
      //cl1.saveProbs(args[2],args[3],args[4]);
      cl1.accuracy(args[2], args[3]);
    }
  }


}
