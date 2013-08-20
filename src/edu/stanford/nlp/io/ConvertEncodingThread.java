package edu.stanford.nlp.io;

import java.io.*;
import java.util.*;

import edu.stanford.nlp.util.StringUtils;


/**
 * Converts the encoding of an <code>InputStream</code> and writes the
 * converted encoding to an <code>OutputStream</code>.
 *
 * @author Roger Levy
 * @version January 2003
 */
public class ConvertEncodingThread extends Thread {

  private Reader r; // = null;
  private Writer w; // = null;


  /**
   * Run character encoding conversion between two streams.
   * Designed for running on a separate thread.
   * The Constructor creates the thread.
   *
   * @param in   The InputStream to be converted.
   * @param out  The OutputStream to write to.
   * @param from The encoding of the InputStream.
   * @param to   The desired encoding for the OutputStream.
   * @throws IOException If any file problem
   * @throws UnsupportedEncodingException If invalid charset
   */
  public ConvertEncodingThread(InputStream in, OutputStream out, String from, String to) throws IOException {
    r = new BufferedReader(new InputStreamReader(in, from));
    w = new BufferedWriter(new OutputStreamWriter(out, to));
  }

  @Override
  public void run() {
    if (r != null && w != null) {
      try {
        char[] buffer = new char[4096];
        int c;
        while ((c = r.read(buffer)) != -1) {
          w.write(buffer, 0, c);
        }
        r.close();
        w.flush();
        w.close();
      } catch (IOException e) {
        System.err.println("ConvertEncodingThread run: " + e);
      }
    }
  }

  /**
   * Converts the character encoding of a file.
   *
   * @param args [-f charset] [-t charset] [inFile] [outFile]
   */
  public static void main(String[] args) {
    try {
      Properties p = StringUtils.argsToProperties(args);
      String otherArgs = p.getProperty("");
      if (otherArgs != null) {
        args = otherArgs.split(" ");
      } else {
        args = StringUtils.EMPTY_STRING_ARRAY;
      }
      InputStream fis = System.in;
      if (args.length > 0) { fis = new FileInputStream(args[0]); }
      OutputStream fos = System.out;
      if (args.length > 1) { fos = new FileOutputStream(args[1]); }

      // Use default encoding if no encoding is specified.
      String from = p.getProperty("f", System.getProperty("file.encoding"));
      String to = p.getProperty("t", System.getProperty("file.encoding"));

      new ConvertEncodingThread(fis, fos, from, to).start();
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

}
