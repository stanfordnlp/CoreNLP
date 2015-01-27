package edu.stanford.nlp.classify;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.text.DecimalFormat;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import edu.stanford.nlp.ling.Datum;
import edu.stanford.nlp.ling.RVFDatum;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.stats.Counters;
import edu.stanford.nlp.util.ErasureUtils;
import edu.stanford.nlp.util.Index;

/**
 * A multinomial logistic regression classifier. Please see FlippingProbsLogisticClassifierFactory
 * or ShiftParamsLogisticClassifierFactory for an example use cases.
 * 
 * @author jtibs
 */
public class MultinomialLogisticClassifier<L, F> implements ProbabilisticClassifier<L, F>, RVFClassifier<L, F> {
  private static final long serialVersionUID = 1L;
  private double[][] weights;
  private Index<F> featureIndex;
  private Index<L> labelIndex;
  
  /**
   * @param weights A (numClasses - 1) by numFeatures matrix that holds the weight array for each
   * class. Note that only (numClasses - 1) rows are needed, as the probability for last class is
   * uniquely determined by the others.
   */
  public MultinomialLogisticClassifier(double[][] weights, Index<F> featureIndex, Index<L> labelIndex) {
    this.featureIndex = featureIndex;
    this.labelIndex = labelIndex;
    this.weights = weights;
  }
  
  @Override
  public Collection<L> labels() {
    return labelIndex.objectsList();
  }
  
  @Override
  public L classOf(Datum<L, F> example) {
    return Counters.argmax(scoresOf(example));
  }

  @Override
  public Counter<L> scoresOf(Datum<L, F> example) {
    return logProbabilityOf(example);
  }

  @Override
  public L classOf(RVFDatum<L, F> example) {
    return classOf((Datum<L, F>)example);
  }

  @Override
  public Counter<L> scoresOf(RVFDatum<L, F> example) {
    return scoresOf((Datum<L, F>)example);
  }

  @Override
  public Counter<L> probabilityOf(Datum<L, F> example) {
    // calculate the feature indices and feature values
    int[] featureIndices = LogisticUtils.indicesOf(example.asFeatures(), featureIndex);
   
    double[] featureValues;
    if (example instanceof RVFDatum<?, ?>) {
      Collection<Double> featureValuesCollection =
          ((RVFDatum<?, ?>) example).asFeaturesCounter().values();
      featureValues = LogisticUtils.convertToArray(featureValuesCollection);
    } else {
      featureValues = new double[example.asFeatures().size()];
      Arrays.fill(featureValues, 1.0);
    }
     
    // calculate probability of each class
    Counter<L> result = new ClassicCounter<L>();
    int numClasses = labelIndex.size();
    double[] sigmoids = LogisticUtils.calculateSigmoids(weights, featureIndices, featureValues);

    for (int c = 0; c < numClasses; c++) {
      L label = labelIndex.get(c);
      result.incrementCount(label, sigmoids[c]);
    }
    
    return result;
  }

  @Override
  public Counter<L> logProbabilityOf(Datum<L, F> example) {
    Counter<L> result = probabilityOf(example);
    Counters.logInPlace(result);
    return result;
  }
  
  private void load(String path) throws IOException, ClassNotFoundException {
    System.out.print("Loading classifier from " + path + "... ");
    
    ObjectInputStream in = new ObjectInputStream(new FileInputStream(path));
    weights = ErasureUtils.uncheckedCast(in.readObject());
    featureIndex = ErasureUtils.uncheckedCast(in.readObject());
    labelIndex = ErasureUtils.uncheckedCast(in.readObject());
    in.close();
    
    System.out.println("done.");
  }
  
  private void save(String path) throws IOException {
    System.out.print("Saving classifier to " + path + "... ");
    
    // make sure the directory specified by path exists
    int lastSlash = path.lastIndexOf(File.separator);
    if (lastSlash > 0) {
      File dir = new File(path.substring(0, lastSlash));
      if (! dir.exists())
        dir.mkdirs();
    }
    
    ObjectOutputStream out = new ObjectOutputStream(new FileOutputStream(path));
    out.writeObject(weights);
    out.writeObject(featureIndex);
    out.writeObject(labelIndex);
    out.close();
    
    System.out.println("done.");
  }

  public Map<L, Counter<F>> weightsAsGenericCounter() {

    Map<L, Counter<F>> allweights = new HashMap<L, Counter<F>>();
    for(int i = 0; i < weights.length; i++){
      Counter<F> c = new ClassicCounter<F>();
      L label  = labelIndex.get(i);
      double[] w =  weights[i];
      for (F f : featureIndex) {
        int indexf = featureIndex.indexOf(f);
        if(w[indexf] != 0.0)
          c.setCount(f, w[indexf]);

      }
      allweights.put(label, c);
    }
    return allweights;
  }
}
