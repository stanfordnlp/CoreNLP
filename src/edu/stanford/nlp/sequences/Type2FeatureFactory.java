package edu.stanford.nlp.sequences;

import edu.stanford.nlp.util.PaddedList;
import edu.stanford.nlp.stats.ClassicCounter;

import java.io.Serializable;

/**
 * This is the abstract class that all feature factories must
 * subclass.  It also defines most of the basic {@link Clique}s
 * that you would want to make features over.  It contains a
 * convenient method, getCliques(maxLeft, maxRight) which will give
 * you all the cliques within the specified limits.
 *
 * @author Jenny Finkel
 */
public abstract class Type2FeatureFactory<IN> implements Serializable {

  private static final long serialVersionUID = -1659118594329526572L;

  protected SeqClassifierFlags flags;
  protected DatasetMetaInfo metaInfo;

  public Type2FeatureFactory() {}

  public void init (SeqClassifierFlags flags) {
    this.flags = flags;
    maxClique = Clique.valueOf(-flags.maxLeft, flags.maxRight);
  }

  public void setDatasetMetaInfo(DatasetMetaInfo metaInfo) {
    this.metaInfo = metaInfo;
  }

  private Clique maxClique; // = null;

  public Clique getMaxClique() { return maxClique; }

  /**
   * This method returns a {@link edu.stanford.nlp.stats.ClassicCounter} of the features
   * calculated for the word at the specified position in info (the list of
   * words) for the specified {@link edu.stanford.nlp.sequences.LabeledClique}.
   * Features may include arbitrary functions of the label, and <i>should</i>
   * contains some information from the label, unless it is the "fake"
   * feature for the intercept (i.e. a weight will be learned for each
   * returned feature).
   * You should
   * use the label from the LabeledClique, <b>NOT</b> from the info list.
   * It should return the actual feature, <b>NOT</b> wrapped in a
   * {@link edu.stanford.nlp.sequences.Features} object, as the wrapping
   * will be done automatically.
   * Because it takes a {@link edu.stanford.nlp.util.PaddedList} you don't
   * need to worry about indices which are outside of the list.
   */
  public abstract ClassicCounter getFeatures(PaddedList<IN> info, int position, LabeledClique lc) ;

}
