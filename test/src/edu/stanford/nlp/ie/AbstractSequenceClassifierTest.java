package edu.stanford.nlp.ie;

import junit.framework.TestCase;

import java.util.List;
import java.util.ArrayList;

import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counter;

/**
 * Test some of the static methods in AbstractSequenceClassifier.
 * In particular, this tests the IOB encoding results counting.
 *
 * @author John Bauer
 */
public class AbstractSequenceClassifierTest extends TestCase {

  static final String BG = "O";

  static final String[][] labelsIOB2 = {
    {    BG,    BG,    BG,    BG,    BG,    BG,    BG,    BG,    BG,    BG },  //0
    {    BG,    BG,    BG,    BG, "I-A",    BG,    BG,    BG,    BG,    BG },
    {    BG,    BG,    BG,    BG, "I-A", "I-A",    BG,    BG,    BG,    BG },
    {    BG,    BG,    BG, "I-A", "I-A",    BG,    BG,    BG,    BG,    BG },  //3
    {    BG,    BG,    BG,    BG, "I-A", "I-B",    BG,    BG,    BG,    BG },
    {    BG,    BG,    BG,    BG, "I-A", "B-A",    BG,    BG,    BG,    BG },
    {    BG,    BG,    BG,    BG,    BG,    BG,    BG,    BG,    BG, "I-A" }   //6
  };

  private static void runIOB2ResultsTest(String[] gold, String[] guess, double tp, double fp, double fn) {
    List<CoreLabel> sentence = new ArrayList<CoreLabel>();
    assertEquals("Cannot run test on lists of different length", gold.length, guess.length);
    for (int i = 0; i < gold.length; ++i) {
      CoreLabel word = new CoreLabel();
      word.set(CoreAnnotations.GoldAnswerAnnotation.class, gold[i]);
      word.set(CoreAnnotations.AnswerAnnotation.class, guess[i]);
      sentence.add(word);
    }
    Counter<String> entityTP = new ClassicCounter<String>();
    Counter<String> entityFP = new ClassicCounter<String>();
    Counter<String> entityFN = new ClassicCounter<String>();
    AbstractSequenceClassifier.countResultsIOB2(sentence, entityTP, entityFP, entityFN, BG);
    assertEquals(tp, entityTP.totalCount(), 0.0001);
    assertEquals(fp, entityFP.totalCount(), 0.0001);
    assertEquals(fn, entityFN.totalCount(), 0.0001);
  }

  public void testIOB2Results() {
    runIOB2ResultsTest(labelsIOB2[0], labelsIOB2[0], 0, 0, 0);

    runIOB2ResultsTest(labelsIOB2[0], labelsIOB2[1], 0, 1, 0);
    runIOB2ResultsTest(labelsIOB2[1], labelsIOB2[0], 0, 0, 1);
    runIOB2ResultsTest(labelsIOB2[1], labelsIOB2[1], 1, 0, 0);

    runIOB2ResultsTest(labelsIOB2[0], labelsIOB2[2], 0, 1, 0);
    runIOB2ResultsTest(labelsIOB2[2], labelsIOB2[0], 0, 0, 1);
    runIOB2ResultsTest(labelsIOB2[1], labelsIOB2[2], 0, 1, 1);
    runIOB2ResultsTest(labelsIOB2[2], labelsIOB2[1], 0, 1, 1);
    runIOB2ResultsTest(labelsIOB2[2], labelsIOB2[2], 1, 0, 0);

    runIOB2ResultsTest(labelsIOB2[0], labelsIOB2[3], 0, 1, 0);
    runIOB2ResultsTest(labelsIOB2[3], labelsIOB2[0], 0, 0, 1);
    runIOB2ResultsTest(labelsIOB2[1], labelsIOB2[3], 0, 1, 1);
    runIOB2ResultsTest(labelsIOB2[3], labelsIOB2[1], 0, 1, 1);
    runIOB2ResultsTest(labelsIOB2[2], labelsIOB2[3], 0, 1, 1);
    runIOB2ResultsTest(labelsIOB2[3], labelsIOB2[2], 0, 1, 1);
    runIOB2ResultsTest(labelsIOB2[3], labelsIOB2[3], 1, 0, 0);

    runIOB2ResultsTest(labelsIOB2[0], labelsIOB2[4], 0, 2, 0);
    runIOB2ResultsTest(labelsIOB2[4], labelsIOB2[0], 0, 0, 2);
    runIOB2ResultsTest(labelsIOB2[1], labelsIOB2[4], 1, 1, 0);
    runIOB2ResultsTest(labelsIOB2[4], labelsIOB2[1], 1, 0, 1);
    runIOB2ResultsTest(labelsIOB2[2], labelsIOB2[4], 0, 2, 1);
    runIOB2ResultsTest(labelsIOB2[4], labelsIOB2[2], 0, 1, 2);
    runIOB2ResultsTest(labelsIOB2[3], labelsIOB2[4], 0, 2, 1);
    runIOB2ResultsTest(labelsIOB2[4], labelsIOB2[3], 0, 1, 2);
    runIOB2ResultsTest(labelsIOB2[4], labelsIOB2[4], 2, 0, 0);

    runIOB2ResultsTest(labelsIOB2[0], labelsIOB2[5], 0, 2, 0);
    runIOB2ResultsTest(labelsIOB2[5], labelsIOB2[0], 0, 0, 2);
    runIOB2ResultsTest(labelsIOB2[1], labelsIOB2[5], 1, 1, 0);
    runIOB2ResultsTest(labelsIOB2[5], labelsIOB2[1], 1, 0, 1);
    runIOB2ResultsTest(labelsIOB2[2], labelsIOB2[5], 0, 2, 1);
    runIOB2ResultsTest(labelsIOB2[5], labelsIOB2[2], 0, 1, 2);
    runIOB2ResultsTest(labelsIOB2[3], labelsIOB2[5], 0, 2, 1);
    runIOB2ResultsTest(labelsIOB2[5], labelsIOB2[3], 0, 1, 2);
    runIOB2ResultsTest(labelsIOB2[4], labelsIOB2[5], 1, 1, 1);
    runIOB2ResultsTest(labelsIOB2[5], labelsIOB2[4], 1, 1, 1);
    runIOB2ResultsTest(labelsIOB2[5], labelsIOB2[5], 2, 0, 0);

    runIOB2ResultsTest(labelsIOB2[0], labelsIOB2[6], 0, 1, 0);
    runIOB2ResultsTest(labelsIOB2[6], labelsIOB2[0], 0, 0, 1);
    runIOB2ResultsTest(labelsIOB2[1], labelsIOB2[6], 0, 1, 1);
    runIOB2ResultsTest(labelsIOB2[6], labelsIOB2[1], 0, 1, 1);
    runIOB2ResultsTest(labelsIOB2[2], labelsIOB2[6], 0, 1, 1);
    runIOB2ResultsTest(labelsIOB2[6], labelsIOB2[2], 0, 1, 1);
    runIOB2ResultsTest(labelsIOB2[3], labelsIOB2[6], 0, 1, 1);
    runIOB2ResultsTest(labelsIOB2[6], labelsIOB2[3], 0, 1, 1);
    runIOB2ResultsTest(labelsIOB2[4], labelsIOB2[6], 0, 1, 2);
    runIOB2ResultsTest(labelsIOB2[6], labelsIOB2[4], 0, 2, 1);
    runIOB2ResultsTest(labelsIOB2[5], labelsIOB2[6], 0, 1, 2);
    runIOB2ResultsTest(labelsIOB2[6], labelsIOB2[5], 0, 2, 1);
    runIOB2ResultsTest(labelsIOB2[6], labelsIOB2[6], 1, 0, 0);
  }

  // TODO: add more IOB tests
  static final String[][] labelsIOB = {
    {    BG,    BG,    BG,    BG,    BG,    BG,    BG,    BG,    BG,    BG },  //0
    {    BG,    BG,    BG,    BG, "B-A",    BG,    BG,    BG,    BG,    BG },
    {    BG,    BG,    BG,    BG, "B-A", "I-A",    BG,    BG,    BG,    BG },
    {    BG,    BG,    BG, "B-A", "I-A",    BG,    BG,    BG,    BG,    BG }
  };

  public static void runIOBResultsTest(String[] gold, String[] guess, double tp, double fp, double fn) {
    List<CoreLabel> sentence = new ArrayList<CoreLabel>();
    assertEquals("Cannot run test on lists of different length", gold.length, guess.length);
    for (int i = 0; i < gold.length; ++i) {
      CoreLabel word = new CoreLabel();
      word.set(CoreAnnotations.GoldAnswerAnnotation.class, gold[i]);
      word.set(CoreAnnotations.AnswerAnnotation.class, guess[i]);
      sentence.add(word);
    }
    Counter<String> entityTP = new ClassicCounter<String>();
    Counter<String> entityFP = new ClassicCounter<String>();
    Counter<String> entityFN = new ClassicCounter<String>();
    AbstractSequenceClassifier.countResultsIOB(sentence, entityTP, entityFP, entityFN, BG);
    assertEquals(tp, entityTP.totalCount(), 0.0001);
    assertEquals(fp, entityFP.totalCount(), 0.0001);
    assertEquals(fn, entityFN.totalCount(), 0.0001);
  }

  public void testIOBResults() {
    runIOBResultsTest(labelsIOB[0], labelsIOB[0], 0, 0, 0);

    runIOBResultsTest(labelsIOB[0], labelsIOB[1], 0, 1, 0);
    runIOBResultsTest(labelsIOB[1], labelsIOB[0], 0, 0, 1);
    runIOBResultsTest(labelsIOB[1], labelsIOB[1], 1, 0, 0);

    runIOBResultsTest(labelsIOB[0], labelsIOB[2], 0, 1, 0);
    runIOBResultsTest(labelsIOB[2], labelsIOB[0], 0, 0, 1);
    runIOBResultsTest(labelsIOB[2], labelsIOB[2], 1, 0, 0);

    runIOBResultsTest(labelsIOB[0], labelsIOB[3], 0, 1, 0);
    runIOBResultsTest(labelsIOB[3], labelsIOB[0], 0, 0, 1);
    runIOBResultsTest(labelsIOB[1], labelsIOB[3], 0, 1, 1);
    runIOBResultsTest(labelsIOB[3], labelsIOB[1], 0, 1, 1);
    runIOBResultsTest(labelsIOB[2], labelsIOB[3], 0, 1, 1);
    runIOBResultsTest(labelsIOB[3], labelsIOB[2], 0, 1, 1);
    runIOBResultsTest(labelsIOB[3], labelsIOB[3], 1, 0, 0);
  }


}

