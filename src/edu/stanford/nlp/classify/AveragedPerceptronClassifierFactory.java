package edu.stanford.nlp.classify;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import edu.stanford.nlp.ling.RVFDatum;
import edu.stanford.nlp.math.ArrayMath;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.stats.Counters;
import edu.stanford.nlp.util.Pair;
import edu.stanford.nlp.util.HashIndex;
import edu.stanford.nlp.util.Index;
import edu.stanford.nlp.util.RandomizedIterable;

/**
 * Trains a classifier using Averaged Perceptron (Collins 2002)
 *
 * @author danielcer (http://dmcer.net)
 *
 * @param <L>
 * @param <F>
 */
public class AveragedPerceptronClassifierFactory<L, F> implements ClassifierFactory<L, F, Classifier<L,F>> {
  /**
   *
   */
  private static final long serialVersionUID = 1L;

  public static final boolean VERBOSE = false;
  public static final int    DEFAULT_EPOCHS = 5;
  final int epochs;


  public AveragedPerceptronClassifierFactory() {
    epochs = DEFAULT_EPOCHS;
  }

  public AveragedPerceptronClassifierFactory(int epochs) {
    this.epochs = epochs;
  }

  public AveragedPerceptronClassifierFactory(String argsToParse) {
    String[] fields = argsToParse.split(",");
    if (fields.length >= 1) {
      this.epochs = Integer.parseInt(fields[0]);
    } else {
      this.epochs = DEFAULT_EPOCHS;
    }

    if (fields.length >= 2) {
      throw new RuntimeException("AveragedPerceptronClassifierFactory takes no more than one argument but was given "+fields.length+": " +argsToParse);
    }
  }

  @Override
  public Classifier<L, F> trainClassifier(List<RVFDatum<L, F>> examples) {
    Dataset<L, F> dataset = new Dataset<L, F>();
    dataset.addAll(examples);
    return trainClassifier(dataset);
  }

  private static final double[] EMPTY_DOUBLE_ARRAY = new double[0];

  @Override
  @SuppressWarnings("unchecked")
  public Classifier<L, F> trainClassifier(GeneralDataset<L, F> dataset) {
    Set<L> labelSet = new HashSet<L>();
    for (RVFDatum<L, F> datum : dataset) {
      labelSet.add(datum.label());
    }
    List<L> labelList = new ArrayList<L>(labelSet);

    Index<Pair<F,L>> weightIndex = new HashIndex<Pair<F,L>>();
    double[] weights = EMPTY_DOUBLE_ARRAY;
    double[] weightsSumLastEpoch = EMPTY_DOUBLE_ARRAY;
    System.out.println("Training with Averaged Perceptron");
    System.out.printf("\tEpochs %d\n", epochs);
    for (int epoch = 0; epoch < epochs; epoch++) {
      int cntIncorrect = 0;
      double magnitudeIncorrect = 0;
      Random r = new Random(1);
      RandomizedIterable<RVFDatum<L,F>> randDatums = new RandomizedIterable<RVFDatum<L,F>>(dataset, r, true);
      for (RVFDatum<L, F> datum : randDatums) {
        ClassicCounter<Pair<F,L>>[] guessedVectors = new ClassicCounter[labelList.size()];
        ClassicCounter<Pair<F,L>>[] goldVector = new ClassicCounter[1];
        double[] losses = new double[labelList.size()];
        double bestGuessScore = Double.NEGATIVE_INFINITY;
        int bestGuessIdx = -1;
        for (int i = 0; i < guessedVectors.length; i++) {
          guessedVectors[i] = makeFeatureFunctionPairs(labelList.get(i), datum);
          if (labelList.get(i).equals(datum.label())) {
            losses[i] = 0;
          } else{
            losses[i] = 1;
          }
          double score = Counters.dotProduct(guessedVectors[i], weights, weightIndex);
          if (bestGuessScore <= score) {
            bestGuessScore = score;
            bestGuessIdx = i;
          }
        }
        goldVector[0] = makeFeatureFunctionPairs(datum.label(), datum);
        double goldScore = Counters.dotProduct(goldVector[0], weights, weightIndex);

        if (losses[bestGuessIdx] == 1) {
          cntIncorrect++;
        }

        if (VERBOSE) {
          System.err.printf("Label: %s (%f) Best Guess: %s (%f) Features: %s\n", datum.label(), goldScore, labelList.get(bestGuessIdx), bestGuessScore, datum.asFeaturesCounter().toString());
        }

        if (bestGuessScore >= goldScore && !goldVector[0].equals(guessedVectors[bestGuessIdx])) {
          magnitudeIncorrect += bestGuessScore - goldScore;
          weightIndex.addAll(goldVector[0].keySet());
          weightIndex.addAll(guessedVectors[bestGuessIdx].keySet());
          if (weightIndex.size() > weights.length) {
            weights = ArrayMath.copyOf(weights, weightIndex.size());
          }
          Counters.addInPlace(weights, goldVector[0], weightIndex);
          Counters.subtractInPlace(weights, guessedVectors[bestGuessIdx], weightIndex);
        }
        if (epoch+1 == epochs) {
           weightsSumLastEpoch = ArrayMath.pairwiseAdd(weights, weightsSumLastEpoch);
        }
      }
      System.out.printf("Averaged Perceptron Epoch %d - Incorrect: %d/%d Magnitude Incorrect: %e\n", epoch, cntIncorrect, dataset.size, magnitudeIncorrect);
    }
    ArrayMath.L1normalize(weightsSumLastEpoch);
    LinearClassifier<L, F> lc = new LinearClassifier<L, F>(weightsSumLastEpoch, weightIndex);
    return lc;
  }


  private ClassicCounter<Pair<F,L>> makeFeatureFunctionPairs(L label, RVFDatum<L, F> datum) {
    ClassicCounter<Pair<F,L>> featFunc = new ClassicCounter<Pair<F,L>>();
    Counter<F> features = datum.asFeaturesCounter();
    for (Map.Entry<F,Double> entry : features.entrySet()) {
      featFunc.setCount(new Pair<F,L>(entry.getKey(), label), entry.getValue());
    }
    return featFunc;
  }

}

