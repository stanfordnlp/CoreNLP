package edu.stanford.nlp.dcoref;

import edu.stanford.nlp.pipeline.Annotation;

import java.util.List;

/**
 * Interface for finding coref mentions in a document.
 *
 * @author Angel Chang
 */
public interface CorefMentionFinder {

  /** Get all the predicted mentions for a document.
   *
   * @param doc The syntactically annotated document
   * @param maxGoldID The last mention ID assigned.  New ones are assigned starting one above this number.
   * @param dict Dictionaries for coref.
   * @return For each of the List of sentences in the document, a List of Mention objects
   */
  public List<List<Mention>> extractPredictedMentions(Annotation doc, int maxGoldID, Dictionaries dict);

}
