package edu.stanford.nlp.optimization;

/**
 * Indicates that an minimizer supports grouping features for g-lasso or ae-lasso
 *
 * @author Mengqiu Wang
 */
public interface HasFeatureGrouping {
  public int[][] getFeatureGrouping();
}
