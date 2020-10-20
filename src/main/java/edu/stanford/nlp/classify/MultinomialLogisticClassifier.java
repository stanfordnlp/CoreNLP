package edu.stanford.nlp.classify;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.io.RuntimeIOException;
import edu.stanford.nlp.ling.Datum;
import edu.stanford.nlp.ling.RVFDatum;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.stats.Counters;
import edu.stanford.nlp.util.ErasureUtils;
import edu.stanford.nlp.util.Index;
import edu.stanford.nlp.util.Timing;
import edu.stanford.nlp.util.logging.Redwood;

/**
 * A multinomial logistic regression classifier. Please see FlippingProbsLogisticClassifierFactory
 * or ShiftParamsLogisticClassifierFactory for example use cases.
 *
 * This is classic multinomial logistic regression where you have one reference class (the last one) and
 * (numClasses - 1) times numFeatures weights, unlike the maxent/softmax regression we more normally use.
 *
 * @author jtibs
 */
public class MultinomialLogisticClassifier<L, F> implements ProbabilisticClassifier<L, F>, RVFClassifier<L, F> {

  private static final long serialVersionUID = 1L;

  /** A logger for this class */
  private static final Redwood.RedwoodChannels logger = Redwood.channels(MultinomialLogisticClassifier.class);

  private final double[][] weights;
  private final Index<F> featureIndex;
  private final Index<L> labelIndex;

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
    Counter<L> result = new ClassicCounter<>();
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

  private static <LL,FF> MultinomialLogisticClassifier<LL,FF> load(String path) {
    Timing t = new Timing();
    try (ObjectInputStream in = IOUtils.readStreamFromString(path)) {
      double[][] myWeights = ErasureUtils.uncheckedCast(in.readObject());
      Index<FF> myFeatureIndex = ErasureUtils.uncheckedCast(in.readObject());
      Index<LL> myLabelIndex = ErasureUtils.uncheckedCast(in.readObject());
      t.done(logger, "Loading classifier from " + path);
      return new MultinomialLogisticClassifier<>(myWeights, myFeatureIndex, myLabelIndex);
    } catch (IOException | ClassNotFoundException e) {
      throw new RuntimeIOException("Error loading classifier from " + path, e);
    }
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

    Map<L, Counter<F>> allweights = new HashMap<>();
    for(int i = 0; i < weights.length; i++){
      Counter<F> c = new ClassicCounter<>();
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
