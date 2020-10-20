
package edu.stanford.nlp.ie;

import java.util.Collection;

import edu.stanford.nlp.sequences.Clique;
import edu.stanford.nlp.sequences.FeatureFactory;
import edu.stanford.nlp.util.PaddedList;

/**
 * For features generated from word embeddings.
 *
 * @author Thang Luong, created on Sep 11, 2013: minor enhancements.
 * @author Mengqiu Wang: original developer.
 */
public class EmbeddingFeatureFactory extends FeatureFactory {

  /* (non-Javadoc)
   * @see edu.stanford.nlp.sequences.FeatureFactory#getCliqueFeatures(edu.stanford.nlp.util.PaddedList, int, edu.stanford.nlp.sequences.Clique)
   */
  @Override
  public Collection getCliqueFeatures(PaddedList info, int position,
      Clique clique) {
    // TODO Auto-generated method stub
    return null;
  }

}
