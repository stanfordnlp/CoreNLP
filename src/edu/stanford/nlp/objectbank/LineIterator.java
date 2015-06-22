package edu.stanford.nlp.objectbank;

import java.io.*;
import java.util.*;

import edu.stanford.nlp.util.Function;
import edu.stanford.nlp.util.AbstractIterator;


/**
 * An Iterator that returns a line of a file at a time.
 * Lines are broken as determined by Java's readLine() method.
 * The returned lines do not include the newline character.
 *
 * @author Christopher Manning
 */
public class LineIterator<X> extends AbstractIterator<X> {

  private final Function<String,X> op;
  private final BufferedReader in;
  private X nextToken; // = null;

  @SuppressWarnings({"unchecked"})
  public LineIterator(Reader r) {
    this(r, new IdentityFunction());  // it seems like this can't be generified: seems a weird brokenness of Java to me! [cdm]
  }

  public LineIterator(Reader r, Function<String,X> op) {
    this.op = op;
    in = new BufferedReader(r);
    setNext();
  }

  private void setNext() {
    String line = null;
    try {
      line = in.readLine();
    } catch (IOException ioe) {
      ioe.printStackTrace();
    }
    if (line != null) {
      nextToken = op.apply(line);
    } else {
      nextToken = null;
    }
  }

  @Override
  public boolean hasNext() {
    return nextToken != null;
  }

  @Override
  public X next() {
    if (nextToken == null) {
      throw new NoSuchElementException("LineIterator reader exhausted");
    }
    X token = nextToken;
    setNext();

    return token;
  }

  public Object peek() {
    return nextToken;
  }

  /**
   * Returns a factory that vends LineIterators that read the contents of the
   * given Reader, splitting on newlines.
   *
   * @return An iterator over the lines of a file
   */
  public static <X> IteratorFromReaderFactory<X> getFactory() {
    return new LineIteratorFactory<X>();
  }

  /**
   * Returns a factory that vends LineIterators that read the contents of the
   * given Reader, splitting on newlines.
   *
   * @param op A function to be applied to each line before it is returned
   * @return An iterator over the lines of a file
   */
  public static <X> IteratorFromReaderFactory<X> getFactory(Function<String,X> op) {
    return new LineIteratorFactory<X>(op);
  }


  public static class LineIteratorFactory<X> implements IteratorFromReaderFactory<X>, Serializable {

    private static final long serialVersionUID = 1L;

    @SuppressWarnings({"NonSerializableFieldInSerializableClass"})
    private final Function<String,X> oper;

    @SuppressWarnings({"unchecked"})
    public LineIteratorFactory() {
      this(new IdentityFunction());  // it seems like this can't be generified: seems a weird brokenness of Java to me! [cdm]
    }

    public LineIteratorFactory(Function<String,X> op) {
      this.oper = op;
    }

    public Iterator<X> getIterator(Reader r) {
      return new LineIterator<X>(r, oper);
    }

  }

  public static void main(String[] args) {
    String s = "\n\n@@123\nthis\nis\na\nsentence\n\n@@124\nThis\nis\nanother\n.\n\n@125\nThis\nis\nthe\nlast\n";
    Iterator<String> di = new LineIterator<String>(new StringReader(s), new IdentityFunction<String>());
    System.out.println("--- start ---");
    while (di.hasNext()) {
      System.out.println(di.next());
    }
    System.out.println("---- end ----");
  }

}
