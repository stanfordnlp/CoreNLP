package edu.stanford.nlp.io;

import java.io.Closeable;
import java.io.Flushable;
import java.io.IOException;
import java.io.OutputStream;

/**
 * This class splits the calls to an OutputStream into two different
 * streams.
 *
 * @author John Bauer
 */
public class TeeStream extends OutputStream 
  implements Closeable, Flushable
{
  public TeeStream(OutputStream s1, OutputStream s2) {
    this.s1 = s1;
    this.s2 = s2;
  }
  
  OutputStream s1, s2;
  
  public void close() 
    throws IOException
  {
    s1.close();
    s2.close();
  }
  
  public void flush() 
    throws IOException
  {
    s1.flush();
    s2.flush();
  }
  
  public void write(byte[] b) 
    throws IOException
  {
    s1.write(b);
    s2.write(b);
  }
  
  public void write(byte[] b, int off, int len) 
    throws IOException
  {
    s1.write(b, off, len);
    s2.write(b, off, len);
  }
  
  public void write(int b) 
    throws IOException
  {
    s1.write(b);
    s2.write(b);
  }
}

