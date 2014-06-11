package edu.stanford.nlp.objectbank;

import edu.stanford.nlp.util.AbstractIterator;
import edu.stanford.nlp.io.EncodingFileReader;

import java.io.*;
import java.net.URL;
import java.util.*;
import java.util.zip.*;

/**
 * A ReaderIteratorFactory provides a means of getting an Iterator
 * which returns java.util.Readers over a Collection of input
 * sources.  Currently supported input sources are: Files, Strings,
 * URLs and Readers.  A ReaderIteratorFactory may take a Collection
 * on construction and new sources may be added either individually
 * (via the add(Object) method) or as a Collection (via the
 * addAll(Collection method).  The implementation automatically
 * determines the type of input and produces a java.util.Reader
 * accordingly.  If you wish to add support for a new kind of input,
 * refer the the setNextObject() method of the nested class
 * ReaderIterator.
 * <p>
 * The Readers returned by this class are not closed by the class when you
 * move to the next element (nor at any other time). So, if you want the
 * files closed, then the caller needs to close them.  The caller can only
 * do this if they pass in Readers.  Otherwise, this class should probably
 * close them but currently doesn't.
 * <p>
 * TODO: Have this class close the files that it opens.
 *
 * @author <A HREF="mailto:jrfinkel@stanford.edu">Jenny Finkel</A>
 * @version 1.0
 */
//TODO: does this always store the same kind of thing in a given instance, 
//or do you want to allow having some Files, some Strings, etc.?
public class ReaderIteratorFactory implements Iterable<Reader> {

  /**
   * Constructs a ReaderIteratorFactory from the input sources
   * contained in the Collection.  The Collection should contain
   * Objects of type File, String, URL and Reader.  See class
   * description for details.
   *
   * @param c Collection of input sources.
   */
  public ReaderIteratorFactory(Collection<?> c) {
    this();
    this.c.addAll(c);
  }

  public ReaderIteratorFactory(Collection<?> c, String encoding){
    this();
    this.enc = encoding;
    this.c.addAll(c);
  }

  /**
   * Convenience constructor to construct a ReaderIteratorFactory from a single
   * input source. The Object should be of type File, String, URL and Reader.  See class
   * description for details.
   *
   * @param o an input source that can be converted into a Reader
   */
  public ReaderIteratorFactory(Object o) {
    this(Collections.singleton(o));
  }

  public ReaderIteratorFactory(Object o, String encoding) {
    this(Collections.singleton(o), encoding);
  }


  public ReaderIteratorFactory() {
    c = new ArrayList<Object>();
  }

  /**
   * The underlying Collection of input sources.  Currently supported
   * input sources are: Files, Strings, URLs and Readers.   The
   * implementation automatically  determines the type of input and
   * produces a java.util.Reader accordingly.
   */
  protected Collection<Object> c;

  /**
   * The encoding for file input.  This is defaulted to "utf-8"
   * only applies when c is of type <code> File </code>.
   */
  protected String enc = "UTF-8";

  /**
   * Returns an Iterator over the input sources in the underlying Collection.
   *
   * @return an Iterator over the input sources in the underlying Collection.
   */
  public Iterator<Reader> iterator() {
    return new ReaderIterator();
  }

  /**
   * Adds an Object to the underlying Collection of  input sources.
   *
   * @param o Input source to be added to the underlying Collection.
   */
  public boolean add(Object o) {
    return this.c.add(o);
  }

  /**
   * Removes an Object from the underlying Collection of  input sources.
   *
   * @param o Input source to be removed from the underlying Collection.
   */
  public boolean remove(Object o) {
    return this.c.remove(o);
  }

  /**
   * Adds all Objects in Collection c to the underlying Collection of
   * input sources.
   *
   * @param c Collection of input sources to be added to the underlying Collection.
   */
  public boolean addAll(Collection<?> c) {
    return this.c.addAll(c);
  }

  /**
   * Removes all Objects in Collection c from the underlying Collection of
   * input sources.
   *
   * @param c Collection of input sources to be removed from the underlying Collection.
   */
  public boolean removeAll(Collection<?> c) {
    return this.c.removeAll(c);
  }

  /**
   * Removes all Objects from the underlying Collection of input sources
   * except those in Collection c
   *
   * @param c Collection of input sources to be retained in the underlying Collection.
   */
  public boolean retainAll(Collection<?> c) {
    return this.c.retainAll(c);
  }

  /**
   * Iterator which contains BufferedReaders.
   */
  class ReaderIterator extends AbstractIterator<Reader> {
    private Iterator<?> iter;
    private Reader nextObject;

    /**
     * Sole constructor.
     */
    public ReaderIterator() {
      iter = c.iterator();
      setNextObject();
    }

    /**
     * sets nextObject to a BufferedReader for the next input source,
     * or null of there is no next input source.
     */
    private void setNextObject() {
      if (!iter.hasNext()) {
        nextObject = null;
        iter = null;
        return;
      }

      Object o = iter.next();
      
      try {
        if (o instanceof File) {
          File file = (File) o;
          if (file.isDirectory()) {
            ArrayList<Object> l = new ArrayList<Object>();
            l.addAll(Arrays.asList(file.listFiles()));
            while (iter.hasNext()) {
              l.add(iter.next());
            }
            iter = l.iterator();
            file = (File) iter.next();
          }
          if (file.getName().endsWith(".gz")) {
            nextObject = new BufferedReader(new InputStreamReader(new GZIPInputStream(new FileInputStream(file)), enc));
          } else {
            nextObject = new BufferedReader(new EncodingFileReader(file, enc));
          }
          //nextObject = new BufferedReader(new FileReader(file));
        } else if (o instanceof String) {
//           File file = new File((String)o);
//           if (file.exists()) {
//             if (file.isDirectory()) {
//               ArrayList l = new ArrayList();
//               l.addAll(Arrays.asList(file.listFiles()));
//               while (iter.hasNext()) {
//                 l.add(iter.next());
//               }
//               iter = l.iterator();
//               file = (File) iter.next();
//             }
//             if (((String)o).endsWith(".gz")) {
//               BufferedReader tmp = new BufferedReader(new InputStreamReader(new GZIPInputStream(new FileInputStream(file)), enc));
//               nextObject = tmp;
//             } else {
//               nextObject = new BufferedReader(new EncodingFileReader(file, enc));
//             }
//           } else {
            nextObject = new BufferedReader(new StringReader((String) o));
//          }
        } else if (o instanceof URL) {
          // todo: add encoding specification to this as well? -akleeman
          nextObject = new BufferedReader(new InputStreamReader(((URL) o).openStream()));
        } else if (o instanceof Reader) {
          nextObject = new BufferedReader((Reader) o);
        } else {
          throw new RuntimeException("don't know how to get Reader from " + o);
        }
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }

    /**
     * @return true if there is another (valid) input source to read from
     */
    @Override
    public boolean hasNext() {
      return nextObject != null;
    }

    /**
     * Returns nextObject and then sets nextObject to the next input source.
     *
     * @return BufferedReader for next input source.
     */
    @Override
    public Reader next() {
      if (nextObject == null) {
        throw new NoSuchElementException();
      }
      Reader tmp = nextObject;
      setNextObject();
      return tmp;
    }
  }

}
