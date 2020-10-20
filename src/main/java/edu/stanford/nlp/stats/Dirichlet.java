package edu.stanford.nlp.stats;

import java.util.Random;

/**
 * simple dirichlet distribution.
 *
 * @author Jenny Finkel
 */

public class Dirichlet<E> implements ConjugatePrior<Multinomial<E>, E> {

  private static final long serialVersionUID = 1L;

  private Counter<E> parameters;

  public Dirichlet(Counter<E> parameters) {
    checkParameters(parameters);
    this.parameters = new ClassicCounter<>(parameters);
  }

  private void checkParameters(Counter<E> parameters) {
    for (E o : parameters.keySet()) {
      if (parameters.getCount(o) < 0.0) {
        throw new RuntimeException("Parameters must be non-negative!");
      }
    }
    if (parameters.totalCount() <= 0.0) {
      throw new RuntimeException("Parameters must have positive mass!");
    }
  }

  public Multinomial<E> drawSample(Random random) {
    return drawSample(random, parameters);
  }
  
  public static <F> Multinomial<F> drawSample(Random random, Counter<F> parameters) {
    Counter<F> multParameters = new ClassicCounter<>();
    double sum = 0.0;
    for (F o : parameters.keySet()) {
      double parameter = Gamma.drawSample(random, parameters.getCount(o));
      sum += parameter;
      multParameters.setCount(o, parameter);
    }
    for (F o : multParameters.keySet()) {
      multParameters.setCount(o, multParameters.getCount(o)/sum);
    }
    return new Multinomial<>(multParameters);
  }

  // Faster sampling from a Dirichlet.
  public static double[] drawSample(Random random, double[] parameters) {
    double sum = 0.0;
    double[] result = new double[parameters.length];
    for(int i = 0; i <  parameters.length; ++i) {
     double parameter = Gamma.drawSample(random, parameters[i]);
     sum += parameter;
     result[i] = parameter;
    }
    for(int i = 0; i < parameters.length; ++i) {
      result[i] /= sum;
    }
    return result;
  }



  public static double sampleBeta(double a, double b, Random random) {
    Counter<Boolean> c = new ClassicCounter<>();
    c.setCount(true, a);
    c.setCount(false, b);
    Multinomial<Boolean> beta = (new Dirichlet<>(c)).drawSample(random);
    return beta.probabilityOf(true);
  }
  
  public double getPredictiveProbability(E object) {
    return parameters.getCount(object) / parameters.totalCount();
  }
  
  public double getPredictiveLogProbability(E object) {
    return Math.log(getPredictiveProbability(object));
  }
  
  public Dirichlet<E> getPosteriorDistribution(Counter<E> counts) {
    Counter<E> newParameters = new ClassicCounter<>(parameters);
    Counters.addInPlace(newParameters, counts);
    return new Dirichlet<>(newParameters);
  }
  
  public double getPosteriorPredictiveProbability(Counter<E> counts, E object) {
    double numerator = parameters.getCount(object) + counts.getCount(object);
    double denominator = parameters.totalCount() + counts.totalCount();
    return numerator / denominator;
  }

  public double getPosteriorPredictiveLogProbability(Counter<E> counts, E object) {
    return Math.log(getPosteriorPredictiveProbability(counts, object));
  }
    
  public double probabilityOf(Multinomial<E> object) {
    // TODO Auto-generated method stub
    return 0;
  }

  // Quick hack method for metropolis
  public static double unnormalizedLogProbabilityOf(double[] mult, double[] params) {
    double sum = 0.0;
    for(int i =0; i < params.length; ++i)  {
      if(mult[i] > 0)
        sum += (params[i] -1 )* Math.log(mult[i]);
    }
    return sum;
  }

  public double logProbabilityOf(Multinomial<E> object) {
    // TODO Auto-generated method stub
    return 0;
  }

  @Override
  public String toString() {
    return Counters.toBiggestValuesFirstString(parameters, 50);
  }
  
}
