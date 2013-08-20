package edu.stanford.nlp.classify;

import edu.stanford.nlp.stats.MultiClassPrecisionRecallStats;
import edu.stanford.nlp.stats.AccuracyStats;
import edu.stanford.nlp.stats.BasicAccuracyStats;
import edu.stanford.nlp.stats.MultiClassAccuracyStats;
import edu.stanford.nlp.stats.Scorer;
import edu.stanford.nlp.util.Generics;
import edu.stanford.nlp.util.Timing;
import edu.stanford.nlp.util.Index;
import edu.stanford.nlp.util.Pair;
import edu.stanford.nlp.ling.Datum;
import edu.stanford.nlp.ling.BasicDatum;
import edu.stanford.nlp.optimization.GridMinimizer;

import java.util.Collection;
import java.util.ArrayList;
import java.util.Set;
import java.util.HashSet;
import java.util.Random;

public class LogisticRegressionVsSupportVectorMachine {

  private static final int seed = 42;
  private static Random rand = new Random(seed);

  public static <L, F> Pair<Dataset<L, F>, Dataset<L, F>> getRandomSubDatasetOfTrain(Dataset<L, F> train, Dataset<L, F> test, double p) {
    Dataset<L, F> tmpTrain = getRandomSubDataset(train, p);
    Dataset<L, F> tmpTest = test;

    // we have to do this, or else we'll have the old,
    // much bigger feature index and that will mess stuff
    // up later

    Dataset<L, F> newTrain = new Dataset<L, F>();
    for (int i = 0; i < tmpTrain.size(); i++) {
      newTrain.add(tmpTrain.getDatum(i));
    }

    newTrain.featureIndex.lock();

    Dataset<L, F> newTest = new Dataset<L, F>(tmpTest.size(), newTrain.featureIndex, newTrain.labelIndex);
    for (int i = 0; i < tmpTest.size(); i++) {
      newTest.add(tmpTest.getDatum(i));
    }

    newTrain.featureIndex.unlock();

    return new Pair<Dataset<L, F>, Dataset<L, F>>(newTrain, newTest);
  }

  public static <L, F> Dataset<L, F> getRandomSubDataset(Dataset<L, F> dataset, double p) {
    int newSize = (int)(p * dataset.size());
    Set<Integer> indicesToKeep = new HashSet<Integer>();

    int s = dataset.size();
    while (indicesToKeep.size() < newSize) {
      indicesToKeep.add(rand.nextInt(s));
    }

    int[][] newData = new int[newSize][];
    int[] newLabels = new int[newSize];


    int[][] data = dataset.getDataArray();
    int[] labels = dataset.getLabelsArray();

    int i = 0;
    for (int j : indicesToKeep) {
      newData[i] = data[j];
      newLabels[i] = labels[j];
      i++;
    }

    return new Dataset<L, F>(dataset.labelIndex, newLabels, dataset.featureIndex, newData);
  }


  public static <L> Pair<Dataset<L, String>, Dataset<L, String>> addRandomFeatures(Dataset<L, String> train, Dataset<L, String> test, double increaseBy) {
    if (train.featureIndex != test.featureIndex) {
      throw new RuntimeException("Both Datasets must have same feature index!");
    }

    Dataset<L, String> newTrain = new Dataset<L, String>(train.size(), train.featureIndex, train.labelIndex);
    Dataset<L, String> newTest = new Dataset<L, String>(test.size(), test.featureIndex, test.labelIndex);

    int numFeatures = train.numFeatures();
    String[] newFeatures = new String[(int)(numFeatures*increaseBy)];

    for (int i = 0; i < newFeatures.length; i++) {
      String f = "***RANDOM-"+i+"***";
      newFeatures[i] = f;
    }

    for (int i = 0; i < train.size(); i++) {
      Datum<L, String> d = train.getDatum(i);
      Collection<String> features = d.asFeatures();
      Collection<String> newDatumFeatures = new ArrayList<String>(features);
      double prob = ((double)features.size()/(double)newFeatures.length)*increaseBy;
      for (int j = 0; j < newFeatures.length; j++) {
        if (rand.nextDouble() < prob) {
          newDatumFeatures.add(newFeatures[j]);
        }
      }

      //System.out.println("train: "+features.size()+"\t"+newDatumFeatures.size());

      newTrain.add(new BasicDatum<L, String>(newDatumFeatures, d.label()));
    }

    newTrain.featureIndex.lock();

    for (int i = 0; i < test.size(); i++) {
      Datum<L, String> d = test.getDatum(i);
      Collection<String> features = d.asFeatures();
      Collection<String> newDatumFeatures = new ArrayList<String>(features);
      double prob = ((double)features.size()/(double)newFeatures.length)*increaseBy;
      for (int j = 0; j < newFeatures.length; j++) {
        if (rand.nextDouble() < prob) {
          newDatumFeatures.add(newFeatures[j]);
        }
      }
      newTest.add(new BasicDatum<L, String>(newDatumFeatures, d.label()));

      //System.out.println("test: "+features.size()+"\t"+newDatumFeatures.size());

    }

    newTrain.featureIndex.unlock();

    return Generics.newPair(newTrain, newTest);
  }

  public static <L> Pair<Dataset<L, String>, Dataset<L, String>> addRedundantFeatures(Dataset<L, String> train, Dataset<L, String> test, double probRedundant) {
    if (train.featureIndex != test.featureIndex) {
      throw new RuntimeException("Both Datasets must have same feature index!");
    }

    Index<String> featureIndex = train.featureIndex;
    Dataset<L, String> newTrain = new Dataset<L, String>(train.size(), featureIndex, train.labelIndex);
    Dataset<L, String> newTest = new Dataset<L, String>(test.size(), featureIndex, test.labelIndex);

    int numFeatures = featureIndex.size();
    int[] redundant = new int[numFeatures];

    for (int i = 0; i < redundant.length; i++) {
      if (rand.nextDouble() < probRedundant) {
        String f = "***REDUNDANT-"+i+"***";
        featureIndex.add(f);
        redundant[i] = featureIndex.indexOf(f);
      } else {
        redundant[i] = -1;
      }
    }

    for (int i = 0; i < train.size(); i++) {
      Datum<L, String> d = train.getDatum(i);
      Collection<String> features = d.asFeatures();
      Collection<String> newFeatures = new ArrayList<String>(features);
      for (String f : features) {
        int index = featureIndex.indexOf(f);
        if (redundant[index] >= 0) {
          newFeatures.add(featureIndex.get(redundant[index]));
        }
      }
      newTrain.add(new BasicDatum<L, String>(newFeatures, d.label()));
    }

    for (int i = 0; i < test.size(); i++) {
      Datum<L, String> d = test.getDatum(i);
      Collection<String> features = d.asFeatures();
      Collection<String> newFeatures = new ArrayList<String>(features);
      for (String f : features) {
        int index = featureIndex.indexOf(f);
        if (redundant[index] >= 0) {
          newFeatures.add(featureIndex.get(redundant[index]));
        }
      }
      newTest.add(new BasicDatum<L, String>(newFeatures, d.label()));
    }


    return Generics.newPair(newTrain, newTest);
  }

  public static void main(String[] args) {

    System.out.println("reading train set");
    Dataset<String, String> train = Dataset.readSVMLightFormat(args[0]);
    train.featureIndex.lock();
    System.out.println("reading test set");
    Dataset<String, String> test = Dataset.readSVMLightFormat(args[1], train.featureIndex(), train.labelIndex());
    train.featureIndex.unlock();
    System.out.println("done reading data");

    if (args[2].equalsIgnoreCase("-usePR")) {
      scorer = new MultiClassPrecisionRecallStats<String>(args[3]);
      basicScorer = scorer;
    } else if (args[2].equalsIgnoreCase("-useAcc")) {
      scorer = new AccuracyStats<String>(args[3], args[4]);
      basicScorer = new BasicAccuracyStats<String>();
    } else if (args[2].equalsIgnoreCase("-useMCAcc")) {
      scorer = new MultiClassAccuracyStats<String>(args[3]);
      basicScorer = new BasicAccuracyStats<String>();
    } else {
      throw new RuntimeException("You must specify a scorer for cross validation!\nOptions: -usePR -useAcc -useMCAcc");
    }

    System.out.println("Current Test: Full training set, optimize params");

    System.out.println(train.toSummaryStatistics());

    svmTrainTestCV(train, test);
    lrTrainTestCV(train, test);
    // get other stats

    double[] trainSizes = {0.1, 0.2, 0.33, 0.5, 0.75};
    // try different sizes of training data
    for (double size : trainSizes) {
      System.out.println("Current Test: "+size+" of training set, optimize params");

      Pair<Dataset<String, String>, Dataset<String, String>> randomSubDatasets = getRandomSubDatasetOfTrain(train, test, size);
      Dataset<String, String> reducedTrain = randomSubDatasets.first();
      Dataset<String, String> reducedTest = randomSubDatasets.second(); // not reduced in size, but shrunk feature index

      System.out.println(reducedTrain.toSummaryStatistics());

      lrTrainTest(reducedTrain, reducedTest);
      svmTrainTest(reducedTrain, reducedTest);

    }

    System.out.println("Current Test: Full of training set, optimize params, randomly added features");

    Pair<Dataset<String, String>, Dataset<String, String>> addRandomFeaturesDatasets = addRandomFeatures(train, test, 1.0);
    Dataset<String, String> randomTrain = addRandomFeaturesDatasets.first();
    Dataset<String, String> randomTest = addRandomFeaturesDatasets.second();

    System.out.println(randomTrain.toSummaryStatistics());

    lrTrainTest(randomTrain, randomTest);
    svmTrainTest(randomTrain, randomTest);

    System.out.println("Current Test: Full of training set, optimize params, randomly added features");

    Pair<Dataset<String, String>, Dataset<String, String>> addRedundantFeaturesDatasets = addRedundantFeatures(train, test, 0.5);
    Dataset<String, String> redundantTrain = addRedundantFeaturesDatasets.first();
    Dataset<String, String> redundantTest = addRedundantFeaturesDatasets.second();

    System.out.println(redundantTrain.toSummaryStatistics());

    lrTrainTest(redundantTrain, redundantTest);
    svmTrainTest(redundantTrain, redundantTest);

  }

  static Scorer<String> scorer = null;
  static Scorer<String> basicScorer = null;

  static Timing timer = new Timing();

  static double sigma = 1.0;
  static double C = 3.1622776601683795;

  public static <F> void lrTrainTest(Dataset<String, F> train, Dataset<String, F> test) {
    System.out.println("*** LR ***");
    System.out.println("training...");
    LinearClassifierFactory<String, F> lrFact = new LinearClassifierFactory<String, F>();
    lrFact.setSigma(sigma);
    LinearClassifier<String, F> lr = null;
    timer.restart();
    lr = lrFact.trainClassifier(train);
    timer.stop("", System.out);
    test(lr, test);
  }

  public static <F> void lrTrainTestCV(Dataset<String, F> train, Dataset<String, F> test) {
    System.out.println("*** LR ***");
    System.out.println("training...");
    LinearClassifierFactory<String, F> lrFact = new LinearClassifierFactory<String, F>();

    LinearClassifier<String, F> lr = null;
    timer.restart();
    //lrFact.crossValidateSetSigma(train, 5, basicScorer);
    lrFact.heldOutSetSigma(train, basicScorer);
    lr = lrFact.trainClassifier(train);

    timer.stop("", System.out);
    sigma = lrFact.getSigma();
    test(lr, test);
  }

  public static <F> void svmTrainTest(Dataset<String, F> train, Dataset<String, F> test) {
    System.out.println("*** SVM ***");
    System.out.println("C = "+C);
    System.out.println("training...");
    SVMLightClassifierFactory<String, F> fact = new SVMLightClassifierFactory<String, F>();
    if (C > 0.0) {
      fact.setC(C);
    }
    timer.restart();
    SVMLightClassifier<String, F> svm = fact.trainClassifier(train);
    timer.stop("", System.out);
    test(svm, test);
  }

  public static <F> void svmTrainTestCV(Dataset<String, F> train, Dataset<String, F> test) {

    double[] cToTry = new double[5];
    for (int k=-2; k <= 1; k++) {
      cToTry[k+3] = Math.pow(10,(k)/2.0);
    }

    double defaultC = 0.0;
    for (int[] d : train.getDataArray()) {
      defaultC += d.length * d.length;
    }
    defaultC = Math.sqrt(1 / (defaultC / train.size()));
    cToTry[0] = defaultC;
    cToTry = new double[]{defaultC, 0.5, 1.0, 5.0, 10.0};
    GridMinimizer gridMinimizer = new GridMinimizer(cToTry);

    System.out.println("*** SVM ***");
    System.out.println("training...");
    SVMLightClassifierFactory<String, F> fact = new SVMLightClassifierFactory<String, F>();
    timer.restart();
    //fact.crossValidateSetC(train, 5, basicScorer, gridMinimizer);
    fact.heldOutSetC(train, 0.3, basicScorer, gridMinimizer);
    SVMLightClassifier<String, F> svm = fact.trainClassifier(train);
    C = fact.getC();
    System.out.println("Picked C: "+C);
    test(svm, test);
  }

  public static <F> void test(ProbabilisticClassifier<String, F> classifier, Dataset<String, F> test) {

    scorer.score(classifier, test);
    System.out.println(scorer.getDescription(4));

  }

}

