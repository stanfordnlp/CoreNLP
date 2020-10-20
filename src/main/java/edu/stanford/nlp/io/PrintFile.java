/**
 * Title:        StanfordMaxEnt<p>
 * Description:  A Maximum Entropy Toolkit<p>
 * Copyright:    Copyright (c) Bruce Eckel<p>
 * Company:      Stanford University<p>
 * @author Bruce Eckel
 * @version 1.0
 */
package edu.stanford.nlp.io;

import java.io.*;

/**
 * Shorthand class for opening an output file for human-readable output.
 * com:bruceeckel:tools:PrintFile.java
 */
public class PrintFile extends PrintStream {

  public PrintFile(String filename) throws IOException {
    super(new BufferedOutputStream(new FileOutputStream(filename)));
  }

  public PrintFile(File file) throws IOException {
    this(file.getPath());
  }

}
