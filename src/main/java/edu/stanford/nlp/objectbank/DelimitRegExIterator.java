package edu.stanford.nlp.objectbank;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import edu.stanford.nlp.io.RuntimeIOException;
import edu.stanford.nlp.util.AbstractIterator;


/**
 * An Iterator that reads the contents of a Reader, delimited by the specified
 * delimiter, and then subsequently processed by an Function to produce
 * Objects of type T.
 *
 * @author <a href="mailto:jrfinkel@cs.stanford.edu">Jenny Finkel</a>
 *
 * @param <T> The type of the objects returned
 */
public class DelimitRegExIterator<T> extends AbstractIterator<T> {

  private Iterator<String> tokens;
  private final Function<String,T> op;
  private T nextToken; // = null;

  //TODO: not sure if this is the best way to name things...
  public static DelimitRegExIterator<String> defaultDelimitRegExIterator(Reader in, String delimiter) {
    return new DelimitRegExIterator<>(in, delimiter, new IdentityFunction<>());
  }

  public DelimitRegExIterator(Reader r, String delimiter, Function<String,T> op) {
    this.op = op;
    BufferedReader in = new BufferedReader(r);
    try {
      String line;
      StringBuilder input = new StringBuilder(10000);
      while ((line = in.readLine()) != null) {
        input.append(line).append('\n');
      }
      line = input.toString();
      Matcher m = Pattern.compile(delimiter).matcher(line);
      ArrayList<String> toks = new ArrayList<>();
      int prev = 0;
      while (m.find()) {
        if (m.start() == 0) { // Skip empty first part
          continue;
        }
        toks.add(line.substring(prev, m.start()));
        prev = m.end();
      }
      if (prev < line.length()) { // Except empty last part
        toks.add(line.substring(prev, line.length()));
      }
      tokens = toks.iterator();
    } catch (IOException e) {
      throw new RuntimeIOException(e);
    }
    setNext();
  }

  private void setNext() {
    nextToken = tokens.hasNext() ? parseString(tokens.next()) : null;
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
      throw new NoSuchElementException("DelimitRegExIterator exhausted");
    }
    T token = nextToken;
    setNext();
    return token;
  }

  public Object peek() {
    return nextToken;
  }

  /**
   * Returns a factory that vends DelimitRegExIterators that read the contents of the
   * given Reader, splits on the specified delimiter, then returns the result.
   */
  public static IteratorFromReaderFactory<String> getFactory(String delim) {
    return DelimitRegExIteratorFactory.defaultDelimitRegExIteratorFactory(delim);
  }

  /**
   * Returns a factory that vends DelimitRegExIterators that reads the contents of the
   * given Reader, splits on the specified delimiter, applies op, then returns the result.
   */
  public static <T> IteratorFromReaderFactory<T> getFactory(String delim, Function<String,T> op) {
    return new DelimitRegExIteratorFactory<>(delim, op);
  }

  public static class DelimitRegExIteratorFactory<T> implements IteratorFromReaderFactory<T> /*, Serializable */ {

    private static final long serialVersionUID = 6846060575832573082L;

    private final String delim;
    @SuppressWarnings("serial")
    private final Function<String,T> op;

    public static DelimitRegExIteratorFactory<String> defaultDelimitRegExIteratorFactory(String delim) {
      return new DelimitRegExIteratorFactory<>(delim, new IdentityFunction<>());
    }

    public DelimitRegExIteratorFactory(String delim, Function<String,T> op) {
      this.delim = delim;
      this.op = op;
    }

    @Override
    public Iterator<T> getIterator(Reader r) {
      return new DelimitRegExIterator<>(r, delim, op);
    }

  }

}
