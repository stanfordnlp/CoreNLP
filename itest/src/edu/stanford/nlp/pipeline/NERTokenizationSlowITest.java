package edu.stanford.nlp.pipeline;

import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.util.TestPaths;

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

  public static String DATA_PATH = String.format("%s/stanford-corenlp/testing/data/ner", TestPaths.testHome());

  public StanfordCoreNLP nerTokenizationPipeline;
  public StanfordCoreNLP standardTokenizationPipeline;
  public StanfordCoreNLP preTokenizedPipeline;


  @Override
  public void setUp() {
    // set up the pipeline with NER tokenization
    Properties props = new Properties();
    props.setProperty("ssplit.isOneSentence", "true");
    props.setProperty("annotators", "tokenize,ssplit,pos,lemma,ner");
    nerTokenizationPipeline = new StanfordCoreNLP(props);
    // set up the pipeline with NER tokenization deactivated
    props.setProperty("ner.useNERSpecificTokenization", "false");
    standardTokenizationPipeline = new StanfordCoreNLP(props);
    // set up pipeline for pre-tokenized text
    props.setProperty("tokenize.whitespace", "true");
    preTokenizedPipeline = new StanfordCoreNLP(props);
  }

  /** Verify pipelines using NER tokenization and not are the same on sentences without hyphen **/
  public void testNonHyphenSentences() {

    List<String> nonHyphenContainingSentences = IOUtils.linesFromFile(String.format("%s/%s", DATA_PATH,
        "ner-tokenization-no-hyphen-large.txt"));

    for (String sentence : nonHyphenContainingSentences) {
      CoreDocument nerTokenizationDoc = new CoreDocument(sentence);
      CoreDocument standardTokenizationDoc = new CoreDocument(sentence);
      nerTokenizationPipeline.annotate(nerTokenizationDoc);
      standardTokenizationPipeline.annotate(standardTokenizationDoc);
      assertEquals(nerTokenizationDoc.tokens(), standardTokenizationDoc.tokens());
    }
  }

  /** Verify using NER tokenization gets expected results on sentences with hyphens **/
  public void testHyphenSentences() {

    List<String> hyphenContainingSentences = IOUtils.linesFromFile(String.format("%s/%s", DATA_PATH,
        "ner-tokenization-w-hyphen-small.txt"));

    List<String> hyphenContainingSentencesPreTokenized = IOUtils.linesFromFile(String.format("%s/%s", DATA_PATH,
        "ner-tokenization-w-hyphen-pretokenized-small.txt"));

    for (int i = 0 ; i < hyphenContainingSentences.size() ; i++) {
      CoreDocument doc = new CoreDocument(nerTokenizationPipeline.process(hyphenContainingSentences.get(i)));
      CoreDocument preTokenizedDoc = new CoreDocument(preTokenizedPipeline.process(hyphenContainingSentencesPreTokenized.get(i)));
      assertEquals(doc.entityMentions().stream().map(em -> em.text()).collect(Collectors.toList()),
          preTokenizedDoc.entityMentions().stream().map(em -> em.text()).collect(Collectors.toList()));
    }
  }

}
