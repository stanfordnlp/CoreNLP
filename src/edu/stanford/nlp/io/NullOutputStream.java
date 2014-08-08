package edu.stanford.nlp.io;

import java.io.OutputStream;
import java.io.IOException;

/**
 * An OutputStream which throws away all output instead of outputting anything
 *<br>
 * Taken from http://stackoverflow.com/questions/2127979
 *
 * @author John Bauer
 */
public class NullOutputStream extends OutputStream {
  @Override
  public void write(int i) throws IOException {
    // do nothing
  }

  @Override
  public void write(byte[] b, int off, int len) {
    // still do nothing
  }

  @Override
  public void write(byte[] b) {
    // this doesn't do anything either
  }

  @Override
  public void flush() {
    // write all buffered text.  
    // just kidding, it actually does nothing
  }
}
