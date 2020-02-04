package edu.stanford.nlp.pipeline;

import edu.stanford.nlp.util.StringUtils;

import java.util.Properties;

public class TokenizerSpanishBenchmarkITest extends TokenizerBenchmarkTestCase {

  @Override
  public void setUp() {
    // set up the pipeline
    Properties props = StringUtils.argsToProperties("-props", "spanish");
    props.put("annotators", "tokenize,ssplit,mwt");
    props.put("ssplit.isOneSentence", "true");
    pipeline = new StanfordCoreNLP(props);
  }

  public void testOnDev() {
    goldFilePath = "/u/nlp/data/stanford-corenlp-testing/data/tokenize/es_ancora-ud-dev.conllu";
    runTest("dev", "es", 0.994);
  }

  public void testOnTest() {
    goldFilePath = "/u/nlp/data/stanford-corenlp-testing/data/tokenize/es_ancora-ud-test.conllu";
    runTest("test", "es", 0.994);
  }

}
