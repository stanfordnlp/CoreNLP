package edu.stanford.nlp.util;

import java.io.*;

/**
 * Stream Gobbler that read and write bytes
 * (can be used to gobble byte based stdout from a process.exec into a file)
 *
 * @author Angel Chang
 */
public class ByteStreamGobbler extends Thread {
  InputStream inStream;
  OutputStream outStream;
  int bufferSize = 4096;

  /**
   * Creates a gobbler which copies {@code is} to {@code out}, both wrapped in buffered streams.
   *
   * @param is The stream to read from
   * @param out The stream to write to
   */
  public ByteStreamGobbler(InputStream is, OutputStream out) {
    this.inStream = new BufferedInputStream(is);
    this.outStream = new BufferedOutputStream(out);
  }

  /**
   * Creates a named gobbler which copies {@code is} to {@code out}, both wrapped in buffered streams.
   *
   * @param name The name of this thread
   * @param is The stream to read from
   * @param out The stream to write to
   */
  public ByteStreamGobbler(String name, InputStream is, OutputStream out) {
    super(name);
    this.inStream = new BufferedInputStream(is);
    this.outStream = new BufferedOutputStream(out);
  }

  /**
   * Creates a named gobbler which copies {@code is} to {@code out} using a read buffer of the given size.
   *
   * @param name The name of this thread
   * @param is The stream to read from
   * @param out The stream to write to
   * @param bufferSize The number of bytes to read at a time
   * @throws IllegalArgumentException If {@code bufferSize} is not positive
   */
  public ByteStreamGobbler(String name, InputStream is, OutputStream out, int bufferSize) {
    super(name);
    this.inStream = new BufferedInputStream(is);
    this.outStream = new BufferedOutputStream(out);
    if (bufferSize <= 0) {
      throw new IllegalArgumentException("Invalid buffer size " + bufferSize + ": must be larger than 0");
    }
    this.bufferSize = bufferSize;
  }

  /**
   * Returns the buffered stream wrapping the input stream given to the constructor.
   *
   * @return The stream being read from
   */
  public InputStream getInputStream()
  {
    return inStream;
  }

  /**
   * Returns the buffered stream wrapping the output stream given to the constructor.
   * {@link #run()} does not flush it, so callers may need to flush it themselves.
   *
   * @return The stream being written to
   */
  public OutputStream getOutputStream()
  {
    return outStream;
  }

  public void run() {
    try {
      byte[] b = new byte[bufferSize];
      int bytesRead;
      while ((bytesRead = inStream.read(b)) >= 0) {
        if (bytesRead > 0) {
          outStream.write(b, 0, bytesRead);
        }
      }
      inStream.close();
    } catch (Exception ex) {
      System.out.println("Problem reading stream :"+inStream.getClass().getCanonicalName()+ " "+ ex);
      ex.printStackTrace();
    }
  }
}
