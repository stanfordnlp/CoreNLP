package edu.stanford.nlp.stats;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

import edu.stanford.cs.ra.arguments.Argument;
import edu.stanford.cs.ra.arguments.ArgumentException;
import edu.stanford.cs.ra.arguments.Arguments;
import edu.stanford.nlp.io.RecordIterator;
import edu.stanford.nlp.math.ArrayMath;
import edu.stanford.nlp.stats.DataSeries.ListDataSeries;
import edu.stanford.nlp.util.StringUtils;

/**
 * A <code>Histogram</code> is a way to visualize a distribution of numeric
 * values. A <code>Histogram</code> is defined by (1) an array of
 * <code>double</code>s, (2) upper and lower bounds <code>max</code> and
 * <code>min</code>, and (3) the number of buckets <code>buckets</code> into
 * which the interval <code>[min, max)</code> is divided. Each bucket has a
 * lower bound; a value falls in a bucket if it is &gt; the bucket's lower bound
 * and &le; its upper bound (i.e. the lower bound of the next bucket). Two
 * additional buckets are created for values less than or equal to
 * <code>min</code> or greater than <code>max</code>.
 * <p/>
 * 
 * The bounds <code>max</code> and <code>min</code> are determined in one of
 * three ways: (1) they may be specified explicitly; (2) they can be set
 * automatically to match the highest and lowest values in the data; or (3) they
 * can be set automatically to span a specified number of standard deviations
 * around the mean of the data.
 * <p/>
 * 
 * The input data can be supplied directly as an array of <code>double</code>s,
 * or it can be read in from a specified file or input stream (such as
 * <code>stdin</code>).
 * <p/>
 * 
 * The resulting <code>Histogram</code> object can be displayed in various ways.
 * There are methods for pretty-printing, and coming very soon is a way to
 * display a <code>Histogram</code> in a {@link javax.swing.JFrame JFrame}.
 * <p/>
 * 
 * Example usage:
 * <p/>
 * 
 * <pre>
 *   java edu.stanford.nlp.stats.Histogram --buckets 20 --sigma 3.0 --file scores.dat
 * </pre>
 * 
 * For extra creamy goodness, make an alias to this:
 * <p/>
 * 
 * <pre>
 *   alias histogram "java edu.stanford.nlp.stats.Histogram \!*"
 * </pre>
 * 
 * Then you can do things like this:
 * <p/>
 * 
 * <pre>
 *   cat scores.dat | histogram
 * </pre>
 * 
 * @author Bill MacCartney
 */
public class Histogram {

  /** A holder for command-line arguments. */
  public static class ArgumentBox {

    @Argument("Minimum value to display")
    @Argument.Switch( { "--min", "-m" })
    public double min = Double.NEGATIVE_INFINITY;

    @Argument("Maximum value to display")
    @Argument.Switch( { "--max", "-M" })
    public double max = Double.POSITIVE_INFINITY;

    @Argument("Number of standard deviations around the mean to display")
    @Argument.Switch( { "--sigma", "-s" })
    public double sigma = 0.0;

    @Argument("Number of buckets between --min and --max")
    @Argument.Switch( { "--buckets", "-b" })
    public int buckets = 10;

    @Argument("Whether to ignore first line of input")
    @Argument.Switch("--headers")
    public boolean useHeaders = false;

    @Argument("Whether to show a demo")
    @Argument.Switch("--demo")
    public boolean demo = false;

    @Argument("A file containing numbers (if absent, read from stdin)")
    @Argument.Switch("--file")
    public String filename = null;

    @Argument.Check
    public void check() {
      if (max < min) {
        throw new ArgumentException("--max must be bigger than --min");
      }
      if (buckets < 1) {
        throw new ArgumentException("--buckets must be positive");
      }
    }

    @Override
    public String toString() {
      StringBuilder sb = new StringBuilder();
      sb.append(String.format("%-20s: %6.4f%n", "min", min));
      sb.append(String.format("%-20s: %6.4f%n", "max", max));
      sb.append(String.format("%-20s: %6.4f%n", "sigma", sigma));
      sb.append(String.format("%-20s: %6d%n", "buckets", buckets));
      sb.append(String.format("%-20s: %6s%n", "useHeaders", useHeaders));
      sb.append(String.format("%-20s: %s%n", "filename", filename));
      return sb.toString();
    }

  }

  // ----------------------------------------------------------------------------

  public double min = Double.NEGATIVE_INFINITY;
  public double max = Double.POSITIVE_INFINITY;
  public int buckets = 10;
  private double[] X; // the values
  private int[] counts; // an int[buckets + 2] array of bucket counts

  /**
   * Constructs a histogram by reading numbers from a file or from stdin, and
   * using the arguments in the given argument box.
   */
  public Histogram(ArgumentBox args) throws IOException {

    // System.err.println("Histogram arguments:\n" + args);

    if (args.filename != null) {
      setDataNoRecount(readData(args.filename, args.useHeaders));
    } else {
      System.err.println("[Reading data from stdin...]");
      setDataNoRecount(readData(System.in, args.useHeaders));
    }

    setBucketsNoRecount(args.buckets);
    if (args.sigma > 0.0) {
      setRangeNoRecount(args.sigma);
    } else if (Double.isInfinite(args.min) || Double.isInfinite(args.max) || Double.isNaN(args.min) || Double.isNaN(args.max)) {
      setRangeNoRecount();
    } else {
      setRangeNoRecount(args.min, args.max);
    }
    recount();
  }

  /**
   * Constructs a <code>Histogram</code> from the values in the supplied vector.
   * <p/>
   * 
   * If any of the values in the vector are <code>NaN</code>, they are ignored.
   */
  public Histogram(double[] X, double min, double max, int buckets) {
    setDataNoRecount(X);
    setBucketsNoRecount(buckets);
    setRangeNoRecount(min, max);
    recount();
  }

  /**
   * Constructs a <code>Histogram</code> from the values in the supplied vector.
   * This method calls {@link #Histogram(double[],double,double,int)
   * Histogram(X, min, max, buckets)} with <code>min</code> and <code>max</code>
   * set to <code>sigma</code> standard deviations below and above the mean of
   * the values in <code>X</code>. In computing the mean and the standard
   * deviation of <code>X</code>, any values which are infinite or
   * <code>NaN</code> are ignored.
   */
  public Histogram(double[] X, double sigma, int buckets) {
    setDataNoRecount(X);
    setBucketsNoRecount(buckets);
    setRangeNoRecount(sigma);
    recount();
  }

  /**
   * Constructs a <code>Histogram</code> from the values in the supplied vector.
   * This method calls {@link #Histogram(double[],double,double,int)
   * Histogram(X, min, max, buckets)} with <code>min</code> and <code>max</code>
   * set to the minimum and maximum values in <code>X</code>. In computing the
   * min and max of <code>X</code>, any values which are infinite or
   * <code>NaN</code> are ignored.
   */
  public Histogram(double[] X, int buckets) {
    setDataNoRecount(X);
    setBucketsNoRecount(buckets);
    setRangeNoRecount();
    recount();
  }

  public Histogram(Counter<?> X, int buckets) {
    double[] x = new double[X.size()];
    int i = 0;
    for (double t : X.values()) {
      x[i] = t;
      i++;
    }
    setDataNoRecount(x);
    setBucketsNoRecount(buckets);
    setRangeNoRecount();
    recount();
  }

  // reading data
  // ---------------------------------------------------------------

  private static double[] readData(RecordIterator it, boolean useHeaders) {
    DataSeries[] serieses = ListDataSeries.readDataSeries(it, useHeaders);
    if (serieses == null || serieses.length == 0)
      return null;
    DataSeries series = serieses[0]; // ignore all serieses except first!
    double[] v = new double[series.size()];
    for (int i = 0; i < v.length; i++) {
      v[i] = series.get(i);
    }
    return v;
  }

  private double[] readData(InputStream in, boolean useHeaders) {
    return readData(new RecordIterator(in), useHeaders);
  }

  private double[] readData(String filename, boolean useHeaders) throws FileNotFoundException {
    return readData(new RecordIterator(filename), useHeaders);
  }

  // accessors -------------------------------------------------------------

  public double[] data() {
    return X;
  }

  public int buckets() {
    return buckets;
  }

  public double min() {
    return min;
  }

  public double max() {
    return max;
  }

  public int[] counts() {
    return counts;
  }

  public int count(int bucket) {
    return counts[bucket];
  }

  // manipulators (cause recount) ------------------------------------------

  public void setData(double[] X) {
    setDataNoRecount(X);
    recount();
  }

  public void setBuckets(int buckets) {
    setBucketsNoRecount(buckets);
    recount();
  }

  public void setRange(double min, double max) {
    setRangeNoRecount(min, max);
    recount();
  }

  public void setRange(double sigma) {
    setRangeNoRecount(sigma);
    recount();
  }

  public void setRange() {
    setRangeNoRecount();
    recount();
  }

  // private manipulators (no recount) -------------------------------------

  private void setDataNoRecount(double[] X) {
    if (X == null)
      throw new NullPointerException("X is null");
    this.X = X;
  }

  private void setBucketsNoRecount(int buckets) {
    if (buckets < 1)
      throw new IllegalArgumentException("can't have <1 buckets");
    this.buckets = buckets;
    this.counts = new int[buckets + 2];
  }

  private void setRangeNoRecount(double min, double max) {
    if (Double.isNaN(min))
      throw new IllegalArgumentException("min is NaN");
    if (Double.isInfinite(min))
      throw new IllegalArgumentException("min is infinite");
    if (Double.isNaN(max))
      throw new IllegalArgumentException("max is NaN");
    if (Double.isInfinite(max))
      throw new IllegalArgumentException("max is infinite");
    if (max <= min)
      throw new IllegalArgumentException("max <= min");
    this.min = min;
    this.max = max;
  }

  // sets range to [mean - sigma, mean + sigma], based on data X
  private void setRangeNoRecount(double sigma) {
    if (sigma <= 0 || Double.isInfinite(sigma))
      throw new IllegalArgumentException("sigma must be > 0");
    double mean = ArrayMath.safeMean(X);
    double stdev = ArrayMath.safeStdev(X);
    this.min = mean - sigma * stdev;
    this.max = mean + sigma * stdev;
  }

  // sets range based on min and max of data X
  private void setRangeNoRecount() {
    this.min = ArrayMath.safeMin(X);
    this.max = ArrayMath.safeMax(X);
  }

  // recount ---------------------------------------------------------------

  public void recount() {
    ClassicCounter<Integer> bucketCounts = new ClassicCounter<Integer>();
    X = ArrayMath.filterNaN(X);
    for (int i = 0; i < X.length; i++)
      bucketCounts.incrementCount(bucketFor(X[i]));
    for (int i = 0; i < buckets + 2; i++)
      counts[i] = (int) bucketCounts.getCount(i);
  }

  private double bucketSize() {
    return (max - min) / buckets;
  }

  private int bucketFor(double x) {
    if (x <= min)
      return 0;
    if (x > max)
      return buckets + 1;
    if (bucketSize() == 0)
      return 1;
    return buckets - (int) ((max - x) / bucketSize());
  }

  public double bucketMin(int i) {
    if (i < 0 || i > buckets + 1)
      throw new IllegalArgumentException();
    if (i == 0)
      return Double.NEGATIVE_INFINITY;
    return min + (i - 1) * bucketSize();
  }

  // toString --------------------------------------------------------------

  @Override
  public String toString() {
    return toString(50, true);
  }

  /**
   * Returns a string representation of this <code>Histogram</code> in vertical
   * format.
   * 
   * @param histWidth
   *          how long the histogram bars should be -- use 0 for default
   * @param withStats
   *          include some statistics before histogram
   */
  public String toString(int histWidth, boolean withStats) {
    double maxCount = ArrayMath.max(counts); // double because we divide by it

    if (histWidth < 1)
      histWidth = 50;
    StringBuilder sb = new StringBuilder();
    if (withStats)
      sb.append(histogramStats());
    if (withStats)
      sb.append('\n');
    for (int i = 0; i < counts.length; i++) {
      String label = "";
      if (i == 0)
        label += "x <= " + String.format("%8.3f", min);
      else if (i < counts.length - 1)
        label += "x <= " + String.format("%8.3f", (min + bucketSize() * i));
      else
        label += "x >  " + String.format("%8.3f", max);
      sb.append(StringUtils.padOrTrim(label, 16));
      sb.append(" ");
      sb.append(histogramBar(counts[i] / maxCount, histWidth));
      sb.append(" (count:" + counts[i] + ")");
      sb.append("\n");
    }
    return StringUtils.chomp(sb);
  }

  private String histogramStats() {
    return String.format(StringUtils.repeat("%-10s%10.3f%n", 5), "size", (double) X.length, "min", ArrayMath.min(X), "max", ArrayMath.max(X), "median", ArrayMath.median(X),
        "mean", ArrayMath.mean(X), "stdev", ArrayMath.stdev(X));
  }

  // Returns a string of the specified width, of which the first (frac *
  // width) characters are the specified char. The rest of the width is
  // filled with spaces.
  private static String histogramBar(double frac, int width, char mark) {
    if (width < 0)
      width = 0;
    if (frac < 0.0)
      frac = 0.0;
    if (frac > 1.0)
      frac = 1.0;
    char[] chars = new char[width];
    Arrays.fill(chars, ' ');
    int bar = (int) Math.round(frac * width);
    Arrays.fill(chars, 0, bar, mark);
    if (bar == 0 && frac > 0.0) {
      chars[0] = '.';
    }
    return new String(chars);
  }

  // Returns a string of the specified width, of which the first (frac *
  // width) characters are '-'. The rest of the width is
  // filled with spaces.
  private static String histogramBar(double frac, int width) {
    return histogramBar(frac, width, '-');
  }

  // XYPlot ----------------------------------------------------------------

  /*
  public XYPlot asXYPlot() {
    DataSeries mins = 
      new DataSeries.FunctionDataSeries("mins", 
                                        new Function<Integer, Double>() {
                                          public Double eval(Integer i) {
                                            if (i == 0) return min - bucketSize();
                                            return bucketMin(i);
                                          }
                                        },
                                        new Function<Object, Integer>() {
                                          public Integer eval(Object o) {
                                            return buckets();
                                          }
                                        });
    DataSeries cnts = 
      new DataSeries.FunctionDataSeries("counts", 
                                        new Function<Integer, Double>() {
                                          public Double eval(Integer i) {
                                            return (double) counts[i];
                                          }
                                        },
                                        new Function<Object, Integer>() {
                                          public Integer eval(Object o) {
                                            return buckets();
                                          }
                                        },
                                        mins);
    XYPlot xyp = new XYPlot();
    xyp.addDataSeries(cnts);
    xyp.setSeriesStyle(XYPlot.Style.BAR);
    return xyp;
  }

  public javax.swing.JFrame asXYPlotFrame() {
    return asXYPlot().showInJFrame();
  }
  */

  // from file -------------------------------------------------------------

  public static Histogram makeHistogramFromInputData(RecordIterator it, boolean useHeaders) {
    DataSeries[] serieses = ListDataSeries.readDataSeries(it, useHeaders);
    for (DataSeries series : serieses) {
      double[] v = new double[series.size()];
      for (int i = 0; i < v.length; i++) {
        v[i] = series.get(i);
      }
      return new Histogram(v, 20);
    }
    return null;
  }

  public static Histogram makeHistogramFromInputData(String filename, boolean useHeaders) throws FileNotFoundException {
    return makeHistogramFromInputData(new RecordIterator(filename), false);
  }

  public static Histogram makeHistogramFromInputData(String filename) throws FileNotFoundException {
    return makeHistogramFromInputData(filename, false);
  }

  public static Histogram makeHistogramFromInputData(InputStream in, boolean useHeaders) {
    return makeHistogramFromInputData(new RecordIterator(in), false);
  }

  public static Histogram makeHistogramFromInputData(InputStream in) {
    return makeHistogramFromInputData(in, false);
  }

  // main ------------------------------------------------------------------

  public static void main(String[] args) throws IOException {
    ArgumentBox box = new ArgumentBox();
    Arguments.parse(args, box);
    if (box.demo) {
      example2();
    } else {
      Histogram histo = new Histogram(box);
      System.out.println(histo.toString(0, true));
    }
  }

  public static void printCounts(Histogram histo) {
    System.out.println("Counts");
    int[] counts = histo.counts();
    for (int i = 0; i < counts.length; i++)
      System.out.println(counts[i]);
  }

  public static void example1() {

    double[] v = new double[] { 12.0, 14.0, 17.0, 20.0, 21.0, 22.0, 22.0 };

    Histogram h = new Histogram(v, 3); // 3 buckets, range = all
    System.out.println(Arrays.toString(h.counts()));
    System.out.println(h.toString(0, true) + "\n");

    h.setRange(2.0); // range = +/- 2 stddev
    System.out.println(h.toString(0, false) + "\n");

    h.setRange(0.0, 30.0); // range = 0, 30
    System.out.println(h.toString(0, false) + "\n");

  }

  public static void example2() {

    // sample binomial distribution
    int trials = 5000;
    double p = 0.5;
    double n = 5000;
    double[] samples = new double[trials];
    for (int trial = 0; trial < trials; trial++)
      for (int i = 0; i < n; i++)
        if (Math.random() < p)
          samples[trial]++;

    Histogram h = new Histogram(samples, 20); // 20 buckets, range = all
    System.out.println(h.toString(0, true));
    // javax.swing.JFrame xyf = h.asXYPlotFrame();

    // for (int buckets = 1; buckets < 50; buckets++ ) {
    // edu.stanford.nlp.util.Timer.sleep(1000);
    // h.setBuckets(buckets);
    // xyf.repaint();
    // }

  }

}
