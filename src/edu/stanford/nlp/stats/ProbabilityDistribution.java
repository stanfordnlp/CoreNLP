package edu.stanford.nlp.stats;

import java.util.Random;

/**
 * This is an interface for probability measures, which will allow
 * samples to be drawn and the probability of objects computed.
 *
 * @author Jenny Finkel
 */

public interface ProbabilityDistribution<E> extends java.io.Serializable {

  public double probabilityOf(E object) ;
  public double logProbabilityOf(E object) ;
  public E drawSample(Random random) ;
  
}
