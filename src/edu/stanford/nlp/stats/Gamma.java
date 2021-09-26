package edu.stanford.nlp.stats;

import java.util.Random;

/**
 * Represents a Gamma distribution.  The way that samples are drawn is
 * stolen from Yee Whye Teh's code.  It won't give the probability of a variable because
 * gamma is a continuous distribution.  should it give the mass at that point?
 *
 * @author Jenny Finkel
 */

public class Gamma implements ProbabilityDistribution<Double> {

  /**
   * 
   */
  private static final long serialVersionUID = -2992079318379176178L;
  public final double alpha;

  public Gamma(double alpha) {
    this.alpha = alpha;
  }
  
  public Double drawSample(Random random) {
    return drawSample(random, alpha);
  }
  
  public static Double drawSample(Random random, double alpha) {
    if (alpha <= 0.0) {
      /* Not well defined, set to zero and skip. */
      return 0.0;
    } else if ( alpha == 1.0 ) {
      /* Exponential */
      return -Math.log(Math.random());
    } else if (alpha < 1.0) {
      /* Use Johnks generator */
      double cc = 1.0 / alpha;
      double dd = 1.0 / (1.0-alpha);
      while (true) {
        double xx = Math.pow(Math.random(), cc);
        double yy = xx + Math.pow(Math.random(), dd);
        if (yy <= 1.0) {
        return -Math.log(Math.random()) * xx / yy;
        }
      }
    } else {
      /* Use bests algorithm */
      double bb = alpha - 1.0;
      double cc = 3.0 * alpha - 0.75;
      while (true) {
        double uu = Math.random();
        double vv = Math.random();
        double ww = uu * (1.0 - uu);
        double yy = Math.sqrt(cc / ww) * (uu - 0.5);
        double xx = bb + yy;
        if (xx >= 0) {
          double zz = 64.0 * ww * ww * ww * vv * vv;
          if ( ( zz <= (1.0 - 2.0 * yy * yy / xx) ) ||
               ( Math.log(zz) <= 2.0 * (bb * Math.log(xx / bb) - yy) ) ) {
            return xx;
          }
        }
      }
    }        
  }


  // Generalized Random sampling.
  public static double drawSample(Random r, double a, double b) {
    return drawSample(r,a)*b;
  }

  public double probabilityOf(Double x) {
    return 0.0; // cos it's not discrete
  }

  public double logProbabilityOf(Double x) {
    return 0.0; // cos it's not discrete
  }

  @Override
  public int hashCode() {
    return Double.valueOf(alpha).hashCode();
  }

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof Gamma)) { return false; }
    return ((Gamma)o).alpha == alpha;
  }

}
