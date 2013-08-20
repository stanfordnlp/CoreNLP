package edu.stanford.nlp.sequences;

import java.util.Arrays;

/**
 * An class capable of computing the best sequence given a SequenceModel.
 * @author Dan Klein
 * @author Teg Grenager (grenager@stanford.edu)
 */
public class PerStateBestSequenceFinder implements BestSequenceFinder {

  /**
   * A class for testing.
   */
  private static class TestSequenceModel implements SequenceModel {

    private int[] correctTags = {0, 0, 1, 2, 3, 4, 5, 6, 7, 6, 5, 4, 3, 2, 1, 0, 0};
    private int[] allTags = {1, 2, 3, 4, 5, 6, 7, 8, 9};
    private int[] midTags = {0, 1, 2, 3};
    private int[] nullTags = {0};

    public int length() {
      return correctTags.length - leftWindow() - rightWindow();
    }

    public int leftWindow() {
      return 2;
    }

    public int rightWindow() {
      return 2;
    }

    public int[] getPossibleValues(int pos) {
      if (pos < leftWindow() || pos >= leftWindow() + length()) {
        return nullTags;
      }
      if (correctTags[pos] < 4) {
        return midTags;
      }
      return allTags;
    }

    public double scoreOf(int[] tags, int pos) {
      //System.out.println("Was asked: "+arrayToString(tags)+" at "+pos);
      boolean match = true;
      for (int loc = pos - leftWindow(); loc <= pos + rightWindow(); loc++) {
        if (tags[loc] != correctTags[loc]) {
          match = false;
        }
      }
      if (match) {
        return pos;
      }
      return 0;
    }

    public double scoreOf(int[] sequence) {
      throw new UnsupportedOperationException();
    }

    public double[] scoresOf(int[] tags, int pos) {
      int[] tagsAtPos = getPossibleValues(pos);
      double[] scores = new double[tagsAtPos.length];
      for (int t = 0; t < tagsAtPos.length; t++) {
        tags[pos] = tagsAtPos[t];
        scores[t] = scoreOf(tags, pos);
      }
      return scores;
    }

  }

  private static String arrayToString(int[] x) {
    StringBuffer sb = new StringBuffer("(");
    for (int j = 0; j < x.length; j++) {
      sb.append(x[j]);
      if (j != x.length - 1) {
        sb.append(", ");
      }
    }
    sb.append(")");
    return sb.toString();
  }

  public static void main(String[] args) {
    BestSequenceFinder ti = new ExactBestSequenceFinder();
    SequenceModel ts = new TestSequenceModel();
    int[] bestTags = ti.bestSequence(ts);
    System.out.println("The best sequence is .... " + arrayToString(bestTags));
  }

  public int[] bestSequence(SequenceModel ts) {

    // Set up tag options
    int length = ts.length();
    int leftWindow = ts.leftWindow();
    int rightWindow = ts.rightWindow();
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
      windowScore[pos] = new double[productSizes[pos]];
      Arrays.fill(tempTags, tags[0][0]);
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
    for (int last = padLength - 1; last >= length - 1; last--) {
      tempTags[last] = tags[last][lastProduct % tagNum[last]];
      lastProduct /= tagNum[last];
    }
    for (int pos = leftWindow + length - 2; pos >= leftWindow; pos--) {
      int bestNextProduct = bestCurrentProduct;
      bestCurrentProduct = trace[pos + 1][bestNextProduct];
      tempTags[pos - leftWindow] = tags[pos - leftWindow][bestCurrentProduct / (productSizes[pos] / tagNum[pos - leftWindow])];
    }
    return tempTags;
  }

  /*
  public int[] bestSequenceOld(TagScorer ts) {

    // Set up tag options
    int length = ts.length();
    int leftWindow = ts.leftWindow();
    int rightWindow = ts.rightWindow();
    int padLength = length+leftWindow+rightWindow;
    int[][] tags = new int[padLength][];
    int[] tagNum = new int[padLength];
    for (int pos = 0; pos < padLength; pos++) {
      tags[pos] = ts.tagsAt(pos);
      tagNum[pos] = tags[pos].length;
    }

    int[] tempTags = new int[padLength];

    // Set up product space sizes
    int[] productSizes = new int[padLength];

    int curProduct = 1;
    for (int i=0; i<leftWindow+rightWindow; i++)
      curProduct *= tagNum[i];
    for (int pos = leftWindow+rightWindow; pos < padLength; pos++) {
      if (pos > leftWindow+rightWindow)
	curProduct /= tagNum[pos-leftWindow-rightWindow-1]; // shift off
      curProduct *= tagNum[pos]; // shift on
      productSizes[pos-rightWindow] = curProduct;
    }

    // Score all of each window's options
    double[][] windowScore = new double[padLength][];
    for (int pos=leftWindow; pos<leftWindow+length; pos++) {
      windowScore[pos] = new double[productSizes[pos]];
      Arrays.fill(tempTags,tags[0][0]);
      for (int product=0; product<productSizes[pos]; product++) {
	int p = product;
	for (int curPos = pos+rightWindow; curPos >= pos-leftWindow; curPos--) {
	  tempTags[curPos] = tags[curPos][p % tagNum[curPos]];
	  p /= tagNum[curPos];
	}
	windowScore[pos][product] = ts.scoreOf(tempTags, pos);
      }
    }
    

    // Set up score and backtrace arrays
    double[][] score = new double[padLength][];
    int[][] trace = new int[padLength][];
    for (int pos=0; pos<padLength; pos++) {
      score[pos] = new double[productSizes[pos]];
      trace[pos] = new int[productSizes[pos]];
    }

    // Do forward Viterbi algorithm

    // loop over the classification spot
    //System.err.println();
    for (int pos=leftWindow; pos<length+leftWindow; pos++) {
      //System.err.print(".");
      // loop over window product types
      for (int product=0; product<productSizes[pos]; product++) {
	// check for initial spot
	if (pos==leftWindow) {
	  // no predecessor type
	  score[pos][product] = windowScore[pos][product];
	  trace[pos][product] = -1;
	} else {
	  // loop over possible predecessor types
	  score[pos][product] = Double.NEGATIVE_INFINITY;
	  trace[pos][product] = -1;
	  int sharedProduct = product / tagNum[pos+rightWindow];
	  int factor = productSizes[pos] / tagNum[pos+rightWindow];
	  for (int newTagNum=0; newTagNum<tagNum[pos-leftWindow-1]; newTagNum++) {
	    int predProduct = newTagNum*factor+sharedProduct;
	    double predScore = score[pos-1][predProduct]+windowScore[pos][product];
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
    for (int product=0; product<productSizes[leftWindow+length-1]; product++) {
      if (score[leftWindow+length-1][product] > bestFinalScore) {
	bestCurrentProduct = product;
	bestFinalScore = score[leftWindow+length-1][product];
      }
    }
    int lastProduct = bestCurrentProduct;
    for (int last=padLength-1; last>=length-1; last--) {
      tempTags[last] = tags[last][lastProduct % tagNum[last]];
      lastProduct /= tagNum[last];
    }
    for (int pos=leftWindow+length-2; pos>=leftWindow; pos--) {
      int bestNextProduct = bestCurrentProduct;
      bestCurrentProduct = trace[pos+1][bestNextProduct];
      tempTags[pos-leftWindow] = tags[pos-leftWindow][bestCurrentProduct / (productSizes[pos]/tagNum[pos-leftWindow])];
    }
    return tempTags;
  }
  */
}
