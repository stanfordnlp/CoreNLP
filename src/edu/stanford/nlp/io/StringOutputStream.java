package edu.stanford.nlp.io;

import java.io.*;

/**
 * An {@code OutputStream} that can be turned into a {@code String}.
 *
 * @author Bill MacCartney
 */
public class StringOutputStream extends OutputStream {

  private final StringBuilder sb = new StringBuilder();

  public StringOutputStream() {}

  public synchronized void clear() {
    sb.setLength(0);
  }

  @Override
  public synchronized void write(int i) {
    sb.append((char) i);
  }

  @Override
  public synchronized String toString()  {
    return sb.toString();
  }

}
