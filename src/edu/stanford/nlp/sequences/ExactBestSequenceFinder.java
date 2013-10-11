package edu.stanford.nlp.sequences;

import edu.stanford.nlp.util.Pair;
import java.util.Arrays;


/**
 * A class capable of computing the best sequence given a SequenceModel.
 * Uses the Viterbi algorithm.
 *
 * @author Dan Klein
 * @author Teg Grenager (grenager@stanford.edu)
 */
public class ExactBestSequenceFinder implements BestSequenceFinder {

  private static final boolean DEBUG = false;

  public static Pair<int[], Double> bestSequenceWithLinearConstraints(SequenceModel ts, double[][] linearConstraints) {
    return bestSequence(ts, linearConstraints);
  }

  /**
   * Runs the Viterbi algorithm on the sequence model given by the TagScorer
   * in order to find the best sequence.
   *
   * @param ts The SequenceModel to be used for scoring
   * @return An array containing the int tags of the best sequence
   */
  @Override
  public int[] bestSequence(SequenceModel ts) {
    return bestSequence(ts, null).first();
  }

  private static Pair<int[], Double> bestSequence(SequenceModel ts, double[][] linearConstraints) {
    // Set up tag options
    int length = ts.length();
    int leftWindow = ts.leftWindow();
    int rightWindow = ts.rightWindow();
    int padLength = length + leftWindow + rightWindow;
    if (linearConstraints != null && linearConstraints.length != padLength)
      throw new RuntimeException("linearConstraints.length (" +  linearConstraints.length + ") does not match padLength (" + padLength + ") of SequenceModel" + ", length=="+length+", leftW="+leftWindow+", rightW="+rightWindow);
    int[][] tags = new int[padLength][];
    int[] tagNum = new int[padLength];
    if (DEBUG) { System.err.println("Doing bestSequence length " + length + "; leftWin " + leftWindow + "; rightWin " + rightWindow + "; padLength " + padLength); }
    for (int pos = 0; pos < padLength; pos++) {
      tags[pos] = ts.getPossibleValues(pos);
      tagNum[pos] = tags[pos].length;
      if (DEBUG) { System.err.println("There are " + tagNum[pos] + " values at position " + pos + ": " + Arrays.toString(tags[pos])); }
    }

    int[] tempTags = new int[padLength];

    // Set up product space sizes
    int[] productSizes = new int[padLength];

    int curProduct = 1;
    for (int i = 0; i < leftWindow + rightWindow; i++) {
      curProduct *= tagNum[i];
    }
    for (int pos = leftWindow + rightWindow; pos < padLength; pos++) {
      if (pos > leftWindow + rightWindow) {
        curProduct /= tagNum[pos - leftWindow - rightWindow - 1]; // shift off
      }
      curProduct *= tagNum[pos]; // shift on
      productSizes[pos - rightWindow] = curProduct;
    }

    // Score all of each window's options
    double[][] windowScore = new double[padLength][];
    for (int pos = leftWindow; pos < leftWindow + length; pos++) {
      if (DEBUG) { System.err.println("scoring word " + pos + " / " + (leftWindow + length) + ", productSizes =  " + productSizes[pos] + ", tagNum = " + tagNum[pos] + "..."); }
      windowScore[pos] = new double[productSizes[pos]];
      Arrays.fill(tempTags, tags[0][0]);
      if (DEBUG) { System.err.println("windowScore[" + pos + "] has size (productSizes[pos]) " + windowScore[pos].length); }

      for (int product = 0; product < productSizes[pos]; product++) {
        int p = product;
        int shift = 1;
        for (int curPos = pos + rightWindow; curPos >= pos - leftWindow; curPos--) {
          tempTags[curPos] = tags[curPos][p % tagNum[curPos]];
          p /= tagNum[curPos];
          if (curPos > pos) {
            shift *= tagNum[curPos];
          }
        }

        // Here now you get ts.scoresOf() for all classifications at a position at once, whereas the old code called ts.scoreOf() on each item.
        // CDM May 2007: The way this is done gives incorrect results if there are repeated values in the values of ts.getPossibleValues(pos) -- in particular if the first value of the array is repeated later.  I tried replacing it with the modulo version, but that only worked for left-to-right, not bidirectional inference, but I still think that if you sorted things out, you should be able to do it with modulos and the result would be conceptually simpler and robust to repeated values.  But in the meantime, I fixed the POS tagger to not give repeated values (which was a bug in the tagger).
        if (tempTags[pos] == tags[pos][0]) {
          // get all tags at once
          double[] scores = ts.scoresOf(tempTags, pos);
          if (DEBUG) { System.err.println("Matched at array index [product] " + product + "; tempTags[pos] == tags[pos][0] == " + tempTags[pos]); }
          if (DEBUG) { System.err.println("For pos " + pos + " scores.length is " + scores.length + "; tagNum[pos] = " + tagNum[pos] + "; windowScore[pos].length = " + windowScore[pos].length); }
          if (DEBUG) { System.err.println("scores: " + Arrays.toString(scores)); }
          // fill in the relevant windowScores
          for (int t = 0; t < tagNum[pos]; t++) {
            if (DEBUG) { System.err.println("Setting value of windowScore[" + pos + "][" + product + "+" + t + "*" + shift + "] = " + scores[t]); }
            windowScore[pos][product + t * shift] = scores[t];
          }
        }
      }
    }

    // Set up score and backtrace arrays
    double[][] score = new double[padLength][];
    int[][] trace = new int[padLength][];
    for (int pos = 0; pos < padLength; pos++) {
      score[pos] = new double[productSizes[pos]];
      trace[pos] = new int[productSizes[pos]];
    }

    // Do forward Viterbi algorithm

    // loop over the classification spot
    //System.err.println();
    for (int pos = leftWindow; pos < length + leftWindow; pos++) {
      //System.err.print(".");
      // loop over window product types
      for (int product = 0; product < productSizes[pos]; product++) {
        // check for initial spot
        if (pos == leftWindow) {
          // no predecessor type
          score[pos][product] = windowScore[pos][product];
          if (linearConstraints != null) {
            if (DEBUG) {
              if (linearConstraints[pos][product % tagNum[pos]] != 0) {
                System.err.println("Applying linear constraints=" + linearConstraints[pos][product % tagNum[pos]] + " to preScore="+ windowScore[pos][product] + " at pos="+pos+" for tag="+(product % tagNum[pos]));
              }
            }
            score[pos][product] += linearConstraints[pos][product % tagNum[pos]];
          }
          trace[pos][product] = -1;
        } else {
          // loop over possible predecessor types
          score[pos][product] = Double.NEGATIVE_INFINITY;
          trace[pos][product] = -1;
          int sharedProduct = product / tagNum[pos + rightWindow];
          int factor = productSizes[pos] / tagNum[pos + rightWindow];
          for (int newTagNum = 0; newTagNum < tagNum[pos - leftWindow - 1]; newTagNum++) {
            int predProduct = newTagNum * factor + sharedProduct;
            double predScore = score[pos - 1][predProduct] + windowScore[pos][product];

            if (linearConstraints != null) {
              if (DEBUG) {
                if (pos == 2 && linearConstraints[pos][product % tagNum[pos]] != 0) {
                  System.err.println("Applying linear constraints=" + linearConstraints[pos][product % tagNum[pos]] + " to preScore="+ predScore + " at pos="+pos+" for tag="+(product % tagNum[pos]));
                  System.err.println("predScore:" + predScore + " = score["+(pos - 1)+"]["+predProduct+"]:" + score[pos - 1][predProduct] + " + windowScore["+pos+"]["+product+"]:" + windowScore[pos][product]);
                }
              }
              predScore += linearConstraints[pos][product % tagNum[pos]];
            }

            if (predScore > score[pos][product]) {
              score[pos][product] = predScore;
              trace[pos][product] = predProduct;
            }
          }
        }
      }
    }

    // Project the actual tag sequence
    double bestFinalScore = Double.NEGATIVE_INFINITY;
    int bestCurrentProduct = -1;
    for (int product = 0; product < productSizes[leftWindow + length - 1]; product++) {
      if (score[leftWindow + length - 1][product] > bestFinalScore) {
        bestCurrentProduct = product;
        bestFinalScore = score[leftWindow + length - 1][product];
      }
    }
    int lastProduct = bestCurrentProduct;
    for (int last = padLength - 1; last >= length - 1 && last >= 0; last--) {
      tempTags[last] = tags[last][lastProduct % tagNum[last]];
      lastProduct /= tagNum[last];
    }
    for (int pos = leftWindow + length - 2; pos >= leftWindow; pos--) {
      int bestNextProduct = bestCurrentProduct;
      bestCurrentProduct = trace[pos + 1][bestNextProduct];
      tempTags[pos - leftWindow] = tags[pos - leftWindow][bestCurrentProduct / (productSizes[pos] / tagNum[pos - leftWindow])];
    }
    return new Pair<int[], Double>(tempTags, bestFinalScore);
  }
}
