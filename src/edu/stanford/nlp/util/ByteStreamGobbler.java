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

  public ByteStreamGobbler(InputStream is, OutputStream out) {
    this.inStream = new BufferedInputStream(is);
    this.outStream = new BufferedOutputStream(out);
  }

  public ByteStreamGobbler(String name, InputStream is, OutputStream out) {
    super(name);
    this.inStream = new BufferedInputStream(is);
    this.outStream = new BufferedOutputStream(out);
  }

  public ByteStreamGobbler(String name, InputStream is, OutputStream out, int bufferSize) {
    super(name);
    this.inStream = new BufferedInputStream(is);
    this.outStream = new BufferedOutputStream(out);
    if (bufferSize <= 0) {
      throw new IllegalArgumentException("Invalid buffer size " + bufferSize + ": must be larger than 0");
    }
    this.bufferSize = bufferSize;
  }

  public InputStream getInputStream()
  {
    return inStream;
  }

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
