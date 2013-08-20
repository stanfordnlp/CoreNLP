package edu.stanford.nlp.objectbank;

import edu.stanford.nlp.util.Function;
import edu.stanford.nlp.util.AbstractIterator;

import java.io.BufferedReader;
import java.io.Reader;
import java.util.Iterator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Tokenizer which takes a regular expression and tokenizes a {@link java.io.Reader}
 * based on it.  If the regular expression has a pair of ()'s then that group is
 * used as the token, otherwise the entire string matching the expression is
 * used.  The token is then processed by an {@link Function} and returned.
 *
 * @author Jenny Finkel
 */
public class RegExIterator<E> extends AbstractIterator<E> {

  private E nextToken;
  private Function<String, E> op;
  private Pattern p;
  private Matcher m;

  public static RegExIterator<String> simple(Reader in, Pattern pattern) {
    return new RegExIterator<String>(new BufferedReader(in), pattern, new IdentityFunction<String>());
  }

  public RegExIterator(Reader in, Pattern pattern, Function<String, E> op) {
    p = pattern;
    this.op = op;
    BufferedReader br = new BufferedReader(in);
    StringBuilder b = new StringBuilder();
    try {
      for (String line; (line = br.readLine()) != null; ) {
        b.append(line).append('\n');
      }
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
    m = p.matcher(b.toString());
    setNext();
  }

  private void setNext() {
    String s = getNext();
    nextToken = parseString(s);
  }

  private String getNext() {
    try {
      boolean b = m.find();
      if (b) {
        if (m.groupCount() > 0) {
          return m.group(1);
        } else {
          return m.group();
        }
      } else {
        return null;
      }
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  protected E parseString(String s) {
    return op.apply(s);
  }

  @Override
  public boolean hasNext() {
    return nextToken != null;
  }

  @Override
  public E next() {
    E token = nextToken;
    setNext();
    return token;
  }

  public Object peek() {
    return nextToken;
  }

  /**
   * Returns a factory that vends RegExIterators that reads the contents of the
   * given Reader, extracts text between the specified Strings, then returns the result.
   */
  public static IteratorFromReaderFactory<String> getFactory(Pattern pattern) {
    return RegExIteratorFactory.simple(pattern);
  }

  /**
   * Returns a factory that vends RegExIterators that reads the contents of the
   * given Reader, extracts text between the specified Strings, applies op, then returns the result.
   */
  public static <RNG> IteratorFromReaderFactory<RNG> getFactory(Pattern pattern, Function<String,RNG> op) {
    return new RegExIteratorFactory<RNG>(pattern, op);
  }

  static class RegExIteratorFactory<E> implements IteratorFromReaderFactory<E> {

    private Pattern pattern;
    private Function<String,E> op;

    public static RegExIteratorFactory<String> simple(Pattern pattern) {
      return new RegExIteratorFactory<String>(pattern, new IdentityFunction<String>());
    }

    public RegExIteratorFactory(Pattern pattern, Function<String,E> op) {
      this.pattern = pattern;
      this.op = op;
    }

    public Iterator<E> getIterator(java.io.Reader r) {
      return new RegExIterator<E>(r, pattern, op);
    }
  }
}
