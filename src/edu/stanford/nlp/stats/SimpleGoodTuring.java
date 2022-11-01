package edu.stanford.nlp.stats;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

/**
 * Simple Good-Turing smoothing, based on code from Sampson, available at:
 * ftp://ftp.informatics.susx.ac.uk/pub/users/grs2/SGT.c <br>
 *
 * See also http://www.grsampson.net/RGoodTur.html
 * 
 * @author Bill MacCartney (wcmac@cs.stanford.edu)
 */
public class SimpleGoodTuring {

  private static final int MIN_INPUT = 5;
  private static final double CONFID_FACTOR = 1.96;
  private static final double TOLERANCE = 1e-12;

  private int[] r;               // for each bucket, a frequency
  private int[] n;               // for each bucket, number of items w that frequency
  private int rows;              // number of frequency buckets

  private int bigN = 0;          // total count of all items
  private double pZero;          // probability of unseen items
  private double bigNPrime;
  private double slope;
  private double intercept;
  private double[] z;
  private double[] logR;
  private double[] logZ;
  private double[] rStar;
  private double[] p;

  /**
   * Each instance of this class encapsulates the computation of the smoothing
   * for one probability distribution.  The constructor takes two arguments
   * which are two parallel arrays.  The first is an array of counts, which must
   * be positive and in ascending order.  The second is an array of
   * corresponding counts of counts; that is, for each i, n[i] represents the
   * number of types which occurred with count r[i] in the underlying
   * collection.  See the documentation for main() for a concrete example.
   */
  public SimpleGoodTuring(int[] r, int[] n) {
    if (r == null) throw new IllegalArgumentException("r must not be null!");
    if (n == null) throw new IllegalArgumentException("n must not be null!");
    if (r.length != n.length) throw new IllegalArgumentException("r and n must have same size!");
    if (r.length < MIN_INPUT) throw new IllegalArgumentException("r must have size >= " + MIN_INPUT + "!");
    this.r = new int[r.length];
    this.n = new int[n.length];
    System.arraycopy(r, 0, this.r, 0, r.length); // defensive copy
    System.arraycopy(n, 0, this.n, 0, n.length); // defensive copy
    this.rows = r.length;
    compute();
    validate(TOLERANCE);
  }

  /**
   * Returns the probability allocated to types not seen in the underlying
   * collection.
   */
  public double getProbabilityForUnseen() {
    return pZero;
  }

  /**
   * Returns the probabilities allocated to each type, according to their count
   * in the underlying collection.  The returned array parallels the arrays
   * passed in to the constructor.  If the returned array is designated p, then
   * for all i, p[i] represents the smoothed probability assigned to types which
   * occurred r[i] times in the underlying collection (where r is the first
   * argument to the constructor).
   */
  public double[] getProbabilities() {
    return p;
  }

  private void compute() {
    int i, j, next_n;
    double k, x, y;
    boolean indiffValsSeen = false;

    z = new double[rows];
    logR = new double[rows];
    logZ = new double[rows];
    rStar = new double[rows];
    p = new double[rows];
        
    for (j = 0; j < rows; ++j) bigN += r[j] * n[j]; // count all items
    next_n = row(1);
    pZero = (next_n < 0) ? 0 : n[next_n] / (double) bigN;
    for (j = 0; j < rows; ++j) {
      i = (j == 0 ? 0 : r[j - 1]);
      if (j == rows - 1)
        k = (double) (2 * r[j] - i);
      else
        k = (double) r[j + 1];
      z[j] = 2 * n[j] / (k - i);
      logR[j] = Math.log(r[j]);
      logZ[j] = Math.log(z[j]);
    }
    findBestFit();
    for (j = 0; j < rows; ++j) {
      y = (r[j] + 1) * smoothed(r[j] + 1) / smoothed(r[j]);
      if (row(r[j] + 1) < 0)
        indiffValsSeen = true;
      if (!indiffValsSeen) {
        x = (r[j] + 1) * (next_n = n[row(r[j] + 1)]) / (double) n[j];
        if (Math.abs(x - y) <= CONFID_FACTOR * Math.sqrt(sq(r[j] + 1.0)
                                                         * next_n / (sq((double) n[j]))
                                                         * (1 + next_n / (double) n[j])))
          indiffValsSeen = true;
        else
          rStar[j] = x;
      }
      if (indiffValsSeen)
        rStar[j] = y;
    }
    bigNPrime = 0.0;
    for (j = 0; j < rows; ++j)
      bigNPrime += n[j] * rStar[j];
    for (j = 0; j < rows; ++j)
      p[j] = (1 - pZero) * rStar[j] / bigNPrime;
  }

  /**
   * Returns the index of the bucket having the given frequency, or else -1 if no
   * bucket has the given frequency.
   */
  private int row(int freq) {
    int i = 0;
    while (i < rows && r[i] < freq) i++;
    return ((i < rows && r[i] == freq) ? i : -1);
  }

  private void findBestFit() {
    double XYs, Xsquares, meanX, meanY;
    int i;
    XYs = Xsquares = meanX = meanY = 0.0;
    for (i = 0; i < rows; ++i) {
      meanX += logR[i];
      meanY += logZ[i];
    }
    meanX /= rows;
    meanY /= rows;
    for (i = 0; i < rows; ++i) {
      XYs += (logR[i] - meanX) * (logZ[i] - meanY);
      Xsquares += sq(logR[i] - meanX);
    }
    slope = XYs / Xsquares;
    intercept = meanY - slope * meanX;
  }

  private double smoothed(int i) {
    return (Math.exp(intercept + slope * Math.log(i)));
  }

  private static double sq(double x) {
    return (x * x);
  }

  private void print() {
    int i;
    System.out.printf("%6s %6s %8s %8s%n", "r", "n", "p", "p*");
    System.out.printf("%6s %6s %8s %8s%n", "----", "----", "----", "----");
    System.out.printf("%6d %6d %8.4g %8.4g%n", 0, 0, 0.0, pZero);
    for (i = 0; i < rows; ++i)
      System.out.printf("%6d %6d %8.4g %8.4g%n", r[i], n[i], 1.0 * r[i] / bigN, p[i]);
  }

  /**
   * Ensures that we have a proper probability distribution.
   */
  private void validate(double tolerance) {
    double sum = pZero;
    for (int i = 0; i < n.length; i++) {
      sum += (n[i] * p[i]);
    }
    double err = 1.0 - sum;
    if (Math.abs(err) > tolerance) {
      throw new IllegalStateException("ERROR: the probability distribution sums to " + sum);
    }
  }


  // static methods -------------------------------------------------------------

  /**
   * Reads from STDIN a sequence of lines, each containing two integers,
   * separated by whitespace.  Returns a pair of int arrays containing the
   * values read.
   */
  private static int[][] readInput() throws Exception {
    List<Integer> rVals = new ArrayList<>();
    List<Integer> nVals = new ArrayList<>();
    BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
    String line;
    while ((line = in.readLine()) != null) {
      String[] tokens = line.trim().split("\\s+");
      if (tokens.length != 2)
        throw new Exception("Line doesn't contain two tokens: " + line);
      Integer r = Integer.valueOf(tokens[0]);
      Integer n = Integer.valueOf(tokens[1]);
      rVals.add(r);
      nVals.add(n);
    }
    in.close();
    int[][] result = new int[2][];
    result[0] = integerList2IntArray(rVals);
    result[1] = integerList2IntArray(nVals);
    return result;
  }

  /**
   * Helper to readInput().
   */
  private static int[] integerList2IntArray(List<Integer> integers) {
    int[] ints = new int[integers.size()];
    int i = 0;
    for (Integer integer : integers) {
      ints[i++] = integer;
    }
    return ints;
  }


  // main =======================================================================

  /**
   * Like Sampson's SGT program, reads data from STDIN and writes results to
   * STDOUT.  The input should contain two integers on each line, separated by
   * whitespace.  The first integer is a count; the second is a count for that
   * count.  The input must be sorted in ascending order, and should not contain
   * 0s.  For example, valid input is: <br>
   *
   * <pre>
   *   1 10
   *   2 6
   *   3 4
   *   5 2
   *   8 1
   * </pre>
   *
   * This represents a collection in which 10 types occur once each, 6 types
   * occur twice each, 4 types occur 3 times each, 2 types occur 5 times each,
   * and one type occurs 10 times, for a total count of 52.  This input will
   * produce the following output: <br>
   *
   * <pre>
   *     r      n        p       p*
   *  ----   ----     ----     ----
   *     0      0    0.000   0.1923
   *     1     10  0.01923  0.01203
   *     2      6  0.03846  0.02951
   *     3      4  0.05769  0.04814
   *     5      2  0.09615  0.08647
   *     8      1   0.1538   0.1448
   * </pre>
   *
   * The last column represents the smoothed probabilities, and the first item
   * in this column represents the probability assigned to unseen items.
   */
  public static void main(String[] args) throws Exception {
    int[][] input = readInput();
    SimpleGoodTuring sgt = new SimpleGoodTuring(input[0], input[1]);
    sgt.print();
  }

}

