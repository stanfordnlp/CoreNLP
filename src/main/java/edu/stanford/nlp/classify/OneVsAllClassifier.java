package edu.stanford.nlp.classify;

import edu.stanford.nlp.ling.Datum;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.stats.Counters;
import edu.stanford.nlp.util.ArrayMap;
import edu.stanford.nlp.util.Generics;
import edu.stanford.nlp.util.HashIndex;
import edu.stanford.nlp.util.Index;

import java.util.Collection;
import java.util.Map;


import edu.stanford.nlp.util.logging.Redwood;

/**
 * One vs All multiclass classifier
 *
 * @author Angel Chang
 */
public class OneVsAllClassifier<L,F> implements Classifier<L,F> {
  private static final long serialVersionUID = -743792054415242776L;

  private static final String POS_LABEL = "+1";
  private static final String NEG_LABEL = "-1";
  private static final Index<String> binaryIndex;
  private static final int posIndex;

  static {
    binaryIndex = new HashIndex<>();
    binaryIndex.add(POS_LABEL);
    binaryIndex.add(NEG_LABEL);
    posIndex = binaryIndex.indexOf(POS_LABEL);
  }

  private Index<F> featureIndex;
  private Index<L> labelIndex;
  private Map<L, Classifier<String,F>> binaryClassifiers;
  private L defaultLabel;

  private static final Redwood.RedwoodChannels logger = Redwood.channels(OneVsAllClassifier.class);

  public OneVsAllClassifier(Index<F> featureIndex, Index<L> labelIndex) {
    this(featureIndex, labelIndex, Generics.newHashMap(), null);
  }

  public OneVsAllClassifier(Index<F> featureIndex, Index<L> labelIndex, Map<L, Classifier<String, F>> binaryClassifiers) {
    this(featureIndex, labelIndex, binaryClassifiers, null);
  }

  public OneVsAllClassifier(Index<F> featureIndex, Index<L> labelIndex, Map<L, Classifier<String, F>> binaryClassifiers, L defaultLabel) {
    this.featureIndex = featureIndex;
    this.labelIndex = labelIndex;
    this.binaryClassifiers = binaryClassifiers;
    this.defaultLabel = defaultLabel;
  }

  public void addBinaryClassifier(L label, Classifier<String,F> classifier) {
    binaryClassifiers.put(label, classifier);
  }

  protected Classifier<String,F> getBinaryClassifier(L label)
  {
    return binaryClassifiers.get(label);
  }

  @Override
  public L classOf(Datum<L, F> example) {
    Counter<L> scores = scoresOf(example);
    if (scores != null) {
      return Counters.argmax(scores);
    } else {
      return defaultLabel;
    }
  }

  @Override
  public Counter<L> scoresOf(Datum<L, F> example) {
    Counter<L> scores = new ClassicCounter<>();
    for (L label:labelIndex) {
      Map<L,String> posLabelMap = new ArrayMap<>();
      posLabelMap.put(label, POS_LABEL);
      Datum<String,F> binDatum = GeneralDataset.mapDatum(example, posLabelMap, NEG_LABEL);
      Classifier<String,F> binaryClassifier = getBinaryClassifier(label);
      Counter<String> binScores = binaryClassifier.scoresOf(binDatum);
      double score = binScores.getCount(POS_LABEL);
      scores.setCount(label, score);
    }
    return scores;
  }

  @Override
  public Collection<L> labels() {
    return labelIndex.objectsList();
  }

  public static <L,F> OneVsAllClassifier<L,F> train(ClassifierFactory<String,F, Classifier<String,F>> classifierFactory,
                                                    GeneralDataset<L, F> dataset) {
    Index<L> labelIndex = dataset.labelIndex();
    return train(classifierFactory, dataset, labelIndex.objectsList());
  }

  public static <L,F> OneVsAllClassifier<L,F> train(ClassifierFactory<String,F, Classifier<String,F>> classifierFactory,
                                                    GeneralDataset<L, F> dataset, Collection<L> trainLabels) {
    Index<L> labelIndex = dataset.labelIndex();
    Index<F> featureIndex = dataset.featureIndex();
    Map<L, Classifier<String, F>> classifiers = Generics.newHashMap();
    for (L label:trainLabels) {
      int i = labelIndex.indexOf(label);
      logger.info("Training " + label + " = " + i + ", posIndex = " + posIndex);
      // Create training data for training this classifier
      Map<L,String> posLabelMap = new ArrayMap<>();
      posLabelMap.put(label, POS_LABEL);
      GeneralDataset<String,F> binaryDataset = dataset.mapDataset(dataset, binaryIndex, posLabelMap, NEG_LABEL);
      Classifier<String,F> binaryClassifier = classifierFactory.trainClassifier(binaryDataset);
      classifiers.put(label, binaryClassifier);
    }
    OneVsAllClassifier<L,F> classifier = new OneVsAllClassifier<>(featureIndex, labelIndex, classifiers);
    return classifier;
  }

}
