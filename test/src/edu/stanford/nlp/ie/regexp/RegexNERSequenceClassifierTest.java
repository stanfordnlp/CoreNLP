package edu.stanford.nlp.ie.regexp;

import junit.framework.TestCase;

import java.io.*;
import java.util.*;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.SentenceUtils;
import edu.stanford.nlp.util.StringUtils;

/**
 * A simple test for the regex ner.  Writes out a temporary file with
 * some patterns.  It then reads in those patterns to a couple regex
 * ner classifiers, tests them on a couple sentences, and makes sure
 * it gets the expected results.
 *
 * @author John Bauer
 */
public class RegexNERSequenceClassifierTest extends TestCase {

  private static File tempFile = null;

  static final String[] words =
  { "My dog likes to eat sausage : turkey , pork , beef , etc .",
    "I went to Shoreline Park and saw an avocet and some curlews ( shorebirds ) ." };
  static final String[] tags =
  { "PRP$ NN RB VBZ VBG NN : NN , NN , NN , FW .",
    "PRP VBD TO NNP NNP CC VBD DT NN CC DT NNS -LRB- NNP -RRB- ." };
  static final String[] ner =
  { "O O O O O O O O O O O O O O O",
    "O O O LOCATION LOCATION O O O O O O O O O O O"};

  static final String[] expectedUncased =
  { "- - - - - food - - - - - - - - -",
    "- - - park park - - - shorebird - - shorebird - - - -" };

  static final String[] expectedCased =
  { "- - - - - food - - - - - - - - -",
    "- - - - - - - - shorebird - - shorebird - - - -" };

  static final String[] nerPatterns = {
          "Shoreline Park\tPARK\n",
          "Shoreline Park\tPARK\tLOCATION\n",
          "Shoreline\tPARK\n",
          "Shoreline Park and\tPARK\tLOCATION\n",
          "My\tPOSS\nsausage \\:\tFOO\n",
          "My\tPOSS\nsausage :\tFOO\n",
          "My\tPOSS\n\\. \\.\tFOO\n",
          "\\.\tPERIOD\n",
          ".\tPERIOD\n",
          "\\(|\\)\tPAREN\n",
  };

  static final String[][] expectedNER =
  {
    { "- - - - - - - - - - - - - - -",
      "- - - - - - - - - - - - - - - -" },
    { "- - - - - - - - - - - - - - -",
      "- - - PARK PARK - - - - - - - - - - -" },
    { "- - - - - - - - - - - - - - -",
      "- - - - - - - - - - - - - - - -" },
    { "- - - - - - - - - - - - - - -",
      "- - - PARK PARK PARK - - - - - - - - - -" }, // not clear it should do this, but does, as it's only tokenwise compatibility
    { "POSS - - - - FOO FOO - - - - - - - -",
      "- - - - - - - - - - - - - - - -" },
    { "POSS - - - - FOO FOO - - - - - - - -",
      "- - - - - - - - - - - - - - - -" },
    { "POSS - - - - - - - - - - - - - -",
      "- - - - - - - - - - - - - - - -" },
    { "- - - - - - - - - - - - - - PERIOD",
      "- - - - - - - - - - - - - - - PERIOD" },
    { "- - - - - - PERIOD - PERIOD - PERIOD - PERIOD - PERIOD",
      "PERIOD - - - - - - - - - - - PERIOD - PERIOD PERIOD" },
    { "- - - - - - - - - - - - - - -",
      "- - - - - - - - - - - - PAREN - PAREN -" },
  };

  public List<List<CoreLabel>> sentences;
  public List<List<CoreLabel>> NERsentences;

  @Override
  public void setUp()
    throws IOException
  {
    synchronized(RegexNERSequenceClassifierTest.class) {
      if (tempFile == null) {
        tempFile = File.createTempFile("regexnertest.patterns", "txt");
        FileWriter fout = new FileWriter(tempFile);
        BufferedWriter bout = new BufferedWriter(fout);
        bout.write("sausage\tfood\n");
        bout.write("(avocet|curlew)(s?)\tshorebird\n");
        bout.write("shoreline park\tpark\n");
        bout.flush();
        fout.close();
      }
    }

    sentences = new ArrayList<List<CoreLabel>>();
    NERsentences = new ArrayList<List<CoreLabel>>();
    assertEquals(words.length, tags.length);
    assertEquals(words.length, ner.length);
    for (int snum = 0; snum < words.length; ++snum) {
      String[] wordPieces = words[snum].split(" ");
      String[] tagPieces = tags[snum].split(" ");
      String[] nerPieces = ner[snum].split(" ");
      assertEquals(wordPieces.length, tagPieces.length);
      assertEquals("Input " + snum + " " + words[snum] + " of different length than " + ner[snum], wordPieces.length, nerPieces.length);
      List<CoreLabel> sentence = new ArrayList<CoreLabel>();
      List<CoreLabel> NERsentence = new ArrayList<CoreLabel>();
      for (int wnum = 0; wnum < wordPieces.length; ++wnum) {
        CoreLabel token = new CoreLabel();
        token.setWord(wordPieces[wnum]);
        token.setTag(tagPieces[wnum]);
        sentence.add(token);
        CoreLabel NERtoken = new CoreLabel();
        NERtoken.setWord(wordPieces[wnum]);
        NERtoken.setTag(tagPieces[wnum]);
        NERtoken.setNER(nerPieces[wnum]);
        NERsentence.add(NERtoken);
      }
      sentences.add(sentence);
      NERsentences.add(NERsentence);
    }
  }

  private static String listToString(List<CoreLabel> sentence) {
    StringBuilder sb = null;
    for (CoreLabel cl : sentence) {
      if (sb == null) {
        sb = new StringBuilder("[");
      } else {
        sb.append(", ");
      }
      sb.append(cl.toShortString());
    }
    if (sb == null) {
      sb = new StringBuilder("[");
    }
    sb.append(']');
    return sb.toString();
  }

  private static List<CoreLabel> deepCopy(List<CoreLabel> in) {
    List<CoreLabel> cll = new ArrayList<CoreLabel>(in.size());
    for (CoreLabel cl : in) {
      cll.add(new CoreLabel(cl));
    }
    return cll;
  }

  private static void compareAnswers(String[] expected, List<CoreLabel> sentence) {
    assertEquals("Lengths different for " + StringUtils.join(expected) + " and " + SentenceUtils.listToString(sentence), expected.length, sentence.size());
    String str = "Comparing " + Arrays.toString(expected) + " and " + listToString(sentence);
    for (int i = 0; i < expected.length; ++i) {
      if (expected[i].equals("-")) {
        assertEquals(str, null, sentence.get(i).get(CoreAnnotations.AnswerAnnotation.class));
      } else {
        assertEquals(str, expected[i],
                     sentence.get(i).get(CoreAnnotations.AnswerAnnotation.class));
      }
    }
  }

  public void testUncased() {
    String tempFilename = tempFile.getPath();
    RegexNERSequenceClassifier uncased =
      new RegexNERSequenceClassifier(tempFilename, true, false);

    assertEquals(sentences.size(), expectedUncased.length);
    for (int i = 0; i < sentences.size(); ++i) {
      List<CoreLabel> sentence = deepCopy(sentences.get(i));
      uncased.classify(sentence);
      String[] answers = expectedUncased[i].split(" ");
      compareAnswers(answers, sentence);
    }
  }

  public void testCased() {
    String tempFilename = tempFile.getPath();
    RegexNERSequenceClassifier cased =
      new RegexNERSequenceClassifier(tempFilename, false, false);

    assertEquals(sentences.size(), expectedCased.length);
    for (int i = 0; i < sentences.size(); ++i) {
      List<CoreLabel> sentence = deepCopy(sentences.get(i));
      cased.classify(sentence);
      String[] answers = expectedCased[i].split(" ");
      compareAnswers(answers, sentence);
    }
  }

  public void testNEROverlaps() {
    assertEquals(nerPatterns.length, expectedNER.length);
    for (int k = 0; k < nerPatterns.length; k++) {
      BufferedReader r1 = new BufferedReader(new StringReader(nerPatterns[k]));
      RegexNERSequenceClassifier cased =
        new RegexNERSequenceClassifier(r1, false, false, null);
      assertEquals(NERsentences.size(), expectedNER[k].length);
      for (int i = 0; i < NERsentences.size(); ++i) {
        List<CoreLabel> sentence = deepCopy(NERsentences.get(i));
        cased.classify(sentence);
        String[] answers = expectedNER[k][i].split(" ");
        compareAnswers(answers, sentence);
      }
      // System.err.println("Completed test " + k);
    }
  }

}
