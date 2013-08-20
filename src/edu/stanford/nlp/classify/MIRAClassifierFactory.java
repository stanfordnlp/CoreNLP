package edu.stanford.nlp.classify;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import org.apache.commons.math.linear.ArrayRealVector;
import org.apache.commons.math.linear.RealVector;

import edu.stanford.nlp.ling.RVFDatum;
import edu.stanford.nlp.math.ArrayMath;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.stats.Counters;
import edu.stanford.nlp.util.Pair;
import edu.stanford.nlp.util.HashIndex;
import edu.stanford.nlp.util.Index;
import edu.stanford.nlp.util.Pair;
import edu.stanford.nlp.util.RandomizedIterable;

/**
 * Trains a classifier using MIRA (Crammer and Singer 2002)
 *
 * @author daniel cer (http://dmcer.net)
 *
 * @param <L>
 * @param <F>
 * @param <C>
 */
public class MIRAClassifierFactory<L, F, C extends Classifier<L, F>> implements ClassifierFactory<L, F, Classifier<L,F>> {
  /**
   *
   */
  private static final long serialVersionUID = 1L;

  public static final boolean VERBOSE = false;
  public static final int    DEFAULT_EPOCHS = 5;
  public static final double DEFAULT_C = 0.01;
  public static final boolean DEFAULT_AVERAGE_WEIGHTS = true;
  final int epochs;
  final double C;
  final boolean averageWeights;

  public MIRAClassifierFactory() {
    epochs = DEFAULT_EPOCHS;
    C = DEFAULT_C;
    averageWeights = DEFAULT_AVERAGE_WEIGHTS;
  }

  public MIRAClassifierFactory(double C) {
    this.C = C;
    epochs = DEFAULT_EPOCHS;
    averageWeights = DEFAULT_AVERAGE_WEIGHTS;
  }

  public MIRAClassifierFactory(double C, int epochs) {
    this.epochs = epochs;
    this.C = C;
    averageWeights = DEFAULT_AVERAGE_WEIGHTS;
  }

  public MIRAClassifierFactory(String argsToParse) {
    String[] fields = argsToParse.split(",");
    if (fields.length >= 1) {
      this.C = Double.parseDouble(fields[0]);
    } else {
      this.C = DEFAULT_C;
    }
    if (fields.length >= 2) {
      this.epochs = Integer.parseInt(fields[1]);
    } else {
      this.epochs = DEFAULT_EPOCHS;
    }
    if (fields.length >= 3) {
      this.averageWeights = Boolean.parseBoolean(fields[2]);
    } else {
      this.averageWeights = DEFAULT_AVERAGE_WEIGHTS;
    }

    if (fields.length >= 4) {
      throw new RuntimeException("MiraClassifierFactory takes no more than two arguments but was given "+fields.length+": " +argsToParse);
    }
  }

  public Classifier<L, F> trainClassifier(List<RVFDatum<L, F>> examples) {
    Dataset<L, F> dataset = new Dataset<L, F>();
    dataset.addAll(examples);
    return trainClassifier(dataset);
  }

  @SuppressWarnings("unchecked")
  public Classifier<L, F> trainClassifier(GeneralDataset<L, F> dataset) {
    Set<L> labelSet = new HashSet<L>();
    for (RVFDatum<L, F> datum : dataset) {
      labelSet.add(datum.label());
    }
    List<L> labelList = new ArrayList<L>(labelSet);

    Index<Pair<F,L>> weightIndex = new HashIndex<Pair<F,L>>();
    double[] weightsArray = new double[0];
    //Counter<Pair<F,L>> weightsSum = new ClassicCounter<Pair<F,L>>();
    ArrayRealVector weightsSum = new ArrayRealVector();

    ClassicCounter<Pair<F,L>> weights = new ClassicCounter<Pair<F,L>>();
    MIRAWeightUpdater<Pair<F,L>, String> miraUpdate = new MIRAWeightUpdater<Pair<F,L>, String>(C);

    System.out.println("Training with MIRA");
    System.out.printf("\tC: %e\n", C);
    System.out.printf("\tEpochs %d\n", epochs);
    System.out.printf("\tWeight averaging: %b\n", averageWeights);
    Random r = new Random(1);

    RandomizedIterable<RVFDatum<L,F>> randDatums = new RandomizedIterable<RVFDatum<L,F>>(dataset, r, true);

    for (int epoch = 0; epoch < epochs; epoch++) {
      double sumMarginViolations = 0;
      int cntMarginViolations = 0;
      int cntIncorrect = 0;
      for (RVFDatum<L, F> datum : randDatums) {
        ClassicCounter<Pair<F,L>>[] guessedVectors = new ClassicCounter[labelList.size()];
        ClassicCounter<Pair<F,L>>[] goldVectors = new ClassicCounter[labelList.size()];
        double[] losses = new double[labelList.size()];
        double bestIncorrectScore = Double.NEGATIVE_INFINITY;
        goldVectors[0] = makeFeatureFunctionPairs(datum.label(), datum);
        for (int i = 0; i < guessedVectors.length; i++) {
          guessedVectors[i] = makeFeatureFunctionPairs(labelList.get(i), datum);
          goldVectors[i] = goldVectors[0];
          if (labelList.get(i).equals(datum.label())) {
            losses[i] = 0;
          } else{
            losses[i] = 1;
            double score = Counters.dotProduct(weights, guessedVectors[i]);
            bestIncorrectScore = (bestIncorrectScore < score ? score : bestIncorrectScore);
          }
        }

        double goldScore = Counters.dotProduct(weights, goldVectors[0]);

        double margin = bestIncorrectScore + 1 - goldScore;
        if (margin >= 0) {
          sumMarginViolations = margin;
          cntMarginViolations++;
          if (VERBOSE) {
            System.out.printf("Margin violation of %e\n", margin);
            System.out.printf("Weights: %s\n", weights);
          }
        }

        if (bestIncorrectScore  >= goldScore) {
          cntIncorrect++;
        }

        Counter<Pair<F,L>> delta = miraUpdate.getUpdate(null, goldVectors, guessedVectors, losses, null, 0);
        weights.addAll(delta);
        if (VERBOSE) {
          bestIncorrectScore = Double.NEGATIVE_INFINITY;
          for (int i = 0; i < guessedVectors.length; i++) {
            if (!labelList.get(i).equals(datum.label())) {
              double score = Counters.dotProduct(weights, guessedVectors[i]);
              bestIncorrectScore = (bestIncorrectScore < score ? score : bestIncorrectScore);
            }
          }
          goldScore = Counters.dotProduct(weights, goldVectors[0]);

          System.out.printf("Post update margin violation of %e\n", bestIncorrectScore + 1 - goldScore);
        }


        if (epoch+1 == epochs && averageWeights) {
          RealVector weightsVec = miraUpdate.getWeights();
          if (weightsVec.getDimension() > weightsSum.getDimension()) {
            weightsSum = (ArrayRealVector)weightsSum.append(new double[weightsVec.getDimension() - weightsSum.getDimension()]);
          }
          weightsSum = MIRAWeightUpdater.quickAddToSelf(weightsSum, weightsVec);
        }
      }
      System.out.printf("MIRA Epoch %d - Incorrect: %d/%d Margin violations: %d Margin violation sum: %e\n", epoch, cntIncorrect, dataset.size, cntMarginViolations, sumMarginViolations);

    }

    double[] finalWeights;

    if (averageWeights) {
      finalWeights = weightsSum.toArray();
    } else {
      finalWeights = miraUpdate.getWeights().toArray();
    }

    if (ArrayMath.L1Norm(finalWeights) != 0) {
      ArrayMath.L1normalize(finalWeights);
    }

    LinearClassifier<L, F> lc = new LinearClassifier<L, F>(finalWeights, miraUpdate.getKeyIndex());

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

