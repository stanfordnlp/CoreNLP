package edu.stanford.nlp.ie.machinereading;

import edu.stanford.nlp.util.Execution.Option;

public class RelationExtractorProps {
  /**
   * 
   */
  @Option(name="featureCountThreshold", gloss="feature count threshold to apply to dataset")
  public static int featureCountThreshold = 2;

  @Option(name="featureFactory", gloss="Feature factory for the relation extractor")
  public static RelationFeatureFactory featureFactory;
  /**
   * strength of the prior on the linear classifier (passed to LinearClassifierFactory) or the C constant if relationExtractorClassifierType=svm
   */
  @Option(name="sigma", gloss="strength of the prior on the linear classifier (passed to LinearClassifierFactory) or the C constant if relationExtractorClassifierType=svm")
  public static double sigma = 1.0;

  /**
   * which classifier to use (can be 'linear' or 'svm')
   */
  public static String relationExtractorClassifierType = "linear";
}
