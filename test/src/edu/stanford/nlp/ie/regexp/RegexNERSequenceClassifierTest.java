package edu.stanford.nlp.ie.regexp;

import junit.framework.TestCase;

import java.io.*;
import java.util.*;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;

/**
 * A simple test for the regex ner.  Writes out a temporary file with
 * some patterns.  It then reads in those patterns to a couple regex
 * ner classifiers, tests them on a couple sentences, and makes sure
 * it gets the expected results.
 *
 * @author John Bauer
 */
public class RegexNERSequenceClassifierTest extends TestCase {
  public static File tempFile = null;

  static final String[] words = 
  { "My dog likes to eat sausage .",
    "I went to Shoreline Park and saw an avocet and some curlews ." };
  static final String[] tags = 
  { "PRP$ NN RB VBZ VBG NN .",
    "PRP VBD TO NNP NNP CC VBD DT NN CC DT NNS ." };

  static final String[] expectedUncased =
  { "- - - - - food -",
    "- - - park park - - - shorebird - - shorebird -" };
    
  static final String[] expectedCased =
  { "- - - - - food -",
    "- - - - - - - - shorebird - - shorebird -" };
    
  public List<List<CoreLabel>> sentences = null;
  
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
    assertEquals(words.length, tags.length);
    for (int snum = 0; snum < words.length; ++snum) {
      String[] wordPieces = words[snum].split(" ");
      String[] tagPieces = tags[snum].split(" ");
      assertEquals(wordPieces.length, tagPieces.length);
      List<CoreLabel> sentence = new ArrayList<CoreLabel>();
      for (int wnum = 0; wnum < wordPieces.length; ++wnum) {
        CoreLabel token = new CoreLabel();
        token.setWord(wordPieces[wnum]);
        token.setTag(tagPieces[wnum]);
        sentence.add(token);
      }
      sentences.add(sentence);
    }
  }

  public void compareAnswers(String[] expected, List<CoreLabel> sentence) {
    assertEquals(expected.length, sentence.size());
    for (int i = 0; i < expected.length; ++i) {
      if (expected[i].equals("-")) {
        assertEquals(null, sentence.get(i).get(CoreAnnotations.AnswerAnnotation.class));
      } else {
        assertEquals(expected[i], 
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
      List<CoreLabel> sentence = sentences.get(i);
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
      List<CoreLabel> sentence = sentences.get(i);
      cased.classify(sentence);
      String[] answers = expectedCased[i].split(" ");
      compareAnswers(answers, sentence);
    }
  }
}
