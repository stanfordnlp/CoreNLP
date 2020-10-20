package edu.stanford.nlp.stats;

import java.util.Random;

public class DirichletProcess<E> implements ProbabilityDistribution<E> {

  /**
   * 
   */
  private static final long serialVersionUID = -8653536087199951278L;
  private final ProbabilityDistribution<E> baseMeasure;
  private final double alpha;

  private final ClassicCounter<E> sampled;
  
  public DirichletProcess(ProbabilityDistribution<E> baseMeasure, double alpha) {
    this.baseMeasure = baseMeasure;
    this.alpha = alpha;
    this.sampled = new ClassicCounter<>();
    sampled.incrementCount(null, alpha);
  }

  public E drawSample(Random random) {
    E drawn = Counters.sample(sampled);
    if (drawn == null) {
      drawn = baseMeasure.drawSample(random);
    }
    sampled.incrementCount(drawn);
    return drawn;
  }

  public double numOccurances(E object) {
    if (object == null) {
      throw new RuntimeException("You cannot ask for the number of occurances of null.");
    }
    return sampled.getCount(object);
  }
  
  public double probabilityOf(E object) {
    if (object == null) {
      throw new RuntimeException("You cannot ask for the probability of null.");
    }

    if (sampled.keySet().contains(object)) {
      return sampled.getCount(object) / sampled.totalCount();
    } else {
      return 0.0;
    }
  }

  public double logProbabilityOf(E object) {
    return Math.log(probabilityOf(object));
  }

  public double probabilityOfNewObject() {
    return alpha / sampled.totalCount();
  }
  
}
