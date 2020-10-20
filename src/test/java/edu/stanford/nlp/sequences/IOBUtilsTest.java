package edu.stanford.nlp.sequences;

import java.util.ArrayList;
import java.util.List;

import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counter;
import junit.framework.TestCase;

/** Tests the any kind of IOB-style notation processing.
 *  In particular, this tests the IOB encoding results counting.
 *
 *  @author Christopher Manning
 *  @author John Bauer
 */
public class IOBUtilsTest extends TestCase {

  private static final String[] words =
          {"Deportivo", "scored", "when", "AJ", "Auxerre", "playmaker", "Corentine", "Angelo",
                  "Martins", "tripped", "on", "Brazilian-born", "Spanish", "Donato", "." };

  private static final String[] iob1 = {
          "I-ORG", "O", "O", "I-ORG", "I-ORG", "O", "I-PER", "I-PER",
          "I-PER", "O", "O", "I-MISC", "B-MISC", "I-PER", "O" };

  private static final String[] iob2 = {
          "B-ORG", "O", "O", "B-ORG", "I-ORG", "O", "B-PER", "I-PER",
          "I-PER", "O", "O", "B-MISC", "B-MISC", "B-PER", "O" };


  private static final String[] iobes = {
          "S-ORG", "O", "O", "B-ORG", "E-ORG", "O", "B-PER", "I-PER",
          "E-PER", "O", "O", "S-MISC", "S-MISC", "S-PER", "O" };

  private static final String[] io = {
          "I-ORG", "O", "O", "I-ORG", "I-ORG", "O", "I-PER", "I-PER",
          "I-PER", "O", "O", "I-MISC", "I-MISC", "I-PER", "O" };

  private static final String[] noprefix = {
          "ORG", "O", "O", "ORG", "ORG", "O", "PER", "PER",
          "PER", "O", "O", "MISC", "MISC", "PER", "O" };

  private static final String[] bilou = {
          "U-ORG", "O", "O", "B-ORG", "L-ORG", "O", "B-PER", "I-PER",
          "L-PER", "O", "O", "U-MISC", "U-MISC", "U-PER", "O" };

  public void testIOB1IOB2() {
    List<CoreLabel> testInput = loadCoreLabelList(words, iob1);
    IOBUtils.entitySubclassify(testInput, CoreAnnotations.AnswerAnnotation.class, "O", "iob2", true);
    checkAnswers(testInput, words, iob2);
  }

  public void testIOB1IOB1() {
    List<CoreLabel> testInput = loadCoreLabelList(words, iob1);
    IOBUtils.entitySubclassify(testInput, CoreAnnotations.AnswerAnnotation.class, "O", "iob1", true);
    checkAnswers(testInput, words, iob1);
  }

  public void testIOB2IOB1() {
    List<CoreLabel> testInput = loadCoreLabelList(words, iob2);
    IOBUtils.entitySubclassify(testInput, CoreAnnotations.AnswerAnnotation.class, "O", "iob1", true);
    checkAnswers(testInput, words, iob1);
  }

  public void testIOB2IOBES() {
    List<CoreLabel> testInput = loadCoreLabelList(words, iob2);
    IOBUtils.entitySubclassify(testInput, CoreAnnotations.AnswerAnnotation.class, "O", "iobes", true);
    checkAnswers(testInput, words, iobes);
  }

  public void testIOBESIOB1() {
    List<CoreLabel> testInput = loadCoreLabelList(words, iobes);
    IOBUtils.entitySubclassify(testInput, CoreAnnotations.AnswerAnnotation.class, "O", "iob1", true);
    checkAnswers(testInput, words, iob1);
  }

  public void testIOB1IO() {
    List<CoreLabel> testInput = loadCoreLabelList(words, iob1);
    IOBUtils.entitySubclassify(testInput, CoreAnnotations.AnswerAnnotation.class, "O", "io", true);
    checkAnswers(testInput, words, io);
  }

  public void testIOB1NoPrefix() {
    List<CoreLabel> testInput = loadCoreLabelList(words, iob1);
    IOBUtils.entitySubclassify(testInput, CoreAnnotations.AnswerAnnotation.class, "O", "noprefix", true);
    checkAnswers(testInput, words, noprefix);
  }

  public void testNoPrefixIO() {
    List<CoreLabel> testInput = loadCoreLabelList(words, noprefix);
    IOBUtils.entitySubclassify(testInput, CoreAnnotations.AnswerAnnotation.class, "O", "io", true);
    checkAnswers(testInput, words, io);
  }

  public void testBILOUIOBES() {
    List<CoreLabel> testInput = loadCoreLabelList(words, bilou);
    IOBUtils.entitySubclassify(testInput, CoreAnnotations.AnswerAnnotation.class, "O", "iobes", true);
    checkAnswers(testInput, words, iobes);
  }

  public void testIOB2BILOU() {
    List<CoreLabel> testInput = loadCoreLabelList(words, iob2);
    IOBUtils.entitySubclassify(testInput, CoreAnnotations.AnswerAnnotation.class, "O", "BILOU", true);
    checkAnswers(testInput, words, bilou);
  }

  private static List<CoreLabel> loadCoreLabelList(String[] words, String[] answers) {
    List<CoreLabel> testInput = new ArrayList<>();
    String[] fields = { "word", "answer"};
    String[] values = new String[2];
    assertEquals(words.length, answers.length);
    for (int i = 0; i < words.length; i++) {
      values[0] = words[i];
      values[1] = answers[i];
      CoreLabel c = new CoreLabel(fields, values);
      testInput.add(c);
    }
    return testInput;
  }

  private static void checkAnswers(List<CoreLabel> testInput, String[] words, String[] answers) {
    for (int i = 0; i < testInput.size(); i++) {
      assertEquals("Wrong for " + words[i], answers[i], testInput.get(i).get(CoreAnnotations.AnswerAnnotation.class));
    }
  }

  private static final String BG = "O";

  private static final String[][] labelsIOB2 = {
    {    BG,    BG,    BG,    BG,    BG,    BG,    BG,    BG,    BG,    BG },  // 0
    {    BG,    BG,    BG,    BG, "I-A",    BG,    BG,    BG,    BG,    BG },  // 1
    {    BG,    BG,    BG,    BG, "I-A", "I-A",    BG,    BG,    BG,    BG },  // 2
    {    BG,    BG,    BG, "I-A", "I-A",    BG,    BG,    BG,    BG,    BG },  // 3
    {    BG,    BG,    BG,    BG, "I-A", "I-B",    BG,    BG,    BG,    BG },  // 4
    {    BG,    BG,    BG,    BG, "I-A", "B-A",    BG,    BG,    BG,    BG },  // 5
    {    BG,    BG,    BG,    BG,    BG,    BG,    BG,    BG,    BG, "I-A" },  // 6
  };

  private static void runIOBResultsTest(String[] gold, String[] guess, double tp, double fp, double fn) {
    List<CoreLabel> sentence = makeListCoreLabel(gold, guess);
    Counter<String> entityTP = new ClassicCounter<>();
    Counter<String> entityFP = new ClassicCounter<>();
    Counter<String> entityFN = new ClassicCounter<>();
    IOBUtils.countEntityResults(sentence, entityTP, entityFP, entityFN, BG);
    assertEquals("For true positives", tp, entityTP.totalCount(), 0.0001);
    assertEquals("For false positives", fp, entityFP.totalCount(), 0.0001);
    assertEquals("For false negatives", fn, entityFN.totalCount(), 0.0001);
  }

  private static List<CoreLabel> makeListCoreLabel(String[] gold, String[] guess) {
    assertEquals("Cannot run test on lists of different length", gold.length, guess.length);
    List<CoreLabel> sentence = new ArrayList<>();
    for (int i = 0; i < gold.length; ++i) {
      CoreLabel word = new CoreLabel();
      word.set(CoreAnnotations.GoldAnswerAnnotation.class, gold[i]);
      word.set(CoreAnnotations.AnswerAnnotation.class, guess[i]);
      sentence.add(word);
    }
    return sentence;
  }

  public void testIOB2Results() {
    runIOBResultsTest(labelsIOB2[0], labelsIOB2[0], 0, 0, 0);

    runIOBResultsTest(labelsIOB2[0], labelsIOB2[1], 0, 1, 0);
    runIOBResultsTest(labelsIOB2[1], labelsIOB2[0], 0, 0, 1);
    runIOBResultsTest(labelsIOB2[1], labelsIOB2[1], 1, 0, 0);

    runIOBResultsTest(labelsIOB2[0], labelsIOB2[2], 0, 1, 0);
    runIOBResultsTest(labelsIOB2[2], labelsIOB2[0], 0, 0, 1);
    runIOBResultsTest(labelsIOB2[1], labelsIOB2[2], 0, 1, 1);
    runIOBResultsTest(labelsIOB2[2], labelsIOB2[1], 0, 1, 1);
    runIOBResultsTest(labelsIOB2[2], labelsIOB2[2], 1, 0, 0);

    runIOBResultsTest(labelsIOB2[0], labelsIOB2[3], 0, 1, 0);
    runIOBResultsTest(labelsIOB2[3], labelsIOB2[0], 0, 0, 1);
    runIOBResultsTest(labelsIOB2[1], labelsIOB2[3], 0, 1, 1);
    runIOBResultsTest(labelsIOB2[3], labelsIOB2[1], 0, 1, 1);
    runIOBResultsTest(labelsIOB2[2], labelsIOB2[3], 0, 1, 1);
    runIOBResultsTest(labelsIOB2[3], labelsIOB2[2], 0, 1, 1);
    runIOBResultsTest(labelsIOB2[3], labelsIOB2[3], 1, 0, 0);

    runIOBResultsTest(labelsIOB2[0], labelsIOB2[4], 0, 2, 0);
    runIOBResultsTest(labelsIOB2[4], labelsIOB2[0], 0, 0, 2);
    runIOBResultsTest(labelsIOB2[1], labelsIOB2[4], 1, 1, 0);
    runIOBResultsTest(labelsIOB2[4], labelsIOB2[1], 1, 0, 1);
    runIOBResultsTest(labelsIOB2[2], labelsIOB2[4], 0, 2, 1);
    runIOBResultsTest(labelsIOB2[4], labelsIOB2[2], 0, 1, 2);
    runIOBResultsTest(labelsIOB2[3], labelsIOB2[4], 0, 2, 1);
    runIOBResultsTest(labelsIOB2[4], labelsIOB2[3], 0, 1, 2);
    runIOBResultsTest(labelsIOB2[4], labelsIOB2[4], 2, 0, 0);

    runIOBResultsTest(labelsIOB2[0], labelsIOB2[5], 0, 2, 0);
    runIOBResultsTest(labelsIOB2[5], labelsIOB2[0], 0, 0, 2);
    runIOBResultsTest(labelsIOB2[1], labelsIOB2[5], 1, 1, 0);
    runIOBResultsTest(labelsIOB2[5], labelsIOB2[1], 1, 0, 1);
    runIOBResultsTest(labelsIOB2[2], labelsIOB2[5], 0, 2, 1);
    runIOBResultsTest(labelsIOB2[5], labelsIOB2[2], 0, 1, 2);
    runIOBResultsTest(labelsIOB2[3], labelsIOB2[5], 0, 2, 1);
    runIOBResultsTest(labelsIOB2[5], labelsIOB2[3], 0, 1, 2);
    runIOBResultsTest(labelsIOB2[4], labelsIOB2[5], 1, 1, 1);
    runIOBResultsTest(labelsIOB2[5], labelsIOB2[4], 1, 1, 1);
    runIOBResultsTest(labelsIOB2[5], labelsIOB2[5], 2, 0, 0);

    runIOBResultsTest(labelsIOB2[0], labelsIOB2[6], 0, 1, 0);
    runIOBResultsTest(labelsIOB2[6], labelsIOB2[0], 0, 0, 1);
    runIOBResultsTest(labelsIOB2[1], labelsIOB2[6], 0, 1, 1);
    runIOBResultsTest(labelsIOB2[6], labelsIOB2[1], 0, 1, 1);
    runIOBResultsTest(labelsIOB2[2], labelsIOB2[6], 0, 1, 1);
    runIOBResultsTest(labelsIOB2[6], labelsIOB2[2], 0, 1, 1);
    runIOBResultsTest(labelsIOB2[3], labelsIOB2[6], 0, 1, 1);
    runIOBResultsTest(labelsIOB2[6], labelsIOB2[3], 0, 1, 1);
    runIOBResultsTest(labelsIOB2[4], labelsIOB2[6], 0, 1, 2);
    runIOBResultsTest(labelsIOB2[6], labelsIOB2[4], 0, 2, 1);
    runIOBResultsTest(labelsIOB2[5], labelsIOB2[6], 0, 1, 2);
    runIOBResultsTest(labelsIOB2[6], labelsIOB2[5], 0, 2, 1);
    runIOBResultsTest(labelsIOB2[6], labelsIOB2[6], 1, 0, 0);
  }

  private static final String[][] labelsIOB = {
    {    BG,    BG,    BG,    BG,    BG,    BG,    BG,    BG,    BG,    BG },  // 0
    {    BG,    BG,    BG,    BG, "B-A",    BG,    BG,    BG,    BG,    BG },  // 1
    {    BG,    BG,    BG,    BG, "B-A", "I-A",    BG,    BG,    BG,    BG },  // 2
    {    BG,    BG,    BG, "B-A", "I-A",    BG,    BG,    BG,    BG,    BG },  // 3
    {    BG,    BG,    BG,    BG, "B-A", "B-A",    BG,    BG,    BG,    BG },  // 4
  };

  public void testIOBResults() {
    // gold, guess, tp, fp, fn
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

    runIOBResultsTest(labelsIOB[2], labelsIOB[4], 0, 2, 1);
    runIOBResultsTest(labelsIOB[4], labelsIOB[2], 0, 1, 2);
  }

  private static final String[][] labelsIOE = {
   {    BG,    BG,    BG,    BG, "I-A", "E-A", "I-A",    BG,    BG,    BG },  // 0
   {    BG,    BG,    BG,    BG, "I-A", "L-A", "I-A",    BG,    BG,    BG },  // 1
   {    BG,    BG,    BG,    BG, "I-A", "I-A", "I-A",    BG,    BG,    BG },  // 2
  };

  public void testIOEResults() {
    // gold, guess, tp, fp, fn

    runIOBResultsTest(labelsIOE[0], labelsIOE[1], 2, 0, 0);
    runIOBResultsTest(labelsIOE[0], labelsIOE[2], 0, 1, 2);
    runIOBResultsTest(labelsIOE[2], labelsIOE[0], 0, 2, 1);
    runIOBResultsTest(labelsIOE[0], labelsIOB[2], 1, 0, 1);
  }

  private static final String[][] labelsIO = {
    {    BG,    BG,    BG,    BG,    BG,    BG,    BG,    BG,    BG,    BG },  // 0
    {    BG,    BG,    BG,    BG, "I-A",    BG,    BG,    BG,    BG,    BG },  // 1
    {    BG,    BG,    BG,    BG, "I-A", "I-A",    BG,    BG,    BG,    BG },  // 2
    {    BG,    BG,    BG, "I-A", "I-A",    BG,    BG,    BG,    BG,    BG },  // 3
    {    BG,    BG,    BG, "I-A", "I-A", "I-A", "I-A",    BG,    BG,    BG },  // 4
    {    BG,    BG,    BG, "I-A", "I-B", "I-B", "I-A",    BG,    BG,    BG },  // 5
    {    BG,    BG,    BG, "I-A", "I-A", "I-B", "I-A",    BG,    BG,    BG },  // 6
  };

  public void testIOResults() {
    // gold, guess, tp, fp, fn

    runIOBResultsTest(labelsIOB[2], labelsIO[2], 1, 0, 0);
    runIOBResultsTest(labelsIOB[4], labelsIO[2], 0, 1, 2);

    runIOBResultsTest(labelsIO[2], labelsIOB[2], 1, 0, 0);
    runIOBResultsTest(labelsIO[2], labelsIOB[4], 0, 2, 1);

    runIOBResultsTest(labelsIO[4], labelsIO[5], 0, 3, 1);
    runIOBResultsTest(labelsIO[4], labelsIO[6], 0, 3, 1);
    runIOBResultsTest(labelsIO[5], labelsIO[6], 1, 2, 2);
  }

  private static final String[][] labelsIOBES = {
    {    BG,    BG,    BG, "B-A", "E-A",    BG,    BG,    BG,    BG,    BG },  // 0
    {    BG,    BG,    BG, "B-A", "L-A",    BG,    BG,    BG,    BG,    BG },  // 1
    {    BG,    BG,    BG, "B-A", "I-A", "I-A", "E-A",    BG,    BG,    BG },  // 2
    {    BG,    BG,    BG, "B-A", "I-A", "I-A", "L-A",    BG,    BG,    BG },  // 3
    {    BG,    BG,    BG, "B-A", "L-A", "U-A", "U-A",    BG,    BG,    BG },  // 4
  };

  public void testIOBESResults() {
    // gold, guess, tp, fp, fn

    runIOBResultsTest(labelsIOBES[0], labelsIOBES[1], 1, 0, 0);
    runIOBResultsTest(labelsIOBES[4], labelsIOBES[0], 1, 0, 2);

    runIOBResultsTest(labelsIOBES[2], labelsIOBES[3], 1, 0, 0);
    runIOBResultsTest(labelsIOBES[2], labelsIOBES[4], 0, 3, 1);
  }

}

