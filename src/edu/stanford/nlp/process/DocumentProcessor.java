package edu.stanford.nlp.process;
import edu.stanford.nlp.ling.Document;

/**
 * Top-level interface for transforming Documents.
 *
 * @author Sepandar Kamvar (sdkamvar@stanford.edu)
 * @see #processDocument
 * @author Sarah Spikes (sdspikes@cs.stanford.edu) (Templatization)
 *
 * @param <L> The type of the labels 
 */
public interface DocumentProcessor<IN,OUT, L, F> {

  /**
   * Converts a Document to a different Document, by transforming
   * or filtering the original Document. The general contract of this method
   * is to not modify the <code>in</code> Document in any way, and to
   * preserve the metadata of the <code>in</code> Document in the
   * returned Document.
   *
   * @see FunctionProcessor
   */
  public Document<L, F, OUT> processDocument(Document<L, F, IN> in);

}
