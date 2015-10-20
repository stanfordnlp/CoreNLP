package edu.stanford.nlp.scoref;

import java.io.File;
import java.util.Properties;

import edu.stanford.nlp.hcoref.data.Dictionaries;
import edu.stanford.nlp.scoref.MetaFeatureExtractor.SingleConjunction;
import edu.stanford.nlp.scoref.StatisticalCorefProperties.Dataset;

public class StatisticalCorefTrainer {
  public static final String CLASSIFICATION_MODEL = "classification-un";
  public static final String RANKING_MODEL = "ranking-un";
  public static final String ANAPHORICITY_MODEL = "anaphoricity-un";
  public static final String CLUSTERING_MODEL_NAME = "clusterer";
  public static final String EXTRACTED_FEATURES_NAME = "features";

  public static String trainingPath;
  public static String pairwiseModelsPath;
  public static String clusteringModelsPath;

  public static String predictionsName;
  public static String datasetFile;
  public static String goldClustersFile;
  public static String wordCountsFile;
  public static String mentionTypesFile;
  public static String compressorFile;
  public static String extractedFeaturesFile;

  private static void makeDir(String path) {
    File outDir = new File(path);
    if (!outDir.exists()) {
        outDir.mkdir();
    }
  }

  public static void setTrainingPath(Properties props) {
    trainingPath = StatisticalCorefProperties.trainingPath(props);
    pairwiseModelsPath = trainingPath + "pairwise_models/";
    clusteringModelsPath = trainingPath + "clustering_models/";
    makeDir(pairwiseModelsPath);
    makeDir(clusteringModelsPath);
  }

  public static void setDataPath(String name, boolean isTraining) {
    String dataPath = trainingPath + name + "/";
    String extractedFeaturesPath = dataPath + EXTRACTED_FEATURES_NAME + "/";
    makeDir(dataPath);
    makeDir(extractedFeaturesPath);

    datasetFile = dataPath + "dataset.ser";
    predictionsName = name + "_predictions";
    goldClustersFile = dataPath + "gold_clusters.ser";
    mentionTypesFile = dataPath + "mention_types.ser";
    compressorFile = extractedFeaturesPath + "compressor.ser";
    extractedFeaturesFile = extractedFeaturesPath + "compressed_features.ser";
    if (isTraining) {
      wordCountsFile = dataPath + "word_counts.ser";
    }
  }

  public static MetaFeatureExtractor anaphoricityMFE() {
    return MetaFeatureExtractor.newBuilder()
    .singleConjunctions(new SingleConjunction[] {SingleConjunction.INDEX,
            SingleConjunction.INDEX_LAST})
    .disallowedPrefixes(new String[] {"parent-word"})
    .anaphoricityClassifier(true)
    .build();
  }

  public static void preprocess(Properties props, Dictionaries dictionaries, boolean isTrainSet)
      throws Exception {
    (isTrainSet ? new DatasetBuilder(StatisticalCorefProperties.minClassImbalance(props),
        StatisticalCorefProperties.minTrainExamplesPerDocument(props)) :
          new DatasetBuilder()).run(props, dictionaries);
    new MetadataWriter(isTrainSet).run(props, dictionaries);
    new FeatureExtractorRunner(props, dictionaries).run(props, dictionaries);
  }
  public static void test(PairwiseModel classificationModel, PairwiseModel rankingModel,
      PairwiseModel anaphoricityModel) throws Exception {
    PairwiseModelTrainer.test(classificationModel, predictionsName, false);
    PairwiseModelTrainer.test(rankingModel, predictionsName, false);
    PairwiseModelTrainer.test(anaphoricityModel, predictionsName, true);
  }

  public static void doTraining(Properties props) throws Exception {
    Dictionaries dictionaries = new Dictionaries(props);

    setDataPath("train", true);
    StatisticalCorefProperties.setInput(props, Dataset.TRAIN);
    preprocess(props, dictionaries, true);

    PairwiseModel classificationModel = PairwiseModel.newBuilder(CLASSIFICATION_MODEL,
        MetaFeatureExtractor.newBuilder().build()).build();
    PairwiseModel rankingModel = PairwiseModel.newBuilder(RANKING_MODEL,
        MetaFeatureExtractor.newBuilder().build()).build();
    PairwiseModel anaphoricityModel = PairwiseModel.newBuilder(ANAPHORICITY_MODEL,
        anaphoricityMFE()).trainingExamples(5000000).build();
    PairwiseModelTrainer.trainRanking(rankingModel);
    PairwiseModelTrainer.trainClassification(classificationModel, false);
    PairwiseModelTrainer.trainClassification(anaphoricityModel, true);

    setDataPath("dev", false);
    StatisticalCorefProperties.setInput(props, Dataset.DEV);
    preprocess(props, dictionaries, false);
    test(classificationModel, rankingModel, anaphoricityModel);
  }

  public static void main(String[] args) throws Exception {
    Properties props = StatisticalCorefProperties.loadProps(args[0]);
    setTrainingPath(props);
    doTraining(props);
    new Clusterer().doTraining(CLUSTERING_MODEL_NAME);
  }
}
