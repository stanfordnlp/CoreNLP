package edu.stanford.nlp.util; 
import edu.stanford.nlp.util.logging.Redwood;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.util.ArrayList;

/**
 * Utilities for monitoring memory use, including peak memory use.
 *
 */
public class MemoryMonitor  {

  /** A logger for this class */
  private static Redwood.RedwoodChannels log = Redwood.channels(MemoryMonitor.class);

  /** The number of swaps per second above which {@link #systemIsSwapping} returns true. */
  public static final int MAX_SWAPS = 50;

  /** The time (in milliseconds) of the last poll of the system. */
  protected long lastPoll;
  /** The minimum time (in milliseconds) between unforced polls of the system. */
  protected long pollEvery;
  /** The free system memory, in kilobytes, from the last poll. */
  protected int freeMem;
  /** The used swap space, in kilobytes, from the last poll. */
  protected int usedSwap;
  /** The swaps (in plus out) per second, from the last poll. */
  protected int swaps;
  /** The Java runtime. */
  protected Runtime r;

  /** Create a monitor which polls the system at most once a minute. This polls the system with vmstat immediately. */
  public MemoryMonitor() {
    this(60000); // 1 min default
  }

  /**
   * Create a monitor. This polls the system with vmstat immediately.
   *
   * @param millis The minimum time, in milliseconds, between unforced polls of the system
   */
  public MemoryMonitor(long millis) {
    lastPoll = 0;
    pollEvery = millis;
    freeMem = 0;
    usedSwap = 0;
    swaps = 0;
    r = Runtime.getRuntime();
    pollVMstat(true);
  }

  // TODO I don't think anyone uses this
  /**
   * Set the minimum time between unforced polls of the system.
   *
   * @param millis The minimum time, in milliseconds
   */
  public void pollAtMostEvery(long millis) {
    pollEvery = millis;
  }

  /**
   * Returns the maximum memory the JVM will attempt to use.
   *
   * @return The maximum memory, in kilobytes
   */
  public int getMaxMemory() {
    return (int) (r.maxMemory() / 1024);
  }

  /**
   * Returns the memory still available to the JVM (maximum memory minus memory in use), without garbage collecting first.
   *
   * @return The available memory, in kilobytes
   */
  public int getMaxAvailableMemory() {
    return getMaxAvailableMemory(false);
  }

  // kilobytes
  /**
   * Returns the memory still available to the JVM (maximum memory minus memory in use).
   *
   * @param accurate Whether to call System.gc() first
   * @return The available memory, in kilobytes
   */
  public int getMaxAvailableMemory(boolean accurate) {
    if (accurate) {
      System.gc();
    }
    return (int) ((r.maxMemory() - r.totalMemory() + r.freeMemory()) / 1024);
  }

  /**
   * Returns the memory in use by the JVM, without garbage collecting first.
   *
   * @return The used memory, in kilobytes
   */
  public int getUsedMemory() {
    return getUsedMemory(false);
  }

  /**
   * Returns the memory in use by the JVM.
   *
   * @param accurate Whether to call System.gc() first
   * @return The used memory, in kilobytes
   */
  public int getUsedMemory(boolean accurate) {
    if (accurate) {
      System.gc();
    }
    return getUsedMemoryStatic(r);
  }

  /**
   * Returns the memory in use by the current JVM.
   *
   * @return The used memory, in kilobytes
   */
  public static int getUsedMemoryStatic() {
    return getUsedMemoryStatic(Runtime.getRuntime());
  }

  /**
   * Returns the memory in use according to the given runtime.
   *
   * @param r The runtime
   * @return The used memory, in kilobytes
   */
  public static int getUsedMemoryStatic(Runtime r) {
    return (int) ((r.totalMemory() - r.freeMemory()) / 1024);
  }

  /**
   * Returns the memory in use by the current JVM as a String, such as "512k" or "20m" (rounded down).
   *
   * @return The used memory as a String
   */
  public static String getUsedMemoryString() {
    int usedK = getUsedMemoryStatic();
    if (usedK < 1024) {
      return String.valueOf(usedK) + "k";
    } else {
      int usedM = usedK / 1024;
      return String.valueOf(usedM) + "m";
    }
  }

  /**
   * Returns the free system memory, polling the system with vmstat if the last poll is old enough.
   *
   * @param accurate Whether to call System.gc() first
   * @return The free system memory, in kilobytes
   */
  public int getSystemFreeMemory(boolean accurate) {
    if (accurate) {
      System.gc();
    }
    pollVMstat(false);
    return freeMem;
  }

  /**
   * Returns the used swap space, polling the system with vmstat if the last poll is old enough.
   *
   * @return The used swap space, in kilobytes
   */
  public int getSystemUsedSwap() {
    pollVMstat(false);
    return usedSwap;
  }

  /**
   * Returns the swaps (in plus out) per second, polling the system with vmstat if the last poll is old enough.
   *
   * @return The number of swaps per second
   */
  public double getSystemSwapsPerSec() {
    pollVMstat(false);
    return swaps;
  }

  /**
   * Reads whitespace-separated fields from the given lines and positions of a reader.
   * Field {@code i} to find is the {@code positions[i]}th non-empty field (counting from 1) of line
   * {@code lineNums[i]} (counting from 1); the fields must be requested in order.
   *
   * @param br The reader to read from
   * @param splitStr The regular expression used to split lines into fields
   * @param lineNums The line of each field to find
   * @param positions The position of each field to find
   * @return The fields found, in order
   * @throws IOException If reading fails
   */
  protected static ArrayList<String> parseFields(BufferedReader br, String splitStr,
      int[] lineNums, int[] positions) throws IOException {
    int currLine = 0;
    int processed = 0;
    ArrayList<String> found = new ArrayList<>();
    while (br.ready()) {
      String[] fields = br.readLine().split(splitStr);
      currLine++;
      if (currLine == lineNums[processed]) {
        int currPosition = 0;
        for (String f : fields) {
          if (f.length() > 0) {
            currPosition++;
            if (currPosition == positions[processed]) {
              found.add(f);
              processed++;
              if (processed == positions.length) {
                break;
              }
            }
          }
        }
      }
    }
    return found;
  }

  /**
   * Poll the system memory using the {@code free} command, unless the last poll was too recent and
   * {@code force} is false. Any exception is logged rather than thrown.
   *
   * @param force Whether to poll even if the last poll was recent
   */
  public void pollFree(boolean force) {
    if (!force) {
      long time = System.currentTimeMillis();
      if (time - lastPoll < pollEvery) {
        return;
      }
    }

    Process p = null;
    int[] freeLines = { 2, 4 };
    int[] freePositions = { 4, 3 };

    lastPoll = System.currentTimeMillis();
    try {
      p = r.exec("free");
      p.waitFor();
      BufferedReader bri = new BufferedReader(new InputStreamReader(p.getInputStream()));
      ArrayList<String> l = parseFields(bri, " ", freeLines, freePositions);
      freeMem = Integer.parseInt(l.get(1));
      usedSwap = Integer.parseInt(l.get(2));
    } catch (Exception e) {
      log.info(e);
    } finally {
      if (p != null) {
        p.destroy();
      }
    }
  }

  /**
   * Poll the system memory and swapping using the {@code vmstat} command, unless the last poll was too recent and
   * {@code force} is false. Any exception is printed rather than thrown.
   *
   * @param force Whether to poll even if the last poll was recent
   */
  public void pollVMstat(boolean force) {
    if (!force) {
      long time = System.currentTimeMillis();
      if (time - lastPoll < pollEvery) {
        return;
      }
    }

    Process p = null;
    int[] lines = { 4, 4, 4, 4 };
    int[] positions = { 3, 4, 7, 8 };

    try {
      p = r.exec("vmstat 1 2");
      p.waitFor();
      long time = System.currentTimeMillis();
      BufferedReader bri = new BufferedReader(new InputStreamReader(p.getInputStream()));
      ArrayList<String> l = parseFields(bri, " ", lines, positions);
      usedSwap = Integer.parseInt(l.get(0));
      freeMem = Integer.parseInt(l.get(1));
      swaps = Integer.parseInt(l.get(2)) + Integer.parseInt(l.get(3));
      lastPoll = time;
    } catch (Exception e) {
      e.printStackTrace();
    } finally {
      if (p != null) {
        p.destroy();
      }
    }
  }

  /**
   * Returns whether the system is doing more than {@link #MAX_SWAPS} swaps per second.
   *
   * @return Whether the system is swapping
   */
  public boolean systemIsSwapping() {
    return (getSystemSwapsPerSec() > MAX_SWAPS);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("lastPoll:").append(lastPoll);
    sb.append(" pollEvery:").append(pollEvery);
    sb.append(" freeMem:").append(freeMem);
    sb.append(" usedSwap:").append(usedSwap);
    sb.append(" swaps:").append(swaps);
    sb.append(" maxAvailable:").append(getMaxAvailableMemory(false));
    sb.append(" used:").append(getUsedMemory(false));
    return sb.toString();
  }

  /**
   * This class offers a simple way to track the peak memory used by a program.
   * Simply launch a <code>PeakMemoryMonitor</code> as
   *
   * <blockquote><code>
   * Thread monitor = new Thread(new PeakMemoryMonitor());<br>
   * monitor.start()
   * </code></blockquote>
   *
   * and then when you want to stop monitoring, call
   *
   * <blockquote><code>
   * monitor.interrupt();
   * monitor.join();
   * </code></blockquote>
   *
   * You only need the last line if you want to be sure the monitor stops before
   * you move on in the code; and strictly speaking, you should surround the
   * <code>monitor.join()</code> call with a <code>try/catch</code> block, as
   * the <code>Thread</code> you are running could itself be interrupted, so you
   * should actually have something like
   *
   * <blockquote><code>
   * monitor.interrupt();
   * try {
   *   monitor.join();
   * } catch (InterruptedException ex) {
   *   // handle the exception
   * }
   * </code></blockquote>
   *
   * or else throw the exception.
   *
   * @author ilya
   */
  public static class PeakMemoryMonitor implements Runnable {
    private static final float GIGABYTE = 1 << 30;
    private static final int DEFAULT_POLL_FREQUENCY = 1000; /* 1 second */
    private static final int DEFAULT_LOG_FREQUENCY = 60000; /* 1 minute */
    private int pollFrequency;
    private int logFrequency;
    private Timing timer;
    private PrintStream outstream;
    private long peak = 0;

    /** Create a monitor which polls every second and logs to System.err every minute. */
    public PeakMemoryMonitor() {
      this(DEFAULT_POLL_FREQUENCY, DEFAULT_LOG_FREQUENCY);
    }

    /**
     * Create a monitor which logs to System.err.
     *
     * @param pollFrequency frequency, in milliseconds, with which to poll
     * @param logFrequency frequency, in milliseconds, with which to log maximum memory
     *          used so far
     */
    public PeakMemoryMonitor(int pollFrequency, int logFrequency) {
      this(pollFrequency, logFrequency, System.err);
    }

    /**
     * Create a monitor.
     *
     * @param pollFrequency frequency, in milliseconds, with which to poll
     * @param logFrequency frequency, in milliseconds, with which to log maximum memory
     *          used so far
     * @param out The stream to log to
     */
    public PeakMemoryMonitor(int pollFrequency, int logFrequency,
                             PrintStream out) {
      this.pollFrequency = pollFrequency;
      this.logFrequency = logFrequency;
      this.outstream = out;
      this.timer = new Timing();
    }

    public void run() {
      Runtime runtime = Runtime.getRuntime();
      timer.start();

      while (true) {
        peak = Math.max(peak, runtime.totalMemory() - runtime.freeMemory());
        if (timer.report() > logFrequency) {
          log();
          timer.restart();
        }

        try {
          Thread.sleep(pollFrequency);
        } catch (InterruptedException e) {
          log();
          throw new RuntimeInterruptedException(e);
        }
      }
    }

    /** Prints the maximum memory used so far, in gigabytes, to the output stream. */
    public void log() {
      outstream.println(String.format("Maximum memory used: %.1f GB", peak / GIGABYTE));
    }
  }

  /**
   * Test method: creates a MemoryMonitor, polls it, and prints its state, while running a PeakMemoryMonitor.
   *
   * @param args Not used
   * @throws InterruptedException If interrupted while waiting for the PeakMemoryMonitor to finish
   */
  public static void main(String[] args) throws InterruptedException {
    Thread pmm = new Thread(new PeakMemoryMonitor());
    pmm.start();

    long time = System.currentTimeMillis();
    MemoryMonitor mm = new MemoryMonitor();
    long time2 = System.currentTimeMillis();
    System.out.println("Created MemoryMonitor.  Took " + (time2 - time)
                       + " milliseconds.");
    System.out.println(mm);

    time = System.currentTimeMillis();
    mm.pollVMstat(true);
    time2 = System.currentTimeMillis();
    System.out.println("Second Poll.  Took " + (time2 - time)
                       + " milliseconds.");
    System.out.println(mm);

    pmm.interrupt();
    pmm.join();
  }
}
