package edu.stanford.nlp.pipeline;

import edu.stanford.nlp.util.StringUtils;
import edu.stanford.nlp.util.TestPaths;

import org.junit.Before;
import org.junit.Test;

import java.util.Properties;

public class TokenizerSpanishBenchmarkITest extends TokenizerBenchmarkTestCase {

  @Before
  public void setUp() {
    // set up the pipeline
    Properties props = StringUtils.argsToProperties("-props", "spanish");
    props.put("annotators", "tokenize,ssplit,mwt");
    props.put("ssplit.isOneSentence", "true");
    pipeline = new StanfordCoreNLP(props);
  }

  @Test
  public void testOnDev() {
    goldFilePath = String.format("%s/stanford-corenlp/testing/data/tokenize/es_ancora-ud-dev.conllu", TestPaths.testHome());
    runTest("dev", "es", 0.9955);
  }

  @Test
  public void testOnTest() {
    goldFilePath = String.format("%s/stanford-corenlp/testing/data/tokenize/es_ancora-ud-test.conllu", TestPaths.testHome());
    runTest("test", "es", 0.996);
  }

}
