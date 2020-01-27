package edu.stanford.nlp.pipeline;

import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.ling.*;
import edu.stanford.nlp.util.StringUtils;

import junit.framework.TestCase;

import java.util.*;
import java.util.stream.*;

/**
 * This is a test to examine the behavior of the NER specific tokenization.
 * The goal is to make sure that nothing crazy is happening.  This test
 * will run on 10,000 English sentences and verify that the pipeline w/NER tokenization
 * matches the pipeline with NER tokenization shut off.  Also it will look at 100
 * examples with hyphens to see expected behavior.
 *
 * **/

public class NERTokenizationSlowITest extends TestCase {

  public static String DATA_PATH = "/u/nlp/data/stanford-corenlp-testing/data/ner";

  public StanfordCoreNLP nerTokenizationPipeline;
  public StanfordCoreNLP standardTokenizationPipeline;


  @Override
  public void setUp() {
    // set up the pipeline with NER tokenization
    Properties props = new Properties();
    props.setProperty("annotators", "tokenize,ssplit,pos,lemma,ner");
    nerTokenizationPipeline = new StanfordCoreNLP(props);
    // set up the pipeline with NER tokenization deactivated
    props.setProperty("ner.useNERSpecificTokenization", "false");
    standardTokenizationPipeline = new StanfordCoreNLP(props);
  }

  /** Verify both pipelines are the same on sentences without hyphen **/
  public void testNonHyphenSentences() {

    List<String> nonHyphenContainingSentences = IOUtils.linesFromFile(String.format("%s/%s", DATA_PATH,
        "en_ewt_sentences_no_hyphen.txt"));

    for (String sentence : nonHyphenContainingSentences) {
      CoreDocument nerTokenizationDoc = new CoreDocument(sentence);
      CoreDocument standardTokenizationDoc = new CoreDocument(sentence);
      nerTokenizationPipeline.annotate(nerTokenizationDoc);
      standardTokenizationPipeline.annotate(standardTokenizationDoc);
      assertEquals(nerTokenizationDoc.tokens(), standardTokenizationDoc.tokens());
    }
  }


}
