package edu.stanford.nlp.classify;

import java.util.Iterator;

import org.apache.commons.math.linear.ArrayRealVector;
import org.apache.commons.math.linear.OpenMapRealVector;
import org.apache.commons.math.linear.RealVector;
import org.apache.commons.math.linear.RealVector.Entry;

import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.stats.CountersRealVectors;
import edu.stanford.nlp.util.HashIndex;
import edu.stanford.nlp.util.Index;

/**
 * MIRA learning.
 *
 * Optimization algorithm from Chiang et al.'s 2008 EMNLP paper
 *
 * Online Large-Margin Training of Syntactic and Structural Translation Features
 *
 * http://www.isi.edu/~chiang/papers/mira.pdf
 *
 * @author daniel cer
 *
 * @param <T>
 * @param <ID>
 */
public class MIRAWeightUpdater<T, ID> implements CounterWeightUpdater<T, ID>,VectorWeightUpdater<T, ID>  {
  final double C;
  final double TOL;

  public static final double DEFAULT_C = 0.01;
  public static final double DEFAULT_TOL = 1e-3;
  public static final double DIFF_TOL = 1e-3;

  public MIRAWeightUpdater() {
    this.C = DEFAULT_C;
    this.TOL = DEFAULT_TOL;
  }

  public MIRAWeightUpdater(double C) {
    this.C = C;
    this.TOL = DEFAULT_TOL;
  }

  public MIRAWeightUpdater(double C, double convergenceTol) {
    this.C = C;
    this.TOL = convergenceTol;
  }

  Index<T> keyIndex = new HashIndex<T>();

  public RealVector getUpdate(RealVector weights,
      RealVector[] goldVectors, RealVector[] guessedVectors,
      double[] losses, ID[] datumIDs, int iterSinceLastUpdate) {
    double[] alphas = new double[goldVectors.length];
    return getUpdate(weights, goldVectors, guessedVectors, losses, datumIDs, iterSinceLastUpdate, alphas);
  }

  public RealVector getUpdate(RealVector weights,
      RealVector[] goldVectors, RealVector[] guessedVectors,
      double[] losses, ID[] datumIDs, int iterSinceLastUpdate, double[] alphas) {

    ArrayRealVector delta = new ArrayRealVector(weights.getDimension());
    if (weights.getDimension() < delta.getDimension()) {
      weights = weights.append(new double[delta.getDimension()-weights.getDimension()]);
    }
    double diff = 0;

    double[] distanceSqrt = new double[goldVectors.length];
    RealVector[] distanceVec = new RealVector[goldVectors.length];
    double[] distanceDotWts = new double[goldVectors.length];

//System.err.printf("weights.dim: %d\n", weights.getDimension());

    int worstGuessIdx = 0;
    double worstErrorTerm = Double.NEGATIVE_INFINITY;
    for (int i = 0; i < alphas.length; i++) {
//System.err.printf("gold.dim: %d\n", goldVectors[i].getDimension());
//System.err.printf("guess.dim: %d\n", guessedVectors[i].getDimension());
      distanceVec[i] = goldVectors[i].add(guessedVectors[i].mapMultiply(-1));
      distanceDotWts[i] = distanceVec[i].dotProduct(weights);

      double norm = distanceVec[i].getNorm();
      distanceSqrt[i] = norm*norm;

      if (losses[i] != 0) {
        double errTerm = losses[i] - distanceVec[i].dotProduct(weights);
        if (errTerm > worstErrorTerm) {
           worstErrorTerm = errTerm;
           worstGuessIdx = i;
        }
      }
    }

    // constraints are already satisfied
    if (worstErrorTerm < 0.0) return new OpenMapRealVector(weights.getDimension());

    alphas[worstGuessIdx] = C;

    quickAddToSelf(delta, distanceVec[worstGuessIdx].mapMultiply(C));

    double[][] diffDist = new double[alphas.length][];

    for (int i = 0; i < alphas.length; i++) {
      diffDist[i] = new double[alphas.length];
      for (int j = 0; j < i; j++) {
        diffDist[i][j] = Math.pow((distanceVec[i].add(distanceVec[j].mapMultiply(-1))).getNorm(), 2);
        diffDist[j][i] = diffDist[i][j];
      }
    }

    int iters = 0;
    double dDdAlphaSum;
    do {
      iters++;
      diff = 0;
      dDdAlphaSum = 0;
      alphasI: for (int i = 0; i < alphas.length; i++) {
        double deltaI = (distanceDotWts[i] + delta.dotProduct(distanceVec[i]));
        double dDdAlpha = distanceSqrt[i] != 0 ? (losses[i] - deltaI)/distanceSqrt[i] : 0;
        dDdAlphaSum += Math.abs(dDdAlpha);
        for (int j = i+1; j < alphas.length; j++) {
          if (diffDist[i][j] == 0) continue;
          if (alphas[i] == 0 && alphas[j] == 0) continue;
          double deltaJ = (distanceDotWts[j] + delta.dotProduct(distanceVec[j]));
          double deltaAlpha = ((losses[i] - losses[j]) - (deltaI-deltaJ))/diffDist[i][j];
          deltaAlpha = Math.max(-alphas[i], deltaAlpha);
          deltaAlpha = Math.min(alphas[j], deltaAlpha);


          if (deltaAlpha != 0) {
            alphas[i] = alphas[i] + deltaAlpha;
            alphas[j] = alphas[j] - deltaAlpha;
            quickAddToSelf(delta, distanceVec[i].mapMultiply(deltaAlpha));
            quickAddToSelf(delta, distanceVec[j].mapMultiply(-deltaAlpha));

            diff += Math.abs(2*deltaAlpha);
            deltaI = (distanceDotWts[i] + delta.dotProduct(distanceVec[i]));
            if (alphas[i] == 0 && losses[i] - deltaI < 0) continue alphasI;
          }
        }
      }
      //System.out.printf("---%d  dDdAlphasum: %e diff = %e\n",iters, dDdAlphaSum,diff);
    } while (dDdAlphaSum/C > TOL && diff/C > DIFF_TOL);

    //System.out.printf("iters: %d dDdAlphasum: %e/%e < %e\n", iters,dDdAlphaSum,C,TOL);

    /* for (int i = 0; i < alphas.length; i++) {
      double errTerm = losses[i] - (distanceVec[i].dotProduct(weights) + delta.dotProduct(distanceVec[i]));
      System.err.printf("final error[%d]: %.3f (delta: %.3f)\n", i, errTerm, delta.dotProduct(distanceVec[i]));
    }  */

    if (weights instanceof ArrayRealVector) {
      lastWeights = quickAddToSelf((ArrayRealVector)weights, delta);
    } else {
      lastWeights = weights.add(delta);
    }

    return delta;
  }

  static ArrayRealVector quickAddToSelf(ArrayRealVector v1, RealVector v2) {
    double[] data = v1.getDataRef();
    if (v2 instanceof ArrayRealVector) {
      double[] data2 = ((ArrayRealVector)v2).getDataRef();
      for (int i = 0; i < data.length; i++) {
        data[i] += data2[i];
      }
    } else {
      Iterator<Entry> itr = v2.sparseIterator();
      Entry e;
      while (itr.hasNext() && (e = itr.next()) != null) {
          data[e.getIndex()] += e.getValue();
      }
    }
    return v1;
  }

  RealVector lastWeights;
  public RealVector getWeights() {
    return lastWeights;
  }

  public Index<T> getKeyIndex() {
    return keyIndex;
  }
  public Counter<T> getUpdate(Counter<T> weights,
      Counter<T>[] goldVectors, Counter<T>[] guessedVectors,
      double[] losses, ID[] datumIDs, int iterSinceLastUpdate) {

    if (lastWeights == null) {
      keyIndex.addAll(weights.keySet());
    }
    for (Counter<T> guessVector : guessedVectors) {
      keyIndex.addAll(guessVector.keySet());
    }
    for (Counter<T> goldVector : goldVectors) {
      keyIndex.addAll(goldVector.keySet());
    }

    // prevent having to rescale weights vector on (almost) every call
    int useSize = Integer.highestOneBit(keyIndex.size())<<1;
    RealVector weightsVec;
    if (weights != null) {
       weightsVec = CountersRealVectors.toRealVector(weights, keyIndex, useSize);
    } else {
       if (lastWeights == null) {
         weightsVec = new ArrayRealVector(useSize);
       } else if (lastWeights.getDimension() != useSize) {
         weightsVec = lastWeights.append(new double[useSize-lastWeights.getDimension()]);
       } else {
         weightsVec = lastWeights;
       }
    }

    RealVector[] guessVec = new RealVector[guessedVectors.length];
    RealVector[] goldVec = new RealVector[goldVectors.length];

    for (int i = 0; i < guessVec.length; i++) {
      guessVec[i] = CountersRealVectors.toSparseRealVector(guessedVectors[i], keyIndex, useSize);
      goldVec[i]  = CountersRealVectors.toSparseRealVector(goldVectors[i], keyIndex, useSize);
    }

    RealVector delta = getUpdate(weightsVec, goldVec, guessVec, losses, datumIDs, iterSinceLastUpdate);
    return CountersRealVectors.fromRealVector(delta, keyIndex);
  }

  public void endEpoch() {
  }

  @SuppressWarnings({ "unchecked", "rawtypes" })
  public static void main(String[] args) {
    ClassicCounter<String>   weights = new ClassicCounter<String>();
    ClassicCounter<String>   goldVector = new ClassicCounter<String>();
    ClassicCounter<String>   guessVector = new ClassicCounter<String>();
    ClassicCounter<String>[] goldVectors = new ClassicCounter[]{goldVector};
    ClassicCounter<String>[] guessVectors = new ClassicCounter[]{guessVector};
    double[] losses = new double[1];

    MIRAWeightUpdater<String, String> mira = new MIRAWeightUpdater<String, String>(Double.POSITIVE_INFINITY);

    // gold (1,1)
    // guess(0,0) loss 1
    System.err.println("|  o");
    System.err.println("|x__");

    goldVector.setCount("x", 1);
    goldVector.setCount("y", 1);
    losses[0] = 1;
    Counter<String> update = mira.getUpdate(weights, goldVectors, guessVectors, losses, new String[0], 0);
    System.err.println("update: "+update);
    weights.addAll(update);

    goldVector.clear();
    guessVector.clear();
    goldVector.setCount("x", 1);
    losses[0] = 2;
    // gold  1,0
    // guess 0,0 loss = 2
    System.err.println("|   ");
    System.err.println("|x_o");

    update = mira.getUpdate(weights, goldVectors, guessVectors, losses, new String[0], 0);
    System.err.println("update: "+update);


    mira = new MIRAWeightUpdater<String, String>(0.5);
    weights = new ClassicCounter<String>();
    goldVectors = new ClassicCounter[]{new ClassicCounter(), new ClassicCounter()};
    guessVectors = new ClassicCounter[]{new ClassicCounter(), new ClassicCounter()};
    losses = new double[2];
    goldVectors[0].setCount("x", 1);
    goldVectors[0].setCount("y", 1);
    goldVectors[1].setCount("x", 1);
    goldVectors[1].setCount("y", 1);
    losses[0] = 1;
    losses[1] = 1;
    guessVectors[0].setCount("x", 1);
    guessVectors[1].setCount("y", 1);

    System.err.println("|x o");
    System.err.println("|__x");
    System.err.println(weights);
    update = mira.getUpdate(weights, goldVectors, guessVectors, losses, new String[0], 0);
    System.err.println("update:" + update);
    mira = new MIRAWeightUpdater<String, String>();
    update = mira.getUpdate(weights, goldVectors, guessVectors, losses, new String[0], 0);
    System.err.println("update:" + update);

    goldVectors = new ClassicCounter[]{new ClassicCounter(), new ClassicCounter(), new ClassicCounter()};
    guessVectors = new ClassicCounter[]{new ClassicCounter(), new ClassicCounter(), new ClassicCounter()};
    losses = new double[3];
    losses[0] = 1;
    losses[1] = 1;
    losses[2] = 1;
    goldVectors[0].setCount("x", 1);
    goldVectors[0].setCount("y", 1);
    goldVectors[1].setCount("x", 1);
    goldVectors[1].setCount("y", 1);
    goldVectors[2].setCount("x", 1);
    goldVectors[2].setCount("y", 1);
    guessVectors[0].setCount("x", 1);
    guessVectors[1].setCount("y", 1);
    guessVectors[2].setCount("x", 0.75);
    guessVectors[2].setCount("y", 0.75);

    update = mira.getUpdate(weights, goldVectors, guessVectors, losses, new String[0], 0);
    System.err.println("update:" + update);

  }
}
