package edu.stanford.nlp.optimization;

import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.stats.Counters;

/**
 * Hildreth algorithm for solving quadratic programming problems.
 * 
 * This code was taken directly from Ryan McDonald's MSTParser.
 * 
 * It is therefore licensed under the Common Public License 1.0. This license is 
 * <strong>not</strong> GPL compatible[1]. The code should not be included
 * in any GPL licensed public release of JavaNLP.
 * 
 * TODO:BadLicense
 *
 * [1] http://www.gnu.org/licenses/license-list.html#CommonPublicLicense10
 */

public class BadLicenseHildreth {

  
  /**
   * Runs the Hildreth algorithm for solving a quadratic program of the following form:
   * 
   * minimize | w |^2
   * such that:
   * 
   * w T a_i >= b_i for all i
   * 
   * Generally, we're using this for machine learning, where w is a vector of weight parameters,
   * a_i is a feature vector difference which we'd like to have bigger than b_i, which we use
   * to represent the loss.
   * 
   * @param a are Counters representing the vector a in the constraints
   * @param b are the real values on the right
   * @return the alpha multipliers for each a vector, such that 

   * Code in this method taken directly from Ryan McDonald's 
   * MST parser implementation.
   */
  public static double[] runHildreth(Counter[] a, double[] b) {

    int i;
    int max_iter = 10000;
    double eps = 0.00000001;
    double zero = 0.000000000001;

    double[] alpha = new double[b.length];

    double[] F = new double[b.length];
    double[] kkt = new double[b.length];
    double max_kkt = Double.NEGATIVE_INFINITY;

    int K = a.length;

    double[][] A = new double[K][K];
    boolean[] is_computed = new boolean[K];
    for (i = 0; i < K; i++) {
      A[i][i] = Counters.dotProduct(a[i], a[i]);
      is_computed[i] = false;
    }

    int max_kkt_i = -1;

    for (i = 0; i < F.length; i++) {
      F[i] = b[i];
      kkt[i] = F[i];
      if (kkt[i] > max_kkt) {
        max_kkt = kkt[i];
        max_kkt_i = i;
      }
    }

    int iter = 0;
    double diff_alpha;
    double try_alpha;
    double add_alpha;

    while (max_kkt >= eps && iter < max_iter) {

      diff_alpha = A[max_kkt_i][max_kkt_i] <= zero ? 0.0 : F[max_kkt_i] / A[max_kkt_i][max_kkt_i];
      try_alpha = alpha[max_kkt_i] + diff_alpha;
      add_alpha = 0.0;

      if (try_alpha < 0.0)
        add_alpha = -1.0 * alpha[max_kkt_i];
      else
        add_alpha = diff_alpha;

      alpha[max_kkt_i] = alpha[max_kkt_i] + add_alpha;

      if (!is_computed[max_kkt_i]) {
        for (i = 0; i < K; i++) {
          A[i][max_kkt_i] = Counters.dotProduct(a[i], a[max_kkt_i]); // for
                                                                          // version
                                                                          // 1
          is_computed[max_kkt_i] = true;
        }
      }

      for (i = 0; i < F.length; i++) {
        F[i] -= add_alpha * A[i][max_kkt_i];
        kkt[i] = F[i];
        if (alpha[i] > zero)
          kkt[i] = Math.abs(F[i]);
      }

      max_kkt = Double.NEGATIVE_INFINITY;
      max_kkt_i = -1;
      for (i = 0; i < F.length; i++)
        if (kkt[i] > max_kkt) {
          max_kkt = kkt[i];
          max_kkt_i = i;
        }

      iter++;
    }

    return alpha;
  }

}
