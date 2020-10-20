package edu.stanford.nlp.coref.statistical;

import java.io.File;
import java.io.PrintWriter;
import java.util.Map;

import edu.stanford.nlp.coref.statistical.SimpleLinearClassifier.LearningRateSchedule;
import edu.stanford.nlp.coref.statistical.SimpleLinearClassifier.Loss;

import edu.stanford.nlp.stats.Counter;

/**
 * Pairwise mention-classification model.
 * @author Kevin Clark
 */
public class PairwiseModel {
  public final String name;
  private final int trainingExamples;
  private final int epochs;
  protected final SimpleLinearClassifier classifier;
  private final double singletonRatio;
  private final String str;
  protected final MetaFeatureExtractor meta;

  public static class Builder {
    private final String name;
    private final MetaFeatureExtractor meta;
    @SuppressWarnings("unused") // output in config file with reflection
    private final String source = StatisticalCorefTrainer.extractedFeaturesFile;

    private int trainingExamples = 100000000;
    private int epochs = 8;
    private Loss loss = SimpleLinearClassifier.log();
    private LearningRateSchedule learningRateSchedule =
        SimpleLinearClassifier.adaGrad(0.05, 30.0);
    private double regularizationStrength = 1e-7;
    private double singletonRatio = 0.3;
    private String modelFile = null;

    public Builder(String name, MetaFeatureExtractor meta) {
      this.name = name;
      this.meta = meta;
    }

    public Builder trainingExamples(int trainingExamples)
      { this.trainingExamples = trainingExamples; return this; }
    public Builder epochs(int epochs)
      { this.epochs = epochs; return this; }
    public Builder singletonRatio(double singletonRatio)
      { this.singletonRatio = singletonRatio; return this; }
    public Builder loss(Loss loss)
      { this.loss = loss; return this; }
    public Builder regularizationStrength(double regularizationStrength)
      { this.regularizationStrength = regularizationStrength; return this; }
    public Builder learningRateSchedule(LearningRateSchedule learningRateSchedule)
      { this.learningRateSchedule = learningRateSchedule; return this; }
    public Builder modelPath(String modelFile)
      { this.modelFile = modelFile; return this; }

    public PairwiseModel build() {
      return new PairwiseModel(this);
    }
  }

  public static Builder newBuilder(String name, MetaFeatureExtractor meta) {
    return new Builder(name, meta);
  }

  public PairwiseModel(Builder builder) {
    name = builder.name;
    meta = builder.meta;
    trainingExamples = builder.trainingExamples;
    epochs = builder.epochs;
    singletonRatio = builder.singletonRatio;
    classifier = new SimpleLinearClassifier(builder.loss, builder.learningRateSchedule,
        builder.regularizationStrength, builder.modelFile == null ? null :
          ((builder.modelFile.endsWith(".ser") || builder.modelFile.endsWith(".gz"))  ? builder.modelFile :
          StatisticalCorefTrainer.pairwiseModelsPath + builder.modelFile + "/model.ser"));
    str = StatisticalCorefTrainer.fieldValues(builder);
  }

  public String getDefaultOutputPath() {
    return StatisticalCorefTrainer.pairwiseModelsPath + name +"/";
  }

  public SimpleLinearClassifier getClassifier() {
    return classifier;
  }

  public void writeModel() throws Exception {
    writeModel(getDefaultOutputPath());
  }

  public void writeModel(String outputPath) throws Exception {
    File outDir = new File(outputPath);
    if (!outDir.exists()) {
      outDir.mkdir();
    }

    try (PrintWriter writer = new PrintWriter(outputPath + "config", "UTF-8")) {
      writer.print(str);
    }

    try (PrintWriter writer = new PrintWriter(outputPath + "/weights", "UTF-8")) {
      classifier.printWeightVector(writer);
    }

    classifier.writeWeights(outputPath + "/model.ser");
  }

  public void learn(Example example,
      Map<Integer, CompressedFeatureVector> mentionFeatures, Compressor<String> compressor) {
    Counter<String> features = meta.getFeatures(example, mentionFeatures, compressor);
    classifier.learn(features, example.label == 1.0 ? 1.0 : -1.0, 1.0);
  }

  public void learn(Example example,
      Map<Integer, CompressedFeatureVector> mentionFeatures, Compressor<String> compressor,
      double weight) {
    Counter<String> features = meta.getFeatures(example, mentionFeatures, compressor);
    classifier.learn(features, example.label == 1.0 ? 1.0 : -1.0, weight);
  }

  public void learn(Example correct, Example incorrect,
      Map<Integer, CompressedFeatureVector> mentionFeatures, Compressor<String> compressor,
      double weight) {
    Counter<String> cFeatures = null;
    Counter<String> iFeatures = null;
    if (correct != null) {
      cFeatures = meta.getFeatures(correct, mentionFeatures, compressor);
    }
    if (incorrect != null) {
      iFeatures = meta.getFeatures(incorrect, mentionFeatures, compressor);
    }

    if (correct == null || incorrect == null) {
      if (singletonRatio != 0) {
        if (correct != null) {
          classifier.learn(cFeatures, 1.0, weight * singletonRatio);
        }
        if (incorrect != null) {
          classifier.learn(iFeatures, -1.0, weight * singletonRatio);
        }
      }
    } else {
      classifier.learn(cFeatures, 1.0, weight);
      classifier.learn(iFeatures, -1.0, weight);
    }
  }

  public double predict(Example example,
      Map<Integer, CompressedFeatureVector> mentionFeatures, Compressor<String> compressor) {
    Counter<String> features = meta.getFeatures(example, mentionFeatures, compressor);
    return classifier.label(features);
  }

  public int getNumTrainingExamples() {
    return trainingExamples;
  }

  public int getNumEpochs() {
    return epochs;
  }
}
