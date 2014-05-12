package edu.stanford.nlp.util;

import java.io.PrintStream;
import java.io.PrintWriter;
import java.text.NumberFormat;
import java.text.DecimalFormat;

/**
 * A class for measuring how long things take.  For backward
 * compatibility, this class contains static methods, but the
 * preferred usage is to instantiate a Timing object and use instance
 * methods.
 *
 * @author Bill MacCartney
 */
public class Timing {

  /**
   * Stores the time at which the timer was started.
   */
  private long start;

  /**
   * Stores the time at which the (static) timer was started.
   */
  private static long startTime = System.currentTimeMillis();

  /** Stores a suitable formatter for printing seconds nicely. */
  private static final NumberFormat nf = new DecimalFormat("0.0");


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
    start = System.currentTimeMillis();
  }
  
  /**
   * Start timer & print a message.
   */
  // Thang Mar14
  public void start(String msg, PrintStream stream) {
    start = System.currentTimeMillis();
    stream.println(msg);
  }
  public void start(String msg) {
    start(msg, System.err);
  }
  
  // report =========================================================

  /**
   * Return elapsed time (without stopping timer).
   *
   * @return Number of milliseconds elapsed
   */
  public long report() {
    return System.currentTimeMillis() - start;
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
    stream.println(str + " Time elapsed: " + (elapsed) + " ms");
    return elapsed;
  }

  /**
   * Print elapsed time to <code>System.err</code> (without stopping timer).
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
   * @param str    Additional prefix string to be printed
   * @param writer PrintWriter on which to write output
   * @return Number of milliseconds elapsed
   */
  public long report(String str, PrintWriter writer) {
    long elapsed = this.report();
    writer.println(str + " Time elapsed: " + (elapsed) + " ms");
    return elapsed;
  }

  /** Returns the number of seconds passed since the timer started in the form "d.d". */
  public String toSecondsString() {
    return toSecondsString(report());
  }

  public static String toSecondsString(long elapsed) {
    return nf.format(((double) elapsed) / 1000);
  }

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
   * Print elapsed time to <code>System.err</code> and restart timer.
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
  // Thang Mar14
  public void end(String msg) {
    long elapsed = System.currentTimeMillis() - start;
    System.err.println(msg + " done [" + nf.format(((double) elapsed) / 1000) + " sec].");
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
   * Print elapsed time to <code>System.err</code> and stop timer.
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
    startTime = System.currentTimeMillis();
  }

  // endTime ========================================================

  /**
   * Return elapsed time on (static) timer (without stopping timer).
   *
   * @return Number of milliseconds elapsed
   */
  public static long endTime() {
    return System.currentTimeMillis() - startTime;
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
   * <code>System.err</code> (without stopping timer).
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
    System.err.print(str);
    System.err.print(" ... ");
    System.err.flush();
    start();
  }

  /** Finish the line from startDoing with the end of the timing done message
   *  and elapsed time in x.y seconds.
   */
  public void done() {
    System.err.println("done [" + toSecondsString() + " sec].");
  }

  /** Give a line saying that something is " done".
   */
  public void done(String msg) {
    System.err.println(msg + " done [" + toSecondsString() + " sec].");
  }

  /** Print the start of timing message to stderr and start the timer. */
  public static void startDoing(String str) {
    System.err.print(str);
    System.err.print(" ... ");
    System.err.flush();
    startTime();
  }

  /** Finish the line from startDoing with the end of the timing done message
   *  and elapsed time in x.y seconds.
   */
  public static void endDoing() {
    long elapsed = System.currentTimeMillis() - startTime;
    System.err.println("done [" + nf.format(((double) elapsed) / 1000) +
                       " sec].");
  }

  /** Finish the line from startDoing with the end of the timing done message
   *  and elapsed time in x.y seconds.
   */
  public static void endDoing(String msg) {
    long elapsed = System.currentTimeMillis() - startTime;
    System.err.println(msg + " done [" + nf.format(((double) elapsed) / 1000) +
                       " sec].");
  }

  // tick ===========================================================

  /**
   * Restart (static) timer.
   *
   * @return Number of milliseconds elapsed
   */
  public static long tick() {
    long elapsed = System.currentTimeMillis() - startTime;
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
   * Print elapsed time to <code>System.err</code> and restart (static) timer.
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
    return "Timing[start=" + startTime + "]";
  }

}
