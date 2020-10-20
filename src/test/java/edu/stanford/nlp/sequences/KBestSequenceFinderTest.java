package edu.stanford.nlp.sequences;

import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.stats.Counters;
import junit.framework.TestCase;

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

/**
 * @author Christopher Manning
 */
public class KBestSequenceFinderTest extends TestCase {

  private static final boolean DEBUG = false;

  private static final int K2NR = 20;

  private static String[] test2nrAnswers = {
          "[0, 0, 7, 8, 9]",
          "[0, 0, 6, 7, 8]",
          "[0, 0, 5, 6, 7]",
          "[0, 0, 4, 5, 6]",
          "[0, 0, 8, 9, 1]",
          "[0, 0, 1, 8, 9]",
          "[0, 0, 2, 8, 9]",
          "[0, 0, 8, 9, 2]",
          "[0, 0, 8, 9, 3]",
          "[0, 0, 3, 8, 9]",
          "[0, 0, 4, 8, 9]",
          "[0, 0, 8, 9, 4]",
          "[0, 0, 3, 4, 5]",
          "[0, 0, 8, 9, 5]",
          "[0, 0, 5, 8, 9]",
          "[0, 0, 6, 8, 9]",
          "[0, 0, 8, 9, 6]",
          "[0, 0, 8, 9, 7]",
          "[0, 0, 8, 8, 9]",
          "[0, 0, 8, 9, 8]",
  };

  private static double[] test2nrScores = {
          17.142857142857142,
          15.166666666666668,
          13.2,
          11.25,
          10.125,
          10.125,
          9.625,
          9.625,
          9.458333333333334,
          9.458333333333334,
          9.375,
          9.375,
          9.333333333333332,
          9.325,
          9.325,
          9.291666666666666,
          9.291666666666666,
          9.267857142857142,
          9.25,
          9.25,
  };

  public void testPerStateBestSequenceFinder() {
    KBestSequenceFinder bsf = new KBestSequenceFinder();
    BestSequenceFinderTest.TestSequenceModel tsm2nr = new BestSequenceFinderTest.TestSequenceModel2nr();
    runSequencesFinder(tsm2nr, bsf);
    BestSequenceFinderTest.runPossibleValuesChecker(tsm2nr, bsf);
  }

  public static void runSequencesFinder(BestSequenceFinderTest.TestSequenceModel tsm,
                                       KBestSequenceFinder sf) {
    Counter<int[]>  bestLabelsCounter = sf.kBestSequences(tsm, K2NR);
    List<int[]> topValues = Counters.toSortedList(bestLabelsCounter);
    Iterator<int[]> iter = topValues.iterator();
    for (int i = 0; i < K2NR; i++) {
      int[] sequence = iter.next();
      String strSequence = Arrays.toString(sequence);
      double score = bestLabelsCounter.getCount(sequence);
      if (DEBUG) {
        System.err.println((i+1) + " best sequence: " + strSequence + "; score: " + score);
        System.err.println("    vs. correct: " + test2nrAnswers[i]);
      }
      // Deal with ties in the scoring ... only tied pairs handled.
      boolean found = false;
      if (strSequence.equals(test2nrAnswers[i])) {
        found = true;
      } else if (i > 0 && Math.abs(score - test2nrScores[i-1]) < 1e-8 &&
              strSequence.equals(test2nrAnswers[i-1])) {
        found = true;
      } else if (i+1 < test2nrScores.length && Math.abs(score - test2nrScores[i+1]) < 1e-8 &&
              strSequence.equals(test2nrAnswers[i+1])) {
        found = true;
      }
      assertTrue("Best sequence is wrong. Correct: " + test2nrAnswers[i] +
                    ", found: " + strSequence, found);
      assertEquals("Best sequence score is wrong.", test2nrScores[i], score, 1e-8);
    }
  }

}