package edu.stanford.nlp.util;

import java.text.SimpleDateFormat;
import java.util.*;
import java.io.*;


/**
 * Useful methods for running shell commands, getting the process ID, checking
 * memory usage, etc.
 *
 * @author Bill MacCartney
 * @author Steven Bethard ({@link #run})
 */
public class SystemUtils {

  private SystemUtils() { } // static methods

  /**
   * Runtime exception thrown by execute.
   */
  public static class ProcessException extends RuntimeException {
    private static final long serialVersionUID = 1L;
    /**
     * Creates an exception with the given message.
     *
     * @param string The detail message
     */
    public ProcessException(String string) {
      super(string);
    }
    /**
     * Wraps another exception.
     *
     * @param cause The cause
     */
    public ProcessException(Throwable cause) {
      super(cause);
    }
  }
  
  /**
   * Start the process defined by the ProcessBuilder, and run until complete.
   * 
   * Process output and errors will be written to System.out and System.err,
   * respectively.
   *  
   * @param builder The ProcessBuilder defining the process to run.
   */
  public static void run(ProcessBuilder builder) {
    run(builder, null, null);
  }

  /**
   * Start the process defined by the ProcessBuilder, and run until complete.
   * 
   * @param builder The ProcessBuilder defining the process to run.
   * @param output  Where the process output should be written. If null, the
   *                process output will be written to System.out.
   * @param error   Where the process error output should be written. If null,
   *                the process error output will written to System.err. 
   */
  public static void run(ProcessBuilder builder, Writer output, Writer error) {
    try {
      Process process = builder.start();
      consume(process, output, error);
      int result = process.waitFor();
      if (result != 0) {
        String msg = "process %s exited with value %d";
        throw new ProcessException(String.format(msg, builder.command(), result));
      }
    } catch (InterruptedException | IOException e) {
      throw new ProcessException(e);
    }
  }
  
  /**
   * Helper method that consumes the output and error streams of a process.
   * 
   * This should avoid deadlocks where, e.g. the process won't complete because
   * it is waiting for output to be read from stdout or stderr.
   * 
   * @param process      A running process.
   * @param outputWriter Where to write output. If null, System.out is used.
   * @param errorWriter  Where to write error output. If null, System.err is used.
   */
  private static void consume(Process process, Writer outputWriter, Writer errorWriter)
          throws InterruptedException {
    if (outputWriter == null) {
      outputWriter = new OutputStreamWriter(System.out);
    }
    if (errorWriter == null) {
      errorWriter = new OutputStreamWriter(System.err);
    }
    WriterThread outputThread = new WriterThread(process.getInputStream(), outputWriter);
    WriterThread errorThread = new WriterThread(process.getErrorStream(), errorWriter);
    outputThread.start();
    errorThread.start();
    outputThread.join();
    errorThread.join();
  }
  
  /**
   * Thread that reads from an Reader and writes to a Writer.
   * 
   * Used as a helper for {@link #consume} to avoid deadlocks.
   */
  private static class WriterThread extends Thread {
    private Reader reader;
    private Writer writer;
    public WriterThread(InputStream inputStream, Writer writer) {
      this.reader = new InputStreamReader(inputStream);
      this.writer = writer;
    }

    @Override
    public void run() {
      char[] buffer = new char[4096];
      while (true) {
        try {
          int read = this.reader.read(buffer);
          if (read == -1) {
            break;
          }
          this.writer.write(buffer, 0, read);
          this.writer.flush();
        } catch (IOException e) {
          throw new ProcessException(e);
        }
        Thread.yield();
      }
    }
  }

  /**
   * Helper class that acts as a output stream to a process
   */
  public static class ProcessOutputStream extends OutputStream
  {
    private Process process;
    private Thread outWriterThread;
    private Thread errWriterThread;

    /**
     * Starts the given command, copying its output to {@code System.out} and
     * its error output to {@code System.err}.
     *
     * @param cmd The command and its arguments
     * @throws IOException If the process cannot be started
     */
    public ProcessOutputStream(String[] cmd) throws IOException {
      this(new ProcessBuilder(cmd), new PrintWriter(System.out), new PrintWriter(System.err));
    }

    /**
     * Starts the given command, copying both its output and its error output to {@code writer}.
     *
     * @param cmd The command and its arguments
     * @param writer Where the process output and error output are written
     * @throws IOException If the process cannot be started
     */
    public ProcessOutputStream(String[] cmd, Writer writer) throws IOException {
      this(new ProcessBuilder(cmd), writer, writer);
    }

    /**
     * Starts the given command, copying its output and error output to the given writers.
     *
     * @param cmd The command and its arguments
     * @param output Where the process output is written
     * @param error Where the process error output is written
     * @throws IOException If the process cannot be started
     */
    public ProcessOutputStream(String[] cmd, Writer output, Writer error) throws IOException {
      this(new ProcessBuilder(cmd), output, error);
    }

    /**
     * Starts the process defined by {@code builder}, copying its output and error
     * output to the given writers using {@link StreamGobbler} threads.
     *
     * @param builder The ProcessBuilder defining the process to run
     * @param output Where the process output is written
     * @param error Where the process error output is written
     * @throws IOException If the process cannot be started
     */
    public ProcessOutputStream(ProcessBuilder builder, Writer output, Writer error) throws IOException {
      this.process = builder.start();

      errWriterThread = new StreamGobbler(process.getErrorStream(), error);
      outWriterThread = new StreamGobbler(process.getInputStream(), output);
      errWriterThread.start();
      outWriterThread.start();
    }

    @Override
    public void flush() throws IOException {
      process.getOutputStream().flush();
    }

    @Override
    public void write(int b) throws IOException {
      process.getOutputStream().write(b);
    }

    @Override
    public void close() throws IOException {
      process.getOutputStream().close();
      try {
        errWriterThread.join();
        outWriterThread.join();
        process.waitFor();
      } catch (InterruptedException e) {
        throw new ProcessException(e);
      }
    }

  } // end static class StaticOutputStream

  /**
   * Runs the shell command which is specified, along with its arguments, in the
   * given {@code String} array.  If there is any regular output or error
   * output, it is appended to the given {@code StringBuilder}s.
   *
   * @param cmd The command and its arguments
   * @param outputLines Where the standard output is appended, or null to not read it
   * @param errorLines Where the error output is appended, or null to not read it
   * @throws IOException If the process cannot be started or its output cannot be read
   */
  public static void runShellCommand(String[] cmd,
                                     StringBuilder outputLines,
                                     StringBuilder errorLines)
          throws IOException {
    runShellCommand(cmd, null, outputLines, errorLines);
  }

  /**
   * Runs the shell command which is specified, along with its arguments, in the
   * given {@code String} array.  If there is any regular output or error
   * output, it is appended to the given {@code StringBuilder}s.
   *
   * @param cmd The command and its arguments
   * @param dir The working directory of the process, or null to use the current one
   * @param outputLines Where the standard output is appended, or null to not read it
   * @param errorLines Where the error output is appended, or null to not read it
   * @throws IOException If the process cannot be started or its output cannot be read
   */
  public static void runShellCommand(String[] cmd,
                                     File dir,
                                     StringBuilder outputLines,
                                     StringBuilder errorLines)
          throws IOException {
    Process p = Runtime.getRuntime().exec(cmd, null, dir);
    if (outputLines != null) {
      try (BufferedReader in = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
        for (String line; (line = in.readLine()) != null; ) {
          outputLines.append(line).append("\n");
        }
      }
    }
    if (errorLines != null) {
      try (BufferedReader err = new BufferedReader(new InputStreamReader(p.getErrorStream()))) {
        for (String line; (line = err.readLine()) != null; ) {
          errorLines.append(line).append("\n");
        }
      }
    }
  }


  /**
   * Runs the shell command which is specified, along with its arguments, in the
   * given {@code String}.  If there is any regular output or error output,
   * it is appended to the given {@code StringBuilder}s.
   *
   * @param cmd The command, which is passed to {@link Runtime#exec(String[], String[], File)}
   *            as a single array element, so it is not split into arguments
   * @param outputLines Where the standard output is appended, or null to not read it
   * @param errorLines Where the error output is appended, or null to not read it
   * @throws IOException If the process cannot be started or its output cannot be read
   */
  public static void runShellCommand(String cmd,
                                     StringBuilder outputLines,
                                     StringBuilder errorLines)
          throws IOException {
    runShellCommand(new String[] {cmd}, outputLines, errorLines);
  }


  /**
   * Runs the shell command which is specified, along with its arguments, in the
   * given {@code String} array.  If there is any regular output, it is
   * appended to the given {@code StringBuilder}.  If there is any error
   * output, it is swallowed (!).
   *
   * @param cmd The command and its arguments
   * @param outputLines Where the standard output is appended, or null to not read it
   * @throws IOException If the process cannot be started or its output cannot be read
   */
  public static void runShellCommand(String[] cmd,
                                     StringBuilder outputLines)
          throws IOException {
    runShellCommand(cmd, outputLines, null);
  }


  /**
   * Runs the shell command which is specified, along with its arguments, in the
   * given {@code String}.  If there is any regular output, it is appended
   * to the given {@code StringBuilder}.  If there is any error output, it
   * is swallowed (!).
   *
   * @param cmd The command, which is passed to {@link Runtime#exec(String[], String[], File)}
   *            as a single array element, so it is not split into arguments
   * @param outputLines Where the standard output is appended, or null to not read it
   * @throws IOException If the process cannot be started or its output cannot be read
   */
  public static void runShellCommand(String cmd,
                                     StringBuilder outputLines)
          throws IOException {
    runShellCommand(new String[] {cmd}, outputLines, null);
  }


  /**
   * Runs the shell command which is specified, along with its arguments, in the
   * given {@code String} array.  If there is any output, it is swallowed (!).
   *
   * @param cmd The command and its arguments
   * @throws IOException If the process cannot be started or its output cannot be read
   */
  public static void runShellCommand(String[] cmd)
          throws IOException {
    runShellCommand(cmd, null, null);
  }


  /**
   * Runs the shell command which is specified, along with its arguments, in the
   * given {@code String}.  If there is any output, it is swallowed (!).
   *
   * @param cmd The command, which is passed to {@link Runtime#exec(String[], String[], File)}
   *            as a single array element, so it is not split into arguments
   * @throws IOException If the process cannot be started or its output cannot be read
   */
  public static void runShellCommand(String cmd)
          throws IOException {
    runShellCommand(new String[] {cmd}, null, null);
  }


  /**
   * Returns the process ID of this JVM, as reported by {@link ProcessHandle}.
   *
   * @return The process ID of this JVM
   * @throws UnsupportedOperationException If the platform does not support getting the process ID
   */
  public static long getPID() {
    return ProcessHandle.current().pid();
  }


  /**
   * Returns the process ID of this JVM, or -1 if it cannot be determined.
   *
   * @return The process ID of this JVM, or -1 if {@link #getPID()} throws
   */
  public static long getPIDNoExceptions() {
    try {
      return SystemUtils.getPID();
    } catch (UnsupportedOperationException e) {
      return -1;
    }
  }


  /**
   * Returns the number of megabytes (MB) of memory in use.
   *
   * @return The allocated heap memory minus the free heap memory, in MB
   */
  public static int getMemoryInUse() {
    Runtime runtime = Runtime.getRuntime();
    long mb = 1024 * 1024;
    long total = runtime.totalMemory();
    long free = runtime.freeMemory();
    return (int) ((total - free) / mb);
  }

  /**
   * Returns the string value of the stack trace for the given Throwable.
   *
   * @param t The Throwable whose stack trace is wanted
   * @return The stack trace, as printed by {@link Throwable#printStackTrace(PrintStream)}
   */
  public static String getStackTraceString(Throwable t) {
    ByteArrayOutputStream bs = new ByteArrayOutputStream();
    t.printStackTrace(new PrintStream(bs));
    return bs.toString();
  }

  // ----------------------------------------------------------------------------

  /**
   * Returns a String representing the current date and time in the given
   * format.
   *
   * @param fmt A {@link SimpleDateFormat} pattern
   * @return The current date and time, formatted with {@code fmt}
   * @see <a href="http://java.sun.com/j2se/1.5.0/docs/api/java/text/SimpleDateFormat.html">SimpleDateFormat</a>
   */
  public static String getTimestampString(String fmt) {
    return (new SimpleDateFormat(fmt)).format(new Date());
  }

  /**
   * Returns a String representing the current date and time in the format
   * "20071022-140522".
   *
   * @return The current date and time
   */
  public static String getTimestampString() {
    return getTimestampString("yyyyMMdd-HHmmss");
  }


  /**
   * Demonstrates the methods of this class by printing the date, the PID,
   * and the memory in use before and after allocating and freeing a large list.
   *
   * @param args Ignored
   * @throws Exception If running a shell command fails
   */
  public static void main(String[] args) throws Exception {
    StringBuilder out = new StringBuilder();
    runShellCommand("date", out);
    System.out.println("The date is " + out);
    long pid = getPID();
    System.out.println("The PID is " + pid);
    System.out.println("The memory in use is " + getMemoryInUse() + "MB");
    List<String> foo = new ArrayList<>();
    for (int i = 0; i < 5000000; i++) {
      foo.add("0123456789");
    }
    System.out.println("The memory in use is " + getMemoryInUse() + "MB");
    foo = null;
    System.gc();
    System.out.println("The memory in use is " + getMemoryInUse() + "MB");
  }

}
