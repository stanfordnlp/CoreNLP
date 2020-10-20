package edu.stanford.nlp.sequences;

import java.util.Arrays;

import junit.framework.TestCase;

/**
 * @author Christopher Manning
 */
public class BestSequenceFinderTest extends TestCase {

  private static final boolean DEBUG = false;

  public interface TestSequenceModel extends SequenceModel {

    /** Returns the best label sequence. */
    int[] correctAnswers();

    /** Returns the score for the best label sequence. */
    double bestSequenceScore();

  }

  /**
   * A class for testing best sequence finding on a SequenceModel.
   * This one isn't very tricky. It scores the correct answer with a label
   * and all other answers as 0.  So you're pretty broken if you can't
   * follow it.
   *
   * In the padding area you can only have tag 0. Otherwise, it likes the tag to match correctTags
   */
  public static class TestSequenceModel1 implements TestSequenceModel {

    private final int[] correctTags = {0, 0, 1, 2, 3, 4, 5, 6, 7, 6, 5, 4, 3, 2, 1, 0, 0};
    private final int[] allTags = {1, 2, 3, 4, 5, 6, 7, 8, 9};
    private final int[] midTags = {0, 1, 2, 3};
    private final int[] nullTags = {0};

    /** {@inheritDoc} */
    @Override
    public int length() {
      return correctTags.length - leftWindow() - rightWindow();
    }

    /** {@inheritDoc} */
    @Override
    public int leftWindow() {
      return 2;
    }

    /** {@inheritDoc} */
    @Override
    public int rightWindow() {
      return 2;
    }

    /** {@inheritDoc} */
    @Override
    public int[] getPossibleValues(int pos) {
      if (pos < leftWindow() || pos >= leftWindow() + length()) {
        return nullTags;
      }
      if (correctTags[pos] < 4) {
        return midTags;
      }
      return allTags;
    }

    /** {@inheritDoc} */
    @Override
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
      return 0.0;
    }

    /** {@inheritDoc} */
    @Override
    public double scoreOf(int[] sequence) {
      double score = 0.0;
      for (int i = leftWindow(); i < leftWindow() + length(); i++) {
        score += scoreOf(sequence, i);
      }
      return score;
    }

    /** {@inheritDoc} */
    @Override
    public double[] scoresOf(int[] tags, int pos) {
      int[] tagsAtPos = getPossibleValues(pos);
      double[] scores = new double[tagsAtPos.length];
      for (int t = 0; t < tagsAtPos.length; t++) {
        tags[pos] = tagsAtPos[t];
        scores[t] = scoreOf(tags, pos);
      }
      return scores;
    }

    /** {@inheritDoc} */
    @Override
    public int[] correctAnswers() {
      return correctTags;
    }

    /** {@inheritDoc} */
    @Override
    public double bestSequenceScore() {
      return scoreOf(correctTags);
    }

  } // end class TestSequenceModel


  /**
   * A second class for testing best sequence finding on a SequenceModel.
   * This wants 0 in padding and a maximal ascending sequence inside, so gets 7, 8, 9
   */
  public static class TestSequenceModel2 implements TestSequenceModel {

    private final int[] correctTags = {0, 0, 7, 8, 9, 0, 0};
    // private final int[] correctTags = {0, 0, 7, 8, 9, 3, 4, 5, 0, 0};
    private final int[] allTags = {1, 2, 3, 4, 5, 6, 7, 8, 9};
    private final int[] midTags = {0, 1, 2, 3, 4, 5};
    private final int[] nullTags = {0};

    /** {@inheritDoc} */
    @Override
    public int length() {
      return correctTags.length - leftWindow() - rightWindow();
    }

    /** {@inheritDoc} */
    @Override
    public int leftWindow() {
      return 2;
    }

    /** {@inheritDoc} */
    @Override
    public int rightWindow() {
      return 2;
    }

    /** {@inheritDoc} */
    @Override
    public int[] getPossibleValues(int pos) {
      if (pos < leftWindow() || pos >= leftWindow() + length()) {
        return nullTags;
      }
      if (pos < 5) {
        return allTags;
      }
      return midTags;
    }

    /** {@inheritDoc} */
    @Override
    public double scoreOf(int[] tags, int pos) {
      double score;
      if (tags[pos] > tags[pos-1] && tags[pos] <= tags[pos-1] + 1) {
//       if (tags[pos] <= tags[pos-1] + 1 && tags[pos] <= tags[pos-2] + 1) {
        score = tags[pos];
      } else {
        score = tags[pos] == 0 ? 0.0: 1.0 / tags[pos];
      }
      // System.out.printf("Score of label %d for position %d in %s is %.2f%n",
      //     tags[pos], pos, Arrays.toString(tags), score);
      return score;
    }

    /** {@inheritDoc} */
    @Override
    public double scoreOf(int[] sequence) {
      double score = 0.0;
      for (int i = leftWindow(); i < leftWindow() + length(); i++) {
        score += scoreOf(sequence, i);
      }
      return score;
    }

    /** {@inheritDoc} */
    @Override
    public double[] scoresOf(int[] tags, int pos) {
      int[] tagsAtPos = getPossibleValues(pos);
      double[] scores = new double[tagsAtPos.length];
      for (int t = 0; t < tagsAtPos.length; t++) {
        tags[pos] = tagsAtPos[t];
        scores[t] = scoreOf(tags, pos);
      }
      return scores;
    }

    /** {@inheritDoc} */
    @Override
    public int[] correctAnswers() {
      return correctTags;
    }

    /** {@inheritDoc} */
    @Override
    public double bestSequenceScore() {
      return scoreOf(correctTags);
    }

  } // end class TestSequenceModel2


  /**
   * A variant of second class for testing best sequence finding on a SequenceModel.
   * This version has rightWindow == 0, which is sometimes needed.
   * This wants 0 in padding and a maximal ascending sequence inside, so gets 7, 8, 9
   */
  public static class TestSequenceModel2nr implements TestSequenceModel {

    private final int[] correctTags = {0, 0, 7, 8, 9 };
    private final int[] allTags = {1, 2, 3, 4, 5, 6, 7, 8, 9};
    private final int[] midTags = {0, 1, 2, 3, 4, 5};
    private final int[] nullTags = {0};

    /** {@inheritDoc} */
    @Override
    public int length() {
      return correctTags.length - leftWindow() - rightWindow();
    }

    /** {@inheritDoc} */
    @Override
    public int leftWindow() {
      return 2;
    }

    /** {@inheritDoc} */
    @Override
    public int rightWindow() {
      return 0;
    }

    /** {@inheritDoc} */
    @Override
    public int[] getPossibleValues(int pos) {
      if (pos < leftWindow() || pos >= leftWindow() + length()) {
        return nullTags;
      }
      if (pos < 5) {
        return allTags;
      }
      return midTags;
    }

    /** {@inheritDoc} */
    @Override
    public double scoreOf(int[] tags, int pos) {
      double score;
      if (tags[pos] > tags[pos-1] && tags[pos] <= tags[pos-1] + 1) {
//       if (tags[pos] <= tags[pos-1] + 1 && tags[pos] <= tags[pos-2] + 1) {
        score = tags[pos];
      } else {
        score = tags[pos] == 0 ? 0.0: 1.0 / tags[pos];
      }
      // System.out.printf("Score of label %d for position %d in %s is %.2f%n",
      //     tags[pos], pos, Arrays.toString(tags), score);
      return score;
    }

    /** {@inheritDoc} */
    @Override
    public double scoreOf(int[] sequence) {
      double score = 0.0;
      for (int i = leftWindow(); i < leftWindow() + length(); i++) {
        score += scoreOf(sequence, i);
      }
      return score;
    }

    /** {@inheritDoc} */
    @Override
    public double[] scoresOf(int[] tags, int pos) {
      int[] tagsAtPos = getPossibleValues(pos);
      double[] scores = new double[tagsAtPos.length];
      for (int t = 0; t < tagsAtPos.length; t++) {
        tags[pos] = tagsAtPos[t];
        scores[t] = scoreOf(tags, pos);
      }
      return scores;
    }

    /** {@inheritDoc} */
    @Override
    public int[] correctAnswers() {
      return correctTags;
    }

    /** {@inheritDoc} */
    @Override
    public double bestSequenceScore() {
      return scoreOf(correctTags);
    }

  } // end class TestSequenceModel2nr


  /**
   * A third class for testing best sequence finding on a SequenceModel.
   */
  public static class TestSequenceModel3 implements TestSequenceModel {

    private final int[] correctTags = {0, 1, 1, 1, 1, 1, 2, 2, 1, 2, 2, 1, 1, 1, 0};
    private final int[] data =        {0, 5, 3, 7, 9, 4, 7, 8, 3, 7, 8, 3, 7, 3, 0};
    private final int[] allTags = {0, 1, 2};
    private final int[] nullTags = {0};

    /** {@inheritDoc} */
    @Override
    public int length() {
      return correctTags.length - leftWindow() - rightWindow();
    }

    /** {@inheritDoc} */
    @Override
    public int leftWindow() {
      return 1;
    }

    /** {@inheritDoc} */
    @Override
    public int rightWindow() {
      return 1;
    }

    /** {@inheritDoc} */
    @Override
    public int[] getPossibleValues(int pos) {
      if (pos < leftWindow() || pos >= leftWindow() + length()) {
        return nullTags;
      }
      return allTags;
    }

    /** {@inheritDoc} */
    @Override
    public double scoreOf(int[] tags, int pos) {
      double score;
      if (data[pos] == 7 && tags[pos] == 2 && tags[pos + 1] == 2) {
        score = 1.0;
      } else if (data[pos] == 8 && tags[pos] == 2) {
        score = 0.5;
      } else if (tags[pos] == 1) {
        score = 0.1;
      } else if (tags[pos] == 2) {
        score = -5.0;
      } else {
        score = 0.0;
      }
      // System.out.printf("Score of label %d for position %d in %s is %.2f%n",
      //     tags[pos], pos, Arrays.toString(tags), score);
      return score;
    }

    /** {@inheritDoc} */
    @Override
    public double scoreOf(int[] sequence) {
      double score = 0.0;
      for (int i = leftWindow(); i < leftWindow() + length(); i++) {
        score += scoreOf(sequence, i);
      }
      return score;
    }

    /** {@inheritDoc} */
    @Override
    public double[] scoresOf(int[] tags, int pos) {
      int[] tagsAtPos = getPossibleValues(pos);
      double[] scores = new double[tagsAtPos.length];
      for (int t = 0; t < tagsAtPos.length; t++) {
        tags[pos] = tagsAtPos[t];
        scores[t] = scoreOf(tags, pos);
      }
      return scores;
    }

    /** {@inheritDoc} */
    @Override
    public int[] correctAnswers() {
      return correctTags;
    }

    /** {@inheritDoc} */
    @Override
    public double bestSequenceScore() {
      return scoreOf(correctTags);
    }

  } // end class TestSequenceModel2


  public static void runSequenceFinder(TestSequenceModel tsm,
                                       BestSequenceFinder sf) {
    int[] bestLabels = sf.bestSequence(tsm);
    if (DEBUG) {
      System.err.println("Best sequence: " + Arrays.toString(bestLabels));
      System.err.println("  vs. correct: " + Arrays.toString(tsm.correctAnswers()));
    }
    assertTrue("Best sequence is wrong. Correct: " + Arrays.toString(tsm.correctAnswers()) +
            ", found: " + Arrays.toString(bestLabels),
            Arrays.equals(tsm.correctAnswers(), bestLabels));
    assertEquals("Best sequence score is wrong.", tsm.bestSequenceScore(), tsm.scoreOf(bestLabels));
  }

  public static void runPossibleValuesChecker(TestSequenceModel tsm,
                                       BestSequenceFinder sf) {
    int[] bestLabels = sf.bestSequence(tsm);
    // System.out.println("The best sequence is ... " + Arrays.toString(bestLabels));
    for (int i = 0; i < bestLabels.length; i++) {
      int[] possibleValues = tsm.getPossibleValues(i);
      boolean found = false;
      for (int possible : possibleValues) {
        if (bestLabels[i] == possible) {
          found = true;
        }
      }
      if ( ! found) {
        fail("Returned impossible label " + bestLabels[i] + " for position " + i);
      }
    }
  }

  public void testExactBestSequenceFinder() {
    BestSequenceFinder bsf = new ExactBestSequenceFinder();
    TestSequenceModel tsm = new TestSequenceModel1();
    runSequenceFinder(tsm, bsf);
    runPossibleValuesChecker(tsm, bsf);
    TestSequenceModel tsm2 = new TestSequenceModel2();
    runSequenceFinder(tsm2, bsf);
    runPossibleValuesChecker(tsm2, bsf);
    TestSequenceModel tsm2nr = new TestSequenceModel2nr();
    runSequenceFinder(tsm2nr, bsf);
    runPossibleValuesChecker(tsm2nr, bsf);
    TestSequenceModel tsm3 = new TestSequenceModel3();
    runSequenceFinder(tsm3, bsf);
    runPossibleValuesChecker(tsm3, bsf);
  }

  // This doesn't seem to work either.  Dodgy stuff in our BestSequenceFinder's
  /*
  public void testKBestSequenceFinder() {
    BestSequenceFinder bsf = new KBestSequenceFinder();
    TestSequenceModel tsm = new TestSequenceModel1();
    runSequenceFinder(tsm, bsf);
    TestSequenceModel tsm2 = new TestSequenceModel2();
    runSequenceFinder(tsm2, bsf);
  }
  */

  public void testBeamBestSequenceFinder() {
    BestSequenceFinder bsf = new BeamBestSequenceFinder(5, true);
    TestSequenceModel tsm = new TestSequenceModel1();
    runSequenceFinder(tsm, bsf);
    runPossibleValuesChecker(tsm, bsf);

    // This one doesn't seem to work with any reasonable parameters.
    // And what is returned is non-deterministic. Heap of crap.
    // BestSequenceFinder bsf2 = new BeamBestSequenceFinder(5000000, false, false);
    // TestSequenceModel tsm2 = new TestSequenceModel2();
    // runSequenceFinder(tsm2, bsf);
  }

  /** For a sequence sampler, we just check that the returned values are
   *  valid values. We don't test the sampling distribution.
   */
  public void testSequenceSampler() {
    BestSequenceFinder bsf = new SequenceSampler();
    TestSequenceModel tsm = new TestSequenceModel1();
    runPossibleValuesChecker(tsm, bsf);
    TestSequenceModel tsm2 = new TestSequenceModel2();
    runPossibleValuesChecker(tsm2, bsf);
    TestSequenceModel tsm3 = new TestSequenceModel3();
    runPossibleValuesChecker(tsm3, bsf);
  }


}