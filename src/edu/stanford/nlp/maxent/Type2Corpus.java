package edu.stanford.nlp.maxent;

import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.classify.LogLikelihoodFunction;
import edu.stanford.nlp.math.ArrayMath;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counters;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.util.Index;
import edu.stanford.nlp.util.IntPair;
import edu.stanford.nlp.util.Interner;
import edu.stanford.nlp.util.Pair;
import edu.stanford.nlp.util.HashIndex;

import java.io.File;
import java.util.*;


/** Another attempt at writing a more memory-efficient representation of
 *  Type2 problems.  The idea is to use almost as little memory as
 *  a type 1 problem when the problem is type 1, to be able to ignore
 *  some feature-class pairs (usually if they have 0 epmirical count),
 *  and to be able to sparsely represent label sequence features.
 *
 *  @author Kristina Toutanova
 *  @version Nov 17, 2004\
 *  @param <L> Class label type
 *  @param <F> Instance feature type
 */
public class Type2Corpus<L,F> implements LogLikelihoodFunction {

  Index<IntPair> pairsIndex; // from inputFeature and class to featureIndex
  Index<L> labelIndex;// index of labels
  Index<F> inputFeatureIndex; // index for "basic" features
  Interner<IntPair> intern; // for interning IntPairs
  private ArrayList<InternalSectionedType2Datum> instances;
  private IntPair temp = new IntPair();
  private boolean makeIndexArray = false;
  private int[][] indices;

  public Type2Corpus() {
    pairsIndex = new HashIndex<IntPair>();
    labelIndex = new HashIndex<L>();
    inputFeatureIndex = new HashIndex<F>();
    intern = new Interner<IntPair>();
    instances = new ArrayList<InternalSectionedType2Datum>();
  }

  public int size() {
    return instances.size();
  }

  public Pair<L,F> getFeature(int index) {
    IntPair p = pairsIndex.get(index); // this is fIndex and integer
    return new Pair<L,F>(labelIndex.get(p.getTarget()), inputFeatureIndex.get(p.getSource()));
  }


  private int indexOf(int fNo, int cNo) {
    if (indices != null) {
      return indices[fNo][cNo];
    }
    temp.set(0, fNo);
    temp.set(1, cNo);
    return pairsIndex.indexOf(temp);
  }

  public void readIndex(String filename) {
    try {
      inputFeatureIndex = IOUtils.<Index<F>>readObjectFromFile(new File(filename));
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  public void saveIndexAndForget(String tmpIndexFile) {
    try {
      Runtime inr = Runtime.getRuntime();
      System.err.println("initial mem is free " + inr.freeMemory() + " total " + inr.totalMemory());
      intern = null;
      IOUtils.writeObjectToFile(inputFeatureIndex, tmpIndexFile);
      inputFeatureIndex = null;
      System.gc();
      inr = Runtime.getRuntime();
      System.err.println("final mem is free " + inr.freeMemory() + " total " + inr.totalMemory());
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  public void clear() {
    System.err.println("clearing object ");
    instances.clear();
    inputFeatureIndex.clear();
  }

  /**
   * the likelihood of a single instance
   *
   */
  private double logLikelihood(InternalSectionedType2Datum datum, double[] weights) {
    double[] scores = new double[datum.numOptions()];
    for (int j = 0; j < datum.numBlocks(); j++) {
      InternalFeatureBlock block = datum.get(j);
      for (int index = 0; index < block.numFeatures(); index++) {
        int fNo = block.getFIndex(index);
        float value = block.getFValue(index);
        for (int l = 0; l < block.numLocations(); l++) {
          IntPair location = block.location(l);
          int cNo = location.get(0);
          int oNo = location.get(1);
          int fIndex = indexOf(fNo, cNo);
          if (fIndex > 0) {
            scores[oNo] += value * weights[fIndex];

          }
        }
      }
    }
    double z = ArrayMath.logSum(scores);
    return scores[datum.correctOption()] - z;
  }

  /**
   * Incrementally adding the derivatives of the negative log-likelihood for a single datum
   *
   * @return the updated derivative
   */
  private double[] addDerivative(InternalSectionedType2Datum datum, double[] weights, double[] derivative) {
    double[] scores = new double[datum.numOptions()];
    for (int j = 0; j < datum.numBlocks(); j++) {
      InternalFeatureBlock block = datum.get(j);
      for (int index = 0; index < block.numFeatures(); index++) {
        int fNo = block.getFIndex(index);
        float value = block.getFValue(index);
        for (int l = 0; l < block.numLocations(); l++) {
          IntPair location = block.location(l);
          int cNo = location.get(0);
          int oNo = location.get(1);
          int fIndex = indexOf(fNo, cNo);
          if (fIndex > -1) {
            scores[oNo] += value * weights[fIndex];

          }
        }
      }
    }
    double z = ArrayMath.logSum(scores);
    ArrayMath.addInPlace(scores, -z);
    ArrayMath.expInPlace(scores);
    for (int j = 0; j < datum.numBlocks(); j++) {
      InternalFeatureBlock block = datum.get(j);
      for (int index = 0; index < block.numFeatures(); index++) {
        int fNo = block.getFIndex(index);
        float value = block.getFValue(index);
        for (int l = 0; l < block.numLocations(); l++) {
          IntPair location = block.location(l);
          int cNo = location.get(0);
          int oNo = location.get(1);
          int fIndex = indexOf(fNo, cNo);
          if (fIndex > -1) {
            derivative[fIndex] -= value * scores[oNo];
            if (oNo == datum.correctOption()) {
              derivative[fIndex] += value;
            }
          }
        }
      }
    }
    return derivative;
  }

  /**
   * log-likelihood of the data at some weights
   *
   */
  public double logLikelihood(double[] weights) {
    double sum = 0.0;
    for (InternalSectionedType2Datum next : instances) {
      sum += logLikelihood(next, weights);
    }
    return sum;
  }

  public int domainDimension() {
    return featureDimension();
  }

  public double[] gradient(double[] weights) {
    double[] derivative = new double[weights.length];
    for (InternalSectionedType2Datum datum : instances) {
      derivative = addDerivative(datum, weights, derivative);
    }
    return derivative;
  }

  public void summaryStatistics() {
    System.out.println(featureDimension() + " composite features formed by " + inputFeatureIndex.size() + " base features and " + labelIndex.size() + " labels ");
  }

  public int getIndex(Pair<L,F> feature) {
    int cIndex = labelIndex.indexOf(feature.first());
    int fIndex = inputFeatureIndex.indexOf(feature.second());
    return pairsIndex.indexOf(new IntPair(fIndex, cIndex));

  }

  public int featureDimension() {
    return pairsIndex.size();
  }

  /**
   * convert this to an internal instance and add it
   *
   */
  public void addInstance(SectionedType2Datum<L, F> datum) {
    InternalSectionedType2Datum internal = new InternalSectionedType2Datum();
    internal.numOptions = datum.numOptions;
    internal.correctOption = datum.correctOption;
    internal.blocks = new InternalFeatureBlock[datum.numBlocks()];
    for (int j = 0; j < internal.blocks.length; j++) {
      internal.blocks[j] = toInternal(datum.getBlock(j));
    }
    instances.add(internal);
  }

  /**
   * make an index of all features that will get weights
   * we have an option to exclude all features with count less or equal to the given bounds
   * one bound is only for correct and the other is for total
   */
  void createFeatureIndex(int lowerCorrectBound, int lowerAllBound) {
    ClassicCounter<IntPair> empirical = new ClassicCounter<IntPair>();
    ClassicCounter<IntPair> all = new ClassicCounter<IntPair>();
    for (InternalSectionedType2Datum instance : instances) {
      int correctOption = instance.correctOption();
      for (int bno = 0; bno < instance.numBlocks(); bno++) {
        InternalFeatureBlock block = instance.get(bno);
        for (int index = 0; index < block.numFeatures(); index++) {
          int fIndex = block.getFIndex(index);
          double value = block.getFValue(index);
          for (int opt = 0; opt < block.numLocations(); opt++) {
            IntPair loc = block.location(opt);
            int cNo = loc.get(0);
            int oNo = loc.get(1);
            IntPair feature = new IntPair(fIndex, cNo);
            all.incrementCount(feature, value);
            if (oNo == correctOption) {
              empirical.incrementCount(feature, value);
            }
          }
        }
      }
    }
    //System.err.println("in createFeatureIndex -- before possible keys");
    Set<IntPair> possibleKeys = Counters.keysAbove(empirical, lowerCorrectBound);
    empirical.clear();
    if (lowerCorrectBound < 0) {
      possibleKeys = all.keySet();
    }
    // try to improve memory problem
    List<IntPair> tmp = new ArrayList<IntPair>(possibleKeys.size());

    for (IntPair pair : possibleKeys) {
      if (all.getCount(pair) > lowerAllBound) {
        tmp.add(pair);
        //pairsIndex.add(pair);
      }
    }

    possibleKeys.clear();
    pairsIndex.addAll(tmp);

    //System.err.println("in createFeatureIndex -- after add in pairsIndex");
    all.clear();
    if (makeIndexArray) {
      indices = new int[inputFeatureIndex.size()][];
      for (int i = 0; i < indices.length; i++) {
        indices[i] = new int[labelIndex.size()];
        for (int j = 0; j < labelIndex.size(); j++) {
          temp.set(0, i);
          temp.set(1, j);
          int fIndex = pairsIndex.indexOf(temp);
          indices[i][j] = fIndex;
        }
      }
    }
  }

  /**
   * will make them all intergers for now
   *
   */
  private InternalFeatureBlock toInternal(FeatureBlock<L, F> block) {
    Counter<F> features = block.features;
    Collection<Pair<L, Integer>> classAlternatives = block.classAlternatives;
    InternalSIFeatureBlock siBlock = new InternalSIFeatureBlock();
    int total = 0;
    for (F feature : features.keySet()) {
      inputFeatureIndex.add(feature);
      total += (int) features.getCount(feature);
    }
    int[] indices = new int[total];
    int index = 0;
    siBlock.inputFIndices = indices;
    for (F feature : features.keySet()) {
      int fIndex = inputFeatureIndex.indexOf(feature);
      int cnt = (int) features.getCount(feature);
      for (int j = 0; j < cnt; j++) {
        indices[index] = fIndex;
        index++;
      }
    }

    siBlock.locations = new IntPair[classAlternatives.size()];
    index = 0;
    for (Pair<L, Integer> p : classAlternatives) {
      labelIndex.add(p.first());
      siBlock.locations[index] = intern.intern(new IntPair(labelIndex.indexOf(p.first()), p.second().intValue()));
      index++;
    }
    return siBlock;
  }

}


/**
 * just an array of internal blocks together with alternatives and correct alternative
 */
class InternalSectionedType2Datum {
  int numOptions;
  int correctOption;
  InternalFeatureBlock[] blocks;

  int numOptions() {
    return numOptions;
  }

  int correctOption() {
    return correctOption;
  }

  int numBlocks() {
    return blocks.length;
  }

  InternalFeatureBlock get(int index) {
    return blocks[index];
  }

}


