package edu.stanford.nlp.classify;

import java.io.PrintWriter;
import java.io.Serializable;
import java.util.*;

import edu.stanford.nlp.ling.BasicDatum;
import edu.stanford.nlp.ling.Datum;
import edu.stanford.nlp.ling.RVFDatum;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.util.Generics;
import edu.stanford.nlp.util.HashIndex;
import edu.stanford.nlp.util.Index;
import edu.stanford.nlp.util.Pair;

/**
 * The purpose of this abstract class is to unify {@link Dataset} and {@link RVFDataset}.
 * Labels and features are stored as integer indices into {@link #labelIndex}
 * and {@link #featureIndex}.
 * <p>
 * Note: Despite these being value classes, at present there are no equals() and hashCode() methods
 * defined so you just get the default ones from Object, so different objects aren't equal.
 * </p>
 *
 * @author Kristina Toutanova (kristina@cs.stanford.edu)
 * @author Anna Rafferty (various refactoring with subclasses)
 * @author Sarah Spikes (sdspikes@cs.stanford.edu) (Templatization)
 * @author Ramesh Nallapati (nmramesh@cs.stanford.edu)
 * (added an abstract method getDatum, July 17th, 2008)
 *
 * @param <L> The type of the labels in the Dataset
 * @param <F> The type of the features in the Dataset
 */
public abstract class GeneralDataset<L, F>  implements Serializable, Iterable<RVFDatum<L, F>> {

  private static final long serialVersionUID = 19157757130054829L;

  /** Index of the labels in this dataset. */
  public Index<L> labelIndex;
  /** Index of the features in this dataset. */
  public Index<F> featureIndex;

  /** The label index of each datum. May be longer than {@link #size}. */
  protected int[] labels;
  /** The feature indices of each datum. May be longer than {@link #size}. */
  protected int[][] data;

  /** The number of datums in the dataset. */
  protected int size;

  /** Constructor for subclasses, which are responsible for initializing the fields. */
  public GeneralDataset() { }

  /**
   * Returns the index of the labels in this dataset.
   *
   * @return The label index
   */
  public Index<L> labelIndex() { return labelIndex; }

  /**
   * Returns the index of the features in this dataset.
   *
   * @return The feature index
   */
  public Index<F> featureIndex() { return featureIndex; }

  /**
   * Returns the number of distinct features in the feature index.
   *
   * @return The size of the feature index
   */
  public int numFeatures() { return featureIndex.size(); }

  /**
   * Returns the number of distinct labels in the label index.
   *
   * @return The size of the label index
   */
  public int numClasses() { return labelIndex.size(); }

  /**
   * Returns the label index of each datum, first trimming the
   * internal array to the size of the dataset.
   *
   * @return The labels array itself, not a copy
   */
  public int[] getLabelsArray() {
    labels = trimToSize(labels);
    return labels;
  }

  /**
   * Returns the feature indices of each datum, first trimming the
   * internal array to the size of the dataset.
   *
   * @return The data array itself, not a copy
   */
  public int[][] getDataArray() {
    data = trimToSize(data);
    return data;
  }

  /**
   * Returns the feature values of each datum, parallel to
   * {@link #getDataArray()}.
   *
   * @return The values array, or null if the features are binary
   */
  public abstract double[][] getValuesArray();

  /**
   * Resets the Dataset so that it is empty and ready to collect data.
   */
  public void clear() {
    clear(10);
  }

  /**
   * Resets the Dataset so that it is empty and ready to collect data.
   * @param numDatums initial capacity of dataset
   */
  public void clear(int numDatums) {
    initialize(numDatums);
  }

  /**
   * This method takes care of resetting values of the dataset
   * such that it is empty with an initial capacity of numDatums.
   * Should be accessed only by appropriate methods within the class,
   * such as clear(), which take care of other parts of the emptying of data.
   *
   * @param numDatums initial capacity of dataset
   */
  protected abstract void initialize(int numDatums);


  /**
   * Returns a datum as an {@link RVFDatum}, with feature values.
   *
   * @param index The position of the datum in the dataset
   * @return A new datum built from the stored indices
   */
  public abstract RVFDatum<L, F> getRVFDatum(int index);

  /**
   * Returns a datum.
   *
   * @param index The position of the datum in the dataset
   * @return A new datum built from the stored indices
   */
  public abstract Datum<L,F> getDatum(int index);


  /**
   * Adds a datum to the dataset, adding its label and features to the
   * indices as needed.
   *
   * @param d The datum to add
   */
  public abstract void add(Datum<L, F> d);

  /**
   * Get the total count (over all data instances) of each feature
   *
   * @return an array containing the counts (indexed by index)
   */
  public float[] getFeatureCounts() {
    float[] counts = new float[featureIndex.size()];
    for (int i = 0, m = size; i < m; i++) {
      for (int j = 0, n = data[i].length; j < n; j++) {
        counts[data[i][j]] += 1.0;
      }
    }
    return counts;
  }

  /**
   * Applies a feature count threshold to the Dataset.  All features that
   * occur fewer than <i>k</i> times are expunged.
   *
   * @param k The minimum count for a feature to be kept
   */
  public void applyFeatureCountThreshold(int k) {
    float[] counts = getFeatureCounts();
    Index<F> newFeatureIndex = new HashIndex<>();

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
      List<Integer> featList = new ArrayList<>(data[i].length);
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
  }

  /**
   * Retains the given features in the Dataset.  All features that
   * do not occur in features are expunged.
   *
   * @param features The features to keep
   */
  public void retainFeatures(Set<F> features) {
    //float[] counts = getFeatureCounts();
    Index<F> newFeatureIndex = new HashIndex<>();

    int[] featMap = new int[featureIndex.size()];
    for (int i = 0; i < featMap.length; i++) {
      F feat = featureIndex.get(i);
      if (features.contains(feat)) {
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
      List<Integer> featList = new ArrayList<>(data[i].length);
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
  }


  /**
   * Applies a max feature count threshold to the Dataset.  All features that
   * occur more than <i>k</i> times are expunged.
   *
   * @param k The maximum count for a feature to be kept
   */
  public void applyFeatureMaxCountThreshold(int k) {
    float[] counts = getFeatureCounts();
    HashIndex<F> newFeatureIndex = new HashIndex<>();

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
      List<Integer> featList = new ArrayList<>(data[i].length);
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
  }


  /**
   * Returns the number of feature tokens in the Dataset.
   *
   * @return The total number of features over all datums
   */
  public int numFeatureTokens() {
    int x = 0;
    for (int i = 0, m = size; i < m; i++) {
      x += data[i].length;
    }
    return x;
  }

  /**
   * Returns the number of distinct feature types in the Dataset.
   *
   * @return The size of the feature index
   */
  public int numFeatureTypes() {
    return featureIndex.size();
  }



  /**
   * Adds all Datums in the given collection of data to this dataset
   * @param data collection of datums you would like to add to the dataset
   */
  public void addAll(Iterable<? extends Datum<L,F>> data) {
    for (Datum<L, F> d : data) {
      add(d);
    }
  }

  /** Divide out a (devtest) split of the dataset versus the rest of it (as a training set).
   *
   *  @param start Begin devtest with this index (inclusive)
   *  @param end End devtest before this index (exclusive)
   *  @return A Pair of data sets, the first being the remainder of size this.size() - (end-start)
   *          and the second being of size (end-start)
   */
  public abstract Pair<GeneralDataset<L, F>, GeneralDataset<L, F>> split (int start, int end);

  /** Divide out a (devtest) split from the start of the dataset and the rest of it (as a training set).
   *
   *  @param fractionSplit The first fractionSplit of datums (rounded down) will be the second split
   *  @return A Pair of data sets, the first being the remainder of size ceiling(this.size() * (1-p)) drawn
   *          from the end of the dataset and the second of size floor(this.size() * p) drawn from the
   *          start of the dataset.
   */
  public abstract Pair<GeneralDataset<L, F>, GeneralDataset<L, F>> split (double fractionSplit);

  /** Divide out a (devtest) split of the dataset versus the rest of it (as a training set).
   *
   *  @param fold The number of this fold (must be between 0 and numFolds - 1)
   *  @param numFolds The number of folds to divide the data into (must be at least 2 and no
   *                  greater than the size of the data set)
   *  @return A Pair of data sets, the first being roughly (numFolds-1)/numFolds of the data items
   *         (for use as training data), and the second being 1/numFolds of the data, taken from the
   *         fold<sup>th</sup> part of the data (for use as devTest data).  The last fold also
   *         gets any items left over when the size is not divisible by numFolds.
   *  @throws IllegalArgumentException If fold or numFolds is out of range
   */
  public Pair<GeneralDataset<L, F>, GeneralDataset<L, F>> splitOutFold(int fold, int numFolds) {
    if (numFolds < 2 || numFolds > size() || fold < 0 || fold >= numFolds) {
      throw new IllegalArgumentException("Illegal request for fold " + fold + " of " + numFolds +
              " on data set of size " + size());
    }
    int normalFoldSize = size()/numFolds;
    int start = normalFoldSize * fold;
    int end = start + normalFoldSize;
    if (fold == (numFolds - 1)) {
      end = size();
    }
    return split(start, end);
  }

  /**
   * Returns the number of examples ({@link Datum}s) in the Dataset.
   *
   * @return The number of datums
   */
  public int size() {
    return size;
  }

  /** Trims the data array to the size of the dataset. */
  protected void trimData() {
    data = trimToSize(data);
  }

  /** Trims the labels array to the size of the dataset. */
  protected void trimLabels() {
    labels = trimToSize(labels);
  }

  /**
   * Copies the first {@link #size} elements of an array into a new array.
   *
   * @param i The array to trim
   * @return A new array of length {@link #size}
   */
  protected int[] trimToSize(int[] i) {
    int[] newI = new int[size];
    synchronized (System.class) {
      System.arraycopy(i, 0, newI, 0, size);
    }
    return newI;
  }

  /**
   * Copies the first {@link #size} elements of an array into a new array.
   * The inner arrays are shared, not copied.
   *
   * @param i The array to trim
   * @return A new array of length {@link #size}
   */
  protected int[][] trimToSize(int[][] i) {
    int[][] newI = new int[size][];
    synchronized (System.class) {
      System.arraycopy(i, 0, newI, 0, size);
    }
    return newI;
  }

  /**
   * Copies the first {@link #size} elements of an array into a new array.
   * The inner arrays are shared, not copied.
   *
   * @param i The array to trim
   * @return A new array of length {@link #size}
   */
  protected double[][] trimToSize(double[][] i) {
    double[][] newI = new double[size][];
    synchronized (System.class) {
      System.arraycopy(i, 0, newI, 0, size);
    }
    return newI;
  }

  /**
   * Randomizes the data array in place.
   * Note: this cannot change the values array or the datum weights,
   * so redefine this for RVFDataset and WeightedDataset!
   * This uses the Fisher-Yates (or Durstenfeld-Knuth) shuffle, which is unbiased.
   * The same algorithm is used by shuffle() in j.u.Collections, and so you should get compatible
   * results if using it on a Collection with the same seed (as of JDK1.7, at least).
   *
   * @param randomSeed A seed for the Random object (allows you to reproduce the same ordering)
   */
  // todo: Probably should be renamed 'shuffle' to be consistent with Java Collections API
  public void randomize(long randomSeed) {
    Random rand = new Random(randomSeed);
    for (int j = size - 1; j > 0; j--) {
      // swap each item with some lower numbered item
      int randIndex = rand.nextInt(j);

      int[] tmp = data[randIndex];
      data[randIndex] = data[j];
      data[j] = tmp;

      int tmpl = labels[randIndex];
      labels[randIndex] = labels[j];
      labels[j] = tmpl;
    }
  }

  /**
   * Randomizes the data array in place, applying the same permutation
   * to a parallel list of side information.
   * Note: this cannot change the values array or the datum weights,
   * so redefine this for RVFDataset and WeightedDataset!
   * This uses the Fisher-Yates (or Durstenfeld-Knuth) shuffle, which is unbiased.
   * The same algorithm is used by shuffle() in j.u.Collections, and so you should get compatible
   * results if using it on a Collection with the same seed (as of JDK1.7, at least).
   *
   * @param <E> The type of the side information
   * @param randomSeed A seed for the Random object (allows you to reproduce the same ordering)
   * @param sideInformation A list parallel to the data, shuffled in place along with it
   * @throws IllegalArgumentException If sideInformation is not the same size as the dataset
   */
  public <E> void shuffleWithSideInformation(long randomSeed, List<E> sideInformation) {
    if (size != sideInformation.size()) {
      throw new IllegalArgumentException("shuffleWithSideInformation: sideInformation not of same size as Dataset");
    }
    Random rand = new Random(randomSeed);
    for (int j = size - 1; j > 0; j--) {
      // swap each item with some lower numbered item
      int randIndex = rand.nextInt(j);

      int[] tmp = data[randIndex];
      data[randIndex] = data[j];
      data[j] = tmp;

      int tmpl = labels[randIndex];
      labels[randIndex] = labels[j];
      labels[j] = tmpl;

      E tmpE = sideInformation.get(randIndex);
      sideInformation.set(randIndex, sideInformation.get(j));
      sideInformation.set(j, tmpE);
    }
  }

  /**
   * Draws a random sample of the datums into a new dataset of the same
   * kind, which gets its own label and feature indices.
   *
   * @param randomSeed A seed for the Random object (allows you to reproduce the same sample)
   * @param sampleFrac The size of the sample as a fraction of this dataset (rounded down)
   * @param sampleWithReplacement Whether a datum may be drawn more than once
   * @return The sampled dataset
   * @throws RuntimeException If this is not a {@link Dataset} or {@link RVFDataset}
   */
  public GeneralDataset<L,F> sampleDataset(long randomSeed, double sampleFrac, boolean sampleWithReplacement) {
    int sampleSize = (int)(this.size()*sampleFrac);
    Random rand = new Random(randomSeed);
    GeneralDataset<L,F> subset;
    if (this instanceof RVFDataset) {
      subset = new RVFDataset<>();
    } else if (this instanceof Dataset) {
      subset = new Dataset<>();
    }
    else {
      throw new RuntimeException("Can't handle this type of GeneralDataset.");
    }
    if (sampleWithReplacement) {
      for(int i = 0; i < sampleSize; i++){
        int datumNum = rand.nextInt(this.size());
        subset.add(this.getDatum(datumNum));
      }
    } else {
      Set<Integer> indicedSampled = Generics.newHashSet();
      while (subset.size() < sampleSize) {
        int datumNum = rand.nextInt(this.size());
        if (!indicedSampled.contains(datumNum)) {
          subset.add(this.getDatum(datumNum));
          indicedSampled.add(datumNum);
        }
      }
    }
    return subset;
  }

  /**
   * Print some statistics summarizing the dataset
   *
   */
  public abstract void summaryStatistics();

  /**
   * Returns an iterator over the class labels of the Dataset
   *
   * @return An iterator over the class labels of the Dataset
   */
  public Iterator<L> labelIterator() {
    return labelIndex.iterator();
  }


  /**
   * Copies another dataset using this dataset's feature and label indices.
   * Useful when two Datasets are created independently and one wants to train a model on one dataset and test on the other. -Ramesh.
   * The indices are locked while copying, so no new features or labels
   * are added to them.
   *
   * @param dataset The dataset to copy
   * @return a new GeneralDataset whose features and ids map exactly to those of this GeneralDataset.
   */
  public GeneralDataset<L,F> mapDataset(GeneralDataset<L,F> dataset){
    GeneralDataset<L,F> newDataset;
    if(dataset instanceof RVFDataset)
      newDataset = new RVFDataset<>(this.featureIndex, this.labelIndex);
    else newDataset = new Dataset<>(this.featureIndex, this.labelIndex);
    this.featureIndex.lock();
    this.labelIndex.lock();
    //System.out.println("inside mapDataset: dataset size:"+dataset.size());
    for(int i = 0; i < dataset.size(); i++)
      //System.out.println("inside mapDataset: adding datum number"+i);
      newDataset.add(dataset.getDatum(i));

    //System.out.println("old Dataset stats: numData:"+dataset.size()+" numfeatures:"+dataset.featureIndex().size()+" numlabels:"+dataset.labelIndex.size());
    //System.out.println("new Dataset stats: numData:"+newDataset.size()+" numfeatures:"+newDataset.featureIndex().size()+" numlabels:"+newDataset.labelIndex.size());
    //System.out.println("this dataset stats: numData:"+size()+" numfeatures:"+featureIndex().size()+" numlabels:"+labelIndex.size());

    this.featureIndex.unlock();
    this.labelIndex.unlock();
    return newDataset;
  }

  /**
   * Makes a copy of a datum with its label mapped to a new label type.
   *
   * @param <L> The original label type
   * @param <L2> The new label type
   * @param <F> The feature type
   * @param d The datum to copy
   * @param labelMapping Map from the original labels to the new labels
   * @param defaultLabel The new label for any label not in {@code labelMapping}
   * @return An {@link RVFDatum} if {@code d} is one, otherwise a {@link BasicDatum}
   */
  public static <L,L2,F> Datum<L2,F> mapDatum(Datum<L,F> d, Map<L,L2> labelMapping, L2 defaultLabel) {
    // TODO: How to copy datum?
    L2 newLabel = labelMapping.get(d.label());
    if (newLabel == null) {
      newLabel = defaultLabel;
    }

    if (d instanceof RVFDatum) {
      return new RVFDatum<>(((RVFDatum<L, F>) d).asFeaturesCounter(), newLabel);
    } else {
      return new BasicDatum<>(d.asFeatures(), newLabel);
    }
  }


  /**
   * Copies another dataset using this dataset's feature index, with its
   * labels mapped to a new label type (see {@link #mapDatum}).
   *
   * @param <L2> The new label type
   * @param dataset The dataset to copy
   * @param newLabelIndex The label index for the new dataset
   * @param labelMapping Map from the original labels to the new labels
   * @param defaultLabel The new label for any label not in {@code labelMapping}
   * @return a new GeneralDataset whose features and ids map exactly to those of this GeneralDataset. But labels are converted to be another set of labels
   */
  public <L2> GeneralDataset<L2,F> mapDataset(GeneralDataset<L,F> dataset, Index<L2> newLabelIndex, Map<L,L2> labelMapping, L2 defaultLabel)
 {
    GeneralDataset<L2,F> newDataset;
    if(dataset instanceof RVFDataset)
      newDataset = new RVFDataset<>(this.featureIndex, newLabelIndex);
    else newDataset = new Dataset<>(this.featureIndex, newLabelIndex);
    this.featureIndex.lock();
    this.labelIndex.lock();
    //System.out.println("inside mapDataset: dataset size:"+dataset.size());
    for(int i = 0; i < dataset.size(); i++)  {
      //System.out.println("inside mapDataset: adding datum number"+i);
      Datum<L,F> d = dataset.getDatum(i);
      Datum<L2,F> d2 = mapDatum(d, labelMapping, defaultLabel);
      newDataset.add(d2);
    }
    //System.out.println("old Dataset stats: numData:"+dataset.size()+" numfeatures:"+dataset.featureIndex().size()+" numlabels:"+dataset.labelIndex.size());
    //System.out.println("new Dataset stats: numData:"+newDataset.size()+" numfeatures:"+newDataset.featureIndex().size()+" numlabels:"+newDataset.labelIndex.size());
    //System.out.println("this dataset stats: numData:"+size()+" numfeatures:"+featureIndex().size()+" numlabels:"+labelIndex.size());

    this.featureIndex.unlock();
    this.labelIndex.unlock();
    return newDataset;
  }

  /**
   * Dumps the Dataset to stdout as a training/test file for SVMLight.
   *
   * @see #printSVMLightFormat(PrintWriter)
   */
  public void printSVMLightFormat() {
    printSVMLightFormat(new PrintWriter(System.out));
  }

  /**
   * Maps our labels to labels that are compatible with svm_light
   * @return array of strings
   */
  public String[] makeSvmLabelMap() {
    String[] labelMap = new String[numClasses()];
    if (numClasses() > 2) {
      for (int i = 0; i < labelMap.length; i++) {
        labelMap[i] = String.valueOf((i + 1));
      }
    } else {
      labelMap = new String[]{"+1", "-1"};
    }
    return labelMap;
  }

  // todo: have unit tested
  /**
   * Print SVM Light Format file, one datum per line as
   * {@code label fno:val fno:val ...}, with the features sorted by index.
   * Feature numbers are the feature index + 1, since SVMLight feature
   * numbers start at 1.
   *
   * Labels are mapped by {@link #makeSvmLabelMap()}: if the Dataset has more than 2 classes, then it
   * prints using the label index (+1) (for svm_struct).  If it is 2 classes, then the labelIndex.get(0)
   * is mapped to +1 and labelIndex.get(1) is mapped to -1 (for svm_light).
   *
   * @param pw Where to print the dataset
   */

  public void printSVMLightFormat(PrintWriter pw) {
    //assumes each data item has a few features on, and sorts the feature keys while collecting the values in a counter

    // old comment:
    // the following code commented out by Ramesh (nmramesh@cs.stanford.edu) 12/17/2009.
    // why not simply print the exact id of the label instead of mapping to some values??
    // new comment:
    // mihai: we NEED this, because svm_light has special conventions not supported by default by our labels,
    //        e.g., in a multiclass setting it assumes that labels start at 1 whereas our labels start at 0 (08/31/2010)
    String[] labelMap = makeSvmLabelMap();

    for (int i = 0; i < size; i++) {
      RVFDatum<L, F> d = getRVFDatum(i);
      Counter<F> c = d.asFeaturesCounter();
      ClassicCounter<Integer> printC = new ClassicCounter<>();
      for (F f : c.keySet()) {
        printC.setCount(featureIndex.indexOf(f), c.getCount(f));
      }
      Integer[] features = printC.keySet().toArray(new Integer[printC.keySet().size()]);
      Arrays.sort(features);
      StringBuilder sb = new StringBuilder();
      sb.append(labelMap[labels[i]]).append(' ');
      // sb.append(labels[i]).append(' '); // commented out by mihai: labels[i] breaks svm_light conventions!

      /* Old code: assumes that F is Integer....
       *
      for (int f: features) {
        sb.append((f + 1)).append(":").append(c.getCount(f)).append(" ");
      }
       */
      //I think this is what was meant (using printC rather than c), but not sure
      // ~Sarah Spikes (sdspikes@cs.stanford.edu)
      for (int f: features) {
        sb.append((f + 1)).append(':').append(printC.getCount(f)).append(' ');
      }
      pw.println(sb.toString());
    }
  }


  public Iterator<RVFDatum<L, F>> iterator() {
    return new Iterator<RVFDatum<L,F>>() {
      private int id; // = 0;

      public boolean hasNext() {
        return id < size();
      }

      public RVFDatum<L, F> next() {
        if (id >= size()) {
          throw new NoSuchElementException();
        }
        return getRVFDatum(id++);
      }

      public void remove() {
        throw new UnsupportedOperationException();
      }

    };
  }

  /**
   * Counts the datums with each label.
   *
   * @return A counter from each label to its number of datums
   */
  public ClassicCounter<L> numDatumsPerLabel(){
    labels = trimToSize(labels);
    ClassicCounter<L> numDatums = new ClassicCounter<>();
    for(int i : labels){
      numDatums.incrementCount(labelIndex.get(i));
    }
    return numDatums;
  }

  /**
   * Prints the sparse feature matrix using
   * {@link #printSparseFeatureMatrix(PrintWriter)} to {@link System#out
   * System.out}.
   */
  public abstract void printSparseFeatureMatrix();

  /**
   * Prints a sparse feature matrix representation of the Dataset.  Prints the actual
   * {@link Object#toString()} representations of features.
   *
   * @param pw Where to print the matrix
   */
  public abstract void printSparseFeatureMatrix(PrintWriter pw);

}
