package edu.stanford.nlp.sequences;

/**
 * @author grenager
 *         Date: Apr 18, 2005
 */
public class FactoredSequenceListener implements SequenceListener {

  SequenceListener model1;
  SequenceListener model2;
  SequenceListener[] models = null;

  /**
   * Informs this sequence model that the value of the element at position pos has changed.
   * This allows this sequence model to update its internal model if desired.
   *
   */
  public void updateSequenceElement(int[] sequence, int pos, int oldVal) {
    if(models != null){
      for (SequenceListener model : models) model.updateSequenceElement(sequence, pos, oldVal);
      return; 
    }
    model1.updateSequenceElement(sequence, pos, oldVal);
    model2.updateSequenceElement(sequence, pos, oldVal);
  }

  /**
   * Informs this sequence model that the value of the whole sequence is initialized to sequence
   *
   */
  public void setInitialSequence(int[] sequence) {
    if(models != null){
      for (SequenceListener model : models) model.setInitialSequence(sequence);
      return;
    }
    model1.setInitialSequence(sequence);
    model2.setInitialSequence(sequence);
  }

  public FactoredSequenceListener(SequenceListener model1, SequenceListener model2) {
    this.model1 = model1;
    this.model2 = model2;
  }
  
  
  public FactoredSequenceListener(SequenceListener[] models){
    this.models = models;
  }
}
