// Stanford Classifier - a multiclass maxent classifier
// LogisticClassifier
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
// along with this program.  If not, see http://www.gnu.org/licenses/ .
//
// For more information, bug reports, fixes, contact:
//    Christopher Manning
//    Dept of Computer Science, Gates 2A
//    Stanford CA 94305-9020
//    USA
//    Support/Questions: java-nlp-user@lists.stanford.edu
//    Licensing: java-nlp-support@lists.stanford.edu
//    https://nlp.stanford.edu/software/classifier.html

package edu.stanford.nlp.classify;

import java.io.File;
import java.util.*;

import edu.stanford.nlp.ling.Datum;
import edu.stanford.nlp.ling.RVFDatum;
import edu.stanford.nlp.optimization.DiffFunction;
import edu.stanford.nlp.optimization.Minimizer;
import edu.stanford.nlp.optimization.QNMinimizer;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.objectbank.ObjectBank;
import edu.stanford.nlp.util.ErasureUtils;
import edu.stanford.nlp.util.Index;
import edu.stanford.nlp.util.ReflectionLoading;
import edu.stanford.nlp.util.StringUtils;

/**
 * A classifier for binary logistic regression problems.
 * This uses the standard statistics textbook formulation of binary
 * logistic regression, which is more efficient than using the
 * LinearClassifier class.
 *
 * @author Galen Andrew
 * @author Sarah Spikes (sdspikes@cs.stanford.edu) (Templatization)
 * @author Ramesh Nallapati nmramesh@cs.stanford.edu {@link #justificationOf(Collection)}
 *
 * @param <L> The type of the labels in the Dataset
 * @param <F> The type of the features in the Dataset
 */
public class LogisticClassifier<L, F> implements Classifier<L, F>, RVFClassifier<L, F> /* Serializable */ {

  //TODO make it implement ProbabilisticClassifier as well. --Ramesh 12/03/2009.
  /**
   *
   */
  private static final long serialVersionUID = 6672245467246897192L;
  private double[] weights;
  private Index<F> featureIndex;
  private L[] classes = ErasureUtils.<L>mkTArray(Object.class,2);
  @Deprecated
  private LogPrior prior;
  @Deprecated
  private boolean biased = false;

  @Override
  public String toString() {
    if (featureIndex == null) {
      return "";
    }

    StringBuilder sb = new StringBuilder();
    for (F f : featureIndex) {
      sb.append(classes[1]).append(" / ").append(f).append(" = ").append(weights[featureIndex.indexOf(f)]);
    }

    return sb.toString();
  }

  public L getLabelForInternalPositiveClass(){
    return classes[1];
  }

  public L getLabelForInternalNegativeClass(){
    return classes[0];
  }

  public Counter<F> weightsAsCounter() {
    Counter<F> c = new ClassicCounter<>();
    for (F f : featureIndex) {
      double w =  weights[featureIndex.indexOf(f)];
      if (w != 0.0) {
        c.setCount(f, w);
      }
    }
    return c;
  }

  public Index<F> getFeatureIndex() {
    return featureIndex;
  }

  public double[] getWeights() {
    return weights;
  }


  public LogisticClassifier(double[] weights, Index<F> featureIndex, L[] classes){
    this.weights = weights;
    this.featureIndex = featureIndex;
    this.classes = classes;
  }


  @Deprecated //use  LogisticClassifierFactory instead
  public LogisticClassifier(boolean biased) {
    this(new LogPrior(LogPrior.LogPriorType.QUADRATIC), biased);
  }

  @Deprecated //use  in LogisticClassifierFactory instead.
  public LogisticClassifier(LogPrior prior) {
    this.prior = prior;
  }


  @Deprecated //use  in LogisticClassifierFactory instead
  public LogisticClassifier(LogPrior prior, boolean biased) {
    this.prior = prior;
    this.biased = biased;
  }

  @Override
  public Collection<L> labels() {
    Collection<L> l = new LinkedList<>();
    l.add(classes[0]);
    l.add(classes[1]);
    return l;
  }

  @Override
  public L classOf(Datum<L, F> datum) {
    if(datum instanceof RVFDatum<?,?>){
      return classOfRVFDatum((RVFDatum<L,F>) datum);
    }
    return classOf(datum.asFeatures());
  }

  @Override
  @Deprecated //use classOf(Datum) instead.
  public L classOf(RVFDatum<L, F> example) {
    return classOf(example.asFeaturesCounter());
  }

  private L classOfRVFDatum(RVFDatum<L, F> example) {
    return classOf(example.asFeaturesCounter());
  }

  public L classOf(Counter<F> features) {
    if (scoreOf(features) > 0) {
      return classes[1];
    }
    return classes[0];
  }

  public L classOf(Collection<F> features) {
    if (scoreOf(features) > 0) {
      return classes[1];
    }
    return classes[0];
  }


  public double scoreOf(Collection<F> features) {
    double sum = 0;
    for (F feature : features) {
      int f = featureIndex.indexOf(feature);
      if (f >= 0) {
        sum += weights[f];
      }
    }
    return sum;
  }

  public double scoreOf(Counter<F> features) {
    double sum = 0;
    for (F feature : features.keySet()) {
      int f = featureIndex.indexOf(feature);
      if (f >= 0) {
        sum += weights[f]*features.getCount(feature);
      }
    }
    return sum;
  }
  /*
   * returns the weights to each feature assigned by the classifier
   * nmramesh@cs.stanford.edu
   */
  public Counter<F> justificationOf(Counter<F> features){
    Counter<F> fWts = new ClassicCounter<>();
    for (F feature : features.keySet()) {
      int f = featureIndex.indexOf(feature);
      if (f >= 0) {
        fWts.incrementCount(feature,weights[f]*features.getCount(feature));
      }
    }
    return fWts;
  }
  /**
   * returns the weights assigned by the classifier to each feature
   */
  public Counter<F> justificationOf(Collection<F> features){
    Counter<F> fWts = new ClassicCounter<>();
    for (F feature : features) {
      int f = featureIndex.indexOf(feature);
      if (f >= 0) {
        fWts.incrementCount(feature,weights[f]);
      }
    }
    return fWts;
  }

  /**
   * returns the scores for both the classes
   */
  @Override
  public Counter<L> scoresOf(Datum<L, F> datum) {
    if(datum instanceof RVFDatum<?,?>)return scoresOfRVFDatum((RVFDatum<L,F>)datum);
    Collection<F> features = datum.asFeatures();
    double sum = scoreOf(features);
    Counter<L> c = new ClassicCounter<>();
    c.setCount(classes[0], -sum);
    c.setCount(classes[1], sum);
    return c;
  }


  @Override
  @Deprecated //use scoresOfDatum(Datum) instead.
  public Counter<L> scoresOf(RVFDatum<L, F> example) {
    return scoresOfRVFDatum(example);
  }


  private Counter<L> scoresOfRVFDatum(RVFDatum<L, F> example) {
    Counter<F> features = example.asFeaturesCounter();
    double sum = scoreOf(features);
    Counter<L> c = new ClassicCounter<>();
    c.setCount(classes[0], -sum);
    c.setCount(classes[1], sum);
    return c;
  }

  public double probabilityOf(Datum<L, F> example) {
    if (example instanceof RVFDatum<?,?>) {
      return probabilityOfRVFDatum((RVFDatum<L,F>)example);
    }
    return probabilityOf(example.asFeatures(), example.label());
  }

  public double probabilityOf(Collection<F> features, L label) {
    short sign = (short)(label.equals(classes[0]) ? 1 : -1);
    return 1.0 / (1.0 + Math.exp(sign * scoreOf(features)));
  }

  public double probabilityOf(RVFDatum<L, F> example) {
    return probabilityOfRVFDatum(example);
  }

  private double probabilityOfRVFDatum(RVFDatum<L, F> example) {
    return probabilityOf(example.asFeaturesCounter(), example.label());
  }

  public double probabilityOf(Counter<F> features, L label) {
    short sign = (short)(label.equals(classes[0]) ? 1 : -1);
    return 1.0 / (1.0 + Math.exp(sign * scoreOf(features)));
  }

  /**
   * Trains on weighted dataset.
   * @param dataWeights weights of the data.
   */
  @Deprecated //Use LogisticClassifierFactory to train instead.
  public void trainWeightedData(GeneralDataset<L,F> data, float[] dataWeights){
    if (data.labelIndex.size() != 2) {
      throw new RuntimeException("LogisticClassifier is only for binary classification!");
    }

    Minimizer<DiffFunction> minim;
      LogisticObjectiveFunction lof = null;
      if(data instanceof Dataset<?,?>)
        lof = new LogisticObjectiveFunction(data.numFeatureTypes(), data.getDataArray(), data.getLabelsArray(), prior,dataWeights);
      else if(data instanceof RVFDataset<?,?>)
        lof = new LogisticObjectiveFunction(data.numFeatureTypes(), data.getDataArray(), data.getValuesArray(), data.getLabelsArray(), prior,dataWeights);
      minim = new QNMinimizer(lof);
      weights = minim.minimize(lof, 1e-4, new double[data.numFeatureTypes()]);

    featureIndex = data.featureIndex;
    classes[0] = data.labelIndex.get(0);
    classes[1] = data.labelIndex.get(1);
  }

  @Deprecated //Use LogisticClassifierFactory to train instead.
  public void train(GeneralDataset<L, F> data) {
    train(data, 0.0, 1e-4);
  }

  @Deprecated //Use LogisticClassifierFactory to train instead.
  public void train(GeneralDataset<L, F> data, double l1reg, double tol) {
    if (data.labelIndex.size() != 2) {
      throw new RuntimeException("LogisticClassifier is only for binary classification!");
    }

    Minimizer<DiffFunction> minim;
    if (!biased) {
      LogisticObjectiveFunction lof = null;
      if(data instanceof Dataset<?,?>)
        lof = new LogisticObjectiveFunction(data.numFeatureTypes(), data.getDataArray(), data.getLabelsArray(), prior);
      else if(data instanceof RVFDataset<?,?>)
        lof = new LogisticObjectiveFunction(data.numFeatureTypes(), data.getDataArray(), data.getValuesArray(), data.getLabelsArray(), prior);
      if (l1reg > 0.0) {
        minim = ReflectionLoading.loadByReflection("edu.stanford.nlp.optimization.OWLQNMinimizer", l1reg);
      } else {
        minim = new QNMinimizer(lof);
      }
      weights = minim.minimize(lof, tol, new double[data.numFeatureTypes()]);
    } else {
      BiasedLogisticObjectiveFunction lof = new BiasedLogisticObjectiveFunction(data.numFeatureTypes(), data.getDataArray(), data.getLabelsArray(), prior);
      if (l1reg > 0.0) {
        minim = ReflectionLoading.loadByReflection("edu.stanford.nlp.optimization.OWLQNMinimizer", l1reg);
      } else {
        minim = new QNMinimizer(lof);
      }
      weights = minim.minimize(lof, tol, new double[data.numFeatureTypes()]);
    }

    featureIndex = data.featureIndex;
    classes[0] = data.labelIndex.get(0);
    classes[1] = data.labelIndex.get(1);
  }

  /** This runs a simple train and test regime.
   *  The data file format is one item per line, space separated, with first the class label
   *  and then a bunch of (categorical) string features.
   *
   *  @param args The arguments/flags are: -trainFile trainFile -testFile testFile [-l1reg num] [-biased]
   *  @throws Exception
   */
  public static void main(String[] args) throws Exception {
    Properties prop = StringUtils.argsToProperties(args);

    double l1reg = Double.parseDouble(prop.getProperty("l1reg","0.0"));

    Dataset<String, String> ds = new Dataset<>();
    for (String line : ObjectBank.getLineIterator(new File(prop.getProperty("trainFile")))) {
      String[] bits = line.split("\\s+");
      Collection<String> f = new LinkedList<>(Arrays.asList(bits).subList(1, bits.length));
      String l = bits[0];
      ds.add(f, l);
    }

    ds.summaryStatistics();

    boolean biased = prop.getProperty("biased", "false").equals("true");
    LogisticClassifierFactory<String, String> factory = new LogisticClassifierFactory<>();
    LogisticClassifier<String, String> lc = factory.trainClassifier(ds, l1reg, 1e-4, biased);

    for (String line : ObjectBank.getLineIterator(new File(prop.getProperty("testFile")))) {
      String[] bits = line.split("\\s+");
      Collection<String> f = new LinkedList<>(Arrays.asList(bits).subList(1, bits.length));
      //String l = bits[0];
      String g = lc.classOf(f);
      double prob = lc.probabilityOf(f, g);
      System.out.printf("%4.3f\t%s\t%s%n", prob, g, line);
    }
  }

}
