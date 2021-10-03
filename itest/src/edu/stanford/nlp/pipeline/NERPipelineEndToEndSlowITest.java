package edu.stanford.nlp.pipeline;

import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.util.TestPaths;

import junit.framework.TestCase;

import java.util.*;

/**
 * Test for verifying that NER pipeline results match benchmark output results.
 */

public class NERPipelineEndToEndSlowITest extends TestCase {

  public static String DATA_PATH = String.format("%s/stanford-corenlp/testing/data/ner", TestPaths.testHome());

  StanfordCoreNLP pipeline3Class;
  StanfordCoreNLP pipeline4Class;
  StanfordCoreNLP pipeline7Class;

  @Override
  public void setUp() {
    // set up the pipeline using 3-class model
    Properties props = new Properties();
    props.setProperty("annotators", "tokenize,ssplit,pos,lemma,ner");
    props.setProperty("ssplit.eolonly", "true");
    props.setProperty("ner.model", "edu/stanford/nlp/models/ner/english.all.3class.distsim.crf.ser.gz");
    props.setProperty("ner.statisticalOnly", "true");
    pipeline3Class = new StanfordCoreNLP(props);
    // set up the pipeline using 4-class model
    props.setProperty("ner.model", "edu/stanford/nlp/models/ner/english.conll.4class.distsim.crf.ser.gz");
    pipeline4Class = new StanfordCoreNLP(props);
    // set up thet pipeline using 7-class model
    props.setProperty("ner.model", "edu/stanford/nlp/models/ner/english.muc.7class.distsim.crf.ser.gz");
    pipeline7Class = new StanfordCoreNLP(props);
  }

  public List<List<String>> readInExpectedNERLabels(String expectedPath) {
    List<String> expectedLines = IOUtils.linesFromFile(String.format("%s/%s", DATA_PATH, expectedPath));
    List<List<String>> expectedNERLabels = new ArrayList<>();
    List<String> currSentence = new ArrayList<>();
    for (String expectedLine : expectedLines) {
      if (expectedLine.trim().equals("")) {
        if (currSentence.size() > 0) {
          expectedNERLabels.add(currSentence);
          currSentence = new ArrayList<>();
        } else {
          currSentence = new ArrayList<>();
        }
      } else {
        System.out.println(expectedLine);
        currSentence.add(expectedLine.split("\t")[1]);
      }
    }
    return expectedNERLabels;
  }

  public void runModelTest(String inputFile, String outputFile, StanfordCoreNLP pipeline) {
    List<String> inputSentences = IOUtils.linesFromFile(String.format("%s/%s", DATA_PATH, inputFile));
    List<List<String>> expectedLabels = readInExpectedNERLabels(outputFile);
    for (int i = 0; i < inputSentences.size() ; i++) {
      CoreDocument doc = new CoreDocument(pipeline.process(inputSentences.get(i)));
      assertEquals(expectedLabels.get(i), doc.sentences().get(0).nerTags());
    }
  }

  /** Test English 3-class model **/
  public void testEnglish3Class() {
    String threeClassInput = "english.all.3class.distsim-regression.input";
    String threeClassOutput = "english.all.3class.distsim-regression.expected";
    runModelTest(threeClassInput, threeClassOutput, pipeline3Class);
  }

  /** Test English 4-class model **/
  public void testEnglish4Class() {
    String fourClassInput = "english.conll.4class.distsim-regression.input";
    String fourClassOutput = "english.conll.4class.distsim-regression.expected";
    runModelTest(fourClassInput, fourClassOutput, pipeline4Class);
  }

  /** Test English 7-class model **/
  public void testEnglish7Class() {
    String sevenClassInput = "english.muc.7class.distsim-regression.input";
    String sevenClassOutput = "english.muc.7class.distsim-regression.expected";
    runModelTest(sevenClassInput, sevenClassOutput, pipeline7Class);
  }

}
