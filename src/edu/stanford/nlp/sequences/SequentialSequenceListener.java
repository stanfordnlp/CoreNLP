package edu.stanford.nlp.sequences;

import java.util.Arrays;

/**
 * @author Mengqiu Wang
 */
public class SequentialSequenceListener implements SequenceListener {

  SequenceListener[] models = null;
  int[] modelLenBound = null;

  /**
   * Informs this sequence model that the value of the element at position pos has changed.
   * This allows this sequence model to update its internal model if desired.
   *
   */
  public void updateSequenceElement(int[] sequence, int pos, int oldVal) {
    int modelIndex = 0;
    for (; modelIndex < modelLenBound.length; modelIndex++) {
      if (pos < modelLenBound[modelIndex])
        break;
    }
    int begin = 0;
    if (modelIndex > 0)
      begin = modelLenBound[modelIndex-1];
    int end = modelLenBound[modelIndex];
    int[] subseq = Arrays.copyOfRange(sequence, begin, end);
    int newPos = pos - begin;
    models[modelIndex].updateSequenceElement(subseq, newPos, oldVal);
  }

  /**
   * Informs this sequence model that the value of the whole sequence is initialized to sequence
   *
   */
  public void setInitialSequence(int[] sequence) {
    int modelIndex = 0;
    for (; modelIndex < modelLenBound.length; modelIndex++) {
      int begin = 0;
      if (modelIndex > 0)
        begin = modelLenBound[modelIndex-1];
      int end = modelLenBound[modelIndex];
      int[] subseq = Arrays.copyOfRange(sequence, begin, end);
      models[modelIndex].setInitialSequence(subseq); 
    }
  }

  public SequentialSequenceListener(SequenceListener[] models, int[] listenerLengths){
    this.models = models;
    this.modelLenBound = new int[models.length];
    int currLenBound = 0;
    for (int i = 0; i < models.length; i++) {
      currLenBound += listenerLengths[i];
      this.modelLenBound[i] = currLenBound;
    }
  }
}
