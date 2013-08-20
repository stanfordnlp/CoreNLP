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

import java.io.Serializable;
import java.util.*;

import edu.stanford.nlp.ling.Datum;
import edu.stanford.nlp.ling.RVFDatum;
import edu.stanford.nlp.optimization.DiffFunction;
import edu.stanford.nlp.optimization.Minimizer;
import edu.stanford.nlp.optimization.QNMinimizer;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.util.ErasureUtils;
import edu.stanford.nlp.util.Index;
import edu.stanford.nlp.util.Pair;

/**
 * A Hierarchical Bayesian Logistic Classifier that allows arbitrary hierarchical tree structure of features. 
 * The additional information it needs is a list of feature-parent pairs and a map of features to values only for
 * those features that are the roots of the hierarchy. (These features will be clamped to the pre-defined values during training).
 *     
 *
 * @author Ramesh Nallapati nmramesh@cs.stanford.edu
 *
 * 
 * @param <L> The type of the labels in the Dataset
 * @param <F> The type of the features in the Dataset
 */
public class HierarchicalBayesianLogisticClassifier<L, F> implements Classifier<L, F>, Serializable, RVFClassifier<L, F> {
  
  /**
   * 
   */
  private static final long serialVersionUID = 6672245467246897192L;
  private double[] weights;
  private Index<F> featureIndex;
  private L[] classes = ErasureUtils.<L>mkTArray(Object.class,2);

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
  
  public Counter<String> weightsAsCounter() {
    Counter<String> c = new ClassicCounter<String>();
    for (F f : featureIndex) {
      c.incrementCount(classes[1]+" / "+f, weights[featureIndex.indexOf(f)]);
    }

    return c;
  }

  public Counter<F> weightsAsGenericCounter() {
    Counter<F> c = new ClassicCounter<F>();
    for (F f : featureIndex) {
      double w =  weights[featureIndex.indexOf(f)];
      if(w != 0.0)
        c.setCount(f, w);
    }
    return c;
  }

  public Index<F> getFeatureIndex() {
    return featureIndex;
  }

  public double[] getWeights() {
    return weights;
  }

  public Collection<L> labels() {
    Collection<L> l = new LinkedList<L>();
    l.add(classes[0]);
    l.add(classes[1]);
    return l;
  }

  public L classOf(Datum<L, F> datum) {
    if(datum instanceof RVFDatum<?,?>){
      return classOf((RVFDatum<L,F>) datum);
    }
    return classOf(datum.asFeatures());
  }
  
  public L classOf(RVFDatum<L, F> example) {
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
    Counter<F> fWts = new ClassicCounter<F>();
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
    Counter<F> fWts = new ClassicCounter<F>();
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
  public Counter<L> scoresOf(Datum<L, F> datum) {
    if(datum instanceof RVFDatum<?,?>)return scoresOf((RVFDatum<L,F>)datum);
    Collection<F> features = datum.asFeatures();
    double sum = scoreOf(features);
    Counter<L> c = new ClassicCounter<L>();
    c.setCount(classes[0], -sum);
    c.setCount(classes[1], sum);
    return c;
  }

  public Counter<L> scoresOf(RVFDatum<L, F> example) {
    Counter<F> features = example.asFeaturesCounter();
    double sum = scoreOf(features);
    Counter<L> c = new ClassicCounter<L>();
    c.setCount(classes[0], -sum);
    c.setCount(classes[1], sum);
    return c;
  }


  public double probabilityOf(Datum<L, F> example) {
    if(example instanceof RVFDatum<?,?>)return probabilityOf((RVFDatum<L,F>)example);
    return probabilityOf(example.asFeatures(), example.label());
  }

  public double probabilityOf(Collection<F> features, L label) {
    short sign = (short)(label.equals(classes[0]) ? 1 : -1);
    return 1.0 / (1.0 + Math.exp(sign * scoreOf(features)));
  }

  public double probabilityOf(RVFDatum<L, F> example) {
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
  public void trainWeightedData(GeneralDataset<L,F> data, List<Pair<F,F>> feature2parent, float[] dataWeights, Set<F> roots, Map<F,Pair<Double,Double>> parent2VarPair, boolean estimateVariance){
    if (data.labelIndex.size() != 2) {
      throw new RuntimeException("LogisticClassifier is only for binary classification!");
    }
    featureIndex = data.featureIndex;
    int[] feature2parentArr = augmentFeatureIndexWithPriors(data,feature2parent);
    weights = getInitialWeights(data.featureIndex,parent2VarPair);
    Minimizer<DiffFunction> minim;
    HierarchicalBayesianLogisticObjectiveFunction lof = null;
    if(data instanceof Dataset<?,?>)
      lof = new HierarchicalBayesianLogisticObjectiveFunction(data.numFeatureTypes(), data.getDataArray(), data.getLabelsArray(),dataWeights,feature2parentArr,getRootIDsSet(roots),getParentID2Var(parent2VarPair),estimateVariance);
    else if(data instanceof RVFDataset<?,?>)
      lof = new HierarchicalBayesianLogisticObjectiveFunction(data.numFeatureTypes(), data.getDataArray(), data.getValuesArray(), data.getLabelsArray(), dataWeights, feature2parentArr,getRootIDsSet(roots),getParentID2Var(parent2VarPair),estimateVariance);
    minim = new QNMinimizer(lof);
    weights = minim.minimize(lof, 1e-4, weights);

    
    classes[0] = data.labelIndex.get(0);
    classes[1] = data.labelIndex.get(1);
  }
  
  public void train(GeneralDataset<L, F> data, List<Pair<F,F>> feature2parent, Set<F> roots, Map<F,Pair<Double,Double>> parent2MeanVarPair, boolean estimateVariance) {
    if (data.labelIndex.size() != 2) {
      throw new RuntimeException("LogisticClassifier is only for binary classification!");
    }
    //printParent2numChildren(feature2parent);
    featureIndex = data.featureIndex;
    int[] feature2parentArr = augmentFeatureIndexWithPriors(data,feature2parent);
    weights = getInitialWeights(data.featureIndex,parent2MeanVarPair);
    Minimizer<DiffFunction> minim;
    HierarchicalBayesianLogisticObjectiveFunction lof = null;
    if(data instanceof Dataset<?,?>)
      lof = new HierarchicalBayesianLogisticObjectiveFunction(data.numFeatureTypes(), data.getDataArray(), data.getLabelsArray(),feature2parentArr, getRootIDsSet(roots),getParentID2Var(parent2MeanVarPair),estimateVariance);
    else if(data instanceof RVFDataset<?,?>)
      lof = new HierarchicalBayesianLogisticObjectiveFunction(data.numFeatureTypes(), data.getDataArray(), data.getValuesArray(), data.getLabelsArray(),feature2parentArr,getRootIDsSet(roots),getParentID2Var(parent2MeanVarPair),estimateVariance);
    minim = new QNMinimizer(lof);
    weights = minim.minimize(lof, 1e-4, weights); 

    
    classes[0] = data.labelIndex.get(0);
    classes[1] = data.labelIndex.get(1);
  }

  private int[] augmentFeatureIndexWithPriors(GeneralDataset<L,F> data, List<Pair<F,F>> feature2parentList){
    Index<F> featureIndex = data.featureIndex();
    //int numTimesParentDetected = 0;
    //int numTimesParentNotDetected = 0;
    for(Pair<F,F> featureParentPair: feature2parentList){
      F feature = featureParentPair.first();
      if(!featureIndex.contains(feature)){
        featureIndex.add(feature);
        //System.out.println("Adding child to the featureIndex:"+feature);
      }
      F parent = featureParentPair.second();
      if(!featureIndex.contains(parent)){
        featureIndex.add(parent);
        //System.out.println("Adding parent to the featureIndex:"+parent);
        //numTimesParentNotDetected++;
      }
      //else numTimesParentDetected++;
    }
    //System.out.println("Parents are detected "+numTimesParentDetected+" times and not detected "+numTimesParentNotDetected+" times.");
    int[] feature2parentArray = new int[featureIndex.size()];
    Arrays.fill(feature2parentArray, -1);
    for(Pair<F,F> featureParentPair: feature2parentList){
      F feature = featureParentPair.first();
      int featureID = featureIndex.indexOf(feature);
      F parent = featureParentPair.second();
      int parentID = featureIndex.indexOf(parent);
      feature2parentArray[featureID] = parentID; 
    }
    return feature2parentArray;
  }

  /**
   * 
   * @return initWeights
   */
  private double[] getInitialWeights(Index<F> featureIndex, Map<F,Pair<Double,Double>> parent2MeanVarPair){
    double[] initWeights = new double[featureIndex.size()];
    
    Arrays.fill(initWeights, 0.0);
    for(F f : parent2MeanVarPair.keySet()){
      int fID = featureIndex.indexOf(f);
      initWeights[fID] = parent2MeanVarPair.get(f).first;
    }
    return initWeights;
  }
  
  private Set<Integer> getRootIDsSet(Set<F> rootNames){
    Set<Integer> roots = new HashSet<Integer>();
    for(F feature : rootNames){
      //System.out.println("root:"+feature);
      int fID = featureIndex.indexOf(feature);
      //System.out.println("rootID:"+fID);
      roots.add(fID);
    }
    return roots;
  }
  
  
  private void printParent2numChildren(List<Pair<F,F>> feature2parentList){
    Map<F,List<F>> parent2children = new HashMap<F,List<F>>();
    for(Pair<F,F> feature2parent : feature2parentList){
      F feature = feature2parent.first;
      F parent = feature2parent.second;
      if(!parent2children.containsKey(parent))
        parent2children.put(parent, new ArrayList<F>());
      parent2children.get(parent).add(feature);
    }
    
    for(F parent : parent2children.keySet()){
      System.out.println(parent+":"+parent2children.get(parent).size());
    }
  }
  
  private Map<Integer,Double> getParentID2Var(Map<F,Pair<Double,Double>> parent2MeanVarPair){
    Map<Integer,Double> parentID2Var = new HashMap<Integer,Double>();
    for(F parent : parent2MeanVarPair.keySet()){
      int parentID = featureIndex.indexOf(parent);
      double var = parent2MeanVarPair.get(parent).second;
      parentID2Var.put(parentID, var);
    }
    return parentID2Var;
  }
  
}
