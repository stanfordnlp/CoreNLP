package edu.stanford.nlp.classify;

import edu.stanford.nlp.ling.BasicDatum;
import edu.stanford.nlp.ling.Datum;
import edu.stanford.nlp.ling.RVFDatum;
import edu.stanford.nlp.maxent.*;
import edu.stanford.nlp.optimization.QNMinimizer;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.stats.TwoDimensionalCounter;
import edu.stanford.nlp.util.Index;
import edu.stanford.nlp.util.Pair;
import edu.stanford.nlp.util.HashIndex;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.*;

/** A class that trains and tests several type 1 and type 2 classifiers.
 *
 * @author Kristina Toutanova
 * @version Feb 6, 2005
 */
public class ClassifierTaggingExamples {

  private ArrayList<FeatureExtractor<String, String, String>> extractors;
  private double weightW = 1.0;
  private double weightP = 0.5;
  private double weightS = 0.5;
  private int maxPrefSuf = 5;
  private HashSet<String> possibleLabels = new HashSet<String>();
  private Index<String> labelIndex = new HashIndex<String>();
  private static final String noun = "NOUN";
  private static final String verb = "VERB";
  private HashSet<Integer> nounLabelIndices = new HashSet<Integer>();
  private ArrayList<String> integers = new ArrayList<String>();

  public ClassifierTaggingExamples(double wW, double wP, double wS, int maxPrefSuf) {
    weightW = wW;
    weightP = wP;
    weightS = wS;
    this.maxPrefSuf = maxPrefSuf;
    init();
  }

  private void init() {
    //making the extractors
    extractors = new ArrayList<FeatureExtractor<String, String, String>>();
    extractors.add(new WordExtractor<String>(weightW));
    for (int i = 1; i <= maxPrefSuf; i++) {
      extractors.add(new ExtractorWordPrefix<String, String>(i, weightP * Math.log(i / (double) 2 + 2)));
      extractors.add(new ExtractorWordSuffix<String, String>(i, weightS * Math.log(i / (double) 2 + 2)));
    }
    possibleLabels.add("NN");
    possibleLabels.add("NNS");
    possibleLabels.add("VB");
    possibleLabels.add("VBZ");
    possibleLabels.add("VBG");
    possibleLabels.add("VBP");
    possibleLabels.add("VBN");
    possibleLabels.add("VBD");
    labelIndex.addAll(possibleLabels);
    nounLabelIndices.add(Integer.valueOf(labelIndex.indexOf("NN")));
    nounLabelIndices.add(Integer.valueOf(labelIndex.indexOf("NNS")));
    for (int i = 0; i < labelIndex.size(); i++) {
      integers.add(String.valueOf(i));
    }
  }


  boolean isNoun(int index) {
    return nounLabelIndices.contains(Integer.valueOf(index));
  }


  <L, F> LinearType2Classifier<L, F> trainClassifier(Type2Dataset<L, F> dataSet) {
    return LinearType2Classifier.trainClassifier(dataSet);
  }

  <L, F> LinearClassifier<L, F> trainClassifier(Dataset<L, F> dataSet) {
    LinearClassifierFactory<L, F> lcFactory = new LinearClassifierFactory<L, F>(new QNMinimizer(5), 1e-3, false, 1.0);
    return lcFactory.trainClassifier(dataSet);

  }

  <L, F> LinearClassifier<L, F> trainClassifier(RVFDataset<L, F> dataSet) {
    LinearClassifierFactory<L, F> lcFactory = new LinearClassifierFactory<L, F>(new QNMinimizer(5), 1e-3, false, 1.0);
    return lcFactory.trainClassifier(dataSet);
  }


  public double testWeightedType2Datum(LinearType2Classifier<String, Pair<String, String>> lC, Iterator<Pair<String, String>> examples) {
    int total = 0;
    int correct = 0;
    for (; examples.hasNext();) {
      Pair<String, String> next = examples.next();
      String correctLabel = integers.get(labelIndex.indexOf(next.second()));
      Object guess = lC.classOf(makeWeightedType2Datum(next.first(), next.second()));
      if (correctLabel.equals(guess)) {
        correct++;
      }
      total++;
    }
    System.err.println("correct " + correct + " out of " + total);
    return correct / (double) total;
  }


  public double testType2Datum(LinearType2Classifier<String, Pair<String, String>> lC, Iterator<Pair<String, String>> examples) {
    int total = 0;
    int correct = 0;
    for (; examples.hasNext();) {
      Pair<String, String> next = examples.next();
      Object correctLabel = integers.get(labelIndex.indexOf(next.second()));
      Type2Datum<String, Pair<String, String>> datum = makeType2Datum(next.first(), next.second());
      Object guess = lC.classOf(datum);
      //lC.justificationOf(datum);
      if (correctLabel.equals(guess)) {
        correct++;
      }
      total++;
    }
    System.err.println("correct " + correct + " out of " + total);
    return correct / (double) total;
  }


  public double testRVFDatum(LinearClassifier<String, String> lC, Iterator<Pair<String, String>> examples) {
    int total = 0;
    int correct = 0;
    for (; examples.hasNext();) {
      Pair<String, String> next = examples.next();
      String correctLabel = next.second();
      String guess = lC.classOf(makeWeightedDatum(next.first(), next.second()));
      if (correctLabel.equals(guess)) {
        correct++;
      }
      total++;
    }
    System.err.println("correct " + correct + " out of " + total);
    return correct / (double) total;
  }


  public double testDatum(LinearClassifier<String, String> lC, Iterator<Pair<String, String>> examples) {
    int total = 0;
    int correct = 0;
    for (; examples.hasNext();) {
      Pair<String, String> next = examples.next();
      String correctLabel = next.second();
      String guess = lC.classOf(makeDatum(next.first(), next.second()));
      if (correctLabel.equals(guess)) {
        correct++;
      }
      total++;
    }
    System.err.println("correct " + correct + " out of " + total);
    return correct / (double) total;
  }


  protected Type2Dataset<String, Pair<String, String>> makeWeightedType2Dataset(Iterator<Pair<String, String>> iterator) {
    Type2Dataset<String, Pair<String, String>> dataSet = new Type2Dataset<String, Pair<String, String>>();
    for (; iterator.hasNext();) {
      Pair<String, String> next = iterator.next();
      dataSet.add(makeWeightedType2Datum(next.first(), next.second()));
    }
    dataSet.summaryStatistics();
    //dataSet.applyFeatureCountThreshold(cutoff);
    dataSet.summaryStatistics();
    return dataSet;
  }


  protected Type2Corpus<String, String> makeUnweightedType2Corpus(Iterator<Pair<String, String>> iterator) {
    Type2Corpus<String, String> dataSet = new Type2Corpus<String, String>();
    for (; iterator.hasNext();) {
      Pair<String, String> next = iterator.next();
      Type2Datum<String, Pair<String, String>> d = makeType2Datum(next.first(), next.second());
      dataSet.addInstance(new SectionedType2Datum<String, String>(d));
    }
    dataSet.summaryStatistics();
    return dataSet;
  }

  protected Type2Dataset<String, Pair<String, String>> makeUnweightedType2Dataset(Iterator<Pair<String, String>> iterator) {
    Type2Dataset<String, Pair<String, String>> dataSet = new Type2Dataset<String, Pair<String, String>>();
    for (; iterator.hasNext();) {
      Pair<String, String> next = iterator.next();
      dataSet.add(makeType2Datum(next.first(), next.second()));
    }
    dataSet.summaryStatistics();
    return dataSet;
  }

  protected Dataset<String, String> makeUnweightedDataset(Iterator<Pair<String, String>> iterator) {
    Dataset<String, String> dataSet = new Dataset<String, String>();
    for (; iterator.hasNext();) {
      Pair<String, String> next = iterator.next();
      dataSet.add(makeDatum(next.first(), next.second()));
    }
    dataSet.summaryStatistics();
    return dataSet;
  }


  protected RVFDataset<String, String> makeWeightedDataset(Iterator<Pair<String, String>> iterator) {
    RVFDataset<String, String> dataSet = new RVFDataset<String, String>();
    for (; iterator.hasNext();) {
      Pair<String, String> next = iterator.next();
      dataSet.add(makeWeightedDatum(next.first(), next.second()));
    }
    dataSet.summaryStatistics();
    return dataSet;
  }

  /**
   * unweighted Type2Datum
   * want to make some features active at each label plus one equivalence class of all
   * VB and one equivalence class of all NN
   *
   * @return A Type2Datum
   */
  protected Type2Datum<String, Pair<String, String>> makeType2Datum(String word, String label) {
    TwoDimensionalCounter<String, Pair<String, String>> features = new TwoDimensionalCounter<String, Pair<String, String>>();
    Datum<String, String> d = makeDatum(word, label);
    Collection<String> feat1D = d.asFeatures();
    for (Iterator<String> featIter = feat1D.iterator(); featIter.hasNext();) {
      String feature1D = featIter.next();
      for (int i = 0; i < labelIndex.size(); i++) {
        features.incrementCount(integers.get(i), new Pair<String, String>(labelIndex.get(i), feature1D));
        if (isNoun(i)) {
          features.incrementCount(integers.get(i), new Pair<String, String>(noun, feature1D));
        } else {
          features.incrementCount(integers.get(i), new Pair<String, String>(verb, feature1D));
        }
      }
    }
    return new Type2Datum<String, Pair<String, String>>(features, integers.get(labelIndex.indexOf(label)));
  }


  /**
   * Weighted Type2Datum
   * want to make some features active at each label plus one equivalence class of all
   * VB and one equivalence class of all NN
   *
   * @return A Type2Datum
   */
  protected Type2Datum<String, Pair<String, String>> makeWeightedType2Datum(String word, String label) {
    TwoDimensionalCounter<String, Pair<String, String>> features = new TwoDimensionalCounter<String, Pair<String, String>>();
    RVFDatum<String, String> d = makeRVFDatum(word, label);
    Counter<String> feat1D = d.asFeaturesCounter();
    for (Iterator<String> featIter = feat1D.keySet().iterator(); featIter.hasNext();) {
      String feature1D = featIter.next();
      for (int i = 0; i < labelIndex.size(); i++) {
        features.incrementCount(integers.get(i), new Pair<String,String>(labelIndex.get(i), feature1D), feat1D.getCount(feature1D));
        if (isNoun(i)) {
          features.incrementCount(integers.get(i), new Pair<String,String>(noun, feature1D), feat1D.getCount(feature1D));
        } else {
          features.incrementCount(integers.get(i), new Pair<String,String>(verb, feature1D), feat1D.getCount(feature1D));
        }
      }
    }
    return new Type2Datum<String, Pair<String, String>>(features, integers.get(labelIndex.indexOf(label)));
  }


  protected RVFDatum<String, String> makeWeightedDatum(String word, String label) {
    ClassicCounter<String> features = new ClassicCounter<String>();
    for (FeatureExtractor<String, String, String> f : extractors){
      features.incrementCount(f.extract(word), f.value(word));
    }
    return new RVFDatum<String, String>(features, label);
  }

  protected Datum<String, String> makeDatum(String word, String label) {
    List<String> features = new ArrayList<String>();
    for (FeatureExtractor<String, String, String> f : extractors){
      features.add(f.extract(word));
    }
    return new BasicDatum<String, String>(features, label);
  }

  protected RVFDatum<String, String> makeRVFDatum(String word, String label) {
    ClassicCounter<String> features = new ClassicCounter<String>();
    for (FeatureExtractor<String, String, String> f:extractors){
      features.incrementCount(f.extract(word), f.value(word));
    }
    return new RVFDatum<String, String>(features, label);
  }


  public static void main(String[] args) {
    // Create a training set
    String train = args[0];
    String test = args[1];
    double[] wts = {.1};
    for (int j = 0; j < wts.length; j++) {
      for (int len = 3; len < 4; len++) {


        ClassifierTaggingExamples cTE = new ClassifierTaggingExamples(1, wts[j], wts[j], len);
        /*
        Dataset dataSet = cTE.makeUnweightedDataset(new FilePairsIterator(train));
        LinearClassifier lCU = cTE.trainClassifier(dataSet);
        double accuracy = cTE.testDatum(lCU, new FilePairsIterator(test));
        System.err.println("accuracy of unweighted type 1 " + accuracy);
        */
        /*
        //weighted type 1
        RVFDataset dataSetW = cTE.makeWeightedDataset(new FilePairsIterator(train));
        LinearClassifier lCUW = cTE.trainClassifier(dataSetW);
        double accuracyW = cTE.testRVFDatum(lCUW, new FilePairsIterator(test));
        System.err.println("accuracy of weighted type 1 " + wts[j] + " length " + len + " " + accuracyW);

        Type2Dataset dataSetT2 = cTE.makeUnweightedType2Dataset(new FilePairsIterator(train));
        LinearType2Classifier lCU2 = cTE.trainClassifier(dataSetT2);
        double accuracy2 = cTE.testType2Datum(lCU2, new FilePairsIterator(test));
        System.err.println("accuracy of unweighted type 2 " + accuracy2);
        */
        Type2Corpus<String, String> dataSetC = cTE.makeUnweightedType2Corpus(new FilePairsIterator(train));
        LinearType2ClassifierFactory<String, String> fact = new LinearType2ClassifierFactory<String, String>(1.0, 7, 1e-5);
        HashMap<Pair<String,String>, Pair<Double, Double>> priors = new HashMap<Pair<String,String>, Pair<Double, Double>>();
        priors.put(new Pair<String,String>("NOUN", "WORDboard"), new Pair<Double,Double>(1.0, 1.0));
        LinearType2Classifier<String, Pair<String, String>> lCUC = fact.trainClassifier(dataSetC, -1, 0, priors);//trainClassifier(dataSetT2);
        double accuracy2C = cTE.testType2Datum(lCUC, new FilePairsIterator(test));
        System.err.println("accuracy of unweighted type 2 corpus " + accuracy2C);
        System.err.println("weight of feature " + lCUC.getWeight(new Pair<String,String>("NOUN", "WORDboard")));

        /*
        dataSetT2 = cTE.makeWeightedType2Dataset(new FilePairsIterator(train));
        lCU2 = cTE.trainClassifier(dataSetT2);
        accuracy2 = cTE.testWeightedType2Datum(lCU2, new FilePairsIterator(test));
        System.err.println("accuracy of weighted type 2 " + accuracy2);
        */
      }
    }
  } // end main


  interface FeatureExtractor<IN, OUT, LABEL> extends InputFeatureExtractor<IN, OUT> {
    OUT extract(IN input, LABEL label);

    double value(IN input, LABEL label);
  }


  interface InputFeatureExtractor<IN, OUT> {
    OUT extract(IN input);

    double value(IN input);
  }


  abstract static class AbstractNominalWeightedExtractor<IN, LABEL> implements FeatureExtractor<IN, String, LABEL> {
    double weight = 1.0;
    String name;

    public double value(IN input) {
      return weight;
    }

    public abstract String nominalValue(IN input);

    public String extract(IN input, LABEL label) {
      return extract(input);
    }

    public double value(IN input, LABEL label) {
      return value(input);
    }

    public String extract(IN input) {
      return name + nominalValue(input);
    }
  }


  static class WordExtractor<LABEL> extends AbstractNominalWeightedExtractor<String, LABEL> {
    WordExtractor() {
      name = "WORD";
    }

    WordExtractor(double weight) {
      this();
      this.weight = weight;
    }

    @Override
    public String nominalValue(String input) {
      return input;
    }
  }


  static class ExtractorWordPrefix<IN, LABEL> extends AbstractNominalWeightedExtractor<IN, LABEL> {
    private int num;
    private static final String na = "NA";// for not applicable

    public ExtractorWordPrefix(int num) {
      this.num = num;
      name = "PREFIX" + num;
    }

    public ExtractorWordPrefix(int num, double weight) {
      this(num);
      this.weight = weight;
    }

    @Override
    public String nominalValue(IN input) {
      String inputString = input.toString();
      if (inputString.length() < num) {
        return na;
      }
      return inputString.substring(0, num);
    }

    @Override
    public String toString() {
      String cl = super.toString();
      return cl + " size " + num;
    }

  } // end class ExtractorCWordPref


  static class ExtractorWordSuffix<IN, LABEL> extends AbstractNominalWeightedExtractor<IN, LABEL> {
    private int num;
    private static final String na = "NA";// for not applicable

    public ExtractorWordSuffix(int num) {
      this.num = num;
      name = "SUFFIX" + num;
    }

    public ExtractorWordSuffix(int num, double weight) {
      this(num);
      this.weight = weight;
    }

    @Override
    public String nominalValue(IN input) {
      String inputString = input.toString();
      if (inputString.length() < num) {
        return na;
      }
      return inputString.substring(inputString.length() - num);
    }

    @Override
    public String toString() {
      String cl = super.toString();
      return cl + " size " + num;
    }

  } // end class ExtractorCWordPref


  static class FilePairsIterator implements Iterator<Pair<String, String>> {
    Pair<String, String> next;
    BufferedReader in;

    public FilePairsIterator(String filename) {
      try {
        in = new BufferedReader(new FileReader(filename));
        advance();
      } catch (Exception e) {
        e.printStackTrace();
      }
    }

    private void advance() {
      try {
        String line = in.readLine();
        if (line == null) {
          next = null;
          return;
        }
        StringTokenizer st = new StringTokenizer(line);
        next = new Pair<String,String>(st.nextToken(), st.nextToken());
      } catch (Exception e) {
        e.printStackTrace();
        next = null;
      }
    }

    public boolean hasNext() {
      return (next != null);
    }

    public Pair<String, String> next() throws NoSuchElementException {
      if (next == null) {
        throw new NoSuchElementException();
      }
      Pair<String, String> prev = next;
      advance();
      return prev;
    }

    public void remove() {
      throw new UnsupportedOperationException();
    }
  }

}


