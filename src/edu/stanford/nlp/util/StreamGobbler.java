package edu.stanford.nlp.util;

import java.io.*;

/**
 * Reads the output of a process started by Process.exec()
 *
 * Adapted from:
 *
 * http://www.velocityreviews.com/forums/t130884-process-runtimeexec-causes-subprocess-hang.html
 *
 * @author pado
 *
 */

public class StreamGobbler extends Thread {

  InputStream is;
  Writer outputFileHandle;
  boolean shouldRun = true;


  /**
   * Creates a daemon thread which, when started, copies each line read from
   * {@code is} to {@code outputFileHandle}, followed by a newline.  After reaching
   * the end of the stream, it checks for more input once a second until {@link #kill()} is called.
   *
   * @param is The stream to read, such as a process's output
   * @param outputFileHandle Where to write the lines read
   */
  public StreamGobbler (InputStream is, Writer outputFileHandle) {
    this.is = is;
    this.outputFileHandle = outputFileHandle;
    this.setDaemon(true);
  }

  /**
   * Asks the thread to stop.  It stops the next time it finishes reading
   * everything currently available from the stream, then closes the stream and
   * flushes (but does not close) the writer.
   */
  public void kill() {
    this.shouldRun = false;
  }

  public void run() {

    try {
      InputStreamReader isr = new InputStreamReader (is);
      BufferedReader br = new BufferedReader (isr);

      String s = null;
      //noinspection ConstantConditions
      while (s == null && shouldRun) {
        while ( (s = br.readLine()) != null ) {
          outputFileHandle.write(s);
          outputFileHandle.write("\n");
        }
        Thread.sleep(1000);
      }

      isr.close();
      br.close();
      outputFileHandle.flush();
    } catch (Exception ex) {
      System.out.println ("Problem reading stream :"+is.getClass().getCanonicalName()+ " "+ ex);
      ex.printStackTrace ();
    }

  }

}
