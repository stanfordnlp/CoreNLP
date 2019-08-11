package edu.stanford.nlp.sequences; 

import edu.stanford.nlp.util.Pair;
import edu.stanford.nlp.util.RuntimeInterruptedException;
import edu.stanford.nlp.util.logging.Redwood;

import java.util.Arrays;


/**
 * A class capable of computing the best sequence given a SequenceModel.
 * Uses the Viterbi algorithm.
 *
 * @author Dan Klein
 * @author Teg Grenager (grenager@stanford.edu)
 * @author kno10
 */
public class ExactBestSequenceFinder implements BestSequenceFinder  {

  /** A logger for this class */
  private static final Redwood.RedwoodChannels log = Redwood.channels(ExactBestSequenceFinder.class);

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
    final int length = ts.length();
    final int leftWindow = ts.leftWindow();
    final int rightWindow = ts.rightWindow();
    final int padLength = length + leftWindow + rightWindow;
    if (linearConstraints != null && linearConstraints.length != padLength)
      throw new RuntimeException("linearConstraints.length (" +  linearConstraints.length + ") does not match padLength (" + padLength + ") of SequenceModel" + ", length=="+length+", leftW="+leftWindow+", rightW="+rightWindow);
    int[][] tags = new int[padLength][];
    int[] tagNum = new int[padLength];
    if (DEBUG) { log.info("Doing bestSequence length " + length + "; leftWin " + leftWindow + "; rightWin " + rightWindow + "; padLength " + padLength); }
    for (int pos = 0; pos < padLength; pos++) {
      // potentially constrain values considered in inference (e.g., to only observed tags for a word if word is common)
      tags[pos] = ts.getPossibleValues(pos);
      tagNum[pos] = tags[pos].length;
      if (DEBUG) { log.info("There are " + tagNum[pos] + " values at position " + pos + ": " + Arrays.toString(tags[pos])); }
    }

    // Set up product space sizes
    int[] productSizes = initProductSizes(ts, tagNum, new int[padLength]);

    // Score all of each window's options
    int[] tempTags = new int[padLength];
    double[][] windowScore = computeWindowScore(ts, tags, tagNum, tempTags, productSizes);

    // Set up score and backtrace arrays
    double[][] score = new double[padLength][];
    int[][] trace = new int[padLength][];
    for (int pos = 0; pos < padLength; pos++) {
      score[pos] = new double[productSizes[pos]];
      trace[pos] = new int[productSizes[pos]];
    }

    // Do forward Viterbi algorithm
    forwardViterbi(leftWindow, rightWindow, length, linearConstraints, tagNum, productSizes, windowScore, score, trace);

    // Project the actual tag sequence
    double bestFinalScore = Double.NEGATIVE_INFINITY;
    int bestCurrentProduct = -1;
    int end = leftWindow + length - 1;
    for (int product = 0, productEnd = productSizes[end]; product < productEnd; product++) {
      final double s = score[end][product];
      if (s > bestFinalScore) {
        bestCurrentProduct = product;
        bestFinalScore = s;
      }
    }
    int lastProduct = bestCurrentProduct;
    for (int last = padLength - 1; last >= length - 1 && last >= 0; last--) {
      final int tagNum_last = tagNum[last], tempProduct = lastProduct;
      lastProduct /= tagNum_last;
      tempTags[last] = tags[last][tempProduct - lastProduct * tagNum_last];
    }
    for (int pos = leftWindow + length - 2; pos >= leftWindow; pos--) {
      final int bestNextProduct = bestCurrentProduct;
      final int prevPos = pos - leftWindow;
      bestCurrentProduct = trace[pos + 1][bestNextProduct];
      tempTags[prevPos] = tags[prevPos][bestCurrentProduct / (productSizes[pos] / tagNum[prevPos])];
    }
    return new Pair<>(tempTags, bestFinalScore);
  }

  @SuppressWarnings("Convert2streamapi")
  private static int[] initProductSizes(final SequenceModel ts, int[] tagNum, int[] productSizes) {
    final int leftWindow = ts.leftWindow();
    final int rightWindow = ts.rightWindow();
    final int window = leftWindow + rightWindow;
    final int padLength = productSizes.length;

    // Skip
    int curProduct = 1;
    for (int i = 0; i < window; i++) {
      curProduct *= tagNum[i];
    }
    // First entry
    if (window < padLength) {
      curProduct *= tagNum[window]; // shift on
      productSizes[leftWindow] = curProduct;
    }
    // Remaining entries
    for (int pos = window + 1; pos < padLength; pos++) {
      curProduct /= tagNum[pos - window - 1]; // shift off
      curProduct *= tagNum[pos]; // shift on
      productSizes[pos - rightWindow] = curProduct;
    }
    return productSizes;
  }

  private static double[][] computeWindowScore(SequenceModel ts, int[][] tags, int[] tagNum, int[] tempTags, int[] productSizes) {
    final int length = ts.length();
    final int leftWindow = ts.leftWindow();
    final int rightWindow = ts.rightWindow();
    final int padLength = length + leftWindow + rightWindow;
    double[][] windowScore = new double[padLength][];
    for (int pos = leftWindow; pos < leftWindow + length; pos++) {
      if (Thread.interrupted()) {  // Allow interrupting
        throw new RuntimeInterruptedException();
      }
      // Local constants, to avoid repeated array access.
      final int tagNum_pos = tagNum[pos];
      final int productSizes_pos = productSizes[pos];
      final double[] windowScore_pos = windowScore[pos] = new double[productSizes_pos];
      if (DEBUG) { log.info("scoring word " + pos + " / " + (leftWindow + length) + ", productSizes =  " + productSizes_pos + ", tagNum = " + tagNum_pos + "..."); }
      Arrays.fill(tempTags, tags[0][0]);
      if (DEBUG) { log.info("windowScore[" + pos + "] has size (productSizes[pos]) " + windowScore_pos.length); }

      for (int product = 0; product < productSizes_pos; product++) {
        int p = product;
        int shift = 1;
        for (int curPos = pos + rightWindow, endCurPos = pos - leftWindow; curPos >= endCurPos; curPos--) {
          final int tn = tagNum[curPos], oldp = p;
          p /= tn;
          tempTags[curPos] = tags[curPos][oldp - p * tn];
          if (curPos > pos) {
            shift *= tn;
          }
        }

        // Here now you get ts.scoresOf() for all classifications at a position at once, whereas the old code called ts.scoreOf() on each item.
        // CDM May 2007: The way this is done gives incorrect results if there are repeated values in the values of ts.getPossibleValues(pos) -- in particular if the first value of the array is repeated later.  I tried replacing it with the modulo version, but that only worked for left-to-right, not bidirectional inference, but I still think that if you sorted things out, you should be able to do it with modulos and the result would be conceptually simpler and robust to repeated values.  But in the meantime, I fixed the POS tagger to not give repeated values (which was a bug in the tagger).
        if (tempTags[pos] == tags[pos][0]) {
          // get all tags at once
          double[] scores = ts.scoresOf(tempTags, pos);
          if (DEBUG) {
            log.info("Matched at array index [product] " + product + "; tempTags[pos] == tags[pos][0] == " + tempTags[pos]);
            log.info("For pos " + pos + " scores.length is " + scores.length + "; tagNum[pos] = " + tagNum_pos + "; windowScore[pos].length = " + windowScore_pos.length);
            log.info("scores: " + Arrays.toString(scores));
          }
          // fill in the relevant windowScores
          for (int t = 0; t < tagNum_pos; t++) {
            if (DEBUG) { log.info("Setting value of windowScore[" + pos + "][" + product + '+' + t + '*' + shift + "] = " + scores[t]); }
            windowScore_pos[product + t * shift] = scores[t];
          }
        }
      }
    }
    return windowScore;
  }

  private static int forwardViterbiInitial(int pos, double[][] linearConstraints, int[] tagNum, int[] productSizes, double[][] windowScore, double[][] score, int[][] trace) {
    // initial spot:
    for (int product = 0, products = productSizes[pos]; product < products; product++) {
      // Local copies, to reduce array lookups.
      final double[] score_pos = score[pos];
      final int[] trace_pos = trace[pos];
      final double[] linearConstraints_pos = linearConstraints != null ? linearConstraints[pos] : null;
      final int tagNum_pos = tagNum[pos];
      final double[] windowScore_pos = windowScore[pos];
      // no predecessor type
      double score_product = windowScore_pos[product];
      if (linearConstraints_pos != null) {
        if (DEBUG) {
          if (linearConstraints_pos[product % tagNum_pos] != 0) {
            log.info("Applying linear constraints=" + linearConstraints_pos[product % tagNum_pos] + " to preScore="+ windowScore_pos[product] + " at pos="+pos+" for tag="+(product % tagNum_pos));
          }
        }
        score_product += linearConstraints_pos[product % tagNum_pos];
      }
      score_pos[product] = score_product;
      trace_pos[product] = -1;
    }
    return pos;
  }

  private static void forwardViterbi(int leftWindow, int rightWindow, int length, double[][] linearConstraints, int[] tagNum, int[] productSizes, double[][] windowScore, double[][] score, int[][] trace) {
    final int endpos = length + leftWindow;
    int pos = forwardViterbiInitial(leftWindow, linearConstraints, tagNum, productSizes, windowScore, score, trace);
    // loop over the remaining classification spots
    //log.info();
    for (pos++; pos < endpos; pos++) {
      if (Thread.interrupted()) {  // Allow interrupting
        throw new RuntimeInterruptedException();
      }
      // Local copies, to reduce array lookups.
      final double[] score_pos = score[pos], score_posm1 = score[pos - 1];
      final int[] trace_pos = trace[pos];
      final double[] linearConstraints_pos = linearConstraints != null ? linearConstraints[pos] : null;
      final int tagNum_pos = tagNum[pos];
      final int tagNumRight = tagNum[pos + rightWindow];
      final int tagNumLeft = tagNum[pos - leftWindow - 1];
      final double[] windowScore_pos = windowScore[pos];
      //log.info(".");
      final int products = productSizes[pos];
      final int factor = products / tagNumRight;
      // loop over window product types
      for (int product = 0; product < products; product++) {
        // loop over possible predecessor types
        double score_product = Double.NEGATIVE_INFINITY;
        int trace_product = -1;
        int sharedProduct = product / tagNumRight;
        final double windowProductScore = windowScore_pos[product];
        for (int newTagNum = 0; newTagNum < tagNumLeft; newTagNum++) {
          int predProduct = newTagNum * factor + sharedProduct;
          double predScore = score_posm1[predProduct] + windowProductScore;

          if (linearConstraints_pos != null) {
            if (DEBUG) {
              if (pos == 2 && linearConstraints_pos[product % tagNum_pos] != 0) {
                log.info("Applying linear constraints=" + linearConstraints_pos[product % tagNum_pos] + " to preScore="+ predScore + " at pos="+pos+" for tag="+(product % tagNum_pos));
                log.info("predScore:" + predScore + " = score["+(pos - 1)+"]["+predProduct+"]:" + score_posm1[predProduct] + " + windowScore["+pos+"]["+product+"]:" + windowProductScore);
              }
            }
            predScore += linearConstraints_pos[product % tagNum_pos];
          }

          if (predScore > score_product) {
            score_product = predScore;
            trace_product = predProduct;
          }
        }
        score_pos[product] = score_product;
        trace_pos[product] = trace_product;
      }
    }
  }

}
