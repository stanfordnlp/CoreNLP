package edu.stanford.nlp.math;

import java.util.Collection;

import edu.stanford.nlp.util.Triple;
import edu.stanford.nlp.util.logging.Redwood;

/**
 * The class {@code SloppyMath} contains methods for performing basic
 * numeric operations.  In some cases, such as max and min, they cut a few
 * corners in
 * the implementation for the sake of efficiency.  In particular, they may
 * not handle special notions like NaN and -0.0 correctly.  This was the
 * origin of the class name, but many other methods are just useful
 * math additions, such as logAdd.  This class just has static math methods.
 *
 * @author Christopher Manning
 * @version 2003/01/02
 */
public final class SloppyMath  {

  /** A logger for this class */
  private static final Redwood.RedwoodChannels log = Redwood.channels(SloppyMath.class);

  private SloppyMath() {}  // this class is just static methods.

  /** Round a double to the nearest integer, via conventional rules
   *  (.5 rounds up, .49 rounds down), and return the result, still as a double.
   *
   *  @param x What to round
   *  @return The rounded value
   */
  public static double round(double x) {
    return Math.floor(x + 0.5d);
  }

  /** Round a double to the given number of decimal places,
   *  rounding to the nearest value via conventional rules (5 rounds up, 49
   *  rounds down).
   *  E.g. round(3.1416, 2) == 3.14, round(431.5, -2) == 400,
   *  round(431.5, 0) = 432
   */
  public static double round(double x, int precision) {
    double power = Math.pow(10.0, precision);
    return round(x * power) / power;
  }


  /* --- extra min and max methods; see also ArrayMath for ones that operate on arrays and varargs */

  /* Note: Math.max(a, b) and Math.min(a, b) do no extra checks when
   * a and b are int or long; they are simply {@code a >= b ? a : b},
   * so you can just call those methods and no extra methods for these
   * are needed here. (And in 2020, it's not clear whether code is useful
   * even for the floating point ones, since there may be intrinsics?)
   */

  /**
   * max() that works on three integers.  Like many of the other max() functions in this class,
   * doesn't perform special checks like NaN or -0.0f to save time.
   *
   * @return The maximum of three int values.
   */
  public static int max(int a, int b, int c) {
    int ma = a;
    if (b > ma) {
      ma = b;
    }
    if (c > ma) {
      ma = c;
    }
    return ma;
  }

  public static int max(Collection<Integer> vals) {
    if (vals.isEmpty()) { throw new RuntimeException(); }
    int max = Integer.MIN_VALUE;

    for (int i : vals) {
      if (i > max) { max = i; }
    }

    return max;
  }

  /**
   * Returns the greater of two {@code float} values.  That is,
   * the result is the argument closer to positive infinity. If the
   * arguments have the same value, the result is that same
   * value.  Does none of the special checks for NaN or -0.0f that
   * {@code Math.max} does.
   *
   * @param a an argument.
   * @param b another argument.
   * @return the larger of {@code a} and {@code b}.
   */
  public static float max(float a, float b) {
    return (a >= b) ? a : b;
  }

  /**
   * Returns the greater of two {@code double} values.  That
   * is, the result is the argument closer to positive infinity. If
   * the arguments have the same value, the result is that same
   * value.  Does none of the special checks for NaN or -0.0f that
   * {@code Math.max} does.
   *
   * @param a an argument.
   * @param b another argument.
   * @return the larger of {@code a} and {@code b}.
   */
  public static double max(double a, double b) {
    return (a >= b) ? a : b;
  }

  /**
   * Returns the minimum of three int values.
   */
  public static int min(int a, int b, int c) {
    int mi = a;
    if (b < mi) {
      mi = b;
    }
    if (c < mi) {
      mi = c;
    }
    return mi;
  }

  /**
   * Returns the smaller of two {@code float} values.  That is,
   * the result is the value closer to negative infinity. If the
   * arguments have the same value, the result is that same
   * value.  Does none of the special checks for NaN or -0.0f that
   * {@code Math.max} does.
   *
   * @param a an argument.
   * @param b another argument.
   * @return the smaller of {@code a} and {@code b.}
   */
  public static float min(float a, float b) {
    return (a <= b) ? a : b;
  }


  /**
   * Returns the smaller of two {@code double} values.  That
   * is, the result is the value closer to negative infinity. If the
   * arguments have the same value, the result is that same
   * value.  Does none of the special checks for NaN or -0.0f that
   * {@code Math.max} does.
   *
   * @param a an argument.
   * @param b another argument.
   * @return the smaller of {@code a} and {@code b}.
   */
  public static double min(double a, double b) {
    return (a <= b) ? a : b;
  }


  /** Returns a mod where the sign of the answer is the same as the sign of the second
   *  argument.  This is how languages like Python do it. Helpful for array accesses.
   *
   * @param num The number
   * @param modulus The modulus
   * @return num mod modulus, where the sign of the answer is the same as the sign of modulus
   */
  public static int pythonMod(int num, int modulus) {
    // This is: num < 0 ? num % modulus + modulus: num % modulus, but avoids a test-and-branch
    return (num % modulus + modulus) % modulus;
  }

  /**
   * @return an approximation of the log of the Gamma function of x.  Laczos Approximation
   * Reference: Numerical Recipes in C
   * http://www.library.cornell.edu/nr/cbookcpdf.html
   * from www.cs.berkeley.edu/~milch/blog/versions/blog-0.1.3/blog/distrib
   */
  public static double lgamma(double x) {
    double[] cof = {76.18009172947146, -86.50532032941677,
      24.01409824083091,-1.231739572450155,
      0.1208650973866179e-2,-0.5395239384953e-5};
    double xxx = x;
    double tmp = x + 5.5;
    tmp -= ((x + 0.5) * Math.log(tmp));
    double ser = 1.000000000190015;
    for (int j = 0; j < 6; j++) {
      xxx++;
      ser += cof[j] / xxx;
    }
    return -tmp + Math.log(2.5066282746310005*ser / x);
  }

  /**
   * Returns true if the argument is a "dangerous" double to have
   * around, namely one that is infinite, NaN or zero.
   */
  public static boolean isDangerous(double d) {
    return Double.isInfinite(d) || Double.isNaN(d) || d == 0.0;
  }

  /**
   * Returns true if the argument is a "very dangerous" double to have
   * around, namely one that is infinite or NaN.
   */
  public static boolean isVeryDangerous(double d) {
    return Double.isInfinite(d) || Double.isNaN(d);
  }

  public static boolean isCloseTo(double a, double b) {
    if (a>b) {
      return (a-b)<1e-4;
    } else {
      return (b-a)<1e-4;
    }
  }

  /**
   * If a difference is bigger than this in log terms, then the sum or
   * difference of them will just be the larger (to 12 or so decimal
   * places for double, and 7 or 8 for float).
   */
  static final double LOGTOLERANCE = 30.0;
  static final float LOGTOLERANCE_F = 20.0f;


  /** Approximation to gamma function.  See e.g., http://www.rskey.org/CMS/index.php/the-library/11 .
   *  Fairly accurate, especially for n greater than 8.
   */
  public static double gamma(double n) {
    return Math.sqrt(2.0*Math.PI/n) * Math.pow((n/Math.E)*Math.sqrt(n*Math.sinh((1.0/n)+(1/(810*Math.pow(n,6))))),n);
  }

  /**
   * Convenience method for log to a different base.
   */
  public static double log(double num, double base) {
    return Math.log(num)/Math.log(base);
  }

  /**
   * Returns the log of the sum of two numbers, which are
   * themselves input in log form.  This uses natural logarithms.
   * Reasonable care is taken to do this as efficiently as possible
   * (under the assumption that the numbers might differ greatly in
   * magnitude), with high accuracy, and without numerical overflow.
   * Also, handle correctly the case of arguments being -Inf (e.g.,
   * probability 0).
   *
   * @param lx First number, in log form
   * @param ly Second number, in log form
   * @return {@code log(exp(lx) + exp(ly))}
   */
  public static float logAdd(float lx, float ly) {
    float max, negDiff;
    if (lx > ly) {
      max = lx;
      negDiff = ly - lx;
    } else {
      max = ly;
      negDiff = lx - ly;
    }
    return (max == Float.NEGATIVE_INFINITY || negDiff < -LOGTOLERANCE_F) ? max : //
      max + (float) Math.log(1.0 + Math.exp(negDiff));
  }

  /**
   * Returns the log of the sum of two numbers, which are
   * themselves input in log form.  This uses natural logarithms.
   * Reasonable care is taken to do this as efficiently as possible
   * (under the assumption that the numbers might differ greatly in
   * magnitude), with high accuracy, and without numerical overflow.
   * Also, handle correctly the case of arguments being -Inf (e.g.,
   * probability 0).
   *
   * @param lx First number, in log form
   * @param ly Second number, in log form
   * @return {@code log(exp(lx) + exp(ly))}
   */
  public static double logAdd(double lx, double ly) {
    double max, negDiff;
    if (lx > ly) {
      max = lx;
      negDiff = ly - lx;
    } else {
      max = ly;
      negDiff = lx - ly;
    }
    return (max == Double.NEGATIVE_INFINITY || negDiff < -LOGTOLERANCE) ? max : //
      max + Math.log(1 + Math.exp(negDiff));
  }

  /**
   * Computes n choose k in an efficient way.  Works with
   * k == 0 or k == n but undefined if k &lt; 0 or k &gt; n
   *
   * @return fact(n) / fact(k) * fact(n-k)
   */
  public static int nChooseK(int n, int k) {
    k = Math.min(k, n - k);
    if (k == 0) {
      return 1;
    }
    int accum = n;
    for (int i = 1; i < k; i++) {
      accum *= (n - i);
      accum /= i;
    }
    return accum / k;
  }

  /**
   * Returns an approximation to Math.pow(a,b) that is ~27x faster
   * with a margin of error possibly around ~10%.  From
   * http://martin.ankerl.com/2007/10/04/optimized-pow-approximation-for-java-and-c-c/
   */
  public static double pow(final double a, final double b) {
    final int x = (int) (Double.doubleToLongBits(a) >> 32);
    final int y = (int) (b * (x - 1072632447) + 1072632447);
    return Double.longBitsToDouble(((long) y) << 32);
  }

  /**
   * Exponentiation like we learned in grade school:
   * multiply b by itself e times.  Uses power of two trick.
   * e must be nonnegative!!!  no checking!!!  For e &lt;= 0,
   * the exponent is treated as 0, and 1 is returned.  0^0 also
   * returns 1. Biased to do quickly small exponents, like the CRF needs.
   * Note that some code claims you can get more speed ups with special cases:
   * http://sourceforge.net/p/jafama/code/ci/master/tree/src/net/jafama/FastMath.java
   * but I couldn't verify any gains beyond special casing 2. May depend on workload.
   *
   * @param b base
   * @param e exponent
   * @return b^e
   */
  public static int intPow(int b, int e) {
    if (e <= 1) {
      if (e == 1) {
        return b;
      } else {
        return 1; // this is also what you get for e < 0 !
      }
    } else {
      if (e == 2) {
        return b * b;
      } else {
        int result = 1;
        while (e > 0) {
          if ((e & 1) != 0) {
            result *= b;
          }
          b *= b;
          e >>= 1;
        }
        return result;
      }
    }
  }

  /**
     * Exponentiation like we learned in grade school:
     * multiply b by itself e times.  Uses power of two trick.
     * e must be nonnegative!!!  no checking!!!
     *
     * @param b base
     * @param e exponent
     * @return b^e
     */
  public static float intPow(float b, int e) {
    float result = 1.0f;
    float currPow = b;
    while (e > 0) {
      if ((e & 1) != 0) {
        result *= currPow;
      }
      currPow *= currPow;
      e >>= 1;
    }
    return result;
  }

  /**
   * Exponentiation like we learned in grade school:
   * multiply b by itself e times.  Uses power of two trick.
   * e must be nonnegative!!!  no checking!!!
   * @param b base
   * @param e exponent
   * @return b^e
   */
  public static double intPow(double b, int e) {
    double result = 1.0;
    double currPow = b;
    while (e > 0) {
      if ((e & 1) != 0) {
        result *= currPow;
      }
      currPow *= currPow;
      e >>= 1;
    }
    return result;
  }

  /**
   * Find a hypergeometric distribution.  This uses exact math, trying
   * fairly hard to avoid numeric overflow by interleaving
   * multiplications and divisions.
   * (To do: make it even better at avoiding overflow, by using loops
   * that will do either a multiple or divide based on the size of the
   * intermediate result.)
   *
   * @param k The number of black balls drawn
   * @param n The total number of balls
   * @param r The number of black balls
   * @param m The number of balls drawn
   * @return The hypergeometric value
   */
  public static double hypergeometric(int k, int n, int r, int m) {
    if (k < 0 || r > n || m > n || n <= 0 || m < 0 || r < 0) {
      throw new IllegalArgumentException("Invalid hypergeometric");
    }

    // exploit symmetry of problem
    if (m > n / 2) {
      m = n - m;
      k = r - k;
    }
    if (r > n / 2) {
      r = n - r;
      k = m - k;
    }
    if (m > r) {
      int temp = m;
      m = r;
      r = temp;
    }
    // now we have that k <= m <= r <= n/2

    if (k < (m + r) - n || k > m) {
      return 0.0;
    }

    // Do limit cases explicitly
    // It's unclear whether this is a good idea.  I put it in fearing
    // numerical errors when the numbers seemed off, but actually there
    // was a bug in the Fisher's exact routine.
    if (r == n) {
      if (k == m) {
        return 1.0;
      } else {
        return 0.0;
      }
    } else if (r == n - 1) {
      if (k == m) {
        return (n - m) / (double) n;
      } else if (k == m - 1) {
        return m / (double) n;
      } else {
        return 0.0;
      }
    } else if (m == 1) {
      if (k == 0) {
        return (n - r) / (double) n;
      } else if (k == 1) {
        return r / (double) n;
      } else {
        return 0.0;
      }
    } else if (m == 0) {
      if (k == 0) {
        return 1.0;
      } else {
        return 0.0;
      }
    } else if (k == 0) {
      double ans = 1.0;
      for (int m0 = 0; m0 < m; m0++) {
        ans *= ((n - r) - m0);
        ans /= (n - m0);
      }
      return ans;
    }

    double ans = 1.0;
    // do (n-r)x...x((n-r)-((m-k)-1))/n x...x (n-((m-k-1)))
    // leaving rest of denominator to get to multiply by (n-(m-1))
    // that's k things which goes into next loop
    for (int nr = n - r, n0 = n; nr > (n - r) - (m - k); nr--, n0--) {
      // System.out.println("Multiplying by " + nr);
      ans *= nr;
      // System.out.println("Dividing by " + n0);
      ans /= n0;
    }
    // System.out.println("Done phase 1");
    for (int k0 = 0; k0 < k; k0++) {
      ans *= (m - k0);
      // System.out.println("Multiplying by " + (m-k0));
      ans /= ((n - (m - k0)) + 1);
      // System.out.println("Dividing by " + ((n-(m+k0)+1)));
      ans *= (r - k0);
      // System.out.println("Multiplying by " + (r-k0));
      ans /= (k0 + 1);
      // System.out.println("Dividing by " + (k0+1));
    }
    return ans;
  }


  /**
   * Find a one tailed exact binomial test probability.  Finds the chance
   * of this or a higher result
   *
   * @param k number of successes
   * @param n Number of trials
   * @param p Probability of a success
   */
  public static double exactBinomial(int k, int n, double p) {
    double total = 0.0;
    for (int m = k; m <= n; m++) {
      double nChooseM = 1.0;
      for (int r = 1; r <= m; r++) {
        nChooseM *= (n - r) + 1;
        nChooseM /= r;
      }
      // System.out.println(n + " choose " + m + " is " + nChooseM);
      // System.out.println("prob contribution is " +
      //	       (nChooseM * Math.pow(p, m) * Math.pow(1.0-p, n - m)));
      total += nChooseM * Math.pow(p, m) * Math.pow(1.0 - p, n - m);
    }
    return total;
  }


  /**
   * Find a one-tailed Fisher's exact probability.  Chance of having seen
   * this or a more extreme departure from what you would have expected
   * given independence.  I.e., k &ge; the value passed in.
   * Warning: this was done just for collocations, where you are
   * concerned with the case of k being larger than predicted.  It doesn't
   * correctly handle other cases, such as k being smaller than expected.
   *
   * @param k The number of black balls drawn
   * @param n The total number of balls
   * @param r The number of black balls
   * @param m The number of balls drawn
   * @return The Fisher's exact p-value
   */
  public static double oneTailedFishersExact(int k, int n, int r, int m) {
    if (k < 0 || k < (m + r) - n || k > r || k > m || r > n || m > n) {
      throw new IllegalArgumentException("Invalid Fisher's exact: " + "k=" + k + " n=" + n + " r=" + r + " m=" + m + " k<0=" + (k < 0) + " k<(m+r)-n=" + (k < (m + r) - n) + " k>r=" + (k > r) + " k>m=" + (k > m) + " r>n=" + (r > n) + "m>n=" + (m > n));
    }
    // exploit symmetry of problem
    if (m > n / 2) {
      m = n - m;
      k = r - k;
    }
    if (r > n / 2) {
      r = n - r;
      k = m - k;
    }
    if (m > r) {
      int temp = m;
      m = r;
      r = temp;
    }
    // now we have that k <= m <= r <= n/2

    double total = 0.0;
    if (k > m / 2) {
      // sum from k to m
      for (int k0 = k; k0 <= m; k0++) {
        // System.out.println("Calling hypg(" + k0 + "; " + n +
        // 		   ", " + r + ", " + m + ")");
        total += SloppyMath.hypergeometric(k0, n, r, m);
      }
    } else {
      // sum from max(0, (m+r)-n) to k-1, and then subtract from 1
      int min = Math.max(0, (m + r) - n);
      for (int k0 = min; k0 < k; k0++) {
        // System.out.println("Calling hypg(" + k0 + "; " + n +
        // 		   ", " + r + ", " + m + ")");
        total += SloppyMath.hypergeometric(k0, n, r, m);
      }
      total = 1.0 - total;
    }
    return total;
  }

  /**
   * Find a 2x2 chi-square value.
   * Note: could do this more neatly using simplified formula for 2x2 case.
   *
   * @param k The number of black balls drawn
   * @param n The total number of balls
   * @param r The number of black balls
   * @param m The number of balls drawn
   * @return The Fisher's exact p-value
   */
  public static double chiSquare2by2(int k, int n, int r, int m) {
    int[][] cg = {{k, r - k}, {m - k, n - (k + (r - k) + (m - k))}};
    int[] cgr = {r, n - r};
    int[] cgc = {m, n - m};
    double total = 0.0;
    for (int i = 0; i < 2; i++) {
      for (int j = 0; j < 2; j++) {
        double exp = (double) cgr[i] * cgc[j] / n;
        total += (cg[i][j] - exp) * (cg[i][j] - exp) / exp;
      }
    }
    return total;
  }

  /**
   * Compute the sigmoid function with mean zero.
   * Care is taken to compute an accurate answer without
   * numerical overflow. (Added by rajatr)
   *
   * @param x Point to compute sigmoid at.
   * @return Value of the sigmoid, given by 1/(1+exp(-x))
   */
  public static double sigmoid(double x) {
    if (x<0) {
      double num = Math.exp(x);
      return num / (1.0 + num);
    }
    else {
      double den = 1.0 + Math.exp(-x);
      return 1.0 / den;
    }
  }


  private static float[] acosCache; // = null;

  /**
   * Compute acos very quickly by directly looking up the value.
   * @param cosValue The cosine of the angle to fine.
   * @return The angle corresponding to the cosine value.
   * @throws IllegalArgumentException if cosValue is not between -1 and 1
   */
  public static double acos(double cosValue) {
    if (cosValue < -1.0 || cosValue > 1.0) {
      throw new IllegalArgumentException("Cosine is not between -1 and 1: " + cosValue);
    }
    int numSamples = 10000;
    if (acosCache == null) {
      acosCache = new float[numSamples + 1];
      for (int i = 0; i <= numSamples; ++i) {
        double x = 2.0 / ((double) numSamples) * ((double) i) - 1.0;
        acosCache[i] = (float) Math.acos(x);
      }
    }

    int i = ((int) (((cosValue + 1.0) / 2.0) * ((double) numSamples)));
    return acosCache[i];
  }


  public static double poisson(int x, double lambda) {
    if (x<0 || lambda<=0.0) throw new RuntimeException("Bad arguments: " + x + " and " + lambda);
    double p = (Math.exp(-lambda) * Math.pow(lambda, x)) / factorial(x);
    if (Double.isInfinite(p) || p<=0.0) throw new RuntimeException(Math.exp(-lambda) +" "+ Math.pow(lambda, x) + ' ' + factorial(x));
    return p;
  }

  /**
   * Uses floating point so that it can represent the really big numbers that come up.
   * @param x Argument to take factorial of
   * @return Factorial of argument
   */
  public static double factorial(int x) {
    double result = 1.0;
    for (int i=x; i>1; i--) {
      result *= i;
    }
    return result;
  }


  /**
   * Taken from http://nerds-central.blogspot.com/2011/05/high-speed-parse-double-for-jvm.html
   */
  private static final double[] exps = new double[617];
  static {
    for(int i=-308;i<308;++i) {
      String toParse = "1.0e" + i;
      exps[(i + 308)]=Double.parseDouble("1.0e" + i);
    }
  }


  /**
   * Taken from http://nerds-central.blogspot.com/2011/05/high-speed-parse-double-for-jvm.html
   */
  public static double parseDouble(boolean negative, long mantissa, int  exponent) {
     // Do this with no locals other than the arguments to make it stupid easy
     // for the JIT compiler to inline the code.
    int e = -16;
    return (negative ? -1. : 1.) * (((double)mantissa) * exps[(e + 308)]) * exps[(exponent + 308)];
  }


  /**
   * Segment a double into a mantissa and exponent.
   */
  public static Triple<Boolean, Long, Integer> segmentDouble(double d) {
    if (Double.isInfinite(d) || Double.isNaN(d)) {
      throw new IllegalArgumentException("Cannot handle weird double: " + d);
    }
    boolean negative = d < 0;
    d = Math.abs(d);
    int exponent = 0;
    while (d >= 10.0) {
      exponent += 1;
      d = d / 10.;
    }
    while (d < 1.0) {
      exponent -= 1;
      d = d * 10.;
    }
    return Triple.makeTriple(negative, (long) (d * 10000000000000000.), exponent);
  }


  /**
   * From http://nadeausoftware.com/articles/2009/08/java_tip_how_parse_integers_quickly
   *
   * Parse an integer very quickly, without sanity checks.
   */
  public static long parseInt( final String s ) {
    // Check for a sign.
    long num  = 0;
    long sign = -1;
    final int len  = s.length( );
    final char ch  = s.charAt( 0 );
    if ( ch == '-' ) {
      sign = 1;
    }
    else {
      final long d = ch - '0';
      num = -d;
    }
    // Build the number.
    final long max = (sign == -1) ?
        -Long.MAX_VALUE : Long.MIN_VALUE;
    final long multmax = max / 10;
    int i = 1;
    while ( i < len ) {
      long d = s.charAt(i++) - '0';
      num *= 10;
      num -= d;
    }
    return sign * num;
  }

  /**
   * Tests the hypergeometric distribution code, or other functions
   * provided in this module.
   *
   * @param args Either none, and the log add routines are tested, or the
   *             following 4 arguments: k (cell), n (total), r (row), m (col)
   */
  public static void main(String[] args) {
    if (args.length == 0) {
      log.info("Usage: java edu.stanford.nlp.math.SloppyMath " + "[-logAdd|-fishers k n r m|-binomial r n p");
    } else if (args[0].equals("-logAdd")) {
      System.out.println("Log adds of neg infinity numbers, etc.");
      System.out.println("(logs) -Inf + -Inf = " + logAdd(Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY));
      System.out.println("(logs) -Inf + -7 = " + logAdd(Double.NEGATIVE_INFINITY, -7.0));
      System.out.println("(logs) -7 + -Inf = " + logAdd(-7.0, Double.NEGATIVE_INFINITY));
      System.out.println("(logs) -50 + -7 = " + logAdd(-50.0, -7.0));
      System.out.println("(logs) -11 + -7 = " + logAdd(-11.0, -7.0));
      System.out.println("(logs) -7 + -11 = " + logAdd(-7.0, -11.0));
      System.out.println("real 1/2 + 1/2 = " + logAdd(Math.log(0.5), Math.log(0.5)));
    } else if (args[0].equals("-fishers")) {
      int k = Integer.parseInt(args[1]);
      int n = Integer.parseInt(args[2]);
      int r = Integer.parseInt(args[3]);
      int m = Integer.parseInt(args[4]);
      double ans = SloppyMath.hypergeometric(k, n, r, m);
      System.out.println("hypg(" + k + "; " + n + ", " + r + ", " + m + ") = " + ans);
      ans = SloppyMath.oneTailedFishersExact(k, n, r, m);
      System.out.println("1-tailed Fisher's exact(" + k + "; " + n + ", " + r + ", " + m + ") = " + ans);
      double ansChi = SloppyMath.chiSquare2by2(k, n, r, m);
      System.out.println("chiSquare(" + k + "; " + n + ", " + r + ", " + m + ") = " + ansChi);

      System.out.println("Swapping arguments should give same hypg:");
      ans = SloppyMath.hypergeometric(k, n, r, m);
      System.out.println("hypg(" + k + "; " + n + ", " + m + ", " + r + ") = " + ans);
      int othrow = n - m;
      int othcol = n - r;
      int cell12 = m - k;
      int cell21 = r - k;
      int cell22 = othrow - (r - k);
      ans = SloppyMath.hypergeometric(cell12, n, othcol, m);
      System.out.println("hypg(" + cell12 + "; " + n + ", " + othcol + ", " + m + ") = " + ans);
      ans = SloppyMath.hypergeometric(cell21, n, r, othrow);
      System.out.println("hypg(" + cell21 + "; " + n + ", " + r + ", " + othrow + ") = " + ans);
      ans = SloppyMath.hypergeometric(cell22, n, othcol, othrow);
      System.out.println("hypg(" + cell22 + "; " + n + ", " + othcol + ", " + othrow + ") = " + ans);
    } else if (args[0].equals("-binomial")) {
      int k = Integer.parseInt(args[1]);
      int n = Integer.parseInt(args[2]);
      double p = Double.parseDouble(args[3]);
      double ans = SloppyMath.exactBinomial(k, n, p);
      System.out.println("Binomial p(X >= " + k + "; " + n + ", " + p + ") = " + ans);
    } else {
      log.info("Unknown option: " + args[0]);
    }
  }

}
