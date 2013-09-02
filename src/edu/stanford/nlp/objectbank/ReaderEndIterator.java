package edu.stanford.nlp.objectbank;

import edu.stanford.nlp.util.Function;
import edu.stanford.nlp.util.AbstractIterator;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * An Iterator that reads the entire contents of a Reader into a buffer, which can
 * then be subsequently processed by a Function to produce T things.
 *
 * @author Huy Nguyen (htnguyen@cs.stanford.edu)
 * @author Sarah Spikes (sdspikes@cs.stanford.edu) (Templatization)
 */
public class ReaderEndIterator<T> extends AbstractIterator<T> {

  private Function<String, T> op;
  private Reader r;
  private boolean hasNext;

  /**
   * Returns a factory that vends ReaderEndIterators that return the entire contents
   * of the given Reader as a String.
   * Calls {@link #factory(Function) factory(new IdentityFunction())}.
   */
  public static IteratorFromReaderFactory<String> factory() {
    return factory(new IdentityFunction<String>());
  }

  /**
   * Returns a factory that vends ReaderEndIterators that reads the entire contents of the
   * given Reader into a StringBuffer, applies op to the String, then returns the result.
   */
  public static <T> IteratorFromReaderFactory<T> factory(Function<String, T> op) {
    return new ReaderEndIteratorFactory<T>(op);
  }

  protected ReaderEndIterator(Reader r, Function<String, T> op) {
    this.r = r;
    this.op = op;
    hasNext = false;
  }

  @Override
  public T next() {
    // returns null if the reader is null
    if (!hasNext) {
      throw new NoSuchElementException();
    }

    // reads all the chars into a buffer
    StringBuilder sb = new StringBuilder(16000);  // make biggish
    try {
      for (int c; (c = r.read()) >= 0; ) {
        sb.append((char) c);
      }
    } catch (IOException e) {
      e.printStackTrace();
    }

    hasNext = false;
    return op.apply(sb.toString());
  }

  @Override
  public boolean hasNext() {
    return hasNext;
  }

  protected static class ReaderEndIteratorFactory<T> implements IteratorFromReaderFactory<T> {
    private Function<String, T> op;

    public ReaderEndIteratorFactory(Function<String, T> op) {
      this.op = op;
    }

    public Iterator<T> getIterator(Reader r) {
      return new ReaderEndIterator<T>(r, op);
    }
  }

  /**
   * For debugging purposes only.
   */
  public static void main(String[] args) {
    if (args.length != 1) {
      System.err.println("Usage: ReaderIteratorFactory <file>");
      System.exit(1);
    }
    IteratorFromReaderFactory<String> oif = ReaderEndIterator.factory();
    ReaderIteratorFactory rif = new ReaderIteratorFactory();
    rif.add(new File(args[0]));
    ObjectBank<String> ob = new ObjectBank<String>(rif, oif);
    for (Object obj : ob) {
      System.out.println(obj);
    }

    oif = ReaderEndIterator.factory(// a silly Function that reverses the input String
            new Function<String,String>() {
              public String apply(String in) {
                return new StringBuffer(in).reverse().toString();
              }
            });
    ob = new ObjectBank<String>(rif, oif);
    for (String str : ob) {
      System.out.println(str);
    }
  }
}
