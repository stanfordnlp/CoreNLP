package edu.stanford.nlp.classify;

import edu.stanford.nlp.ling.Datum;
import edu.stanford.nlp.ling.RVFDatum;
import edu.stanford.nlp.math.ArrayMath;
import edu.stanford.nlp.optimization.AbstractCachingDiffFunction;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.util.Generics;
import edu.stanford.nlp.util.Triple;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;


/**
 * Implementation of Generalized Expectation Objective function for
 * an I.I.D. log-linear model. See Mann and McCallum, ACL 2008.
 * IMPORTANT: the current implementation is only correct as long as
 * the labeled features passed to GE are binary.
 * However, other features are allowed to be real valued.
 * The original paper also discusses GE only for binary features.
 *
 * @author Ramesh Nallapati (nmramesh@cs.stanford.edu)
 */

public class GeneralizedExpectationObjectiveFunction<L,F> extends AbstractCachingDiffFunction {

  private final GeneralDataset<L,F> labeledDataset;
  private final List<? extends Datum<L,F>> unlabeledDataList;
  private final List<F> geFeatures;
  private final LinearClassifier<L,F> classifier;
  private double[][] geFeature2EmpiricalDist; //empirical label distributions of each feature. Really final but java won't let us.
  private List<List<Integer>> geFeature2DatumList; //an inverted list of active unlabeled documents for each feature. Really final but java won't let us.

  private final int numFeatures;
  private final int numClasses;


  @Override
  public int domainDimension() {
    return numFeatures * numClasses;
  }

  int classOf(int index) {
    return index % numClasses;
  }

  int featureOf(int index) {
    return index / numClasses;
  }

  protected int indexOf(int f, int c) {
    return f * numClasses + c;
  }

  public double[][] to2D(double[] x) {
    double[][] x2 = new double[numFeatures][numClasses];
    for (int i = 0; i < numFeatures; i++) {
      for (int j = 0; j < numClasses; j++) {
        x2[i][j] = x[indexOf(i, j)];
      }
    }
    return x2;
  }

  @Override
  protected void calculate(double[] x) {
    classifier.setWeights(to2D(x));
    if (derivative == null) {
      derivative = new double[x.length];
    } else {
      Arrays.fill(derivative, 0.0);
    }
    Counter<Triple<Integer,Integer,Integer>> feature2classPairDerivatives = new ClassicCounter<Triple<Integer,Integer,Integer>>();

    value = 0.0;
    for(int n = 0; n < geFeatures.size(); n++){
      //F feature = geFeatures.get(n);
      double[] modelDist = new double[numClasses];
      Arrays.fill(modelDist,0);

    //go over the unlabeled active data to compute expectations
      List<Integer> activeData = geFeature2DatumList.get(n);
      for (Integer activeDatum : activeData) {
        Datum<L, F> datum = unlabeledDataList.get(activeDatum);
        double[] probs = getModelProbs(datum);
        for (int c = 0; c < numClasses; c++) {
          modelDist[c] += probs[c];
        }
        updateDerivative(datum, probs, feature2classPairDerivatives); //computes p(y_d)*(1-p(y_d))*f_d for all active features.
      }

      //now  compute the value (KL-divergence) and the final value of the derivative.
      if (activeData.size()>0) {
        for (int c = 0; c < numClasses; c++) {
          modelDist[c]/= activeData.size();
        }
        smoothDistribution(modelDist);

        for(int c = 0; c < numClasses; c++)
          value += -geFeature2EmpiricalDist[n][c]*Math.log(modelDist[c]);

        for(int f = 0; f < labeledDataset.featureIndex().size(); f++) {
          for(int c = 0; c < numClasses; c++) {
            int wtIndex = indexOf(f,c);
            for(int cPrime = 0;  cPrime < numClasses; cPrime++){
              derivative[wtIndex] += feature2classPairDerivatives.getCount(new Triple<Integer,Integer,Integer>(f,c,cPrime))*geFeature2EmpiricalDist[n][cPrime]/modelDist[cPrime];
            }
            derivative[wtIndex] /= activeData.size();
          }
        } // loop over each feature for derivative computation
      } //end of if condition
    } //loop over each GE feature
  }


   private void updateDerivative(Datum<L,F> datum, double[] probs,Counter<Triple<Integer,Integer,Integer>> feature2classPairDerivatives){
     for (F feature : datum.asFeatures()) {
       int fID = labeledDataset.featureIndex.indexOf(feature);
       if (fID >= 0) {
         for (int c = 0; c < numClasses; c++) {
           for (int cPrime = 0; cPrime < numClasses; cPrime++) {
             if (cPrime == c) {
               feature2classPairDerivatives.incrementCount(new Triple<Integer,Integer,Integer>(fID,c,cPrime), - probs[c]*(1-probs[c])*valueOfFeature(feature,datum));
             } else {
               feature2classPairDerivatives.incrementCount(new Triple<Integer,Integer,Integer>(fID,c,cPrime), probs[c]*probs[cPrime]*valueOfFeature(feature,datum));
             }
           }
         }
       }
     }
   }

   /*
    * This method assumes the feature already exists in the datum.
    */
   private double valueOfFeature(F feature, Datum<L,F> datum){
      if(datum instanceof RVFDatum)
        return ((RVFDatum<L,F>)datum).asFeaturesCounter().getCount(feature);
      else return 1.0;
    }

    private void computeEmpiricalStatistics(List<F> geFeatures){
      //allocate memory to the containers and initialize them
      geFeature2EmpiricalDist = new double[geFeatures.size()][labeledDataset.labelIndex.size()];
      geFeature2DatumList = new ArrayList<List<Integer>>(geFeatures.size());
      Map<F,Integer> geFeatureMap = Generics.newHashMap();
      Set<Integer> activeUnlabeledExamples = Generics.newHashSet();
      for(int n = 0; n < geFeatures.size(); n++){
        F geFeature = geFeatures.get(n);
        geFeature2DatumList.add(new ArrayList<Integer>());
        Arrays.fill(geFeature2EmpiricalDist[n], 0);
        geFeatureMap.put(geFeature,n);
      }

      //compute the empirical label distribution for each GE feature
      for(int i = 0; i < labeledDataset.size(); i++){
        Datum<L,F> datum = labeledDataset.getDatum(i);
        int labelID = labeledDataset.labelIndex.indexOf(datum.label());
        for(F feature : datum.asFeatures()){
          if(geFeatureMap.containsKey(feature)){
            int geFnum = geFeatureMap.get(feature);
            geFeature2EmpiricalDist[geFnum][labelID]++;
          }
        }
      }
      //now normalize and smooth the label distribution for each feature.
      for(int n = 0;  n < geFeatures.size(); n++){
        ArrayMath.normalize(geFeature2EmpiricalDist[n]);
        smoothDistribution(geFeature2EmpiricalDist[n]);
      }

      //now build the inverted index from each GE feature to unlabeled datums that contain it.
      for (int i = 0; i < unlabeledDataList.size(); i++) {
        Datum<L,F> datum = unlabeledDataList.get(i);
        for (F feature : datum.asFeatures()) {
          if (geFeatureMap.containsKey(feature)) {
            int geFnum = geFeatureMap.get(feature);
            geFeature2DatumList.get(geFnum).add(i);
            activeUnlabeledExamples.add(i);
          }
        }
      }
      System.out.println("Number of active unlabeled examples:"+activeUnlabeledExamples.size());
    }

    private static void smoothDistribution(double [] dist) {
      //perform Laplace smoothing
      double epsilon = 1e-6;
      for(int i = 0; i < dist.length; i++)
        dist[i] += epsilon;
      ArrayMath.normalize(dist);
    }

    private double[] getModelProbs(Datum<L,F> datum){
      double[] condDist = new double[labeledDataset.numClasses()];
      Counter<L> probCounter = classifier.probabilityOf(datum);
      for(L label : probCounter.keySet()){
        int labelID = labeledDataset.labelIndex.indexOf(label);
        condDist[labelID] = probCounter.getCount(label);
      }
      return condDist;
    }

  public GeneralizedExpectationObjectiveFunction(GeneralDataset<L, F> labeledDataset, List<? extends Datum<L,F>> unlabeledDataList,List<F> geFeatures) {
    System.out.println("Number of labeled examples:"+labeledDataset.size+"\nNumber of unlabeled examples:"+unlabeledDataList.size());
    System.out.println("Number of GE features:"+geFeatures.size());
    this.numFeatures = labeledDataset.numFeatures();
    this.numClasses = labeledDataset.numClasses();
    this.labeledDataset = labeledDataset;
    this.unlabeledDataList = unlabeledDataList;
    this.geFeatures = geFeatures;
    this.classifier = new LinearClassifier<L,F>(null,labeledDataset.featureIndex,labeledDataset.labelIndex);
    computeEmpiricalStatistics(geFeatures);
    //empirical distributions don't change with iterations, so compute them only once.
    //model distributions will have to be recomputed every iteration though.
  }

}
