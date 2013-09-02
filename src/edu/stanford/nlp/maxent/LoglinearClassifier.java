/**
 * Title:        StanfordMaxEnt<p>
 * Description:  A Maximum Entropy Toolkit<p>
 * Copyright:    Copyright (c) Kristina Toutanova<p>
 * Company:      Stanford University<p>
 * @author Kristina Toutanova
 * @version 1.0
 */


package edu.stanford.nlp.maxent;

import edu.stanford.nlp.io.InDataStreamFile;
import edu.stanford.nlp.io.OutDataStreamFile;
import edu.stanford.nlp.math.ArrayMath;
import edu.stanford.nlp.maxent.iis.LambdaSolve;
import edu.stanford.nlp.util.IntTuple;

import java.io.*;
import java.util.Map;
import java.util.Set;

/**
 * This is a general log-linear classifier
 * It has a set of weights lambda_i for its real values features f: inputs x classes -> R
 * LoglinearClassiifiers can be read from files, or created by LoglinearFactories from data
 * Similarly to the classifiers in the edu.stanford.nlp.classify package, it has methods classOf and scoreOf, but the * arguments are not arrays of doubles but rather a sparse representation (FeaturesMap) of the non-zero values features for a sample
 */
public class LoglinearClassifier {

  int numClasses;
  int numFeatures;
  double[] lambdas;
  double[] priors;
  boolean hasPriors = false; //hasPriors if we keep the values of f0(y) - marginals for all y, this is


  public LoglinearClassifier(int numClasses, int numFeatures, double[] lambdas) {
    this.numClasses = numClasses;
    this.numFeatures = numFeatures;
    this.lambdas = lambdas;
  }


  public LoglinearClassifier() {
  }


  public void setPriors(double[] priors) {
    hasPriors = true;
    this.priors = priors;
  }

  /**
   * save the classifier to a file
   */
  public void save(String filename) {
    try {
      OutDataStreamFile rf = new OutDataStreamFile(filename);
      rf.writeInt(numClasses);
      rf.writeInt(numFeatures);
      LambdaSolve.save_lambdas(rf, lambdas);
      if (hasPriors) {
        rf.writeInt(1);
        LambdaSolve.save_lambdas(rf, priors);
      } else {
        rf.writeInt(0);
      }
      rf.close();
    } catch (Exception e) {
      e.printStackTrace();
    }
  }


  /**
   * read the classifier from a file
   */

  public void read(String filename) {
    try {
      InDataStreamFile rf = new InDataStreamFile(filename);
      numClasses = rf.readInt();
      numFeatures = rf.readInt();
      lambdas = LambdaSolve.read_lambdas(rf);
      for (int j = 0; j < lambdas.length; j++) {
        System.out.println("lambda " + j + " " + lambdas[j]);
        //lambdas[j]=lambdas[j]*1000;
        //if(j<(lambdas.length-1)){lambdas[j]=0;}
        //else{ lambdas[j]=100;}
        //if(lambdas[j]<0){lambdas[j]=0;}else{lambdas[j]=40;}
      }
      /*
      int hPriors=rf.readInt();
      if(hPriors==1){
      priors=LambdaSolve.read_lambdas(rf);
      hasPriors=true;
      }
      */

      rf.close();
    } catch (Exception e) {
      e.printStackTrace();
    }
  }


  /**
   * read the classifier from a lambdas file
   */
  public void readL(String filename) {
    try {
      InDataStreamFile rf = new InDataStreamFile(filename);
      numClasses = 3093;
      //rf.readInt();
      numFeatures = 96540;
      //rf.readInt();
      lambdas = LambdaSolve.read_lambdas(rf);
      for (int j = 0; j < lambdas.length; j++) {
        System.out.println("lambda " + j + " " + lambdas[j]);
        //lambdas[j]=lambdas[j]*1000;
        //if(j<(lambdas.length-1)){lambdas[j]=0;}
        //else{ lambdas[j]=100;}
        //if(lambdas[j]<0){lambdas[j]=0;}else{lambdas[j]=40;}

      }
      /*
      int hPriors=rf.readInt();
      if(hPriors==1){
      priors=LambdaSolve.read_lambdas(rf);
      hasPriors=true;
      }
      */

      rf.close();
    } catch (Exception e) {
      e.printStackTrace();
    }
  }


  public int classOf(FeaturesMap fM) {
    double[] scores = new double[numClasses];

    Set<Map.Entry<IntTuple, Double>> entries = fM.entries();

    if (hasPriors) {
      for (int j = 0; j < scores.length; j++) {
        scores[j] = lambdas[0] * priors[j];
      }
    }

    for (Map.Entry<IntTuple, Double> eN: entries) {
      IntTuple wP = eN.getKey();
      Double val = eN.getValue();
      int y = wP.get(0);
      int fno = wP.get(1);
      scores[y] += val.doubleValue() * lambdas[fno];

    }

    int maxy = 0;
    for (int j = 0; j < numClasses; j++) {
      if (scores[j] > scores[maxy]) {
        maxy = j;
      }

    }

    return maxy;
  }


  /**
   * @return the log-probability of the label
   */
  public double scoreOf(FeaturesMap fM, int label) {

    if (label == -1) {
      label = numClasses - 1;
    }

    double[] scores = new double[numClasses];

    Set<Map.Entry<IntTuple, Double>> entries = fM.entries();

    for (Map.Entry<IntTuple, Double> eN: entries) {
      IntTuple wP = eN.getKey();
      Double val = eN.getValue();
      int y = wP.get(0);
      int fno = wP.get(1);
      scores[y] += val.doubleValue() * lambdas[fno];
    }

    /*
    for(int i=0;i<numClasses;i++){
    System.out.print("score "+i+" "+scores[i]+"\t");
    }
    System.out.println();
    */

    /*
    double total=0;
    for(int j=0;j<numClasses;j++){
    total+=Math.exp(scores[j]);

    }
    //System.out.println("total "+total+" classes "+numClasses);

    total=Math.log(total);
    */

    double total = ArrayMath.logSum(scores);

    for (int j = 0; j < numClasses; j++) {
      scores[j] -= total;
    }
    return scores[label];
  }


  /**
   * in the features file, we have lines that can be read by FeaturesMap
   * in the labels file, just one int per line - the class
   */
  public double accuracy(String filefeatures, String filelabels) {

    int correct = 0, total = 0;
    try {
      BufferedReader in = new BufferedReader(new FileReader(filefeatures));
      BufferedReader labels = new BufferedReader(new FileReader(filelabels));
      String s;
      while ((s = in.readLine()) != null) {
        String lab = labels.readLine();
        int label = Integer.parseInt(lab);
        FeaturesMap fm = new FeaturesMap(s);
        int guess = classOf(fm);
        if (guess == label) {
          correct++;
        }
        total++;
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
    System.out.println("Correct " + correct + " out of " + total);
    return correct / (double) total;
  }


  public void print(PrintStream p) {
    p.println(lambdas.length + " features");
    for (int j = 0; j < lambdas.length; j++) {
      p.println(j + " " + lambdas[j]);
    }
  }


  /**
   * save the scores of the correct expansion
   */
  public void saveProbs(String filefeatures, String filelabels, String filescores) {

    try {

      BufferedReader in = new BufferedReader(new FileReader(filefeatures));
      BufferedReader labels = new BufferedReader(new FileReader(filelabels));
      BufferedWriter scores = new BufferedWriter(new FileWriter(filescores));
      String s;
      while ((s = in.readLine()) != null) {
        String lab = labels.readLine();
        int label = Integer.parseInt(lab);
        FeaturesMap fm = new FeaturesMap(s);
        double score = scoreOf(fm, label);
        scores.write(score + "\n");
      }
      in.close();
      labels.close();
      scores.close();
    } catch (Exception e) {
      e.printStackTrace();
    }
  }


}
