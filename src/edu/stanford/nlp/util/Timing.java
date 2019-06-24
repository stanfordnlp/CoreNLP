package edu.stanford.nlp.util;

import edu.stanford.nlp.util.logging.Redwood;

import java.io.PrintStream;
import java.io.PrintWriter;
import java.text.DecimalFormatSymbols;
import java.text.NumberFormat;
import java.text.DecimalFormat;
import java.util.Locale;

/**
 * A class for measuring how long things take.  For backward
 * compatibility, this class contains static methods, but the
 * preferred usage is to instantiate a Timing object and use instance
 * methods.
 *
 * <p>To use, call {@link #startTime()} before running the code in
 * question. Call {@link #tick()} to print an intermediate update, and {@link #endTime()} to
 * finish the timing and print the result. You can optionally pass a descriptive
 * string and {@code PrintStream} to {@code tick} and {@code endTime}
 * for more control over what gets printed where.</p>
 *
 * <p>Example: time reading in a big file and transforming it:</p>
 * <p><code>Timing.startTime();<br>
 * String bigFileContents = IOUtils.slurpFile(bigFile);<br>
 * Timing.tick(&quot;read in big file&quot;, System.err);<br>
 * String output = costlyTransform(bigFileContents);<br>
 * Timing.endTime(&quot;transformed big file&quot;, System.err);</code></p>
 *
 * @author Bill MacCartney
 */
public class Timing  {

  /** A logger for this class */
  private static final Redwood.RedwoodChannels log = Redwood.channels(Timing.class);

  private static final long MILLISECONDS_TO_SECONDS = 1000L;
  private static final long SECOND_DIVISOR = 1000000000L;
  private static final long MILLISECOND_DIVISOR = 1000000L;

  /**
   * Stores the time at which the timer was started. Now stored as nanoseconds.
   */
  private long start;

  /**
   * Stores the time at which the (static) timer was started. Stored as nanoseconds.
   */
  private static long startTime = System.nanoTime();

  /** Stores a suitable formatter for printing seconds nicely. */
  private static final NumberFormat nf = new DecimalFormat("0.0", DecimalFormatSymbols.getInstance(Locale.ROOT));


  /**
   * Constructs new Timing object and starts the timer.
   */
  public Timing() {
    this.start();
  }

  // start ==========================================================

  /**
   * Start timer.
   */
  public void start() {
    start = System.nanoTime();
  }

  // report =========================================================

  /**
   * Return elapsed time (without stopping timer).
   *
   * @return Number of milliseconds elapsed
   */
  public long report() {
    return (System.nanoTime() - start) / MILLISECOND_DIVISOR;
  }

  /**
   * Return elapsed time (without stopping timer).
   *
   * @return Number of nanoseconds elapsed
   */
  public long reportNano() {
    return System.nanoTime() - start;
  }

  /**
   * Print elapsed time (without stopping timer).
   *
   * @param str    Additional prefix string to be printed
   * @param stream PrintStream on which to write output
   * @return Number of milliseconds elapsed
   */
  public long report(String str, PrintStream stream) {
    long elapsed = this.report();
    stream.println(str + " Time elapsed: " + elapsed + " ms");
    return elapsed;
  }

  /**
   * Print elapsed time to {@code System.err} (without stopping timer).
   *
   * @param str Additional prefix string to be printed
   * @return Number of milliseconds elapsed
   */
  public long report(String str) {
    return this.report(str, System.err);
  }

  /**
   * Print elapsed time (without stopping timer).
   *
   * @param logger Logger to log a timed operation with
   * @param str    Additional prefix string to be printed
   * @return Number of milliseconds elapsed
   */
  public long report(Redwood.RedwoodChannels logger, String str) {
    long elapsed = this.report();
    logger.info(str + " ... Time elapsed: " +
                toSecondsString(elapsed) + " sec");
    return elapsed;
  }

  /**
   * Print elapsed time (without stopping timer).
   *
   * @param str    Additional prefix string to be printed
   * @param writer PrintWriter on which to write output
   * @return Number of milliseconds elapsed
   */
  public long report(String str, PrintWriter writer) {
    long elapsed = this.report();
    writer.println(str + " ... Time elapsed: " +
                   toSecondsString(elapsed) + " sec");
    return elapsed;
  }

  /** Returns the number of seconds passed since the timer started in the form "d.d". */
  public String toSecondsString() {
    return toSecondsString(report());
  }

  /** Format with one decimal place elapsed milliseconds in seconds.
   *
   * @param elapsed Number of milliseconds elapsed
   * @return Formatted String
   */
  public static String toSecondsString(long elapsed) {
    return nf.format(((double) elapsed) / MILLISECONDS_TO_SECONDS);
  }

  /** Format with one decimal place elapsed milliseconds.
   *
   * @param elapsed Number of milliseconds elapsed
   * @return Formatted String
   */
  public static String toMilliSecondsString(long elapsed) {
    return nf.format(elapsed);
  }


  // restart ========================================================

  /**
   * Restart timer.
   *
   * @return Number of milliseconds elapsed
   */
  public long restart() {
    long elapsed = this.report();
    this.start();
    return elapsed;
  }

  /**
   * Print elapsed time and restart timer.
   *
   * @param str    Additional prefix string to be printed
   * @param stream PrintStream on which to write output
   * @return Number of milliseconds elapsed
   */
  public long restart(String str, PrintStream stream) {
    long elapsed = this.report(str, stream);
    this.start();
    return elapsed;
  }

  /**
   * Print elapsed time to {@code System.err} and restart timer.
   *
   * @param str Additional prefix string to be printed
   * @return Number of milliseconds elapsed
   */
  public long restart(String str) {
    return this.restart(str, System.err);
  }

  /**
   * Print elapsed time and restart timer.
   *
   * @param str    Additional prefix string to be printed
   * @param writer PrintWriter on which to write output
   * @return Number of milliseconds elapsed
   */
  public long restart(String str, PrintWriter writer) {
    long elapsed = this.report(str, writer);
    this.start();
    return elapsed;
  }

  /**
   * Print the timing done message with elapsed time in x.y seconds.
   * Restart the timer too.
   */
  public void end(String msg) {
    long elapsed = System.nanoTime() - start;
    log.info(msg + " done [" + nf.format(((double) elapsed) / SECOND_DIVISOR) + " sec].");
    this.start();
  }


  // stop ===========================================================

  /**
   * Stop timer.
   *
   * @return Number of milliseconds elapsed
   */
  public long stop() {
    long elapsed = this.report();
    this.start = 0;
    return elapsed;
  }

  /**
   * Print elapsed time and stop timer.
   *
   * @param str    Additional prefix string to be printed
   * @param stream PrintStream on which to write output
   * @return Number of milliseconds elapsed
   */
  public long stop(String str, PrintStream stream) {
    this.report(str, stream);
    return this.stop();
  }

  /**
   * Print elapsed time to {@code System.err} and stop timer.
   *
   * @param str Additional prefix string to be printed
   * @return Number of milliseconds elapsed
   */
  public long stop(String str) {
    return stop(str, System.err);
  }

  /**
   * Print elapsed time and stop timer.
   *
   * @param str    Additional prefix string to be printed
   * @param writer PrintWriter on which to write output
   * @return Number of milliseconds elapsed
   */
  public long stop(String str, PrintWriter writer) {
    this.report(str, writer);
    return this.stop();
  }

  // startTime ======================================================

  /**
   * Start (static) timer.
   */
  public static void startTime() {
    startTime = System.nanoTime();
  }

  // endTime ========================================================

  /**
   * Return elapsed time on (static) timer (without stopping timer).
   *
   * @return Number of milliseconds elapsed
   */
  public static long endTime() {
    return (System.nanoTime() - startTime) / MILLISECOND_DIVISOR;
  }

  /**
   * Print elapsed time on (static) timer (without stopping timer).
   *
   * @param str    Additional prefix string to be printed
   * @param stream PrintStream on which to write output
   * @return Number of milliseconds elapsed
   */
  public static long endTime(String str, PrintStream stream) {
    long elapsed = endTime();
    stream.println(str + " Time elapsed: " + (elapsed) + " ms");
    return elapsed;
  }

  /**
   * Print elapsed time on (static) timer to
   * {@code System.err} (without stopping timer).
   *
   * @param str Additional prefix string to be printed
   * @return Number of milliseconds elapsed
   */
  public static long endTime(String str) {
    return endTime(str, System.err);
  }

  // chris' new preferred methods 2006 for loading things etc.


  /** Print the start of timing message to stderr and start the timer. */
  public void doing(String str) {
    log.info(str + " ... ");
    start();
  }

  /** Finish the line from doing() with the end of the timing done message
   *  and elapsed time in x.y seconds.
   */
  public void done() {
    log.info("done [" + toSecondsString() + " sec].");
  }

  /** Give a line saying that something is " done".
   */
  public void done(String msg) {
    log.info(msg + " done [" + toSecondsString() + " sec].");
  }

  public void done(StringBuilder msg) {
    msg.append(" done [").append(toSecondsString()).append(" sec].");
    log.info(msg.toString());
  }

  /** This method allows you to show the results of timing according to another class' logger.
   *  E.g., {@code timing.done(logger, "Loading lexicon")}.
   *
   *  @param logger Logger to log a timed operation with
   *  @param msg Message to report.
   */
  public void done(Redwood.RedwoodChannels logger, StringBuilder msg) {
    msg.append("... done [").append(toSecondsString()).append(" sec].");
    logger.info(msg.toString());
  }

  public void done(Redwood.RedwoodChannels logger, String msg) {
    logger.info(msg + " ... done [" + toSecondsString() + " sec].");
  }

  /** Print the start of timing message to stderr and start the timer. */
  public static void startDoing(String str) {
    log.info(str + " ... ");
    startTime();
  }

  /** Finish the line from startDoing with the end of the timing done message
   *  and elapsed time in x.y seconds.
   */
  public static void endDoing() {
    long elapsed = System.nanoTime() - startTime;
    log.info("done [" + nf.format(((double) elapsed) / SECOND_DIVISOR) +
                       " sec].");
  }

  /** Finish the line from startDoing with the end of the timing done message
   *  and elapsed time in x.y seconds.
   */
  public static void endDoing(String msg) {
    long elapsed = System.nanoTime() - startTime;
    log.info(msg + " done [" + nf.format(((double) elapsed) / SECOND_DIVISOR) +
                       " sec].");
  }

  // tick ===========================================================

  /**
   * Restart (static) timer.
   *
   * @return Number of milliseconds elapsed
   */
  public static long tick() {
    long elapsed = (System.nanoTime() - startTime) / MILLISECOND_DIVISOR;
    startTime();
    return elapsed;
  }

  /**
   * Print elapsed time and restart (static) timer.
   *
   * @param str    Additional prefix string to be printed
   * @param stream PrintStream on which to write output
   * @return Number of milliseconds elapsed
   */
  public static long tick(String str, PrintStream stream) {
    long elapsed = tick();
    stream.println(str + " Time elapsed: " + (elapsed) + " ms");
    return elapsed;
  }

  /**
   * Print elapsed time to {@code System.err} and restart (static) timer.
   *
   * @param str Additional prefix string to be printed
   * @return Number of milliseconds elapsed
   */
  public static long tick(String str) {
    return tick(str, System.err);
  }

  // import java.util.Calendar;
  // import java.util.TimeZone;

  // // Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("EST"));
  //  Calendar cal = Calendar.getInstance(TimeZone.getDefault());
  //  String DATE_FORMAT = "yyyy-MM-dd HH:mm:ss";
  //  java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat(DATE_FORMAT);
  // // sdf.setTimeZone(TimeZone.getTimeZone("EST"));
  // sdf.setTimeZone(TimeZone.getDefault());
  // System.out.println("Now : " + sdf.format(cal.getTime()));

  @Override
  public String toString() {
    return "Timing[start=" + startTime + ']';
  }

}
