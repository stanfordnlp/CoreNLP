package edu.stanford.nlp.ie;

import java.util.List;

import edu.stanford.nlp.sequences.ListeningSequenceModel;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.Index;

/**
 * Uniform prior to be used for generic Gibbs inference in the ie.crf.CRFClassifier.
 * If used, CRF will do generic Gibbs inference without any priors.
 *
 * @author Mihai
 */
public class UniformPrior<IN extends CoreMap> implements ListeningSequenceModel {

  protected int[] sequence;
  protected final int backgroundSymbol;
  protected final int numClasses;
  protected final int[] possibleValues;
  protected final Index<String> classIndex;
  protected final List<IN> doc;

  public UniformPrior(String backgroundSymbol, Index<String> classIndex, List<IN> doc) {
    this.classIndex = classIndex;
    this.backgroundSymbol = classIndex.indexOf(backgroundSymbol);
    this.numClasses = classIndex.size();
    this.possibleValues = new int[numClasses];
    for (int i=0; i<numClasses; i++) {
      possibleValues[i] = i;
    }
    this.doc = doc;
  }

  @Override
  public double scoreOf(int[] sequence) {
    return 0;
  }

  @Override
  public  double[] scoresOf (int[] sequence, int position) {
    double[] probs = new double[numClasses];
    for(int i = 0; i < probs.length; i ++)
      probs[i] = 0.0;
    return probs;
  }

  @Override
  public int[] getPossibleValues(int position) {
    return possibleValues;
  }

  @Override
  public int leftWindow() {
    return Integer.MAX_VALUE; // not Markovian!
  }

  @Override
  public int length() {
    return doc.size();
  }

  @Override
  public int rightWindow() {
    return Integer.MAX_VALUE; // not Markovian!
  }

  @Override
  public double scoreOf(int[] sequence, int position) {
    return 0.0;
  }

  @Override
  public void setInitialSequence(int[] sequence) {
  }

  @Override
  public void updateSequenceElement(int[] sequence, int pos, int oldVal) {
  }

}
