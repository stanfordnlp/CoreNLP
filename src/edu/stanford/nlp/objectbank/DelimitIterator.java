package edu.stanford.nlp.objectbank;

import edu.stanford.nlp.util.Function;
import edu.stanford.nlp.util.AbstractIterator;

import java.io.BufferedReader;
import java.io.Reader;
import java.io.StringReader;
import java.util.*;


/**
 * An Iterator that reads the contents of a buffer, delimited by the specified
 * delimiter, and then be subsequently processed by an Function to produce Objects.
 *
 * @author Jenny Finkel <A HREF="mailto:jrfinkel@stanford.edu>jrfinkel@stanford.edu</A>
 * @author Sarah Spikes (sdspikes@cs.stanford.edu) (Templatization)
 */
public class DelimitIterator<T> extends AbstractIterator<T> {

  private String delimiter;
  private T nextToken;
  private boolean eolIsSignificant;
  private Function<String, T> op;
  private String input;

  public DelimitIterator(Reader in, String delimiter, Function<String, T> op) {
    this(in, delimiter, true, op);
  }

  public static DelimitIterator<String> defaultDelimitIterator(Reader in, String delimiter, boolean eolIsSignificant) {
    return new DelimitIterator<String>(in, delimiter, eolIsSignificant, new IdentityFunction<String>());
  }

  public DelimitIterator(Reader r, String delimiter, boolean eolIsSignificant, Function<String, T> op) {
    this.op = op;
    this.delimiter = delimiter;
    BufferedReader in = new BufferedReader(r);
    try {
      StringBuilder inputSB = new StringBuilder();
      for (String line; (line = in.readLine()) != null; ) {
        inputSB.append(line).append('\n');
      }
      this.input = inputSB.toString();
    } catch (Exception e) {
      this.input = null;
    }
    this.eolIsSignificant = eolIsSignificant;
    setNext();
  }

  private void setNext() {
    String s = getNext();
    nextToken = parseString(s);
  }

  private String getNext() {
    if (input == null) {
      return null;
    }
    if (input.length() < 1) {
      input = null;
      return null;
    }
    int delimIndex = input.indexOf(delimiter);
    if (eolIsSignificant) {
      int eolIndex = input.indexOf('\n');
      if (eolIndex >= 0 && (eolIndex < delimIndex || delimIndex < 0)) {
        String token = input.substring(0, eolIndex);
        input = input.substring(eolIndex + 1);
        return token;
      }
    }
    if (delimIndex < 0) {
      String token = input;
      if (token.length() < 1) {
        token = null;
      }
      input = null;
      return token;
    }
    String token = input.substring(0, delimIndex);
    input = input.substring(delimIndex + delimiter.length());
    return token;
  }

  protected T parseString(String s) {
    return op.apply(s);
  }

  @Override
  public boolean hasNext() {
    return nextToken != null;
  }

  @Override
  public T next() {
    if (nextToken == null) {
      throw new NoSuchElementException("DelimitIterator exhausted.");
    }
    T token = nextToken;
    setNext();
    return token;
  }

  public T peek() {
    return nextToken;
  }

  /**
   * Returns a factory that vends DelimitIterators that reads the contents of the
   * given Reader, splits on the specified delimiter, then returns the result.
   */
  public static  IteratorFromReaderFactory<String> getFactory(String delim) {
    return DelimitIteratorFactory.defaultDelimitIteratorFactory(delim);
  }

  /**
   * Returns a factory that vends DelimitIterators that reads the contents of the
   * given Reader, splits on the specified delimiter, then returns the result.
   */
  public static IteratorFromReaderFactory<String> getFactory(String delim, boolean eolIsSignificant) {
    return DelimitIteratorFactory.defaultDelimitIteratorFactory(delim, eolIsSignificant);
  }

  /**
   * Returns a factory that vends DelimitIterators that reads the contents of the
   * given Reader, splits on the specified delimiter, applies op, then returns the result.
   */
  public static <T> IteratorFromReaderFactory<T> getFactory(String delim, Function<String, T> op) {
    return new DelimitIteratorFactory<T>(delim, op);
  }

  /**
   * Returns a factory that vends DelimitIterators that reads the contents of the
   * given Reader, splits on the specified delimiter, applies op, then returns the result.
   */
  public static <T> IteratorFromReaderFactory<T> getFactory(String delim, Function<String, T> op, boolean eolIsSignificant) {
    return new DelimitIteratorFactory<T>(delim, op, eolIsSignificant);
  }

  public static class DelimitIteratorFactory<T> implements IteratorFromReaderFactory<T> {

    String delim;
    Function<String, T> op;
    boolean eolIsSignificant;

    public static DelimitIteratorFactory<String> defaultDelimitIteratorFactory(String delim) {
      return new DelimitIteratorFactory<String>(delim, new IdentityFunction<String>());
    }

    public static DelimitIteratorFactory<String> defaultDelimitIteratorFactory(String delim, boolean eolIsSignificant) {
      return new DelimitIteratorFactory<String>(delim, new IdentityFunction<String>(), eolIsSignificant);
    }

    public DelimitIteratorFactory(String delim, Function<String, T> op) {
      this(delim, op, true);
    }

    public DelimitIteratorFactory(String delim, Function<String, T> op, boolean eolIsSignificant) {
      this.delim = delim;
      this.op = op;
      this.eolIsSignificant = eolIsSignificant;
    }

    public Iterator<T> getIterator(java.io.Reader r) {
      return new DelimitIterator<T>(r, delim, eolIsSignificant, op);
    }

  }

  public static void main(String[] args) {
    String s = "@@123\nthis\nis\na\nsentence\n\n@@124\nThis\nis\nanother\n.\n\n@125\nThis\nis\nthe\nlast\n";
    DelimitIterator<String> di = DelimitIterator.defaultDelimitIterator(new StringReader(s), "\n\n", false);
    while (di.hasNext()) {
      System.out.println("****");
      System.out.println(di.next());
      System.out.println("****");
    }
  }

}
