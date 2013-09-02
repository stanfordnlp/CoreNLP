package edu.stanford.nlp.ling;

import edu.stanford.nlp.io.ExtensionFileFilter;

import java.io.File;
import java.io.FileFilter;
import java.util.AbstractCollection;
import java.util.ArrayList;
import java.util.Iterator;


/**
 * A <code>Sentencebank</code> object provides access to a corpus of
 * sentences -- just plain sentences or tagged sentences, etc.
 * This class implements the Collection interface, but in a read-only way.
 * A typical way of creating and using a Sentencebank looks like:
 * <p><blockquote><pre>
 * SentenceReaderFactory srf = new AdwaitSentenceReaderFactory(true);
 * Sentencebank sb1 = new DiskSentencebank(srf);
 * sb1.loadPath(inputFile);
 * <p/>
 * for (Iterator i1 = sb1.iterator(); i1.hasNext(); ) {
 *     Sentence s1 = (Sentence) i1.next();
 *     System.out.println(s1);
 * }
 * </pre></blockquote></p>
 *
 * @author Christopher Manning
 */
public abstract class Sentencebank<T extends ArrayList<U>, U extends HasWord> extends AbstractCollection<T> {

  /**
   * Stores the <code>SentenceReaderFactory</code> that will be used to
   * create a <code>SentenceReader</code> to process a file of sentences.
   */
  private SentenceReaderFactory<U> srf;

  /**
   * Create a new Sentencebank.
   */
  public Sentencebank() {
    this(new SimpleSentenceReaderFactory<U>());
  }


  /**
   * Create a new Sentencebank.
   *
   * @param srf the factory class to be called to create a new
   *            <code>SentenceReader</code>
   */
  public Sentencebank(SentenceReaderFactory<U> srf) {
    this.srf = srf;
  }


  /**
   * Create a new Sentencebank.
   *
   * @param initialCapacity The initial size of the underlying Collection,
   *                        (if a Collection-based storage mechanism is being provided)
   */
  public Sentencebank(int initialCapacity) {
    this(initialCapacity, new SimpleSentenceReaderFactory<U>());
  }


  /**
   * Create a new Sentencebank.
   *
   * @param initialCapacity The initial size of the underlying Collection,
   *                        (if a Collection-based storage mechanism is being provided)
   * @param srf             the factory class to be called to create a new
   *                        <code>SentenceReader</code>
   */
  public Sentencebank(int initialCapacity, SentenceReaderFactory<U> srf) {
    this.srf = srf;
  }


  /**
   * Get the <code>SentenceReaderFactory</code> for a
   * <code>Sentencebank</code> --  this method is provided in order to
   * make the
   * <code>SentenceReaderFactory</code> available to subclasses.
   * @return The SetenceReaderFactory
   */
  protected SentenceReaderFactory<U> sentenceReaderFactory() {
    return srf;
  }


  /**
   * Empty a <code>Sentencebank</code>.
   */
  @Override
  public abstract void clear();


  /**
   * Load a sequence of sentences from the given directory and its
   * subdirectories.
   * Sentences should reside in files with the suffix ".pos".
   * Or: load a single file with the given pathName (including extension)
   *
   * @param pathName file or directory name
   */
  public void loadPath(String pathName) {
    loadPath(new File(pathName));
  }


  /**
   * Load a sequence of sentences from given directory and its
   * subdirectories.
   * Sentences should reside in files with the suffix ".pos".
   *
   * @param path File specification
   */
  public void loadPath(File path) {
    loadPath(path, "pos", true);
  }


  /**
   * Load sentences from given directory.
   *
   * @param path        file or directory to load from
   * @param suffix      suffix of files to load
   * @param recursively descend into subdirectories as well
   */
  public void loadPath(File path, String suffix, boolean recursively) {
    loadPath(path, new ExtensionFileFilter(suffix, recursively));
  }


  /**
   * Load sentences from given path specification.
   *
   * @param path file or directory to load from
   * @param filt a FilenameFilter of files to load
   */
  public abstract void loadPath(File path, FileFilter filt);


  /**
   * Apply a SentenceVisitor to each sentence in the Sentencebank.
   *
   * @param tp The SentenceVisitor to be applied
   */
  public abstract void apply(SentenceVisitor<U> tp);


  /**
   * Return an Iterator over Sentences in the Sentencebank.
   */
  @Override
  public abstract Iterator<T> iterator();


  /**
   * Return the whole Sentencebank as a series of big bracketed lists.
   * Calling this is a really bad idea if your Sentencebank is large.
   */
  @Override
  public String toString() {
    final StringBuffer sb = new StringBuffer();
    apply(new SentenceVisitor<U>() {
      public void visitSentence(ArrayList<U> t) {
        sb.append(Sentence.listToString(t));
        sb.append("\n");
      }
    });
    return sb.toString();
  }


  private final class CounterSentenceVisitor implements SentenceVisitor<U> {
    int i; // = 0;

    public void visitSentence(ArrayList<U> t) {
      i++;
    }

    public int total() {
      return i;
    }
  }

  /**
   * Returns the size of the Sentencebank.
   */
  @Override
  public int size() {
    CounterSentenceVisitor counter = new CounterSentenceVisitor();
    apply(counter);
    return counter.total();
  }


  /**
   * This operation isn't supported for a Sentencebank.
   * Tell them immediately.
   *
   * @param o The object that would be removed
   */
  @Override
  public boolean remove(Object o) {
    throw new UnsupportedOperationException("Sentencebank is read-only");
  }

}
