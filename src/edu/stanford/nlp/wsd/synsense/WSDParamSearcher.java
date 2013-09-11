package edu.stanford.nlp.wsd.synsense;

import edu.stanford.nlp.optimization.Function;
import edu.stanford.nlp.optimization.Minimizer;
import edu.stanford.nlp.optimization.RandomFunction;
import edu.stanford.nlp.optimization.RandomGreedyLocalSearch;
import edu.stanford.nlp.util.StringUtils;

import java.io.*;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Class WSDParamSearcher
 *
 * @author Teg Grenager
 */
public class WSDParamSearcher {

  protected static final NumberFormat formatter = new DecimalFormat("0.000");


  private WSDParamSearcher() {
  }


  public static List<Instance> readInData(String senseFilename) throws Exception {
    System.out.println("Reading in data.");
    List<Instance> trainInstances = new ArrayList<Instance>();
    String line;
    System.out.println("Reading and processing sense instances.");
    BufferedReader senseTrainFile = new BufferedReader(new FileReader(senseFilename));
    int numTotal = 0;
    while ((line = senseTrainFile.readLine()) != null) {
      try {
        trainInstances.add(new Instance(line, null)); // no parsing
        numTotal++;
      } catch (Exception e) {
        // do nothing
      }
    }
    System.out.println("Got " + numTotal + " train instances from " + senseFilename);
    Collections.shuffle(trainInstances);
    return trainInstances;
  }

  public static String[] getAllFileNames() {
    File currentDir = new File(".");
    String[] filenames = currentDir.list();
    List<String> result = new ArrayList<String>();
    for (String filename : filenames) {
      if (filename.endsWith(".train")) {
        result.add(filename);
      }
    }
    return result.toArray(new String[result.size()]);
  }


  static class BasicSenseParamsFunction implements Function {
    int word;
    int dim;
    double subcatEvidenceMult;
    double bestResult = Double.NEGATIVE_INFINITY;

    public BasicSenseParamsFunction(int word, double subcatEvidenceMult) {
      this.word = word;
      this.subcatEvidenceMult = subcatEvidenceMult;
      dim = 7;
    }

    public double valueAt(double[] x) {
      // we are minimizing negative accuracy
      double[] modBuckWeights = {x[0], x[1], x[2], x[3], x[4], x[5]};
      Model model = new BasicModel(modBuckWeights, x[6], subcatEvidenceMult);
      //      SynSense.shuffleDatasets();
      int[] numCorrect = SynSense.kFoldTrainAndTestOneWord(model, word);
      double[] accuracies = {(double) numCorrect[0] / SynSense.senseTrainData[word].size(), (double) numCorrect[1] / SynSense.subcatTrainData[word].size()};
      double result = numCorrect[0];
      if (result > bestResult) {
        bestResult = result;
      }

      // print out the point and the value
      System.out.print("new weights: ");
      for (int i = 0; i < x.length; i++) {
        System.out.print(formatter.format(x[i]) + "\t");
      }
      System.out.println();
      System.out.println("numCorrect:" + numCorrect[0]);
      System.out.println("accuracy:  " + formatter.format(accuracies[0]));

      return -result;
    }

    public int domainDimension() {
      return dim;
    }

  }

  static class BasicRandomFunction extends BasicSenseParamsFunction implements RandomFunction {
    public BasicRandomFunction(int word, double subcatEvidenceMult) {
      super(word, subcatEvidenceMult);
    }

    public void randomize() {
      SynSense.shuffleDatasets();
    }
  }

  static class BasicSubcatParamsFunction implements Function {
    int word;
    int dim;
    double[] modBuckWeights;
    double senseEvidenceMult;
    double bestResult = Double.NEGATIVE_INFINITY;

    public BasicSubcatParamsFunction(int word, double[] modBuckWeights, double seMult) {
      this.word = word;
      this.modBuckWeights = modBuckWeights;
      this.senseEvidenceMult = seMult;
      dim = 1;
    }

    public double valueAt(double[] x) {
      // we are minimizing negative accuracy
      Model model = new BasicModel(modBuckWeights, senseEvidenceMult, x[0]);
      //      int numCorrect[] = SynSense.kFoldTrainAndTestOneWord(model, word);
      int[] numCorrect = SynSense.kFoldTrainAndTestOneWord(model, word);
      double[] accuracies = {(double) numCorrect[0] / SynSense.senseTrainData[word].size(), (double) numCorrect[1] / SynSense.subcatTrainData[word].size()};
      double result = numCorrect[1];
      if (result > bestResult) {
        bestResult = result;
      }

      // print out the point and the value
      System.out.print("new weights: ");
      for (int i = 0; i < x.length; i++) {
        System.out.print(formatter.format(x[i]) + "\t");
      }
      System.out.println();
      System.out.println("numCorrect: " + numCorrect[1]);
      System.out.println("accuracy:  " + formatter.format(accuracies[1]));

      return -result;
    }

    public int domainDimension() {
      return dim;
    }

  }

  static class JointRandomFunction extends BasicSubcatParamsFunction implements RandomFunction {
    public JointRandomFunction(int word, double[] modBuckWeights, double seMult) {
      super(word, modBuckWeights, seMult);
    }

    public void randomize() {
      SynSense.shuffleDatasets();
    }
  }

  static class JointParamsFunction implements Function {
    int word;
    int dim;
    double senseBaseline, subcatBaseline;
    double[] modBuckWeights;
    double senseEvidenceMult, subcatEvidenceMult;
    int[] bestNumCorrect;
    double bestResult = Double.NEGATIVE_INFINITY;

    public JointParamsFunction(int word, double senseBaseline, double subcatBaseline, double[] modBuckWeights, double senseEvidenceMult, double subcatEvidenceMult) {
      this.word = word;
      this.senseBaseline = senseBaseline;
      this.subcatBaseline = subcatBaseline;
      this.modBuckWeights = modBuckWeights;
      this.senseEvidenceMult = senseEvidenceMult;
      this.subcatEvidenceMult = subcatEvidenceMult;
      dim = 2;
    }

    public double valueAt(double[] x) {
      // we are minimizing negative accuracy
      Model model = new JointModel(modBuckWeights, senseEvidenceMult, subcatEvidenceMult, x[0], x[1], 0.5);
      int[] numCorrect = SynSense.kFoldTrainAndTestOneWord(model, word);
      //      int numCorrect[] = SynSense.kFoldTrainAndTestOneWord(model, word);
      double[] accuracies = {(double) numCorrect[0] / SynSense.senseTrainData[word].size(), (double) numCorrect[1] / SynSense.subcatTrainData[word].size()};
      // sum of error rate reductions
      double senseErrReduc;
      if (senseBaseline == 1) {
        if (accuracies[0] < 1) {
          senseErrReduc = (accuracies[0] - 1);
        } else {
          senseErrReduc = 0;
        }
      } else {
        senseErrReduc = (accuracies[0] - senseBaseline) / (1 - senseBaseline);
      }

      double subcatErrReduc;
      if (subcatBaseline == 1) {
        if (accuracies[1] < 1) {
          subcatErrReduc = (accuracies[1] - 1);
        } else {
          subcatErrReduc = 0;
        }
      } else {
        subcatErrReduc = (accuracies[1] - subcatBaseline) / (1 - subcatBaseline);
      }

      double result = senseErrReduc + subcatErrReduc;

      if (result > bestResult) {
        bestResult = result;
        bestNumCorrect = numCorrect.clone();
      }

      // print out the point and the value
      System.out.print("new weights: ");
      for (int i = 0; i < x.length; i++) {
        System.out.print(formatter.format(x[i]) + "\t");
      }
      System.out.println();
      System.out.println("numCorrect: " + numCorrect[0] + "\t" + numCorrect[1]);
      System.out.println("accuracies: " + formatter.format(accuracies[0]) + '\t' + formatter.format(accuracies[1]));
      System.out.println("score " + formatter.format(result));

      return -result;
    }

    public int domainDimension() {
      return dim;
    }

  }

  static class BigJointRandomFunction extends JointParamsFunction implements RandomFunction {
    public BigJointRandomFunction(int word, double senseBaseline, double subcatBaseline, double[] modBuckWeights, double senseEvidenceMult, double subcatEvidenceMult) {
      super(word, senseBaseline, subcatBaseline, modBuckWeights, senseEvidenceMult, subcatEvidenceMult);
    }

    public void randomize() {
      SynSense.shuffleDatasets();
    }
  }

  public static void main(String[] args) throws Exception {
    Map argMap = StringUtils.parseCommandLineArguments(args);

    SynSense.readInData(argMap);
    SynSense.shuffleDatasets();
    List<double[]> allParamsByWord = new ArrayList<double[]>();
    List<double[]> allKfoldAccuraciesByWord = new ArrayList<double[]>();
    List<double[]> allTestAccuraciesByWord = new ArrayList<double[]>();

    BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream("everything.csv"));
    PrintWriter pw = new PrintWriter(bos);
    pw.print("word,buck1,buck2,buck3,buck4,buck5,buck6,senseEvidenceMult,subcatEvidenceMult,senseGivenSubcatMult,");
    pw.print("subcatGivenSenseMult,BASkfSense,BASkfSubcat,BAStSense,BAStSubcat,"); //,jointSmoothingParam
    pw.print("JOINTkfSense,JOINTkfSubcat,JOINTtSense,JOINTtSubcat,numSenseTrain,numSubcatTrain,");
    pw.println("numSenseTest,numSubcatTest");

    for (int word = 0; word < SynSense.words.size(); word++) {
      double[] initialWeights;
      double[] allParams = new double[11];

      System.out.println("\n\n\n\n\n\n\n\n");
      System.out.println("NEW WORD: " + SynSense.words.get(word));
      pw.print(SynSense.words.get(word));

      double[] bucketWeights = {2.4, 8.0, 28.0, 11.1, 12.0, 1.5}; //buckets
      double senseEvidenceMult = 1.666;
      double subcatEvidenceMult = 0.75;
      double senseGivenSubcatMult = 1.0;
      double subcatGivenSenseMult = 1.0;

      // compute baseline
      int[] baseline = SynSense.kFoldTrainAndTestOneWord(new BasicModel(), word);
      int bestSenseResult = baseline[0];
      int bestSubcatResult = baseline[1];

      //  train sense weights only
      /*
      System.out.println("Training basic model sense params for word " + SynSense.words.get(word));
      initialWeights = new double[] {bucketWeights[0], //buckets
                                     bucketWeights[1],
                                     bucketWeights[2],
                                     bucketWeights[3],
                                     bucketWeights[4],
                                     bucketWeights[5],
                                     senseEvidenceMult}; //senseEvidence
      BasicSenseParamsFunction senseFunction= new BasicSenseParamsFunction(word, subcatEvidenceMult);
      Minimizer minimizer = new RandomGreedyLocalSearch(0.5, 6);
      double[] ans = minimizer.minimize(senseFunction, 0.01, initialWeights);
      bucketWeights = new double[] {ans[0], ans[1], ans[2], ans[3], ans[4], ans[5]};
      senseEvidenceMult = ans[6];
      System.out.println(formatter.format(senseEvidenceMult));
      System.out.println("Giving k-fold accuracy of ");
      bestSenseResult = senseFunction.bestResult;
      double basicSenseAcc = (double)bestSenseResult/ SynSense.senseTrainData[word].size();
      System.out.println(formatter.format(basicSenseAcc));
      for (int i=0; i<7; i++) allParams[i] = ans[i];
      */
      for (int i = 0; i < bucketWeights.length; i++) {
        pw.print("," + bucketWeights[i]);
      }
      pw.print("," + senseEvidenceMult);

      // train subcat evidence mult only
      /*
      System.out.println("Training basic model subcat params for word " + SynSense.words.get(word));
      initialWeights = new double[] {0.75}; //subcatEvidence
      BasicSubcatParamsFunction jointFunction= new BasicSubcatParamsFunction(word, bucketWeights, senseEvidenceMult);
      Minimizer minimizer2 = new RandomGreedyLocalSearch(0.5, 6);
      double[] ans2 = minimizer2.minimize(jointFunction, 0.01, initialWeights);
      subcatEvidenceMult = ans2[0];
      System.out.println("Found basic subcat param: ");
      System.out.println(formatter.format(subcatEvidenceMult));
      System.out.println("Giving k-fold accuracy of ");
      bestSubcatResult = jointFunction.bestResult;
      basicSubcatAcc = (double) bestSubcatResult / SynSense.subcatTrainData[word].size();
      System.out.println(formatter.format(basicSubcatAcc));
      allParams[7] = ans2[0];
      */
      pw.print("," + subcatEvidenceMult);

      // train joint model params
      System.out.println("Training joint model params for word " + SynSense.words.get(word));
      initialWeights = new double[]{senseGivenSubcatMult, subcatGivenSenseMult};
      JointParamsFunction bigJointFunction = new JointParamsFunction(word, (double) bestSenseResult / SynSense.senseTrainData[word].size(), (double) bestSubcatResult / SynSense.subcatTrainData[word].size(), bucketWeights, senseEvidenceMult, subcatEvidenceMult);
      Minimizer<JointParamsFunction> minimizer3 = new RandomGreedyLocalSearch(0.5, 6);
      double[] ans3 = minimizer3.minimize(bigJointFunction, 0.01, initialWeights);
      senseGivenSubcatMult = ans3[0];
      subcatGivenSenseMult = ans3[1];
      //      double jointSmoothingParam = ans[2];
      System.out.println("Found joint params: ");
      System.out.println(formatter.format(senseGivenSubcatMult) + '\t' + formatter.format(subcatGivenSenseMult));
      //              + '\t' + formatter.format(jointSmoothingParam));
      System.out.println("Giving k-fold accuracies of ");
      double jointSenseAcc = (double) bigJointFunction.bestNumCorrect[0] / SynSense.senseTrainData[word].size();
      double jointSubcatAcc = (double) bigJointFunction.bestNumCorrect[1] / SynSense.subcatTrainData[word].size();
      System.out.println(formatter.format(jointSenseAcc) + '\t' + formatter.format(jointSubcatAcc));
      allParams[8] = senseGivenSubcatMult;
      pw.print("," + senseGivenSubcatMult);
      allParams[9] = subcatGivenSenseMult;
      pw.print("," + subcatGivenSenseMult);
      allParamsByWord.add(word, allParams);

      //      for (int i=1; i<10; i++) {
      //        JointModel model = new JointModel(modBuckWeights, senseEvidenceMult, subcatEvidenceMult, senseGivenSubcatMult, subcatGivenSenseMult, 0.5);
      //        model.EMsteps = i;
      //        System.out.println("Training model with " + i + " steps of EM.");
      //        int numCorrect[] = SynSense.kFoldTrainAndTestOneWord(model, word);
      //        System.out.println("numCorrect: " + numCorrect[0] + "    " + numCorrect[1]);
      //      }

      BasicModel basicModel = new BasicModel(bucketWeights, senseEvidenceMult, subcatEvidenceMult);
      int[] testBasicNum = SynSense.trainAndTestOneWord(basicModel, word);
      pw.print("," + bestSenseResult + "," + bestSubcatResult);
      pw.print("," + testBasicNum[0] + "," + testBasicNum[1]);

      JointModel subcatModel = new JointModel(bucketWeights, senseEvidenceMult, subcatEvidenceMult, senseGivenSubcatMult, subcatGivenSenseMult, 0.5);
      int[] testSubcatNum = SynSense.trainAndTestOneWord(subcatModel, word);
      pw.print("," + bigJointFunction.bestNumCorrect[0] + "," + bigJointFunction.bestNumCorrect[1]);
      pw.print("," + testSubcatNum[0] + "," + testSubcatNum[1]);

      pw.println("," + SynSense.senseTrainData[word].size() + "," + SynSense.subcatTrainData[word].size() + "," + SynSense.senseTestData[word].size() + "," + SynSense.subcatTestData[word].size());

      double[] allTestAcc = {testBasicNum[0], testBasicNum[1], testSubcatNum[0], testSubcatNum[1]};
      allTestAccuraciesByWord.add(word, allTestAcc);

      double[] allKfoldAccuracies = {bestSenseResult, bestSubcatResult, bigJointFunction.bestNumCorrect[0], bigJointFunction.bestNumCorrect[1]};
      allKfoldAccuraciesByWord.add(word, allKfoldAccuracies);
    }

    pw.close();
    //
    //    System.out.println("\n\nFINAL RESULTS!!!\n\n");
    //    double senseTestAcc = (double)SynSense.totalSenseCorrect / SynSense.totalSense;
    //    double subcatTestAcc = (double)SynSense.totalSubcatCorrect / SynSense.totalSubcat;
    //    System.out.println("Overall testset accuracies:" + formatter.format(senseTestAcc) +
    //            "      " + formatter.format(subcatTestAcc) + "\n\n\n");
    //    System.out.println("WORD    k-fold BASIC\tk-fold SUBCAT\tTest BASIC\tTest SUBCAT");
    //    for (int word=0; word<SynSense.words.size(); word++) {
    //      double[] kfoldAcc = (double[])allKfoldAccuraciesByWord.get(word);
    //      double[] testAcc = (double[])allTestAccuraciesByWord.get(word);
    //      System.out.print(SynSense.words.get(word) + "      ");
    //      for (int i=0; i<4; i++)
    //        System.out.print(formatter.format(kfoldAcc[i]) + "      ");
    //      for (int i=0; i<4; i++)
    //        System.out.print(formatter.format(testAcc[i] + "      "));
    //      System.out.println("\n\n\n");
    //    }

  }

}

