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
    public ProcessException(String string) {
      super(string);
    }
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

    public ProcessOutputStream(String[] cmd) throws IOException {
      this(new ProcessBuilder(cmd), new PrintWriter(System.out), new PrintWriter(System.err));
    }

    public ProcessOutputStream(String[] cmd, Writer writer) throws IOException {
      this(new ProcessBuilder(cmd), writer, writer);
    }

    public ProcessOutputStream(String[] cmd, Writer output, Writer error) throws IOException {
      this(new ProcessBuilder(cmd), output, error);
    }

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
   */
  public static void runShellCommand(String cmd,
                                     StringBuilder outputLines)
          throws IOException {
    runShellCommand(new String[] {cmd}, outputLines, null);
  }


  /**
   * Runs the shell command which is specified, along with its arguments, in the
   * given {@code String} array.  If there is any output, it is swallowed (!).
   */
  public static void runShellCommand(String[] cmd)
          throws IOException {
    runShellCommand(cmd, null, null);
  }


  /**
   * Runs the shell command which is specified, along with its arguments, in the
   * given {@code String}.  If there is any output, it is swallowed (!).
   */
  public static void runShellCommand(String cmd)
          throws IOException {
    runShellCommand(new String[] {cmd}, null, null);
  }


  /**
   * Returns the process ID, via an awful hack.
   */
  public static int getPID() throws IOException {
    // note that we ask Perl for "ppid" -- process ID of parent -- that's us
    String[] cmd = 
      new String[] {"perl", "-e", "print getppid() . \"\\n\";"};
    StringBuilder out = new StringBuilder();
    runShellCommand(cmd, out);
    return Integer.parseInt(out.toString());
  }


  /**
   * Returns the process ID, via an awful hack, or else -1.
   */
  public static int getPIDNoExceptions() {
    try {
      return SystemUtils.getPID();
    } catch (IOException e) {
      return -1;
    }
  }


  /**
   * Returns the number of megabytes (MB) of memory in use.
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
   * @see <a href="http://java.sun.com/j2se/1.5.0/docs/api/java/text/SimpleDateFormat.html">SimpleDateFormat</a>
   */
  public static String getTimestampString(String fmt) {
    return (new SimpleDateFormat(fmt)).format(new Date());
  }

  /**
   * Returns a String representing the current date and time in the format
   * "20071022-140522".
   */
  public static String getTimestampString() {
    return getTimestampString("yyyyMMdd-HHmmss");
  }


  public static void main(String[] args) throws Exception {
    StringBuilder out = new StringBuilder();
    runShellCommand("date", out);
    System.out.println("The date is " + out);
    int pid = getPID();
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
