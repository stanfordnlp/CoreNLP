package edu.stanford.nlp.classify;

import edu.stanford.nlp.optimization.AbstractCachingDiffFunction;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;


/**
 * Maximizes the conditional likelihood with a given prior.
 * Because the problem is binary, optimizations are possible that
 * cannot be done in LogConditionalObjectiveFunction.
 *
 * @author Ramesh Nallapati
 */

public class HierarchicalBayesianLogisticObjectiveFunction extends AbstractCachingDiffFunction {
  //Ideally should extend LogisticObjectiveFunction, but unfortunately LogisticObjectiveFunction enforces a quadratic prior by default,
  //Hence we have to make our own function recycling most of the code from LogisticObjectiveFunction.

  private final int numFeatures;
  private final int[][] data;
  private final double[][] dataValues; 
  private final int[] labels;
  private final int[] feature2parent;
  private final Map<Integer,List<Integer>> parent2children;
  protected float[] dataweights = null;
  private final Set<Integer> roots;
  private boolean estimateVariance = false;
  private Map<Integer,Double> parent2variance;

  @Override
  public int domainDimension() {
    return numFeatures;
  }
  
  @Override
  protected void calculate(double[] x) {

    value = 0.0;
    Arrays.fill(derivative, 0.0);
    
    
    //compute the value and derivatives from the prior terms
    for(int i = 0; i < numFeatures; i++){
      assert !(new Double(x[i]).isNaN());  
      if(feature2parent[i] >= 0){
        int parent = feature2parent[i];
        assert parent2variance.get(parent) != 0 : "variance of parent "+parent+" is zero.";
        value += 0.5*Math.pow((x[i]-x[parent]),2)/parent2variance.get(parent);
        derivative[i] += (x[i] - x[parent])/parent2variance.get(parent);        
          //System.out.printf("before datalikeilihood:  feature:%d parent:%d w[feature]: %f w[parent]: %f\n",i,parent,x[i],x[parent]);
      }
      if(parent2children.containsKey(i) && !roots.contains(i)){
        //System.out.println("computing derivative of parent:");
        //System.exit(0);
        assert parent2variance.get(i) != 0 : "variance of parent "+i+" is zero.";
        for(int child: parent2children.get(i)){
          derivative[i] += (x[i] -x[child])/parent2variance.get(i);
        }
      }
      
    }
   
    if(estimateVariance){
      for(int parent : parent2children.keySet()){
        List<Integer> children = parent2children.get(parent);
        if(!roots.contains(parent) && children.size()>0){
          double var = 0;
          for(int child : children)
            var+= Math.pow((x[child] - x[parent]),2);
          var/= children.size();
          //assert var != 0 : "variance is zero for parent:"+parent+" with number of children:"+children.size();
          if(var > 1e-3){ //don't allow too small variances to prevent instabilities in learning.
            //System.out.println("parent:"+parent+" computed variance:"+var);
            parent2variance.put(parent,var);
          }
        }      
      }
    }
    
    if (dataValues != null) {
      calculateRVF(x);
      return;
    }
    
    for (int d = 0; d < data.length; d++) {
      int[] features = data[d];
      double sum = 0;

      for (int f = 0; f < features.length; f++) {
        sum += x[features[f]];
      }

      double expSum, derivativeIncrement;

      if (labels[d] == 0) {
        expSum = Math.exp(sum);
        derivativeIncrement = 1.0 / (1.0 + (1.0 / expSum));
      } else {
        expSum = Math.exp(-sum);
        derivativeIncrement = -1.0 / (1.0 + (1.0 / expSum));
      }

      if (dataweights == null) {
        value += Math.log(1.0 + expSum);
      } else {
        value += Math.log(1.0 + expSum) * dataweights[d];
        derivativeIncrement *= dataweights[d];
      }

      for (int f = 0; f < features.length; f++) {
        derivative[features[f]] += derivativeIncrement;
      }
    }
    
    
  }

  protected void calculateRVF(double[] x) {
    
    for (int d = 0; d < data.length; d++) {
      int[] features = data[d];
      double[] values = dataValues[d];
      double sum = 0;

      for (int f = 0; f < features.length; f++) {
        sum += x[features[f]]*values[f];
      }

      double expSum, derivativeIncrement;

      if (labels[d] == 0) {
        expSum = Math.exp(sum);
        derivativeIncrement = 1.0 / (1.0 + (1.0 / expSum));
      } else {
        expSum = Math.exp(-sum);
        derivativeIncrement = -1.0 / (1.0 + (1.0 / expSum));
      }

      if (dataweights == null) {
        value += Math.log(1.0 + expSum);
      } else {
        value += Math.log(1.0 + expSum) * dataweights[d];
        derivativeIncrement *= dataweights[d];
      }

      for (int f = 0; f < features.length; f++) {
        derivative[features[f]] += values[f]*derivativeIncrement;
      }
    }
    
   
  }

  private Map<Integer,List<Integer>> getfeature2children(int[] feature2parent){
    Map<Integer,List<Integer>> feature2children = new HashMap<Integer,List<Integer>>();
    for(int i = 0; i < numFeatures; i++){
      int parent = feature2parent[i];
      if(parent >= 0){
        if(!feature2children.containsKey(parent))
          feature2children.put(parent, new ArrayList<Integer>());
        feature2children.get(parent).add(i);
      }
    }
    //for(int parent : feature2children.keySet()){
      //System.out.println("parent:"+parent+" numChildren:"+feature2children.get(parent).size());
    //}
    return feature2children;
  }
  
  public HierarchicalBayesianLogisticObjectiveFunction(int numFeatures, int[][] data, int[] labels, int[] feature2parent,Set<Integer> roots,Map<Integer,Double> parent2variance, boolean estimateVariance) {
    this(numFeatures,data,null,labels,null,feature2parent, roots, parent2variance, estimateVariance);
  }

  public HierarchicalBayesianLogisticObjectiveFunction(int numFeatures, int[][] data, int[] labels, float[] dataWeights, int[] feature2parent, Set<Integer> roots,Map<Integer,Double> parent2variance, boolean estimateVariance) {    
    this(numFeatures, data, null, labels, dataWeights, feature2parent, roots,parent2variance, estimateVariance);    
  }
  
  public HierarchicalBayesianLogisticObjectiveFunction(int numFeatures, int[][] data, double[][] values,  int[] labels, int[] feature2parent, Set<Integer> roots,Map<Integer,Double> parent2variance, boolean estimateVariance) {
    this(numFeatures, data, values, labels, null, feature2parent, roots,parent2variance, estimateVariance); 
  }
  
  public HierarchicalBayesianLogisticObjectiveFunction(int numFeatures, int[][] data, double[][] values,  int[] labels, float[] dataWeights, int[] feature2parent, Set<Integer> roots,Map<Integer,Double> parent2variance, boolean estimateVariance) {
    this.numFeatures = numFeatures;
    this.data = data;
    this.dataValues = values;    
    this.labels = labels;
    this.dataweights = dataWeights;
    this.feature2parent = feature2parent;
    this.parent2children = getfeature2children(feature2parent);
    this.parent2variance = parent2variance;
    this.estimateVariance = estimateVariance;
    //System.out.println("Number of parents = "+parent2children.size());
    //for(int f: parent2children.keySet()){
      //System.out.println("Parent ID:"+f+ " number of children:"+parent2children.get(f).size());
    //}
    this.roots = roots;
    //System.out.println("Number of roots:"+roots.size());
    //for(int f : roots){
      //System.out.println("root id:"+f);
    //}
  }
}
