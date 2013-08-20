package edu.stanford.nlp.util;

import edu.stanford.cs.ra.RA;

/**
 * Various utility methods to be used in conjunction with Dan Ramage's
 * ResearchAssistant (RA) package.
 *
 * @author Bill MacCartney
 */
public class RAUtils {

  /**
   * If <code>RA</code> is active, the given message (formatted a la
   * <code>printf()</code>) is written to <code>RA.stream</code> with the given
   * XML element name.  Otherwise, the message is printed to
   * <code>System.err</code>.
   */
  public static void msgf(String eltName, String format, Object... args) {
    String msg = String.format(format, args);
    if (RA.isActive()) {
      RA.stream.line(eltName, msg);
    } else {
      System.err.println(msg);
    }
  }

  /**
   * If <code>RA</code> is active, the given message is written to
   * <code>RA.stream</code> with the given XML element name.  Otherwise, the
   * message is printed to <code>System.err</code>.
   */
  public static void msgln(String eltName, String msg) {
    msgf(eltName, "%s%n", msg);
  }

  /**
   * If <code>RA</code> is active, a memory usage message is written to
   * <code>RA.stream</code> with the XML element name <code>memory</code>.
   * Otherwise, the message is printed to <code>System.err</code>.
   */
  public static void reportMemory() {
    Runtime runtime = Runtime.getRuntime();
    long mb = 1024 * 1024;
    long total = runtime.totalMemory();
    long free = runtime.freeMemory();
    long used = (total - free);
    if (RA.isActive()) {
      msgf("memory", String.format("%d MB", used / mb));
    } else {
      msgf("memory", "......................... memory in use: %d MB .........................%n", used / mb);
    }
  }

}
