package edu.stanford.nlp.sequences;

import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.stats.Counters;

import java.util.Arrays;

/** A SequenceFinder which can efficiently return a k-best list of sequence labellings.
 *
 *  @author Jenny Finkel
 *  @author Sven Zethelius
 */
public class KBestSequenceFinder implements BestSequenceFinder {

  /**
   * Runs the Viterbi algorithm on the sequence model
   * in order to find the best sequence.
   * This sequence finder only works on SequenceModel's with rightWindow == 0.
   *
   * @return An array containing the int tags of the best sequence
   */
  @Override
  public int[] bestSequence(SequenceModel ts) {
    return Counters.argmax(kBestSequences(ts, 1));
  }

  /**
   * Runs the Viterbi algorithm on the sequence model, and then proceeds to efficiently
   * backwards decode the best k label sequence assignments.
   * This sequence finder only works on SequenceModel's with rightWindow == 0.
   *
   * @param ts The SequenceModel to find the best k label sequence assignments of
   * @param k The number of top-scoring assignments to find.
   * @return A Counter with k entries that map from a sequence assignment (int array) to a double score
   */
  @SuppressWarnings("MethodMayBeStatic")
  public Counter<int[]> kBestSequences(SequenceModel ts, int k) {

    // Set up tag options
    int length = ts.length();
    int leftWindow = ts.leftWindow();
    int rightWindow = ts.rightWindow();

    if (rightWindow != 0) {
      throw new IllegalArgumentException("KBestSequenceFinder only works with rightWindow == 0 not " + rightWindow);
    }

    int padLength = length + leftWindow + rightWindow;

    int[][] tags = new int[padLength][];
    int[] tagNum = new int[padLength];
    for (int pos = 0; pos < padLength; pos++) {
      tags[pos] = ts.getPossibleValues(pos);
      tagNum[pos] = tags[pos].length;
    }

    int[] tempTags = new int[padLength];

    // Set up product space sizes
    int[] productSizes = new int[padLength];

    int curProduct = 1;
    for (int i = 0; i < leftWindow; i++) {
      curProduct *= tagNum[i];
    }
    for (int pos = leftWindow; pos < padLength; pos++) {
      if (pos > leftWindow + rightWindow) {
        curProduct /= tagNum[pos - leftWindow - rightWindow - 1]; // shift off
      }
      curProduct *= tagNum[pos]; // shift on
      productSizes[pos - rightWindow] = curProduct;
    }

    double[][] windowScore = new double[padLength][];

    // Score all of each window's options
    for (int pos = leftWindow; pos < leftWindow + length; pos++) {
      windowScore[pos] = new double[productSizes[pos]];
      Arrays.fill(tempTags, tags[0][0]);

      for (int product = 0; product < productSizes[pos]; product++) {
        int p = product;
        int shift = 1;
        for (int curPos = pos; curPos >= pos - leftWindow; curPos--) {
          tempTags[curPos] = tags[curPos][p % tagNum[curPos]];
          p /= tagNum[curPos];
          if (curPos > pos) {
            shift *= tagNum[curPos];
          }
        }
        if (tempTags[pos] == tags[pos][0]) {
          // get all tags at once
          double[] scores = ts.scoresOf(tempTags, pos);
          // fill in the relevant windowScores
          for (int t = 0; t < tagNum[pos]; t++) {
            windowScore[pos][product + t * shift] = scores[t];
          }
        }
      }
    }

    // Set up score and backtrace arrays
    double[][][] score = new double[padLength][][];
    int[][][][] trace = new int[padLength][][][];
    int[][] numWaysToMake = new int[padLength][];
    for (int pos = 0; pos < padLength; pos++) {

      score[pos] = new double[productSizes[pos]][];
      trace[pos] = new int[productSizes[pos]][][]; // the 2 is for backtrace, and which of the k best for that backtrace

      numWaysToMake[pos] = new int[productSizes[pos]];
      Arrays.fill(numWaysToMake[pos], 1);
      for (int product = 0; product < productSizes[pos]; product++) {
        if (pos > leftWindow) {
          // loop over possible predecessor types
          int sharedProduct = product / tagNum[pos];
          int factor = productSizes[pos] / tagNum[pos];

          numWaysToMake[pos][product] = 0;
          for (int newTagNum = 0; newTagNum < tagNum[pos - leftWindow - 1] && numWaysToMake[pos][product] < k; newTagNum++) {
            int predProduct = newTagNum * factor + sharedProduct;
            numWaysToMake[pos][product] += numWaysToMake[pos-1][predProduct];
          }
          if (numWaysToMake[pos][product] > k) { numWaysToMake[pos][product] = k; }
        }

        score[pos][product] = new double[numWaysToMake[pos][product]];
        Arrays.fill(score[pos][product], Double.NEGATIVE_INFINITY);
        trace[pos][product] = new int[numWaysToMake[pos][product]][];
        Arrays.fill(trace[pos][product], new int[]{-1,-1});
      }
    }

    // Do forward Viterbi algorithm
    // this is the hottest loop, so cache loop control variables hoping for a little speed....

    // loop over the classification spot
    for (int pos = leftWindow, posMax = length + leftWindow; pos < posMax; pos++) {
      // loop over window product types
      for (int product = 0, productMax = productSizes[pos]; product < productMax; product++) {
        // check for initial spot
        double[] scorePos = score[pos][product];
        int[][] tracePos = trace[pos][product];
        if (pos == leftWindow) {
          // no predecessor type
          scorePos[0] = windowScore[pos][product];
        } else {
          // loop over possible predecessor types/k-best

          int sharedProduct = product / tagNum[pos + rightWindow];
          int factor = productSizes[pos] / tagNum[pos + rightWindow];
          for (int newTagNum = 0, maxTagNum = tagNum[pos - leftWindow - 1]; newTagNum < maxTagNum; newTagNum++) {
            int predProduct = newTagNum * factor + sharedProduct;
            double[] scorePosPrev = score[pos-1][predProduct];
            for (int k1 = 0; k1 < scorePosPrev.length; k1++) {
              double predScore = scorePosPrev[k1] + windowScore[pos][product];
              if (predScore > scorePos[0]) { // new value higher then lowest value we should keep
                int k2 = Arrays.binarySearch(scorePos, predScore);
                k2 = k2 < 0 ? -k2 - 2 : k2 - 1;
                // open a spot at k2 by shifting off the lowest value
                System.arraycopy(scorePos, 1, scorePos, 0, k2);
                System.arraycopy(tracePos, 1, tracePos, 0, k2);

                scorePos[k2] = predScore;
                tracePos[k2]= new int[] {predProduct, k1};
              }
            }
          }
        }
      }
    }

    // Project the actual tag sequence
    int[] whichDerivation = new int[k];
    int[] bestCurrentProducts = new int[k];
    double[] bestFinalScores = new double[k];
    Arrays.fill(bestFinalScores, Double.NEGATIVE_INFINITY);

    // just the last guy
    for (int product = 0; product < productSizes[padLength - 1]; product++) {
      double[] scorePos = score[padLength - 1][product];
      for (int k1 = scorePos.length - 1;
            k1 >= 0 && scorePos[k1] > bestFinalScores[0];
            k1--) {
        int k2 = Arrays.binarySearch(bestFinalScores, scorePos[k1]);
        k2 = k2 < 0 ? -k2 - 2 : k2 - 1;
        // open a spot at k2 by shifting off the lowest value
        System.arraycopy(bestFinalScores, 1, bestFinalScores, 0, k2);
        System.arraycopy(whichDerivation, 1, whichDerivation, 0, k2);
        System.arraycopy(bestCurrentProducts, 1, bestCurrentProducts, 0, k2);

        bestCurrentProducts[k2] = product;
        whichDerivation[k2] = k1;
        bestFinalScores[k2] = scorePos[k1];
      }
    }
    ClassicCounter<int[]> kBestWithScores = new ClassicCounter<>();
    for (int k1 = k - 1; k1 >= 0 && bestFinalScores[k1] > Double.NEGATIVE_INFINITY; k1--) {
      int lastProduct = bestCurrentProducts[k1];
      for (int last = padLength - 1; last >= length - 1 && last >= 0; last--) {
        tempTags[last] = tags[last][lastProduct % tagNum[last]];
        lastProduct /= tagNum[last];
      }

      for (int pos = leftWindow + length - 2; pos >= leftWindow; pos--) {
        int bestNextProduct = bestCurrentProducts[k1];
        bestCurrentProducts[k1] = trace[pos + 1][bestNextProduct][whichDerivation[k1]][0];
        whichDerivation[k1] = trace[pos + 1][bestNextProduct][whichDerivation[k1]][1];
        tempTags[pos - leftWindow] =
                 tags[pos - leftWindow][bestCurrentProducts[k1]
                       / (productSizes[pos] / tagNum[pos - leftWindow])];
      }
      kBestWithScores.setCount(Arrays.copyOf(tempTags, tempTags.length), bestFinalScores[k1]);
    }

    return kBestWithScores;
  }

}
