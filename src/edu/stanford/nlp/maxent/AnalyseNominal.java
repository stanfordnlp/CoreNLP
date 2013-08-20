/**
 * Title:        StanfordMaxEnt<p>
 * Description:  A Maximum Entropy Toolkit<p>
 * Copyright:    Copyright (c) Kristina Toutanova<p>
 * Company:      Stanford University<p>
 * @author Kristina Toutanova
 * @version 1.0
 */


package edu.stanford.nlp.maxent;


import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;


/**
 * This class was created to make it easy to train MaxEnt models from files
 * in the format that Weka uses.
 * See {@link ReadDataWeka} class for a brief description of the file format.
 * The class can be used to classify instances specified in the same format
 * also.
 */
public class AnalyseNominal {
  ReadDataWeka train;
  double[] accuracies;
  double[] probs; // these are the probabilities of the senses
  double[][][][][] measures; // for each feature the true positive and true negative it predicts
  // fNo val class tp tn { a rather big array }
  // should do that on the features themselves

  public AnalyseNominal() {
  }

  public AnalyseNominal(String wekaProbFile) throws Exception {
    readTrainingInstances(wekaProbFile);
    train.makeStringsClasses();
    probs = new double[train.numClasses];
    accuracies = new double[(train.getNumAttributes())];
    for (int i = 0; i < train.numSamples(); i++) {
      double[] a = train.getData(i).x;
      int y = train.getClass(i);
      probs[y]++;
      String cls = train.getYName(y);
      for (int j = 0; j < a.length; j++) {
        if (train.getAttrName(j, (int) a[j]).equals(cls)) {
          accuracies[j]++;
        }
      }

    }// for

    for (int j = 0; j < accuracies.length; j++) {
      accuracies[j] = accuracies[j] / train.numSamples();
      System.out.println(j + " " + accuracies[j]);

    }

    for (int j = 0; j < probs.length; j++) {
      probs[j] = probs[j] / train.numSamples();
      System.out.println(" The probability of " + j + " is " + probs[j]);
    }


  }


  public void readTrainingInstances(String wekaDataFile) throws Exception {
    train = new ReadDataWeka(wekaDataFile);
  }

  void split(String trainFileName, String fileOne, String fileTwo) throws Exception {

    BufferedReader in = new BufferedReader(new FileReader(trainFileName));
    BufferedWriter out1 = new BufferedWriter(new FileWriter(fileOne));
    BufferedWriter out2 = new BufferedWriter(new FileWriter(fileTwo));

    String line;


    while (true) {
      line = (in.readLine() + "\n");
      out1.write(line);
      out2.write(line);
      if (line.startsWith("@data")) {
        break;
      }
    }

    //separate 9 to 1
    while ((line = in.readLine()) != null) {
      double ran = Math.random();
      if (ran < .9) {
        out1.write(line + "\n");
      } else {
        out2.write(line + "\n");
      }
    }//while
    in.close();
    out1.close();
    out2.close();

  }


  public static void main(String[] args) {


    try {
      new AnalyseNominal(args[0]);
    } catch (Exception e) {
      e.printStackTrace();
    }

  }


}

