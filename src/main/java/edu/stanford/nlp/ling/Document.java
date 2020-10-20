package edu.stanford.nlp.ling;

import java.util.List;

/**
 * Represents a text document as a list of Words with a String title.
 *
 * @author Sepandar Kamvar (sdkamvar@stanford.edu)
 * @author Joseph Smarr (jsmarr@stanford.edu)
 * @author Sarah Spikes (sdspikes@cs.stanford.edu) (Templatization - added another parameter)
 *
 * @param <L> The type of the labels in the Datum
 * @param <F> The type of the features in the Datum,
 *	and the type stored in the List
 */
public interface Document<L, F, T> extends Datum<L, F>, List<T> {

  /**
   * Returns title of document, or "" if the document has no title.
   * Implementations should never return <tt>null</tt>.
   *
   * @return The document's title
   */
  public abstract String title();

  /**
   * Returns a new empty Document with the same meta-data (title, labels, etc)
   * as this Document. Subclasses that store extra state should provide custom
   * implementations of this method. This method is primarily used by the
   * processing API, so the input document can be preserved and the output
   * document can maintain the meta-data of the in document.
   *
   * @return An empty document of the right sort.
   */
  public <OUT> Document<L, F, OUT> blankDocument();

}

