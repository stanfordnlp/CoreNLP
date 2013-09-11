package edu.stanford.nlp.io;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

/**
 * Serves a function similar to a <code>StringBuffer</code> or a
 * <code>StringBuilder</code>, but defines <code>print()</code> methods, so that
 * you can do e.g. formatted printing.
 *
 * @author Bill MacCartney <wcmac@cs.stanford.edu>
 */
public class StringPrinter {
  
  private ByteArrayOutputStream bs;
  private PrintStream ps;
  
  public StringPrinter() {
    bs = new ByteArrayOutputStream();
    ps = new PrintStream(bs);
  }

  public void print(String s) {
    ps.print(s);
  }

  public void print(Object o) {
    ps.print(o);
  }

  public void printf(String format, Object... args) {
    ps.printf(format, args);
  }

  public void println() {
    ps.println();
  }

  public void println(String s) {
    ps.println(s);
  }

  public void println(Object o) {
    ps.println(o.toString());
  }

  @Override
  public String toString() {
    return bs.toString();
  }
  
   public static void main(String[] args) {
     StringPrinter sp = new StringPrinter();
     sp.println("yeah baby");
     sp.printf("pi is %f%n", 3.14);
     sp.println();
     System.out.println(sp);
   } 

}
