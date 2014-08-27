package edu.stanford.nlp.ie;

import java.util.List;

import edu.stanford.nlp.sequences.SequenceListener;
import edu.stanford.nlp.sequences.SequenceModel;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.Index;

/**
 * Uniform prior to be used for generic Gibbs inference in the ie.crf.CRFClassifier 
 * @author Mihai
 *
 */
public class UniformPrior<IN extends CoreMap> implements SequenceModel, SequenceListener {
  
  protected int[] sequence;
  protected int backgroundSymbol;
  protected int numClasses;
  protected int[] possibleValues;
  protected Index<String> classIndex;
  protected List<IN> doc;

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

  public double scoreOf(int[] sequence) {
    return 0;
  }
  
  public  double[] scoresOf (int[] sequence, int position) {
    double[] probs = new double[numClasses];
    for(int i = 0; i < probs.length; i ++)
      probs[i] = 0.0;
    return probs;
  }

  public int[] getPossibleValues(int position) {
    return possibleValues;
  }

  public int leftWindow() {
    return Integer.MAX_VALUE; // not Markovian!
  }

  public int length() {
    return doc.size();
  }

  public int rightWindow() {
    return Integer.MAX_VALUE; // not Markovian!
  }

  public double scoreOf(int[] sequence, int position) {
    return 0.0;
  }

  public void setInitialSequence(int[] sequence) {
  }

  public void updateSequenceElement(int[] sequence, int pos, int oldVal) {
  }
}
