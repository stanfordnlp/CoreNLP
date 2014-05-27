package edu.stanford.nlp.classify;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Random;
import java.util.Set;

import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.io.RuntimeIOException;
import edu.stanford.nlp.ling.Datum;
import edu.stanford.nlp.ling.RVFDatum;
import edu.stanford.nlp.math.ArrayMath;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.stats.Counters;
import edu.stanford.nlp.util.Generics;
import edu.stanford.nlp.util.Index;
import edu.stanford.nlp.util.Pair;
import edu.stanford.nlp.util.HashIndex;

/**
 * An interfacing class for {@link ClassifierFactory} that incrementally builds
 * a more memory-efficient representation of a {@link List} of {@link RVFDatum}
 * objects for the purposes of training a {@link Classifier} with a
 * {@link ClassifierFactory}.
 *
 * @author Jenny Finkel (jrfinkel@stanford.edu)
 * @author Rajat Raina (added methods to record data sources and ids)
 * @author Anna Rafferty (various refactoring with GeneralDataset/Dataset)
 * @author Sarah Spikes (sdspikes@cs.stanford.edu) (Templatization)
 *
 * @param <L> The type of the labels in the Dataset
 * @param <F> The type of the features in the Dataset
 */
public class RVFDataset<L, F> extends GeneralDataset<L, F> { // implements Iterable<RVFDatum<L, F>>, Serializable

  private static final long serialVersionUID = -3841757837680266182L;

  private double[][] values;  // [datumIndex][i] values of features listed in int[][] data
  private double[] minValues; // = null; //stores the minValues of all features
                              // for normalization.
  private double[] maxValues; // = null; //stores the maxValues of all features
                              // for normalization.
  double[] means;
  double[] stdevs; // means and stdevs of features, used for

  /*
   * Store source and id of each datum; optional, and not fully supported.
   */
  private ArrayList<Pair<String, String>> sourcesAndIds;

  public RVFDataset() {
    this(10);
  }

  public RVFDataset(int numDatums, Index<F> featureIndex, Index<L> labelIndex) {
    this(numDatums);
    this.labelIndex = labelIndex;
    this.featureIndex = featureIndex;
  }

  public RVFDataset(Index<F> featureIndex, Index<L> labelIndex) {
    this(10);
    this.labelIndex = labelIndex;
    this.featureIndex = featureIndex;
  }

  public RVFDataset(int numDatums) {
    initialize(numDatums);
  }

  /**
   * Constructor that fully specifies a Dataset. Needed this for
   * MulticlassDataset.
   */
  public RVFDataset(Index<L> labelIndex, int[] labels, Index<F> featureIndex, int[][] data, double[][] values) {
    this.labelIndex = labelIndex;
    this.labels = labels;
    this.featureIndex = featureIndex;
    this.data = data;
    this.values = values;
    this.size = labels.length;
  }

  @Override
  public Pair<GeneralDataset<L, F>, GeneralDataset<L, F>> split(double percentDev) {
    int devSize = (int) (percentDev * size());
    int trainSize = size() - devSize;

    int[][] devData = new int[devSize][];
    double[][] devValues = new double[devSize][];
    int[] devLabels = new int[devSize];

    int[][] trainData = new int[trainSize][];
    double[][] trainValues = new double[trainSize][];
    int[] trainLabels = new int[trainSize];

    System.arraycopy(data, 0, devData, 0, devSize);
    System.arraycopy(values, 0, devValues, 0, devSize);
    System.arraycopy(labels, 0, devLabels, 0, devSize);

    System.arraycopy(data, devSize, trainData, 0, trainSize);
    System.arraycopy(values, devSize, trainValues, 0, trainSize);
    System.arraycopy(labels, devSize, trainLabels, 0, trainSize);

    RVFDataset<L, F> dev = new RVFDataset<L, F>(labelIndex, devLabels, featureIndex, devData, devValues);
    RVFDataset<L, F> train = new RVFDataset<L, F>(labelIndex, trainLabels, featureIndex, trainData, trainValues);

    return new Pair<GeneralDataset<L, F>, GeneralDataset<L, F>>(train, dev);

  }

  public void scaleFeaturesGaussian() {
    means = new double[this.numFeatures()];
    Arrays.fill(means, 0);

    for (int i = 0; i < this.size(); i++) {
      for (int j = 0; j < data[i].length; j++)
        means[data[i][j]] += values[i][j];
    }
    ArrayMath.multiplyInPlace(means, 1.0 / this.size());

    stdevs = new double[this.numFeatures()];
    Arrays.fill(stdevs, 0);
    double[] deltaX = new double[this.numFeatures()];

    for (int i = 0; i < this.size(); i++) {
      for (int f = 0; f < this.numFeatures(); f++)
        deltaX[f] = -means[f];
      for (int j = 0; j < data[i].length; j++)
        deltaX[data[i][j]] += values[i][j];
      for (int f = 0; f < this.numFeatures(); f++) {
        stdevs[f] += deltaX[f] * deltaX[f];
      }
    }
    for (int f = 0; f < this.numFeatures(); f++) {
      stdevs[f] /= (this.size() - 1);
      stdevs[f] = Math.sqrt(stdevs[f]);
    }
    for (int i = 0; i < this.size(); i++) {
      for (int j = 0; j < data[i].length; j++) {
        int fID = data[i][j];
        if (stdevs[fID] != 0)
          values[i][j] = (values[i][j] - means[fID]) / stdevs[fID];
      }
    }

  }

  /**
   * Scales feature values linearly such that each feature value lies between 0
   * and 1.
   *
   */
  public void scaleFeatures() {
    // TODO: should also implement a method that scales the features using the
    // mean and std.
    minValues = new double[featureIndex.size()];
    maxValues = new double[featureIndex.size()];
    Arrays.fill(minValues, Double.POSITIVE_INFINITY);
    Arrays.fill(maxValues, Double.NEGATIVE_INFINITY);

    // first identify the max and min values for each feature.
    // System.out.printf("number of datums: %d dataset size: %d\n",data.length,size());
    for (int i = 0; i < size(); i++) {
      // System.out.printf("datum %d length %d\n", i,data[i].length);
      for (int j = 0; j < data[i].length; j++) {
        int f = data[i][j];
        if (values[i][j] < minValues[f])
          minValues[f] = values[i][j];
        if (values[i][j] > maxValues[f])
          maxValues[f] = values[i][j];
      }
    }

    for (int f = 0; f < featureIndex.size(); f++) {
      if (minValues[f] == Double.POSITIVE_INFINITY)
        throw new RuntimeException("minValue for feature " + f + " not assigned. ");
      if (maxValues[f] == Double.NEGATIVE_INFINITY)
        throw new RuntimeException("maxValue for feature " + f + " not assigned.");
    }

    // now scale each value such that it's between 0 and 1.
    for (int i = 0; i < size(); i++) {
      for (int j = 0; j < data[i].length; j++) {
        int f = data[i][j];
        if (minValues[f] != maxValues[f])// the equality can happen for binary
                                         // features which always take the value
                                         // of 1.0
          values[i][j] = (values[i][j] - minValues[f]) / (maxValues[f] - minValues[f]);
      }
    }

    /*
    for(int f = 0; f < featureIndex.size(); f++){
      if(minValues[f] == maxValues[f])
        throw new RuntimeException("minValue for feature "+f+" is equal to maxValue:"+minValues[f]);
    }
    */
  }

  /**
   * Checks if the dataset has any unbounded values. Always good to use this
   * before training a model on the dataset. This way, one can avoid seeing the
   * infamous 4's that get printed by the QuasiNewton Method when NaNs exist in
   * the data! -Ramesh
   */
  public void ensureRealValues() {
    double[][] values = getValuesArray();
    int[][] data = getDataArray();
    for (int i = 0; i < size(); i++) {
      for (int j = 0; j < values[i].length; j++) {
        if (Double.isNaN(values[i][j])) {
          int fID = data[i][j];
          F feature = featureIndex.get(fID);
          throw new RuntimeException("datum " + i + " has a NaN value for feature:" + feature);
        }
        if (Double.isInfinite(values[i][j])) {
          int fID = data[i][j];
          F feature = featureIndex.get(fID);
          throw new RuntimeException("datum " + i + " has infinite value for feature:" + feature);
        }
      }
    }
  }

  /**
   * Scales the values of each feature in each linearly using the min and max
   * values found in the training set. NOTE1: Not guaranteed to be between 0 and
   * 1 for a test datum. NOTE2: Also filters out features from each datum that
   * are not seen at training time.
   *
   * @param dataset
   * @return a new dataset
   */
  public RVFDataset<L, F> scaleDataset(RVFDataset<L, F> dataset) {
    RVFDataset<L, F> newDataset = new RVFDataset<L, F>(this.featureIndex, this.labelIndex);
    for (int i = 0; i < dataset.size(); i++) {
      RVFDatum<L, F> datum = dataset.getDatum(i);
      newDataset.add(scaleDatum(datum));
    }
    return newDataset;
  }

  /**
   * Scales the values of each feature linearly using the min and max values
   * found in the training set. NOTE1: Not guaranteed to be between 0 and 1 for
   * a test datum. NOTE2: Also filters out features from the datum that are not
   * seen at training time.
   *
   * @param datum
   * @return a new datum
   */
  public RVFDatum<L, F> scaleDatum(RVFDatum<L, F> datum) {
    // scale this dataset before scaling the datum
    if (minValues == null || maxValues == null)
      scaleFeatures();
    Counter<F> scaledFeatures = new ClassicCounter<F>();
    for (F feature : datum.asFeatures()) {
      int fID = this.featureIndex.indexOf(feature);
      if (fID >= 0) {
        double oldVal = datum.asFeaturesCounter().getCount(feature);
        double newVal;
        if (minValues[fID] != maxValues[fID])
          newVal = (oldVal - minValues[fID]) / (maxValues[fID] - minValues[fID]);
        else
          newVal = oldVal;
        scaledFeatures.incrementCount(feature, newVal);
      }
    }
    return new RVFDatum<L, F>(scaledFeatures, datum.label());
  }

  public RVFDataset<L, F> scaleDatasetGaussian(RVFDataset<L, F> dataset) {
    RVFDataset<L, F> newDataset = new RVFDataset<L, F>(this.featureIndex, this.labelIndex);
    for (int i = 0; i < dataset.size(); i++) {
      RVFDatum<L, F> datum = dataset.getDatum(i);
      newDataset.add(scaleDatumGaussian(datum));
    }
    return newDataset;
  }

  public RVFDatum<L, F> scaleDatumGaussian(RVFDatum<L, F> datum) {
    // scale this dataset before scaling the datum
    if (means == null || stdevs == null)
      scaleFeaturesGaussian();
    Counter<F> scaledFeatures = new ClassicCounter<F>();
    for (F feature : datum.asFeatures()) {
      int fID = this.featureIndex.indexOf(feature);
      if (fID >= 0) {
        double oldVal = datum.asFeaturesCounter().getCount(feature);
        double newVal;
        if (stdevs[fID] != 0)
          newVal = (oldVal - means[fID]) / stdevs[fID];
        else
          newVal = oldVal;
        scaledFeatures.incrementCount(feature, newVal);
      }
    }
    return new RVFDatum<L, F>(scaledFeatures, datum.label());
  }

  @Override
  public Pair<GeneralDataset<L, F>, GeneralDataset<L, F>> split(int start, int end) {
    int devSize = end - start;
    int trainSize = size() - devSize;

    int[][] devData = new int[devSize][];
    double[][] devValues = new double[devSize][];
    int[] devLabels = new int[devSize];

    int[][] trainData = new int[trainSize][];
    double[][] trainValues = new double[trainSize][];
    int[] trainLabels = new int[trainSize];

    System.arraycopy(data, start, devData, 0, devSize);
    System.arraycopy(values, start, devValues, 0, devSize);
    System.arraycopy(labels, start, devLabels, 0, devSize);

    System.arraycopy(data, 0, trainData, 0, start);
    System.arraycopy(data, end, trainData, start, size() - end);
    System.arraycopy(values, 0, trainValues, 0, start);
    System.arraycopy(values, end, trainValues, start, size() - end);
    System.arraycopy(labels, 0, trainLabels, 0, start);
    System.arraycopy(labels, end, trainLabels, start, size() - end);

    GeneralDataset<L, F> dev = new RVFDataset<L, F>(labelIndex, devLabels, featureIndex, devData, devValues);
    GeneralDataset<L, F> train = new RVFDataset<L, F>(labelIndex, trainLabels, featureIndex, trainData, trainValues);

    return new Pair<GeneralDataset<L, F>, GeneralDataset<L, F>>(train, dev);

  }

  // TODO: Check that this does what we want for Datum other than RVFDatum
  @Override
  public void add(Datum<L, F> d) {
    if (d instanceof RVFDatum<?, ?>) {
      addLabel(d.label());
      addFeatures(((RVFDatum<L, F>) d).asFeaturesCounter());
      size++;
    } else {
      addLabel(d.label());
      addFeatures(Counters.asCounter(d.asFeatures()));
      size++;
    }
  }

  public void add(Datum<L, F> d, String src, String id) {
    if (d instanceof RVFDatum<?, ?>) {
      addLabel(d.label());
      addFeatures(((RVFDatum<L, F>) d).asFeaturesCounter());
      addSourceAndId(src, id);
      size++;
    } else {
      addLabel(d.label());
      addFeatures(Counters.asCounter(d.asFeatures()));
      addSourceAndId(src, id);
      size++;
    }
  }

  // TODO shouldn't have both this and getRVFDatum
  @Override
  public RVFDatum<L, F> getDatum(int index) {
    return getRVFDatum(index);
  }

  /**
   * @return the index-ed datum
   *
   *         Note, this returns a new RVFDatum object, not the original RVFDatum
   *         that was added to the dataset.
   */
  @Override
  public RVFDatum<L, F> getRVFDatum(int index) {
    ClassicCounter<F> c = new ClassicCounter<F>();
    for (int i = 0; i < data[index].length; i++) {
      c.incrementCount(featureIndex.get(data[index][i]), values[index][i]);
    }
    return new RVFDatum<L, F>(c, labelIndex.get(labels[index]));
  }

  public String getRVFDatumSource(int index) {
    return sourcesAndIds.get(index).first();
  }

  public String getRVFDatumId(int index) {
    return sourcesAndIds.get(index).second();
  }

  private void addSourceAndId(String src, String id) {
    sourcesAndIds.add(new Pair<String, String>(src, id));
  }

  private void addLabel(L label) {
    if (labels.length == size) {
      int[] newLabels = new int[size * 2];
      System.arraycopy(labels, 0, newLabels, 0, size);
      labels = newLabels;
    }
    labels[size] = labelIndex.indexOf(label, true);
  }

  private void addFeatures(Counter<F> features) {
    if (data.length == size) {
      int[][] newData = new int[size * 2][];
      double[][] newValues = new double[size * 2][];
      System.arraycopy(data, 0, newData, 0, size);
      System.arraycopy(values, 0, newValues, 0, size);
      data = newData;
      values = newValues;
    }

    final List<F> featureNames = new ArrayList<F>(features.keySet());
    final int nFeatures = featureNames.size();
    data[size] = new int[nFeatures];
    values[size] = new double[nFeatures];
    for (int i = 0; i < nFeatures; ++i) {
      F feature = featureNames.get(i);
      int fID = featureIndex.indexOf(feature, true);
      if (fID >= 0) {
        data[size][i] = fID;
        values[size][i] = features.getCount(feature);
      } else {
        // Usually a feature present at test but not training time.
        assert featureIndex.isLocked() : "Could not add feature to index: " + feature;
      }
    }
  }

  /**
   * Resets the Dataset so that it is empty and ready to collect data.
   */
  @Override
  public void clear() {
    clear(10);
  }

  /**
   * Resets the Dataset so that it is empty and ready to collect data.
   */
  @Override
  public void clear(int numDatums) {
    initialize(numDatums);
  }

  @Override
  protected void initialize(int numDatums) {
    labelIndex = new HashIndex<L>();
    featureIndex = new HashIndex<F>();
    labels = new int[numDatums];
    data = new int[numDatums][];
    values = new double[numDatums][];
    sourcesAndIds = new ArrayList<Pair<String, String>>(numDatums);
    size = 0;
  }

  /**
   * Prints some summary statistics to stderr for the Dataset.
   */
  @Override
  public void summaryStatistics() {
    System.err.println("numDatums: " + size);
    System.err.print("numLabels: " + labelIndex.size() + " [");
    Iterator<L> iter = labelIndex.iterator();
    while (iter.hasNext()) {
      System.err.print(iter.next());
      if (iter.hasNext()) {
        System.err.print(", ");
      }
    }
    System.err.println("]");
    System.err.println("numFeatures (Phi(X) types): " + featureIndex.size());
    /*for(int i = 0; i < data.length; i++) {
      for(int j = 0; j < data[i].length; j++) {
      System.out.println(data[i][j]);
      }
      }*/
  }

  /**
   * prints the full feature matrix in tab-delimited form. These can be BIG
   * matrices, so be careful! [Can also use printFullFeatureMatrixWithValues]
   */
  public void printFullFeatureMatrix(PrintWriter pw) {
    String sep = "\t";
    for (int i = 0; i < featureIndex.size(); i++) {
      pw.print(sep + featureIndex.get(i));
    }
    pw.println();
    for (int i = 0; i < labels.length; i++) {
      pw.print(labelIndex.get(i));
      Set<Integer> feats = Generics.newHashSet();
      for (int j = 0; j < data[i].length; j++) {
        int feature = data[i][j];
        feats.add(Integer.valueOf(feature));
      }
      for (int j = 0; j < featureIndex.size(); j++) {
        if (feats.contains(Integer.valueOf(j))) {
          pw.print(sep + "1");
        } else {
          pw.print(sep + "0");
        }
      }
      pw.println();
    }
  }

  /**
   * Modification of printFullFeatureMatrix to correct bugs & print values
   * (Rajat). Prints the full feature matrix in tab-delimited form. These can be
   * BIG matrices, so be careful!
   */
  public void printFullFeatureMatrixWithValues(PrintWriter pw) {
    String sep = "\t";
    for (int i = 0; i < featureIndex.size(); i++) {
      pw.print(sep + featureIndex.get(i));
    }
    pw.println();
    for (int i = 0; i < size; i++) { // changed labels.length to size
      pw.print(labelIndex.get(labels[i])); // changed i to labels[i]
      Map<Integer, Double> feats = Generics.newHashMap();
      for (int j = 0; j < data[i].length; j++) {
        int feature = data[i][j];
        double val = values[i][j];
        feats.put(Integer.valueOf(feature), new Double(val));
      }
      for (int j = 0; j < featureIndex.size(); j++) {
        if (feats.containsKey(Integer.valueOf(j))) {
          pw.print(sep + feats.get(Integer.valueOf(j)));
        } else {
          pw.print(sep + " ");
        }
      }
      pw.println();
    }
    pw.flush();
  }

  /**
   * Constructs a Dataset by reading in a file in SVM light format.
   *
   */
  public static RVFDataset<String, String> readSVMLightFormat(String filename) {
    return readSVMLightFormat(filename, new HashIndex<String>(), new HashIndex<String>());
  }

  /**
   * Constructs a Dataset by reading in a file in SVM light format. The lines
   * parameter is filled with the lines of the file for further processing (if
   * lines is null, it is assumed no line information is desired)
   */
  public static RVFDataset<String, String> readSVMLightFormat(String filename, List<String> lines) {
    return readSVMLightFormat(filename, new HashIndex<String>(), new HashIndex<String>(), lines);
  }

  /**
   * Constructs a Dataset by reading in a file in SVM light format. the created
   * dataset has the same feature and label index as given
   */
  public static RVFDataset<String, String> readSVMLightFormat(String filename, Index<String> featureIndex, Index<String> labelIndex) {
    return readSVMLightFormat(filename, featureIndex, labelIndex, null);
  }

  /**
   * Removes all features from the dataset that are not in featureSet.
   *
   * @param featureSet
   */
  public void selectFeaturesFromSet(Set<F> featureSet) {
    HashIndex<F> newFeatureIndex = new HashIndex<F>();
    int[] featMap = new int[featureIndex.size()];
    Arrays.fill(featMap, -1);
    for (F feature : featureSet) {
      int oldID = featureIndex.indexOf(feature);
      if (oldID >= 0) { // it's a valid feature in the index
        int newID = newFeatureIndex.indexOf(feature, true);
        featMap[oldID] = newID;
      }
    }
    featureIndex = newFeatureIndex;
    for (int i = 0; i < size; i++) {
      List<Integer> featList = new ArrayList<Integer>(data[i].length);
      List<Double> valueList = new ArrayList<Double>(values[i].length);
      for (int j = 0; j < data[i].length; j++) {
        if (featMap[data[i][j]] >= 0) {
          featList.add(featMap[data[i][j]]);
          valueList.add(values[i][j]);
        }
      }
      data[i] = new int[featList.size()];
      values[i] = new double[valueList.size()];
      for (int j = 0; j < data[i].length; j++) {
        data[i][j] = featList.get(j);
        values[i][j] = valueList.get(j);
      }
    }
  }

  /**
   * Applies a feature count threshold to the RVFDataset. All features that
   * occur fewer than <i>k</i> times are expunged.
   */
  public void applyFeatureCountThreshold(int k) {
    float[] counts = getFeatureCounts();
    HashIndex<F> newFeatureIndex = new HashIndex<F>();

    int[] featMap = new int[featureIndex.size()];
    for (int i = 0; i < featMap.length; i++) {
      F feat = featureIndex.get(i);
      if (counts[i] >= k) {
        int newIndex = newFeatureIndex.size();
        newFeatureIndex.add(feat);
        featMap[i] = newIndex;
      } else {
        featMap[i] = -1;
      }
      // featureIndex.remove(feat);
    }

    featureIndex = newFeatureIndex;
    // counts = null; // This is unnecessary; JVM can clean it up

    for (int i = 0; i < size; i++) {
      List<Integer> featList = new ArrayList<Integer>(data[i].length);
      List<Double> valueList = new ArrayList<Double>(values[i].length);
      for (int j = 0; j < data[i].length; j++) {
        if (featMap[data[i][j]] >= 0) {
          featList.add(featMap[data[i][j]]);
          valueList.add(values[i][j]);
        }
      }
      data[i] = new int[featList.size()];
      values[i] = new double[valueList.size()];
      for (int j = 0; j < data[i].length; j++) {
        data[i][j] = featList.get(j);
        values[i][j] = valueList.get(j);
      }
    }
  }

  /**
   * Applies a feature max count threshold to the RVFDataset. All features that
   * occur greater than <i>k</i> times are expunged.
   */
  @Override
  public void applyFeatureMaxCountThreshold(int k) {
    float[] counts = getFeatureCounts();
    HashIndex<F> newFeatureIndex = new HashIndex<F>();

    int[] featMap = new int[featureIndex.size()];
    for (int i = 0; i < featMap.length; i++) {
      F feat = featureIndex.get(i);
      if (counts[i] <= k) {
        int newIndex = newFeatureIndex.size();
        newFeatureIndex.add(feat);
        featMap[i] = newIndex;
      } else {
        featMap[i] = -1;
      }
      // featureIndex.remove(feat);
    }

    featureIndex = newFeatureIndex;
    // counts = null; // This is unnecessary; JVM can clean it up

    for (int i = 0; i < size; i++) {
      List<Integer> featList = new ArrayList<Integer>(data[i].length);
      List<Double> valueList = new ArrayList<Double>(values[i].length);
      for (int j = 0; j < data[i].length; j++) {
        if (featMap[data[i][j]] >= 0) {
          featList.add(featMap[data[i][j]]);
          valueList.add(values[i][j]);
        }
      }
      data[i] = new int[featList.size()];
      values[i] = new double[valueList.size()];
      for (int j = 0; j < data[i].length; j++) {
        data[i][j] = featList.get(j);
        values[i][j] = valueList.get(j);
      }
    }
  }

  private static RVFDataset<String, String> readSVMLightFormat(String filename, Index<String> featureIndex, Index<String> labelIndex, List<String> lines) {
    BufferedReader in = null;
    RVFDataset<String, String> dataset;
    try {
      dataset = new RVFDataset<String, String>(10, featureIndex, labelIndex);
      in = IOUtils.readerFromString(filename);

      while (in.ready()) {
        String line = in.readLine();
        if (lines != null)
          lines.add(line);
        dataset.add(svmLightLineToRVFDatum(line));
      }
    } catch (IOException e) {
      throw new RuntimeIOException(e);
    } finally {
      IOUtils.closeIgnoringExceptions(in);
    }
    return dataset;
  }

  public static RVFDatum<String, String> svmLightLineToRVFDatum(String l) {
    l = l.replaceFirst("#.*$", ""); // remove any trailing comments
    String[] line = l.split("\\s+");
    ClassicCounter<String> features = new ClassicCounter<String>();
    for (int i = 1; i < line.length; i++) {
      String[] f = line[i].split(":");
      if (f.length != 2) {
        throw new IllegalArgumentException("Bad data format: " + l);
      }
      double val = Double.parseDouble(f[1]);
      features.incrementCount(f[0], val);
    }
    return new RVFDatum<String, String>(features, line[0]);
  }

  // todo [cdm 2012]: This duplicates the functionality of the methods above. Should be refactored.
  /**
   * Read SVM-light formatted data into this dataset.
   *
   * A strict SVM-light format is expected, where labels and features are both
   * encoded as integers. These integers are converted into the dataset label
   * and feature types using the indexes stored in this dataset.
   *
   * @param file The file from which the data should be read.
   */
  public void readSVMLightFormat(File file) {
    for (String line : IOUtils.readLines(file)) {
      line = line.replaceAll("#.*", ""); // remove any trailing comments
      String[] items = line.split("\\s+");
      Integer label = Integer.parseInt(items[0]);
      Counter<F> features = new ClassicCounter<F>();
      for (int i = 1; i < items.length; i++) {
        String[] featureItems = items[i].split(":");
        int feature = Integer.parseInt(featureItems[0]);
        double value = Double.parseDouble(featureItems[1]);
        features.incrementCount(this.featureIndex.get(feature), value);
      }
      this.add(new RVFDatum<L, F>(features, this.labelIndex.get(label)));
    }
  }

  /**
   * Write the dataset in SVM-light format to the file.
   *
   * A strict SVM-light format will be written, where labels and features are
   * both encoded as integers, using the label and feature indexes of this
   * dataset. Datasets written by this method can be read by
   * {@link #readSVMLightFormat(File)}.
   *
   * @param file The location where the dataset should be written.
   */
  public void writeSVMLightFormat(File file) throws FileNotFoundException {
    PrintWriter writer = new PrintWriter(file);
    writeSVMLightFormat(writer);
    writer.close();
  }

  public void writeSVMLightFormat(PrintWriter writer) {
    for (RVFDatum<L, F> datum : this) {
      writer.print(this.labelIndex.indexOf(datum.label()));
      Counter<F> features = datum.asFeaturesCounter();
      for (F feature : features.keySet()) {
        double count = features.getCount(feature);
        writer.format(" %s:%f", this.featureIndex.indexOf(feature), count);
      }
      writer.println();
    }
  }

  /**
   * Prints the sparse feature matrix using
   * {@link #printSparseFeatureMatrix(PrintWriter)} to {@link System#out
   * System.out}.
   */
  @Override
  public void printSparseFeatureMatrix() {
    printSparseFeatureMatrix(new PrintWriter(System.out, true));
  }

  /**
   * Prints a sparse feature matrix representation of the Dataset. Prints the
   * actual {@link Object#toString()} representations of features.
   */
  @Override
  public void printSparseFeatureMatrix(PrintWriter pw) {
    String sep = "\t";
    for (int i = 0; i < size; i++) {
      pw.print(labelIndex.get(labels[i]));
      int[] datum = data[i];
      for (int feat : datum) {
        pw.print(sep);
        pw.print(featureIndex.get(feat));
      }
      pw.println();
    }
  }

  /**
   * Prints a sparse feature-value output of the Dataset. Prints the actual
   * {@link Object#toString()} representations of features. This is probably
   * what you want for RVFDataset since the above two methods seem useless and
   * unused.
   */
  public void printSparseFeatureValues(PrintWriter pw) {
    for (int i = 0; i < size; i++) {
      printSparseFeatureValues(i, pw);
    }
  }

  /**
   * Prints a sparse feature-value output of the Dataset. Prints the actual
   * {@link Object#toString()} representations of features. This is probably
   * what you want for RVFDataset since the above two methods seem useless and
   * unused.
   */
  public void printSparseFeatureValues(int datumNo, PrintWriter pw) {
    pw.print(labelIndex.get(labels[datumNo]));
    pw.print('\t');
    pw.println("LABEL");
    int[] datum = data[datumNo];
    double[] vals = values[datumNo];
    assert datum.length == vals.length;
    for (int i = 0; i < datum.length; i++) {
      pw.print(featureIndex.get(datum[i]));
      pw.print('\t');
      pw.println(vals[i]);
    }
    pw.println();
  }

  public static void main(String[] args) {
    RVFDataset<String, String> data = new RVFDataset<String, String>();
    ClassicCounter<String> c1 = new ClassicCounter<String>();
    c1.incrementCount("fever", 3.5);
    c1.incrementCount("cough", 1.1);
    c1.incrementCount("congestion", 4.2);

    ClassicCounter<String> c2 = new ClassicCounter<String>();
    c2.incrementCount("fever", 1.5);
    c2.incrementCount("cough", 2.1);
    c2.incrementCount("nausea", 3.2);

    ClassicCounter<String> c3 = new ClassicCounter<String>();
    c3.incrementCount("cough", 2.5);
    c3.incrementCount("congestion", 3.2);

    data.add(new RVFDatum<String, String>(c1, "cold"));
    data.add(new RVFDatum<String, String>(c2, "flu"));
    data.add(new RVFDatum<String, String>(c3, "cold"));
    data.summaryStatistics();

    LinearClassifierFactory<String, String> factory = new LinearClassifierFactory<String, String>();
    factory.useQuasiNewton();

    LinearClassifier<String, String> c = factory.trainClassifier(data);

    ClassicCounter<String> c4 = new ClassicCounter<String>();
    c4.incrementCount("cough", 2.3);
    c4.incrementCount("fever", 1.3);

    RVFDatum<String, String> datum = new RVFDatum<String, String>(c4);

    c.justificationOf((Datum<String, String>) datum);
  }

  @Override
  public double[][] getValuesArray() {
    if (size == 0) {
      return new double[0][];
    }
    values = trimToSize(values);
    data = trimToSize(data);
    return values;
  }

  @Override
  public String toString() {
    return "Dataset of size " + size;
  }

  public String toSummaryString() {
    StringWriter sw = new StringWriter();
    PrintWriter pw = new PrintWriter(sw);
    pw.println("Number of data points: " + size());

    pw.print("Number of labels: " + labelIndex.size() + " [");
    Iterator<L> iter = labelIndex.iterator();
    while (iter.hasNext()) {
      pw.print(iter.next());
      if (iter.hasNext()) {
        pw.print(", ");
      }
    }
    pw.println("]");
    pw.println("Number of features (Phi(X) types): " + featureIndex.size());
    pw.println("Number of active feature types: " + numFeatureTypes());
    pw.println("Number of active feature tokens: " + numFeatureTokens());

    return sw.toString();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Iterator<RVFDatum<L, F>> iterator() {
    return new Iterator<RVFDatum<L, F>>() {
      private int index; // = 0;

      @Override
      public boolean hasNext() {
        return this.index < size;
      }

      public RVFDatum<L, F> next() {
        if (index >= size) {
          throw new NoSuchElementException();
        }
        RVFDatum<L, F> next = getRVFDatum(this.index);
        ++this.index;
        return next;
      }

      @Override
      public void remove() {
        throw new UnsupportedOperationException();
      }
    };
  }

  /**
   * Randomizes the data array in place. Needs to be redefined here because we
   * need to randomize the values as well.
   */
  @Override
  public void randomize(long randomSeed) {
    Random rand = new Random(randomSeed);
    for (int j = size - 1; j > 0; j--) {
      int randIndex = rand.nextInt(j);
      int[] tmp = data[randIndex];
      data[randIndex] = data[j];
      data[j] = tmp;

      int tmpl = labels[randIndex];
      labels[randIndex] = labels[j];
      labels[j] = tmpl;

      double[] tmpv = values[randIndex];
      values[randIndex] = values[j];
      values[j] = tmpv;
    }
  }

  /**
   * Randomizes the data array in place. Needs to be redefined here because we
   * need to randomize the values as well.
   */
  @Override
  public <E> void shuffleWithSideInformation(long randomSeed, List<E> sideInformation) {
    if (size != sideInformation.size()) {
      throw new IllegalArgumentException("shuffleWithSideInformation: sideInformation not of same size as Dataset");
    }
    Random rand = new Random(randomSeed);
    for (int j = size - 1; j > 0; j--) {
      int randIndex = rand.nextInt(j);

      int[] tmp = data[randIndex];
      data[randIndex] = data[j];
      data[j] = tmp;

      int tmpl = labels[randIndex];
      labels[randIndex] = labels[j];
      labels[j] = tmpl;

      double[] tmpv = values[randIndex];
      values[randIndex] = values[j];
      values[j] = tmpv;

      E tmpE = sideInformation.get(randIndex);
      sideInformation.set(randIndex, sideInformation.get(j));
      sideInformation.set(j, tmpE);
    }
  }

}
