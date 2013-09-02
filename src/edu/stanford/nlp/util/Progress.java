package edu.stanford.nlp.util;

/**
 * Output progress percent to standard out. Will print backspace characters to
 * update percentage in-place in the terminal.
 *
 * @author melevin
 */
public class Progress {

  /** Set to false to disable backspace character */
  public static boolean backSpace = true;

  /**
   * Call on each iteration of a loop.
   *
   * @param header  Prints this before the percentage
   * @param n       Loop iteration
   * @param total   Total number of iterations
   * @param inc     Output percentage in increments
   */
  public static void print(final String header, long n, long total, long inc) {
    if (total < 2)
      return;

    // Simple prints without backspace
    if (!backSpace) {
      if (n == 0)
        System.err.print(header + ": ");
      else if (n == total - 1)
        System.err.printf("done\n");
    }

    // Updating print outs
    else if (n == 0)
      System.err.print(header + ":  0%");
    else if (n == total - 1)
      System.err.printf("\b\b\bdone\n");
    else if (n >= total)
      System.err.printf("\b\b\b\b????\n");
    else if (n % inc == 0)
      System.err.printf("\b\b\b%2d%%", 100 * n / total);
  }

  /**
   * Call on each iteration of a loop.
   *
   * @param header  Prints this before the percentage
   * @param n       Loop iteration
   * @param total   Total number of iterations
   */
  public static void print(final String header, long n, long total) {
    long inc = total / 100;
    if (inc < 1)
      inc = 1;
    print(header, n, total, inc);
  }

  /** Print "done" */
  public static void done() {
    System.err.printf(backSpace ? "\b\b\bdone\n" : "done\n");
  }
}
