// Stanford Classifier - a multiclass maxent classifier
// LinearClassifier
// Copyright (c) 2003-2007 The Board of Trustees of
// The Leland Stanford Junior University. All Rights Reserved.
//
// This program is free software; you can redistribute it and/or
// modify it under the terms of the GNU General Public License
// as published by the Free Software Foundation; either version 2
// of the License, or (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with this program; if not, write to the Free Software
// Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
//
// For more information, bug reports, fixes, contact:
//    Christopher Manning
//    Dept of Computer Science, Gates 1A
//    Stanford CA 94305-9010
//    USA
//    Support/Questions: java-nlp-user@lists.stanford.edu
//    Licensing: java-nlp-support@lists.stanford.edu
//    http://www-nlp.stanford.edu/software/classifier.shtml

package edu.stanford.nlp.classify;

import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.ling.BasicDatum;
import edu.stanford.nlp.ling.Datum;
import edu.stanford.nlp.ling.RVFDatum;
import edu.stanford.nlp.util.*;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.stats.Distribution;
import edu.stanford.nlp.stats.Counters;

import java.io.*;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.*;


/**
 * Implements a multiclass linear classifier. At classification time this
 * can be any generalized linear model classifier (such as a perceptron,
 * a maxent classifier (softmax logistic regression), or an SVM).
 *
 * @author Dan Klein
 * @author Jenny Finkel
 * @author Galen Andrew (converted to arrays and indices)
 * @author Christopher Manning (most of the printing options)
 * @author Eric Yeh (save to text file, new constructor w/thresholds)
 * @author Sarah Spikes (sdspikes@cs.stanford.edu) (Templatization)
 * @author {@literal nmramesh@cs.stanford.edu} {@link #weightsAsMapOfCounters()}
 * @author Angel Chang (Add functions to get top features, and number of features with weights above a certain threshold)
 *
 * @param <L> The type of the labels in the Classifier
 * @param <F> The type of the features in the Classifier
 */
public class LinearClassifier<L, F> implements ProbabilisticClassifier<L, F>, RVFClassifier<L, F> {

  /** Classifier weights. First index is the featureIndex value and second
   *  index is the labelIndex value.
   */
  private double[][] weights;
  private Index<L> labelIndex;
  private Index<F> featureIndex;
  public boolean intern = false;   // variable should be deleted when breaking serialization anyway....
  private double[] thresholds = null;

  private static final long serialVersionUID = 8499574525453275255L;

  private static final int MAX_FEATURE_ALIGN_WIDTH = 50;

  public static final String TEXT_SERIALIZATION_DELIMITER = "\t";

  @Override
  public Collection<L> labels() {
    return labelIndex.objectsList();
  }

  public Collection<F> features() {
    return featureIndex.objectsList();
  }

  public Index<L> labelIndex() {
    return labelIndex;
  }

  public Index<F> featureIndex() {
    return featureIndex;
  }

  private double weight(int iFeature, int iLabel) {
    if (iFeature < 0) {
      //System.err.println("feature not seen ");
      return 0.0;
    }
    assert iFeature < weights.length;
    assert iLabel < weights[iFeature].length;
    return weights[iFeature][iLabel];
  }

  private double weight(F feature, int iLabel) {
    int f = featureIndex.indexOf(feature);
    return weight(f, iLabel);
  }

  public double weight(F feature, L label) {
    int f = featureIndex.indexOf(feature);
    int iLabel = labelIndex.indexOf(label);
    return weight(f, iLabel);
  }

  /* --- obsolete method from before this class was rewritten using arrays
  public Counter scoresOf(Datum example) {
    Counter scores = new Counter();
    for (Object l : labels()) {
      scores.setCount(l, scoreOf(example, l));
    }
    return scores;
  }
  --- */

  /** Construct a counter with keys the labels of the classifier and
   *  values the score (unnormalized log probability) of each class.
   */
  @Override
  public Counter<L> scoresOf(Datum<L, F> example) {
    if(example instanceof RVFDatum<?, ?>)return scoresOfRVFDatum((RVFDatum<L,F>)example);
    Collection<F> feats = example.asFeatures();
    int[] features = new int[feats.size()];
    int i = 0;
    for (F f : feats) {
      int index = featureIndex.indexOf(f);
      if (index >= 0) {
        features[i++] = index;
      } else {
        //System.err.println("FEATURE LESS THAN ZERO: " + f);
      }
    }
    int[] activeFeatures = new int[i];
    System.arraycopy(features, 0, activeFeatures, 0, i);
    Counter<L> scores = new ClassicCounter<L>();
    for (L lab : labels()) {
      scores.setCount(lab, scoreOf(activeFeatures, lab));
    }
    return scores;
  }

  /** Given a datum's features, construct a counter with keys
   *  the labels and values the score (unnormalized log probability)
   *  for each class.
   */
  public Counter<L> scoresOf(int[] features) {
    Counter<L> scores = new ClassicCounter<L>();
    for (L label : labels())
      scores.setCount(label, scoreOf(features, label));
    return scores;
  }

  /** Returns of the score of the Datum for the specified label.
   *  Ignores the true label of the Datum.
   */
  public double scoreOf(Datum<L, F> example, L label) {
    if(example instanceof RVFDatum<?, ?>)return scoreOfRVFDatum((RVFDatum<L,F>)example, label);
    int iLabel = labelIndex.indexOf(label);
    double score = 0.0;
    for (F f : example.asFeatures()) {
      score += weight(f, iLabel);
    }
    return score + thresholds[iLabel];
  }

  /** Construct a counter with keys the labels of the classifier and
   *  values the score (unnormalized log probability) of each class
   *  for an RVFDatum.
   */
  @Override
  @Deprecated
  public Counter<L> scoresOf(RVFDatum<L, F> example) {
    Counter<L> scores = new ClassicCounter<L>();
    for (L l : labels()) {
      scores.setCount(l, scoreOf(example, l));
    }
    //System.out.println("Scores are: " + scores + "   (gold: " + example.label() + ")");
    return scores;
  }

  /** Construct a counter with keys the labels of the classifier and
   *  values the score (unnormalized log probability) of each class
   *  for an RVFDatum.
   */
  private Counter<L> scoresOfRVFDatum(RVFDatum<L, F> example) {
    Counter<L> scores = new ClassicCounter<L>();
    for (L l : labels()) {
      scores.setCount(l, scoreOfRVFDatum(example, l));
    }
    //System.out.println("Scores are: " + scores + "   (gold: " + example.label() + ")");
    return scores;
  }

  /** Returns the score of the RVFDatum for the specified label.
   *  Ignores the true label of the RVFDatum.
   */
  @Deprecated
  public double scoreOf(RVFDatum<L, F> example, L label) {
    int iLabel = labelIndex.indexOf(label);
    double score = 0.0;
    Counter<F> features = example.asFeaturesCounter();
    for (F f : features.keySet()) {
      score += weight(f, iLabel) * features.getCount(f);
    }
    return score + thresholds[iLabel];
  }

  /** Returns the score of the RVFDatum for the specified label.
   *  Ignores the true label of the RVFDatum.
   */
  private double scoreOfRVFDatum(RVFDatum<L, F> example, L label) {
    int iLabel = labelIndex.indexOf(label);
    double score = 0.0;
    Counter<F> features = example.asFeaturesCounter();
    for (F f : features.keySet()) {
      score += weight(f, iLabel) * features.getCount(f);
    }
    return score + thresholds[iLabel];
  }


  /** Returns of the score of the Datum as internalized features for the
   *  specified label. Ignores the true label of the Datum.
   *  Doesn't consider a value for each feature.
   */
  private double scoreOf(int[] feats, L label) {
    int iLabel = labelIndex.indexOf(label);
    assert iLabel >= 0;
    double score = 0.0;
    for (int feat : feats) {
      score += weight(feat, iLabel);
    }
    return score + thresholds[iLabel];
  }


  /**
   * Returns a counter mapping from each class name to the probability of
   * that class for a certain example.
   * Looking at the the sum of each count v, should be 1.0.
   */
  @Override
  public Counter<L> probabilityOf(Datum<L, F> example) {
    if(example instanceof RVFDatum<?, ?>)return probabilityOfRVFDatum((RVFDatum<L,F>)example);
    Counter<L> scores = logProbabilityOf(example);
    for (L label : scores.keySet()) {
      scores.setCount(label, Math.exp(scores.getCount(label)));
    }
    return scores;
  }

  /**
   * Returns a counter mapping from each class name to the probability of
   * that class for a certain example.
   * Looking at the the sum of each count v, should be 1.0.
   */
  private Counter<L> probabilityOfRVFDatum(RVFDatum<L, F> example) {
    // NB: this duplicate method is needed so it calls the scoresOf method
    // with a RVFDatum signature
    Counter<L> scores = logProbabilityOfRVFDatum(example);
    for (L label : scores.keySet()) {
      scores.setCount(label, Math.exp(scores.getCount(label)));
    }
    return scores;
  }

  /**
   * Returns a counter mapping from each class name to the probability of
   * that class for a certain example.
   * Looking at the the sum of each count v, should be 1.0.
   */
  @Deprecated
  public Counter<L> probabilityOf(RVFDatum<L, F> example) {
    // NB: this duplicate method is needed so it calls the scoresOf method
    // with a RVFDatum signature
    Counter<L> scores = logProbabilityOf(example);
    for (L label : scores.keySet()) {
      scores.setCount(label, Math.exp(scores.getCount(label)));
    }
    return scores;
  }

  /**
   * Returns a counter mapping from each class name to the log probability of
   * that class for a certain example.
   * Looking at the the sum of e^v for each count v, should be 1.0.
   */
  @Override
  public Counter<L> logProbabilityOf(Datum<L, F> example) {
    if(example instanceof RVFDatum<?, ?>)return logProbabilityOfRVFDatum((RVFDatum<L,F>)example);
    Counter<L> scores = scoresOf(example);
    Counters.logNormalizeInPlace(scores);
    return scores;
  }

  /**
   * Given a datum's features, returns a counter mapping from each
   * class name to the log probability of that class.
   * Looking at the the sum of e^v for each count v, should be 1.
   */
  public Counter<L> logProbabilityOf(int[] features) {
    Counter<L> scores = scoresOf(features);
    Counters.logNormalizeInPlace(scores);
    return scores;
  }

  public Counter<L> probabilityOf(int [] features) {
    Counter<L> scores = logProbabilityOf(features);
    for (L label : scores.keySet()) {
      scores.setCount(label, Math.exp(scores.getCount(label)));
    }
    return scores;
  }

  /**
   * Returns a counter for the log probability of each of the classes
   * looking at the the sum of e^v for each count v, should be 1
   */
  private Counter<L> logProbabilityOfRVFDatum(RVFDatum<L, F> example) {
    // NB: this duplicate method is needed so it calls the scoresOf method
    // with an RVFDatum signature!!  Don't remove it!
    // JLS: type resolution of method parameters is static
    Counter<L> scores = scoresOfRVFDatum(example);
    Counters.logNormalizeInPlace(scores);
    return scores;
  }

  /**
   * Returns a counter for the log probability of each of the classes.
   * Looking at the the sum of e^v for each count v, should give 1.
   */
  @Deprecated
  public Counter<L> logProbabilityOf(RVFDatum<L, F> example) {
    // NB: this duplicate method is needed so it calls the scoresOf method
    // with an RVFDatum signature!!  Don't remove it!
    // JLS: type resolution of method parameters is static
    Counter<L> scores = scoresOf(example);
    Counters.logNormalizeInPlace(scores);
    return scores;
  }

  /**
   * Returns indices of labels
   * @param labels - Set of labels to get indices
   * @return Set of indices
   */
  protected Set<Integer> getLabelIndices(Set<L> labels) {
    Set<Integer> iLabels = Generics.newHashSet();
    for (L label:labels) {
      int iLabel = labelIndex.indexOf(label);
      iLabels.add(iLabel);
      if (iLabel < 0) throw new IllegalArgumentException("Unknown label " + label);
    }
    return iLabels;
  }

  /**
   * Returns number of features with weight above a certain threshold
   * (across all labels).
   *
   * @param threshold  Threshold above which we will count the feature
   * @param useMagnitude Whether the notion of "large" should ignore
   *                     the sign of the feature weight.
   * @return number of features satisfying the specified conditions
   */
  public int getFeatureCount(double threshold, boolean useMagnitude)
  {
    int n = 0;
    for (double[] weightArray : weights) {
      for (double weight : weightArray) {
        double thisWeight = (useMagnitude) ? Math.abs(weight) : weight;
        if (thisWeight > threshold) {
          n++;
        }
      }
    }
    return n;
  }

  /**
   * Returns number of features with weight above a certain threshold.
   *
   * @param labels Set of labels we care about when counting features
   *               Use null to get counts across all labels
   * @param threshold  Threshold above which we will count the feature
   * @param useMagnitude Whether the notion of "large" should ignore
   *                     the sign of the feature weight.
   * @return number of features satisfying the specified conditions
   */
  public int getFeatureCount(Set<L> labels, double threshold, boolean useMagnitude)
  {
    if (labels != null) {
      Set<Integer> iLabels = getLabelIndices(labels);
      return getFeatureCountLabelIndices(iLabels, threshold, useMagnitude);
    } else {
      return getFeatureCount(threshold, useMagnitude);
    }
  }

  /**
   * Returns number of features with weight above a certain threshold.
   *
   * @param iLabels Set of label indices we care about when counting features
   *                Use null to get counts across all labels
   * @param threshold  Threshold above which we will count the feature
   * @param useMagnitude Whether the notion of "large" should ignore
   *                     the sign of the feature weight.
   * @return number of features satisfying the specified conditions
   */
  protected int getFeatureCountLabelIndices(Set<Integer> iLabels, double threshold, boolean useMagnitude)
  {
    int n = 0;
    for (double[] weightArray : weights) {
      for (int labIndex : iLabels) {
        double thisWeight = (useMagnitude) ? Math.abs(weightArray[labIndex]) : weightArray[labIndex];
        if (thisWeight > threshold) {
          n++;
        }
      }
    }
    return n;
  }

  /**
   * Returns list of top features with weight above a certain threshold
   * (list is descending and across all labels).
   *
   * @param threshold  Threshold above which we will count the feature
   * @param useMagnitude Whether the notion of "large" should ignore
   *                     the sign of the feature weight.
   * @param numFeatures  How many top features to return (-1 for unlimited)
   * @return List of triples indicating feature, label, weight
   */
  public List<Triple<F,L,Double>> getTopFeatures(double threshold, boolean useMagnitude, int numFeatures)
  {
    return getTopFeatures(null, threshold, useMagnitude, numFeatures, true);
  }

  /**
   * Returns list of top features with weight above a certain threshold
   * @param labels Set of labels we care about when getting features
   *               Use null to get features across all labels
   * @param threshold  Threshold above which we will count the feature
   * @param useMagnitude Whether the notion of "large" should ignore
   *                     the sign of the feature weight.
   * @param numFeatures  How many top features to return (-1 for unlimited)
   * @param descending Return weights in descending order
   * @return List of triples indicating feature, label, weight
   */
  public List<Triple<F,L,Double>> getTopFeatures(Set<L> labels,
                                                 double threshold, boolean useMagnitude, int numFeatures,
                                                 boolean descending)
  {
    if (labels != null) {
      Set<Integer> iLabels = getLabelIndices(labels);
      return getTopFeaturesLabelIndices(iLabels, threshold, useMagnitude, numFeatures, descending);
    } else {
      return getTopFeaturesLabelIndices(null, threshold, useMagnitude, numFeatures, descending);
    }
  }

  /**
   * Returns list of top features with weight above a certain threshold
   * @param iLabels Set of label indices we care about when getting features
   *                Use null to get features across all labels
   * @param threshold  Threshold above which we will count the feature
   * @param useMagnitude Whether the notion of "large" should ignore
   *                     the sign of the feature weight.
   * @param numFeatures  How many top features to return (-1 for unlimited)
   * @param descending Return weights in descending order
   * @return List of triples indicating feature, label, weight
   */
  protected List<Triple<F,L,Double>> getTopFeaturesLabelIndices(Set<Integer> iLabels,
                                                 double threshold, boolean useMagnitude, int numFeatures,
                                                 boolean descending)
  {
    edu.stanford.nlp.util.PriorityQueue<Pair<Integer,Integer>> biggestKeys =
      new FixedPrioritiesPriorityQueue<Pair<Integer,Integer>>();

    // locate biggest keys
    for (int feat = 0; feat < weights.length; feat++) {
      for (int lab = 0; lab < weights[feat].length; lab++) {
        if (iLabels != null && !iLabels.contains(lab)) {
          continue;
        }
        double thisWeight;
        if (useMagnitude) {
          thisWeight = Math.abs(weights[feat][lab]);
        } else {
          thisWeight = weights[feat][lab];
        }

        if (thisWeight > threshold) {
          // reverse the weight, so get smallest first
          thisWeight = -thisWeight;
          if (biggestKeys.size() == numFeatures) {
            // have enough features, add only if bigger
            double lowest = biggestKeys.getPriority();
            if (thisWeight < lowest) {
              // remove smallest
              biggestKeys.removeFirst();
              biggestKeys.add(new Pair<Integer, Integer>(feat, lab), thisWeight);
            }
          } else {
            // always add it if don't have enough features yet
            biggestKeys.add(new Pair<Integer, Integer>(feat, lab), thisWeight);
          }
        }
      }
    }

    List<Triple<F,L,Double>> topFeatures = new ArrayList<Triple<F,L,Double>>(biggestKeys.size());
    while (!biggestKeys.isEmpty()) {
      Pair<Integer,Integer> p = biggestKeys.removeFirst();
      double weight = weights[p.first()][p.second()];
      F feat = featureIndex.get(p.first());
      L label = labelIndex.get(p.second());
      topFeatures.add(new Triple<F,L,Double>(feat, label, weight));
    }
    if (descending) {
      Collections.reverse(topFeatures);
    }
    return topFeatures;
  }

  /**
   * Returns string representation of a list of top features
   * @param topFeatures List of triples indicating feature, label, weight
   * @return String representation of the list of features
   */
  public String topFeaturesToString(List<Triple<F,L,Double>> topFeatures)
  {
    // find longest key length (for pretty printing) with a limit
    int maxLeng = 0;
    for (Triple<F,L,Double> t : topFeatures) {
      String key = "(" + t.first + "," + t.second + ")";
      int leng = key.length();
      if (leng > maxLeng) {
        maxLeng = leng;
      }
    }
    maxLeng = Math.min(64, maxLeng);

    // set up pretty printing of weights
    NumberFormat nf = NumberFormat.getNumberInstance();
    nf.setMinimumFractionDigits(4);
    nf.setMaximumFractionDigits(4);
    if (nf instanceof DecimalFormat) {
      ((DecimalFormat) nf).setPositivePrefix(" ");
    }

    //print high weight features to a String
    StringBuilder sb = new StringBuilder();
    for (Triple<F,L,Double> t : topFeatures) {
      String key = "(" + t.first + "," + t.second + ")";
      sb.append(StringUtils.pad(key, maxLeng));
      sb.append(" ");
      double cnt = t.third();
      if (Double.isInfinite(cnt)) {
        sb.append(cnt);
      } else {
        sb.append(nf.format(cnt));
      }
      sb.append("\n");
    }
    return sb.toString();
  }

  /** Return a String that prints features with large weights.
   *
   * @param useMagnitude Whether the notion of "large" should ignore
   *                     the sign of the feature weight.
   * @param numFeatures  How many top features to print
   * @param printDescending Print weights in descending order
   * @return The String representation of features with large weights
   */
  public String toBiggestWeightFeaturesString(boolean useMagnitude,
      int numFeatures,
      boolean printDescending) {
    // this used to try to use a treeset, but that was WRONG....
    edu.stanford.nlp.util.PriorityQueue<Pair<Integer,Integer>> biggestKeys =
      new FixedPrioritiesPriorityQueue<Pair<Integer,Integer>>();

    // locate biggest keys
    for (int feat = 0; feat < weights.length; feat++) {
      for (int lab = 0; lab < weights[feat].length; lab++) {
        double thisWeight;
        // reverse the weight, so get smallest first
        if (useMagnitude) {
          thisWeight = -Math.abs(weights[feat][lab]);
        } else {
          thisWeight = -weights[feat][lab];
        }
        if (biggestKeys.size() == numFeatures) {
          // have enough features, add only if bigger
          double lowest = biggestKeys.getPriority();
          if (thisWeight < lowest) {
            // remove smallest
            biggestKeys.removeFirst();
            biggestKeys.add(new Pair<Integer, Integer>(feat, lab), thisWeight);
          }
        } else {
          // always add it if don't have enough features yet
          biggestKeys.add(new Pair<Integer, Integer>(feat, lab), thisWeight);
        }
      }
    }

    // Put in List either reversed or not
    // (Note: can't repeatedly iterate over PriorityQueue.)
    int actualSize = biggestKeys.size();
    Pair<Integer, Integer>[] bigArray = ErasureUtils.<Pair<Integer, Integer>>mkTArray(Pair.class,actualSize);
    // System.err.println("biggestKeys is " + biggestKeys);
    if (printDescending) {
      for (int j = actualSize - 1; j >= 0; j--) {
        bigArray[j] = biggestKeys.removeFirst();
      }
    } else {
      for (int j = 0; j < actualSize; j--) {
        bigArray[j] = biggestKeys.removeFirst();
      }
    }
    List<Pair<Integer, Integer>> bigColl = Arrays.asList(bigArray);
    // System.err.println("bigColl is " + bigColl);

    // find longest key length (for pretty printing) with a limit
    int maxLeng = 0;
    for (Pair<Integer,Integer> p : bigColl) {
      String key = "(" + featureIndex.get(p.first) + "," + labelIndex.get(p.second) + ")";
      int leng = key.length();
      if (leng > maxLeng) {
        maxLeng = leng;
      }
    }
    maxLeng = Math.min(64, maxLeng);

    // set up pretty printing of weights
    NumberFormat nf = NumberFormat.getNumberInstance();
    nf.setMinimumFractionDigits(4);
    nf.setMaximumFractionDigits(4);
    if (nf instanceof DecimalFormat) {
      ((DecimalFormat) nf).setPositivePrefix(" ");
    }

    //print high weight features to a String
    StringBuilder sb = new StringBuilder("LinearClassifier [printing top " + numFeatures + " features]\n");
    for (Pair<Integer, Integer> p : bigColl) {
      String key = "(" + featureIndex.get(p.first) + "," + labelIndex.get(p.second) + ")";
      sb.append(StringUtils.pad(key, maxLeng));
      sb.append(" ");
      double cnt = weights[p.first][p.second];
      if (Double.isInfinite(cnt)) {
        sb.append(cnt);
      } else {
        sb.append(nf.format(cnt));
      }
      sb.append("\n");
    }
    return sb.toString();
  }

  /**
   * Similar to histogram but exact values of the weights
   * to see whether there are many equal weights.
   *
   * @return A human readable string about the classifier distribution.
   */
  public String toDistributionString(int threshold) {
    Counter<Double> weightCounts = new ClassicCounter<Double>();
    StringBuilder s = new StringBuilder();
    s.append("Total number of weights: ").append(totalSize());
    for (double[] weightArray : weights) {
      for (double weight : weightArray) {
        weightCounts.incrementCount(weight);
      }
    }

    s.append("Counts of weights\n");
    Set<Double> keys = Counters.keysAbove(weightCounts, threshold);
    s.append(keys.size()).append(" keys occur more than ").append(threshold).append(" times ");
    return s.toString();
  }

  public int totalSize() {
    return labelIndex.size() * featureIndex.size();
  }

  public String toHistogramString() {
    // big classifiers
    double[][] hist = new double[3][202];
    Object[][] histEg = new Object[3][202];
    int num = 0;
    int pos = 0;
    int neg = 0;
    int zero = 0;
    double total = 0.0;
    double x2total = 0.0;
    double max = 0.0, min = 0.0;
    for (int f = 0; f < weights.length; f++) {
      for (int l = 0; l < weights[f].length; l++) {
        Pair<F, L> feat = new Pair<F, L>(featureIndex.get(f), labelIndex.get(l));
        num++;
        double wt = weights[f][l];
        total += wt;
        x2total += wt * wt;
        if (wt > max) {
          max = wt;
        }
        if (wt < min) {
          min = wt;
        }
        if (wt < 0.0) {
          neg++;
        } else if (wt > 0.0) {
          pos++;
        } else {
          zero++;
        }
        int index;
        index = bucketizeValue(wt);
        hist[0][index]++;
        if (histEg[0][index] == null) {
          histEg[0][index] = feat;
        }
        if (wt < 0.1 && wt >= -0.1) {
          index = bucketizeValue(wt * 100.0);
          hist[1][index]++;
          if (histEg[1][index] == null) {
            histEg[1][index] = feat;
          }
          if (wt < 0.001 && wt >= -0.001) {
            index = bucketizeValue(wt * 10000.0);
            hist[2][index]++;
            if (histEg[2][index] == null) {
              histEg[2][index] = feat;
            }
          }
        }
      }
    }
    double ave = total / num;
    double stddev = (x2total / num) - ave * ave;
    StringWriter sw = new StringWriter();
    PrintWriter pw = new PrintWriter(sw);

    pw.println("Linear classifier with " + num + " f(x,y) features");
    pw.println("Average weight: " + ave + "; std dev: " + stddev);
    pw.println("Max weight: " + max + " min weight: " + min);
    pw.println("Weights: " + neg + " negative; " + pos + " positive; " + zero + " zero.");

    printHistCounts(0, "Counts of lambda parameters between [-10, 10)", pw, hist, histEg);
    printHistCounts(1, "Closeup view of [-0.1, 0.1) depicted * 10^2", pw, hist, histEg);
    printHistCounts(2, "Closeup view of [-0.001, 0.001) depicted * 10^4", pw, hist, histEg);
    pw.close();
    return sw.toString();
  }

  /** Print out a partial representation of a linear classifier.
   *  This just calls toString("WeightHistogram", 0)
   */
  @Override
  public String toString() {
    return toString("WeightHistogram", 0);
  }


  /**
   * Print out a partial representation of a linear classifier in one of
   * several ways.
   *
   * @param style Options are:
   *              HighWeight: print out the param parameters with largest weights;
   *              HighMagnitude: print out the param parameters for which the absolute
   *              value of their weight is largest;
   *              AllWeights: print out the weights of all features;
   *              WeightHistogram: print out a particular hard-coded textual histogram
   *              representation of a classifier;
   *              WeightDistribution;
   *
   * @param param Determines the number of things printed in certain styles
   * @throws IllegalArgumentException if the style name is unrecognized
   */
  public String toString(String style, int param) {
    if (style == null || "".equals(style)) {
      return "LinearClassifier with " + featureIndex.size() + " features, " +
              labelIndex.size() + " classes, and " +
              labelIndex.size() * featureIndex.size() + " parameters.\n";
    } else if (style.equalsIgnoreCase("HighWeight")) {
      return toBiggestWeightFeaturesString(false, param, true);
    } else if (style.equalsIgnoreCase("HighMagnitude")) {
      return toBiggestWeightFeaturesString(true, param, true);
    } else if (style.equalsIgnoreCase("AllWeights")) {
      return toAllWeightsString();
    } else if (style.equalsIgnoreCase("WeightHistogram")) {
      return toHistogramString();
    } else if (style.equalsIgnoreCase("WeightDistribution")) {
      return toDistributionString(param);
    } else {
      throw new IllegalArgumentException("Unknown style: " + style);
    }
  }


  /**
   * Convert parameter value into number between 0 and 201
   */
  private static int bucketizeValue(double wt) {
    int index;
    if (wt >= 0.0) {
      index = ((int) (wt * 10.0)) + 100;
    } else {
      index = ((int) (Math.floor(wt * 10.0))) + 100;
    }
    if (index < 0) {
      index = 201;
    } else if (index > 200) {
      index = 200;
    }
    return index;
  }

  /**
   * Print histogram counts from hist and examples over a certain range
   */
  private static void printHistCounts(int ind, String title, PrintWriter pw, double[][] hist, Object[][] histEg) {
    pw.println(title);
    for (int i = 0; i < 200; i++) {
      int intpart, fracpart;
      if (i < 100) {
        intpart = 10 - ((i + 9) / 10);
        fracpart = (10 - (i % 10)) % 10;
      } else {
        intpart = (i / 10) - 10;
        fracpart = i % 10;
      }
      pw.print("[" + ((i < 100) ? "-" : "") + intpart + "." + fracpart + ", " + ((i < 100) ? "-" : "") + intpart + "." + fracpart + "+0.1): " + hist[ind][i]);
      if (histEg[ind][i] != null) {
        pw.print("  [" + histEg[ind][i] + ((hist[ind][i] > 1) ? ", ..." : "") + "]");
      }
      pw.println();
    }
  }


  //TODO: Sort of assumes that Labels are Strings...
  public String toAllWeightsString() {
    StringWriter sw = new StringWriter();
    PrintWriter pw = new PrintWriter(sw);
    pw.println("Linear classifier with the following weights");
    Datum<L, F> allFeatures = new BasicDatum<L, F>(features(), (L)null);
    justificationOf(allFeatures, pw);
    return sw.toString();
  }


  /**
   * Print all features in the classifier and the weight that they assign
   * to each class.
   */
  public void dump() {
    Datum<L, F> allFeatures = new BasicDatum<L, F>(features(), (L)null);
    justificationOf(allFeatures);
  }

  public void dump(PrintWriter pw) {
    Datum<L, F> allFeatures = new BasicDatum<L, F>(features(), (L)null);
    justificationOf(allFeatures, pw);
  }



  @Deprecated
  public void justificationOf(RVFDatum<L, F> example) {
    PrintWriter pw = new PrintWriter(System.err, true);
    justificationOf(example, pw);
  }

  /**
   * Print all features active for a particular datum and the weight that
   * the classifier assigns to each class for those features.
   */
  private void justificationOfRVFDatum(RVFDatum<L, F> example, PrintWriter pw) {
    int featureLength = 0;
    int labelLength = 6;
    NumberFormat nf = NumberFormat.getNumberInstance();
    nf.setMinimumFractionDigits(2);
    nf.setMaximumFractionDigits(2);
    if (nf instanceof DecimalFormat) {
      ((DecimalFormat) nf).setPositivePrefix(" ");
    }
    Counter<F> features = example.asFeaturesCounter();
    for (F f : features.keySet()) {
      featureLength = Math.max(featureLength, f.toString().length() + 2 +
          nf.format(features.getCount(f)).length());
    }
    // make as wide as total printout
    featureLength = Math.max(featureLength, "Total:".length());
    // don't make it ridiculously wide
    featureLength = Math.min(featureLength, MAX_FEATURE_ALIGN_WIDTH);

    for (Object l : labels()) {
      labelLength = Math.max(labelLength, l.toString().length());
    }

    StringBuilder header = new StringBuilder("");
    for (int s = 0; s < featureLength; s++) {
      header.append(' ');
    }
    for (L l : labels()) {
      header.append(' ');
      header.append(StringUtils.pad(l, labelLength));
    }
    pw.println(header);
    for (F f : features.keySet()) {
      String fStr = f.toString();
      StringBuilder line = new StringBuilder(fStr);
      line.append("[").append(nf.format(features.getCount(f))).append("]");
      fStr = line.toString();
      for (int s = fStr.length(); s < featureLength; s++) {
        line.append(' ');
      }
      for (L l : labels()) {
        String lStr = nf.format(weight(f, l));
        line.append(' ');
        line.append(lStr);
        for (int s = lStr.length(); s < labelLength; s++) {
          line.append(' ');
        }
      }
      pw.println(line);
    }
    Counter<L> scores = scoresOfRVFDatum(example);
    StringBuilder footer = new StringBuilder("Total:");
    for (int s = footer.length(); s < featureLength; s++) {
      footer.append(' ');
    }
    for (L l : labels()) {
      footer.append(' ');
      String str = nf.format(scores.getCount(l));
      footer.append(str);
      for (int s = str.length(); s < labelLength; s++) {
        footer.append(' ');
      }
    }
    pw.println(footer);
    Distribution<L> distr = Distribution.distributionFromLogisticCounter(scores);
    footer = new StringBuilder("Prob:");
    for (int s = footer.length(); s < featureLength; s++) {
      footer.append(' ');
    }
    for (L l : labels()) {
      footer.append(' ');
      String str = nf.format(distr.getCount(l));
      footer.append(str);
      for (int s = str.length(); s < labelLength; s++) {
        footer.append(' ');
      }
    }
    pw.println(footer);
  }


  /**
   * Print all features active for a particular datum and the weight that
   * the classifier assigns to each class for those features.
   */
  @Deprecated
  public void justificationOf(RVFDatum<L, F> example, PrintWriter pw) {
    int featureLength = 0;
    int labelLength = 6;
    NumberFormat nf = NumberFormat.getNumberInstance();
    nf.setMinimumFractionDigits(2);
    nf.setMaximumFractionDigits(2);
    if (nf instanceof DecimalFormat) {
      ((DecimalFormat) nf).setPositivePrefix(" ");
    }
    Counter<F> features = example.asFeaturesCounter();
    for (F f : features.keySet()) {
      featureLength = Math.max(featureLength, f.toString().length() + 2 +
          nf.format(features.getCount(f)).length());
    }
    // make as wide as total printout
    featureLength = Math.max(featureLength, "Total:".length());
    // don't make it ridiculously wide
    featureLength = Math.min(featureLength, MAX_FEATURE_ALIGN_WIDTH);

    for (Object l : labels()) {
      labelLength = Math.max(labelLength, l.toString().length());
    }

    StringBuilder header = new StringBuilder("");
    for (int s = 0; s < featureLength; s++) {
      header.append(' ');
    }
    for (L l : labels()) {
      header.append(' ');
      header.append(StringUtils.pad(l, labelLength));
    }
    pw.println(header);
    for (F f : features.keySet()) {
      String fStr = f.toString();
      StringBuilder line = new StringBuilder(fStr);
      line.append("[").append(nf.format(features.getCount(f))).append("]");
      fStr = line.toString();
      for (int s = fStr.length(); s < featureLength; s++) {
        line.append(' ');
      }
      for (L l : labels()) {
        String lStr = nf.format(weight(f, l));
        line.append(' ');
        line.append(lStr);
        for (int s = lStr.length(); s < labelLength; s++) {
          line.append(' ');
        }
      }
      pw.println(line);
    }
    Counter<L> scores = scoresOf(example);
    StringBuilder footer = new StringBuilder("Total:");
    for (int s = footer.length(); s < featureLength; s++) {
      footer.append(' ');
    }
    for (L l : labels()) {
      footer.append(' ');
      String str = nf.format(scores.getCount(l));
      footer.append(str);
      for (int s = str.length(); s < labelLength; s++) {
        footer.append(' ');
      }
    }
    pw.println(footer);
    Distribution<L> distr = Distribution.distributionFromLogisticCounter(scores);
    footer = new StringBuilder("Prob:");
    for (int s = footer.length(); s < featureLength; s++) {
      footer.append(' ');
    }
    for (L l : labels()) {
      footer.append(' ');
      String str = nf.format(distr.getCount(l));
      footer.append(str);
      for (int s = str.length(); s < labelLength; s++) {
        footer.append(' ');
      }
    }
    pw.println(footer);
  }


  public void justificationOf(Datum<L, F> example) {
    PrintWriter pw = new PrintWriter(System.err, true);
    justificationOf(example, pw);
  }

  public <T> void justificationOf(Datum<L, F> example, PrintWriter pw, Function<F, T> printer) {
    justificationOf(example, pw, printer, false);
  }

  /** Print all features active for a particular datum and the weight that
   *  the classifier assigns to each class for those features.
   *
   *  @param example The datum for which features are to be printed
   *  @param pw Where to print it to
   *  @param printer If this is non-null, then it is applied to each
   *        feature to convert it to a more readable form
   *  @param sortedByFeature Whether to sort by feature names
   */
  public <T> void justificationOf(Datum<L, F> example, PrintWriter pw,
      Function<F, T> printer, boolean sortedByFeature) {

    if(example instanceof RVFDatum<?, ?>) {
      justificationOfRVFDatum((RVFDatum<L,F>)example,pw);
      return;
    }
    NumberFormat nf = NumberFormat.getNumberInstance();
    nf.setMinimumFractionDigits(2);
    nf.setMaximumFractionDigits(2);
    if (nf instanceof DecimalFormat) {
      ((DecimalFormat) nf).setPositivePrefix(" ");
    }

    // determine width for features, making it at least total's width
    int featureLength = 0;
    //TODO: not really sure what this Printer is supposed to spit out...
    for (F f : example.asFeatures()) {
      int length = f.toString().length();
      if (printer != null) {
        length = printer.apply(f).toString().length();
      }
      featureLength = Math.max(featureLength, length);
    }
    // make as wide as total printout
    featureLength = Math.max(featureLength, "Total:".length());
    // don't make it ridiculously wide
    featureLength = Math.min(featureLength, MAX_FEATURE_ALIGN_WIDTH);

    // determine width for labels
    int labelLength = 6;
    for (L l : labels()) {
      labelLength = Math.max(labelLength, l.toString().length());
    }

    // print header row of output listing classes
    StringBuilder header = new StringBuilder("");
    for (int s = 0; s < featureLength; s++) {
      header.append(' ');
    }
    for (L l : labels()) {
      header.append(' ');
      header.append(StringUtils.pad(l, labelLength));
    }
    pw.println(header);

    // print active features and weights per class
    Collection<F> featColl = example.asFeatures();
    if (sortedByFeature){
      featColl = ErasureUtils.sortedIfPossible(featColl);
    }
    for (F f : featColl) {
      String fStr;
      if (printer != null) {
        fStr = printer.apply(f).toString();
      } else {
        fStr = f.toString();
      }
      StringBuilder line = new StringBuilder(fStr);
      for (int s = fStr.length(); s < featureLength; s++) {
        line.append(' ');
      }
      for (L l : labels()) {
        String lStr = nf.format(weight(f, l));
        line.append(' ');
        line.append(lStr);
        for (int s = lStr.length(); s < labelLength; s++) {
          line.append(' ');
        }
      }
      pw.println(line);
    }

    // Print totals, probs, etc.
    Counter<L> scores = scoresOf(example);
    StringBuilder footer = new StringBuilder("Total:");
    for (int s = footer.length(); s < featureLength; s++) {
      footer.append(' ');
    }
    for (L l : labels()) {
      footer.append(' ');
      String str = nf.format(scores.getCount(l));
      footer.append(str);
      for (int s = str.length(); s < labelLength; s++) {
        footer.append(' ');
      }
    }
    pw.println(footer);
    Distribution<L> distr = Distribution.distributionFromLogisticCounter(scores);
    footer = new StringBuilder("Prob:");
    for (int s = footer.length(); s < featureLength; s++) {
      footer.append(' ');
    }
    for (L l : labels()) {
      footer.append(' ');
      String str = nf.format(distr.getCount(l));
      footer.append(str);
      for (int s = str.length(); s < labelLength; s++) {
        footer.append(' ');
      }
    }
    pw.println(footer);
  }

/**
 * This method returns a map from each label to a counter of feature weights for that label.
 * Useful for feature analysis.
 *
 * @return a map of counters
 */
  public Map<L,Counter<F>> weightsAsMapOfCounters() {
    Map<L,Counter<F>> mapOfCounters = Generics.newHashMap();
    for(L label : labelIndex){
      int labelID = labelIndex.indexOf(label);
      Counter<F> c = new ClassicCounter<F>();
      mapOfCounters.put(label, c);
      for (F f : featureIndex) {
        c.incrementCount(f, weights[featureIndex.indexOf(f)][labelID]);
      }
    }
    return mapOfCounters;
  }

  /**
   * Print all features active for a particular datum and the weight that
   * the classifier assigns to each class for those features.
   */
  public void justificationOf(Datum<L, F> example, PrintWriter pw) {
    justificationOf(example, pw, null);
  }


  /**
   * Print all features in the classifier and the weight that they assign
   * to each class. The feature names are printed in sorted order.
   */
  public void dumpSorted() {
    Datum<L, F> allFeatures = new BasicDatum<L, F>(features(), (L)null);
    justificationOf(allFeatures, new PrintWriter(System.err, true), true);
  }

  /**
   * Print all features active for a particular datum and the weight that
   * the classifier assigns to each class for those features. Sorts by feature
   * name if 'sorted' is true.
   */
  public void justificationOf(Datum<L, F> example, PrintWriter pw, boolean sorted) {
    if(example instanceof RVFDatum<?, ?>)
    justificationOf(example, pw, null, sorted);
  }


  public Counter<L> scoresOf(Datum<L, F> example, Collection<L> possibleLabels) {
    Counter<L> scores = new ClassicCounter<L>();
    for (L l : possibleLabels) {
      if (labelIndex.indexOf(l) == -1) {
        continue;
      }
      double score = scoreOf(example, l);
      scores.setCount(l, score);
    }
    return scores;
  }

  /* -- looks like a failed attempt at micro-optimization --

  public L experimentalClassOf(Datum<L,F> example) {
    if(example instanceof RVFDatum<?, ?>) {
      throw new UnsupportedOperationException();
    }

    int labelCount = weights[0].length;
    //System.out.printf("labelCount: %d\n", labelCount);
    Collection<F> features = example.asFeatures();

    int[] featureInts = new int[features.size()];
    int fI = 0;
    for (F feature : features) {
      featureInts[fI++] = featureIndex.indexOf(feature);
    }
    //System.out.println("Features: "+features);
    double bestScore = Double.NEGATIVE_INFINITY;
    int bestI = 0;
    for (int i = 0; i < labelCount; i++) {
      double score = 0;
      for (int j = 0; j < featureInts.length; j++) {
        if (featureInts[j] < 0) continue;
        score += weights[featureInts[j]][i];
      }
      if (score > bestScore) {
        bestI = i;
        bestScore = score;
      }
      //System.out.printf("Score: %s(%d): %e\n", labelIndex.get(i), i, score);
    }
    //System.out.printf("label(%d): %s\n", bestI, labelIndex.get(bestI));;
    return labelIndex.get(bestI);
  }
  -- */

  @Override
  public L classOf(Datum<L, F> example) {
    if(example instanceof RVFDatum<?, ?>)return classOfRVFDatum((RVFDatum<L,F>)example);
    Counter<L> scores = scoresOf(example);
    return Counters.argmax(scores);
  }


  private L classOfRVFDatum(RVFDatum<L, F> example) {
    Counter<L> scores = scoresOfRVFDatum(example);
    return Counters.argmax(scores);
  }

  @Override
  @Deprecated
  public L classOf(RVFDatum<L, F> example) {
    Counter<L> scores = scoresOf(example);
    return Counters.argmax(scores);
  }

  /** Make a linear classifier from the parameters. The parameters are used, not copied.
   *
   *  @param weights The parameters of the classifier. The first index is the
   *                 featureIndex value and second index is the labelIndex value.
   * @param featureIndex An index from F to integers used to index the features in the weights array
   * @param labelIndex An index from L to integers used to index the labels in the weights array
   */
  public LinearClassifier(double[][] weights, Index<F> featureIndex, Index<L> labelIndex) {
    this.featureIndex = featureIndex;
    this.labelIndex = labelIndex;
    this.weights = weights;
    thresholds = new double[labelIndex.size()];
    Arrays.fill(thresholds, 0.0);
  }

  // todo: This is unused and seems broken (ignores passed in thresholds)
  public LinearClassifier(double[][] weights, Index<F> featureIndex, Index<L> labelIndex,
      double[] thresholds) throws Exception {
    this.featureIndex = featureIndex;
    this.labelIndex = labelIndex;
    this.weights = weights;
    if (thresholds.length != labelIndex.size())
      throw new Exception("Number of thresholds and number of labels do not match.");
    thresholds = new double[thresholds.length];
    int curr = 0;
    for (double tval : thresholds) {
      thresholds[curr++] = tval;
    }
    Arrays.fill(thresholds, 0.0);
  }

  private static <F, L> Counter<Pair<F, L>> makeWeightCounter(double[] weights, Index<Pair<F, L>> weightIndex) {
    Counter<Pair<F,L>> weightCounter = new ClassicCounter<Pair<F,L>>();
    for (int i = 0; i < weightIndex.size(); i++) {
      if (weights[i] == 0) {
        continue; // no need to save 0 weights
      }
      weightCounter.setCount(weightIndex.get(i), weights[i]);
    }
    return weightCounter;
  }

  public LinearClassifier(double[] weights, Index<Pair<F, L>> weightIndex) {
    this(makeWeightCounter(weights, weightIndex));
  }

  public LinearClassifier(Counter<? extends Pair<F, L>> weightCounter) {
    this(weightCounter, new ClassicCounter<L>());
  }

  public LinearClassifier(Counter<? extends Pair<F, L>> weightCounter, Counter<L> thresholdsC) {
    Collection<? extends Pair<F, L>> keys = weightCounter.keySet();
    featureIndex = new HashIndex<F>();
    labelIndex = new HashIndex<L>();
    for (Pair<F, L> p : keys) {
      featureIndex.add(p.first());
      labelIndex.add(p.second());
    }
    thresholds = new double[labelIndex.size()];
    for (L label : labelIndex) {
      thresholds[labelIndex.indexOf(label)] = thresholdsC.getCount(label);
    }
    weights = new double[featureIndex.size()][labelIndex.size()];
    Pair<F, L> tempPair = new Pair<F, L>();
    for (int f = 0; f < weights.length; f++) {
      for (int l = 0; l < weights[f].length; l++) {
        tempPair.first = featureIndex.get(f);
        tempPair.second = labelIndex.get(l);
        weights[f][l] = weightCounter.getCount(tempPair);
      }
    }
  }


  public void adaptWeights(Dataset<L, F> adapt,LinearClassifierFactory<L, F> lcf) {
    System.err.println("before adapting, weights size="+weights.length);
    weights = lcf.adaptWeights(weights,adapt);
    System.err.println("after adapting, weights size="+weights.length);
  }

  public double[][] weights() {
    return weights;
  }

  public void setWeights(double[][] newWeights) {
    weights = newWeights;
  }

  /**
   * Loads a classifier from a file.
   * Simple convenience wrapper for IOUtils.readFromString.
   */
  public static <L, F> LinearClassifier<L, F> readClassifier(String loadPath) {
    System.err.print("Deserializing classifier from " + loadPath + "...");

    try {
      ObjectInputStream ois = IOUtils.readStreamFromString(loadPath);
      LinearClassifier<L, F> classifier = ErasureUtils.<LinearClassifier<L, F>>uncheckedCast(ois.readObject());
      ois.close();
      return classifier;
    } catch (Exception e) {
      throw new RuntimeException("Deserialization failed: "+e.getMessage(), e);
    }
  }

  /**
   * Convenience wrapper for IOUtils.writeObjectToFile
   */
  public static void writeClassifier(LinearClassifier<?, ?> classifier, String writePath) {
    try {
      IOUtils.writeObjectToFile(classifier, writePath);
    } catch (Exception e) {
      throw new RuntimeException("Serialization failed: "+e.getMessage(), e);
    }
  }

  /**
   * Saves this out to a standard text file, instead of as a serialized Java object.
   * NOTE: this currently assumes feature and weights are represented as Strings.
   * @param file String filepath to write out to.
   */
  public void saveToFilename(String file) {
    try {
      File tgtFile = new File(file);
      BufferedWriter out = new BufferedWriter(new FileWriter(tgtFile));
      // output index first, blank delimiter, outline feature index, then weights
      labelIndex.saveToWriter(out);
      featureIndex.saveToWriter(out);
      int numLabels = labelIndex.size();
      int numFeatures = featureIndex.size();
      for (int featIndex=0; featIndex<numFeatures; featIndex++) {
        for (int labelIndex=0;labelIndex<numLabels;labelIndex++) {
          out.write(String.valueOf(featIndex));
          out.write(TEXT_SERIALIZATION_DELIMITER);
          out.write(String.valueOf(labelIndex));
          out.write(TEXT_SERIALIZATION_DELIMITER);
          out.write(String.valueOf(weight(featIndex, labelIndex)));
          out.write("\n");
        }
      }

      // write out thresholds: first item after blank is the number of thresholds, after is the threshold array values.
      out.write("\n");
      out.write(String.valueOf(thresholds.length));
      out.write("\n");
      for (double val : thresholds) {
        out.write(String.valueOf(val));
        out.write("\n");
      }
      out.close();
    } catch (Exception e) {
      System.err.println("Error attempting to save classifier to file="+file);
      e.printStackTrace();
    }
  }

}
