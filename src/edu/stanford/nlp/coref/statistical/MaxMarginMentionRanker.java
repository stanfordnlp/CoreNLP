package edu.stanford.nlp.coref.statistical;

import java.util.Map;

import edu.stanford.nlp.coref.statistical.SimpleLinearClassifier.Loss;

import edu.stanford.nlp.stats.Counter;

/**
 * A max-margin mention-ranking coreference model.
 * @author Kevin Clark
 */
public class MaxMarginMentionRanker extends PairwiseModel {
  public enum ErrorType {
    FN(0), FN_PRON(1), FL(2), WL(3);
    public final int id;
    private ErrorType(int id) {
      this.id = id;
    }
  };

  private final Loss[] losses = new Loss[ErrorType.values().length];
  private final Loss loss;

  public final double[] costs;
  public final boolean multiplicativeCost;

  public static class Builder extends PairwiseModel.Builder {
    private double[] costs = new double[] {1.2, 1.2, 0.5, 1.0};
    private boolean multiplicativeCost = true;

    public Builder(String name, MetaFeatureExtractor meta) {
      super(name, meta);
    }

    public Builder setCosts(double fnCost, double fnPronounCost, double faCost, double wlCost)
      { this.costs = new double[] {fnCost, fnPronounCost, faCost, wlCost}; return this; }
    public Builder multiplicativeCost(boolean multiplicativeCost)
      { this.multiplicativeCost = multiplicativeCost; return this; }

    @Override
    public MaxMarginMentionRanker build() {
      return new MaxMarginMentionRanker(this);
    }
  }

  public static Builder newBuilder(String name, MetaFeatureExtractor meta) {
    return new Builder(name, meta);
  }

  public MaxMarginMentionRanker(Builder builder) {
    super(builder);
    costs = builder.costs;
    multiplicativeCost = builder.multiplicativeCost;
    if (multiplicativeCost) {
      for (ErrorType et : ErrorType.values()) {
        losses[et.id] = SimpleLinearClassifier.maxMargin(builder.costs[et.id]);
      }
    }
    loss = SimpleLinearClassifier.maxMargin(1.0);
  }

  public void learn(Example correct, Example incorrect,
      Map<Integer, CompressedFeatureVector> mentionFeatures, Compressor<String> compressor,
      ErrorType errorType) {

    Counter<String> cFeatures = meta.getFeatures(correct, mentionFeatures, compressor);
    Counter<String> iFeatures = meta.getFeatures(incorrect, mentionFeatures, compressor);
    for (Map.Entry<String, Double> e : cFeatures.entrySet()) {
      iFeatures.decrementCount(e.getKey(), e.getValue());
    }
    if (multiplicativeCost) {
      classifier.learn(iFeatures, 1.0, costs[errorType.id], loss);
    } else {
      classifier.learn(iFeatures, 1.0, 1.0, losses[errorType.id]);
    }
  }
}
