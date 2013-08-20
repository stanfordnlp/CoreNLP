package edu.stanford.nlp.stats;

import java.util.Random;

import junit.framework.TestCase;

public class GaussianTest extends TestCase {
  
  Random random = new Random();
  
  public void testSample() {
    checkSamples(0.0, 1.0);
    checkSamples(11.0, 1.0);
    checkSamples(-3.0, 1.0);
    checkSamples(-3.0, 0.1);
    checkSamples(-3.0, 1000.0);
    checkSamples(-30000.0, 1000.0);
  }

  static final int SAMPLE_SIZE = 100000;

  private void checkSamples(double mean, double var) {
    // collect 1000 examples
    // should have half over
    int numHigh = 0;
    int numLow = 0;
    // should have two-thirds inside
    int numIn = 0;
    int numOut = 0;
    for (int i=0; i<SAMPLE_SIZE; i++) {
      double sample = Gaussian.drawSample(random, mean, var);
      if (sample>mean) {
        numHigh++;
      } else {
        numLow++;
      }
      if (Math.abs(sample-mean)<Math.sqrt(var)) {
        numIn++;
      } else {
        numOut++;
      }
    }
//    System.err.println(numHigh + " " + numLow + " " + numIn + " " + numOut);
    assertTrue(Math.abs(numHigh-numLow)<SAMPLE_SIZE*0.1); // roughly balanced
    assertTrue(Math.abs(numIn-(numOut*2))<SAMPLE_SIZE*0.2); // roughly two-thirds inside stdev
  }
  
  public void testProbabilityOf() {
    checkProbabilityOf(0.0, 1.0);
    checkProbabilityOf(11.0, 1.0);
    checkProbabilityOf(-3.0, 1.0);
    checkProbabilityOf(-3.0, 0.1);
    checkProbabilityOf(-3.0, 1000.0);
    checkProbabilityOf(-30000.0, 1000.0);
  }

  private void checkProbabilityOf(double mean, double var) {
    double mp = Gaussian.probabilityOf(mean, mean, var);
    assertTrue(mp>Gaussian.probabilityOf(mean-var, mean, var));
    assertTrue(mp>Gaussian.probabilityOf(mean+var, mean, var));
    assertTrue(mp>Gaussian.probabilityOf(mean+0.0001, mean, var));
    assertTrue(mp>Gaussian.probabilityOf(mean-0.0001, mean, var));
    assertTrue(Gaussian.probabilityOf(1e100, mean, var)==0.0);
    // now try to sum up lots of little boxes from -var to var and make sure they have mass of close to 0.66
    double mass = 0.0;
    double stdev = Math.sqrt(var);
    for (int i=0; i<1000; i++) {
      double x = mean - (stdev * i / 1000.0);
      double prob = Gaussian.probabilityOf(x, mean, var);
      mass += prob * stdev / 1000.0;
    }
    for (int i=0; i<1000; i++) {
      double x = mean + (stdev * i / 1000.0);
      double prob = Gaussian.probabilityOf(x, mean, var);
      mass += prob * stdev / 1000.0;
    }
//    System.err.println(mass);
    assertTrue(Math.abs(mass - 0.66)<0.05); // pretty close to 0.66 of the mass
  }

}
