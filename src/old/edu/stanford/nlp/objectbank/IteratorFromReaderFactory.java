package old.edu.stanford.nlp.objectbank;

import java.util.Iterator;

/**
 * A IteratorFromReaderFactory is used to convert a java.io.Reader
 * into an Iterator over the Objects represented by the text in the
 * java.io.Reader.
 *
 * @author Jenny Finkel
 */

public interface IteratorFromReaderFactory<T> {

  public Iterator<T> getIterator(java.io.Reader r);

}
