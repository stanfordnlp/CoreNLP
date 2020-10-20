package edu.stanford.nlp.stats;

/**
 * An interface for classes which are probability distributions over other probability distributions.
 * This class could also represent non-conjugate priors, but we thought this name would be more 
 * representative of the common case.
 * 
 * @author grenager
 * @author jrfinkel
 *
 * @param <T>
 * @param <E>
 */
public interface ConjugatePrior<T extends ProbabilityDistribution<E>, E> extends ProbabilityDistribution<T> {
  
  /**
   * Marginalizes over all possible likelihood distributions to give the marginal probability of 
   * the observation.
   * 
   */
  double getPredictiveProbability(E observation);
  
  double getPredictiveLogProbability(E observation);
  
  /**
   * Gets the posterior probability of the observation, after conditioning on all of the evidence.
   * Marginalizes over all possible likelihood distributions.
   * 
   */
  double getPosteriorPredictiveProbability(Counter<E> evidence, E observation);
  
  double getPosteriorPredictiveLogProbability(Counter<E> evidence, E observation);
  
  /**
   * Gets the ConjugatePrior which results from conditioning on all of these evidence.
   * 
   */
  ConjugatePrior<T,E> getPosteriorDistribution(Counter<E> evidence);
  
}
