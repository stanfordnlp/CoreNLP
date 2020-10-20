package edu.stanford.nlp.ie;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.CoreUtilities;
import junit.framework.TestCase;

/** @author Christopher Manning */
public class ClassifierCombinerTest extends TestCase {

  String[] words = { "Joe", "Smith", "drank", "44", "Budweiser", "cans",
                      "at", "Monaco", "Brewing", "."
  };
  String[] tags  = {  "NNP",  "NNP",  "VBD",   "CD",  "NNP",  "NNS",   "IN",  "NNP",  "NNP",    "." };

  String[] ans1  = {  "PER",  "PER",    "O",    "O",  "ORG",    "O",    "O",  "ORG",  "ORG",    "O" };
  String[] ans2  = {    "O",    "O",    "O",  "NUM",    "O",    "O",    "O",    "O",    "O",    "O" };
  String[] ans3  = {    "O",    "O",    "O",  "NUM", "PROD", "PROD",    "O",    "O",    "O",    "O" };
  String[] ans4  = {  "PER",  "PER",    "O",    "O",    "O",    "O",    "O",    "O",    "O",    "O" };
  String[] ans5  = {    "O",    "O",    "O",  "NUM", "PROD", "PROD",    "O",  "ORG",  "ORG",  "ORG" };
  String[] ans6  = {    "O",    "O",    "O",    "O",    "O",    "O",    "O",    "O",    "O",    "O" };
  String[] ans7  = {  "PER",  "PER",    "O",  "NUM", "PROD", "PROD",    "O",  "ORG",  "ORG",  "ORG" };
  String[] ans8  = {    "O",    "O",    "O", "PROD", "PROD",    "O",    "O",    "O",    "O",    "O" };
  String[] ans9  = {    "O",    "O",    "O",    "O",    "O",    "O",    "O",    "O",    "O",  "NUM" };
  String[] ans10 = {    "O",    "O",    "O",    "O",    "O",    "O",    "O",    "O",  "NUM",  "NUM" };
  String[] ans11 = {    "O",    "O",    "O",    "O", "PROD", "PROD",    "O",    "O",    "O",    "O" };
  String[] ans12 = {    "O",    "O",    "O",    "O",    "O",    "O",    "O",    "O",  "NUM",  "NUM" };
  String[] ans13 = {    "O",    "O",    "O",    "O",    "O",    "O",  "NUM",  "NUM",    "O",    "O" };
  String[] ans14 = {    "O",    "O",    "O",    "O",    "O",    "O",  "FOO",  "FOO",    "O",    "O" };
  String[] ans15 = {    "O",    "O",  "PER",  "PER",    "O",    "O",    "O",    "O",    "O",    "O" };
  String[] ans16 = {    "O",    "O",  "FOO",  "FOO",    "O",    "O",    "O",    "O",    "O",    "O" };

  String[] out1  = {  "PER",  "PER",    "O",  "NUM",  "ORG",    "O",    "O",  "ORG",  "ORG",    "O" };
  String[] out2  = {  "PER",  "PER",    "O",  "NUM", "PROD", "PROD",    "O",  "ORG",  "ORG",  "ORG" };
  String[] out3  = {    "O",    "O",    "O",  "NUM", "PROD", "PROD",    "O",  "ORG",  "ORG",  "ORG" };
  String[] out4  = {    "O",    "O",    "O",  "NUM",    "O",    "O",    "O",    "O",    "O",  "NUM" };
  String[] out5  = {    "O",    "O",    "O",  "NUM",    "O",    "O",    "O",    "O",  "NUM",  "NUM" };
  String[] out6  = {    "O",    "O",    "O",    "O",    "O",    "O",  "NUM",  "NUM",  "NUM",  "NUM" };
  String[] out7  = {    "O",    "O",    "O",    "O",    "O",    "O",  "FOO",  "FOO",  "NUM",  "NUM" };
  String[] out8  = {  "PER",  "PER",  "PER",  "PER",    "O",    "O",    "O",    "O",    "O",    "O" };
  String[] out9  = {  "PER",  "PER",  "FOO",  "FOO",    "O",    "O",    "O",    "O",    "O",    "O" };
  String[] out10 = {  "PER",  "PER",    "O",  "NUM", "PROD", "PROD",    "O",    "O",    "O",    "O" };


  public void testCombination() {
    // test that a non-conflicting label can be added
    runTest(ans1, ans2, out1, "NUM");

    // test that a conflicting label isn't added
    runTest(ans1, ans3, out1, "NUM", "PROD");

    // test that a sequence final label is added (didn't used to work...)
    runTest(ans4, ans5, out2, "NUM", "PROD", "ORG");
    runTest(ans5, ans4, out2, "PER");

    // test that a label not in the auxLabels set isn't added
    runTest(ans6, ans7, out3, "NUM", "PROD", "ORG");

    // test that a sequence initial label is added
    runTest(ans6, ans7, out2, "NUM", "PROD", "ORG", "PER");

    // test that a label segment that conflicts later on isn't added
    runTest(ans1, ans8, ans1, "NUM", "PROD", "ORG", "PER");

    // Test that labels that are already in the first sequence are
    // still added if they are present in later sequences
    runTest(ans2, ans9, out4, "NUM");
    runTest(ans9, ans2, out4, "NUM");
    runTest(ans2, ans10, out5, "NUM");
    runTest(ans10, ans2, out5, "NUM");

    // Test neighbors overlapping
    runTest(ans8, ans11, ans8, "PROD");
    runTest(ans11, ans8, ans11, "PROD");

    // Test non-overlapping neighbors at the end of a sequence
    runTest(ans12, ans13, out6, "NUM");
    runTest(ans13, ans12, out6, "NUM");
    runTest(ans12, ans14, out7, "FOO");
    runTest(ans14, ans12, out7, "NUM");

    // Test non-overlapping neighbors at the start of a sequence
    runTest(ans4, ans15, out8, "PER");
    runTest(ans15, ans4, out8, "PER");
    runTest(ans4, ans16, out9, "FOO");
    runTest(ans16, ans4, out9, "PER");

    // test consecutive labels
    runTest(ans3, ans4, out10, "PER", "NUM", "PROD");

    // test consecutive labels
    runTest(ans4, ans3, out10, "PER", "NUM", "PROD");

    // test a label that conflicted with a main label, followed by a
    // label that doesn't conflict
    runTest(ans2, ans3, ans3, "NUM", "PROD");
  }

  public void outputResults(String[] firstInput, String[] secondInput, 
                            String[] expectedOutput, String ... labels) {
    List<CoreLabel> input1 = CoreUtilities.toCoreLabelList(words, tags, firstInput);
    List<CoreLabel> input2 = CoreUtilities.toCoreLabelList(words, tags, secondInput);
    List<CoreLabel> result = CoreUtilities.toCoreLabelList(words, tags, expectedOutput);
    Set<String> auxLabels = new HashSet<>();
    for (String label : labels) {
      auxLabels.add(label);
    }
    ClassifierCombiner.mergeTwoDocuments(input1, input2, auxLabels, "O");
    for (CoreLabel word : input1) {
      System.out.println(word.word() + " " + word.tag() + " " + 
                         word.get(CoreAnnotations.AnswerAnnotation.class));
    }
  }

  public void runTest(String[] firstInput, String[] secondInput, 
                      String[] expectedOutput, String ... labels) {    
    List<CoreLabel> input1 = CoreUtilities.toCoreLabelList(words, tags, firstInput);
    List<CoreLabel> input2 = CoreUtilities.toCoreLabelList(words, tags, secondInput);
    List<CoreLabel> result = CoreUtilities.toCoreLabelList(words, tags, expectedOutput);
    Set<String> auxLabels = new HashSet<>();
    for (String label : labels) {
      auxLabels.add(label);
    }
    ClassifierCombiner.mergeTwoDocuments(input1, input2, auxLabels, "O");
    assertEquals(result, input1);
  }

}
