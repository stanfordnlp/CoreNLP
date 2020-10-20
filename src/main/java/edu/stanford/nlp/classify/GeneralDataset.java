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
 * The purpose of this interface is to unify {@link Dataset} and {@link RVFDataset}.
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

  public Index<L> labelIndex;
  public Index<F> featureIndex;

  protected int[] labels;
  protected int[][] data;

  protected int size;

  public GeneralDataset() { }

  public Index<L> labelIndex() { return labelIndex; }

  public Index<F> featureIndex() { return featureIndex; }

  public int numFeatures() { return featureIndex.size(); }

  public int numClasses() { return labelIndex.size(); }

  public int[] getLabelsArray() {
    labels = trimToSize(labels);
    return labels;
  }

  public int[][] getDataArray() {
    data = trimToSize(data);
    return data;
  }

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


  public abstract RVFDatum<L, F> getRVFDatum(int index);

  public abstract Datum<L,F> getDatum(int index);


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
   * occur greater than <i>k</i> times are expunged.
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
   * returns the number of feature tokens in the Dataset.
   */
  public int numFeatureTokens() {
    int x = 0;
    for (int i = 0, m = size; i < m; i++) {
      x += data[i].length;
    }
    return x;
  }

  /**
   * returns the number of distinct feature types in the Dataset.
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
   *  @param fold The number of this fold (must be between 0 and (numFolds - 1)
   *  @param numFolds The number of folds to divide the data into (must be greater than or equal to the
   *                  size of the data set)
   *  @return A Pair of data sets, the first being roughly (numFolds-1)/numFolds of the data items
   *         (for use as training data_, and the second being 1/numFolds of the data, taken from the
   *         fold<sup>th</sup> part of the data (for use as devTest data)
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
   */
  public int size() {
    return size;
  }

  protected void trimData() {
    data = trimToSize(data);
  }

  protected void trimLabels() {
    labels = trimToSize(labels);
  }

  protected int[] trimToSize(int[] i) {
    int[] newI = new int[size];
    synchronized (System.class) {
      System.arraycopy(i, 0, newI, 0, size);
    }
    return newI;
  }

  protected int[][] trimToSize(int[][] i) {
    int[][] newI = new int[size][];
    synchronized (System.class) {
      System.arraycopy(i, 0, newI, 0, size);
    }
    return newI;
  }

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
   * Randomizes the data array in place.
   * Note: this cannot change the values array or the datum weights,
   * so redefine this for RVFDataset and WeightedDataset!
   * This uses the Fisher-Yates (or Durstenfeld-Knuth) shuffle, which is unbiased.
   * The same algorithm is used by shuffle() in j.u.Collections, and so you should get compatible
   * results if using it on a Collection with the same seed (as of JDK1.7, at least).
   *
   * @param randomSeed A seed for the Random object (allows you to reproduce the same ordering)
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
   *
   * @param dataset
   * @return a new GeneralDataset whose features and ids map exactly to those of this GeneralDataset.
   * Useful when two Datasets are created independently and one wants to train a model on one dataset and test on the other. -Ramesh.
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
   *
   * @param dataset
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
   * Dumps the Dataset as a training/test file for SVMLight. <br>
   * class [fno:val]+
   * The features must occur in consecutive order.
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

  // todo: Fix javadoc, have unit tested
  /**
   * Print SVM Light Format file.
   *
   * The following comments are no longer applicable because I am
   * now printing out the exact labelID for each example. -Ramesh (nmramesh@cs.stanford.edu) 12/17/2009.
   *
   * If the Dataset has more than 2 classes, then it
   * prints using the label index (+1) (for svm_struct).  If it is 2 classes, then the labelIndex.get(0)
   * is mapped to +1 and labelIndex.get(1) is mapped to -1 (for svm_light).
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
   * prints a sparse feature matrix representation of the Dataset.  Prints the actual
   * {@link Object#toString()} representations of features.
   */
  public abstract void printSparseFeatureMatrix(PrintWriter pw);

}
