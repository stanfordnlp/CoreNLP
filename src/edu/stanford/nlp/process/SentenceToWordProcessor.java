package edu.stanford.nlp.process;


import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Transforms a Document of Sentences to a Document of Words
 * by flattening out the Sentences. All the words in each
 * Sentence are dumped out into a new Document with the same
 * meta-data as the original Document. Opposite of
 * {@link WordToSentenceProcessor}.
 *
 * @author Joseph Smarr (jsmarr@stanford.edu)
 * @author Sarah Spikes (sdspikes@cs.stanford.edu) (Templatization)
 *
 * @param <L> The type of the labels
 * @param <F> The type of the features
 */
public class SentenceToWordProcessor<Inner, L, F> extends AbstractListProcessor<Collection<Inner>, Inner, L, F> {
  /**
   * Transforms the given Document of Sentences into a
   * Document of Words by flattening out the Sentences.
   */
  public List<Inner> process(List<? extends Collection<Inner>> sentences) {
    List<Inner> words = new ArrayList<Inner>();
    for (Collection<Inner> sent :  sentences) {
      words.addAll(sent); // sentence is just a list
    }
    return (words);
  }
}

