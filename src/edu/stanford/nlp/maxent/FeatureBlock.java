package edu.stanford.nlp.maxent;

import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.util.Pair;
import java.util.Collection;


/**
 * An outside view of a feature block.
 * It consists of a Counter and a List of &lt;Label,Integer&gt; pairs.
 *
 * @author Kristina Toutanova
 * @version Nov 17, 2004
 * @param <L> Class label type
 * @param <F> Example feature type
 */
public class FeatureBlock<L,F> {
  Counter<F> features;
  Collection<Pair<L, Integer>> classAlternatives;

  /**
   * for a type1 classifier a feature block can be specified
   * by the Counter of features and the List of possible classes
   */
  public FeatureBlock(Counter<F> features, Collection<Pair<L, Integer>> classAlt) {
    this.features = features;
    classAlternatives = classAlt;
  }

  /**
   * if we have to set them
   *
   */
  public void setAlternatives(Collection<Pair<L, Integer>> classAlt) {
    classAlternatives = classAlt;
  }

}
