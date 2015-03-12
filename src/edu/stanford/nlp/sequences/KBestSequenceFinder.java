package edu.stanford.nlp.sequences;

import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counters;

import java.util.Arrays;

/**
 * @author Jenny Finkel
 */
public class KBestSequenceFinder implements BestSequenceFinder {

  /**
   * Runs the Viterbi algorithm on the sequence model
   * in order to find the best sequence.
   *
   * @return An array containing the int tags of the best sequence
   */
  @Override
  public int[] bestSequence(SequenceModel ts) {
    return Counters.argmax(kBestSequences(ts, 1));
  }

  public ClassicCounter<int[]> kBestSequences(SequenceModel ts, int k) {

    // Set up tag options
    int length = ts.length();
    int leftWindow = ts.leftWindow();
    int rightWindow = ts.rightWindow();

    assert (rightWindow == 0);

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
        if (pos == leftWindow) {
          numWaysToMake[pos][product] = 1;
        } else if (pos > leftWindow) {
          // loop over possible predecessor types
          int sharedProduct = product / tagNum[pos];
          int factor = productSizes[pos] / tagNum[pos];

          numWaysToMake[pos][product] = 0;
          for (int newTagNum = 0; newTagNum < tagNum[pos - leftWindow - 1]; newTagNum++) {
            int predProduct = newTagNum * factor + sharedProduct;
            numWaysToMake[pos][product] += numWaysToMake[pos-1][predProduct];
          }
          if (numWaysToMake[pos][product] > k) { numWaysToMake[pos][product] = k; }
        } else {
          numWaysToMake[pos][product] = 1;
        }

        score[pos][product] = new double[numWaysToMake[pos][product]];
        trace[pos][product] = new int[numWaysToMake[pos][product]][2];
      }
    }

    // Do forward Viterbi algorithm

    // loop over the classification spot
    for (int pos = leftWindow; pos < length + leftWindow; pos++) {
      // loop over window product types
      for (int product = 0; product < productSizes[pos]; product++) {
        // check for initial spot
        if (pos == leftWindow) {
          // no predecessor type
          score[pos][product][0] = windowScore[pos][product];
          trace[pos][product][0][0] = -1;
          trace[pos][product][0][1] = -1;
        } else {
          // loop over possible predecessor types/k-best

          for (int k1 = 0; k1 < score[pos][product].length; k1++) {
            score[pos][product][k1] = Double.NEGATIVE_INFINITY;
            trace[pos][product][k1][0] = -1;
            trace[pos][product][k1][1] = -1;
          }
          int sharedProduct = product / tagNum[pos + rightWindow];
          int factor = productSizes[pos] / tagNum[pos + rightWindow];
          for (int newTagNum = 0; newTagNum < tagNum[pos - leftWindow - 1]; newTagNum++) {
            int predProduct = newTagNum * factor + sharedProduct;
            for (int k1 = 0; k1 < score[pos-1][predProduct].length; k1++) {
              double predScore = score[pos - 1][predProduct][k1] + windowScore[pos][product];
              for (int k2 = 0; k2 < score[pos][product].length; k2++) {
                if (predScore > score[pos][product][k2]) {
                  System.arraycopy(score[pos][product], k2, score[pos][product], k2+1, score[pos][product].length-(k2+1));
                  System.arraycopy(trace[pos][product], k2, trace[pos][product], k2+1, trace[pos][product].length-(k2+1));
                  score[pos][product][k2] = predScore;
                  trace[pos][product][k2]= new int[2];
                  trace[pos][product][k2][0] = predProduct;
                  trace[pos][product][k2][1] = k1;
                  break;
                }
              }
            }
          }
        }
      }
    }

    // Project the actual tag sequence
    int[][] kBest = new int[k][padLength];
    int[] whichDerivation = new int[k];
    int[] bestCurrentProducts = new int[k];
    double[] bestFinalScores = new double[k];
    Arrays.fill(bestFinalScores, Double.NEGATIVE_INFINITY);

    // just the last guy
    for (int product = 0; product < productSizes[padLength - 1]; product++) {
      for (int k1 = 0; k1 < score[padLength - 1][product].length; k1++) {
        for (int k2 = 0; k2 < bestFinalScores.length; k2++) {
          if (score[padLength - 1][product][k1] > bestFinalScores[k2]) {

            System.arraycopy(bestFinalScores, k1, bestFinalScores, k1+1, bestFinalScores.length-(k1+1));
            System.arraycopy(whichDerivation, k1, whichDerivation, k1+1, whichDerivation.length-(k1+1));
            System.arraycopy(bestCurrentProducts, k1, bestCurrentProducts, k1+1, bestCurrentProducts.length-(k1+1));

            bestCurrentProducts[k2] = product;
            whichDerivation[k2] = k1;
            bestFinalScores[k2] = score[padLength - 1][product][k1];
            break;
          }
        }
      }
    }
    int[] lastProducts = new int[k];
    System.arraycopy(bestCurrentProducts, 0, lastProducts, 0, lastProducts.length);

    for (int last = padLength - 1; last >= length - 1 && last >= 0; last--) {
      for (int k1 = 0; k1 < lastProducts.length; k1++) {
        kBest[k1][last] = tags[last][lastProducts[k1] % tagNum[last]];
        lastProducts[k1] /= tagNum[last];
      }
    }
    for (int pos = padLength - 2; pos >= leftWindow; pos--) {
      System.arraycopy(bestCurrentProducts, 0, lastProducts, 0, lastProducts.length);
      Arrays.fill(bestCurrentProducts, -1);
      for (int k1 = 0; k1 < lastProducts.length; k1++) {
        bestCurrentProducts[k1] = trace[pos + 1][lastProducts[k1]][whichDerivation[k1]][0];
        whichDerivation[k1] = trace[pos + 1][lastProducts[k1]][whichDerivation[k1]][1];
        kBest[k1][pos - leftWindow] = tags[pos - leftWindow][bestCurrentProducts[k1] / (productSizes[pos] / tagNum[pos - leftWindow])];
      }
    }

    ClassicCounter<int[]> kBestWithScores = new ClassicCounter<int[]>();
    for (int i = 0; i < kBest.length; i++) {
      if(bestFinalScores[i] > Double.NEGATIVE_INFINITY) {
        kBestWithScores.setCount(kBest[i], bestFinalScores[i]);
        //System.err.println(bestFinalScores[i]+"\t"+Arrays.toString(kBest[i]));
      }
    }

    return kBestWithScores;
  }

}
