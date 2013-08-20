package edu.stanford.nlp.stats;

/**
 * This is a class which vends {@link Sampler}s.  Classifier
 * like objects should subclass it, and it can vend
 * a {@link Sampler} for a particular input (If this where a Parser it
 * would take sentences), from which you can draw samples
 * from the label space of the input (in the parser case.
 * parse trees over the sentence).
 *
 * @author Jenny Finkel
 */

public interface Sampleable<T1,T2> {

  public Sampler<T2> getSampler(T1 input) ;
  
}
