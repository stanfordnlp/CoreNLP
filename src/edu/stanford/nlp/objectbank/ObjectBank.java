package edu.stanford.nlp.objectbank;

import edu.stanford.nlp.util.AbstractIterator;
import edu.stanford.nlp.util.Function;

import java.util.List;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.io.Reader;
import java.io.File;
import java.io.IOException;
import java.io.Serializable;

/**
 * The ObjectBank class is designed to make it easy to change the format/source
 * of data read in by other classes and to standardize how data is read in
 * javaNLP classes.
 * This should make reuse of existing code (by non-authors of the code)
 * easier because one has to just create a new ObjectBank which knows where to
 * look for the data and how to turn it into Objects, and then use the new
 * ObjectBank in the class.  This will also make it easier to reuse code for
 * reading in the same data.
 * <p/>
 * An ObjectBank is a Collection of Objects.  These objects are taken
 * from input sources and then tokenized and parsed into the desired
 * kind of Object.  An ObjectBank requires a ReaderIteratorFactory and a
 * IteratorFromReaderFactory.  The ReaderIteratorFactory is used to get
 * an Iterator over java.util.Readers which contain representations of
 * the Objects.  A ReaderIteratorFactory resembles a collection that
 * takes input sources and dispenses Iterators over java.util.Readers
 * of those sources.  A IteratorFromReaderFactory is used to turn a single
 * java.io.Reader into an Iterator over Objects.  The
 * IteratorFromReaderFactory splits the contents of the java.util.Reader
 * into Strings and then parses them into appropriate Objects.
 * <p/>
 *
 * <h3>Example Usages:</h3>
 * The general case is covered below, but the most common thing people
 * <i>actually</i> want to do is read lines from a file.  There are special
 * methods to make this easy!  You use the <code>getLineIterator</code> method.
 * In its simplest use, it returns an ObjectBank&lt;String&gt;, which is a subclass of
 * Collection&lt;String&gt;.  So, statements like these work:
 * <blockquote>
 * <code>
 * for (String str : ObjectBank.getLineIterator(filename) { <br>
 *   System.out.println(str); <br>
 * } <br><br>
 * String[] strings = ObjectBank.getLineIterator(filename).toArray(new String[0]); <br><br>
 * String[] strings = ObjectBank.getLineIterator(filename, "GB18030").toArray(new String[0]);
 * </code>
 * </blockquote>
 * More complex uses of getLineIterator let you interpret each line of a file
 * as an object of arbitrary type via a transformer Function.
 *
 * <p/>
 * As an example of the general power of this class, suppose you have
 * a collection of files in the directory /u/nlp/data/gre/questions.  Each file
 * contains several Puzzle documents which look like:
 * <pre>
 * &lt;puzzle>
 *    &lt;preamble> some text &lt;/preamble>
 *    &lt;question> some intro text
 *      &lt;answer> answer1 &lt;/answer>
 *      &lt;answer> answer2 &lt;/answer>
 *      &lt;answer> answer3 &lt;/answer>
 *      &lt;answer> answer4 &lt;/answer>
 *    &lt;/question>
 *    &lt;question> another question
 *      &lt;answer> answer1 &lt;/answer>
 *      &lt;answer> answer2 &lt;/answer>
 *      &lt;answer> answer3 &lt;/answer>
 *      &lt;answer> answer4 &lt;/answer>
 *    &lt;/question>
 * &lt;/puzzle>
 * </pre>
 * <p/>
 * First you need to build a ReaderIteratorFactory which will provide java.io.Readers
 * over all the files in your directory:
 * <p/>
 * <pre>
 * Collection c = new FileSequentialCollection("/u/nlp/data/gre/questions/", "", false);
 * ReaderIteratorFactory rif = new ReaderIteratorFactory(c);
 * </pre>
 * <p/>
 * Next you need to make an IteratorFromReaderFactory which will take the
 * java.io.Readers vended by the ReaderIteratorFactory, split them up into
 * documents (Strings) and
 * then convert the Strings into Objects.  In this case we want to keep everything
 * between each set of <puzzle> </puzzle> tags so we would use a BeginEndTokenizerFactory.
 * You would also need to write a class which extends Function and whose apply method
 * converts the String between the <puzzle> </puzzle> tags into Puzzle objects.
 * <p/>
 * <pre>
 * public class PuzzleParser implements Function {
 *   public Object apply (Object o) {
 *     String s = (String)o;
 *     ...
 *     Puzzle p = new Puzzle(...);
 *     ...
 *     return p;
 *   }
 * }
 * </pre>
 * <p/>
 * Now to build the IteratorFromReaderFactory:
 * <p/>
 * <pre>
 * IteratorFromReaderFactory rtif = new BeginEndTokenizerFactory("<puzzle>", "</puzzle>", new PuzzleParser());
 * </pre>
 * <p/>
 * Now, to create your ObjectBank you just give it the ReaderIteratorFactory and
 * IteratorFromReaderFactory that you just created:
 * <p/>
 * <pre>
 * ObjectBank puzzles = new ObjectBank(rif, rtif);
 * </pre>
 * <p/>
 * Now, if you get a new set of puzzles that are located elsewhere and formatted differently
 * you create a new ObjectBank for reading them in and use that ObjectBank instead with only
 * trivial changes (or possible none at all if the ObjectBank is read in on a constructor)
 * to your code.  Or even better, if someone else wants to use your code to evaluate their puzzles,
 * which are  located elsewhere and formatted differently, they already know what they have to do
 * to make your code work for them.
 * <p/>
 *
 * @author Jenny Finkel <A HREF="mailto:jrfinkel@stanford.edu>jrfinkel@stanford.edu</A>
 * @author Sarah Spikes (sdspikes@cs.stanford.edu) - cleanup and filling in types
 */

public class ObjectBank<E> implements Collection<E>, Serializable {

  /**
   * This creates a new ObjectBank with the given ReaderIteratorFactory
   * and ObjectIteratorFactory.
   *
   * @param rif  The {@link ReaderIteratorFactory} from which to get Readers
   * @param ifrf The {@link IteratorFromReaderFactory} which turns java.io.Readers
   *             into Iterators of Objects
   */
  public ObjectBank(ReaderIteratorFactory rif, IteratorFromReaderFactory<E> ifrf) {
    this.rif = rif;
    this.ifrf = ifrf;
  }


  @SuppressWarnings({"NonSerializableFieldInSerializableClass"})
  protected ReaderIteratorFactory rif;

  @SuppressWarnings({"NonSerializableFieldInSerializableClass"})
  protected IteratorFromReaderFactory<E> ifrf;

  @SuppressWarnings({"NonSerializableFieldInSerializableClass"})
  private List<E> contents; // = null;


  public static ObjectBank<String> getLineIterator(String filename) {
    return getLineIterator(new File(filename));
  }

  public static <X> ObjectBank<X> getLineIterator(String filename, Function<String,X> op) {
    return getLineIterator(new File(filename), op);
  }

  public static ObjectBank<String> getLineIterator(String filename, String encoding) {
    return getLineIterator(new File(filename), encoding);
  }

  public static ObjectBank<String> getLineIterator(Reader reader) {
    return getLineIterator(reader, new IdentityFunction<String>());
  }

  public static <X> ObjectBank<X> getLineIterator(Reader reader, Function<String,X> op) {
    ReaderIteratorFactory rif = new ReaderIteratorFactory(reader);
    IteratorFromReaderFactory<X> ifrf = LineIterator.getFactory(op);
    return new ObjectBank<X>(rif, ifrf);
  }

  public static ObjectBank<String> getLineIterator(File file) {
    return getLineIterator(Collections.singleton(file), new IdentityFunction<String>());
  }

  public static <X> ObjectBank<X> getLineIterator(File file, Function<String,X> op) {
    return getLineIterator(Collections.singleton(file), op);
  }

  public static ObjectBank<String> getLineIterator(File file, String encoding) {
    return getLineIterator(file, new IdentityFunction<String>(), encoding);
  }

  public static <X> ObjectBank<X> getLineIterator(File file, Function<String,X> op, String encoding) {
    ReaderIteratorFactory rif = new ReaderIteratorFactory(file, encoding);
    IteratorFromReaderFactory<X> ifrf = LineIterator.getFactory(op);
    return new ObjectBank<X>(rif, ifrf);
  }

  public static <X> ObjectBank<X> getLineIterator(Collection<?> filesStringsAndReaders, Function<String,X> op) {
    ReaderIteratorFactory rif = new ReaderIteratorFactory(filesStringsAndReaders);
    IteratorFromReaderFactory<X> ifrf = LineIterator.getFactory(op);
    return new ObjectBank<X>(rif, ifrf);
  }

  public static ObjectBank<String> getLineIterator(Collection<?> filesStringsAndReaders, String encoding) {
    return getLineIterator(filesStringsAndReaders, new IdentityFunction<String>(), encoding);
  }

  public static <X> ObjectBank<X> getLineIterator(Collection<?> filesStringsAndReaders, Function<String,X> op, String encoding) {
    ReaderIteratorFactory rif = new ReaderIteratorFactory(filesStringsAndReaders, encoding);
    IteratorFromReaderFactory<X> ifrf = LineIterator.getFactory(op);
    return new ObjectBank<X>(rif, ifrf);
  }

  /** This is handy for having getLineIterator return a collection of files for feeding into another ObjectBank. */
  public static class PathToFileFunction implements Function<String, File> {

    @Override
    public File apply(String str) {
      return new File(str);
    }

  }


  @Override
  public Iterator<E> iterator() {

    // basically concatenates Iterator's made from
    // each java.io.Reader.
    if (keepInMemory) {
      if (contents == null) {
        contents = new ArrayList<E>();
        Iterator<E> iter = new OBIterator();
        while (iter.hasNext()) {
          contents.add(iter.next());
        }
      }
      return contents.iterator();
    }

    return new OBIterator();
  }

  private boolean keepInMemory; // = false;

  /**
   * Tells the ObjectBank to store all of
   * its contents in memory so that it doesn't
   * have to be recomputed each time you iterate
   * through it.  This is useful when the data
   * is small enough that it can be kept in
   * memory, but reading/processing it
   * is expensive/slow.  Defaults to false.
   *
   * @param keep Whether to keep contents in memory
   */
  public void keepInMemory(boolean keep) {
    keepInMemory = keep;
  }

  /**
   * If you are keeping the contents in memory,
   * this will clear hte memory, and they will be
   * recomputed the next time iterator() is
   * called.
   */
  public void clearMemory(){
    contents = null;
  }

  @Override
  public boolean isEmpty() {
    return !iterator().hasNext();
  }

  /**
   * Can be slow.  Usage not recommended.
   */
  @Override
  public boolean contains(Object o) {
    Iterator<E> iter = iterator();
    while (iter.hasNext()) {
      if (iter.next() == o) {
        return true;
      }
    }
    return false;
  }

  /**
   * Can be slow.  Usage not recommended.
   */
  @Override
  public boolean containsAll(Collection<?> c) {
    for (Object obj : c) {
      if ( ! contains(obj)) {
        return false;
      }
    }
    return true;
  }

  /**
   * Can be slow.  Usage not recommended.
   */
  @Override
  public int size() {
    Iterator<E> iter = iterator();
    int size = 0;
    while (iter.hasNext()) {
      size++;
      iter.next();
    }
    return size;
  }

  @Override
  public void clear() {
    rif = new ReaderIteratorFactory();
  }

  @Override
  public Object[] toArray() {
    Iterator<E> iter = iterator();
    ArrayList<Object> al = new ArrayList<Object>();
    while (iter.hasNext()) {
      al.add(iter.next());
    }
    return al.toArray();
  }

  /**
   * Can be slow.  Usage not recommended.
   */
  @Override
  @SuppressWarnings({"SuspiciousToArrayCall"})
  public <T> T[] toArray(T[] o) {
    Iterator<E> iter = iterator();
    ArrayList<E> al = new ArrayList<E>();
    while (iter.hasNext()) {
      al.add(iter.next());
    }
    return al.toArray(o);
  }


  /**
   * Unsupported Operation.  If you wish to add a new data source,
   * do so in the underlying ReaderIteratorFactory
   */
  @Override
  public boolean add(E o) {
    throw new UnsupportedOperationException();
  }

  /**
   * Unsupported Operation.  If you wish to remove a data source,
   * do so in the underlying ReaderIteratorFactory
   */
  @Override
  public boolean remove(Object o) {
    throw new UnsupportedOperationException();
  }

  /**
   * Unsupported Operation.  If you wish to add new data sources,
   * do so in the underlying ReaderIteratorFactory
   */
  @Override
  public boolean addAll(Collection<? extends E> c) {
    throw new UnsupportedOperationException();
  }

  /**
   * Unsupported Operation.  If you wish to remove data sources,
   * remove, do so in the underlying ReaderIteratorFactory.
   */
  @Override
  public boolean removeAll(Collection<?> c) {
    throw new UnsupportedOperationException();
  }

  /**
   * Unsupported Operation.  If you wish to retain only certain data
   * sources, do so in the underlying ReaderIteratorFactory.
   */
  @Override
  public boolean retainAll(Collection<?> c) {
    throw new UnsupportedOperationException();
  }

  /**
   * Iterator of Objects.
   */
  class OBIterator extends AbstractIterator<E> {

    private final Iterator<Reader> readerIterator;
    private Iterator<E> tok;
    private E nextObject;
    private Reader currReader; // = null;

    public OBIterator() {
      readerIterator = rif.iterator();
      setNextObject();
    }

    private void setNextObject() {

      if (tok != null && tok.hasNext()) {
        nextObject = tok.next();
        return;
      }

      while (true) {
        try {
          if (currReader != null) {
            currReader.close();
          }
        } catch (IOException e) {
          throw new RuntimeException(e);
        }

        if (readerIterator.hasNext()) {
          currReader = readerIterator.next();
          tok = ifrf.getIterator(currReader);
        } else {
          nextObject = null;
          return;
        }

        if (tok.hasNext()) {
          nextObject = tok.next();
          return;
        }
      }
    }

    @Override
    public boolean hasNext() {
      return nextObject != null;
    }

    @Override
    public E next() {
      if (nextObject == null) {
        throw new NoSuchElementException();
      }
      E tmp = nextObject;
      setNextObject();
      return tmp;
    }

  } // end class OBIterator

  private static final long serialVersionUID = -4030295596701541770L;

}
