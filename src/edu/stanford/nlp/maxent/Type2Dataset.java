package edu.stanford.nlp.maxent;

import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.stats.TwoDimensionalCounter;
import edu.stanford.nlp.util.Index;
import edu.stanford.nlp.util.IntPair;
import edu.stanford.nlp.util.HashIndex;

import java.io.PrintWriter;
import java.util.HashSet;
import java.util.Set;


/**
 * An efficient representation of a collection of {@link Type2Datum} objects to be used to train a type 2 classifier.
 *
 * @author Roger Levy
 */
public class Type2Dataset<L, F> {
  private static final double SIZE_INCREMENT = 1.5;
  private static final int DEFAULT_INITIAL_SIZE = 1000;
  private static final int DEFAULT_SINGLE_FEATURE_ARRAY_SIZE = 2;

  //Experiments stuff
  private int[][] trueClasses; // always 2-deep.  for now, the first field of trueClasses is just a filler number
  //private int[][] classes;
  //private int[][] features;
  //private float[][] values;
  private int size = 0;
  //end Experiments stuff

  // Feature stuff
  private int[][] featureInstances;
  private double[][] featureInstanceValues;
  private int[] featureArraySizes;
  private int[] maxYs;// the number of possible class values for each instance (maxY+1)
  // end Feature stuff

  private Index<L> classIndex = new HashIndex<L>();
  private Index<F> featureIndex = new HashIndex<F>();
  private Index<IntPair> instanceIndex = new HashIndex<IntPair>(); // an index for (x,y) instances to be used by Feature.


  /**
   * Constructs a Type2Dataset with an initial maximum number of examples to hold.
   *
   * @param initialSize the initial maximum number of examples to hold.
   */
  public Type2Dataset(int initialSize) {
    trueClasses = new int[initialSize][];
    maxYs = new int[initialSize];
    featureInstances = new int[initialSize][];
    featureInstanceValues = new double[initialSize][];
    featureArraySizes = new int[initialSize];
  }

  /**
   * Constructs a Type2Dataset.
   */
  public Type2Dataset() {
    this(DEFAULT_INITIAL_SIZE);
  }

  /**
   * Returns a {@link Problem} instance that contains the relevant information from the Type2Dataset.
   *
   */
  public Problem toProblem() {
    trimToSize();
    Experiments experiments = toExperiments();
    Features feats = toFeatures(experiments);
    return new Problem(experiments, feats);
  }

  private Experiments toExperiments() {
    return new Experiments(trueClasses, maxYs);
  }


  private Features toFeatures(Experiments e) {
    Features feats = new Features();
    for (int i = 0; i < featureInstances.length; i++) {
      feats.add(new Feature(e, featureInstances[i], featureInstanceValues[i], instanceIndex));
    }
    return feats;
  }

  /**
   * Adds a {@link Type2Datum} to the dataset.
   *
   */
  public void add(Type2Datum<L, F> datum) {
    ensureSize();
    addTrueClass(datum.trueClass());
    addClasses(datum.classes());
    addClassFeatures(datum.classFeatureCounts);
    size++;
  }


  /**
   * helper function for add.
   */
  private void addClasses(Set<L> classes) {
    for (L possibleClass: classes) {
      classIndex.add(possibleClass);
      int intPossibleClass = classIndex.indexOf(possibleClass);
      if (maxYs[size] <= intPossibleClass) {
        maxYs[size] = intPossibleClass + 1;
      }
    }
  }

  /**
   * helper function for add.
   */
  private void addTrueClass(L trueClass) {
    classIndex.add(trueClass);
    int intTrueClass = classIndex.indexOf(trueClass);
    trueClasses[size] = new int[]{size, intTrueClass};
  }

  /* helper function for add. */
  private void addClassFeatures(TwoDimensionalCounter<L, F> classFeatures) {
    for (L thisClass: classFeatures.firstKeySet()) {
      Counter<F> thisClassFeatures = classFeatures.getCounter(thisClass);
      classIndex.add(thisClass);
      int thisIntClass = classIndex.indexOf(thisClass);
      IntPair instance = new IntPair(size, thisIntClass);
      instanceIndex.add(instance);
      int intInstance = instanceIndex.indexOf(instance);
      for (F thisFeature: thisClassFeatures.keySet()) {
        double thisFeatureValue = thisClassFeatures.getCount(thisFeature);
        if (thisFeatureValue == 0) {
          continue;
        }
        featureIndex.add(thisFeature);
        int thisIntFeature = featureIndex.indexOf(thisFeature);

        ensureFeatureIndexSize(thisIntFeature);

        //classes[size][k] = thisIntClass;
        //features[size][k] = thisIntFeature;
        //values[size][k] = thisFeatureValue;

        featureInstances[thisIntFeature][featureArraySizes[thisIntFeature]] = intInstance;
        featureInstanceValues[thisIntFeature][featureArraySizes[thisIntFeature]] = thisFeatureValue;
        featureArraySizes[thisIntFeature]++;
      }
    }
  }

  /**
   * returns the {@link edu.stanford.nlp.util.Index} that maps from features to integers.
   */
  public Index<F> featureIndex() {
    return featureIndex;
  }

  private void ensureFeatureIndexSize(int thisIntFeature) {
    ensureFeatureArrays(thisIntFeature);
    ensureThisFeatureArrays(thisIntFeature);
    int s = featureArraySizes[thisIntFeature];
    if (s == featureInstances[thisIntFeature].length) {
      int[] newFeatureInstances = new int[(int) ((s) * SIZE_INCREMENT)];
      System.arraycopy(featureInstances[thisIntFeature], 0, newFeatureInstances, 0, s);
      featureInstances[thisIntFeature] = newFeatureInstances;
      double[] newFeatureInstanceValues = new double[(int) ((s) * SIZE_INCREMENT)];
      System.arraycopy(featureInstanceValues[thisIntFeature], 0, newFeatureInstanceValues, 0, s);
      featureInstanceValues[thisIntFeature] = newFeatureInstanceValues;
    }
  }

  private void ensureThisFeatureArrays(int thisIntFeature) {
    if (featureInstances[thisIntFeature] == null) {
      featureInstances[thisIntFeature] = new int[DEFAULT_SINGLE_FEATURE_ARRAY_SIZE];
      featureInstanceValues[thisIntFeature] = new double[DEFAULT_SINGLE_FEATURE_ARRAY_SIZE];
    }
  }

  private void ensureFeatureArrays(int thisIntFeature) {
    if (featureArraySizes.length <= thisIntFeature) {
      int[][] newFeatureInstances = new int[(int) ((featureArraySizes.length) * SIZE_INCREMENT)][];
      System.arraycopy(featureInstances, 0, newFeatureInstances, 0, featureArraySizes.length);
      featureInstances = newFeatureInstances;
      double[][] newFeatureInstanceValues = new double[(int) ((featureArraySizes.length) * SIZE_INCREMENT)][];
      System.arraycopy(featureInstanceValues, 0, newFeatureInstanceValues, 0, featureArraySizes.length);
      featureInstanceValues = newFeatureInstanceValues;
      int[] newFeatureArraySizes = new int[(int) ((featureArraySizes.length) * SIZE_INCREMENT)];
      System.arraycopy(featureArraySizes, 0, newFeatureArraySizes, 0, featureArraySizes.length);
      featureArraySizes = newFeatureArraySizes;
    }
  }


  private void ensureSize() {
    if (size == trueClasses.length) {
      int[][] newTrueClasses = new int[(int) ((size) * SIZE_INCREMENT)][2];
      System.arraycopy(trueClasses, 0, newTrueClasses, 0, size);
      trueClasses = newTrueClasses;
      int[] newMaxYs = new int[(int) ((size) * SIZE_INCREMENT)];
      System.arraycopy(maxYs, 0, newMaxYs, 0, size);
      maxYs = newMaxYs;
      /*
      int[][] newClasses = new int[(int) (((double) size)*SIZE_INCREMENT)][];
      System.arraycopy(classes,0,newClasses,0,size);
      classes = newClasses;
      int[][] newFeatures = new int[(int) (((double) size)*SIZE_INCREMENT)][];
      System.arraycopy(features,0,newFeatures,0,size);
      features = newFeatures;
      float[][] newValues = new float[(int) (((double) size)*SIZE_INCREMENT)][];
      System.arraycopy(values,0,newValues,0,size);
      values = newValues;
      */
    }
  }


  /**
   * Returns the number of examples in the Type2Dataset.
   */
  public int size() {
    return size;
  }

  private void trimToSize() {
    int numFeatures = featureIndex.size();
    int[][] newFeatureInstances = new int[numFeatures][];
    System.arraycopy(featureInstances, 0, newFeatureInstances, 0, numFeatures);
    featureInstances = newFeatureInstances;
    double[][] newFeatureInstanceValues = new double[numFeatures][];
    System.arraycopy(featureInstanceValues, 0, newFeatureInstanceValues, 0, numFeatures);
    featureInstanceValues = newFeatureInstanceValues;
    int[] newFeatureArraySizes = new int[numFeatures];
    System.arraycopy(featureArraySizes, 0, newFeatureArraySizes, 0, numFeatures);
    featureArraySizes = newFeatureArraySizes;
    int[][] newTrueClasses = new int[size][];
    System.arraycopy(trueClasses, 0, newTrueClasses, 0, size);
    trueClasses = newTrueClasses;
  }

  /**
   * Is "I saw the dog with a telescope" an NP or VP attachment?
   *
   */
  public static void main(String[] args) {
    Type2Dataset<String, String> data = new Type2Dataset<String, String>();
    TwoDimensionalCounter<String, String> gc1 = new TwoDimensionalCounter<String, String>();
    Set<String> possible = new HashSet<String>();
    possible.add("NP");
    possible.add("VP");
    possible.add("NONE");

    gc1.incrementCount("NP", "dog-with", 1.0);
    gc1.incrementCount("NP", "dog-telescope", 1.0);
    gc1.incrementCount("VP", "saw-with", 1.0);
    gc1.incrementCount("VP", "saw-telescope", 1.0);
    gc1.incrementCount("NP", "saw", 1.0);
    gc1.incrementCount("VP", "saw", 1.0);
    gc1.incrementCount("NP", "with", 1.0);
    gc1.incrementCount("VP", "with", 1.0);
    gc1.incrementCount("NP", "telescope", 1.0);
    gc1.incrementCount("VP", "telescope", 1.0);
    data.add(new Type2Datum<String, String>(gc1, possible, "VP"));

    TwoDimensionalCounter<String, String> gc2 = new TwoDimensionalCounter<String, String>();
    gc2.incrementCount("NP", "girl-with", 1.0);
    gc2.incrementCount("NP", "girl-telescope", 1.0);
    gc2.incrementCount("VP", "met-with", 1.0);
    gc2.incrementCount("VP", "met-telescope", 1.0);
    gc2.incrementCount("NP", "met", 1.0);
    gc2.incrementCount("VP", "met", 1.0);
    gc2.incrementCount("NP", "with", 1.0);
    gc2.incrementCount("VP", "with", 1.0);
    gc2.incrementCount("NP", "telescope", 1.0);
    gc2.incrementCount("VP", "telescope", 1.0);
    data.add(new Type2Datum<String, String>(gc2, possible, "NP"));

    data.summaryStatistics();
    LinearType2Classifier<String, String> classifier = LinearType2Classifier.trainClassifier(data);
    ClassicCounter<String> scores = classifier.scoresOf(new Type2Datum<String, String>(gc1, possible, "VP"));
    System.out.println(scores.toString());
    scores = classifier.scoresOf(new Type2Datum<String, String>(gc2, possible, "NP"));
    System.out.println(scores.toString());

  }

  /**
   * Prints some summary statistics for the Type2Dataset.  The number of features, number of classes, number of examples.
   */
  public void summaryStatistics() {
    summaryStatistics(new PrintWriter(System.err, true));
  }

  /**
   * Prints some summary statistics for the Type2Dataset to a {@link PrintWriter}.  The number of features, number of classes, number of examples.
   *
   * @param pw the PrintWriter to print the statistics to.
   */
  public void summaryStatistics(PrintWriter pw) {
    pw.println("number of features: " + featureIndex.size());
    pw.println("number of classes: " + classIndex.size());
    pw.println("number of examples: " + size());
  }

}
