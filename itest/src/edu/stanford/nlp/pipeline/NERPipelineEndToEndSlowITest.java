package edu.stanford.nlp.pipeline;

import edu.stanford.nlp.io.IOUtils;

import junit.framework.TestCase;

import java.util.*;

public class NERPipelineEndToEndSlowITest extends TestCase {

  public static String DATA_PATH = "/u/nlp/data/stanford-corenlp-testing/data/ner";

  StanfordCoreNLP pipeline3Class;

  @Override
  public void setUp() {
    // set up the pipeline with NER tokenization
    Properties props3Class = new Properties();
    props3Class.setProperty("annotators", "tokenize,ssplit,pos,lemma,ner");
    props3Class.setProperty("ssplit.eolonly", "true");
    props3Class.setProperty("ner.model", "edu/stanford/nlp/models/ner/english.all.3class.distsim.crf.ser.gz");
    props3Class.setProperty("ner.applyNumericClassifiers", "false");
    props3Class.setProperty("ner.applyFineGrained", "false");
    props3Class.setProperty("ner.useSUTime", "false");
    pipeline3Class = new StanfordCoreNLP(props3Class);
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

  public void testEnglish3Class() {
    String inputFile = "english.all.3class.distsim-regression.input";
    String outputFile = "english.all.3class.distsim-regression.expected";
    List<String> inputSentences = IOUtils.linesFromFile(String.format("%s/%s", DATA_PATH, inputFile));
    List<List<String>> expectedLabels = readInExpectedNERLabels(outputFile);
    for (int i = 0; i < inputSentences.size() ; i++) {
      CoreDocument doc = new CoreDocument(pipeline3Class.process(inputSentences.get(i)));
      assertEquals(expectedLabels.get(i), doc.sentences().get(0).nerTags());
    }
  }

}
