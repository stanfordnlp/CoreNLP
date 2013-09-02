package edu.stanford.nlp.objectbank;

import edu.stanford.nlp.util.Function;
import edu.stanford.nlp.util.AbstractIterator;

import java.io.BufferedReader;
import java.io.Reader;
import java.util.Iterator;

/**
 * Tokenizer which returns tokens between specified begin and end
 * Strings.  For instance, if your begin and end markers are <B>&lt;a&gt;</B>
 * and  <B>&lt;/a&gt;</B> then
 * <BLOCKQUOTE>
 * This &lt;a&gt; is &lt;/a&gt; just &lt;a&gt; a &lt;/a&gt; test.
 * </BLOCKQUOTE>
 * would return <B>is</B> and <B>a</B> as tokens.
 *
 * @author <A HREF="mailto:jrfinkel@stanford.edu>Jenny Finkel</A>
 */
public class BeginEndIterator extends AbstractIterator<String> {

  private String begin, end;
  private BufferedReader in;
  private String currentLine;
  private String nextToken;
  private Function<String, String> op;

  public BeginEndIterator(Reader in, String begin, String end) {
    this(new BufferedReader(in), begin, end, new IdentityFunction<String>());
  }

  public BeginEndIterator(Reader in, String begin, String end, Function<String, String> op) {
    this.begin = begin;
    this.end = end;
    this.op = op;
    this.in = new BufferedReader(in);
    try {
      currentLine = this.in.readLine();
    } catch (Exception e) {
      currentLine = null;
    }
    setNext();
  }

  private void setNext() {
    String s = getNext();
    nextToken = parseString(s);
  }

  private String getNext() {
    try {
      int index;
      while ((index = currentLine.indexOf(begin)) < 0) {
        currentLine = in.readLine();
        if (currentLine == null) {
          return null;
        }
      }
      StringBuilder token = new StringBuilder(currentLine.substring(index + begin.length()));
      while ((index = token.indexOf(end)) < 0) {
        currentLine = in.readLine();
        if (currentLine == null) {
          return null;
        }
        token.append("\n").append(currentLine);
      }
      currentLine = token.substring(index + end.length());
      return token.substring(0, index);
    } catch (Exception e) {
      // throw exception?
      return null;
    }
  }

  protected String parseString(String s) {
    return op.apply(s);
  }

  @Override
  public boolean hasNext() {
    return nextToken != null;
  }

  @Override
  public String next() {
    String token = nextToken;
    setNext();
    return token;
  }

  public Object peek() {
    return nextToken;
  }

  /**
   * Returns a factory that vends BeginEndIterators that reads the contents of the
   * given Reader, extracts text between the specified Strings, then returns the result.
   */
  public static IteratorFromReaderFactory<String> getFactory(String begin, String end) {
    return new BeginEndIteratorFactory(begin, end);
  }

  /**
   * Returns a factory that vends BeginEndIterators that reads the contents of the
   * given Reader, extracts text between the specified Strings, applies op, then returns the result.
   */
  public static IteratorFromReaderFactory<String> getFactory(String begin, String end, Function<String, String> op) {
    return new BeginEndIteratorFactory(begin, end, op);
  }

  static class BeginEndIteratorFactory implements IteratorFromReaderFactory<String> {

    String begin, end;
    Function<String, String> op;

    public BeginEndIteratorFactory(String begin, String end) {
      this(begin, end, new IdentityFunction<String>());
    }

    public BeginEndIteratorFactory(String begin, String end, Function<String, String> op) {
      this.begin = begin;
      this.end = end;
      this.op = op;
    }

    public Iterator<String> getIterator(java.io.Reader r) {
      return new BeginEndIterator(r, begin, end, op);
    }
  }
}
