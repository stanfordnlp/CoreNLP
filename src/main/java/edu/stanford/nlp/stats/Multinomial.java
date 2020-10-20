package edu.stanford.nlp.stats;

import java.util.Random;

/**
 * a multinomial distribution.  pretty straightforward.  specify the parameters with
 * a counter.  It is assumed that the Counter's keySet() contains all of the parameters (i.e., there are not other
 * possible values which are set to 0).  It makes a copy of the Counter, so tha parameters cannot be changes,
 * and it normalizes the values if they are not already normalized.
 *
 * @author Jenny Finkel
 */

public class Multinomial<E> implements ProbabilityDistribution<E> {

  /**
   * 
   */
  private static final long serialVersionUID = -697457414113362926L;
  private Counter<E> parameters;

  public Multinomial(Counter<E> parameters) {
    double totalMass = parameters.totalCount();
    if (totalMass <= 0.0) {
      throw new RuntimeException("total mass must be positive!");
    }

    this.parameters = new ClassicCounter<>();
    for (E object : parameters.keySet()) {
      double oldCount = parameters.getCount(object);
      if (oldCount < 0.0) {
        throw new RuntimeException("no negative parameters allowed!");
      }
      this.parameters.setCount(object, oldCount/totalMass);
    }
  }

  public Counter<E> getParameters() {
    return new ClassicCounter<>(parameters);
  }
  
  public double probabilityOf(E object) {
    if (!parameters.keySet().contains(object)) {
      throw new RuntimeException("Not a valid object for this multinomial!");
    }
    return parameters.getCount(object);
  }

  public double logProbabilityOf(E object) {
    if (!parameters.keySet().contains(object)) {
      throw new RuntimeException("Not a valid object for this multinomial!");
    }
    return Math.log(parameters.getCount(object));
  }

  public E drawSample(Random random) {
    double r = random.nextDouble();
    double sum = 0.0;
    for (E object : parameters.keySet()) {
      sum += parameters.getCount(object);
      if (sum  >= r) {
        return object;
      }
    }
    throw new RuntimeException("This point should never be reached");
  }

  @SuppressWarnings("unchecked")
  @Override
  public boolean equals(Object o) {
    if (!(o instanceof Multinomial)) { return false; }
    Multinomial otherMultinomial = (Multinomial)o;
    return parameters.equals(otherMultinomial.parameters);
  }

  private int hashCode = -1;
  @Override
  public int hashCode() {
    if (hashCode == -1) {
      hashCode = parameters.hashCode() + 17;
    }
    return hashCode;
  }
  
}
