package edu.stanford.nlp.classify;

import java.io.BufferedWriter;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.File;
import java.util.StringTokenizer;

import edu.stanford.nlp.io.RuntimeIOException;
import edu.stanford.nlp.ling.RVFDatum;
import edu.stanford.nlp.optimization.QNMinimizer;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.stats.Counters;
import edu.stanford.nlp.objectbank.ObjectBank;
import edu.stanford.nlp.util.ErasureUtils;
import edu.stanford.nlp.util.Interner;

/**
 * This class can create a log-linear model by reading data from a file.
 * Meant for the RTE confidences fitting where we have scores from different models
 * and it can dumps scores for data in files.
 *
 * @author Kristina Toutanova
 * @version Feb 9, 2005
 */
public class LogisticTrainTest {

  private final Interner<Integer> intern = new Interner<Integer>();
  private static final String tab = "\t";
  private static final String nl = "\n";

  /**
   * Making datums from a line.
   * The first element is the class 0 or 1 and the following elements are assumed doubles.
   *
   */
  RVFDatum<String, Integer> makeDatum(String line) {
    StringTokenizer st = new StringTokenizer(line);
    String label = st.nextToken();
    int index = 1;
    ClassicCounter<Integer> features = new ClassicCounter<Integer>();
    features.incrementCount(intern.intern(Integer.valueOf(0)));
    while (st.hasMoreTokens()) {
      double value = Double.parseDouble(st.nextToken());
      features.incrementCount(intern.intern(Integer.valueOf(index)), value);
      index++;
    }
    return new RVFDatum<String, Integer>(features, label);
  }

  RVFDataset<String, Integer> makeDataset(String trainFile) {
    RVFDataset<String, Integer> dataSet = new RVFDataset<String, Integer>();
    for(String line : ObjectBank.getLineIterator(new File(trainFile))) {
      dataSet.add(makeDatum(line));
    }
    return dataSet;
  }


  void testClassifier(LinearClassifier<String, Integer> lC, String testFile, String testOutputFile) {
    try {
      BufferedWriter out = new BufferedWriter(new FileWriter(testOutputFile));
      int correct = 0;
      int total = 0;
      for (String line : ObjectBank.getLineIterator(new File(testFile))) {
        RVFDatum<String, Integer> datum = makeDatum(line);
        Counter<String> scores = lC.probabilityOf(datum);
        Object guess = Counters.argmax(scores);
        Object label = datum.label();
        if (label.equals(guess)) {
          correct++;
        }
        total++;
        StringBuilder sb = new StringBuilder();
        String[] arr = ErasureUtils.uncheckedCast(scores.keySet().toArray());
        for (String anArr : arr) {
          sb.append(anArr);
          sb.append(tab);
          sb.append(scores.getCount(anArr));
          sb.append(tab);
        }
        sb.append(nl);
        out.write(sb.toString());
      }
      out.close();
      System.err.println("correct " + correct + " out of " + total + " accuracy " + correct / (double) total);
    } catch (IOException e) {
      throw new RuntimeIOException(e);
    }
  }

  static LinearClassifier<String, Integer> readClassifier(String filename) {
    try {
      ObjectInputStream out = new ObjectInputStream(new FileInputStream(filename));
      LinearClassifier<String, Integer> lC = ErasureUtils.uncheckedCast(out.readObject());
      out.close();
      return lC;
    } catch (IOException e) {
      throw new RuntimeIOException(e);
    } catch (ClassNotFoundException e) {
      throw new RuntimeException(e);
    }
  }

  static void saveClassifier(LinearClassifier<String, Integer> lC, String filename) {
    try {
      ObjectOutputStream out = new ObjectOutputStream(new FileOutputStream(filename));
      out.writeObject(lC);
      out.close();
    } catch (IOException e) {
      throw new RuntimeIOException(e);
    }
  }

  public static LinearClassifier<String, Integer> trainClassifier(RVFDataset<String, Integer> dataset) {
    return trainClassifier(dataset, 1.0);
  }


  public static LinearClassifier<String, Integer> trainClassifierSettingSigmaUsingFolds(RVFDataset<String, Integer> dataset, int numFolds) {
    LinearClassifierFactory<String, Integer> fact = new LinearClassifierFactory<String, Integer>(new QNMinimizer(), 1e-4, false, 1.0);
    fact.setTuneSigmaCV(numFolds);
    return fact.trainClassifier(dataset);
  }


  public static LinearClassifier<String, Integer> trainClassifier(RVFDataset<String, Integer> dataset, double sigma) {
    LinearClassifierFactory<String, Integer> fact = new LinearClassifierFactory<String, Integer>(new QNMinimizer(), 1e-4, false, sigma);
    return fact.trainClassifier(dataset);
  }

  /**
   * The main method for training and testing
   *
   */
  public static void main(String[] args) {
    int start = 0;
    double sigma = Double.NaN;
    String modelFile = null;
    String testFile = null;
    String testOutputFile = null;
    String trainFile = null;
    boolean trainModel = false;
    int folds = 4;


    while (start < args.length) {
      if (args[start].equals("-train")) {
        trainFile = args[++start];
        start++;
        trainModel = true;
        modelFile = trainFile + ".model";
      } else if (args[start].equals("-model")) {
        modelFile = args[++start];
        start++;
      } else if (args[start].equals("-folds")) {
        folds = Integer.parseInt(args[++start]);
        start++;
      } else if (args[start].equals("-test")) {
        testFile = args[++start];
        start++;
        testOutputFile = testFile + ".out";
      } else if (args[start].equals("-sigma")) {
        sigma = Double.parseDouble(args[++start]);
        start++;
      }
    }
    LogisticTrainTest lTT = new LogisticTrainTest();
    LinearClassifier<String, Integer> lC; // initialized below
    if (trainModel) {
      if (sigma > 0) {
        lC = LogisticTrainTest.trainClassifier(lTT.makeDataset(trainFile), sigma);
      } else {
        lC = LogisticTrainTest.trainClassifierSettingSigmaUsingFolds(lTT.makeDataset(trainFile), folds);
      }
      System.err.println("learned classifier " + lC.toAllWeightsString());
      LogisticTrainTest.saveClassifier(lC, modelFile);
    } else {
      lC = LogisticTrainTest.readClassifier(modelFile);
      System.err.println("read classifier " + lC.toAllWeightsString());
    }
    if (testFile != null) {
      lTT.testClassifier(lC, testFile, testOutputFile);
    }
  }
}
