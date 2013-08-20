package edu.stanford.nlp.dcoref;

import edu.stanford.nlp.pipeline.Annotation;

import java.util.List;

/**
 * Interface for finding coref mentions in a document
 *
 * @author Angel Chang
 */
public interface CorefMentionFinder {
  public List<List<Mention>> extractPredictedMentions(Annotation doc, int maxGoldID, Dictionaries dict);
}
