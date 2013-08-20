package edu.stanford.nlp.sequences;

import edu.stanford.nlp.util.Index;
import edu.stanford.nlp.util.HashIndex;
import edu.stanford.nlp.io.IOUtils;

import java.util.*;
import java.io.*;

/**
 * This class is a holder for meta-info about a dataset.  It holds things
 * like {@link edu.stanford.nlp.util.HashIndex}es over features and
 * labels.  Multiple Datasets can (and should) point to the same
 * DatasetMetaInfo.
 *
 * @author Jenny Finkel
 */

public class DatasetMetaInfo implements Serializable {

  private static final long serialVersionUID = 3837195914761840458L;

  private HashIndex labelIndex = new HashIndex();
  private HashIndex featureIndex = null;
  private int backgroundIndex;
  public Type2FeatureFactory featureFactory;
  private Clique maxClique = null;
  private Collection<LabeledClique> allowedSequences = null;

  public FeatureMap fm = new FeatureMap(this);

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("DatasetMetaInfo----");
    sb.append("\nlabelIndex: ").append(labelIndex.toString(10));
    sb.append("\nfeatureIndex: ").append(featureIndex.toString(10));
    sb.append("\nbackgroundIndex: ").append(backgroundIndex);
    sb.append("\nfeatureFactory: ").append(featureFactory);
    sb.append("\nmaxClique: ").append(maxClique);
    sb.append("\nallowedSequences: ").append(allowedSequences);
    sb.append("\n-----------------\n");
    return sb.toString();
  }

  public void printAllFeaturesToFile(String filename) {
    // int numFeat = labelIndex.size();
    // StringBuilder buff = new StringBuilder(featureIndex.toStringOneEntryPerLine());
    try {
      BufferedWriter out = new BufferedWriter(new FileWriter(filename));
      out.write(featureIndex.toStringOneEntryPerLine());
      out.close();
    } catch (IOException e) {
      System.out.println("Exception ");
    }
    System.out.println("Wrote feature names to file: " +  filename);
  }

  public DatasetMetaInfo(Type2FeatureFactory featureFactory, Object backgroundLabel) {
    this.featureFactory = featureFactory;
    featureFactory.setDatasetMetaInfo(this);
    labelIndex.add(backgroundLabel);
    backgroundIndex = labelIndex.indexOf(backgroundLabel);

//     System.err.println("1>> "+(featureFactory instanceof FeatureFactoryWrapper));
//     System.err.println("2>> "+(!featureFactory.flags.useObservedFeaturesOnly));
//     System.err.println("3>> "+(featureFactory.flags.featureCountThreshold <= 0));
//     System.err.println("4>> "+(featureFactory.flags.randomizedRatio >= 1.0));
//     System.err.println("5>> "+(featureFactory.flags.featureWeightThreshold <= 0.0));
//     System.err.println("6>> "+(featureFactory.flags.removeTopN <= 0));
//     System.err.println("7>> "+(featureFactory.flags.removeTopNPercent <= 0.0));
//     System.err.println("8>> "+(!featureFactory.flags.useSeenFeaturesOnly));

    if (featureFactory instanceof FeatureFactoryWrapper && !featureFactory.flags.useObservedFeaturesOnly && featureFactory.flags.featureCountThreshold <= 0 && featureFactory.flags.randomizedRatio >= 1.0 && featureFactory.flags.featureWeightThreshold <= 0.0 && featureFactory.flags.removeTopN <= 0 && featureFactory.flags.removeTopNPercent <= 0.0 && !featureFactory.flags.useSeenFeaturesOnly && false) {
      System.err.println("OldStyleFeatureIndex");
      featureIndex = getOldStyleFeatureIndex();
    } else {
      featureIndex = new HashIndex();
    }
    maxClique = featureFactory.getMaxClique();
  }

  public boolean hasOldStyleFeatureIndex() { return (featureIndex instanceof OldStyleFeatureIndex); }

  public void setAllowedSequences (Collection<LabeledClique> allowed) {
    allowedSequences = allowed;
  }

  public void retainAllowed(Collection<LabeledClique> labels) {
    if (allowedSequences != null) {
      labels.retainAll(allowedSequences);
    }
  }

  public boolean isAllowed(LabeledClique lc) {
    return allowedSequences.contains(lc);
  }

  public void setFeatureIndex(HashIndex fi) {
    featureIndex = fi;
  }

  private File featureIndexFile = null;

  public void writeToDisk() {
    try {
      System.err.println("Writing feature index to temporary file.");
      featureIndexFile = IOUtils.writeObjectToTempFile(featureIndex, "featIndex.tmp");
      numFeatures = featureIndex.size();
      featureIndex = null;
    } catch (Exception e) {
      throw new RuntimeException("Error writing Indexes to disk.");
    }
  }

  public void readFromDisk() {
    try {
      System.err.println("Reading temporary feature index file.");
      featureIndex = (HashIndex) IOUtils.readObjectFromFile(featureIndexFile);
      featureIndexFile = null;
    } catch (Exception e) {
      throw new RuntimeException("Error reading Indexes from disk.");
    }
  }

  public Type2FeatureFactory featureFactory() { return featureFactory; }

  /**
   * @return the furthest left any of the cliques let you look.
   */
  public int leftWindow() { return -maxClique.maxLeft(); }

  /**
   * @return the furthest right any of the cliques let you look.
   */
  public int rightWindow() { return maxClique.maxRight(); }

  public Clique getMaxClique() { return maxClique; }

  public int backgroundIndex() { return backgroundIndex; }

  public Object backgroundLabel() { return labelIndex.get(backgroundIndex); }

  // num methods

  private int numFeatures = 0;

  public int numFeatures() {
    if (featureIndex != null) {
      return featureIndex.size();
    } else {
      return numFeatures;
    }
  }
  public int numLabels() { return labelIndex.size(); }

  // add methods

  public void addLabel(Object label) { labelIndex.add(label); }
  public void addFeature(Object feature) { featureIndex.add(feature); }
  public void addFeatures(Collection features) {
    if (isLocked()) { return; }
    for (Object feature : features) {
      addFeature(feature);
    }
  }

  // get methods

  public Object getFeature(int index) { return featureIndex.get(index); }
  public Object getLabel(int index) { return labelIndex.get(index); }

  // indexOf methods

  public int indexOfFeature(Object feature) { return featureIndex.indexOf(feature); }
  public int indexOfLabel(Object label) { return labelIndex.indexOf(label); }

  // get collections

  /**
   * @return an unmodifiable view of the label index.
   */
  public HashIndex getLabels() { return labelIndex.unmodifiableView(); }

  /**
   * @return an unmodifiable view of the feature index.
   */
  public Index getFeatures() { return featureIndex.unmodifiableView(); }

  // locking stuff

  private boolean locked = false;

  public void lock() {
    // lock everything
    labelIndex.lock();
    featureIndex.lock();
    locked = true;
  }

  public void unlock() {
    // unlock everything
    labelIndex.unlock();
    featureIndex.unlock();
    locked = false;
  }

  public boolean isLocked() {
    return locked;
  }

  public HashIndex getOldStyleFeatureIndex() {
    return new OldStyleFeatureIndex();
  }

  public Collection<LabeledClique> allSequences() {
    Clique c = getMaxClique();

    int[] labels = new int[c.size()];
    int[] labelIndex = new int[c.size()];

    Arrays.fill(labelIndex, 0);

    List<LabeledClique> maxLabels = new ArrayList<LabeledClique>();

    while (true) {

      for (int i = 0; i < labels.length; i++) {
        labels[i] = labelIndex[i];
      }

      LabeledClique lc = LabeledClique.valueOf(c, labels);
      maxLabels.add(lc);

      boolean done = true;
      for (int i = 0; i < labelIndex.length; i++) {
        if (labelIndex[i] == numLabels()-1) {
          labelIndex[i] = 0;
        } else {
          labelIndex[i]++;
          done = false;
          break;
        }
      }

      if (done) { break; }
    }
    return maxLabels;
  }

  private class OldStyleFeatureIndex extends HashIndex<FeatureFactoryWrapper.ImmutablePairOfImmutables> {
    /**
     *
     */
    private static final long serialVersionUID = 46061012084372878L;
    private HashIndex fIndex = null;
    private Index<Integer> numFeatures = new HashIndex<Integer>();
    private List<Clique> featureCliques = new ArrayList<Clique>();
    private Map<Clique, HashIndex<LabeledClique>> lIndex = null;
    private HashIndex<LabeledClique> maxLabels = null;
    private int currentSize = 0;

    private void initLIndex() {
      if (maxLabels == null) {
        maxLabels = new HashIndex(allowedSequences);
        lIndex = new HashMap<Clique, HashIndex<LabeledClique>>();
        lIndex.put(maxClique, maxLabels);
        featureCliques = new ArrayList<Clique>();
      }
    }

    public OldStyleFeatureIndex() {
      fIndex = new HashIndex();
    }

    @Override
    public boolean add(FeatureFactoryWrapper.ImmutablePairOfImmutables o) {
      initLIndex();
      FeatureFactoryWrapper.ImmutablePairOfImmutables p = o;
      if (fIndex.add(p.first())) {
        //System.err.println(p+"  "+currentSize);
        Clique c = ((LabeledClique)p.second()).clique;
        HashIndex<LabeledClique> labels = lIndex.get(c);
        if (labels == null) {
          labels = new HashIndex<LabeledClique>();
          for (LabeledClique lc : maxLabels) {
            LabeledClique newLC = LabeledClique.valueOf(c, lc, 0);
            labels.add(newLC);
          }
          lIndex.put(c, labels);
        }
        numFeatures.add(currentSize);
        currentSize += labels.size();
        featureCliques.add(c);
        return true;
      } else {
        return false;
      }
    }

    @Override
    public boolean addAll(Collection<? extends FeatureFactoryWrapper.ImmutablePairOfImmutables> c) {
      boolean changed = false;
      for (FeatureFactoryWrapper.ImmutablePairOfImmutables element : c){
        if (add(element)) {
          changed = true;
        }
      }
      return changed;
    }

    private int firstSize = -1;

    @Override
    public FeatureFactoryWrapper.ImmutablePairOfImmutables get(int index) {
      if (index >= currentSize) { return null; }
      int fi = -1;
      int i = index+1;
      while (fi < 0) {
        i--;
        fi = numFeatures.indexOf(i);
      }
      Object feature = fIndex.get(fi);
      //System.err.println(feature);
      Clique c = featureCliques.get(fi);
      //System.err.println(c);
      //System.err.println(index+" "+fi);
      Object label = lIndex.get(c).get(index-i);

      return new FeatureFactoryWrapper.ImmutablePairOfImmutables(feature, label);
    }

    @Override
    public int indexOf(FeatureFactoryWrapper.ImmutablePairOfImmutables f) {
      if (firstSize < 0) { firstSize = numLabels(); }
      else if (firstSize != numLabels()) { throw new RuntimeException("You cannot call indexOf after adding a label after getting an index!"); }

      int fi = fIndex.indexOf(f.first());
      if (fi < 0) { return -1; }
      int offset = numFeatures.get(fi);
      Clique c = featureCliques.get(fi);
      Index<LabeledClique> labels = lIndex.get(c);
      if (labels == null) { return -1; }
      int li = labels.indexOf((LabeledClique) f.second());
      if (li < 0) { return -1; }

      return offset+li;
    }

    @Override
    public int indexOf(FeatureFactoryWrapper.ImmutablePairOfImmutables o, boolean add) {
      int i = indexOf(o);
      if (i >= 0) { return i; }
      add(o);
      return indexOf(o);
    }

    @Override
    public void clear() { throw new UnsupportedOperationException(); }
    @Override
    public Iterator<FeatureFactoryWrapper.ImmutablePairOfImmutables> iterator() { throw new UnsupportedOperationException(); }
    @Override
    public boolean isLocked() { return fIndex.isLocked(); }
    @Override
    public void lock() { fIndex.lock(); }
    @Override
    public void unlock() { fIndex.unlock(); }
    public Collection objects() { throw new UnsupportedOperationException(); }
    @Override
    public List<FeatureFactoryWrapper.ImmutablePairOfImmutables> objectsList() { throw new UnsupportedOperationException(); }
    @Override
    public boolean remove(Object o) { throw new UnsupportedOperationException(); }
    @Override
    public void saveToFilename(String file) { throw new UnsupportedOperationException(); }
    @Override
    public int size() { return currentSize; }

  }

}
