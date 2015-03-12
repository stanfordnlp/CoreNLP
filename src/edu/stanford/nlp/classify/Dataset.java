package edu.stanford.nlp.classify;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import edu.stanford.nlp.ling.BasicDatum;
import edu.stanford.nlp.ling.Datum;
import edu.stanford.nlp.ling.RVFDatum;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.stats.TwoDimensionalCounter;
import edu.stanford.nlp.objectbank.ObjectBank;
import edu.stanford.nlp.util.Generics;
import edu.stanford.nlp.util.Index;
import edu.stanford.nlp.util.HashIndex;
import edu.stanford.nlp.util.Pair;
import edu.stanford.nlp.util.ScoredComparator;
import edu.stanford.nlp.util.ScoredObject;


/**
 * An interfacing class for {@link ClassifierFactory} that incrementally
 * builds a more memory-efficient representation of a {@link List} of
 * {@link Datum} objects for the purposes of training a {@link Classifier}
 * with a {@link ClassifierFactory}.
 *
 * @author Roger Levy (rog@stanford.edu)
 * @author Anna Rafferty (various refactoring with GeneralDataset/RVFDataset)
 * @author Sarah Spikes (sdspikes@cs.stanford.edu) (templatization)
 * @author nmramesh@cs.stanford.edu {@link #getL1NormalizedTFIDFDatum(Datum, Counter) and #getL1NormalizedTFIDFDataset()}
 *
 * @param <L> Label type
 * @param <F> Feature type
 */
public class Dataset<L, F> extends GeneralDataset<L, F> {

  private static final long serialVersionUID = -3883164942879961091L;

  public Dataset() {
    this(10);
  }

  public Dataset(int numDatums) {
    initialize(numDatums);
  }

  public Dataset(int numDatums, Index<F> featureIndex, Index<L> labelIndex) {
    initialize(numDatums);
    this.featureIndex = featureIndex;
    this.labelIndex = labelIndex;
  }

  public Dataset(Index<F> featureIndex, Index<L> labelIndex) {
    this(10, featureIndex, labelIndex);
  }


  /**
   * Constructor that fully specifies a Dataset.  Needed this for MulticlassDataset.
   */
  public Dataset(Index<L> labelIndex, int[] labels, Index<F> featureIndex, int[][] data) {
    this (labelIndex, labels, featureIndex, data, data.length);
  }

  /**
   * Constructor that fully specifies a Dataset.  Needed this for MulticlassDataset.
   */
  public Dataset(Index<L> labelIndex, int[] labels, Index<F> featureIndex, int[][] data, int size) {
    this.labelIndex = labelIndex;
    this.labels = labels;
    this.featureIndex = featureIndex;
    this.data = data;
    this.size = size;
  }

  /** {@inheritDoc} */
  @Override
  public Pair<GeneralDataset<L, F>, GeneralDataset<L, F>> split(double percentDev) {
    return split(0, (int)(percentDev * size()));
  }

  /** {@inheritDoc} */
  @Override
  public Pair<GeneralDataset<L, F>,GeneralDataset<L, F>> split(int start, int end) {
    int devSize = end - start;
    int trainSize = size() - devSize;

    int[][] devData = new int[devSize][];
    int[] devLabels = new int[devSize];

    int[][] trainData = new int[trainSize][];
    int[] trainLabels = new int[trainSize];

    System.arraycopy(data, start, devData, 0, devSize);
    System.arraycopy(labels, start, devLabels, 0, devSize);

    System.arraycopy(data, 0, trainData, 0, start);
    System.arraycopy(data, end, trainData, start, size()-end);
    System.arraycopy(labels, 0, trainLabels, 0, start);
    System.arraycopy(labels, end, trainLabels, start, size()-end);

    if (this instanceof WeightedDataset<?,?>) {
      float[] trainWeights = new float[trainSize];
      float[] devWeights = new float[devSize];

      WeightedDataset<L, F> w = (WeightedDataset<L, F>)this;

      System.arraycopy(w.weights, start, devWeights, 0, devSize);
      System.arraycopy(w.weights, 0, trainWeights, 0, start);
      System.arraycopy(w.weights, end, trainWeights, start, size()-end);

      WeightedDataset<L, F> dev = new WeightedDataset<L, F>(labelIndex, devLabels, featureIndex, devData, devSize, devWeights);
      WeightedDataset<L, F> train = new WeightedDataset<L, F>(labelIndex, trainLabels, featureIndex, trainData, trainSize, trainWeights);

      return new Pair<GeneralDataset<L, F>,GeneralDataset<L, F>>(train, dev);
    }
    Dataset<L, F> dev = new Dataset<L, F>(labelIndex, devLabels, featureIndex, devData, devSize);
    Dataset<L, F> train = new Dataset<L, F>(labelIndex, trainLabels, featureIndex, trainData, trainSize);

    return new Pair<GeneralDataset<L, F>,GeneralDataset<L, F>>(train, dev);
  }


  public Dataset<L, F> getRandomSubDataset(double p, int seed) {
    int newSize = (int)(p * size());
    Set<Integer> indicesToKeep = Generics.newHashSet();
    Random r = new Random(seed);
    int s = size();
    while (indicesToKeep.size() < newSize) {
      indicesToKeep.add(r.nextInt(s));
    }

    int[][] newData = new int[newSize][];
    int[] newLabels = new int[newSize];

    int i = 0;
    for (int j : indicesToKeep) {
      newData[i] = data[j];
      newLabels[i] = labels[j];
      i++;
    }

    return new Dataset<L, F>(labelIndex, newLabels, featureIndex, newData);
  }

  @Override
  public double[][] getValuesArray() {
    return null;
  }

  /**
   * Constructs a Dataset by reading in a file in SVM light format.
   */
  public static Dataset<String, String> readSVMLightFormat(String filename) {
    return readSVMLightFormat(filename, new HashIndex<String>(), new HashIndex<String>());
  }

  /**
   * Constructs a Dataset by reading in a file in SVM light format.
   * The lines parameter is filled with the lines of the file for further processing
   * (if lines is null, it is assumed no line information is desired)
   */
  public static Dataset<String, String> readSVMLightFormat(String filename, List<String> lines) {
    return readSVMLightFormat(filename, new HashIndex<String>(), new HashIndex<String>(), lines);
  }

  /**
   * Constructs a Dataset by reading in a file in SVM light format.
   * the created dataset has the same feature and label index as given
   */
  public static Dataset<String, String> readSVMLightFormat(String filename, Index<String> featureIndex, Index<String> labelIndex) {
    return readSVMLightFormat(filename, featureIndex, labelIndex, null);
  }
  /**
   * Constructs a Dataset by reading in a file in SVM light format.
   * the created dataset has the same feature and label index as given
   */
  public static Dataset<String, String> readSVMLightFormat(String filename, Index<String> featureIndex, Index<String> labelIndex, List<String> lines) {
    Dataset<String, String> dataset;
    try {
      dataset = new Dataset<String, String>(10, featureIndex, labelIndex);
      for (String line : ObjectBank.getLineIterator(new File(filename))) {
        if(lines != null)
          lines.add(line);
        dataset.add(svmLightLineToDatum(line));
      }

    } catch (Exception e) {
      throw new RuntimeException(e);
    }
    return dataset;
  }

  private static int line1 = 0;

  public static Datum<String, String> svmLightLineToDatum(String l) {
    line1++;
    l = l.replaceAll("#.*", ""); // remove any trailing comments
    String[] line = l.split("\\s+");
    Collection<String> features = new ArrayList<String>();
    for (int i = 1; i < line.length; i++) {
      String[] f = line[i].split(":");
      if (f.length != 2) {
        System.err.println("Dataset error: line " + line1);
      }
      int val = (int) Double.parseDouble(f[1]);
      for (int j = 0; j < val; j++) {
        features.add(f[0]);
      }
    }
    features.add(String.valueOf(Integer.MAX_VALUE));  // a constant feature for a class
    Datum<String, String> d = new BasicDatum<String, String>(features, line[0]);
    return d;
  }

  /**
   *  Get Number of datums a given feature appears in.
   */
  public Counter<F> getFeatureCounter()
  {
    Counter<F> featureCounts = new ClassicCounter<F>();
    for (int i=0; i < this.size(); i++)
    {
      BasicDatum<L, F> datum = (BasicDatum<L, F>) getDatum(i);
      Set<F> featureSet   = Generics.newHashSet(datum.asFeatures());
      for (F key : featureSet) {
        featureCounts.incrementCount(key, 1.0);
      }
    }
    return featureCounts;
  }

  /**
   * Method to convert features from counts to L1-normalized TFIDF based features
   * @param datum with a collection of features.
   * @param featureDocCounts a counter of doc-count for each feature.
   * @return RVFDatum with l1-normalized tf-idf features.
   */
  public RVFDatum<L,F> getL1NormalizedTFIDFDatum(Datum<L,F> datum,Counter<F> featureDocCounts){
      Counter<F> tfidfFeatures = new ClassicCounter<F>();
      for(F feature : datum.asFeatures()){
        if(featureDocCounts.containsKey(feature))
          tfidfFeatures.incrementCount(feature,1.0);
      }
      double l1norm = 0;
      for(F feature: tfidfFeatures.keySet()){
        double idf = Math.log(((double)(this.size()+1))/(featureDocCounts.getCount(feature)+0.5));
        double tf = tfidfFeatures.getCount(feature);
        tfidfFeatures.setCount(feature, tf*idf);
        l1norm += tf*idf;
      }
      for(F feature: tfidfFeatures.keySet()){
        double tfidf = tfidfFeatures.getCount(feature);
        tfidfFeatures.setCount(feature, tfidf/l1norm);
      }
      RVFDatum<L,F> rvfDatum = new RVFDatum<L,F>(tfidfFeatures,datum.label());
      return rvfDatum;
  }

  /**
   * Method to convert this dataset to RVFDataset using L1-normalized TF-IDF features
   * @return RVFDataset
   */
  public RVFDataset<L,F> getL1NormalizedTFIDFDataset(){
    RVFDataset<L,F> rvfDataset = new RVFDataset<L,F>(this.size(),this.featureIndex,this.labelIndex);
    Counter<F> featureDocCounts = getFeatureCounter();
    for(int i = 0; i < this.size(); i++){
      Datum<L,F> datum = this.getDatum(i);
      RVFDatum<L,F> rvfDatum = getL1NormalizedTFIDFDatum(datum,featureDocCounts);
      rvfDataset.add(rvfDatum);
    }
    return rvfDataset;
  }

  @Override
  public void add(Datum<L, F> d) {
    add(d.asFeatures(), d.label());
  }

  public void add(Collection<F> features, L label) {
    add(features, label, true);
  }

  public void add(Collection<F> features, L label, boolean addNewFeatures) {
    ensureSize();
    addLabel(label);
    addFeatures(features, addNewFeatures);
    size++;
  }

  /**
   * Adds a datums defined by feature indices and label index
   * Careful with this one! Make sure that all indices are valid!
   * @param features
   * @param label
   */
  public void add(int [] features, int label) {
    ensureSize();
    addLabelIndex(label);
    addFeatureIndices(features);
    size++;
  }

  protected void ensureSize() {
    if (labels.length == size) {
      int[] newLabels = new int[size * 2];
      System.arraycopy(labels, 0, newLabels, 0, size);
      labels = newLabels;
      int[][] newData = new int[size * 2][];
      System.arraycopy(data, 0, newData, 0, size);
      data = newData;
    }
  }

  protected void addLabel(L label) {
    labelIndex.add(label);
    labels[size] = labelIndex.indexOf(label);
  }

  protected void addLabelIndex(int label) {
    labels[size] = label;
  }

  protected void addFeatures(Collection<F> features) {
    addFeatures(features, true);
  }

  protected void addFeatures(Collection<F> features, boolean addNewFeatures) {
    int[] intFeatures = new int[features.size()];
    int j = 0;
    for (F feature : features) {
      if(addNewFeatures) featureIndex.add(feature);
      int index = featureIndex.indexOf(feature);
      if (index >= 0) {
        intFeatures[j] = featureIndex.indexOf(feature);
        j++;
      }
    }
    data[size] = new int[j];
    System.arraycopy(intFeatures, 0, data[size], 0, j);
  }

  protected void addFeatureIndices(int [] features) {
    data[size] = features;
  }

  @Override
  protected final void initialize(int numDatums) {
    labelIndex = new HashIndex<L>();
    featureIndex = new HashIndex<F>();
    labels = new int[numDatums];
    data = new int[numDatums][];
    size = 0;
  }

  /**
   * @return the index-ed datum
   */
  @Override
  public Datum<L, F> getDatum(int index) {
    return new BasicDatum<L, F>(featureIndex.objects(data[index]), labelIndex.get(labels[index]));
  }

  /**
   * @return the index-ed datum
   */
  @Override
  public RVFDatum<L, F> getRVFDatum(int index) {
    ClassicCounter<F> c = new ClassicCounter<F>();
    for (F key : featureIndex.objects(data[index])) {
      c.incrementCount(key);
    }
    return new RVFDatum<L, F>(c, labelIndex.get(labels[index]));
  }

  /**
   * Prints some summary statistics to stderr for the Dataset.
   */
  @Override
  public void summaryStatistics() {
    System.err.println(toSummaryStatistics());
  }

  /** A String that is multiple lines of text giving summary statistics.
   *  (It does not end with a newline, though.)
   *
   *  @return A textual summary of the Dataset
   */
  public String toSummaryStatistics() {
    StringBuilder sb = new StringBuilder();
    sb.append("numDatums: ").append(size).append('\n');
    sb.append("numLabels: ").append(labelIndex.size()).append(" [");
    Iterator<L> iter = labelIndex.iterator();
    while (iter.hasNext()) {
      sb.append(iter.next());
      if (iter.hasNext()) {
        sb.append(", ");
      }
    }
    sb.append("]\n");
    sb.append("numFeatures (Phi(X) types): ").append(featureIndex.size()).append(" [");
    int sz = Math.min(5, featureIndex.size());
    for (int i = 0; i < sz; i++) {
      if (i > 0) {
        sb.append(", ");
      }
      sb.append(featureIndex.get(i));
    }
    if (sz < featureIndex.size()) {
      sb.append(", ...");
    }
    sb.append(']');
    return sb.toString();
  }


  /**
   * Applies feature count thresholds to the Dataset.
   * Only features that match pattern_i and occur at
   * least threshold_i times (for some i) are kept.
   *
   * @param thresholds a list of pattern, threshold pairs
   */
  public void applyFeatureCountThreshold(List<Pair<Pattern, Integer>> thresholds) {

    // get feature counts
    float[] counts = getFeatureCounts();

    // build a new featureIndex
    Index<F> newFeatureIndex = new HashIndex<F>();
    LOOP:
    for (F f : featureIndex) {
      for (Pair<Pattern, Integer> threshold : thresholds) {
        Pattern p = threshold.first();
        Matcher m = p.matcher(f.toString());
        if (m.matches()) {
          if (counts[featureIndex.indexOf(f)] >= threshold.second) {
            newFeatureIndex.add(f);
          }
          continue LOOP;
        }
      }
      // we only get here if it didn't match anything on the list
      newFeatureIndex.add(f);
    }

    counts = null;

    int[] featMap = new int[featureIndex.size()];
    for (int i = 0; i < featMap.length; i++) {
      featMap[i] = newFeatureIndex.indexOf(featureIndex.get(i));
    }

    featureIndex = null;

    for (int i = 0; i < size; i++) {
      List<Integer> featList = new ArrayList<Integer>(data[i].length);
      for (int j = 0; j < data[i].length; j++) {
        if (featMap[data[i][j]] >= 0) {
          featList.add(featMap[data[i][j]]);
        }
      }
      data[i] = new int[featList.size()];
      for (int j = 0; j < data[i].length; j++) {
        data[i][j] = featList.get(j);
      }
    }

    featureIndex = newFeatureIndex;
  }


  /**
   * prints the full feature matrix in tab-delimited form.  These can be BIG
   * matrices, so be careful!
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
          pw.print(sep + '1');
        } else {
          pw.print(sep + '0');
        }
      }
    }
  }

  /** {@inheritDoc} */
  @Override
  public void printSparseFeatureMatrix() {
    printSparseFeatureMatrix(new PrintWriter(System.out, true));
  }

  /** {@inheritDoc} */
  @Override
  public void printSparseFeatureMatrix(PrintWriter pw) {
    String sep = "\t";
    for (int i = 0; i < size; i++) {
      pw.print(labelIndex.get(labels[i]));
      int[] datum = data[i];
      for (int j : datum) {
        pw.print(sep + featureIndex.get(j));
      }
      pw.println();
    }
  }


  public void changeLabelIndex(Index<L> newLabelIndex) {

    labels = trimToSize(labels);

    for (int i = 0; i < labels.length; i++) {
      labels[i] = newLabelIndex.indexOf(labelIndex.get(labels[i]));
    }
    labelIndex = newLabelIndex;
  }

  public void changeFeatureIndex(Index<F> newFeatureIndex) {

    data = trimToSize(data);
    labels = trimToSize(labels);

    int[][] newData = new int[data.length][];
    for (int i = 0; i < data.length; i++) {
      int[] newD = new int[data[i].length];
      int k = 0;
      for (int j = 0; j < data[i].length; j++) {
        int newIndex = newFeatureIndex.indexOf(featureIndex.get(data[i][j]));
        if (newIndex >= 0) {
          newD[k++] = newIndex;
        }
      }
      newData[i] = new int[k];
      System.arraycopy(newD, 0, newData[i], 0, k);
    }
    data = newData;
    featureIndex = newFeatureIndex;
  }

  public void selectFeaturesBinaryInformationGain(int numFeatures) {
    double[] scores = getInformationGains();
    selectFeatures(numFeatures,scores);
  }

  /**
   * Generic method to select features based on the feature scores vector provided as an argument.
   * @param numFeatures number of features to be selected.
   * @param scores a vector of size total number of features in the data.
   */
  public void selectFeatures(int numFeatures, double[] scores) {

    List<ScoredObject<F>> scoredFeatures = new ArrayList<ScoredObject<F>>();

    for (int i = 0; i < scores.length; i++) {
      scoredFeatures.add(new ScoredObject<F>(featureIndex.get(i), scores[i]));
    }

    Collections.sort(scoredFeatures, ScoredComparator.DESCENDING_COMPARATOR);
    Index<F> newFeatureIndex = new HashIndex<F>();
    for (int i = 0; i < scoredFeatures.size() && i < numFeatures; i++) {
      newFeatureIndex.add(scoredFeatures.get(i).object());
      //System.err.println(scoredFeatures.get(i));
    }

    for (int i = 0; i < size; i++) {
      int[] newData = new int[data[i].length];
      int curIndex = 0;
      for (int j = 0; j < data[i].length; j++) {
        int index;
        if ((index = newFeatureIndex.indexOf(featureIndex.get(data[i][j]))) != -1) {
          newData[curIndex++] = index;
        }
      }
      int[] newDataTrimmed = new int[curIndex];
      System.arraycopy(newData, 0, newDataTrimmed, 0, curIndex);
      data[i] = newDataTrimmed;
    }
    featureIndex = newFeatureIndex;
  }


  public double[] getInformationGains() {

//    assert size > 0;
//    data = trimToSize(data);  // Don't need to trim to size, and trimming is dangerous the dataset is empty (you can't add to it thereafter)
    labels = trimToSize(labels);

    // counts the number of times word X is present
    ClassicCounter<F> featureCounter = new ClassicCounter<F>();

    // counts the number of time a document has label Y
    ClassicCounter<L> labelCounter = new ClassicCounter<L>();

    // counts the number of times the document has label Y given word X is present
    TwoDimensionalCounter<F,L> condCounter = new TwoDimensionalCounter<F,L>();

    for (int i = 0; i < labels.length; i++) {
      labelCounter.incrementCount(labelIndex.get(labels[i]));

      // convert the document to binary feature representation
      boolean[] doc = new boolean[featureIndex.size()];
      //System.err.println(i);
      for (int j = 0; j < data[i].length; j++) {
        doc[data[i][j]] = true;
      }

      for (int j = 0; j < doc.length; j++) {
        if (doc[j]) {
          featureCounter.incrementCount(featureIndex.get(j));
          condCounter.incrementCount(featureIndex.get(j), labelIndex.get(labels[i]), 1.0);
        }
      }
    }

    double entropy = 0.0;
    for (int i = 0; i < labelIndex.size(); i++) {
      double labelCount = labelCounter.getCount(labelIndex.get(i));
      double p = labelCount / size();
      entropy -= p * (Math.log(p) / Math.log(2));
    }

    double[] ig = new double[featureIndex.size()];
    Arrays.fill(ig, entropy);

    for (int i = 0; i < featureIndex.size(); i++) {
      F feature = featureIndex.get(i);

      double featureCount = featureCounter.getCount(feature);
      double notFeatureCount = size() - featureCount;

      double pFeature =  featureCount / size();
      double pNotFeature = (1.0 - pFeature);

      if (featureCount == 0) { ig[i] = 0; continue; }
      if (notFeatureCount == 0) { ig[i] = 0; continue; }

      double sumFeature = 0.0;
      double sumNotFeature = 0.0;

      for (int j = 0; j < labelIndex.size(); j++) {
        L label = labelIndex.get(j);

        double featureLabelCount = condCounter.getCount(feature, label);
        double notFeatureLabelCount = size() - featureLabelCount;

        // yes, these dont sum to 1.  that is correct.
        // one is the prob of the label, given that the
        // feature is present, and the other is the prob
        // of the label given that the feature is absent
        double p = featureLabelCount / featureCount;
        double pNot = notFeatureLabelCount / notFeatureCount;

        if (featureLabelCount != 0) {
          sumFeature += p * (Math.log(p) / Math.log(2));
        }

        if (notFeatureLabelCount != 0) {
          sumNotFeature += pNot * (Math.log(pNot) / Math.log(2));
        }
        //System.out.println(pNot+" "+(Math.log(pNot)/Math.log(2)));

      }

        //System.err.println(pFeature+" * "+sumFeature+" = +"+);
        //System.err.println("^ "+pNotFeature+" "+sumNotFeature);

      ig[i] += pFeature*sumFeature + pNotFeature*sumNotFeature;
      /* earlier the line above used to be: ig[i] = pFeature*sumFeature + pNotFeature*sumNotFeature;
       * This completely ignored the entropy term computed above. So added the "+=" to take that into account.
       * -Ramesh (nmramesh@cs.stanford.edu)
       */
    }
    return ig;
  }

  public void updateLabels(int[] labels) {
    if (labels.length != size())
      throw new IllegalArgumentException(
          "size of labels array does not match dataset size");

    this.labels = labels;
  }

  @Override
  public String toString() {
    return "Dataset of size " + size;
  }

  public String toSummaryString() {
    StringWriter sw = new StringWriter();
    PrintWriter pw = new PrintWriter(sw);
    pw.println("Number of data points: " + size());
    pw.println("Number of active feature tokens: " + numFeatureTokens());
    pw.println("Number of active feature types:" + numFeatureTypes());
    return pw.toString();
  }

  /**
   * Need to sort the counter by feature keys and dump it
   *
   */
  public static void printSVMLightFormat(PrintWriter pw, ClassicCounter<Integer> c, int classNo) {
    Integer[] features = c.keySet().toArray(new Integer[c.keySet().size()]);
    Arrays.sort(features);
    StringBuilder sb = new StringBuilder();
    sb.append(classNo);
    sb.append(' ');
    for (int f: features) {
      sb.append(f + 1).append(':').append(c.getCount(f)).append(' ');
    }
    pw.println(sb.toString());
  }

}
