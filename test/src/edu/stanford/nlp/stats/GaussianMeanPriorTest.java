package edu.stanford.nlp.stats;

import junit.framework.TestCase;
import no.uib.cipr.matrix.*;

public class GaussianMeanPriorTest extends TestCase {

  
  public void testPosterior() {
    
    // start in 1 dimension
    GaussianMeanPrior prior = new  GaussianMeanPrior(new double[] {0.0}, new double[] {10.0}, new double[] {1.0});
    
    // now pass in NO observations, and get out the predictive prob
    double m1 = prior.getMean().get(0);
    double v1 = prior.getVar().get(0,0);
    
    ClassicCounter<Vector> observations = new ClassicCounter<Vector>();

    // now make one observation
    observations.setCount(new DenseVector(new double[] {5.0}), 1.0);
    GaussianMeanPrior posterior = prior.getPosteriorDistribution(observations);
    double m2 = posterior.getMean().get(0);
    double v2 = posterior.getVar().get(0,0);
    assertTrue(m2>m1); // mean should go up
    assertTrue(v2<v1); // variance overall should go down
    
    // now make more observations
    observations.clear();
    observations.setCount(new DenseVector(new double[] {5.0}), 10.0);
    posterior = posterior.getPosteriorDistribution(observations);
    double m3 = posterior.getMean().get(0);
    double v3 = posterior.getVar().get(0,0);
    assertTrue(m3>m2); // mean should go up
    assertTrue(v3<v2); // variance overall should go down

    // now make more observations
    observations.clear();
    observations.setCount(new DenseVector(new double[] {-5.0}), 10.0);
    posterior = posterior.getPosteriorDistribution(observations);
    double m4 = posterior.getMean().get(0);
    double v4 = posterior.getVar().get(0,0);
    assertTrue(m4<m3); // mean should go down
    assertTrue(v3<v2); // variance overall should go down

  }
}
