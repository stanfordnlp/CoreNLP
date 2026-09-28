package edu.stanford.nlp.stats;

/**
 * An interace for drawing samples from the label
 * space of an object.  The classifiers themselves are
 * sampleable.  For instance, a parser can be
 * sampleable and then vend Sampler instances
 * based on specific inputs (words in the sentence).
 * The Sampler would then return parse trees (over
 * that particular sentence, not over all sentences)
 * drawn from
 * the underlying distribution.
 *
 * @author Jenny Finkel
 */
public interface Sampler<T> {

  /**
   * @return labels (of type T) drawn from the underlying
   * distribution for the observation this Sampler was
   * created for.
   */
  public T drawSample();

}
