package edu.stanford.nlp.classify;

import edu.stanford.nlp.ling.Datum;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.stats.Counters;
import edu.stanford.nlp.util.ArrayMap;
import edu.stanford.nlp.util.Generics;
import edu.stanford.nlp.util.HashIndex;
import edu.stanford.nlp.util.Index;
import edu.stanford.nlp.util.logging.Logging;

import java.util.Collection;
import java.util.Map;

/**
 * One vs All multiclass classifier
 *
 * @author Angel Chang
 */
public class OneVsAllClassifier<L,F> implements Classifier<L,F> {
  private static final long serialVersionUID = -743792054415242776L;

  final static String POS_LABEL = "+1";
  final static String NEG_LABEL = "-1";
  final static Index<String> binaryIndex;
  final static int posIndex;
  static {
    binaryIndex = new HashIndex<String>();
    binaryIndex.add(POS_LABEL);
    binaryIndex.add(NEG_LABEL);
    posIndex = binaryIndex.indexOf(POS_LABEL);
  }

  Index<F> featureIndex;
  Index<L> labelIndex;
  Map<L, Classifier<String,F>> binaryClassifiers;
  L defaultLabel;

  public OneVsAllClassifier(Index<F> featureIndex, Index<L> labelIndex) {
    this(featureIndex, labelIndex, Generics.<L, Classifier<String, F>>newHashMap(), null);
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

  public void addBinaryClassifier(L label, Classifier<String,F> classifier)
  {
    binaryClassifiers.put(label, classifier);
  }

  protected Classifier<String,F> getBinaryClassifier(L label)
  {
    return binaryClassifiers.get(label);
  }

  public L classOf(Datum<L, F> example) {
    Counter<L> scores = scoresOf(example);
    if (scores != null) {
      return Counters.argmax(scores);
    } else {
      return defaultLabel;
    }
  }

  public Counter<L> scoresOf(Datum<L, F> example) {
    Counter<L> scores = new ClassicCounter<L>();
    for (L label:labelIndex) {
      Map<L,String> posLabelMap = new ArrayMap<L,String>();
      posLabelMap.put(label, POS_LABEL);
      Datum<String,F> binDatum = GeneralDataset.mapDatum(example, posLabelMap, NEG_LABEL);
      Classifier<String,F> binaryClassifier = getBinaryClassifier(label);
      Counter<String> binScores = binaryClassifier.scoresOf(binDatum);
      double score = binScores.getCount(POS_LABEL);
      scores.setCount(label, score);
    }
    return scores;
  }

  public Collection<L> labels() {
    return labelIndex.objectsList();
  }

  public static <L,F> OneVsAllClassifier<L,F> train(ClassifierFactory<String,F, Classifier<String,F>> classifierFactory,
                                                    GeneralDataset<L, F> dataset)
  {
    Index<L> labelIndex = dataset.labelIndex();
    return train(classifierFactory, dataset, labelIndex.objectsList());
  }

  public static <L,F> OneVsAllClassifier<L,F> train(ClassifierFactory<String,F, Classifier<String,F>> classifierFactory,
                                                    GeneralDataset<L, F> dataset, Collection<L> trainLabels)
  {
    Index<L> labelIndex = dataset.labelIndex();
    Index<F> featureIndex = dataset.featureIndex();
    Map<L, Classifier<String, F>> classifiers = Generics.newHashMap();
    for (L label:trainLabels) {
      int i = labelIndex.indexOf(label);
      Logging.logger(OneVsAllClassifier.class).info("Training " + label + "=" + i + ", posIndex=" + posIndex);
      // Create training data for training this classifier
      Map<L,String> posLabelMap = new ArrayMap<L,String>();
      posLabelMap.put(label, POS_LABEL);
      GeneralDataset<String,F> binaryDataset = dataset.<String>mapDataset(dataset, binaryIndex, posLabelMap, NEG_LABEL);
      Classifier<String,F> binaryClassifier = classifierFactory.trainClassifier(binaryDataset);
      classifiers.put(label, binaryClassifier);
    }
    OneVsAllClassifier<L,F> classifier = new OneVsAllClassifier<L,F>(featureIndex, labelIndex, classifiers);
    return classifier;
  }
}
